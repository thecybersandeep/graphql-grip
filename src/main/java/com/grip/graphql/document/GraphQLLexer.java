package com.grip.graphql.document;

final class GraphQLLexer {

    private final String source;
    private int pos;
    private int line;
    private int lineStart;

    GraphQLLexer(String source) {
        this.source = source == null ? "" : source;
        this.pos = 0;
        this.line = 1;
        this.lineStart = 0;
    }

    Token next() {
        skipIgnored();

        int tokenLine = line;
        int tokenColumn = column();

        if (pos >= source.length()) {
            return new Token(TokenKind.EOF, "", false, tokenLine, tokenColumn);
        }

        char c = source.charAt(pos);
        switch (c) {
            case '!':
                pos++;
                return new Token(TokenKind.BANG, "!", false, tokenLine, tokenColumn);
            case '$':
                pos++;
                return new Token(TokenKind.DOLLAR, "$", false, tokenLine, tokenColumn);
            case '&':
                pos++;
                return new Token(TokenKind.AMP, "&", false, tokenLine, tokenColumn);
            case '(':
                pos++;
                return new Token(TokenKind.PAREN_L, "(", false, tokenLine, tokenColumn);
            case ')':
                pos++;
                return new Token(TokenKind.PAREN_R, ")", false, tokenLine, tokenColumn);
            case ':':
                pos++;
                return new Token(TokenKind.COLON, ":", false, tokenLine, tokenColumn);
            case '=':
                pos++;
                return new Token(TokenKind.EQUALS, "=", false, tokenLine, tokenColumn);
            case '@':
                pos++;
                return new Token(TokenKind.AT, "@", false, tokenLine, tokenColumn);
            case '[':
                pos++;
                return new Token(TokenKind.BRACKET_L, "[", false, tokenLine, tokenColumn);
            case ']':
                pos++;
                return new Token(TokenKind.BRACKET_R, "]", false, tokenLine, tokenColumn);
            case '{':
                pos++;
                return new Token(TokenKind.BRACE_L, "{", false, tokenLine, tokenColumn);
            case '}':
                pos++;
                return new Token(TokenKind.BRACE_R, "}", false, tokenLine, tokenColumn);
            case '|':
                pos++;
                return new Token(TokenKind.PIPE, "|", false, tokenLine, tokenColumn);
            case '.':
                return readSpread(tokenLine, tokenColumn);
            case '"':
                return readString(tokenLine, tokenColumn);
            default:
                if (isNameStart(c)) {
                    return readName(tokenLine, tokenColumn);
                }
                if (c == '-' || isDigit(c)) {
                    return readNumber(tokenLine, tokenColumn);
                }
                throw new GraphQLSyntaxException("Unexpected character '" + c + "'", tokenLine, tokenColumn);
        }
    }

    private int column() {
        return pos - lineStart + 1;
    }

    private void skipIgnored() {
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '\n') {
                pos++;
                line++;
                lineStart = pos;
            } else if (c == '\r') {
                pos++;
                if (pos < source.length() && source.charAt(pos) == '\n') {
                    pos++;
                }
                line++;
                lineStart = pos;
            } else if (c == ' ' || c == '\t' || c == ',' || c == '﻿') {
                pos++;
            } else if (c == '#') {
                pos++;
                while (pos < source.length()) {
                    char inComment = source.charAt(pos);
                    if (inComment == '\n' || inComment == '\r') {
                        break;
                    }
                    pos++;
                }
            } else {
                return;
            }
        }
    }

    private Token readSpread(int tokenLine, int tokenColumn) {
        if (pos + 2 < source.length() && source.charAt(pos + 1) == '.' && source.charAt(pos + 2) == '.') {
            pos += 3;
            return new Token(TokenKind.SPREAD, "...", false, tokenLine, tokenColumn);
        }
        throw new GraphQLSyntaxException("Expected '...'", tokenLine, tokenColumn);
    }

    private Token readName(int tokenLine, int tokenColumn) {
        int start = pos;
        while (pos < source.length() && isNameContinue(source.charAt(pos))) {
            pos++;
        }
        return new Token(TokenKind.NAME, source.substring(start, pos), false, tokenLine, tokenColumn);
    }

    private Token readNumber(int tokenLine, int tokenColumn) {
        int start = pos;
        boolean isFloat = false;

        if (source.charAt(pos) == '-') {
            pos++;
        }

        if (pos < source.length() && source.charAt(pos) == '0') {
            pos++;
            if (pos < source.length() && isDigit(source.charAt(pos))) {
                throw new GraphQLSyntaxException("Invalid number, unexpected digit after leading zero",
                        tokenLine, tokenColumn);
            }
        } else {
            readDigits(tokenLine, tokenColumn);
        }

        if (pos < source.length() && source.charAt(pos) == '.') {
            isFloat = true;
            pos++;
            readDigits(tokenLine, tokenColumn);
        }

        if (pos < source.length() && (source.charAt(pos) == 'e' || source.charAt(pos) == 'E')) {
            isFloat = true;
            pos++;
            if (pos < source.length() && (source.charAt(pos) == '+' || source.charAt(pos) == '-')) {
                pos++;
            }
            readDigits(tokenLine, tokenColumn);
        }

        String raw = source.substring(start, pos);
        return new Token(isFloat ? TokenKind.FLOAT : TokenKind.INT, raw, false, tokenLine, tokenColumn);
    }

    private void readDigits(int tokenLine, int tokenColumn) {
        if (pos >= source.length() || !isDigit(source.charAt(pos))) {
            throw new GraphQLSyntaxException("Invalid number, expected a digit", tokenLine, tokenColumn);
        }
        while (pos < source.length() && isDigit(source.charAt(pos))) {
            pos++;
        }
    }

    private Token readString(int tokenLine, int tokenColumn) {
        if (pos + 2 < source.length() && source.charAt(pos + 1) == '"' && source.charAt(pos + 2) == '"') {
            return readBlockString(tokenLine, tokenColumn);
        }

        pos++;
        StringBuilder value = new StringBuilder();

        while (pos < source.length()) {
            char c = source.charAt(pos);

            if (c == '"') {
                pos++;
                return new Token(TokenKind.STRING, value.toString(), false, tokenLine, tokenColumn);
            }
            if (c == '\n' || c == '\r') {
                throw new GraphQLSyntaxException("Unterminated string", tokenLine, tokenColumn);
            }
            if (c != '\\') {
                value.append(c);
                pos++;
                continue;
            }

            pos++;
            if (pos >= source.length()) {
                throw new GraphQLSyntaxException("Unterminated escape sequence", tokenLine, tokenColumn);
            }

            char escape = source.charAt(pos);
            if (escape == '"') {
                value.append('"');
            } else if (escape == '\\') {
                value.append('\\');
            } else if (escape == '/') {
                value.append('/');
            } else if (escape == 'b') {
                value.append('\b');
            } else if (escape == 'f') {
                value.append('\f');
            } else if (escape == 'n') {
                value.append('\n');
            } else if (escape == 'r') {
                value.append('\r');
            } else if (escape == 't') {
                value.append('\t');
            } else if (escape == 'u') {
                if (pos + 4 >= source.length()) {
                    throw new GraphQLSyntaxException("Invalid unicode escape sequence", tokenLine, tokenColumn);
                }
                String hex = source.substring(pos + 1, pos + 5);
                try {
                    value.append((char) Integer.parseInt(hex, 16));
                } catch (NumberFormatException e) {
                    throw new GraphQLSyntaxException("Invalid unicode escape sequence", tokenLine, tokenColumn);
                }
                pos += 4;
            } else {
                throw new GraphQLSyntaxException("Invalid escape sequence '\\" + escape + "'",
                        tokenLine, tokenColumn);
            }
            pos++;
        }

        throw new GraphQLSyntaxException("Unterminated string", tokenLine, tokenColumn);
    }

    private Token readBlockString(int tokenLine, int tokenColumn) {
        pos += 3;
        StringBuilder raw = new StringBuilder();

        while (pos < source.length()) {
            char c = source.charAt(pos);

            if (c == '"' && pos + 2 < source.length()
                    && source.charAt(pos + 1) == '"' && source.charAt(pos + 2) == '"') {
                pos += 3;
                return new Token(TokenKind.STRING, dedentBlockString(raw.toString()), true, tokenLine, tokenColumn);
            }

            if (c == '\\' && pos + 3 < source.length() && source.charAt(pos + 1) == '"'
                    && source.charAt(pos + 2) == '"' && source.charAt(pos + 3) == '"') {
                raw.append("\"\"\"");
                pos += 4;
                continue;
            }

            if (c == '\n' || c == '\r') {
                line++;
                lineStart = pos + 1;
            }

            raw.append(c);
            pos++;
        }

        throw new GraphQLSyntaxException("Unterminated block string", tokenLine, tokenColumn);
    }

    private static String dedentBlockString(String raw) {
        String[] lines = raw.split("\r\n|\r|\n", -1);

        int commonIndent = Integer.MAX_VALUE;
        for (int i = 1; i < lines.length; i++) {
            String text = lines[i];
            int indent = leadingWhitespace(text);
            if (indent < text.length() && indent < commonIndent) {
                commonIndent = indent;
            }
        }

        if (commonIndent != Integer.MAX_VALUE) {
            for (int i = 1; i < lines.length; i++) {
                lines[i] = lines[i].length() >= commonIndent ? lines[i].substring(commonIndent) : "";
            }
        }

        int start = 0;
        while (start < lines.length && lines[start].isBlank()) {
            start++;
        }

        int end = lines.length;
        while (end > start && lines[end - 1].isBlank()) {
            end--;
        }

        StringBuilder result = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) {
                result.append('\n');
            }
            result.append(lines[i]);
        }
        return result.toString();
    }

    private static int leadingWhitespace(String text) {
        int count = 0;
        while (count < text.length() && (text.charAt(count) == ' ' || text.charAt(count) == '\t')) {
            count++;
        }
        return count;
    }

    private static boolean isNameStart(char c) {
        return c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean isNameContinue(char c) {
        return isNameStart(c) || isDigit(c);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
