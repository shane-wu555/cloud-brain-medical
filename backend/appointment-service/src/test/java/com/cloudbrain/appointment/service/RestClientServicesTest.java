package com.cloudbrain.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudbrain.appointment.support.TestHttpServer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RestClientServicesTest {
    @Test
    void cashierClientPostsPaymentAndRefund() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.enqueueJson(200, "{}");
            server.enqueueJson(200, "{}");
            CashierClient client = new CashierClient(server.baseUrl(), "secret");

            client.recordPayment(Map.of("businessId", "appt-1", "amount", 12));
            client.recordRefund(Map.of("businessId", "appt-1", "amount", 6));

            List<TestHttpServer.RecordedRequest> requests = server.requests();
            assertThat(requests).hasSize(2);
            assertThat(requests.get(0).method()).isEqualTo("POST");
            assertThat(requests.get(0).path()).isEqualTo("/api/internal/payments");
            assertThat(requests.get(0).headers().getFirst("X-Internal-Api-Key")).isEqualTo("secret");
            assertThat(requests.get(0).body()).contains("appt-1");
            assertThat(requests.get(1).path()).isEqualTo("/api/internal/refunds");
        }
    }

    @Test
    void medicalRecordClientCreatesInitialRecordAndReadsSavedFlag() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.enqueueJson(200, "{}");
            server.enqueueJson(200, "{\"saved\":true}");
            server.enqueueJson(200, "{\"saved\":false}");
            MedicalRecordClient client = new MedicalRecordClient(server.baseUrl(), "secret");

            client.createInitialRecord(Map.of("appointmentId", "appt-1"));
            boolean saved = client.isSaved("appt-1");
            boolean notSaved = client.isSaved("appt-2");

            List<TestHttpServer.RecordedRequest> requests = server.requests();
            assertThat(requests.get(0).path()).isEqualTo("/api/medical-records/initial");
            assertThat(requests.get(1).path()).isEqualTo("/api/medical-records/internal/appt-1/saved");
            assertThat(requests.get(2).path()).isEqualTo("/api/medical-records/internal/appt-2/saved");
            assertThat(saved).isTrue();
            assertThat(notSaved).isFalse();
        }
    }

    @Test
    void patientVerificationClientReadsVerificationOwnershipAndBinding() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.enqueueJson(200, "{\"realNameVerified\":true}");
            server.enqueueJson(200, "{\"owned\":true}");
            server.enqueueJson(200, "{\"hasBoundPatient\":true,\"boundPatientId\":\"patient-1\"}");
            server.enqueueJson(200, "{\"hasBoundPatient\":false}");
            PatientVerificationClient client = new PatientVerificationClient(server.baseUrl(), "secret");

            boolean verified = client.isVerified("patient-1");
            boolean owned = client.owns("account-1", "patient-1");
            boolean hasBoundPatient = client.hasBoundPatient("account-1");
            String boundPatientId = client.boundPatientId("account-2");

            List<TestHttpServer.RecordedRequest> requests = server.requests();
            assertThat(requests.get(0).path()).isEqualTo("/api/internal/patients/patient-1/verification");
            assertThat(requests.get(1).path()).isEqualTo("/api/internal/patients/patient-1/ownership");
            assertThat(requests.get(1).query()).contains("accountId=account-1");
            assertThat(requests.get(2).path()).isEqualTo("/api/internal/patients/accounts/account-1/binding");
            assertThat(requests.get(3).path()).isEqualTo("/api/internal/patients/accounts/account-2/binding");
            assertThat(verified).isTrue();
            assertThat(owned).isTrue();
            assertThat(hasBoundPatient).isTrue();
            assertThat(boundPatientId).isNull();
        }
    }
}
