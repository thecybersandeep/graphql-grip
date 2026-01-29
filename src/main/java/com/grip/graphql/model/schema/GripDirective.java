package com.grip.graphql.model.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class GripDirective {

    public enum Location {

        QUERY, MUTATION, SUBSCRIPTION, FIELD, FRAGMENT_DEFINITION,
        FRAGMENT_SPREAD, INLINE_FRAGMENT, VARIABLE_DEFINITION,

        SCHEMA, SCALAR, OBJECT, FIELD_DEFINITION, ARGUMENT_DEFINITION,
        INTERFACE, UNION, ENUM, ENUM_VALUE, INPUT_OBJECT, INPUT_FIELD_DEFINITION
    }

    private final String name;
    private String description;
    private final List<GripArgument> arguments;
    private final List<Location> locations;
    private boolean isRepeatable;

    public GripDirective(String name) {
        this.name = name;
        this.arguments = new ArrayList<>();
        this.locations = new ArrayList<>();
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

    public List<GripArgument> getArguments() {
        return Collections.unmodifiableList(arguments);
    }

    public void addArgument(GripArgument argument) {
        arguments.add(argument);
    }

    public boolean hasArguments() {
        return !arguments.isEmpty();
    }

    public List<Location> getLocations() {
        return Collections.unmodifiableList(locations);
    }

    public void addLocation(Location location) {
        if (!locations.contains(location)) {
            locations.add(location);
        }
    }

    public boolean isValidAt(Location location) {
        return locations.contains(location);
    }

    public boolean isRepeatable() {
        return isRepeatable;
    }

    public void setRepeatable(boolean repeatable) {
        isRepeatable = repeatable;
    }

    public boolean isBuiltIn() {
        return Set.of("skip", "include", "deprecated", "specifiedBy").contains(name);
    }

    public String toGraphQLString() {
        StringBuilder sb = new StringBuilder();
        sb.append("directive @").append(name);

        if (!arguments.isEmpty()) {
            sb.append("(");
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(arguments.get(i).toGraphQLString());
            }
            sb.append(")");
        }

        if (isRepeatable) {
            sb.append(" repeatable");
        }

        if (!locations.isEmpty()) {
            sb.append(" on ");
            for (int i = 0; i < locations.size(); i++) {
                if (i > 0) sb.append(" | ");
                sb.append(locations.get(i).name());
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "@" + name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GripDirective that = (GripDirective) obj;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
