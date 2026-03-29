package com.grip.graphql.model.schema;

import java.util.*;

public class GripSchema {

    private String queryTypeName;
    private String mutationTypeName;
    private String subscriptionTypeName;

    private final Map<String, GripType> types;
    private final List<GripDirective> directives;

    private String sourceEndpoint;
    private String sdlCache;
    private final long createdAt;
    private boolean isPartial;
    private boolean isReconstructed;

    public GripSchema() {
        this.types = new LinkedHashMap<>();
        this.directives = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.isPartial = false;
    }

    public String getQueryTypeName() {
        return queryTypeName;
    }

    public void setQueryTypeName(String name) {
        this.queryTypeName = name;
        this.sdlCache = null;
    }

    public String getMutationTypeName() {
        return mutationTypeName;
    }

    public void setMutationTypeName(String name) {
        this.mutationTypeName = name;
        this.sdlCache = null;
    }

    public String getSubscriptionTypeName() {
        return subscriptionTypeName;
    }

    public void setSubscriptionTypeName(String name) {
        this.subscriptionTypeName = name;
        this.sdlCache = null;
    }

    public GripType getQueryType() {
        return queryTypeName != null ? types.get(queryTypeName) : null;
    }

    public GripType getMutationType() {
        return mutationTypeName != null ? types.get(mutationTypeName) : null;
    }

    public GripType getSubscriptionType() {
        return subscriptionTypeName != null ? types.get(subscriptionTypeName) : null;
    }

    public void addType(GripType type) {
        types.put(type.getName(), type);
        sdlCache = null;
    }

    public GripType getType(String name) {
        return types.get(name);
    }

    public boolean hasType(String name) {
        return types.containsKey(name);
    }

    public Collection<GripType> getAllTypes() {
        return Collections.unmodifiableCollection(types.values());
    }

    public Set<String> getTypeNames() {
        return Collections.unmodifiableSet(types.keySet());
    }

    public List<GripType> getTypesByKind(GripTypeKind kind) {
        List<GripType> result = new ArrayList<>();
        for (GripType type : types.values()) {
            if (type.getKind() == kind) {
                result.add(type);
            }
        }
        return result;
    }

    public List<GripType> getUserTypes() {
        List<GripType> result = new ArrayList<>();
        for (GripType type : types.values()) {
            if (!type.isIntrospectionType() && !type.isBuiltInScalar()) {
                result.add(type);
            }
        }
        return result;
    }

    public List<GripField> getQueries() {
        GripType queryType = getQueryType();
        return queryType != null ? queryType.getFields() : Collections.emptyList();
    }

    public List<GripField> getMutations() {
        GripType mutationType = getMutationType();
        return mutationType != null ? mutationType.getFields() : Collections.emptyList();
    }

    public List<GripField> getSubscriptions() {
        GripType subscriptionType = getSubscriptionType();
        return subscriptionType != null ? subscriptionType.getFields() : Collections.emptyList();
    }

    public void addDirective(GripDirective directive) {
        directives.add(directive);
        this.sdlCache = null;
    }

    public List<GripDirective> getDirectives() {
        return Collections.unmodifiableList(directives);
    }

    public GripDirective getDirective(String name) {
        for (GripDirective directive : directives) {
            if (directive.getName().equals(name)) {
                return directive;
            }
        }
        return null;
    }

    public String getSourceEndpoint() {
        return sourceEndpoint;
    }

    public void setSourceEndpoint(String endpoint) {
        this.sourceEndpoint = endpoint;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isPartial() {
        return isPartial;
    }

    public void setPartial(boolean partial) {
        this.isPartial = partial;
    }

    public boolean isReconstructed() {
        return isReconstructed;
    }

    public void setReconstructed(boolean reconstructed) {
        this.isReconstructed = reconstructed;
    }

    public int getTypeCount() {
        return types.size();
    }

    public int getTotalFieldCount() {
        int count = 0;
        for (GripType type : types.values()) {
            count += type.getFields().size();
        }
        return count;
    }

    public List<GripType> getUnexploredTypes() {
        List<GripType> unexplored = new ArrayList<>();
        for (GripType type : types.values()) {
            if (type.getKind() == GripTypeKind.OBJECT &&
                !type.hasFields() &&
                !type.isBuiltInScalar() &&
                !type.isIntrospectionType()) {
                unexplored.add(type);
            }
        }
        return unexplored;
    }

    public List<String> findPathToType(String targetTypeName) {

        Queue<List<String>> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        if (queryTypeName != null) {
            queue.add(new ArrayList<>(List.of(queryTypeName)));
        }

        while (!queue.isEmpty()) {
            List<String> path = queue.poll();
            String currentTypeName = path.get(path.size() - 1);

            if (currentTypeName.equals(targetTypeName)) {
                return path;
            }

            if (visited.contains(currentTypeName)) {
                continue;
            }
            visited.add(currentTypeName);

            GripType currentType = types.get(currentTypeName);
            if (currentType == null) continue;

            for (GripField field : currentType.getFields()) {
                GripTypeRef fieldType = field.getType();
                if (fieldType != null && !visited.contains(fieldType.getName())) {
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(field.getName());
                    newPath.add(fieldType.getName());
                    queue.add(newPath);
                }
            }
        }

        return Collections.emptyList();
    }

    public String toSDL() {
        if (sdlCache != null) {
            return sdlCache;
        }

        StringBuilder sb = new StringBuilder();

        sb.append("schema {\n");
        if (queryTypeName != null) {
            sb.append("  query: ").append(queryTypeName).append("\n");
        }
        if (mutationTypeName != null) {
            sb.append("  mutation: ").append(mutationTypeName).append("\n");
        }
        if (subscriptionTypeName != null) {
            sb.append("  subscription: ").append(subscriptionTypeName).append("\n");
        }
        sb.append("}\n\n");

        for (GripType type : types.values()) {
            if (!type.isIntrospectionType() && !type.isBuiltInScalar()) {
                sb.append(type.toSDL()).append("\n\n");
            }
        }

        for (GripDirective directive : directives) {
            if (!directive.isBuiltIn()) {
                sb.append(directive.toGraphQLString()).append("\n");
            }
        }

        sdlCache = sb.toString();
        return sdlCache;
    }

    @Override
    public String toString() {
        return String.format("GripSchema[types=%d, queries=%d, mutations=%d, partial=%b]",
                types.size(), getQueries().size(), getMutations().size(), isPartial);
    }
}
