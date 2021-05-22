package com.playmonumenta.networkchat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.NetworkRelayAPI;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.transformation.TransformationType;
import net.kyori.adventure.text.minimessage.markdown.DiscordFlavor;

// A channel visible to all shards
public class ChannelWhisper extends Channel {
	public static final String CHANNEL_CLASS_ID = "whisper";
	private static final String[] WHISPER_COMMANDS = {"msg", "tell", "w"};
	private static final String REPLY_COMMAND = "r";

	private UUID mId;
	private Instant mLastUpdate;
	private List<UUID> mParticipants;
	private ChannelSettings mDefaultSettings;
	private ChannelPerms mDefaultPerms;
	private Map<UUID, ChannelPerms> mPlayerPerms;

	private ChannelWhisper(UUID channelId, Instant lastUpdate, List<UUID> participants) {
		mId = channelId;
		mLastUpdate = lastUpdate;
		mParticipants = new ArrayList<>(participants);

		mDefaultSettings = new ChannelSettings();
		mDefaultSettings.isListening(true);

		mDefaultPerms = new ChannelPerms();
		mDefaultPerms.mayChat(true);
		mDefaultPerms.mayListen(true);

		mPlayerPerms = new HashMap<>();
	}

	public ChannelWhisper(UUID from, UUID to) {
		mLastUpdate = Instant.now();
		mId = UUID.randomUUID();
		List<UUID> participants = new ArrayList<>();
		participants.add(from);
		participants.add(to);
		Collections.sort(participants);
		mParticipants = new ArrayList<>(participants);

		mDefaultSettings = new ChannelSettings();
		mDefaultSettings.isListening(true);

		mDefaultPerms = new ChannelPerms();
		mDefaultPerms.mayChat(true);
		mDefaultPerms.mayListen(true);

		mPlayerPerms = new HashMap<>();
	}

	protected static Channel fromJsonInternal(JsonObject channelJson) throws Exception {
		String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();
		if (channelClassId == null || !channelClassId.equals(CHANNEL_CLASS_ID)) {
			throw new Exception("Cannot create ChannelWhisper from channel ID " + channelClassId);
		}
		String uuidString = channelJson.getAsJsonPrimitive("uuid").getAsString();
		UUID channelId = UUID.fromString(uuidString);
		Instant lastUpdate = Instant.now();
		if (channelJson.get("lastUpdate") != null) {
			lastUpdate = Instant.ofEpochMilli(channelJson.get("lastUpdate").getAsLong());
		}

		JsonArray participantsJson = channelJson.getAsJsonArray("participants");
		List<UUID> participants = new ArrayList<>();
		for (JsonElement participantJson : participantsJson) {
			participants.add(UUID.fromString(participantJson.getAsString()));
		}

		ChannelWhisper channel = new ChannelWhisper(channelId, lastUpdate, participants);

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
					// TODO Log this
					continue;
				}
				ChannelPerms playerPerms = ChannelPerms.fromJson(playerPermsJson);
				channel.mPlayerPerms.put(playerId, playerPerms);
			}
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

		JsonArray participantsJson = new JsonArray();
		for (UUID playerUuid : mParticipants) {
			participantsJson.add(playerUuid.toString());
		}

		JsonObject result = new JsonObject();
		result.addProperty("type", CHANNEL_CLASS_ID);
		result.addProperty("uuid", mId.toString());
		result.addProperty("lastUpdate", mLastUpdate.toEpochMilli());
		result.addProperty("name", getName());
		result.add("participants", participantsJson);
		result.add("defaultSettings", mDefaultSettings.toJson());
		result.add("defaultPerms", mDefaultPerms.toJson());
		result.add("playerPerms", allPlayerPermsJson);
		return result;
	}

	public static void registerNewChannelCommands(String[] baseCommands, List<Argument> prefixArguments) {
		// Setting up new whisper channels will be done via /msg, /tell, /w, and similar,
		// not through /chattest new Blah whisper. The provided arguments are ignored.
		List<Argument> arguments = new ArrayList<>();

		for (String command : WHISPER_COMMANDS) {
			CommandAPI.unregister(command);

			arguments.clear();
			arguments.add(new StringArgument("recipient").overrideSuggestions((sender) -> {
				return RemotePlayerManager.onlinePlayerNames().toArray(new String[0]);
			}));
			new CommandAPICommand(command)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return runCommandSet(sender, (String)args[0]);
				})
				.register();

			arguments.clear();
			arguments.add(new StringArgument("recipient").overrideSuggestions((sender) -> {
				return RemotePlayerManager.onlinePlayerNames().toArray(new String[0]);
			}));
			arguments.add(new GreedyStringArgument("message"));
			new CommandAPICommand(command)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return runCommandSay(sender, (String)args[0], (String)args[1]);
				})
				.register();
		}

		new CommandAPICommand(REPLY_COMMAND)
			.executes((sender, args) -> {
				return runCommandReplySet(sender);
			})
			.register();

		arguments.clear();
		arguments.add(new GreedyStringArgument("message"));
		new CommandAPICommand(REPLY_COMMAND)
			.withArguments(arguments)
			.executes((sender, args) -> {
				return runCommandReplySay(sender, (String)args[0]);
			})
			.register();
	}

	private static int runCommandSet(CommandSender sender, String recipientName) throws WrapperCommandSyntaxException {
		if (!(sender instanceof Player)) {
			CommandAPI.fail("This command can only be run as a player.");
		}

		Player sendingPlayer = (Player) sender;

		UUID recipientUuid = RemotePlayerManager.getPlayerId(recipientName);
		if (recipientUuid == null) {
			CommandAPI.fail(recipientName + " is not online.");
		}

		PlayerState senderState = PlayerStateManager.getPlayerState(sendingPlayer);
		ChannelWhisper channel = senderState.getWhisperChannel(recipientUuid);
		if (channel == null) {
			try {
				channel = new ChannelWhisper(sendingPlayer.getUniqueId(), recipientUuid);
			} catch (Exception e) {
				CommandAPI.fail("Could not create new whisper channel: Could not connect to RabbitMQ.");
			}
			ChannelManager.registerNewChannel(sender, channel);
			senderState.setWhisperChannel(recipientUuid, channel);
		}

		senderState.setActiveChannel(channel);
		sender.sendMessage(Component.text("You are now typing whispers to " + recipientName + ".", NamedTextColor.GRAY));
		return 1;
	}

	private static int runCommandSay(CommandSender sender, String recipientName, String message) throws WrapperCommandSyntaxException {
		if (!(sender instanceof Player)) {
			CommandAPI.fail("This command can only be run as a player.");
		}

		Player sendingPlayer = (Player) sender;

		UUID recipientUuid = RemotePlayerManager.getPlayerId(recipientName);
		if (recipientUuid == null) {
			CommandAPI.fail(recipientName + " is not online.");
		}

		PlayerState senderState = PlayerStateManager.getPlayerState(sendingPlayer);
		ChannelWhisper channel = senderState.getWhisperChannel(recipientUuid);
		if (channel == null) {
			try {
				channel = new ChannelWhisper(sendingPlayer.getUniqueId(), recipientUuid);
			} catch (Exception e) {
				CommandAPI.fail("Could not create new whisper channel: Could not connect to RabbitMQ.");
			}
			ChannelManager.registerNewChannel(sender, channel);
			senderState.setWhisperChannel(recipientUuid, channel);
		}

		channel.sendMessage(sendingPlayer, message);
		return 1;
	}

	private static int runCommandReplySet(CommandSender sender) throws WrapperCommandSyntaxException {
		if (!(sender instanceof Player)) {
			CommandAPI.fail("This command can only be run as a player.");
		}

		Player sendingPlayer = (Player) sender;

		PlayerState senderState = PlayerStateManager.getPlayerState(sendingPlayer);
		ChannelWhisper channel = senderState.getLastWhisperChannel();
		if (channel == null) {
			CommandAPI.fail("No one has sent you a whisper yet.");
		}

		senderState.setActiveChannel(channel);
		sender.sendMessage(Component.text("You are now typing replies to the last person to whisper to you.", NamedTextColor.GRAY));
		return 1;
	}

	private static int runCommandReplySay(CommandSender sender, String message) throws WrapperCommandSyntaxException {
		if (!(sender instanceof Player)) {
			CommandAPI.fail("This command can only be run as a player.");
		}

		Player sendingPlayer = (Player) sender;

		PlayerState senderState = PlayerStateManager.getPlayerState(sendingPlayer);
		ChannelWhisper channel = senderState.getLastWhisperChannel();
		if (channel == null) {
			CommandAPI.fail("No one has sent you a whisper yet.");
		}

		channel.sendMessage(sendingPlayer, message);
		return 1;
	}

	public static String getClassId() {
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
		CommandAPI.fail("Whisper channels may not be named.");
	}

	public String getName() {
		String name = "Whisper";
		for (UUID participant : mParticipants) {
			name = name + "_" + participant.toString();
		}
		return name;
	}

	public List<UUID> getParticipants() {
		return new ArrayList<>(mParticipants);
	}

	public UUID getOtherParticipant(UUID from) {
		if (mParticipants.get(0).equals(from)) {
			return mParticipants.get(1);
		} else {
			return mParticipants.get(0);
		}
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

	public ChannelPerms playerPerms(OfflinePlayer player) {
		if (player == null) {
			return null;
		}
		UUID playerId = player.getUniqueId();
		ChannelPerms perms = mPlayerPerms.get(playerId);
		if (perms == null) {
			perms = new ChannelPerms();
			mPlayerPerms.put(playerId, perms);
		}
		return perms;
	}

	public void clearPlayerPerms(OfflinePlayer player) {
		if (player == null) {
			return;
		}
		mPlayerPerms.remove(player.getUniqueId());
	}

	public void sendMessage(CommandSender sender, String messageText) throws WrapperCommandSyntaxException {
		// TODO Add permission check for whisper chat.

		if (!(sender instanceof Player)) {
			CommandAPI.fail("Only players may whisper.");
		}

		UUID senderId = ((Player) sender).getUniqueId();
		if (!mParticipants.contains(senderId)) {
			CommandAPI.fail("You do not have permission to chat in this channel.");
		}
		UUID receiverId = getOtherParticipant(senderId);

		ChannelPerms playerPerms = mPlayerPerms.get(((Player) sender).getUniqueId());
		if (playerPerms == null) {
			if (mDefaultPerms.mayChat() != null && !mDefaultPerms.mayChat()) {
				CommandAPI.fail("You do not have permission to chat in this channel.");
			}
		} else if (playerPerms.mayChat() != null && !playerPerms.mayChat()) {
			CommandAPI.fail("You do not have permission to chat in this channel.");
		}

		if (RemotePlayerManager.getPlayerName(receiverId) == null) {
			CommandAPI.fail("That player is not online.");
		}

		JsonObject extraData = new JsonObject();
		extraData.addProperty("receiver", receiverId.toString());

		// TODO Permissions for allowed chat transformations?
		Set<TransformationType> allowedTransforms = new HashSet<>();
		allowedTransforms.add(TransformationType.COLOR);
		allowedTransforms.add(TransformationType.DECORATION);
		allowedTransforms.add(TransformationType.KEYBIND);
		allowedTransforms.add(TransformationType.FONT);
		allowedTransforms.add(TransformationType.GRADIENT);
		allowedTransforms.add(TransformationType.RAINBOW);

		Message message = Message.createMessage(this, sender, extraData, messageText, true, allowedTransforms);

		try {
			MessageManager.getInstance().broadcastMessage(message);
		} catch (Exception e) {
			sender.sendMessage(Component.text("An exception occured broadcasting your message.", NamedTextColor.RED)
			    .hoverEvent(Component.text(e.getMessage(), NamedTextColor.RED)));
			CommandAPI.fail("Could not send message.");
		}
	}

	public void distributeMessage(Message message) {
		showMessage(Bukkit.getConsoleSender(), message);

		// TODO Check permission to see the message.
		JsonObject extraData = message.getExtraData();
		UUID receiverUuid;
		try {
			receiverUuid = UUID.fromString(extraData.getAsJsonPrimitive("receiver").getAsString());
		} catch (Exception e) {
			// Could not read receiver from message
			return;
		}
		UUID senderUuid = message.getSenderId();
		distributeMessageToPlayer(receiverUuid, senderUuid, message);
		if (!senderUuid.equals(receiverUuid)) {
			distributeMessageToPlayer(senderUuid, receiverUuid, message);
		}
	}

	private void distributeMessageToPlayer(UUID playerId, UUID otherId, Message message) {
		PlayerState state = PlayerStateManager.getPlayerStates().get(playerId);
		if (state == null) {
			// Player is not on this shard
			return;
		}
		state.setWhisperChannel(otherId, this);

		ChannelPerms playerPerms = mPlayerPerms.get(playerId);
		if (playerPerms == null) {
			if (mDefaultPerms.mayListen() != null && !mDefaultPerms.mayListen()) {
				return;
			}
		} else if (playerPerms.mayListen() != null && !playerPerms.mayListen()) {
			return;
		}

		if (state.isListening(this)) {
			// This accounts for players who have paused their chat
			state.receiveMessage(message);
		}
	}

	protected void showMessage(CommandSender recipient, Message message) {
		JsonObject extraData = message.getExtraData();
		String receiverName;
		try {
			UUID receiverUuid = UUID.fromString(extraData.getAsJsonPrimitive("receiver").getAsString());
			receiverName = RemotePlayerManager.getPlayerName(receiverUuid);
		} catch (Exception e) {
			receiverName = "ErrorLoadingName";
		}

		MiniMessage minimessage = MiniMessage.builder()
			.transformation(TransformationType.COLOR)
			.transformation(TransformationType.DECORATION)
			.markdown()
			.markdownFlavor(DiscordFlavor.get())
			.build();

		// TODO Use configurable formatting, not hard-coded formatting.
		String prefix = "<gray><sender> whispers to <receiver> <gray>» ";
		// TODO We should use templates to insert these and related formatting.
		prefix = prefix.replace("<sender>", message.getSenderName())
		    .replace("<receiver>", receiverName);

		UUID senderUuid = message.getSenderId();
		Identity senderIdentity;
		if (senderUuid == null) {
			senderIdentity = Identity.nil();
		} else {
			senderIdentity = Identity.identity(senderUuid);
		}

		Component fullMessage = Component.empty()
		    .append(minimessage.parse(prefix))
		    .append(Component.empty().color(NamedTextColor.GRAY).append(message.getMessage()));
		recipient.sendMessage(senderIdentity, fullMessage, MessageType.CHAT);
		if (recipient instanceof Player && !((Player) recipient).getUniqueId().equals(senderUuid)) {
			PlayerStateManager.getPlayerState((Player) recipient).playMessageSound(this);
		}
	}
}