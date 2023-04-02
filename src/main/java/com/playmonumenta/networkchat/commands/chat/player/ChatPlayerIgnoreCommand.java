package com.playmonumenta.networkchat.commands.chat.player;

import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatPlayerIgnoreCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		for (String baseCommand : ChatCommand.COMMANDS) {
			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("ignore"));
			arguments.add(new MultiLiteralArgument("hide"));
			arguments.add(new StringArgument("name").replaceSuggestions(
				ArgumentSuggestions.strings((suggestionInfo)
					-> {
					String selfStr = CommandUtils.getCallee(suggestionInfo.sender()) instanceof Player player ? player.getName() : null;
					return MonumentaRedisSyncAPI.getAllCachedPlayerNames().stream().filter(player -> !player.equals(selfStr)).toArray(String[]::new);
				})));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					if (CommandUtils.checkSudoCommandDisallowed(sender)) {
						throw CommandUtils.fail(sender, "You may not change other players' ignored players.");
					}
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						throw CommandUtils.fail(sender, "This command can only be run as a player.");
					} else {
						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
						}
						String ignoredName = (String) args[3];
						if (ignoredName.equals(target.getName())) {
							throw CommandUtils.fail(sender, "You cannot ignore yourself.");
						}
						@Nullable UUID ignoredId = MonumentaRedisSyncAPI.cachedNameToUuid(ignoredName);
						if (ignoredId == null) {
							throw CommandUtils.fail(sender, "The player " + ignoredName + " has not joined this server before and may not be ignored. Double check capitalization and spelling.");
						}
						Set<UUID> ignoredPlayers = state.getIgnoredPlayerIds();
						if (ignoredPlayers.contains(ignoredId)) {
							target.sendMessage(Component.text("You are still ignoring " + ignoredName, NamedTextColor.GRAY));
						} else {
							ignoredPlayers.add(ignoredId);
							target.sendMessage(Component.text("You are now ignoring " + ignoredName, NamedTextColor.GRAY));
						}
					}
					return 1;
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("ignore"));
			arguments.add(new MultiLiteralArgument("list"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					if (CommandUtils.checkSudoCommandDisallowed(sender)) {
						throw CommandUtils.fail(sender, "You may not change other players' ignored players.");
					}
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						throw CommandUtils.fail(sender, "This command can only be run as a player.");
					} else {
						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
						}
						Set<String> ignoredNames = state.getIgnoredPlayerNames();
						target.sendMessage(Component.text("You are ignoring:", NamedTextColor.DARK_GRAY, TextDecoration.BOLD));
						boolean lightLine = false;
						TextColor lineColor;
						for (String ignoredName : ignoredNames) {
							lightLine = !lightLine;
							lineColor = lightLine ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY;
							target.sendMessage(Component.text("- " + ignoredName, lineColor));
						}
						lightLine = !lightLine;
						lineColor = lightLine ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY;
						target.sendMessage(Component.text("Players ignored: " + ignoredNames.size(), lineColor));
					}
					return 1;
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("ignore"));
			arguments.add(new MultiLiteralArgument("show"));
			arguments.add(new StringArgument("name")
				.replaceSuggestions(ArgumentSuggestions.strings(info -> {
					CommandSender sender = info.sender();
					if (CommandUtils.checkSudoCommandDisallowed(sender)) {
						return new String[] {};
					}
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						return new String[] {};
					} else {
						@Nullable PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							return new String[] {};
						}
						return state.getIgnoredPlayerNames().toArray(new String[0]);
					}
				})));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					if (CommandUtils.checkSudoCommandDisallowed(sender)) {
						throw CommandUtils.fail(sender, "You may not change other players' ignored players.");
					}
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						throw CommandUtils.fail(sender, "This command can only be run as a player.");
					} else {
						@Nullable PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
						}
						String ignoredName = (String) args[3];
						@Nullable UUID ignoredId = MonumentaRedisSyncAPI.cachedNameToUuid(ignoredName);
						if (ignoredId == null) {
							throw CommandUtils.fail(sender, "The player " + ignoredName + " has not joined this server before and could not be ignored. Double check capitalization and spelling.");
						}
						Set<UUID> ignoredPlayers = state.getIgnoredPlayerIds();
						if (ignoredPlayers.contains(ignoredId)) {
							ignoredPlayers.remove(ignoredId);
							target.sendMessage(Component.text("You are no longer ignoring " + ignoredName, NamedTextColor.GRAY));
						} else {
							target.sendMessage(Component.text("You are still not ignoring " + ignoredName, NamedTextColor.GRAY));
						}
					}
					return 1;
				})
				.register();
		}
	}
}
