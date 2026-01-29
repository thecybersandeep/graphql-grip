package com.grip.graphql.model.schema;

import java.util.Set;

public class GripTypeRef {

    private static final Set<String> BUILT_IN_SCALARS = Set.of(
            "String", "Int", "Float", "Boolean", "ID"
    );

    private static final Set<String> COMMON_CUSTOM_SCALARS = Set.of(
            "ID", "URL", "URI", "UUID", "JSON", "HTML", "XML", "JWT", "AWS", "ISO"
    );

    private final String name;
    private final GripTypeKind kind;
    private final boolean isList;
    private final boolean isNonNull;
    private final boolean isNonNullItem;

    public GripTypeRef(String name, GripTypeKind kind, boolean isList,
                       boolean isNonNull, boolean isNonNullItem) {
        this.name = name;
        this.kind = kind;
        this.isList = isList;
        this.isNonNull = isNonNull;
        this.isNonNullItem = isNonNullItem;
    }

    public static GripTypeRef simple(String name) {
        return new GripTypeRef(name, determineKind(name), false, false, false);
    }

    public static GripTypeRef nonNull(String name) {
        return new GripTypeRef(name, determineKind(name), false, true, false);
    }

    public static GripTypeRef list(String name, boolean itemNonNull, boolean listNonNull) {
        return new GripTypeRef(name, determineKind(name), true, listNonNull, itemNonNull);
    }

    public static GripTypeRef fromString(String typeString) {
        if (typeString == null || typeString.isEmpty()) {
            return null;
        }

        String s = typeString.trim();
        boolean isNonNull = s.endsWith("!");
        if (isNonNull) {
            s = s.substring(0, s.length() - 1);
        }

        boolean isList = s.startsWith("[") && s.endsWith("]");
        boolean isNonNullItem = false;

        if (isList) {
            s = s.substring(1, s.length() - 1);
            if (s.endsWith("!")) {
                isNonNullItem = true;
                s = s.substring(0, s.length() - 1);
            }
        }

        String baseName = s.trim();
        GripTypeKind kind = determineKind(baseName);

        return new GripTypeRef(baseName, kind, isList, isNonNull, isNonNullItem);
    }

    private static GripTypeKind determineKind(String name) {
        if (name == null) return GripTypeKind.OBJECT;

        if (BUILT_IN_SCALARS.contains(name)) {
            return GripTypeKind.SCALAR;
        }

        if (COMMON_CUSTOM_SCALARS.contains(name)) {
            return GripTypeKind.SCALAR;
        }
        if (name.endsWith("Input")) {
            return GripTypeKind.INPUT_OBJECT;
        }

        if (name.endsWith("Enum") || (name.equals(name.toUpperCase()) && name.length() > 4)) {
            return GripTypeKind.ENUM;
        }
        return GripTypeKind.OBJECT;
    }

    public String getName() {
        return name;
    }

    public GripTypeKind getKind() {
        return kind;
    }

    public boolean isList() {
        return isList;
    }

    public boolean isNonNull() {
        return isNonNull;
    }

    public boolean isNonNullItem() {
        return isNonNullItem;
    }

    public boolean isBuiltInScalar() {
        return BUILT_IN_SCALARS.contains(name);
    }

    public boolean isScalar() {
        return kind == GripTypeKind.SCALAR;
    }

    public String toGraphQLString() {
        StringBuilder sb = new StringBuilder();
        if (isList) sb.append("[");
        sb.append(name);
        if (isNonNullItem) sb.append("!");
        if (isList) sb.append("]");
        if (isNonNull) sb.append("!");
        return sb.toString();
    }

    @Override
    public String toString() {
        return toGraphQLString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GripTypeRef that = (GripTypeRef) obj;
        return isList == that.isList &&
               isNonNull == that.isNonNull &&
               isNonNullItem == that.isNonNullItem &&
               name.equals(that.name) &&
               kind == that.kind;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + kind.hashCode();
        result = 31 * result + (isList ? 1 : 0);
        result = 31 * result + (isNonNull ? 1 : 0);
        result = 31 * result + (isNonNullItem ? 1 : 0);
        return result;
    }
}
