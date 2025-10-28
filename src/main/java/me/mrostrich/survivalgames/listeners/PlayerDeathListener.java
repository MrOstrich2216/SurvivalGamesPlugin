package me.mrostrich.survivalgames.listeners;

import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class PlayerDeathListener implements Listener {

    private final SurvivalGamesPlugin plugin;

    public PlayerDeathListener(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.isPluginEnabledFlag()) return;

        // Suppress vanilla death message
        event.setDeathMessage(null);

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        String msg;
        if (killer != null) {
            boolean ranged = false;
            double distance = -1;

            if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent last) {
                if (last.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player shooter && shooter.equals(killer)) {
                    ranged = true;
                    try {
                        if (victim.getWorld() != null && killer.getWorld() != null && victim.getWorld().equals(killer.getWorld())) {
                            distance = victim.getLocation().distance(killer.getLocation());
                        }
                    } catch (Throwable ignored) {
                        distance = -1;
                    }
                }
            }

            if (ranged && distance >= 0) {
                msg = ChatColor.RED + "" + ChatColor.BOLD + victim.getName()
                        + ChatColor.GRAY + " was shot by "
                        + ChatColor.GREEN + "" + ChatColor.BOLD + killer.getName()
                        + ChatColor.GRAY + " from "
                        + ChatColor.AQUA + String.format("%.0f", distance)
                        + ChatColor.GRAY + " blocks away";
            } else {
                ItemStack weapon = killer.getInventory().getItemInMainHand();
                if (weapon == null || weapon.getType() == Material.AIR) {
                    msg = ChatColor.RED + "" + ChatColor.BOLD + victim.getName()
                            + ChatColor.GRAY + " was K.O by "
                            + ChatColor.GREEN + "" + ChatColor.BOLD + killer.getName()
                            + ChatColor.GRAY + " with "
                            + ChatColor.DARK_RED + "bare hands";
                } else {
                    String weaponName = formatWeaponName(weapon);
                    msg = ChatColor.RED + "" + ChatColor.BOLD + victim.getName()
                            + ChatColor.GRAY + " was killed by "
                            + ChatColor.GREEN + "" + ChatColor.BOLD + killer.getName()
                            + ChatColor.GRAY + " using "
                            + ChatColor.WHITE + "" + ChatColor.ITALIC + weaponName;
                }
            }
        } else {
            msg = ChatColor.RED + "" + ChatColor.BOLD + victim.getName()
                    + ChatColor.GRAY + " died.";
        }

        Bukkit.broadcastMessage(msg);

        if (victim.getWorld() != null) {
            try {
                victim.getWorld().playSound(victim.getLocation(), org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
            } catch (Throwable ignored) {}
        }

        // Track stats via GameManager
        try {
            if (killer != null) plugin.getGameManager().recordKill(killer);
            plugin.getGameManager().recordDeath(victim);
        } catch (Throwable t) {
            plugin.getLogger().warning("Error recording kill/death: " + t.getMessage());
        }

        plugin.getLogger().info("Death processed: victim=" + victim.getName() + " killer=" + (killer != null ? killer.getName() : "null"));
    }

    private String formatWeaponName(ItemStack weapon) {
        if (weapon == null) return "Unknown";
        ItemMeta meta = weapon.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        String s = weapon.getType().name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}