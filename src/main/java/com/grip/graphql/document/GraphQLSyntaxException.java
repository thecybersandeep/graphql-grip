package com.grip.graphql.document;

public class GraphQLSyntaxException extends RuntimeException {

    private final int line;
    private final int column;

    public GraphQLSyntaxException(String message, int line, int column) {
        super(message + " (line " + line + ", column " + column + ")");
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
