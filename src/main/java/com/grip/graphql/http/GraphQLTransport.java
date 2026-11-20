package com.grip.graphql.http;

import com.google.gson.JsonObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public enum GraphQLTransport {

    POST_JSON("POST with a JSON body", "POST", "application/json"),
    GET("GET with a query parameter", "GET", null),
    POST_FORM("POST with a form encoded body", "POST", "application/x-www-form-urlencoded");

    private final String description;
    private final String method;
    private final String contentType;

    GraphQLTransport(String description, String method, String contentType) {
        this.description = description;
        this.method = method;
        this.contentType = contentType;
    }

    public String description() {
        return description;
    }

    public String method() {
        return method;
    }

    public String contentType() {
        return contentType;
    }

    public boolean carriesBody() {
        return contentType != null;
    }

    public String url(String endpoint, String query) {
        if (this != GET) {
            return endpoint;
        }
        return endpoint + (endpoint.contains("?") ? "&" : "?") + "query=" + encode(query);
    }

    public String body(String query) {
        return switch (this) {
            case POST_JSON -> jsonBody(query);
            case GET -> "";
            case POST_FORM -> "query=" + encode(query);
        };
    }

    @Override
    public String toString() {
        return description;
    }

    private static String jsonBody(String query) {
        JsonObject body = new JsonObject();
        body.addProperty("query", query == null ? "" : query);
        return body.toString();
    }

    private static String encode(String query) {
        return URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8);
    }
}
