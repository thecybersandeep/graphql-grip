package com.grip.graphql;

import burp.api.montoya.MontoyaApi;
import com.grip.graphql.http.GripHttpClient;
import com.grip.graphql.ui.GripMainTab;
import com.grip.graphql.ui.GripSettingsPanel;
import com.grip.graphql.ui.GripTheme;
import com.grip.graphql.ui.GripContextMenu;
import com.grip.graphql.editor.GripRequestEditorProvider;

public class GripCore {

    private final MontoyaApi api;
    private final GripConfig config;
    private final GripTheme theme;
    private GripMainTab mainTab;
    private GripHttpClient httpClient;

    public GripCore(MontoyaApi api) {
        this.api = api;
        this.config = GripConfig.getInstance(api);
        this.theme = GripTheme.getInstance(api);
    }

    public void initialize() {

        httpClient = new GripHttpClient(api);

        mainTab = new GripMainTab(this);
        api.userInterface().registerSuiteTab("GraphQL Grip", mainTab);

        api.userInterface().registerSettingsPanel(new GripSettingsPanel(api, config, theme));

        api.userInterface().registerContextMenuItemsProvider(new GripContextMenu(this));

        api.userInterface().registerHttpRequestEditorProvider(new GripRequestEditorProvider(api, this));
        api.logging().logToOutput("[GraphQL Grip] Repeater tab registered");

        api.logging().logToOutput("[GraphQL Grip] Core initialization complete");
    }

    public void shutdown() {
        api.logging().logToOutput("[GraphQL Grip] Shutting down...");

        if (httpClient != null) {
            httpClient.shutdown();
        }

        api.logging().logToOutput("[GraphQL Grip] Shutdown complete");
    }

    public MontoyaApi getApi() {
        return api;
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
