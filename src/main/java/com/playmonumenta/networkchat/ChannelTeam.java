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
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

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

// A channel visible to all shards
public class ChannelTeam extends Channel {
	public static final String CHANNEL_CLASS_ID = "team";
	private static final String[] TEAM_COMMANDS = {"teammsg", "tm"};

	private UUID mId;
	private Instant mLastUpdate;
	private String mTeamName;
	private ChannelSettings mDefaultSettings;
	private ChannelPerms mDefaultPerms;
	private Map<UUID, ChannelPerms> mPlayerPerms;

	private ChannelTeam(UUID channelId, Instant lastUpdate, String teamName) {
		mId = channelId;
		mLastUpdate = lastUpdate;
		mTeamName = teamName;

		mDefaultSettings = new ChannelSettings();
		mDefaultPerms = new ChannelPerms();
		mPlayerPerms = new HashMap<>();
	}

	public ChannelTeam(String teamName) {
		mLastUpdate = Instant.now();
		mId = UUID.randomUUID();
		mTeamName = teamName;

		mDefaultSettings = new ChannelSettings();
		mDefaultPerms = new ChannelPerms();
		mPlayerPerms = new HashMap<>();
	}

	protected static Channel fromJsonInternal(JsonObject channelJson) throws Exception {
		String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();
		if (channelClassId == null || !channelClassId.equals(CHANNEL_CLASS_ID)) {
			throw new Exception("Cannot create ChannelTeam from channel ID " + channelClassId);
		}
		String uuidString = channelJson.getAsJsonPrimitive("uuid").getAsString();
		UUID channelId = UUID.fromString(uuidString);
		Instant lastUpdate = Instant.now();
		if (channelJson.get("lastUpdate") != null) {
			lastUpdate = Instant.ofEpochMilli(channelJson.get("lastUpdate").getAsLong());
		}

		String teamName = channelJson.getAsJsonPrimitive("team").getAsString();

		ChannelTeam channel = new ChannelTeam(channelId, lastUpdate, teamName);

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
		result.addProperty("name", getName());
		result.addProperty("team", mTeamName);
		result.add("defaultSettings", mDefaultSettings.toJson());
		result.add("defaultPerms", mDefaultPerms.toJson());
		result.add("playerPerms", allPlayerPermsJson);
		return result;
	}

	public static void registerNewChannelCommands(String[] baseCommands, List<Argument> prefixArguments) {
		// Setting up new team channels will be done via /teammsg, /tm, and similar,
		// not through /chat new Blah team. The provided arguments are ignored.
		List<Argument> arguments = new ArrayList<>();

		for (String command : TEAM_COMMANDS) {
			CommandAPI.unregister(command);

			new CommandAPICommand(command)
				.executes((sender, args) -> {
					return runCommandSet(sender);
				})
				.register();

			arguments.clear();
			arguments.add(new GreedyStringArgument("message"));
			new CommandAPICommand(command)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return runCommandSay(sender, (String)args[0]);
				})
				.register();
		}
	}

	private static int runCommandSet(CommandSender sender) throws WrapperCommandSyntaxException {
		if (!(sender instanceof Player)) {
			sender.sendMessage(Component.translatable("permissions.requires.player"));
			CommandUtils.fail(sender, "A player is required to run this command here");
		}

		Player sendingPlayer = (Player) sender;
		Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam((sendingPlayer).getName());
		if (team == null) {
			sender.sendMessage(Component.translatable("commands.teammsg.failed.noteam"));
			CommandUtils.fail(sender, sendingPlayer.getName() + " must be on a team to message their team.");
		}
		String teamName = team.getName();

		Channel channel = ChannelManager.getChannel("Team_" + teamName);
		if (channel == null) {
			try {
				channel = new ChannelTeam(teamName);
			} catch (Exception e) {
				CommandUtils.fail(sender, "Could not create new team channel: Could not connect to RabbitMQ.");
			}
			ChannelManager.registerNewChannel(sender, channel);
		}

		PlayerState senderState = PlayerStateManager.getPlayerState(sendingPlayer);
		senderState.setActiveChannel(channel);
		sender.sendMessage(Component.text("You are now typing to team ", NamedTextColor.GRAY).append(team.displayName()));
		return 1;
	}

	private static int runCommandSay(CommandSender sender, String message) throws WrapperCommandSyntaxException {
		if (!(sender instanceof Entity)) {
			sender.sendMessage(Component.translatable("permissions.requires.entity"));
			CommandUtils.fail(sender, "An entity is required to run this command here");
		}

		Entity sendingEntity = (Entity) sender;
		Team team;
		if (sendingEntity instanceof Player) {
			team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(((Player) sendingEntity).getName());
		} else {
			team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(sendingEntity.getUniqueId().toString());
		}
		if (team == null) {
			sender.sendMessage(Component.translatable("commands.teammsg.failed.noteam"));
			CommandUtils.fail(sender, sendingEntity.getName() + " must be on a team to message their team.");
		}
		String teamName = team.getName();

		Channel channel = ChannelManager.getChannel("Team_" + teamName);
		if (channel == null) {
			try {
				channel = new ChannelTeam(teamName);
			} catch (Exception e) {
				CommandUtils.fail(sender, "Could not create new team channel: Could not connect to RabbitMQ.");
			}
			ChannelManager.registerNewChannel(sender, channel);
		}

		channel.sendMessage(sendingEntity, message);
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
		CommandAPI.fail("Team channels may not be named.");
	}

	public String getName() {
		return "Team_" + mTeamName;
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
		return true;
	}

	public boolean mayChat(CommandSender sender) {
		if (!sender.hasPermission("networkchat.say")) {
			return false;
		}
		if (!sender.hasPermission("networkchat.say.team")) {
			return false;
		}

		if (!(sender instanceof Player)) {
			return true;
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

		Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
		if (team == null) {
			return false;
		}
		String teamName = team.getName();
		return mTeamName.equals(teamName);
	}

	public boolean mayListen(CommandSender sender) {
		if (!sender.hasPermission("networkchat.see")) {
			return false;
		}
		if (!sender.hasPermission("networkchat.see.team")) {
			return false;
		}

		if (!(sender instanceof Player)) {
			return false;
		}

		Player player = (Player) sender;
		UUID playerId = player.getUniqueId();

		ChannelPerms playerPerms = mPlayerPerms.get(playerId);
		if (playerPerms == null) {
			if (mDefaultPerms.mayListen() != null && !mDefaultPerms.mayListen()) {
				return false;
			}
		} else if (playerPerms.mayListen() != null && !playerPerms.mayListen()) {
			return false;
		}

		Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
		if (team == null) {
			return false;
		}
		String teamName = team.getName();
		return mTeamName.equals(teamName);
	}

	public void sendMessage(CommandSender sender, String messageText) throws WrapperCommandSyntaxException {
		if (!sender.hasPermission("networkchat.say")) {
			CommandUtils.fail(sender, "You do not have permission to chat.");
		}
		if (!sender.hasPermission("networkchat.say.team")) {
			CommandUtils.fail(sender, "You do not have permission to talk to a team.");
		}

		if (!mayChat(sender)) {
			CommandUtils.fail(sender, "You do not have permission to chat in this channel.");
		}

		JsonObject extraData = new JsonObject();
		extraData.addProperty("team", mTeamName);

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

		Message message = Message.createMessage(this, sender, extraData, messageText, true, allowedTransforms);

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

		JsonObject extraData = message.getExtraData();
		String teamName;
		try {
			teamName = extraData.getAsJsonPrimitive("team").getAsString();
		} catch (Exception e) {
			// Could not read team from message
			return;
		}
		Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName);
		if (team == null) {
			// No such team on this server
			return;
		}

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
		JsonObject extraData = message.getExtraData();
		String teamName;
		try {
			teamName = extraData.getAsJsonPrimitive("team").getAsString();
		} catch (Exception e) {
			// Could not read team from message
			return;
		}

		Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName);
		TextColor color;
		Component teamPrefix;
		Component teamDisplayName;
		Component teamSuffix;
		try {
			color = team.color();
			teamPrefix = team.prefix();
			teamDisplayName = team.displayName();
			teamSuffix = team.suffix();
		} catch (Exception e) {
			color = null;
			teamPrefix = Component.empty();
			teamDisplayName = Component.translatable("chat.square_brackets", Component.text(team.getName()));
			teamSuffix = Component.empty();
		}

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
		if (senderUuid == null) {
			senderIdentity = Identity.nil();
		} else {
			senderIdentity = Identity.identity(senderUuid);
		}

		Component fullMessage = Component.empty()
		    .append(minimessage.parse(prefix, List.of(Template.of("sender", message.getSenderComponent()),
		        Template.of("team_color", (color == null) ? "" : "<" + color.asHexString() + ">"),
		        Template.of("team_prefix", teamPrefix),
		        Template.of("team_displayname", teamDisplayName),
		        Template.of("team_suffix", teamSuffix))))
		    .append(Component.empty().color(channelColor).append(message.getMessage()));
		recipient.sendMessage(senderIdentity, fullMessage, MessageType.CHAT);
		if (recipient instanceof Player && !((Player) recipient).getUniqueId().equals(senderUuid)) {
			PlayerStateManager.getPlayerState((Player) recipient).playMessageSound(this);
		}
	}
}
