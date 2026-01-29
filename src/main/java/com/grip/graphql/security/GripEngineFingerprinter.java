package com.grip.graphql.security;

import com.grip.graphql.http.GripHttpClient;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class GripEngineFingerprinter {

    public static final String DETECTION_DISCLAIMER =
        "Note: Engine detection is heuristic-based and may not be accurate. " +
        "Results are observational hints, not definitive identification.";

    private final GripHttpClient httpClient;
    private Consumer<String> progressCallback;

    public static class EngineResult {
        public final String engineName;
        public final String version;
        public final String confidence;
        public final String evidence;
        public final List<String> detectedFeatures;

        public EngineResult(String engineName, String confidence, String evidence) {
            this.engineName = engineName;
            this.version = null;
            this.confidence = confidence;
            this.evidence = evidence;
            this.detectedFeatures = new ArrayList<>();
        }
    }

    public GripEngineFingerprinter(GripHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void setProgressCallback(Consumer<String> callback) {
        this.progressCallback = callback;
    }

    private void log(String message) {
        if (progressCallback != null) {
            progressCallback.accept(message);
        }
    }

    public CompletableFuture<EngineResult> fingerprint(String endpoint) {
        return CompletableFuture.supplyAsync(() -> {
            log("[*] Starting engine fingerprinting...");

            if (!verifyGraphQLEndpoint(endpoint)) {
                log("[-] Not a valid GraphQL endpoint");
                return new EngineResult("Unknown", "None", "Could not verify GraphQL endpoint");
            }

            log("[+] GraphQL endpoint confirmed");

            List<DetectionProbe> probes = buildDetectionProbes();

            for (DetectionProbe probe : probes) {
                try {
                    String response = sendProbeQuery(endpoint, probe.query);
                    if (response != null && probe.matcher.matches(response)) {
                        log("[+] Detected: " + probe.engineName);
                        return new EngineResult(probe.engineName, "High", probe.evidence);
                    }
                } catch (Exception e) {

                }
            }

            EngineResult secondary = trySecondaryDetection(endpoint);
            if (secondary != null) {
                return secondary;
            }

            log("[-] Could not determine engine");
            return new EngineResult("Unknown", "None", "No matching signatures found");
        });
    }

    private boolean verifyGraphQLEndpoint(String endpoint) {
        try {
            HttpRequestResponse response = httpClient.sendQueryWithLog(
                endpoint, "query { __typename }", "Engine Fingerprint - Verify"
            );

            if (response == null || response.response() == null) {
                return false;
            }

            String body = response.response().bodyToString();

            if (body.contains("\"data\"") || body.contains("\"errors\"")) {
                return true;
            }

            if (body.contains("__typename")) {
                return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private String sendProbeQuery(String endpoint, String query) {
        try {
            HttpRequestResponse response = httpClient.sendQueryWithLog(
                endpoint, query, "Engine Fingerprint"
            );

            if (response != null && response.response() != null) {
                return response.response().bodyToString();
            }
        } catch (Exception e) {

        }
        return null;
    }

    private List<DetectionProbe> buildDetectionProbes() {
        List<DetectionProbe> probes = new ArrayList<>();

        probes.add(new DetectionProbe(
            "Apollo Server",
            "query @skip { __typename }",
            resp -> containsIgnoreCase(resp, "Directive \"@skip\" argument \"if\" of type \"Boolean!\""),
            "Missing directive argument error format"
        ));

        probes.add(new DetectionProbe(
            "Apollo Server",
            "query @deprecated { __typename }",
            resp -> containsIgnoreCase(resp, "Directive \"@deprecated\" may not be used on QUERY"),
            "Directive location validation"
        ));

        probes.add(new DetectionProbe(
            "Hasura",
            "query { nonexistent_field_xyz }",
            resp -> containsIgnoreCase(resp, "not found in type: 'query_root'"),
            "Hasura-specific field not found error"
        ));

        probes.add(new DetectionProbe(
            "Hasura",
            "query { __typename }",
            resp -> containsIgnoreCase(resp, "query_root"),
            "Returns query_root as typename"
        ));

        probes.add(new DetectionProbe(
            "GraphQL Yoga",
            "subscription { __typename }",
            resp -> containsIgnoreCase(resp, "asyncExecutionResult") ||
                    containsIgnoreCase(resp, "Symbol.asyncIterator"),
            "Subscription handling error"
        ));

        probes.add(new DetectionProbe(
            "Graphene",
            "invalidquery",
            resp -> containsIgnoreCase(resp, "Syntax Error GraphQL"),
            "Python Graphene syntax error format"
        ));

        probes.add(new DetectionProbe(
            "graphql-java",
            "queryy { __typename }",
            resp -> containsIgnoreCase(resp, "Invalid Syntax") &&
                    containsIgnoreCase(resp, "offending token"),
            "Java GraphQL syntax error"
        ));

        probes.add(new DetectionProbe(
            "Juniper",
            "queryy { __typename }",
            resp -> containsIgnoreCase(resp, "Unexpected") &&
                    containsIgnoreCase(resp, "queryy"),
            "Rust Juniper unexpected token"
        ));

        probes.add(new DetectionProbe(
            "Sangria",
            "queryy { __typename }",
            resp -> containsIgnoreCase(resp, "Syntax error while parsing GraphQL query") &&
                    containsIgnoreCase(resp, "Invalid input"),
            "Scala Sangria syntax error"
        ));

        probes.add(new DetectionProbe(
            "Hot Chocolate",
            "queryy { __typename }",
            resp -> containsIgnoreCase(resp, "Unexpected token") &&
                    containsIgnoreCase(resp, "Name"),
            ".NET Hot Chocolate parser error"
        ));

        probes.add(new DetectionProbe(
            "GraphQL PHP",
            "query ! { __typename }",
            resp -> containsIgnoreCase(resp, "Syntax Error") &&
                    containsIgnoreCase(resp, "Cannot parse the unexpected character"),
            "PHP GraphQL parser error"
        ));

        probes.add(new DetectionProbe(
            "WPGraphQL",
            "query { invalid_wp_field }",
            resp -> containsIgnoreCase(resp, "DEBUG_LOGS") ||
                    containsIgnoreCase(resp, "is_graphql_request"),
            "WordPress GraphQL debug info"
        ));

        probes.add(new DetectionProbe(
            "Ariadne",
            "{}",
            resp -> containsIgnoreCase(resp, "The query must be a string") ||
                    containsIgnoreCase(resp, "ariadne"),
            "Ariadne empty query error"
        ));

        probes.add(new DetectionProbe(
            "AWS AppSync",
            "query @skip { __typename }",
            resp -> containsIgnoreCase(resp, "MisplacedDirective"),
            "AppSync directive validation"
        ));

        probes.add(new DetectionProbe(
            "Mercurius",
            "{}",
            resp -> containsIgnoreCase(resp, "Unknown query") ||
                    containsIgnoreCase(resp, "mercurius"),
            "Mercurius empty query"
        ));

        probes.add(new DetectionProbe(
            "Dgraph",
            "query { __typename @cascade }",
            resp -> {
                try {
                    JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
                    if (json.has("data")) {
                        JsonObject data = json.getAsJsonObject("data");
                        if (data.has("__typename")) {
                            return "Query".equals(data.get("__typename").getAsString());
                        }
                    }
                } catch (Exception e) {}
                return false;
            },
            "Dgraph cascade directive support"
        ));

        probes.add(new DetectionProbe(
            "Ruby GraphQL",
            "query @skip { __typename }",
            resp -> containsIgnoreCase(resp, "'@skip' can't be applied to queries"),
            "Ruby directive application error"
        ));

        probes.add(new DetectionProbe(
            "Absinthe",
            "query { nonexistent_xyz }",
            resp -> containsIgnoreCase(resp, "Cannot query field") &&
                    containsIgnoreCase(resp, "RootQueryType"),
            "Elixir Absinthe field error"
        ));

        probes.add(new DetectionProbe(
            "Tartiflette",
            "query { nonexistent_xyz }",
            resp -> containsIgnoreCase(resp, "doesn't exist on Query"),
            "Tartiflette field error"
        ));

        probes.add(new DetectionProbe(
            "Strawberry",
            "query @deprecated { __typename }",
            resp -> containsIgnoreCase(resp, "Directive '@deprecated' may not be used on query"),
            "Strawberry directive error"
        ));

        probes.add(new DetectionProbe(
            "gqlgen",
            "query { nonexistent_xyz }",
            resp -> containsIgnoreCase(resp, "Cannot query field") &&
                    containsIgnoreCase(resp, "Query"),
            "Go gqlgen field error"
        ));

        probes.add(new DetectionProbe(
            "Lighthouse",
            "query { __typename }",
            resp -> containsIgnoreCase(resp, "lighthouse") ||
                    containsIgnoreCase(resp, "laravel"),
            "Laravel Lighthouse extension"
        ));

        probes.add(new DetectionProbe(
            "Caliban",
            "fragment woof on Query { __typename } fragment woof2 on Query { ...woof } query { ...woof2 }",
            resp -> containsIgnoreCase(resp, "Fragment") &&
                    containsIgnoreCase(resp, "is not used"),
            "Caliban fragment validation"
        ));

        probes.add(new DetectionProbe(
            "HyperGraphQL",
            "query { __typename @deprecated }",
            resp -> containsIgnoreCase(resp, "Unknown directive") &&
                    containsIgnoreCase(resp, "deprecated"),
            "HyperGraphQL directive error"
        ));

        probes.add(new DetectionProbe(
            "Directus",
            "",
            resp -> {
                try {
                    JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
                    if (json.has("errors")) {
                        JsonArray errors = json.getAsJsonArray("errors");
                        for (JsonElement err : errors) {
                            JsonObject errObj = err.getAsJsonObject();
                            if (errObj.has("extensions")) {
                                JsonObject ext = errObj.getAsJsonObject("extensions");
                                if (ext.has("code") && "INVALID_PAYLOAD".equals(ext.get("code").getAsString())) {
                                    return true;
                                }
                            }
                        }
                    }
                } catch (Exception e) {}
                return false;
            },
            "Directus error code"
        ));

        return probes;
    }

    private EngineResult trySecondaryDetection(String endpoint) {

        try {
            HttpRequestResponse response = httpClient.sendQueryWithLog(
                endpoint, "query { __typename }", "Engine Fingerprint - Extensions"
            );

            if (response == null || response.response() == null) {
                return null;
            }

            String body = response.response().bodyToString();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            if (json.has("extensions")) {
                JsonObject extensions = json.getAsJsonObject("extensions");

                if (extensions.has("inigo")) {
                    log("[+] Detected: Inigo (via extensions)");
                    return new EngineResult("Inigo", "High", "inigo field in extensions");
                }

                if (extensions.has("tracing")) {
                    log("[+] Likely Apollo Server (tracing extension)");
                    return new EngineResult("Apollo Server", "Medium", "Apollo tracing extension");
                }

                if (extensions.has("newrelic")) {
                    log("[+] Server uses New Relic monitoring");
                }
            }

            if (json.has("data")) {
                JsonObject data = json.getAsJsonObject("data");
                if (data.has("__typename")) {
                    String typename = data.get("__typename").getAsString();

                    if ("RootQuery".equals(typename)) {
                        log("[+] Detected: graphql-go (RootQuery typename)");
                        return new EngineResult("graphql-go", "Medium", "RootQuery typename");
                    }

                    if ("query_root".equals(typename)) {
                        log("[+] Detected: Hasura (query_root typename)");
                        return new EngineResult("Hasura", "High", "query_root typename");
                    }
                }
            }

        } catch (Exception e) {

        }

        return null;
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) return false;
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private static class DetectionProbe {
        final String engineName;
        final String query;
        final ResponseMatcher matcher;
        final String evidence;

        DetectionProbe(String engineName, String query, ResponseMatcher matcher, String evidence) {
            this.engineName = engineName;
            this.query = query;
            this.matcher = matcher;
            this.evidence = evidence;
        }
    }

    @FunctionalInterface
    private interface ResponseMatcher {
        boolean matches(String response);
    }
}
