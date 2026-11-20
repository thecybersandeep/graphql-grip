package com.grip.graphql.schema;

import java.util.List;

public final class GraphQLEndpointPaths {

    private static final List<String> PATHS = List.of(
            "graphql", "graphiql", "playground", "altair", "explorer",
            "graphql/console", "graphql-explorer", "subscriptions", "graph",
            "api", "api/graphql", "api/gql", "api/data", "api/query",
            "api/v1", "api/v2", "api/v1/graphql", "api/v2/graphql", "api/v3/graphql",
            "graphql/api", "graphql/graphql",
            "graphiql.css", "graphiql/finland", "graphiql.js", "graphiql.min.css",
            "graphiql.min.js", "graphiql.php", "graphql.php",
            "graphql/schema.json", "graphql/schema.xml", "graphql/schema.yaml",
            "v1/graphql", "v1/graphiql", "v1/playground", "v1/altair", "v1/explorer",
            "v1/graphql/console", "v1/graphql-explorer", "v1/api/graphql", "v1/graph",
            "v2/graphql", "v2/graphiql", "v2/playground", "v2/altair", "v2/explorer",
            "v2/graphql/console", "v2/graphql-explorer", "v2/api/graphql", "v2/graph",
            "v3/graphql", "v3/graphiql", "v3/playground", "v3/api/graphql",
            "v4/graphql", "v4/graphiql", "v4/playground", "v4/api/graphql",
            "query", "gql", "data",
            "console", "dev/graphql", "test/graphql", "staging/graphql",
            "__graphql", "_graphql", "graphql-api", "graphql/v1", "graphql/v2");

    private GraphQLEndpointPaths() {
    }

    public static List<String> all() {
        return PATHS;
    }
}
