package com.grip.graphql.document;

public sealed interface Selection permits Field, FragmentSpread, InlineFragment {
}
