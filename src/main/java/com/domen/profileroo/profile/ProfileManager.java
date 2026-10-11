package com.domen.profileroo.profile;

import com.domen.profileroo.Profileroo;
import com.domen.profileroo.Reference;
import com.domen.profileroo.config.Configs;
import com.domen.profileroo.core.FileIO;
import com.domen.profileroo.core.ProfileNames;
import com.domen.profileroo.core.ProfileState;
import com.domen.profileroo.core.ProfileState.ProfileData;
import com.domen.profileroo.core.ProfileState.Resolution;
import com.domen.profileroo.core.ProfileState.ServerRule;
import com.domen.profileroo.core.ProfileStore;
import com.domen.profileroo.core.SessionInfo;
import com.domen.profileroo.core.SharedOptions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.StringUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jetbrains.annotations.Nullable;

/**
 * Everything that changes profiles goes through here.
 * <p>
 * The config files in {@code config/} always hold the active profile. Switching saves every managed mod's config
 * (MaLiLib handlers write their files), copies those files into the active profile, copies the target profile's files
 * over them and makes the mods read them again. On joining a world this happens just before MaLiLib loads all configs
 * by itself, so there is no reload of our own then.
 */
public final class ProfileManager {
	private static final ProfileManager INSTANCE = new ProfileManager();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public enum Cause {
		JOIN,
		MANUAL,
		HOTKEY,
		COMMAND
	}

	@Nullable
	private ProfileStore store;
	private ProfileState state = ProfileState.defaults();
	private final ProfileHotkeys hotkeys = new ProfileHotkeys();
	private boolean stateLoaded;
	private boolean ready;
	@Nullable
	private SessionInfo session;
	@Nullable
	private SessionInfo lastSession;
	@Nullable
	private OpResult joinMessage;
	private final Map<String, List<Path>> knownFiles = new TreeMap<>();

	private ProfileManager() {
	}

	public static ProfileManager getInstance() {
		return INSTANCE;
	}

	public ProfileStore store() {
		if (this.store == null) {
			this.store = new ProfileStore(FileUtils.getConfigDirectory(), Reference.MOD_ID);
		}
		return this.store;
	}

	// ----------------------------------------------------------------------------------------------------------------
	// state.json

	public void loadState() {
		Path file = this.store().stateFile();
		ProfileState loaded = null;
		if (Files.isRegularFile(file)) {
			try {
				JsonElement element = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
				if (element instanceof JsonObject obj) {
					loaded = ProfileState.fromJson(obj);
				}
			} catch (IOException | RuntimeException e) {
				Profileroo.LOGGER.error("Could not read {}, starting over (profile folders are kept)", file, e);
				try {
					Files.copy(file, file.resolveSibling("state.json.broken"), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ignored) {
				}
			}
		}
		this.state = loaded != null ? loaded : ProfileState.defaults();
		this.stateLoaded = true;
		this.hotkeys.rebuild(this.state.profiles);
	}

	public void saveState() {
		if (!this.stateLoaded) {
			return;
		}
		this.hotkeys.writeBack(this.state.profiles);
		try {
			FileIO.writeAtomic(this.store().stateFile(), GSON.toJson(this.state.toJson()).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			Profileroo.LOGGER.error("Could not save {}", this.store().stateFile(), e);
		}
	}

	/**
	 * First real use in a session (after MaLiLib has loaded all configs): picks up profile folders copied in by hand,
	 * creates the first profile from the current configs and finishes a switch the game was closed in the middle of.
	 */
	public void ensureReady() {
		if (this.ready) {
			return;
		}
		this.ready = true;
		try {
			Files.createDirectories(this.store().profilesDir());
			for (String folder : this.store().profileFolders()) {
				if (ProfileNames.problem(folder) == null && this.state.find(folder) == null) {
					this.state.profiles.add(new ProfileData(folder));
				}
			}
			if (this.state.find(this.state.active) == null) {
				// The current configs belong to no profile yet: keep them as one, so nothing is lost or overwritten.
				Map<String, List<Path>> live = this.captureLive();
				if (this.state.profiles.isEmpty()) {
					Path backup = this.store().backup("initial", live);
					Profileroo.LOGGER.info("First start: the current configs are copied to {}", backup);
				}
				String name = ProfileNames.unique(ProfileState.DEFAULT_PROFILE, n -> this.state.find(n) != null);
				this.store().storeAll(name, live);
				this.state.profiles.add(new ProfileData(name));
				this.state.active = name;
			}
			this.state.active = this.state.find(this.state.active).name;
			ProfileData pending = this.state.find(this.state.pendingApply);
			if (pending != null) {
				Profileroo.LOGGER.warn("The game stopped while profile '{}' was being applied, applying it again", pending.name);
				this.state.active = pending.name;
				this.reload(this.applyFiles(pending.name));
			}
			this.state.pendingApply = null;
		} catch (IOException e) {
			Profileroo.LOGGER.error("Could not set up the profiles", e);
		}
		this.hotkeys.rebuild(this.state.profiles);
		this.saveState();
		InputEventHandler.getKeybindManager().updateUsedKeys();
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Reading

	public List<ProfileData> profiles() {
		return this.state.profiles;
	}

	public String activeName() {
		return this.state.active;
	}

	@Nullable
	public ProfileData find(@Nullable String name) {
		return this.state.find(name);
	}

	public List<ServerRule> rules() {
		return this.state.rules;
	}

	public List<ConfigHotkey> profileHotkeys() {
		return this.hotkeys.list();
	}

	@Nullable
	public SessionInfo session() {
		return this.session;
	}

	@Nullable
	public Resolution resolveCurrent() {
		return this.session == null ? null : this.state.resolve(this.session);
	}

	public String fallback(boolean singleplayer) {
		return singleplayer ? this.state.fallbackSingleplayer : this.state.fallbackMultiplayer;
	}

	public Set<String> storedMods(String profile) {
		return this.store().storedMods(profile);
	}

	public int ruleCount(String profile) {
		return this.state.ruleCount(profile);
	}

	public boolean isExcluded(String modId) {
		return this.state.isExcluded(modId);
	}

	/** Managed mods: every MaLiLib mod with a config except the excluded ones. */
	public List<String> managedMods() {
		List<String> mods = new ArrayList<>();
		for (String modId : ManagedMods.allModIds()) {
			if (!this.state.isExcluded(modId)) {
				mods.add(modId);
			}
		}
		return mods;
	}

	/** Files the mod wrote the last time it was saved by this mod, or its usual file when it was not saved yet. */
	public List<Path> knownFiles(String modId) {
		List<Path> files = this.knownFiles.get(modId);
		if (files != null) {
			return files;
		}
		Path usual = this.store().configDir().resolve(modId + ".json");
		return Files.isRegularFile(usual) ? List.of(usual) : List.of();
	}

	/** Saves every MaLiLib mod's config once, to learn which files each one uses. */
	public void refreshKnownFiles() {
		for (String modId : ManagedMods.allModIds()) {
			this.knownFiles.put(modId, ManagedMods.saveAndCapture(modId, this.store()));
		}
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Switching

	public OpResult switchTo(String name, Cause cause) {
		this.ensureReady();
		ProfileData target = this.state.find(name);
		if (target == null) {
			return OpResult.error("profileroo.message.error.no_such_profile", name);
		}
		if (target.name.equals(this.state.active)) {
			return OpResult.info("profileroo.message.already_active", target.name);
		}
		OpResult result = this.doSwitch(target, cause != Cause.JOIN);
		// Remembered rules only matter when joining switches profiles, so nothing is remembered while that is off.
		if (result.ok() && cause != Cause.JOIN && this.session != null && Configs.Generic.REMEMBER_MANUAL_CHOICE.getBooleanValue()
				&& Configs.Generic.AUTO_SWITCH.getBooleanValue()) {
			Resolution resolution = this.state.resolve(this.session);
			// Only where no rule decides: an explicit rule is the player's word and a manual switch may be temporary.
			if (resolution.isFallback() && !target.name.equalsIgnoreCase(String.valueOf(resolution.profile()))) {
				this.state.rules.add(new ServerRule(this.session.exactPattern(), target.name));
				this.saveState();
				return OpResult.success("profileroo.message.switched_remembered", target.name, this.session.label());
			}
		}
		return result;
	}

	public boolean isAutoSwitch() {
		return Configs.Generic.AUTO_SWITCH.getBooleanValue();
	}

	/** Turns switching on join on or off; manual switching keeps working either way. */
	public OpResult setAutoSwitch(boolean enabled) {
		Configs.Generic.AUTO_SWITCH.setBooleanValue(enabled);
		Configs.saveToFile();
		return enabled
				? OpResult.success("profileroo.message.auto_on")
				: OpResult.warning("profileroo.message.auto_off");
	}

	public void switchByHotkey(String name) {
		Messages.show(this.switchTo(name, Cause.HOTKEY), Configs.Generic.MESSAGE_ON_SWITCH);
	}

	public OpResult cycle(int direction) {
		this.ensureReady();
		int count = this.state.profiles.size();
		if (count < 2) {
			return OpResult.info("profileroo.message.only_one_profile");
		}
		int index = Math.max(0, this.state.indexOf(this.state.active));
		return this.switchTo(this.state.profiles.get(Math.floorMod(index + direction, count)).name, Cause.HOTKEY);
	}

	private OpResult doSwitch(ProfileData target, boolean reloadNow) {
		ProfileData current = this.state.find(this.state.active);
		try {
			Map<String, List<Path>> live = this.captureLive();
			if (current != null) {
				this.storeInto(current, live, false);
			}
			this.state.active = target.name;
			this.state.pendingApply = target.name;
			this.saveState();
			Set<String> applied = this.applyFiles(target.name);
			this.state.pendingApply = null;
			this.saveState();
			if (reloadNow) {
				this.reload(applied);
			}
			Profileroo.LOGGER.info("Switched profile {} -> {} ({})", current == null ? "-" : current.name, target.name, applied);
			return OpResult.success("profileroo.message.switched", target.name);
		} catch (IOException e) {
			Profileroo.LOGGER.error("Switching to profile '{}' failed", target.name, e);
			return OpResult.error("profileroo.message.error.io", e.getMessage());
		}
	}

	/** Saves every managed mod and returns the config files of each one that has any. */
	private Map<String, List<Path>> captureLive() {
		Map<String, List<Path>> live = new LinkedHashMap<>();
		for (String modId : this.managedMods()) {
			List<Path> files = ManagedMods.saveAndCapture(modId, this.store());
			this.knownFiles.put(modId, files);
			if (!files.isEmpty()) {
				live.put(modId, files);
			}
		}
		return live;
	}

	private void storeInto(ProfileData profile, Map<String, List<Path>> live, boolean force) throws IOException {
		if (profile.locked && !force) {
			// A locked profile keeps its saved state and only picks up mods it has never had.
			Map<String, List<Path>> missing = new LinkedHashMap<>();
			live.forEach((modId, files) -> {
				if (!this.store().hasMod(profile.name, modId)) {
					missing.put(modId, files);
				}
			});
			this.store().storeAll(profile.name, missing);
		} else {
			this.store().storeAll(profile.name, live);
		}
	}

	/** Writes the profile's files into the config folder. Returns the mods that got files. */
	private Set<String> applyFiles(String profile) throws IOException {
		SharedOptions shared = SharedOptions.parse(Configs.Generic.SHARED_OPTIONS.getStrings());
		Set<String> applied = new LinkedHashSet<>();
		for (String modId : this.managedMods()) {
			List<ProfileStore.StoredFile> files = this.store().storedFiles(profile, modId);
			for (ProfileStore.StoredFile file : files) {
				byte[] data = Files.readAllBytes(file.source());
				if (shared.affects(modId) && file.target().getFileName().toString().endsWith(".json")) {
					data = mergeShared(shared, modId, data, file.target());
				}
				FileIO.writeLive(file.target(), data);
			}
			if (!files.isEmpty()) {
				applied.add(modId);
			}
		}
		return applied;
	}

	private static byte[] mergeShared(SharedOptions shared, String modId, byte[] data, Path liveFile) {
		if (!Files.isRegularFile(liveFile)) {
			return data;
		}
		try {
			JsonElement incoming = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
			JsonElement live = JsonParser.parseString(Files.readString(liveFile, StandardCharsets.UTF_8));
			if (incoming instanceof JsonObject incomingObj && live instanceof JsonObject liveObj) {
				return GSON.toJson(shared.merge(modId, incomingObj, liveObj)).getBytes(StandardCharsets.UTF_8);
			}
		} catch (IOException | RuntimeException e) {
			Profileroo.LOGGER.warn("Could not keep the shared options of {} in {}", modId, liveFile, e);
		}
		return data;
	}

	private void reload(Collection<String> mods) {
		for (String modId : mods) {
			ManagedMods.reload(modId);
		}
		InputEventHandler.getKeybindManager().updateUsedKeys();
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Joining and leaving

	/** Called right before MaLiLib loads all configs for the new world. */
	public void onJoin(@Nullable SessionInfo info, boolean reconfiguration) {
		boolean sameServer = reconfiguration && info != null && this.lastSession != null && this.lastSession.sameAs(info);
		this.session = info;
		if (info != null) {
			this.lastSession = info;
		}
		this.joinMessage = null;
		this.ensureReady();
		if (info == null || !Configs.Generic.AUTO_SWITCH.getBooleanValue()) {
			return;
		}
		Resolution resolution = this.state.resolve(info);
		if (resolution.profile() == null) {
			return;
		}
		String reason = resolution.rule() != null
				? StringUtils.translate("profileroo.label.reason_rule", resolution.rule().pattern)
				: StringUtils.translate(info.isSingleplayer() ? "profileroo.label.fallback.singleplayer" : "profileroo.label.fallback.multiplayer");
		ProfileData target = this.state.find(resolution.profile());
		if (target == null) {
			this.joinMessage = OpResult.warning("profileroo.message.error.rule_profile_missing", resolution.profile(), reason);
			return;
		}
		if (target.name.equals(this.state.active)) {
			// A locked profile starts every visit from its saved state, but a proxy moving us between its servers is
			// the same visit.
			if (target.locked && !sameServer) {
				try {
					this.captureLive();
					this.applyFiles(target.name);
					this.joinMessage = OpResult.info("profileroo.message.join_reset", target.name);
				} catch (IOException e) {
					Profileroo.LOGGER.error("Resetting locked profile '{}' failed", target.name, e);
				}
			}
			return;
		}
		OpResult result = this.doSwitch(target, false);
		this.joinMessage = result.ok() ? OpResult.success("profileroo.message.join_switched", target.name, reason) : result;
	}

	/** Called once the world is there, so the message has somewhere to show. */
	public void onJoinFinished() {
		if (this.joinMessage != null) {
			Messages.show(this.joinMessage, Configs.Generic.MESSAGE_ON_JOIN);
			this.joinMessage = null;
		}
	}

	/** Called after MaLiLib saved all configs on leaving a world. */
	public void onLeave() {
		this.session = null;
		if (!this.ready) {
			return;
		}
		ProfileData active = this.state.find(this.state.active);
		if (active != null) {
			try {
				this.storeInto(active, this.captureLive(), false);
			} catch (IOException e) {
				Profileroo.LOGGER.error("Saving profile '{}' on leaving failed", active.name, e);
			}
		}
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Profile operations

	@Nullable
	private OpResult checkNewName(String name) {
		String problem = ProfileNames.problem(name);
		if (problem != null) {
			return OpResult.error(problem, name, ProfileNames.MAX_LENGTH);
		}
		if (this.state.find(name) != null) {
			return OpResult.error("profileroo.message.error.name_taken", name);
		}
		return null;
	}

	private void profilesChanged() {
		this.hotkeys.rebuild(this.state.profiles);
		this.saveState();
		InputEventHandler.getKeybindManager().updateUsedKeys();
	}

	public void rebuildHotkeys() {
		if (this.stateLoaded) {
			this.hotkeys.rebuild(this.state.profiles);
			InputEventHandler.getKeybindManager().updateUsedKeys();
		}
	}

	/** A new profile with the current settings of every managed mod. */
	public OpResult create(String name) {
		this.ensureReady();
		OpResult problem = this.checkNewName(name);
		if (problem != null) {
			return problem;
		}
		try {
			this.store().createEmpty(name);
			this.store().storeAll(name, this.captureLive());
		} catch (IOException e) {
			Profileroo.LOGGER.error("Creating profile '{}' failed", name, e);
			return OpResult.error("profileroo.message.error.io", e.getMessage());
		}
		this.state.profiles.add(new ProfileData(name));
		this.profilesChanged();
		return OpResult.success("profileroo.message.created", name);
	}

	public OpResult duplicate(String from, String to) {
		this.ensureReady();
		ProfileData source = this.state.find(from);
		if (source == null) {
			return OpResult.error("profileroo.message.error.no_such_profile", from);
		}
		OpResult problem = this.checkNewName(to);
		if (problem != null) {
			return problem;
		}
		try {
			if (source.name.equals(this.state.active) && !source.locked) {
				// The active profile's folder lags behind the game until the next switch: bring it up to date first.
				this.storeInto(source, this.captureLive(), false);
			}
			this.store().copyProfile(source.name, to);
		} catch (IOException e) {
			Profileroo.LOGGER.error("Copying profile '{}' to '{}' failed", source.name, to, e);
			return OpResult.error("profileroo.message.error.io", e.getMessage());
		}
		ProfileData copy = new ProfileData(to);
		copy.locked = source.locked;
		this.state.profiles.add(this.state.profiles.indexOf(source) + 1, copy);
		this.profilesChanged();
		return OpResult.success("profileroo.message.duplicated", source.name, to);
	}

	public OpResult rename(String from, String to) {
		this.ensureReady();
		ProfileData profile = this.state.find(from);
		if (profile == null) {
			return OpResult.error("profileroo.message.error.no_such_profile", from);
		}
		if (profile.name.equals(to)) {
			return OpResult.info("profileroo.message.nothing_changed");
		}
		String problem = ProfileNames.problem(to);
		if (problem != null) {
			return OpResult.error(problem, to, ProfileNames.MAX_LENGTH);
		}
		ProfileData existing = this.state.find(to);
		if (existing != null && existing != profile) {
			return OpResult.error("profileroo.message.error.name_taken", to);
		}
		try {
			this.store().renameProfile(profile.name, to);
		} catch (IOException e) {
			Profileroo.LOGGER.error("Renaming profile '{}' to '{}' failed", profile.name, to, e);
			return OpResult.error("profileroo.message.error.io", e.getMessage());
		}
		String old = profile.name;
		this.hotkeys.writeBack(this.state.profiles);
		this.state.renameReferences(old, to);
		profile.name = to;
		this.profilesChanged();
		return OpResult.success("profileroo.message.renamed", old, to);
	}

	public OpResult delete(String name) {
		this.ensureReady();
		ProfileData profile = this.state.find(name);
		if (profile == null) {
			return OpResult.error("profileroo.message.error.no_such_profile", name);
		}
		if (profile.name.equals(this.state.active)) {
			return OpResult.error("profileroo.message.error.delete_active", profile.name);
		}
		try {
			this.store().trashProfile(profile.name);
		} catch (IOException e) {
			Profileroo.LOGGER.error("Deleting profile '{}' failed", profile.name, e);
			return OpResult.error("profileroo.message.error.io", e.getMessage());
		}
		this.state.profiles.remove(profile);
		int rules = this.state.removeReferences(profile.name);
		this.profilesChanged();
		return OpResult.success("profileroo.message.deleted", profile.name, rules);
	}

	/** Replaces what the profile holds with the current settings, locked or not. */
	public OpResult overwrite(String name) {
		this.ensureReady();
		ProfileData profile = this.state.find(name);
		if (profile == null) {
			return OpResult.error("profileroo.message.error.no_such_profile", name);
		}
		try {
			this.storeInto(profile, this.captureLive(), true);
		} catch (IOException e) {
			Profileroo.LOGGER.error("Saving into profile '{}' failed", profile.name, e);
			return OpResult.error("profileroo.message.error.io", e.getMessage());
		}
		return OpResult.success("profileroo.message.saved", profile.name);
	}

	public OpResult saveActive() {
		this.ensureReady();
		return this.overwrite(this.state.active);
	}

	/** Throws away the changes made since the active profile was last saved. */
	public OpResult resetActive() {
		this.ensureReady();
		ProfileData profile = this.state.find(this.state.active);
		if (profile == null) {
			return OpResult.error("profileroo.message.error.no_such_profile", this.state.active);
		}
		try {
			this.captureLive();
			this.reload(this.applyFiles(profile.name));
		} catch (IOException e) {
			Profileroo.LOGGER.error("Resetting profile '{}' failed", profile.name, e);
			return OpResult.error("profileroo.message.error.io", e.getMessage());
		}
		return OpResult.success("profileroo.message.reset", profile.name);
	}

	public OpResult setLocked(String name, boolean locked) {
		this.ensureReady();
		ProfileData profile = this.state.find(name);
		if (profile == null) {
			return OpResult.error("profileroo.message.error.no_such_profile", name);
		}
		if (locked && profile.name.equals(this.state.active)) {
			// Locking the profile in use freezes it as it is now.
			try {
				this.storeInto(profile, this.captureLive(), true);
			} catch (IOException e) {
				return OpResult.error("profileroo.message.error.io", e.getMessage());
			}
		}
		profile.locked = locked;
		this.saveState();
		return OpResult.success(locked ? "profileroo.message.locked" : "profileroo.message.unlocked", profile.name);
	}

	public void move(String name, int delta) {
		ProfileData profile = this.state.find(name);
		if (profile == null) {
			return;
		}
		int from = this.state.profiles.indexOf(profile);
		int to = Math.max(0, Math.min(this.state.profiles.size() - 1, from + delta));
		if (from != to) {
			this.state.profiles.remove(from);
			this.state.profiles.add(to, profile);
			this.profilesChanged();
		}
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Rules and mods

	public void addRule(String pattern, String profile) {
		this.state.rules.add(new ServerRule(pattern, profile));
		this.saveState();
	}

	public void setRulePattern(int index, String pattern) {
		if (index >= 0 && index < this.state.rules.size()) {
			this.state.rules.get(index).pattern = pattern.trim();
			this.saveState();
		}
	}

	public void setRuleProfile(int index, String profile) {
		if (index >= 0 && index < this.state.rules.size()) {
			this.state.rules.get(index).profile = profile;
			this.saveState();
		}
	}

	public void moveRule(int index, int delta) {
		int to = index + delta;
		if (index >= 0 && index < this.state.rules.size() && to >= 0 && to < this.state.rules.size()) {
			this.state.rules.add(to, this.state.rules.remove(index));
			this.saveState();
		}
	}

	public void removeRule(int index) {
		if (index >= 0 && index < this.state.rules.size()) {
			this.state.rules.remove(index);
			this.saveState();
		}
	}

	public void setFallback(boolean singleplayer, String profile) {
		if (singleplayer) {
			this.state.fallbackSingleplayer = profile;
		} else {
			this.state.fallbackMultiplayer = profile;
		}
		this.saveState();
	}

	/** Puts a rule for the current server or world at the top of the list, or retargets the one that is there. */
	public OpResult bindCurrent(@Nullable String profileName) {
		this.ensureReady();
		if (this.session == null) {
			return OpResult.error("profileroo.message.error.not_connected");
		}
		ProfileData profile = this.state.find(profileName == null ? this.state.active : profileName);
		if (profile == null) {
			return OpResult.error("profileroo.message.error.no_such_profile", profileName);
		}
		String pattern = this.session.exactPattern();
		ServerRule existing = null;
		for (ServerRule rule : this.state.rules) {
			if (rule.pattern.equalsIgnoreCase(pattern)) {
				existing = rule;
				break;
			}
		}
		if (existing != null) {
			this.state.rules.remove(existing);
		}
		this.state.rules.add(0, new ServerRule(pattern, profile.name));
		this.saveState();
		return OpResult.success("profileroo.message.bound", pattern, profile.name);
	}

	public OpResult unbindCurrent() {
		if (this.session == null) {
			return OpResult.error("profileroo.message.error.not_connected");
		}
		String pattern = this.session.exactPattern();
		int before = this.state.rules.size();
		this.state.rules.removeIf(rule -> rule.pattern.equalsIgnoreCase(pattern));
		this.saveState();
		return before == this.state.rules.size()
				? OpResult.info("profileroo.message.no_exact_rule", pattern)
				: OpResult.success("profileroo.message.unbound", pattern);
	}

	public void setExcluded(String modId, boolean excluded) {
		this.state.excludedMods.removeIf(id -> id.equalsIgnoreCase(modId));
		if (excluded) {
			this.state.excludedMods.add(modId);
		}
		this.saveState();
	}
}
