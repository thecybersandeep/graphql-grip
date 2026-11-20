package com.grip.graphql.document;

import java.util.List;

public record Field(String alias, String name, List<Argument> arguments,
                    List<Directive> directives, SelectionSet selectionSet) implements Selection {

    public Field(String alias, String name, List<Argument> arguments,
                 List<Directive> directives, SelectionSet selectionSet) {
        this.alias = alias;
        this.name = name;
        this.arguments = arguments == null ? List.of() : List.copyOf(arguments);
        this.directives = directives == null ? List.of() : List.copyOf(directives);
        this.selectionSet = selectionSet;
    }

    public static Field of(String name) {
        return new Field(null, name, List.of(), List.of(), null);
    }

    public String responseKey() {
        return alias != null ? alias : name;
    }

    public boolean hasSelectionSet() {
        return selectionSet != null && !selectionSet.isEmpty();
    }

    public Argument argument(String argumentName) {
        for (Argument argument : arguments) {
            if (argument.name().equals(argumentName)) {
                return argument;
            }
        }
        return null;
    }

    public Field withAlias(String newAlias) {
        return new Field(newAlias, name, arguments, directives, selectionSet);
    }

    public Field withDirectives(List<Directive> newDirectives) {
        return new Field(alias, name, arguments, newDirectives, selectionSet);
    }

    public Field withSelectionSet(SelectionSet newSelectionSet) {
        return new Field(alias, name, arguments, directives, newSelectionSet);
    }
}
