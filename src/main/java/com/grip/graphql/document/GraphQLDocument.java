package com.grip.graphql.document;

import java.util.ArrayList;
import java.util.List;

public record GraphQLDocument(List<Definition> definitions) {

    public GraphQLDocument(List<Definition> definitions) {
        this.definitions = definitions == null ? List.of() : List.copyOf(definitions);
    }

    public List<OperationDefinition> operations() {
        List<OperationDefinition> operations = new ArrayList<>();
        for (Definition definition : definitions) {
            if (definition instanceof OperationDefinition operation) {
                operations.add(operation);
            }
        }
        return operations;
    }

    public List<FragmentDefinition> fragments() {
        List<FragmentDefinition> fragments = new ArrayList<>();
        for (Definition definition : definitions) {
            if (definition instanceof FragmentDefinition fragment) {
                fragments.add(fragment);
            }
        }
        return fragments;
    }

    public OperationDefinition firstOperation() {
        for (Definition definition : definitions) {
            if (definition instanceof OperationDefinition operation) {
                return operation;
            }
        }
        return null;
    }

    public OperationDefinition operation(String name) {
        if (name == null || name.isBlank()) {
            return firstOperation();
        }
        for (Definition definition : definitions) {
            if (definition instanceof OperationDefinition operation && name.equals(operation.name())) {
                return operation;
            }
        }
        return null;
    }

    public GraphQLDocument withFirstOperation(OperationDefinition replacement) {
        List<Definition> updated = new ArrayList<>(definitions);
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i) instanceof OperationDefinition) {
                updated.set(i, replacement);
                return new GraphQLDocument(updated);
            }
        }
        updated.add(replacement);
        return new GraphQLDocument(updated);
    }

    public FragmentDefinition fragment(String name) {
        if (name == null) {
            return null;
        }
        for (Definition definition : definitions) {
            if (definition instanceof FragmentDefinition fragment && name.equals(fragment.name())) {
                return fragment;
            }
        }
        return null;
    }
}
