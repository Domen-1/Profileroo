package com.domen.profileroo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileStoreTest {
	@TempDir
	Path config;

	private Path write(String relative, String content) throws IOException {
		Path file = this.config.resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
		return file;
	}

	@Test
	void storeAndListFiles() throws IOException {
		ProfileStore store = new ProfileStore(this.config, "profileroo");
		Path tweakeroo = this.write("tweakeroo.json", "{\"a\":1}");
		Path nested = this.write("litematica/extra.json", "{}");
		store.storeAll("smp", Map.of("tweakeroo", List.of(tweakeroo), "litematica", List.of(nested)));

		assertEquals("{\"a\":1}", Files.readString(store.profileDir("smp").resolve("tweakeroo/tweakeroo.json")));
		assertEquals(Set.of("litematica", "tweakeroo"), store.storedMods("smp"));
		List<ProfileStore.StoredFile> files = store.storedFiles("smp", "litematica");
		assertEquals(1, files.size());
		assertEquals(nested.toAbsolutePath().normalize(), files.getFirst().target());
		assertTrue(store.hasMod("smp", "tweakeroo"));
		assertFalse(store.hasMod("smp", "minihud"));
	}

	@Test
	void storingAgainDropsFilesTheModNoLongerWrites() throws IOException {
		ProfileStore store = new ProfileStore(this.config, "profileroo");
		Path a = this.write("mod.json", "1");
		Path b = this.write("mod/old.json", "1");
		store.storeMod("p", "mod", List.of(a, b));
		Files.writeString(a, "2");
		store.storeMod("p", "mod", List.of(a));
		assertEquals(1, store.storedFiles("p", "mod").size());
		assertEquals("2", Files.readString(store.profileDir("p").resolve("mod/mod.json")));
		assertFalse(Files.exists(store.profileDir("p").resolve("mod/mod")), "empty folders are pruned");

		// Nothing written this time (the mod failed to save?): keep what the profile has.
		store.storeMod("p", "mod", List.of());
		assertEquals(1, store.storedFiles("p", "mod").size());
	}

	@Test
	void filesOutsideTheConfigFolderAreIgnored() throws IOException {
		ProfileStore store = new ProfileStore(this.config, "profileroo");
		Path outside = Files.writeString(this.config.getParent().resolve("outside-" + System.nanoTime() + ".json"), "x");
		Path own = this.write("profileroo/state.json", "{}");
		assertNull(store.relativeToConfig(outside));
		assertNull(store.relativeToConfig(own));
		store.storeMod("p", "mod", List.of(outside, own));
		assertTrue(store.storedFiles("p", "mod").isEmpty());
		Files.delete(outside);

		// Profile folders copied in by hand must not be able to overwrite this mod's own files.
		Path sneaky = store.profileDir("p").resolve("mod/profileroo/state.json");
		Files.createDirectories(sneaky.getParent());
		Files.writeString(sneaky, "{}");
		assertTrue(store.storedFiles("p", "mod").isEmpty());

		assertFalse(ProfileStore.isSafeModId(".."));
		assertFalse(ProfileStore.isSafeModId("a/b"));
		assertTrue(ProfileStore.isSafeModId("masa_gadget_mod"));
	}

	@Test
	void copyRenameTrash() throws IOException {
		ProfileStore store = new ProfileStore(this.config, "profileroo");
		store.storeMod("smp", "mod", List.of(this.write("mod.json", "1")));
		store.copyProfile("smp", "smp-copy");
		assertEquals(Set.of("mod"), store.storedMods("smp-copy"));

		store.renameProfile("smp", "SMP");
		assertEquals(List.of("SMP", "smp-copy"), store.profileFolders());

		Path trashed = store.trashProfile("SMP");
		assertTrue(Files.exists(trashed.resolve("mod/mod.json")));
		assertEquals(List.of("smp-copy"), store.profileFolders());
	}

	@Test
	void backupKeepsLayout() throws IOException {
		ProfileStore store = new ProfileStore(this.config, "profileroo");
		Path backup = store.backup("initial", Map.of("tweakeroo", List.of(this.write("tweakeroo.json", "t"))));
		assertEquals("t", Files.readString(backup.resolve("tweakeroo/tweakeroo.json")));
	}

	@Test
	void writeLiveFollowsSymlinks() throws IOException {
		Path shared = this.write("shared/real.json", "old");
		Path link = this.config.resolve("linked.json");
		Files.createSymbolicLink(link, shared);
		FileIO.writeLive(link, "new".getBytes());
		assertTrue(Files.isSymbolicLink(link));
		assertEquals("new", Files.readString(shared));
	}
}
