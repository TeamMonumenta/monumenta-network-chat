package com.playmonumenta.networkchat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.NetworkRelayAPI;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
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

// DEBUG REMOVE WHEN DONE
import java.io.PrintWriter;
import java.io.StringWriter;


// A channel for server announcements
public class ChannelAnnouncement extends Channel {
	public static final String CHANNEL_CLASS_ID = "announcement";

	private UUID mId;
	private Instant mLastUpdate;
	private String mName;
	private ChannelSettings mDefaultSettings;
	private ChannelPerms mDefaultPerms;
	private Map<UUID, ChannelPerms> mPlayerPerms;

	private ChannelAnnouncement(UUID channelId, Instant lastUpdate, String name) {
		mId = channelId;
		mLastUpdate = lastUpdate;
		mName = name;

		mDefaultSettings = new ChannelSettings();
		mDefaultSettings.isListening(true);
		mDefaultSettings.messagesPlaySound(true);

		mDefaultPerms = new ChannelPerms();
		mDefaultPerms.mayChat(true);
		mDefaultPerms.mayListen(true);

		mPlayerPerms = new HashMap<>();
	}

	public ChannelAnnouncement(String name) {
		mLastUpdate = Instant.now();
		mId = UUID.randomUUID();
		mName = name;

		mDefaultSettings = new ChannelSettings();
		mDefaultSettings.isListening(true);
		mDefaultSettings.messagesPlaySound(true);

		mDefaultPerms = new ChannelPerms();
		mDefaultPerms.mayChat(true);
		mDefaultPerms.mayListen(true);

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

		JsonObject result = new JsonObject();
		result.addProperty("type", CHANNEL_CLASS_ID);
		result.addProperty("uuid", mId.toString());
		result.addProperty("lastUpdate", mLastUpdate.toEpochMilli());
		result.addProperty("name", mName);
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
					String channelName = (String)args[prefixArguments.size()-1];
					ChannelAnnouncement newChannel = null;
					// TODO Perms check

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelAnnouncement(channelName);
					} catch (Exception e) {
						CommandAPI.fail("Could not create new channel " + channelName + ": Could not connect to RabbitMQ.");
					}
					// Throws an exception if the channel already exists, failing the command.
					ChannelManager.registerNewChannel(sender, newChannel);
				})
				.register();
		}
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
		// TODO Add permission check for announcement chat.

		if (sender instanceof Player) {
			ChannelPerms playerPerms = mPlayerPerms.get(((Player) sender).getUniqueId());
			if (playerPerms == null) {
				if (mDefaultPerms.mayChat() != null && !mDefaultPerms.mayChat()) {
					CommandAPI.fail("You do not have permission to chat in this channel.");
				}
			} else if (playerPerms.mayChat() != null && !playerPerms.mayChat()) {
				CommandAPI.fail("You do not have permission to chat in this channel.");
			}
		}

		Set<TransformationType> allowedTransforms = new HashSet<>();
		allowedTransforms.add(TransformationType.COLOR);
		allowedTransforms.add(TransformationType.DECORATION);
		allowedTransforms.add(TransformationType.KEYBIND);
		allowedTransforms.add(TransformationType.FONT);
		allowedTransforms.add(TransformationType.GRADIENT);
		allowedTransforms.add(TransformationType.RAINBOW);

		Message message = Message.createMessage(this, sender, null, messageText, true, allowedTransforms);

		try {
			MessageManager.getInstance().broadcastMessage(message);
		} catch (Exception e) {
			sender.sendMessage(Component.text("An exception occured broadcasting your message.", NamedTextColor.RED)
			    .hoverEvent(Component.text(e.getMessage(), NamedTextColor.RED)));

			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			String sStackTrace = sw.toString();
			NetworkChatPlugin.getInstance().getLogger().warning(sStackTrace);

			CommandAPI.fail("Could not send message.");
		}
	}

	public void distributeMessage(Message message) {
		showMessage(Bukkit.getConsoleSender(), message);
		// TODO Check permission to see the message.
		for (Map.Entry<UUID, PlayerState> playerStateEntry : PlayerStateManager.getPlayerStates().entrySet()) {
			UUID playerId = playerStateEntry.getKey();

			ChannelPerms playerPerms = mPlayerPerms.get(playerId);
			if (playerPerms == null) {
				if (mDefaultPerms.mayListen() != null && !mDefaultPerms.mayListen()) {
					continue;
				}
			} else if (playerPerms.mayListen() != null && !playerPerms.mayListen()) {
				continue;
			}

			PlayerState state = playerStateEntry.getValue();

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

		// TODO Use configurable formatting, not hard-coded formatting.
		String prefix = "<gray><hover:show_text:\"<red>Announcement Channel\n<red>TODO Click for gui on channel/message?\">\\<<red><channelName><gray>></hover> ";
		// TODO We should use templates to insert these and related formatting.
		prefix = prefix.replace("<channelName>", mName);

		Component fullMessage = Component.empty()
		    .append(minimessage.parse(prefix))
		    .append(Component.empty().color(NamedTextColor.RED).append(message.getMessage()));
		recipient.sendMessage(Identity.nil(), fullMessage, MessageType.SYSTEM);
		if (recipient instanceof Player) {
			PlayerStateManager.getPlayerState((Player) recipient).playMessageSound(this);
		}
	}
}