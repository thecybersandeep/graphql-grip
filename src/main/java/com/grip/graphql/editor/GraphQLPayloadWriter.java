package com.grip.graphql.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.grip.graphql.document.GraphQLDocument;
import com.grip.graphql.document.GraphQLParser;
import com.grip.graphql.document.GraphQLPrinter;
import com.grip.graphql.document.GraphQLVariables;
import com.grip.graphql.document.OperationDefinition;
import com.grip.graphql.document.VariableDefinition;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

final class GraphQLPayloadWriter {

    private static final Gson PRETTY_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private GraphQLPayloadWriter() {
    }

    static String json(JsonObject body) {
        return PRETTY_GSON.toJson(body);
    }

    static String json(JsonArray body) {
        return PRETTY_GSON.toJson(body);
    }

    static JsonObject body(GraphQLOperationSource source, String query) {
        JsonObject body = new JsonObject();

        GraphQLDocument document = source == null ? null : GraphQLParser.tryParse(query);
        OperationDefinition operation = document == null ? null : document.firstOperation();

        if (operation == null) {
            body.addProperty("query", query);
            return body;
        }

        Set<String> referenced = GraphQLVariables.referencedBy(document, operation);
        if (referenced.isEmpty()) {
            body.addProperty("query", query);
            return body;
        }

        List<VariableDefinition> declarations = GraphQLVariables.reconcile(
                operation.variableDefinitions(), referenced, source.declaredVariables());

        if (declarations.equals(operation.variableDefinitions())) {
            body.addProperty("query", query);
        } else {
            body.addProperty("query", GraphQLPrinter.print(document.withFirstOperation(
                    operation.withVariableDefinitions(declarations))));
        }

        if (operation.name() != null) {
            body.addProperty("operationName", operation.name());
        }

        JsonObject variables = subsetOf(source.variables(), referenced);
        if (variables != null) {
            body.add("variables", variables);
        }
        return body;
    }

    static JsonArray repeated(GraphQLOperationSource source, String query, int count) {
        JsonArray batch = new JsonArray();
        for (int i = 0; i < count; i++) {
            batch.add(body(source, query));
        }
        return batch;
    }

    static JsonArray alternating(GraphQLOperationSource source, String even, String odd, int count) {
        JsonArray batch = new JsonArray();
        for (int i = 0; i < count; i++) {
            batch.add(body(source, i % 2 == 0 ? even : odd));
        }
        return batch;
    }

    static JsonArray renamedSeries(GraphQLOperationSource source, String prefix,
                                   String fallback, int count) {
        JsonArray batch = new JsonArray();
        for (int i = 0; i < count; i++) {
            String name = prefix + i;
            String renamed = source == null ? null : source.renamedOperation(name);
            if (renamed == null) {
                batch.add(body(source, fallback));
            } else {
                JsonObject item = body(source, renamed);
                item.addProperty("operationName", name);
                batch.add(item);
            }
        }
        return batch;
    }

    static String parameters(JsonObject envelope, GraphQLOperationSource source) {
        String query = stringMember(envelope, "query");

        StringBuilder out = new StringBuilder("query=").append(encode(query == null ? "" : query));

        String operationName = stringMember(envelope, "operationName");
        if (operationName == null && source != null) {
            operationName = source.operationName();
        }
        if (operationName != null) {
            out.append("&operationName=").append(encode(operationName));
        }

        appendObjectMember(out, envelope, "variables");
        appendObjectMember(out, envelope, "extensions");
        return out.toString();
    }

    static String multipartBody(JsonObject envelope, GraphQLOperationSource source, String boundary) {
        JsonObject operations = envelope.deepCopy();
        if (!operations.has("operationName") && source != null && source.operationName() != null) {
            operations.addProperty("operationName", source.operationName());
        }

        return "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"operations\"\r\n\r\n"
                + operations + "\r\n"
                + "--" + boundary + "--\r\n";
    }

    private static void appendObjectMember(StringBuilder out, JsonObject envelope, String name) {
        if (envelope != null && envelope.has(name) && envelope.get(name).isJsonObject()) {
            out.append('&').append(name).append('=')
                    .append(encode(envelope.getAsJsonObject(name).toString()));
        }
    }

    private static String stringMember(JsonObject envelope, String name) {
        if (envelope == null || !envelope.has(name) || !envelope.get(name).isJsonPrimitive()) {
            return null;
        }
        String value = envelope.get(name).getAsString();
        return value.isBlank() ? null : value;
    }

    private static JsonObject subsetOf(JsonObject variables, Set<String> referenced) {
        if (variables == null) {
            return null;
        }

        JsonObject subset = new JsonObject();
        for (String name : referenced) {
            if (variables.has(name)) {
                subset.add(name, variables.get(name).deepCopy());
            }
        }
        return subset.keySet().isEmpty() ? null : subset;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
