package com.grip.graphql;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class AttackPayloadSettingTest {

    @Test
    void everyDefaultSitsInsideTheRangeTheSettingsPanelOffers() {
        for (AttackPayloadSetting setting : AttackPayloadSetting.values()) {
            assertTrue(setting.defaultValue() >= setting.minimum(),
                    setting + " default " + setting.defaultValue()
                            + " is below its minimum " + setting.minimum());
            assertTrue(setting.defaultValue() <= setting.maximum(),
                    setting + " default " + setting.defaultValue()
                            + " is above its maximum " + setting.maximum());
        }
    }

    @Test
    void everyRangeIsUsable() {
        for (AttackPayloadSetting setting : AttackPayloadSetting.values()) {
            assertTrue(setting.minimum() < setting.maximum(), setting.name());
            assertTrue(setting.step() > 0, setting.name());
            assertTrue(setting.step() <= setting.maximum() - setting.minimum(), setting.name());
            assertFalse(setting.label().isBlank(), setting.name());
        }
    }

    @Test
    void clampKeepsAStoredValueInsideTheRange() {
        AttackPayloadSetting aliases = AttackPayloadSetting.ALIASES;

        assertEquals(aliases.minimum(), aliases.clamp(aliases.minimum() - 1));
        assertEquals(aliases.maximum(), aliases.clamp(aliases.maximum() + 1));
        assertEquals(250, aliases.clamp(250));
    }

    @Test
    void clampFallsBackToTheDefaultWhenNothingIsStored() {
        for (AttackPayloadSetting setting : AttackPayloadSetting.values()) {
            assertEquals(setting.defaultValue(), setting.clamp(null), setting.name());
        }
    }

    @Test
    void eachSettingOwnsADistinctConfigKey() {
        Set<String> keys = new LinkedHashSet<>();
        for (AttackPayloadSetting setting : AttackPayloadSetting.values()) {
            assertTrue(keys.add(setting.key()), "duplicate key " + setting.key());
            assertFalse(setting.key().isBlank(), setting.name());
        }
        assertEquals(AttackPayloadSetting.values().length, keys.size());
    }

    @Test
    void theSettingsPanelCoversEveryAttackCountTheConfigDeclares() throws Exception {
        Set<String> declared = new TreeSet<>();
        for (Field field : GripConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    && field.getType() == String.class
                    && field.getName().startsWith("ATTACK_")) {
                declared.add((String) field.get(null));
            }
        }

        Set<String> offered = new TreeSet<>();
        for (AttackPayloadSetting setting : AttackPayloadSetting.values()) {
            offered.add(setting.key());
        }

        assertEquals(declared, offered);
    }
}
