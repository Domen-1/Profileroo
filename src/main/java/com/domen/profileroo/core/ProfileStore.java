package com.domen.profileroo.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/**
 * Profiles on disk. A profile is a folder with a copy of each managed mod's config files, laid out by mod and then by
 * the path inside the config folder: {@code profiles/smp/tweakeroo/tweakeroo.json} goes to {@code config/tweakeroo.json}.
 * Profile settings (lock, hotkey) and server rules are not here but in {@code state.json}.
 */
public final class ProfileStore {
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);

	private final Path configDir;
	private final Path root;

	public ProfileStore(Path configDir, String folderName) {
		this.configDir = configDir.toAbsolutePath().normalize();
		this.root = this.configDir.resolve(folderName);
	}

	public Path configDir() {
		return this.configDir;
	}

	public Path root() {
		return this.root;
	}

	public Path stateFile() {
		return this.root.resolve("state.json");
	}

	public Path profilesDir() {
		return this.root.resolve("profiles");
	}

	public Path profileDir(String profile) {
		return this.profilesDir().resolve(profile);
	}

	/** Mod ids become folder names inside a profile; anything that could step out of the folder is refused. */
	public static boolean isSafeModId(String modId) {
		return !modId.isEmpty() && !modId.equals(".") && !modId.equals("..") && modId.matches("[A-Za-z0-9_.\\-]+");
	}

	/** The file's path inside the config folder, or null if it lies outside it or inside this mod's own folder. */
	@Nullable
	public Path relativeToConfig(Path file) {
		Path normalized = file.toAbsolutePath().normalize();
		if (!normalized.startsWith(this.configDir) || normalized.startsWith(this.root) || normalized.equals(this.configDir)) {
			return null;
		}
		return this.configDir.relativize(normalized);
	}

	/** Profile folders that exist on disk. */
	public List<String> profileFolders() throws IOException {
		Path dir = this.profilesDir();
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
		}
	}

	public boolean hasMod(String profile, String modId) {
		Path dir = this.profileDir(profile).resolve(modId);
		try {
			return !FileIO.listFiles(dir).isEmpty();
		} catch (IOException e) {
			return false;
		}
	}

	/** Mods the profile has config files for. */
	public Set<String> storedMods(String profile) {
		Set<String> mods = new TreeSet<>();
		Path dir = this.profileDir(profile);
		if (!Files.isDirectory(dir)) {
			return mods;
		}
		try (Stream<Path> stream = Files.list(dir)) {
			stream.filter(Files::isDirectory).forEach(p -> {
				String modId = p.getFileName().toString();
				if (this.hasMod(profile, modId)) {
					mods.add(modId);
				}
			});
		} catch (IOException ignored) {
		}
		return mods;
	}

	/**
	 * Puts the current config files of one mod into the profile. Files of that mod which the mod no longer writes are
	 * removed from the profile, unless nothing could be copied at all — then the profile keeps what it had.
	 */
	public void storeMod(String profile, String modId, Collection<Path> liveFiles) throws IOException {
		Path modDir = this.profileDir(profile).resolve(modId);
		Set<Path> written = new HashSet<>();
		for (Path live : liveFiles) {
			Path rel = this.relativeToConfig(live);
			if (rel == null || !Files.isRegularFile(live)) {
				continue;
			}
			Path dest = modDir.resolve(rel.toString()).normalize();
			FileIO.writeAtomic(dest, Files.readAllBytes(live));
			written.add(dest);
		}
		if (written.isEmpty()) {
			return;
		}
		for (Path file : FileIO.listFiles(modDir)) {
			if (!written.contains(file.normalize())) {
				Files.delete(file);
			}
		}
		FileIO.pruneEmptyDirs(modDir);
	}

	public void storeAll(String profile, Map<String, ? extends Collection<Path>> liveByMod) throws IOException {
		Files.createDirectories(this.profileDir(profile));
		for (Map.Entry<String, ? extends Collection<Path>> entry : liveByMod.entrySet()) {
			this.storeMod(profile, entry.getKey(), entry.getValue());
		}
	}

	/** A file kept in a profile and where it goes when the profile is applied. */
	public record StoredFile(Path source, Path target) {
	}

	public List<StoredFile> storedFiles(String profile, String modId) throws IOException {
		Path modDir = this.profileDir(profile).resolve(modId);
		List<StoredFile> files = new ArrayList<>();
		for (Path source : FileIO.listFiles(modDir)) {
			Path target = this.configDir.resolve(modDir.relativize(source).toString()).normalize();
			if (this.relativeToConfig(target) != null) {
				files.add(new StoredFile(source, target));
			}
		}
		return files;
	}

	public void createEmpty(String profile) throws IOException {
		Files.createDirectories(this.profileDir(profile));
	}

	public void copyProfile(String from, String to) throws IOException {
		FileIO.copyTree(this.profileDir(from), this.profileDir(to));
	}

	public void renameProfile(String from, String to) throws IOException {
		Path source = this.profileDir(from);
		Path target = this.profileDir(to);
		if (!Files.exists(source)) {
			Files.createDirectories(target);
			return;
		}
		// "smp" -> "SMP" on a case-insensitive file system: the target "exists" already, so go through another name.
		Path tmp = this.profilesDir().resolve(".rename-" + System.nanoTime());
		Files.move(source, tmp);
		Files.move(tmp, target);
	}

	/** Moves the profile into {@code trash/} instead of deleting it. */
	public Path trashProfile(String profile) throws IOException {
		Path source = this.profileDir(profile);
		Path target = this.root.resolve("trash").resolve(profile + "_" + LocalDateTime.now().format(STAMP));
		Files.createDirectories(target.getParent());
		if (Files.exists(source)) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
		return target;
	}

	/** Copies the current config files into {@code backups/<label>_<time>/}, laid out like a profile. */
	public Path backup(String label, Map<String, ? extends Collection<Path>> liveByMod) throws IOException {
		Path target = this.root.resolve("backups").resolve(label + "_" + LocalDateTime.now().format(STAMP));
		for (Map.Entry<String, ? extends Collection<Path>> entry : liveByMod.entrySet()) {
			for (Path live : entry.getValue()) {
				Path rel = this.relativeToConfig(live);
				if (rel != null && Files.isRegularFile(live)) {
					Path dest = target.resolve(entry.getKey()).resolve(rel.toString());
					Files.createDirectories(dest.getParent());
					Files.copy(live, dest, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
		Files.createDirectories(target);
		return target;
	}
}
