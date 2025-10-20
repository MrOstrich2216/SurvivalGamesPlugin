package me.mrostrich.uhcrunplugin.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/**
 * Utility for configuring the world border.
 */
public final class BorderUtil {

    private BorderUtil() {
        // Prevent instantiation
    }

    /**
     * Configures the world border to a given center and diameter.
     *
     * @param world    The world to configure.
     * @param center   The center location.
     * @param diameter The border diameter in blocks.
     */
    public static void configureBorder(World world, Location center, double diameter) {
        WorldBorder border = world.getWorldBorder();
        border.setCenter(center);
        border.setSize(diameter);
        border.setWarningDistance(5); // warn players 5 blocks before border
        border.setWarningTime(5);     // warn players 5 seconds before border reaches them
    }
}
