package com.grip.graphql.model.schema;

public enum GripTypeKind {

    SCALAR,

    OBJECT,

    INTERFACE,

    UNION,

    ENUM,

    INPUT_OBJECT,

    LIST,

    NON_NULL;

    public boolean isLeaf() {
        return this == SCALAR || this == ENUM;
    }

    public boolean isComposite() {
        return this == OBJECT || this == INTERFACE || this == UNION;
    }

    public boolean isInput() {
        return this == SCALAR || this == ENUM || this == INPUT_OBJECT;
    }

    public boolean isWrapper() {
        return this == LIST || this == NON_NULL;
    }

    public static GripTypeKind fromIntrospection(String value) {
        if (value == null) return OBJECT;
        return switch (value.toUpperCase()) {
            case "SCALAR" -> SCALAR;
            case "OBJECT" -> OBJECT;
            case "INTERFACE" -> INTERFACE;
            case "UNION" -> UNION;
            case "ENUM" -> ENUM;
            case "INPUT_OBJECT" -> INPUT_OBJECT;
            case "LIST" -> LIST;
            case "NON_NULL" -> NON_NULL;
            default -> OBJECT;
        };
    }
}
