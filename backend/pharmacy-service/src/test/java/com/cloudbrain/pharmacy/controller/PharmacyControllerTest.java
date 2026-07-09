package com.cloudbrain.pharmacy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.pharmacy.entity.DrugReturnOrder;
import com.cloudbrain.pharmacy.entity.DrugReturnStatus;
import com.cloudbrain.pharmacy.entity.Prescription;
import com.cloudbrain.pharmacy.entity.PrescriptionItem;
import com.cloudbrain.pharmacy.entity.PrescriptionStatus;
import com.cloudbrain.pharmacy.repository.PharmacyRepository;
import com.cloudbrain.pharmacy.service.InventoryDemandForecastService;
import com.cloudbrain.pharmacy.service.PharmacyService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PharmacyControllerTest {
    @Mock
    PharmacyService service;

    @Mock
    InventoryDemandForecastService inventoryDemandForecastService;

    @Test
    void drugsDelegatesToService() {
        PharmacyController controller = controller();
        List<PharmacyRepository.Drug> drugs = List.of(drug());
        when(service.drugs("aspirin", "ROOM")).thenReturn(drugs);

        List<PharmacyRepository.Drug> result = controller.drugs("aspirin", "ROOM");

        assertThat(result).isSameAs(drugs);
    }

    @Test
    void reindexDrugSearchReturnsCount() {
        PharmacyController controller = controller();
        when(service.reindexDrugSearchIndex()).thenReturn(4);

        Map<String, Integer> result = controller.reindexDrugSearch();

        assertThat(result).isEqualTo(Map.of("indexed", 4));
    }

    @Test
    void stockInUsesAuthenticatedSubject() {
        PharmacyController controller = controller();
        PharmacyRepository.Drug drug = drug();
        when(service.addStock("drug-1", new PharmacyController.StockInRequest(5, "purchase"), "pharmacist-1"))
                .thenReturn(drug);

        PharmacyRepository.Drug result = controller.stockIn(
                "drug-1",
                new PharmacyController.StockInRequest(5, "purchase"),
                authentication("pharmacist-1", "PHARMACY_STAFF"));

        assertThat(result).isSameAs(drug);
    }

    @Test
    void inventoryForecastDelegatesToForecastService() {
        PharmacyController controller = controller();
        InventoryDemandForecastService.ForecastRun run = new InventoryDemandForecastService.ForecastRun(30, 14, 0, List.of());
        when(inventoryDemandForecastService.preview(30)).thenReturn(run);

        InventoryDemandForecastService.ForecastRun result = controller.inventoryForecast(30);

        assertThat(result).isSameAs(run);
    }

    @Test
    void prescribeUsesAuthenticatedDoctor() {
        PharmacyController controller = controller();
        PharmacyController.CreatePrescriptionRequest request = new PharmacyController.CreatePrescriptionRequest(
                "appt-1",
                "mr-1",
                "patient-1",
                "Alice",
                "diagnosis",
                null,
                "HUMAN_ONLY",
                null,
                List.of(new PharmacyController.PrescriptionItemRequest(
                        "drug-1", 1, "100mg", "oral", "daily", 7, null)));
        Prescription prescription = prescription();
        when(service.prescribe(request, "doctor-1")).thenReturn(prescription);

        Prescription result = controller.prescribe(request, authentication("doctor-1", "OUTPATIENT_DOCTOR"));

        assertThat(result).isSameAs(prescription);
    }

    @Test
    void prescriptionsForwardFiltersPagingAndRole() {
        PharmacyController controller = controller();
        List<Prescription> prescriptions = List.of(prescription());
        when(service.list(
                "patient-1",
                "WAITING_DISPENSE",
                "DISPENSE_ARRANGEMENT",
                "Alice",
                "RX-1",
                2,
                20,
                "staff-1",
                "PHARMACY_STAFF")).thenReturn(prescriptions);

        List<Prescription> result = controller.prescriptions(
                "patient-1",
                "WAITING_DISPENSE",
                "DISPENSE_ARRANGEMENT",
                "Alice",
                "RX-1",
                2,
                20,
                authentication("staff-1", "PHARMACY_STAFF"));

        assertThat(result).isSameAs(prescriptions);
    }

    @Test
    void prescriptionDelegatesDetailLookupWithAuthContext() {
        PharmacyController controller = controller();
        Prescription prescription = prescription();
        when(service.find("pres-1", "patient-account-1", "PATIENT")).thenReturn(prescription);

        Prescription result = controller.prescription("pres-1", authentication("patient-account-1", "PATIENT"));

        assertThat(result).isSameAs(prescription);
    }

    @Test
    void dispenseUsesAuthenticatedSubject() {
        PharmacyController controller = controller();
        Prescription prescription = prescription();
        when(service.dispense("pres-1", "pharmacist-1")).thenReturn(prescription);

        Prescription result = controller.dispense("pres-1", authentication("pharmacist-1", "PHARMACY_STAFF"));

        assertThat(result).isSameAs(prescription);
    }

    @Test
    void returnDrugsUsesRequestReasonAndAuthenticatedSubject() {
        PharmacyController controller = controller();
        Prescription prescription = prescription();
        when(service.returnDrugs("pres-1", "pharmacist-1", "damaged")).thenReturn(prescription);

        Prescription result = controller.returnDrugs(
                "pres-1",
                new PharmacyController.ReturnRequest("damaged"),
                authentication("pharmacist-1", "PHARMACY_STAFF"));

        assertThat(result).isSameAs(prescription);
    }

    @Test
    void createDrugReturnDelegatesToService() {
        PharmacyController controller = controller();
        PharmacyController.CreateDrugReturnRequest request =
                new PharmacyController.CreateDrugReturnRequest("approved", "template-1");
        DrugReturnOrder order = order();
        when(service.createDrugReturn("pres-1", request, "doctor-1")).thenReturn(order);

        DrugReturnOrder result = controller.createDrugReturn(
                "pres-1",
                request,
                authentication("doctor-1", "OUTPATIENT_DOCTOR"));

        assertThat(result).isSameAs(order);
    }

    @Test
    void drugReturnsForwardFiltersPagingAndRole() {
        PharmacyController controller = controller();
        List<DrugReturnOrder> orders = List.of(order());
        when(service.drugReturns(
                "patient-1",
                "RETURN_PENDING_REFUND",
                "Alice",
                "RX-1",
                "RT-1",
                1,
                10,
                "cashier-1",
                "CASHIER")).thenReturn(orders);

        List<DrugReturnOrder> result = controller.drugReturns(
                "patient-1",
                "RETURN_PENDING_REFUND",
                "Alice",
                "RX-1",
                "RT-1",
                1,
                10,
                authentication("cashier-1", "CASHIER"));

        assertThat(result).isSameAs(orders);
    }

    @Test
    void paymentConfirmationRejectsInvalidInternalApiKey() {
        PharmacyController controller = controller();

        assertThatThrownBy(() -> controller.paymentConfirmation(
                "pres-1",
                new PharmacyController.PaymentConfirmation("patient-1", "payment-1"),
                "wrong-key"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void paymentConfirmationDelegatesWhenInternalApiKeyMatches() {
        PharmacyController controller = controller();
        Prescription prescription = prescription();
        when(service.confirmPayment("pres-1", "patient-1", "payment-1")).thenReturn(prescription);

        Prescription result = controller.paymentConfirmation(
                "pres-1",
                new PharmacyController.PaymentConfirmation("patient-1", "payment-1"),
                "internal-key");

        assertThat(result).isSameAs(prescription);
    }

    @Test
    void refundCompletionDelegatesWhenInternalApiKeyMatches() {
        PharmacyController controller = controller();
        DrugReturnOrder order = order();
        when(service.completeDrugReturn("return-1", "cashier-1", "refund-1")).thenReturn(order);

        DrugReturnOrder result = controller.refundCompletion(
                "return-1",
                new PharmacyController.RefundCompletion("cashier-1", "refund-1"),
                "internal-key");

        assertThat(result).isSameAs(order);
        verify(service).completeDrugReturn("return-1", "cashier-1", "refund-1");
    }

    private PharmacyController controller() {
        return new PharmacyController(service, inventoryDemandForecastService, "internal-key");
    }

    private JwtAuthenticationToken authentication(String subject, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", subject)
                .claim("role", role)
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private PharmacyRepository.Drug drug() {
        return new PharmacyRepository.Drug(
                "drug-1",
                "DRUG-1",
                "Aspirin",
                "100mg",
                "box",
                BigDecimal.TEN,
                "tablet",
                "ROOM",
                50,
                10);
    }

    private Prescription prescription() {
        return new Prescription(
                "pres-1",
                "RX-1",
                "appt-1",
                "mr-1",
                "patient-1",
                "Alice",
                "doctor-1",
                "diagnosis",
                PrescriptionStatus.WAITING_DISPENSE,
                BigDecimal.TEN,
                "payment-1",
                null,
                "HUMAN_ONLY",
                null,
                LocalDateTime.of(2026, 7, 9, 10, 0),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new PrescriptionItem(
                        "item-1",
                        "pres-1",
                        "drug-1",
                        "Aspirin",
                        1,
                        "100mg",
                        "oral",
                        "daily",
                        7,
                        null,
                        BigDecimal.TEN,
                        BigDecimal.TEN)));
    }

    private DrugReturnOrder order() {
        return new DrugReturnOrder(
                "return-1",
                "RT-1",
                "pres-1",
                "RX-1",
                "patient-1",
                "Alice",
                "doctor-1",
                "approved",
                "template-1",
                DrugReturnStatus.RETURN_PENDING_REFUND,
                BigDecimal.TEN,
                null,
                null,
                "cashier-1",
                "refund-1",
                LocalDateTime.of(2026, 7, 9, 10, 0),
                null,
                null,
                List.of());
    }
}
