package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.commands.ChatCommand;
import dev.jorel.commandapi.arguments.LiteralArgument;

public class ChatReloadCommand {
	public static void register() {
		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("reload"))
			.executesNative((sender, args) -> {
				NetworkChatPlugin.reload(sender);
			})
			.register();
	}
}
