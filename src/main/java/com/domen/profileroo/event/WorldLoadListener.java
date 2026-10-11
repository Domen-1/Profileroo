package com.domen.profileroo.event;

import com.domen.profileroo.profile.ProfileManager;
import fi.dy.masa.malilib.interfaces.IWorldLoadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

/**
 * MaLiLib saves every config after the world is gone and loads them all once the new one is set. Switching in "pre" of
 * a join means the mods read the new profile in that very load.
 */
public class WorldLoadListener implements IWorldLoadListener {
	@Override
	public void onWorldLoadPre(@Nullable ClientLevel worldBefore, @Nullable ClientLevel worldAfter, Minecraft mc) {
		if (worldBefore == null && worldAfter != null) {
			ProfileManager.getInstance().onJoin(SessionTracker.current(mc, worldAfter), SessionTracker.consumeReconfiguring());
		}
	}

	@Override
	public void onWorldLoadPost(@Nullable ClientLevel worldBefore, @Nullable ClientLevel worldAfter, Minecraft mc) {
		if (worldBefore != null && worldAfter == null) {
			ProfileManager.getInstance().onLeave();
		} else if (worldBefore == null && worldAfter != null) {
			ProfileManager.getInstance().onJoinFinished();
		}
	}
}
