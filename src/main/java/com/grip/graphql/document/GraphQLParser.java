package com.grip.graphql.document;

import java.util.ArrayList;
import java.util.List;

public final class GraphQLParser {

    private final List<Token> tokens;
    private int index;

    private GraphQLParser(String source) {
        GraphQLLexer lexer = new GraphQLLexer(source);
        List<Token> collected = new ArrayList<>();
        Token token = lexer.next();
        while (token.kind() != TokenKind.EOF) {
            collected.add(token);
            token = lexer.next();
        }
        collected.add(token);
        this.tokens = collected;
        this.index = 0;
    }

    public static GraphQLDocument parse(String source) {
        return new GraphQLParser(source).parseDocument();
    }

    public static GraphQLDocument tryParse(String source) {
        try {
            return parse(source);
        } catch (GraphQLSyntaxException e) {
            return null;
        }
    }

    public static SchemaDocument parseSchema(String source) {
        return new GraphQLParser(source).parseSchemaDocument();
    }

    public static SchemaDocument tryParseSchema(String source) {
        try {
            return parseSchema(source);
        } catch (GraphQLSyntaxException e) {
            return null;
        }
    }

    private SchemaDocument parseSchemaDocument() {
        List<SchemaDefinition> definitions = new ArrayList<>();
        while (!at(TokenKind.EOF)) {
            definitions.add(parseSchemaDefinition());
        }
        if (definitions.isEmpty()) {
            throw error("Expected a GraphQL type system definition");
        }
        return new SchemaDocument(definitions);
    }

    private SchemaDefinition parseSchemaDefinition() {
        String description = parseDescription();

        if (!at(TokenKind.NAME)) {
            throw error("Unexpected " + current().describe()
                    + " where a type system definition was expected");
        }

        boolean extension = "extend".equals(current().value());
        if (extension) {
            advance();
            if (!at(TokenKind.NAME)) {
                throw error("Expected a type system keyword after 'extend'");
            }
        }

        String keyword = current().value();
        return switch (keyword) {
            case "schema" -> parseSchemaRoots();
            case "scalar" -> parseScalarType(description, extension);
            case "type" -> parseObjectType(description, extension);
            case "interface" -> parseInterfaceType(description, extension);
            case "union" -> parseUnionType(description, extension);
            case "enum" -> parseEnumType(description, extension);
            case "input" -> parseInputObjectType(description, extension);
            case "directive" -> parseDirectiveDefinition(description);
            default -> throw error("Unknown type system keyword '" + keyword + "'");
        };
    }

    private SchemaDefinition parseSchemaRoots() {
        advance();
        List<Directive> directives = parseDirectives();

        String query = null;
        String mutation = null;
        String subscription = null;

        if (at(TokenKind.BRACE_L)) {
            advance();
            while (!at(TokenKind.BRACE_R)) {
                if (at(TokenKind.EOF)) {
                    throw error("Unterminated schema definition");
                }
                String role = expect(TokenKind.NAME).value();
                expect(TokenKind.COLON);
                String typeName = expect(TokenKind.NAME).value();
                switch (role) {
                    case "query" -> query = typeName;
                    case "mutation" -> mutation = typeName;
                    case "subscription" -> subscription = typeName;
                    default -> throw error("Unknown root operation type '" + role + "'");
                }
            }
            expect(TokenKind.BRACE_R);
        }

        return new SchemaDefinition.SchemaRoots(query, mutation, subscription, directives);
    }

    private SchemaDefinition parseScalarType(String description, boolean extension) {
        advance();
        String name = expect(TokenKind.NAME).value();
        return new SchemaDefinition.ScalarType(name, description, parseDirectives(), extension);
    }

    private SchemaDefinition parseObjectType(String description, boolean extension) {
        advance();
        String name = expect(TokenKind.NAME).value();
        List<String> interfaces = parseImplementsInterfaces();
        List<Directive> directives = parseDirectives();
        List<SchemaDefinition.FieldDef> fields =
                at(TokenKind.BRACE_L) ? parseFieldDefinitions() : List.of();
        return new SchemaDefinition.ObjectType(name, description, interfaces, fields,
                directives, extension);
    }

    private SchemaDefinition parseInterfaceType(String description, boolean extension) {
        advance();
        String name = expect(TokenKind.NAME).value();
        List<String> interfaces = parseImplementsInterfaces();
        List<Directive> directives = parseDirectives();
        List<SchemaDefinition.FieldDef> fields =
                at(TokenKind.BRACE_L) ? parseFieldDefinitions() : List.of();
        return new SchemaDefinition.InterfaceType(name, description, interfaces, fields,
                directives, extension);
    }

    private SchemaDefinition parseUnionType(String description, boolean extension) {
        advance();
        String name = expect(TokenKind.NAME).value();
        List<Directive> directives = parseDirectives();

        List<String> members = new ArrayList<>();
        if (at(TokenKind.EQUALS)) {
            advance();
            if (at(TokenKind.PIPE)) {
                advance();
            }
            members.add(expect(TokenKind.NAME).value());
            while (at(TokenKind.PIPE)) {
                advance();
                members.add(expect(TokenKind.NAME).value());
            }
        }

        return new SchemaDefinition.UnionType(name, description, members, directives, extension);
    }

    private SchemaDefinition parseEnumType(String description, boolean extension) {
        advance();
        String name = expect(TokenKind.NAME).value();
        List<Directive> directives = parseDirectives();

        List<SchemaDefinition.EnumValueDef> values = new ArrayList<>();
        if (at(TokenKind.BRACE_L)) {
            advance();
            while (!at(TokenKind.BRACE_R)) {
                if (at(TokenKind.EOF)) {
                    throw error("Unterminated enum definition");
                }
                String valueDescription = parseDescription();
                String valueName = expect(TokenKind.NAME).value();
                values.add(new SchemaDefinition.EnumValueDef(valueName, valueDescription,
                        parseDirectives()));
            }
            expect(TokenKind.BRACE_R);
        }

        return new SchemaDefinition.EnumType(name, description, values, directives, extension);
    }

    private SchemaDefinition parseInputObjectType(String description, boolean extension) {
        advance();
        String name = expect(TokenKind.NAME).value();
        List<Directive> directives = parseDirectives();
        List<SchemaDefinition.InputValueDef> fields = at(TokenKind.BRACE_L)
                ? parseInputValueDefinitions(TokenKind.BRACE_L, TokenKind.BRACE_R)
                : List.of();
        return new SchemaDefinition.InputObjectType(name, description, fields, directives,
                extension);
    }

    private SchemaDefinition parseDirectiveDefinition(String description) {
        advance();
        expect(TokenKind.AT);
        String name = expect(TokenKind.NAME).value();
        List<SchemaDefinition.InputValueDef> arguments = at(TokenKind.PAREN_L)
                ? parseInputValueDefinitions(TokenKind.PAREN_L, TokenKind.PAREN_R)
                : List.of();

        boolean repeatable = at(TokenKind.NAME) && "repeatable".equals(current().value());
        if (repeatable) {
            advance();
        }

        if (!at(TokenKind.NAME) || !"on".equals(current().value())) {
            throw error("Expected 'on' followed by at least one directive location");
        }
        advance();
        if (at(TokenKind.PIPE)) {
            advance();
        }

        List<String> locations = new ArrayList<>();
        locations.add(expect(TokenKind.NAME).value());
        while (at(TokenKind.PIPE)) {
            advance();
            locations.add(expect(TokenKind.NAME).value());
        }

        return new SchemaDefinition.DirectiveDef(name, description, arguments, locations,
                repeatable);
    }

    private List<String> parseImplementsInterfaces() {
        if (!at(TokenKind.NAME) || !"implements".equals(current().value())) {
            return List.of();
        }
        advance();
        if (at(TokenKind.AMP)) {
            advance();
        }

        List<String> interfaces = new ArrayList<>();
        interfaces.add(expect(TokenKind.NAME).value());
        while (at(TokenKind.AMP)) {
            advance();
            interfaces.add(expect(TokenKind.NAME).value());
        }
        return interfaces;
    }

    private List<SchemaDefinition.FieldDef> parseFieldDefinitions() {
        expect(TokenKind.BRACE_L);

        List<SchemaDefinition.FieldDef> fields = new ArrayList<>();
        while (!at(TokenKind.BRACE_R)) {
            if (at(TokenKind.EOF)) {
                throw error("Unterminated field definition block");
            }
            String description = parseDescription();
            String name = expect(TokenKind.NAME).value();
            List<SchemaDefinition.InputValueDef> arguments = at(TokenKind.PAREN_L)
                    ? parseInputValueDefinitions(TokenKind.PAREN_L, TokenKind.PAREN_R)
                    : List.of();
            expect(TokenKind.COLON);
            TypeNode type = parseType();
            fields.add(new SchemaDefinition.FieldDef(name, description, arguments, type,
                    parseDirectives()));
        }
        expect(TokenKind.BRACE_R);
        return fields;
    }

    private List<SchemaDefinition.InputValueDef> parseInputValueDefinitions(TokenKind open,
                                                                           TokenKind close) {
        expect(open);

        List<SchemaDefinition.InputValueDef> values = new ArrayList<>();
        while (!at(close)) {
            if (at(TokenKind.EOF)) {
                throw error("Unterminated input value definition block");
            }
            String description = parseDescription();
            String name = expect(TokenKind.NAME).value();
            expect(TokenKind.COLON);
            TypeNode type = parseType();

            Value defaultValue = null;
            if (at(TokenKind.EQUALS)) {
                advance();
                defaultValue = parseValue(true);
            }

            values.add(new SchemaDefinition.InputValueDef(name, description, type, defaultValue,
                    parseDirectives()));
        }
        expect(close);
        return values;
    }

    private String parseDescription() {
        if (!at(TokenKind.STRING)) {
            return null;
        }
        String value = current().value();
        advance();
        return value;
    }

    private GraphQLDocument parseDocument() {
        List<Definition> definitions = new ArrayList<>();
        while (!at(TokenKind.EOF)) {
            definitions.add(parseDefinition());
        }
        if (definitions.isEmpty()) {
            throw error("Expected a GraphQL operation or fragment definition");
        }
        return new GraphQLDocument(definitions);
    }

    private Definition parseDefinition() {
        if (at(TokenKind.BRACE_L)) {
            return new OperationDefinition(OperationType.QUERY, null, List.of(), List.of(),
                    parseSelectionSet(), true);
        }

        if (at(TokenKind.NAME)) {
            String keyword = current().value();
            OperationType operationType = OperationType.fromKeyword(keyword);
            if (operationType != null) {
                return parseOperation(operationType);
            }
            if ("fragment".equals(keyword)) {
                return parseFragment();
            }
            throw error("Unexpected name '" + keyword
                    + "', expected query, mutation, subscription or fragment");
        }

        throw error("Unexpected " + current().describe()
                + ", expected query, mutation, subscription, fragment or '{'");
    }

    private OperationDefinition parseOperation(OperationType operationType) {
        advance();

        String name = null;
        if (at(TokenKind.NAME)) {
            name = current().value();
            advance();
        }

        List<VariableDefinition> variableDefinitions =
                at(TokenKind.PAREN_L) ? parseVariableDefinitions() : List.of();
        List<Directive> directives = parseDirectives();

        return new OperationDefinition(operationType, name, variableDefinitions, directives,
                parseSelectionSet(), false);
    }

    private FragmentDefinition parseFragment() {
        advance();

        String name = expect(TokenKind.NAME).value();
        if ("on".equals(name)) {
            throw error("Fragment name cannot be 'on'");
        }

        Token onKeyword = expect(TokenKind.NAME);
        if (!"on".equals(onKeyword.value())) {
            throw new GraphQLSyntaxException("Expected 'on' in fragment definition",
                    onKeyword.line(), onKeyword.column());
        }

        String typeCondition = expect(TokenKind.NAME).value();
        List<Directive> directives = parseDirectives();

        return new FragmentDefinition(name, typeCondition, directives, parseSelectionSet());
    }

    private List<VariableDefinition> parseVariableDefinitions() {
        expect(TokenKind.PAREN_L);
        List<VariableDefinition> definitions = new ArrayList<>();

        while (!at(TokenKind.PAREN_R)) {
            if (at(TokenKind.EOF)) {
                throw error("Unterminated variable definitions");
            }
            expect(TokenKind.DOLLAR);
            String name = expect(TokenKind.NAME).value();
            expect(TokenKind.COLON);
            TypeNode type = parseType();

            Value defaultValue = null;
            if (at(TokenKind.EQUALS)) {
                advance();
                defaultValue = parseValue(true);
            }

            definitions.add(new VariableDefinition(name, type, defaultValue, parseDirectives()));
        }

        expect(TokenKind.PAREN_R);
        if (definitions.isEmpty()) {
            throw error("Variable definitions cannot be empty");
        }
        return definitions;
    }

    private TypeNode parseType() {
        TypeNode type;
        if (at(TokenKind.BRACKET_L)) {
            advance();
            TypeNode element = parseType();
            expect(TokenKind.BRACKET_R);
            type = new TypeNode.ListOf(element);
        } else {
            type = new TypeNode.Named(expect(TokenKind.NAME).value());
        }

        if (at(TokenKind.BANG)) {
            advance();
            return new TypeNode.NonNull(type);
        }
        return type;
    }

    private SelectionSet parseSelectionSet() {
        expect(TokenKind.BRACE_L);
        List<Selection> selections = new ArrayList<>();

        while (!at(TokenKind.BRACE_R)) {
            if (at(TokenKind.EOF)) {
                throw error("Unterminated selection set");
            }
            selections.add(parseSelection());
        }

        if (selections.isEmpty()) {
            throw error("Selection set cannot be empty");
        }
        expect(TokenKind.BRACE_R);
        return new SelectionSet(selections);
    }

    private Selection parseSelection() {
        if (at(TokenKind.SPREAD)) {
            advance();

            if (at(TokenKind.NAME) && !"on".equals(current().value())) {
                String name = current().value();
                advance();
                return new FragmentSpread(name, parseDirectives());
            }

            String typeCondition = null;
            if (at(TokenKind.NAME)) {
                advance();
                typeCondition = expect(TokenKind.NAME).value();
            }

            List<Directive> directives = parseDirectives();
            return new InlineFragment(typeCondition, directives, parseSelectionSet());
        }

        String alias = null;
        String name = expect(TokenKind.NAME).value();
        if (at(TokenKind.COLON)) {
            advance();
            alias = name;
            name = expect(TokenKind.NAME).value();
        }

        List<Argument> arguments = at(TokenKind.PAREN_L) ? parseArguments() : List.of();
        List<Directive> directives = parseDirectives();
        SelectionSet selectionSet = at(TokenKind.BRACE_L) ? parseSelectionSet() : null;

        return new Field(alias, name, arguments, directives, selectionSet);
    }

    private List<Argument> parseArguments() {
        expect(TokenKind.PAREN_L);
        List<Argument> arguments = new ArrayList<>();

        while (!at(TokenKind.PAREN_R)) {
            if (at(TokenKind.EOF)) {
                throw error("Unterminated argument list");
            }
            String name = expect(TokenKind.NAME).value();
            expect(TokenKind.COLON);
            arguments.add(new Argument(name, parseValue(false)));
        }

        expect(TokenKind.PAREN_R);
        return arguments;
    }

    private List<Directive> parseDirectives() {
        if (!at(TokenKind.AT)) {
            return List.of();
        }

        List<Directive> directives = new ArrayList<>();
        while (at(TokenKind.AT)) {
            advance();
            String name = expect(TokenKind.NAME).value();
            List<Argument> arguments = at(TokenKind.PAREN_L) ? parseArguments() : List.of();
            directives.add(new Directive(name, arguments));
        }
        return directives;
    }

    private Value parseValue(boolean constant) {
        Token token = current();
        TokenKind kind = token.kind();

        if (kind == TokenKind.DOLLAR) {
            if (constant) {
                throw error("Variables are not allowed in this position");
            }
            advance();
            return new Value.VariableValue(expect(TokenKind.NAME).value());
        }

        if (kind == TokenKind.INT) {
            advance();
            return new Value.IntValue(token.value());
        }

        if (kind == TokenKind.FLOAT) {
            advance();
            return new Value.FloatValue(token.value());
        }

        if (kind == TokenKind.STRING) {
            advance();
            return new Value.StringValue(token.value(), token.block());
        }

        if (kind == TokenKind.NAME) {
            advance();
            String name = token.value();
            if ("true".equals(name)) {
                return new Value.BooleanValue(true);
            }
            if ("false".equals(name)) {
                return new Value.BooleanValue(false);
            }
            if ("null".equals(name)) {
                return new Value.NullValue();
            }
            return new Value.EnumValue(name);
        }

        if (kind == TokenKind.BRACKET_L) {
            advance();
            List<Value> values = new ArrayList<>();
            while (!at(TokenKind.BRACKET_R)) {
                if (at(TokenKind.EOF)) {
                    throw error("Unterminated list value");
                }
                values.add(parseValue(constant));
            }
            expect(TokenKind.BRACKET_R);
            return new Value.ListValue(values);
        }

        if (kind == TokenKind.BRACE_L) {
            advance();
            List<Value.ObjectField> fields = new ArrayList<>();
            while (!at(TokenKind.BRACE_R)) {
                if (at(TokenKind.EOF)) {
                    throw error("Unterminated object value");
                }
                String name = expect(TokenKind.NAME).value();
                expect(TokenKind.COLON);
                fields.add(new Value.ObjectField(name, parseValue(constant)));
            }
            expect(TokenKind.BRACE_R);
            return new Value.ObjectValue(fields);
        }

        throw error("Unexpected " + token.describe() + " where a value was expected");
    }

    private Token current() {
        return tokens.get(index);
    }

    private boolean at(TokenKind kind) {
        return current().kind() == kind;
    }

    private void advance() {
        if (index < tokens.size() - 1) {
            index++;
        }
    }

    private Token expect(TokenKind kind) {
        Token token = current();
        if (token.kind() != kind) {
            throw new GraphQLSyntaxException("Expected " + kind.description()
                    + " but found " + token.describe(), token.line(), token.column());
        }
        advance();
        return token;
    }

    private GraphQLSyntaxException error(String message) {
        Token token = current();
        return new GraphQLSyntaxException(message, token.line(), token.column());
    }
}
