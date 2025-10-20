package me.mrostrich.uhcrunplugin.listeners;

import me.mrostrich.uhcrunplugin.UhcRunPlugin;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class RecorderCompassListener implements Listener {

    private final UhcRunPlugin plugin;
    private static final String TRACKER_NAME = ChatColor.GOLD + "" + ChatColor.BOLD + "Player Tracker";

    public RecorderCompassListener(UhcRunPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player p = event.getPlayer();
        if (!"Recorder".equals(p.getName())) return;

        ItemStack item = p.getInventory().getItemInMainHand();
        if (!isTracker(item)) return;

        // Build eligible list
        List<UUID> eligible = new ArrayList<>(plugin.getGameManager().getAlive())
                .stream()
                .map(uuid -> uuid) // identity
                .collect(Collectors.toList());

        // Exclude Recorder and MrOstrich2216
        eligible.remove(p.getUniqueId());
        Player ostrich = Bukkit.getPlayerExact("MrOstrich2216");
        if (ostrich != null) eligible.remove(ostrich.getUniqueId());

        if (eligible.isEmpty()) {
            p.sendMessage(ChatColor.RED + "No players available to teleport to.");
            return;
        }

        UUID pick = eligible.get((int) (Math.random() * eligible.size()));
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
        if (!"Recorder".equals(p.getName())) return;
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
