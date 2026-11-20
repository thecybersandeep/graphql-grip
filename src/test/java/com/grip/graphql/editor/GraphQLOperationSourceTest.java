package com.grip.graphql.editor;

import com.google.gson.JsonObject;
import com.grip.graphql.document.OperationType;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLOperationSourceTest {

    private static final String LOGIN_QUERY =
            "\n    mutation login($input: LoginInput!) {\n"
                    + "        login(input: $input) {\n"
                    + "            token\n"
                    + "            success\n"
                    + "        }\n"
                    + "    }";

    @Test
    void readsQueryOperationNameAndVariablesFromAJsonBody() {
        GraphQLOperationSource source = GraphQLOperationSource.from(
                "POST", "/graphql/v1", "application/json", loginBody());

        assertEquals("login", source.operationName());
        assertTrue(source.hasOperation());
        assertNull(source.syntaxError());
        assertEquals(OperationType.MUTATION, source.operationType());
        assertEquals("mutation", source.operationKeyword());
        assertTrue(source.isMutation());
        assertEquals("login", source.rootFieldName());

        assertNotNull(source.variables());
        assertEquals("test", source.variables().getAsJsonObject("input").get("username").getAsString());
    }

    @Test
    void printsTheRootFieldCallIncludingItsVariableReference() {
        GraphQLOperationSource source = GraphQLOperationSource.from(
                "POST", "/graphql/v1", "application/json", loginBody());

        String expected = """
                login(input: $input) {
                  token
                  success
                }""";

        assertEquals(expected, source.rootFieldCall());
    }

    @Test
    void exposesTheDeclarationsTheCapturedOperationCarries() {
        GraphQLOperationSource source = GraphQLOperationSource.from(
                "POST", "/graphql/v1", "application/json", loginBody());

        assertEquals(1, source.declaredVariables().size());
        assertEquals("input", source.declaredVariables().get(0).name());
        assertTrue(source.declaredVariables().get(0).isRequired());
    }

    @Test
    void readsVariablesFromAFormEncodedBody() {
        String body = "query=" + encode(LOGIN_QUERY)
                + "&operationName=login"
                + "&variables=" + encode("{\"input\":{\"username\":\"test\"}}");

        GraphQLOperationSource source = GraphQLOperationSource.from(
                "POST", "/api", "application/x-www-form-urlencoded", body);

        assertEquals("login", source.operationName());
        assertEquals("login", source.rootFieldName());
        assertEquals("test", source.variables().getAsJsonObject("input").get("username").getAsString());
    }

    @Test
    void readsVariablesFromAGetQueryString() {
        String path = "/api?query=" + encode("query Viewer($id: ID!) { user(id: $id) { name } }")
                + "&operationName=Viewer"
                + "&variables=" + encode("{\"id\":\"3\"}");

        GraphQLOperationSource source = GraphQLOperationSource.from("GET", path, null, null);

        assertEquals("Viewer", source.operationName());
        assertEquals("user", source.rootFieldName());
        assertEquals("3", source.variables().get("id").getAsString());
    }

    @Test
    void readsVariablesFromAMultipartOperationsPart() {
        String body = "--b\r\n"
                + "Content-Disposition: form-data; name=\"operations\"\r\n\r\n"
                + "{\"query\":\"mutation Upload($name: String!) { upload(name: $name) { id } }\","
                + "\"operationName\":\"Upload\",\"variables\":{\"name\":\"x\"}}\r\n"
                + "--b--\r\n";

        GraphQLOperationSource source = GraphQLOperationSource.from(
                "POST", "/upload", "multipart/form-data; boundary=b", body);

        assertEquals("Upload", source.operationName());
        assertEquals("upload", source.rootFieldName());
        assertEquals("x", source.variables().get("name").getAsString());
    }

    @Test
    void readsTheFirstOperationOfABatchBody() {
        String body = "[{\"query\":\"query A($a: Int) { x(a: $a) }\",\"variables\":{\"a\":1}},"
                + "{\"query\":\"query B { y }\"}]";

        GraphQLOperationSource source = GraphQLOperationSource.from(
                "POST", "/graphql", "application/json", body);

        assertEquals("x", source.rootFieldName());
        assertEquals(1, source.variables().get("a").getAsInt());
    }

    @Test
    void selectsTheOperationNamedByTheEnvelope() {
        String query = "query First($a: Int) { x(a: $a) } query Second($b: Int) { y(b: $b) }";
        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        body.addProperty("operationName", "Second");

        GraphQLOperationSource source = GraphQLOperationSource.from(
                "POST", "/graphql", "application/json", body.toString());

        assertEquals("y", source.rootFieldName());
        assertEquals("b", source.declaredVariables().get(0).name());
    }

    @Test
    void renamingAnOperationKeepsTheFragmentsItDependsOn() {
        JsonObject body = new JsonObject();
        body.addProperty("query", "query Viewer { ...F } fragment F on Query { __typename }");

        GraphQLOperationSource source = GraphQLOperationSource.from(
                "POST", "/graphql", "application/json", body.toString());

        String renamed = source.renamedOperation("GripBatch0");

        assertTrue(renamed.contains("query GripBatch0 {"));
        assertTrue(renamed.contains("fragment F on Query {"));
        assertTrue(renamed.contains("...F"));
    }

    @Test
    void reportsASyntaxErrorAndFallsBackToSafeDefaults() {
        JsonObject body = new JsonObject();
        body.addProperty("query", "mutation Gripé { __typename }");

        GraphQLOperationSource source = GraphQLOperationSource.from(
                "POST", "/graphql", "application/json", body.toString());

        assertFalse(source.hasOperation());
        assertNotNull(source.syntaxError());
        assertEquals("__typename", source.rootFieldName());
        assertNull(source.rootFieldCall());
        assertEquals("query", source.operationKeyword());
        assertFalse(source.isMutation());
        assertTrue(source.declaredVariables().isEmpty());
        assertNull(source.renamedOperation("Anything"));
    }

    @Test
    void ignoresMalformedAndEmptyVariableEnvelopes() {
        String malformed = "query=" + encode("query { x }") + "&variables=" + encode("not json");
        assertNull(GraphQLOperationSource.from(
                "POST", "/api", "application/x-www-form-urlencoded", malformed).variables());

        JsonObject body = new JsonObject();
        body.addProperty("query", "query { x }");
        body.add("variables", new JsonObject());
        assertNull(GraphQLOperationSource.from(
                "POST", "/graphql", "application/json", body.toString()).variables());
    }

    @Test
    void emptySourceExposesSafeDefaults() {
        GraphQLOperationSource source = GraphQLOperationSource.empty();

        assertFalse(source.hasOperation());
        assertNull(source.query());
        assertNull(source.operationName());
        assertNull(source.variables());
        assertEquals("__typename", source.rootFieldName());
        assertEquals("query", source.operationKeyword());
        assertTrue(source.declaredVariables().isEmpty());
    }

    private static String loginBody() {
        JsonObject input = new JsonObject();
        input.addProperty("username", "test");
        input.addProperty("password", "test");

        JsonObject variables = new JsonObject();
        variables.add("input", input);

        JsonObject body = new JsonObject();
        body.addProperty("query", LOGIN_QUERY);
        body.addProperty("operationName", "login");
        body.add("variables", variables);
        return body.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
