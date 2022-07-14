package com.playmonumenta.networkchat;

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
import dev.jorel.commandapi.wrappers.NativeProxyCommandSender;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.Template;
import net.kyori.adventure.text.minimessage.template.TemplateResolver;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

// A channel visible only to this world (and moderators who opt in from elsewhere)
public class ChannelWorld extends Channel implements ChannelPermissionNode, ChannelAutoJoin {
	public static final String CHANNEL_CLASS_ID = "world";

	private final UUID mId;
	private Instant mLastUpdate;
	private String mName;
	private TextColor mMessageColor;
	private ChannelSettings mDefaultSettings;
	private ChannelAccess mDefaultAccess;
	private final Map<UUID, ChannelAccess> mPlayerAccess;
	private boolean mAutoJoin = true;
	private String mChannelPermission = null;

	private ChannelWorld(UUID channelId, Instant lastUpdate, String name) {
		mId = channelId;
		mLastUpdate = lastUpdate;
		mName = name;

		mDefaultSettings = new ChannelSettings();
		mDefaultAccess = new ChannelAccess();
		mPlayerAccess = new HashMap<>();
	}

	public ChannelWorld(String name) {
		mLastUpdate = Instant.now();
		mId = UUID.randomUUID();
		mName = name;

		mDefaultSettings = new ChannelSettings();
		mDefaultAccess = new ChannelAccess();
		mPlayerAccess = new HashMap<>();
	}

	protected static Channel fromJsonInternal(JsonObject channelJson) throws Exception {
		String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();
		if (channelClassId == null || !channelClassId.equals(CHANNEL_CLASS_ID)) {
			throw new Exception("Cannot create ChannelWorld from channel ID " + channelClassId);
		}
		String uuidString = channelJson.getAsJsonPrimitive("uuid").getAsString();
		UUID channelId = UUID.fromString(uuidString);
		Instant lastUpdate = Instant.now();
		JsonElement lastUpdateJson = channelJson.get("lastUpdate");
		if (lastUpdateJson != null) {
			lastUpdate = Instant.ofEpochMilli(lastUpdateJson.getAsLong());
		}
		String name = channelJson.getAsJsonPrimitive("name").getAsString();

		ChannelWorld channel = new ChannelWorld(channelId, lastUpdate, name);

		JsonPrimitive messageColorJson = channelJson.getAsJsonPrimitive("messageColor");
		if (messageColorJson != null && messageColorJson.isString()) {
			String messageColorString = messageColorJson.getAsString();
			try {
				channel.mMessageColor = MessagingUtils.colorFromString(messageColorString);
			} catch (Exception e) {
				assert NetworkChatPlugin.getInstance() != null;
				NetworkChatPlugin.getInstance().getLogger().warning("Caught exception getting mMessageColor from json: " + e.getMessage());
			}
		}

		JsonObject defaultSettingsJson = channelJson.getAsJsonObject("defaultSettings");
		if (defaultSettingsJson != null) {
			channel.mDefaultSettings = ChannelSettings.fromJson(defaultSettingsJson);
		}

		JsonObject defaultAccessJson = channelJson.getAsJsonObject("defaultAccess");
		if (defaultAccessJson == null) {
			defaultAccessJson = channelJson.getAsJsonObject("defaultPerms");
		}
		if (defaultAccessJson != null) {
			channel.mDefaultAccess = ChannelAccess.fromJson(defaultAccessJson);
		}

		JsonObject allPlayerAccessJson = channelJson.getAsJsonObject("playerAccess");
		if (allPlayerAccessJson == null) {
			allPlayerAccessJson = channelJson.getAsJsonObject("playerPerms");
		}
		if (allPlayerAccessJson != null) {
			for (Map.Entry<String, JsonElement> playerPermEntry : allPlayerAccessJson.entrySet()) {
				UUID playerId;
				JsonObject playerAccessJson;
				try {
					playerId = UUID.fromString(playerPermEntry.getKey());
					playerAccessJson = playerPermEntry.getValue().getAsJsonObject();
				} catch (Exception e) {
					assert NetworkChatPlugin.getInstance() != null;
					NetworkChatPlugin.getInstance().getLogger().warning("Catch exception during converting json to channel world reason: " + e.getMessage());
					continue;
				}
				ChannelAccess playerAccess = ChannelAccess.fromJson(playerAccessJson);
				channel.mPlayerAccess.put(playerId, playerAccess);
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
		JsonObject allPlayerAccessJson = new JsonObject();
		for (Map.Entry<UUID, ChannelAccess> playerPermEntry : mPlayerAccess.entrySet()) {
			UUID channelId = playerPermEntry.getKey();
			ChannelAccess channelAccess = playerPermEntry.getValue();
			if (!channelAccess.isDefault()) {
				allPlayerAccessJson.add(channelId.toString(), channelAccess.toJson());
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("type", CHANNEL_CLASS_ID);
		result.addProperty("uuid", mId.toString());
		result.addProperty("lastUpdate", mLastUpdate.toEpochMilli());
		result.addProperty("name", mName);
		if (mMessageColor != null) {
			result.addProperty("messageColor", MessagingUtils.colorToString(mMessageColor));
		}
		result.addProperty("autoJoin", mAutoJoin);
		if (mChannelPermission != null) {
			result.addProperty("channelPermission", mChannelPermission);
		}
		result.add("defaultSettings", mDefaultSettings.toJson());
		result.add("defaultAccess", mDefaultAccess.toJson());
		result.add("playerAccess", allPlayerAccessJson);
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
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.new.world")) {
						CommandUtils.fail(sender, "You do not have permission to create world channels.");
					}

					String channelName = (String)args[prefixArguments.size() - 1];
					ChannelWorld newChannel = null;

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelWorld(channelName);
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
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.new.world")) {
						CommandUtils.fail(sender, "You do not have permission to create world channels.");
					}

					String channelName = (String)args[prefixArguments.size() - 1];
					ChannelWorld newChannel = null;

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelWorld(channelName);
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
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.new.world")) {
						CommandUtils.fail(sender, "You do not have permission to create world channels.");
					}

					String channelName = (String)args[prefixArguments.size() - 1];
					ChannelWorld newChannel = null;

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelWorld(channelName);
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

	public @Nullable TextColor color() {
		return mMessageColor;
	}

	public void color(CommandSender sender, @Nullable TextColor color) throws WrapperCommandSyntaxException {
		mMessageColor = color;
	}

	public ChannelSettings channelSettings() {
		return mDefaultSettings;
	}

	public ChannelAccess channelAccess() {
		return mDefaultAccess;
	}

	public ChannelAccess playerAccess(UUID playerId) {
		if (playerId == null) {
			return null;
		}
		ChannelAccess playerAccess = mPlayerAccess.get(playerId);
		if (playerAccess == null) {
			playerAccess = new ChannelAccess();
			mPlayerAccess.put(playerId, playerAccess);
		}
		return playerAccess;
	}

	public void resetPlayerAccess(UUID playerId) {
		if (playerId == null) {
			return;
		}
		mPlayerAccess.remove(playerId);
	}

	public boolean shouldAutoJoin(PlayerState state) {
		Player player = state.getPlayer();
		return mAutoJoin && player != null && mayListen(player);
	}

	public boolean mayChat(CommandSender sender) {
		if (!CommandUtils.hasPermission(sender, "networkchat.say.world")) {
			return false;
		}
		if (mChannelPermission != null && !CommandUtils.hasPermission(sender, mChannelPermission)) {
			return false;
		}

		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player player)) {
			return true;
		} else {
			ChannelAccess playerAccess = mPlayerAccess.get(player.getUniqueId());
			if (playerAccess == null) {
				return mDefaultAccess.mayChat() == null || mDefaultAccess.mayChat();
			} else {
				return playerAccess.mayChat() == null || playerAccess.mayChat();
			}
		}
	}

	public boolean mayListen(CommandSender sender) {
		if (!CommandUtils.hasPermission(sender, "networkchat.see.world")) {
			return false;
		}
		if (mChannelPermission != null && !CommandUtils.hasPermission(sender, mChannelPermission)) {
			return false;
		}

		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player player)) {
			return true;
		} else {
			UUID playerId = player.getUniqueId();

			ChannelAccess playerAccess = mPlayerAccess.get(playerId);
			if (playerAccess == null) {
				return mDefaultAccess.mayListen() == null || mDefaultAccess.mayListen();
			} else {
				return playerAccess.mayListen() == null || playerAccess.mayListen();
			}
		}
	}

	public void sendMessage(CommandSender sender, String messageText) throws WrapperCommandSyntaxException {
		if (!CommandUtils.hasPermission(sender, "networkchat.say.world")) {
			CommandUtils.fail(sender, "You do not have permission to talk in world chat.");
		}
		if (mChannelPermission != null && !CommandUtils.hasPermission(sender, mChannelPermission)) {
			CommandUtils.fail(sender, "You do not have permission to talk in " + mName + ".");
		}

		if (!mayChat(sender)) {
			CommandUtils.fail(sender, "You do not have permission to chat in this channel.");
		}

		if (messageText.contains("@")) {
			if (messageText.contains("@everyone") && !CommandUtils.hasPermission(sender, "networkchat.ping.everyone")) {
				CommandUtils.fail(sender, "You do not have permission to ping everyone in this channel.");
			} else if (!CommandUtils.hasPermission(sender, "networkchat.ping.player")) {
				CommandUtils.fail(sender, "You do not have permission to ping a player in this channel.");
			}
		}

		World world;
		if (sender instanceof NativeProxyCommandSender nativeSender) {
			world = nativeSender.getWorld();
		} else if (sender instanceof Entity entity) {
			world = entity.getWorld();
		} else if (sender instanceof BlockCommandSender blockCommandSender) {
			world = blockCommandSender.getBlock().getWorld();
		} else {
			CommandUtils.fail(sender, "Unable to get world for world channel message.");
			throw new RuntimeException("The previous line should have thrown an exception.");
		}

		JsonObject extraData = new JsonObject();
		extraData.addProperty("fromShard", RemotePlayerManager.getShardName());
		extraData.addProperty("fromWorld", world.getName());

		@Nullable Message message = Message.createMessage(this, MessageType.CHAT, sender, extraData, messageText);
		if (message == null) {
			return;
		}

		try {
			MessageManager.getInstance().broadcastMessage(message);
		} catch (Exception e) {
			CommandUtils.fail(sender, "Could not send message; RabbitMQ is not responding.");
		}
	}

	public void distributeMessage(Message message) {
		NetworkChatPlugin instance = NetworkChatPlugin.getInstance();
		assert instance != null;
		JsonObject extraData = message.getExtraData();
		if (extraData == null) {
			instance.getLogger().warning("Got world chat message with no fromShard, ignoring.");
			return;
		}

		String fromShard;
		JsonElement fromShardJsonElement = extraData.get("fromShard");
		if (!fromShardJsonElement.isJsonPrimitive()) {
			instance.getLogger().warning("Got world chat message with invalid fromShard json, ignoring.");
			return;
		}
		JsonPrimitive fromShardJsonPrimitive = fromShardJsonElement.getAsJsonPrimitive();
		if (!fromShardJsonPrimitive.isString()) {
			instance.getLogger().warning("Got world chat message with invalid fromShard json, ignoring.");
			return;
		}
		fromShard = fromShardJsonPrimitive.getAsString();
		if (!fromShard.equals(RemotePlayerManager.getShardName())) {
			// TODO Chat spy here
			return;
		}

		String fromWorld;
		JsonElement fromWorldJsonElement = extraData.get("fromWorld");
		if (!fromWorldJsonElement.isJsonPrimitive()) {
			instance.getLogger().warning("Got world chat message with invalid fromWorld json, ignoring.");
			return;
		}
		JsonPrimitive fromWorldJsonPrimitive = fromWorldJsonElement.getAsJsonPrimitive();
		if (!fromWorldJsonPrimitive.isString()) {
			instance.getLogger().warning("Got world chat message with invalid fromWorld json, ignoring.");
			return;
		}
		fromWorld = fromWorldJsonPrimitive.getAsString();

		showMessage(Bukkit.getConsoleSender(), message);
		for (Map.Entry<UUID, PlayerState> playerStateEntry : PlayerStateManager.getPlayerStates().entrySet()) {
			PlayerState state = playerStateEntry.getValue();
			Player player = state.getPlayer();
			if (player == null || !player.getWorld().getName().equals(fromWorld) || !mayListen(player)) {
				continue;
			}

			if (state.isListening(this)) {
				// This accounts for players who have paused their chat
				state.receiveMessage(message);
			}
		}
	}

	protected Component shownMessage(CommandSender recipient, Message message) {
		TextColor channelColor;
		if (mMessageColor != null) {
			channelColor = mMessageColor;
		} else {
			channelColor = NetworkChatPlugin.messageColor(CHANNEL_CLASS_ID);
		}
		String prefix = NetworkChatPlugin.messageFormat(CHANNEL_CLASS_ID)
			.replace("<message_gui_cmd>", message.getGuiCommand())
			.replace("<channel_color>", MessagingUtils.colorToMiniMessage(channelColor)) + " ";

		return Component.empty()
			.append(MessagingUtils.SENDER_FMT_MINIMESSAGE.deserialize(prefix, TemplateResolver.templates(Template.template("channel_name", mName),
				Template.template("sender", message.getSenderComponent()))))
			.append(Component.empty().color(channelColor).append(message.getMessage()));
	}

	protected void showMessage(CommandSender recipient, Message message) {
		UUID senderUuid = message.getSenderId();
		recipient.sendMessage(message.getSenderIdentity(), shownMessage(recipient, message), message.getMessageType());
		if (recipient instanceof Player player && !player.getUniqueId().equals(senderUuid)) {
			@Nullable PlayerState playerState = PlayerStateManager.getPlayerState(player);
			if (playerState == null) {
				player.sendMessage(MessagingUtils.noChatState(player));
				return;
			}
			playerState.playMessageSound(message);
		}
	}

	@Override
	public String getChannelPermission() {
		return mChannelPermission;
	}

	@Override
	public void setChannelPermission(String newPerms) {
		mChannelPermission = newPerms;
	}

	public boolean getAutoJoin() {
		return mAutoJoin;
	}

	@Override
	public void setAutoJoin(Boolean newAutoJoin) {
		mAutoJoin = newAutoJoin;
	}
}