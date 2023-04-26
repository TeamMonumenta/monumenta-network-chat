package com.playmonumenta.networkchat.commands.chat.server;

import com.playmonumenta.networkchat.MessageVisibility;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import java.util.ArrayList;
import java.util.List;

public class ChatServerMessageVisibilityCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		for (String baseCommand : ChatCommand.COMMANDS) {
			arguments.clear();
			arguments.add(new MultiLiteralArgument("server"));
			arguments.add(new MultiLiteralArgument("messagevisibility"));
			arguments.add(new MultiLiteralArgument("visibility"));
			arguments.add(new MultiLiteralArgument(MessageVisibility.getVisibilityKeys()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					return PlayerStateManager.getDefaultMessageVisibility().commandVisibility(sender, (String) args[2]);
				})
				.register();

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("server"));
				arguments.add(new MultiLiteralArgument("messagevisibility"));
				arguments.add(new MultiLiteralArgument(MessageVisibility.getVisibilityKeys()));
				arguments.add(new MultiLiteralArgument(MessageVisibility.getVisibilityValues()));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executesNative((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.visibility.default")) {
							throw CommandUtils.fail(sender, "You do not have permission to change server-wide message visibility.");
						}

						int result = PlayerStateManager.getDefaultMessageVisibility().commandVisibility(sender, (String) args[2], (String) args[3]);
						PlayerStateManager.saveSettings();
						return result;
					})
					.register();
			}
		}
	}
}
