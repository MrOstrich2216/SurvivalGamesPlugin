package me.mrostrich.uhcrunplugin.listeners;

import me.mrostrich.uhcrunplugin.GameManager;
import me.mrostrich.uhcrunplugin.UhcRunPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerDeathListener implements Listener {

    private final UhcRunPlugin plugin;

    public PlayerDeathListener(UhcRunPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.isPluginEnabledFlag()) return;

        // Suppress vanilla death message
        event.setDeathMessage(null);

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Build colored custom message
        String msg;
        if (killer != null) {
            // Detect last damage for ranged info
            boolean ranged = false;
            double distance = -1;

            if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent last) {
                if (last.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player shooter && shooter.equals(killer)) {
                    ranged = true;
                    if (victim.getLocation().getWorld() != null && killer.getLocation().getWorld() != null
                            && victim.getWorld().equals(killer.getWorld())) {
                        distance = victim.getLocation().distance(killer.getLocation());
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
                    String weaponName = weapon.hasItemMeta() && weapon.getItemMeta().hasDisplayName()
                            ? weapon.getItemMeta().getDisplayName()
                            : formatMaterialName(weapon.getType());
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
        victim.getWorld().playSound(victim.getLocation(), org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        // Track stats
        if (killer != null) {
            plugin.getGameManager().recordKill(killer);
        }
        plugin.getGameManager().recordDeath(victim);
    }

    private String formatMaterialName(Material m) {
        String s = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
