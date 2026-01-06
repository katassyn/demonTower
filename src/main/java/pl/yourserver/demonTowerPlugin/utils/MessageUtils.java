package pl.yourserver.demonTowerPlugin.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Collection;

public class MessageUtils {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public static Component colorize(String message) {
        return LEGACY.deserialize(message);
    }

    public static void sendMessage(Player player, String message) {
        player.sendMessage(colorize(message));
    }

    public static void sendMessage(Collection<Player> players, String message) {
        Component component = colorize(message);
        for (Player player : players) {
            player.sendMessage(component);
        }
    }

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Title.Times times = Title.Times.times(
            Duration.ofMillis(fadeIn * 50L),
            Duration.ofMillis(stay * 50L),
            Duration.ofMillis(fadeOut * 50L)
        );

        Title titleObj = Title.title(
            colorize(title),
            colorize(subtitle),
            times
        );

        player.showTitle(titleObj);
    }

    public static void sendTitle(Collection<Player> players, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        for (Player player : players) {
            sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(colorize(message));
    }

    public static void sendActionBar(Collection<Player> players, String message) {
        Component component = colorize(message);
        for (Player player : players) {
            player.sendActionBar(component);
        }
    }

    public static String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    public static String formatTimeColored(int seconds) {
        String color;
        if (seconds <= 30) {
            color = "&c";
        } else if (seconds <= 60) {
            color = "&e";
        } else {
            color = "&a";
        }
        return color + formatTime(seconds);
    }
}
