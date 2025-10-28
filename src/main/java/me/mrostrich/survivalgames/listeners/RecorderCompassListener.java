package me.mrostrich.survivalgames.listeners;

import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import me.mrostrich.survivalgames.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class RecorderCompassListener implements Listener {

    private final SurvivalGamesPlugin plugin;

    public RecorderCompassListener(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.isPluginEnabledFlag()) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return; // Avoid double-fire

        Player player = event.getPlayer();
        if (player == null) return;

        try {
            ItemStack item = event.getItem();
            if (item == null) return;
            if (item.getType() != org.bukkit.Material.COMPASS) return;

            // Only allow exempt recorder users to use the compass functionality
            if (!plugin.isExempt(player)) return;

            event.setCancelled(true); // avoid normal compass behavior

            // Find a random alive non-exempt player and teleport the recorder to them
            var gm = plugin.getGameManager();
            if (gm == null) {
                player.sendMessage(ChatColor.RED + "Recorder: game manager unavailable.");
                return;
            }

            var alive = gm.getAlive().stream()
                    .filter(uuid -> {
                        var op = Bukkit.getOfflinePlayer(uuid);
                        var name = op.getName();
                        return name != null && !plugin.getExemptUsers().contains(name);
                    })
                    .toList();

            if (alive.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "No alive players to track.");
                return;
            }

            // Choose a random alive player
            java.util.UUID targetId = alive.get(new java.util.Random().nextInt(alive.size()));
            var targetOp = Bukkit.getOfflinePlayer(targetId);
            if (targetOp == null || targetOp.getName() == null) {
                player.sendMessage(ChatColor.RED + "Failed to resolve tracked player.");
                return;
            }

            Player target = Bukkit.getPlayer(targetId);
            if (target == null || !target.isOnline()) {
                player.sendMessage(ChatColor.YELLOW + "Tracked player is not online.");
                return;
            }

            // Teleport recorder near the target using TeleportUtil for safety
            var loc = target.getLocation();
            var world = loc.getWorld();
            if (world == null) {
                player.sendMessage(ChatColor.RED + "Target world unavailable.");
                return;
            }

            var safe = TeleportUtil.randomSafeLocation(world, loc, 8.0);
            if (safe == null) safe = loc;

            player.teleport(safe);
            player.sendMessage(ChatColor.GREEN + "Teleported to " + target.getName() + ".");
        } catch (Throwable t) {
            plugin.getLogger().warning("RecorderCompassListener error: " + t.getMessage());
        }
    }
}
