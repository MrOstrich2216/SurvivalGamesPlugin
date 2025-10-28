package me.mrostrich.survivalgames.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/**
 * Small utility to configure the world border reliably.
 */
public final class BorderUtil {

    private BorderUtil() {}

    /**
     * Configure world border center and diameter safely.
     *
     * @param world    target world
     * @param center   center location for the border
     * @param diameter diameter in blocks
     */
    public static void configureBorder(World world, Location center, double diameter) {
        if (world == null || center == null) {
            Bukkit.getLogger().warning("BorderUtil.configureBorder called with null world or center.");
            return;
        }

        try {
            WorldBorder border = world.getWorldBorder();
            border.setCenter(center);
            border.setSize(Math.max(1.0, diameter));
            border.setWarningDistance(5);
            border.setWarningTime(5);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("BorderUtil: failed to configure border: " + t.getMessage());
        }
    }
}
