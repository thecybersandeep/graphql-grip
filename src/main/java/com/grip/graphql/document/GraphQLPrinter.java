package com.grip.graphql.document;

import java.util.List;

public final class GraphQLPrinter {

    private static final String INDENT = "  ";

    private GraphQLPrinter() {
    }

    public static String print(GraphQLDocument document) {
        StringBuilder out = new StringBuilder();
        List<Definition> definitions = document.definitions();
        for (int i = 0; i < definitions.size(); i++) {
            if (i > 0) {
                out.append("\n\n");
            }
            appendDefinition(out, definitions.get(i));
        }
        return out.toString();
    }

    public static String print(Definition definition) {
        StringBuilder out = new StringBuilder();
        appendDefinition(out, definition);
        return out.toString();
    }

    public static String print(Selection selection) {
        StringBuilder out = new StringBuilder();
        appendSelection(out, selection, 0);
        return out.toString();
    }

    public static String print(SelectionSet selectionSet) {
        StringBuilder out = new StringBuilder();
        appendSelectionSet(out, selectionSet, 0);
        return out.toString();
    }

    public static String print(Value value) {
        StringBuilder out = new StringBuilder();
        appendValue(out, value);
        return out.toString();
    }

    public static String print(TypeNode type) {
        StringBuilder out = new StringBuilder();
        appendType(out, type);
        return out.toString();
    }

    public static String printVariableDefinitions(List<VariableDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder("(");
        for (int i = 0; i < definitions.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            VariableDefinition definition = definitions.get(i);
            out.append('$').append(definition.name()).append(": ");
            appendType(out, definition.type());
            if (definition.defaultValue() != null) {
                out.append(" = ");
                appendValue(out, definition.defaultValue());
            }
            appendDirectives(out, definition.directives());
        }
        out.append(')');
        return out.toString();
    }

    private static void appendDefinition(StringBuilder out, Definition definition) {
        if (definition instanceof OperationDefinition operation) {
            appendOperation(out, operation);
        } else if (definition instanceof FragmentDefinition fragment) {
            appendFragment(out, fragment);
        }
    }

    private static void appendOperation(StringBuilder out, OperationDefinition operation) {
        if (!operation.shorthand()) {
            out.append(operation.operationType().keyword());
            if (operation.name() != null) {
                out.append(' ').append(operation.name());
            }
            out.append(printVariableDefinitions(operation.variableDefinitions()));
            appendDirectives(out, operation.directives());
            out.append(' ');
        }
        appendSelectionSet(out, operation.selectionSet(), 0);
    }

    private static void appendFragment(StringBuilder out, FragmentDefinition fragment) {
        out.append("fragment ").append(fragment.name())
                .append(" on ").append(fragment.typeCondition());
        appendDirectives(out, fragment.directives());
        out.append(' ');
        appendSelectionSet(out, fragment.selectionSet(), 0);
    }

    private static void appendSelectionSet(StringBuilder out, SelectionSet selectionSet, int depth) {
        out.append("{\n");
        for (Selection selection : selectionSet.selections()) {
            appendIndent(out, depth + 1);
            appendSelection(out, selection, depth + 1);
            out.append('\n');
        }
        appendIndent(out, depth);
        out.append('}');
    }

    private static void appendSelection(StringBuilder out, Selection selection, int depth) {
        if (selection instanceof Field field) {
            if (field.alias() != null) {
                out.append(field.alias()).append(": ");
            }
            out.append(field.name());
            appendArguments(out, field.arguments());
            appendDirectives(out, field.directives());
            if (field.selectionSet() != null) {
                out.append(' ');
                appendSelectionSet(out, field.selectionSet(), depth);
            }
        } else if (selection instanceof FragmentSpread spread) {
            out.append("...").append(spread.name());
            appendDirectives(out, spread.directives());
        } else if (selection instanceof InlineFragment inline) {
            out.append("...");
            if (inline.typeCondition() != null) {
                out.append(" on ").append(inline.typeCondition());
            }
            appendDirectives(out, inline.directives());
            out.append(' ');
            appendSelectionSet(out, inline.selectionSet(), depth);
        }
    }

    private static void appendArguments(StringBuilder out, List<Argument> arguments) {
        if (arguments.isEmpty()) {
            return;
        }
        out.append('(');
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            Argument argument = arguments.get(i);
            out.append(argument.name()).append(": ");
            appendValue(out, argument.value());
        }
        out.append(')');
    }

    private static void appendDirectives(StringBuilder out, List<Directive> directives) {
        for (Directive directive : directives) {
            out.append(" @").append(directive.name());
            appendArguments(out, directive.arguments());
        }
    }

    private static void appendType(StringBuilder out, TypeNode type) {
        if (type instanceof TypeNode.Named named) {
            out.append(named.name());
        } else if (type instanceof TypeNode.ListOf list) {
            out.append('[');
            appendType(out, list.element());
            out.append(']');
        } else if (type instanceof TypeNode.NonNull nonNull) {
            appendType(out, nonNull.element());
            out.append('!');
        }
    }

    private static void appendValue(StringBuilder out, Value value) {
        if (value instanceof Value.VariableValue variable) {
            out.append('$').append(variable.name());
        } else if (value instanceof Value.IntValue intValue) {
            out.append(intValue.raw());
        } else if (value instanceof Value.FloatValue floatValue) {
            out.append(floatValue.raw());
        } else if (value instanceof Value.StringValue stringValue) {
            appendString(out, stringValue);
        } else if (value instanceof Value.BooleanValue booleanValue) {
            out.append(booleanValue.value() ? "true" : "false");
        } else if (value instanceof Value.NullValue) {
            out.append("null");
        } else if (value instanceof Value.EnumValue enumValue) {
            out.append(enumValue.name());
        } else if (value instanceof Value.ListValue listValue) {
            appendListValue(out, listValue.values());
        } else if (value instanceof Value.ObjectValue objectValue) {
            appendObjectValue(out, objectValue.fields());
        }
    }

    private static void appendListValue(StringBuilder out, List<Value> values) {
        out.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            appendValue(out, values.get(i));
        }
        out.append(']');
    }

    private static void appendObjectValue(StringBuilder out, List<Value.ObjectField> fields) {
        out.append('{');
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(fields.get(i).name()).append(": ");
            appendValue(out, fields.get(i).value());
        }
        out.append('}');
    }

    private static void appendString(StringBuilder out, Value.StringValue value) {
        if (value.block()) {
            out.append("\"\"\"\n")
                    .append(value.value().replace("\"\"\"", "\\\"\"\""))
                    .append("\n\"\"\"");
            return;
        }

        out.append('"');
        String text = value.value();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                out.append("\\\"");
            } else if (c == '\\') {
                out.append("\\\\");
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else if (c == '\b') {
                out.append("\\b");
            } else if (c == '\f') {
                out.append("\\f");
            } else if (c < 0x20) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        out.append('"');
    }

    private static void appendIndent(StringBuilder out, int depth) {
        for (int i = 0; i < depth; i++) {
            out.append(INDENT);
        }
    }
}
