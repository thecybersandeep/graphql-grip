package com.grip.graphql.schema;

import com.grip.graphql.http.GraphQLTransport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IntrospectionLadderTest {

    private static final String TYPENAME_OK = "{\"data\":{\"__typename\":\"Query\"}}";
    private static final String SCHEMA_OK = "{\"data\":{\"__schema\":{\"queryType\":{\"name\":\"Query\"}}}}";
    private static final String BLOCKED =
            "{\"errors\":[{\"message\":\"GraphQL introspection is not allowed\"}]}";
    private static final String NOT_FOUND = "<!DOCTYPE html><html><body>Not Found</body></html>";

    @Test
    void confirmsPostJsonWhenIntrospectionIsOpen() {
        IntrospectionLadder.Outcome outcome = IntrospectionLadder.probe(
                (transport, query) -> query.contains("__typename") ? json(TYPENAME_OK) : json(SCHEMA_OK),
                null);

        assertTrue(outcome.graphQL());
        assertTrue(outcome.introspectionAvailable());
        assertFalse(outcome.bypassed());
        assertEquals(GraphQLTransport.POST_JSON, outcome.transport());
        assertEquals(IntrospectionBypass.NONE, outcome.bypass());
        assertEquals(4, outcome.attempts());
    }

    @Test
    void cancellingBeforeTheFirstProbeSendsNothing() {
        List<String> sent = new ArrayList<>();

        IntrospectionLadder.Outcome outcome = IntrospectionLadder.probe((transport, query) -> {
            sent.add(query);
            return json(TYPENAME_OK);
        }, null, () -> true);

        assertTrue(sent.isEmpty());
        assertFalse(outcome.graphQL());
        assertEquals(0, outcome.attempts());
        assertTrue(outcome.detail().toLowerCase(java.util.Locale.ROOT).contains("cancelled"));
    }

    @Test
    void cancellingPartWayThroughStopsTheRemainingProbes() {
        List<String> sent = new ArrayList<>();
        boolean[] stop = {false};

        IntrospectionLadder.Outcome outcome = IntrospectionLadder.probe((transport, query) -> {
            sent.add(query);
            if (sent.size() >= 2) {
                stop[0] = true;
            }
            return json(TYPENAME_OK);
        }, null, () -> stop[0]);

        assertEquals(2, sent.size());
        assertEquals(2, outcome.attempts());
        assertFalse(outcome.graphQL());
    }

    @Test
    void cancellingAfterTheTransportProbesKeepsWhatWasConfirmed() {
        boolean[] stop = {false};

        IntrospectionLadder.Outcome outcome = IntrospectionLadder.probe((transport, query) -> {
            if (query.contains("__typename")) {
                return json(TYPENAME_OK);
            }
            stop[0] = true;
            return json(BLOCKED);
        }, null, () -> stop[0]);

        assertTrue(outcome.graphQL());
        assertFalse(outcome.introspectionAvailable());
        assertEquals(3, outcome.transports().size());
        assertNotNull(outcome.transport());
    }

    @Test
    void anUncancelledProbeBehavesExactlyAsBefore() {
        IntrospectionLadder.Outcome withSupplier = IntrospectionLadder.probe(
                (transport, query) -> query.contains("__typename")
                        ? json(TYPENAME_OK) : json(SCHEMA_OK),
                null, () -> false);

        IntrospectionLadder.Outcome withoutSupplier = IntrospectionLadder.probe(
                (transport, query) -> query.contains("__typename")
                        ? json(TYPENAME_OK) : json(SCHEMA_OK),
                null);

        assertEquals(withoutSupplier.graphQL(), withSupplier.graphQL());
        assertEquals(withoutSupplier.introspectionAvailable(), withSupplier.introspectionAvailable());
        assertEquals(withoutSupplier.attempts(), withSupplier.attempts());
        assertEquals(withoutSupplier.transport(), withSupplier.transport());
        assertEquals(withoutSupplier.bypass(), withSupplier.bypass());
    }

    @Test
    void findsTheNewlineBypassOverGetWhenPostIsNotAccepted() {
        IntrospectionLadder.Outcome outcome = IntrospectionLadder.probe(getOnlyWithSchemaFilter(), null);

        assertTrue(outcome.graphQL());
        assertTrue(outcome.introspectionAvailable());
        assertTrue(outcome.bypassed());
        assertEquals(List.of(GraphQLTransport.GET), outcome.transports());
        assertEquals(GraphQLTransport.GET, outcome.transport());
        assertEquals(IntrospectionBypass.NEWLINE, outcome.bypass());
    }

    @Test
    void treatsGetAsABypassWhenPostIntrospectionIsFiltered() {
        IntrospectionLadder.Outcome outcome = IntrospectionLadder.probe((transport, query) -> {
            if (query.contains("__typename")) {
                return json(TYPENAME_OK);
            }
            return transport == GraphQLTransport.GET ? json(SCHEMA_OK) : json(BLOCKED);
        }, null);

        assertTrue(outcome.introspectionAvailable());
        assertEquals(GraphQLTransport.GET, outcome.transport());
        assertEquals(IntrospectionBypass.NONE, outcome.bypass());
        assertFalse(outcome.bypassed());
        assertEquals(3, outcome.transports().size());
    }

    @Test
    void reportsNotGraphQLWhenNoTransportAnswers() {
        IntrospectionLadder.Outcome outcome = IntrospectionLadder.probe(
                (transport, query) -> new IntrospectionLadder.Response(404, "text/html", NOT_FOUND),
                null);

        assertFalse(outcome.graphQL());
        assertFalse(outcome.introspectionAvailable());
        assertTrue(outcome.transports().isEmpty());
        assertNull(outcome.transport());
        assertNull(outcome.bypass());
        assertEquals(3, outcome.attempts());
        assertNotNull(outcome.detail());
    }

    @Test
    void separatesABlockedEndpointFromOneThatIsNotGraphQL() {
        IntrospectionLadder.Outcome outcome = IntrospectionLadder.probe(
                (transport, query) -> query.contains("__typename") ? json(TYPENAME_OK) : json(BLOCKED),
                null);

        assertTrue(outcome.graphQL());
        assertFalse(outcome.introspectionAvailable());
        assertNull(outcome.bypass());
        assertEquals(GraphQLTransport.POST_JSON, outcome.transport());
        assertTrue(outcome.detail().contains("introspection"));
    }

    @Test
    void keepsGoingWhenOneTransportThrows() {
        IntrospectionLadder.Outcome outcome = IntrospectionLadder.probe((transport, query) -> {
            if (transport == GraphQLTransport.POST_JSON) {
                throw new IllegalStateException("connection reset");
            }
            return query.contains("__typename") ? json(TYPENAME_OK) : json(SCHEMA_OK);
        }, null);

        assertTrue(outcome.introspectionAvailable());
        assertFalse(outcome.transports().contains(GraphQLTransport.POST_JSON));
        assertEquals(GraphQLTransport.GET, outcome.transport());
    }

    @Test
    void toleratesSendersThatReturnNothing() {
        IntrospectionLadder.Outcome outcome = IntrospectionLadder.probe((transport, query) -> null, null);

        assertFalse(outcome.graphQL());
        assertEquals(3, outcome.attempts());
    }

    @Test
    void reportsProgressForEachStep() {
        List<String> messages = new ArrayList<>();

        IntrospectionLadder.probe(getOnlyWithSchemaFilter(), messages::add);

        assertFalse(messages.isEmpty());
        assertTrue(containing(messages, "Probing POST with a JSON body"));
        assertTrue(containing(messages, "GraphQL confirmed over GET with a query parameter"));
        assertTrue(containing(messages, "newline after __schema"));
    }

    @Test
    void reportsWhyEachTransportWasRejected() {
        List<String> messages = new ArrayList<>();

        IntrospectionLadder.probe((transport, query) ->
                new IntrospectionLadder.Response(404, "application/json", "{\"error\":\"not found\"}"),
                messages::add);

        assertTrue(containing(messages, "POST with a JSON body rejected: HTTP 404"));
        assertTrue(containing(messages, "GET with a query parameter rejected: HTTP 404"));
        assertTrue(containing(messages, "POST with a form encoded body rejected: HTTP 404"));
    }

    @Test
    void reportsADifferentReasonPerTransport() {
        List<String> messages = new ArrayList<>();

        IntrospectionLadder.probe((transport, query) -> transport == GraphQLTransport.GET
                        ? new IntrospectionLadder.Response(200, "text/html", NOT_FOUND)
                        : new IntrospectionLadder.Response(404, "application/json", "{\"error\":\"nope\"}"),
                messages::add);

        assertTrue(containing(messages,
                "GET with a query parameter rejected: HTTP 200 returned text/html"));
        assertTrue(containing(messages, "POST with a JSON body rejected: HTTP 404"));
    }

    @Test
    void reportsAFailedProbeInProgress() {
        List<String> messages = new ArrayList<>();

        IntrospectionLadder.probe((transport, query) -> {
            throw new IllegalStateException("connection reset");
        }, messages::add);

        assertTrue(containing(messages, "connection reset"));
    }

    @Test
    void probeQueriesAreValidAndDistinct() {
        assertTrue(IntrospectionLadder.TYPENAME_PROBE.contains("__typename"));
        assertTrue(IntrospectionLadder.INTROSPECTION_PROBE.contains("__schema {"));
        assertNotEquals(IntrospectionLadder.TYPENAME_PROBE, IntrospectionLadder.INTROSPECTION_PROBE);
    }

    private static IntrospectionLadder.Sender getOnlyWithSchemaFilter() {
        return (transport, query) -> {
            if (transport != GraphQLTransport.GET) {
                return new IntrospectionLadder.Response(404, "text/html", NOT_FOUND);
            }
            if (query.contains("__typename")) {
                return json(TYPENAME_OK);
            }
            if (query.contains("__schema {") || query.contains("__schema{")) {
                return json(BLOCKED);
            }
            return json(SCHEMA_OK);
        };
    }

    private static IntrospectionLadder.Response json(String body) {
        return new IntrospectionLadder.Response(200, "application/json", body);
    }

    private static boolean containing(List<String> messages, String fragment) {
        for (String message : messages) {
            if (message.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
