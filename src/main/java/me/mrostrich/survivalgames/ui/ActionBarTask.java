package me.mrostrich.survivalgames.ui;

import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import me.mrostrich.survivalgames.state.MatchState;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class ActionBarTask {
    private final SurvivalGamesPlugin plugin;
    private BukkitRunnable task;

    public ActionBarTask(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        plugin.getLogger().info("ActionBarTask started.");
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isPluginEnabledFlag()) return;

                MatchState state = plugin.getMatchState();

                String rawMsg;
                switch (state) {
                    case WAITING -> rawMsg = "§eWaiting for the game to start...";
                    case RUNNING -> rawMsg = "§6The fight begins! §fPave your way to victory.";
                    case FINAL_FIGHT -> rawMsg = "§4The final fight begins! §fFight for glory!";
                    case ENDED -> rawMsg = "§5The nightmare is over. §7May the last one standing win.";
                    default -> rawMsg = "§eWaiting for the game to start...";
                }

                Component msg = Component.text(rawMsg);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        if (!plugin.isExempt(p)) {
                            p.sendActionBar(msg);
                        }
                    } catch (Throwable t) {
                        plugin.getLogger().warning("Failed to send action bar to " + p.getName() + ": " + t.getMessage());
                    }
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 20L);
    }

    public void stop() {
        if (task != null) {
            try { task.cancel(); } catch (Throwable ignored) {}
            task = null;
        }
    }
}
