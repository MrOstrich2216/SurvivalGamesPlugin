package me.mrostrich.uhcrunplugin.ui;

import me.mrostrich.uhcrunplugin.GameManager;
import me.mrostrich.uhcrunplugin.UhcRunPlugin;
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

    private final UhcRunPlugin plugin;
    private BukkitRunnable task;

    private static final String[] ENTRY_KEYS = new String[]{
            "§0","§1","§2","§3","§4","§5","§6","§7","§8","§9",
            "§a","§b","§c","§d","§e","§f"
    };

    public ScoreboardTask(UhcRunPlugin plugin) {
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

                    Objective obj = board.getObjective("uhc");
                    if (obj == null) {
                        obj = board.registerNewObjective("uhc", "dummy", Component.text("§b§lUHC RUN")); // Cyan + Bold
                    }
                    obj.setDisplaySlot(DisplaySlot.SIDEBAR);

                    int line = 15;

                    setLine(board, obj, line--, "§b§lUltra Hardcore Run");
                    setLine(board, obj, line--, "§eGaming Competition");
                    setLine(board, obj, line--, "§7-by MrOstrich2216");
                    setLine(board, obj, line--, "§7--------------------");

                    setLine(board, obj, line--, "§ePlayer: §f" + p.getName()); // White

                    String aliveColor = switch (gm.getState()) {
                        case GRACE -> "§a";       // Green
                        case FIGHT -> "§6";       // Orange
                        case FINAL_FIGHT -> "§c"; // Red
                        default -> "§f";
                    };
                    setLine(board, obj, line--, aliveColor + "Players Left: §f" + gm.getAliveCount());

                    setLine(board, obj, line--, "§eTop Kills:");

                    List<Map.Entry<UUID, Integer>> top = gm.topKills(3).stream()
                            .filter(entry -> {
                                OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                                return !"Recorder".equalsIgnoreCase(op.getName());
                            })
                            .collect(Collectors.toList());

                    for (int i = 0; i < 3; i++) {
                        String content;
                        if (i < top.size()) {
                            UUID id = top.get(i).getKey();
                            int k = top.get(i).getValue();
                            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                            String name = (op.getName() != null) ? op.getName() : "Unknown";
                            String rankColor = switch (i) {
                                case 0 -> "§b"; // Cyan
                                case 1 -> "§e"; // Yellow
                                case 2 -> "§7"; // Gray
                                default -> "§f";
                            };
                            content = rankColor + (i + 1) + ". " + name + " - " + k;
                        } else {
                            content = "§7---";
                        }
                        setLine(board, obj, line--, content);
                    }

                    if (gm.getState() == GameManager.State.ENDED) {
                        setLine(board, obj, line--, "§ePlacements:");

                        List<UUID> aliveList = gm.getAlive().stream()
                                .filter(id -> {
                                    OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                                    return !"Recorder".equalsIgnoreCase(op.getName());
                                })
                                .collect(Collectors.toList());

                        List<UUID> deaths = gm.getDeathOrder().stream()
                                .filter(id -> {
                                    OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                                    return !"Recorder".equalsIgnoreCase(op.getName());
                                })
                                .collect(Collectors.toList());

                        String first = aliveList.isEmpty() ? "Unknown" : Bukkit.getOfflinePlayer(aliveList.get(0)).getName();
                        String second = deaths.size() >= 1 ? Bukkit.getOfflinePlayer(deaths.get(deaths.size() - 1)).getName() : "Unknown";
                        String third = deaths.size() >= 2 ? Bukkit.getOfflinePlayer(deaths.get(deaths.size() - 2)).getName() : "Unknown";

                        setLine(board, obj, line--, "§61st: " + first);   // Gold
                        setLine(board, obj, line--, "§72nd: " + second);  // Silver
                        setLine(board, obj, line--, "§43rd: " + third);   // Bronze
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