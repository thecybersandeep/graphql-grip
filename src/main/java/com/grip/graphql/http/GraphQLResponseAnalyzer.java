package com.grip.graphql.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;

public final class GraphQLResponseAnalyzer {

    private GraphQLResponseAnalyzer() {
    }

    public static Analysis analyzeEndpointProbe(int statusCode, String contentType, String body) {
        return analyzeExpectedField(statusCode, contentType, body, "__typename");
    }

    public static Analysis analyzeIntrospectionResponse(int statusCode, String contentType, String body) {
        return analyzeExpectedField(statusCode, contentType, body, "__schema");
    }

    private static Analysis analyzeExpectedField(int statusCode, String contentType, String body,
                                                  String expectedField) {
        if (body == null || body.isBlank()) {
            return Analysis.notGraphQL("HTTP " + statusCode + " returned an empty response");
        }

        if (isHtmlResponse(contentType, body)) {
            return Analysis.notGraphQL("HTTP " + statusCode + " returned " +
                    displayContentType(contentType) + " instead of GraphQL JSON");
        }

        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(body);
        } catch (RuntimeException e) {
            return Analysis.notGraphQL("HTTP " + statusCode + " returned non-JSON content (" +
                    displayContentType(contentType) + ")");
        }

        if (!parsed.isJsonObject()) {
            return Analysis.notGraphQL("HTTP " + statusCode +
                    " returned JSON, but not a GraphQL response object");
        }

        JsonObject root = parsed.getAsJsonObject();
        if (root.has("data") && root.get("data").isJsonObject()) {
            JsonObject data = root.getAsJsonObject("data");
            if (data.has(expectedField)) {
                boolean expectedFieldAvailable = !data.get(expectedField).isJsonNull();
                String evidence = "__schema".equals(expectedField)
                        ? "GraphQL introspection response"
                        : "GraphQL __typename response";
                return Analysis.graphQL(expectedFieldAvailable, evidence, evidence);
            }
        }

        if (hasGraphQLErrors(root, contentType)) {
            return Analysis.graphQL(false, "GraphQL error response", firstErrorMessage(root));
        }

        return Analysis.notGraphQL("HTTP " + statusCode +
                " returned JSON without the expected GraphQL response structure");
    }

    public static boolean isHtmlResponse(String contentType, String body) {
        String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalizedType.contains("text/html") || normalizedType.contains("application/xhtml+xml")) {
            return true;
        }

        if (body == null) {
            return false;
        }
        String trimmed = body.stripLeading().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("<!doctype html") || trimmed.startsWith("<html");
    }

    public static String graphQLIdeEvidence(int statusCode, String contentType, String body) {
        if (statusCode < 200 || statusCode >= 300 || !isHtmlResponse(contentType, body) || body == null) {
            return null;
        }

        String normalized = body.toLowerCase(Locale.ROOT);
        if (normalized.contains("graphiql-container") || normalized.contains("graphiql.min.js")) {
            return "GraphiQL UI";
        }
        if (normalized.contains("graphql-playground-react") || normalized.contains("graphqlplayground.init")) {
            return "GraphQL Playground UI";
        }
        if (normalized.contains("altair-static") || normalized.contains("altairgraphql.init")) {
            return "Altair GraphQL UI";
        }
        return null;
    }

    public static String displayContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "an unspecified content type";
        }
        int separator = contentType.indexOf(';');
        return separator >= 0 ? contentType.substring(0, separator).trim() : contentType.trim();
    }

    private static boolean hasGraphQLErrors(JsonObject root, String contentType) {
        if (!root.has("errors") || !root.get("errors").isJsonArray()) {
            return false;
        }

        JsonArray errors = root.getAsJsonArray("errors");
        if (errors.isEmpty()) {
            return false;
        }

        boolean graphQLSpecificSignal = contentType != null &&
                contentType.toLowerCase(Locale.ROOT).contains("application/graphql-response+json");
        for (JsonElement error : errors) {
            if (!error.isJsonObject()) {
                return false;
            }
            JsonObject errorObject = error.getAsJsonObject();
            if (!errorObject.has("message") || !errorObject.get("message").isJsonPrimitive() ||
                    !errorObject.getAsJsonPrimitive("message").isString()) {
                return false;
            }
            if (errorObject.has("locations") || errorObject.has("path") || errorObject.has("extensions") ||
                    hasGraphQLErrorLanguage(errorObject.get("message").getAsString())) {
                graphQLSpecificSignal = true;
            }
        }
        return graphQLSpecificSignal;
    }

    private static boolean hasGraphQLErrorLanguage(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("cannot query field") ||
                normalized.contains("unknown argument") ||
                normalized.contains("syntax error") ||
                normalized.contains("validation error") ||
                normalized.contains("operation name") ||
                normalized.contains("selection set") ||
                normalized.contains("introspection") ||
                normalized.contains("graphql");
    }

    private static String firstErrorMessage(JsonObject root) {
        String message = root.getAsJsonArray("errors")
                .get(0).getAsJsonObject().get("message").getAsString().trim();
        if (message.length() > 180) {
            return message.substring(0, 177) + "...";
        }
        return message;
    }

    public static final class Analysis {
        private final boolean graphQL;
        private final boolean expectedFieldAvailable;
        private final String evidence;
        private final String detail;

        private Analysis(boolean graphQL, boolean expectedFieldAvailable, String evidence, String detail) {
            this.graphQL = graphQL;
            this.expectedFieldAvailable = expectedFieldAvailable;
            this.evidence = evidence;
            this.detail = detail;
        }

        private static Analysis graphQL(boolean expectedFieldAvailable, String evidence, String detail) {
            return new Analysis(true, expectedFieldAvailable, evidence, detail);
        }

        private static Analysis notGraphQL(String detail) {
            return new Analysis(false, false, null, detail);
        }

        public boolean isGraphQL() {
            return graphQL;
        }

        public boolean isExpectedFieldAvailable() {
            return expectedFieldAvailable;
        }

        public String getEvidence() {
            return evidence;
        }

        public String getDetail() {
            return detail;
        }
    }
}
