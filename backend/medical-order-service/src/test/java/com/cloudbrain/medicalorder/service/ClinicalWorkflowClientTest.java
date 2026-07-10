package com.cloudbrain.medicalorder.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.domain.MedicalReport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClinicalWorkflowClientTest {
    private HttpServer appointmentServer;
    private HttpServer recordServer;

    @AfterEach
    void tearDown() {
        if (appointmentServer != null) {
            appointmentServer.stop(0);
        }
        if (recordServer != null) {
            recordServer.stop(0);
        }
    }

    @Test
    void publishCallsBothDownstreamSystemsAndSwallowsFailures() throws Exception {
        AtomicInteger recordCalls = new AtomicInteger();
        AtomicInteger appointmentCalls = new AtomicInteger();
        recordServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        appointmentServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        recordServer.createContext("/api/medical-records/internal/appt-1/reports", exchange -> replyNoContent(exchange, recordCalls));
        appointmentServer.createContext("/api/internal/appointments/appt-1/revisit", exchange -> replyNoContent(exchange, appointmentCalls));
        recordServer.start();
        appointmentServer.start();

        ClinicalWorkflowClient client = new ClinicalWorkflowClient(
                "http://127.0.0.1:" + appointmentServer.getAddress().getPort(),
                "http://127.0.0.1:" + recordServer.getAddress().getPort(),
                "internal-key");

        client.publish(order(), report());

        assertThat(recordCalls.get()).isEqualTo(1);
        assertThat(appointmentCalls.get()).isEqualTo(1);

        ClinicalWorkflowClient failingClient = new ClinicalWorkflowClient("http://127.0.0.1:1", "http://127.0.0.1:2", "internal-key");
        failingClient.publish(order(), report());
    }

    private void replyNoContent(HttpExchange exchange, AtomicInteger counter) throws IOException {
        counter.incrementAndGet();
        assertThat(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key")).isEqualTo("internal-key");
        byte[] body = exchange.getRequestBody().readAllBytes();
        if (body.length > 0) {
            assertThat(new String(body, StandardCharsets.UTF_8)).contains("reportId");
        }
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private MedicalOrder order() {
        return new MedicalOrder("order-1", "appt-1", "patient-1", "Patient", "doctor-1", "CHECK", "ITEM", "Item", "purpose", "HEAD",
                BigDecimal.TEN, "PAID", "COMPLETED", "room-1", "Room 1", "Floor 1", "staff-1", 1, "ROUTINE", null, null, 0, null, null, null, null, null,
                LocalDateTime.now(), null, LocalDateTime.now());
    }

    private MedicalReport report() {
        return new MedicalReport("report-1", "order-1", "CHECK", "CONFIRMED", "findings", "conclusion", "advice", "HUMAN", null, null, null, false,
                "doctor-1", LocalDateTime.now(), null, LocalDateTime.now());
    }
}
