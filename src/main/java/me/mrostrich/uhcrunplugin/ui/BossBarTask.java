package me.mrostrich.uhcrunplugin.ui;

import me.mrostrich.uhcrunplugin.GameManager;
import me.mrostrich.uhcrunplugin.UhcRunPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class BossBarTask {
    private final UhcRunPlugin plugin;
    private BukkitRunnable task;
    private final BossBar bossBar;
    private final double initialRadius;

    public BossBarTask(UhcRunPlugin plugin) {
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

                Bukkit.getOnlinePlayers().forEach(p -> {
                    if (!bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);

                    switch (gm.getState()) {
                        case WAITING, ENDED -> bossBar.setVisible(false);

                        case GRACE -> {
                            bossBar.setVisible(true);
                            bossBar.setColor(BarColor.BLUE);
                            int secs = gm.getGraceRemaining();
                            bossBar.setTitle("§bGrace Period: §f" + formatMMSS(secs));
                            double progress = Math.max(0.0, Math.min(1.0, secs / (double) Math.max(1, plugin.getConfig().getInt("grace-seconds", 600))));
                            bossBar.setProgress(progress);
                        }

                        case FIGHT, FINAL_FIGHT -> {
                            bossBar.setVisible(true);
                            bossBar.setColor(BarColor.RED);

                            Location center = p.getWorld().getWorldBorder().getCenter();
                            double halfSize = p.getWorld().getWorldBorder().getSize() / 2.0;

                            double minX = center.getX() - halfSize;
                            double maxX = center.getX() + halfSize;
                            double minZ = center.getZ() - halfSize;
                            double maxZ = center.getZ() + halfSize;

                            double px = p.getLocation().getX();
                            double pz = p.getLocation().getZ();

                            double distX = Math.min(Math.abs(px - minX), Math.abs(maxX - px));
                            double distZ = Math.min(Math.abs(pz - minZ), Math.abs(maxZ - pz));

                            double distanceToBorder = Math.min(distX, distZ);

                            bossBar.setTitle("§cBorder distance from you: §f" + (int) distanceToBorder + " blocks");

                            double progress = Math.max(0.0, Math.min(1.0, gm.getCurrentBorderRadius() / Math.max(1.0, initialRadius)));
                            bossBar.setProgress(progress);
                        }
                    }
                });
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
