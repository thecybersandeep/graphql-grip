package com.grip.graphql.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLResponseAnalyzerTest {

    @Test
    void rejectsHtmlEvenWhenItContainsGraphQLMarkers() {
        String html = "<!DOCTYPE html><script>{\"__typename\":\"Image\"}</script>GraphQL";

        GraphQLResponseAnalyzer.Analysis result =
                GraphQLResponseAnalyzer.analyzeEndpointProbe(404, "text/html; charset=utf-8", html);

        assertFalse(result.isGraphQL());
        assertTrue(result.getDetail().contains("404"));
        assertTrue(result.getDetail().contains("text/html"));
    }

    @Test
    void acceptsExpectedTypenameData() {
        GraphQLResponseAnalyzer.Analysis result = GraphQLResponseAnalyzer.analyzeEndpointProbe(
                200, "application/graphql-response+json", "{\"data\":{\"__typename\":\"Query\"}}");

        assertTrue(result.isGraphQL());
        assertTrue(result.isExpectedFieldAvailable());
    }

    @Test
    void acceptsStructuredGraphQLErrors() {
        GraphQLResponseAnalyzer.Analysis result = GraphQLResponseAnalyzer.analyzeEndpointProbe(
                400, "application/json", "{\"errors\":[{\"message\":\"Cannot query field\"}]}");

        assertTrue(result.isGraphQL());
        assertFalse(result.isExpectedFieldAvailable());
        assertEquals("Cannot query field", result.getDetail());
    }

    @Test
    void rejectsGenericDataEnvelope() {
        GraphQLResponseAnalyzer.Analysis result = GraphQLResponseAnalyzer.analyzeEndpointProbe(
                200, "application/json", "{\"data\":{\"name\":\"example\"}}");

        assertFalse(result.isGraphQL());
    }

    @Test
    void rejectsGenericRestErrorEnvelope() {
        GraphQLResponseAnalyzer.Analysis result = GraphQLResponseAnalyzer.analyzeEndpointProbe(
                400, "application/json", "{\"errors\":[{\"message\":\"Request was rejected\"}]}");

        assertFalse(result.isGraphQL());
    }

    @Test
    void separatesGraphQLErrorsFromEnabledIntrospection() {
        GraphQLResponseAnalyzer.Analysis disabled = GraphQLResponseAnalyzer.analyzeIntrospectionResponse(
                200, "application/json", "{\"errors\":[{\"message\":\"Introspection is disabled\"}]}");
        GraphQLResponseAnalyzer.Analysis enabled = GraphQLResponseAnalyzer.analyzeIntrospectionResponse(
                200, "application/json", "{\"data\":{\"__schema\":{\"queryType\":{\"name\":\"Query\"}}}}");

        assertTrue(disabled.isGraphQL());
        assertFalse(disabled.isExpectedFieldAvailable());
        assertTrue(enabled.isGraphQL());
        assertTrue(enabled.isExpectedFieldAvailable());
    }

    @Test
    void requiresSpecificIdeMarkersAndSuccessfulStatus() {
        assertEquals("GraphiQL UI", GraphQLResponseAnalyzer.graphQLIdeEvidence(
                200, "text/html", "<html><div id=\"graphiql-container\"></div></html>"));
        assertNull(GraphQLResponseAnalyzer.graphQLIdeEvidence(
                404, "text/html", "<html><div id=\"graphiql-container\"></div></html>"));
        assertNull(GraphQLResponseAnalyzer.graphQLIdeEvidence(
                200, "text/html", "<html>Apollo GraphQL consulting</html>"));
    }
}
