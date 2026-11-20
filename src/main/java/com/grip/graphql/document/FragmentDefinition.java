package com.grip.graphql.document;

import java.util.List;

public record FragmentDefinition(String name, String typeCondition, List<Directive> directives,
                                 SelectionSet selectionSet) implements Definition {

    public FragmentDefinition(String name, String typeCondition, List<Directive> directives,
                              SelectionSet selectionSet) {
        this.name = name;
        this.typeCondition = typeCondition;
        this.directives = directives == null ? List.of() : List.copyOf(directives);
        this.selectionSet = selectionSet;
    }

    public FragmentDefinition withSelectionSet(SelectionSet newSelectionSet) {
        return new FragmentDefinition(name, typeCondition, directives, newSelectionSet);
    }
}
