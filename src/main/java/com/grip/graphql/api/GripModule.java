package com.grip.graphql.api;

import burp.api.montoya.MontoyaApi;
import com.grip.graphql.event.GripEventBus;

public interface GripModule {

    String getModuleId();

    String getModuleName();

    String getDescription();

    void initialize(MontoyaApi api, GripEventBus eventBus);

    void shutdown();

    boolean isEnabled();

    void setEnabled(boolean enabled);
}
