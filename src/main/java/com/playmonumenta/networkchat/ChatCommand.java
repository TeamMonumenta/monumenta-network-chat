package com.playmonumenta.networkchat;

import java.util.ArrayList;
import java.util.List;
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

import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;

public class ChatCommand {
	public static final String[] COMMANDS = new String[]{"chat", "ch", "chattest"};

	public static void register() {
		List<Argument> arguments = new ArrayList<>();

		arguments.add(new MultiLiteralArgument("new"));
		arguments.add(new StringArgument("Channel ID"));
		ChannelAnnouncement.registerNewChannelCommands(COMMANDS, new ArrayList<Argument>(arguments));
		ChannelLocal.registerNewChannelCommands(COMMANDS, new ArrayList<Argument>(arguments));
		ChannelGlobal.registerNewChannelCommands(COMMANDS, new ArrayList<Argument>(arguments));
		ChannelParty.registerNewChannelCommands(COMMANDS, new ArrayList<Argument>(arguments));
		ChannelWhisper.registerNewChannelCommands(COMMANDS, new ArrayList<Argument>(arguments));

		for (String baseCommand : COMMANDS) {
			arguments.clear();
			arguments.add(new MultiLiteralArgument("rename"));
			arguments.add(new StringArgument("Old Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getChatableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new StringArgument("New Channel ID"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return renameChannel(sender, (String)args[1], (String)args[2]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("setdefaultchannel"));
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getChannelNames(sender).toArray(new String[0]);
			}));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					if (!sender.hasPermission("networkchat.setdefaultchannel")) {
						CommandAPI.fail("You do not have permission to set the default channel.");
					}
					return ChannelManager.setDefaultChannel(sender, (String)args[1]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("forceload"));
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getChannelNames(sender).toArray(new String[0]);
			}));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					if (!sender.hasPermission("networkchat.forceload")) {
						CommandAPI.fail("You do not have permission to forceload channels.");
					}
					return ChannelManager.forceLoadChannel(sender, (String)args[1]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("delete"));
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
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
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
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
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
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
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
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
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
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
			arguments.add(new MultiLiteralArgument(ChannelSettings.mFlagKeys.stream().toArray(String[]::new)));
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
			arguments.add(new MultiLiteralArgument(ChannelSettings.mFlagKeys.stream().toArray(String[]::new)));
			arguments.add(new MultiLiteralArgument(ChannelSettings.mFlagValues.stream().toArray(String[]::new)));
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
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getListenableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument(ChannelSettings.mFlagKeys.stream().toArray(String[]::new)));
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

					String channelId = (String) args[3];
					Channel channel = ChannelManager.getChannel(channelId);
					if (channel == null) {
						CommandAPI.fail("No such channel " + channelId + ".");
					}

					ChannelSettings settings = state.channelSettings(channel);
					return settings.commandFlag(sender, (String) args[4]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new MultiLiteralArgument("my"));
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getListenableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument(ChannelSettings.mFlagKeys.stream().toArray(String[]::new)));
			arguments.add(new MultiLiteralArgument(ChannelSettings.mFlagValues.stream().toArray(String[]::new)));
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

					String channelId = (String) args[3];
					Channel channel = ChannelManager.getChannel(channelId);
					if (channel == null) {
						CommandAPI.fail("No such channel " + channelId + ".");
					}

					ChannelSettings settings = state.channelSettings(channel);
					return settings.commandFlag(sender, (String) args[4], (String) args[5]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getListenableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument(ChannelSettings.mFlagKeys.stream().toArray(String[]::new)));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelId = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelId);
					if (channel == null) {
						CommandAPI.fail("No such channel " + channelId + ".");
					}

					ChannelSettings settings = channel.channelSettings();
					return settings.commandFlag(sender, (String) args[3]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getListenableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument(ChannelSettings.mFlagKeys.stream().toArray(String[]::new)));
			arguments.add(new MultiLiteralArgument(ChannelSettings.mFlagValues.stream().toArray(String[]::new)));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelId = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelId);
					if (channel == null) {
						CommandAPI.fail("No such channel " + channelId + ".");
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
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getManageableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument("default"));
			arguments.add(new MultiLiteralArgument(ChannelPerms.mFlagKeys.stream().toArray(String[]::new)));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelId = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelId);
					if (channel == null) {
						CommandAPI.fail("No such channel " + channelId + ".");
					}

					ChannelPerms perms = channel.channelPerms();
					return perms.commandFlag(sender, (String) args[4]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("permissions"));
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getManageableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument("default"));
			arguments.add(new MultiLiteralArgument(ChannelPerms.mFlagKeys.stream().toArray(String[]::new)));
			arguments.add(new MultiLiteralArgument(ChannelPerms.mFlagValues.stream().toArray(String[]::new)));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelId = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelId);
					if (channel == null) {
						CommandAPI.fail("No such channel " + channelId + ".");
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
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getManageableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new StringArgument("name").overrideSuggestions((sender) -> {
				return MonumentaRedisSyncAPI.getAllCachedPlayerNames().toArray(String[]::new);
			}));
			arguments.add(new MultiLiteralArgument(ChannelPerms.mFlagKeys.stream().toArray(String[]::new)));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelId = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelId);
					if (channel == null) {
						CommandAPI.fail("No such channel " + channelId + ".");
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
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getManageableChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new StringArgument("name").overrideSuggestions((sender) -> {
				return MonumentaRedisSyncAPI.getAllCachedPlayerNames().toArray(String[]::new);
			}));
			arguments.add(new MultiLiteralArgument(ChannelPerms.mFlagKeys.stream().toArray(String[]::new)));
			arguments.add(new MultiLiteralArgument(ChannelPerms.mFlagValues.stream().toArray(String[]::new)));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelId = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelId);
					if (channel == null) {
						CommandAPI.fail("No such channel " + channelId + ".");
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
	}

	private static int renameChannel(CommandSender sender, String oldChannelId, String newChannelId) throws WrapperCommandSyntaxException {
		if (!sender.hasPermission("networkchat.rename")) {
			CommandAPI.fail("You do not have permission to rename channels.");
		}

		// May call CommandAPI.fail()
		ChannelManager.renameChannel(oldChannelId, newChannelId);
		sender.sendMessage(Component.text("Channel " + oldChannelId + " renamed to " + newChannelId + ".", NamedTextColor.GRAY));
		return 1;
	}

	private static int deleteChannel(CommandSender sender, String channelId) throws WrapperCommandSyntaxException {
		if (!sender.hasPermission("networkchat.delete")) {
			CommandAPI.fail("You do not have permission to delete channels.");
		}

		// May call CommandAPI.fail()
		ChannelManager.deleteChannel(channelId);
		sender.sendMessage(Component.text("Channel " + channelId + " deleted.", NamedTextColor.GRAY));
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

	private static int joinChannel(CommandSender sender, String channelId) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player)) {
			CommandAPI.fail("This command can only be run as a player.");
		}

		Player target = (Player) callee;
		PlayerState playerState = PlayerStateManager.getPlayerState(target);
		if (playerState == null) {
			CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
		}

		Channel channel = ChannelManager.getChannel(channelId);
		if (channel == null) {
			CommandAPI.fail("No such channel " + channelId + ".");
		}

		playerState.setActiveChannel(channel);
		target.sendMessage(Component.text("Joined channel " + channelId + ".", NamedTextColor.GRAY));
		return 1;
	}

	private static int leaveChannel(CommandSender sender, String channelId) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player)) {
			CommandAPI.fail("This command can only be run as a player.");
		}

		Player target = (Player) callee;
		PlayerState playerState = PlayerStateManager.getPlayerState(target);
		if (playerState == null) {
			CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
		}

		Channel channel = ChannelManager.getChannel(channelId);
		if (channel == null) {
			CommandAPI.fail("No such channel " + channelId + ".");
		}

		playerState.leaveChannel(channel);
		target.sendMessage(Component.text("Left channel " + channelId + ".", NamedTextColor.GRAY));
		return 1;
	}

	private static int setActiveChannel(CommandSender sender, String channelId) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player)) {
			CommandAPI.fail("Only players have an active channel.");
		}
		Player player = (Player) callee;

		PlayerState playerState = PlayerStateManager.getPlayerState(player);
		if (playerState == null) {
			CommandAPI.fail("That player has no chat state. Please report this bug.");
		}

		Channel channel = ChannelManager.getChannel(channelId);
		if (channel == null) {
			CommandAPI.fail("No such channel " + channelId + ".");
		}

		playerState.setActiveChannel(channel);
		player.sendMessage(Component.text("You are now typing in " + channelId + ".", NamedTextColor.GRAY));
		return 1;
	}

	private static int sendMessage(CommandSender sender, String channelId, String message) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (callee instanceof Player && sender != callee) {
			sender.sendMessage(Component.text("Hey! It's not nice to put words in people's mouths! Where are your manners?", NamedTextColor.RED));
			CommandAPI.fail("You cannot chat as another player.");
		}

		Channel channel = ChannelManager.getChannel(channelId);
		if (channel == null) {
			CommandAPI.fail("No such channel " + channelId + ".");
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
