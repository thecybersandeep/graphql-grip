package com.grip.graphql.http;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.grip.graphql.GripConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class GripHttpClient {

    private final MontoyaApi api;
    private final Semaphore rateLimiter;
    private final int requestDelayMs;
    private final int timeoutMs;
    private final int maxRetries;
    private final ExecutorService taskExecutor;
    private final ExecutorService sendExecutor;

    private static final Set<Integer> AUTH_ERROR_CODES = Set.of(401, 403);

    private final AtomicLong lastRequestTime = new AtomicLong(0);

    private final Map<String, String> defaultHeaders;

    private static final java.util.Set<String> SKIP_HEADERS = java.util.Set.of(
        "host", "content-length", "content-type", "accept",
        "connection", "accept-encoding", "accept-language"
    );

    private final Map<String, String> inheritedHeaders = new ConcurrentHashMap<>();
    private volatile HttpRequest requestTemplate;

    public GripHttpClient(MontoyaApi api) {
        this(api, 10, 100, 30000, 2);
    }

    public GripHttpClient(MontoyaApi api, int maxConcurrent, int requestDelayMs, int timeoutMs, int maxRetries) {
        this.api = api;
        this.rateLimiter = new Semaphore(maxConcurrent);
        this.requestDelayMs = requestDelayMs;
        this.timeoutMs = timeoutMs;
        this.maxRetries = maxRetries;
        this.taskExecutor = Executors.newFixedThreadPool(maxConcurrent, r -> {
            Thread t = new Thread(r, "grip-task-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
        this.sendExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "grip-http-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
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

    public ExecutorService getExecutor() {
        return taskExecutor;
    }

    private HttpRequestResponse sendWithTimeout(HttpRequest request) throws Exception {
        Future<HttpRequestResponse> future = sendExecutor.submit(() -> api.http().sendRequest(request));
        try {
            HttpRequestResponse response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            lastRequestTime.set(System.currentTimeMillis());
            return response;
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new GripHttpException("Request timed out after " + timeoutMs + "ms. " +
                "Server may be unreachable or not responding.", request.url(), null);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new GripHttpException("Request failed: " + cause.getMessage(), request.url(), null);
        }
    }

    private void checkAuthErrors(int statusCode, String endpoint, String responseBody) throws GripAuthException {
        if (AUTH_ERROR_CODES.contains(statusCode)) {
            throw new GripAuthException(
                "Authentication failed: HTTP " + statusCode +
                ". Ensure valid Cookie/Authorization headers are configured in the Scanner or Schema tab.",
                endpoint, responseBody, statusCode);
        }
    }

    private void checkResponseAuth(HttpRequestResponse response, String endpoint) throws GripAuthException {
        if (response != null && response.response() != null) {
            checkAuthErrors(response.response().statusCode(), endpoint, response.response().bodyToString());
        }
    }

    public void inheritAuthHeaders(HttpRequest originalRequest) {
        if (originalRequest == null) return;

        GripConfig config = GripConfig.getInstance();
        boolean shouldInherit = config == null || config.getBoolean(GripConfig.HTTP_INHERIT_AUTH_HEADERS);

        if (!shouldInherit) return;

        inheritedHeaders.clear();

        for (HttpHeader header : originalRequest.headers()) {
            String name = header.name();
            String value = header.value();
            if (name == null || value == null || value.isEmpty()) continue;

            if (SKIP_HEADERS.contains(name.toLowerCase())) continue;

            if ("User-Agent".equalsIgnoreCase(name)) {
                defaultHeaders.put("User-Agent", value);
            } else {
                inheritedHeaders.put(name, value);
                logDebug("Inherited header: " + name + " = " + maskValue(value));
            }
        }

        logDebug("Inherited " + inheritedHeaders.size() + " header(s) from original request");
    }

    private String maskValue(String value) {
        if (value.length() <= 12) return "***";
        return value.substring(0, 6) + "..." + value.substring(value.length() - 6);
    }

    public void clearInheritedHeaders() {
        inheritedHeaders.clear();
    }

    public Map<String, String> getInheritedHeaders() {
        return new HashMap<>(inheritedHeaders);
    }

    public void setRequestTemplate(HttpRequest template) {
        this.requestTemplate = template;
        if (template != null) {
            inheritAuthHeaders(template);
        }
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

                HttpRequest request = buildPostRequest(endpoint, body.toString());
                if (headers != null) {
                    for (Map.Entry<String, String> header : headers.entrySet()) {
                        request = request.withUpdatedHeader(header.getKey(), header.getValue());
                    }
                }

                HttpRequestResponse response = sendWithTimeout(request);

                if (response == null || response.response() == null) {
                    throw new GripHttpException("No response received from server", endpoint, null);
                }

                String responseBody = response.response().bodyToString();

                int statusCode = response.response().statusCode();
                checkAuthErrors(statusCode, endpoint, responseBody);

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
            } catch (GripAuthException e) {
                logError("Auth error for " + endpoint + ": " + e.getMessage());
                throw e;
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

            HttpRequestResponse response = sendWithTimeout(request);
            checkResponseAuth(response, endpoint);
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
                    .withMethod("GET")
                    .withUpdatedHeader("Accept", "application/json")
                    .withUpdatedHeader("User-Agent", defaultHeaders.get("User-Agent"));

            request = applyInheritedAndCustomHeaders(request, headers);

            HttpRequestResponse response = sendWithTimeout(request);
            checkResponseAuth(response, endpoint);
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
                    .withUpdatedHeader("Content-Type", "application/x-www-form-urlencoded")
                    .withUpdatedHeader("Accept", "application/json")
                    .withUpdatedHeader("User-Agent", defaultHeaders.get("User-Agent"))
                    .withBody(body);

            request = applyInheritedAndCustomHeaders(request, headers);

            HttpRequestResponse response = sendWithTimeout(request);
            checkResponseAuth(response, endpoint);
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

            HttpRequest request = buildPostRequest(endpoint, body.toString());

            HttpRequestResponse response = sendWithTimeout(request);
            checkResponseAuth(response, endpoint);

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

    public HttpRequest buildPostRequest(String endpoint, String body) {
        HttpRequest request;
        if (requestTemplate != null && requestTemplate.url().equals(endpoint)) {
            request = requestTemplate
                    .withMethod("POST")
                    .withBody(body)
                    .withUpdatedHeader("Content-Type", "application/json")
                    .withUpdatedHeader("Accept", "application/json");
            logDebug("Using request template for " + endpoint);
        } else {
            request = HttpRequest.httpRequestFromUrl(endpoint)
                    .withMethod("POST")
                    .withBody(body);
            request = addAllHeaders(request, null);
        }
        return request;
    }

    private HttpRequest addAllHeaders(HttpRequest request, Map<String, String> customHeaders) {
        for (Map.Entry<String, String> header : defaultHeaders.entrySet()) {
            request = request.withUpdatedHeader(header.getKey(), header.getValue());
        }
        return applyInheritedAndCustomHeaders(request, customHeaders);
    }

    private HttpRequest applyInheritedAndCustomHeaders(HttpRequest request, Map<String, String> customHeaders) {
        for (Map.Entry<String, String> header : inheritedHeaders.entrySet()) {
            request = request.withUpdatedHeader(header.getKey(), header.getValue());
        }
        if (customHeaders != null) {
            for (Map.Entry<String, String> header : customHeaders.entrySet()) {
                request = request.withUpdatedHeader(header.getKey(), header.getValue());
            }
        }
        return request;
    }

    private void enforceDelay() throws InterruptedException {
        if (requestDelayMs > 0) {
            long last = lastRequestTime.get();
            long now = System.currentTimeMillis();
            long elapsed = now - last;
            if (elapsed < requestDelayMs && lastRequestTime.compareAndSet(last, now)) {
                Thread.sleep(requestDelayMs - elapsed);
            }
        }
    }

    public void shutdown() {
        taskExecutor.shutdown();
        sendExecutor.shutdown();
        try {
            if (!taskExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                taskExecutor.shutdownNow();
            }
            if (!sendExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                sendExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            taskExecutor.shutdownNow();
            sendExecutor.shutdownNow();
            Thread.currentThread().interrupt();
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

    public static class GripAuthException extends GripHttpException {
        private final int statusCode;

        public GripAuthException(String message, String endpoint, String responseBody, int statusCode) {
            super(message, endpoint, responseBody);
            this.statusCode = statusCode;
        }

        public int getStatusCode() { return statusCode; }
    }
}
