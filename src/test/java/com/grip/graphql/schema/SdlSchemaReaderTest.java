package com.grip.graphql.schema;

import com.grip.graphql.model.schema.GripArgument;
import com.grip.graphql.model.schema.GripDirective;
import com.grip.graphql.model.schema.GripField;
import com.grip.graphql.model.schema.GripSchema;
import com.grip.graphql.model.schema.GripType;
import com.grip.graphql.model.schema.GripTypeKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SdlSchemaReaderTest {

    private static final String SCHEMA = """
            schema {
              query: RootQuery
              mutation: RootMutation
            }

            scalar DateTime

            "Anything with a stable identity."
            interface Node {
              id: ID!
            }

            enum Role {
              ADMIN
              EDITOR
              VIEWER @deprecated(reason: "use EDITOR")
            }

            input LoginInput {
              username: String!
              password: String!
            }

            input PostFilter {
              authorId: ID
              since: DateTime
              roles: [Role!]
            }

            type User implements Node {
              id: ID!
              username: String!
              role: Role!
              posts(filter: PostFilter, first: Int = 10): [Post!]!
              manager: User
              legacyName: String @deprecated(reason: "use username")
            }

            type Post implements Node {
              id: ID!
              title: String!
              author: User!
              publishedAt: DateTime
            }

            type LoginPayload {
              token: String
              success: Boolean!
              user: User
            }

            union SearchResult = User | Post

            type RootQuery {
              me: User
              node(id: ID!): Node
              search(term: String!): [SearchResult!]!
            }

            type RootMutation {
              login(input: LoginInput!): LoginPayload!
              deletePost(id: ID!): Boolean!
            }

            directive @auth(requires: Role = VIEWER) repeatable on FIELD_DEFINITION | OBJECT
            """;

    @Test
    void readsEveryTypeInTheDocument() {
        GripSchema schema = read();

        assertEquals(11, schema.getTypeCount());
        for (String name : List.of("DateTime", "Node", "Role", "LoginInput", "PostFilter",
                "User", "Post", "LoginPayload", "SearchResult", "RootQuery", "RootMutation")) {
            assertTrue(schema.hasType(name), name);
        }
    }

    @Test
    void readsTheRootTypeNamesFromTheSchemaDefinition() {
        GripSchema schema = read();

        assertEquals("RootQuery", schema.getQueryTypeName());
        assertEquals("RootMutation", schema.getMutationTypeName());
        assertNull(schema.getSubscriptionTypeName());

        assertEquals(3, schema.getQueries().size());
        assertEquals(2, schema.getMutations().size());
        assertTrue(schema.getSubscriptions().isEmpty());
    }

    @Test
    void fallsBackToTheConventionalRootNamesOnlyWhenThoseTypesExist() {
        GripSchema schema = SdlSchemaReader.read(
                "type Query { me: String }\ntype Mutation { ping: String }", "http://x/graphql");

        assertEquals("Query", schema.getQueryTypeName());
        assertEquals("Mutation", schema.getMutationTypeName());
        assertNull(schema.getSubscriptionTypeName());
    }

    @Test
    void aSchemaBlockOverridesTheNamingConvention() {
        GripSchema schema = SdlSchemaReader.read("""
                schema { query: RootQuery }
                type RootQuery { me: String }
                type Mutation { ping: String }
                type Subscription { tick: String }
                """, "http://x/graphql");

        assertEquals("RootQuery", schema.getQueryTypeName());
        assertNull(schema.getMutationTypeName());
        assertNull(schema.getSubscriptionTypeName());
        assertTrue(schema.hasType("Mutation"));
        assertTrue(schema.getMutations().isEmpty());
    }

    @Test
    void mergesSchemaExtensionsIntoTheRootTypes() {
        GripSchema schema = SdlSchemaReader.read("""
                schema { query: RootQuery }
                extend schema { mutation: RootMutation }
                type RootQuery { me: String }
                type RootMutation { ping: String }
                """, "http://x/graphql");

        assertEquals("RootQuery", schema.getQueryTypeName());
        assertEquals("RootMutation", schema.getMutationTypeName());
    }

    @Test
    void neverInventsARootTypeThatIsNotDeclared() {
        GripSchema schema = SdlSchemaReader.read("type Widget { id: ID! }", "http://x/graphql");

        assertNull(schema.getQueryTypeName());
        assertNull(schema.getMutationTypeName());
        assertEquals(1, schema.getTypeCount());
        assertTrue(schema.getQueries().isEmpty());
    }

    @Test
    void keepsTheRealKindOfEveryType() {
        GripSchema schema = read();

        assertEquals(GripTypeKind.SCALAR, schema.getType("DateTime").getKind());
        assertEquals(GripTypeKind.INTERFACE, schema.getType("Node").getKind());
        assertEquals(GripTypeKind.ENUM, schema.getType("Role").getKind());
        assertEquals(GripTypeKind.INPUT_OBJECT, schema.getType("LoginInput").getKind());
        assertEquals(GripTypeKind.UNION, schema.getType("SearchResult").getKind());
        assertEquals(GripTypeKind.OBJECT, schema.getType("User").getKind());
    }

    @Test
    void keepsTypesThatImplementAnInterface() {
        GripSchema schema = read();
        GripType user = schema.getType("User");

        assertEquals(6, user.getFields().size());
        assertEquals(1, user.getInterfaces().size());
        assertEquals("Node", user.getInterfaces().get(0).getName());
    }

    @Test
    void resolvesFieldTypeKindsFromTheDocumentRatherThanTheName() {
        GripField posts = read().getType("User").getField("posts");

        assertEquals("Post", posts.getType().getName());
        assertEquals(GripTypeKind.OBJECT, posts.getType().getKind());
        assertTrue(posts.getType().isList());
        assertTrue(posts.getType().isNonNull());
        assertTrue(posts.getType().isNonNullItem());

        GripField role = read().getType("User").getField("role");
        assertEquals(GripTypeKind.ENUM, role.getType().getKind());
    }

    @Test
    void doesNotGuessAnInputKindFromATypeNameSuffix() {
        GripSchema schema = SdlSchemaReader.read(
                "type Query { thing: ThingInput }\ntype ThingInput { id: ID! }", "http://x/graphql");

        assertEquals(GripTypeKind.OBJECT, schema.getType("ThingInput").getKind());
        assertEquals(GripTypeKind.OBJECT,
                schema.getType("Query").getField("thing").getType().getKind());
    }

    @Test
    void keepsFieldArgumentsAndTheirDefaults() {
        GripField posts = read().getType("User").getField("posts");

        assertEquals(2, posts.getArguments().size());

        GripArgument filter = posts.getArgument("filter");
        assertEquals("PostFilter", filter.getType().getName());
        assertEquals(GripTypeKind.INPUT_OBJECT, filter.getType().getKind());
        assertFalse(filter.isRequired());

        GripArgument first = posts.getArgument("first");
        assertEquals("10", first.getDefaultValue());

        GripArgument input = read().getType("RootMutation").getField("login").getArgument("input");
        assertTrue(input.isRequired());
    }

    @Test
    void keepsInputObjectFields() {
        GripType filter = read().getType("PostFilter");

        assertEquals(3, filter.getInputFields().size());
        assertTrue(filter.getFields().isEmpty());

        GripField roles = filter.getInputFields().get(2);
        assertEquals("roles", roles.getName());
        assertEquals("Role", roles.getType().getName());
        assertTrue(roles.getType().isList());
    }

    @Test
    void keepsEnumValuesAndTheirDeprecation() {
        GripType role = read().getType("Role");

        assertEquals(3, role.getEnumValues().size());
        assertEquals("ADMIN", role.getEnumValues().get(0).getName());
        assertTrue(role.getEnumValues().get(2).isDeprecated());
        assertEquals("use EDITOR", role.getEnumValues().get(2).getDeprecationReason());
    }

    @Test
    void keepsUnionMembers() {
        GripType result = read().getType("SearchResult");

        assertEquals(2, result.getPossibleTypes().size());
        assertEquals("User", result.getPossibleTypes().get(0).getName());
        assertEquals("Post", result.getPossibleTypes().get(1).getName());
    }

    @Test
    void keepsDeprecatedFieldsWithTheirReason() {
        GripField legacy = read().getType("User").getField("legacyName");

        assertTrue(legacy.isDeprecated());
        assertEquals("use username", legacy.getDeprecationReason());
    }

    @Test
    void keepsDescriptions() {
        assertEquals("Anything with a stable identity.", read().getType("Node").getDescription());
    }

    @Test
    void readsDirectiveDefinitions() {
        GripDirective auth = read().getDirective("auth");

        assertNotNull(auth);
        assertTrue(auth.isRepeatable());
        assertEquals(1, auth.getArguments().size());
        assertEquals("VIEWER", auth.getArguments().get(0).getDefaultValue());
        assertTrue(auth.isValidAt(GripDirective.Location.FIELD_DEFINITION));
        assertTrue(auth.isValidAt(GripDirective.Location.OBJECT));
    }

    @Test
    void mergesTypeExtensionsIntoTheBaseType() {
        GripSchema schema = SdlSchemaReader.read("""
                type Query { me: String }
                extend type Query { version: String }
                enum Status { OPEN }
                extend enum Status { CLOSED }
                """, "http://x/graphql");

        assertEquals(2, schema.getType("Query").getFields().size());
        assertEquals(2, schema.getType("Status").getEnumValues().size());
        assertEquals(2, schema.getQueries().size());
    }

    @Test
    void readsAnExtensionThatArrivesBeforeItsBaseType() {
        GripSchema schema = SdlSchemaReader.read("""
                extend type Query { version: String }
                type Query { me: String }
                """, "http://x/graphql");

        assertEquals(2, schema.getType("Query").getFields().size());
    }

    @Test
    void readsBackWhatTheExporterWrites() {
        GripSchema exported = SdlSchemaReader.read(read().toSDL(), "http://x/graphql");

        assertEquals("RootQuery", exported.getQueryTypeName());
        assertEquals("RootMutation", exported.getMutationTypeName());
        assertEquals(3, exported.getQueries().size());
        assertEquals(2, exported.getMutations().size());
        assertEquals(GripTypeKind.ENUM, exported.getType("Role").getKind());
        assertEquals(GripTypeKind.INPUT_OBJECT, exported.getType("LoginInput").getKind());
        assertEquals(GripTypeKind.UNION, exported.getType("SearchResult").getKind());
        assertEquals(1, exported.getType("User").getInterfaces().size());
        assertEquals(2, exported.getType("User").getField("posts").getArguments().size());
    }

    @Test
    void recordsTheEndpointItWasImportedFor() {
        assertEquals("https://target.example/graphql",
                SdlSchemaReader.read("type Query { me: String }",
                        "https://target.example/graphql").getSourceEndpoint());
    }

    private static GripSchema read() {
        return SdlSchemaReader.read(SCHEMA, "http://x/graphql");
    }
}
