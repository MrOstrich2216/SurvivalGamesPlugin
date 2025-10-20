package me.mrostrich.uhcrunplugin.listeners;

import me.mrostrich.uhcrunplugin.GameManager;
import me.mrostrich.uhcrunplugin.UhcRunPlugin;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final UhcRunPlugin plugin;

    public PlayerJoinListener(UhcRunPlugin plugin) {
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
