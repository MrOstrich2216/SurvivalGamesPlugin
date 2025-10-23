package me.mrostrich.survivalgames;

import me.mrostrich.survivalgames.util.BorderUtil;
import me.mrostrich.survivalgames.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Sound;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GameManager {

    public enum State { WAITING, GRACE, FIGHT, FINAL_FIGHT, ENDED }

    private final SurvivalGamesPlugin plugin;
    private State state = State.WAITING;

    // Config

    private final int graceSeconds;
    private final double initialBorderDiameter;
    private final double shrinkRateFight;
    private final double shrinkRateFinal;
    private final int finalFightThreshold;
    private final double minBorderDiameter;
    private final Set<String> exemptUsers;

    // Runtime
    private final Set<UUID> teleportedOnJoin = new HashSet<>();
    private final Map<UUID, Location> spawnLocations = new HashMap<>();
    private final Set<UUID> alive = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final LinkedList<UUID> deathOrder = new LinkedList<>();
    private int graceRemaining;
    private double currentBorderDiameter;
    private BukkitRunnable graceTask;
    private BukkitRunnable borderTask;
    public void setState(State newState) {
        this.state = newState;
    }

    public GameManager(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;

        // Load config values
        this.graceSeconds = plugin.getConfig().getInt("grace-seconds");
        this.initialBorderDiameter = plugin.getConfig().getDouble("initial-border-diameter");
        this.shrinkRateFight = plugin.getConfig().getDouble("shrink-rate-fight");
        this.shrinkRateFinal = plugin.getConfig().getDouble("shrink-rate-final");
        this.finalFightThreshold = plugin.getConfig().getInt("final-fight-threshold");
        this.minBorderDiameter = plugin.getConfig().getDouble("min-border-diameter");
        this.exemptUsers = new HashSet<>(plugin.getConfig().getStringList("exempt-users"));

        // Initialize runtime values
        this.currentBorderDiameter = initialBorderDiameter;
        this.graceRemaining = graceSeconds;

        // ✅ Log loaded config values
        plugin.getLogger().info("Config loaded: graceSeconds=" + graceSeconds +
                ", initialBorderDiameter=" + initialBorderDiameter +
                ", shrinkRateFight=" + shrinkRateFight +
                ", shrinkRateFinal=" + shrinkRateFinal +
                ", finalFightThreshold=" + finalFightThreshold +
                ", minBorderDiameter=" + minBorderDiameter +
                ", exemptUsers=" + exemptUsers);
    }


    public State getState() { return state; }
    public int getGraceRemaining() { return graceRemaining; }
    public int getAliveCount() {
        return (int) alive.stream()
                .map(Bukkit::getOfflinePlayer)
                .filter(op -> !exemptUsers.contains(op.getName()))
                .count();
    }
    public double getCurrentBorderDiameter() { return currentBorderDiameter; }
    public double getCurrentBorderRadius() { return currentBorderDiameter / 2.0; }
    public Set<UUID> getAlive() { return Collections.unmodifiableSet(alive); }
    public Map<UUID, Integer> getKills() { return Collections.unmodifiableMap(kills); }
    public List<UUID> getDeathOrder() { return Collections.unmodifiableList(deathOrder); }
    public Set<String> getExemptUsers() { return Collections.unmodifiableSet(exemptUsers); }

    public boolean isExempt(Player p) {
        return exemptUsers.contains(p.getName());
    }

    public void onJoin(Player p) {
        if (!plugin.isPluginEnabledFlag() || state != State.WAITING || isExempt(p)) return;

        p.setGameMode(GameMode.ADVENTURE);

        if (!teleportedOnJoin.contains(p.getUniqueId())) {
            Location spawn = Bukkit.getWorld("world").getSpawnLocation();
            p.teleport(spawn);
            teleportedOnJoin.add(p.getUniqueId());
        }
    }

    public void startGame() {
        if (!plugin.isPluginEnabledFlag()) {
            msg(Bukkit.getConsoleSender(), ChatColor.RED + "Plugin is disabled. Use /uhc enable first.");
            return;
        }
        if (state != State.WAITING) {
            msg(Bukkit.getConsoleSender(), ChatColor.YELLOW + "Game is not in WAITING state.");
            return;
        }

        World world = Bukkit.getWorld("world");
        if (world == null) world = Bukkit.getWorlds().get(0);
        Objects.requireNonNull(world, "World cannot be null");

        // Setup border
        Location spawn = world.getSpawnLocation();
        BorderUtil.configureBorder(world, spawn, initialBorderDiameter);
        this.currentBorderDiameter = initialBorderDiameter;

        // Prepare runtime
        alive.clear();
        kills.clear();
        deathOrder.clear();

        List<String> exempt = plugin.getConfig().getStringList("exempt-users");

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!exempt.contains(p.getName())) {
                alive.add(p.getUniqueId());
                kills.put(p.getUniqueId(), 0);

                Location target = TeleportUtil.randomSafeLocation(world, spawn, getCurrentBorderRadius());
                if (target != null) {
                    p.teleport(target);
                }
                p.setGameMode(GameMode.SURVIVAL);
            } else {
                // Full mod setup: teleport + invis + compass
                Bukkit.getLogger().info("Recorder teleported to spawn: " + spawn);
                p.teleport(spawn);
                TeleportUtil.setupMod(p, plugin);
            }
        }

        // Start grace
        this.state = State.GRACE;
        this.graceRemaining = graceSeconds;

        // UI: Title + ActionBar + Sound
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§aLet the games begin!", "", 10, 40, 10);
            p.sendActionBar("§eGrace period: " + (graceSeconds / 60) + " minutes. §cYou have only one life.");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        // Grace countdown task
        if (graceTask != null) graceTask.cancel();
        graceTask = new BukkitRunnable() {
            @Override
            public void run() {
                graceRemaining--;
                if (graceRemaining <= 0) {
                    this.cancel();
                    onGraceEnd();
                }
            }
        };
        graceTask.runTaskTimer(plugin, 20L, 20L);

        // Legacy chat messages (preserved)
        Bukkit.broadcastMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "The game has begun! "
                + ChatColor.GRAY + "Grace period: " + (graceSeconds / 60) + " minutes. PvP disabled.");
        Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "You have only one life. "
                + ChatColor.DARK_GRAY + "No second chances. No respawns. Just glory or defeat.");
        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Let the game begin!! "
                + ChatColor.YELLOW + "May the smartest survive.");
        teleportedOnJoin.clear();
    }


    private void onGraceEnd() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        }

        Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "Grace period ended! PvP is now enabled.");

        long aliveCount = getAlive().stream()
                .filter(id -> !plugin.isExempt(id))
                .count();

        if (aliveCount <= finalFightThreshold) {
            this.state = State.FINAL_FIGHT;
            startBorderShrink(shrinkRateFinal);
            Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Final fight begins! "
                    + ChatColor.GRAY + "Border shrinking faster.");
        } else {
            this.state = State.FIGHT;
            startBorderShrink(shrinkRateFight);
        }
    }


    private void startBorderShrink(double ratePerSecond) {
        if (borderTask != null) borderTask.cancel();

        borderTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state == State.ENDED) {
                    this.cancel();
                    return;
                }

                currentBorderDiameter = Math.max(minBorderDiameter, currentBorderDiameter - ratePerSecond);

                World world = Bukkit.getWorld("world");
                if (world == null) {
                    List<World> worlds = Bukkit.getWorlds();
                    if (worlds.isEmpty()) {
                        plugin.getLogger().warning("No worlds available for border shrink.");
                        this.cancel();
                        return;
                    }
                    world = worlds.get(0);
                }

                Location center = world.getSpawnLocation();
                WorldBorder border = world.getWorldBorder();
                border.setSize(currentBorderDiameter);
                border.setCenter(center);
                border.setWarningDistance(10);

                if (currentBorderDiameter <= minBorderDiameter + 0.001) {
                    plugin.getLogger().info("Border reached minimum diameter: " + minBorderDiameter);
                    this.cancel();
                }
            }
        };

        borderTask.runTaskTimer(plugin, 20L, 20L); // tick every second
    }

    public void checkFinalFightAcceleration() {
        if (state == State.FINAL_FIGHT) return; // Already in final fight — skip

        if (state == State.FIGHT || state == State.GRACE) {
            long aliveCount = getAlive().stream()
                    .filter(id -> !plugin.isExempt(id))
                    .count();

            if (aliveCount <= finalFightThreshold) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
                }

                Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "Final fight begins! Border speeds up!");

                this.state = State.FINAL_FIGHT;
                startBorderShrink(shrinkRateFinal);

                Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
                Team redTeam = board.getTeam("finalFight");
                if (redTeam == null) {
                    redTeam = board.registerNewTeam("finalFight");
                    redTeam.setColor(ChatColor.RED);
                }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!plugin.isExempt(p.getUniqueId())) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 1, false, false));
                        redTeam.addEntry(p.getName());
                    }
                }
            }
        }
    }



    public void recordKill(Player killer) {
        if (killer == null) return;
        kills.put(killer.getUniqueId(), kills.getOrDefault(killer.getUniqueId(), 0) + 1);
    }

    public void recordDeath(Player victim) {
        UUID id = victim.getUniqueId();
        alive.remove(id);
        deathOrder.add(id);
        victim.setGameMode(GameMode.SPECTATOR); // hardcore-style spectate
        checkFinalFightAcceleration();

        if (alive.size() == 1) {
            endGame();
        }
    }

    private String nameOf(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() != null ? op.getName() : "Unknown";
    }

    private void endGame() {
        this.state = State.ENDED;

        // Stop tasks
        if (graceTask != null) graceTask.cancel();
        if (borderTask != null) borderTask.cancel();

        // Compute placements
        List<UUID> aliveList = new ArrayList<>(alive);
        String first = aliveList.isEmpty() ? "Unknown" : nameOf(aliveList.get(0));
        String second = deathOrder.size() >= 1 ? nameOf(deathOrder.get(deathOrder.size() - 1)) : "Unknown";
        String third = deathOrder.size() >= 2 ? nameOf(deathOrder.get(deathOrder.size() - 2)) : "Unknown";

        // UI: Title + ActionBar + Inventory wipe
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§cThe game has ended.", "", 10, 40, 10);
            p.sendActionBar("§5The nightmare is over. §bMay the last one standing win.");
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            p.getInventory().setExtraContents(null);
            p.removePotionEffect(PotionEffectType.GLOWING);
        }

        // Remove red outline team
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team redTeam = board.getTeam("finalFight");
        if (redTeam != null) redTeam.unregister();

        // Play fireworks + level-up sound 5 times
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count++ >= 5) {
                    cancel();
                    return;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 5 times, once per second
    }

    public void forceStop() {
        if (graceTask != null) graceTask.cancel();
        if (borderTask != null) borderTask.cancel();
        World w = Bukkit.getWorld("world");
        if (w == null && !Bukkit.getWorlds().isEmpty()) w = Bukkit.getWorlds().get(0);
        if (w != null) {
            w.getWorldBorder().setSize(initialBorderDiameter);
        }
        this.state = State.ENDED;
    }
    public void neutralGame(CommandSender sender) {
        // Cancel grace task if running
        if (graceTask != null) {
            graceTask.cancel();
            graceTask = null;
        }

        // Reset runtime data
        alive.clear();
        kills.clear();
        deathOrder.clear();
        spawnLocations.clear();

        //Teleport Players to Spawn Location
        for (UUID id : spawnLocations.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                Location originalSpawn = spawnLocations.get(id);
                p.teleport(originalSpawn);
            }
        }
        spawnLocations.clear();

        // Reset exempt users
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isExempt(p)) {
                p.setGameMode(GameMode.SURVIVAL);
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
                for (Player other : Bukkit.getOnlinePlayers()) {
                    other.showPlayer(plugin, p);
                }
            }
        }

        // Reset border
        World world = Bukkit.getWorld("world");
        if (world == null) world = Bukkit.getWorlds().get(0);
        Location spawn = world.getSpawnLocation();
        BorderUtil.configureBorder(world, spawn, initialBorderDiameter);
        currentBorderDiameter = initialBorderDiameter;

        // Reset state
        state = State.WAITING;
        graceRemaining = graceSeconds;

        sender.sendMessage(ChatColor.GREEN + "Game fully reset. State set to WAITING.");
    }


    public List<Map.Entry<UUID, Integer>> topKills(int n) {
        return kills.entrySet().stream()
                .filter(entry -> {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                    return !"Recorder".equalsIgnoreCase(op.getName());
                })
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(n)
                .collect(Collectors.toList());
    }
    private void msg(CommandSender sender, String text) {
        sender.sendMessage(text);
    }
}
