package com.domen.profileroo.core;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class FileIO {
	private FileIO() {
	}

	/** Writes through a temporary file, so a crash never leaves half a file behind. */
	public static void writeAtomic(Path file, byte[] data) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		Files.createDirectories(parent);
		Path tmp = parent.resolve(file.getFileName() + ".tbp-tmp");
		Files.write(tmp, data);
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Writes a config file that mods read. A symbolic link is written through (someone shares the config between
	 * instances), anything else atomically.
	 */
	public static void writeLive(Path file, byte[] data) throws IOException {
		if (Files.isSymbolicLink(file)) {
			Files.write(file, data);
		} else {
			writeAtomic(file, data);
		}
	}

	/** Regular files under {@code dir}, in a stable order; empty when the folder does not exist. */
	public static List<Path> listFiles(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> stream = Files.walk(dir)) {
			return stream.filter(Files::isRegularFile)
					.filter(p -> !p.getFileName().toString().endsWith(".tbp-tmp"))
					.sorted()
					.toList();
		}
	}

	public static void copyTree(Path from, Path to) throws IOException {
		for (Path file : listFiles(from)) {
			Path target = to.resolve(from.relativize(file).toString());
			Files.createDirectories(target.getParent());
			Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
		}
		Files.createDirectories(to);
	}

	public static void deleteTree(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}
		List<Path> paths;
		try (Stream<Path> stream = Files.walk(dir)) {
			paths = new ArrayList<>(stream.toList());
		}
		paths.sort(Comparator.reverseOrder());
		for (Path path : paths) {
			Files.deleteIfExists(path);
		}
	}

	/** Removes empty folders under {@code dir} (not {@code dir} itself). */
	public static void pruneEmptyDirs(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) {
			return;
		}
		List<Path> dirs;
		try (Stream<Path> stream = Files.walk(dir)) {
			dirs = new ArrayList<>(stream.filter(Files::isDirectory).filter(p -> !p.equals(dir)).toList());
		}
		dirs.sort(Comparator.reverseOrder());
		for (Path sub : dirs) {
			try (Stream<Path> children = Files.list(sub)) {
				if (children.findAny().isEmpty()) {
					Files.delete(sub);
				}
			}
		}
	}
}
