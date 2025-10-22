package me.mrostrich.survivalgames;

import me.mrostrich.survivalgames.commands.GameCommands;
import me.mrostrich.survivalgames.listeners.PlayerDamageListener;
import me.mrostrich.survivalgames.listeners.PlayerDeathListener;
import me.mrostrich.survivalgames.listeners.PlayerJoinListener;
import me.mrostrich.survivalgames.listeners.RecorderCompassListener;
import me.mrostrich.survivalgames.ui.ActionBarTask;
import me.mrostrich.survivalgames.ui.BossBarTask;
import me.mrostrich.survivalgames.ui.ScoreboardTask;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.network.ITextFilter;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

public class SurvivalGamesPlugin extends JavaPlugin {

    public static Field FILTER_FIELD; // ✅ Added for hardcore hearts injection

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

        // ✅ Initialize FILTER_FIELD for hardcore hearts injection
        for (Field f : EntityPlayer.class.getDeclaredFields()) {
            if (f.getType() == ITextFilter.class) {
                f.setAccessible(true);
                FILTER_FIELD = f;
                break;
            }
        }

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
        if (getCommand("game") != null) {
            getCommand("game").setExecutor(new GameCommands(this));
            getCommand("game").setTabCompleter(new GameCommands(this));
        }

        if (pluginEnabled) {
            enableSystems();
        }
    }

    public boolean isExempt(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        String name = op.getName();
        List<String> exemptList = getConfig().getStringList("exempt-users");
        return name != null && exemptList.stream().anyMatch(ex -> name.equalsIgnoreCase(ex));
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
