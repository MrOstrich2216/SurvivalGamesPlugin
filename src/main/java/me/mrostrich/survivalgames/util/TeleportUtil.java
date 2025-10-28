package me.mrostrich.survivalgames.util;

import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.block.Block;

import java.util.Random;

/**
 * Teleport and moderator utility helpers.
 */
public final class TeleportUtil {

    private static final Random RANDOM = new Random();

    public TeleportUtil() {}

    /**
     * Find a random safe location within a radius around center. Attempts a number of times,
     * checks for solid ground and non-liquid landing spot.
     *
     * @param world  world to search
     * @param center center to offset from
     * @param radius radius in blocks
     * @return a safe Location or null if none found
     */
    public static Location randomSafeLocation(World world, Location center, double radius) {
        if (world == null || center == null || radius <= 0) return null;

        int attempts = 30;
        int maxY = Math.min(world.getMaxHeight() - 2, 256);
        for (int i = 0; i < attempts; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            double r = RANDOM.nextDouble() * radius;
            double dx = Math.cos(angle) * r;
            double dz = Math.sin(angle) * r;
            int x = center.getBlockX() + (int) Math.round(dx);
            int z = center.getBlockZ() + (int) Math.round(dz);

            int y = world.getHighestBlockYAt(x, z);
            if (y <= 1 || y > maxY) continue;

            Location tryLoc = new Location(world, x + 0.5, y, z + 0.5);
            if (isSafeSpawn(tryLoc)) return tryLoc;
        }

        // Fallback: try near center horizontally scanning outward
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        for (int r = 1; r <= Math.max(10, (int) Math.ceil(radius)); r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int x = cx + dx;
                    int z = cz + dz;
                    int y = world.getHighestBlockYAt(x, z);
                    if (y <= 1 || y > maxY) continue;
                    Location tryLoc = new Location(world, x + 0.5, y, z + 0.5);
                    if (isSafeSpawn(tryLoc)) return tryLoc;
                }
            }
        }

        return null;
    }

    /**
     * Minimal safety check for spawn location: ensure block below is solid and feet/head space is non-solid and non-liquid.
     */
    private static boolean isLiquid(Material mat) {
        String name = mat.name();
        return name.contains("WATER") || name.contains("LAVA");
    }

    private static boolean isPassable(Material mat) {
        // Conservative fallback: allow air, plants, carpets, etc.
        return mat.isAir() ||
                nameContains(mat, "GRASS") ||
                nameContains(mat, "CARPET") ||
                nameContains(mat, "FLOWER") ||
                nameContains(mat, "VINE") ||
                nameContains(mat, "BUSH") ||
                nameContains(mat, "LEAVES");
    }

    private static boolean nameContains(Material mat, String keyword) {
        return mat.name().contains(keyword);
    }

    private static boolean isSafeSpawn(Location loc) {
        if (loc == null) return false;
        World w = loc.getWorld();
        if (w == null) return false;

        Block feet = w.getBlockAt(loc);
        Block below = feet.getRelative(0, -1, 0);
        Block head = w.getBlockAt(loc.clone().add(0, 1, 0));

        Material belowMat = below.getType();
        Material feetMat = feet.getType();
        Material headMat = head.getType();

        if (belowMat == Material.AIR || belowMat.isAir() || belowMat.isInteractable()) return false;
        if (isLiquid(feetMat) || isLiquid(headMat)) return false;
        if (!isPassable(feetMat) && !feetMat.isAir()) {
            return headMat == Material.AIR;
        }
        return headMat == Material.AIR && !isLiquid(feetMat);
    }

    /**
     * Prepare a moderator player on join: set vanish/invisibility where possible and give basic tools.
     * This method tries to be non-destructive and logs errors to the plugin logger when available.
     */
    public static void setupMod(Player p, SurvivalGamesPlugin plugin) {
        if (p == null) return;

        try {
            // Ensure survival gamemode for mods
            p.setGameMode(GameMode.SURVIVAL);

            // Attempt to make them invisible to other players by applying potion effect and hiding
            try {
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
            } catch (Throwable ignored) {}

            for (Player other : p.getWorld().getPlayers()) {
                if (!other.equals(p)) {
                    try {
                        other.hidePlayer(plugin, p);
                    } catch (Throwable ignored) {}
                }
            }

            // Ensure recorder compass and minimal inventory
            giveRecorderCompass(p, plugin);
        } catch (Throwable t) {
            if (plugin != null) plugin.getLogger().warning("TeleportUtil.setupMod failed for " + p.getName() + ": " + t.getMessage());
        }
    }

    /**
     * Give the recorder compass item to a player if they don't already have one in inventory.
     */
    public static void giveRecorderCompass(Player p, SurvivalGamesPlugin plugin) {
        if (p == null) return;
        try {
            boolean has = p.getInventory().contains(Material.COMPASS);
            if (has) return;

            ItemStack compass = new ItemStack(Material.COMPASS, 1);
            ItemMeta meta = compass.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "Recorder Compass");
                meta.setLore(java.util.List.of(ChatColor.GRAY + "Right-click to teleport near a player"));
                compass.setItemMeta(meta);
            }

            p.getInventory().addItem(compass);
        } catch (Throwable t) {
            if (plugin != null) plugin.getLogger().warning("Failed to give recorder compass to " + p.getName() + ": " + t.getMessage());
        }
    }
}
