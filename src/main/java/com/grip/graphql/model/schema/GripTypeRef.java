package com.grip.graphql.model.schema;

import java.util.Set;

public class GripTypeRef {

    private static final Set<String> BUILT_IN_SCALARS = Set.of(
            "String", "Int", "Float", "Boolean", "ID"
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

    private static GripTypeKind determineKind(String name) {
        return BUILT_IN_SCALARS.contains(name) ? GripTypeKind.SCALAR : GripTypeKind.OBJECT;
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
