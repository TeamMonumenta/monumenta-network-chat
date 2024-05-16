package com.playmonumenta.networkchat;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

public class RemotePlayerDiff {
	protected static class DiffDeadline {
		public UUID mPlayerUuid;
		public final int mStartTick;
		public @Nullable String mPlayerName = null;
		public int mLastUpdateTick;
		public @Nullable Integer mLastChatUpdateTick = null;
		public @Nullable Integer mLastRelayUpdateTick = null;
		public final List<String> mCauses = new ArrayList<>();
		public boolean mChatVersionUpdated = false;
		public boolean mRelayVersionUpdated = false;

		public DiffDeadline(UUID playerUuid, String cause, boolean causedByRelay) {
			mPlayerUuid = playerUuid;
			mStartTick = Bukkit.getCurrentTick();
			update(cause, causedByRelay);
		}

		public void update(String cause, boolean causedByRelay) {
			int currentTick = Bukkit.getCurrentTick();
			boolean isNew = mCauses.isEmpty();
			mLastUpdateTick = currentTick;
			if (causedByRelay) {
				mRelayVersionUpdated = true;
				mLastRelayUpdateTick = currentTick;
			} else {
				mChatVersionUpdated = true;
				mLastChatUpdateTick = currentTick;
			}
			if (!mCauses.contains(cause)) {
				mCauses.add(cause);
			}
			if (mPlayerName == null) {
				mPlayerName = MonumentaRedisSyncAPI.cachedUuidToName(mPlayerUuid);
			}

			// TODO log this update
		}

		// TODO Method to check expiry and/or important time intervals

		// TODO Logging method
	}

	public static final int TIME_LIMIT_TICKS = 20;

	private static final List<DiffDeadline> mUpcomingDeadlines = new ArrayList<>();
	private static final Map<UUID, DiffDeadline> mDeadlinesByPlayer = new HashMap<>();
	// TODO BukkitRunnable to clear out deadlines as they pass instead of after the fact

	public void update(UUID playerUuid, String cause, boolean causedByRelay) {
		int currentTick = Bukkit.getCurrentTick();

		// Go through the backlog first if needed
		Iterator<DiffDeadline> it = mUpcomingDeadlines.iterator();
		while (it.hasNext()) {
			DiffDeadline otherDeadline = it.next();
			if (currentTick - otherDeadline.mLastUpdateTick < TIME_LIMIT_TICKS) {
				// No more deadlines to process
				break;
			}
			it.remove();
			mDeadlinesByPlayer.remove(otherDeadline.mPlayerUuid);

			// TODO Deadline has passed, verify results
		}

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
	}
}
