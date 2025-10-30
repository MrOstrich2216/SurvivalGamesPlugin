package me.mrostrich.survivalgames.listeners;

import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import me.mrostrich.survivalgames.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;

/**
 * Handles player join interactions: enforce pre-game gamemode, apply exempt mod setup,
 * ensure teleports for players joining during a waiting period, and update tabs.
 */
public class PlayerJoinListener implements Listener {

    private final SurvivalGamesPlugin plugin;

    public PlayerJoinListener(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.isPluginEnabledFlag()) return;

        Player p = event.getPlayer();

        // Greet
        try {
            p.sendMessage(ChatColor.GREEN + "Welcome to Survival Games!");
        } catch (Throwable ignored) {}

        // If plugin is enabled and game is in pre-start waiting state, enforce ADVENTURE for non-exempt players
        try {
            if (plugin.getGameManager() != null && plugin.getGameManager().getState() == null) {
                // defensive: nothing
            }
        } catch (Throwable ignored) {}

        try {
            if (!plugin.isExempt(p)) {
                // if game is waiting, enforce adventure; otherwise let GameManager handle onJoin teleport
                try {
                    if (plugin.getMatchState() == null || plugin.getMatchState().name().equalsIgnoreCase("WAITING")) {
                        p.setGameMode(GameMode.ADVENTURE);
                    }
                } catch (Throwable ignored) {}
            } else {
                // Exempt users get moderator setup on join
                try {
                    TeleportUtil.setupMod(p, plugin);
                    TeleportUtil.giveRecorderCompass(p, plugin);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Failed to setup exempt user on join: " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Error during join handling for " + p.getName() + ": " + t.getMessage());
        }

        // Let GameManager perform join-specific handling (teleport to spawn if configured)
        try {
            if (plugin.getGameManager() != null) plugin.getGameManager().onJoin(p);
        } catch (Throwable t) {
            plugin.getLogger().warning("GameManager.onJoin error for " + p.getName() + ": " + t.getMessage());
        }

        // Kickstart UI state for the player
        try {
            if (plugin.getTabManager() != null) plugin.getTabManager().updateTab(p);
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to update tab for " + p.getName() + ": " + t.getMessage());
        }

        plugin.getLogger().info("Player joined: " + p.getName() + " exempt=" + plugin.isExempt(p));
    }
}
