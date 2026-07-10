package com.cloudbrain.medicalorder.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.repository.MedicalOrderRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AiTriageClientTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void triageReturnsAiResultWhenProviderResponds() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/ai/triage", this::handleSuccess);
        server.start();

        AiTriageClient client = new AiTriageClient("http://127.0.0.1:" + server.getAddress().getPort());
        AiTriageClient.TriageResult result = client.triage(order(), List.of(candidate("room-1", 2), candidate("room-2", 5)));

        assertThat(result.roomId()).isEqualTo("room-1");
        assertThat(result.source()).isEqualTo("AI");
        assertThat(result.reasons()).contains("match");
    }

    @Test
    void triageFallsBackToLowestLoadRoomWhenAiFails() {
        AiTriageClient client = new AiTriageClient("http://127.0.0.1:1");

        AiTriageClient.TriageResult result = client.triage(order(), List.of(candidate("room-1", 5), candidate("room-2", 1)));

        assertThat(result.roomId()).isEqualTo("room-2");
        assertThat(result.source()).isEqualTo("RULE");
    }

    private void handleSuccess(HttpExchange exchange) throws IOException {
        byte[] response = "{\"doctorId\":\"room-1\",\"reasons\":[\"match\",\"low-load\"]}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private MedicalOrder order() {
        return new MedicalOrder("order-1", "appt-1", "patient-1", "Patient", "doctor-1", "CHECK", "CT", "CT", "purpose", "HEAD",
                BigDecimal.TEN, "PAID", "WAITING_TRIAGE", null, null, null, null, null, "ROUTINE", null, null, 0, null, null, null, null, null,
                LocalDateTime.now(), null, null);
    }

    private MedicalOrderRepository.RoomCandidate candidate(String id, int load) {
        return new MedicalOrderRepository.RoomCandidate(id, "Room " + id, "CT,MR", "Floor 1", "CT-1,MR-1", 10, load);
    }
}
