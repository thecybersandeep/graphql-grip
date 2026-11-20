package com.grip.graphql.document;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLPrinterTest {

    private static final String LOGIN_MUTATION =
            "\n    mutation login($input: LoginInput!) {\n"
                    + "        login(input: $input) {\n"
                    + "            token\n"
                    + "            success\n"
                    + "        }\n"
                    + "    }";

    @Test
    void printsNamedOperationWithVariableDefinitions() {
        String expected = """
                mutation login($input: LoginInput!) {
                  login(input: $input) {
                    token
                    success
                  }
                }""";

        assertEquals(expected, GraphQLPrinter.print(GraphQLParser.parse(LOGIN_MUTATION)));
    }

    @Test
    void printsVariableDefinitionsForReuseOnGeneratedOperations() {
        OperationDefinition operation = GraphQLParser.parse(LOGIN_MUTATION).firstOperation();

        assertEquals("($input: LoginInput!)",
                GraphQLPrinter.printVariableDefinitions(operation.variableDefinitions()));
        assertEquals("", GraphQLPrinter.printVariableDefinitions(java.util.List.of()));
        assertEquals("", GraphQLPrinter.printVariableDefinitions(null));
    }

    @Test
    void printsRootFieldWithItsSelectionSet() {
        String expected = """
                login(input: $input) {
                  token
                  success
                }""";

        OperationDefinition operation = GraphQLParser.parse(LOGIN_MUTATION).firstOperation();
        assertEquals(expected, GraphQLPrinter.print(operation.firstRootField()));
    }

    @Test
    void printsShorthandQueryWithoutAHeader() {
        String expected = """
                {
                  viewer {
                    id
                  }
                }""";

        assertEquals(expected, GraphQLPrinter.print(GraphQLParser.parse("{ viewer { id } }")));
    }

    @Test
    void printsAnonymousOperationWithItsKeyword() {
        String expected = """
                query {
                  viewer {
                    id
                  }
                }""";

        assertEquals(expected, GraphQLPrinter.print(GraphQLParser.parse("query { viewer { id } }")));
    }

    @Test
    void printsDirectivesWithArgumentsAndVariableDefaults() {
        String expected = """
                query($v: Boolean! = true) {
                  __schema @include(if: $v) {
                    types {
                      name
                    }
                  }
                }""";

        assertEquals(expected, GraphQLPrinter.print(GraphQLParser.parse(
                "query($v: Boolean! = true) { __schema @include(if: $v) { types { name } } }")));
    }

    @Test
    void printsInlineFragmentWithDirective() {
        String expected = """
                query GripDeferTest {
                  __schema {
                    queryType {
                      name
                    }
                    ... @defer {
                      types {
                        name
                      }
                    }
                  }
                }""";

        assertEquals(expected, GraphQLPrinter.print(GraphQLParser.parse(expected)));
    }

    @Test
    void printsFragmentDefinitionsAndSpreads() {
        String expected = """
                fragment SchemaFields on __Schema {
                  types {
                    name
                    fields {
                      name
                    }
                  }
                }

                query {
                  __schema {
                    ...SchemaFields
                  }
                }""";

        assertEquals(expected, GraphQLPrinter.print(GraphQLParser.parse(expected)));
    }

    @Test
    void printsAliasedSelections() {
        String expected = """
                query {
                  a: user(id: 1) {
                    id
                  }
                  b: user(id: 2) {
                    id
                  }
                }""";

        assertEquals(expected, GraphQLPrinter.print(GraphQLParser.parse(expected)));
    }

    @Test
    void printsListObjectAndEnumValues() {
        String expected = """
                mutation {
                  create(input: {name: "x", tags: ["a", "b"], role: ADMIN, count: 3, missing: null}) {
                    id
                  }
                }""";

        assertEquals(expected, GraphQLPrinter.print(GraphQLParser.parse(expected)));
    }

    @Test
    void printsNestedListAndNonNullTypes() {
        VariableDefinition variable = GraphQLParser.parse("query($ids: [ID!]!) { nodes(ids: $ids) { id } }")
                .firstOperation().variableDefinition("ids");

        assertEquals("[ID!]!", GraphQLPrinter.print(variable.type()));
    }

    @Test
    void escapesStringValues() {
        Field root = GraphQLParser.parse("query { a(x: \"q\\\"uote\\\\back\\ttab\") }")
                .firstOperation().firstRootField();

        assertEquals("\"q\\\"uote\\\\back\\ttab\"", GraphQLPrinter.print(root.argument("x").value()));
    }

    @Test
    void printsBlockStringsThatParseBackToTheSameValue() {
        String source = "query { search(text: \"\"\"\n  first\n    second\n  \"\"\") { id } }";
        Value original = GraphQLParser.parse(source).firstOperation().firstRootField()
                .argument("text").value();

        String printed = GraphQLPrinter.print(GraphQLParser.parse(source));
        Value reparsed = GraphQLParser.parse(printed).firstOperation().firstRootField()
                .argument("text").value();

        assertEquals(original, reparsed);
    }

    @Test
    void printsStableOutputAcrossRepeatedParsing() {
        assertStable(LOGIN_MUTATION);
        assertStable("{ viewer { id } }");
        assertStable("query { __schema\n{ types { name } } }");
        assertStable("query query { __typename }");
        assertStable("query First { a } mutation Second { b(c: [1, 2]) { d } }");
        assertStable("fragment F on Query { __typename } query { ...F }");
        assertStable("query { ... on Query { __schema { types { name } } } }");
        assertStable("mutation($i: [In!] = [{a: 1}]) { go(i: $i) @skip(if: false) { ok } }");
    }

    @Test
    void preservesVariableDefinitionsThroughARoundTrip() {
        String printed = GraphQLPrinter.print(GraphQLParser.parse(LOGIN_MUTATION));
        OperationDefinition operation = GraphQLParser.parse(printed).firstOperation();

        assertEquals("login", operation.name());
        assertEquals(OperationType.MUTATION, operation.operationType());
        assertNotNull(operation.variableDefinition("input"));
        assertEquals("LoginInput", TypeNode.baseName(operation.variableDefinition("input").type()));
    }

    @Test
    void printsGeneratedAliasesCarryingTheOriginalVariableReference() {
        OperationDefinition operation = GraphQLParser.parse(LOGIN_MUTATION).firstOperation();
        Field root = operation.firstRootField();

        OperationDefinition generated = operation
                .withName("GripAliasedMutations")
                .withSelectionSet(SelectionSet.of(root.withAlias("m0"), root.withAlias("m1")));

        String expected = """
                mutation GripAliasedMutations($input: LoginInput!) {
                  m0: login(input: $input) {
                    token
                    success
                  }
                  m1: login(input: $input) {
                    token
                    success
                  }
                }""";

        assertEquals(expected, GraphQLPrinter.print(generated));
        assertNotNull(GraphQLParser.parse(GraphQLPrinter.print(generated))
                .firstOperation().variableDefinition("input"));
    }

    private static void assertStable(String source) {
        String once = GraphQLPrinter.print(GraphQLParser.parse(source));
        String twice = GraphQLPrinter.print(GraphQLParser.parse(once));
        assertEquals(once, twice, source);
    }
}
