package com.cloudbrain.medicalorder.service;

import com.cloudbrain.medicalorder.controller.MedicalOrderController;
import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.audit.AuditPublisher;
import com.cloudbrain.medicalorder.repository.MedicalOrderRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
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
    private final AuditPublisher auditPublisher;
    private final NotificationClient notificationClient;

    public MedicalOrderService(MedicalOrderRepository repository, AiTriageClient triageClient,
            AuditPublisher auditPublisher, NotificationClient notificationClient) {
        this.repository = repository;
        this.triageClient = triageClient;
        this.auditPublisher = auditPublisher;
        this.notificationClient = notificationClient;
    }

    @Transactional
    public MedicalOrder create(MedicalOrderController.CreateRequest request, String doctorId) {
        String type = requireType(request.orderType());
        require(request.appointmentId(), "appointmentId");
        require(request.patientId(), "patientId");
        require(request.itemCode(), "itemCode");
        require(request.itemName(), "itemName");
        String urgency = request.urgency() == null ? "ROUTINE" : request.urgency().toUpperCase();
        if (!Set.of("ROUTINE", "EMERGENCY").contains(urgency)) {
            throw new IllegalArgumentException("urgency 必须为 ROUTINE 或 EMERGENCY");
        }
        if (repository.existsActiveOrder(request.appointmentId(), request.itemCode())) {
            throw new IllegalStateException("该检查/检验项目已在本次就诊中开单，不可重复申请：" + request.itemName());
        }
        MedicalOrder order = repository.create(new MedicalOrder(
                UUID.randomUUID().toString(),
                request.appointmentId(), request.patientId(), request.patientName(), doctorId,
                type, request.itemCode(), request.itemName(), request.purpose(), request.bodyPart(),
                request.amount() == null ? BigDecimal.ZERO : request.amount(),
                "UNPAID", "PENDING_PAYMENT",
                null, null, null, null, null, urgency, null, null, 0,
                null, null, null, null, null,
                java.time.LocalDateTime.now(), null, null));
        try {
            notificationClient.notify(order.patientId(), "PENDING_PAYMENT",
                    "医生已开具" + order.itemName() + "，请缴费", null,
                    "MEDICAL_ORDER", order.id());
        } catch (Exception ignored) { /* notification failure must not fail the transaction */ }
        return order;
    }

    public List<MedicalOrder> list(String type, String status, String patientId, String appointmentId) {
        return repository.find(type == null ? null : requireType(type), status, patientId, appointmentId);
    }

    public List<MedicalOrder> listAuthorized(String type, String status, String patientId,
            String appointmentId, String view, String actorId, String role) {
        String forcedType = switch (role) {
            case "CHECK_DOCTOR"    -> "CHECK";
            case "LAB_DOCTOR"     -> "LAB";
            case "DISPOSAL_DOCTOR"-> "DISPOSAL";
            default -> type;
        };
        List<MedicalOrder> orders = list(forcedType, status, patientId, appointmentId);
        if (TECH_ROLES.contains(role)) {
            List<MedicalOrder> queriedOrders = orders;
            orders = repository.staffRoom(actorId)
                    .map(sr -> queriedOrders.stream().filter(o -> sr.roomId().equals(o.roomId())).toList())
                    .orElse(List.of());
        } else if ("OUTPATIENT_DOCTOR".equals(role)) {
            orders = orders.stream().filter(o -> actorId.equals(o.orderingDoctorId())).toList();
        }
        String normalizedView = normalizeView(view);
        orders = filterByView(orders, normalizedView);
        auditListView(forcedType, status, patientId, appointmentId, normalizedView, actorId, role, orders);
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
        List<MedicalOrderRepository.RoomCandidate> candidates =
                repository.roomCandidates(paid.orderType(), paid.itemCode());
        AiTriageClient.TriageResult triage = triageClient.triage(paid, candidates);
        if (!repository.assign(id, triage.roomId(), triage.source(), triage.reasons())) {
            throw new IllegalStateException("医技分诊状态已变化");
        }
        MedicalOrder afterPay = get(id);
        try {
            notificationClient.markReferenceRead(afterPay.patientId(), "PENDING_PAYMENT",
                    "MEDICAL_ORDER", afterPay.id());
            notificationClient.notify(afterPay.patientId(), arrangementCategory(afterPay.orderType()),
                    "缴费成功：" + afterPay.itemName(), null,
                    "MEDICAL_ORDER", afterPay.id());
        } catch (Exception ignored) { /* notification failure must not fail the transaction */ }
        return afterPay;
    }

    @Transactional
    public MedicalOrder miss(String id, String actorId, String role) {
        MedicalOrder order = get(id);
        validateExecutor(order.orderType(), role);
        MedicalOrderRepository.StaffRoom staffRoom = staffRoom(actorId);
        if (!staffRoom.roomId().equals(order.roomId())) {
            throw new AccessDeniedException("只能操作自己执行房间的队列");
        }
        repository.moveToTail(id, staffRoom.roomId());
        return get(id);
    }

    @Transactional
    public MedicalOrder call(String id, String actorId, String role) {
        MedicalOrder order = get(id);
        validateExecutor(order.orderType(), role);
        MedicalOrderRepository.StaffRoom staffRoom = staffRoom(actorId);
        if (!staffRoom.roomId().equals(order.roomId())) {
            throw new AccessDeniedException("医技单未分配给当前执行房间");
        }
        if (!repository.call(id, staffRoom.roomId())) {
            throw new IllegalStateException("只有待执行医技单可以叫号");
        }
        MedicalOrder called = get(id);
        try {
            String roomLabel = called.roomName() != null ? called.roomName() : "检查室";
            String location = called.roomLocation() != null ? "（" + called.roomLocation() + "）" : "";
            notificationClient.notify(called.patientId(), "CALLED",
                    "您的" + called.itemName() + "已叫号，请前往" + roomLabel + location, null,
                    "MEDICAL_ORDER", called.id());
        } catch (Exception ignored) { /* notification failure must not fail the transaction */ }
        return called;
    }

    @Transactional
    public MedicalOrder start(String id, String actorId, String role) {
        MedicalOrder order = get(id);
        validateExecutor(order.orderType(), role);
        MedicalOrderRepository.StaffRoom staffRoom = staffRoom(actorId);
        if (!staffRoom.roomId().equals(order.roomId())) {
            throw new AccessDeniedException("医技单未分配给当前执行房间");
        }
        if (!repository.start(id, staffRoom.roomId(), staffRoom.staffId())) {
            throw new IllegalStateException("只有已分诊待执行的医技单可以开始");
        }
        return get(id);
    }

    @Transactional
    public MedicalOrder complete(String id, String actorId, String role, String summary,
            String createdByType, String aiRecordId) {
        MedicalOrder order = get(id);
        validateExecutor(order.orderType(), role);
        String source = createdByType == null || createdByType.isBlank() ? "HUMAN"
                : createdByType.toUpperCase();
        if (!source.equals("HUMAN") && !source.equals("AI")) {
            throw new IllegalArgumentException("createdByType 必须为 HUMAN 或 AI");
        }
        if (source.equals("AI") && (aiRecordId == null || aiRecordId.isBlank())) {
            throw new IllegalArgumentException("AI 生成结果必须关联 aiRecordId");
        }
        MedicalOrderRepository.StaffRoom staffRoom = staffRoom(actorId);
        if (!repository.complete(id, staffRoom.roomId(), staffRoom.staffId(), summary, source, aiRecordId)) {
            throw new IllegalStateException("医技单未开始、已完成或执行房间不匹配");
        }
        MedicalOrder completed = get(id);
        markArrangementRead(completed);
        return completed;
    }

    @Transactional
    public MedicalOrder markReportPending(String id, String actorId, String role, String summary) {
        MedicalOrder order = get(id);
        validateExecutor(order.orderType(), role);
        MedicalOrderRepository.StaffRoom staffRoom = staffRoom(actorId);
        if (!staffRoom.roomId().equals(order.roomId())) {
            throw new AccessDeniedException("医技单未分配给当前执行房间");
        }
        String text = summary == null || summary.isBlank() ? "患者执行已完成，待发布正式报告" : summary;
        if (!repository.markReportPending(id, staffRoom.roomId(), staffRoom.staffId(), text)) {
            throw new IllegalStateException("只有执行中的医技单可以标记为待报告");
        }
        MedicalOrder updated = get(id);
        markArrangementRead(updated);
        return updated;
    }

    private void markArrangementRead(MedicalOrder order) {
        try {
            notificationClient.markReferenceRead(order.patientId(), arrangementCategory(order.orderType()),
                    "MEDICAL_ORDER", order.id());
        } catch (Exception ignored) { /* notification failure must not fail the transaction */ }
    }

    private static String arrangementCategory(String orderType) {
        return "DISPOSAL".equals(orderType) ? "DISPOSAL_ARRANGEMENT" : "EXAM_ARRANGEMENT";
    }

    private MedicalOrder get(String id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("医技申请不存在"));
    }

    private MedicalOrderRepository.StaffRoom staffRoom(String actorId) {
        return repository.staffRoom(actorId).orElseThrow(() ->
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

    private static List<MedicalOrder> filterByView(List<MedicalOrder> orders, String view) {
        return switch (view) {
            case "DISPOSAL_ARRANGEMENT" -> orders.stream()
                    .filter(order -> "UNPAID".equals(order.paymentStatus())
                            || !Set.of("COMPLETED", "MISSED").contains(order.status()))
                    .toList();
            case "DISPOSAL_RECORD" -> orders.stream()
                    .filter(order -> Set.of("COMPLETED", "MISSED").contains(order.status()))
                    .toList();
            default -> orders;
        };
    }

    private void auditListView(String type, String status, String patientId, String appointmentId,
            String view, String actorId, String role, List<MedicalOrder> orders) {
        int resultCount = orders.size();
        long disposalCount = orders.stream().filter(order -> "DISPOSAL".equals(order.orderType())).count();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("accessScope", "LIST");
        if (type != null && !type.isBlank()) {
            details.put("typeFilter", type);
        }
        if (status != null && !status.isBlank()) {
            details.put("statusFilter", status);
        }
        if (appointmentId != null && !appointmentId.isBlank()) {
            details.put("appointmentId", appointmentId);
        }
        if (!view.isBlank()) {
            details.put("view", view);
            details.put("auditSummary", auditSummary(type, view));
        }
        if (Set.of("OUTPATIENT_PAYMENT", "PAYMENT_RECORD").contains(view) && disposalCount > 0) {
            details.put("relatedDisposalCount", disposalCount);
        }
        details.put("resultCount", resultCount);
        auditPublisher.publish(
                "MEDICAL_ORDER_LIST_VIEW",
                "MEDICAL_ORDER",
                null,
                patientId,
                appointmentId,
                actorId,
                role,
                details);
    }

    private static String auditSummary(String type, String view) {
        return switch (view) {
            case "DISPOSAL_ARRANGEMENT" -> "查看了待处置安排";
            case "DISPOSAL_RECORD" -> "查看了处置记录";
            case "OUTPATIENT_PAYMENT" -> "查看了门诊缴费项目（含处置信息）";
            case "PAYMENT_RECORD" -> "查看了缴费退费记录（含处置信息）";
            default -> "DISPOSAL".equals(type) ? "查看了处置列表" : "查看了医技医嘱列表";
        };
    }

    private static String normalizeView(String value) {
        return value == null || value.isBlank() ? "" : value.trim().toUpperCase();
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }
}
