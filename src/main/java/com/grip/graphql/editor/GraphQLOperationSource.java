package com.grip.graphql.editor;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grip.graphql.document.Directive;
import com.grip.graphql.document.Field;
import com.grip.graphql.document.GraphQLDocument;
import com.grip.graphql.document.GraphQLParser;
import com.grip.graphql.document.GraphQLPrinter;
import com.grip.graphql.document.GraphQLSyntaxException;
import com.grip.graphql.document.OperationDefinition;
import com.grip.graphql.document.OperationType;
import com.grip.graphql.document.VariableDefinition;

import java.util.ArrayList;
import java.util.List;

final class GraphQLOperationSource {

    private static final String DEFAULT_FIELD = "__typename";

    private final String query;
    private final String operationName;
    private final JsonObject variables;
    private final GraphQLDocument document;
    private final OperationDefinition operation;
    private final String syntaxError;

    private GraphQLOperationSource(String query, String operationName, JsonObject variables,
                                   GraphQLDocument document, OperationDefinition operation,
                                   String syntaxError) {
        this.query = query;
        this.operationName = operationName;
        this.variables = variables;
        this.document = document;
        this.operation = operation;
        this.syntaxError = syntaxError;
    }

    static GraphQLOperationSource empty() {
        return new GraphQLOperationSource(null, null, null, null, null, null);
    }

    static GraphQLOperationSource from(HttpRequest request) {
        if (request == null) {
            return empty();
        }
        return from(request.method(), request.path(), GripRequestEditor.contentTypeOf(request),
                request.bodyToString());
    }

    static GraphQLOperationSource from(String method, String path, String contentType, String body) {
        String query = GraphQLRequestDetector.extractQuery(method, path, contentType, body);
        JsonObject envelope = GraphQLRequestDetector.extractEnvelope(method, path, contentType, body);

        String operationName = stringMember(envelope, "operationName");
        JsonObject variables = objectMember(envelope, "variables");

        GraphQLDocument document = null;
        OperationDefinition operation = null;
        String syntaxError = null;

        if (query != null && !query.isBlank()) {
            try {
                document = GraphQLParser.parse(query);
                operation = document.operation(operationName);
                if (operation == null) {
                    operation = document.firstOperation();
                }
            } catch (GraphQLSyntaxException e) {
                syntaxError = e.getMessage();
            }
        }

        return new GraphQLOperationSource(query, operationName, variables,
                document, operation, syntaxError);
    }

    String query() {
        return query;
    }

    String operationName() {
        return operationName;
    }

    JsonObject variables() {
        return variables;
    }

    String syntaxError() {
        return syntaxError;
    }

    boolean hasOperation() {
        return operation != null;
    }

    OperationType operationType() {
        return operation != null ? operation.operationType() : OperationType.QUERY;
    }

    String operationKeyword() {
        return operationType().keyword();
    }

    boolean isMutation() {
        return operationType() == OperationType.MUTATION;
    }

    String rootFieldName() {
        if (operation != null && operation.firstRootField() != null) {
            return operation.firstRootField().name();
        }
        return DEFAULT_FIELD;
    }

    String rootFieldCall() {
        if (operation != null && operation.firstRootField() != null) {
            return GraphQLPrinter.print(operation.firstRootField());
        }
        return null;
    }

    String directedRootFieldCall(List<Directive> extra) {
        Field root = operation != null ? operation.firstRootField() : null;
        if (root == null) {
            root = Field.of(DEFAULT_FIELD);
        }
        List<Directive> merged = new ArrayList<>(root.directives());
        merged.addAll(extra);
        return GraphQLPrinter.print(root.withDirectives(merged));
    }

    List<VariableDefinition> declaredVariables() {
        return operation != null ? operation.variableDefinitions() : List.of();
    }

    String renamedOperation(String name) {
        if (document == null || operation == null) {
            return null;
        }
        return GraphQLPrinter.print(document.withFirstOperation(operation.withName(name)));
    }

    private static String stringMember(JsonObject envelope, String name) {
        if (envelope == null || !envelope.has(name)) {
            return null;
        }
        JsonElement member = envelope.get(name);
        if (!member.isJsonPrimitive() || !member.getAsJsonPrimitive().isString()) {
            return null;
        }
        String value = member.getAsString();
        return value.isBlank() ? null : value;
    }

    private static JsonObject objectMember(JsonObject envelope, String name) {
        if (envelope == null || !envelope.has(name) || !envelope.get(name).isJsonObject()) {
            return null;
        }
        JsonObject member = envelope.getAsJsonObject(name);
        return member.keySet().isEmpty() ? null : member;
    }
}
