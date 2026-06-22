package com.cloudbrain.medicalorder.repository;

import com.cloudbrain.medicalorder.domain.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MedicalReportRepository {
    private final JdbcTemplate jdbc;public MedicalReportRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public MedicalAttachment attachment(String orderId,String key,String name,String type,long size,String bucket,String actor){String id="attachment-"+UUID.randomUUID();jdbc.update("insert into medical_attachment(id,medical_order_id,object_key,original_name,content_type,size_bytes,storage_bucket,uploaded_by) values(?,?,?,?,?,?,?,?)",id,orderId,key,name,type,size,bucket,actor);return attachments(orderId).stream().filter(a->a.id().equals(id)).findFirst().orElseThrow();}
    public List<MedicalAttachment> attachments(String orderId){return jdbc.query("select * from medical_attachment where medical_order_id=? order by created_at",(rs,n)->new MedicalAttachment(rs.getString("id"),rs.getString("medical_order_id"),rs.getString("object_key"),rs.getString("original_name"),rs.getString("content_type"),rs.getLong("size_bytes"),rs.getString("storage_bucket"),rs.getString("uploaded_by"),rs.getTimestamp("created_at").toLocalDateTime()),orderId);}
    public AiMedicalTask createTask(String orderId,String externalId){String id="ai-task-"+UUID.randomUUID();jdbc.update("insert into ai_medical_task(id,medical_order_id,external_task_id,task_type,status) values(?,?,?,'CT_ANALYSIS','QUEUED')",id,orderId,externalId);return taskByExternal(externalId).orElseThrow();}
    public Optional<AiMedicalTask> taskByExternal(String id){return jdbc.query("select * from ai_medical_task where external_task_id=?",(rs,n)->task(rs),id).stream().findFirst();}
    public AiMedicalTask updateTask(String externalId,String status,String modelVersion,String output,String error){jdbc.update("update ai_medical_task set status=?,model_version=?,raw_output=?::jsonb,error_message=?,updated_at=now() where external_task_id=?",status,modelVersion,output==null?"{}":output,error,externalId);return taskByExternal(externalId).orElseThrow();}
    private AiMedicalTask task(java.sql.ResultSet rs)throws java.sql.SQLException{return new AiMedicalTask(rs.getString("id"),rs.getString("medical_order_id"),rs.getString("external_task_id"),rs.getString("task_type"),rs.getString("status"),rs.getString("model_version"),rs.getString("raw_output"),rs.getString("error_message"),rs.getTimestamp("updated_at").toLocalDateTime());}
    public MedicalReport saveDraft(String orderId,String type,String findings,String conclusion,String advice,String source,String aiTaskId){String id="report-"+UUID.randomUUID();jdbc.update("""
            insert into medical_report(id,medical_order_id,report_type,status,findings,conclusion,advice,created_by_type,ai_task_id,ai_original_findings,ai_original_conclusion)
            values(?,? ,?,'PENDING_CONFIRMATION',?,?,?,?,?,?,?)
            on conflict(medical_order_id) do update set status='PENDING_CONFIRMATION',findings=excluded.findings,conclusion=excluded.conclusion,advice=excluded.advice,created_by_type=excluded.created_by_type,ai_task_id=excluded.ai_task_id,ai_original_findings=coalesce(medical_report.ai_original_findings,excluded.ai_original_findings),ai_original_conclusion=coalesce(medical_report.ai_original_conclusion,excluded.ai_original_conclusion),updated_at=now()
            """,id,orderId,type,findings,conclusion,advice,source,aiTaskId,"AI".equals(source)?findings:null,"AI".equals(source)?conclusion:null);return reportByOrder(orderId).orElseThrow();}
    public Optional<MedicalReport> reportByOrder(String orderId){return jdbc.query("select * from medical_report where medical_order_id=?",(rs,n)->report(rs),orderId).stream().findFirst();}
    public List<MedicalReport> reports(){return jdbc.query("select * from medical_report order by updated_at desc",(rs,n)->report(rs));}
    public MedicalReport confirm(String orderId,String findings,String conclusion,String advice,String doctor){if(jdbc.update("""
            update medical_report set status='CONFIRMED',findings=?,conclusion=?,advice=?,modified_from_ai=(created_by_type='AI' and (findings is distinct from ? or conclusion is distinct from ?)),confirmed_by=?,confirmed_at=now(),updated_at=now() where medical_order_id=? and status='PENDING_CONFIRMATION'
            """,findings,conclusion,advice,findings,conclusion,doctor,orderId)!=1)throw new IllegalStateException("报告不存在或当前状态不能确认");return reportByOrder(orderId).orElseThrow();}
    public MedicalReport reject(String orderId,String doctor,String reason){if(jdbc.update("update medical_report set status='REJECTED',rejected_by=?,rejected_at=now(),rejection_reason=?,updated_at=now() where medical_order_id=? and status='PENDING_CONFIRMATION'",doctor,reason,orderId)!=1)throw new IllegalStateException("报告当前状态不能驳回");return reportByOrder(orderId).orElseThrow();}
    private MedicalReport report(java.sql.ResultSet rs)throws java.sql.SQLException{return new MedicalReport(rs.getString("id"),rs.getString("medical_order_id"),rs.getString("report_type"),rs.getString("status"),rs.getString("findings"),rs.getString("conclusion"),rs.getString("advice"),rs.getString("created_by_type"),rs.getString("ai_task_id"),rs.getString("ai_original_findings"),rs.getString("ai_original_conclusion"),rs.getBoolean("modified_from_ai"),rs.getString("confirmed_by"),rs.getTimestamp("confirmed_at")==null?null:rs.getTimestamp("confirmed_at").toLocalDateTime(),rs.getString("rejection_reason"),rs.getTimestamp("updated_at").toLocalDateTime());}
}
