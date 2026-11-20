package com.grip.graphql.document;

import java.util.List;

public record Directive(String name, List<Argument> arguments) {

    public Directive(String name, List<Argument> arguments) {
        this.name = name;
        this.arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
}
