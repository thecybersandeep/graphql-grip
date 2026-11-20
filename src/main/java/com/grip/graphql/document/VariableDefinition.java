package com.grip.graphql.document;

import java.util.List;

public record VariableDefinition(String name, TypeNode type, Value defaultValue, List<Directive> directives) {

    public VariableDefinition(String name, TypeNode type, Value defaultValue, List<Directive> directives) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.directives = directives == null ? List.of() : List.copyOf(directives);
    }

    public boolean isRequired() {
        return type instanceof TypeNode.NonNull && defaultValue == null;
    }
}
