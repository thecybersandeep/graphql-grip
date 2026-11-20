package com.grip.graphql.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PayloadTransportTest {

    @Test
    void readsGetFromTheCapturedMethod() {
        assertEquals(PayloadTransport.GET, PayloadTransport.of("GET", null));
        assertEquals(PayloadTransport.GET, PayloadTransport.of("get", "application/json"));
    }

    @Test
    void readsFormAndMultipartFromTheContentType() {
        assertEquals(PayloadTransport.FORM,
                PayloadTransport.of("POST", "application/x-www-form-urlencoded"));
        assertEquals(PayloadTransport.MULTIPART,
                PayloadTransport.of("POST", "multipart/form-data; boundary=abc"));
    }

    @Test
    void defaultsToJsonForEverythingElse() {
        assertEquals(PayloadTransport.JSON, PayloadTransport.of("POST", "application/json"));
        assertEquals(PayloadTransport.JSON, PayloadTransport.of("POST", null));
        assertEquals(PayloadTransport.JSON, PayloadTransport.of("POST", "text/plain"));
        assertEquals(PayloadTransport.JSON, PayloadTransport.of(null, null));
    }

    @Test
    void ignoresContentTypeCasingAndParameters() {
        assertEquals(PayloadTransport.FORM,
                PayloadTransport.of("POST", "Application/X-WWW-Form-Urlencoded; charset=utf-8"));
        assertEquals(PayloadTransport.MULTIPART,
                PayloadTransport.of("POST", "MULTIPART/FORM-DATA; boundary=x"));
    }

    @Test
    void onlyJsonCanCarryABatch() {
        assertTrue(PayloadTransport.JSON.supportsBatch());
        assertFalse(PayloadTransport.GET.supportsBatch());
        assertFalse(PayloadTransport.FORM.supportsBatch());
        assertFalse(PayloadTransport.MULTIPART.supportsBatch());
    }

    @Test
    void everyTransportHasADescription() {
        for (PayloadTransport transport : PayloadTransport.values()) {
            assertFalse(transport.description().isBlank(), transport.name());
        }
    }

    @Test
    void everyTransportSaysHowThePayloadIsSent() {
        java.util.Set<String> wordings = new java.util.LinkedHashSet<>();
        for (PayloadTransport transport : PayloadTransport.values()) {
            assertFalse(transport.sendsAs().isBlank(), transport.name());
            assertTrue(wordings.add(transport.sendsAs()), transport.name());
        }
        assertEquals(PayloadTransport.values().length, wordings.size());
    }
}
