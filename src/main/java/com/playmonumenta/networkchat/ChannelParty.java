package com.playmonumenta.networkchat;

import java.time.Instant;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
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
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.Template;
import net.kyori.adventure.text.minimessage.transformation.Transformation;
import net.kyori.adventure.text.minimessage.transformation.TransformationType;
import net.kyori.adventure.text.minimessage.markdown.DiscordFlavor;

// A channel for invited players
public class ChannelParty extends Channel implements ChannelInviteOnly {
	public static final String CHANNEL_CLASS_ID = "party";

	private UUID mId;
	private Instant mLastUpdate;
	private String mName;
	private Set<UUID> mParticipants;
	private ChannelSettings mDefaultSettings;
	private ChannelPerms mDefaultPerms;
	private Map<UUID, ChannelPerms> mPlayerPerms;

	private ChannelParty(UUID channelId, Instant lastUpdate, String name) {
		mId = channelId;
		mLastUpdate = lastUpdate;
		mName = name;
		mParticipants = new HashSet<>();

		mDefaultSettings = new ChannelSettings();
		mDefaultPerms = new ChannelPerms();
		mPlayerPerms = new HashMap<>();
	}

	public ChannelParty(String name) {
		mLastUpdate = Instant.now();
		mId = UUID.randomUUID();
		mName = name;
		mParticipants = new HashSet<>();

		mDefaultSettings = new ChannelSettings();
		mDefaultPerms = new ChannelPerms();
		mPlayerPerms = new HashMap<>();
	}

	protected static Channel fromJsonInternal(JsonObject channelJson) throws Exception {
		String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();
		if (channelClassId == null || !channelClassId.equals(CHANNEL_CLASS_ID)) {
			throw new Exception("Cannot create ChannelParty from channel ID " + channelClassId);
		}
		String uuidString = channelJson.getAsJsonPrimitive("uuid").getAsString();
		UUID channelId = UUID.fromString(uuidString);
		Instant lastUpdate = Instant.now();
		if (channelJson.get("lastUpdate") != null) {
			lastUpdate = Instant.ofEpochMilli(channelJson.get("lastUpdate").getAsLong());
		}
		String name = channelJson.getAsJsonPrimitive("name").getAsString();

		ChannelParty channel = new ChannelParty(channelId, lastUpdate, name);

		JsonArray participantsJson = channelJson.getAsJsonArray("participants");
		Set<UUID> participants = new HashSet<>();
		for (JsonElement participantJson : participantsJson) {
			channel.addPlayer(UUID.fromString(participantJson.getAsString()), false);
		}

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
		for (UUID playerId : mParticipants) {
			participantsJson.add(playerId.toString());
		}

		JsonObject result = new JsonObject();
		result.addProperty("type", CHANNEL_CLASS_ID);
		result.addProperty("uuid", mId.toString());
		result.addProperty("lastUpdate", mLastUpdate.toEpochMilli());
		result.addProperty("name", mName);
		result.add("participants", participantsJson);
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
					ChannelParty newChannel = null;
					if (!sender.hasPermission("networkchat.new.party")) {
						CommandAPI.fail("You do not have permission to make new party channels.");
					}

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelParty(channelName);
					} catch (Exception e) {
						CommandAPI.fail("Could not create new channel " + channelName + ": Could not connect to RabbitMQ.");
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
			// last element of prefixArguments is channel ID
			arguments.add(new MultiLiteralArgument(CHANNEL_CLASS_ID));
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getPartyChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument("invite"));
			arguments.add(new StringArgument("Player").overrideSuggestions((sender) -> {
				return RemotePlayerManager.onlinePlayerNames().toArray(new String[0]);
			}));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelId = (String)args[1];
					Channel ch = ChannelManager.getChannel(channelId);
					if (ch == null) {
						CommandAPI.fail("No such channel " + channelId + ".");
					}
					if (!(ch instanceof ChannelParty)) {
						CommandAPI.fail("Channel " + channelId + " is not a party channel.");
					}
					ChannelParty channel = (ChannelParty) ch;

					if (!channel.isParticipant(sender)) {
						CommandAPI.fail("You are not a participant of " + channelId + ".");
					}

					String playerName = (String)args[3];
					UUID playerId = MonumentaRedisSyncAPI.cachedNameToUuid(playerName);
					if (playerId == null) {
						CommandAPI.fail("No such player " + playerName + ".");
					}

					sender.sendMessage(Component.text("Added " + playerName + " to " + channelId + ".", NamedTextColor.GRAY));
					channel.addPlayer(playerId);
				})
				.register();

			arguments.clear();
			// last element of prefixArguments is channel ID
			arguments.add(new MultiLiteralArgument(CHANNEL_CLASS_ID));
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getPartyChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument("kick"));
			arguments.add(new StringArgument("Player").overrideSuggestions((sender) -> {
				return RemotePlayerManager.onlinePlayerNames().toArray(new String[0]);
			}));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelId = (String)args[1];
					Channel ch = ChannelManager.getChannel(channelId);
					if (ch == null) {
						CommandAPI.fail("No such channel " + channelId + ".");
					}
					if (!(ch instanceof ChannelParty)) {
						CommandAPI.fail("Channel " + channelId + " is not a party channel.");
					}
					ChannelParty channel = (ChannelParty) ch;

					if (!channel.isParticipant(sender)) {
						CommandAPI.fail("You are not a participant of " + channelId + ".");
					}

					String playerName = (String)args[3];
					UUID playerId = MonumentaRedisSyncAPI.cachedNameToUuid(playerName);
					if (playerId == null) {
						CommandAPI.fail("No such player " + playerName + ".");
					}

					// TODO Display message and make player unwatch channel.
					channel.removePlayer(playerId);
					sender.sendMessage(Component.text("Kicked " + playerName + " from " + channelId + ".", NamedTextColor.GRAY));
				})
				.register();

			arguments.clear();
			// last element of prefixArguments is channel ID
			arguments.add(new MultiLiteralArgument(CHANNEL_CLASS_ID));
			arguments.add(new StringArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getPartyChannelNames(sender).toArray(new String[0]);
			}));
			arguments.add(new MultiLiteralArgument("leave"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelId = (String)args[1];
					Channel ch = ChannelManager.getChannel(channelId);
					if (ch == null) {
						CommandAPI.fail("No such channel " + channelId + ".");
					}
					if (!(ch instanceof ChannelParty)) {
						CommandAPI.fail("Channel " + channelId + " is not a party channel.");
					}
					ChannelParty channel = (ChannelParty) ch;

					if (!channel.isParticipant(sender)) {
						CommandAPI.fail("You are not a participant of " + channelId + ".");
					}
					Player player = (Player) sender;

					// TODO Make player unwatch channel
					channel.removePlayer(player.getUniqueId());
					sender.sendMessage(Component.text("You have left " + channelId + ".", NamedTextColor.GRAY));
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

	public void addPlayer(UUID playerId) {
		addPlayer(playerId, true);
	}

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

	public void removePlayer(UUID playerId) {
		mParticipants.remove(playerId);
		if (mParticipants.isEmpty()) {
			try {
				ChannelManager.deleteChannel(getName());
			} catch (Exception e) {
				NetworkChatPlugin.getInstance().getLogger().info("Failed to delete empty channel " + getName());
			}
		} else {
			ChannelManager.saveChannel(this);
		}
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

	public boolean mayManage(CommandSender sender) {
		if (sender.hasPermission("networkchat.moderator")) {
			return true;
		}

		if (!(sender instanceof Player)) {
			return false;
		}
		Player player = (Player) sender;
		UUID playerId = player.getUniqueId();
		return mParticipants.contains(playerId);
	}

	public boolean mayChat(CommandSender sender) {
		if (!sender.hasPermission("networkchat.say")) {
			return false;
		}
		if (!sender.hasPermission("networkchat.say.party")) {
			return false;
		}

		if (!(sender instanceof Player)) {
			return false;
		}

		Player player = (Player) sender;
		UUID playerId = player.getUniqueId();
		if (!mParticipants.contains(playerId)) {
			return false;
		}
		ChannelPerms playerPerms = mPlayerPerms.get(playerId);
		if (playerPerms == null) {
			if (mDefaultPerms.mayChat() != null && !mDefaultPerms.mayChat()) {
				return false;
			}
		} else if (playerPerms.mayChat() != null && !playerPerms.mayChat()) {
			return false;
		}

		return true;
	}

	public boolean mayListen(CommandSender sender) {
		if (!sender.hasPermission("networkchat.see")) {
			return false;
		}
		if (!sender.hasPermission("networkchat.see.party")) {
			return false;
		}

		if (!(sender instanceof Player)) {
			return true;
		}

		UUID playerId = ((Player) sender).getUniqueId();
		if (!mParticipants.contains(playerId)) {
			return false;
		}

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
		if (!sender.hasPermission("networkchat.say")) {
			CommandAPI.fail("You do not have permission to chat.");
		}
		if (!sender.hasPermission("networkchat.say.party")) {
			CommandAPI.fail("You do not have permission to talk in party chat.");
		}

		if (!mayChat(sender)) {
			CommandAPI.fail("You do not have permission to chat in this channel.");
		}

		Set<TransformationType<? extends Transformation>> allowedTransforms = new HashSet<>();
		if (sender.hasPermission("networkchat.transform.color")) {
			allowedTransforms.add(TransformationType.COLOR);
		}
		if (sender.hasPermission("networkchat.transform.decoration")) {
			allowedTransforms.add(TransformationType.DECORATION);
		}
		if (sender.hasPermission("networkchat.transform.keybind")) {
			allowedTransforms.add(TransformationType.KEYBIND);
		}
		if (sender.hasPermission("networkchat.transform.font")) {
			allowedTransforms.add(TransformationType.FONT);
		}
		if (sender.hasPermission("networkchat.transform.gradient")) {
			allowedTransforms.add(TransformationType.GRADIENT);
		}
		if (sender.hasPermission("networkchat.transform.rainbow")) {
			allowedTransforms.add(TransformationType.RAINBOW);
		}

		Message message = Message.createMessage(this, sender, null, messageText, true, allowedTransforms);

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
		MiniMessage minimessage = MiniMessage.builder()
			.transformation(TransformationType.COLOR)
			.transformation(TransformationType.DECORATION)
			.markdown()
			.markdownFlavor(DiscordFlavor.get())
			.build();

		TextColor channelColor = NetworkChatPlugin.messageColor(CHANNEL_CLASS_ID);
		String prefix = NetworkChatPlugin.messageFormat(CHANNEL_CLASS_ID)
		    .replace("<channel_color>", MessagingUtils.colorToMiniMessage(channelColor)) + " ";

		UUID senderUuid = message.getSenderId();
		Identity senderIdentity;
		if (message.senderIsPlayer()) {
			senderIdentity = Identity.identity(senderUuid);
		} else {
			senderIdentity = Identity.nil();
		}

		Component fullMessage = Component.empty()
		    .append(minimessage.parse(prefix, List.of(Template.of("channel_name", mName),
		        Template.of("sender", message.getSenderComponent()))))
		    .append(Component.empty().color(channelColor).append(message.getMessage()));
		recipient.sendMessage(senderIdentity, fullMessage, MessageType.CHAT);
		if (recipient instanceof Player && !((Player) recipient).getUniqueId().equals(senderUuid)) {
			PlayerStateManager.getPlayerState((Player) recipient).playMessageSound(this);
		}
	}
}
