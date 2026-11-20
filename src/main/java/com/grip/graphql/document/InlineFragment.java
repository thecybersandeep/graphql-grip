package com.grip.graphql.document;

import java.util.List;

public record InlineFragment(String typeCondition, List<Directive> directives,
                             SelectionSet selectionSet) implements Selection {

    public InlineFragment(String typeCondition, List<Directive> directives, SelectionSet selectionSet) {
        this.typeCondition = typeCondition;
        this.directives = directives == null ? List.of() : List.copyOf(directives);
        this.selectionSet = selectionSet;
    }
}
