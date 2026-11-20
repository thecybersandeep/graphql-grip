package com.grip.graphql.document;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLParserTest {

    private static final String LOGIN_MUTATION =
            "\n    mutation login($input: LoginInput!) {\n"
                    + "        login(input: $input) {\n"
                    + "            token\n"
                    + "            success\n"
                    + "        }\n"
                    + "    }";

    @Test
    void parsesNamedMutationWithVariableDefinitions() {
        GraphQLDocument document = GraphQLParser.parse(LOGIN_MUTATION);
        OperationDefinition operation = document.firstOperation();

        assertEquals(1, document.operations().size());
        assertEquals(OperationType.MUTATION, operation.operationType());
        assertEquals("login", operation.name());
        assertFalse(operation.shorthand());
        assertTrue(operation.usesVariables());

        VariableDefinition variable = operation.variableDefinition("input");
        assertNotNull(variable);
        assertInstanceOf(TypeNode.NonNull.class, variable.type());
        assertEquals("LoginInput", TypeNode.baseName(variable.type()));
        assertTrue(variable.isRequired());
        assertNull(variable.defaultValue());
    }

    @Test
    void keepsVariableReferenceOnRootFieldArgument() {
        OperationDefinition operation = GraphQLParser.parse(LOGIN_MUTATION).firstOperation();
        Field root = operation.firstRootField();

        assertEquals("login", root.name());
        assertEquals("login", root.responseKey());

        Argument argument = root.argument("input");
        assertNotNull(argument);
        assertEquals(new Value.VariableValue("input"), argument.value());

        List<Field> selected = root.selectionSet().fields();
        assertEquals(2, selected.size());
        assertEquals("token", selected.get(0).name());
        assertEquals("success", selected.get(1).name());
    }

    @Test
    void parsesShorthandQuery() {
        OperationDefinition operation = GraphQLParser.parse("{ viewer { id } }").firstOperation();

        assertEquals(OperationType.QUERY, operation.operationType());
        assertNull(operation.name());
        assertTrue(operation.shorthand());
        assertEquals("viewer", operation.firstRootField().name());
    }

    @Test
    void distinguishesAnonymousQueryFromShorthand() {
        assertFalse(GraphQLParser.parse("query { viewer { id } }").firstOperation().shorthand());
        assertTrue(GraphQLParser.parse("{ viewer { id } }").firstOperation().shorthand());
    }

    @Test
    void parsesAliasedRootFields() {
        OperationDefinition operation = GraphQLParser.parse(
                "query { a: user(id: 1) { id } b: user(id: 2) { id } }").firstOperation();

        List<Field> roots = operation.rootFields();
        assertEquals(2, roots.size());
        assertEquals("a", roots.get(0).alias());
        assertEquals("user", roots.get(0).name());
        assertEquals("a", roots.get(0).responseKey());
        assertEquals("b", roots.get(1).alias());
    }

    @Test
    void parsesOperationNamedWithAReservedWord() {
        OperationDefinition operation = GraphQLParser.parse("query query { __typename }").firstOperation();

        assertEquals(OperationType.QUERY, operation.operationType());
        assertEquals("query", operation.name());
        assertEquals("__typename", operation.firstRootField().name());
    }

    @Test
    void parsesFragmentDefinitionAndSpread() {
        GraphQLDocument document = GraphQLParser.parse(
                "fragment SchemaFields on __Schema { types { name fields { name } } }\n"
                        + "query { __schema { ...SchemaFields } }");

        assertEquals(2, document.definitions().size());

        FragmentDefinition fragment = document.fragment("SchemaFields");
        assertNotNull(fragment);
        assertEquals("__Schema", fragment.typeCondition());

        Field schema = document.firstOperation().firstRootField();
        Selection spread = schema.selectionSet().selections().get(0);
        assertInstanceOf(FragmentSpread.class, spread);
        assertEquals("SchemaFields", ((FragmentSpread) spread).name());
    }

    @Test
    void parsesInlineFragmentWithTypeCondition() {
        Selection selection = GraphQLParser.parse("query { ... on Query { __schema { types { name } } } }")
                .firstOperation().selectionSet().selections().get(0);

        InlineFragment inline = assertInstanceOf(InlineFragment.class, selection);
        assertEquals("Query", inline.typeCondition());
        assertEquals("__schema", inline.selectionSet().firstField().name());
    }

    @Test
    void parsesInlineFragmentWithDirectiveAndNoTypeCondition() {
        OperationDefinition operation = GraphQLParser.parse(
                "query GripDeferTest {\n  __schema {\n    queryType {\n      name\n    }\n"
                        + "    ... @defer {\n      types {\n        name\n      }\n    }\n  }\n}")
                .firstOperation();

        Field schema = operation.firstRootField();
        Selection deferred = schema.selectionSet().selections().get(1);

        InlineFragment inline = assertInstanceOf(InlineFragment.class, deferred);
        assertNull(inline.typeCondition());
        assertEquals(1, inline.directives().size());
        assertEquals("defer", inline.directives().get(0).name());
    }

    @Test
    void parsesDirectiveArgumentsAndVariableDefaults() {
        OperationDefinition operation = GraphQLParser.parse(
                "query($v: Boolean! = true) { __schema @include(if: $v) { types { name } } }")
                .firstOperation();

        VariableDefinition variable = operation.variableDefinition("v");
        assertEquals(new Value.BooleanValue(true), variable.defaultValue());
        assertFalse(variable.isRequired());

        Directive include = operation.firstRootField().directives().get(0);
        assertEquals("include", include.name());
        assertEquals("if", include.arguments().get(0).name());
        assertEquals(new Value.VariableValue("v"), include.arguments().get(0).value());
    }

    @Test
    void parsesListAndNonNullTypes() {
        VariableDefinition variable = GraphQLParser.parse("query($ids: [ID!]!) { nodes(ids: $ids) { id } }")
                .firstOperation().variableDefinition("ids");

        TypeNode.NonNull outer = assertInstanceOf(TypeNode.NonNull.class, variable.type());
        TypeNode.ListOf list = assertInstanceOf(TypeNode.ListOf.class, outer.element());
        TypeNode.NonNull inner = assertInstanceOf(TypeNode.NonNull.class, list.element());

        assertEquals(new TypeNode.Named("ID"), inner.element());
        assertEquals("ID", TypeNode.baseName(variable.type()));
    }

    @Test
    void parsesObjectListEnumAndNullValues() {
        Field root = GraphQLParser.parse(
                "mutation { create(input: {name: \"x\", tags: [\"a\", \"b\"], role: ADMIN,"
                        + " count: 3, ratio: 1.5, missing: null, flag: false}) { id } }")
                .firstOperation().firstRootField();

        Value.ObjectValue input = assertInstanceOf(Value.ObjectValue.class, root.argument("input").value());

        assertEquals(new Value.StringValue("x", false), input.field("name"));
        assertEquals(new Value.EnumValue("ADMIN"), input.field("role"));
        assertEquals(new Value.IntValue("3"), input.field("count"));
        assertEquals(new Value.FloatValue("1.5"), input.field("ratio"));
        assertEquals(new Value.NullValue(), input.field("missing"));
        assertEquals(new Value.BooleanValue(false), input.field("flag"));

        Value.ListValue tags = assertInstanceOf(Value.ListValue.class, input.field("tags"));
        assertEquals(2, tags.values().size());
        assertEquals(new Value.StringValue("b", false), tags.values().get(1));
    }

    @Test
    void parsesNegativeAndExponentNumbers() {
        Field root = GraphQLParser.parse("query { a(i: -42, f: -0.5, e: 1.0e10) }")
                .firstOperation().firstRootField();

        assertEquals(new Value.IntValue("-42"), root.argument("i").value());
        assertEquals(new Value.FloatValue("-0.5"), root.argument("f").value());
        assertEquals(new Value.FloatValue("1.0e10"), root.argument("e").value());
    }

    @Test
    void ignoresCommentsAndCommas() {
        OperationDefinition operation = GraphQLParser.parse(
                "query { # pick two\n  a, b\n} # trailing").firstOperation();

        List<Field> roots = operation.rootFields();
        assertEquals(2, roots.size());
        assertEquals("a", roots.get(0).name());
        assertEquals("b", roots.get(1).name());
    }

    @Test
    void acceptsWhitespaceUsedToBypassIntrospectionFilters() {
        assertEquals("__schema", GraphQLParser.parse("query { __schema\n{ types { name } } }")
                .firstOperation().firstRootField().name());
        assertEquals("__schema", GraphQLParser.parse("query { __schema\t{ types { name } } }")
                .firstOperation().firstRootField().name());
        assertEquals("__schema", GraphQLParser.parse("query { __schema\r\n{ types { name } } }")
                .firstOperation().firstRootField().name());
    }

    @Test
    void parsesEscapeSequencesInStrings() {
        Field root = GraphQLParser.parse("query { a(x: \"q\\\"uote\\\\back\\ttab\") }")
                .firstOperation().firstRootField();

        assertEquals(new Value.StringValue("q\"uote\\back\ttab", false), root.argument("x").value());
    }

    @Test
    void parsesUnicodeEscapeSequences() {
        String source = "query { a(x: \"" + "\\" + "u0041" + "\") }";
        Field root = GraphQLParser.parse(source).firstOperation().firstRootField();

        assertEquals(new Value.StringValue("A", false), root.argument("x").value());
    }

    @Test
    void dedentsBlockStringValues() {
        String source = "query { search(text: \"\"\"\n  first\n    second\n  \"\"\") { id } }";
        Field root = GraphQLParser.parse(source).firstOperation().firstRootField();

        Value.StringValue text = assertInstanceOf(Value.StringValue.class, root.argument("text").value());
        assertTrue(text.block());
        assertEquals("first\n  second", text.value());
    }

    @Test
    void parsesMultipleOperationsInOneDocument() {
        GraphQLDocument document = GraphQLParser.parse(
                "query First { a } mutation Second { b } subscription Third { c }");

        assertEquals(3, document.operations().size());
        assertEquals(OperationType.MUTATION, document.operation("Second").operationType());
        assertEquals(OperationType.SUBSCRIPTION, document.operation("Third").operationType());
        assertEquals("First", document.firstOperation().name());
    }

    @Test
    void looksUpOperationByNameAndFallsBackToTheFirst() {
        GraphQLDocument document = GraphQLParser.parse("query First { a } query Second { b }");

        assertEquals("Second", document.operation("Second").name());
        assertEquals("First", document.operation(null).name());
        assertEquals("First", document.operation("  ").name());
        assertNull(document.operation("Missing"));
    }

    @Test
    void rejectsEmptySelectionSet() {
        assertThrows(GraphQLSyntaxException.class, () -> GraphQLParser.parse("query { }"));
    }

    @Test
    void rejectsUnterminatedSelectionSet() {
        GraphQLSyntaxException thrown = assertThrows(GraphQLSyntaxException.class,
                () -> GraphQLParser.parse("query { user { id } "));
        assertTrue(thrown.getMessage().contains("Unterminated selection set"));
    }

    @Test
    void rejectsUnterminatedString() {
        assertThrows(GraphQLSyntaxException.class, () -> GraphQLParser.parse("query { a(x: \"open) }"));
    }

    @Test
    void rejectsSchemaDefinitionLanguage() {
        GraphQLSyntaxException thrown = assertThrows(GraphQLSyntaxException.class,
                () -> GraphQLParser.parse("type User { id: ID }"));
        assertTrue(thrown.getMessage().contains("'type'"));
    }

    @Test
    void rejectsVariablesInDefaultValues() {
        GraphQLSyntaxException thrown = assertThrows(GraphQLSyntaxException.class,
                () -> GraphQLParser.parse("query($a: Int = $b) { x }"));
        assertTrue(thrown.getMessage().contains("Variables are not allowed"));
    }

    @Test
    void rejectsNumbersWithLeadingZeros() {
        assertThrows(GraphQLSyntaxException.class, () -> GraphQLParser.parse("query { a(i: 01) }"));
    }

    @Test
    void rejectsEmptyInput() {
        assertThrows(GraphQLSyntaxException.class, () -> GraphQLParser.parse(""));
        assertThrows(GraphQLSyntaxException.class, () -> GraphQLParser.parse("   \n  "));
        assertThrows(GraphQLSyntaxException.class, () -> GraphQLParser.parse(null));
    }

    @Test
    void reportsLineAndColumnForUnexpectedTokens() {
        GraphQLSyntaxException thrown = assertThrows(GraphQLSyntaxException.class,
                () -> GraphQLParser.parse("query {\n  @\n}"));

        assertEquals(2, thrown.getLine());
        assertEquals(3, thrown.getColumn());
        assertTrue(thrown.getMessage().contains("line 2, column 3"));
    }

    @Test
    void parsesUnderscoreHeavyOperationNames() {
        assertEquals("_______GripSpecial_____",
                GraphQLParser.parse("query _______GripSpecial_____ { __typename }")
                        .firstOperation().name());
    }

    @Test
    void rejectsNonAsciiOperationNamesSoDeliberatelyInvalidPayloadsStayIntact() {
        String source = "query Gripéèê { __typename }";

        assertThrows(GraphQLSyntaxException.class, () -> GraphQLParser.parse(source));
        assertNull(GraphQLParser.tryParse(source));
    }

    @Test
    void tryParseReturnsNullInsteadOfThrowing() {
        assertNull(GraphQLParser.tryParse("{\"query\": \"not a document\"}"));
        assertNull(GraphQLParser.tryParse("query { "));
        assertNotNull(GraphQLParser.tryParse("{ __typename }"));
    }
}
