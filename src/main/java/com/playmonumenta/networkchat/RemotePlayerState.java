package com.playmonumenta.networkchat;

import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.networkrelay.NetworkRelayAPI;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

class RemotePlayerState {
	public final UUID mUuid;
	public final String mName;
	public final Component mComponent;
	public final boolean mIsHidden;
	public final boolean mIsOnline;
	public final String mShard;

	public RemotePlayerState(Player player, boolean isOnline) {
		mUuid = player.getUniqueId();
		mName = player.getName();
		mComponent = MessagingUtils.playerComponent(player);
		mIsHidden = !RemotePlayerManager.isLocalPlayerVisible(player);
		mIsOnline = isOnline;
		mShard = NetworkChatPlugin.getShardName();

		MMLog.fine("Created RemotePlayerState for " + mName + " from " + mShard + ": " + (mIsOnline ? "online" : "offline"));
	}

	public RemotePlayerState(JsonObject remoteData) {
		mUuid = UUID.fromString(remoteData.get("playerUuid").getAsString());
		mName = remoteData.get("playerName").getAsString();
		mComponent = MessagingUtils.fromJson(remoteData.get("playerComponent"));
		mIsHidden = remoteData.get("isHidden").getAsBoolean();
		mIsOnline = remoteData.get("isOnline").getAsBoolean();
		mShard = remoteData.get("shard").getAsString();

		MMLog.fine("Received RemotePlayerState for " + mName + " from " + mShard + ": " + (mIsOnline ? "online" : "offline"));
	}

	public JsonObject getRemotePlayerData() {
		JsonObject remotePlayerData = new JsonObject();
		remotePlayerData.add("playerComponent", MessagingUtils.toJson(mComponent));
		return remotePlayerData;
	}

	public void broadcast() {
		JsonObject remotePlayerData = getRemotePlayerData();
		remotePlayerData.addProperty("playerUuid", mUuid.toString());
		remotePlayerData.addProperty("playerName", mName);
		remotePlayerData.addProperty("isHidden", mIsHidden);
		remotePlayerData.addProperty("isOnline", mIsOnline);
		remotePlayerData.addProperty("shard", mShard);

		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(RemotePlayerManager.REMOTE_PLAYER_CHANNEL,
				remotePlayerData,
				NetworkChatPlugin.getMessageTtl());
		} catch (Exception e) {
			MMLog.severe("Failed to broadcast " + RemotePlayerManager.REMOTE_PLAYER_CHANNEL);
		}
	}
}
