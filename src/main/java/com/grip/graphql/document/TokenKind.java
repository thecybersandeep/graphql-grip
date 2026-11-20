package com.grip.graphql.document;

enum TokenKind {

    NAME("a name"),
    INT("an integer value"),
    FLOAT("a float value"),
    STRING("a string value"),
    BANG("'!'"),
    DOLLAR("'$'"),
    AMP("'&'"),
    PAREN_L("'('"),
    PAREN_R("')'"),
    SPREAD("'...'"),
    COLON("':'"),
    EQUALS("'='"),
    AT("'@'"),
    BRACKET_L("'['"),
    BRACKET_R("']'"),
    BRACE_L("'{'"),
    BRACE_R("'}'"),
    PIPE("'|'"),
    EOF("end of input");

    private final String description;

    TokenKind(String description) {
        this.description = description;
    }

    String description() {
        return description;
    }
}
