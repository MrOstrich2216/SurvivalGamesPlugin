package me.mrostrich.survivalgames.ui;

import me.mrostrich.survivalgames.GameManager;
import me.mrostrich.survivalgames.SurvivalGamesPlugin;
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
        this.initialRadius = plugin.getConfig().getDouble("initial-border-diameter", 1500.0) / 2.0;
    }

    public void start() {
        stop();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isPluginEnabledFlag()) return;

                GameManager gm = plugin.getGameManager();
                World world = Bukkit.getWorld("world");
                if (world == null) return;

                bossBar.removeAll();

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!plugin.isExempt(p)) {
                        bossBar.addPlayer(p);
                    }
                }

                switch (gm.getState()) {
                    case WAITING, ENDED -> {
                        bossBar.setVisible(false);
                    }

                    case GRACE -> {
                        bossBar.setVisible(true);
                        bossBar.setColor(BarColor.BLUE);

                        int secs = gm.getGraceRemaining();
                        double progress = Math.max(0.0, Math.min(1.0,
                                secs / (double) Math.max(1, plugin.getConfig().getInt("grace-seconds", 600))));

                        String title = "§bGrace Period: §f" + formatMMSS(secs);
                        double border = world.getWorldBorder().getSize();
                        title += ChatColor.GRAY + " | Zone: " + String.format("%.0f", border) + "m";

                        bossBar.setTitle(title);
                        bossBar.setProgress(progress);
                    }

                    case FIGHT, FINAL_FIGHT -> {
                        bossBar.setVisible(true);
                        bossBar.setColor(BarColor.RED);

                        double border = world.getWorldBorder().getSize();
                        String title = "§cZone Diameter: §f" + String.format("%.0f", border) + " blocks";

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
        if (task != null) task.cancel();
        bossBar.removeAll();
        bossBar.setVisible(false);
    }

    private String formatMMSS(int secs) {
        int m = secs / 60;
        int s = secs % 60;
        return String.format("%02d:%02d", m, s);
    }
}
