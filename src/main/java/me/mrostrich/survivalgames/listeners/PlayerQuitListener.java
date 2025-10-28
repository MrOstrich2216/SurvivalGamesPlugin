package me.mrostrich.survivalgames.listeners;

import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

/**
 * Handles player quit events to keep GameManager state consistent.
 */
public class PlayerQuitListener implements Listener {

    private final SurvivalGamesPlugin plugin;

    public PlayerQuitListener(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!plugin.isPluginEnabledFlag()) return;

        Player p = event.getPlayer();
        if (p == null) return;

        try {
            // If player was alive in the game, mark as death to preserve ordering and triggers
            var gm = plugin.getGameManager();
            if (gm != null && gm.getAlive().contains(p.getUniqueId())) {
                // Record death via GameManager to trigger end-of-game checks and UI updates
                try {
                    gm.recordDeath(p);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Failed to record death for quitting player " + p.getName() + ": " + t.getMessage());
                }
            }

            // Ensure moderators/exempt players are made visible on quit to avoid stale hidden state
            if (plugin.isExempt(p)) {
                try {
                    for (Player other : Bukkit.getOnlinePlayers()) {
                        try { other.showPlayer(plugin, p); } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }

            // Update tabs for all players to reflect the departure
            try { plugin.getTabManager().updateAllTabs(); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            plugin.getLogger().warning("PlayerQuitListener error for " + p.getName() + ": " + t.getMessage());
        }
    }
}
