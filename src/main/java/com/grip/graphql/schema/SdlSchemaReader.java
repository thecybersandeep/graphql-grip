package com.grip.graphql.schema;

import com.grip.graphql.document.Argument;
import com.grip.graphql.document.Directive;
import com.grip.graphql.document.GraphQLParser;
import com.grip.graphql.document.GraphQLPrinter;
import com.grip.graphql.document.SchemaDefinition;
import com.grip.graphql.document.SchemaDocument;
import com.grip.graphql.document.TypeNode;
import com.grip.graphql.document.Value;
import com.grip.graphql.model.schema.GripArgument;
import com.grip.graphql.model.schema.GripDirective;
import com.grip.graphql.model.schema.GripEnumValue;
import com.grip.graphql.model.schema.GripField;
import com.grip.graphql.model.schema.GripSchema;
import com.grip.graphql.model.schema.GripType;
import com.grip.graphql.model.schema.GripTypeKind;
import com.grip.graphql.model.schema.GripTypeRef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SdlSchemaReader {

    private static final Set<String> BUILT_IN_SCALARS =
            Set.of("String", "Int", "Float", "Boolean", "ID");

    private static final String DEPRECATED = "deprecated";

    private final Map<String, SchemaDefinition.TypeDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, GripTypeKind> kinds = new LinkedHashMap<>();

    private SdlSchemaReader() {
    }

    public static GripSchema read(String sdl, String endpoint) {
        return new SdlSchemaReader().convert(GraphQLParser.parseSchema(sdl), endpoint);
    }

    private GripSchema convert(SchemaDocument document, String endpoint) {
        for (SchemaDefinition.TypeDefinition definition : document.typeDefinitions()) {
            merge(definition);
        }

        for (String name : BUILT_IN_SCALARS) {
            kinds.putIfAbsent(name, GripTypeKind.SCALAR);
        }

        GripSchema schema = new GripSchema();
        schema.setSourceEndpoint(endpoint);

        for (SchemaDefinition.TypeDefinition definition : definitions.values()) {
            schema.addType(typeOf(definition));
        }

        for (SchemaDefinition.TypeDefinition definition : definitions.values()) {
            linkReferences(schema, definition);
        }

        for (SchemaDefinition.DirectiveDef definition : document.directiveDefinitions()) {
            schema.addDirective(directiveOf(definition));
        }

        applyRoots(schema, document.roots());
        return schema;
    }

    private void merge(SchemaDefinition.TypeDefinition definition) {
        SchemaDefinition.TypeDefinition existing = definitions.get(definition.name());
        if (existing == null) {
            definitions.put(definition.name(), definition);
            kinds.put(definition.name(), kindOf(definition));
            return;
        }

        definitions.put(definition.name(), combine(existing, definition));
    }

    private static SchemaDefinition.TypeDefinition combine(
            SchemaDefinition.TypeDefinition base, SchemaDefinition.TypeDefinition addition) {

        if (base instanceof SchemaDefinition.ObjectType first
                && addition instanceof SchemaDefinition.ObjectType second) {
            return new SchemaDefinition.ObjectType(first.name(),
                    first.description() != null ? first.description() : second.description(),
                    join(first.interfaces(), second.interfaces()),
                    join(first.fields(), second.fields()),
                    join(first.directives(), second.directives()), false);
        }

        if (base instanceof SchemaDefinition.InterfaceType first
                && addition instanceof SchemaDefinition.InterfaceType second) {
            return new SchemaDefinition.InterfaceType(first.name(),
                    first.description() != null ? first.description() : second.description(),
                    join(first.interfaces(), second.interfaces()),
                    join(first.fields(), second.fields()),
                    join(first.directives(), second.directives()), false);
        }

        if (base instanceof SchemaDefinition.InputObjectType first
                && addition instanceof SchemaDefinition.InputObjectType second) {
            return new SchemaDefinition.InputObjectType(first.name(),
                    first.description() != null ? first.description() : second.description(),
                    join(first.fields(), second.fields()),
                    join(first.directives(), second.directives()), false);
        }

        if (base instanceof SchemaDefinition.EnumType first
                && addition instanceof SchemaDefinition.EnumType second) {
            return new SchemaDefinition.EnumType(first.name(),
                    first.description() != null ? first.description() : second.description(),
                    join(first.values(), second.values()),
                    join(first.directives(), second.directives()), false);
        }

        if (base instanceof SchemaDefinition.UnionType first
                && addition instanceof SchemaDefinition.UnionType second) {
            return new SchemaDefinition.UnionType(first.name(),
                    first.description() != null ? first.description() : second.description(),
                    join(first.members(), second.members()),
                    join(first.directives(), second.directives()), false);
        }

        return base.extension() ? addition : base;
    }

    private static <T> List<T> join(List<T> first, List<T> second) {
        List<T> combined = new ArrayList<>(first);
        for (T candidate : second) {
            if (!combined.contains(candidate)) {
                combined.add(candidate);
            }
        }
        return combined;
    }

    private static GripTypeKind kindOf(SchemaDefinition.TypeDefinition definition) {
        if (definition instanceof SchemaDefinition.ScalarType) {
            return GripTypeKind.SCALAR;
        }
        if (definition instanceof SchemaDefinition.InterfaceType) {
            return GripTypeKind.INTERFACE;
        }
        if (definition instanceof SchemaDefinition.UnionType) {
            return GripTypeKind.UNION;
        }
        if (definition instanceof SchemaDefinition.EnumType) {
            return GripTypeKind.ENUM;
        }
        if (definition instanceof SchemaDefinition.InputObjectType) {
            return GripTypeKind.INPUT_OBJECT;
        }
        return GripTypeKind.OBJECT;
    }

    private GripType typeOf(SchemaDefinition.TypeDefinition definition) {
        GripType type = new GripType(definition.name(), kinds.get(definition.name()));
        type.setDescription(definition.description());

        if (definition instanceof SchemaDefinition.ObjectType object) {
            for (SchemaDefinition.FieldDef field : object.fields()) {
                type.addField(fieldOf(field));
            }
        } else if (definition instanceof SchemaDefinition.InterfaceType iface) {
            for (SchemaDefinition.FieldDef field : iface.fields()) {
                type.addField(fieldOf(field));
            }
        } else if (definition instanceof SchemaDefinition.InputObjectType input) {
            for (SchemaDefinition.InputValueDef field : input.fields()) {
                type.addInputField(inputFieldOf(field));
            }
        } else if (definition instanceof SchemaDefinition.EnumType enumType) {
            for (SchemaDefinition.EnumValueDef value : enumType.values()) {
                type.addEnumValue(enumValueOf(value));
            }
        }

        return type;
    }

    private void linkReferences(GripSchema schema, SchemaDefinition.TypeDefinition definition) {
        GripType type = schema.getType(definition.name());
        if (type == null) {
            return;
        }

        List<String> interfaces = definition instanceof SchemaDefinition.ObjectType object
                ? object.interfaces()
                : definition instanceof SchemaDefinition.InterfaceType iface
                        ? iface.interfaces()
                        : List.of();

        for (String name : interfaces) {
            GripType target = schema.getType(name);
            if (target != null) {
                type.addInterface(target);
            }
        }

        if (definition instanceof SchemaDefinition.UnionType union) {
            for (String name : union.members()) {
                GripType target = schema.getType(name);
                if (target != null) {
                    type.addPossibleType(target);
                }
            }
        }
    }

    private GripField fieldOf(SchemaDefinition.FieldDef definition) {
        GripField field = new GripField(definition.name(), refOf(definition.type()),
                definition.description());

        for (SchemaDefinition.InputValueDef argument : definition.arguments()) {
            field.addArgument(argumentOf(argument));
        }

        Directive deprecated = definition.directive(DEPRECATED);
        if (deprecated != null) {
            field.setDeprecated(true);
            field.setDeprecationReason(reasonOf(deprecated));
        }

        return field;
    }

    private GripField inputFieldOf(SchemaDefinition.InputValueDef definition) {
        return new GripField(definition.name(), refOf(definition.type()), definition.description());
    }

    private GripArgument argumentOf(SchemaDefinition.InputValueDef definition) {
        GripArgument argument = new GripArgument(definition.name(), refOf(definition.type()),
                definition.description());
        if (definition.defaultValue() != null) {
            argument.setDefaultValue(GraphQLPrinter.print(definition.defaultValue()));
        }
        return argument;
    }

    private static GripEnumValue enumValueOf(SchemaDefinition.EnumValueDef definition) {
        GripEnumValue value = new GripEnumValue(definition.name(), definition.description());

        Directive deprecated = definition.directive(DEPRECATED);
        if (deprecated != null) {
            value.setDeprecated(true);
            value.setDeprecationReason(reasonOf(deprecated));
        }

        return value;
    }

    private GripDirective directiveOf(SchemaDefinition.DirectiveDef definition) {
        GripDirective directive = new GripDirective(definition.name());
        directive.setDescription(definition.description());
        directive.setRepeatable(definition.repeatable());

        for (SchemaDefinition.InputValueDef argument : definition.arguments()) {
            directive.addArgument(argumentOf(argument));
        }

        for (String location : definition.locations()) {
            GripDirective.Location known = locationOf(location);
            if (known != null) {
                directive.addLocation(known);
            }
        }

        return directive;
    }

    private static GripDirective.Location locationOf(String name) {
        for (GripDirective.Location location : GripDirective.Location.values()) {
            if (location.name().equals(name)) {
                return location;
            }
        }
        return null;
    }

    private static void applyRoots(GripSchema schema, SchemaDefinition.SchemaRoots roots) {
        if (roots != null) {
            schema.setQueryTypeName(declaredRoot(schema, roots.queryType()));
            schema.setMutationTypeName(declaredRoot(schema, roots.mutationType()));
            schema.setSubscriptionTypeName(declaredRoot(schema, roots.subscriptionType()));
            return;
        }

        schema.setQueryTypeName(conventionalRoot(schema, "Query"));
        schema.setMutationTypeName(conventionalRoot(schema, "Mutation"));
        schema.setSubscriptionTypeName(conventionalRoot(schema, "Subscription"));
    }

    private static String declaredRoot(GripSchema schema, String declared) {
        return declared != null && schema.hasType(declared) ? declared : null;
    }

    private static String conventionalRoot(GripSchema schema, String conventional) {
        return schema.hasType(conventional) ? conventional : null;
    }

    private GripTypeRef refOf(TypeNode node) {
        boolean nonNull = false;
        TypeNode current = node;

        if (current instanceof TypeNode.NonNull outer) {
            nonNull = true;
            current = outer.element();
        }

        if (current instanceof TypeNode.ListOf list) {
            TypeNode element = list.element();
            boolean itemNonNull = false;
            if (element instanceof TypeNode.NonNull inner) {
                itemNonNull = true;
                element = inner.element();
            }
            String name = nameOf(element);
            return new GripTypeRef(name, kindFor(name), true, nonNull, itemNonNull);
        }

        String name = nameOf(current);
        return new GripTypeRef(name, kindFor(name), false, nonNull, false);
    }

    private static String nameOf(TypeNode node) {
        if (node instanceof TypeNode.Named named) {
            return named.name();
        }
        if (node instanceof TypeNode.ListOf list) {
            return nameOf(list.element());
        }
        if (node instanceof TypeNode.NonNull nonNull) {
            return nameOf(nonNull.element());
        }
        return null;
    }

    private GripTypeKind kindFor(String name) {
        GripTypeKind kind = kinds.get(name);
        return kind != null ? kind : GripTypeKind.OBJECT;
    }

    private static String reasonOf(Directive directive) {
        for (Argument argument : directive.arguments()) {
            if ("reason".equals(argument.name())
                    && argument.value() instanceof Value.StringValue text) {
                return text.value();
            }
        }
        return null;
    }
}
