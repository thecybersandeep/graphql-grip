package com.grip.graphql.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.grip.graphql.document.GraphQLParser;
import com.grip.graphql.document.OperationDefinition;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLPayloadWriterTest {

    private static final String LOGIN_QUERY =
            "\n    mutation login($input: LoginInput!) {\n"
                    + "        login(input: $input) {\n"
                    + "            token\n"
                    + "            success\n"
                    + "        }\n"
                    + "    }";

    private static final String CHANGE_EMAIL_QUERY =
            "\n    mutation changeEmail($input: ChangeEmailInput!) {\n"
                    + "        changeEmail(input: $input) {\n"
                    + "            email\n"
                    + "        }\n"
                    + "    }";

    @Test
    void aliasPayloadDeclaresAndCarriesTheCapturedVariables() {
        GraphQLOperationSource source = login();

        StringBuilder generated = new StringBuilder();
        generated.append(source.operationKeyword()).append(" GripAliasOverload {\n");
        for (int i = 0; i < 2; i++) {
            generated.append(String.format("  a%d: %s\n", i, source.rootFieldCall()));
        }
        generated.append("}");

        JsonObject payload = GraphQLPayloadWriter.body(source, generated.toString());

        String expected = """
                mutation GripAliasOverload($input: LoginInput!) {
                  a0: login(input: $input) {
                    token
                    success
                  }
                  a1: login(input: $input) {
                    token
                    success
                  }
                }""";

        assertEquals(expected, payload.get("query").getAsString());
        assertEquals("GripAliasOverload", payload.get("operationName").getAsString());
        assertEquals("test", payload.getAsJsonObject("variables")
                .getAsJsonObject("input").get("username").getAsString());
    }

    @Test
    void aliasPayloadParsesBackAsAValidOperation() {
        GraphQLOperationSource source = login();
        JsonObject payload = GraphQLPayloadWriter.body(source,
                "mutation GripAliasedMutations {\n  m0: " + source.rootFieldCall() + "\n}");

        OperationDefinition operation = GraphQLParser.parse(payload.get("query").getAsString())
                .operation("GripAliasedMutations");

        assertNotNull(operation);
        assertNotNull(operation.variableDefinition("input"));
        assertEquals("m0", operation.firstRootField().alias());
    }

    @Test
    void leavesQueriesThatAlreadyDeclareTheirVariablesUntouched() {
        GraphQLOperationSource source = login();
        JsonObject payload = GraphQLPayloadWriter.body(source, source.query());

        assertEquals(source.query(), payload.get("query").getAsString());
        assertEquals("login", payload.get("operationName").getAsString());
        assertTrue(payload.has("variables"));
    }

    @Test
    void standalonePayloadsCarryNothingButTheQuery() {
        String query = "query GripWidthProbe50 {\n  a0: __typename\n}";
        JsonObject payload = GraphQLPayloadWriter.body(login(), query);

        assertEquals(query, payload.get("query").getAsString());
        assertEquals(1, payload.keySet().size());
    }

    @Test
    void carriesOnlyTheVariablesThePayloadReferences() {
        JsonObject variables = new JsonObject();
        variables.addProperty("a", 1);
        variables.addProperty("b", 2);

        JsonObject captured = new JsonObject();
        captured.addProperty("query", "query($a: Int, $b: Int) { x(a: $a) y(b: $b) }");
        captured.add("variables", variables);

        GraphQLOperationSource source = GraphQLOperationSource.from(
                "POST", "/graphql", "application/json", captured.toString());

        JsonObject payload = GraphQLPayloadWriter.body(source, "query Grip($a: Int) { x(a: $a) }");

        assertTrue(payload.getAsJsonObject("variables").has("a"));
        assertFalse(payload.getAsJsonObject("variables").has("b"));
    }

    @Test
    void passesDeliberatelyInvalidPayloadsThroughUnchanged() {
        String invalid = "query Gripé { __typename }";
        JsonObject payload = GraphQLPayloadWriter.body(login(), invalid);

        assertEquals(invalid, payload.get("query").getAsString());
        assertEquals(1, payload.keySet().size());
    }

    @Test
    void toleratesAMissingSource() {
        assertEquals("query { x }",
                GraphQLPayloadWriter.body(null, "query { x }").get("query").getAsString());
    }

    @Test
    void repeatedBatchItemsEachCarryTheEnvelope() {
        JsonArray batch = GraphQLPayloadWriter.repeated(login(), LOGIN_QUERY, 3);

        assertEquals(3, batch.size());
        for (int i = 0; i < batch.size(); i++) {
            JsonObject item = batch.get(i).getAsJsonObject();
            assertEquals("login", item.get("operationName").getAsString());
            assertTrue(item.has("variables"));
        }
    }

    @Test
    void renamedBatchItemsDefineTheOperationTheyName() {
        JsonArray batch = GraphQLPayloadWriter.renamedSeries(login(), "GripBatch", LOGIN_QUERY, 3);

        assertEquals(3, batch.size());
        for (int i = 0; i < batch.size(); i++) {
            JsonObject item = batch.get(i).getAsJsonObject();
            String name = "GripBatch" + i;

            assertEquals(name, item.get("operationName").getAsString());

            OperationDefinition operation = GraphQLParser.parse(item.get("query").getAsString())
                    .operation(name);
            assertNotNull(operation);
            assertNotNull(operation.variableDefinition("input"));
            assertTrue(item.has("variables"));
        }
    }

    @Test
    void renamedBatchFallsBackWhenNothingWasParsed() {
        JsonArray batch = GraphQLPayloadWriter.renamedSeries(
                GraphQLOperationSource.empty(), "Op", "query { __typename }", 2);

        assertEquals(2, batch.size());
        for (int i = 0; i < batch.size(); i++) {
            JsonObject item = batch.get(i).getAsJsonObject();
            assertEquals("query { __typename }", item.get("query").getAsString());
            assertFalse(item.has("operationName"));
        }
    }

    @Test
    void alternatingBatchAppliesTheEnvelopePerItem() {
        JsonArray batch = GraphQLPayloadWriter.alternating(
                login(), LOGIN_QUERY, "query { __typename }", 4);

        assertEquals(4, batch.size());
        assertTrue(batch.get(0).getAsJsonObject().has("variables"));
        assertFalse(batch.get(1).getAsJsonObject().has("variables"));
        assertTrue(batch.get(2).getAsJsonObject().has("variables"));
        assertFalse(batch.get(3).getAsJsonObject().has("variables"));
    }

    @Test
    void formParametersCarryQueryOperationNameAndVariables() {
        GraphQLOperationSource source = changeEmail();
        JsonObject envelope = GraphQLPayloadWriter.body(source, source.query());

        String parameters = GraphQLPayloadWriter.parameters(envelope, source);
        String decoded = URLDecoder.decode(parameters, StandardCharsets.UTF_8);

        assertTrue(parameters.startsWith("query="));
        assertTrue(decoded.contains("operationName=changeEmail"));
        assertTrue(decoded.contains("variables={\"input\":{\"email\":\"hacker@hacker.com\"}}"));
    }

    @Test
    void formParametersMatchTheBodyAFormEncodedEndpointExpects() {
        String query = "\n    mutation changeEmail($input: ChangeEmailInput!) {\n"
                + "        changeEmail(input: $input) {\n"
                + "            email\n"
                + "        }\n"
                + "    }\n";

        JsonObject input = new JsonObject();
        input.addProperty("email", "hacker@hacker.com");

        GraphQLOperationSource source = capture(query, "changeEmail", "input", input);

        String expected =
                "query=%0A++++mutation+changeEmail%28%24input%3A+ChangeEmailInput%21%29+%7B%0A"
                        + "++++++++changeEmail%28input%3A+%24input%29+%7B%0A"
                        + "++++++++++++email%0A++++++++%7D%0A++++%7D%0A"
                        + "&operationName=changeEmail"
                        + "&variables=%7B%22input%22%3A%7B%22email%22%3A%22hacker%40hacker.com%22%7D%7D";

        assertEquals(expected, GraphQLPayloadWriter.parameters(
                GraphQLPayloadWriter.body(source, source.query()), source));
    }

    @Test
    void aFormEncodedCaptureStillYieldsAJsonEnvelopeForTheEditor() {
        String formBody = "query=query+%7B%0A++movieVersion%28where%3A+%7B++%7D%29+%7B%0A"
                + "++++id%0A++%7D%0A%7D";

        assertThrows(RuntimeException.class,
                () -> com.google.gson.JsonParser.parseString(formBody).getAsJsonObject());

        GraphQLOperationSource source = GraphQLOperationSource.from(
                "POST", "/graphql", "application/x-www-form-urlencoded", formBody);

        assertNotNull(source.query());
        assertEquals("movieVersion", source.rootFieldName());

        JsonObject envelope = GraphQLPayloadWriter.body(source, source.query());
        String rendered = GraphQLPayloadWriter.json(envelope);

        assertTrue(com.google.gson.JsonParser.parseString(rendered).isJsonObject());
        assertTrue(rendered.contains("movieVersion"));
    }

    @Test
    void formParametersSurviveARoundTrip() {
        GraphQLOperationSource source = changeEmail();
        String parameters = GraphQLPayloadWriter.parameters(
                GraphQLPayloadWriter.body(source, source.query()), source);

        GraphQLOperationSource reparsed = GraphQLOperationSource.from(
                "POST", "/graphql/v1", "application/x-www-form-urlencoded", parameters);

        assertEquals("changeEmail", reparsed.operationName());
        assertEquals("changeEmail", reparsed.rootFieldName());
        assertEquals("hacker@hacker.com",
                reparsed.variables().getAsJsonObject("input").get("email").getAsString());
    }

    @Test
    void parametersUseTheEnvelopeTheyAreGivenRatherThanTheCapture() {
        GraphQLOperationSource source = changeEmail();

        JsonObject variables = new JsonObject();
        JsonObject input = new JsonObject();
        input.addProperty("email", "edited@example.com");
        variables.add("input", input);

        JsonObject envelope = new JsonObject();
        envelope.addProperty("query", source.query());
        envelope.addProperty("operationName", "changeEmail");
        envelope.add("variables", variables);

        String decoded = URLDecoder.decode(
                GraphQLPayloadWriter.parameters(envelope, source), StandardCharsets.UTF_8);

        assertTrue(decoded.contains("edited@example.com"));
        assertFalse(decoded.contains("hacker@hacker.com"));
    }

    @Test
    void parametersCarryExtensionsWhenThePayloadHasThem() {
        JsonObject extensions = new JsonObject();
        extensions.addProperty("tracing", true);

        JsonObject envelope = new JsonObject();
        envelope.addProperty("query", "query { __typename }");
        envelope.add("extensions", extensions);

        String decoded = URLDecoder.decode(
                GraphQLPayloadWriter.parameters(envelope, null), StandardCharsets.UTF_8);

        assertTrue(decoded.contains("extensions={\"tracing\":true}"));
    }

    @Test
    void multipartBodySurvivesARoundTrip() {
        GraphQLOperationSource source = changeEmail();
        String boundary = "----GripBoundaryTest";
        String body = GraphQLPayloadWriter.multipartBody(
                GraphQLPayloadWriter.body(source, source.query()), source, boundary);

        assertTrue(body.contains("name=\"operations\""));

        GraphQLOperationSource reparsed = GraphQLOperationSource.from(
                "POST", "/graphql/v1", "multipart/form-data; boundary=" + boundary, body);

        assertEquals("changeEmail", reparsed.operationName());
        assertEquals("hacker@hacker.com",
                reparsed.variables().getAsJsonObject("input").get("email").getAsString());
    }

    @Test
    void parametersStillCarryTheOperationNameForQueriesWithoutVariables() {
        JsonObject captured = new JsonObject();
        captured.addProperty("query", "query Viewer { viewer { id } }");
        captured.addProperty("operationName", "Viewer");

        GraphQLOperationSource source = GraphQLOperationSource.from(
                "POST", "/graphql", "application/json", captured.toString());

        String decoded = URLDecoder.decode(GraphQLPayloadWriter.parameters(
                GraphQLPayloadWriter.body(source, source.query()), source), StandardCharsets.UTF_8);

        assertTrue(decoded.contains("operationName=Viewer"));
        assertFalse(decoded.contains("variables="));
    }

    private static GraphQLOperationSource login() {
        JsonObject input = new JsonObject();
        input.addProperty("username", "test");
        input.addProperty("password", "test");
        return capture(LOGIN_QUERY, "login", "input", input);
    }

    private static GraphQLOperationSource changeEmail() {
        JsonObject input = new JsonObject();
        input.addProperty("email", "hacker@hacker.com");
        return capture(CHANGE_EMAIL_QUERY, "changeEmail", "input", input);
    }

    private static GraphQLOperationSource capture(String query, String operationName,
                                                  String variableName, JsonObject variableValue) {
        JsonObject variables = new JsonObject();
        variables.add(variableName, variableValue);

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        body.addProperty("operationName", operationName);
        body.add("variables", variables);

        return GraphQLOperationSource.from("POST", "/graphql/v1", "application/json", body.toString());
    }
}
