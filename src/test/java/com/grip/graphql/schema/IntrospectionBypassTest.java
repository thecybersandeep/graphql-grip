package com.grip.graphql.schema;

import com.grip.graphql.document.GraphQLParser;
import com.grip.graphql.document.OperationDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IntrospectionBypassTest {

    private static final String PROBE = IntrospectionLadder.INTROSPECTION_PROBE;

    @Test
    void noneLeavesTheQueryAlone() {
        assertEquals(PROBE, IntrospectionBypass.NONE.apply(PROBE));
    }

    @Test
    void whitespaceVariantsBreakTheSchemaBraceSequence() {
        for (IntrospectionBypass bypass : List.of(IntrospectionBypass.NEWLINE,
                IntrospectionBypass.TAB, IntrospectionBypass.CARRIAGE_RETURN)) {
            String applied = bypass.apply(PROBE);

            assertFalse(applied.contains("__schema {"), bypass.name());
            assertFalse(applied.contains("__schema{"), bypass.name());
        }
    }

    @Test
    void newlineVariantInsertsALineBreakAfterSchema() {
        assertTrue(IntrospectionBypass.NEWLINE.apply(PROBE).contains("__schema\n{"));
        assertTrue(IntrospectionBypass.TAB.apply(PROBE).contains("__schema\t{"));
        assertTrue(IntrospectionBypass.CARRIAGE_RETURN.apply(PROBE).contains("__schema\r\n{"));
    }

    @Test
    void directiveVariantsAttachToTheSchemaField() {
        assertTrue(IntrospectionBypass.INCLUDE_DIRECTIVE.apply(PROBE)
                .contains("__schema @include(if: true) {"));
        assertTrue(IntrospectionBypass.SKIP_DIRECTIVE.apply(PROBE)
                .contains("__schema @skip(if: false) {"));
    }

    @Test
    void longOperationNameVariantExtendsTheName() {
        String applied = IntrospectionBypass.LONG_OPERATION_NAME.apply(PROBE);

        assertTrue(applied.length() > PROBE.length() + 400);
        assertTrue(applied.startsWith("query GripIntrospectionProbeAAA"));
    }

    @Test
    void longOperationNameVariantWorksOnAnonymousOperations() {
        String applied = IntrospectionBypass.LONG_OPERATION_NAME.apply("query { __schema { types { name } } }");

        assertEquals("__schema", GraphQLParser.parse(applied).firstOperation().firstRootField().name());
    }

    @Test
    void longOperationNameVariantNamesAShorthandOperation() {
        String applied = IntrospectionBypass.LONG_OPERATION_NAME.apply("{ __schema { types { name } } }");

        assertTrue(applied.startsWith("query AAA"));
        assertEquals("__schema", GraphQLParser.parse(applied).firstOperation().firstRootField().name());
    }

    @Test
    void longOperationNameVariantDoesNotFuseWithANameEndingInAKeyword() {
        String applied = IntrospectionBypass.LONG_OPERATION_NAME
                .apply("query myquery { __schema { types { name } } }");

        assertTrue(applied.startsWith("query myqueryAAA"));
        assertEquals("myquery" + "A".repeat(500),
                GraphQLParser.parse(applied).firstOperation().name());
    }

    @Test
    void everyVariantStaysValidGraphQLStillAskingForSchema() {
        for (IntrospectionBypass bypass : IntrospectionBypass.values()) {
            String applied = bypass.apply(PROBE);
            OperationDefinition operation = GraphQLParser.parse(applied).firstOperation();

            assertNotNull(operation, bypass.name());
            assertEquals("__schema", operation.firstRootField().name(), bypass.name());
        }
    }

    @Test
    void everyVariantExceptNoneChangesTheQuery() {
        for (IntrospectionBypass bypass : IntrospectionBypass.values()) {
            String applied = bypass.apply(PROBE);

            if (bypass == IntrospectionBypass.NONE) {
                assertEquals(PROBE, applied);
            } else {
                assertNotEquals(PROBE, applied, bypass.name());
            }
        }
    }

    @Test
    void appliesToTheFullIntrospectionQuery() {
        String applied = IntrospectionBypass.NEWLINE.apply(IntrospectionHandler.FULL_INTROSPECTION_QUERY);

        assertFalse(applied.contains("__schema {"));
        assertEquals("__schema", GraphQLParser.parse(applied).firstOperation().firstRootField().name());
    }

    @Test
    void onlyTheFirstSchemaOccurrenceIsRewritten() {
        String query = "query { __schema { types { name } } second: __schema { types { name } } }";
        String applied = IntrospectionBypass.NEWLINE.apply(query);

        assertTrue(applied.contains("__schema\n{"));
        assertTrue(applied.contains("second: __schema {"));
    }

    @Test
    void queriesWithoutTheAnchorAreReturnedUnchanged() {
        String query = "query { __typename }";

        for (IntrospectionBypass bypass : List.of(IntrospectionBypass.NEWLINE,
                IntrospectionBypass.TAB, IntrospectionBypass.INCLUDE_DIRECTIVE)) {
            assertEquals(query, bypass.apply(query), bypass.name());
        }
    }

    @Test
    void toleratesNullQueries() {
        for (IntrospectionBypass bypass : IntrospectionBypass.values()) {
            assertNull(bypass.apply(null), bypass.name());
        }
    }

    @Test
    void everyVariantHasADescription() {
        for (IntrospectionBypass bypass : IntrospectionBypass.values()) {
            assertNotNull(bypass.description());
            assertFalse(bypass.description().isBlank());
        }
    }
}
