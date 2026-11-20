package com.grip.graphql.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PayloadDispatchTest {

    private static final String BODY = "{\n  \"query\": \"query { __typename }\"\n}";

    @Test
    void switchingTheRequestFormatCountsAsAModification() {
        assertTrue(PayloadDispatch.isModified(PayloadTransport.JSON, PayloadTransport.GET,
                BODY, BODY));
        assertTrue(PayloadDispatch.isModified(PayloadTransport.JSON, PayloadTransport.FORM,
                BODY, BODY));
        assertTrue(PayloadDispatch.isModified(PayloadTransport.JSON, PayloadTransport.MULTIPART,
                BODY, BODY));
        assertTrue(PayloadDispatch.isModified(PayloadTransport.GET, PayloadTransport.JSON,
                BODY, BODY));
    }

    @Test
    void editingThePayloadCountsAsAModification() {
        assertTrue(PayloadDispatch.isModified(PayloadTransport.JSON, PayloadTransport.JSON,
                BODY, BODY + " "));
    }

    @Test
    void anUntouchedRequestIsNotModified() {
        assertFalse(PayloadDispatch.isModified(PayloadTransport.JSON, PayloadTransport.JSON,
                BODY, BODY));
        assertFalse(PayloadDispatch.isModified(PayloadTransport.FORM, PayloadTransport.FORM,
                BODY, BODY));
    }

    @Test
    void anEditorThatNeverLoadedAnythingIsNotModified() {
        assertFalse(PayloadDispatch.isModified(PayloadTransport.JSON, PayloadTransport.JSON,
                null, "anything"));
    }

    @Test
    void aFormatChangeStillCountsBeforeAnyContentIsRecorded() {
        assertTrue(PayloadDispatch.isModified(PayloadTransport.JSON, PayloadTransport.GET,
                null, "anything"));
    }

    @Test
    void everyFormatAcceptsASingleOperation() {
        for (PayloadTransport transport : PayloadTransport.values()) {
            assertTrue(PayloadDispatch.accepts(transport, false), transport.name());
        }
    }

    @Test
    void onlyJsonAcceptsABatch() {
        assertTrue(PayloadDispatch.accepts(PayloadTransport.JSON, true));
        assertFalse(PayloadDispatch.accepts(PayloadTransport.GET, true));
        assertFalse(PayloadDispatch.accepts(PayloadTransport.FORM, true));
        assertFalse(PayloadDispatch.accepts(PayloadTransport.MULTIPART, true));
    }

    @Test
    void switchingBackToABodyFormatDropsTheOperationItPutInTheUrl() {
        String afterGet = "/graphql?query=query+%7B+__typename+%7D"
                + "&operationName=Grip&variables=%7B%7D";

        assertEquals("/graphql", PayloadDispatch.pathWithoutOperationParameters(afterGet));
    }

    @Test
    void theEndpointKeepsItsOwnQueryParameters() {
        String path = "/graphql?apiKey=abc123&query=query+%7B+x+%7D&region=eu";

        assertEquals("/graphql?apiKey=abc123&region=eu",
                PayloadDispatch.pathWithoutOperationParameters(path));
    }

    @Test
    void aPathWithNoQueryStringIsLeftAlone() {
        assertEquals("/graphql", PayloadDispatch.pathWithoutOperationParameters("/graphql"));
        assertEquals("/", PayloadDispatch.pathWithoutOperationParameters("/"));
        assertEquals("", PayloadDispatch.pathWithoutOperationParameters(null));
    }

    @Test
    void convertingToGetKeepsTheEndpointParametersAndAddsTheOperationOnce() {
        String path = PayloadDispatch.appendParameters(
                "/graphql?apiKey=abc123", "query=query+%7B+x+%7D");

        assertEquals("/graphql?apiKey=abc123&query=query+%7B+x+%7D", path);
    }

    @Test
    void convertingToGetTwiceDoesNotStackTheOperation() {
        String once = PayloadDispatch.appendParameters("/graphql", "query=first");
        String twice = PayloadDispatch.appendParameters(once, "query=second");

        assertEquals("/graphql?query=first", once);
        assertEquals("/graphql?query=second", twice);
        assertEquals(1, twice.split("query=", -1).length - 1);
    }

    @Test
    void convertingToGetWithNoParametersLeavesABarePath() {
        assertEquals("/graphql", PayloadDispatch.appendParameters("/graphql?query=old", ""));
        assertEquals("/graphql", PayloadDispatch.appendParameters("/graphql?query=old", null));
    }

    @Test
    void aMissingFormatIsNeverAccepted() {
        assertFalse(PayloadDispatch.accepts(null, false));
        assertFalse(PayloadDispatch.accepts(null, true));
    }
}
