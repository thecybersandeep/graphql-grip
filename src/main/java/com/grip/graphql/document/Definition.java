package com.grip.graphql.document;

public sealed interface Definition permits OperationDefinition, FragmentDefinition {
}
