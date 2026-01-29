package com.grip.graphql.model.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GripType {

    private final String name;
    private final GripTypeKind kind;
    private String description;

    private final List<GripField> fields;
    private final List<GripType> interfaces;

    private final List<GripType> possibleTypes;

    private final List<GripEnumValue> enumValues;

    private final List<GripField> inputFields;

    public GripType(String name, GripTypeKind kind) {
        this.name = name;
        this.kind = kind;
        this.fields = new ArrayList<>();
        this.interfaces = new ArrayList<>();
        this.possibleTypes = new ArrayList<>();
        this.enumValues = new ArrayList<>();
        this.inputFields = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public GripTypeKind getKind() {
        return kind;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<GripField> getFields() {
        return Collections.unmodifiableList(fields);
    }

    public void addField(GripField field) {
        fields.add(field);
    }

    public GripField getField(String name) {
        for (GripField field : fields) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        return null;
    }

    public boolean hasFields() {
        return !fields.isEmpty();
    }

    public List<GripType> getInterfaces() {
        return Collections.unmodifiableList(interfaces);
    }

    public void addInterface(GripType iface) {
        interfaces.add(iface);
    }

    public List<GripType> getPossibleTypes() {
        return Collections.unmodifiableList(possibleTypes);
    }

    public void addPossibleType(GripType type) {
        possibleTypes.add(type);
    }

    public List<GripEnumValue> getEnumValues() {
        return Collections.unmodifiableList(enumValues);
    }

    public void addEnumValue(GripEnumValue value) {
        enumValues.add(value);
    }

    public void addEnumValue(String name) {
        enumValues.add(new GripEnumValue(name));
    }

    public List<GripField> getInputFields() {
        return Collections.unmodifiableList(inputFields);
    }

    public void addInputField(GripField field) {
        inputFields.add(field);
    }

    public boolean isBuiltInScalar() {
        return kind == GripTypeKind.SCALAR &&
               (name.equals("String") || name.equals("Int") ||
                name.equals("Float") || name.equals("Boolean") || name.equals("ID"));
    }

    public boolean isLeaf() {
        return kind.isLeaf();
    }

    public boolean isComposite() {
        return kind.isComposite();
    }

    public boolean isIntrospectionType() {
        return name.startsWith("__");
    }

    public String toSDL() {
        StringBuilder sb = new StringBuilder();

        if (description != null && !description.isEmpty()) {
            sb.append("\"\"\"").append(description).append("\"\"\"\n");
        }

        switch (kind) {
            case SCALAR -> sb.append("scalar ").append(name);

            case OBJECT -> {
                sb.append("type ").append(name);
                if (!interfaces.isEmpty()) {
                    sb.append(" implements ");
                    for (int i = 0; i < interfaces.size(); i++) {
                        if (i > 0) sb.append(" & ");
                        sb.append(interfaces.get(i).getName());
                    }
                }
                sb.append(" {\n");
                for (GripField field : fields) {
                    sb.append("  ").append(field.toGraphQLString()).append("\n");
                }
                sb.append("}");
            }

            case INTERFACE -> {
                sb.append("interface ").append(name).append(" {\n");
                for (GripField field : fields) {
                    sb.append("  ").append(field.toGraphQLString()).append("\n");
                }
                sb.append("}");
            }

            case UNION -> {
                sb.append("union ").append(name).append(" = ");
                for (int i = 0; i < possibleTypes.size(); i++) {
                    if (i > 0) sb.append(" | ");
                    sb.append(possibleTypes.get(i).getName());
                }
            }

            case ENUM -> {
                sb.append("enum ").append(name).append(" {\n");
                for (GripEnumValue value : enumValues) {
                    sb.append("  ").append(value.getName()).append("\n");
                }
                sb.append("}");
            }

            case INPUT_OBJECT -> {
                sb.append("input ").append(name).append(" {\n");
                for (GripField field : inputFields) {
                    sb.append("  ").append(field.toGraphQLString()).append("\n");
                }
                sb.append("}");
            }

            default -> sb.append("# Unknown type kind: ").append(kind);
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return name + " (" + kind + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GripType that = (GripType) obj;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
