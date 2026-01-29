package com.grip.graphql;

import burp.api.montoya.MontoyaApi;
import com.grip.graphql.api.GripModule;
import com.grip.graphql.event.GripEventBus;
import com.grip.graphql.http.GripHttpClient;
import com.grip.graphql.ui.GripMainTab;
import com.grip.graphql.ui.GripTheme;
import com.grip.graphql.ui.GripContextMenu;
import com.grip.graphql.editor.GripRequestEditorProvider;

import java.util.ArrayList;
import java.util.List;

public class GripCore {

    private final MontoyaApi api;
    private final GripEventBus eventBus;
    private final GripConfig config;
    private final GripTheme theme;
    private final List<GripModule> modules;
    private GripMainTab mainTab;
    private GripHttpClient httpClient;

    public GripCore(MontoyaApi api) {
        this.api = api;
        this.eventBus = new GripEventBus();
        this.eventBus.setErrorLogger(msg -> api.logging().logToError(msg));
        this.config = GripConfig.getInstance(api);
        this.theme = GripTheme.getInstance(api);
        this.modules = new ArrayList<>();
    }

    public void initialize() {

        httpClient = new GripHttpClient(api);

        for (GripModule module : modules) {
            try {
                module.initialize(api, eventBus);
                api.logging().logToOutput("[GraphQL Grip] Module initialized: " + module.getModuleName());
            } catch (Exception e) {
                api.logging().logToError("[GraphQL Grip] Failed to initialize module " +
                        module.getModuleName() + ": " + e.getMessage());
            }
        }

        mainTab = new GripMainTab(this);
        api.userInterface().registerSuiteTab("GraphQL Grip", mainTab);

        api.userInterface().registerContextMenuItemsProvider(new GripContextMenu(this));

        api.userInterface().registerHttpRequestEditorProvider(new GripRequestEditorProvider(api, this));
        api.logging().logToOutput("[GraphQL Grip] Repeater tab registered");

        api.logging().logToOutput("[GraphQL Grip] Core initialization complete");
    }

    public void registerModule(GripModule module) {
        modules.add(module);
    }

    public void shutdown() {
        api.logging().logToOutput("[GraphQL Grip] Shutting down...");

        for (int i = modules.size() - 1; i >= 0; i--) {
            GripModule module = modules.get(i);
            try {
                module.shutdown();
                api.logging().logToOutput("[GraphQL Grip] Module shutdown: " + module.getModuleName());
            } catch (Exception e) {
                api.logging().logToError("[GraphQL Grip] Error shutting down module " +
                        module.getModuleName() + ": " + e.getMessage());
            }
        }

        eventBus.shutdown();

        api.logging().logToOutput("[GraphQL Grip] Shutdown complete");
    }

    public MontoyaApi getApi() {
        return api;
    }

    public GripEventBus getEventBus() {
        return eventBus;
    }

    public GripConfig getConfig() {
        return config;
    }

    public GripTheme getTheme() {
        return theme;
    }

    public GripMainTab getMainTab() {
        return mainTab;
    }

    public GripHttpClient getHttpClient() {
        return httpClient;
    }

    @SuppressWarnings("unchecked")
    public <T extends GripModule> T getModule(Class<T> moduleClass) {
        for (GripModule module : modules) {
            if (moduleClass.isInstance(module)) {
                return (T) module;
            }
        }
        return null;
    }

    public GripModule getModuleById(String moduleId) {
        for (GripModule module : modules) {
            if (module.getModuleId().equals(moduleId)) {
                return module;
            }
        }
        return null;
    }

    public List<GripModule> getModules() {
        return new ArrayList<>(modules);
    }

    public void log(String message) {
        api.logging().logToOutput("[GraphQL Grip] " + message);
    }

    public void logError(String message) {
        api.logging().logToError("[GraphQL Grip] " + message);
    }

    public void logError(String message, Exception e) {
        api.logging().logToError("[GraphQL Grip] " + message + ": " + e.getMessage());
    }
}
