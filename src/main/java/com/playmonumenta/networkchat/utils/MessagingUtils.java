package com.playmonumenta.networkchat.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import com.google.gson.JsonElement;
import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.RemotePlayerManager;

import me.clip.placeholderapi.PlaceholderAPI;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.Template;
import net.kyori.adventure.text.minimessage.transformation.Transformation;
import net.kyori.adventure.text.minimessage.transformation.TransformationType;
import net.kyori.adventure.text.minimessage.markdown.DiscordFlavor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;

public class MessagingUtils {
	public static final GsonComponentSerializer GSON_SERIALIZER = GsonComponentSerializer.gson();
	public static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
	public static final PlainComponentSerializer PLAIN_SERIALIZER = PlainComponentSerializer.plain();
	private static final MiniMessage PLAYER_FMT_MINIMESSAGE = MiniMessage.builder()
	    .removeDefaultTransformations()
	    .markdownFlavor(DiscordFlavor.get())
	    .transformation(TransformationType.COLOR)
	    .transformation(TransformationType.DECORATION)
	    .transformation(TransformationType.HOVER_EVENT)
	    .transformation(TransformationType.CLICK_EVENT)
	    .transformation(TransformationType.KEYBIND)
	    .transformation(TransformationType.TRANSLATABLE)
	    .transformation(TransformationType.INSERTION)
	    .transformation(TransformationType.FONT)
	    .transformation(TransformationType.GRADIENT)
	    .transformation(TransformationType.RAINBOW)
	    .transformation(TransformationType.RESET)
	    .build();

	public static String translatePlayerName(Player player, String message) {
		return (message.replaceAll("@S", player.getName()));
	}

	public static Component senderComponent(CommandSender sender) {
		if (sender instanceof Entity) {
			return entityComponent((Entity) sender);
		}
		return Component.text(sender.getName());
	}

	public static Component entityComponent(Entity entity) {
		return entityComponent(entity.getType().getKey(), entity.getUniqueId(), entity.customName());
	}

	public static Component entityComponent(NamespacedKey type, UUID id, Component name) {
		if (type.toString().equals("minecraft:player")) {
			return RemotePlayerManager.getPlayerComponent(id);
		}

		Component result = name;
		if (name == null) {
			result = Component.translatable("entity." + type.toString().replace(":", "."));
		}

		return result.insertion(id.toString())
		    .hoverEvent(HoverEvent.showEntity(type, id, name));
	}

	public static Component playerComponent(Player player) {
		Team playerTeam = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(player.getName());
		TextColor color;
		Component teamPrefix;
		Component teamSuffix;
		try {
			color = playerTeam.color();
			teamPrefix = playerTeam.prefix();
			teamSuffix = playerTeam.suffix();
		} catch (Exception e) {
			color = null;
			teamPrefix = Component.empty();
			teamSuffix = Component.empty();
		}

		return PLAYER_FMT_MINIMESSAGE.parse(PlaceholderAPI.setPlaceholders(player, NetworkChatPlugin.mMessageFormats.get("player")),
		    List.of(Template.of("team_color", (color == null) ? "" : "<" + color.asHexString() + ">"),
		        Template.of("team_prefix", teamPrefix),
		        Template.of("team_suffix", teamSuffix)));
	}

	public static void sendStackTrace(CommandSender sender, Exception e) {
		TextComponent formattedMessage;
		String errorMessage = e.getLocalizedMessage();
		if (errorMessage != null) {
			formattedMessage = LEGACY_SERIALIZER.deserialize(errorMessage);
		} else {
			formattedMessage = Component.text("An error occured without a set message. Hover for stack trace.");
		}
		formattedMessage.color(NamedTextColor.RED);

		// Get the first 300 characters of the stacktrace and send them to the player
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		String sStackTrace = sw.toString();
		sStackTrace = sStackTrace.substring(0, Math.min(sStackTrace.length(), 300));

		TextComponent textStackTrace = Component.text(sStackTrace.replace("\t", "  "), NamedTextColor.RED);
		formattedMessage.hoverEvent(textStackTrace);
		sender.sendMessage(formattedMessage);

		e.printStackTrace();
	}

	public static Component fromJson(JsonElement json) {
		return GSON_SERIALIZER.deserializeFromTree(json);
	}

	public static JsonElement toJson(Component component) {
		return GSON_SERIALIZER.serializeToTree(component);
	}

	public static String plainText(Component formattedText) {
		String legacyText = PLAIN_SERIALIZER.serialize(formattedText);
		return PLAIN_SERIALIZER.serialize(LEGACY_SERIALIZER.deserialize(legacyText));
	}

	public static TextColor colorFromString(String value) {
		if (value.startsWith("#")) {
			return TextColor.fromHexString(value);
		} else {
			return NamedTextColor.NAMES.value(value);
		}
	}

	public static String colorToString(TextColor color) {
		if (color instanceof NamedTextColor) {
			return color.toString();
		}
		return color.asHexString();
	}

	public static String colorToMiniMessage(TextColor color) {
		return "<" + colorToString(color) + ">";
	}
}
