package com.cloudbrain.medicalorder.service;

import com.cloudbrain.medicalorder.controller.LaboratoryWorkflowController;
import com.cloudbrain.medicalorder.domain.LaboratoryResultItem;
import com.cloudbrain.medicalorder.domain.Specimen;
import com.cloudbrain.medicalorder.repository.LaboratoryWorkflowRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LaboratoryWorkflowService {
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "REQUESTED", Set.of("COLLECTED", "DISCARDED"),
            "COLLECTED", Set.of("RECEIVED", "DISCARDED"),
            "RECEIVED", Set.of("ANALYZING", "DISCARDED"),
            "ANALYZING", Set.of("REVIEWED", "DISCARDED"),
            "REVIEWED", Set.of("EXHAUSTED", "DISCARDED"));
    private final LaboratoryWorkflowRepository repository;

    public LaboratoryWorkflowService(LaboratoryWorkflowRepository repository) { this.repository = repository; }

    @Transactional
    public Specimen create(String orderId, String type, String barcode) {
        if (!repository.isLabOrder(orderId)) throw new IllegalArgumentException("只有检验医嘱可以登记样本");
        require(type, "specimenType");
        require(barcode, "barcode");
        return repository.createSpecimen(orderId, type, barcode);
    }

    public List<Specimen> specimens(String orderId) { return repository.specimens(orderId); }

    @Transactional
    public Specimen transition(String specimenId, String nextStatus, String actorId, String reason) {
        Specimen specimen = repository.specimen(specimenId).orElseThrow(() -> new IllegalArgumentException("样本不存在"));
        String next = nextStatus == null ? "" : nextStatus.toUpperCase();
        if (!TRANSITIONS.getOrDefault(specimen.status(), Set.of()).contains(next)) {
            throw new IllegalStateException("样本不能从 " + specimen.status() + " 流转到 " + next);
        }
        if (next.equals("DISCARDED") && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("废弃样本必须填写原因");
        }
        if (!repository.transition(specimenId, specimen.status(), next, actorId, reason)) {
            throw new IllegalStateException("样本状态已变化，请刷新后重试");
        }
        return repository.specimen(specimenId).orElseThrow();
    }

    @Transactional
    public List<LaboratoryResultItem> saveResults(String orderId, String specimenId,
                                                  List<LaboratoryWorkflowController.ResultItemRequest> items,
                                                  String confirmerId) {
        Specimen specimen = repository.specimen(specimenId).orElseThrow(() -> new IllegalArgumentException("样本不存在"));
        if (!specimen.medicalOrderId().equals(orderId)) throw new IllegalArgumentException("样本不属于当前检验医嘱");
        if (!Set.of("ANALYZING", "REVIEWED").contains(specimen.status())) throw new IllegalStateException("样本未进入分析阶段");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("检验结果明细不能为空");
        for (LaboratoryWorkflowController.ResultItemRequest item : items) {
            require(item.itemCode(), "itemCode"); require(item.itemName(), "itemName"); require(item.resultValue(), "resultValue");
            String source = item.createdByType() == null ? "HUMAN" : item.createdByType().toUpperCase();
            if (!Set.of("HUMAN", "AI").contains(source)) throw new IllegalArgumentException("createdByType 必须为 HUMAN 或 AI");
            if (source.equals("AI") && (item.aiRecordId() == null || item.aiRecordId().isBlank())) {
                throw new IllegalArgumentException("AI 生成检验结果必须关联 aiRecordId");
            }
            repository.upsertResult(orderId, specimenId, item.itemCode(), item.itemName(), item.resultValue(),
                    item.unit(), item.referenceRange(), normalizeFlag(item.abnormalFlag()), source, item.aiRecordId(), confirmerId);
        }
        return repository.results(orderId);
    }

    public List<LaboratoryResultItem> results(String orderId) { return repository.results(orderId); }
    private void require(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " 不能为空"); }

    private String normalizeFlag(String flag) {
        if (flag == null || flag.isBlank()) return "NORMAL";
        String value = flag.trim().toUpperCase();
        return switch (value) {
            case "HIGH", "H", "↑", "偏高" -> "HIGH";
            case "LOW", "L", "↓", "偏低" -> "LOW";
            case "CRITICAL", "C", "危急", "危急值" -> "CRITICAL";
            case "NORMAL", "N", "正常" -> "NORMAL";
            default -> throw new IllegalArgumentException("异常标志必须为 NORMAL、HIGH、LOW 或 CRITICAL");
        };
    }
}
