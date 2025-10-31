package me.mrostrich.survivalgames;

import me.mrostrich.survivalgames.commands.GameCommands;
import me.mrostrich.survivalgames.inject.HardcorePacketInjector;
import me.mrostrich.survivalgames.listeners.*;
import me.mrostrich.survivalgames.state.MatchState;
import me.mrostrich.survivalgames.ui.*;
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
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        try {
            reloadConfig();
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Failed to load config: " + t.getMessage(), t);
        }

        this.gameManager = new GameManager(this);
        this.tabManager = new TabManager(this);
        this.actionBarTask = new ActionBarTask(this);
        this.bossBarTask = new BossBarTask(this);
        this.scoreboardTask = new ScoreboardTask(this);

        var gameCmd = new GameCommands(this);
        getCommand("game").setExecutor(gameCmd);
        getCommand("game").setTabCompleter(gameCmd);

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerJoinListener(this), this);
        pm.registerEvents(new PlayerDeathListener(this), this);
        pm.registerEvents(new PlayerDamageListener(this), this);
        pm.registerEvents(new RecorderCompassListener(this), this);

        try {
            HardcorePacketInjector.register(this);
        } catch (Throwable t) {
            getLogger().log(Level.INFO, "ProtocolLib not available or injector failed: " + t.getMessage());
        }

        reloadExemptCache();
        this.pluginEnabledFlag = getConfig().getBoolean("plugin-enabled", true);

        if (this.pluginEnabledFlag) {
            enableSystems();
        }

        getLogger().info("SurvivalGamesPlugin v" + getDescription().getVersion() + " enabled. plugin-enabled=" + pluginEnabledFlag);
    }

    @Override
    public void onDisable() {
        try {
            disableSystems();
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Error while disabling systems: " + t.getMessage(), t);
        }
        getLogger().info("SurvivalGamesPlugin disabled.");
    }

    public synchronized void enableSystems() {
        if (pluginEnabledFlag) {
            getLogger().info("enableSystems called but plugin already enabled.");
        }
        pluginEnabledFlag = true;

        try { actionBarTask.start(); } catch (Throwable t) { getLogger().warning("ActionBarTask failed to start: " + t.getMessage()); }
        try { bossBarTask.start(); } catch (Throwable t) { getLogger().warning("BossBarTask failed to start: " + t.getMessage()); }
        try { scoreboardTask.start(); } catch (Throwable t) { getLogger().warning("ScoreboardTask failed to start: " + t.getMessage()); }

        try { tabManager.updateAllTabs(); } catch (Throwable t) { getLogger().warning("TabManager update failed: " + t.getMessage()); }
    }

    public synchronized void disableSystems() {
        pluginEnabledFlag = false;

        try { actionBarTask.stop(); } catch (Throwable ignored) {}
        try { bossBarTask.stop(); } catch (Throwable ignored) {}
        try { scoreboardTask.stop(); } catch (Throwable ignored) {}

        try {
            if (gameManager != null) gameManager.forceStop();
        } catch (Throwable t) {
            getLogger().warning("GameManager.forceStop error: " + t.getMessage());
        }

        try {
            tabManager.updateAllTabs();
        } catch (Throwable ignored) {}
    }

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

    public boolean isExempt(OfflinePlayer p) {
        if (p == null) return false;
        String name = p.getName();
        return name != null && exemptCache.contains(name);
    }

    public boolean isExempt(Player p) {
        if (p == null) return false;
        return isExempt((OfflinePlayer) p);
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

    public int getGraceRemaining() {
        return gameManager != null ? gameManager.getGraceRemaining() : 0;
    }

    public void logInfo(String msg) { getLogger().info(msg); }
    public void logWarn(String msg) { getLogger().warning(msg); }
    public void logSevere(String msg) { getLogger().severe(msg); }

    public TeleportUtil getTeleportUtil() {
        return new TeleportUtil();
    }
}
