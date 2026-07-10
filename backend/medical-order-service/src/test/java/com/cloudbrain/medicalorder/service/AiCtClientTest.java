package com.cloudbrain.medicalorder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private String requestPath;
    private String receivedContentType;
    private String receivedCaller;
    private String receivedDeclaredBodyBytes;
    private String receivedBody;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
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
                "澶撮",
                "鎬ヨ瘖澶寸棝");

        assertThat(response).containsEntry("taskId", "ct-test");
        assertThat(receivedContentType).contains("application/json");
        assertThat(receivedCaller).isEqualTo("medical-order-service");
        assertThat(receivedDeclaredBodyBytes).isEqualTo(String.valueOf(receivedBody.getBytes(StandardCharsets.UTF_8).length));
        assertThat(receivedBody).contains("\"orderId\":\"order-1\"");
        assertThat(receivedBody).contains("\"objectKey\":\"orders/order-1/image.nii.gz\"");
        assertThat(receivedBody).contains("\"modality\":\"CT\"");
        assertThat(receivedBody).contains("\"bodyPart\":\"澶撮\"");
        assertThat(receivedBody).contains("\"clinicalContext\":\"鎬ヨ瘖澶寸棝\"");
    }

    @Test
    void submitUsesDefaultsWhenOptionalFieldsAreBlank() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/ai/ct-analysis", this::handleSubmit);
        server.start();

        AiCtClient client = new AiCtClient("http://127.0.0.1:" + server.getAddress().getPort() + "/", new ObjectMapper());
        client.submit("order-2", "orders/order-2/image.nii.gz", "   ", null);

        assertThat(receivedBody).contains("\"bodyPart\":\"HEAD\"");
        assertThat(receivedBody).contains("\"clinicalContext\":\"\"");
    }

    @Test
    void taskReadsEncodedTaskIdentifier() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/ai/tasks", this::handleTaskLookup);
        server.start();

        AiCtClient client = new AiCtClient("http://127.0.0.1:" + server.getAddress().getPort(), new ObjectMapper());
        Map<String, Object> response = client.task("task id/1");

        assertThat(response).containsEntry("status", "DONE");
        assertThat(requestPath).isEqualTo("/api/ai/tasks/task+id%2F1");
    }

    @Test
    void submitThrowsWhenAiServiceReturnsHttpError() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/ai/ct-analysis", exchange -> {
            byte[] response = "{\"error\":\"bad request\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        AiCtClient client = new AiCtClient("http://127.0.0.1:" + server.getAddress().getPort(), new ObjectMapper());

        assertThatThrownBy(() -> client.submit("order-3", "object-key", "HEAD", "context"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI service returned HTTP 400");
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

    private void handleTaskLookup(HttpExchange exchange) throws IOException {
        requestPath = exchange.getRequestURI().getRawPath();
        byte[] response = "{\"taskId\":\"task id/1\",\"status\":\"DONE\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
