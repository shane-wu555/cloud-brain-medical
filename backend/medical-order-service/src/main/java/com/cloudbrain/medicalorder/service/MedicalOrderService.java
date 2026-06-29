package com.cloudbrain.medicalorder.service;

import com.cloudbrain.medicalorder.controller.MedicalOrderController;
import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.repository.MedicalOrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicalOrderService {
    private static final Map<String, String> EXECUTOR_ROLE = Map.of(
            "CHECK", "CHECK_DOCTOR", "LAB", "LAB_DOCTOR", "DISPOSAL", "DISPOSAL_DOCTOR");
    private static final Set<String> TECH_ROLES = Set.of("CHECK_DOCTOR", "LAB_DOCTOR", "DISPOSAL_DOCTOR");

    private final MedicalOrderRepository repository;
    private final AiTriageClient triageClient;

    public MedicalOrderService(MedicalOrderRepository repository, AiTriageClient triageClient) {
        this.repository = repository;
        this.triageClient = triageClient;
    }

    @Transactional
    public MedicalOrder create(MedicalOrderController.CreateRequest request, String doctorId) {
        String type = requireType(request.orderType());
        require(request.appointmentId(), "appointmentId");
        require(request.patientId(), "patientId");
        require(request.projectCode(), "projectCode");
        require(request.projectName(), "projectName");
        String urgency = request.urgency() == null ? "ROUTINE" : request.urgency().toUpperCase();
        if (!Set.of("ROUTINE", "EMERGENCY").contains(urgency)) {
            throw new IllegalArgumentException("urgency 必须为 ROUTINE 或 EMERGENCY");
        }
        if (repository.existsActiveOrder(request.appointmentId(), request.projectCode())) {
            throw new IllegalStateException("该检查/检验项目已在本次就诊中开单，不可重复申请：" + request.projectName());
        }
        return repository.create(new MedicalOrder(
                "order-" + UUID.randomUUID(), request.appointmentId(), request.patientId(), request.patientName(), doctorId,
                type, request.projectCode(), request.projectName(), request.purpose(), request.bodyPart(),
                request.amount() == null ? BigDecimal.ZERO : request.amount(), "UNPAID", "PENDING_PAYMENT",
                null, null, null, null, null, urgency, null, null, 0,
                null, null, java.time.LocalDateTime.now(), null, null));
    }

    public List<MedicalOrder> list(String type, String status, String patientId, String appointmentId) {
        return repository.find(type == null ? null : requireType(type), status, patientId, appointmentId);
    }

    public List<MedicalOrder> listAuthorized(String type, String status, String patientId, String appointmentId, String actorId, String role) {
        String forcedType = switch (role) {
            case "CHECK_DOCTOR" -> "CHECK";
            case "LAB_DOCTOR" -> "LAB";
            case "DISPOSAL_DOCTOR" -> "DISPOSAL";
            default -> type;
        };
        List<MedicalOrder> orders = list(forcedType, status, patientId, appointmentId);
        if (TECH_ROLES.contains(role)) {
            return repository.technician(actorId)
                    .map(t -> orders.stream().filter(o -> t.workspaceId().equals(o.executorId())).toList())
                    .orElse(List.of());
        }
        if ("OUTPATIENT_DOCTOR".equals(role)) {
            return orders.stream().filter(order -> actorId.equals(order.orderingDoctorId())).toList();
        }
        return orders;
    }

    @Transactional
    public MedicalOrder pay(String id, String actorId, String role) {
        MedicalOrder order = get(id);
        if ("PATIENT".equals(role) && !order.patientId().equals(actorId)) {
            throw new AccessDeniedException("患者只能支付自己的医技申请");
        }
        if (!repository.markPaid(id)) {
            throw new IllegalStateException("医技申请不存在或当前状态不可缴费");
        }
        MedicalOrder paid = get(id);
        List<MedicalOrderRepository.ExecutorCandidate> candidates =
                repository.executorCandidates(paid.orderType(), paid.projectCode());
        AiTriageClient.TriageResult triage = triageClient.triage(paid, candidates);
        if (!repository.assign(id, triage.executorId(), triage.executorName(), triage.location(),
                triage.equipmentId(), triage.source(), triage.reasons())) {
            throw new IllegalStateException("医技分诊状态已变化");
        }
        return get(id);
    }

    @Transactional
    public MedicalOrder miss(String id, String actorId, String role) {
        MedicalOrder order = get(id);
        validateExecutor(order.orderType(), role);
        MedicalOrderRepository.MedicalTechnician technician = technician(actorId);
        if (!technician.workspaceId().equals(order.executorId())) {
            throw new AccessDeniedException("只能操作自己执行房间的队列");
        }
        repository.moveToTail(id, technician.workspaceId());
        return get(id);
    }

    @Transactional
    public MedicalOrder start(String id, String executorId, String role) {
        MedicalOrder order = get(id);
        validateExecutor(order.orderType(), role);
        MedicalOrderRepository.MedicalTechnician technician = technician(executorId);
        if (!technician.workspaceId().equals(order.executorId())) {
            throw new AccessDeniedException("医技单未分配给当前执行房间");
        }
        if (!repository.start(id, technician.workspaceId(), technician.id(), technician.name())) {
            throw new IllegalStateException("只有已缴费待执行医技单可以开始");
        }
        return get(id);
    }

    @Transactional
    public MedicalOrder complete(String id, String executorId, String role, String resultData, String summary,
            String createdByType, String aiRecordId) {
        MedicalOrder order = get(id);
        validateExecutor(order.orderType(), role);
        String json = resultData == null || resultData.isBlank() ? "{}" : resultData;
        String source = createdByType == null || createdByType.isBlank() ? "HUMAN" : createdByType.toUpperCase();
        if (!source.equals("HUMAN") && !source.equals("AI")) {
            throw new IllegalArgumentException("createdByType 必须为 HUMAN 或 AI");
        }
        if (source.equals("AI") && (aiRecordId == null || aiRecordId.isBlank())) {
            throw new IllegalArgumentException("AI 生成结果必须关联 aiRecordId");
        }
        MedicalOrderRepository.MedicalTechnician technician = technician(executorId);
        if (!repository.complete(id, technician.workspaceId(), technician.id(), json, summary, source, aiRecordId)) {
            throw new IllegalStateException("医技单未开始、已完成或执行房间不匹配");
        }
        return get(id);
    }

    private MedicalOrder get(String id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("医技申请不存在"));
    }

    private MedicalOrderRepository.MedicalTechnician technician(String actorId) {
        return repository.technician(actorId).orElseThrow(() ->
                new AccessDeniedException("当前医技人员未绑定执行房间"));
    }

    private void validateExecutor(String type, String role) {
        if (!EXECUTOR_ROLE.get(type).equals(role)) {
            throw new IllegalStateException("当前医生角色不能执行该类医技单");
        }
    }

    private String requireType(String type) {
        if (!EXECUTOR_ROLE.containsKey(type)) {
            throw new IllegalArgumentException("orderType 必须为 CHECK、LAB 或 DISPOSAL");
        }
        return type;
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }
}
