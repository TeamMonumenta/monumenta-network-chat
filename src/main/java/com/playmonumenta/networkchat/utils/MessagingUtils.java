package com.playmonumenta.networkchat.utils;

import com.comphenix.protocol.PacketType;
import com.google.gson.JsonElement;
import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.RemotePlayerManager;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.Template;
import net.kyori.adventure.text.minimessage.template.TemplateResolver;
import net.kyori.adventure.text.minimessage.transformation.TransformationRegistry;
import net.kyori.adventure.text.minimessage.transformation.TransformationType;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Team;

public class MessagingUtils {
	public static final GsonComponentSerializer GSON_SERIALIZER = GsonComponentSerializer.gson();
	public static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
	public static final PlainComponentSerializer PLAIN_SERIALIZER = PlainComponentSerializer.plain();
	public static final Pattern REGEX_LEGACY_PREFIX = Pattern.compile("[&\u00a7]");
	public static final Pattern REGEX_LEGACY_RGB_1 = Pattern.compile("(?<!<)[&\u00a7]?#([0-9a-fA-F]{6})(?!>)");
	public static final Pattern REGEX_LEGACY_RGB_2 = Pattern.compile("(?<!<)[&\u00a7]#(([&\u00a7][0-9a-fA-F]){6})(?!>)");
	public static final MiniMessage CHANNEL_HEADER_FMT_MINIMESSAGE = MiniMessage.builder()
		.transformations(
			TransformationRegistry.builder().clear()
				.add(TransformationType.COLOR)
				.add(TransformationType.DECORATION)
				.build()
		)
		.build();
	public static final MiniMessage SENDER_FMT_MINIMESSAGE = MiniMessage.builder()
		.transformations(
			TransformationRegistry.builder().clear()
				.add(TransformationType.COLOR)
				.add(TransformationType.DECORATION)
				.add(TransformationType.HOVER_EVENT)
				.add(TransformationType.CLICK_EVENT)
				.add(TransformationType.KEYBIND)
				.add(TransformationType.TRANSLATABLE)
				.add(TransformationType.INSERTION)
				.add(TransformationType.FONT)
				.add(TransformationType.GRADIENT)
				.add(TransformationType.RAINBOW)
				.build()
		)
		.build();

	public static String legacyToMiniMessage(String legacy) {
		String result = legacy;

		result = REGEX_LEGACY_RGB_1.matcher(result).replaceAll("<#$1>");
		result = REGEX_LEGACY_RGB_2.matcher(result).replaceAll(mr -> "<#" + REGEX_LEGACY_PREFIX.matcher(mr.group(1)).replaceAll("") + ">");

		for (ChatColor legacyFormat : ChatColor.values()) {
			if (ChatColor.MAGIC.equals(legacyFormat)) {
				result = result.replace("&" + legacyFormat.getChar(), "<obfuscated>");
				result = result.replace(legacyFormat.toString(), "<obfuscated>");
			} else if (ChatColor.UNDERLINE.equals(legacyFormat)) {
				result = result.replace("&" + legacyFormat.getChar(), "<underlined>");
				result = result.replace(legacyFormat.toString(), "<underlined>");
			} else {
				result = result.replace("&" + legacyFormat.getChar(), "<" + legacyFormat.name() + ">");
				result = result.replace(legacyFormat.toString(), "<" + legacyFormat.name() + ">");
			}
		}
		return result;
	}

	public static MiniMessage getAllowedMiniMessage(CommandSender sender) {
		TransformationRegistry.Builder transforms = TransformationRegistry.builder().clear();

		if (sender instanceof Player) {
			if (CommandUtils.hasPermission(sender, "networkchat.transform.color")) {
				transforms.add(TransformationType.COLOR);
			}
			if (CommandUtils.hasPermission(sender, "networkchat.transform.decoration")) {
				transforms.add(TransformationType.DECORATION);
			}
			if (CommandUtils.hasPermission(sender, "networkchat.transform.keybind")) {
				transforms.add(TransformationType.KEYBIND);
			}
			if (CommandUtils.hasPermission(sender, "networkchat.transform.font")) {
				transforms.add(TransformationType.FONT);
			}
			if (CommandUtils.hasPermission(sender, "networkchat.transform.gradient")) {
				transforms.add(TransformationType.GRADIENT);
			}
			if (CommandUtils.hasPermission(sender, "networkchat.transform.rainbow")) {
				transforms.add(TransformationType.RAINBOW);
			}
		} else {
			transforms.add(TransformationType.COLOR)
				.add(TransformationType.DECORATION)
				.add(TransformationType.KEYBIND)
				.add(TransformationType.FONT)
				.add(TransformationType.GRADIENT)
				.add(TransformationType.RAINBOW);
		}

		return MiniMessage.builder()
			.transformations(transforms.build())
			.build();
	}

	public static String translatePlayerName(Player player, String message) {
		return (message.replaceAll("@S", player.getName()));
	}

	public static Component senderComponent(CommandSender sender) {
		if (sender instanceof Entity) {
			return entityComponent((Entity) sender);
		}
		return SENDER_FMT_MINIMESSAGE.deserialize(PlaceholderAPI.setPlaceholders(null, NetworkChatPlugin.messageFormat("sender")),
			TemplateResolver.templates(Template.template("sender_name", sender.getName())));
	}

	public static Component entityComponent(Entity entity) {
		return entityComponent(entity.getType().getKey(), entity.getUniqueId(), entity.customName());
	}

	public static Component entityComponent(NamespacedKey type, UUID id, @Nullable Component name) {
		if (type.toString().equals("minecraft:player")) {
			return RemotePlayerManager.getPlayerComponent(id);
		}

		Component entityName = name;
		if (name == null) {
			entityName = Component.translatable("entity." + type.toString().replace(":", "."));
		}

		@Nullable Team entityTeam = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(id.toString());
		@Nullable TextColor color;
		Component teamPrefix;
		Component teamDisplayName;
		Component teamSuffix;
		if (entityTeam == null) {
			color = null;
			teamPrefix = Component.empty();
			teamDisplayName = Component.empty();
			teamSuffix = Component.empty();
		} else {
			try {
				color = entityTeam.color();
				teamPrefix = entityTeam.prefix();
				teamDisplayName = entityTeam.displayName();
				teamSuffix = entityTeam.suffix();
			} catch (Exception e) {
				color = null;
				teamPrefix = Component.empty();
				teamDisplayName = Component.translatable("minecraft:chat.square_brackets", Component.text(entityTeam.getName()));
				teamSuffix = Component.empty();
			}
		}

		return SENDER_FMT_MINIMESSAGE.deserialize(PlaceholderAPI.setPlaceholders(null, NetworkChatPlugin.messageFormat("entity")),
			TemplateResolver.templates(Template.template("entity_type", type.toString()),
				Template.template("entity_uuid", id.toString()),
				Template.template("entity_name", entityName),
				Template.template("team_color", (color == null) ? "" : "<" + color.asHexString() + ">"),
				Template.template("team_prefix", teamPrefix),
				Template.template("team_displayname", teamDisplayName),
				Template.template("team_suffix", teamSuffix)));
	}

	public static Component playerComponent(Player player) {
		if (player == null) {
			return Component.empty();
		}
		@Nullable Team playerTeam = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
		String colorMiniMessage = "";
		Component teamPrefix;
		Component teamDisplayName;
		Component teamSuffix;
		if (playerTeam == null) {
			colorMiniMessage = "";
			teamPrefix = Component.empty();
			teamDisplayName = Component.empty();
			teamSuffix = Component.empty();
		} else {
			@Nullable TextColor color;
			try {
				/*
				 * Team.color() claims to return RESET if color is not set, but TextColor has no RESET value.
				 * Instead, null is the appropriate return value.
				 */
				color = playerTeam.color();
				if (color != null) {
					colorMiniMessage = "<" + color.asHexString() + ">";
				}
				teamPrefix = playerTeam.prefix();
				teamDisplayName = playerTeam.displayName();
				teamSuffix = playerTeam.suffix();
			} catch (Exception e) {
				colorMiniMessage = "";
				teamPrefix = Component.empty();
				teamDisplayName = Component.translatable("chat.square_brackets", Component.text(playerTeam.getName()));
				teamSuffix = Component.empty();
			}
		}

		@Nullable PlayerState state = PlayerStateManager.getPlayerState(player);
		if (state == null) {
			return Component.text(player.getName(), NamedTextColor.RED)
				.hoverEvent(Component.text("Invalid player state, please relog.", NamedTextColor.RED));
		}
		Component profileMessage = state.profileMessageComponent();
		String postPapiProcessing = PlaceholderAPI.setPlaceholders(player, NetworkChatPlugin.messageFormat("player"))
			.replaceAll("[\u00a7&][Rr]", colorMiniMessage)
			// https://github.com/KyoriPowered/adventure-text-minimessage/issues/166
			.replace("<hover:show_text:\"\"></hover>", "");
		postPapiProcessing = legacyToMiniMessage(postPapiProcessing);
		return SENDER_FMT_MINIMESSAGE.deserialize(postPapiProcessing,
			TemplateResolver.templates(Template.template("team_color", colorMiniMessage),
				Template.template("team_prefix", teamPrefix),
				Template.template("team_displayname", teamDisplayName),
				Template.template("team_suffix", teamSuffix),
				Template.template("profile_message", profileMessage),
				Template.template("item", () -> {
					if (!CommandUtils.hasPermission(player, "networkchat.transform.item")) {
						return Component.empty();
					}

					ItemStack item = player.getInventory().getItemInMainHand();
					if (item.getType() != Material.AIR) {
						return item.displayName().hoverEvent(item);
					}
					return Component.empty();
			})));
	}

	public static void sendStackTrace(CommandSender sender, Exception e) {
		TextComponent formattedMessage;
		String errorMessage = e.getLocalizedMessage();
		if (errorMessage != null) {
			formattedMessage = LEGACY_SERIALIZER.deserialize(errorMessage);
		} else {
			formattedMessage = Component.text("An error occurred without a set message. Hover for stack trace.");
		}
		formattedMessage = formattedMessage.color(NamedTextColor.RED);

		// Get the first 300 characters of the stacktrace and send them to the player
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		String sStackTrace = sw.toString();
		sStackTrace = sStackTrace.substring(0, Math.min(sStackTrace.length(), 300));

		TextComponent textStackTrace = Component.text(sStackTrace.replace("\t", "  "), NamedTextColor.RED);
		formattedMessage = formattedMessage.hoverEvent(textStackTrace);
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

	public static String colorToString(@Nullable TextColor color) {
		if (color == null) {
			return "#deadbe";
		}
		if (color instanceof NamedTextColor) {
			return color.toString();
		}
		return color.asHexString();
	}

	public static String colorToMiniMessage(@Nullable TextColor color) {
		if (color == null) {
			return "";
		}
		return "<" + colorToString(color) + ">";
	}
}
