package com.spt.learningmanage.client.knowledge;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

public class KnowledgeRestTransport {

    private final RestClient restClient;
    private final String apiKey;

    public KnowledgeRestTransport(String baseUrl, String apiKey, int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder().baseUrl(trimSlash(baseUrl)).requestFactory(requestFactory).build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public RestTransportResponse exchange(HttpMethod method, String path, String body, boolean bearerAuth) {
        try {
            return restClient.method(method)
                    .uri(path)
                    .headers(headers -> applyHeaders(headers, bearerAuth))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body == null ? "" : body)
                    .exchange((request, response) -> new RestTransportResponse(
                            response.getStatusCode().value(),
                            new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8),
                            firstHeader(response.getHeaders(), "x-request-id", "request-id")
                    ));
        } catch (Exception exception) {
            throw new TransportFailureException("Knowledge dependency HTTP request failed", exception);
        }
    }

    private void applyHeaders(HttpHeaders headers, boolean bearerAuth) {
        if (apiKey.isBlank()) {
            return;
        }
        if (bearerAuth) {
            headers.setBearerAuth(apiKey);
        } else {
            headers.set("api-key", apiKey);
        }
    }

    private static String firstHeader(HttpHeaders headers, String... names) {
        for (String name : names) {
            String value = headers.getFirst(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String trimSlash(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static class TransportFailureException extends RuntimeException {
        public TransportFailureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
