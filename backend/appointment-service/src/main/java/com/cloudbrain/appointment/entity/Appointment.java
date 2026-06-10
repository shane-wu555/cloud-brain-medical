package com.cloudbrain.appointment.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Appointment {
    private final String id;
    private final String scheduleId;
    private final String patientId;
    private final String patientName;
    private final String doctorId;
    private final String doctorName;
    private final String departmentId;
    private final String departmentName;
    private final LocalDate visitDate;
    private final String period;
    private final AppointmentSource source;
    private AppointmentStatus status;
    private PaymentStatus paymentStatus;
    private String paymentMethod;
    private final String triageSummary;
    private final String riskLevel;
    private final String recommendedDepartmentId;
    private int queueNumber;
    private int missedCount;
    private final LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;

    public Appointment(
            String id,
            String scheduleId,
            String patientId,
            String patientName,
            String doctorId,
            String doctorName,
            String departmentId,
            String departmentName,
            LocalDate visitDate,
            String period,
            AppointmentSource source,
            AppointmentStatus status,
            PaymentStatus paymentStatus,
            String triageSummary,
            String riskLevel,
            String recommendedDepartmentId,
            int queueNumber) {
        this.id = id;
        this.scheduleId = scheduleId;
        this.patientId = patientId;
        this.patientName = patientName;
        this.doctorId = doctorId;
        this.doctorName = doctorName;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.visitDate = visitDate;
        this.period = period;
        this.source = source;
        this.status = status;
        this.paymentStatus = paymentStatus;
        this.triageSummary = triageSummary;
        this.riskLevel = riskLevel;
        this.recommendedDepartmentId = recommendedDepartmentId;
        this.queueNumber = queueNumber;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public String getPeriod() {
        return period;
    }

    public AppointmentSource getSource() {
        return source;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getTriageSummary() {
        return triageSummary;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getRecommendedDepartmentId() {
        return recommendedDepartmentId;
    }

    public int getQueueNumber() {
        return queueNumber;
    }

    public int getMissedCount() {
        return missedCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void markPaid(String paymentMethod) {
        this.paymentMethod = paymentMethod;
        this.paymentStatus = PaymentStatus.PAID;
        this.status = AppointmentStatus.WAITING;
        this.paidAt = LocalDateTime.now();
    }

    public void markFinished() {
        this.status = AppointmentStatus.FINISHED;
    }

    public void markCancelled() {
        this.status = AppointmentStatus.CANCELLED;
        this.paymentStatus = PaymentStatus.REFUNDED;
        this.cancelledAt = LocalDateTime.now();
    }

    public void skip(int positions) {
        this.queueNumber += positions;
        this.missedCount++;
        this.status = AppointmentStatus.WAITING;
    }

    public void restorePersistenceState(String paymentMethod, int missedCount) {
        this.paymentMethod = paymentMethod;
        this.missedCount = missedCount;
    }
}
