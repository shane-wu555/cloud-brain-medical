package com.cloudbrain.medicalorder.service;

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

class AiCtClientTest {
    private HttpServer server;
    private String receivedContentType;
    private String receivedCaller;
    private String receivedDeclaredBodyBytes;
    private String receivedBody;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void submitSendsJsonBodyToAiService() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/ai/ct-analysis", this::handleSubmit);
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        AiCtClient client = new AiCtClient(baseUrl, new ObjectMapper());

        Map<String, Object> response = client.submit(
                "order-1",
                "orders/order-1/image.nii.gz",
                "头颅",
                "急诊头痛");

        assertThat(response).containsEntry("taskId", "ct-test");
        assertThat(receivedContentType).contains("application/json");
        assertThat(receivedCaller).isEqualTo("medical-order-service");
        assertThat(receivedDeclaredBodyBytes).isEqualTo(String.valueOf(receivedBody.getBytes(StandardCharsets.UTF_8).length));
        assertThat(receivedBody).contains("\"orderId\":\"order-1\"");
        assertThat(receivedBody).contains("\"objectKey\":\"orders/order-1/image.nii.gz\"");
        assertThat(receivedBody).contains("\"modality\":\"CT\"");
        assertThat(receivedBody).contains("\"bodyPart\":\"头颅\"");
        assertThat(receivedBody).contains("\"clinicalContext\":\"急诊头痛\"");
    }

    private void handleSubmit(HttpExchange exchange) throws IOException {
        receivedContentType = exchange.getRequestHeaders().getFirst("Content-Type");
        receivedCaller = exchange.getRequestHeaders().getFirst("X-CloudBrain-Caller");
        receivedDeclaredBodyBytes = exchange.getRequestHeaders().getFirst("X-CloudBrain-Request-Body-Bytes");
        receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        byte[] response = "{\"taskId\":\"ct-test\",\"status\":\"QUEUED\",\"progress\":0}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(202, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
