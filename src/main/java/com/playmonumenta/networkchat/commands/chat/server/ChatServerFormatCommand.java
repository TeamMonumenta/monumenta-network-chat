package com.playmonumenta.networkchat.commands.chat.server;

import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.channel.ChannelAnnouncement;
import com.playmonumenta.networkchat.channel.ChannelGlobal;
import com.playmonumenta.networkchat.channel.ChannelLocal;
import com.playmonumenta.networkchat.channel.ChannelParty;
import com.playmonumenta.networkchat.channel.ChannelTeam;
import com.playmonumenta.networkchat.channel.ChannelWhisper;
import com.playmonumenta.networkchat.channel.ChannelWorld;
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
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class ChatServerFormatCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		for (String baseCommand : ChatCommand.COMMANDS) {
			arguments.clear();
			arguments.add(new MultiLiteralArgument("server"));
			arguments.add(new MultiLiteralArgument("format"));
			arguments.add(new MultiLiteralArgument("player",
				"entity",
				"sender",
				ChannelAnnouncement.CHANNEL_CLASS_ID,
				ChannelGlobal.CHANNEL_CLASS_ID,
				ChannelLocal.CHANNEL_CLASS_ID,
				ChannelParty.CHANNEL_CLASS_ID,
				ChannelTeam.CHANNEL_CLASS_ID,
				ChannelWhisper.CHANNEL_CLASS_ID,
				ChannelWorld.CHANNEL_CLASS_ID));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					String id = (String) args[2];
					TextColor color = NetworkChatPlugin.messageColor(id);
					String format = NetworkChatPlugin.messageFormat(id);
					if (format == null) {
						format = "";
					}
					format = format.replace("\n", "\\n");

					Component senderComponent = MessagingUtils.senderComponent(sender);
					sender.sendMessage(Component.text(id + " is " + format, color)
						.clickEvent(ClickEvent.suggestCommand("/" + baseCommand + " format default " + id + " " + format)));
					if (id.equals("player")) {
						sender.sendMessage(Component.text("Example: ").append(senderComponent));
					} else {
						String prefix = format.replace("<channel_color>", MessagingUtils.colorToMiniMessage(color));
						Component fullMessage = Component.empty()
							.append(MessagingUtils.CHANNEL_HEADER_FMT_MINIMESSAGE.deserialize(prefix,
								Placeholder.unparsed("channel_name", "ExampleChannel"),
								Placeholder.component("sender", senderComponent),
								Placeholder.component("receiver", senderComponent)))
							.append(Component.empty().color(color).append(Component.text("Test message")));

						sender.sendMessage(Component.text("Example message:", color));
						sender.sendMessage(fullMessage);
					}
					return 1;
				})
				.register();

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("server"));
				arguments.add(new MultiLiteralArgument("format"));
				arguments.add(new MultiLiteralArgument("player",
					"entity",
					"sender",
					ChannelAnnouncement.CHANNEL_CLASS_ID,
					ChannelGlobal.CHANNEL_CLASS_ID,
					ChannelLocal.CHANNEL_CLASS_ID,
					ChannelParty.CHANNEL_CLASS_ID,
					ChannelTeam.CHANNEL_CLASS_ID,
					ChannelWhisper.CHANNEL_CLASS_ID,
					ChannelWorld.CHANNEL_CLASS_ID));
				arguments.add(new GreedyStringArgument("format"));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executesNative((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.format.default")) {
							throw CommandUtils.fail(sender, "You do not have permission to change message formatting.");
						}

						String id = (String) args[2];
						TextColor color = NetworkChatPlugin.messageColor(id);
						String format = (String) args[3];
						NetworkChatPlugin.messageFormat(id, format.replace("\\n", "\n"));

						Component senderComponent = MessagingUtils.senderComponent(sender);
						sender.sendMessage(Component.text(id + " set to " + format, color)
							.clickEvent(ClickEvent.suggestCommand("/" + baseCommand + " format default " + id + " " + format)));
						if (id.equals("player")) {
							sender.sendMessage(Component.text("Example: ").append(senderComponent));
						} else {
							String prefix = format.replace("<channel_color>", MessagingUtils.colorToMiniMessage(color));
							Component fullMessage = Component.empty()
								.append(MessagingUtils.CHANNEL_HEADER_FMT_MINIMESSAGE.deserialize(prefix,
									Placeholder.unparsed("channel_name", "ExampleChannel"),
									Placeholder.component("sender", senderComponent),
									Placeholder.component("receiver", senderComponent)))
								.append(Component.empty().color(color).append(Component.text("Test message")));

							sender.sendMessage(Component.text("Example message:", color));
							sender.sendMessage(fullMessage);
						}
						return 1;
					})
					.register();
			}
		}
	}
}
