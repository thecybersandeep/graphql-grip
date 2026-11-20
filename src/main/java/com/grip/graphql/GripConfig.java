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

    public static final String ATTACK_ALIAS_COUNT = "grip.attack.aliases";
    public static final String ATTACK_BATCH_COUNT = "grip.attack.batch";
    public static final String ATTACK_FIELD_COUNT = "grip.attack.fields";
    public static final String ATTACK_DIRECTIVE_COUNT = "grip.attack.directives";
    public static final String ATTACK_DEPTH_COUNT = "grip.attack.depth";
    public static final String ATTACK_FRAGMENT_COUNT = "grip.attack.fragments";

    public static final String HTTP_USER_AGENT = "http.user_agent";
    public static final String HTTP_INHERIT_AUTH_HEADERS = "http.inherit_auth_headers";

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

        for (AttackPayloadSetting setting : AttackPayloadSetting.values()) {
            defaults.put(setting.key(), setting.defaultValue());
        }

        defaults.put(HTTP_USER_AGENT, "");
        defaults.put(HTTP_INHERIT_AUTH_HEADERS, true);

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
