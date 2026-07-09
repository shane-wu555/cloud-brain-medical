package com.cloudbrain.cashier.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PrescriptionPaymentClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private String requestPath;
    private String requestBody;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void confirmPostsPrescriptionPaymentConfirmation() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/prescriptions/pres-1/payment-confirmation", this::handle);
        server.start();

        PrescriptionPaymentClient client = new PrescriptionPaymentClient(baseUrl(), "internal-key");
        client.confirm("pres-1", "patient-1", "payment-1");

        assertThat(requestPath).isEqualTo("/api/internal/prescriptions/pres-1/payment-confirmation");
        assertThat(objectMapper.readValue(requestBody, Map.class))
                .containsEntry("patientId", "patient-1")
                .containsEntry("paymentOrderId", "payment-1");
    }

    @Test
    void completeDrugReturnPostsRefundCompletionPayload() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/drug-returns/return-1/refund-completion", this::handle);
        server.start();

        PrescriptionPaymentClient client = new PrescriptionPaymentClient(baseUrl(), "internal-key");
        client.completeDrugReturn("return-1", "cashier-1", "refund-1");

        assertThat(requestPath).isEqualTo("/api/internal/drug-returns/return-1/refund-completion");
        assertThat(objectMapper.readValue(requestBody, Map.class))
                .containsEntry("cashierId", "cashier-1")
                .containsEntry("refundOrderId", "refund-1");
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestPath = exchange.getRequestURI().getPath();
        requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
