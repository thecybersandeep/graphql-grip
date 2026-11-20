package com.grip.graphql.schema;

import com.grip.graphql.http.GraphQLResponseAnalyzer;
import com.grip.graphql.http.GraphQLTransport;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class IntrospectionLadder {

    public static final String TYPENAME_PROBE = "query GripTransportProbe { __typename }";
    public static final String INTROSPECTION_PROBE =
            "query GripIntrospectionProbe { __schema { queryType { name } } }";

    public interface Sender {
        Response send(GraphQLTransport transport, String query) throws Exception;
    }

    public record Response(int statusCode, String contentType, String body) {
    }

    public record Outcome(boolean graphQL, boolean introspectionAvailable,
                          List<GraphQLTransport> transports, GraphQLTransport transport,
                          IntrospectionBypass bypass, String detail, int attempts) {

        public Outcome(boolean graphQL, boolean introspectionAvailable, List<GraphQLTransport> transports,
                       GraphQLTransport transport, IntrospectionBypass bypass, String detail, int attempts) {
            this.graphQL = graphQL;
            this.introspectionAvailable = introspectionAvailable;
            this.transports = transports == null ? List.of() : List.copyOf(transports);
            this.transport = transport;
            this.bypass = bypass;
            this.detail = detail;
            this.attempts = attempts;
        }

        public boolean bypassed() {
            return introspectionAvailable && bypass != null && bypass != IntrospectionBypass.NONE;
        }
    }

    private IntrospectionLadder() {
    }

    public static Outcome probe(Sender sender, Consumer<String> progress) {
        return probe(sender, progress, () -> false);
    }

    public static Outcome probe(Sender sender, Consumer<String> progress,
                                BooleanSupplier cancelled) {
        List<GraphQLTransport> confirmed = new ArrayList<>();
        String detail = null;
        int attempts = 0;

        for (GraphQLTransport transport : GraphQLTransport.values()) {
            if (cancelled.getAsBoolean()) {
                return new Outcome(false, false, List.of(), null, null,
                        "Cancelled before any transport was confirmed", attempts);
            }
            attempts++;
            report(progress, "Probing " + transport.description());

            Response response = send(sender, transport, TYPENAME_PROBE, progress);
            if (response == null) {
                continue;
            }

            GraphQLResponseAnalyzer.Analysis analysis = GraphQLResponseAnalyzer.analyzeEndpointProbe(
                    response.statusCode(), response.contentType(), response.body());

            if (analysis.isGraphQL()) {
                confirmed.add(transport);
                report(progress, "GraphQL confirmed over " + transport.description());
            } else {
                detail = analysis.getDetail();
                report(progress, transport.description() + " rejected: " + analysis.getDetail());
            }
        }

        if (confirmed.isEmpty()) {
            return new Outcome(false, false, List.of(), null, null, detail, attempts);
        }

        for (GraphQLTransport transport : confirmed) {
            for (IntrospectionBypass bypass : IntrospectionBypass.values()) {
                if (cancelled.getAsBoolean()) {
                    return new Outcome(true, false, confirmed, confirmed.get(0), null,
                            "Cancelled before introspection was confirmed", attempts);
                }
                attempts++;
                report(progress, "Introspection over " + transport.description()
                        + " with " + bypass.description());

                Response response = send(sender, transport, bypass.apply(INTROSPECTION_PROBE), progress);
                if (response == null) {
                    continue;
                }

                GraphQLResponseAnalyzer.Analysis analysis =
                        GraphQLResponseAnalyzer.analyzeIntrospectionResponse(
                                response.statusCode(), response.contentType(), response.body());

                if (analysis.isExpectedFieldAvailable()) {
                    return new Outcome(true, true, confirmed, transport, bypass,
                            analysis.getDetail(), attempts);
                }
                detail = analysis.getDetail();
            }
        }

        return new Outcome(true, false, confirmed, confirmed.get(0), null, detail, attempts);
    }

    private static Response send(Sender sender, GraphQLTransport transport, String query,
                                 Consumer<String> progress) {
        try {
            return sender.send(transport, query);
        } catch (Exception e) {
            report(progress, transport.description() + " probe failed: " + e.getMessage());
            return null;
        }
    }

    private static void report(Consumer<String> progress, String message) {
        if (progress != null) {
            progress.accept(message);
        }
    }
}
