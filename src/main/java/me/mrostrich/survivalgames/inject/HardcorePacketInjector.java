package me.mrostrich.survivalgames.inject;

import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.entity.Player;
import java.util.logging.Level;

public final class HardcorePacketInjector {

    private HardcorePacketInjector() {}

    public static void register(SurvivalGamesPlugin plugin) {
        try {
            ProtocolManager manager = ProtocolLibrary.getProtocolManager();

            manager.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Server.LOGIN) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    Player player = event.getPlayer();
                    try {
                        if (player == null) return;
                        if (((SurvivalGamesPlugin) plugin).isExempt(player)) {
                            plugin.getLogger().fine("Skipping hardcore injection for exempt user: " + player.getName());
                            return;
                        }

                        PacketContainer packet = event.getPacket();
                        try {
                            // Attempt to set the hardcore flag if present in this packet structure.
                            // Many server versions expose a boolean at index 0 for hardcore; guard with try/catch.
                            packet.getBooleans().write(0, true);
                            plugin.getLogger().info("Hardcore flag injected for " + player.getName());
                        } catch (Throwable t) {
                            // If the packet structure doesn't match, log at FINE/INFO to avoid spamming.
                            plugin.getLogger().log(Level.FINE, "HardcorePacketInjector: boolean write failed for " + player.getName() + " - " + t.getMessage());
                        }
                    } catch (Throwable t) {
                        plugin.getLogger().log(Level.WARNING, "HardcorePacketInjector listener error: " + t.getMessage(), t);
                    }
                }
            });

            plugin.getLogger().info("ProtocolLib hardcore packet injector registered.");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to register HardcorePacketInjector: " + t.getMessage(), t);
        }
    }
}
