package com.grip.graphql.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GraphQLRequestDetector {

    private static final Pattern GRAPHQL_PATH_SEGMENT =
            Pattern.compile("(?:^|/)(?:graphql|gql)(?:/|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GRAPHQL_DOCUMENT =
            Pattern.compile("^(?:\\s|#[^\\r\\n]*(?:\\r?\\n|$))*(?:query|mutation|subscription|fragment|\\{)\\b?",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTIPART_QUERY = Pattern.compile(
            "(?is)name=\\\"?query\\\"?[^\\r\\n]*\\r?\\n\\r?\\n(.*?)(?=\\r?\\n--)");
    private static final Pattern MULTIPART_OPERATIONS = Pattern.compile(
            "(?is)name=\\\"?operations\\\"?[^\\r\\n]*\\r?\\n\\r?\\n(.*?)(?=\\r?\\n--)");

    private GraphQLRequestDetector() {
    }

    static boolean isGraphQLRequest(String method, String path, String contentType, String body) {
        String normalizedPath = path == null ? "" : path;
        String pathOnly = normalizedPath.split("\\?", 2)[0];
        if (GRAPHQL_PATH_SEGMENT.matcher(pathOnly).find()) {
            return true;
        }

        String query = extractQuery(method, path, contentType, body);
        if (query != null && looksLikeGraphQLDocument(query)) {
            return true;
        }

        return containsPersistedQuery(path) || containsPersistedQuery(body);
    }

    static String extractQuery(String method, String path, String contentType, String body) {
        String fromUrl = parameterValue(path, "query");
        if (fromUrl != null && !fromUrl.isBlank()) {
            return fromUrl;
        }

        if (body == null || body.isBlank()) {
            return null;
        }

        String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalizedType.contains("application/x-www-form-urlencoded")) {
            return parameterValue("?" + body, "query");
        }

        if (normalizedType.contains("multipart/form-data")) {
            Matcher queryPart = MULTIPART_QUERY.matcher(body);
            if (queryPart.find()) {
                return queryPart.group(1).trim();
            }
            Matcher operationsPart = MULTIPART_OPERATIONS.matcher(body);
            if (operationsPart.find()) {
                return queryFromJson(operationsPart.group(1).trim());
            }
        }

        String jsonQuery = queryFromJson(body);
        if (jsonQuery != null) {
            return jsonQuery;
        }

        String trimmedBody = body.trim();
        boolean jsonContentType = normalizedType.contains("application/json") ||
                normalizedType.contains("application/graphql-response+json");
        if ((trimmedBody.startsWith("{") || trimmedBody.startsWith("[")) &&
                (jsonContentType || isValidJson(trimmedBody))) {
            return null;
        }

        if (normalizedType.contains("application/graphql") || looksLikeGraphQLDocument(body)) {
            return trimmedBody;
        }

        return null;
    }

    private static boolean isValidJson(String value) {
        try {
            JsonParser.parseString(value);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String queryFromJson(String body) {
        String trimmed = body == null ? "" : body.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return null;
        }

        try {
            JsonElement parsed = JsonParser.parseString(trimmed);
            if (parsed.isJsonObject()) {
                return queryFromOperation(parsed.getAsJsonObject());
            }
            if (parsed.isJsonArray()) {
                JsonArray batch = parsed.getAsJsonArray();
                for (JsonElement item : batch) {
                    if (item.isJsonObject()) {
                        String query = queryFromOperation(item.getAsJsonObject());
                        if (query != null) {
                            return query;
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static String queryFromOperation(JsonObject operation) {
        if (!operation.has("query") || !operation.get("query").isJsonPrimitive() ||
                !operation.getAsJsonPrimitive("query").isString()) {
            return null;
        }
        String query = operation.get("query").getAsString();
        return query.isBlank() ? null : query;
    }

    private static boolean containsPersistedQuery(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String extensions = parameterValue(value, "extensions");
        if (extensions != null && extensions.toLowerCase(Locale.ROOT).contains("persistedquery")) {
            return true;
        }

        try {
            JsonElement parsed = JsonParser.parseString(value.trim());
            if (parsed.isJsonObject()) {
                return hasPersistedQueryExtension(parsed.getAsJsonObject());
            }
            if (parsed.isJsonArray()) {
                for (JsonElement item : parsed.getAsJsonArray()) {
                    if (item.isJsonObject() && hasPersistedQueryExtension(item.getAsJsonObject())) {
                        return true;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    private static boolean hasPersistedQueryExtension(JsonObject operation) {
        if (!operation.has("extensions") || !operation.get("extensions").isJsonObject()) {
            return false;
        }
        return operation.getAsJsonObject("extensions").has("persistedQuery");
    }

    private static boolean looksLikeGraphQLDocument(String value) {
        return value != null && GRAPHQL_DOCUMENT.matcher(value).find();
    }

    private static String parameterValue(String value, String requestedName) {
        if (value == null) {
            return null;
        }
        int questionMark = value.indexOf('?');
        String parameters = questionMark >= 0 ? value.substring(questionMark + 1) : value;
        for (String parameter : parameters.split("&")) {
            int separator = parameter.indexOf('=');
            String encodedName = separator >= 0 ? parameter.substring(0, separator) : parameter;
            String encodedValue = separator >= 0 ? parameter.substring(separator + 1) : "";
            try {
                String name = URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
                if (requestedName.equals(name)) {
                    return URLDecoder.decode(encodedValue, StandardCharsets.UTF_8);
                }
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
