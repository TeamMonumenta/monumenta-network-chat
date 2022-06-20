package com.playmonumenta.networkchat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.Template;
import net.kyori.adventure.text.minimessage.template.TemplateResolver;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// A channel visible to all shards
public class ChannelWhisper extends Channel implements ChannelInviteOnly {
	public static final String CHANNEL_CLASS_ID = "whisper";
	private static final String[] WHISPER_COMMANDS = {"msg", "tell", "w"};
	private static final String REPLY_COMMAND = "r";

	private final UUID mId;
	private Instant mLastUpdate;
	private final List<UUID> mParticipants;
	private ChannelSettings mDefaultSettings;
	private ChannelAccess mDefaultAccess;
	private final Map<UUID, ChannelAccess> mPlayerAccess;

	public ChannelWhisper(UUID from, UUID to) {
		this(UUID.randomUUID(), Instant.now(), List.of(from, to));
	}

	private ChannelWhisper(UUID channelId, Instant lastUpdate, List<UUID> participants) {
		mId = channelId;
		mLastUpdate = lastUpdate;
		mParticipants = new ArrayList<>(participants);

		mDefaultSettings = new ChannelSettings();
		mDefaultSettings.addSound(Sound.ENTITY_PLAYER_LEVELUP, 1, 0.5f);
		mDefaultAccess = new ChannelAccess();
		mPlayerAccess = new HashMap<>();
	}



	protected static Channel fromJsonInternal(JsonObject channelJson) throws Exception {
		String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();
		if (channelClassId == null || !channelClassId.equals(CHANNEL_CLASS_ID)) {
			throw new Exception("Cannot create ChannelWhisper from channel ID " + channelClassId);
		}
		String uuidString = channelJson.getAsJsonPrimitive("uuid").getAsString();
		UUID channelId = UUID.fromString(uuidString);
		Instant lastUpdate = Instant.now();
		JsonElement lastUpdateJson = channelJson.get("lastUpdate");
		if (lastUpdateJson != null) {
			lastUpdate = Instant.ofEpochMilli(lastUpdateJson.getAsLong());
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

		JsonObject defaultAccessJson = channelJson.getAsJsonObject("defaultAccess");
		if (defaultAccessJson == null) {
			defaultAccessJson = channelJson.getAsJsonObject("defaultPerms");
		}
		if (defaultAccessJson != null) {
			channel.mDefaultAccess = ChannelAccess.fromJson(defaultAccessJson);
		}

		JsonObject allPlayerAccessJson = channelJson.getAsJsonObject("playerAccess");
		if (allPlayerAccessJson != null) {
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
					// TODO Log this
					continue;
				}
				ChannelAccess playerAccess = ChannelAccess.fromJson(playerAccessJson);
				channel.mPlayerAccess.put(playerId, playerAccess);
			}
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
		result.add("defaultAccess", mDefaultAccess.toJson());
		result.add("playerAccess", allPlayerAccessJson);
		return result;
	}

	public static void registerNewChannelCommands() {
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
		if (!(sender instanceof Player sendingPlayer)) {
			CommandUtils.fail(sender, "This command can only be run as a player.");
		} else {
			UUID recipientUuid = RemotePlayerManager.getPlayerId(recipientName);
			if (recipientUuid == null) {
				CommandUtils.fail(sender, recipientName + " is not online.");
			}

			@Nullable PlayerState senderState = PlayerStateManager.getPlayerState(sendingPlayer);
			if (senderState == null) {
				sendingPlayer.sendMessage(MessagingUtils.noChatState(sendingPlayer));
				return 0;
			}
			@Nullable ChannelWhisper channel = senderState.getWhisperChannel(recipientUuid);
			if (channel == null) {
				try {
					channel = new ChannelWhisper(sendingPlayer.getUniqueId(), recipientUuid);
				} catch (Exception e) {
					CommandUtils.fail(sender, "Could not create new whisper channel: Could not connect to RabbitMQ.");
				}
				ChannelManager.registerNewChannel(sender, channel);
				senderState.setWhisperChannel(recipientUuid, channel);
			}

			senderState.setActiveChannel(channel);
			sender.sendMessage(Component.text("You are now typing whispers to " + recipientName + ".", NamedTextColor.GRAY));
		}
		return 1;
	}

	private static int runCommandSay(CommandSender sender, String recipientName, String message) throws WrapperCommandSyntaxException {
		if (!(sender instanceof Player sendingPlayer)) {
			CommandUtils.fail(sender, "This command can only be run as a player.");
		} else {
			UUID recipientUuid = RemotePlayerManager.getPlayerId(recipientName);
			if (recipientUuid == null) {
				CommandUtils.fail(sender, recipientName + " is not online.");
			}

			@Nullable PlayerState senderState = PlayerStateManager.getPlayerState(sendingPlayer);
			if (senderState == null) {
				CommandUtils.fail(sendingPlayer, MessagingUtils.noChatStateStr(sendingPlayer));
			} else if (senderState.isPaused()) {
				CommandUtils.fail(sendingPlayer, "You cannot chat with chat paused (/chat unpause)");
			}
			@Nullable ChannelWhisper channel = senderState.getWhisperChannel(recipientUuid);
			if (channel == null) {
				try {
					channel = new ChannelWhisper(sendingPlayer.getUniqueId(), recipientUuid);
				} catch (Exception e) {
					CommandUtils.fail(sender, "Could not create new whisper channel: Could not connect to RabbitMQ.");
				}
				ChannelManager.registerNewChannel(sender, channel);
				senderState.setWhisperChannel(recipientUuid, channel);
			}

			channel.sendMessage(sendingPlayer, message);
		}
		return 1;
	}

	private static int runCommandReplySet(CommandSender sender) throws WrapperCommandSyntaxException {
		if (!(sender instanceof Player sendingPlayer)) {
			CommandUtils.fail(sender, "This command can only be run as a player.");
		} else {
			PlayerState senderState = PlayerStateManager.getPlayerState(sendingPlayer);
			if (senderState == null) {
				sendingPlayer.sendMessage(MessagingUtils.noChatState(sendingPlayer));
				return 0;
			}
			ChannelWhisper channel = senderState.getLastWhisperChannel();
			if (channel == null) {
				CommandUtils.fail(sender, "No one has sent you a whisper yet.");
			}

			senderState.setActiveChannel(channel);
			sender.sendMessage(Component.text("You are now typing replies to the last person to whisper to you.", NamedTextColor.GRAY));
		}
		return 1;
	}

	private static int runCommandReplySay(CommandSender sender, String message) throws WrapperCommandSyntaxException {
		if (!(sender instanceof Player sendingPlayer)) {
			CommandUtils.fail(sender, "This command can only be run as a player.");
		} else {
			PlayerState senderState = PlayerStateManager.getPlayerState(sendingPlayer);
			if (senderState == null) {
				CommandUtils.fail(sendingPlayer, MessagingUtils.noChatStateStr(sendingPlayer));
			} else if (senderState.isPaused()) {
				CommandUtils.fail(sendingPlayer, "You cannot chat with chat paused (/chat unpause)");
			}
			ChannelWhisper channel = senderState.getLastWhisperChannel();
			if (channel == null) {
				CommandUtils.fail(sender, "No one has sent you a whisper yet.");
			}

			channel.sendMessage(sendingPlayer, message);
		}
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
		return getName(mParticipants);
	}

	public static String getName(List<UUID> participants) {
		participants = new ArrayList<>(participants);
		Collections.sort(participants);
		StringBuilder name = new StringBuilder("Whisper");
		for (UUID participant : participants) {
			name.append("_").append(participant.toString());
		}
		return name.toString();
	}

	public static String getAltName(List<UUID> participants) {
		participants = new ArrayList<>(participants);
		Collections.sort(participants);
		Collections.reverse(participants);
		StringBuilder name = new StringBuilder("Whisper");
		for (UUID participant : participants) {
			name.append("_").append(participant.toString());
		}
		return name.toString();
	}

	public @Nullable TextColor color() {
		return null;
	}

	public void color(CommandSender sender, @Nullable TextColor color) throws WrapperCommandSyntaxException {
		CommandUtils.fail(sender,"Whisper channels do not support custom text colors.");
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
		return false;
	}

	public boolean mayChat(CommandSender sender) {
		if (!CommandUtils.hasPermission(sender, "networkchat.say")) {
			return false;
		}
		if (!CommandUtils.hasPermission(sender, "networkchat.say.whisper")) {
			return false;
		}

		if (!(sender instanceof Player player)) {
			return false;
		} else {
			ChannelAccess playerAccess = mPlayerAccess.get(player.getUniqueId());
			if (playerAccess == null) {
				if (mDefaultAccess.mayChat() != null && !mDefaultAccess.mayChat()) {
					return false;
				}
			} else if (playerAccess.mayChat() != null && !playerAccess.mayChat()) {
				return false;
			}
		}

		return mParticipants.contains(player.getUniqueId());
	}

	public boolean mayListen(CommandSender sender) {
		if (!CommandUtils.hasPermission(sender, "networkchat.see")) {
			return false;
		}
		if (!CommandUtils.hasPermission(sender, "networkchat.see.whisper")) {
			return false;
		}

		if (!(sender instanceof Player)) {
			return false;
		}

		UUID playerId = ((Player) sender).getUniqueId();

		ChannelAccess playerAccess = mPlayerAccess.get(playerId);
		if (playerAccess == null) {
			if (mDefaultAccess.mayListen() != null && !mDefaultAccess.mayListen()) {
				return false;
			}
		} else if (playerAccess.mayListen() != null && !playerAccess.mayListen()) {
			return false;
		}

		return mParticipants.contains(playerId);
	}

	public void sendMessage(CommandSender sender, String messageText) throws WrapperCommandSyntaxException {
		if (!(sender instanceof Player)) {
			CommandUtils.fail(sender, "Only players may whisper.");
		}

		if (!CommandUtils.hasPermission(sender, "networkchat.say")) {
			CommandUtils.fail(sender, "You do not have permission to chat.");
		}
		if (!CommandUtils.hasPermission(sender, "networkchat.say.whisper")) {
			CommandUtils.fail(sender, "You do not have permission to whisper.");
		}

		if (!mayChat(sender)) {
			CommandUtils.fail(sender, "You do not have permission to chat in this channel.");
		}

		UUID senderId = ((Player) sender).getUniqueId();
		UUID receiverId = getOtherParticipant(senderId);

		if (!RemotePlayerManager.isPlayerVisible(receiverId)) {
			sender.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
		}

		JsonObject extraData = new JsonObject();
		extraData.addProperty("receiver", receiverId.toString());

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
		showMessage(Bukkit.getConsoleSender(), message);

		JsonObject extraData = message.getExtraData();
		UUID receiverUuid;
		try {
			receiverUuid = UUID.fromString(extraData.getAsJsonPrimitive("receiver").getAsString());
		} catch (Exception e) {
			assert NetworkChatPlugin.getInstance() != null;
			NetworkChatPlugin.getInstance().getLogger().warning("Could not get receiver from Message; reason: " + e.getMessage());
			return;
		}
		UUID senderUuid = message.getSenderId();
		distributeMessageToPlayer(receiverUuid, senderUuid, message);
		if (!senderUuid.equals(receiverUuid)) {
			distributeMessageToPlayer(senderUuid, receiverUuid, message);
		}
	}

	private void distributeMessageToPlayer(UUID playerId, UUID otherId, Message message) {
		PlayerState state = PlayerStateManager.getPlayerState(playerId);
		if (state == null) {
			assert NetworkChatPlugin.getInstance() != null;
			NetworkChatPlugin.getInstance().getLogger().finer("Receiver not on this shard.");
			return;
		}
		state.setWhisperChannel(otherId, this);

		ChannelAccess playerAccess = mPlayerAccess.get(playerId);
		if (playerAccess == null) {
			if (mDefaultAccess.mayListen() != null && !mDefaultAccess.mayListen()) {
				return;
			}
		} else if (playerAccess.mayListen() != null && !playerAccess.mayListen()) {
			return;
		}

		if (state.isListening(this)) {
			// This accounts for players who have paused their chat
			state.receiveMessage(message);
		}
	}

	protected Component shownMessage(CommandSender recipient, Message message) {
		JsonObject extraData = message.getExtraData();
		Component receiverComp;
		try {
			UUID receiverUuid = UUID.fromString(extraData.getAsJsonPrimitive("receiver").getAsString());
			receiverComp = RemotePlayerManager.getPlayerComponent(receiverUuid);
		} catch (Exception e) {
			assert NetworkChatPlugin.getInstance() != null;
			NetworkChatPlugin.getInstance().getLogger().warning("Could not get receiver from Message; reason: " + e.getMessage());
			receiverComp = Component.text("ErrorLoadingName");
		}

		TextColor channelColor = NetworkChatPlugin.messageColor(CHANNEL_CLASS_ID);
		String prefix = NetworkChatPlugin.messageFormat(CHANNEL_CLASS_ID)
			.replace("<channel_color>", MessagingUtils.colorToMiniMessage(channelColor)) + " ";

		return Component.empty()
			.append(MessagingUtils.SENDER_FMT_MINIMESSAGE.deserialize(prefix, TemplateResolver.templates(Template.template("sender", message.getSenderComponent()),
				Template.template("receiver", receiverComp))))
			.append(Component.empty().color(channelColor).append(message.getMessage()));
	}

	protected void showMessage(CommandSender recipient, Message message) {
		UUID senderUuid = message.getSenderId();
		recipient.sendMessage(message.getSenderIdentity(), shownMessage(recipient, message), message.getMessageType());
		if (recipient instanceof Player player && !((Player) recipient).getUniqueId().equals(senderUuid)) {
			@Nullable PlayerState playerState = PlayerStateManager.getPlayerState(player);
			if (playerState == null) {
				player.sendMessage(MessagingUtils.noChatState(player));
				return;
			}
			playerState.playMessageSound(message);
		}
	}
}
