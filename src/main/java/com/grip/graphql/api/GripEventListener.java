package com.grip.graphql.api;

import com.grip.graphql.event.GripEvent;

@FunctionalInterface
public interface GripEventListener<T extends GripEvent> {

    void onEvent(T event);
}
