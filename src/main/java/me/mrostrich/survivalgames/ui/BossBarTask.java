package me.mrostrich.survivalgames.ui;

import me.mrostrich.survivalgames.GameManager;
import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import me.mrostrich.survivalgames.state.MatchState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class BossBarTask {
    private final SurvivalGamesPlugin plugin;
    private BukkitRunnable task;
    private final BossBar bossBar;
    private final double initialRadius;

    public BossBarTask(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;
        this.bossBar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
        this.initialRadius = plugin.getConfig().getDouble("initial-border-diameter", 500.0) / 2.0;
    }

    public void start() {
        stop();
        plugin.getLogger().info("BossBarTask started.");
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isPluginEnabledFlag()) return;

                GameManager gm = plugin.getGameManager();
                if (gm == null) return;

                World world = Bukkit.getWorld("world");
                if (world == null && !Bukkit.getWorlds().isEmpty()) world = Bukkit.getWorlds().get(0);
                if (world == null) return;

                bossBar.removeAll();

                for (Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        if (!plugin.isExempt(p)) {
                            bossBar.addPlayer(p);
                        }
                    } catch (Throwable ignored) {}
                }

                // Detect legacy GRACE state first (GameManager still tracks GRACE for countdown)
                if (gm.getState() == GameManager.State.GRACE) {
                    bossBar.setVisible(true);
                    bossBar.setColor(BarColor.BLUE);

                    int secs = gm.getGraceRemaining();
                    double max = Math.max(1, plugin.getConfig().getInt("grace-seconds", 180));
                    double progress = Math.max(0.0, Math.min(1.0, secs / max));

                    double border = world.getWorldBorder().getSize();
                    String title = "§bGrace Period: §f" + formatMMSS(secs)
                            + ChatColor.GRAY + " | Zone: " + String.format("%.0f", border) + "m";

                    bossBar.setTitle(title);
                    bossBar.setProgress(progress);
                    return;
                }

                // Otherwise use canonical MatchState
                MatchState state = plugin.getMatchState();

                switch (state) {
                    case WAITING, ENDED -> {
                        bossBar.setVisible(false);
                    }

                    case FIGHT -> {
                        bossBar.setVisible(true);
                        bossBar.setColor(BarColor.RED);

                        double border = world.getWorldBorder().getSize();
                        int aliveCount = (int) gm.getAlive().stream()
                                .filter(id -> !plugin.isExempt(id))
                                .count();

                        String title = "§cFight Phase" + ChatColor.GRAY + " | Zone: §f" + String.format("%.0f", border)
                                + " blocks" + ChatColor.GRAY + " | Alive: §f" + aliveCount;

                        bossBar.setTitle(title);

                        double progress = 1.0 - Math.max(0.0, Math.min(1.0,
                                gm.getCurrentBorderRadius() / initialRadius));
                        bossBar.setProgress(progress);
                    }

                    case FINAL_FIGHT -> {
                        bossBar.setVisible(true);
                        bossBar.setColor(BarColor.RED);

                        double border = world.getWorldBorder().getSize();
                        int aliveCount = (int) gm.getAlive().stream()
                                .filter(id -> !plugin.isExempt(id))
                                .count();

                        String title = "§4§lFinal Fight" + ChatColor.GRAY + " | Zone: §f" + String.format("%.0f", border)
                                + " blocks" + ChatColor.GRAY + " | Alive: §f" + aliveCount;

                        bossBar.setTitle(title);

                        double progress = 1.0 - Math.max(0.0, Math.min(1.0,
                                gm.getCurrentBorderRadius() / initialRadius));
                        bossBar.setProgress(progress);
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
        try {
            bossBar.removeAll();
            bossBar.setVisible(false);
        } catch (Throwable ignored) {}
    }

    private String formatMMSS(int secs) {
        int m = secs / 60;
        int s = secs % 60;
        return String.format("%02d:%02d", m, s);
    }
}