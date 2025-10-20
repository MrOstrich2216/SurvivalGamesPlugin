package me.mrostrich.uhcrunplugin.commands;

import me.mrostrich.uhcrunplugin.GameManager;
import me.mrostrich.uhcrunplugin.UhcRunPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class UhcCommand implements CommandExecutor {

    private final UhcRunPlugin plugin;

    public UhcCommand(UhcRunPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("uhc.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /uhc <enable|disable|start|status|forcestop|neutral>");
            return true;
        }

        String sub = args[0].toLowerCase();
        GameManager gm = plugin.getGameManager();

        switch (sub) {
            case "enable": {
                if (plugin.isPluginEnabledFlag()) {
                    sender.sendMessage(ChatColor.YELLOW + "UhcRunPlugin is already enabled.");
                    return true;
                }
                plugin.setPluginEnabledFlag(true);
                plugin.enableSystems();
                sender.sendMessage(ChatColor.GREEN + "UhcRunPlugin enabled. Use /uhc start when ready.");
                return true;
            }
            case "disable": {
                if (!plugin.isPluginEnabledFlag()) {
                    sender.sendMessage(ChatColor.YELLOW + "UhcRunPlugin is already disabled.");
                    return true;
                }
                plugin.disableSystems();
                plugin.setPluginEnabledFlag(false);
                sender.sendMessage(ChatColor.RED + "UhcRunPlugin disabled. All tasks stopped.");
                return true;
            }
            case "start": {
                if (!plugin.isPluginEnabledFlag()) {
                    sender.sendMessage(ChatColor.RED + "Plugin disabled. Use /uhc enable first.");
                    return true;
                }
                gm.startGame();
                return true;
            }
            case "status": {
                World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                double border = (w != null) ? w.getWorldBorder().getSize() : 0.0;
                sender.sendMessage(ChatColor.GOLD + "---- UHC Status ----");
                sender.sendMessage(ChatColor.YELLOW + "Enabled: " + ChatColor.WHITE + plugin.isPluginEnabledFlag());
                sender.sendMessage(ChatColor.YELLOW + "State: " + ChatColor.WHITE + gm.getState());
                sender.sendMessage(ChatColor.YELLOW + "Alive: " + ChatColor.WHITE + gm.getAliveCount());
                if (gm.getState() == GameManager.State.GRACE) {
                    sender.sendMessage(ChatColor.YELLOW + "Grace remaining: " + ChatColor.WHITE + gm.getGraceRemaining() + "s");
                }
                sender.sendMessage(ChatColor.YELLOW + "Border diameter: " + ChatColor.WHITE + String.format("%.1f", border));
                return true;
            }
            case "forcestop": {
                gm.forceStop();
                Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "UHC forcibly stopped by an admin.");
                return true;
            }
            case "neutral": {
                gm.neutralGame(sender);
                return true;
            }
            default:
                sender.sendMessage(ChatColor.YELLOW + "Usage: /uhc <enable|disable|start|status|forcestop|neutral>");
                return true;
        }
    }
}
