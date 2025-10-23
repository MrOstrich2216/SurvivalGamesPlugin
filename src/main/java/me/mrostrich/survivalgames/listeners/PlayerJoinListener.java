package me.mrostrich.survivalgames.listeners;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import me.mrostrich.survivalgames.GameManager;
import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import me.mrostrich.survivalgames.util.ReflectionUtil;
import me.mrostrich.survivalgames.inject.SpyingTextFilter;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.protocol.game.PacketPlayOutLogin;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.network.ITextFilter;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.logging.Level;

public class PlayerJoinListener implements Listener {

    private final SurvivalGamesPlugin plugin;

    public PlayerJoinListener(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.isPluginEnabledFlag()) return;

        GameManager gm = plugin.getGameManager();
        Player player = event.getPlayer();

        if (plugin.isExempt(player)) {
            // Moderator setup (no compass here)
            player.setGameMode(GameMode.CREATIVE);
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 1, false, false)); // optional

            for (Player other : plugin.getServer().getOnlinePlayers()) {
                if (!other.equals(player)) {
                    other.hidePlayer(plugin, player);
                }
            }
        } else if (gm.getState() == GameManager.State.WAITING) {
            player.setGameMode(GameMode.ADVENTURE);
        }

        if (plugin.getConfig().getBoolean("visuals.hardcore-hearts")) {
            plugin.getLogger().info("Attempting hardcore hearts injection for " + player.getName());
            injectHardcoreHearts(player);
        }
    }

    private void injectHardcoreHearts(Player player) {
        try {
            Method getHandle = player.getClass().getDeclaredMethod("getHandle");
            getHandle.setAccessible(true);
            EntityPlayer handle = (EntityPlayer) getHandle.invoke(player);

            Field filterField = SurvivalGamesPlugin.FILTER_FIELD;
            Object originalFilter = filterField.get(handle);

            if (originalFilter instanceof SpyingTextFilter) {
                plugin.getLogger().info("Hardcore hearts already injected for " + player.getName());
                return;
            }

            plugin.getLogger().info("Injecting SpyingTextFilter for " + player.getName());

            ReflectionUtil.setPrivateFinalField(
                    filterField,
                    handle,
                    new SpyingTextFilter((ITextFilter) originalFilter, () -> handleCallback(handle))
            );
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to inject hardcore hearts for " + player.getName(), e);
        }
    }

    private void handleCallback(EntityPlayer handle) {
        ChannelHandler injector = new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof PacketPlayOutLogin login) {
                    plugin.getLogger().info("Intercepting PacketPlayOutLogin for hardcore override");
                    PacketPlayOutLogin fakeLogin = new PacketPlayOutLogin(
                            login.b(), true, login.f(), login.g(), login.h(),
                            login.i(), login.j(), login.k(), login.l(), login.m(), login.n()
                    );
                    super.write(ctx, fakeLogin, promise);
                } else {
                    super.write(ctx, msg, promise);
                }
            }
        };

        try {
            Field connectionField = EntityPlayer.class.getDeclaredField("connection");
            connectionField.setAccessible(true);
            Object serverConnection = connectionField.get(handle);

            Field netManField = serverConnection.getClass().getDeclaredField("networkManager");
            netManField.setAccessible(true);
            NetworkManager netMan = (NetworkManager) netManField.get(serverConnection);

            Field channelField = NetworkManager.class.getDeclaredField("channel");
            channelField.setAccessible(true);
            Channel channel = (Channel) channelField.get(netMan);

            if (channel.pipeline().get("hardcore_injector") == null) {
                plugin.getLogger().info("Injecting Netty channel for hardcore hearts");
                channel.pipeline().addBefore("packet_handler", "hardcore_injector", injector);
            } else {
                plugin.getLogger().info("Netty injector already present for " + handle.getBukkitEntity().getName());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to inject Netty channel for hardcore hearts", e);
        }
    }
}
