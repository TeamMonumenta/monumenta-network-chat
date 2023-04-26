package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.MessageManager;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.bukkit.command.CommandSender;

public class ChatMessageCommand {
	public static void register() {
		if (NetworkChatProperties.getChatCommandModifyEnabled()) {
			List<Argument<?>> arguments = new ArrayList<>();

			for (String baseCommand : ChatCommand.COMMANDS) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("message"));
				arguments.add(new MultiLiteralArgument("delete"));
				arguments.add(new StringArgument("Message ID"));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executesNative((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.delete.message")) {
							throw CommandUtils.fail(sender, "You do not have permission to run this command.");
						}

						return deleteMessage(sender, (String) args[2]);
					})
					.register();

				arguments.clear();
				arguments.add(new MultiLiteralArgument("message"));
				arguments.add(new MultiLiteralArgument("deletefromsender"));
				arguments.add(new StringArgument("name").replaceSuggestions(ChatCommand.ALL_CACHED_PLAYER_NAMES_SUGGESTIONS));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.message.deletefromsender")) {
							throw CommandUtils.fail(sender, "You do not have permission to run this command.");
						}
						String targetName = (String) args[2];
						@Nullable UUID targetId = MonumentaRedisSyncAPI.cachedNameToUuid(targetName);
						if (targetId == null) {
							throw CommandUtils.fail(sender, "The player " + targetName + " has not joined this server before. Double check capitalization and spelling.");
						}

						MessageManager.deleteMessagesFromSender(targetId);
						return 1;
					})
					.register();

				arguments.clear();
				arguments.add(new MultiLiteralArgument("message"));
				arguments.add(new MultiLiteralArgument("clearchat"));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.message.clearchat")) {
							throw CommandUtils.fail(sender, "You do not have permission to run this command.");
						}

						MessageManager.clearChat();
						return 1;
					})
					.register();
			}
		}
	}

	private static int deleteMessage(CommandSender sender, String messageIdStr) throws WrapperCommandSyntaxException {
		UUID messageId;
		try {
			messageId = UUID.fromString(messageIdStr);
		} catch (Exception e) {
			throw CommandUtils.fail(sender, "Invalid message ID. Click a channel name to open the message GUI.");
		}

		MessageManager.deleteMessage(messageId);
		return 1;
	}
}
