package com.domen.profileroo.profile;

import com.domen.profileroo.Profileroo;
import com.domen.profileroo.Reference;
import com.domen.profileroo.core.ConfigFileTracker;
import com.domen.profileroo.core.ProfileStore;
import com.domen.profileroo.mixin.ConfigManagerAccessor;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.IConfigHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

/** The mods registered in MaLiLib's ConfigManager — Tweakeroo, MiniHUD, Litematica, Item Scroller and the like. */
public final class ManagedMods {
	private ManagedMods() {
	}

	private static Map<String, IConfigHandler> handlers() {
		return ((ConfigManagerAccessor) ConfigManager.getInstance()).profileroo$getConfigHandlers();
	}

	/** Every MaLiLib mod with a config, sorted by id, without this mod itself. */
	public static List<String> allModIds() {
		List<String> ids = new ArrayList<>();
		for (String modId : handlers().keySet()) {
			if (!modId.equals(Reference.MOD_ID) && ProfileStore.isSafeModId(modId)) {
				ids.add(modId);
			}
		}
		ids.sort(String::compareTo);
		return ids;
	}

	@Nullable
	public static IConfigHandler handler(String modId) {
		return handlers().get(modId);
	}

	public static String displayName(String modId) {
		return FabricLoader.getInstance().getModContainer(modId).map(c -> c.getMetadata().getName()).orElse(modId);
	}

	/**
	 * Saves the mod's config and returns the files that were written inside the config folder. When the mod writes
	 * without MaLiLib's FileUtils, falls back to {@code config/<modId>.json}, the name every MaLiLib mod uses.
	 */
	public static List<Path> saveAndCapture(String modId, ProfileStore store) {
		IConfigHandler handler = handler(modId);
		if (handler == null) {
			return List.of();
		}
		Set<Path> written;
		try {
			written = ConfigFileTracker.capture(handler::save);
		} catch (RuntimeException e) {
			Profileroo.LOGGER.error("Saving the config of '{}' failed", modId, e);
			return List.of();
		}
		List<Path> files = new ArrayList<>();
		for (Path file : written) {
			if (store.relativeToConfig(file) != null && Files.isRegularFile(file)) {
				files.add(file);
			}
		}
		if (files.isEmpty()) {
			Path fallback = store.configDir().resolve(modId + ".json");
			if (Files.isRegularFile(fallback)) {
				files.add(fallback);
			}
		}
		Profileroo.debug("Config files of {}: {}", modId, files);
		return files;
	}

	/** Re-reads the mod's config from disk into the game. */
	public static void reload(String modId) {
		IConfigHandler handler = handler(modId);
		if (handler == null) {
			return;
		}
		try {
			handler.load();
		} catch (RuntimeException e) {
			Profileroo.LOGGER.error("Loading the config of '{}' failed", modId, e);
		}
	}
}
