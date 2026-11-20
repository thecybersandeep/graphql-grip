package com.grip.graphql.document;

import java.util.ArrayList;
import java.util.List;

public record SelectionSet(List<Selection> selections) {

    public SelectionSet(List<Selection> selections) {
        this.selections = selections == null ? List.of() : List.copyOf(selections);
    }

    public static SelectionSet of(Selection... selections) {
        return new SelectionSet(List.of(selections));
    }

    public boolean isEmpty() {
        return selections.isEmpty();
    }

    public List<Field> fields() {
        List<Field> fields = new ArrayList<>();
        for (Selection selection : selections) {
            if (selection instanceof Field field) {
                fields.add(field);
            }
        }
        return fields;
    }

    public Field firstField() {
        for (Selection selection : selections) {
            if (selection instanceof Field field) {
                return field;
            }
        }
        return null;
    }
}
