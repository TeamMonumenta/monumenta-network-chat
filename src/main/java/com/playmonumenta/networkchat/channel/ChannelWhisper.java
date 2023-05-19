package com.playmonumenta.networkchat.channel;

import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.Message;
import com.playmonumenta.networkchat.MessageManager;
import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.RemotePlayerManager;
import com.playmonumenta.networkchat.channel.interfaces.ChannelInviteOnly;
import com.playmonumenta.networkchat.channel.property.ChannelAccess;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// A channel visible to all shards
public class ChannelWhisper extends Channel implements ChannelInviteOnly {
	public static final String CHANNEL_CLASS_ID = "whisper";
	private static final String[] WHISPER_COMMANDS = {"msg", "tell", "w"};
	private static final String REPLY_COMMAND = "r";

	protected final Set<UUID> mParticipants = new TreeSet<>();

	public ChannelWhisper(UUID from, UUID to) {
		this(UUID.randomUUID(), Instant.now(), List.of(from, to));
	}

	private ChannelWhisper(UUID channelId, Instant lastUpdate, List<UUID> participants) {
		super(channelId, lastUpdate, getName(participants));
		mParticipants.addAll(participants);
		mDefaultSettings.addSound(Sound.ENTITY_PLAYER_LEVELUP, 1, 0.5f);
	}

	protected ChannelWhisper(JsonObject channelJson) throws Exception {
		super(channelJson);
		participantsFromJson(mParticipants, channelJson);
	}

	@Override
	public JsonObject toJson() {
		JsonObject result = super.toJson();
		participantsToJson(result, mParticipants);
		return result;
	}

	public static void registerNewChannelCommands() {
		// Setting up new whisper channels will be done via /msg, /tell, /w, and similar,
		// not through /chat new Blah whisper. The provided arguments are ignored.
		List<Argument<?>> arguments = new ArrayList<>();

		for (String command : WHISPER_COMMANDS) {
			CommandAPI.unregister(command);

			arguments.clear();
			arguments.add(new StringArgument("recipient").replaceSuggestions(RemotePlayerManager.SUGGESTIONS_VISIBLE_PLAYER_NAMES));
			new CommandAPICommand(command)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					return runCommandSet(sender, (String)args[0]);
				})
				.register();

			arguments.clear();
			arguments.add(new StringArgument("recipient").replaceSuggestions(RemotePlayerManager.SUGGESTIONS_VISIBLE_PLAYER_NAMES));
			arguments.add(new GreedyStringArgument("message"));
			new CommandAPICommand(command)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					return runCommandSay(sender, (String)args[0], (String)args[1]);
				})
				.register();
		}

		new CommandAPICommand(REPLY_COMMAND)
			.executesNative((sender, args) -> {
				return runCommandReplySet(sender);
			})
			.register();

		arguments.clear();
		arguments.add(new GreedyStringArgument("message"));
		new CommandAPICommand(REPLY_COMMAND)
			.withArguments(arguments)
			.executesNative((sender, args) -> {
				return runCommandReplySay(sender, (String)args[0]);
			})
			.register();
	}

	private static int runCommandSet(CommandSender sender, String recipientName) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player sendingPlayer)) {
			throw CommandUtils.fail(sender, "This command can only be run as a player.");
		} else {
			UUID recipientUuid = RemotePlayerManager.getPlayerId(recipientName);
			if (recipientUuid == null) {
				throw CommandUtils.fail(sender, recipientName + " is not online.");
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
					throw CommandUtils.fail(sender, "Could not create new whisper channel: Could not connect to RabbitMQ.");
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
		@Nullable PlayerState senderState;
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player sendingPlayer)) {
			throw CommandUtils.fail(sender, "This command can only be run as a player.");
		} else {
			UUID recipientUuid = RemotePlayerManager.getPlayerId(recipientName);
			if (recipientUuid == null) {
				throw CommandUtils.fail(sender, recipientName + " is not online.");
			}

			senderState = PlayerStateManager.getPlayerState(sendingPlayer);
			if (senderState == null) {
				throw CommandUtils.fail(sendingPlayer, MessagingUtils.noChatStateStr(sendingPlayer));
			} else if (senderState.isPaused()) {
				throw CommandUtils.fail(sendingPlayer, "You cannot chat with chat paused (/chat unpause)");
			}
			@Nullable ChannelWhisper channel = senderState.getWhisperChannel(recipientUuid);
			if (channel == null) {
				try {
					channel = new ChannelWhisper(sendingPlayer.getUniqueId(), recipientUuid);
				} catch (Exception e) {
					throw CommandUtils.fail(sender, "Could not create new whisper channel: Could not connect to RabbitMQ.");
				}
				ChannelManager.registerNewChannel(sender, channel);
				senderState.setWhisperChannel(recipientUuid, channel);
			}

			senderState.joinChannel(channel);
			channel.sendMessage(sendingPlayer, message);
		}
		return 1;
	}

	private static int runCommandReplySet(CommandSender sender) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player sendingPlayer)) {
			throw CommandUtils.fail(sender, "This command can only be run as a player.");
		} else {
			PlayerState senderState = PlayerStateManager.getPlayerState(sendingPlayer);
			if (senderState == null) {
				sendingPlayer.sendMessage(MessagingUtils.noChatState(sendingPlayer));
				return 0;
			}
			ChannelWhisper channel = senderState.getLastWhisperChannel();
			if (channel == null) {
				throw CommandUtils.fail(sender, "No one has sent you a whisper yet.");
			}

			senderState.setActiveChannel(channel);
			callee.sendMessage(Component.text("You are now typing replies to the last person to whisper to you.", NamedTextColor.GRAY));
		}
		return 1;
	}

	private static int runCommandReplySay(CommandSender sender, String message) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player sendingPlayer)) {
			throw CommandUtils.fail(sender, "This command can only be run as a player.");
		} else {
			PlayerState senderState = PlayerStateManager.getPlayerState(sendingPlayer);
			if (senderState == null) {
				throw CommandUtils.fail(sendingPlayer, MessagingUtils.noChatStateStr(sendingPlayer));
			} else if (senderState.isPaused()) {
				throw CommandUtils.fail(sendingPlayer, "You cannot chat with chat paused (/chat unpause)");
			}
			ChannelWhisper channel = senderState.getLastWhisperChannel();
			if (channel == null) {
				throw CommandUtils.fail(sender, "No one has sent you a whisper yet.");
			}

			channel.sendMessage(sendingPlayer, message);
		}
		return 1;
	}

	@Override
	public String getClassId() {
		return CHANNEL_CLASS_ID;
	}

	@Override
	public void addPlayer(UUID playerId, boolean save) {
		mParticipants.add(playerId);
		PlayerState state = PlayerStateManager.getPlayerState(playerId);
		if (state != null) {
			if (!state.isWatchingChannelId(mId)) {
				state.joinChannel(this);
			}
		}
		if (save) {
			ChannelManager.saveChannel(this);
		}
	}

	@Override
	public void removePlayer(UUID playerId) {
		mParticipants.remove(playerId);
		if (mParticipants.isEmpty()) {
			try {
				ChannelManager.deleteChannel(getName());
			} catch (Exception e) {
				MMLog.info("Failed to delete empty channel " + getName());
			}
		} else {
			ChannelManager.saveChannel(this);
		}
	}

	@Override
	public boolean isParticipant(UUID playerId) {
		return mParticipants.contains(playerId);
	}

	@Override
	public List<UUID> getParticipantIds() {
		return new ArrayList<>(mParticipants);
	}

	@Override
	public void setName(String name) throws WrapperCommandSyntaxException {
		throw CommandAPI.failWithString("Whisper channels may not be named.");
	}

	@Override
	public String getName() {
		return getName(mParticipants);
	}

	public static String getName(Collection<UUID> participants) {
		List<UUID> participantsList = new ArrayList<>(participants);
		if (participantsList.size() == 1) {
			participantsList.add(participantsList.get(0));
		} else {
			Collections.sort(participantsList);
		}
		StringBuilder name = new StringBuilder("Whisper");
		for (UUID participant : participantsList) {
			name.append("_").append(participant.toString());
		}
		return name.toString();
	}

	public static String getAltName(List<UUID> participants) {
		List<UUID> participantsList = new ArrayList<>(participants);
		if (participantsList.size() == 1) {
			participantsList.add(participantsList.get(0));
		} else {
			Collections.sort(participants);
			Collections.reverse(participants);
		}
		StringBuilder name = new StringBuilder("Whisper");
		for (UUID participant : participants) {
			name.append("_").append(participant.toString());
		}
		return name.toString();
	}

	@Override
	public @Nullable TextColor color() {
		return null;
	}

	@Override
	public void color(CommandSender sender, @Nullable TextColor color) throws WrapperCommandSyntaxException {
		throw CommandUtils.fail(sender, "Whisper channels do not support custom text colors.");
	}

	public UUID getOtherParticipant(UUID from) {
		List<UUID> participantsList = new ArrayList<>(mParticipants);
		if (participantsList.size() == 1) {
			return participantsList.get(0);
		} else if (participantsList.get(0).equals(from)) {
			return participantsList.get(1);
		} else {
			return participantsList.get(0);
		}
	}

	@Override
	public boolean shouldAutoJoin(PlayerState state) {
		return false;
	}

	@Override
	public boolean mayManage(CommandSender sender) {
		return isParticipantOrModerator(sender);
	}

	@Override
	public boolean mayChat(CommandSender sender) {
		if (!CommandUtils.hasPermission(sender, "networkchat.say.whisper")) {
			return false;
		}

		CommandSender callee = CommandUtils.getCallee(sender);
		if (!isParticipant(callee)) {
			return false;
		}
		if (!(callee instanceof Player player)) {
			return false;
		} else {
			ChannelAccess playerAccess = mPlayerAccess.get(player.getUniqueId());
			if (playerAccess == null) {
				return !Boolean.FALSE.equals(mDefaultAccess.mayChat());
			} else {
				return !Boolean.FALSE.equals(playerAccess.mayChat());
			}
		}
	}

	@Override
	public boolean mayListen(CommandSender sender) {
		if (!CommandUtils.hasPermission(sender, "networkchat.see.whisper")) {
			return false;
		}

		CommandSender callee = CommandUtils.getCallee(sender);
		if (!isParticipant(callee)) {
			return false;
		}
		if (!(callee instanceof Player player)) {
			return false;
		} else {
			UUID playerId = player.getUniqueId();
			ChannelAccess playerAccess = mPlayerAccess.get(playerId);
			if (playerAccess == null) {
				return !Boolean.FALSE.equals(mDefaultAccess.mayListen());
			} else {
				return !Boolean.FALSE.equals(playerAccess.mayListen());
			}
		}
	}

	@Override
	public void sendMessage(CommandSender sender, String messageText) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player)) {
			throw CommandUtils.fail(sender, "Only players may whisper.");
		}

		if (!CommandUtils.hasPermission(sender, "networkchat.say.whisper")) {
			throw CommandUtils.fail(sender, "You do not have permission to whisper.");
		}

		if (!mayChat(sender)) {
			throw CommandUtils.fail(sender, "You do not have permission to chat in this channel.");
		}

		WrapperCommandSyntaxException notListeningEx = isListeningCheck(sender);
		if (notListeningEx != null) {
			throw notListeningEx;
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

		MessageManager.getInstance().broadcastMessage(message);
	}

	@Override
	public void distributeMessage(Message message) {
		showMessage(Bukkit.getConsoleSender(), message);

		JsonObject extraData = message.getExtraData();
		if (extraData == null) {
			MMLog.warning("No receiver specified for whisper message");
			return;
		}
		UUID receiverUuid;
		try {
			receiverUuid = UUID.fromString(extraData.getAsJsonPrimitive("receiver").getAsString());
		} catch (Exception e) {
			MMLog.warning("Could not get receiver from Message; reason: " + e.getMessage());
			return;
		}
		UUID senderUuid = message.getSenderId();
		if (senderUuid == null) {
			senderUuid = new UUID(0L, 0L);
		}
		distributeMessageToPlayer(receiverUuid, senderUuid, message);
		if (!senderUuid.equals(receiverUuid)) {
			distributeMessageToPlayer(senderUuid, receiverUuid, message);
		}
	}

	private void distributeMessageToPlayer(UUID playerId, UUID otherId, Message message) {
		PlayerState state = PlayerStateManager.getPlayerState(playerId);
		if (state == null) {
			MMLog.finer("Receiver not on this shard.");
			return;
		}
		state.setWhisperChannel(otherId, this);

		ChannelAccess playerAccess = mPlayerAccess.get(playerId);
		if (playerAccess == null) {
			if (Boolean.FALSE.equals(mDefaultAccess.mayListen())) {
				return;
			}
		} else if (Boolean.FALSE.equals(playerAccess.mayListen())) {
			return;
		}

		if (state.isListening(this)) {
			// This accounts for players who have paused their chat
			state.receiveMessage(message);
		}
	}

	@Override
	public Component shownMessage(CommandSender recipient, Message message) {
		JsonObject extraData = message.getExtraData();
		if (extraData == null) {
			MMLog.warning("No receiver specified for whisper message");
			return Component.empty();
		}
		Component receiverComp;
		try {
			UUID receiverUuid = UUID.fromString(extraData.getAsJsonPrimitive("receiver").getAsString());
			receiverComp = RemotePlayerManager.getPlayerComponent(receiverUuid);
		} catch (Exception e) {
			MMLog.warning("Could not get receiver from Message; reason: " + e.getMessage());
			receiverComp = Component.text("ErrorLoadingName");
		}

		TextColor channelColor = NetworkChatPlugin.messageColor(CHANNEL_CLASS_ID);
		String prefix = NetworkChatPlugin.messageFormat(CHANNEL_CLASS_ID);
		if (prefix == null) {
			prefix = "";
		}
		prefix = prefix
			.replace("<channel_color>", MessagingUtils.colorToMiniMessage(channelColor)) + " ";

		return Component.empty()
			.append(MessagingUtils.SENDER_FMT_MINIMESSAGE.deserialize(prefix,
				Placeholder.component("sender", message.getSenderComponent()),
				Placeholder.component("receiver", receiverComp)))
			.append(Component.empty().color(channelColor).append(message.getMessage()));
	}

	@Override
	public void showMessage(CommandSender recipient, Message message) {
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
}
