package com.playmonumenta.networkchat.utils;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;

import com.google.gson.JsonElement;

public class MessagingUtils {
	public static final GsonComponentSerializer GSON_SERIALIZER = GsonComponentSerializer.gson();
	public static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
	public static final PlainComponentSerializer PLAIN_SERIALIZER = PlainComponentSerializer.plain();

	public static String translatePlayerName(Player player, String message) {
		return (message.replaceAll("@S", player.getName()));
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
}
