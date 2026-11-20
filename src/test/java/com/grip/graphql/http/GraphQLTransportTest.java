package com.grip.graphql.http;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLTransportTest {

    private static final String ENDPOINT = "https://host/api";
    private static final String QUERY = "query { user(id: \"1\") { name } }";

    @Test
    void postJsonSendsTheQueryInAJsonBody() {
        assertEquals("POST", GraphQLTransport.POST_JSON.method());
        assertEquals("application/json", GraphQLTransport.POST_JSON.contentType());
        assertTrue(GraphQLTransport.POST_JSON.carriesBody());
        assertEquals(ENDPOINT, GraphQLTransport.POST_JSON.url(ENDPOINT, QUERY));

        JsonObject body = JsonParser.parseString(GraphQLTransport.POST_JSON.body(QUERY))
                .getAsJsonObject();
        assertEquals(QUERY, body.get("query").getAsString());
    }

    @Test
    void getPutsTheQueryInTheUrlAndSendsNoBody() {
        assertEquals("GET", GraphQLTransport.GET.method());
        assertNull(GraphQLTransport.GET.contentType());
        assertFalse(GraphQLTransport.GET.carriesBody());
        assertEquals("", GraphQLTransport.GET.body(QUERY));

        String url = GraphQLTransport.GET.url(ENDPOINT, QUERY);
        assertTrue(url.startsWith(ENDPOINT + "?query="));
        assertEquals(QUERY, URLDecoder.decode(url.substring(url.indexOf("?query=") + 7),
                StandardCharsets.UTF_8));
    }

    @Test
    void getAppendsToAnExistingQueryString() {
        assertTrue(GraphQLTransport.GET.url(ENDPOINT + "?debug=1", QUERY)
                .startsWith(ENDPOINT + "?debug=1&query="));
    }

    @Test
    void formEncodesTheQueryInTheBody() {
        assertEquals("POST", GraphQLTransport.POST_FORM.method());
        assertEquals("application/x-www-form-urlencoded", GraphQLTransport.POST_FORM.contentType());
        assertTrue(GraphQLTransport.POST_FORM.carriesBody());
        assertEquals(ENDPOINT, GraphQLTransport.POST_FORM.url(ENDPOINT, QUERY));

        String body = GraphQLTransport.POST_FORM.body(QUERY);
        assertTrue(body.startsWith("query="));
        assertEquals(QUERY, URLDecoder.decode(body.substring(6), StandardCharsets.UTF_8));
    }

    @Test
    void onlyGetRewritesTheUrl() {
        assertEquals(ENDPOINT, GraphQLTransport.POST_JSON.url(ENDPOINT, QUERY));
        assertEquals(ENDPOINT, GraphQLTransport.POST_FORM.url(ENDPOINT, QUERY));
        assertNotEquals(ENDPOINT, GraphQLTransport.GET.url(ENDPOINT, QUERY));
    }

    @Test
    void onlyPostTransportsCarryABody() {
        assertTrue(GraphQLTransport.POST_JSON.carriesBody());
        assertTrue(GraphQLTransport.POST_FORM.carriesBody());
        assertFalse(GraphQLTransport.GET.carriesBody());
    }

    @Test
    void descriptionsAreUsedForDisplay() {
        for (GraphQLTransport transport : GraphQLTransport.values()) {
            assertFalse(transport.description().isBlank(), transport.name());
            assertEquals(transport.description(), transport.toString(), transport.name());
        }
    }

    @Test
    void toleratesANullQuery() {
        for (GraphQLTransport transport : GraphQLTransport.values()) {
            assertNotNull(transport.body(null), transport.name());
            assertNotNull(transport.url(ENDPOINT, null), transport.name());
        }
    }
}
