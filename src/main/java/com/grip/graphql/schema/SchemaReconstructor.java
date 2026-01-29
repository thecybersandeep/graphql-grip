package com.grip.graphql.schema;

import com.grip.graphql.http.GripHttpClient;
import com.grip.graphql.model.schema.*;
import com.grip.graphql.schema.regex.GripRegexStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class SchemaReconstructor {

    private static final int BUCKET_SIZE = 64;
    private static final int MAX_CONCURRENT = 8;
    private static final int MAX_DEPTH = 10;
    private static final int REQUEST_DELAY_MS = 50;

    private final GripHttpClient httpClient;
    private final ExecutorService executor;
    private final Semaphore semaphore;
    private final List<String> wordlist;

    private final Map<String, GripType> discoveredTypes;
    private final Set<String> exploredTypes;
    private final Queue<TypeExplorationTask> explorationQueue;

    private Consumer<String> progressCallback;
    private volatile boolean cancelled = false;
    private final AtomicInteger totalProbes = new AtomicInteger(0);
    private final AtomicInteger successfulProbes = new AtomicInteger(0);

    private static class TypeExplorationTask {
        final String typeName;
        final String parentPath;
        final int depth;

        TypeExplorationTask(String typeName, String parentPath, int depth) {
            this.typeName = typeName;
            this.parentPath = parentPath;
            this.depth = depth;
        }
    }

    public SchemaReconstructor(GripHttpClient httpClient) {
        this.httpClient = httpClient;
        this.executor = Executors.newFixedThreadPool(MAX_CONCURRENT);
        this.semaphore = new Semaphore(MAX_CONCURRENT);
        this.wordlist = loadWordlist();
        this.discoveredTypes = new ConcurrentHashMap<>();
        this.exploredTypes = ConcurrentHashMap.newKeySet();
        this.explorationQueue = new ConcurrentLinkedQueue<>();
    }

    public void setProgressCallback(Consumer<String> callback) {
        this.progressCallback = callback;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public CompletableFuture<GripSchema> reconstructSchema(String endpoint) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                reportProgress("Starting schema reconstruction for " + endpoint);

                discoveredTypes.clear();
                exploredTypes.clear();
                explorationQueue.clear();
                cancelled = false;
                totalProbes.set(0);
                successfulProbes.set(0);

                reportProgress("Phase 1: Discovering Query type fields...");
                Set<String> queryFields = probeRootType(endpoint, "Query");

                if (queryFields.isEmpty()) {
                    reportProgress("No Query fields discovered. Schema may be protected.");
                    return createEmptySchema(endpoint);
                }

                reportProgress("Discovered " + queryFields.size() + " Query fields");

                GripType queryType = new GripType("Query", GripTypeKind.OBJECT);
                for (String fieldName : queryFields) {
                    GripField field = new GripField(fieldName, GripTypeRef.simple("Unknown"));
                    queryType.addField(field);
                }
                discoveredTypes.put("Query", queryType);

                reportProgress("Phase 2: Discovering Mutation type...");
                Set<String> mutationFields = probeRootType(endpoint, "Mutation");
                if (!mutationFields.isEmpty()) {
                    reportProgress("Discovered " + mutationFields.size() + " Mutation fields");
                    GripType mutationType = new GripType("Mutation", GripTypeKind.OBJECT);
                    for (String fieldName : mutationFields) {
                        GripField field = new GripField(fieldName, GripTypeRef.simple("Unknown"));
                        mutationType.addField(field);
                    }
                    discoveredTypes.put("Mutation", mutationType);
                }

                reportProgress("Phase 3: Probing field return types...");
                probeFieldTypes(endpoint);

                reportProgress("Phase 4: Exploring nested types...");
                exploreDiscoveredTypes(endpoint);

                GripSchema schema = buildSchema(endpoint);
                schema.setReconstructed(true);

                reportProgress("Schema reconstruction complete. Discovered " +
                    discoveredTypes.size() + " types, " + totalProbes.get() + " probes (" +
                    successfulProbes.get() + " successful)");

                return schema;

            } catch (Exception e) {
                reportProgress("Error during reconstruction: " + e.getMessage());
                return createEmptySchema(endpoint);
            }
        }, executor);
    }

    private Set<String> probeRootType(String endpoint, String rootType) {
        Set<String> discoveredFields = ConcurrentHashMap.newKeySet();

        List<List<String>> buckets = createBuckets(wordlist, BUCKET_SIZE);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (List<String> bucket : buckets) {
            if (cancelled) break;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    semaphore.acquire();
                    Set<String> found = probeBucket(endpoint, bucket, rootType.equals("Mutation"));
                    discoveredFields.addAll(found);
                    totalProbes.incrementAndGet();
                    if (!found.isEmpty()) successfulProbes.incrementAndGet();
                } catch (Exception e) {

                    reportProgress("Probe failed: " + e.getMessage());
                } finally {
                    semaphore.release();
                    delay();
                }
            }, executor);

            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return discoveredFields;
    }

    private Set<String> probeBucket(String endpoint, List<String> fieldNames, boolean isMutation) {
        Set<String> discovered = new HashSet<>();

        StringBuilder query = new StringBuilder();
        query.append(isMutation ? "mutation GripProbe {" : "query GripProbe {");

        for (int i = 0; i < fieldNames.size(); i++) {
            String fieldName = fieldNames.get(i);
            query.append(" g").append(i).append(": ").append(fieldName);
        }
        query.append(" }");

        try {
            JsonObject response = httpClient.sendQuery(endpoint, query.toString());

            if (response.has("data") && !response.get("data").isJsonNull()) {
                JsonObject data = response.getAsJsonObject("data");
                for (int i = 0; i < fieldNames.size(); i++) {
                    String alias = "g" + i;
                    if (data.has(alias)) {
                        discovered.add(fieldNames.get(i));
                    }
                }
            }

            if (response.has("errors") && response.get("errors").isJsonArray()) {
                JsonArray errors = response.getAsJsonArray("errors");
                for (JsonElement errorElement : errors) {
                    if (errorElement.isJsonObject()) {
                        String message = getErrorMessage(errorElement.getAsJsonObject());

                        if (GripRegexStore.indicatesValidField(message)) {

                            GripRegexStore.MatchResult result = GripRegexStore.extractFieldInfo(message);
                            if (result != null && result.getField() != null) {
                                discovered.add(result.getField());
                            }
                        }

                        List<String> suggestions = GripRegexStore.extractSuggestions(message);
                        discovered.addAll(suggestions);
                    }
                }
            }

        } catch (Exception e) {

            if (e.getMessage() != null && !e.getMessage().contains("Cannot query field")) {
                reportProgress("Bucket probe error: " + e.getMessage());
            }
        }

        return discovered;
    }

    private void probeFieldTypes(String endpoint) {
        GripType queryType = discoveredTypes.get("Query");
        if (queryType == null) return;

        for (GripField field : queryType.getFields()) {
            if (cancelled) break;

            try {

                String query = "query { " + field.getName() + " { __typename } }";
                JsonObject response = httpClient.sendQuery(endpoint, query);

                String errorMessage = getFirstErrorMessage(response);

                if (errorMessage == null && response != null && response.has("data") && !response.get("data").isJsonNull()) {

                    JsonObject data = response.getAsJsonObject("data");
                    if (data != null && data.has(field.getName()) && !data.get(field.getName()).isJsonNull()) {
                        JsonElement fieldData = data.get(field.getName());
                        if (fieldData.isJsonObject()) {
                            JsonObject fieldObj = fieldData.getAsJsonObject();
                            if (fieldObj.has("__typename") && !fieldObj.get("__typename").isJsonNull()) {
                                String typeName = fieldObj.get("__typename").getAsString();
                                field.setType(GripTypeRef.simple(typeName));

                                if (!exploredTypes.contains(typeName)) {
                                    explorationQueue.add(new TypeExplorationTask(typeName, field.getName(), 1));
                                }
                            }
                        }
                    }
                } else if (GripRegexStore.indicatesObjectType(errorMessage)) {

                    GripRegexStore.MatchResult result = GripRegexStore.extractFieldInfo(errorMessage);
                    if (result != null && result.getType() != null) {
                        String typeName = GripRegexStore.normalizeTypeName(result.getType());
                        field.setType(GripTypeRef.simple(typeName));

                        if (!exploredTypes.contains(typeName)) {
                            explorationQueue.add(new TypeExplorationTask(typeName, field.getName(), 1));
                        }
                    }
                } else if (GripRegexStore.indicatesScalarType(errorMessage)) {

                    GripRegexStore.MatchResult result = GripRegexStore.extractFieldInfo(errorMessage);
                    if (result != null && result.getType() != null) {
                        String typeName = GripRegexStore.normalizeTypeName(result.getType());
                        field.setType(new GripTypeRef(typeName, GripTypeKind.SCALAR, false, false, false));
                    }
                }

                totalProbes.incrementAndGet();
                delay();

            } catch (Exception e) {

                reportProgress("Type probe failed for field " + field.getName() + ": " + e.getMessage());
            }
        }
    }

    private void exploreDiscoveredTypes(String endpoint) {
        while (!explorationQueue.isEmpty() && !cancelled) {
            TypeExplorationTask task = explorationQueue.poll();
            if (task == null || task.depth > MAX_DEPTH) continue;
            if (exploredTypes.contains(task.typeName)) continue;

            exploredTypes.add(task.typeName);
            reportProgress("Exploring type: " + task.typeName + " (depth " + task.depth + ")");

            Set<String> typeFields = probeTypeFields(endpoint, task.parentPath);

            if (!typeFields.isEmpty()) {
                GripType type = new GripType(task.typeName, GripTypeKind.OBJECT);
                for (String fieldName : typeFields) {
                    GripField field = new GripField(fieldName, GripTypeRef.simple("Unknown"));
                    type.addField(field);
                }
                discoveredTypes.put(task.typeName, type);
            }
        }
    }

    private Set<String> probeTypeFields(String endpoint, String parentPath) {
        Set<String> discoveredFields = ConcurrentHashMap.newKeySet();

        List<List<String>> buckets = createBuckets(wordlist, BUCKET_SIZE / 2);

        for (List<String> bucket : buckets) {
            if (cancelled) break;

            try {
                semaphore.acquire();

                StringBuilder query = new StringBuilder("query GripNestedProbe { ");
                query.append(parentPath).append(" {");
                for (int i = 0; i < bucket.size(); i++) {
                    query.append(" g").append(i).append(": ").append(bucket.get(i));
                }
                query.append(" } }");

                JsonObject response = httpClient.sendQuery(endpoint, query.toString());

                if (response.has("data") && !response.get("data").isJsonNull()) {
                    discoveredFields.addAll(extractFieldsFromData(response.getAsJsonObject("data"), parentPath));
                }

                if (response.has("errors")) {
                    JsonArray errors = response.getAsJsonArray("errors");
                    for (JsonElement errorElement : errors) {
                        if (errorElement.isJsonObject()) {
                            String message = getErrorMessage(errorElement.getAsJsonObject());
                            if (GripRegexStore.indicatesValidField(message)) {
                                GripRegexStore.MatchResult result = GripRegexStore.extractFieldInfo(message);
                                if (result != null && result.getField() != null) {
                                    discoveredFields.add(result.getField());
                                }
                            }
                            List<String> suggestions = GripRegexStore.extractSuggestions(message);
                            discoveredFields.addAll(suggestions);
                        }
                    }
                }

                totalProbes.incrementAndGet();
                if (!discoveredFields.isEmpty()) successfulProbes.incrementAndGet();

            } catch (Exception e) {

                reportProgress("Nested probe failed: " + e.getMessage());
            } finally {
                semaphore.release();
                delay();
            }
        }

        return discoveredFields;
    }

    private Set<String> extractFieldsFromData(JsonObject data, String path) {
        Set<String> fields = new HashSet<>();

        String[] parts = path.split("\\s+");
        JsonObject current = data;

        for (String part : parts) {
            if (current.has(part) && current.get(part).isJsonObject()) {
                current = current.getAsJsonObject(part);
            } else {
                return fields;
            }
        }

        for (String key : current.keySet()) {

            if (key.startsWith("g") && key.substring(1).matches("\\d+")) {

                fields.add(key);
            } else {
                fields.add(key);
            }
        }

        return fields;
    }

    private GripSchema buildSchema(String endpoint) {
        GripSchema schema = new GripSchema();
        schema.setSourceEndpoint(endpoint);
        schema.setReconstructed(true);

        for (GripType type : discoveredTypes.values()) {
            schema.addType(type);
        }

        if (discoveredTypes.containsKey("Query")) {
            schema.setQueryTypeName("Query");
        }
        if (discoveredTypes.containsKey("Mutation")) {
            schema.setMutationTypeName("Mutation");
        }

        for (String scalarName : Arrays.asList("String", "Int", "Float", "Boolean", "ID")) {
            GripType scalar = new GripType(scalarName, GripTypeKind.SCALAR);
            schema.addType(scalar);
        }

        return schema;
    }

    private GripSchema createEmptySchema(String endpoint) {
        GripSchema schema = new GripSchema();
        schema.setSourceEndpoint(endpoint);
        schema.setReconstructed(true);
        schema.setPartial(true);
        return schema;
    }

    private <T> List<List<T>> createBuckets(List<T> list, int bucketSize) {
        List<List<T>> buckets = new ArrayList<>();
        for (int i = 0; i < list.size(); i += bucketSize) {
            buckets.add(list.subList(i, Math.min(i + bucketSize, list.size())));
        }
        return buckets;
    }

    private String getErrorMessage(JsonObject error) {
        if (error.has("message")) {
            return error.get("message").getAsString();
        }
        return null;
    }

    private String getFirstErrorMessage(JsonObject response) {
        if (response.has("errors") && response.get("errors").isJsonArray()) {
            JsonArray errors = response.getAsJsonArray("errors");
            if (errors.size() > 0 && errors.get(0).isJsonObject()) {
                return getErrorMessage(errors.get(0).getAsJsonObject());
            }
        }
        return null;
    }

    private List<String> loadWordlist() {
        List<String> words = new ArrayList<>();

        java.io.InputStream resourceStream = getClass().getResourceAsStream("/wordlists/graphql-fields.txt");
        if (resourceStream == null) {
            reportProgress("Wordlist resource not found, using default wordlist");
            return getDefaultWordlist();
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    words.add(line);
                }
            }
        } catch (IOException e) {
            reportProgress("Error loading wordlist: " + e.getMessage());
            return getDefaultWordlist();
        }

        return words.isEmpty() ? getDefaultWordlist() : words;
    }

    private List<String> getDefaultWordlist() {
        return new ArrayList<>(Arrays.asList(
                "user", "users", "me", "viewer", "node", "nodes", "query",
                "id", "name", "email", "password", "username", "profile",
                "login", "logout", "register", "authenticate",
                "create", "update", "delete", "get", "list", "search",
                "admin", "config", "settings", "system",
                "__schema", "__type", "__typename"
        ));
    }

    private void reportProgress(String message) {
        if (progressCallback != null) {
            progressCallback.accept(message);
        }
    }

    private void delay() {
        try {
            Thread.sleep(REQUEST_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown() {
        cancelled = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    public int getDiscoveredTypeCount() {
        return discoveredTypes.size();
    }

    public Set<String> getAllDiscoveredFields() {
        Set<String> allFields = new HashSet<>();
        for (GripType type : discoveredTypes.values()) {
            for (GripField field : type.getFields()) {
                allFields.add(field.getName());
            }
        }
        return allFields;
    }

    public void addToWordlist(Collection<String> words) {
        wordlist.addAll(words);
    }

    public void setWordlist(List<String> words) {
        wordlist.clear();
        wordlist.addAll(words);
    }
}
