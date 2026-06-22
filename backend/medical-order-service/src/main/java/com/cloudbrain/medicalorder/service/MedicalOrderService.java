package com.cloudbrain.medicalorder.service;

import com.cloudbrain.medicalorder.controller.MedicalOrderController;
import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.repository.MedicalOrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicalOrderService {
    private static final Map<String, String> EXECUTOR_ROLE = Map.of(
            "CHECK", "CHECK_DOCTOR", "LAB", "LAB_DOCTOR", "DISPOSAL", "DISPOSAL_DOCTOR");
    private final MedicalOrderRepository repository;
    private final AiTriageClient triageClient;

    public MedicalOrderService(MedicalOrderRepository repository,AiTriageClient triageClient) { this.repository = repository;this.triageClient=triageClient; }

    @Transactional
    public MedicalOrder create(MedicalOrderController.CreateRequest request, String doctorId) {
        String type = requireType(request.orderType());
        require(request.appointmentId(), "appointmentId");
        require(request.patientId(), "patientId");
        require(request.projectCode(), "projectCode");
        require(request.projectName(), "projectName");
        String urgency=request.urgency()==null?"ROUTINE":request.urgency().toUpperCase();
        if(!Set.of("ROUTINE","EMERGENCY").contains(urgency))throw new IllegalArgumentException("urgency 必须为 ROUTINE 或 EMERGENCY");
        return repository.create(new MedicalOrder(
                "order-" + UUID.randomUUID(), request.appointmentId(), request.patientId(), request.patientName(), doctorId,
                type, request.projectCode(), request.projectName(), request.purpose(), request.bodyPart(),
                request.amount() == null ? BigDecimal.ZERO : request.amount(), "UNPAID", "PENDING_PAYMENT",
                null,null,null,null,null,urgency,null,null,0,
                null, null, java.time.LocalDateTime.now(), null, null));
    }

    public List<MedicalOrder> list(String type, String status, String patientId) {
        return repository.find(type == null ? null : requireType(type), status, patientId);
    }

    public List<MedicalOrder> listAuthorized(String type,String status,String patientId,String actorId,String role){
        String forcedType=switch(role){case "CHECK_DOCTOR"->"CHECK";case "LAB_DOCTOR"->"LAB";case "DISPOSAL_DOCTOR"->"DISPOSAL";default->type;};
        List<MedicalOrder> orders=list(forcedType,status,patientId);
        if(Set.of("CHECK_DOCTOR","LAB_DOCTOR","DISPOSAL_DOCTOR").contains(role))
            return orders.stream().filter(o->actorId.equals(o.executorId())).toList();
        if("OUTPATIENT_DOCTOR".equals(role))return orders.stream().filter(o->actorId.equals(o.orderingDoctorId())).toList();
        return orders;
    }

    @Transactional
    public MedicalOrder pay(String id, String actorId, String role) {
        MedicalOrder order = get(id);
        if ("PATIENT".equals(role) && !order.patientId().equals(actorId)) {
            throw new org.springframework.security.access.AccessDeniedException("患者只能支付自己的医技申请");
        }
        if (!repository.markPaid(id)) throw new IllegalStateException("医技申请不存在或当前状态不可缴费");
        MedicalOrder paid=get(id);
        List<MedicalOrderRepository.ExecutorCandidate> candidates=repository.executorCandidates(paid.orderType());
        AiTriageClient.TriageResult triage=triageClient.triage(paid,candidates);
        if(!repository.assign(id,triage.executorId(),triage.executorName(),triage.location(),triage.equipmentId(),triage.source(),triage.reasons()))
            throw new IllegalStateException("医技分诊状态已变化");
        return get(id);
    }

    @Transactional public MedicalOrder miss(String id,String actorId,String role){MedicalOrder order=get(id);validateExecutor(order.orderType(),role);if(!actorId.equals(order.executorId()))throw new org.springframework.security.access.AccessDeniedException("只能操作分配给自己的队列");repository.moveToTail(id,actorId);return get(id);}

    @Transactional
    public MedicalOrder start(String id, String executorId, String role) {
        MedicalOrder order = get(id);
        validateExecutor(order.orderType(), role);
        if(!executorId.equals(order.executorId()))throw new org.springframework.security.access.AccessDeniedException("医嘱未分配给当前执行医生");
        if (!repository.start(id, executorId)) throw new IllegalStateException("只有已缴费待执行医嘱可以开始");
        return get(id);
    }

    @Transactional
    public MedicalOrder complete(String id, String executorId, String role, String resultData, String summary,
                                 String createdByType, String aiRecordId) {
        MedicalOrder order = get(id);
        validateExecutor(order.orderType(), role);
        String json = resultData == null || resultData.isBlank() ? "{}" : resultData;
        String source = createdByType == null || createdByType.isBlank() ? "HUMAN" : createdByType.toUpperCase();
        if (!source.equals("HUMAN") && !source.equals("AI")) throw new IllegalArgumentException("createdByType 必须为 HUMAN 或 AI");
        if (source.equals("AI") && (aiRecordId == null || aiRecordId.isBlank())) {
            throw new IllegalArgumentException("AI 生成结果必须关联 aiRecordId");
        }
        if (!repository.complete(id, executorId, json, summary, source, aiRecordId)) throw new IllegalStateException("医嘱未开始、已完成或执行人不匹配");
        return get(id);
    }

    private MedicalOrder get(String id) { return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("医技申请不存在")); }
    private void validateExecutor(String type, String role) {
        if (!EXECUTOR_ROLE.get(type).equals(role)) throw new IllegalStateException("当前医生角色不能执行该类医嘱");
    }
    private String requireType(String type) {
        if (!EXECUTOR_ROLE.containsKey(type)) throw new IllegalArgumentException("orderType 必须为 CHECK、LAB 或 DISPOSAL");
        return type;
    }
    private void require(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " 不能为空"); }
}
