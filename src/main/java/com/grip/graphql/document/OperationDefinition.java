package com.grip.graphql.document;

import java.util.List;

public record OperationDefinition(OperationType operationType, String name,
                                  List<VariableDefinition> variableDefinitions, List<Directive> directives,
                                  SelectionSet selectionSet, boolean shorthand) implements Definition {

    public OperationDefinition(OperationType operationType, String name,
                               List<VariableDefinition> variableDefinitions, List<Directive> directives,
                               SelectionSet selectionSet, boolean shorthand) {
        this.operationType = operationType;
        this.name = name;
        this.variableDefinitions = variableDefinitions == null ? List.of() : List.copyOf(variableDefinitions);
        this.directives = directives == null ? List.of() : List.copyOf(directives);
        this.selectionSet = selectionSet;
        this.shorthand = shorthand;
    }

    public List<Field> rootFields() {
        return selectionSet.fields();
    }

    public Field firstRootField() {
        return selectionSet.firstField();
    }

    public boolean usesVariables() {
        return !variableDefinitions.isEmpty();
    }

    public VariableDefinition variableDefinition(String variableName) {
        for (VariableDefinition definition : variableDefinitions) {
            if (definition.name().equals(variableName)) {
                return definition;
            }
        }
        return null;
    }

    public OperationDefinition withName(String newName) {
        return new OperationDefinition(operationType, newName, variableDefinitions, directives,
                selectionSet, false);
    }

    public OperationDefinition withSelectionSet(SelectionSet newSelectionSet) {
        return new OperationDefinition(operationType, name, variableDefinitions, directives,
                newSelectionSet, shorthand);
    }

    public OperationDefinition withVariableDefinitions(List<VariableDefinition> newVariableDefinitions) {
        boolean stillShorthand = shorthand
                && (newVariableDefinitions == null || newVariableDefinitions.isEmpty());
        return new OperationDefinition(operationType, name, newVariableDefinitions, directives,
                selectionSet, stillShorthand);
    }
}
