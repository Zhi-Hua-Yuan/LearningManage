package com.spt.learningmanage.client.knowledge;

public record RestTransportResponse(int statusCode, String body, String requestId) {
}
