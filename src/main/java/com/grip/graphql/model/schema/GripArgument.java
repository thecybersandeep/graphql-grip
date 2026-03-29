package com.grip.graphql.model.schema;

public class GripArgument {

    private final String name;
    private GripTypeRef type;
    private String description;
    private String defaultValue;

    public GripArgument(String name, GripTypeRef type) {
        this.name = name;
        this.type = type;
    }

    public GripArgument(String name, GripTypeRef type, String description) {
        this.name = name;
        this.type = type;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public GripTypeRef getType() {
        return type;
    }

    public void setType(GripTypeRef type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean hasDefaultValue() {
        return defaultValue != null;
    }

    public boolean isRequired() {
        return type != null && type.isNonNull() && !hasDefaultValue();
    }

    public String toGraphQLString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(": ");
        if (type != null) {
            sb.append(type.toGraphQLString());
        } else {
            sb.append("Unknown");
        }
        if (defaultValue != null) {
            sb.append(" = ").append(defaultValue);
        }
        return sb.toString();
    }

    public String generateSampleValue() {
        if (type == null) return "null";

        String typeName = type.getName();
        return switch (typeName) {
            case "String", "ID" -> "\"sample\"";
            case "Int" -> "1";
            case "Float" -> "1.0";
            case "Boolean" -> "true";
            default -> {
                if (type.getKind() == GripTypeKind.ENUM) {
                    yield "ENUM_VALUE";
                } else if (type.getKind() == GripTypeKind.INPUT_OBJECT) {
                    yield "{}";
                } else {
                    yield "null";
                }
            }
        };
    }

    @Override
    public String toString() {
        return toGraphQLString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GripArgument that = (GripArgument) obj;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
