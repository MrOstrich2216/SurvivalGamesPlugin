package me.mrostrich.survivalgames;

import me.mrostrich.survivalgames.state.MatchState;
import me.mrostrich.survivalgames.util.BorderUtil;
import me.mrostrich.survivalgames.util.ComponentTextUtil;
import me.mrostrich.survivalgames.util.TeleportUtil;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GameManager {

    /**
     * Legacy internal state enum kept for compatibility with files that reference GameManager.State.
     * The canonical state for the plugin is MatchState (in me.mrostrich.survivalgames.state).
     * We keep both and keep them synced to avoid compilation/order-of-patching problems while refactoring files one-by-one.
     */
    public enum State { WAITING, GRACE, FIGHT, FINAL_FIGHT, ENDED }

    private final SurvivalGamesPlugin plugin;

    /* Canonical state (MatchState) used across plugin logic going forward */
    private MatchState matchState = MatchState.WAITING;

    /* Legacy state kept for compatibility */
    private State state = State.WAITING;

    private final int graceSeconds;
    private final double initialBorderDiameter;
    private final double shrinkRateFight;
    private final double shrinkRateFinal;
    private final int finalFightThreshold;
    private final double minBorderDiameter;
    private final Set<String> exemptUsers;

    private final Set<UUID> teleportedOnJoin = new HashSet<>();
    private final Map<UUID, Location> spawnLocations = new HashMap<>();
    private final Set<UUID> alive = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> killMap = new ConcurrentHashMap<>();
    private final LinkedList<UUID> deathOrder = new LinkedList<>();

    private int graceRemaining;
    private double currentBorderDiameter;
    private BukkitRunnable graceTask;
    private BukkitRunnable borderTask;

    public GameManager(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;

        this.graceSeconds = plugin.getConfig().getInt("grace-seconds", 180);
        this.initialBorderDiameter = plugin.getConfig().getDouble("initial-border-diameter", 500.0);
        this.shrinkRateFight = plugin.getConfig().getDouble("shrink-rate-fight", 1.0);
        this.shrinkRateFinal = plugin.getConfig().getDouble("shrink-rate-final", 2.0);
        this.finalFightThreshold = plugin.getConfig().getInt("final-fight-threshold", 10);
        this.minBorderDiameter = plugin.getConfig().getDouble("min-border-diameter", 32.0);
        this.exemptUsers = new HashSet<>(plugin.getConfig().getStringList("exempt-users"));

        this.currentBorderDiameter = Math.max(initialBorderDiameter, minBorderDiameter);
        this.graceRemaining = graceSeconds;

        plugin.getLogger().info("GameManager initialized with config:");
        plugin.getLogger().info("Grace: " + graceSeconds + "s, Border: " + initialBorderDiameter +
                ", Shrink(Fight): " + shrinkRateFight + ", Shrink(Final): " + shrinkRateFinal +
                ", Threshold: " + finalFightThreshold + ", Min Border: " + minBorderDiameter +
                ", Exempt: " + exemptUsers);
    }

    /* ------------------------
       Accessors and compatibility
       ------------------------ */

    public MatchState getMatchState() {
        return matchState == null ? MatchState.WAITING : matchState;
    }

    /**
     * Set canonical MatchState; sync legacy State enum and propagate UI updates.
     */
    public synchronized void setMatchState(MatchState newState) {
        if (newState == null) newState = MatchState.WAITING;
        if (this.matchState == newState) return; // avoid redundant transitions
        this.matchState = newState;
        // sync legacy enum
        switch (newState) {
            case WAITING -> this.state = State.WAITING;
            case RUNNING -> this.state = State.FIGHT; // map RUNNING -> FIGHT for legacy
            case FINAL_FIGHT -> this.state = State.FINAL_FIGHT;
            case ENDED -> this.state = State.ENDED;
            default -> this.state = State.WAITING;
        }

        // Update tabs/overlays immediately
        try {
            if (plugin.getTabManager() != null) plugin.getTabManager().updateAllTabs();
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to update tabs after state change: " + t.getMessage());
        }
        plugin.getLogger().info("MatchState changed to " + newState + " (legacy mapped to " + this.state + ")");
    }

    /**
     * Compatibility method used by some unpatched files. Returns legacy State.
     */
    @Deprecated
    public State getState() {
        return state;
    }

    public int getGraceRemaining() { return graceRemaining; }
    public double getCurrentBorderDiameter() { return currentBorderDiameter; }
    public double getCurrentBorderRadius() { return currentBorderDiameter / 2.0; }
    public Set<UUID> getAlive() { return Collections.unmodifiableSet(alive); }
    public Map<UUID, Integer> getKills() { return Collections.unmodifiableMap(killMap); }
    public List<UUID> getDeathOrder() { return Collections.unmodifiableList(deathOrder); }
    public Set<String> getExemptUsers() { return Collections.unmodifiableSet(exemptUsers); }

    public boolean isExempt(Player p) {
        return p != null && p.getName() != null && exemptUsers.contains(p.getName());
    }

    public boolean isExempt(OfflinePlayer op) {
        return op != null && op.getName() != null && exemptUsers.contains(op.getName());
    }

    public boolean isExempt(UUID id) {
        if (id == null) return false;
        Player p = Bukkit.getPlayer(id);
        if (p != null) return isExempt(p);
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return isExempt(op);
    }

    /* ------------------------
       Join / Start / Lifecycle
       ------------------------ */

    public void onJoin(Player p) {
        if (!plugin.isPluginEnabledFlag() || state != State.WAITING || isExempt(p)) return;

        p.setGameMode(GameMode.ADVENTURE);

        if (!teleportedOnJoin.contains(p.getUniqueId())) {
            World world = Bukkit.getWorld("world");
            if (world == null && !Bukkit.getWorlds().isEmpty()) world = Bukkit.getWorlds().get(0);
            if (world != null) {
                Location spawn = world.getSpawnLocation();
                p.teleport(spawn);
                teleportedOnJoin.add(p.getUniqueId());
            }
        }
    }

    public void startGame() {
        if (!plugin.isPluginEnabledFlag()) {
            msg(Bukkit.getConsoleSender(), ChatColor.RED + "Plugin is disabled. Use /game enable first.");
            return;
        }
        if (state != State.WAITING) {
            msg(Bukkit.getConsoleSender(), ChatColor.YELLOW + "Game is not in WAITING state.");
            return;
        }

        World world = Bukkit.getWorld("world");
        if (world == null && !Bukkit.getWorlds().isEmpty()) world = Bukkit.getWorlds().get(0);
        if (world == null) {
            plugin.getLogger().severe("No valid world found to start the game.");
            return;
        }

        Location spawn = world.getSpawnLocation();
        BorderUtil.configureBorder(world, spawn, initialBorderDiameter);
        currentBorderDiameter = Math.max(initialBorderDiameter, minBorderDiameter);

        alive.clear();
        killMap.clear();
        deathOrder.clear();
        spawnLocations.clear();

        plugin.getLogger().info("Game started. Grace: " + graceSeconds + "s, Border: " + currentBorderDiameter);
        plugin.getLogger().info("Exempt users: " + exemptUsers);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isExempt(p)) {
                alive.add(p.getUniqueId());
                killMap.put(p.getUniqueId(), 0);
                Location target = TeleportUtil.randomSafeLocation(world, spawn, getCurrentBorderRadius());
                if (target != null) p.teleport(target);
                p.setGameMode(GameMode.SURVIVAL);

                // record original spawn for possible neutral resets
                spawnLocations.put(p.getUniqueId(), p.getLocation());
            } else {
                plugin.getLogger().info("Setting up exempt mod: " + p.getName());
                p.teleport(spawn);
                TeleportUtil.setupMod(p, plugin);
            }
        }

        // canonical state set and UI update
        setMatchState(MatchState.RUNNING);
        // keep legacy grace mapping
        this.state = State.GRACE;
        graceRemaining = graceSeconds;

        // start grace countdown
        if (graceTask != null) graceTask.cancel();
        graceTask = new BukkitRunnable() {
            @Override
            public void run() {
                graceRemaining--;
                if (graceRemaining <= 0) {
                    cancel();
                    onGraceEnd();
                }
            }
        };
        graceTask.runTaskTimer(plugin, 20L, 20L);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§aLet the games begin!", "", 10, 40, 10);
            p.sendActionBar(ComponentTextUtil.simpleAction("§eGrace period: " + (graceSeconds / 60) + " minutes. §cYou have only one life."));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        Bukkit.broadcastMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "The game has begun! "
                + ChatColor.GRAY + "Grace period: " + (graceSeconds / 60) + " minutes. PvP disabled.");
        Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "You have only one life. "
                + ChatColor.DARK_GRAY + "No second chances. No respawns. Just glory or defeat.");
        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Let the game begin!! "
                + ChatColor.YELLOW + "May the smartest survive.");
        teleportedOnJoin.clear();
    }

    /* ------------------------
       Grace end / Border shrink / Final fight
       ------------------------ */

    private void onGraceEnd() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        }

        Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "Grace period ended! PvP is now enabled.");

        long aliveCount = getAlive().stream()
                .filter(id -> !isExempt(id))
                .count();

        plugin.getLogger().info("Grace ended. Alive count: " + aliveCount);

        // canonical state becomes RUNNING, legacy maps to FIGHT for older code expecting GRACE->FIGHT
        setMatchState(MatchState.RUNNING);
        this.state = State.FIGHT;
        startBorderShrink(shrinkRateFight);
        plugin.getLogger().info("Grace ended. Switching to state: RUNNING -> legacy FIGHT");

        // Immediately check if final fight should begin
        checkFinalFightAcceleration();
    }

    private void startBorderShrink(double ratePerSecond) {
        if (borderTask != null) borderTask.cancel();

        borderTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state == State.ENDED || matchState == MatchState.ENDED) {
                    cancel();
                    return;
                }

                currentBorderDiameter = Math.max(minBorderDiameter, currentBorderDiameter - ratePerSecond);

                World world = Bukkit.getWorld("world");
                if (world == null && !Bukkit.getWorlds().isEmpty()) {
                    world = Bukkit.getWorlds().get(0);
                }

                if (world == null) {
                    plugin.getLogger().warning("No worlds available for border shrink.");
                    cancel();
                    return;
                }

                Location center = world.getSpawnLocation();
                WorldBorder border = world.getWorldBorder();
                border.setSize(currentBorderDiameter);
                border.setCenter(center);
                border.setWarningDistance(10);

                plugin.getLogger().info("Border shrinking to: " + currentBorderDiameter);

                if (currentBorderDiameter <= minBorderDiameter + 0.001) {
                    plugin.getLogger().info("Border reached minimum diameter: " + minBorderDiameter);
                    cancel();
                }
            }
        };

        borderTask.runTaskTimer(plugin, 20L, 20L);
    }

    public void checkFinalFightAcceleration() {
        if (matchState == MatchState.FINAL_FIGHT || state == State.FINAL_FIGHT) return;

        if (matchState == MatchState.RUNNING || state == State.FIGHT || state == State.GRACE) {
            long aliveCount = getAlive().stream()
                    .filter(id -> !isExempt(id))
                    .count();

            if (aliveCount <= finalFightThreshold) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
                }

                Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "Final fight begins! Border speeds up!");

                setMatchState(MatchState.FINAL_FIGHT);
                this.state = State.FINAL_FIGHT;
                startBorderShrink(shrinkRateFinal);

                Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
                Team redTeam = board.getTeam("finalFight");
                if (redTeam == null) {
                    redTeam = board.registerNewTeam("finalFight");
                    redTeam.setColor(org.bukkit.ChatColor.RED);
                }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (isExempt(p)) continue;
                    try {
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 1, false, false));
                        try {
                            redTeam.addEntry(p.getName());
                        } catch (Throwable ignored) {}
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    /* ------------------------
       Kills / Deaths / Leaderboards
       ------------------------ */

    public void recordKill(Player killer) {
        if (killer == null) return;
        UUID id = killer.getUniqueId();
        killMap.put(id, killMap.getOrDefault(id, 0) + 1);
        plugin.getLogger().info("Kill recorded: " + killer.getName() + " -> " + killMap.get(id));
        try {
            if (plugin.getTabManager() != null) plugin.getTabManager().updateAllTabs();
        } catch (Throwable ignored) {}
    }

    public void recordDeath(Player victim) {
        if (victim == null) return;
        UUID id = victim.getUniqueId();

        // remove from alive set and add to death order
        alive.remove(id);
        if (!deathOrder.contains(id)) deathOrder.add(id);

        // set spectator mode
        try {
            victim.setGameMode(GameMode.SPECTATOR);
        } catch (Throwable ignored) {}

        plugin.getLogger().info("Death recorded: " + victim.getName() + ". Alive remaining: " + alive.size());

        // update UI and check final fight
        try {
            if (plugin.getTabManager() != null) plugin.getTabManager().updateAllTabs();
        } catch (Throwable ignored) {}

        checkFinalFightAcceleration();

        // If only one non-exempt player remains, end the game
        long remaining = alive.stream()
                .filter(u -> !isExempt(u))
                .count();

        if (remaining <= 1) {
            endGame();
        }
    }

    public List<Map.Entry<UUID, Integer>> topKills(int n) {
        return killMap.entrySet().stream()
                .filter(entry -> !isExempt(entry.getKey()))
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(n)
                .collect(Collectors.toList());
    }

    private String nameOf(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() != null ? op.getName() : "Unknown";
    }

    private void endGame() {
        setMatchState(MatchState.ENDED);
        this.state = State.ENDED;

        if (graceTask != null) {
            graceTask.cancel();
            graceTask = null;
        }
        if (borderTask != null) {
            borderTask.cancel();
            borderTask = null;
        }

        List<UUID> aliveList = new ArrayList<>(alive);
        String first = aliveList.isEmpty() ? "Unknown" : nameOf(aliveList.get(0));
        String second = deathOrder.size() >= 1 ? nameOf(deathOrder.get(deathOrder.size() - 1)) : "Unknown";
        String third = deathOrder.size() >= 2 ? nameOf(deathOrder.get(deathOrder.size() - 2)) : "Unknown";

        plugin.getLogger().info("Game ended. Winners: 1=" + first + " 2=" + second + " 3=" + third);

        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                p.sendTitle("§cThe game has ended.", "", 10, 40, 10);
            } catch (Throwable ignored) {}
            try {
                p.sendActionBar(ComponentTextUtil.simpleAction("§5The nightmare is over. §bMay the last one standing win."));
            } catch (Throwable ignored) {}

            try {
                PlayerInventory inv = p.getInventory();
                inv.clear();
                inv.setArmorContents(null);
                try { inv.setExtraContents(null); } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            try { p.removePotionEffect(PotionEffectType.GLOWING); } catch (Throwable ignored) {}

            if (isExempt(p)) {
                try { p.setGameMode(GameMode.SURVIVAL); } catch (Throwable ignored) {}
                try { p.removePotionEffect(PotionEffectType.INVISIBILITY); } catch (Throwable ignored) {}
                for (Player other : Bukkit.getOnlinePlayers()) {
                    try { other.showPlayer(plugin, p); } catch (Throwable ignored) {}
                }
            }
        }

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team redTeam = board.getTeam("finalFight");
        if (redTeam != null) {
            try { redTeam.unregister(); } catch (Throwable ignored) {}
        }

        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count++ >= 5) {
                    cancel();
                    return;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    } catch (Throwable ignored) {}
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void forceStop() {
        if (graceTask != null) {
            graceTask.cancel();
            graceTask = null;
        }
        if (borderTask != null) {
            borderTask.cancel();
            borderTask = null;
        }

        World w = Bukkit.getWorld("world");
        if (w == null && !Bukkit.getWorlds().isEmpty()) w = Bukkit.getWorlds().get(0);
        if (w != null) {
            try { w.getWorldBorder().setSize(initialBorderDiameter); } catch (Throwable ignored) {}
        }

        setMatchState(MatchState.ENDED);
        this.state = State.ENDED;
        alive.clear();
        killMap.clear();
        deathOrder.clear();
        spawnLocations.clear();

        plugin.getLogger().info("Force stop executed, border reset, state set to ENDED.");
    }

    /* ------------------------
       Neutral / Utility
       ------------------------ */

    public void neutralGame(CommandSender sender) {
        // cancel grace task if running
        if (graceTask != null) {
            graceTask.cancel();
            graceTask = null;
        }

        // Teleport players back to their recorded spawn locations
        for (UUID id : new ArrayList<>(spawnLocations.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                Location originalSpawn = spawnLocations.get(id);
                if (originalSpawn != null) p.teleport(originalSpawn);
            }
        }

        // Clear match state
        alive.clear();
        killMap.clear();
        deathOrder.clear();
        spawnLocations.clear();

        // Set everyone to ADVENTURE except exempt users
        int changed = 0;
        int skipped = 0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isExempt(p)) {
                // Exempt users remain in SURVIVAL and retain visibility/invisibility cleanup
                skipped++;
                try { p.setGameMode(GameMode.SURVIVAL); } catch (Throwable ignored) {}
                try { p.removePotionEffect(PotionEffectType.INVISIBILITY); } catch (Throwable ignored) {}
                for (Player other : Bukkit.getOnlinePlayers()) {
                    try { other.showPlayer(plugin, p); } catch (Throwable ignored) {}
                }
                plugin.getLogger().info("Neutral: skipped exempt user " + p.getName());
                continue;
            }

            try {
                p.setGameMode(GameMode.ADVENTURE);
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
                for (Player other : Bukkit.getOnlinePlayers()) {
                    try { other.showPlayer(plugin, p); } catch (Throwable ignored) {}
                }
                changed++;
                plugin.getLogger().info("Neutral: set ADVENTURE for " + p.getName());
            } catch (Throwable t) {
                plugin.getLogger().warning("Neutral: failed changing gamemode for " + p.getName() + " - " + t.getMessage());
            }
        }

        // Reset border to initial state
        World world = Bukkit.getWorld("world");
        if (world == null && !Bukkit.getWorlds().isEmpty()) world = Bukkit.getWorlds().get(0);
        if (world != null) {
            Location spawn = world.getSpawnLocation();
            BorderUtil.configureBorder(world, spawn, initialBorderDiameter);
            currentBorderDiameter = initialBorderDiameter;
        }

        // Set state and grace
        setMatchState(MatchState.WAITING);
        this.state = State.WAITING;
        graceRemaining = graceSeconds;

        // Feedback
        sender.sendMessage(ChatColor.GREEN + "Game fully reset. State set to WAITING. Neutral applied: "
                + changed + " players set to ADVENTURE, " + skipped + " exempt/skipped.");
        plugin.getLogger().info("neutralGame executed by " + (sender instanceof Player ? ((Player) sender).getName() : "console")
                + " — changed=" + changed + " skipped=" + skipped);
    }

    private void msg(CommandSender sender, String text) {
        if (sender != null) sender.sendMessage(text);
    }
}
