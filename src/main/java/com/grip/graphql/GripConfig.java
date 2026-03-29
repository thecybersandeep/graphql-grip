package com.grip.graphql;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.persistence.Preferences;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class GripConfig {

    private static GripConfig instance;

    private final MontoyaApi api;
    private final Preferences preferences;
    private final Map<String, Object> defaults;
    private final Map<String, Consumer<Object>> hooks;

    public static final String DISCOVERY_ENABLED = "discovery.enabled";
    public static final String DISCOVERY_AUTO_DETECT = "discovery.auto_detect";

    public static final String SCHEMA_CODEGEN_DEPTH = "schema.codegen.depth";
    public static final String SCHEMA_CODEGEN_PAD = "schema.codegen.pad";
    public static final String SCHEMA_RECONSTRUCTION_BUCKET_SIZE = "schema.reconstruction.bucket_size";
    public static final String SCHEMA_RECONSTRUCTION_CONCURRENCY = "schema.reconstruction.concurrency";

    public static final String SECURITY_DOS_ALIAS_COUNT = "security.dos.alias_count";
    public static final String SECURITY_DOS_BATCH_COUNT = "security.dos.batch_count";
    public static final String SECURITY_DOS_FIELD_COUNT = "security.dos.field_count";
    public static final String SECURITY_DOS_DIRECTIVE_COUNT = "security.dos.directive_count";

    public static final String POI_ENABLED = "poi.enabled";
    public static final String POI_DEPTH = "poi.depth";
    public static final String POI_AUTH = "poi.auth";
    public static final String POI_PII = "poi.pii";
    public static final String POI_PAYMENT = "poi.payment";
    public static final String POI_DATABASE = "poi.database";

    public static final String UI_THEME = "ui.theme";
    public static final String UI_HIGHLIGHT_COLOR = "ui.highlight_color";

    public static final String HTTP_TIMEOUT_MS = "http.timeout_ms";
    public static final String HTTP_MAX_CONCURRENT = "http.max_concurrent";
    public static final String HTTP_REQUEST_DELAY_MS = "http.request_delay_ms";
    public static final String HTTP_USER_AGENT = "http.user_agent";
    public static final String HTTP_INHERIT_AUTH_HEADERS = "http.inherit_auth_headers";

    public static final String SECURITY_DEPTH_LEVELS = "security.depth_levels";
    public static final String SECURITY_CIRCULAR_DEPTH = "security.circular_depth";

    private GripConfig(MontoyaApi api) {
        this.api = api;
        this.preferences = api.persistence().preferences();
        this.defaults = initializeDefaults();
        this.hooks = new ConcurrentHashMap<>();
    }

    public static synchronized GripConfig getInstance(MontoyaApi api) {
        if (instance == null) {
            instance = new GripConfig(api);
        }
        return instance;
    }

    public static GripConfig getInstance() {
        return instance;
    }

    private Map<String, Object> initializeDefaults() {
        Map<String, Object> defaults = new ConcurrentHashMap<>();

        defaults.put(DISCOVERY_ENABLED, true);
        defaults.put(DISCOVERY_AUTO_DETECT, true);

        defaults.put(SCHEMA_CODEGEN_DEPTH, 2);
        defaults.put(SCHEMA_CODEGEN_PAD, 4);
        defaults.put(SCHEMA_RECONSTRUCTION_BUCKET_SIZE, 64);
        defaults.put(SCHEMA_RECONSTRUCTION_CONCURRENCY, 8);

        defaults.put(SECURITY_DOS_ALIAS_COUNT, 100);
        defaults.put(SECURITY_DOS_BATCH_COUNT, 10);
        defaults.put(SECURITY_DOS_FIELD_COUNT, 500);
        defaults.put(SECURITY_DOS_DIRECTIVE_COUNT, 10);

        defaults.put(POI_ENABLED, true);
        defaults.put(POI_DEPTH, 4);
        defaults.put(POI_AUTH, true);
        defaults.put(POI_PII, true);
        defaults.put(POI_PAYMENT, true);
        defaults.put(POI_DATABASE, true);

        defaults.put(UI_THEME, "auto");
        defaults.put(UI_HIGHLIGHT_COLOR, "ORANGE");

        defaults.put(HTTP_TIMEOUT_MS, 30000);
        defaults.put(HTTP_MAX_CONCURRENT, 10);
        defaults.put(HTTP_REQUEST_DELAY_MS, 100);
        defaults.put(HTTP_USER_AGENT, "");
        defaults.put(HTTP_INHERIT_AUTH_HEADERS, true);

        defaults.put(SECURITY_DEPTH_LEVELS, 15);
        defaults.put(SECURITY_CIRCULAR_DEPTH, 10);

        return defaults;
    }

    public void registerHook(String key, Consumer<Object> hook) {
        hooks.put(key, hook);
    }

    public Boolean getBoolean(String key) {
        Boolean value = preferences.getBoolean(key);
        if (value == null && defaults.containsKey(key)) {
            return (Boolean) defaults.get(key);
        }
        return value;
    }

    public Integer getInteger(String key) {
        Integer value = preferences.getInteger(key);
        if (value == null && defaults.containsKey(key)) {
            return (Integer) defaults.get(key);
        }
        return value;
    }

    public String getString(String key) {
        String value = preferences.getString(key);
        if (value == null && defaults.containsKey(key)) {
            return (String) defaults.get(key);
        }
        return value;
    }

    public void setBoolean(String key, boolean value) {
        preferences.setBoolean(key, value);
        triggerHook(key, value);
    }

    public void setInteger(String key, int value) {
        preferences.setInteger(key, value);
        triggerHook(key, value);
    }

    public void setString(String key, String value) {
        preferences.setString(key, value);
        triggerHook(key, value);
    }

    public void resetToDefault(String key) {
        Object defaultValue = defaults.get(key);
        if (defaultValue != null) {
            if (defaultValue instanceof Boolean) {
                setBoolean(key, (Boolean) defaultValue);
            } else if (defaultValue instanceof Integer) {
                setInteger(key, (Integer) defaultValue);
            } else if (defaultValue instanceof String) {
                setString(key, (String) defaultValue);
            }
        }
    }

    public void resetAllToDefaults() {
        for (String key : defaults.keySet()) {
            resetToDefault(key);
        }
    }

    private void triggerHook(String key, Object value) {
        Consumer<Object> hook = hooks.get(key);
        if (hook != null) {
            try {
                hook.accept(value);
            } catch (Exception e) {
                api.logging().logToError("[GripConfig] Error in hook for " + key + ": " + e.getMessage());
            }
        }
    }

    public Object getDefault(String key) {
        return defaults.get(key);
    }
}
