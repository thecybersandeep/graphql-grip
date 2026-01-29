package com.grip.graphql.event;

import com.grip.graphql.api.GripEventListener;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class GripEventBus {

    private final Map<Class<? extends GripEvent>, Set<GripEventListener<?>>> listeners;
    private final ExecutorService executor;
    private volatile boolean shutdown = false;
    private Consumer<String> errorLogger;

    public GripEventBus() {
        this(4);
    }

    public GripEventBus(int threadPoolSize) {
        this.listeners = new ConcurrentHashMap<>();
        this.executor = Executors.newFixedThreadPool(threadPoolSize, r -> {
            Thread t = new Thread(r, "GripEventBus-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    public void setErrorLogger(Consumer<String> logger) {
        this.errorLogger = logger;
    }

    private void logError(String message) {
        if (errorLogger != null) {
            errorLogger.accept(message);
        }
    }

    public <T extends GripEvent> void subscribe(Class<T> eventType, GripEventListener<T> listener) {
        if (shutdown) {
            throw new IllegalStateException("EventBus is shutdown");
        }
        listeners.computeIfAbsent(eventType, k -> ConcurrentHashMap.newKeySet()).add(listener);
    }

    public <T extends GripEvent> void unsubscribe(Class<T> eventType, GripEventListener<T> listener) {
        Set<GripEventListener<?>> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            eventListeners.remove(listener);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends GripEvent> void publish(T event) {
        if (shutdown) {
            return;
        }

        Set<GripEventListener<?>> eventListeners = listeners.get(event.getClass());
        if (eventListeners != null && !eventListeners.isEmpty()) {
            for (GripEventListener<?> listener : eventListeners) {
                executor.submit(() -> {
                    try {
                        ((GripEventListener<T>) listener).onEvent(event);
                    } catch (Exception e) {

                        logError("[GripEventBus] Error in listener: " + e.getMessage());
                    }
                });
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends GripEvent> boolean publishAndWait(T event, long timeout, TimeUnit unit) {
        if (shutdown) {
            return false;
        }

        Set<GripEventListener<?>> eventListeners = listeners.get(event.getClass());
        if (eventListeners == null || eventListeners.isEmpty()) {
            return true;
        }

        CountDownLatch latch = new CountDownLatch(eventListeners.size());
        for (GripEventListener<?> listener : eventListeners) {
            executor.submit(() -> {
                try {
                    ((GripEventListener<T>) listener).onEvent(event);
                } catch (Exception e) {
                    logError("[GripEventBus] Error in listener: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            return latch.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public int getListenerCount(Class<? extends GripEvent> eventType) {
        Set<GripEventListener<?>> eventListeners = listeners.get(eventType);
        return eventListeners != null ? eventListeners.size() : 0;
    }

    public void shutdown() {
        shutdown = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        listeners.clear();
    }
}
