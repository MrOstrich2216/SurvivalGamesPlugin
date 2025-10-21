package me.mrostrich.survivalgames.listeners;

import me.mrostrich.survivalgames.GameManager;
import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final SurvivalGamesPlugin plugin;

    public PlayerJoinListener(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.isPluginEnabledFlag()) return;
        GameManager gm = plugin.getGameManager();
        if (gm.getState() == GameManager.State.WAITING && !gm.isExempt(event.getPlayer())) {
            event.getPlayer().setGameMode(GameMode.ADVENTURE);
        }
    }
}
