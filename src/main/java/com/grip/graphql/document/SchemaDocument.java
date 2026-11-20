package com.grip.graphql.document;

import java.util.ArrayList;
import java.util.List;

public record SchemaDocument(List<SchemaDefinition> definitions) {

    public SchemaDocument(List<SchemaDefinition> definitions) {
        this.definitions = definitions == null ? List.of() : List.copyOf(definitions);
    }

    public SchemaDefinition.SchemaRoots roots() {
        SchemaDefinition.SchemaRoots merged = null;

        for (SchemaDefinition definition : definitions) {
            if (!(definition instanceof SchemaDefinition.SchemaRoots roots)) {
                continue;
            }
            merged = merged == null ? roots : new SchemaDefinition.SchemaRoots(
                    merged.queryType() != null ? merged.queryType() : roots.queryType(),
                    merged.mutationType() != null ? merged.mutationType() : roots.mutationType(),
                    merged.subscriptionType() != null
                            ? merged.subscriptionType() : roots.subscriptionType(),
                    merged.directives());
        }

        return merged;
    }

    public List<SchemaDefinition.TypeDefinition> typeDefinitions() {
        List<SchemaDefinition.TypeDefinition> types = new ArrayList<>();
        for (SchemaDefinition definition : definitions) {
            if (definition instanceof SchemaDefinition.TypeDefinition type) {
                types.add(type);
            }
        }
        return types;
    }

    public List<SchemaDefinition.DirectiveDef> directiveDefinitions() {
        List<SchemaDefinition.DirectiveDef> found = new ArrayList<>();
        for (SchemaDefinition definition : definitions) {
            if (definition instanceof SchemaDefinition.DirectiveDef directive) {
                found.add(directive);
            }
        }
        return found;
    }
}
