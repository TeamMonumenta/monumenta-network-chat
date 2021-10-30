package com.playmonumenta.networkchat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.Template;

// A channel visible to all shards
public class ChannelWhisper extends Channel implements ChannelInviteOnly {
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
		mDefaultPerms = new ChannelPerms();
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
		mDefaultPerms = new ChannelPerms();
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
		// not through /chat new Blah whisper. The provided arguments are ignored.
		List<Argument> arguments = new ArrayList<>();

		for (String command : WHISPER_COMMANDS) {
			CommandAPI.unregister(command);

			arguments.clear();
			arguments.add(new StringArgument("recipient").replaceSuggestions(info ->
				RemotePlayerManager.visiblePlayerNames().toArray(new String[0])
			));
			new CommandAPICommand(command)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return runCommandSet(sender, (String)args[0]);
				})
				.register();

			arguments.clear();
			arguments.add(new StringArgument("recipient").replaceSuggestions(info ->
				RemotePlayerManager.visiblePlayerNames().toArray(new String[0])
			));
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
		CommandAPI.fail("Whisper channels may not be named.");
	}

	public String getName() {
		String name = "Whisper";
		for (UUID participant : mParticipants) {
			name = name + "_" + participant.toString();
		}
		return name;
	}

	public boolean isParticipant(CommandSender sender) {
		if (!(sender instanceof Player)) {
			return false;
		}
		return isParticipant((Player) sender);
	}

	public boolean isParticipant(Player player) {
		return isParticipant(player.getUniqueId());
	}

	public boolean isParticipant(UUID playerId) {
		return mParticipants.contains(playerId);
	}

	public List<UUID> getParticipantIds() {
		return new ArrayList<>(mParticipants);
	}

	public List<String> getParticipantNames() {
		List<String> names = new ArrayList<>();
		for (UUID playerId : mParticipants) {
			String name = MonumentaRedisSyncAPI.cachedUuidToName(playerId);
			if (name != null) {
				names.add(name);
			}
		}
		return names;
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
		return false;
	}

	public boolean mayChat(CommandSender sender) {
		if (!sender.hasPermission("networkchat.say")) {
			return false;
		}
		if (!sender.hasPermission("networkchat.say.whisper")) {
			return false;
		}

		if (!(sender instanceof Player)) {
			return false;
		}

		Player player = (Player) sender;
		ChannelPerms playerPerms = mPlayerPerms.get(player.getUniqueId());
		if (playerPerms == null) {
			if (mDefaultPerms.mayChat() != null && !mDefaultPerms.mayChat()) {
				return false;
			}
		} else if (playerPerms.mayChat() != null && !playerPerms.mayChat()) {
			return false;
		}

		return mParticipants.contains(player.getUniqueId());
	}

	public boolean mayListen(CommandSender sender) {
		if (!sender.hasPermission("networkchat.see")) {
			return false;
		}
		if (!sender.hasPermission("networkchat.see.whisper")) {
			return false;
		}

		if (!(sender instanceof Player)) {
			return false;
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

		return mParticipants.contains(playerId);
	}

	public void sendMessage(CommandSender sender, String messageText) throws WrapperCommandSyntaxException {
		if (!(sender instanceof Player)) {
			CommandAPI.fail("Only players may whisper.");
		}

		if (!sender.hasPermission("networkchat.say")) {
			CommandAPI.fail("You do not have permission to chat.");
		}
		if (!sender.hasPermission("networkchat.say.whisper")) {
			CommandAPI.fail("You do not have permission to whisper.");
		}

		if (!mayChat(sender)) {
			CommandAPI.fail("You do not have permission to chat in this channel.");
		}

		UUID senderId = ((Player) sender).getUniqueId();
		UUID receiverId = getOtherParticipant(senderId);

		if (!RemotePlayerManager.isPlayerVisible(receiverId)) {
			sender.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
		}

		JsonObject extraData = new JsonObject();
		extraData.addProperty("receiver", receiverId.toString());

		Message message = Message.createMessage(this, MessageType.CHAT, sender, extraData, messageText);

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
		Component receiverComp;
		try {
			UUID receiverUuid = UUID.fromString(extraData.getAsJsonPrimitive("receiver").getAsString());
			receiverComp = RemotePlayerManager.getPlayerComponent(receiverUuid);
		} catch (Exception e) {
			receiverComp = Component.text("ErrorLoadingName");
		}

		TextColor channelColor = NetworkChatPlugin.messageColor(CHANNEL_CLASS_ID);
		String prefix = NetworkChatPlugin.messageFormat(CHANNEL_CLASS_ID)
		    .replace("<channel_color>", MessagingUtils.colorToMiniMessage(channelColor)) + " ";

		UUID senderUuid = message.getSenderId();
		Identity senderIdentity;
		if (senderUuid == null) {
			senderIdentity = Identity.nil();
		} else {
			senderIdentity = Identity.identity(senderUuid);
		}

		Component fullMessage = Component.empty()
		    .append(MessagingUtils.SENDER_FMT_MINIMESSAGE.parse(prefix, List.of(Template.of("sender", message.getSenderComponent()),
		        Template.of("receiver", receiverComp))))
		    .append(Component.empty().color(channelColor).append(message.getMessage()));
		recipient.sendMessage(senderIdentity, fullMessage, message.getMessageType());
		if (recipient instanceof Player && !((Player) recipient).getUniqueId().equals(senderUuid)) {
			PlayerStateManager.getPlayerState((Player) recipient).playMessageSound(this);
		}
	}
}
