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

class AppointmentPaymentClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private String requestPath;
    private String apiKey;
    private String requestBody;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void confirmPostsPaymentConfirmationPayload() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/appointments/appt-1/payment-confirmation", this::handle);
        server.start();

        AppointmentPaymentClient client = new AppointmentPaymentClient(baseUrl(), "internal-key");
        client.confirm("appt-1", "patient-1", "WECHAT_TEST", "payment-1");

        assertThat(requestPath).isEqualTo("/api/internal/appointments/appt-1/payment-confirmation");
        assertThat(apiKey).isEqualTo("internal-key");
        assertThat(objectMapper.readValue(requestBody, Map.class)).containsEntry("patientId", "patient-1");
    }

    @Test
    void refundPostsRefundConfirmationPayload() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/appointments/appt-2/refund-confirmation", this::handle);
        server.start();

        AppointmentPaymentClient client = new AppointmentPaymentClient(baseUrl(), "internal-key");
        client.refund("appt-2", "patient-2", "cashier-1");

        assertThat(requestPath).isEqualTo("/api/internal/appointments/appt-2/refund-confirmation");
        assertThat(objectMapper.readValue(requestBody, Map.class))
                .containsEntry("patientId", "patient-2")
                .containsEntry("operatorId", "cashier-1");
    }

    @Test
    void failPostsPaymentFailurePayload() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/appointments/appt-3/payment-failure", this::handle);
        server.start();

        AppointmentPaymentClient client = new AppointmentPaymentClient(baseUrl(), "internal-key");
        client.fail("appt-3", "patient-3", "payment-3");

        assertThat(requestPath).isEqualTo("/api/internal/appointments/appt-3/payment-failure");
        assertThat(objectMapper.readValue(requestBody, Map.class))
                .containsEntry("patientId", "patient-3")
                .containsEntry("paymentOrderId", "payment-3");
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestPath = exchange.getRequestURI().getPath();
        apiKey = exchange.getRequestHeaders().getFirst("X-Internal-Api-Key");
        requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
