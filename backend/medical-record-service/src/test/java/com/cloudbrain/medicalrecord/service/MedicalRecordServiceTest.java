package com.cloudbrain.medicalrecord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cloudbrain.medicalrecord.controller.MedicalRecordController;
import com.cloudbrain.medicalrecord.entity.MedicalRecord;
import com.cloudbrain.medicalrecord.repository.MedicalRecordRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MedicalRecordServiceTest {
    @Mock MedicalRecordRepository repository;
    @Test void duplicateInitialEventReturnsExistingRecordWithoutOverwritingDoctorContent(){
        MedicalRecord existing=new MedicalRecord("r","a","p","患者","d","医生","神经内科","2026-06-23","上午","摘要","LOW");
        when(repository.findByAppointmentId("a")).thenReturn(Optional.of(existing));
        MedicalRecord result=new MedicalRecordService(repository).createInitial(request());
        assertThat(result).isSameAs(existing);
        verify(repository,never()).createInitialIfAbsent(any());
    }
    @Test void staleVersionIsRejectedBeforeSaving(){
        MedicalRecord existing=record("a");
        when(repository.findByAppointmentId("a")).thenReturn(Optional.of(existing));
        var request=new MedicalRecordController.WriteDoctorNoteRequest("a",1,"主诉","现病史","既往史","无",
                "体检","初步诊断","方案","","HUMAN",null);
        assertThatThrownBy(()->new MedicalRecordService(repository).writeDoctorNote(request,"d"))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        verify(repository,never()).save(any(),any(Long.class));
    }
    @Test void historicalAccessRequiresReasonAndWritesAudit(){
        MedicalRecord current=record("current");MedicalRecord old=record("old");
        when(repository.findByAppointmentId("current")).thenReturn(Optional.of(current));
        when(repository.findByPatientId("p")).thenReturn(java.util.List.of(current,old));
        var result=new MedicalRecordService(repository).history("p","current","复诊查阅","d");
        assertThat(result).containsExactly(old);
        verify(repository).recordAccess(old.getId(),"p","d","OUTPATIENT_DOCTOR","HISTORY","复诊查阅");
    }
    private MedicalRecord record(String appointmentId){return new MedicalRecord("r-"+appointmentId,appointmentId,"p","患者","d","医生","神经内科","2026-06-23","上午","摘要","LOW");}
    private MedicalRecordController.CreateInitialRecordRequest request(){
        return new MedicalRecordController.CreateInitialRecordRequest("a","p","患者","d","医生","神经内科","2026-06-23","上午","摘要","LOW");
    }
}
