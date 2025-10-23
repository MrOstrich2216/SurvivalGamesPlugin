package me.mrostrich.survivalgames.listeners;

import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class RecorderCompassListener implements Listener {

    private final SurvivalGamesPlugin plugin;
    private static final String TRACKER_NAME = ChatColor.GOLD + "" + ChatColor.BOLD + "Player Tracker";
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long TELEPORT_COOLDOWN_MS = 5000;

    public RecorderCompassListener(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player p = event.getPlayer();
        if (!plugin.isExempt(p)) return;

        ItemStack item = p.getInventory().getItemInMainHand();
        if (!isTracker(item)) return;

        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(p.getUniqueId()) && now - cooldowns.get(p.getUniqueId()) < TELEPORT_COOLDOWN_MS) {
            p.sendMessage(ChatColor.RED + "Please wait before using the tracker again.");
            return;
        }

        List<UUID> eligible = plugin.getGameManager().getAlive().stream()
                .filter(uuid -> !uuid.equals(p.getUniqueId()))
                .filter(uuid -> {
                    Player target = Bukkit.getPlayer(uuid);
                    return target != null && target.isOnline() && !plugin.isExempt(target);
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
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        cooldowns.put(p.getUniqueId(), now);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player p = event.getPlayer();
        if (!plugin.isExempt(p)) return;

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
