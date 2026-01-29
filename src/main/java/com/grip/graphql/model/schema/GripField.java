package com.grip.graphql.model.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GripField {

    private final String name;
    private GripTypeRef type;
    private String description;
    private final List<GripArgument> arguments;
    private boolean isDeprecated;
    private String deprecationReason;

    public GripField(String name, GripTypeRef type) {
        this.name = name;
        this.type = type;
        this.arguments = new ArrayList<>();
    }

    public GripField(String name, GripTypeRef type, String description) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.arguments = new ArrayList<>();
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

    public List<GripArgument> getArguments() {
        return Collections.unmodifiableList(arguments);
    }

    public void addArgument(GripArgument argument) {
        arguments.add(argument);
    }

    public boolean hasArguments() {
        return !arguments.isEmpty();
    }

    public GripArgument getArgument(String name) {
        for (GripArgument arg : arguments) {
            if (arg.getName().equals(name)) {
                return arg;
            }
        }
        return null;
    }

    public List<GripArgument> getRequiredArguments() {
        List<GripArgument> required = new ArrayList<>();
        for (GripArgument arg : arguments) {
            if (arg.isRequired()) {
                required.add(arg);
            }
        }
        return required;
    }

    public boolean isDeprecated() {
        return isDeprecated;
    }

    public void setDeprecated(boolean deprecated) {
        isDeprecated = deprecated;
    }

    public String getDeprecationReason() {
        return deprecationReason;
    }

    public void setDeprecationReason(String reason) {
        this.deprecationReason = reason;
        if (reason != null && !reason.isEmpty()) {
            this.isDeprecated = true;
        }
    }

    public boolean isLeafType() {
        return type != null && type.getKind().isLeaf();
    }

    public String toGraphQLString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);

        if (!arguments.isEmpty()) {
            sb.append("(");
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(arguments.get(i).toGraphQLString());
            }
            sb.append(")");
        }

        sb.append(": ");
        if (type != null) {
            sb.append(type.toGraphQLString());
        } else {
            sb.append("Unknown");
        }

        return sb.toString();
    }

    public String generateQuerySelection() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);

        List<GripArgument> required = getRequiredArguments();
        if (!required.isEmpty()) {
            sb.append("(");
            for (int i = 0; i < required.size(); i++) {
                if (i > 0) sb.append(", ");
                GripArgument arg = required.get(i);
                sb.append(arg.getName()).append(": ").append(arg.generateSampleValue());
            }
            sb.append(")");
        }

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
        GripField that = (GripField) obj;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
