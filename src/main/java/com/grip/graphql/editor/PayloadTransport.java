package com.grip.graphql.editor;

import java.util.Locale;

enum PayloadTransport {

    JSON("POST with a JSON body", "sent as the JSON body, unchanged"),
    GET("GET with a query parameter", "URL encoded into the query string, with no body"),
    FORM("POST with a form encoded body", "URL encoded into the body"),
    MULTIPART("POST with a multipart body", "wrapped in a multipart operations part");

    private final String description;
    private final String sendsAs;

    PayloadTransport(String description, String sendsAs) {
        this.description = description;
        this.sendsAs = sendsAs;
    }

    String description() {
        return description;
    }

    String sendsAs() {
        return sendsAs;
    }

    boolean supportsBatch() {
        return this == JSON;
    }

    static PayloadTransport of(String method, String contentType) {
        if ("GET".equalsIgnoreCase(method)) {
            return GET;
        }

        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalized.contains("application/x-www-form-urlencoded")) {
            return FORM;
        }
        if (normalized.contains("multipart/form-data")) {
            return MULTIPART;
        }
        return JSON;
    }
}
