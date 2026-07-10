package com.cloudbrain.medicalorder.service;

import com.cloudbrain.medicalorder.controller.WorkspaceDto;
import com.cloudbrain.medicalorder.controller.WorkspaceDto.WorkspaceDetail;
import com.cloudbrain.medicalorder.domain.*;
import com.cloudbrain.medicalorder.repository.LaboratoryWorkflowRepository;
import com.cloudbrain.medicalorder.repository.MedicalOrderRepository;
import com.cloudbrain.medicalorder.repository.MedicalReportRepository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * Aggregates all data needed by the medical-tech workbench into a single response
 * to eliminate the 5+ serial HTTP round trips when selecting an order.
 */
@Service
public class WorkspaceService {
    private final JdbcTemplate jdbc;
    private final MedicalOrderRepository orders;
    private final MedicalReportRepository reports;
    private final LaboratoryWorkflowRepository labWorkflow;

    public WorkspaceService(JdbcTemplate jdbc, MedicalOrderRepository orders,
            MedicalReportRepository reports, LaboratoryWorkflowRepository labWorkflow) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.reports = reports;
        this.labWorkflow = labWorkflow;
    }

    /**
     * Returns the full workbench workspace: queue + optional detail for the selected order.
     * Replaces 5+ separate HTTP calls with a single request.
     */
    public WorkspaceDto workspace(String staffId, String role, String orderId) {
        // 1) Queue sidebar — get all orders for this staff's room
        List<MedicalOrder> queue = orders.findByStaffRole(staffId, role);

        // 2) If an order is selected, aggregate its full detail in one go
        WorkspaceDetail detail = null;
        if (orderId != null && !orderId.isBlank()) {
            MedicalOrder selected = queue.stream()
                    .filter(o -> o.id().equals(orderId))
                    .findFirst()
                    .orElse(null);
            if (selected != null) {
                List<Specimen> specimens = "LAB".equals(selected.orderType())
                        ? labWorkflow.specimens(orderId)
                        : List.of();
                List<LaboratoryResultItem> labResults = "LAB".equals(selected.orderType())
                        ? labWorkflow.results(orderId)
                        : List.of();
                List<MedicalAttachment> attachments = reports.attachments(orderId);
                MedicalReport report = reports.reportByOrder(orderId).orElse(null);
                detail = new WorkspaceDetail(selected, specimens, labResults, attachments, report);
            }
        }

        return new WorkspaceDto(queue, detail);
    }
}
