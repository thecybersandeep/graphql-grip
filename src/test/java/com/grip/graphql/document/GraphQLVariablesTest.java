package com.grip.graphql.document;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLVariablesTest {

    @Test
    void collectsVariablesFromRootFieldArguments() {
        GraphQLDocument document = GraphQLParser.parse(
                "mutation login($input: LoginInput!) { login(input: $input) { token } }");

        assertEquals(Set.of("input"),
                GraphQLVariables.referencedBy(document, document.firstOperation()));
    }

    @Test
    void collectsVariablesFromNestedListAndObjectValues() {
        GraphQLDocument document = GraphQLParser.parse(
                "query($a: Int, $b: Int, $c: Boolean) { x(in: {k: [$a, {j: $b}]}) @include(if: $c) }");

        assertEquals(Set.of("a", "b", "c"),
                GraphQLVariables.referencedBy(document, document.firstOperation()));
    }

    @Test
    void collectsVariablesFromNestedSelections() {
        GraphQLDocument document = GraphQLParser.parse(
                "query($id: ID!) { user { posts(id: $id) { title } } }");

        assertEquals(Set.of("id"),
                GraphQLVariables.referencedBy(document, document.firstOperation()));
    }

    @Test
    void collectsVariablesThroughFragmentSpreads() {
        GraphQLDocument document = GraphQLParser.parse(
                "query($a: Int) { ...Outer }\n"
                        + "fragment Outer on Query { ...Inner }\n"
                        + "fragment Inner on Query { x(a: $a) }");

        assertEquals(Set.of("a"),
                GraphQLVariables.referencedBy(document, document.firstOperation()));
    }

    @Test
    void collectsVariablesFromInlineFragments() {
        GraphQLDocument document = GraphQLParser.parse(
                "query($a: Int) { ... on Query { x(a: $a) } }");

        assertEquals(Set.of("a"),
                GraphQLVariables.referencedBy(document, document.firstOperation()));
    }

    @Test
    void stopsOnCyclicFragmentSpreads() {
        GraphQLDocument document = GraphQLParser.parse(
                "query($a: Int) { ...F }\n"
                        + "fragment F on Query { x(a: $a) ...G }\n"
                        + "fragment G on Query { ...F }");

        assertEquals(Set.of("a"),
                GraphQLVariables.referencedBy(document, document.firstOperation()));
    }

    @Test
    void ignoresVariablesBelongingToOtherOperations() {
        GraphQLDocument document = GraphQLParser.parse(
                "query First($a: Int) { x(a: $a) } query Second($b: Int) { y(b: $b) }");

        assertEquals(Set.of("a"),
                GraphQLVariables.referencedBy(document, document.operation("First")));
        assertEquals(Set.of("b"),
                GraphQLVariables.referencedBy(document, document.operation("Second")));
    }

    @Test
    void returnsNothingForOperationsWithoutVariables() {
        GraphQLDocument document = GraphQLParser.parse("query { __schema { types { name } } }");

        assertTrue(GraphQLVariables.referencedBy(document, document.firstOperation()).isEmpty());
        assertTrue(GraphQLVariables.referencedBy(document, null).isEmpty());
    }

    @Test
    void reconcileAppendsOnlyReferencedDeclarations() {
        List<VariableDefinition> available = List.of(
                definition("a", "Int"),
                definition("b", "String"));

        List<VariableDefinition> result = GraphQLVariables.reconcile(List.of(), Set.of("b"), available);

        assertEquals(1, result.size());
        assertEquals("b", result.get(0).name());
        assertEquals("String", TypeNode.baseName(result.get(0).type()));
    }

    @Test
    void reconcileKeepsDeclarationsTheQueryAlreadyCarries() {
        List<VariableDefinition> result = GraphQLVariables.reconcile(
                List.of(definition("a", "Int")), Set.of("a"), List.of(definition("a", "String")));

        assertEquals(1, result.size());
        assertEquals("Int", TypeNode.baseName(result.get(0).type()));
    }

    @Test
    void reconcilePreservesOrderAndSkipsUnknownVariables() {
        List<VariableDefinition> result = GraphQLVariables.reconcile(
                List.of(), Set.of("a", "missing"), List.of(definition("a", "Int"), definition("b", "Int")));

        assertEquals(1, result.size());
        assertEquals("a", result.get(0).name());
    }

    @Test
    void reconcileToleratesMissingInputs() {
        assertTrue(GraphQLVariables.reconcile(null, null, null).isEmpty());
        assertEquals(1, GraphQLVariables.reconcile(List.of(definition("a", "Int")), null, null).size());
    }

    private static VariableDefinition definition(String name, String type) {
        return new VariableDefinition(name, new TypeNode.Named(type), null, List.of());
    }
}
