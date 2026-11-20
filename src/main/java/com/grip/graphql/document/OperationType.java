package com.grip.graphql.document;

import java.util.Locale;

public enum OperationType {

    QUERY,
    MUTATION,
    SUBSCRIPTION;

    public String keyword() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static OperationType fromKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        return switch (keyword) {
            case "query" -> QUERY;
            case "mutation" -> MUTATION;
            case "subscription" -> SUBSCRIPTION;
            default -> null;
        };
    }
}
