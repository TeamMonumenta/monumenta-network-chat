package com.playmonumenta.networkchat;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

// Permissions to listen or talk in a channel
public class ChannelPerms {
	private Boolean mMayChat = null;
	private Boolean mMayListen = null;

	public static ChannelPerms fromJson(JsonObject object) {
		ChannelPerms perms = new ChannelPerms();
		if (object != null) {
			JsonPrimitive mayChatJson = object.getAsJsonPrimitive("mayChat");
			if (mayChatJson != null && mayChatJson.isBoolean()) {
				perms.mMayChat = mayChatJson.getAsBoolean();
			}

			JsonPrimitive mayListenJson = object.getAsJsonPrimitive("mayListen");
			if (mayListenJson != null && mayListenJson.isBoolean()) {
				perms.mMayListen = mayListenJson.getAsBoolean();
			}
		}
		return perms;
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		if (mMayChat != null) {
			object.addProperty("mayChat", mMayChat);
		}
		if (mMayListen != null) {
			object.addProperty("mayListen", mMayListen);
		}
		return object;
	}

	public boolean isDefault() {
		if (mMayChat != null) {
			return false;
		}
		if (mMayListen != null) {
			return false;
		}
		return true;
	}

	public Boolean mayChat() {
		return mMayChat;
	}

	public void mayChat(Boolean value) {
		mMayChat = value;
	}

	public Boolean mayListen() {
		return mMayListen;
	}

	public void mayListen(Boolean value) {
		mMayListen = value;
	}
}
