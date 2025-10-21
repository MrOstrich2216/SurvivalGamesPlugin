package me.mrostrich.survivalgames.ui;

import me.mrostrich.survivalgames.GameManager;
import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

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
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isPluginEnabledFlag()) return;

                GameManager gm = plugin.getGameManager();

                for (Player p : Bukkit.getOnlinePlayers()) {
                    ScoreboardManager mgr = Bukkit.getScoreboardManager();
                    if (mgr == null) continue;
                    Scoreboard board = mgr.getNewScoreboard();

                    Objective obj = board.getObjective("survival");
                    if (obj == null) {
                        obj = board.registerNewObjective("survival", Criteria.DUMMY, Component.text("§b§lSurvival Games"));
                    }
                    obj.setDisplaySlot(DisplaySlot.SIDEBAR);

                    int line = 15;
                    setLine(board, obj, line--, "§e   Survival Games");
                    setLine(board, obj, line--, "§7A game by §fMrOstrich2216");
                    setLine(board, obj, line--, "§7--------------------");

                    setLine(board, obj, line--, "§ePlayer: §f" + p.getName());

                    String aliveColor = switch (gm.getState()) {
                        case GRACE -> "§a";
                        case FIGHT -> "§6";
                        case FINAL_FIGHT -> "§4§l";
                        default -> "§f";
                    };
                    setLine(board, obj, line--, aliveColor + "Players Left: §f" + gm.getAliveCount());

                    setLine(board, obj, line--, "§eTop Killer:");

                    Map.Entry<UUID, Integer> topKiller = gm.topKills(1).stream()
                            .filter(entry -> !plugin.isExempt(entry.getKey()))
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

                    if (gm.getState() == GameManager.State.ENDED) {
                        setLine(board, obj, line--, "§eFinal Standings:");

                        List<UUID> aliveList = gm.getAlive().stream()
                                .filter(id -> !plugin.isExempt(id))
                                .collect(Collectors.toList());

                        List<UUID> deaths = gm.getDeathOrder().stream()
                                .filter(id -> !plugin.isExempt(id))
                                .collect(Collectors.toList());

                        String first = aliveList.isEmpty() ? "Unknown" : Bukkit.getOfflinePlayer(aliveList.get(0)).getName();
                        String second = deaths.size() >= 1 ? Bukkit.getOfflinePlayer(deaths.get(deaths.size() - 1)).getName() : "Unknown";
                        String third = deaths.size() >= 2 ? Bukkit.getOfflinePlayer(deaths.get(deaths.size() - 2)).getName() : "Unknown";

                        setLine(board, obj, line--, "§61st: " + first);
                        setLine(board, obj, line--, "§72nd: " + second);
                        setLine(board, obj, line--, "§43rd: " + third);
                    }

                    setLine(board, obj, line--, "§7--------------------");

                    p.setScoreboard(board);
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void setLine(Scoreboard board, Objective obj, int score, String content) {
        String entryKey = keyFor(score);
        Team team = board.getTeam("line" + score);
        if (team == null) {
            team = board.registerNewTeam("line" + score);
            team.addEntry(entryKey);
        }
        team.prefix(Component.text(content));
        obj.getScore(entryKey).setScore(score);
    }

    private String keyFor(int score) {
        int idx = Math.max(0, Math.min(ENTRY_KEYS.length - 1, score % ENTRY_KEYS.length));
        return ENTRY_KEYS[idx] + ChatColor.RESET;
    }
}