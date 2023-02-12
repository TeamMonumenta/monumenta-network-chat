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
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// A channel for invited players
public class ChannelParty extends Channel implements ChannelInviteOnly {
	public static final String CHANNEL_CLASS_ID = "party";

	protected final Set<UUID> mParticipants = new TreeSet<>();

	private ChannelParty(UUID channelId, Instant lastUpdate, String name) {
		super(channelId, lastUpdate, name);
	}

	public ChannelParty(String name) {
		this(UUID.randomUUID(), Instant.now(), name);
	}

	protected ChannelParty(JsonObject channelJson) throws Exception {
		super(channelJson);
		participantsFromJson(mParticipants, channelJson);
	}

	@Override
	public JsonObject toJson() {
		JsonObject result = super.toJson();
		participantsToJson(result, mParticipants);
		return result;
	}

	public static void registerNewChannelCommands(String[] baseCommands, List<Argument<?>> prefixArguments) {
		List<Argument<?>> arguments;

		for (String baseCommand : baseCommands) {
			arguments = new ArrayList<>(prefixArguments);
			// last element of prefixArguments is channel ID
			arguments.add(new MultiLiteralArgument(CHANNEL_CLASS_ID));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.new.party")) {
						throw CommandUtils.fail(sender, "You do not have permission to create party channels.");
					}

					String channelName = (String)args[prefixArguments.size() - 1];
					ChannelParty newChannel;

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelParty(channelName);
					} catch (Exception e) {
						throw CommandUtils.fail(sender, "Could not create new channel " + channelName + ": Could not connect to RabbitMQ.");
					}
					// Add the sender to the party if they're a player
					CommandSender callee = CommandUtils.getCallee(sender);
					if (callee instanceof Player) {
						newChannel.addPlayer(((Player) callee).getUniqueId(), false);
					}
					// Throws an exception if the channel already exists, failing the command.
					ChannelManager.registerNewChannel(sender, newChannel);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument(CHANNEL_CLASS_ID));
			arguments.add(new StringArgument("Channel Name").replaceSuggestions(ChannelManager.SUGGESTIONS_PARTY_CHANNEL_NAMES));
			arguments.add(new MultiLiteralArgument("invite"));
			arguments.add(new StringArgument("Player").replaceSuggestions(RemotePlayerManager.SUGGESTIONS_VISIBLE_PLAYER_NAMES));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					String channelName = (String)args[1];
					Channel ch = ChannelManager.getChannel(channelName);
					if (ch == null) {
						throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}
					if (!(ch instanceof ChannelParty channel)) {
						throw CommandUtils.fail(sender, "Channel " + channelName + " is not a party channel.");
					} else {
						if (!channel.isParticipant(sender)) {
							throw CommandUtils.fail(sender, "You are not a participant of " + channelName + ".");
						}

						String playerName = (String) args[3];
						UUID playerId = MonumentaRedisSyncAPI.cachedNameToUuid(playerName);
						if (playerId == null) {
							throw CommandUtils.fail(sender, "No such player " + playerName + ".");
						}

						sender.sendMessage(Component.text("Added " + playerName + " to " + channelName + ".", NamedTextColor.GRAY));
						channel.addPlayer(playerId);
					}
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument(CHANNEL_CLASS_ID));
			arguments.add(new StringArgument("Channel Name").replaceSuggestions(ChannelManager.SUGGESTIONS_PARTY_CHANNEL_NAMES));
			arguments.add(new MultiLiteralArgument("kick"));
			arguments.add(new StringArgument("Player").replaceSuggestions(RemotePlayerManager.SUGGESTIONS_VISIBLE_PLAYER_NAMES));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					String channelName = (String)args[1];
					Channel ch = ChannelManager.getChannel(channelName);
					if (ch == null) {
						throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}
					if (!(ch instanceof ChannelParty channel)) {
						throw CommandUtils.fail(sender, "Channel " + channelName + " is not a party channel.");
					} else {
						if (!channel.isParticipant(sender)) {
							throw CommandUtils.fail(sender, "You are not a participant of " + channelName + ".");
						}

						String playerName = (String) args[3];
						UUID playerId = MonumentaRedisSyncAPI.cachedNameToUuid(playerName);
						if (playerId == null) {
							throw CommandUtils.fail(sender, "No such player " + playerName + ".");
						}

						channel.removePlayer(playerId);
						sender.sendMessage(Component.text("Kicked " + playerName + " from " + channelName + ".", NamedTextColor.GRAY));
					}
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument(CHANNEL_CLASS_ID));
			arguments.add(new StringArgument("Channel Name").replaceSuggestions(ChannelManager.SUGGESTIONS_PARTY_CHANNEL_NAMES));
			arguments.add(new MultiLiteralArgument("leave"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					String channelId = (String)args[1];
					Channel ch = ChannelManager.getChannel(channelId);
					if (ch == null) {
						throw CommandUtils.fail(sender, "No such channel " + channelId + ".");
					}
					if (!(ch instanceof ChannelParty channel)) {
						throw CommandUtils.fail(sender, "Channel " + channelId + " is not a party channel.");
					} else {
						if (!channel.isParticipant(sender)) {
							throw CommandUtils.fail(sender, "You are not a participant of " + channelId + ".");
						}
						Player player = (Player) sender;

						channel.removePlayer(player.getUniqueId());
						sender.sendMessage(Component.text("You have left " + channelId + ".", NamedTextColor.GRAY));
					}
				})
				.register();
		}
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
	public boolean mayManage(CommandSender sender) {
		return isParticipantOrModerator(sender);
	}

	@Override
	public boolean mayChat(CommandSender sender) {
		if (!CommandUtils.hasPermission(sender, "networkchat.say.party")) {
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
				return !Boolean.FALSE.equals(mDefaultAccess.mayChat());
			} else {
				return !Boolean.FALSE.equals(playerAccess.mayChat());
			}
		}
	}

	@Override
	public boolean mayListen(CommandSender sender) {
		if (!CommandUtils.hasPermission(sender, "networkchat.see.party")) {
			return false;
		}

		CommandSender callee = CommandUtils.getCallee(sender);
		if (!isParticipant(callee)) {
			return false;
		}
		if (!(callee instanceof Player player)) {
			return true;
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
		if (!CommandUtils.hasPermission(sender, "networkchat.say.party")) {
			throw CommandUtils.fail(sender, "You do not have permission to talk in party chat.");
		}

		if (!mayChat(sender)) {
			throw CommandUtils.fail(sender, "You do not have permission to chat in this channel.");
		}

		WrapperCommandSyntaxException notListeningEx = isListeningCheck(sender);
		if (notListeningEx != null) {
			throw notListeningEx;
		}

		if (messageText.contains("@")) {
			if (messageText.contains("@everyone") && !CommandUtils.hasPermission(sender, "networkchat.ping.everyone")) {
				throw CommandUtils.fail(sender, "You do not have permission to ping everyone in this channel.");
			} else if (!CommandUtils.hasPermission(sender, "networkchat.ping.player") && MessagingUtils.containsPlayerMention(messageText)) {
				throw CommandUtils.fail(sender, "You do not have permission to ping a player in this channel.");
			}
		}

		@Nullable Message message = Message.createMessage(this, MessageType.CHAT, sender, null, messageText);
		if (message == null) {
			return;
		}

		try {
			MessageManager.getInstance().broadcastMessage(message);
		} catch (Exception e) {
			MMLog.warning("Could not send message; RabbitMQ is not responding.", e);
			throw CommandUtils.fail(sender, "Could not send message; RabbitMQ is not responding.");
		}
	}

	@Override
	public void distributeMessage(Message message) {
		showMessage(Bukkit.getConsoleSender(), message);
		for (Map.Entry<UUID, PlayerState> playerStateEntry : PlayerStateManager.getPlayerStates().entrySet()) {
			PlayerState state = playerStateEntry.getValue();
			Player player = state.getPlayer();
			if (player == null || !mayListen(player)) {
				continue;
			}

			if (state.isListening(this)) {
				// This accounts for players who have paused their chat
				state.receiveMessage(message);
			}
		}
	}

	@Override
	public Component shownMessage(CommandSender recipient, Message message) {
		TextColor channelColor;
		if (mMessageColor != null) {
			channelColor = mMessageColor;
		} else {
			channelColor = NetworkChatPlugin.messageColor(CHANNEL_CLASS_ID);
		}
		String prefix = NetworkChatPlugin.messageFormat(CHANNEL_CLASS_ID);
		if (prefix == null) {
			prefix = "";
		}
		prefix = prefix
			.replace("<message_gui_cmd>", message.getGuiCommand())
			.replace("<channel_color>", MessagingUtils.colorToMiniMessage(channelColor)) + " ";

		return Component.empty()
			.append(MessagingUtils.SENDER_FMT_MINIMESSAGE.deserialize(prefix,
				Placeholder.unparsed("channel_name", mName),
				Placeholder.component("sender", message.getSenderComponent())))
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
