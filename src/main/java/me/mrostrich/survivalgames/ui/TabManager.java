package me.mrostrich.survivalgames.ui;

import me.mrostrich.survivalgames.SurvivalGamesPlugin;
import me.mrostrich.survivalgames.state.MatchState;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Lightweight tab header/footer manager using Adventure when available and falling back to legacy API.
 * Keeps header/footer consistent across players and updates on request.
 */
public class TabManager {

    private final SurvivalGamesPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    public TabManager(SurvivalGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public void updateAllTabs() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                updateTab(p);
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to update tab for " + p.getName() + ": " + t.getMessage());
            }
        }
    }

    public void updateTab(Player p) {
        if (p == null) return;

        try {
            MatchState state = plugin.getMatchState();
            String headerRaw;
            String footerRaw;

            switch (state) {
                case WAITING -> {
                    headerRaw = "§6Survival Games §7| §aWaiting";
                    footerRaw = "§7Type §e/game start §7to begin the match";
                }
                case RUNNING -> {
                    headerRaw = "§6Survival Games §7| §cFight in progress";
                    footerRaw = "§7Players left: §f" + plugin.getGameManager().getAlive().size();
                }
                case FINAL_FIGHT -> {
                    headerRaw = "§6Survival Games §7| §4Final Fight";
                    footerRaw = "§7May the last one standing win";
                }
                case ENDED -> {
                    headerRaw = "§6Survival Games §7| §7Match Ended";
                    footerRaw = "§7Thanks for playing";
                }
                default -> {
                    headerRaw = "§6Survival Games";
                    footerRaw = "";
                }
            }

            Component header = legacy.deserialize(headerRaw);
            Component footer = legacy.deserialize(footerRaw);

            // Try Adventure API if present
            try {
                p.sendPlayerListHeaderAndFooter(header, footer);
                return;
            } catch (NoSuchMethodError | NoClassDefFoundError | UnsupportedOperationException ignored) {
                // Fall through to legacy approach
            }

            // Legacy fallback: use old Bukkit methods
            try {
                // Older servers used Strings; send legacy serialized text
                p.setPlayerListHeader(legacy.serialize(header));
                p.setPlayerListFooter(legacy.serialize(footer));
            } catch (Throwable ignored) {
                // If neither API is available, quietly ignore
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Error updating tab for player " + p.getName() + ": " + t.getMessage());
        }
    }
}
