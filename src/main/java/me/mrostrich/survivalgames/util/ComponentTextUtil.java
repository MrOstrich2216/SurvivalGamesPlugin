package me.mrostrich.survivalgames.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Small helper for converting legacy color-coded strings into Adventure Components.
 */
public final class ComponentTextUtil {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private ComponentTextUtil() {}

    public static Component simpleAction(String legacyText) {
        if (legacyText == null) return Component.empty();
        return LEGACY.deserialize(legacyText);
    }
}
