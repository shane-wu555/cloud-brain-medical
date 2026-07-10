package com.cloudbrain.appointment.support;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TestHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final Queue<Response> responses = new ConcurrentLinkedQueue<>();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

    public TestHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new Handler());
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void enqueueJson(int status, String body) {
        responses.add(new Response(status, body, "application/json"));
    }

    public void enqueueText(int status, String body) {
        responses.add(new Response(status, body, "text/plain"));
    }

    public List<RecordedRequest> requests() {
        return new ArrayList<>(requests);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private final class Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String body = read(exchange.getRequestBody());
            Headers requestHeaders = new Headers();
            requestHeaders.putAll(exchange.getRequestHeaders());
            requests.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getQuery(),
                    requestHeaders,
                    body));

            Response response = responses.poll();
            if (response == null) {
                response = new Response(200, "{}", "application/json");
            }
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", response.contentType());
            exchange.sendResponseHeaders(response.status(), bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }

        private String read(InputStream input) throws IOException {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public record RecordedRequest(String method, String path, String query, Headers headers, String body) {}

    private record Response(int status, String body, String contentType) {}
}
