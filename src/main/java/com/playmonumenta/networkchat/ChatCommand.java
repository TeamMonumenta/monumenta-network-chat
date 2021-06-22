package com.playmonumenta.networkchat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Player;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.Template;
import net.kyori.adventure.text.minimessage.transformation.Transformation;
import net.kyori.adventure.text.minimessage.transformation.TransformationType;
import net.kyori.adventure.text.minimessage.markdown.DiscordFlavor;

import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;

public class ChatCommand {
	public static final String[] COMMANDS = new String[]{"chat", "ch", "chattest"};
	public static final String[] COLOR_SUGGESTIONS = new String[]{"aqua", "dark_purple", "#0189af"};
	private static final MiniMessage MINIMESSAGE = MiniMessage.builder()
		.transformation(TransformationType.COLOR)
		.transformation(TransformationType.DECORATION)
		.markdown()
		.markdownFlavor(DiscordFlavor.get())
		.build();

	public static void register() {
		List<Argument> arguments = new ArrayList<>();

		arguments.add(new MultiLiteralArgument("new"));
		arguments.add(new StringArgument("Channel Name"));
		ChannelAnnouncement.registerNewChannelCommands(COMMANDS, new ArrayList<Argument>(arguments));
		ChannelLocal.registerNewChannelCommands(COMMANDS, new ArrayList<Argument>(arguments));
		ChannelGlobal.registerNewChannelCommands(COMMANDS, new ArrayList<Argument>(arguments));
		ChannelParty.registerNewChannelCommands(COMMANDS, new ArrayList<Argument>(arguments));
		ChannelWhisper.registerNewChannelCommands(COMMANDS, new ArrayList<Argument>(arguments));

		for (String baseCommand : COMMANDS) {
			arguments.clear();
			arguments.add(new MultiLiteralArgument("rename"));
			arguments.add(new StringArgument("Old Channel Name").overrideSuggestions((sender) -> {
				return ChannelManager.getChannelNames().toArray(new String[0]);
			}));
			arguments.add(new StringArgument("New Channel Name"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return renameChannel(sender, (String)args[1], (String)args[2]);
				})
				.register();

			for (String channelType : DefaultChannels.CHANNEL_TYPES) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("setdefaultchannel"));
				arguments.add(new MultiLiteralArgument("server"));
				arguments.add(new MultiLiteralArgument(channelType));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						if (!sender.hasPermission("networkchat.setdefaultchannel")) {
							CommandAPI.fail("You do not have permission to set the default channels.");
						}
						return ChannelManager.getDefaultChannels().command(sender, channelType, true);
					})
					.register();

				arguments.clear();
				arguments.add(new MultiLiteralArgument("setdefaultchannel"));
				arguments.add(new MultiLiteralArgument("server"));
				arguments.add(new MultiLiteralArgument(channelType));
				if (channelType.equals("default")) {
					arguments.add(new StringArgument("Channel Name").overrideSuggestions((sender) -> {
						return ChannelManager.getChannelNames().toArray(new String[0]);
					}));
				} else {
					arguments.add(new StringArgument("Channel Name").overrideSuggestions((sender) -> {
						return ChannelManager.getChannelNames(channelType).toArray(new String[0]);
					}));
				}
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						if (!sender.hasPermission("networkchat.setdefaultchannel")) {
							CommandAPI.fail("You do not have permission to set the default channels.");
						}
						return ChannelManager.getDefaultChannels().command(sender, channelType, (String)args[3]);
					})
					.register();

				arguments.clear();
				arguments.add(new MultiLiteralArgument("setdefaultchannel"));
				arguments.add(new MultiLiteralArgument("my"));
				arguments.add(new MultiLiteralArgument(channelType));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						CommandSender callee = CommandUtils.getCallee(sender);
						if (!(callee instanceof Player)) {
							CommandAPI.fail("This command can only be run as a player.");
						}

						Player target = (Player) callee;
						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
						}
						return state.defaultChannels().command(sender, channelType);
					})
					.register();

				arguments.clear();
				arguments.add(new MultiLiteralArgument("setdefaultchannel"));
				arguments.add(new MultiLiteralArgument("my"));
				arguments.add(new MultiLiteralArgument(channelType));
				if (channelType.equals("default")) {
					arguments.add(new StringArgument("Channel Name").overrideSuggestions((sender) -> {
						return ChannelManager.getChannelNames().toArray(new String[0]);
					}));
				} else {
					arguments.add(new StringArgument("Channel Name").overrideSuggestions((sender) -> {
						return ChannelManager.getChannelNames(channelType).toArray(new String[0]);
					}));
				}
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						CommandSender callee = CommandUtils.getCallee(sender);
						if (!(callee instanceof Player)) {
							CommandAPI.fail("This command can only be run as a player.");
						}

						Player target = (Player) callee;
						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
						}
						return state.defaultChannels().command(sender, channelType, (String)args[3]);
					})
					.register();
			}

			arguments.clear();
			arguments.add(new MultiLiteralArgument("delete"));
			arguments.add(new StringArgument("Channel Name").overrideSuggestions((sender) -> {
				return ChannelManager.getChatableChannelNames(sender).toArray(new String[0]);
			}));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return deleteChannel(sender, (String)args[1]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("resetall"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return resetAll(sender);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("say"));
			arguments.add(new StringArgument("Channel Name").overrideSuggestions((sender) -> {
				return ChannelManager.getChatableChannelNames(sender).toArray(new String[0]);
			}));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					setActiveChannel(sender, (String)args[1]);
					return 1;
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("say"));
			arguments.add(new StringArgument("Channel Name").overrideSuggestions((sender) -> {
				return ChannelManager.getChatableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new GreedyStringArgument("Message"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					sendMessage(sender, (String)args[1], (String)args[2]);
					return 1;
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("join"));
			arguments.add(new StringArgument("Channel Name").overrideSuggestions((sender) -> {
				return ChannelManager.getListenableChannelNames(sender).toArray(new String[0]);
			}));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return joinChannel(sender, (String)args[1]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("leave"));
			arguments.add(new StringArgument("Channel Name").overrideSuggestions((sender) -> {
				return ChannelManager.getListenableChannelNames(sender).toArray(new String[0]);
			}));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return leaveChannel(sender, (String)args[1]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("pause"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return pause(sender);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("unpause"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return unpause(sender);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("listplayers"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					RemotePlayerManager.showOnlinePlayers((Audience) CommandUtils.getCallee(sender));
					return 1;
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("resetnick"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player)) {
						CommandAPI.fail("This command can only be run as a player.");
					}

					Player target = (Player) callee;
					target.displayName(null);
					target.playerListName(null);
					return 1;
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new MultiLiteralArgument("my"));
			arguments.add(new MultiLiteralArgument("default"));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagKeys()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player)) {
						CommandAPI.fail("This command can only be run as a player.");
					}

					Player target = (Player) callee;
					PlayerState state = PlayerStateManager.getPlayerState(target);
					if (state == null) {
						CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
					}
					ChannelSettings settings = state.channelSettings();
					return settings.commandFlag(sender, (String) args[3]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new MultiLiteralArgument("my"));
			arguments.add(new MultiLiteralArgument("default"));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagKeys()));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagValues()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player)) {
						CommandAPI.fail("This command can only be run as a player.");
					}

					Player target = (Player) callee;
					PlayerState state = PlayerStateManager.getPlayerState(target);
					if (state == null) {
						CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
					}
					ChannelSettings settings = state.channelSettings();
					return settings.commandFlag(sender, (String) args[3], (String) args[4]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new MultiLiteralArgument("my"));
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new StringArgument("Channel Name").overrideSuggestions((sender) -> {
				return ChannelManager.getListenableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagKeys()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player)) {
						CommandAPI.fail("This command can only be run as a player.");
					}

					Player target = (Player) callee;
					PlayerState state = PlayerStateManager.getPlayerState(target);
					if (state == null) {
						CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
					}

					String channelName = (String) args[3];
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						CommandAPI.fail("No such channel " + channelName + ".");
					}

					ChannelSettings settings = state.channelSettings(channel);
					return settings.commandFlag(sender, (String) args[4]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new MultiLiteralArgument("my"));
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new StringArgument("Channel Name").overrideSuggestions((sender) -> {
				return ChannelManager.getListenableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagKeys()));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagValues()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player)) {
						CommandAPI.fail("This command can only be run as a player.");
					}

					Player target = (Player) callee;
					PlayerState state = PlayerStateManager.getPlayerState(target);
					if (state == null) {
						CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
					}

					String channelName = (String) args[3];
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						CommandAPI.fail("No such channel " + channelName + ".");
					}

					ChannelSettings settings = state.channelSettings(channel);
					return settings.commandFlag(sender, (String) args[4], (String) args[5]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new StringArgument("Channel Name").overrideSuggestions((sender) -> {
				return ChannelManager.getListenableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagKeys()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelName = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						CommandAPI.fail("No such channel " + channelName + ".");
					}

					ChannelSettings settings = channel.channelSettings();
					return settings.commandFlag(sender, (String) args[3]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new StringArgument("Channel Name").overrideSuggestions((sender) -> {
				return ChannelManager.getListenableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagKeys()));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagValues()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelName = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						CommandAPI.fail("No such channel " + channelName + ".");
					}

					ChannelSettings settings = channel.channelSettings();
					int result = settings.commandFlag(sender, (String) args[3], (String) args[4]);
					ChannelManager.saveChannel(channel);
					return result;
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("permissions"));
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new StringArgument("Channel Name").overrideSuggestions((sender) -> {
				return ChannelManager.getManageableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument("default"));
			arguments.add(new MultiLiteralArgument(ChannelPerms.getFlagKeys()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelName = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						CommandAPI.fail("No such channel " + channelName + ".");
					}

					ChannelPerms perms = channel.channelPerms();
					return perms.commandFlag(sender, (String) args[4]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("permissions"));
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new StringArgument("Channel Name").overrideSuggestions((sender) -> {
				return ChannelManager.getManageableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument("default"));
			arguments.add(new MultiLiteralArgument(ChannelPerms.getFlagKeys()));
			arguments.add(new MultiLiteralArgument(ChannelPerms.getFlagValues()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelName = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						CommandAPI.fail("No such channel " + channelName + ".");
					}
					if (!channel.mayManage(sender)) {
						CommandAPI.fail("You do not have permission to manage channel " + channel.getName() + ".");
					}

					ChannelPerms perms = channel.channelPerms();
					int result = perms.commandFlag(sender, (String) args[4], (String) args[5]);
					ChannelManager.saveChannel(channel);
					return result;
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("permissions"));
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new StringArgument("Channel Name").overrideSuggestions((sender) -> {
				return ChannelManager.getManageableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new StringArgument("name").overrideSuggestions((sender) -> {
				return MonumentaRedisSyncAPI.getAllCachedPlayerNames().toArray(String[]::new);
			}));
			arguments.add(new MultiLiteralArgument(ChannelPerms.getFlagKeys()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelName = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						CommandAPI.fail("No such channel " + channelName + ".");
					}
					if (!channel.mayManage(sender)) {
						CommandAPI.fail("You do not have permission to manage channel " + channel.getName() + ".");
					}

					String playerName = (String) args[4];
					UUID playerId = MonumentaRedisSyncAPI.cachedNameToUuid(playerName);
					if (playerId == null) {
						CommandAPI.fail("No such player " + playerName + ".");
					}
					ChannelPerms perms = channel.playerPerms(playerId);
					return perms.commandFlag(sender, (String) args[5]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("permissions"));
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new StringArgument("Channel Name").overrideSuggestions((sender) -> {
				return ChannelManager.getManageableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new StringArgument("name").overrideSuggestions((sender) -> {
				return MonumentaRedisSyncAPI.getAllCachedPlayerNames().toArray(String[]::new);
			}));
			arguments.add(new MultiLiteralArgument(ChannelPerms.getFlagKeys()));
			arguments.add(new MultiLiteralArgument(ChannelPerms.getFlagValues()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelName = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						CommandAPI.fail("No such channel " + channelName + ".");
					}
					if (!channel.mayManage(sender)) {
						CommandAPI.fail("You do not have permission to manage channel " + channel.getName() + ".");
					}

					String playerName = (String) args[4];
					UUID playerId = MonumentaRedisSyncAPI.cachedNameToUuid(playerName);
					if (playerId == null) {
						CommandAPI.fail("No such player " + playerName + ".");
					}
					ChannelPerms perms = channel.playerPerms(playerId);
					int result = perms.commandFlag(sender, (String) args[5], (String) args[6]);
					if (perms.isDefault()) {
						channel.clearPlayerPerms(playerId);
					}
					ChannelManager.saveChannel(channel);
					return result;
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("visibility"));
			arguments.add(new MultiLiteralArgument("default"));
			arguments.add(new MultiLiteralArgument(MessageVisibility.getVisibilityKeys()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return PlayerStateManager.getDefaultMessageVisibility().commandVisibility(sender, (String) args[2]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("visibility"));
			arguments.add(new MultiLiteralArgument("default"));
			arguments.add(new MultiLiteralArgument(MessageVisibility.getVisibilityKeys()));
			arguments.add(new MultiLiteralArgument(MessageVisibility.getVisibilityValues()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					if (!sender.hasPermission("networkchat.visibility.default")) {
						CommandAPI.fail("You do not have permission to change the default message visibility.");
					}
					int result = PlayerStateManager.getDefaultMessageVisibility().commandVisibility(sender, (String) args[2], (String) args[3]);
					PlayerStateManager.saveSettings();
					return result;
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("color"));
			arguments.add(new MultiLiteralArgument("default"));
			arguments.add(new MultiLiteralArgument(ChannelAnnouncement.CHANNEL_CLASS_ID,
				ChannelGlobal.CHANNEL_CLASS_ID,
				ChannelLocal.CHANNEL_CLASS_ID,
				ChannelParty.CHANNEL_CLASS_ID,
				ChannelWhisper.CHANNEL_CLASS_ID));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					if (!sender.hasPermission("networkchat.format.default")) {
						CommandAPI.fail("You do not have permission to change the default channel formats.");
					}
					String id = (String) args[2];
					TextColor color = NetworkChatPlugin.messageColor(id);
					sender.sendMessage(Component.text(id + " is " + MessagingUtils.colorToString(color), color));
					return 1;
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("color"));
			arguments.add(new MultiLiteralArgument("default"));
			arguments.add(new MultiLiteralArgument(ChannelAnnouncement.CHANNEL_CLASS_ID,
				ChannelGlobal.CHANNEL_CLASS_ID,
				ChannelLocal.CHANNEL_CLASS_ID,
				ChannelParty.CHANNEL_CLASS_ID,
				ChannelWhisper.CHANNEL_CLASS_ID));
			arguments.add(new GreedyStringArgument("color").overrideSuggestions((sender) -> {
				return COLOR_SUGGESTIONS;
			}));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					if (!sender.hasPermission("networkchat.format.default")) {
						CommandAPI.fail("You do not have permission to change the default channel formats.");
					}
					String id = (String) args[2];
					String colorString = (String) args[3];
					TextColor color = MessagingUtils.colorFromString(colorString);
					if (color == null) {
						CommandAPI.fail("No such color " + colorString);
					}
					NetworkChatPlugin.messageColor(id, color);
					sender.sendMessage(Component.text(id + " set to " + MessagingUtils.colorToString(color), color));
					return 1;
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("format"));
			arguments.add(new MultiLiteralArgument("default"));
			arguments.add(new MultiLiteralArgument("player",
				ChannelAnnouncement.CHANNEL_CLASS_ID,
				ChannelGlobal.CHANNEL_CLASS_ID,
				ChannelLocal.CHANNEL_CLASS_ID,
				ChannelParty.CHANNEL_CLASS_ID,
				ChannelWhisper.CHANNEL_CLASS_ID));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					if (!sender.hasPermission("networkchat.format.default")) {
						CommandAPI.fail("You do not have permission to change the default channel formats.");
					}
					String id = (String) args[2];
					TextColor color = NetworkChatPlugin.messageColor(id);
					String format = NetworkChatPlugin.messageFormat(id);

					Component senderComponent = MessagingUtils.senderComponent(sender);
					sender.sendMessage(Component.text(id + " is " + format, color)
						.insertion("/chattest format default " + id + " " + format));
					if (id.equals("player")) {
						sender.sendMessage(Component.text("Example: ").append(senderComponent));
					} else {
						String prefix = format.replace("<channel_color>", MessagingUtils.colorToMiniMessage(color));
						Component fullMessage = Component.empty()
							.insertion("/chattest format default " + id + " " + format)
							.append(MINIMESSAGE.parse(prefix, List.of(Template.of("channel_name", "ExampleChannel"),
								Template.of("sender", senderComponent),
								Template.of("receiver", senderComponent))))
							.append(Component.empty().color(color).append(Component.text("Test message")));

						sender.sendMessage(Component.text("Example message:", color));
						sender.sendMessage(fullMessage);
					}
					return 1;
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("format"));
			arguments.add(new MultiLiteralArgument("default"));
			arguments.add(new MultiLiteralArgument("player",
				ChannelAnnouncement.CHANNEL_CLASS_ID,
				ChannelGlobal.CHANNEL_CLASS_ID,
				ChannelLocal.CHANNEL_CLASS_ID,
				ChannelParty.CHANNEL_CLASS_ID,
				ChannelWhisper.CHANNEL_CLASS_ID));
			arguments.add(new GreedyStringArgument("format"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					if (!sender.hasPermission("networkchat.format.default")) {
						CommandAPI.fail("You do not have permission to change the default channel formats.");
					}
					String id = (String) args[2];
					TextColor color = NetworkChatPlugin.messageColor(id);
					String format = (String) args[3];
					NetworkChatPlugin.messageFormat(id, format);

					Component senderComponent = MessagingUtils.senderComponent(sender);
					sender.sendMessage(Component.text(id + " set to " + format, color)
						.insertion("/chattest format default " + id + " " + format));
					if (id.equals("player")) {
						sender.sendMessage(Component.text("Example: ").append(senderComponent));
					} else {
						String prefix = format.replace("<channel_color>", MessagingUtils.colorToMiniMessage(color));
						Component fullMessage = Component.empty()
							.append(MINIMESSAGE.parse(prefix, List.of(Template.of("channel_name", "ExampleChannel"),
								Template.of("sender", senderComponent),
								Template.of("receiver", senderComponent))))
							.append(Component.empty().color(color).append(Component.text("Test message")));

						sender.sendMessage(Component.text("Example message:", color));
						sender.sendMessage(fullMessage);
					}
					return 1;
				})
				.register();
		}

		new CommandAPICommand("pausechat")
			.executes((sender, args) -> {
				return togglePause(sender);
			})
			.register();

		new CommandAPICommand("pc")
			.executes((sender, args) -> {
				return togglePause(sender);
			})
			.register();

		Set<String> usedShortcuts = new HashSet<>();
		for (String channelType : DefaultChannels.CHANNEL_TYPES) {
			if (channelType.equals("default")) {
				continue;
			}

			new CommandAPICommand(channelType)
				.executes((sender, args) -> {
					setActiveToDefault(sender, channelType);
					return 1;
				})
				.register();

			arguments.clear();
			arguments.add(new GreedyStringArgument("Message"));
			new CommandAPICommand(channelType)
				.withArguments(arguments)
				.executes((sender, args) -> {
					sendMessageInDefault(sender, channelType, (String)args[0]);
					return 1;
				})
				.register();

			String shortcut = channelType.substring(0, 1);
			if (usedShortcuts.contains(shortcut)) {
				continue;
			}

			new CommandAPICommand(shortcut)
				.executes((sender, args) -> {
					setActiveToDefault(sender, channelType);
					return 1;
				})
				.register();

			arguments.clear();
			arguments.add(new GreedyStringArgument("Message"));
			new CommandAPICommand(shortcut)
				.withArguments(arguments)
				.executes((sender, args) -> {
					sendMessageInDefault(sender, channelType, (String)args[0]);
					return 1;
				})
				.register();
		}
	}

	private static int renameChannel(CommandSender sender, String oldChannelName, String newChannelName) throws WrapperCommandSyntaxException {
		if (!sender.hasPermission("networkchat.rename")) {
			CommandAPI.fail("You do not have permission to rename channels.");
		}

		// May call CommandAPI.fail()
		ChannelManager.renameChannel(oldChannelName, newChannelName);
		sender.sendMessage(Component.text("Channel " + oldChannelName + " renamed to " + newChannelName + ".", NamedTextColor.GRAY));
		return 1;
	}

	private static int deleteChannel(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		if (!sender.hasPermission("networkchat.delete")) {
			CommandAPI.fail("You do not have permission to delete channels.");
		}

		// May call CommandAPI.fail()
		ChannelManager.deleteChannel(channelName);
		sender.sendMessage(Component.text("Channel " + channelName + " deleted.", NamedTextColor.GRAY));
		return 1;
	}

	private static int resetAll(CommandSender sender) throws WrapperCommandSyntaxException {
		if (!sender.hasPermission("networkchat.resetall")) {
			CommandAPI.fail("You do not have permission to delete channels.");
		}

		ChannelManager.resetAll();
		sender.sendMessage(Component.text("All things reset.", NamedTextColor.RED));
		return 1;
	}

	private static int joinChannel(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player)) {
			CommandAPI.fail("This command can only be run as a player.");
		}

		Player target = (Player) callee;
		PlayerState playerState = PlayerStateManager.getPlayerState(target);
		if (playerState == null) {
			CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
		}

		Channel channel = ChannelManager.getChannel(channelName);
		if (channel == null) {
			CommandAPI.fail("No such channel " + channelName + ".");
		}

		playerState.setActiveChannel(channel);
		target.sendMessage(Component.text("Joined channel " + channelName + ".", NamedTextColor.GRAY));
		return 1;
	}

	private static int leaveChannel(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player)) {
			CommandAPI.fail("This command can only be run as a player.");
		}

		Player target = (Player) callee;
		PlayerState playerState = PlayerStateManager.getPlayerState(target);
		if (playerState == null) {
			CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
		}

		Channel channel = ChannelManager.getChannel(channelName);
		if (channel == null) {
			CommandAPI.fail("No such channel " + channelName + ".");
		}

		playerState.leaveChannel(channel);
		target.sendMessage(Component.text("Left channel " + channelName + ".", NamedTextColor.GRAY));
		return 1;
	}

	private static int setActiveChannel(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player)) {
			CommandAPI.fail("Only players have an active channel.");
		}
		Player player = (Player) callee;

		PlayerState playerState = PlayerStateManager.getPlayerState(player);
		if (playerState == null) {
			CommandAPI.fail("You have no chat state. Please report this bug and reconnect to the server.");
		}

		Channel channel = ChannelManager.getChannel(channelName);
		if (channel == null) {
			CommandAPI.fail("No such channel " + channelName + ".");
		}

		playerState.setActiveChannel(channel);
		player.sendMessage(Component.text("You are now typing in " + channelName + ".", NamedTextColor.GRAY));
		return 1;
	}

	private static int setActiveToDefault(CommandSender sender, String channelType) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player)) {
			CommandAPI.fail("Only players have an active channel.");
		}
		Player player = (Player) callee;

		PlayerState playerState = PlayerStateManager.getPlayerState(player);
		if (playerState == null) {
			CommandAPI.fail("You have no chat state. Please report this bug and reconnect to the server.");
		}

		Channel channel = playerState.getDefaultChannel(channelType);
		if (channel == null) {
			CommandAPI.fail("No default for " + channelType + " channel type.");
		}

		playerState.setActiveChannel(channel);
		player.sendMessage(Component.text("You are now typing in " + channel.getName() + ".", NamedTextColor.GRAY));
		return 1;
	}

	private static int sendMessage(CommandSender sender, String channelName, String message) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (callee instanceof Player && sender != callee) {
			sender.sendMessage(Component.text("Hey! It's not nice to put words in people's mouths! Where are your manners?", NamedTextColor.RED));
			CommandAPI.fail("You cannot chat as another player.");
		}

		Channel channel = ChannelManager.getChannel(channelName);
		if (channel == null) {
			CommandAPI.fail("No such channel " + channelName + ".");
		}

		channel.sendMessage(callee, message);
		return 1;
	}

	private static int sendMessageInDefault(CommandSender sender, String channelType, String message) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (callee instanceof Player && sender != callee) {
			sender.sendMessage(Component.text("Hey! It's not nice to put words in people's mouths! Where are your manners?", NamedTextColor.RED));
			CommandAPI.fail("You cannot chat as another player.");
		}

		Channel channel = null;
		if (callee instanceof Player) {
			channel = PlayerStateManager.getPlayerState((Player) callee).getDefaultChannel(channelType);
		} else {
			channel = ChannelManager.getDefaultChannel(channelType);
		}
		if (channel == null) {
			CommandAPI.fail("No default for " + channelType + " channel type.");
		}

		channel.sendMessage(callee, message);
		return 1;
	}

	private static int pause(CommandSender sender) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player)) {
			CommandAPI.fail("This command can only be run as a player.");
		}

		Player target = (Player) callee;
		PlayerState playerState = PlayerStateManager.getPlayerState(target);
		if (playerState == null) {
			CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
		}

		playerState.pauseChat();
		target.sendMessage(Component.text("Chat paused.", NamedTextColor.GRAY));
		return 1;
	}

	private static int unpause(CommandSender sender) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player)) {
			CommandAPI.fail("This command can only be run as a player.");
		}

		Player target = (Player) callee;
		PlayerState playerState = PlayerStateManager.getPlayerState(target);
		if (playerState == null) {
			CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
		}

		target.sendMessage(Component.text("Unpausing chat.", NamedTextColor.GRAY));
		playerState.unpauseChat();
		return 1;
	}

	private static int togglePause(CommandSender sender) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player)) {
			CommandAPI.fail("This command can only be run as a player.");
		}

		Player target = (Player) callee;
		PlayerState playerState = PlayerStateManager.getPlayerState(target);
		if (playerState == null) {
			CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
		}

		if (playerState.isPaused()) {
			target.sendMessage(Component.text("Unpausing chat.", NamedTextColor.GRAY));
			playerState.unpauseChat();
		} else {
			playerState.pauseChat();
			target.sendMessage(Component.text("Chat paused.", NamedTextColor.GRAY));
		}
		return 1;
	}
}
