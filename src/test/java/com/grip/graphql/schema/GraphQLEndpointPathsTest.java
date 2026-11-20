package com.grip.graphql.schema;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLEndpointPathsTest {

    @Test
    void containsNoDuplicates() {
        List<String> paths = GraphQLEndpointPaths.all();
        Set<String> unique = new HashSet<>(paths);

        assertEquals(paths.size(), unique.size());
    }

    @Test
    void everyPathIsRelativeAndUsable() {
        for (String path : GraphQLEndpointPaths.all()) {
            assertFalse(path.isBlank());
            assertFalse(path.startsWith("/"), path);
            assertFalse(path.endsWith("/"), path);
            assertFalse(path.contains(" "), path);
            assertFalse(path.contains("?"), path);
        }
    }

    @Test
    void coversTheCommonMountPoints() {
        List<String> paths = GraphQLEndpointPaths.all();

        for (String expected : List.of("graphql", "graphiql", "api", "api/graphql",
                "gql", "query", "v1/graphql", "playground")) {
            assertTrue(paths.contains(expected), expected);
        }
    }

    @Test
    void theListCannotBeModifiedByCallers() {
        assertThrows(UnsupportedOperationException.class,
                () -> GraphQLEndpointPaths.all().add("injected"));
    }
}
