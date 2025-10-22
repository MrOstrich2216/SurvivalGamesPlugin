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

        if (gm.getState() == GameManager.State.WAITING && !gm.isExempt(player)) {
            player.setGameMode(GameMode.ADVENTURE);
        }

        if (plugin.getConfig().getBoolean("visuals.hardcore-hearts")) {
            injectHardcoreHearts(player);
        }
    }

    private void injectHardcoreHearts(Player player) {
        try {
            Method getHandle = player.getClass().getDeclaredMethod("getHandle");
getHandle.setAccessible(true);
EntityPlayer handle = (EntityPlayer) getHandle.invoke(player);

            Field filterField = SurvivalGamesPlugin.FILTER_FIELD;
            Object filter = filterField.get(handle);

            ReflectionUtil.setPrivateFinalField(
                    filterField,
                    handle,
                    new SpyingTextFilter((ITextFilter) filter, () -> handleCallback(handle))
            );
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to inject hardcore hearts", e);
        }
    }

    private void handleCallback(EntityPlayer handle) {
        ChannelHandler injector = new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof PacketPlayOutLogin login) {
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

            channel.pipeline().addBefore("packet_handler", "hardcore_injector", injector);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject Netty channel", e);
        }
    }
}
