package com.grip.graphql.editor;

import com.google.gson.JsonObject;
import com.grip.graphql.document.Argument;
import com.grip.graphql.document.Field;
import com.grip.graphql.document.GraphQLParser;
import com.grip.graphql.document.GraphQLSyntaxException;
import com.grip.graphql.document.InlineFragment;
import com.grip.graphql.document.OperationDefinition;
import com.grip.graphql.document.OperationType;
import com.grip.graphql.document.Selection;
import com.grip.graphql.document.SelectionSet;
import com.grip.graphql.document.Value;
import com.grip.graphql.document.VariableDefinition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLPayloadsTest {

    private static final String LOGIN_QUERY = """
            mutation login($input: LoginInput!) {
              login(input: $input) {
                token
                success
              }
            }""";

    private static final String INLINE_ARGUMENT_QUERY =
            "query search { search(filter: {name: \"grip\", limit: 10}) { id name } }";

    private static final Set<String> TYPE_VALUED_FIELDS = Set.of(
            "types", "queryType", "mutationType", "subscriptionType",
            "type", "ofType", "__type", "interfaces", "possibleTypes");

    private static Map<String, String> payloads(GraphQLOperationSource source) {
        Map<String, String> all = new LinkedHashMap<>();
        all.put("aliasOverloading", GraphQLPayloads.aliasOverloading(source, 3));
        all.put("fieldDuplication", GraphQLPayloads.fieldDuplication(source, 3));
        all.put("widthAttack", GraphQLPayloads.widthAttack(4));
        all.put("widthProbe", GraphQLPayloads.widthProbe(4));
        all.put("fragmentOverloading", GraphQLPayloads.fragmentOverloading(3));
        all.put("deepRecursion", GraphQLPayloads.deepRecursion(4));
        all.put("circularIntrospection", GraphQLPayloads.circularIntrospection(4));
        all.put("depthProbe", GraphQLPayloads.depthProbe(4));
        all.put("aliasedMutations", GraphQLPayloads.aliasedMutations(source, 3));
        all.put("mutationFragments", GraphQLPayloads.mutationFragments(source, 3));
        all.put("includeOverloading", GraphQLPayloads.directiveOverloading(source, "include", 3));
        all.put("skipOverloading", GraphQLPayloads.directiveOverloading(source, "skip", 3));
        all.put("mixedDirectives", GraphQLPayloads.mixedDirectives(source, 4));
        all.put("longOperationName", GraphQLPayloads.longOperationName());
        all.put("bypassLongOperationName", GraphQLPayloads.bypassLongOperationName());
        all.put("fieldSuggestionProbe", GraphQLPayloads.fieldSuggestionProbe(source));
        all.put("typenameProbe", GraphQLPayloads.TYPENAME_PROBE);
        all.put("typenameMutation", GraphQLPayloads.TYPENAME_MUTATION);
        all.put("introspectionPing", GraphQLPayloads.INTROSPECTION_PING);
        all.put("typeBypass", GraphQLPayloads.TYPE_BYPASS);
        all.put("deferTest", GraphQLPayloads.DEFER_TEST);
        all.put("streamTest", GraphQLPayloads.STREAM_TEST);
        all.put("directiveDiscovery", GraphQLPayloads.DIRECTIVE_DISCOVERY);
        all.put("fingerprint", GraphQLPayloads.FINGERPRINT);
        all.put("typeEnumeration", GraphQLPayloads.TYPE_ENUMERATION);
        all.put("errorTrigger", GraphQLPayloads.ERROR_TRIGGER);
        all.put("specialCharsOperationName", GraphQLPayloads.SPECIAL_CHARS_OPERATION_NAME);
        all.put("reservedWordsOperationName", GraphQLPayloads.RESERVED_WORDS_OPERATION_NAME);
        all.put("bypassNewline", GraphQLPayloads.BYPASS_NEWLINE);
        all.put("bypassTab", GraphQLPayloads.BYPASS_TAB);
        all.put("bypassCRLF", GraphQLPayloads.BYPASS_CRLF);
        all.put("bypassFragment", GraphQLPayloads.BYPASS_FRAGMENT);
        all.put("bypassInlineFragment", GraphQLPayloads.BYPASS_INLINE_FRAGMENT);
        all.put("bypassAlias", GraphQLPayloads.BYPASS_ALIAS);
        all.put("bypassInclude", GraphQLPayloads.BYPASS_INCLUDE);
        all.put("bypassSkip", GraphQLPayloads.BYPASS_SKIP);
        all.put("bypassGet", GraphQLPayloads.BYPASS_GET);
        all.put("fullIntrospection", GraphQLPayloads.FULL_INTROSPECTION);
        return all;
    }

    @Test
    void everyPayloadParsesForACapturedMutation() {
        payloads(login()).forEach((label, query) ->
                assertDoesNotThrow(() -> GraphQLParser.parse(query), label));
    }

    @Test
    void everyPayloadParsesForAFieldWithInlineObjectArguments() {
        payloads(inlineArguments()).forEach((label, query) ->
                assertDoesNotThrow(() -> GraphQLParser.parse(query), label));
    }

    @Test
    void everyPayloadParsesWithNoRequestLoaded() {
        payloads(GraphQLOperationSource.empty()).forEach((label, query) ->
                assertDoesNotThrow(() -> GraphQLParser.parse(query), label));
    }

    @Test
    void everyPayloadStillParsesAfterTheEnvelopeWriterTouchesIt() {
        GraphQLOperationSource source = login();

        payloads(source).forEach((label, query) -> {
            JsonObject body = GraphQLPayloadWriter.body(source, query);
            assertDoesNotThrow(() -> GraphQLParser.parse(body.get("query").getAsString()), label);
        });
    }

    @Test
    void noPayloadSelectsAFieldOnATypeThatDoesNotHaveIt() {
        payloads(login()).forEach((label, query) ->
                assertIntrospectionFieldsAreReachable(label,
                        GraphQLParser.parse(query).firstOperation().selectionSet(), null));
    }

    @Test
    void noPayloadConditionsOnARootTypeNameItCannotKnow() {
        payloads(login()).forEach((label, query) -> {
            assertFalse(query.contains("on Query"), label);
            assertFalse(query.contains("on Mutation"), label);
            assertFalse(query.contains("on Subscription"), label);
        });
    }

    @Test
    void duplicatedFieldsKeepTheArgumentsAndSelectionOfTheCapturedField() {
        OperationDefinition operation = GraphQLParser
                .parse(GraphQLPayloads.fieldDuplication(login(), 3)).firstOperation();

        assertEquals(3, operation.selectionSet().selections().size());
        for (Selection selection : operation.selectionSet().selections()) {
            Field field = (Field) selection;
            assertEquals("login", field.name());
            assertNull(field.alias());
            assertNotNull(field.argument("input"));
            assertTrue(field.hasSelectionSet());
        }
    }

    @Test
    void aliasedCopiesKeepTheArgumentsAndSelectionOfTheCapturedField() {
        OperationDefinition operation = GraphQLParser
                .parse(GraphQLPayloads.aliasOverloading(login(), 5)).firstOperation();

        assertEquals(5, operation.selectionSet().selections().size());
        Field first = (Field) operation.selectionSet().selections().get(0);

        assertEquals("a0", first.alias());
        assertEquals("login", first.name());
        assertNotNull(first.argument("input"));
        assertTrue(first.hasSelectionSet());
    }

    @Test
    void directivesLandAfterTheArgumentsOfAFieldWithInlineObjectArguments() {
        Field field = firstField(GraphQLPayloads.directiveOverloading(inlineArguments(), "include", 3));

        assertEquals("search", field.name());
        assertNotNull(field.argument("filter"));
        assertEquals(3, field.directives().size());
        assertTrue(field.hasSelectionSet());
    }

    @Test
    void directiveOverloadingKeepsTheFieldResolvable() {
        assertTrue(GraphQLPayloads.directiveOverloading(login(), "include", 1)
                .contains("@include(if: true)"));
        assertTrue(GraphQLPayloads.directiveOverloading(login(), "skip", 1)
                .contains("@skip(if: false)"));
    }

    @Test
    void mixedDirectivesAlternateIncludeAndSkipOnOneField() {
        Field field = firstField(GraphQLPayloads.mixedDirectives(login(), 4));

        assertEquals(4, field.directives().size());
        assertEquals("include", field.directives().get(0).name());
        assertEquals("skip", field.directives().get(1).name());
        assertEquals("include", field.directives().get(2).name());
        assertEquals("skip", field.directives().get(3).name());
    }

    @Test
    void directivesGoOntoTypenameWhenNothingIsLoaded() {
        Field field = firstField(
                GraphQLPayloads.mixedDirectives(GraphQLOperationSource.empty(), 2));

        assertEquals("__typename", field.name());
        assertEquals(2, field.directives().size());
    }

    @Test
    void mutationFragmentsWrapTheCapturedMutationWithoutNamingAType() {
        OperationDefinition operation = GraphQLParser
                .parse(GraphQLPayloads.mutationFragments(login(), 3)).firstOperation();

        assertEquals(OperationType.MUTATION, operation.operationType());
        assertEquals(3, operation.selectionSet().selections().size());

        InlineFragment fragment = (InlineFragment) operation.selectionSet().selections().get(0);
        assertNull(fragment.typeCondition());

        Field field = (Field) fragment.selectionSet().selections().get(0);
        assertEquals("m0", field.alias());
        assertEquals("login", field.name());
        assertNotNull(field.argument("input"));
    }

    @Test
    void aliasedMutationsFallBackToTypenameWhenTheCaptureIsNotAMutation() {
        OperationDefinition operation = GraphQLParser
                .parse(GraphQLPayloads.aliasedMutations(inlineArguments(), 2)).firstOperation();

        assertEquals(OperationType.MUTATION, operation.operationType());

        Field field = (Field) operation.selectionSet().selections().get(0);
        assertEquals("m0", field.alias());
        assertEquals("__typename", field.name());
        assertFalse(field.hasSelectionSet());
    }

    @Test
    void mutationPayloadsCarryTheCapturedVariablesThroughTheWriter() {
        GraphQLOperationSource source = login();

        for (String query : List.of(GraphQLPayloads.aliasedMutations(source, 2),
                GraphQLPayloads.mutationFragments(source, 2),
                GraphQLPayloads.fieldDuplication(source, 2),
                GraphQLPayloads.aliasOverloading(source, 2),
                GraphQLPayloads.mixedDirectives(source, 2))) {

            JsonObject body = GraphQLPayloadWriter.body(source, query);
            OperationDefinition operation = GraphQLParser
                    .parse(body.get("query").getAsString()).firstOperation();

            assertNotNull(operation.variableDefinition("input"), query);
            assertTrue(body.has("variables"), query);
        }
    }

    @Test
    void widthAttackSendsADistinctArgumentPerAlias() {
        OperationDefinition operation = GraphQLParser
                .parse(GraphQLPayloads.widthAttack(5)).firstOperation();

        Set<String> names = new LinkedHashSet<>();
        for (Selection selection : operation.selectionSet().selections()) {
            Argument argument = ((Field) selection).argument("name");
            if (argument != null && argument.value() instanceof Value.StringValue text) {
                names.add(text.value());
            }
        }

        assertEquals(5, names.size());
        assertEquals(6, operation.selectionSet().selections().size());
    }

    @Test
    void countedPayloadsHonourTheirCount() {
        assertEquals(7, GraphQLParser.parse(GraphQLPayloads.widthProbe(7))
                .firstOperation().selectionSet().selections().size());
        assertEquals(7, GraphQLParser.parse(GraphQLPayloads.fragmentOverloading(7))
                .fragments().size());
        assertEquals(7, firstField(GraphQLPayloads.fragmentOverloading(7))
                .selectionSet().selections().size());
        assertEquals(9, GraphQLParser.parse(GraphQLPayloads.mutationFragments(login(), 9))
                .firstOperation().selectionSet().selections().size());
    }

    @Test
    void recursivePayloadsNestAsDeepAsTheyClaim() {
        assertEquals(2 * 4 + 3, depthOf(GraphQLPayloads.depthProbe(4)));
        assertEquals(2 * 4 + 3, depthOf(GraphQLPayloads.deepRecursion(4)));
        assertEquals(3 * 4 + 3, depthOf(GraphQLPayloads.circularIntrospection(4)));
    }

    @Test
    void theUnicodeOperationNameIsDeliberatelyInvalid() {
        assertThrows(GraphQLSyntaxException.class,
                () -> GraphQLParser.parse(GraphQLPayloads.UNICODE_OPERATION_NAME));
    }

    @Test
    void theInvalidOperationNameReachesTheServerUnchanged() {
        assertEquals(GraphQLPayloads.UNICODE_OPERATION_NAME,
                GraphQLPayloadWriter.body(login(), GraphQLPayloads.UNICODE_OPERATION_NAME)
                        .get("query").getAsString());
    }

    @Test
    void whitespaceBypassesKeepTheirWhitespaceThroughTheWriter() {
        GraphQLOperationSource source = login();

        assertTrue(GraphQLPayloadWriter.body(source, GraphQLPayloads.BYPASS_NEWLINE)
                .get("query").getAsString().contains("__schema\n{"));
        assertTrue(GraphQLPayloadWriter.body(source, GraphQLPayloads.BYPASS_TAB)
                .get("query").getAsString().contains("__schema\t{"));
        assertTrue(GraphQLPayloadWriter.body(source, GraphQLPayloads.BYPASS_CRLF)
                .get("query").getAsString().contains("__schema\r\n{"));
    }

    @Test
    void theDirectiveBypassesSupplyTheirOwnVariableValue() {
        for (String query : List.of(GraphQLPayloads.BYPASS_INCLUDE, GraphQLPayloads.BYPASS_SKIP)) {
            VariableDefinition definition = GraphQLParser.parse(query)
                    .firstOperation().variableDefinition("v");

            assertNotNull(definition, query);
            assertNotNull(definition.defaultValue(), query);
        }

        assertFalse(GraphQLPayloadWriter.body(login(), GraphQLPayloads.BYPASS_INCLUDE)
                .has("variables"));
    }

    @Test
    void theGetBypassIsAShorthandOperationWithNoKeyword() {
        OperationDefinition operation = GraphQLParser
                .parse(GraphQLPayloads.BYPASS_GET).firstOperation();

        assertTrue(operation.shorthand());
        assertEquals(OperationType.QUERY, operation.operationType());
    }

    @Test
    void theFragmentBypassConditionsOnTheSpecMandatedSchemaType() {
        assertEquals("__Schema", GraphQLParser.parse(GraphQLPayloads.BYPASS_FRAGMENT)
                .fragment("SchemaFields").typeCondition());
    }

    @Test
    void theInlineFragmentBypassHasNoTypeCondition() {
        Selection selection = GraphQLParser.parse(GraphQLPayloads.BYPASS_INLINE_FRAGMENT)
                .firstOperation().selectionSet().selections().get(0);

        assertInstanceOf(InlineFragment.class, selection);
        assertNull(((InlineFragment) selection).typeCondition());
    }

    @Test
    void theApqHashIsLowercaseHexSha256() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                GraphQLPayloads.sha256(""));
        assertEquals(64, GraphQLPayloads.sha256(LOGIN_QUERY).length());
    }

    @Test
    void fieldSuggestionProbeAsksAboutNamesNearTheCapturedField() {
        String query = GraphQLPayloads.fieldSuggestionProbe(login());

        assertTrue(query.contains("logi "), query);
        assertTrue(query.contains("loginn "), query);
        assertTrue(query.contains("logins "), query);
        assertTrue(query.contains("getLogin "), query);
        assertTrue(query.contains("viewer "), query);
    }

    @Test
    void fieldSuggestionProbeNeverMutatesAnIntrospectionFieldName() {
        String query = GraphQLPayloads.fieldSuggestionProbe(GraphQLOperationSource.empty());

        assertFalse(query.contains("__typenam "), query);
        assertFalse(query.contains("__typenames"), query);
        assertTrue(query.contains("viewer "), query);
    }

    @Test
    void theFingerprintAsksOnlyForFieldsEveryServerHas() {
        assertFalse(GraphQLPayloads.FINGERPRINT.contains("description"));
        assertTrue(GraphQLPayloads.FINGERPRINT.contains("queryType { name }"));
        assertTrue(GraphQLPayloads.FINGERPRINT.contains("directives"));
    }

    private static void assertIntrospectionFieldsAreReachable(String label, SelectionSet set,
                                                              String parent) {
        for (Selection selection : set.selections()) {
            if (selection instanceof InlineFragment inline) {
                assertIntrospectionFieldsAreReachable(label, inline.selectionSet(), parent);
                continue;
            }
            if (!(selection instanceof Field field)) {
                continue;
            }

            if ("types".equals(field.name())) {
                assertEquals("__schema", parent, label + ": types selected on " + parent);
            }
            if ("fields".equals(field.name())) {
                assertTrue(TYPE_VALUED_FIELDS.contains(parent),
                        label + ": fields selected on " + parent);
            }
            if (field.selectionSet() != null) {
                assertIntrospectionFieldsAreReachable(label, field.selectionSet(), field.name());
            }
        }
    }

    private static int depthOf(String query) {
        return depthOf(GraphQLParser.parse(query).firstOperation().selectionSet());
    }

    private static int depthOf(SelectionSet set) {
        int deepest = 0;
        for (Selection selection : set.selections()) {
            SelectionSet nested = selection instanceof Field field ? field.selectionSet()
                    : selection instanceof InlineFragment inline ? inline.selectionSet() : null;
            if (nested != null) {
                deepest = Math.max(deepest, depthOf(nested));
            }
        }
        return deepest + 1;
    }

    private static Field firstField(String query) {
        Selection selection = GraphQLParser.parse(query)
                .firstOperation().selectionSet().selections().get(0);
        return selection instanceof Field field ? field : null;
    }

    private static GraphQLOperationSource login() {
        JsonObject input = new JsonObject();
        input.addProperty("username", "test");
        input.addProperty("password", "test");

        JsonObject variables = new JsonObject();
        variables.add("input", input);

        JsonObject body = new JsonObject();
        body.addProperty("query", LOGIN_QUERY);
        body.addProperty("operationName", "login");
        body.add("variables", variables);

        return GraphQLOperationSource.from("POST", "/graphql", "application/json", body.toString());
    }

    private static GraphQLOperationSource inlineArguments() {
        JsonObject body = new JsonObject();
        body.addProperty("query", INLINE_ARGUMENT_QUERY);

        return GraphQLOperationSource.from("POST", "/graphql", "application/json", body.toString());
    }
}
