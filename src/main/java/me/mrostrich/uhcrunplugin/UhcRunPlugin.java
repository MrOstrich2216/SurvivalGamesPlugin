package me.mrostrich.uhcrunplugin;

import me.mrostrich.uhcrunplugin.commands.UhcCommand;
import me.mrostrich.uhcrunplugin.listeners.PlayerDamageListener;
import me.mrostrich.uhcrunplugin.listeners.PlayerDeathListener;
import me.mrostrich.uhcrunplugin.listeners.PlayerJoinListener;
import me.mrostrich.uhcrunplugin.listeners.RecorderCompassListener;
import me.mrostrich.uhcrunplugin.ui.ActionBarTask;
import me.mrostrich.uhcrunplugin.ui.BossBarTask;
import me.mrostrich.uhcrunplugin.ui.ScoreboardTask;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class UhcRunPlugin extends JavaPlugin {

    private GameManager gameManager;
    private boolean pluginEnabled;

    // UI tasks
    private ActionBarTask actionBarTask;
    private BossBarTask bossBarTask;
    private ScoreboardTask scoreboardTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration cfg = getConfig();
        this.pluginEnabled = cfg.getBoolean("plugin-enabled", false);

        // Ensure overworld exists (Terraform Generator handles terrain)
        World world = Bukkit.getWorld("world");
        if (world == null) {
            world = Bukkit.createWorld(new WorldCreator("world"));
        }
        if (world != null) {
            world.setGameRule(GameRule.NATURAL_REGENERATION, true);
        }

        this.gameManager = new GameManager(this);

        // Command is always registered; logic is gated behind enable flag
        if (getCommand("uhc") != null) {
            getCommand("uhc").setExecutor(new UhcCommand(this));
        }

        if (pluginEnabled) {
            enableSystems();
        }
    }

    @Override
    public void onDisable() {
        disableSystems();
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public boolean isPluginEnabledFlag() {
        return pluginEnabled;
    }

    public void setPluginEnabledFlag(boolean enabled) {
        this.pluginEnabled = enabled;
        getConfig().set("plugin-enabled", enabled);
        saveConfig();
    }

    public void enableSystems() {
        // Register listeners
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerDamageListener(this), this);
        Bukkit.getPluginManager().registerEvents(new RecorderCompassListener(this), this);

        // Enforce pre-game adventure for non-exempt if waiting
        if (gameManager.getState() == GameManager.State.WAITING) {
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (!gameManager.isExempt(p)) {
                    p.setGameMode(org.bukkit.GameMode.ADVENTURE);
                }
            });
        }

        // Start UI tasks
        actionBarTask = new ActionBarTask(this);
        bossBarTask = new BossBarTask(this);
        scoreboardTask = new ScoreboardTask(this);

        actionBarTask.start();
        bossBarTask.start();
        scoreboardTask.start();
    }

    public void disableSystems() {
        if (actionBarTask != null) actionBarTask.stop();
        if (bossBarTask != null) bossBarTask.stop();
        if (scoreboardTask != null) scoreboardTask.stop();
        HandlerList.unregisterAll(this);

        if (gameManager != null) {
            gameManager.forceStop();
        }
    }
}
