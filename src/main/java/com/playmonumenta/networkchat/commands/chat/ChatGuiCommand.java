package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.Message;
import com.playmonumenta.networkchat.MessageManager;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatGuiCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		for (String baseCommand : ChatCommand.COMMANDS) {
			arguments.clear();
			arguments.add(new MultiLiteralArgument("gui"));
			arguments.add(new MultiLiteralArgument("message"));
			arguments.add(new GreedyStringArgument("message ID"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.gui.message")) {
						throw CommandUtils.fail(sender, "You do not have permission to run this command.");
					}

					messageGui(baseCommand, sender, (String) args[2]);
				})
				.register();
		}
	}

	private static void messageGui(String baseCommand, CommandSender sender, String messageIdStr) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player target)) {
			throw CommandUtils.fail(sender, "This command can only be run as a player.");
		} else {
			PlayerState playerState = PlayerStateManager.getPlayerState(target);
			if (playerState == null) {
				throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
			}

			UUID messageId;
			try {
				messageId = UUID.fromString(messageIdStr);
			} catch (Exception e) {
				throw CommandUtils.fail(sender, "Invalid message ID. Click a channel name to open the message GUI.");
			}

			Message message = MessageManager.getMessage(messageId);
			if (message == null) {
				throw CommandUtils.fail(sender, "That message is no longer available on this shard. Pause chat and avoid switching shards to keep messages loaded.");
			}
			Component gui = Component.empty();

			Channel channel = message.getChannel();
			if (channel != null) {
				gui = gui.append(Component.text(" "))
					.append(Component.text("[]", NamedTextColor.LIGHT_PURPLE)
						.hoverEvent(Component.text("Leave channel", NamedTextColor.LIGHT_PURPLE))
						.clickEvent(ClickEvent.runCommand("/" + baseCommand + " leave " + channel.getName())));
				gui = gui.append(Component.text(" "))
					.append(Component.text("[]", NamedTextColor.LIGHT_PURPLE)
						.hoverEvent(Component.text("My channel settings", NamedTextColor.LIGHT_PURPLE))
						.clickEvent(ClickEvent.suggestCommand("/" + baseCommand + " player settings channel " + channel.getName() + " ")));
				if (CommandUtils.hasPermission(target, "networkchat.rename")) {
					gui = gui.append(Component.text(" "))
						.append(Component.text("[]", NamedTextColor.LIGHT_PURPLE)
							.hoverEvent(Component.text("Rename channel", NamedTextColor.LIGHT_PURPLE))
							.clickEvent(ClickEvent.suggestCommand("/" + baseCommand + " channel rename " + channel.getName() + " ")));
				}
				if (CommandUtils.hasPermission(target, "networkchat.delete.channel")) {
					gui = gui.append(Component.text(" "))
						.append(Component.text("[]", NamedTextColor.RED)
							.hoverEvent(Component.text("Delete channel", NamedTextColor.RED))
							.clickEvent(ClickEvent.runCommand("/" + baseCommand + " channel delete " + channel.getName())));
				}

				if (message.senderIsPlayer()) {
					String fromName = message.getSenderName();

					if (channel.mayManage(target)) {
						gui = gui.append(Component.text(" "))
							.append(Component.text("[]", NamedTextColor.LIGHT_PURPLE)
								.hoverEvent(Component.text("Sender channel access", NamedTextColor.LIGHT_PURPLE))
								.clickEvent(ClickEvent.suggestCommand("/" + baseCommand + " channel access " + channel.getName() + " player " + fromName + " ")));
					}
				}
			}

			if (CommandUtils.hasPermission(target, "networkchat.delete.message")) {
				gui = gui.append(Component.text(" "))
					.append(Component.text("[]", NamedTextColor.RED)
						.hoverEvent(Component.text("Delete message", NamedTextColor.RED))
						.clickEvent(ClickEvent.runCommand("/" + baseCommand + " message delete " + messageIdStr)));
			}

			if (message.senderIsPlayer()) {
				if (CommandUtils.hasPermission(target, "networkchat.message.deletefromsender")) {
					String fromName = message.getSenderName();
					gui = gui.append(Component.text(" "))
						.append(Component.text("[]", NamedTextColor.RED)
							.hoverEvent(Component.text("Delete messages from sender", NamedTextColor.RED))
							.clickEvent(ClickEvent.runCommand("/" + baseCommand + " message deletefromsender " + fromName)));
				}
			}

			target.sendMessage(gui);
		}
	}
}
