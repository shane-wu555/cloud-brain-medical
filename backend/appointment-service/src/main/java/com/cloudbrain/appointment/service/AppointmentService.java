package com.cloudbrain.appointment.service;

import com.cloudbrain.appointment.controller.AppointmentController;
import com.cloudbrain.appointment.entity.Appointment;
import com.cloudbrain.appointment.entity.AppointmentSource;
import com.cloudbrain.appointment.entity.AppointmentStatus;
import com.cloudbrain.appointment.entity.PaymentStatus;
import com.cloudbrain.appointment.entity.SlotInventory;
import com.cloudbrain.appointment.repository.AppointmentRepository;
import com.cloudbrain.appointment.repository.MedicalRecordEventRepository;
import java.math.BigDecimal;
import com.cloudbrain.appointment.repository.SlotInventoryRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;
    private final SlotInventoryRepository slotInventoryRepository;
    private final MedicalRecordEventRepository integrationEventRepository;
    private final MedicalRecordClient medicalRecordClient;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            SlotInventoryRepository slotInventoryRepository,
            MedicalRecordEventRepository integrationEventRepository,
            MedicalRecordClient medicalRecordClient) {
        this.appointmentRepository = appointmentRepository;
        this.slotInventoryRepository = slotInventoryRepository;
        this.integrationEventRepository = integrationEventRepository;
        this.medicalRecordClient = medicalRecordClient;
    }

    public List<Appointment> list(String doctorId, String patientId, String status) {
        return appointmentRepository.findAll().stream()
                .filter(item -> Optional.ofNullable(doctorId).map(id -> id.equals(item.getDoctorId())).orElse(true))
                .filter(item -> Optional.ofNullable(patientId).map(id -> id.equals(item.getPatientId())).orElse(true))
                .filter(item -> Optional.ofNullable(status).map(value -> value.equals(item.getStatus().name())).orElse(true))
                .toList();
    }

    public List<Appointment> todayQueue(String doctorId) {
        return appointmentRepository.findAll().stream()
                .filter(a->a.getDoctorId().equals(doctorId) && a.getVisitDate().equals(LocalDate.now()))
                .filter(a->List.of(AppointmentStatus.WAITING,AppointmentStatus.CALLED,AppointmentStatus.IN_VISIT,AppointmentStatus.REVISIT_WAITING,AppointmentStatus.FINISHED).contains(a.getStatus()))
                .sorted(java.util.Comparator.comparingInt(Appointment::getQueueNumber)).toList();
    }

    public void validatePatientAccess(String appointmentId, String actorId, String role) {
        if ("PATIENT".equals(role) && !get(appointmentId).getPatientId().equals(actorId)) {
            throw new org.springframework.security.access.AccessDeniedException("患者只能操作自己的挂号记录");
        }
    }

    @Transactional
    public Appointment lockOnline(AppointmentController.CreateAppointmentRequest request) {
        validateRequired(request.scheduleId(), "scheduleId");
        validateRequired(request.patientId(), "patientId");
        validateRequired(request.doctorId(), "doctorId");
        validateRequired(request.visitDate(), "visitDate");
        validateRequired(request.period(), "period");
        validateBookingWindow(request.visitDate());
        LocalTime startTime = parseStartTime(request);
        validateNoRepeatedAppointment(request, startTime);

        if (!slotInventoryRepository.tryLock(request.scheduleId())) {
            throw new IllegalStateException("当前号源已约满或排班不存在");
        }

        Appointment appointment = buildAppointment(request, AppointmentSource.ONLINE, AppointmentStatus.PENDING_PAYMENT, PaymentStatus.UNPAID, startTime);
        return saveNewAppointment(appointment);
    }

    @Transactional
    public Appointment createOffline(AppointmentController.CreateAppointmentRequest request) {
        validateRequired(request.scheduleId(), "scheduleId");
        validateRequired(request.patientId(), "patientId");
        validateRequired(request.doctorId(), "doctorId");
        validateRequired(request.visitDate(), "visitDate");
        validateRequired(request.period(), "period");
        validateBookingWindow(request.visitDate());
        LocalTime startTime = parseStartTime(request);
        validateNoRepeatedAppointment(request, startTime);
        if (!slotInventoryRepository.bookOffline(request.scheduleId())) {
            throw new IllegalStateException("当前号源已约满或排班不存在");
        }
        Appointment appointment = buildAppointment(request, AppointmentSource.OFFLINE, AppointmentStatus.WAITING, PaymentStatus.PAID, startTime);
        appointment.markPaid("OFFLINE_WINDOW");
        saveNewAppointment(appointment);
        integrationEventRepository.enqueuePayment(appointment, new BigDecimal("0.01"), "cashier");
        integrationEventRepository.enqueueMedicalRecord(appointment);
        return appointment;
    }

    @Transactional
    public Appointment pay(String id, String paymentMethod, BigDecimal amount, String operatorId) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("挂号记录不存在"));
        if (appointment.getPaymentStatus() == PaymentStatus.PAID) return appointment;
        if (appointment.getStatus() != AppointmentStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("只有待缴费挂号可以支付");
        }
        if (!slotInventoryRepository.confirmLocked(appointment.getSlotId())) {
            throw new IllegalStateException("锁定号源已失效，请重新挂号");
        }
        appointment.markPaid(Optional.ofNullable(paymentMethod).orElse("WECHAT"));
        Appointment saved = appointmentRepository.save(appointment);
        integrationEventRepository.enqueuePayment(
                appointment, Optional.ofNullable(amount).orElse(BigDecimal.ZERO),
                Optional.ofNullable(operatorId).orElse(appointment.getPatientId()));
        integrationEventRepository.enqueueMedicalRecord(appointment);
        return saved;
    }

    @Transactional
    public Appointment cancel(String id, boolean windowOperator) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("挂号记录不存在"));
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) return appointment;
        if (appointment.getStatus() == AppointmentStatus.FINISHED || appointment.getStatus() == AppointmentStatus.IN_VISIT) {
            throw new IllegalStateException("当前挂号状态不允许取消");
        }
        if (!windowOperator && !appointment.getVisitDate().isAfter(LocalDate.now())) {
            throw new IllegalStateException("就诊当天不可取消挂号或退费");
        }
        boolean paid = appointment.getPaymentStatus() == PaymentStatus.PAID;
        if (paid) {
            slotInventoryRepository.releaseBooked(appointment.getSlotId());
            integrationEventRepository.enqueueRefund(appointment, BigDecimal.ZERO, appointment.getPatientId());
        } else {
            slotInventoryRepository.releaseLocked(appointment.getSlotId());
        }
        appointment.markCancelled(paid);
        return appointmentRepository.save(appointment);
    }

    public Appointment updateStatus(String id, String status, String doctorId) {
        Appointment appointment = get(id);
        validateDoctor(appointment,doctorId);
        if ("FINISHED".equals(status)) {
            if(!medicalRecordClient.isSaved(id)) throw new IllegalStateException("病历未保存，不能结束接诊");
            appointment.markFinished();
            return appointmentRepository.save(appointment);
        }
        throw new IllegalArgumentException("暂不支持的状态流转: " + status);
    }

    @Transactional
    public Appointment call(String id,String doctorId) {
        Appointment appointment=appointmentRepository.findByIdForUpdate(id).orElseThrow(()->new IllegalArgumentException("挂号记录不存在"));
        validateDoctor(appointment,doctorId);
        appointment.markCalled(); return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment startVisit(String id,String doctorId) {
        Appointment appointment=appointmentRepository.findByIdForUpdate(id).orElseThrow(()->new IllegalArgumentException("挂号记录不存在"));
        validateDoctor(appointment,doctorId);
        appointment.startVisit(); return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment skip(String id, String doctorId) {
        Appointment appt = get(id);
        validateDoctor(appt, doctorId);
        int missed = appt.getMissedCount();
        // 第1次过号挪3位，第2次挪5位，第3次及之后移至当日队尾
        int positions = missed == 0 ? 3 : missed == 1 ? 5 : Integer.MAX_VALUE;
        return appointmentRepository.skipByPositions(id, positions);
    }

    private void validateDoctor(Appointment appointment,String doctorId) {
        if(!appointment.getDoctorId().equals(doctorId))
            throw new org.springframework.security.access.AccessDeniedException("医生只能操作自己的接诊队列");
    }

    public List<SlotInventory> slots() {
        return slotInventoryRepository.findAll();
    }

    @Transactional
    public SlotInventory syncSlot(String scheduleId, int capacity) {
        SlotInventory inventory = slotInventoryRepository.findByScheduleId(scheduleId)
                .orElse(new SlotInventory(scheduleId, capacity, 0));
        inventory.resize(capacity);
        return slotInventoryRepository.save(inventory);
    }

    @Transactional
    public List<SlotInventory> syncSlots(List<AppointmentController.SyncSlotRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<SlotInventoryRepository.SlotCapacity> capacities = requests.stream()
                .filter(request -> request.scheduleId() != null && !request.scheduleId().isBlank())
                .map(request -> new SlotInventoryRepository.SlotCapacity(request.scheduleId(), request.capacity()))
                .toList();
        slotInventoryRepository.saveAllCapacities(capacities);
        return capacities.stream()
                .map(item -> new SlotInventory(item.slotId(), item.capacity(), 0))
                .toList();
    }

    @Scheduled(fixedDelayString="${appointment.lock-expiration-scan-ms:30000}")
    @Transactional
    public void releaseExpiredLocks() {
        for(String id:appointmentRepository.findExpiredPendingIds()) expireOne(id);
    }

    public void expireOne(String id) {
        Appointment appointment=appointmentRepository.findByIdForUpdate(id).orElse(null);
        if(appointment==null || appointment.getStatus()!=AppointmentStatus.PENDING_PAYMENT) return;
        slotInventoryRepository.releaseLocked(appointment.getSlotId());
        appointment.markPaymentExpired();
        appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment failPayment(String id,String patientId) {
        Appointment appointment=appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(()->new IllegalArgumentException("挂号记录不存在"));
        if(!appointment.getPatientId().equals(patientId)) throw new org.springframework.security.access.AccessDeniedException("支付患者不匹配");
        if(appointment.getPaymentStatus()==PaymentStatus.FAILED) return appointment;
        if(appointment.getStatus()!=AppointmentStatus.PENDING_PAYMENT) throw new IllegalStateException("当前挂号状态不能标记支付失败");
        slotInventoryRepository.releaseLocked(appointment.getSlotId()); appointment.markPaymentExpired();
        return appointmentRepository.save(appointment);
    }
    @Transactional
    public Appointment enterRevisit(String id) {
        Appointment a = appointmentRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("挂号记录不存在"));
        if (a.getStatus() == AppointmentStatus.REVISIT_WAITING) return a;
        if (a.getStatus() != AppointmentStatus.FINISHED && a.getStatus() != AppointmentStatus.IN_VISIT)
            throw new IllegalStateException("当前就诊状态不能进入复诊队列");
        return appointmentRepository.insertForRevisit(id, 3);
    }

    public Appointment find(String id) {
        return get(id);
    }

    private Appointment get(String id) {
        return appointmentRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("挂号记录不存在"));
    }

    private Appointment buildAppointment(
            AppointmentController.CreateAppointmentRequest request,
            AppointmentSource source,
            AppointmentStatus status,
            PaymentStatus paymentStatus,
            LocalTime startTime) {
        int queueNumber = appointmentRepository.nextQueueNumber(request.doctorId(), request.visitDate());
        return new Appointment(
                UUID.randomUUID().toString(),
                request.scheduleId(),
                request.patientId(),
                request.patientName(),
                request.doctorId(),
                request.doctorName(),
                request.departmentId(),
                request.departmentName(),
                LocalDate.parse(request.visitDate()),
                request.period(),
                startTime,
                source,
                status,
                paymentStatus,
                request.triageSummary(),
                Optional.ofNullable(request.riskLevel()).orElse("LOW"),
                request.recommendedDepartmentId(),
                queueNumber);
    }

    private void validateRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }

    private void validateBookingWindow(String visitDate) {
        LocalDate date = LocalDate.parse(visitDate);
        LocalDate today = LocalDate.now();
        if (date.isBefore(today) || date.isAfter(today.plusDays(6))) {
            throw new IllegalArgumentException("只能挂当前日期起 7 天内的号源");
        }
    }

    private LocalTime parseStartTime(AppointmentController.CreateAppointmentRequest request) {
        if (request.startTime() != null && !request.startTime().isBlank()) {
            return LocalTime.parse(request.startTime());
        }
        return switch (request.period()) {
            case "下午", "AFTERNOON" -> LocalTime.of(14, 0);
            default -> LocalTime.of(8, 0);
        };
    }

    private void validateNoRepeatedAppointment(AppointmentController.CreateAppointmentRequest request, LocalTime startTime) {
        if (appointmentRepository.existsActiveAtStartTime(request.patientId(), request.visitDate(), startTime)) {
            throw new IllegalStateException("同一就诊人同一时段不能重复预约");
        }
    }

    private Appointment saveNewAppointment(Appointment appointment) {
        try {
            return appointmentRepository.save(appointment);
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("同一就诊人同一时段不能重复预约");
        }
    }

}
