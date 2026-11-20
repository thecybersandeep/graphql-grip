package com.grip.graphql.ui;

import com.grip.graphql.model.schema.GripArgument;
import com.grip.graphql.model.schema.GripField;
import com.grip.graphql.model.schema.GripSchema;
import com.grip.graphql.model.schema.GripType;
import com.grip.graphql.model.schema.GripTypeRef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class SchemaGraphModel {

    static final int NODE_WIDTH = 140;
    static final int NODE_HEIGHT = 36;
    static final int MARGIN = 80;

    private static final int HORIZONTAL_GAP = 200;
    private static final int VERTICAL_GAP = 100;
    private static final int MAX_LAYER_HEIGHT = 24;
    private static final int FOCUS_DEPTH = 2;
    private static final int ORDERING_PASSES = 4;

    enum EdgeKind {
        FIELD, ARGUMENT, INHERITS, MEMBER
    }

    static final class Node {

        final String name;
        final GripType type;
        double x;
        double y;
        int depth;

        Node(String name, GripType type) {
            this.name = name;
            this.type = type;
        }
    }

    static final class Edge {

        final Node source;
        final Node target;
        final EdgeKind kind;

        Edge(Node source, Node target, EdgeKind kind) {
            this.source = source;
            this.target = target;
            this.kind = kind;
        }
    }

    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final Map<String, Node> byName = new LinkedHashMap<>();

    private String focusName;
    private int hiddenTypeCount;

    List<Node> nodes() {
        return nodes;
    }

    List<Edge> edges() {
        return edges;
    }

    Node node(String name) {
        return name == null ? null : byName.get(name);
    }

    String focusName() {
        return focusName;
    }

    int hiddenTypeCount() {
        return hiddenTypeCount;
    }

    boolean isEmpty() {
        return nodes.isEmpty();
    }

    void clear() {
        nodes.clear();
        edges.clear();
        byName.clear();
        focusName = null;
        hiddenTypeCount = 0;
    }

    void build(GripSchema schema, String focus, int maxNodes) {
        nodes.clear();
        edges.clear();
        byName.clear();
        focusName = focus;
        hiddenTypeCount = 0;

        if (schema == null) {
            return;
        }

        Map<String, GripType> visible = selectVisibleTypes(schema, maxNodes);
        if (visible.isEmpty()) {
            return;
        }

        for (GripType type : visible.values()) {
            Node node = new Node(type.getName(), type);
            nodes.add(node);
            byName.put(node.name, node);
        }

        buildEdges(visible);
        layoutByDepth(schema);
    }

    private Map<String, GripType> selectVisibleTypes(GripSchema schema, int maxNodes) {
        Map<String, GripType> all = new LinkedHashMap<>();
        for (GripType type : schema.getAllTypes()) {
            if (!type.getName().startsWith("__")) {
                all.put(type.getName(), type);
            }
        }

        if (focusName != null && all.containsKey(focusName)) {
            return narrowTo(all, focusName);
        }

        if (all.size() <= maxNodes) {
            return all;
        }

        for (String root : rootTypeNames(schema)) {
            if (all.containsKey(root)) {
                focusName = root;
                return narrowTo(all, root);
            }
        }

        Map<String, GripType> capped = new LinkedHashMap<>();
        for (Map.Entry<String, GripType> entry : all.entrySet()) {
            if (capped.size() >= maxNodes) {
                break;
            }
            capped.put(entry.getKey(), entry.getValue());
        }
        hiddenTypeCount = all.size() - capped.size();
        return capped;
    }

    private Map<String, GripType> narrowTo(Map<String, GripType> all, String start) {
        Map<String, GripType> found = new LinkedHashMap<>();
        found.put(start, all.get(start));

        Set<String> frontier = new LinkedHashSet<>();
        frontier.add(start);

        for (int level = 0; level < FOCUS_DEPTH && !frontier.isEmpty(); level++) {
            Set<String> next = new LinkedHashSet<>();

            for (String name : frontier) {
                for (String reference : referencesOf(all.get(name))) {
                    if (all.containsKey(reference) && !found.containsKey(reference)) {
                        found.put(reference, all.get(reference));
                        next.add(reference);
                    }
                }
            }

            for (Map.Entry<String, GripType> entry : all.entrySet()) {
                if (found.containsKey(entry.getKey())) {
                    continue;
                }
                for (String reference : referencesOf(entry.getValue())) {
                    if (frontier.contains(reference)) {
                        found.put(entry.getKey(), entry.getValue());
                        next.add(entry.getKey());
                        break;
                    }
                }
            }

            frontier = next;
        }

        hiddenTypeCount = all.size() - found.size();
        return found;
    }

    static List<String> rootTypeNames(GripSchema schema) {
        List<String> roots = new ArrayList<>();
        for (String name : new String[]{schema.getQueryTypeName(), schema.getMutationTypeName(),
                schema.getSubscriptionTypeName()}) {
            if (name != null && schema.hasType(name) && !roots.contains(name)) {
                roots.add(name);
            }
        }
        return roots;
    }

    static Set<String> referencesOf(GripType type) {
        Set<String> references = new LinkedHashSet<>();
        if (type == null) {
            return references;
        }

        for (GripField field : type.getFields()) {
            addReference(references, field.getType());
            for (GripArgument argument : field.getArguments()) {
                addReference(references, argument.getType());
            }
        }
        for (GripField field : type.getInputFields()) {
            addReference(references, field.getType());
        }
        for (GripType iface : type.getInterfaces()) {
            references.add(iface.getName());
        }
        for (GripType member : type.getPossibleTypes()) {
            references.add(member.getName());
        }

        references.remove(type.getName());
        return references;
    }

    private static void addReference(Set<String> references, GripTypeRef reference) {
        if (reference != null && reference.getName() != null
                && !reference.getName().startsWith("__")) {
            references.add(reference.getName());
        }
    }

    private void buildEdges(Map<String, GripType> visible) {
        Set<String> seen = new HashSet<>();

        for (GripType type : visible.values()) {
            Node source = byName.get(type.getName());
            if (source == null) {
                continue;
            }

            for (GripField field : type.getFields()) {
                link(seen, source, field.getType(), EdgeKind.FIELD);
                for (GripArgument argument : field.getArguments()) {
                    link(seen, source, argument.getType(), EdgeKind.ARGUMENT);
                }
            }
            for (GripField field : type.getInputFields()) {
                link(seen, source, field.getType(), EdgeKind.FIELD);
            }
            for (GripType iface : type.getInterfaces()) {
                link(seen, source, iface.getName(), EdgeKind.INHERITS);
            }
            for (GripType member : type.getPossibleTypes()) {
                link(seen, source, member.getName(), EdgeKind.MEMBER);
            }
        }
    }

    private void link(Set<String> seen, Node source, GripTypeRef reference, EdgeKind kind) {
        if (reference != null) {
            link(seen, source, reference.getName(), kind);
        }
    }

    private void link(Set<String> seen, Node source, String targetName, EdgeKind kind) {
        Node target = byName.get(targetName);
        if (target == null) {
            return;
        }
        if (!seen.add(source.name + '\u0000' + targetName + '\u0000' + kind)) {
            return;
        }
        edges.add(new Edge(source, target, kind));
    }

    private void layoutByDepth(GripSchema schema) {
        assignDepths(schema);

        Map<Integer, List<Node>> layers = new TreeMap<>();
        for (Node node : nodes) {
            layers.computeIfAbsent(node.depth, key -> new ArrayList<>()).add(node);
        }
        for (List<Node> layer : layers.values()) {
            layer.sort(Comparator.comparing(node -> node.name));
        }

        placeLayers(layers);
        for (int pass = 0; pass < ORDERING_PASSES; pass++) {
            orderByBarycentre(layers);
            placeLayers(layers);
        }
    }

    private void assignDepths(GripSchema schema) {
        for (Node node : nodes) {
            node.depth = -1;
        }

        Map<String, List<Node>> outgoing = new HashMap<>();
        for (Edge edge : edges) {
            outgoing.computeIfAbsent(edge.source.name, key -> new ArrayList<>()).add(edge.target);
        }

        Deque<Node> queue = new ArrayDeque<>();
        for (String root : rootTypeNames(schema)) {
            Node node = byName.get(root);
            if (node != null && node.depth < 0) {
                node.depth = 0;
                queue.add(node);
            }
        }

        if (queue.isEmpty()) {
            Node busiest = null;
            int best = -1;
            for (Node node : nodes) {
                int degree = outgoing.getOrDefault(node.name, List.of()).size();
                if (degree > best) {
                    best = degree;
                    busiest = node;
                }
            }
            if (busiest != null) {
                busiest.depth = 0;
                queue.add(busiest);
            }
        }

        while (!queue.isEmpty()) {
            Node current = queue.removeFirst();
            for (Node next : outgoing.getOrDefault(current.name, List.of())) {
                if (next.depth < 0) {
                    next.depth = current.depth + 1;
                    queue.add(next);
                }
            }
        }

        int deepest = 0;
        for (Node node : nodes) {
            deepest = Math.max(deepest, node.depth);
        }
        for (Node node : nodes) {
            if (node.depth < 0) {
                node.depth = deepest + 1;
            }
        }
    }

    private void placeLayers(Map<Integer, List<Node>> layers) {
        double columnStep = NODE_WIDTH + HORIZONTAL_GAP / 3.0;
        double rowStep = NODE_HEIGHT + VERTICAL_GAP / 2.0;
        double x = MARGIN + NODE_WIDTH / 2.0;

        for (List<Node> layer : layers.values()) {
            int rows = Math.min(MAX_LAYER_HEIGHT, layer.size());
            int columns = (layer.size() + rows - 1) / rows;

            for (int i = 0; i < layer.size(); i++) {
                Node node = layer.get(i);
                node.x = x + (i / rows) * columnStep;
                node.y = MARGIN + (i % rows) * rowStep + NODE_HEIGHT / 2.0;
            }

            x += columns * columnStep + HORIZONTAL_GAP * 2.0 / 3.0;
        }
    }

    private void orderByBarycentre(Map<Integer, List<Node>> layers) {
        Map<String, List<Node>> incoming = new HashMap<>();
        for (Edge edge : edges) {
            incoming.computeIfAbsent(edge.target.name, key -> new ArrayList<>()).add(edge.source);
        }

        for (Map.Entry<Integer, List<Node>> entry : layers.entrySet()) {
            if (entry.getKey() == 0) {
                continue;
            }

            Map<String, Double> weights = new HashMap<>();
            for (Node node : entry.getValue()) {
                double total = 0;
                int counted = 0;
                for (Node source : incoming.getOrDefault(node.name, List.of())) {
                    if (source.depth < node.depth) {
                        total += source.y;
                        counted++;
                    }
                }
                weights.put(node.name, counted == 0 ? node.y : total / counted);
            }

            entry.getValue().sort(Comparator
                    .<Node, Double>comparing(node -> weights.get(node.name))
                    .thenComparing(node -> node.name));
        }
    }
}
