package me.mrostrich.survivalgames.ui;

import me.mrostrich.survivalgames.GameManager;
import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import me.mrostrich.survivalgames.state.MatchState;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ScoreboardTask {

    private final SurvivalGamesPlugin plugin;
    private BukkitRunnable task;

    private static final String[] ENTRY_KEYS = new String[]{
            "§0","§1","§2","§3","§4","§5","§6","§7","§8","§9",
            "§a","§b","§c","§d","§e","§f"
    };

    public ScoreboardTask(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        plugin.getLogger().info("ScoreboardTask started.");
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isPluginEnabledFlag()) return;

                GameManager gm = plugin.getGameManager();
                if (gm == null) return;

                ScoreboardManager mgr = Bukkit.getScoreboardManager();
                if (mgr == null) return;

                for (Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        Scoreboard board = mgr.getNewScoreboard();

                        Objective obj = board.getObjective("survival");
                        if (obj == null) {
                            obj = board.registerNewObjective("survival", Criteria.DUMMY, Component.text("§b§lSurvival Games"));
                        }
                        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

                        int line = 15;
                        setLine(board, obj, line--, "§7A game by §fMrOstrich2216");
                        setLine(board, obj, line--, "§7--------------------");

                        setLine(board, obj, line--, "§ePlayer: §f" + p.getName());

                        // Phase label using canonical MatchState where possible
                        MatchState matchState = plugin.getMatchState();
                        String phaseLabel;
                        switch (matchState) {
                            case FIGHT -> {
                                if (gm.getState() == GameManager.State.GRACE) {
                                    phaseLabel = "§aGrace Period";
                                } else {
                                    phaseLabel = "§6Fight Phase";
                                }
                            }
                            case FINAL_FIGHT -> phaseLabel = "§4§lFinal Fight";
                            case ENDED -> phaseLabel = "§7Game Over";
                            case WAITING -> {
                                if (gm.getState() == GameManager.State.GRACE) phaseLabel = "§aGrace Period";
                                else phaseLabel = "§fWaiting";
                            }
                            default -> {
                                if (gm.getState() == GameManager.State.GRACE) phaseLabel = "§aGrace Period";
                                else phaseLabel = "§fWaiting";
                            }
                        }

                        setLine(board, obj, line--, "§ePhase: " + phaseLabel);

                        // Border diameter
                        double border = gm.getCurrentBorderDiameter();
                        setLine(board, obj, line--, "§eBorder: §f" + (int) border + " blocks");

                        // Alive count color based on phase
                        String aliveColor;
                        switch (matchState) {
                            case FIGHT -> aliveColor = "§6";
                            case FINAL_FIGHT -> aliveColor = "§4§l";
                            case ENDED -> aliveColor = "§7";
                            case WAITING -> aliveColor = "§a";
                            default -> aliveColor = "§a";
                        }

                        int aliveCount = (int) gm.getAlive().stream()
                                .filter(id -> !plugin.isExempt(Bukkit.getPlayer(id)) && !plugin.isExempt(Bukkit.getOfflinePlayer(id)))
                                .count();

                        setLine(board, obj, line--, aliveColor + "Players Left: §f" + aliveCount);

                        // Top killer
                        setLine(board, obj, line--, "§eTop Killer:");

                        Map.Entry<UUID, Integer> topKiller = gm.topKills(1).stream()
                                .filter(entry -> !plugin.isExempt(Bukkit.getPlayer(entry.getKey())) && !plugin.isExempt(Bukkit.getOfflinePlayer(entry.getKey())))
                                .findFirst()
                                .orElse(null);

                        String killerName = "Unknown";
                        int killCount = 0;

                        if (topKiller != null) {
                            OfflinePlayer op = Bukkit.getOfflinePlayer(topKiller.getKey());
                            if (op.getName() != null) killerName = op.getName();
                            killCount = topKiller.getValue();
                        }

                        setLine(board, obj, line--, "§c" + killerName + " §6- " + killCount);

                        // Final standings
                        if (matchState == MatchState.ENDED || gm.getState() == GameManager.State.ENDED) {
                            setLine(board, obj, line--, "§eFinal Standings:");

                            List<UUID> aliveList = gm.getAlive().stream()
                                    .filter(id -> {
                                        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                                        String n = op.getName();
                                        return n != null && !plugin.isExempt(Bukkit.getOfflinePlayer(id));
                                    })
                                    .collect(Collectors.toList());

                            List<UUID> deaths = gm.getDeathOrder().stream()
                                    .filter(id -> {
                                        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                                        String n = op.getName();
                                        return n != null && !plugin.isExempt(Bukkit.getOfflinePlayer(id));
                                    })
                                    .collect(Collectors.toList());

                            String first = "Unknown";
                            if (!aliveList.isEmpty()) {
                                OfflinePlayer op = Bukkit.getOfflinePlayer(aliveList.get(0));
                                if (op.getName() != null) first = op.getName();
                            }

                            String second = "Unknown";
                            if (deaths.size() >= 1) {
                                OfflinePlayer op = Bukkit.getOfflinePlayer(deaths.get(deaths.size() - 1));
                                if (op.getName() != null) second = op.getName();
                            }

                            String third = "Unknown";
                            if (deaths.size() >= 2) {
                                OfflinePlayer op = Bukkit.getOfflinePlayer(deaths.get(deaths.size() - 2));
                                if (op.getName() != null) third = op.getName();
                            }

                            setLine(board, obj, line--, "§61st: " + first);
                            setLine(board, obj, line--, "§72nd: " + second);
                            setLine(board, obj, line--, "§43rd: " + third);
                        }

                        setLine(board, obj, line--, "§7--------------------");

                        p.setScoreboard(board);
                    } catch (Throwable t) {
                        plugin.getLogger().warning("Scoreboard update failed for player " + p.getName() + ": " + t.getMessage());
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

    private void setLine(Scoreboard board, Objective obj, int score, String content) {
        String entryKey = keyFor(score);
        String teamName = "line" + score;
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
            team.addEntry(entryKey);
        } else {
            if (!team.getEntries().contains(entryKey)) team.addEntry(entryKey);
        }
        team.prefix(Component.text(content));
        obj.getScore(entryKey).setScore(score);
    }

    private String keyFor(int score) {
        int idx = Math.floorMod(score, ENTRY_KEYS.length);
        return ENTRY_KEYS[idx] + ChatColor.RESET;
    }
}