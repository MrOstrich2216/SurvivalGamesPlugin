package me.mrostrich.survivalgames.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

public final class BorderUtil {

    private BorderUtil() {}

    public static void configureBorder(World world, Location center, double diameter) {
        WorldBorder border = world.getWorldBorder();
        border.setCenter(center);
        border.setSize(diameter);
        border.setWarningDistance(5);
        border.setWarningTime(5);
    }
}