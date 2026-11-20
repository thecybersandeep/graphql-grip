package com.grip.graphql.document;

public sealed interface TypeNode {

    record Named(String name) implements TypeNode {
    }

    record ListOf(TypeNode element) implements TypeNode {
    }

    record NonNull(TypeNode element) implements TypeNode {
    }

    static String baseName(TypeNode type) {
        if (type instanceof Named named) {
            return named.name();
        }
        if (type instanceof ListOf list) {
            return baseName(list.element());
        }
        if (type instanceof NonNull nonNull) {
            return baseName(nonNull.element());
        }
        return null;
    }
}
