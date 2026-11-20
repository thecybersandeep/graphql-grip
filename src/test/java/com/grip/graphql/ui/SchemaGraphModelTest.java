package com.grip.graphql.ui;

import com.grip.graphql.model.schema.GripSchema;
import com.grip.graphql.schema.SdlSchemaReader;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SchemaGraphModelTest {

    private static final String SDL = """
            schema {
              query: RootQuery
              mutation: RootMutation
            }

            scalar DateTime

            interface Node {
              id: ID!
            }

            enum Role {
              ADMIN
              VIEWER
            }

            input PostFilter {
              since: DateTime
              roles: [Role!]
            }

            input LoginInput {
              username: String!
            }

            type User implements Node {
              id: ID!
              role: Role!
              posts(filter: PostFilter): [Post!]!
              manager: User
            }

            type Post implements Node {
              id: ID!
              author: User!
              publishedAt: DateTime
            }

            type LoginPayload {
              token: String
              user: User
            }

            union SearchResult = User | Post

            type Subscription {
              ignored: String
            }

            type RootQuery {
              me: User
              search(term: String!): [SearchResult!]!
            }

            type RootMutation {
              login(input: LoginInput!): LoginPayload!
            }
            """;

    @Test
    void drawsAnEdgeForEveryKindOfTypeReference() {
        SchemaGraphModel graph = build(null);

        assertTrue(hasEdge(graph, "RootQuery", "User", SchemaGraphModel.EdgeKind.FIELD));
        assertTrue(hasEdge(graph, "User", "Post", SchemaGraphModel.EdgeKind.FIELD));
        assertTrue(hasEdge(graph, "User", "Role", SchemaGraphModel.EdgeKind.FIELD));
        assertTrue(hasEdge(graph, "User", "PostFilter", SchemaGraphModel.EdgeKind.ARGUMENT));
        assertTrue(hasEdge(graph, "RootMutation", "LoginInput", SchemaGraphModel.EdgeKind.ARGUMENT));
        assertTrue(hasEdge(graph, "PostFilter", "Role", SchemaGraphModel.EdgeKind.FIELD));
        assertTrue(hasEdge(graph, "PostFilter", "DateTime", SchemaGraphModel.EdgeKind.FIELD));
        assertTrue(hasEdge(graph, "User", "Node", SchemaGraphModel.EdgeKind.INHERITS));
        assertTrue(hasEdge(graph, "SearchResult", "User", SchemaGraphModel.EdgeKind.MEMBER));
        assertTrue(hasEdge(graph, "SearchResult", "Post", SchemaGraphModel.EdgeKind.MEMBER));
    }

    @Test
    void leavesNoTypeStrandedWithoutAnEdge() {
        SchemaGraphModel graph = build(null);

        Set<String> connected = new LinkedHashSet<>();
        for (SchemaGraphModel.Edge edge : graph.edges()) {
            connected.add(edge.source.name);
            connected.add(edge.target.name);
        }

        for (SchemaGraphModel.Node node : graph.nodes()) {
            if ("Subscription".equals(node.name)) {
                continue;
            }
            assertTrue(connected.contains(node.name), node.name + " has no edge");
        }
    }

    @Test
    void keepsASelfReferenceAsItsOwnEdge() {
        assertTrue(hasEdge(build(null), "User", "User", SchemaGraphModel.EdgeKind.FIELD));
    }

    @Test
    void drawsOneEdgePerTypePairAndKind() {
        GripSchema schema = SdlSchemaReader.read("""
                type Query { a: Thing b: Thing c: [Thing!]! }
                type Thing { id: ID! }
                """, "http://x/graphql");

        SchemaGraphModel graph = new SchemaGraphModel();
        graph.build(schema, null, 400);

        assertEquals(1, countEdges(graph, "Query", "Thing"));
    }

    @Test
    void excludesIntrospectionTypes() {
        for (SchemaGraphModel.Node node : build(null).nodes()) {
            assertFalse(node.name.startsWith("__"), node.name);
        }
    }

    @Test
    void putsTheDeclaredRootTypesInTheFirstLayer() {
        SchemaGraphModel graph = build(null);

        assertEquals(0, graph.node("RootQuery").depth);
        assertEquals(0, graph.node("RootMutation").depth);
        assertEquals(1, graph.node("User").depth);
        assertEquals(1, graph.node("LoginPayload").depth);
    }

    @Test
    void treatsATypeNamedSubscriptionAsARootOnlyWhenTheSchemaSaysSo() {
        SchemaGraphModel graph = build(null);

        assertEquals(List.of("RootQuery", "RootMutation"),
                SchemaGraphModel.rootTypeNames(schema()));
        assertTrue(graph.node("Subscription").depth > 0);
    }

    @Test
    void neverNamesARootTypeTheSchemaDoesNotHave() {
        GripSchema schema = SdlSchemaReader.read("type Widget { id: ID! }", "http://x/graphql");
        assertTrue(SchemaGraphModel.rootTypeNames(schema).isEmpty());
    }

    @Test
    void laysDeeperLayersFurtherRight() {
        SchemaGraphModel graph = build(null);

        double roots = graph.node("RootQuery").x;
        double firstHop = graph.node("User").x;

        assertTrue(roots < firstHop, roots + " should be left of " + firstHop);
        assertEquals(graph.node("RootQuery").x, graph.node("RootMutation").x, 0.001);
    }

    @Test
    void givesEveryNodeItsOwnPosition() {
        SchemaGraphModel graph = build(null);

        Set<String> seen = new HashSet<>();
        for (SchemaGraphModel.Node node : graph.nodes()) {
            assertTrue(seen.add(node.x + ":" + node.y), node.name + " overlaps another node");
        }
    }

    @Test
    void placesEveryNodeInsideTheMargin() {
        for (SchemaGraphModel.Node node : build(null).nodes()) {
            assertTrue(node.x >= SchemaGraphModel.MARGIN, node.name);
            assertTrue(node.y >= SchemaGraphModel.MARGIN, node.name);
        }
    }

    @Test
    void placesAnOrphanTypeInTheLastLayer() {
        SchemaGraphModel graph = build(null);

        int deepest = 0;
        for (SchemaGraphModel.Node node : graph.nodes()) {
            deepest = Math.max(deepest, node.depth);
        }
        assertEquals(deepest, graph.node("Subscription").depth);
    }

    @Test
    void focusKeepsTheTypesAroundTheChosenOne() {
        SchemaGraphModel graph = build("Post");

        assertNotNull(graph.node("Post"));
        assertNotNull(graph.node("User"));
        assertNotNull(graph.node("Node"));
        assertNotNull(graph.node("DateTime"));
        assertNotNull(graph.node("SearchResult"));
        assertNotNull(graph.node("RootQuery"));
        assertNull(graph.node("Subscription"));
        assertEquals("Post", graph.focusName());
        assertTrue(graph.hiddenTypeCount() > 0);
    }

    @Test
    void focusReachesTypesThatPointAtTheChosenOne() {
        SchemaGraphModel graph = build("LoginInput");

        assertNotNull(graph.node("RootMutation"));
        assertNotNull(graph.node("LoginPayload"));
        assertNull(graph.node("User"));
        assertTrue(graph.hiddenTypeCount() > 0);
    }

    @Test
    void focusOnAnUnknownTypeFallsBackToTheWholeSchema() {
        SchemaGraphModel graph = build("DoesNotExist");

        assertEquals(12, graph.nodes().size());
        assertEquals(0, graph.hiddenTypeCount());
    }

    @Test
    void aSchemaLargerThanTheCapFocusesOnTheQueryRootInsteadOfDrawingNothing() {
        StringBuilder sdl = new StringBuilder("type Query { entry: Thing0 }\n");
        for (int i = 0; i < 500; i++) {
            sdl.append("type Thing").append(i).append(" { id: ID! }\n");
        }

        SchemaGraphModel graph = new SchemaGraphModel();
        graph.build(SdlSchemaReader.read(sdl.toString(), "http://x/graphql"), null, 400);

        assertFalse(graph.isEmpty());
        assertEquals("Query", graph.focusName());
        assertNotNull(graph.node("Query"));
        assertNotNull(graph.node("Thing0"));
        assertTrue(graph.hiddenTypeCount() > 400);
    }

    @Test
    void aCappedSchemaWithNoRootStillDrawsSomething() {
        StringBuilder sdl = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sdl.append("type Thing").append(i).append(" { id: ID! }\n");
        }

        SchemaGraphModel graph = new SchemaGraphModel();
        graph.build(SdlSchemaReader.read(sdl.toString(), "http://x/graphql"), null, 400);

        assertEquals(400, graph.nodes().size());
        assertEquals(100, graph.hiddenTypeCount());
    }

    @Test
    void anEmptySchemaProducesAnEmptyGraph() {
        SchemaGraphModel graph = new SchemaGraphModel();
        graph.build(null, null, 400);

        assertTrue(graph.isEmpty());
        assertTrue(graph.edges().isEmpty());
        assertNull(graph.node("anything"));
    }

    @Test
    void rebuildingReplacesTheGraphRatherThanAppendingToIt() {
        SchemaGraphModel graph = new SchemaGraphModel();
        graph.build(schema(), null, 400);
        int nodeCount = graph.nodes().size();
        int edgeCount = graph.edges().size();

        graph.build(schema(), null, 400);

        assertEquals(nodeCount, graph.nodes().size());
        assertEquals(edgeCount, graph.edges().size());
    }

    private static GripSchema schema() {
        return SdlSchemaReader.read(SDL, "http://x/graphql");
    }

    private static SchemaGraphModel build(String focus) {
        SchemaGraphModel graph = new SchemaGraphModel();
        graph.build(schema(), focus, 400);
        return graph;
    }

    private static boolean hasEdge(SchemaGraphModel graph, String source, String target,
                                   SchemaGraphModel.EdgeKind kind) {
        for (SchemaGraphModel.Edge edge : graph.edges()) {
            if (edge.source.name.equals(source) && edge.target.name.equals(target)
                    && edge.kind == kind) {
                return true;
            }
        }
        return false;
    }

    private static int countEdges(SchemaGraphModel graph, String source, String target) {
        int count = 0;
        for (SchemaGraphModel.Edge edge : graph.edges()) {
            if (edge.source.name.equals(source) && edge.target.name.equals(target)) {
                count++;
            }
        }
        return count;
    }
}
