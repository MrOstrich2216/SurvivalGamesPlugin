package me.mrostrich.survivalgames.listeners;

import me.mrostrich.survivalgames.GameManager;
import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class PlayerDamageListener implements Listener {

    private final SurvivalGamesPlugin plugin;

    public PlayerDamageListener(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!plugin.isPluginEnabledFlag()) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Player damagerPlayer = null;

        if (event.getDamager() instanceof Player p) {
            damagerPlayer = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            damagerPlayer = p;
        }

        if (damagerPlayer == null) return;

        if (plugin.getGameManager().getState() == GameManager.State.GRACE) {
            event.setCancelled(true);
            damagerPlayer.sendMessage(ChatColor.RED + "PvP is disabled during the grace period.");
        }
    }
}
