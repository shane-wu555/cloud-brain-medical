package com.cloudbrain.medicalorder.controller;

import com.cloudbrain.medicalorder.domain.*;
import java.util.List;

/**
 * Aggregate DTO returned by /api/medical-orders/workspace.
 * Combines the queue sidebar and optional order detail into one response.
 */
public record WorkspaceDto(
        List<MedicalOrder> orders,
        WorkspaceDetail detail) {

    /**
     * Full detail for a single selected order — everything the workbench needs.
     */
    public record WorkspaceDetail(
            MedicalOrder order,
            List<Specimen> specimens,
            List<LaboratoryResultItem> labResults,
            List<MedicalAttachment> attachments,
            MedicalReport report) {
    }
}
