package com.grip.graphql.schema;

import com.grip.graphql.http.GripHttpClient;
import com.grip.graphql.model.schema.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

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

    public IntrospectionHandler(GripHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public CompletableFuture<GripSchema> fetchSchema(String endpoint) {
        return CompletableFuture.supplyAsync(() -> {
            try {

                JsonObject response = httpClient.sendQueryAndLog(endpoint, FULL_INTROSPECTION_QUERY, "Introspection (Full)");

                if (response != null && response.has("data") && !response.get("data").isJsonNull()) {
                    JsonObject data = response.getAsJsonObject("data");
                    if (data.has("__schema") && !data.get("__schema").isJsonNull()) {
                        return parseIntrospectionResult(data.getAsJsonObject("__schema"), endpoint);
                    }
                }

                response = httpClient.sendQueryAndLog(endpoint, MINIMAL_INTROSPECTION_QUERY, "Introspection (Minimal)");
                if (response != null && response.has("data") && !response.get("data").isJsonNull()) {
                    JsonObject data = response.getAsJsonObject("data");
                    if (data.has("__schema") && !data.get("__schema").isJsonNull()) {
                        GripSchema schema = parseIntrospectionResult(data.getAsJsonObject("__schema"), endpoint);
                        schema.setPartial(true);
                        return schema;
                    }
                }

                return null;
            } catch (Exception e) {
                return null;
            }
        });
    }

    public CompletableFuture<Boolean> isIntrospectionEnabled(String endpoint) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String simpleQuery = "query { __schema { queryType { name } } }";
                JsonObject response = httpClient.sendQueryAndLog(endpoint, simpleQuery, "Introspection Check");

                if (response == null || !response.has("data") || response.get("data").isJsonNull()) {
                    return false;
                }
                JsonObject data = response.getAsJsonObject("data");
                return data != null && data.has("__schema");
            } catch (Exception e) {
                return false;
            }
        });
    }

    public GripSchema parseIntrospectionResult(JsonObject schemaJson, String endpoint) {
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
                GripType type = parseType(typeElement.getAsJsonObject());
                if (type != null) {
                    schema.addType(type);
                }
            }
        }

        if (schemaJson.has("directives") && schemaJson.get("directives").isJsonArray()) {
            JsonArray directives = schemaJson.getAsJsonArray("directives");
            for (JsonElement directiveElement : directives) {
                GripDirective directive = parseDirective(directiveElement.getAsJsonObject());
                if (directive != null) {
                    schema.addDirective(directive);
                }
            }
        }

        return schema;
    }

    private GripType parseType(JsonObject typeJson) {
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

    private GripField parseField(JsonObject fieldJson) {
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

    private GripField parseInputField(JsonObject inputFieldJson) {
        String name = inputFieldJson.has("name") ? inputFieldJson.get("name").getAsString() : null;
        if (name == null) return null;

        GripTypeRef typeRef = parseTypeRef(inputFieldJson.getAsJsonObject("type"));
        GripField field = new GripField(name, typeRef);

        if (inputFieldJson.has("description") && !inputFieldJson.get("description").isJsonNull()) {
            field.setDescription(inputFieldJson.get("description").getAsString());
        }

        return field;
    }

    private GripArgument parseArgument(JsonObject argJson) {
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

    private GripTypeRef parseTypeRef(JsonObject typeJson) {
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

    private GripDirective parseDirective(JsonObject directiveJson) {
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
