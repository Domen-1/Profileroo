package com.domen.profileroo.core;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Finds out which files a mod's config handler writes: MaLiLib's {@code IConfigHandler} does not say where the config
 * lives, but every MaLiLib mod saves through {@code FileUtils.writeDataToFile}, which reports here (see FileUtilsMixin).
 */
public final class ConfigFileTracker {
	private static final ThreadLocal<Set<Path>> CAPTURE = new ThreadLocal<>();

	private ConfigFileTracker() {
	}

	/** Runs {@code action} and returns the files written meanwhile on this thread. */
	public static Set<Path> capture(Runnable action) {
		Set<Path> previous = CAPTURE.get();
		Set<Path> written = new LinkedHashSet<>();
		CAPTURE.set(written);
		try {
			action.run();
		} finally {
			CAPTURE.set(previous);
		}
		return written;
	}

	public static void onWrite(Path file) {
		Set<Path> written = CAPTURE.get();
		if (written != null) {
			written.add(file.toAbsolutePath().normalize());
		}
	}
}
