package com.playmonumenta.networkchat.commands.chat.player;

import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatPlayerProfileMessageCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		for (String baseCommand : ChatCommand.COMMANDS) {
			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("profilemessage"));
			arguments.add(new MultiLiteralArgument("get"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					String profileMessage;
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						throw CommandUtils.fail(sender, "This command can only be run as a player.");
					} else {
						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
						}
						profileMessage = state.profileMessage();
						if (profileMessage.isEmpty()) {
							target.sendMessage(Component.text("Your profile message is blank.", NamedTextColor.GRAY));
						} else {
							target.sendMessage(Component.text("Your profile message is:", NamedTextColor.GRAY));
							target.sendMessage(state.profileMessageComponent()
								.clickEvent(ClickEvent.suggestCommand("/" + baseCommand + " profilemessage set " + profileMessage)));
						}
					}
					return profileMessage.length();
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("profilemessage"));
			arguments.add(new MultiLiteralArgument("set"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						throw CommandUtils.fail(sender, "This command can only be run as a player.");
					} else {
						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
						}
						target.sendMessage(Component.text("Your profile message has been cleared.", NamedTextColor.GRAY));
						state.profileMessage("");
					}
					return 0;
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("profilemessage"));
			arguments.add(new MultiLiteralArgument("set"));
			arguments.add(new GreedyStringArgument("message"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.setprofilemessage")) {
						throw CommandUtils.fail(sender, "You do not have permission to run this command.");
					}

					String profileMessage = (String) args[3];
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						throw CommandUtils.fail(sender, "This command can only be run as a player.");
					} else {
						if (CommandUtils.checkSudoCommandDisallowed(sender)) {
							throw CommandUtils.fail(sender, "You may not change other player's profile messages on this shard.");
						}

						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
						}
						target.sendMessage(Component.text("Your profile message has been set to:", NamedTextColor.GRAY));
						state.profileMessage(profileMessage);
						target.sendMessage(state.profileMessageComponent()
							.clickEvent(ClickEvent.suggestCommand("/" + baseCommand + " profilemessage set " + profileMessage)));
					}
					return profileMessage.length();
				})
				.register();
		}
	}
}
