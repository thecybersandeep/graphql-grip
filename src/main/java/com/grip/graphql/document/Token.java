package com.grip.graphql.document;

record Token(TokenKind kind, String value, boolean block, int line, int column) {

    String describe() {
        if (kind == TokenKind.NAME) {
            return "'" + value + "'";
        }
        return kind.description();
    }
}
