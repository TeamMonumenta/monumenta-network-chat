package com.playmonumenta.networkchat;

import javax.annotation.Nullable;

public interface ChannelPermissionNode {
	@Nullable String getChannelPermission();

	void setChannelPermission(@Nullable String newPerms);
}
