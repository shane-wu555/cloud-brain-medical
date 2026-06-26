package com.cloudbrain.medicalrecord.service;

import com.cloudbrain.medicalrecord.controller.MedicalRecordController;
import com.cloudbrain.medicalrecord.entity.*;
import com.cloudbrain.medicalrecord.repository.MedicalRecordRepository;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicalRecordService {
    private final MedicalRecordRepository repository;
    private final PatientAccessClient patientAccessClient;
    public MedicalRecordService(MedicalRecordRepository repository, PatientAccessClient patientAccessClient){
        this.repository=repository;
        this.patientAccessClient=patientAccessClient;
    }

    public List<MedicalRecord> listAuthorized(String actorId,String role,String patientId,String appointmentId,String status){
        List<MedicalRecord> records;
        if("PATIENT".equals(role)) {
            String scopedPatientId=patientId;
            if(scopedPatientId==null||scopedPatientId.isBlank()) scopedPatientId=patientAccessClient.boundPatientId(actorId);
            if(scopedPatientId==null||scopedPatientId.isBlank()) throw new AccessDeniedException("请先添加并绑定就诊人");
            if(!patientAccessClient.owns(actorId,scopedPatientId)) throw new AccessDeniedException("无权访问该就诊人的病历");
            records=repository.findByPatientId(scopedPatientId);
            patientId=scopedPatientId;
        }
        else if("OUTPATIENT_DOCTOR".equals(role)) records=appointmentId!=null
                ? repository.findByAppointmentId(appointmentId).stream().toList()
                : repository.findAll().stream().filter(r->r.getDoctorId().equals(actorId)).toList();
        else throw new AccessDeniedException("无权访问病历");
        String finalPatientId=patientId;
        return records.stream()
                .filter(r->finalPatientId==null||r.getPatientId().equals(finalPatientId))
                .filter(r->appointmentId==null||r.getAppointmentId().equals(appointmentId))
                .filter(r->status==null||r.getStatus().name().equals(status))
                .peek(r->{if("OUTPATIENT_DOCTOR".equals(role)&&!r.getDoctorId().equals(actorId))throw new AccessDeniedException("无权访问该病历");})
                .toList();
    }

    public List<MedicalRecord> history(String patientId,String currentAppointmentId,String reason,String doctorId){
        if(reason==null||reason.isBlank()) throw new IllegalArgumentException("查看其他历史病历必须填写访问原因");
        MedicalRecord current=repository.findByAppointmentId(currentAppointmentId)
                .orElseThrow(()->new IllegalArgumentException("本次病历不存在"));
        if(!current.getDoctorId().equals(doctorId)||!current.getPatientId().equals(patientId))throw new AccessDeniedException("无权查看该患者历史病历");
        List<MedicalRecord> history=repository.findByPatientId(patientId).stream()
                .filter(r->!r.getAppointmentId().equals(currentAppointmentId)).toList();
        history.forEach(r->repository.recordAccess(r.getId(),patientId,doctorId,"OUTPATIENT_DOCTOR","HISTORY",reason));
        return history;
    }

    public MedicalRecord createInitial(MedicalRecordController.CreateInitialRecordRequest request){
        return repository.findByAppointmentId(request.appointmentId()).orElseGet(()->repository.createInitialIfAbsent(new MedicalRecord(
                "record-"+UUID.randomUUID(),request.appointmentId(),request.patientId(),request.patientName(),request.doctorId(),
                request.doctorName(),request.departmentName(),request.visitDate(),request.period(),request.triageSummary(),request.riskLevel())));
    }

    @Transactional
    public MedicalRecord writeDoctorNote(MedicalRecordController.WriteDoctorNoteRequest request,String doctorId){
        MedicalRecord record=repository.findByAppointmentId(request.appointmentId()).orElseThrow(()->new IllegalArgumentException("本次就诊病历不存在"));
        if(!record.getDoctorId().equals(doctorId))throw new AccessDeniedException("医生只能编辑自己的患者病历");
        if(record.getVersion()!=request.version())throw new org.springframework.dao.OptimisticLockingFailureException("病历已被其他窗口更新，请刷新后重试");
        String source=request.diagnosisCreatedByType()==null?"HUMAN":request.diagnosisCreatedByType().toUpperCase();
        if(!List.of("HUMAN","AI").contains(source))throw new IllegalArgumentException("diagnosisCreatedByType 必须为 HUMAN 或 AI");
        if("AI".equals(source)&&(request.diagnosisAiRecordId()==null||request.diagnosisAiRecordId().isBlank()))throw new IllegalArgumentException("AI 生成诊断必须关联 diagnosisAiRecordId");
        long expected=record.getVersion();
        record.writeDoctorNote(request.chiefComplaint(),request.presentIllness(),request.pastHistory(),request.allergyHistory(),
                request.physicalExamination(),request.preliminaryDiagnosis(),request.treatmentPlan(),request.doctorRevisionNote(),source,request.diagnosisAiRecordId());
        return repository.save(record,expected);
    }

    @Transactional public MedicalRecord archive(String id,String doctorId){MedicalRecord record=repository.findById(id).orElseThrow(()->new IllegalArgumentException("病历不存在"));if(!record.getDoctorId().equals(doctorId))throw new AccessDeniedException("无权归档该病历");if(record.getStatus()==MedicalRecordStatus.DRAFT)throw new IllegalStateException("病历尚未保存，不能归档");long expected=record.getVersion();record.archive();return repository.save(record,expected);}
    public boolean isSaved(String appointmentId){return repository.findByAppointmentId(appointmentId).map(r->r.getStatus()!=MedicalRecordStatus.DRAFT).orElse(false);}
    public List<MedicalRecordRepository.AccessLog> accessLogs(String patientId){return repository.accessLogs(patientId);}
    public void linkReport(String appointmentId,String orderId,String reportId,String type,String conclusion,String confirmer,java.time.LocalDateTime confirmedAt){repository.linkReport(appointmentId,orderId,reportId,type,conclusion,confirmer,confirmedAt);}
}
