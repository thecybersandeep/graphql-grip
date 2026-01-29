package com.grip.graphql;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.extension.ExtensionUnloadingHandler;

public class GripExtender implements BurpExtension, ExtensionUnloadingHandler {

    private static final String EXTENSION_NAME = "GraphQL Grip";
    private GripCore gripCore;

    @Override
    public void initialize(MontoyaApi api) {

        api.extension().setName(EXTENSION_NAME);

        api.extension().registerUnloadingHandler(this);

        this.gripCore = new GripCore(api);

        try {
            this.gripCore.initialize();
            api.logging().logToOutput("[GraphQL Grip] Extension loaded successfully");
            api.logging().logToOutput("[GraphQL Grip] Version 1.0.0");
        } catch (Exception e) {
            api.logging().logToError("[GraphQL Grip] Failed to initialize: " + e.getMessage());
            throw new RuntimeException("Failed to initialize GraphQL Grip", e);
        }
    }

    @Override
    public void extensionUnloaded() {
        if (gripCore != null) {
            gripCore.shutdown();
        }
    }
}
