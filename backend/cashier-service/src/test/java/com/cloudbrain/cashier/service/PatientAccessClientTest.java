package com.cloudbrain.cashier.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PatientAccessClientTest {
    private HttpServer server;
    private String requestPath;
    private String requestQuery;
    private String apiKey;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void ownsReturnsTrueWhenResponseContainsOwnedTrue() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/patients/patient-1/ownership", exchange ->
                handle(exchange, "{\"owned\":true}"));
        server.start();

        PatientAccessClient client = new PatientAccessClient(baseUrl(), "internal-key");

        assertThat(client.owns("account-1", "patient-1")).isTrue();
        assertThat(requestPath).isEqualTo("/api/internal/patients/patient-1/ownership");
        assertThat(requestQuery).isEqualTo("accountId=account-1");
        assertThat(apiKey).isEqualTo("internal-key");
    }

    @Test
    void boundPatientIdReturnsNullWhenNoBindingExists() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/patients/accounts/account-1/binding", exchange ->
                handle(exchange, "{\"hasBoundPatient\":false}"));
        server.start();

        PatientAccessClient client = new PatientAccessClient(baseUrl(), "internal-key");

        assertThat(client.boundPatientId("account-1")).isNull();
    }

    @Test
    void boundPatientIdReturnsBoundIdWhenPresent() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/patients/accounts/account-1/binding", exchange ->
                handle(exchange, "{\"hasBoundPatient\":true,\"boundPatientId\":\"patient-9\"}"));
        server.start();

        PatientAccessClient client = new PatientAccessClient(baseUrl(), "internal-key");

        assertThat(client.boundPatientId("account-1")).isEqualTo("patient-9");
    }

    private void handle(HttpExchange exchange, String responseBody) throws IOException {
        requestPath = exchange.getRequestURI().getPath();
        requestQuery = exchange.getRequestURI().getQuery();
        apiKey = exchange.getRequestHeaders().getFirst("X-Internal-Api-Key");
        byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
