package com.grip.graphql.event;

public abstract class GripEvent {

    private final long timestamp;
    private final String source;

    protected GripEvent(String source) {
        this.timestamp = System.currentTimeMillis();
        this.source = source;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSource() {
        return source;
    }

    public String getEventType() {
        return getClass().getSimpleName();
    }
}
