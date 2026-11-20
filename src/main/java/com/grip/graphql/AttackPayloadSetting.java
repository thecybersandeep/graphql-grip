package com.grip.graphql;

public enum AttackPayloadSetting {

    ALIASES(GripConfig.ATTACK_ALIAS_COUNT, "Aliases per payload", 100, 10, 10000, 50),
    BATCH(GripConfig.ATTACK_BATCH_COUNT, "Operations per batch", 10, 2, 1000, 5),
    FIELDS(GripConfig.ATTACK_FIELD_COUNT, "Duplicated fields", 500, 50, 10000, 100),
    DIRECTIVES(GripConfig.ATTACK_DIRECTIVE_COUNT, "Directives per field", 50, 10, 500, 10),
    DEPTH(GripConfig.ATTACK_DEPTH_COUNT, "Nesting depth", 10, 3, 100, 5),
    FRAGMENTS(GripConfig.ATTACK_FRAGMENT_COUNT, "Fragments per payload", 50, 10, 500, 10);

    private final String key;
    private final String label;
    private final int defaultValue;
    private final int minimum;
    private final int maximum;
    private final int step;

    AttackPayloadSetting(String key, String label, int defaultValue,
                         int minimum, int maximum, int step) {
        this.key = key;
        this.label = label;
        this.defaultValue = defaultValue;
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public int defaultValue() {
        return defaultValue;
    }

    public int minimum() {
        return minimum;
    }

    public int maximum() {
        return maximum;
    }

    public int step() {
        return step;
    }

    public int clamp(Integer value) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
