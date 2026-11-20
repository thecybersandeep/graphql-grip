package com.grip.graphql.document;

import java.util.List;

public sealed interface SchemaDefinition {

    record SchemaRoots(String queryType, String mutationType, String subscriptionType,
                       List<Directive> directives) implements SchemaDefinition {

        public SchemaRoots(String queryType, String mutationType, String subscriptionType,
                           List<Directive> directives) {
            this.queryType = queryType;
            this.mutationType = mutationType;
            this.subscriptionType = subscriptionType;
            this.directives = directives == null ? List.of() : List.copyOf(directives);
        }
    }

    sealed interface TypeDefinition extends SchemaDefinition {

        String name();

        String description();

        List<Directive> directives();

        boolean extension();
    }

    record ScalarType(String name, String description, List<Directive> directives,
                      boolean extension) implements TypeDefinition {

        public ScalarType(String name, String description, List<Directive> directives,
                          boolean extension) {
            this.name = name;
            this.description = description;
            this.directives = directives == null ? List.of() : List.copyOf(directives);
            this.extension = extension;
        }
    }

    record ObjectType(String name, String description, List<String> interfaces,
                      List<FieldDef> fields, List<Directive> directives,
                      boolean extension) implements TypeDefinition {

        public ObjectType(String name, String description, List<String> interfaces,
                          List<FieldDef> fields, List<Directive> directives, boolean extension) {
            this.name = name;
            this.description = description;
            this.interfaces = interfaces == null ? List.of() : List.copyOf(interfaces);
            this.fields = fields == null ? List.of() : List.copyOf(fields);
            this.directives = directives == null ? List.of() : List.copyOf(directives);
            this.extension = extension;
        }
    }

    record InterfaceType(String name, String description, List<String> interfaces,
                         List<FieldDef> fields, List<Directive> directives,
                         boolean extension) implements TypeDefinition {

        public InterfaceType(String name, String description, List<String> interfaces,
                             List<FieldDef> fields, List<Directive> directives, boolean extension) {
            this.name = name;
            this.description = description;
            this.interfaces = interfaces == null ? List.of() : List.copyOf(interfaces);
            this.fields = fields == null ? List.of() : List.copyOf(fields);
            this.directives = directives == null ? List.of() : List.copyOf(directives);
            this.extension = extension;
        }
    }

    record UnionType(String name, String description, List<String> members,
                     List<Directive> directives, boolean extension) implements TypeDefinition {

        public UnionType(String name, String description, List<String> members,
                         List<Directive> directives, boolean extension) {
            this.name = name;
            this.description = description;
            this.members = members == null ? List.of() : List.copyOf(members);
            this.directives = directives == null ? List.of() : List.copyOf(directives);
            this.extension = extension;
        }
    }

    record EnumType(String name, String description, List<EnumValueDef> values,
                    List<Directive> directives, boolean extension) implements TypeDefinition {

        public EnumType(String name, String description, List<EnumValueDef> values,
                        List<Directive> directives, boolean extension) {
            this.name = name;
            this.description = description;
            this.values = values == null ? List.of() : List.copyOf(values);
            this.directives = directives == null ? List.of() : List.copyOf(directives);
            this.extension = extension;
        }
    }

    record InputObjectType(String name, String description, List<InputValueDef> fields,
                           List<Directive> directives, boolean extension) implements TypeDefinition {

        public InputObjectType(String name, String description, List<InputValueDef> fields,
                               List<Directive> directives, boolean extension) {
            this.name = name;
            this.description = description;
            this.fields = fields == null ? List.of() : List.copyOf(fields);
            this.directives = directives == null ? List.of() : List.copyOf(directives);
            this.extension = extension;
        }
    }

    record DirectiveDef(String name, String description, List<InputValueDef> arguments,
                        List<String> locations, boolean repeatable) implements SchemaDefinition {

        public DirectiveDef(String name, String description, List<InputValueDef> arguments,
                            List<String> locations, boolean repeatable) {
            this.name = name;
            this.description = description;
            this.arguments = arguments == null ? List.of() : List.copyOf(arguments);
            this.locations = locations == null ? List.of() : List.copyOf(locations);
            this.repeatable = repeatable;
        }
    }

    record FieldDef(String name, String description, List<InputValueDef> arguments,
                    TypeNode type, List<Directive> directives) {

        public FieldDef(String name, String description, List<InputValueDef> arguments,
                        TypeNode type, List<Directive> directives) {
            this.name = name;
            this.description = description;
            this.arguments = arguments == null ? List.of() : List.copyOf(arguments);
            this.type = type;
            this.directives = directives == null ? List.of() : List.copyOf(directives);
        }

        public Directive directive(String directiveName) {
            for (Directive directive : directives) {
                if (directive.name().equals(directiveName)) {
                    return directive;
                }
            }
            return null;
        }
    }

    record InputValueDef(String name, String description, TypeNode type, Value defaultValue,
                         List<Directive> directives) {

        public InputValueDef(String name, String description, TypeNode type, Value defaultValue,
                             List<Directive> directives) {
            this.name = name;
            this.description = description;
            this.type = type;
            this.defaultValue = defaultValue;
            this.directives = directives == null ? List.of() : List.copyOf(directives);
        }
    }

    record EnumValueDef(String name, String description, List<Directive> directives) {

        public EnumValueDef(String name, String description, List<Directive> directives) {
            this.name = name;
            this.description = description;
            this.directives = directives == null ? List.of() : List.copyOf(directives);
        }

        public Directive directive(String directiveName) {
            for (Directive directive : directives) {
                if (directive.name().equals(directiveName)) {
                    return directive;
                }
            }
            return null;
        }
    }
}
