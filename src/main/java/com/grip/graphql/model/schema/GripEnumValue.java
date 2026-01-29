package com.grip.graphql.model.schema;

public class GripEnumValue {

    private final String name;
    private String description;
    private boolean isDeprecated;
    private String deprecationReason;

    public GripEnumValue(String name) {
        this.name = name;
    }

    public GripEnumValue(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GripEnumValue that = (GripEnumValue) obj;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
