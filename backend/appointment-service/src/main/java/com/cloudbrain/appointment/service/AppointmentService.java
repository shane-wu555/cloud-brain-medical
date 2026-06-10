package com.cloudbrain.appointment.service;

import com.cloudbrain.appointment.controller.AppointmentController;
import com.cloudbrain.appointment.entity.Appointment;
import com.cloudbrain.appointment.entity.AppointmentSource;
import com.cloudbrain.appointment.entity.AppointmentStatus;
import com.cloudbrain.appointment.entity.PaymentStatus;
import com.cloudbrain.appointment.entity.SlotInventory;
import com.cloudbrain.appointment.repository.AppointmentRepository;
import com.cloudbrain.appointment.repository.SlotInventoryRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;
    private final SlotInventoryRepository slotInventoryRepository;
    private final MedicalRecordClient medicalRecordClient;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            SlotInventoryRepository slotInventoryRepository,
            MedicalRecordClient medicalRecordClient) {
        this.appointmentRepository = appointmentRepository;
        this.slotInventoryRepository = slotInventoryRepository;
        this.medicalRecordClient = medicalRecordClient;
        seed();
    }

    public List<Appointment> list(String doctorId, String patientId, String status) {
        return appointmentRepository.findAll().stream()
                .filter(item -> Optional.ofNullable(doctorId).map(id -> id.equals(item.getDoctorId())).orElse(true))
                .filter(item -> Optional.ofNullable(patientId).map(id -> id.equals(item.getPatientId())).orElse(true))
                .filter(item -> Optional.ofNullable(status).map(value -> value.equals(item.getStatus().name())).orElse(true))
                .toList();
    }

    public Appointment lockOnline(AppointmentController.CreateAppointmentRequest request) {
        validateRequired(request.scheduleId(), "scheduleId");
        validateRequired(request.patientId(), "patientId");
        validateRequired(request.doctorId(), "doctorId");
        validateRequired(request.visitDate(), "visitDate");
        validateRequired(request.period(), "period");

        SlotInventory inventory = slotInventoryRepository.findByScheduleId(request.scheduleId())
                .orElseThrow(() -> new IllegalArgumentException("排班号源不存在"));
        synchronized (inventory) {
            inventory.lock();
            slotInventoryRepository.save(inventory);
        }

        Appointment appointment = buildAppointment(request, AppointmentSource.ONLINE, AppointmentStatus.PENDING_PAYMENT, PaymentStatus.UNPAID);
        return appointmentRepository.save(appointment);
    }

    public Appointment createOffline(AppointmentController.CreateAppointmentRequest request) {
        Appointment appointment = buildAppointment(request, AppointmentSource.OFFLINE, AppointmentStatus.WAITING, PaymentStatus.PAID);
        appointment.markPaid("OFFLINE_WINDOW");
        appointmentRepository.save(appointment);
        medicalRecordClient.createInitialRecord(appointment);
        return appointment;
    }

    public Appointment pay(String id, String paymentMethod) {
        Appointment appointment = get(id);
        if (appointment.getStatus() != AppointmentStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("只有待缴费挂号可以支付");
        }
        SlotInventory inventory = slotInventoryRepository.findByScheduleId(appointment.getScheduleId())
                .orElseThrow(() -> new IllegalArgumentException("排班号源不存在"));
        synchronized (inventory) {
            inventory.confirm();
            slotInventoryRepository.save(inventory);
        }
        appointment.markPaid(Optional.ofNullable(paymentMethod).orElse("WECHAT"));
        medicalRecordClient.createInitialRecord(appointment);
        return appointmentRepository.save(appointment);
    }

    public Appointment cancel(String id) {
        Appointment appointment = get(id);
        if (!appointment.getVisitDate().isAfter(LocalDate.now())) {
            throw new IllegalStateException("就诊当天不可取消挂号或退费");
        }
        SlotInventory inventory = slotInventoryRepository.findByScheduleId(appointment.getScheduleId()).orElse(null);
        if (inventory != null) {
            synchronized (inventory) {
                inventory.releasePaidOrLocked(appointment.getPaymentStatus() == PaymentStatus.PAID);
                slotInventoryRepository.save(inventory);
            }
        }
        appointment.markCancelled();
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
                "appt-" + String.format("%03d", appointmentRepository.size() + 1),
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

    private void seed() {
        if (appointmentRepository.size() > 0) {
            return;
        }
        AppointmentController.CreateAppointmentRequest request = new AppointmentController.CreateAppointmentRequest(
                "schedule-001",
                "patient-001",
                "王小云",
                "doctor-001",
                "张医生",
                "dept-neuro",
                "神经内科",
                LocalDate.now().toString(),
                "上午",
                "AI问诊提示：反复头痛，建议神经内科复诊",
                "MEDIUM",
                "dept-neuro");
        Appointment seed = buildAppointment(request, AppointmentSource.ONLINE, AppointmentStatus.WAITING, PaymentStatus.PAID);
        seed.markPaid("WECHAT");
        appointmentRepository.save(seed);
    }
}
