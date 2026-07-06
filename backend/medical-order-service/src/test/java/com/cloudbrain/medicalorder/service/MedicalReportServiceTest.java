package com.cloudbrain.medicalorder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalorder.audit.AuditPublisher;
import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.domain.MedicalReport;
import com.cloudbrain.medicalorder.repository.MedicalOrderRepository;
import com.cloudbrain.medicalorder.repository.MedicalReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MedicalReportServiceTest {
    private final MedicalReportRepository reports = mock(MedicalReportRepository.class);
    private final MedicalOrderRepository orders = mock(MedicalOrderRepository.class);
    private final MedicalOrderService orderService = mock(MedicalOrderService.class);
    private final MinioStorageService storage = mock(MinioStorageService.class);
    private final AiCtClient ai = mock(AiCtClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ClinicalWorkflowClient workflow = mock(ClinicalWorkflowClient.class);
    private final PatientAccessClient patientAccessClient = mock(PatientAccessClient.class);
    private final AuditPublisher auditPublisher = mock(AuditPublisher.class);

    private final MedicalReportService service = new MedicalReportService(
            reports,
            orders,
            orderService,
            storage,
            ai,
            mapper,
            workflow,
            patientAccessClient,
            auditPublisher);

    @Test
    void reportListAuditsAsSingleAggregatedAccess() {
        MedicalReport report1 = report("report-1", "order-1");
        MedicalReport report2 = report("report-2", "order-2");
        when(reports.reports()).thenReturn(List.of(report1, report2));
        when(orders.findById("order-1")).thenReturn(Optional.of(order("order-1", "doctor-1", "patient-1")));
        when(orders.findById("order-2")).thenReturn(Optional.of(order("order-2", "doctor-1", "patient-2")));

        List<MedicalReport> visible = service.list(null, "doctor-1", "OUTPATIENT_DOCTOR");

        assertThat(visible).containsExactly(report1, report2);
        verify(auditPublisher).publish(
                "MEDICAL_REPORT_LIST_VIEW",
                "MEDICAL_REPORT",
                null,
                null,
                null,
                "doctor-1",
                "OUTPATIENT_DOCTOR",
                Map.of("accessScope", "LIST", "resultCount", 2));
    }

    private MedicalReport report(String id, String orderId) {
        return new MedicalReport(
                id,
                orderId,
                "CHECK",
                "CONFIRMED",
                "findings",
                "conclusion",
                "advice",
                "HUMAN",
                null,
                null,
                null,
                false,
                "doctor-1",
                LocalDateTime.now(),
                null,
                LocalDateTime.now());
    }

    private MedicalOrder order(String id, String doctorId, String patientId) {
        return new MedicalOrder(
                id,
                "appointment-" + id,
                patientId,
                "patient",
                doctorId,
                "CHECK",
                "CT",
                "CT 检查",
                "purpose",
                "body",
                BigDecimal.TEN,
                "PAID",
                "COMPLETED",
                "room-1",
                "room",
                "location",
                "executor-1",
                1,
                "ROUTINE",
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                null,
                null);
    }
}
