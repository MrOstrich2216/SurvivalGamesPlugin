package me.mrostrich.survivalgames.util;

import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Random;

/**
 * Utility for safe random teleports and giving the Recorder's tracking compass.
 */
public final class TeleportUtil {

    private TeleportUtil() {
        // Prevent instantiation
    }

    private static final Random RNG = new Random();

    /**
     * Finds a random safe location within a radius of the given center.
     *
     * @param world  The world to search in.
     * @param center The center location.
     * @param radius The maximum radius from the center.
     * @return A safe location, or the center if none found.
     */
    public static Location randomSafeLocation(World world, Location center, double radius) {
        int attempts = 100;
        for (int i = 0; i < attempts; i++) {
            double angle = RNG.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(RNG.nextDouble()) * radius; // Uniform area sampling
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * r);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * r);

            int y = world.getHighestBlockYAt(x, z);
            if (y <= world.getMinHeight()) continue;

            Block feet = world.getBlockAt(x, y, z);
            Block below = feet.getRelative(BlockFace.DOWN);

            if (!below.getType().isSolid()) continue;
            if (feet.isLiquid()) continue;

            Location loc = new Location(world, x + 0.5, y, z + 0.5);
            loc.setYaw(RNG.nextFloat() * 360f);
            return loc;
        }

        // Fallback: random direction from center
        double fallbackAngle = RNG.nextDouble() * Math.PI * 2.0;
        double fallbackRadius = radius * 0.75;
        int fx = center.getBlockX() + (int) Math.round(Math.cos(fallbackAngle) * fallbackRadius);
        int fz = center.getBlockZ() + (int) Math.round(Math.sin(fallbackAngle) * fallbackRadius);
        int fy = world.getHighestBlockYAt(fx, fz);
        return new Location(world, fx + 0.5, fy, fz + 0.5);
    }


    /**
     * Gives the Recorder player an unbreakable compass that teleports them to random alive players.
     *
     * @param player The Recorder player.
     * @param plugin The plugin instance.
     */
    public static void giveRecorderCompass(Player player, SurvivalGamesPlugin plugin) {
        ItemStack compass = new ItemStack(Material.COMPASS, 1);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Player Tracker");
            meta.setLore(List.of(
                    ChatColor.GRAY + "Right-click to teleport",
                    ChatColor.GRAY + "to a random alive player"
            ));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            compass.setItemMeta(meta);
        }

        // Place in first empty slot or offhand if full
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(compass);
        } else {
            player.getInventory().setItemInOffHand(compass);
        }
    }
}
