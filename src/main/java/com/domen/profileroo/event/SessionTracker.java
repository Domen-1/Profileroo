package com.domen.profileroo.event;

import com.domen.profileroo.core.SessionInfo;
import com.domen.profileroo.mixin.ClientLevelAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

public final class SessionTracker {
	private static boolean reconfiguring;

	private SessionTracker() {
	}

	public static void onReconfiguration() {
		reconfiguring = true;
	}

	public static void onDisconnect() {
		reconfiguring = false;
	}

	/** Whether the world being joined follows a reconfiguration rather than a fresh connection. Clears the flag. */
	public static boolean consumeReconfiguring() {
		boolean value = reconfiguring;
		reconfiguring = false;
		return value;
	}

	/**
	 * Where the player is going: the singleplayer world, or the server the new level's connection belongs to.
	 * {@code Minecraft.getCurrentServer()} goes through the player, who does not exist yet while the level loads.
	 */
	@Nullable
	public static SessionInfo current(Minecraft mc, @Nullable ClientLevel level) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null) {
			String folder = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
			return SessionInfo.singleplayer(server.getWorldData().getLevelName(), folder);
		}
		ClientPacketListener connection = level != null ? ((ClientLevelAccessor) level).profileroo$getConnection() : mc.getConnection();
		ServerData data = connection != null ? connection.getServerData() : null;
		if (data == null) {
			return null;
		}
		if (data.isRealm()) {
			return SessionInfo.realms(data.name);
		}
		if (data.isLan()) {
			return SessionInfo.lan(data.ip, data.name);
		}
		return SessionInfo.multiplayer(data.ip, data.name);
	}
}
