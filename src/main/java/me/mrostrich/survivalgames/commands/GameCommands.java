package me.mrostrich.survivalgames.commands;

import me.mrostrich.survivalgames.GameManager;
import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GameCommands implements CommandExecutor, TabCompleter {

    private final SurvivalGamesPlugin plugin;

    public GameCommands(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("survival.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return List.of("enable", "disable", "start", "status", "forcestop", "neutral").stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("survival.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /game <enable|disable|start|status|forcestop|neutral>");
            return true;
        }

        String sub = args[0].toLowerCase();
        GameManager gm = plugin.getGameManager();

        switch (sub) {
            case "enable" -> {
                if (plugin.isPluginEnabledFlag()) {
                    sender.sendMessage(ChatColor.YELLOW + "SurvivalGamesPlugin is already enabled.");
                    return true;
                }
                plugin.setPluginEnabledFlag(true);
                plugin.enableSystems();
                sender.sendMessage(ChatColor.GREEN + "SurvivalGamesPlugin enabled. Use /game start when ready.");
            }
            case "disable" -> {
                if (!plugin.isPluginEnabledFlag()) {
                    sender.sendMessage(ChatColor.YELLOW + "SurvivalGamesPlugin is already disabled.");
                    return true;
                }
                plugin.disableSystems();
                plugin.setPluginEnabledFlag(false);
                sender.sendMessage(ChatColor.RED + "SurvivalGamesPlugin disabled. All tasks stopped.");
            }
            case "start" -> {
                if (!plugin.isPluginEnabledFlag()) {
                    sender.sendMessage(ChatColor.RED + "Plugin disabled. Use /game enable first.");
                    return true;
                }
                gm.startGame();
            }
            case "status" -> {
                World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                double border = (w != null) ? w.getWorldBorder().getSize() : 0.0;
                sender.sendMessage(ChatColor.GOLD + "---- Survival Games Status ----");
                sender.sendMessage(ChatColor.YELLOW + "Enabled: " + ChatColor.WHITE + plugin.isPluginEnabledFlag());
                sender.sendMessage(ChatColor.YELLOW + "State: " + ChatColor.WHITE + gm.getState());
                sender.sendMessage(ChatColor.YELLOW + "Alive: " + ChatColor.WHITE + gm.getAliveCount());
                if (gm.getState() == GameManager.State.GRACE) {
                    sender.sendMessage(ChatColor.YELLOW + "Grace remaining: " + ChatColor.WHITE + gm.getGraceRemaining() + "s");
                }
                sender.sendMessage(ChatColor.YELLOW + "Zone diameter: " + ChatColor.WHITE + String.format("%.1f", border));
            }
            case "forcestop" -> {
                gm.forceStop();
                Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "Survival Games forcibly stopped by an admin.");
            }
            case "neutral" -> gm.neutralGame(sender);
            default -> {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /game <enable|disable|start|status|forcestop|neutral>");
                return true;
            }
        }

        return true;
    }
}
