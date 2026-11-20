package com.grip.graphql.document;

import java.util.List;

public record FragmentSpread(String name, List<Directive> directives) implements Selection {

    public FragmentSpread(String name, List<Directive> directives) {
        this.name = name;
        this.directives = directives == null ? List.of() : List.copyOf(directives);
    }
}
