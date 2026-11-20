package com.grip.graphql.document;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GraphQLVariables {

    private GraphQLVariables() {
    }

    public static Set<String> referencedBy(GraphQLDocument document, OperationDefinition operation) {
        Set<String> referenced = new LinkedHashSet<>();
        if (operation == null) {
            return referenced;
        }
        collectDirectives(operation.directives(), referenced);
        collectSelectionSet(document, operation.selectionSet(), referenced, new HashSet<>());
        return referenced;
    }

    public static List<VariableDefinition> reconcile(List<VariableDefinition> declared,
                                                     Set<String> referenced,
                                                     List<VariableDefinition> available) {
        List<VariableDefinition> result = new ArrayList<>();
        Set<String> known = new HashSet<>();

        if (declared != null) {
            for (VariableDefinition definition : declared) {
                result.add(definition);
                known.add(definition.name());
            }
        }

        if (referenced == null || available == null) {
            return result;
        }

        for (VariableDefinition definition : available) {
            if (referenced.contains(definition.name()) && known.add(definition.name())) {
                result.add(definition);
            }
        }
        return result;
    }

    private static void collectSelectionSet(GraphQLDocument document, SelectionSet selectionSet,
                                            Set<String> referenced, Set<String> visitedFragments) {
        if (selectionSet == null) {
            return;
        }
        for (Selection selection : selectionSet.selections()) {
            collectSelection(document, selection, referenced, visitedFragments);
        }
    }

    private static void collectSelection(GraphQLDocument document, Selection selection,
                                         Set<String> referenced, Set<String> visitedFragments) {
        if (selection instanceof Field field) {
            collectArguments(field.arguments(), referenced);
            collectDirectives(field.directives(), referenced);
            collectSelectionSet(document, field.selectionSet(), referenced, visitedFragments);
        } else if (selection instanceof InlineFragment inline) {
            collectDirectives(inline.directives(), referenced);
            collectSelectionSet(document, inline.selectionSet(), referenced, visitedFragments);
        } else if (selection instanceof FragmentSpread spread) {
            collectDirectives(spread.directives(), referenced);
            if (document == null || !visitedFragments.add(spread.name())) {
                return;
            }
            FragmentDefinition fragment = document.fragment(spread.name());
            if (fragment != null) {
                collectDirectives(fragment.directives(), referenced);
                collectSelectionSet(document, fragment.selectionSet(), referenced, visitedFragments);
            }
        }
    }

    private static void collectArguments(List<Argument> arguments, Set<String> referenced) {
        for (Argument argument : arguments) {
            collectValue(argument.value(), referenced);
        }
    }

    private static void collectDirectives(List<Directive> directives, Set<String> referenced) {
        for (Directive directive : directives) {
            collectArguments(directive.arguments(), referenced);
        }
    }

    private static void collectValue(Value value, Set<String> referenced) {
        if (value instanceof Value.VariableValue variable) {
            referenced.add(variable.name());
        } else if (value instanceof Value.ListValue list) {
            for (Value item : list.values()) {
                collectValue(item, referenced);
            }
        } else if (value instanceof Value.ObjectValue object) {
            for (Value.ObjectField field : object.fields()) {
                collectValue(field.value(), referenced);
            }
        }
    }
}
