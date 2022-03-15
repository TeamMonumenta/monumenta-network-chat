package com.playmonumenta.networkchat;

import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.FileUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument.EntitySelector;
import dev.jorel.commandapi.arguments.FloatArgument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.SoundArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.Template;
import net.kyori.adventure.text.minimessage.template.TemplateResolver;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatCommand {
	public static final String[] COMMANDS = new String[]{"chat", "ch", "networkchat"};
	public static final String[] COLOR_SUGGESTIONS = new String[]{"aqua", "dark_purple", "#0189af"};

	static class HelpTreeNode extends TreeMap<String, HelpTreeNode> {
		public @Nullable String mResourcePath = null;
		public @Nullable String mExternalPath = null;
	}

	public static void register(NetworkChatPlugin plugin, final @Nullable ZipFile zip) {
		// Start loading entries for help command
		final HelpTreeNode helpTree = new HelpTreeNode();

		// Load from plugin jar (null if unable to load and exception has been logged)
		if (zip != null) {
			for (Enumeration<? extends ZipEntry> zipEntries = zip.entries(); zipEntries.hasMoreElements();) {
				ZipEntry entry = zipEntries.nextElement();
				String resourceName = entry.getName();
				if (!resourceName.startsWith("help/") || resourceName.endsWith("/")) {
					continue;
				}
				HelpTreeNode node = helpTree;
				for (String argString : resourceName.substring(5).split("/")) {
					if (argString.endsWith(".txt")) {
						argString = argString.substring(0, argString.length() - 4);
					}
					@Nullable HelpTreeNode testNode = node.get(argString);
					if (testNode == null) {
						testNode = new HelpTreeNode();
						node.put(argString, testNode);
					}
					node = testNode;
				}
				node.mResourcePath = resourceName;
			}
		}

		// Load from plugin data folder
		String folderLocation = plugin.getDataFolder() + File.separator + "help";
		ArrayList<File> listOfFiles;
		try {
			File directory = new File(folderLocation);
			if (!directory.exists()) {
				if (directory.mkdirs()) {
					plugin.getLogger().info("Created plugin help directory.");
				}
			}

			listOfFiles = FileUtils.getFilesInDirectory(folderLocation, ".txt");
		} catch (IOException e) {
			plugin.getLogger().severe("Caught exception trying to load help files from plugin folder.");
			return;
		}
		Collections.sort(listOfFiles);
		for (File file : listOfFiles) {
			String filePath = file.toString();
			String subPath = filePath.substring(folderLocation.length() + 1, filePath.length() - 4);

			HelpTreeNode node = helpTree;
			for (String argString : subPath.split("/")) {
				@Nullable HelpTreeNode testNode = node.get(argString);
				if (testNode == null) {
					testNode = new HelpTreeNode();
					node.put(argString, testNode);
				}
				node = testNode;
			}
			node.mExternalPath = filePath;
		}

		// Register all discovered help entries
		registerHelpCommandNode(zip, new ArrayList<>(), helpTree);

		List<Argument> arguments = new ArrayList<>();

		if (NetworkChatProperties.getChatCommandCreateEnabled()) {
			arguments.add(new MultiLiteralArgument("new"));
			arguments.add(new StringArgument("channel name"));
			ChannelAnnouncement.registerNewChannelCommands(COMMANDS, new ArrayList<>(arguments));
			ChannelLocal.registerNewChannelCommands(COMMANDS, new ArrayList<>(arguments));
			ChannelGlobal.registerNewChannelCommands(COMMANDS, new ArrayList<>(arguments));
			ChannelParty.registerNewChannelCommands(COMMANDS, new ArrayList<>(arguments));
		}
		ChannelTeam.registerNewChannelCommands();
		ChannelWhisper.registerNewChannelCommands();

		for (String baseCommand : COMMANDS) {
			arguments.clear();
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new MultiLiteralArgument("access"));
			arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
				ChannelManager.getManageableChannelNames(info.sender()).toArray(new String[0])
			));
			arguments.add(new MultiLiteralArgument("default"));
			arguments.add(new MultiLiteralArgument(ChannelAccess.getFlagKeys()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelName = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}

					ChannelAccess perms = channel.channelAccess();
					return perms.commandFlag(sender, (String) args[4]);
				})
				.register();

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("access"));
				arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
					ChannelManager.getManageableChannelNames(info.sender()).toArray(new String[0])
				));
				arguments.add(new MultiLiteralArgument("default"));
				arguments.add(new MultiLiteralArgument(ChannelAccess.getFlagKeys()));
				arguments.add(new MultiLiteralArgument(ChannelAccess.getFlagValues()));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						String channelName = (String) args[2];
						Channel channel = ChannelManager.getChannel(channelName);
						if (channel == null) {
							CommandUtils.fail(sender, "No such channel " + channelName + ".");
						}
						if (!channel.mayManage(sender)) {
							CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
						}

						ChannelAccess perms = channel.channelAccess();
						int result = perms.commandFlag(sender, (String) args[4], (String) args[5]);
						ChannelManager.saveChannel(channel);
						return result;
					})
					.register();
			}

			arguments.clear();
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new MultiLiteralArgument("access"));
			arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
				ChannelManager.getManageableChannelNames(info.sender()).toArray(new String[0])
			));
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new StringArgument("name").replaceSuggestions(info ->
				MonumentaRedisSyncAPI.getAllCachedPlayerNames().toArray(String[]::new)
			));
			arguments.add(new MultiLiteralArgument(ChannelAccess.getFlagKeys()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelName = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}
					if (!channel.mayManage(sender)) {
						CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
					}

					String playerName = (String) args[4];
					UUID playerId = MonumentaRedisSyncAPI.cachedNameToUuid(playerName);
					if (playerId == null) {
						CommandUtils.fail(sender, "No such player " + playerName + ".");
					}
					ChannelAccess perms = channel.playerAccess(playerId);
					return perms.commandFlag(sender, (String) args[5]);
				})
				.register();

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("access"));
				arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
					ChannelManager.getManageableChannelNames(info.sender()).toArray(new String[0])
				));
				arguments.add(new MultiLiteralArgument("player"));
				arguments.add(new StringArgument("name").replaceSuggestions(info ->
					MonumentaRedisSyncAPI.getAllCachedPlayerNames().toArray(String[]::new)));
				arguments.add(new MultiLiteralArgument(ChannelAccess.getFlagKeys()));
				arguments.add(new MultiLiteralArgument(ChannelAccess.getFlagValues()));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						String channelName = (String) args[2];
						Channel channel = ChannelManager.getChannel(channelName);
						if (channel == null) {
							CommandUtils.fail(sender, "No such channel " + channelName + ".");
						}
						if (!channel.mayManage(sender)) {
							CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
						}

						String playerName = (String) args[4];
						UUID playerId = MonumentaRedisSyncAPI.cachedNameToUuid(playerName);
						if (playerId == null) {
							CommandUtils.fail(sender, "No such player " + playerName + ".");
						}
						ChannelAccess perms = channel.playerAccess(playerId);
						int result = perms.commandFlag(sender, (String) args[5], (String) args[6]);
						if (perms.isDefault()) {
							channel.resetPlayerAccess(playerId);
						}
						ChannelManager.saveChannel(channel);
						return result;
					})
					.register();
			}

			arguments.clear();
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new MultiLiteralArgument("autojoin"));
			arguments.add(new StringArgument("channel name").replaceSuggestions(info ->
				ChannelManager.getAutoJoinableChannelNames(info.sender()).toArray(new String[0])
			));
			arguments.add(new MultiLiteralArgument("get"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelName = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelName);

					if (channel == null) {
						CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}

					if (!channel.mayManage(sender)) {
						CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
					}

					if (!(channel instanceof ChannelAutoJoin)) {
						CommandUtils.fail(sender, "This channel has no auto join setting.");
					}

					sender.sendMessage("Channel " + channelName + " auto join: " + (((ChannelAutoJoin) channel).getAutoJoin() ? "enabled." : "disabled."));

					return 1;
				})
				.register();

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("autojoin"));
				arguments.add(new StringArgument("channel name").replaceSuggestions(info ->
					ChannelManager.getAutoJoinableChannelNames(info.sender()).toArray(new String[0])
				));
				arguments.add(new MultiLiteralArgument("enable", "disable"));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						String channelName = (String) args[2];
						Channel channel = ChannelManager.getChannel(channelName);

						if (channel == null) {
							CommandUtils.fail(sender, "No such channel " + channelName + ".");
						}

						if (!channel.mayManage(sender)) {
							CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
						}

						if (!(channel instanceof ChannelAutoJoin)) {
							CommandUtils.fail(sender, "This channel does not support auto join settings.");
						}

						boolean newAutoJoin = args[3].equals("enable");

						((ChannelAutoJoin) channel).setAutoJoin(newAutoJoin);
						ChannelManager.saveChannel(channel);

						sender.sendMessage("Channel " + channelName + " set auto join to " + (newAutoJoin ? "enabled." : "disabled."));

						return 1;
					})
					.register();
			}

			arguments.clear();
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new MultiLiteralArgument("color"));
			arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
				ChannelManager.getChatableChannelNames(info.sender()).toArray(new String[0])
			));
			arguments.add(new MultiLiteralArgument("get"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelName = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelName);

					if (channel == null) {
						CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}

					@Nullable TextColor color = channel.color();
					sender.sendMessage(Component.text(channelName + " is " + MessagingUtils.colorToString(color), color));
					return 1;
				})
				.register();

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("color"));
				arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
					ChannelManager.getChatableChannelNames(info.sender()).toArray(new String[0])
				));
				arguments.add(new MultiLiteralArgument("clear"));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.channel.color")) {
							CommandUtils.fail(sender, "You do not have permission to change channel colors.");
						}

						String channelName = (String) args[2];
						Channel channel = ChannelManager.getChannel(channelName);

						if (channel == null) {
							CommandUtils.fail(sender, "No such channel " + channelName + ".");
						}

						if (!channel.mayManage(sender)) {
							CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
						}

						TextColor color = NetworkChatPlugin.messageColor(channel.getClassId());
						channel.color(sender, null);
						ChannelManager.saveChannel(channel);
						sender.sendMessage(Component.text(channelName + " reset to.", color));
						return 1;
					})
					.register();
			}

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("color"));
				arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
					ChannelManager.getChatableChannelNames(info.sender()).toArray(new String[0])
				));
				arguments.add(new MultiLiteralArgument("set"));
				arguments.add(new GreedyStringArgument("color").replaceSuggestions(info -> COLOR_SUGGESTIONS));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.channel.color")) {
							CommandUtils.fail(sender, "You do not have permission to change channel colors.");
						}

						String channelName = (String) args[2];
						Channel channel = ChannelManager.getChannel(channelName);

						if (channel == null) {
							CommandUtils.fail(sender, "No such channel " + channelName + ".");
						}

						if (!channel.mayManage(sender)) {
							CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
						}

						String colorString = (String) args[4];
						@Nullable TextColor color = MessagingUtils.colorFromString(colorString);
						if (color == null) {
							CommandUtils.fail(sender, "No such color " + colorString);
						}
						channel.color(sender, color);
						ChannelManager.saveChannel(channel);
						sender.sendMessage(Component.text(channelName + " set to " + MessagingUtils.colorToString(color), color));
						return 1;
					})
					.register();
			}

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("delete"));
				arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
					ChannelManager.getChatableChannelNames(info.sender()).toArray(new String[0])
				));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.delete.channel")) {
							CommandUtils.fail(sender, "You do not have permission to delete channels.");
						}

						return deleteChannel(sender, (String) args[2]);
					})
					.register();
			}

			arguments.clear();
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new MultiLiteralArgument("permission"));
			arguments.add(new StringArgument("channel name").replaceSuggestions(info ->
				ChannelManager.getManageableChannelNames(info.sender()).toArray(new String[0])
			));
			arguments.add(new MultiLiteralArgument("get"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return getChannelPermission(sender, (String) args[2]);
				})
				.register();

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("permission"));
				arguments.add(new StringArgument("channel name").replaceSuggestions(info ->
					ChannelManager.getManageableChannelNames(info.sender()).toArray(new String[0])
				));
				arguments.add(new MultiLiteralArgument("set"));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						return changeChannelPerms(sender, (String) args[2], null);
					})
					.register();
			}

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("permission"));
				arguments.add(new StringArgument("channel name").replaceSuggestions(info ->
					ChannelManager.getManageableChannelNames(info.sender()).toArray(new String[0])
				));
				arguments.add(new MultiLiteralArgument("set"));
				arguments.add(new GreedyStringArgument("New channel perms"));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						return changeChannelPerms(sender, (String) args[2], (String) args[4]);
					})
					.register();
			}

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("rename"));
				arguments.add(new StringArgument("old channel name").replaceSuggestions(info ->
					ChannelManager.getChannelNames().toArray(new String[0])
				));
				arguments.add(new StringArgument("new channel name"));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.rename")) {
							CommandUtils.fail(sender, "You do not have permission to rename channels.");
						}

						return renameChannel(sender, (String) args[2], (String) args[3]);
					})
					.register();
			}

			arguments.clear();
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
				ChannelManager.getListenableChannelNames(info.sender()).toArray(new String[0])
			));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagKeys()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelName = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}

					ChannelSettings settings = channel.channelSettings();
					return settings.commandFlag(sender, (String) args[3]);
				})
				.register();

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("settings"));
				arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
					ChannelManager.getListenableChannelNames(info.sender()).toArray(new String[0])
				));
				arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagKeys()));
				arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagValues()));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						String channelName = (String) args[2];
						Channel channel = ChannelManager.getChannel(channelName);
						if (channel == null) {
							CommandUtils.fail(sender, "No such channel " + channelName + ".");
						}

						if (!channel.mayManage(sender)) {
							CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
						}

						ChannelSettings settings = channel.channelSettings();
						int result = settings.commandFlag(sender, (String) args[3], (String) args[4]);
						ChannelManager.saveChannel(channel);
						return result;
					})
					.register();
			}

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("settings"));
				arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
					ChannelManager.getListenableChannelNames(info.sender()).toArray(new String[0])
				));
				arguments.add(new MultiLiteralArgument("sound"));
				arguments.add(new MultiLiteralArgument("add"));
				arguments.add(new SoundArgument("Notification sound"));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						String channelName = (String) args[2];
						Channel channel = ChannelManager.getChannel(channelName);
						if (channel == null) {
							CommandUtils.fail(sender, "No such channel " + channelName + ".");
						}

						if (!channel.mayManage(sender)) {
							CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
						}

						ChannelSettings settings = channel.channelSettings();
						settings.addSound((Sound) args[5], 1, 1);
						ChannelManager.saveChannel(channel);

						return 1;
					})
					.register();
			}

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.add(new FloatArgument("Volume", 0.0f, 1.0f));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						String channelName = (String) args[2];
						Channel channel = ChannelManager.getChannel(channelName);
						if (channel == null) {
							CommandUtils.fail(sender, "No such channel " + channelName + ".");
						}

						if (!channel.mayManage(sender)) {
							CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
						}

						ChannelSettings settings = channel.channelSettings();
						settings.addSound((Sound) args[5], (Float) args[6], 1);
						ChannelManager.saveChannel(channel);

						return 1;
					})
					.register();
			}

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.add(new FloatArgument("Pitch", 0.5f, 2.0f));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						String channelName = (String) args[2];
						Channel channel = ChannelManager.getChannel(channelName);
						if (channel == null) {
							CommandUtils.fail(sender, "No such channel " + channelName + ".");
						}

						if (!channel.mayManage(sender)) {
							CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
						}

						ChannelSettings settings = channel.channelSettings();
						settings.addSound((Sound) args[5], (Float) args[6], (Float) args[7]);
						ChannelManager.saveChannel(channel);

						return 1;
					})
					.register();
			}

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("settings"));
				arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
					ChannelManager.getListenableChannelNames(info.sender()).toArray(new String[0])
				));
				arguments.add(new MultiLiteralArgument("sound"));
				arguments.add(new MultiLiteralArgument("clear"));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						String channelName = (String) args[2];
						Channel channel = ChannelManager.getChannel(channelName);
						if (channel == null) {
							CommandUtils.fail(sender, "No such channel " + channelName + ".");
						}

						if (!channel.mayManage(sender)) {
							CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
						}

						ChannelSettings settings = channel.channelSettings();
						settings.clearSound();
						ChannelManager.saveChannel(channel);

						return 1;
					})
					.register();
			}

			arguments.clear();
			arguments.add(new MultiLiteralArgument("gui"));
			arguments.add(new MultiLiteralArgument("message"));
			arguments.add(new GreedyStringArgument("message ID"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.gui.message")) {
						CommandUtils.fail(sender, "You do not have permission to run this command.");
					}

					messageGui(baseCommand, sender, (String) args[2]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("join"));
			arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
				ChannelManager.getListenableChannelNames(info.sender()).toArray(new String[0])
			));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return joinChannel(sender, (String) args[1]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("leave"));
			arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
				ChannelManager.getListenableChannelNames(info.sender()).toArray(new String[0])
			));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return leaveChannel(sender, (String) args[1]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("listplayers"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					RemotePlayerManager.showOnlinePlayers(CommandUtils.getCallee(sender));
					return 1;
				})
				.register();

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("message"));
				arguments.add(new MultiLiteralArgument("delete"));
				arguments.add(new StringArgument("Message ID"));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.delete.message")) {
							CommandUtils.fail(sender, "You do not have permission to run this command.");
						}

						return deleteMessage(sender, (String) args[2]);
					})
					.register();
			}

			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("profilemessage"));
			arguments.add(new MultiLiteralArgument("get"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String profileMessage = "";
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						CommandUtils.fail(sender, "This command can only be run as a player.");
					} else {
						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							CommandUtils.fail(sender, callee.getName() + " has no chat state and must relog.");
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
				.executes((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						CommandUtils.fail(sender, "This command can only be run as a player.");
					} else {
						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							CommandUtils.fail(sender, callee.getName() + " has no chat state and must relog.");
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
				.executes((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.setprofilemessage")) {
						CommandUtils.fail(sender, "You do not have permission to run this command.");
					}

					String profileMessage = (String) args[3];
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						CommandUtils.fail(sender, "This command can only be run as a player.");
					} else {
						if (!CommandUtils.checkSudoCommand(sender)) {
							CommandUtils.fail(sender, "You may not change other player's profile messages on this shard.");
						}

						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							CommandUtils.fail(sender, callee.getName() + " has no chat state and must relog.");
						}
						target.sendMessage(Component.text("Your profile message has been set to:", NamedTextColor.GRAY));
						state.profileMessage(profileMessage);
						target.sendMessage(state.profileMessageComponent()
							.clickEvent(ClickEvent.suggestCommand("/" + baseCommand + " profilemessage set " + profileMessage)));
					}
					return profileMessage.length();
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("refresh"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						CommandUtils.fail(sender, "This command can only be run as a player.");
					} else {
						RemotePlayerManager.refreshLocalPlayer(target);
					}
					return 1;
				})
				.register();

			arguments.add(new EntitySelectorArgument("Players", EntitySelector.MANY_PLAYERS));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					@SuppressWarnings("unchecked")
					Collection<Player> players = (Collection<Player>) args[2];

					for (Player player : players) {
						RemotePlayerManager.refreshLocalPlayer(player);
					}
					return 1;
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("resetnick"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						CommandUtils.fail(sender, "This command can only be run as a player.");
					} else {
						target.displayName(null);
						target.playerListName(null);
					}
					return 1;
				})
				.register();

			for (String channelType : DefaultChannels.CHANNEL_TYPES) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("player"));
				arguments.add(new MultiLiteralArgument("setdefaultchannel"));
				arguments.add(new MultiLiteralArgument(channelType));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						CommandSender callee = CommandUtils.getCallee(sender);
						if (!(callee instanceof Player target)) {
							CommandUtils.fail(sender, "This command can only be run as a player.");
							return 0;
						} else {
							if (!CommandUtils.checkSudoCommand(sender)) {
								CommandUtils.fail(sender, "You may not change other players' default channel on this shard.");
							}

							PlayerState state = PlayerStateManager.getPlayerState(target);
							if (state == null) {
								CommandUtils.fail(sender, callee.getName() + " has no chat state and must relog.");
							}

							return state.defaultChannels().command(sender, channelType);
						}
					})
					.register();

				arguments.clear();
				arguments.add(new MultiLiteralArgument("player"));
				arguments.add(new MultiLiteralArgument("setdefaultchannel"));
				arguments.add(new MultiLiteralArgument(channelType));
				if (channelType.equals("default")) {
					arguments.add(new StringArgument("channel name").replaceSuggestions(info ->
						ChannelManager.getChannelNames().toArray(new String[0])
					));
				} else {
					arguments.add(new StringArgument("channel name").replaceSuggestions(info ->
						ChannelManager.getChannelNames(channelType).toArray(new String[0])
					));
				}
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						CommandSender callee = CommandUtils.getCallee(sender);
						if (!(callee instanceof Player target)) {
							CommandUtils.fail(sender, "This command can only be run as a player.");
							return 0;
						} else {
							if (!CommandUtils.checkSudoCommand(sender)) {
								CommandUtils.fail(sender, "You may not change other players' default channel on this shard.");
							}

							PlayerState state = PlayerStateManager.getPlayerState(target);
							if (state == null) {
								CommandUtils.fail(sender, callee.getName() + " has no chat state and must relog.");
							}
							return state.defaultChannels().command(sender, channelType, (String) args[3]);
						}
					})
					.register();
			}

			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
				ChannelManager.getListenableChannelNames(info.sender()).toArray(new String[0])
			));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagKeys()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						CommandUtils.fail(sender, "This command can only be run as a player.");
						return 0;
					} else {
						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							CommandUtils.fail(sender, callee.getName() + " has no chat state and must relog.");
						}

						String channelName = (String) args[3];
						Channel channel = ChannelManager.getChannel(channelName);
						if (channel == null) {
							CommandUtils.fail(sender, "No such channel " + channelName + ".");
						}

						ChannelSettings settings = state.channelSettings(channel);
						return settings.commandFlag(sender, (String) args[4]);
					}
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
				ChannelManager.getListenableChannelNames(info.sender()).toArray(new String[0])
			));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagKeys()));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagValues()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						CommandUtils.fail(sender, "This command can only be run as a player.");
						return 0;
					} else {
						if (!CommandUtils.checkSudoCommand(sender)) {
							CommandUtils.fail(sender, "You may not edit other player's settings on this shard.");
						}

						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							CommandUtils.fail(sender, callee.getName() + " has no chat state and must relog.");
						}

						String channelName = (String) args[3];
						Channel channel = ChannelManager.getChannel(channelName);
						if (channel == null) {
							CommandUtils.fail(sender, "No such channel " + channelName + ".");
						}

						ChannelSettings settings = state.channelSettings(channel);
						return settings.commandFlag(sender, (String) args[4], (String) args[5]);
					}
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new MultiLiteralArgument("default"));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagKeys()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						CommandUtils.fail(sender, "This command can only be run as a player.");
						return 0;
					} else {
						if (!CommandUtils.checkSudoCommand(sender)) {
							CommandUtils.fail(sender, "You may not edit other player's settings on this shard.");
						}

						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							CommandUtils.fail(sender, callee.getName() + " has no chat state and must relog.");
						}
						ChannelSettings settings = state.channelSettings();
						return settings.commandFlag(sender, (String) args[3]);
					}
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new MultiLiteralArgument("default"));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagKeys()));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagValues()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						CommandUtils.fail(sender, "This command can only be run as a player.");
						return 0;
					} else {
						if (!CommandUtils.checkSudoCommand(sender)) {
							CommandUtils.fail(sender, "You may not edit other player's settings on this shard.");
						}

						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							CommandUtils.fail(sender, callee.getName() + " has no chat state and must relog.");
						}
						ChannelSettings settings = state.channelSettings();
						return settings.commandFlag(sender, (String) args[3], (String) args[4]);
					}
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("say"));
			arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
				ChannelManager.getChatableChannelNames(info.sender()).toArray(new String[0])
			));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return setActiveChannel(sender, (String) args[1]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("say"));
			arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
				ChannelManager.getChatableChannelNames(info.sender()).toArray(new String[0])
			));
			arguments.add(new GreedyStringArgument("message"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return sendMessage(sender, (String) args[1], (String) args[2]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("server"));
			arguments.add(new MultiLiteralArgument("color"));
			arguments.add(new MultiLiteralArgument(ChannelAnnouncement.CHANNEL_CLASS_ID,
				ChannelGlobal.CHANNEL_CLASS_ID,
				ChannelLocal.CHANNEL_CLASS_ID,
				ChannelParty.CHANNEL_CLASS_ID,
				ChannelTeam.CHANNEL_CLASS_ID,
				ChannelWhisper.CHANNEL_CLASS_ID));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String id = (String) args[2];
					TextColor color = NetworkChatPlugin.messageColor(id);
					sender.sendMessage(Component.text(id + " is " + MessagingUtils.colorToString(color), color));
					return 1;
				})
				.register();

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("server"));
				arguments.add(new MultiLiteralArgument("color"));
				arguments.add(new MultiLiteralArgument(ChannelAnnouncement.CHANNEL_CLASS_ID,
					ChannelGlobal.CHANNEL_CLASS_ID,
					ChannelLocal.CHANNEL_CLASS_ID,
					ChannelParty.CHANNEL_CLASS_ID,
					ChannelTeam.CHANNEL_CLASS_ID,
					ChannelWhisper.CHANNEL_CLASS_ID));
				arguments.add(new GreedyStringArgument("color").replaceSuggestions(info -> COLOR_SUGGESTIONS));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.format.default")) {
							CommandUtils.fail(sender, "You do not have permission to change channel colors server-wide.");
						}

						String id = (String) args[2];
						String colorString = (String) args[3];
						TextColor color = MessagingUtils.colorFromString(colorString);
						if (color == null) {
							CommandUtils.fail(sender, "No such color " + colorString);
						}
						NetworkChatPlugin.messageColor(id, color);
						sender.sendMessage(Component.text(id + " set to " + MessagingUtils.colorToString(color), color));
						return 1;
					})
					.register();
			}

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
				ChannelWhisper.CHANNEL_CLASS_ID));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String id = (String) args[2];
					TextColor color = NetworkChatPlugin.messageColor(id);
					String format = NetworkChatPlugin.messageFormat(id).replace("\n", "\\n");

					Component senderComponent = MessagingUtils.senderComponent(sender);
					sender.sendMessage(Component.text(id + " is " + format, color)
						.clickEvent(ClickEvent.suggestCommand("/" + baseCommand + " format default " + id + " " + format)));
					if (id.equals("player")) {
						sender.sendMessage(Component.text("Example: ").append(senderComponent));
					} else {
						String prefix = format.replace("<channel_color>", MessagingUtils.colorToMiniMessage(color));
						Component fullMessage = Component.empty()
							.append(MessagingUtils.CHANNEL_HEADER_FMT_MINIMESSAGE.deserialize(prefix, TemplateResolver.templates(Template.template("channel_name", "ExampleChannel"),
								Template.template("sender", senderComponent),
								Template.template("receiver", senderComponent))))
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
					ChannelWhisper.CHANNEL_CLASS_ID));
				arguments.add(new GreedyStringArgument("format"));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.format.default")) {
							CommandUtils.fail(sender, "You do not have permission to change message formatting.");
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
								.append(MessagingUtils.CHANNEL_HEADER_FMT_MINIMESSAGE.deserialize(prefix, TemplateResolver.templates(Template.template("channel_name", "ExampleChannel"),
									Template.template("sender", senderComponent),
									Template.template("receiver", senderComponent))))
								.append(Component.empty().color(color).append(Component.text("Test message")));

							sender.sendMessage(Component.text("Example message:", color));
							sender.sendMessage(fullMessage);
						}
						return 1;
					})
					.register();
			}

			arguments.clear();
			arguments.add(new MultiLiteralArgument("server"));
			arguments.add(new MultiLiteralArgument("messagevisibility"));
			arguments.add(new MultiLiteralArgument("visibility"));
			arguments.add(new MultiLiteralArgument(MessageVisibility.getVisibilityKeys()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
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
					.executes((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.visibility.default")) {
							CommandUtils.fail(sender, "You do not have permission to change server-wide message visibility.");
						}

						int result = PlayerStateManager.getDefaultMessageVisibility().commandVisibility(sender, (String) args[2], (String) args[3]);
						PlayerStateManager.saveSettings();
						return result;
					})
					.register();
			}

			for (String channelType : DefaultChannels.CHANNEL_TYPES) {
				if (NetworkChatProperties.getChatCommandModifyEnabled()) {
					arguments.clear();
					arguments.add(new MultiLiteralArgument("server"));
					arguments.add(new MultiLiteralArgument("setdefaultchannel"));
					arguments.add(new MultiLiteralArgument(channelType));
					new CommandAPICommand(baseCommand)
						.withArguments(arguments)
						.executes((sender, args) -> {
							if (!CommandUtils.hasPermission(sender, "networkchat.setdefaultchannel")) {
								CommandUtils.fail(sender, "You do not have permission to change server-wide default channels.");
							}

							return ChannelManager.getDefaultChannels().command(sender, channelType, true);
						})
						.register();

					arguments.clear();
					arguments.add(new MultiLiteralArgument("server"));
					arguments.add(new MultiLiteralArgument("setdefaultchannel"));
					arguments.add(new MultiLiteralArgument(channelType));
					if (channelType.equals("default")) {
						arguments.add(new StringArgument("channel name").replaceSuggestions(info ->
							ChannelManager.getChannelNames().toArray(new String[0])
						));
					} else {
						arguments.add(new StringArgument("channel name").replaceSuggestions(info ->
							ChannelManager.getChannelNames(channelType).toArray(new String[0])
						));
					}
					new CommandAPICommand(baseCommand)
						.withArguments(arguments)
						.executes((sender, args) -> {
							if (!CommandUtils.hasPermission(sender, "networkchat.setdefaultchannel")) {
								CommandUtils.fail(sender, "You do not have permission to change server-wide default channels.");
							}

							int result = ChannelManager.getDefaultChannels().command(sender, channelType, (String) args[3]);
							ChannelManager.saveDefaultChannels();
							return result;
						})
						.register();
				}
			}

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
		}

		arguments.clear();
		arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
			ChannelManager.getListenableChannelNames(info.sender()).toArray(new String[0])
		));
		new CommandAPICommand("join")
			.withArguments(arguments)
			.executes((sender, args) -> {
				return joinChannel(sender, (String) args[0]);
			})
			.register();

		arguments.clear();
		arguments.add(new StringArgument("Channel Name").replaceSuggestions(info ->
			ChannelManager.getListenableChannelNames(info.sender()).toArray(new String[0])
		));
		new CommandAPICommand("leave")
			.withArguments(arguments)
			.executes((sender, args) -> {
				return leaveChannel(sender, (String) args[0]);
			})
			.register();

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
			if (channelType.equals(DefaultChannels.DEFAULT_CHANNEL)) {
				continue;
			}

			new CommandAPICommand(channelType)
				.executes((sender, args) -> {
					return setActiveToDefault(sender, channelType);
				})
				.register();

			arguments.clear();
			arguments.add(new GreedyStringArgument("message"));
			new CommandAPICommand(channelType)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return sendMessageInDefault(sender, channelType, (String) args[0]);
				})
				.register();

			String shortcut = channelType.substring(0, 1);
			if (!usedShortcuts.add(shortcut)) {
				continue;
			}

			new CommandAPICommand(shortcut)
				.executes((sender, args) -> {
					return setActiveToDefault(sender, channelType);
				})
				.register();

			arguments.clear();
			arguments.add(new GreedyStringArgument("message"));
			new CommandAPICommand(shortcut)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return sendMessageInDefault(sender, channelType, (String) args[0]);
				})
				.register();
		}
	}

	private static void registerHelpCommandNode(final @Nullable ZipFile zip, final List<String> argStrings, final HelpTreeNode node) {
		List<Argument> arguments = new ArrayList<>();
		arguments.add(new MultiLiteralArgument("help"));
		for (String arg : argStrings) {
			arguments.add(new MultiLiteralArgument(arg));
		}
		for (final String baseCommand : COMMANDS) {
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					// Title
					StringBuilder titleBuilder = new StringBuilder("/").append(baseCommand).append(" help");
					for (String arg : argStrings) {
						titleBuilder.append(' ').append(arg);
					}
					titleBuilder.append(':');
					sender.sendMessage(Component.text(titleBuilder.toString(), NamedTextColor.GOLD, TextDecoration.BOLD));

					// Node's help info (if any)
					@Nullable BufferedReader helpFile = null;
					if (node.mExternalPath != null) {
						try {
							helpFile = new BufferedReader(new InputStreamReader(new FileInputStream(node.mExternalPath)));
						} catch (IOException ex) {
							sender.sendMessage(Component.text("Unable to load server-specific help file. Attempting to load default help file...", NamedTextColor.RED));
						}
					}
					if (helpFile == null && node.mResourcePath != null && zip != null) {
						ZipEntry zipEntry = zip.getEntry(node.mResourcePath);
						try {
							helpFile = new BufferedReader(new InputStreamReader(zip.getInputStream(zipEntry)));
						} catch (IOException ex) {
							CommandUtils.fail(sender, "Unable to load help file. This shard may need to restart.");
							return 0;
						}
					}
					if (helpFile != null) {
						try {
							for (@Nullable String line; (line = helpFile.readLine()) != null; ) {
								sender.sendMessage(Component.empty()
									.color(NamedTextColor.GREEN)
									.append(MessagingUtils.SENDER_FMT_MINIMESSAGE.deserialize(line)));
							}
						} catch (IOException ex) {
							CommandUtils.fail(sender, "Failed to read all lines from help file. This shard may need to restart.");
						}
					}

					// Back link (if applicable)
					if (!argStrings.isEmpty()) {
						StringBuilder parentCmdBuilder = new StringBuilder("/")
							.append(baseCommand)
							.append(" help");
						for (String arg : argStrings.subList(0, argStrings.size() - 1)) {
							parentCmdBuilder.append(' ').append(arg);
						}
						sender.sendMessage(Component.text("[Back]", NamedTextColor.LIGHT_PURPLE)
							.clickEvent(ClickEvent.runCommand(parentCmdBuilder.toString())));
					}

					// Child links
					for (String childArg : node.keySet()) {
						StringBuilder childCmdBuilder = new StringBuilder("/")
							.append(baseCommand)
							.append(" help");
						for (String arg : argStrings) {
							childCmdBuilder.append(' ').append(arg);
						}
						childCmdBuilder.append(' ').append(childArg);
						sender.sendMessage(Component.text("[" + childArg + "]", NamedTextColor.LIGHT_PURPLE)
							.clickEvent(ClickEvent.runCommand(childCmdBuilder.toString())));
					}
					return 1;
				})
				.register();
		}

		for (Map.Entry<String, HelpTreeNode> entry : node.entrySet()) {
			String arg = entry.getKey();
			HelpTreeNode child = entry.getValue();
			List<String> nextArgStrings = new ArrayList<>(argStrings);
			nextArgStrings.add(arg);
			registerHelpCommandNode(zip, nextArgStrings, child);
		}
	}

	private static int getChannelPermission(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		Channel channel = ChannelManager.getChannel(channelName);
		if (channel == null) {
			CommandUtils.fail(sender, "No such channel " + channelName + ".");
		}

		if (!channel.mayManage(sender)) {
			CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
		}

		if (!(channel instanceof ChannelPermissionNode)) {
			CommandUtils.fail(sender, "This channel does not support permission");
		}

		String perms = ((ChannelPermissionNode) channel).getChannelPermission();

		if (perms == null || perms.isEmpty()) {
			sender.sendMessage("This channel has no permission set");
		} else {
			sender.sendMessage("Permission: " + perms + " for channel " + channelName);
		}

		return 1;
	}

	private static int changeChannelPerms(CommandSender sender, String channelName, String newPerms) throws WrapperCommandSyntaxException {
		Channel channel = ChannelManager.getChannel(channelName);
		if (channel == null) {
			CommandUtils.fail(sender, "No such channel " + channelName + ".");
		}

		if (!channel.mayManage(sender)) {
			CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
		}

		if (!(channel instanceof ChannelPermissionNode)) {
			CommandUtils.fail(sender, "This channel does not support permission");
		}

		((ChannelPermissionNode) channel).setChannelPermission(newPerms);

		return 1;
	}

	private static int renameChannel(CommandSender sender, String oldChannelName, String newChannelName) throws WrapperCommandSyntaxException {
		// May call CommandUtils.fail(sender, )
		ChannelManager.renameChannel(oldChannelName, newChannelName);
		sender.sendMessage(Component.text("Channel " + oldChannelName + " renamed to " + newChannelName + ".", NamedTextColor.GRAY));
		return 1;
	}

	private static int deleteChannel(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		// May call CommandUtils.fail(sender, )
		ChannelManager.deleteChannel(channelName);
		sender.sendMessage(Component.text("Channel " + channelName + " deleted.", NamedTextColor.GRAY));
		return 1;
	}

	private static int joinChannel(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player target)) {
			CommandUtils.fail(sender, "This command can only be run as a player.");
			return 0;
		} else {
			if (!CommandUtils.checkSudoCommand(sender)) {
				CommandUtils.fail(sender, "You may not make other players join channels on this shard.");
			}

			PlayerState playerState = PlayerStateManager.getPlayerState(target);
			if (playerState == null) {
				CommandUtils.fail(sender, callee.getName() + " has no chat state and must relog.");
			}

			Channel channel = ChannelManager.getChannel(channelName);
			if (channel == null) {
				CommandUtils.fail(sender, "No such channel " + channelName + ".");
			}

			playerState.setActiveChannel(channel);
			target.sendMessage(Component.text("Joined channel " + channelName + ".", NamedTextColor.GRAY));
			return 1;
		}
	}

	private static int leaveChannel(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player target)) {
			CommandUtils.fail(sender, "This command can only be run as a player.");
			return 0;
		} else {
			if (!CommandUtils.checkSudoCommand(sender)) {
				CommandUtils.fail(sender, "You may not make other players leave channels on this shard.");
			}

			PlayerState playerState = PlayerStateManager.getPlayerState(target);
			if (playerState == null) {
				CommandUtils.fail(sender, callee.getName() + " has no chat state and must relog.");
			}

			Channel channel = ChannelManager.getChannel(channelName);
			if (channel == null) {
				CommandUtils.fail(sender, "No such channel " + channelName + ".");
			}

			playerState.leaveChannel(channel);
			target.sendMessage(Component.text("Left channel " + channelName + ".", NamedTextColor.GRAY));
			return 1;
		}
	}

	private static int setActiveChannel(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player player)) {
			CommandUtils.fail(sender, "Only players have an active channel.");
			return 0;
		} else {
			if (!CommandUtils.checkSudoCommand(sender)) {
				CommandUtils.fail(sender, "You may not change other players' active channel on this shard.");
			}

			PlayerState playerState = PlayerStateManager.getPlayerState(player);
			if (playerState == null) {
				CommandUtils.fail(sender, "You have no chat state. Please report this bug and reconnect to the server.");
			}

			Channel channel = ChannelManager.getChannel(channelName);
			if (channel == null) {
				CommandUtils.fail(sender, "No such channel " + channelName + ".");
			}

			playerState.setActiveChannel(channel);
			player.sendMessage(Component.text("You are now typing in " + channelName + ".", NamedTextColor.GRAY));
			return 1;
		}
	}

	private static int setActiveToDefault(CommandSender sender, String channelType) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player player)) {
			CommandUtils.fail(sender, "Only players have an active channel.");
			return 0;
		} else {
			if (!CommandUtils.checkSudoCommand(sender)) {
				CommandUtils.fail(sender, "You may not change other players' active channel on this shard.");
			}

			PlayerState playerState = PlayerStateManager.getPlayerState(player);
			if (playerState == null) {
				CommandUtils.fail(sender, "You have no chat state. Please report this bug and reconnect to the server.");
			}

			Channel channel = playerState.getDefaultChannel(channelType);
			if (channel == null) {
				CommandUtils.fail(sender, "No default for " + channelType + " channel type.");
			}

			playerState.setActiveChannel(channel);
			player.sendMessage(Component.text("You are now typing in " + channel.getName() + ".", NamedTextColor.GRAY));
			return 1;
		}
	}

	private static int sendMessage(CommandSender sender, String channelName, String message) throws WrapperCommandSyntaxException {
		CommandSender caller = CommandUtils.getCaller(sender);
		CommandSender callee = CommandUtils.getCallee(sender);
		if (NetworkChatProperties.getChatRequiresPlayer()) {
			if (!(caller instanceof Player)) {
				CommandUtils.fail(sender, "Only players may chat on this shard.");
				return 0;
			}
		}
		if (callee instanceof Player && callee != caller) {
			CommandUtils.fail(sender, "Hey! It's not nice to put words in people's mouths! Where are your manners?");
			return 0;
		}

		Channel channel = ChannelManager.getChannel(channelName);
		if (channel == null) {
			CommandUtils.fail(sender, "No such channel " + channelName + ".");
			return 0;
		}

		channel.sendMessage(sender, message);
		return 1;
	}

	private static int sendMessageInDefault(CommandSender sender, String channelType, String message) throws WrapperCommandSyntaxException {
		CommandSender caller = CommandUtils.getCaller(sender);
		CommandSender callee = CommandUtils.getCallee(sender);
		if (NetworkChatProperties.getChatRequiresPlayer()) {
			if (!(caller instanceof Player)) {
				CommandUtils.fail(sender, "Only players may chat on this shard.");
				return 0;
			}
		}
		if (callee instanceof Player && callee != caller) {
			CommandUtils.fail(sender, "Hey! It's not nice to put words in people's mouths! Where are your manners?");
		}
		Channel channel;
		if (sender instanceof Player) {
			channel = PlayerStateManager.getPlayerState((Player) sender).getDefaultChannel(channelType);
		} else {
			channel = ChannelManager.getDefaultChannel(channelType);
		}
		if (channel == null) {
			CommandUtils.fail(sender, "No default for " + channelType + " channel type.");
		}

		channel.sendMessage(sender, message);
		return 1;
	}

	private static int pause(CommandSender sender) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player target)) {
			CommandUtils.fail(sender, "This command can only be run as a player.");
			return 0;
		} else {
			if (!CommandUtils.checkSudoCommand(sender)) {
				CommandUtils.fail(sender, "You may not pause chat for other players on this shard.");
			}

			PlayerState playerState = PlayerStateManager.getPlayerState(target);
			if (playerState == null) {
				CommandUtils.fail(sender, callee.getName() + " has no chat state and must relog.");
			}

			playerState.pauseChat();
			target.sendMessage(Component.text("Chat paused.", NamedTextColor.GRAY));
			return 1;
		}
	}

	private static int unpause(CommandSender sender) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player target)) {
			CommandUtils.fail(sender, "This command can only be run as a player.");
			return 0;
		} else {
			if (!CommandUtils.checkSudoCommand(sender)) {
				CommandUtils.fail(sender, "You may not unpause chat for other players on this shard.");
			}

			PlayerState playerState = PlayerStateManager.getPlayerState(target);
			if (playerState == null) {
				CommandUtils.fail(sender, callee.getName() + " has no chat state and must relog.");
			}

			target.sendMessage(Component.text("Unpausing chat.", NamedTextColor.GRAY));
			playerState.unpauseChat();
			return 1;
		}
	}

	private static int togglePause(CommandSender sender) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player target)) {
			CommandUtils.fail(sender, "This command can only be run as a player.");
			return 0;
		} else {
			if (!CommandUtils.checkSudoCommand(sender)) {
				CommandUtils.fail(sender, "You may not pause chat for other players on this shard.");
			}

			PlayerState playerState = PlayerStateManager.getPlayerState(target);
			if (playerState == null) {
				CommandUtils.fail(sender, callee.getName() + " has no chat state and must relog.");
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

	private static int deleteMessage(CommandSender sender, String messageIdStr) throws WrapperCommandSyntaxException {
		UUID messageId;
		try {
			messageId = UUID.fromString(messageIdStr);
		} catch (Exception e) {
			CommandUtils.fail(sender, "Invalid message ID. Click a channel name to open the message GUI.");
			return 0;
		}

		MessageManager.deleteMessage(messageId);
		return 1;
	}

	private static void messageGui(String baseCommand, CommandSender sender, String messageIdStr) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player target)) {
			CommandUtils.fail(sender, "This command can only be run as a player.");
		} else {
			PlayerState playerState = PlayerStateManager.getPlayerState(target);
			if (playerState == null) {
				CommandUtils.fail(sender, callee.getName() + " has no chat state and must relog.");
			}

			UUID messageId;
			try {
				messageId = UUID.fromString(messageIdStr);
			} catch (Exception e) {
				CommandUtils.fail(sender, "Invalid message ID. Click a channel name to open the message GUI.");
				return;
			}

			Message message = MessageManager.getMessage(messageId);
			if (message == null) {
				CommandUtils.fail(sender, "That message is no longer available on this shard. Pause chat and avoid switching shards to keep messages loaded.");
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

			target.sendMessage(gui);
		}
	}
}
