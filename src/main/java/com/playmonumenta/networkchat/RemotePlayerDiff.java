package com.playmonumenta.networkchat;

import com.google.gson.JsonElement;
import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.networkrelay.RemotePlayerAPI;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

public class RemotePlayerDiff {
	protected static class DiffDeadline {
		public UUID mPlayerUuid;
		public final int mStartTick;
		public @Nullable String mPlayerName = null;
		public int mLastUpdateTick;
		public @Nullable Integer mLastChatUpdateTick = null;
		public @Nullable Integer mLastRelayUpdateTick = null;
		public String mLastCause = "???";
		public boolean mChatVersionUpdated = false;
		public boolean mRelayVersionUpdated = false;

		public DiffDeadline(UUID playerUuid, String cause, boolean causedByRelay) {
			mPlayerUuid = playerUuid;
			mStartTick = Bukkit.getCurrentTick();
			update(cause, causedByRelay);
		}

		public void update(String cause, boolean causedByRelay) {
			int currentTick = Bukkit.getCurrentTick();
			mLastUpdateTick = currentTick;
			if (causedByRelay) {
				mRelayVersionUpdated = true;
				mLastRelayUpdateTick = currentTick;
			} else {
				mChatVersionUpdated = true;
				mLastChatUpdateTick = currentTick;
			}
			mLastCause = cause;
			if (mPlayerName == null) {
				mPlayerName = MonumentaRedisSyncAPI.cachedUuidToName(mPlayerUuid);
			}

			logStatus(currentTick);
		}

		/**
		 * Check expiry and other important time intervals
		 * @param currentTick The current server tick
		 * @return true if this entry has expired
		 */
		public boolean checkDeadline(int currentTick) {
			boolean hasExpired = currentTick - mLastUpdateTick >= TIME_LIMIT_TICKS;

			if (
				hasExpired
					|| (mLastChatUpdateTick != null && currentTick - mLastChatUpdateTick == TIME_LIMIT_TICKS)
					|| (mLastRelayUpdateTick != null && currentTick - mLastRelayUpdateTick == TIME_LIMIT_TICKS)
			) {
				logStatus(currentTick);
			}

			return hasExpired;
		}

		/**
		 * Logs the current difference between Chat/Relay implementations, if any
		 * @param currentTick The current server tick
		 */
		public void logStatus(int currentTick) {
			@Nullable Component chatComponent = RemotePlayerManager.getPlayerComponent(mPlayerUuid);
			@Nullable Component relayComponent = RemotePlayerListener.getPlayerComponent(mPlayerUuid);
			@Nullable JsonElement chatJson = chatComponent == null ? null : MessagingUtils.toJson(chatComponent);
			@Nullable JsonElement relayJson = relayComponent == null ? null : MessagingUtils.toJson(relayComponent);
			@Nullable String chatJsonStr = chatJson == null ? "null" : chatJson.toString();
			@Nullable String relayJsonStr = relayJson == null ? "null" : relayJson.toString();
			@Nullable String chatShard = RemotePlayerManager.getPlayerShard(mPlayerUuid);
			@Nullable String relayShard = RemotePlayerAPI.getPlayerShard(mPlayerUuid);
			boolean componentDiffers = !Objects.equals(chatComponent, relayComponent);
			boolean shardDiffers = !Objects.equals(chatShard, relayShard);
			boolean differs = componentDiffers || shardDiffers;
			boolean deadlineExpired = currentTick - mLastUpdateTick >= TIME_LIMIT_TICKS;
			boolean isProblem = differs && deadlineExpired;

			int ticksSinceChatUpdate = mLastChatUpdateTick == null ? -1 : currentTick - mLastChatUpdateTick;
			int ticksSinceRelayUpdate = mLastRelayUpdateTick == null ? -1 : currentTick - mLastRelayUpdateTick;

			List<String> additionalLines = new ArrayList<>();

			StringBuilder diffStatus = new StringBuilder("[RPM Diff.1] ");
			diffStatus.append(mPlayerName);
			diffStatus.append(" at ");
			diffStatus.append(ticksSinceChatUpdate);
			diffStatus.append(" ticks since chat update (");
			diffStatus.append(chatShard == null ? "offline" : chatShard);
			diffStatus.append(") and ");
			diffStatus.append(ticksSinceRelayUpdate);
			diffStatus.append(" ticks since relay update (");
			diffStatus.append(relayShard == null ? "offline" : relayShard);
			diffStatus.append(") last caused by ");
			diffStatus.append(mLastCause);
			diffStatus.append("; component ");
			diffStatus.append(componentDiffers ? "differs: " : "matches: ");
			if (chatJson == null && relayJson == null) {
				diffStatus.append("both null");
			} else if (chatJsonStr == null) {
				diffStatus.append("chat is null, but relay is set");
			} else if (relayJsonStr == null) {
				diffStatus.append("relay is null, but chat is set");
			} else {
				// IntelliJ incorrectly assumes Components that are not null are equal when using Objects.equals();
				// use a.equals(b) if more information is required
				diffStatus.append("both set");
				additionalLines.add("[RPM Diff.2] Chat component: " + chatJsonStr);
				additionalLines.add("[RPM Diff.3] Relay component: " + relayJsonStr);
			}

			if (isProblem) {
				MMLog.warning(diffStatus.toString());
				for (String line : additionalLines) {
					MMLog.warning(line);
				}
			} else {
				MMLog.info(diffStatus.toString());
				// Additional lines not required until deadline has been reached
			}
		}
	}

	public static final int TIME_LIMIT_TICKS = 200;

	private static final List<DiffDeadline> mUpcomingDeadlines = new ArrayList<>();
	private static final Map<UUID, DiffDeadline> mDeadlinesByPlayer = new HashMap<>();
	private static @Nullable BukkitRunnable mDiffRunnable = null;

	public static void update(UUID playerUuid, String cause, boolean causedByRelay) {
		// Check if an upcoming deadline already exists for this player
		DiffDeadline deadline = mDeadlinesByPlayer.get(playerUuid);
		if (deadline != null) {
			// Update it
			deadline.update(cause, causedByRelay);
		} else {
			// Create it
			deadline = new DiffDeadline(playerUuid, cause, causedByRelay);
			mUpcomingDeadlines.add(deadline);
			mDeadlinesByPlayer.put(playerUuid, deadline);
		}
		startRunnable();
	}

	public static void startRunnable() {
		if (mDiffRunnable != null && !mDiffRunnable.isCancelled()) {
			return;
		}

		mDiffRunnable = new BukkitRunnable() {
			@Override
			public void run() {
				int currentTick = Bukkit.getCurrentTick();

				Iterator<DiffDeadline> it = mUpcomingDeadlines.iterator();
				while (it.hasNext()) {
					DiffDeadline otherDeadline = it.next();
					if (otherDeadline.checkDeadline(currentTick)) {
						it.remove();
						mDeadlinesByPlayer.remove(otherDeadline.mPlayerUuid);
					}
				}

				if (mUpcomingDeadlines.isEmpty()) {
					cancel();
					mDiffRunnable = null;
				}
			}
		};
		mDiffRunnable.runTaskTimer(NetworkChatPlugin.getInstance(), 0L, 1L);
	}
}
