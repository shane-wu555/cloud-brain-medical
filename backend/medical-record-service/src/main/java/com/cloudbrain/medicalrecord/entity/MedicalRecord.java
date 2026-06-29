package com.cloudbrain.medicalrecord.entity;

import java.time.LocalDateTime;

/**
 * 病历实体（适配新 schema）
 * 已删除字段（新 schema 无此列，永远不会被持久化）：
 *   doctorRevisionNote / archivedAt
 * period / aiRiskLevel 仍保留（来自挂号，内存中使用，不再写入 medical_record 表）
 */
public class MedicalRecord {
    private final String id;
    private final String appointmentId;
    private final String patientId;
    private final String patientName;
    private final String doctorId;
    private final String doctorName;
    private final String departmentName;
    private final String visitDate;
    private final String period;          // 来自挂号，内存使用，不持久化
    private final String aiTriageSummary;
    private final String aiRiskLevel;     // 来自挂号，内存使用，不持久化
    private String chiefComplaint;
    private String presentIllness;
    private String pastHistory;
    private String allergyHistory;
    private String physicalExamination;
    private String preliminaryDiagnosis;
    private String diagnosis;
    private String diagnosisCreatedByType = "HUMAN";
    private String diagnosisAiRecordId;
    private String diagnosisConfirmedBy;
    private LocalDateTime diagnosisConfirmedAt;
    private String treatmentPlan;
    private String doctorRevisionNote;  // 医生签注，计划后续加 DB 列
    private MedicalRecordStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long version;

    public MedicalRecord(
            String id, String appointmentId, String patientId, String patientName,
            String doctorId, String doctorName, String departmentName,
            String visitDate, String period, String aiTriageSummary, String aiRiskLevel) {
        this.id = id;
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.patientName = patientName;
        this.doctorId = doctorId;
        this.doctorName = doctorName;
        this.departmentName = departmentName;
        this.visitDate = visitDate;
        this.period = period;
        this.aiTriageSummary = aiTriageSummary;
        this.aiRiskLevel = aiRiskLevel;
        this.chiefComplaint = aiTriageSummary;
        this.status = MedicalRecordStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public String getId()             { return id; }
    public String getAppointmentId()  { return appointmentId; }
    public String getPatientId()      { return patientId; }
    public String getPatientName()    { return patientName; }
    public String getDoctorId()       { return doctorId; }
    public String getDoctorName()     { return doctorName; }
    public String getDepartmentName() { return departmentName; }
    public String getVisitDate()      { return visitDate; }
    public String getPeriod()         { return period; }
    public String getAiTriageSummary(){ return aiTriageSummary; }
    public String getAiRiskLevel()    { return aiRiskLevel; }
    public String getChiefComplaint() { return chiefComplaint; }
    public String getPresentIllness() { return presentIllness; }
    public String getPastHistory()    { return pastHistory; }
    public String getAllergyHistory()  { return allergyHistory; }
    public String getPhysicalExamination()  { return physicalExamination; }
    public String getPreliminaryDiagnosis() { return preliminaryDiagnosis; }
    public String getDiagnosis()            { return diagnosis; }
    public String getDiagnosisCreatedByType() { return diagnosisCreatedByType; }
    public String getDiagnosisAiRecordId()    { return diagnosisAiRecordId; }
    public String getDiagnosisConfirmedBy()   { return diagnosisConfirmedBy; }
    public LocalDateTime getDiagnosisConfirmedAt() { return diagnosisConfirmedAt; }
    public String getTreatmentPlan()       { return treatmentPlan; }
    public String getDoctorRevisionNote()  { return doctorRevisionNote; }
    public MedicalRecordStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion()            { return version; }

    public void writeDoctorNote(
            String chiefComplaint, String presentIllness, String pastHistory,
            String allergyHistory, String physicalExamination,
            String preliminaryDiagnosis, String treatmentPlan,
            String doctorRevisionNote,
            String diagnosisCreatedByType, String diagnosisAiRecordId) {
        this.chiefComplaint = chiefComplaint;
        this.presentIllness = presentIllness;
        this.pastHistory = pastHistory;
        this.allergyHistory = allergyHistory;
        this.physicalExamination = physicalExamination;
        this.preliminaryDiagnosis = preliminaryDiagnosis;
        this.diagnosis = preliminaryDiagnosis;
        this.diagnosisCreatedByType = diagnosisCreatedByType;
        this.diagnosisAiRecordId = diagnosisAiRecordId;
        this.diagnosisConfirmedBy = this.doctorId;
        this.diagnosisConfirmedAt = LocalDateTime.now();
        this.treatmentPlan = treatmentPlan;
        this.doctorRevisionNote = doctorRevisionNote;
        this.status = MedicalRecordStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
        this.version++;
    }

    public void archive() {
        this.status = MedicalRecordStatus.ARCHIVED;
        this.updatedAt = LocalDateTime.now();
        this.version++;
    }

    public void restoreDoctorContent(
            String chiefComplaint, String presentIllness, String pastHistory,
            String allergyHistory, String physicalExamination,
            String preliminaryDiagnosis, String diagnosis, String treatmentPlan,
            String ignoredDoctorRevisionNote,   // 参数保留以兼容调用方，值被忽略
            MedicalRecordStatus status,
            String diagnosisCreatedByType, String diagnosisAiRecordId,
            String diagnosisConfirmedBy, LocalDateTime diagnosisConfirmedAt,
            long version) {
        this.chiefComplaint = chiefComplaint;
        this.presentIllness = presentIllness;
        this.pastHistory = pastHistory;
        this.allergyHistory = allergyHistory;
        this.physicalExamination = physicalExamination;
        this.preliminaryDiagnosis = preliminaryDiagnosis;
        this.diagnosis = diagnosis;
        this.treatmentPlan = treatmentPlan;
        this.status = status;
        this.diagnosisCreatedByType = diagnosisCreatedByType;
        this.diagnosisAiRecordId = diagnosisAiRecordId;
        this.diagnosisConfirmedBy = diagnosisConfirmedBy;
        this.diagnosisConfirmedAt = diagnosisConfirmedAt;
        this.version = version;
    }
}
