package com.grip.graphql.schema;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grip.graphql.model.schema.GripField;
import com.grip.graphql.model.schema.GripSchema;
import com.grip.graphql.model.schema.GripType;
import com.grip.graphql.model.schema.GripTypeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntrospectionImportTest {

    private static final String RESULT = """
            {
              "data": {
                "__schema": {
                  "queryType": { "name": "RootQuery" },
                  "mutationType": { "name": "RootMutation" },
                  "subscriptionType": null,
                  "types": [
                    {
                      "kind": "INTERFACE", "name": "Node",
                      "fields": [
                        { "name": "id", "args": [],
                          "type": { "kind": "NON_NULL", "name": null,
                                    "ofType": { "kind": "SCALAR", "name": "ID" } } }
                      ],
                      "possibleTypes": [
                        { "kind": "OBJECT", "name": "User" },
                        { "kind": "OBJECT", "name": "Post" }
                      ]
                    },
                    {
                      "kind": "OBJECT", "name": "User",
                      "interfaces": [ { "kind": "INTERFACE", "name": "Node" } ],
                      "fields": [
                        { "name": "id", "args": [],
                          "type": { "kind": "SCALAR", "name": "ID" } },
                        { "name": "posts", "args": [
                            { "name": "first",
                              "type": { "kind": "SCALAR", "name": "Int" },
                              "defaultValue": "10" }
                          ],
                          "type": { "kind": "LIST", "name": null,
                                    "ofType": { "kind": "OBJECT", "name": "Post" } } }
                      ]
                    },
                    {
                      "kind": "OBJECT", "name": "Post",
                      "interfaces": [ { "kind": "INTERFACE", "name": "Node" } ],
                      "fields": [
                        { "name": "author", "args": [],
                          "type": { "kind": "NON_NULL", "name": null,
                                    "ofType": { "kind": "OBJECT", "name": "User" } } }
                      ]
                    },
                    {
                      "kind": "UNION", "name": "SearchResult",
                      "possibleTypes": [
                        { "kind": "OBJECT", "name": "User" },
                        { "kind": "OBJECT", "name": "Post" }
                      ]
                    },
                    {
                      "kind": "ENUM", "name": "Role",
                      "enumValues": [
                        { "name": "ADMIN", "isDeprecated": false },
                        { "name": "VIEWER", "isDeprecated": true,
                          "deprecationReason": "use ADMIN" }
                      ]
                    },
                    {
                      "kind": "INPUT_OBJECT", "name": "LoginInput",
                      "inputFields": [
                        { "name": "username",
                          "type": { "kind": "NON_NULL", "name": null,
                                    "ofType": { "kind": "SCALAR", "name": "String" } } }
                      ]
                    },
                    {
                      "kind": "OBJECT", "name": "RootQuery",
                      "fields": [
                        { "name": "me", "args": [],
                          "type": { "kind": "OBJECT", "name": "User" } },
                        { "name": "search", "args": [],
                          "type": { "kind": "UNION", "name": "SearchResult" } }
                      ]
                    },
                    {
                      "kind": "OBJECT", "name": "RootMutation",
                      "fields": [
                        { "name": "login", "args": [
                            { "name": "input",
                              "type": { "kind": "NON_NULL", "name": null,
                                        "ofType": { "kind": "INPUT_OBJECT",
                                                    "name": "LoginInput" } } }
                          ],
                          "type": { "kind": "SCALAR", "name": "String" } }
                      ]
                    },
                    { "kind": "SCALAR", "name": "String" },
                    { "kind": "SCALAR", "name": "ID" },
                    { "kind": "SCALAR", "name": "Int" }
                  ],
                  "directives": [
                    { "name": "include", "locations": ["FIELD", "INLINE_FRAGMENT"], "args": [] },
                    { "name": "cacheControl", "locations": ["NOT_A_REAL_LOCATION"], "args": [] }
                  ]
                }
              }
            }
            """;

    @Test
    void readsTheRootTypeNamesFromTheResult() {
        GripSchema schema = parse();

        assertEquals("RootQuery", schema.getQueryTypeName());
        assertEquals("RootMutation", schema.getMutationTypeName());
        assertNull(schema.getSubscriptionTypeName());
        assertEquals(2, schema.getQueries().size());
        assertEquals(1, schema.getMutations().size());
    }

    @Test
    void linksTheInterfacesATypeImplements() {
        GripType user = parse().getType("User");

        assertEquals(1, user.getInterfaces().size());
        assertEquals("Node", user.getInterfaces().get(0).getName());
        assertEquals(GripTypeKind.INTERFACE, user.getInterfaces().get(0).getKind());
    }

    @Test
    void linksTheMembersOfAUnion() {
        GripType result = parse().getType("SearchResult");

        assertEquals(2, result.getPossibleTypes().size());
        assertEquals("User", result.getPossibleTypes().get(0).getName());
        assertEquals("Post", result.getPossibleTypes().get(1).getName());
    }

    @Test
    void linksTheTypesThatImplementAnInterface() {
        assertEquals(2, parse().getType("Node").getPossibleTypes().size());
    }

    @Test
    void keepsWrappedFieldTypesAndTheirKinds() {
        GripField posts = parse().getType("User").getField("posts");

        assertEquals("Post", posts.getType().getName());
        assertEquals(GripTypeKind.OBJECT, posts.getType().getKind());
        assertTrue(posts.getType().isList());
        assertEquals("10", posts.getArgument("first").getDefaultValue());

        GripField author = parse().getType("Post").getField("author");
        assertTrue(author.getType().isNonNull());
        assertFalse(author.getType().isList());
    }

    @Test
    void keepsEnumValuesAndTheirDeprecation() {
        GripType role = parse().getType("Role");

        assertEquals(2, role.getEnumValues().size());
        assertTrue(role.getEnumValues().get(1).isDeprecated());
        assertEquals("use ADMIN", role.getEnumValues().get(1).getDeprecationReason());
    }

    @Test
    void skipsADirectiveLocationItDoesNotRecognise() {
        GripSchema schema = parse();

        assertNotNull(schema.getDirective("include"));
        assertNotNull(schema.getDirective("cacheControl"));
        assertTrue(schema.getDirective("cacheControl").getLocations().isEmpty());
    }

    @Test
    void exportsSdlThatCanBeReadBackIn() {
        String sdl = parse().toSDL();

        assertTrue(sdl.contains("type User implements Node"), sdl);
        assertTrue(sdl.contains("union SearchResult = User | Post"), sdl);
        assertFalse(sdl.contains("union SearchResult = \n"), sdl);

        assertFalse(sdl.contains("directive @cacheControl"), sdl);

        GripSchema reread = SdlSchemaReader.read(sdl, "http://x/graphql");

        assertEquals("RootQuery", reread.getQueryTypeName());
        assertEquals("RootMutation", reread.getMutationTypeName());
        assertEquals(1, reread.getType("User").getInterfaces().size());
        assertEquals(2, reread.getType("SearchResult").getPossibleTypes().size());
        assertEquals(GripTypeKind.ENUM, reread.getType("Role").getKind());
        assertEquals(GripTypeKind.INPUT_OBJECT, reread.getType("LoginInput").getKind());
    }

    @Test
    void exportsValidSdlForATypeWithNoFieldsLoaded() {
        GripSchema schema = new GripSchema();
        schema.addType(new GripType("Mystery", GripTypeKind.OBJECT));
        schema.addType(new GripType("Nothing", GripTypeKind.UNION));

        String sdl = schema.toSDL();

        assertFalse(sdl.contains("{\n}"), sdl);
        assertFalse(sdl.contains("= \n"), sdl);
        assertEquals(2, SdlSchemaReader.read(sdl, "http://x/graphql").getTypeCount());
    }

    private static GripSchema parse() {
        JsonObject schemaJson = JsonParser.parseString(RESULT).getAsJsonObject()
                .getAsJsonObject("data").getAsJsonObject("__schema");
        return IntrospectionHandler.parseIntrospectionResult(schemaJson, "http://x/graphql");
    }
}
