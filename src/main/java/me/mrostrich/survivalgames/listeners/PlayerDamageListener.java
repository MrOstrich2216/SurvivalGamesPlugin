package me.mrostrich.survivalgames.listeners;

import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Prevents PvP damage during the grace period and blocks interactions that would affect exempt users.
 */
public class PlayerDamageListener implements Listener {

    private final SurvivalGamesPlugin plugin;

    public PlayerDamageListener(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!plugin.isPluginEnabledFlag()) return;

        // If the victim is an exempt player, cancel most damaging events to protect moderators
        if (event.getEntity() instanceof Player victim) {
            try {
                if (plugin.isExempt(victim)) {
                    event.setCancelled(true);
                    return;
                }
            } catch (Throwable ignored) {}
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.isPluginEnabledFlag()) return;

        if (!(event.getEntity() instanceof Player victim)) return;

        try {
            // If victim is exempt, cancel to protect moderators
            if (plugin.isExempt(victim)) {
                event.setCancelled(true);
                return;
            }

            // If damager is a player and is exempt, cancel
            if (event.getDamager() instanceof Player damager) {
                if (plugin.isExempt(damager)) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Enforce grace period: cancel PvP while in grace (GameManager historically tracked GRACE)
            try {
                if (plugin.getMatchState() != null) {
                    switch (plugin.getMatchState()) {
                        case WAITING, FIGHT, FINAL_FIGHT, ENDED -> {
                            // Only block in the specific legacy grace condition: check GameManager's graceRemaining if available
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // As a robust fallback, consult GameManager legacy state if present
            try {
                var gm = plugin.getGameManager();
                if (gm != null && gm.getState() == me.mrostrich.survivalgames.GameManager.State.GRACE) {
                    // Cancel damage between players during grace
                    if (event.getDamager() instanceof Player) {
                        event.setCancelled(true);
                        if (event.getDamager() instanceof Player p) {
                            try { p.sendMessage(ChatColor.YELLOW + "PvP is disabled during the grace period."); } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            plugin.getLogger().warning("PlayerDamageListener error: " + t.getMessage());
        }
    }
}