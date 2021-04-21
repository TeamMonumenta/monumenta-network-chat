package com.playmonumenta.networkchat;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

// Settings for a given channel, same structure for channel default and player preference
public class ChannelSettings {
	private Boolean mIsListening = null;
	private Boolean mMessagesPlaySound = null; // TODO Allow specifying a sound.

	public static ChannelSettings fromJson(JsonObject object) {
		ChannelSettings settings = new ChannelSettings();
		if (object != null) {
			JsonPrimitive isListeningJson = object.getAsJsonPrimitive("isListening");
			if (isListeningJson != null && isListeningJson.isBoolean()) {
				settings.mIsListening = isListeningJson.getAsBoolean();
			}

			JsonPrimitive messagesPlaySoundJson = object.getAsJsonPrimitive("messagesPlaySound");
			if (messagesPlaySoundJson != null && messagesPlaySoundJson.isBoolean()) {
				settings.mMessagesPlaySound = messagesPlaySoundJson.getAsBoolean();
			}
		}
		return settings;
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		if (mIsListening != null) {
			object.addProperty("isListening", mIsListening);
		}
		if (mMessagesPlaySound != null) {
			object.addProperty("messagesPlaySound", mMessagesPlaySound);
		}
		return object;
	}

	public boolean isDefault() {
		if (mIsListening != null) {
			return false;
		}
		if (mMessagesPlaySound != null) {
			return false;
		}
		return true;
	}

	public Boolean isListening() {
		return mIsListening;
	}

	public void isListening(Boolean value) {
		mIsListening = value;
	}

	public Boolean messagesPlaySound() {
		return mMessagesPlaySound;
	}

	public void messagesPlaySound(Boolean value) {
		mMessagesPlaySound = value;
	}
}
