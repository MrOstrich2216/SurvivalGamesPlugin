package me.mrostrich.survivalgames.listeners;

import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RecorderCompassListener implements Listener {

    private final SurvivalGamesPlugin plugin;
    private static final String TRACKER_NAME = ChatColor.GOLD + "" + ChatColor.BOLD + "Player Tracker";

    public RecorderCompassListener(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player p = event.getPlayer();
        if (!plugin.getGameManager().isExempt(p)) return;

        ItemStack item = p.getInventory().getItemInMainHand();
        if (!isTracker(item)) return;

        List<UUID> eligible = plugin.getGameManager().getAlive().stream()
                .filter(uuid -> !uuid.equals(p.getUniqueId()))
                .filter(uuid -> {
                    Player target = Bukkit.getPlayer(uuid);
                    return target != null && target.isOnline() && !"MrOstrich2216".equals(target.getName());
                })
                .toList();

        if (eligible.isEmpty()) {
            p.sendMessage(ChatColor.RED + "No players available to teleport to.");
            return;
        }

        UUID pick = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        Player target = Bukkit.getPlayer(pick);
        if (target == null || !target.isOnline()) {
            p.sendMessage(ChatColor.RED + "No players available to teleport to.");
            return;
        }

        p.teleport(target.getLocation());
        p.sendMessage(ChatColor.GREEN + "Teleported to " + ChatColor.WHITE + target.getName());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player p = event.getPlayer();
        if (!plugin.getGameManager().isExempt(p)) return;

        ItemStack dropped = event.getItemDrop().getItemStack();
        if (isTracker(dropped)) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.YELLOW + "You cannot drop the Player Tracker.");
        }
    }

    private boolean isTracker(ItemStack stack) {
        if (stack == null || stack.getType() != Material.COMPASS) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.hasDisplayName() && TRACKER_NAME.equals(meta.getDisplayName());
    }
}
