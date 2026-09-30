package com.grip.graphql.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLRequestDetectorTest {

    @Test
    void recognizesGraphQLPathSegmentWithoutMatchingArticleSlug() {
        assertTrue(GraphQLRequestDetector.isGraphQLRequest("POST", "/api/graphql", null, ""));
        assertFalse(GraphQLRequestDetector.isGraphQLRequest(
                "GET", "/articles/graphql-security", "text/html", ""));
    }

    @Test
    void recognizesJsonAndBatchOperationsWithoutSpecialPath() {
        assertTrue(GraphQLRequestDetector.isGraphQLRequest(
                "POST", "/api", "application/json", "{\"query\":\"query { viewer { id } }\"}"));
        assertTrue(GraphQLRequestDetector.isGraphQLRequest(
                "POST", "/api", "application/json",
                "[{\"query\":\"mutation { updateName(name: \\\"x\\\") }\"}]"));
    }

    @Test
    void recognizesGetAndFormEncodedQueries() {
        assertTrue(GraphQLRequestDetector.isGraphQLRequest(
                "GET", "/api?query=query%20%7Bviewer%7Bid%7D%7D", null, null));
        assertTrue(GraphQLRequestDetector.isGraphQLRequest(
                "POST", "/api", "application/x-www-form-urlencoded",
                "operationName=Viewer&query=query%20%7Bviewer%7Bid%7D%7D"));
    }

    @Test
    void recognizesRawDocumentsAndPersistedQueries() {
        assertTrue(GraphQLRequestDetector.isGraphQLRequest(
                "POST", "/api", "application/graphql", "query Viewer { viewer { id } }"));
        assertTrue(GraphQLRequestDetector.isGraphQLRequest(
                "POST", "/api", "application/graphql", "{ viewer { id } }"));
        assertTrue(GraphQLRequestDetector.isGraphQLRequest(
                "POST", "/api", "application/json",
                "{\"extensions\":{\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"abc\"}}}"));
    }

    @Test
    void extractsQueryFromMultipartOperations() {
        String body = "--boundary\r\n" +
                "Content-Disposition: form-data; name=\"operations\"\r\n\r\n" +
                "{\"query\":\"mutation Upload { upload }\",\"variables\":{}}\r\n" +
                "--boundary--\r\n";

        assertEquals("mutation Upload { upload }", GraphQLRequestDetector.extractQuery(
                "POST", "/upload", "multipart/form-data; boundary=boundary", body));
        assertTrue(GraphQLRequestDetector.isGraphQLRequest(
                "POST", "/upload", "multipart/form-data; boundary=boundary", body));
    }

    @Test
    void rejectsUnrelatedJson() {
        assertFalse(GraphQLRequestDetector.isGraphQLRequest(
                "POST", "/api", "application/json", "{\"query\":42,\"data\":\"GraphQL\"}"));
    }
}
