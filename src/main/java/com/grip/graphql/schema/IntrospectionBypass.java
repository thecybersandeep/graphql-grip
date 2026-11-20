package com.grip.graphql.schema;

public enum IntrospectionBypass {

    NONE("no bypass"),
    NEWLINE("newline after __schema"),
    TAB("tab after __schema"),
    CARRIAGE_RETURN("carriage return after __schema"),
    INCLUDE_DIRECTIVE("@include(if: true) on __schema"),
    SKIP_DIRECTIVE("@skip(if: false) on __schema"),
    LONG_OPERATION_NAME("long operation name");

    private static final String ANCHOR = "__schema {";
    private static final int OPERATION_NAME_PADDING = 500;

    private final String description;

    IntrospectionBypass(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public String apply(String query) {
        if (query == null) {
            return null;
        }
        return switch (this) {
            case NONE -> query;
            case NEWLINE -> replaceAnchor(query, "__schema\n{");
            case TAB -> replaceAnchor(query, "__schema\t{");
            case CARRIAGE_RETURN -> replaceAnchor(query, "__schema\r\n{");
            case INCLUDE_DIRECTIVE -> replaceAnchor(query, "__schema @include(if: true) {");
            case SKIP_DIRECTIVE -> replaceAnchor(query, "__schema @skip(if: false) {");
            case LONG_OPERATION_NAME -> padOperationName(query);
        };
    }

    private static String replaceAnchor(String query, String replacement) {
        int index = query.indexOf(ANCHOR);
        if (index < 0) {
            return query;
        }
        return query.substring(0, index) + replacement + query.substring(index + ANCHOR.length());
    }

    private static String padOperationName(String query) {
        int brace = query.indexOf('{');
        if (brace < 0) {
            return query;
        }

        int insert = brace;
        while (insert > 0 && Character.isWhitespace(query.charAt(insert - 1))) {
            insert--;
        }

        String head = query.substring(0, insert);
        String tail = query.substring(insert);
        String padding = "A".repeat(OPERATION_NAME_PADDING);

        if (head.isBlank()) {
            return "query " + padding + tail;
        }
        if (endsWithOperationKeyword(head)) {
            return head + " " + padding + tail;
        }
        return head + padding + tail;
    }

    private static boolean endsWithOperationKeyword(String head) {
        int start = head.length();
        while (start > 0 && isNameCharacter(head.charAt(start - 1))) {
            start--;
        }

        String word = head.substring(start);
        if (!word.equals("query") && !word.equals("mutation") && !word.equals("subscription")) {
            return false;
        }
        return start == 0 || !isNameCharacter(head.charAt(start - 1));
    }

    private static boolean isNameCharacter(char c) {
        return c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }
}
