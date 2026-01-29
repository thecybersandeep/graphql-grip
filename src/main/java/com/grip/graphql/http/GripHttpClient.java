package com.grip.graphql.http;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.grip.graphql.GripConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public class GripHttpClient {

    private final MontoyaApi api;
    private final Semaphore rateLimiter;
    private final int requestDelayMs;
    private final int timeoutMs;
    private final int maxRetries;

    private final AtomicLong lastRequestTime = new AtomicLong(0);

    private final Map<String, String> defaultHeaders;

    private static final List<String> AUTH_HEADERS = List.of(
        "Authorization",
        "Cookie",
        "X-API-Key",
        "X-Auth-Token",
        "X-Access-Token",
        "X-CSRF-Token",
        "X-XSRF-Token",
        "Api-Key",
        "Session-Id",
        "X-Session-Token"
    );

    private final Map<String, String> inheritedHeaders = new ConcurrentHashMap<>();

    public GripHttpClient(MontoyaApi api) {
        this(api, 10, 100, 30000, 2);
    }

    public GripHttpClient(MontoyaApi api, int maxConcurrent, int requestDelayMs, int timeoutMs, int maxRetries) {
        this.api = api;
        this.rateLimiter = new Semaphore(maxConcurrent);
        this.requestDelayMs = requestDelayMs;
        this.timeoutMs = timeoutMs;
        this.maxRetries = maxRetries;
        this.defaultHeaders = new ConcurrentHashMap<>();

        defaultHeaders.put("Content-Type", "application/json");
        defaultHeaders.put("Accept", "application/json");

        GripConfig config = GripConfig.getInstance();
        String userAgent = config != null ? config.getString(GripConfig.HTTP_USER_AGENT) : null;
        if (userAgent == null || userAgent.isEmpty()) {

            defaultHeaders.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        } else {
            defaultHeaders.put("User-Agent", userAgent);
        }
    }

    public void inheritAuthHeaders(HttpRequest originalRequest) {
        if (originalRequest == null) return;

        GripConfig config = GripConfig.getInstance();
        boolean shouldInherit = config == null || config.getBoolean(GripConfig.HTTP_INHERIT_AUTH_HEADERS);

        if (!shouldInherit) return;

        inheritedHeaders.clear();

        for (String headerName : AUTH_HEADERS) {
            String value = originalRequest.headerValue(headerName);
            if (value != null && !value.isEmpty()) {
                inheritedHeaders.put(headerName, value);
                logDebug("Inherited auth header: " + headerName);
            }
        }

        String originalUserAgent = originalRequest.headerValue("User-Agent");
        if (originalUserAgent != null && !originalUserAgent.isEmpty()) {
            defaultHeaders.put("User-Agent", originalUserAgent);
            logDebug("Inherited User-Agent from original request");
        }
    }

    public void clearInheritedHeaders() {
        inheritedHeaders.clear();
    }

    public Map<String, String> getInheritedHeaders() {
        return new HashMap<>(inheritedHeaders);
    }

    public JsonObject sendQuery(String endpoint, String query) throws Exception {
        return sendQuery(endpoint, query, null, null);
    }

    public JsonObject sendQuery(String endpoint, String query, JsonObject variables) throws Exception {
        return sendQuery(endpoint, query, variables, null);
    }

    public JsonObject sendQuery(String endpoint, String query, JsonObject variables,
                                 Map<String, String> headers) throws Exception {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < maxRetries) {
            attempts++;
            rateLimiter.acquire();
            try {
                enforceDelay();

                JsonObject body = new JsonObject();
                body.addProperty("query", query);
                if (variables != null) {
                    body.add("variables", variables);
                }

                HttpRequest request = HttpRequest.httpRequestFromUrl(endpoint)
                        .withMethod("POST")
                        .withBody(body.toString());

                request = addAllHeaders(request, headers);

                HttpRequestResponse response = api.http().sendRequest(request);
                lastRequestTime.set(System.currentTimeMillis());

                if (response == null || response.response() == null) {
                    throw new GripHttpException("No response received from server", endpoint, null);
                }

                String responseBody = response.response().bodyToString();

                int statusCode = response.response().statusCode();
                if (statusCode == 429) {

                    long backoffMs = (long) Math.pow(2, attempts) * 1000;
                    logDebug("Rate limited (429), backing off for " + backoffMs + "ms");
                    Thread.sleep(backoffMs);
                    throw new GripHttpException("Rate limited: HTTP 429", endpoint, responseBody);
                }
                if (statusCode >= 500) {
                    throw new GripHttpException("Server error: HTTP " + statusCode, endpoint, responseBody);
                }

                return JsonParser.parseString(responseBody).getAsJsonObject();

            } catch (JsonSyntaxException e) {
                lastException = new GripHttpException("Invalid JSON response from server", endpoint, e.getMessage());
                logError("JSON parse error for " + endpoint + ": " + e.getMessage());
                break;
            } catch (GripHttpException e) {
                lastException = e;
                logError("HTTP error for " + endpoint + ": " + e.getMessage());
                if (attempts < maxRetries && isRetryable(e)) {
                    logDebug("Retrying request (attempt " + (attempts + 1) + "/" + maxRetries + ")");
                    continue;
                }
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                lastException = new GripHttpException("Request failed: " + e.getMessage(), endpoint, null);
                logError("Unexpected error for " + endpoint + ": " + e.getMessage());
                if (attempts < maxRetries) {
                    logDebug("Retrying request (attempt " + (attempts + 1) + "/" + maxRetries + ")");
                    continue;
                }
                break;
            } finally {
                rateLimiter.release();
            }
        }

        throw lastException != null ? lastException : new Exception("Request failed after " + maxRetries + " attempts");
    }

    private boolean isRetryable(GripHttpException e) {

        return e.getMessage().contains("Server error") ||
               e.getMessage().contains("Rate limited") ||
               e.getMessage().contains("Connection") ||
               e.getMessage().contains("timeout");
    }

    public HttpRequestResponse sendRaw(String endpoint, String method, String body,
                                        Map<String, String> headers) throws Exception {
        rateLimiter.acquire();
        try {
            enforceDelay();

            HttpRequest request = HttpRequest.httpRequestFromUrl(endpoint)
                    .withMethod(method);

            if (body != null && !body.isEmpty()) {
                request = request.withBody(body);
            }

            request = addAllHeaders(request, headers);

            HttpRequestResponse response = api.http().sendRequest(request);
            lastRequestTime.set(System.currentTimeMillis());

            return response;

        } catch (Exception e) {
            logError("Raw request failed for " + endpoint + ": " + e.getMessage());
            throw e;
        } finally {
            rateLimiter.release();
        }
    }

    public HttpRequestResponse sendGet(String endpoint, String query) throws Exception {
        return sendGet(endpoint, query, null);
    }

    public HttpRequestResponse sendGet(String endpoint, String query, Map<String, String> headers) throws Exception {
        rateLimiter.acquire();
        try {
            enforceDelay();

            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            String url = endpoint + (endpoint.contains("?") ? "&" : "?") + "query=" + encodedQuery;

            HttpRequest request = HttpRequest.httpRequestFromUrl(url)
                    .withMethod("GET");

            request = request.withAddedHeader("Accept", "application/json");
            request = request.withAddedHeader("User-Agent", defaultHeaders.get("User-Agent"));

            for (Map.Entry<String, String> header : inheritedHeaders.entrySet()) {
                request = request.withAddedHeader(header.getKey(), header.getValue());
            }

            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    request = request.withAddedHeader(header.getKey(), header.getValue());
                }
            }

            HttpRequestResponse response = api.http().sendRequest(request);
            lastRequestTime.set(System.currentTimeMillis());

            return response;

        } catch (Exception e) {
            logError("GET request failed for " + endpoint + ": " + e.getMessage());
            throw e;
        } finally {
            rateLimiter.release();
        }
    }

    public HttpRequestResponse sendPostUrlEncoded(String endpoint, String query) throws Exception {
        return sendPostUrlEncoded(endpoint, query, null);
    }

    public HttpRequestResponse sendPostUrlEncoded(String endpoint, String query, Map<String, String> headers) throws Exception {
        rateLimiter.acquire();
        try {
            enforceDelay();

            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            String body = "query=" + encodedQuery;

            HttpRequest request = HttpRequest.httpRequestFromUrl(endpoint)
                    .withMethod("POST")
                    .withAddedHeader("Content-Type", "application/x-www-form-urlencoded")
                    .withAddedHeader("Accept", "application/json")
                    .withAddedHeader("User-Agent", defaultHeaders.get("User-Agent"))
                    .withBody(body);

            for (Map.Entry<String, String> header : inheritedHeaders.entrySet()) {
                request = request.withAddedHeader(header.getKey(), header.getValue());
            }

            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    request = request.withAddedHeader(header.getKey(), header.getValue());
                }
            }

            HttpRequestResponse response = api.http().sendRequest(request);
            lastRequestTime.set(System.currentTimeMillis());

            return response;

        } catch (Exception e) {
            logError("POST URL-encoded request failed for " + endpoint + ": " + e.getMessage());
            throw e;
        } finally {
            rateLimiter.release();
        }
    }

    public void setDefaultHeader(String name, String value) {
        defaultHeaders.put(name, value);
    }

    public void removeDefaultHeader(String name) {
        defaultHeaders.remove(name);
    }

    public void setUserAgent(String userAgent) {
        if (userAgent != null && !userAgent.isEmpty()) {
            defaultHeaders.put("User-Agent", userAgent);
        }
    }

    public JsonObject sendQueryAndLog(String endpoint, String query, String source) throws Exception {
        HttpRequestResponse response = sendQueryWithLog(endpoint, query, source);
        if (response == null || response.response() == null) {
            throw new GripHttpException("No response received from server", endpoint, null);
        }
        String responseBody = response.response().bodyToString();
        return JsonParser.parseString(responseBody).getAsJsonObject();
    }

    public HttpRequestResponse sendQueryWithLog(String endpoint, String query, String source) throws Exception {
        rateLimiter.acquire();
        try {
            enforceDelay();

            JsonObject body = new JsonObject();
            body.addProperty("query", query);

            HttpRequest request = HttpRequest.httpRequestFromUrl(endpoint)
                    .withMethod("POST")
                    .withBody(body.toString());

            request = addAllHeaders(request, null);

            HttpRequestResponse response = api.http().sendRequest(request);
            lastRequestTime.set(System.currentTimeMillis());

            logDebug("[" + source + "] Request sent to " + endpoint);
            return response;

        } catch (Exception e) {
            logError("[" + source + "] Request failed for " + endpoint + ": " + e.getMessage());
            throw e;
        } finally {
            rateLimiter.release();
        }
    }

    public HttpRequestResponse sendRawWithLog(String endpoint, String method, String body,
                                               Map<String, String> headers, String source) throws Exception {
        logDebug("[" + source + "] Sending " + method + " to " + endpoint);
        return sendRaw(endpoint, method, body, headers);
    }

    public HttpRequestResponse sendGetWithLog(String endpoint, String query, String source) throws Exception {
        logDebug("[" + source + "] Sending GET to " + endpoint);
        return sendGet(endpoint, query);
    }

    public HttpRequestResponse sendPostUrlEncodedWithLog(String endpoint, String query, String source) throws Exception {
        logDebug("[" + source + "] Sending POST URL-encoded to " + endpoint);
        return sendPostUrlEncoded(endpoint, query);
    }

    private HttpRequest addAllHeaders(HttpRequest request, Map<String, String> customHeaders) {

        for (Map.Entry<String, String> header : defaultHeaders.entrySet()) {
            request = request.withAddedHeader(header.getKey(), header.getValue());
        }

        for (Map.Entry<String, String> header : inheritedHeaders.entrySet()) {
            request = request.withAddedHeader(header.getKey(), header.getValue());
        }

        if (customHeaders != null) {
            for (Map.Entry<String, String> header : customHeaders.entrySet()) {
                request = request.withAddedHeader(header.getKey(), header.getValue());
            }
        }

        return request;
    }

    private void enforceDelay() throws InterruptedException {
        if (requestDelayMs > 0) {
            long now = System.currentTimeMillis();
            long last = lastRequestTime.get();
            long elapsed = now - last;
            if (elapsed < requestDelayMs) {
                Thread.sleep(requestDelayMs - elapsed);
            }
        }
    }

    private void logDebug(String message) {
        api.logging().logToOutput("[GraphQL Grip] " + message);
    }

    private void logError(String message) {
        api.logging().logToError("[GraphQL Grip] " + message);
    }

    public static class GripHttpException extends Exception {
        private final String endpoint;
        private final String responseBody;

        public GripHttpException(String message, String endpoint, String responseBody) {
            super(message);
            this.endpoint = endpoint;
            this.responseBody = responseBody;
        }

        public String getEndpoint() { return endpoint; }
        public String getResponseBody() { return responseBody; }
    }
}
