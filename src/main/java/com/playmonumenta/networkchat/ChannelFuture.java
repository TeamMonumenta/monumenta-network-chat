package com.playmonumenta.networkchat;

import java.util.UUID;

import com.google.gson.JsonObject;

import org.bukkit.command.CommandSender;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

// A channel that is from a future plugin version. This may not be saved.
public class ChannelFuture extends ChannelBase {
	public static final String CHANNEL_CLASS_ID = "future";

	private String mType;
	private UUID mId;
	private String mName;

	private ChannelFuture(String type, UUID channelId, String name) {
		mType = type;
		mId = channelId;
		mName = name;
	}

	protected static ChannelBase fromJsonInternal(JsonObject channelJson) {
		String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();
		String uuidString = channelJson.getAsJsonPrimitive("uuid").getAsString();
		UUID channelId = UUID.fromString(uuidString);
		String name = channelJson.getAsJsonPrimitive("name").getAsString();
		return new ChannelFuture(channelClassId, channelId, name);
	}

	// NOTE: This channel type should never be saved, as it will overwrite a real channel.
	// toJson() shouldn't even be called for this class.
	public JsonObject toJson() {
		return null;
	}

	// NOTE: If this class ID is ever read, it means the channel hasn't loaded and should be ignored.
	public static String getClassId() {
		return CHANNEL_CLASS_ID;
	}

	public UUID getUniqueId() {
		return mId;
	}

	protected void setName(String name) throws WrapperCommandSyntaxException {
		mName = name;
	}

	public String getName() {
		return mName;
	}

	public void sendMessage(CommandSender sender, String message) throws WrapperCommandSyntaxException {
		CommandAPI.fail("This channel's type is not supported in this plugin version.");
	}

	// Messages will be replayed for anyone triggering the channel to load, nothing to do.
	public void distributeMessage(Message message) {
		;
	}

	// The channel is loading - we can't determine who can see this yet!
	protected void showMessage(CommandSender recipient, Message message) {
		;
	}
}
