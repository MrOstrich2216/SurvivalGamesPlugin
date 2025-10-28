package me.mrostrich.survivalgames;

import me.mrostrich.survivalgames.commands.GameCommands;
import me.mrostrich.survivalgames.inject.HardcorePacketInjector;
import me.mrostrich.survivalgames.listeners.*;
import me.mrostrich.survivalgames.state.MatchState;
import me.mrostrich.survivalgames.ui.ActionBarTask;
import me.mrostrich.survivalgames.ui.BossBarTask;
import me.mrostrich.survivalgames.ui.ScoreboardTask;
import me.mrostrich.survivalgames.ui.TabManager;
import me.mrostrich.survivalgames.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class SurvivalGamesPlugin extends JavaPlugin {

    private GameManager gameManager;
    private TabManager tabManager;
    private ActionBarTask actionBarTask;
    private BossBarTask bossBarTask;
    private ScoreboardTask scoreboardTask;

    private volatile boolean pluginEnabledFlag = false;

    private final Set<String> exemptCache = new HashSet<>();

    @Override
    public void onLoad() {
        // Ensure config defaults exist
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        // Basic initialization
        try {
            reloadConfig();
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Failed to load config: " + t.getMessage(), t);
        }

        // Initialize core managers
        this.gameManager = new GameManager(this);
        this.tabManager = new TabManager(this);
        this.actionBarTask = new ActionBarTask(this);
        this.bossBarTask = new BossBarTask(this);
        this.scoreboardTask = new ScoreboardTask(this);

        // Commands
        var gameCmd = new GameCommands(this);
        getCommand("game").setExecutor(gameCmd);
        getCommand("game").setTabCompleter(gameCmd);

        // Register listeners (listeners check pluginEnabledFlag and GameManager where needed)
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerJoinListener(this), this);
        pm.registerEvents(new PlayerDeathListener(this), this);
        pm.registerEvents(new PlayerDamageListener(this), this);
        pm.registerEvents(new RecorderCompassListener(this), this);

        // Attempt ProtocolLib injection registration; guard with try/catch
        try {
            HardcorePacketInjector.register(this);
        } catch (Throwable t) {
            getLogger().log(Level.INFO, "ProtocolLib not available or injector failed: " + t.getMessage());
        }

        // Prepare exempt cache and initial enabled flag from config
        reloadExemptCache();
        this.pluginEnabledFlag = getConfig().getBoolean("plugin-enabled", true);

        // If plugin is marked enabled in config, start systems
        if (this.pluginEnabledFlag) {
            enableSystems();
        }

        getLogger().info("SurvivalGamesPlugin v" + getDescription().getVersion() + " enabled. plugin-enabled=" + pluginEnabledFlag);
    }

    @Override
    public void onDisable() {
        // Gracefully stop running systems
        try {
            disableSystems();
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Error while disabling systems: " + t.getMessage(), t);
        }
        getLogger().info("SurvivalGamesPlugin disabled.");
    }

    /* ------------------------
       Systems management
       ------------------------ */

    public synchronized void enableSystems() {
        if (pluginEnabledFlag) {
            getLogger().info("enableSystems called but plugin already enabled.");
        }
        pluginEnabledFlag = true;

        // Start UI tasks
        try { actionBarTask.start(); } catch (Throwable t) { getLogger().warning("ActionBarTask failed to start: " + t.getMessage()); }
        try { bossBarTask.start(); } catch (Throwable t) { getLogger().warning("BossBarTask failed to start: " + t.getMessage()); }
        try { scoreboardTask.start(); } catch (Throwable t) { getLogger().warning("ScoreboardTask failed to start: " + t.getMessage()); }

        // Ensure tab refresh for all players
        try { tabManager.updateAllTabs(); } catch (Throwable t) { getLogger().warning("TabManager update failed: " + t.getMessage()); }
    }

    public synchronized void disableSystems() {
        pluginEnabledFlag = false;

        // Stop UI tasks
        try { actionBarTask.stop(); } catch (Throwable ignored) {}
        try { bossBarTask.stop(); } catch (Throwable ignored) {}
        try { scoreboardTask.stop(); } catch (Throwable ignored) {}

        // Reset match state and perform a neutral cleanup
        try {
            if (gameManager != null) gameManager.forceStop();
        } catch (Throwable t) {
            getLogger().warning("GameManager.forceStop error: " + t.getMessage());
        }

        // Clear tabs for players
        try {
            tabManager.updateAllTabs();
        } catch (Throwable ignored) {}
    }

    /* ------------------------
       Utility / accessors
       ------------------------ */

    public synchronized void reloadExemptCache() {
        exemptCache.clear();
        try {
            var list = getConfig().getStringList("exempt-users");
            if (list != null) exemptCache.addAll(list);
        } catch (Throwable t) {
            getLogger().warning("Failed to reload exempt cache: " + t.getMessage());
        }
        getLogger().info("Exempt cache reloaded: " + exemptCache);
    }

    public boolean isExempt(org.bukkit.OfflinePlayer p) {
        if (p == null) return false;
        String name = p.getName();
        return name != null && exemptCache.contains(name);
    }

    public boolean isExempt(org.bukkit.entity.Player p) {
        if (p == null) return false;
        return isExempt((org.bukkit.OfflinePlayer) p);
    }

    public boolean isExempt(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) return isExempt(player);
        OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        return isExempt(offline);
    }

    public Set<String> getExemptUsers() {
        return Set.copyOf(exemptCache);
    }

    public synchronized void setPluginEnabledFlag(boolean enabled) {
        this.pluginEnabledFlag = enabled;
        getConfig().set("plugin-enabled", enabled);
        saveConfig();
    }

    public boolean isPluginEnabledFlag() {
        return pluginEnabledFlag;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public TabManager getTabManager() {
        return tabManager;
    }

    public MatchState getMatchState() {
        try {
            if (gameManager != null) return gameManager.getMatchState();
        } catch (Throwable ignored) {}
        return MatchState.WAITING;
    }

    /* ------------------------
       Convenience helpers
       ------------------------ */

    public void logInfo(String msg) { getLogger().info(msg); }
    public void logWarn(String msg) { getLogger().warning(msg); }
    public void logSevere(String msg) { getLogger().severe(msg); }

    /* Teleport utilities pass-through for other classes */
    public TeleportUtil getTeleportUtil() {
        return new TeleportUtil(); // TeleportUtil contains only static helpers; returning new for API parity is harmless
    }
}
