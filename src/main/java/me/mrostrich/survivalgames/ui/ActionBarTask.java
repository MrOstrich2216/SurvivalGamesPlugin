package me.mrostrich.survivalgames.ui;

import me.mrostrich.survivalgames.GameManager;
import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

public class ActionBarTask {
    private final SurvivalGamesPlugin plugin;
    private BukkitRunnable task;

    public ActionBarTask(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isPluginEnabledFlag()) return;

                GameManager.State state = plugin.getGameManager().getState();

                String msg = switch (state) {
                    case WAITING -> "§eWaiting for the game to start...";
                    case GRACE -> "§aLook for resources before the fight starts!";
                    case FIGHT -> "§6The fight begins! §fPave your way to victory.";
                    case FINAL_FIGHT -> "§4The final fight begins! §fFight for glory!";
                    case ENDED -> "§5The nightmare is over. §7May the last one standing win.";
                };

                Bukkit.getOnlinePlayers().forEach(p -> p.sendActionBar(Component.text(msg)));
            }
        };
        task.runTaskTimer(plugin, 0L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }
}
