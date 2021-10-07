package com.playmonumenta.networkchat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.BooleanArgument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import me.clip.placeholderapi.PlaceholderAPI;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.Template;

// A channel for server announcements
public class ChannelAnnouncement extends Channel implements ChannelPermissionNode {
	public static final String CHANNEL_CLASS_ID = "announcement";

	private UUID mId;
	private Instant mLastUpdate;
	private String mName;
	private ChannelSettings mDefaultSettings;
	private ChannelPerms mDefaultPerms;
	private Map<UUID, ChannelPerms> mPlayerPerms;
	private boolean mAutoJoin = true;
	private String mChannelPermission = null;

	private ChannelAnnouncement(UUID channelId, Instant lastUpdate, String name) {
		mId = channelId;
		mLastUpdate = lastUpdate;
		mName = name;

		mDefaultSettings = new ChannelSettings();
		mDefaultSettings.messagesPlaySound(true);

		mDefaultPerms = new ChannelPerms();
		mPlayerPerms = new HashMap<>();
	}

	public ChannelAnnouncement(String name) {
		mLastUpdate = Instant.now();
		mId = UUID.randomUUID();
		mName = name;

		mDefaultSettings = new ChannelSettings();
		mDefaultPerms = new ChannelPerms();
		mPlayerPerms = new HashMap<>();
	}

	protected static Channel fromJsonInternal(JsonObject channelJson) throws Exception {
		String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();
		if (channelClassId == null || !channelClassId.equals(CHANNEL_CLASS_ID)) {
			throw new Exception("Cannot create ChannelAnnouncement from channel ID " + channelClassId);
		}
		String uuidString = channelJson.getAsJsonPrimitive("uuid").getAsString();
		UUID channelId = UUID.fromString(uuidString);
		Instant lastUpdate = Instant.now();
		if (channelJson.get("lastUpdate") != null) {
			lastUpdate = Instant.ofEpochMilli(channelJson.get("lastUpdate").getAsLong());
		}
		String name = channelJson.getAsJsonPrimitive("name").getAsString();

		ChannelAnnouncement channel = new ChannelAnnouncement(channelId, lastUpdate, name);

		JsonObject defaultSettingsJson = channelJson.getAsJsonObject("defaultSettings");
		if (defaultSettingsJson != null) {
			channel.mDefaultSettings = ChannelSettings.fromJson(defaultSettingsJson);
		}

		JsonObject defaultPermsJson = channelJson.getAsJsonObject("defaultPerms");
		if (defaultPermsJson != null) {
			channel.mDefaultPerms = ChannelPerms.fromJson(defaultPermsJson);
		}

		JsonObject allPlayerPermsJson = channelJson.getAsJsonObject("playerPerms");
		if (defaultPermsJson != null) {
			for (Map.Entry<String, JsonElement> playerPermEntry : allPlayerPermsJson.entrySet()) {
				UUID playerId;
				JsonObject playerPermsJson;
				try {
					playerId = UUID.fromString(playerPermEntry.getKey());
					playerPermsJson = playerPermEntry.getValue().getAsJsonObject();
				} catch (Exception e) {
					NetworkChatPlugin.getInstance().getLogger().warning("Catch exeption during converting json to channel Announcement reason: " + e.getMessage());
					continue;
				}
				ChannelPerms playerPerms = ChannelPerms.fromJson(playerPermsJson);
				channel.mPlayerPerms.put(playerId, playerPerms);
			}
		}

		JsonPrimitive autoJoinJson = channelJson.getAsJsonPrimitive("autoJoin");
		if (autoJoinJson != null && autoJoinJson.isBoolean()) {
			channel.mAutoJoin = autoJoinJson.getAsBoolean();
		}

		JsonPrimitive channelPermissionJson = channelJson.getAsJsonPrimitive("channelPermission");
		if (channelPermissionJson != null && channelPermissionJson.isString()) {
			channel.mChannelPermission = channelPermissionJson.getAsString();
		}

		return channel;
	}

	public JsonObject toJson() {
		JsonObject allPlayerPermsJson = new JsonObject();
		for (Map.Entry<UUID, ChannelPerms> playerPermEntry : mPlayerPerms.entrySet()) {
			UUID channelId = playerPermEntry.getKey();
			ChannelPerms channelPerms = playerPermEntry.getValue();
			if (!channelPerms.isDefault()) {
				allPlayerPermsJson.add(channelId.toString(), channelPerms.toJson());
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("type", CHANNEL_CLASS_ID);
		result.addProperty("uuid", mId.toString());
		result.addProperty("lastUpdate", mLastUpdate.toEpochMilli());
		result.addProperty("name", mName);
		result.addProperty("autoJoin", mAutoJoin);
		if (mChannelPermission != null) {
			result.addProperty("channelPermission", mChannelPermission);
		}
		result.add("defaultSettings", mDefaultSettings.toJson());
		result.add("defaultPerms", mDefaultPerms.toJson());
		result.add("playerPerms", allPlayerPermsJson);
		return result;
	}

	public static void registerNewChannelCommands(String[] baseCommands, List<Argument> prefixArguments) {
		List<Argument> arguments;

		for (String baseCommand : baseCommands) {
			arguments = new ArrayList<>(prefixArguments);
			// last element of prefixArguments is channel ID
			arguments.add(new MultiLiteralArgument(CHANNEL_CLASS_ID));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelName = (String)args[prefixArguments.size() - 1];
					ChannelAnnouncement newChannel = null;
					if (!sender.hasPermission("networkchat.new.announcement")) {
						CommandUtils.fail(sender, "You do not have permission to make new announcement channels.");
					}

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelAnnouncement(channelName);
					} catch (Exception e) {
						CommandUtils.fail(sender, "Could not create new channel " + channelName + ": Could not connect to RabbitMQ.");
					}
					// Throws an exception if the channel already exists, failing the command.
					ChannelManager.registerNewChannel(sender, newChannel);
				})
				.register();

			arguments.add(new BooleanArgument("Auto Join"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelName = (String)args[prefixArguments.size() - 1];
					ChannelAnnouncement newChannel = null;
					if (!sender.hasPermission("networkchat.new.announcement")) {
						CommandUtils.fail(sender, "You do not have permission to make new announcement channels.");
					}

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelAnnouncement(channelName);
						newChannel.mAutoJoin = (boolean)args[prefixArguments.size() + 1];
					} catch (Exception e) {
						CommandUtils.fail(sender, "Could not create new channel " + channelName + ": Could not connect to RabbitMQ.");
					}
					// Throws an exception if the channel already exists, failing the command.
					ChannelManager.registerNewChannel(sender, newChannel);
				})
				.register();

			arguments.add(new GreedyStringArgument("Channel Permission"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelName = (String)args[prefixArguments.size() - 1];
					ChannelAnnouncement newChannel = null;
					if (!sender.hasPermission("networkchat.new.announcement")) {
						CommandUtils.fail(sender, "You do not have permission to make new announcement channels.");
					}

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelAnnouncement(channelName);
						newChannel.mAutoJoin = (boolean)args[prefixArguments.size() + 1];
						newChannel.mChannelPermission = (String)args[prefixArguments.size() + 2];
					} catch (Exception e) {
						CommandUtils.fail(sender, "Could not create new channel " + channelName + ": Could not connect to RabbitMQ.");
					}
					// Throws an exception if the channel already exists, failing the command.
					ChannelManager.registerNewChannel(sender, newChannel);
				})
				.register();
		}
	}

	public String getClassId() {
		return CHANNEL_CLASS_ID;
	}

	public UUID getUniqueId() {
		return mId;
	}

	public void markModified() {
		mLastUpdate = Instant.now();
	}

	public Instant lastModified() {
		return mLastUpdate;
	}

	protected void setName(String name) throws WrapperCommandSyntaxException {
		mName = name;
	}

	public String getName() {
		return mName;
	}

	public ChannelSettings channelSettings() {
		return mDefaultSettings;
	}

	public ChannelSettings playerSettings(Player player) {
		if (player == null) {
			return null;
		}
		PlayerState playerState = PlayerStateManager.getPlayerState(player);
		if (playerState != null) {
			return playerState.channelSettings(this);
		}
		return null;
	}

	public ChannelPerms channelPerms() {
		return mDefaultPerms;
	}

	public ChannelPerms playerPerms(UUID playerId) {
		if (playerId == null) {
			return null;
		}
		ChannelPerms perms = mPlayerPerms.get(playerId);
		if (perms == null) {
			perms = new ChannelPerms();
			mPlayerPerms.put(playerId, perms);
		}
		return perms;
	}

	public void clearPlayerPerms(UUID playerId) {
		if (playerId == null) {
			return;
		}
		mPlayerPerms.remove(playerId);
	}

	public boolean shouldAutoJoin(PlayerState state) {
		return mAutoJoin && mayListen(state.getPlayer());
	}

	public boolean mayChat(CommandSender sender) {
		if (!sender.hasPermission("networkchat.say")) {
			return false;
		}
		if (!sender.hasPermission("networkchat.say.announcement")) {
			return false;
		}
		if (mChannelPermission != null && !sender.hasPermission(mChannelPermission)) {
			return false;
		}

		if (!(sender instanceof Player)) {
			return true;
		}

		ChannelPerms playerPerms = mPlayerPerms.get(((Player) sender).getUniqueId());
		if (playerPerms == null) {
			if (mDefaultPerms.mayChat() != null && mDefaultPerms.mayChat()) {
				return true;
			}
		} else if (playerPerms.mayChat() != null && playerPerms.mayChat()) {
			return true;
		}

		return false;
	}

	public boolean mayListen(CommandSender sender) {
		if (!sender.hasPermission("networkchat.see")) {
			return false;
		}
		if (!sender.hasPermission("networkchat.see.announcement")) {
			return false;
		}
		if (mChannelPermission != null && !sender.hasPermission(mChannelPermission)) {
			return false;
		}

		if (!(sender instanceof Player)) {
			return true;
		}

		UUID playerId = ((Player) sender).getUniqueId();

		ChannelPerms playerPerms = mPlayerPerms.get(playerId);
		if (playerPerms == null) {
			if (mDefaultPerms.mayListen() != null && !mDefaultPerms.mayListen()) {
				return false;
			}
		} else if (playerPerms.mayListen() != null && !playerPerms.mayListen()) {
			return false;
		}

		return true;
	}

	public void sendMessage(CommandSender sender, String messageText) throws WrapperCommandSyntaxException {
		if (sender instanceof Player) {
			if (!sender.hasPermission("networkchat.say")) {
				CommandUtils.fail(sender, "You do not have permission to chat.");
			}
			if (!sender.hasPermission("networkchat.say.announcement")) {
				CommandUtils.fail(sender, "You do not have permission to make announcements.");
			}
			if (mChannelPermission != null && !sender.hasPermission(mChannelPermission)) {
				CommandUtils.fail(sender, "You do not have permission to talk in " + mName + ".");
			}

			if (!mayChat(sender)) {
				CommandUtils.fail(sender, "You do not have permission to chat in this channel.");
			}
		}

		messageText = PlaceholderAPI.setPlaceholders(null, messageText);

		Message message = Message.createMessage(this, MessageType.SYSTEM, sender, null, messageText);

		try {
			MessageManager.getInstance().broadcastMessage(message);
		} catch (Exception e) {
			sender.sendMessage(Component.text("An exception occured broadcasting your message.", NamedTextColor.RED)
			    .hoverEvent(Component.text(e.getMessage(), NamedTextColor.RED)));
			CommandUtils.fail(sender, "Could not send message.");
		}
	}

	public void distributeMessage(Message message) {
		showMessage(Bukkit.getConsoleSender(), message);
		for (Map.Entry<UUID, PlayerState> playerStateEntry : PlayerStateManager.getPlayerStates().entrySet()) {
			PlayerState state = playerStateEntry.getValue();
			if (!mayListen(state.getPlayer())) {
				continue;
			}

			if (state.isListening(this)) {
				// This accounts for players who have paused their chat
				state.receiveMessage(message);
			}
		}
	}

	protected void showMessage(CommandSender recipient, Message message) {
		TextColor channelColor = NetworkChatPlugin.messageColor(CHANNEL_CLASS_ID);
		String prefix = NetworkChatPlugin.messageFormat(CHANNEL_CLASS_ID)
			.replace("<message_gui_cmd>", message.getGuiCommand())
			.replace("<channel_color>", MessagingUtils.colorToMiniMessage(channelColor)) + " ";

		Component fullMessage = Component.empty()
			.append(MessagingUtils.SENDER_FMT_MINIMESSAGE.parse(prefix, List.of(Template.of("channel_name", mName))))
			.append(Component.empty().color(channelColor).append(message.getMessage()));
		recipient.sendMessage(Identity.nil(), fullMessage, message.getMessageType());
		if (recipient instanceof Player) {
			PlayerStateManager.getPlayerState((Player) recipient).playMessageSound(this);
		}
	}

	public String getChannelPermission() {
		return mChannelPermission;
	}

	public void setChannelPermission(String newPerms) {
		mChannelPermission = newPerms;
	}
}
