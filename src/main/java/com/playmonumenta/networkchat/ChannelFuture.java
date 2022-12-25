package com.playmonumenta.networkchat;

import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;

// A channel that is from a future plugin version. This may not be saved.
public class ChannelFuture extends Channel {
	public static final String CHANNEL_CLASS_ID = "future";

	private final String mType;
	private final UUID mId;
	private Instant mLastUpdate;
	private String mName;

	private ChannelFuture(String type, UUID channelId, Instant lastUpdate, String name) {
		mType = type;
		mId = channelId;
		mLastUpdate = lastUpdate;
		mName = name;
	}

	protected static Channel fromJsonInternal(JsonObject channelJson) {
		String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();
		String uuidString = channelJson.getAsJsonPrimitive("uuid").getAsString();
		UUID channelId = UUID.fromString(uuidString);
		Instant lastUpdate = Instant.now();
		if (channelJson.get("lastUpdate") != null) {
			lastUpdate = Instant.ofEpochMilli(channelJson.get("lastUpdate").getAsLong());
		}
		String name = channelJson.getAsJsonPrimitive("name").getAsString();
		return new ChannelFuture(channelClassId, channelId, lastUpdate, name);
	}

	// NOTE: This channel type should never be saved, as it will overwrite a real channel.
	// toJson() shouldn't even be called for this class.
	@Override
	public JsonObject toJson() {
		throw new RuntimeException("Cannot convert future channels to json");
	}

	// NOTE: If this class ID is ever read, it means the channel hasn't loaded and should be ignored.
	@Override
	public String getClassId() {
		return CHANNEL_CLASS_ID;
	}

	@Override
	public UUID getUniqueId() {
		return mId;
	}

	@Override
	public void markModified() {
		mLastUpdate = Instant.now();
	}

	@Override
	public Instant lastModified() {
		return mLastUpdate;
	}

	@Override
	protected void setName(String name) throws WrapperCommandSyntaxException {
		mName = name;
	}

	@Override
	public String getName() {
		return mName;
	}

	@Override
	public @Nullable TextColor color() {
		return null;
	}

	@Override
	public void color(CommandSender sender, @Nullable TextColor color) throws WrapperCommandSyntaxException {
		throw CommandUtils.fail(sender, "This channel's type (" + mType + ") is not supported in this plugin version.");
	}

	@Override
	public void sendMessage(CommandSender sender, String message) throws WrapperCommandSyntaxException {
		throw CommandUtils.fail(sender, "This channel's type (" + mType + ") is not supported in this plugin version.");
	}

	// Messages will be replayed for anyone triggering the channel to load, nothing to do.
	@Override
	public void distributeMessage(Message message) {
	}

	// The channel is from a future version - we can't determine how to display this!
	@Override
	protected Component shownMessage(CommandSender recipient, Message message) {
		return message.getMessage();
	}

	// The channel is from a future version - we can't determine who can see this!
	@Override
	protected void showMessage(CommandSender recipient, Message message) {
	}
}
