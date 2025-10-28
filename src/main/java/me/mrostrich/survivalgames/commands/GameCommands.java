package me.mrostrich.survivalgames.commands;

import me.mrostrich.survivalgames.GameManager;
import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import me.mrostrich.survivalgames.state.MatchState;
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
            return List.of("enable", "disable", "start", "status", "forcestop", "neutral")
                    .stream()
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

        plugin.getLogger().info("Command executed: /game " + sub + " by " + sender.getName());

        switch (sub) {
            case "enable" -> {
                if (plugin.isPluginEnabledFlag()) {
                    sender.sendMessage(ChatColor.YELLOW + "SurvivalGamesPlugin is already enabled.");
                    return true;
                }
                plugin.setPluginEnabledFlag(true);
                plugin.reloadExemptCache();
                plugin.enableSystems();
                sender.sendMessage(ChatColor.GREEN + "SurvivalGamesPlugin enabled. Use /game start when ready.");
                plugin.getLogger().info("Plugin enabled via command by " + sender.getName());
            }
            case "disable" -> {
                if (!plugin.isPluginEnabledFlag()) {
                    sender.sendMessage(ChatColor.YELLOW + "SurvivalGamesPlugin is already disabled.");
                    return true;
                }
                plugin.disableSystems();
                plugin.setPluginEnabledFlag(false);
                sender.sendMessage(ChatColor.RED + "SurvivalGamesPlugin disabled. All tasks stopped.");
                plugin.getLogger().info("Plugin disabled via command by " + sender.getName());
            }
            case "start" -> {
                if (!plugin.isPluginEnabledFlag()) {
                    sender.sendMessage(ChatColor.RED + "Plugin disabled. Use /game enable first.");
                    return true;
                }

                // Enforce minimum players
                int onlineFighters = (int) Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !plugin.isExempt(p))
                        .count();
                if (onlineFighters < 2) {
                    sender.sendMessage(ChatColor.RED + "Need at least 2 non-exempt players to start the game.");
                    return true;
                }

                if (gm == null) {
                    sender.sendMessage(ChatColor.RED + "Game manager unavailable.");
                    return true;
                }

                gm.startGame();
                // Ensure canonical state propagated and UI updated
                try { plugin.getTabManager().updateAllTabs(); } catch (Throwable ignored) {}
                sender.sendMessage(ChatColor.GREEN + "Start command issued.");
            }
            case "status" -> {
                if (gm == null) {
                    sender.sendMessage(ChatColor.RED + "Game manager unavailable.");
                    return true;
                }

                World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                double border = (w != null) ? w.getWorldBorder().getSize() : 0.0;
                sender.sendMessage(ChatColor.GOLD + "---- Survival Games Status ----");
                sender.sendMessage(ChatColor.YELLOW + "Enabled: " + ChatColor.WHITE + plugin.isPluginEnabledFlag());
                sender.sendMessage(ChatColor.YELLOW + "State: " + ChatColor.WHITE + plugin.getMatchState());
                sender.sendMessage(ChatColor.YELLOW + "Alive: " + ChatColor.WHITE + gm.getAlive().size());
                if (gm.getState() == GameManager.State.GRACE) {
                    sender.sendMessage(ChatColor.YELLOW + "Grace remaining: " + ChatColor.WHITE + gm.getGraceRemaining() + "s");
                }
                sender.sendMessage(ChatColor.YELLOW + "Zone diameter: " + ChatColor.WHITE + String.format("%.1f", border));
            }
            case "forcestop" -> {
                if (gm != null) gm.forceStop();
                Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "Survival Games forcibly stopped by an admin.");
                plugin.getLogger().info("Force stop executed by " + sender.getName());
            }
            case "neutral" -> {
                if (gm != null) gm.neutralGame(sender);
                plugin.getLogger().info("Neutral reset executed by " + sender.getName());
            }
            default -> {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /game <enable|disable|start|status|forcestop|neutral>");
                return true;
            }
        }

        return true;
    }
}