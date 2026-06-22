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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;
    private final SlotInventoryRepository slotInventoryRepository;
    private final MedicalRecordEventRepository integrationEventRepository;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            SlotInventoryRepository slotInventoryRepository,
            MedicalRecordEventRepository integrationEventRepository) {
        this.appointmentRepository = appointmentRepository;
        this.slotInventoryRepository = slotInventoryRepository;
        this.integrationEventRepository = integrationEventRepository;
    }

    public List<Appointment> list(String doctorId, String patientId, String status) {
        return appointmentRepository.findAll().stream()
                .filter(item -> Optional.ofNullable(doctorId).map(id -> id.equals(item.getDoctorId())).orElse(true))
                .filter(item -> Optional.ofNullable(patientId).map(id -> id.equals(item.getPatientId())).orElse(true))
                .filter(item -> Optional.ofNullable(status).map(value -> value.equals(item.getStatus().name())).orElse(true))
                .toList();
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

        if (!slotInventoryRepository.tryLock(request.scheduleId())) {
            throw new IllegalStateException("当前号源已约满或排班不存在");
        }

        Appointment appointment = buildAppointment(request, AppointmentSource.ONLINE, AppointmentStatus.PENDING_PAYMENT, PaymentStatus.UNPAID);
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment createOffline(AppointmentController.CreateAppointmentRequest request) {
        validateRequired(request.scheduleId(), "scheduleId");
        validateRequired(request.patientId(), "patientId");
        validateRequired(request.doctorId(), "doctorId");
        validateRequired(request.visitDate(), "visitDate");
        if (!slotInventoryRepository.bookOffline(request.scheduleId())) {
            throw new IllegalStateException("当前号源已约满或排班不存在");
        }
        Appointment appointment = buildAppointment(request, AppointmentSource.OFFLINE, AppointmentStatus.WAITING, PaymentStatus.PAID);
        appointment.markPaid("OFFLINE_WINDOW");
        appointmentRepository.save(appointment);
        integrationEventRepository.enqueuePayment(appointment, BigDecimal.ZERO, "cashier");
        integrationEventRepository.enqueueMedicalRecord(appointment);
        return appointment;
    }

    @Transactional
    public Appointment pay(String id, String paymentMethod, BigDecimal amount, String operatorId) {
        Appointment appointment = get(id);
        if (appointment.getStatus() != AppointmentStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("只有待缴费挂号可以支付");
        }
        if (!slotInventoryRepository.confirmLocked(appointment.getScheduleId())) {
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
    public Appointment cancel(String id) {
        Appointment appointment = get(id);
        if (appointment.getStatus() == AppointmentStatus.CANCELLED || appointment.getStatus() == AppointmentStatus.FINISHED) {
            throw new IllegalStateException("当前挂号状态不允许取消");
        }
        if (!appointment.getVisitDate().isAfter(LocalDate.now())) {
            throw new IllegalStateException("就诊当天不可取消挂号或退费");
        }
        boolean paid = appointment.getPaymentStatus() == PaymentStatus.PAID;
        if (paid) {
            slotInventoryRepository.releaseBooked(appointment.getScheduleId());
            integrationEventRepository.enqueueRefund(appointment, BigDecimal.ZERO, appointment.getPatientId());
        } else {
            slotInventoryRepository.releaseLocked(appointment.getScheduleId());
        }
        appointment.markCancelled(paid);
        return appointmentRepository.save(appointment);
    }

    public Appointment updateStatus(String id, String status) {
        Appointment appointment = get(id);
        if ("FINISHED".equals(status)) {
            appointment.markFinished();
            return appointmentRepository.save(appointment);
        }
        throw new IllegalArgumentException("暂不支持的状态流转: " + status);
    }

    public Appointment skip(String id) {
        Appointment appointment = get(id);
        if (appointment.getStatus() != AppointmentStatus.WAITING) {
            throw new IllegalStateException("只有待接诊患者可以过号");
        }
        appointment.skip(3);
        return appointmentRepository.save(appointment);
    }

    public List<SlotInventory> slots() {
        return slotInventoryRepository.findAll();
    }

    @Transactional
    public SlotInventory syncSlot(String scheduleId, int capacity) {
        SlotInventory inventory = slotInventoryRepository.findByScheduleId(scheduleId)
                .orElse(new SlotInventory(scheduleId, capacity, 0));
        return slotInventoryRepository.save(inventory);
    }

    private Appointment get(String id) {
        return appointmentRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("挂号记录不存在"));
    }

    private Appointment buildAppointment(
            AppointmentController.CreateAppointmentRequest request,
            AppointmentSource source,
            AppointmentStatus status,
            PaymentStatus paymentStatus) {
        int queueNumber = appointmentRepository.nextQueueNumber(request.doctorId(), request.visitDate());
        return new Appointment(
                "appt-" + UUID.randomUUID(),
                request.scheduleId(),
                request.patientId(),
                request.patientName(),
                request.doctorId(),
                request.doctorName(),
                request.departmentId(),
                request.departmentName(),
                LocalDate.parse(request.visitDate()),
                request.period(),
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

}
