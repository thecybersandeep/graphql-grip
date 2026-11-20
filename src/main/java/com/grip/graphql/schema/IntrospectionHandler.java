package com.grip.graphql.schema;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.grip.graphql.http.GraphQLResponseAnalyzer;
import com.grip.graphql.http.GraphQLTransport;
import com.grip.graphql.http.GripHttpClient;
import com.grip.graphql.model.schema.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class IntrospectionHandler {

    public static final String FULL_INTROSPECTION_QUERY = """
        query GripIntrospection {
          __schema {
            queryType { name }
            mutationType { name }
            subscriptionType { name }
            types {
              ...FullType
            }
            directives {
              name
              description
              locations
              args {
                ...InputValue
              }
            }
          }
        }

        fragment FullType on __Type {
          kind
          name
          description
          fields(includeDeprecated: true) {
            name
            description
            args {
              ...InputValue
            }
            type {
              ...TypeRef
            }
            isDeprecated
            deprecationReason
          }
          inputFields {
            ...InputValue
          }
          interfaces {
            ...TypeRef
          }
          enumValues(includeDeprecated: true) {
            name
            description
            isDeprecated
            deprecationReason
          }
          possibleTypes {
            ...TypeRef
          }
        }

        fragment InputValue on __InputValue {
          name
          description
          type {
            ...TypeRef
          }
          defaultValue
        }

        fragment TypeRef on __Type {
          kind
          name
          ofType {
            kind
            name
            ofType {
              kind
              name
              ofType {
                kind
                name
                ofType {
                  kind
                  name
                  ofType {
                    kind
                    name
                    ofType {
                      kind
                      name
                      ofType {
                        kind
                        name
                      }
                    }
                  }
                }
              }
            }
          }
        }
        """;

    public static final String MINIMAL_INTROSPECTION_QUERY = """
        query GripMinimalIntrospection {
          __schema {
            queryType { name }
            mutationType { name }
            subscriptionType { name }
            types {
              kind
              name
              fields {
                name
                type {
                  kind
                  name
                  ofType {
                    kind
                    name
                  }
                }
              }
            }
          }
        }
        """;

    private final GripHttpClient httpClient;
    private final ExecutorService executor;

    public IntrospectionHandler(GripHttpClient httpClient) {
        this.httpClient = httpClient;
        this.executor = httpClient.getExecutor();
    }

    public CompletableFuture<GripSchema> fetchSchema(String endpoint) {
        return fetchSchema(endpoint, GraphQLTransport.POST_JSON, IntrospectionBypass.NONE);
    }

    public CompletableFuture<GripSchema> fetchSchema(String endpoint, GraphQLTransport transport,
                                                     IntrospectionBypass bypass) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                GripSchema full = fetch(endpoint, transport, bypass,
                        FULL_INTROSPECTION_QUERY, "Introspection (Full)");
                if (full != null) {
                    return full;
                }

                GripSchema minimal = fetch(endpoint, transport, bypass,
                        MINIMAL_INTROSPECTION_QUERY, "Introspection (Minimal)");
                if (minimal != null) {
                    minimal.setPartial(true);
                }
                return minimal;
            } catch (GripHttpClient.GripAuthException e) {
                throw new CompletionException(e);
            }
        }, executor).orTimeout(120, TimeUnit.SECONDS);
    }

    private GripSchema fetch(String endpoint, GraphQLTransport transport, IntrospectionBypass bypass,
                             String query, String label) throws GripHttpClient.GripAuthException {
        try {
            HttpRequestResponse response = httpClient.send(transport, endpoint, bypass.apply(query), label);
            if (response == null || response.response() == null) {
                return null;
            }

            String body = response.response().bodyToString();
            GraphQLResponseAnalyzer.Analysis analysis = GraphQLResponseAnalyzer.analyzeIntrospectionResponse(
                    response.response().statusCode(),
                    response.response().headerValue("Content-Type"),
                    body);

            if (!analysis.isExpectedFieldAvailable()) {
                return null;
            }

            JsonObject schemaJson = JsonParser.parseString(body).getAsJsonObject()
                    .getAsJsonObject("data").getAsJsonObject("__schema");
            return parseIntrospectionResult(schemaJson, endpoint, httpClient);
        } catch (GripHttpClient.GripAuthException e) {
            throw e;
        } catch (Exception e) {
            httpClient.logError(label + " failed for " + endpoint, e);
            return null;
        }
    }

    private static void linkTypeReferences(GripSchema schema, JsonArray types) {
        for (JsonElement typeElement : types) {
            if (typeElement.isJsonNull() || !typeElement.isJsonObject()) {
                continue;
            }

            JsonObject typeJson = typeElement.getAsJsonObject();
            if (!typeJson.has("name") || typeJson.get("name").isJsonNull()) {
                continue;
            }

            GripType type = schema.getType(typeJson.get("name").getAsString());
            if (type == null) {
                continue;
            }

            for (String name : referencedNames(typeJson, "interfaces")) {
                GripType target = schema.getType(name);
                if (target != null) {
                    type.addInterface(target);
                }
            }
            for (String name : referencedNames(typeJson, "possibleTypes")) {
                GripType target = schema.getType(name);
                if (target != null) {
                    type.addPossibleType(target);
                }
            }
        }
    }

    private static List<String> referencedNames(JsonObject typeJson, String member) {
        if (!typeJson.has(member) || !typeJson.get(member).isJsonArray()) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        for (JsonElement element : typeJson.getAsJsonArray(member)) {
            if (element.isJsonNull() || !element.isJsonObject()) {
                continue;
            }
            JsonObject reference = element.getAsJsonObject();
            if (reference.has("name") && !reference.get("name").isJsonNull()) {
                names.add(reference.get("name").getAsString());
            }
        }
        return names;
    }

    public static GripSchema parseIntrospectionResult(JsonObject schemaJson, String endpoint) {
        return parseIntrospectionResult(schemaJson, endpoint, null);
    }

    public static GripSchema parseIntrospectionResult(JsonObject schemaJson, String endpoint,
                                                      GripHttpClient logger) {
        GripSchema schema = new GripSchema();
        schema.setSourceEndpoint(endpoint);

        if (schemaJson.has("queryType") && !schemaJson.get("queryType").isJsonNull()) {
            JsonObject queryType = schemaJson.getAsJsonObject("queryType");
            if (queryType.has("name") && !queryType.get("name").isJsonNull()) {
                schema.setQueryTypeName(queryType.get("name").getAsString());
            }
        }
        if (schemaJson.has("mutationType") && !schemaJson.get("mutationType").isJsonNull()) {
            JsonObject mutationType = schemaJson.getAsJsonObject("mutationType");
            if (mutationType.has("name") && !mutationType.get("name").isJsonNull()) {
                schema.setMutationTypeName(mutationType.get("name").getAsString());
            }
        }
        if (schemaJson.has("subscriptionType") && !schemaJson.get("subscriptionType").isJsonNull()) {
            JsonObject subscriptionType = schemaJson.getAsJsonObject("subscriptionType");
            if (subscriptionType.has("name") && !subscriptionType.get("name").isJsonNull()) {
                schema.setSubscriptionTypeName(subscriptionType.get("name").getAsString());
            }
        }

        if (schemaJson.has("types") && schemaJson.get("types").isJsonArray()) {
            JsonArray types = schemaJson.getAsJsonArray("types");
            for (JsonElement typeElement : types) {
                if (typeElement.isJsonNull() || !typeElement.isJsonObject()) {
                    continue;
                }
                GripType type = parseType(typeElement.getAsJsonObject());
                if (type != null) {
                    schema.addType(type);
                }
            }
            linkTypeReferences(schema, types);
        }

        if (schemaJson.has("directives") && schemaJson.get("directives").isJsonArray()) {
            JsonArray directives = schemaJson.getAsJsonArray("directives");
            for (JsonElement directiveElement : directives) {
                GripDirective directive = parseDirective(directiveElement.getAsJsonObject(), logger);
                if (directive != null) {
                    schema.addDirective(directive);
                }
            }
        }

        return schema;
    }

    private static GripType parseType(JsonObject typeJson) {
        String name = typeJson.has("name") && !typeJson.get("name").isJsonNull()
            ? typeJson.get("name").getAsString() : null;
        if (name == null) return null;

        String kindStr = typeJson.has("kind") ? typeJson.get("kind").getAsString() : "OBJECT";
        GripTypeKind kind = GripTypeKind.fromIntrospection(kindStr);

        GripType type = new GripType(name, kind);

        if (typeJson.has("description") && !typeJson.get("description").isJsonNull()) {
            type.setDescription(typeJson.get("description").getAsString());
        }

        if (typeJson.has("fields") && typeJson.get("fields").isJsonArray()) {
            JsonArray fields = typeJson.getAsJsonArray("fields");
            for (JsonElement fieldElement : fields) {
                if (!fieldElement.isJsonNull()) {
                    GripField field = parseField(fieldElement.getAsJsonObject());
                    if (field != null) {
                        type.addField(field);
                    }
                }
            }
        }

        if (typeJson.has("inputFields") && typeJson.get("inputFields").isJsonArray()) {
            JsonArray inputFields = typeJson.getAsJsonArray("inputFields");
            for (JsonElement fieldElement : inputFields) {
                if (!fieldElement.isJsonNull()) {
                    GripField field = parseInputField(fieldElement.getAsJsonObject());
                    if (field != null) {
                        type.addInputField(field);
                    }
                }
            }
        }

        if (typeJson.has("enumValues") && typeJson.get("enumValues").isJsonArray()) {
            JsonArray enumValues = typeJson.getAsJsonArray("enumValues");
            for (JsonElement enumElement : enumValues) {
                if (!enumElement.isJsonNull()) {
                    JsonObject enumJson = enumElement.getAsJsonObject();
                    String enumName = enumJson.get("name").getAsString();
                    GripEnumValue enumValue = new GripEnumValue(enumName);

                    if (enumJson.has("description") && !enumJson.get("description").isJsonNull()) {
                        enumValue.setDescription(enumJson.get("description").getAsString());
                    }
                    if (enumJson.has("isDeprecated")) {
                        enumValue.setDeprecated(enumJson.get("isDeprecated").getAsBoolean());
                    }
                    if (enumJson.has("deprecationReason") && !enumJson.get("deprecationReason").isJsonNull()) {
                        enumValue.setDeprecationReason(enumJson.get("deprecationReason").getAsString());
                    }

                    type.addEnumValue(enumValue);
                }
            }
        }

        return type;
    }

    private static GripField parseField(JsonObject fieldJson) {
        String name = fieldJson.has("name") ? fieldJson.get("name").getAsString() : null;
        if (name == null) return null;

        GripTypeRef typeRef = parseTypeRef(fieldJson.getAsJsonObject("type"));
        GripField field = new GripField(name, typeRef);

        if (fieldJson.has("description") && !fieldJson.get("description").isJsonNull()) {
            field.setDescription(fieldJson.get("description").getAsString());
        }

        if (fieldJson.has("isDeprecated")) {
            field.setDeprecated(fieldJson.get("isDeprecated").getAsBoolean());
        }
        if (fieldJson.has("deprecationReason") && !fieldJson.get("deprecationReason").isJsonNull()) {
            field.setDeprecationReason(fieldJson.get("deprecationReason").getAsString());
        }

        if (fieldJson.has("args") && fieldJson.get("args").isJsonArray()) {
            JsonArray args = fieldJson.getAsJsonArray("args");
            for (JsonElement argElement : args) {
                if (!argElement.isJsonNull()) {
                    GripArgument arg = parseArgument(argElement.getAsJsonObject());
                    if (arg != null) {
                        field.addArgument(arg);
                    }
                }
            }
        }

        return field;
    }

    private static GripField parseInputField(JsonObject inputFieldJson) {
        String name = inputFieldJson.has("name") ? inputFieldJson.get("name").getAsString() : null;
        if (name == null) return null;

        GripTypeRef typeRef = parseTypeRef(inputFieldJson.getAsJsonObject("type"));
        GripField field = new GripField(name, typeRef);

        if (inputFieldJson.has("description") && !inputFieldJson.get("description").isJsonNull()) {
            field.setDescription(inputFieldJson.get("description").getAsString());
        }

        return field;
    }

    private static GripArgument parseArgument(JsonObject argJson) {
        String name = argJson.has("name") ? argJson.get("name").getAsString() : null;
        if (name == null) return null;

        GripTypeRef typeRef = parseTypeRef(argJson.getAsJsonObject("type"));
        GripArgument arg = new GripArgument(name, typeRef);

        if (argJson.has("description") && !argJson.get("description").isJsonNull()) {
            arg.setDescription(argJson.get("description").getAsString());
        }
        if (argJson.has("defaultValue") && !argJson.get("defaultValue").isJsonNull()) {
            arg.setDefaultValue(argJson.get("defaultValue").getAsString());
        }

        return arg;
    }

    private static GripTypeRef parseTypeRef(JsonObject typeJson) {
        if (typeJson == null) return null;

        String kind = typeJson.has("kind") ? typeJson.get("kind").getAsString() : null;
        String name = typeJson.has("name") && !typeJson.get("name").isJsonNull()
            ? typeJson.get("name").getAsString() : null;

        if ("NON_NULL".equals(kind) || "LIST".equals(kind)) {
            JsonObject ofType = typeJson.has("ofType") && !typeJson.get("ofType").isJsonNull()
                ? typeJson.getAsJsonObject("ofType") : null;

            if (ofType != null) {
                GripTypeRef innerRef = parseTypeRef(ofType);
                if (innerRef != null) {
                    if ("NON_NULL".equals(kind)) {
                        return new GripTypeRef(
                            innerRef.getName(),
                            innerRef.getKind(),
                            innerRef.isList(),
                            true,
                            innerRef.isNonNullItem()
                        );
                    } else {
                        return new GripTypeRef(
                            innerRef.getName(),
                            innerRef.getKind(),
                            true,
                            false,
                            innerRef.isNonNull()
                        );
                    }
                }
            }
        }

        if (name != null) {
            GripTypeKind typeKind = kind != null ? GripTypeKind.fromIntrospection(kind) : GripTypeKind.OBJECT;
            return new GripTypeRef(name, typeKind, false, false, false);
        }

        return null;
    }

    private static GripDirective parseDirective(JsonObject directiveJson, GripHttpClient logger) {
        String name = directiveJson.has("name") ? directiveJson.get("name").getAsString() : null;
        if (name == null) return null;

        GripDirective directive = new GripDirective(name);

        if (directiveJson.has("description") && !directiveJson.get("description").isJsonNull()) {
            directive.setDescription(directiveJson.get("description").getAsString());
        }

        if (directiveJson.has("locations") && directiveJson.get("locations").isJsonArray()) {
            JsonArray locations = directiveJson.getAsJsonArray("locations");
            for (JsonElement locElement : locations) {
                try {
                    GripDirective.Location loc = GripDirective.Location.valueOf(locElement.getAsString());
                    directive.addLocation(loc);
                } catch (IllegalArgumentException e) {
                    if (logger != null) {
                            logger.logError("Unsupported directive location " + locElement, e);
                        }
                }
            }
        }

        if (directiveJson.has("args") && directiveJson.get("args").isJsonArray()) {
            JsonArray args = directiveJson.getAsJsonArray("args");
            for (JsonElement argElement : args) {
                if (!argElement.isJsonNull()) {
                    GripArgument arg = parseArgument(argElement.getAsJsonObject());
                    if (arg != null) {
                        directive.addArgument(arg);
                    }
                }
            }
        }

        return directive;
    }
}
