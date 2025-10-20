package me.mrostrich.uhcrunplugin.listeners;

import me.mrostrich.uhcrunplugin.GameManager;
import me.mrostrich.uhcrunplugin.UhcRunPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class PlayerDamageListener implements Listener {

    private final UhcRunPlugin plugin;

    public PlayerDamageListener(UhcRunPlugin plugin) {
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
