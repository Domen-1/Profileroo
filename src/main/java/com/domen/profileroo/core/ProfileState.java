package com.domen.profileroo.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Everything in {@code state.json}: the profile list with their settings, which one is active, server rules and the
 * profiles for places no rule covers. Pure data; {@link ProfileManager} owns the instance.
 */
public final class ProfileState {
	public static final String DEFAULT_PROFILE = "default";

	public String active = "";
	/** Set while a profile's files are being written into the config folder; a crash leaves it set, see bootstrap. */
	@Nullable
	public String pendingApply;
	/** Profile for servers no rule matches; empty — keep the current one. LAN and Realms use it too. */
	public String fallbackMultiplayer = DEFAULT_PROFILE;
	/** Profile for singleplayer worlds no rule matches; empty — keep the current one. */
	public String fallbackSingleplayer = DEFAULT_PROFILE;
	/** MaLiLib mods whose configs profiles do not touch. */
	public final List<String> excludedMods = new ArrayList<>();
	public final List<ProfileData> profiles = new ArrayList<>();
	public final List<ServerRule> rules = new ArrayList<>();

	public static ProfileState defaults() {
		ProfileState state = new ProfileState();
		// MaLiLib's own config is global stuff (config menu hotkey, message settings), not something to switch per server.
		state.excludedMods.add("malilib");
		return state;
	}

	public static final class ProfileData {
		public String name;
		/** Changes made while the profile is active are not saved into it; every join starts from the saved state. */
		public boolean locked;
		/** The profile's switch hotkey as MaLiLib stores keybinds: {@code {"keys": "...", "settings": {...}}}. */
		@Nullable
		public JsonObject hotkey;

		public ProfileData(String name) {
			this.name = name;
		}
	}

	public static final class ServerRule {
		public String pattern;
		public String profile;

		public ServerRule(String pattern, String profile) {
			this.pattern = pattern;
			this.profile = profile;
		}
	}

	/**
	 * Which profile a place should use: the first matching rule, or the fallback for its kind. {@code profile} is null
	 * when nothing should be switched.
	 */
	public record Resolution(@Nullable String profile, @Nullable ServerRule rule, int ruleIndex) {
		public boolean isFallback() {
			return this.rule == null;
		}
	}

	public Resolution resolve(SessionInfo session) {
		for (int i = 0; i < this.rules.size(); i++) {
			ServerRule rule = this.rules.get(i);
			if (RulePattern.matches(rule.pattern, session)) {
				return new Resolution(rule.profile, rule, i);
			}
		}
		String fallback = session.isSingleplayer() ? this.fallbackSingleplayer : this.fallbackMultiplayer;
		return new Resolution(fallback.isEmpty() ? null : fallback, null, -1);
	}

	@Nullable
	public ProfileData find(@Nullable String name) {
		if (name == null) {
			return null;
		}
		for (ProfileData profile : this.profiles) {
			if (profile.name.equalsIgnoreCase(name)) {
				return profile;
			}
		}
		return null;
	}

	public int indexOf(@Nullable String name) {
		ProfileData profile = this.find(name);
		return profile == null ? -1 : this.profiles.indexOf(profile);
	}

	public boolean isExcluded(String modId) {
		for (String excluded : this.excludedMods) {
			if (excluded.equalsIgnoreCase(modId)) {
				return true;
			}
		}
		return false;
	}

	/** Points rules, fallbacks and the active profile at the new name. */
	public void renameReferences(String from, String to) {
		for (ServerRule rule : this.rules) {
			if (rule.profile.equalsIgnoreCase(from)) {
				rule.profile = to;
			}
		}
		if (this.fallbackMultiplayer.equalsIgnoreCase(from)) {
			this.fallbackMultiplayer = to;
		}
		if (this.fallbackSingleplayer.equalsIgnoreCase(from)) {
			this.fallbackSingleplayer = to;
		}
		if (this.active.equalsIgnoreCase(from)) {
			this.active = to;
		}
		if (this.pendingApply != null && this.pendingApply.equalsIgnoreCase(from)) {
			this.pendingApply = to;
		}
	}

	/** Drops rules that use the profile and clears fallbacks pointing at it. Returns how many rules went away. */
	public int removeReferences(String name) {
		int before = this.rules.size();
		this.rules.removeIf(rule -> rule.profile.equalsIgnoreCase(name));
		if (this.fallbackMultiplayer.equalsIgnoreCase(name)) {
			this.fallbackMultiplayer = "";
		}
		if (this.fallbackSingleplayer.equalsIgnoreCase(name)) {
			this.fallbackSingleplayer = "";
		}
		return before - this.rules.size();
	}

	public int ruleCount(String profile) {
		int count = 0;
		for (ServerRule rule : this.rules) {
			if (rule.profile.equalsIgnoreCase(profile)) {
				count++;
			}
		}
		return count;
	}

	public JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.addProperty("active", this.active);
		if (this.pendingApply != null) {
			root.addProperty("pendingApply", this.pendingApply);
		}
		root.addProperty("fallbackMultiplayer", this.fallbackMultiplayer);
		root.addProperty("fallbackSingleplayer", this.fallbackSingleplayer);
		JsonArray excluded = new JsonArray();
		this.excludedMods.forEach(excluded::add);
		root.add("excludedMods", excluded);

		JsonArray profiles = new JsonArray();
		for (ProfileData profile : this.profiles) {
			JsonObject obj = new JsonObject();
			obj.addProperty("name", profile.name);
			obj.addProperty("locked", profile.locked);
			if (profile.hotkey != null) {
				obj.add("hotkey", profile.hotkey.deepCopy());
			}
			profiles.add(obj);
		}
		root.add("profiles", profiles);

		JsonArray rules = new JsonArray();
		for (ServerRule rule : this.rules) {
			JsonObject obj = new JsonObject();
			obj.addProperty("pattern", rule.pattern);
			obj.addProperty("profile", rule.profile);
			rules.add(obj);
		}
		root.add("rules", rules);
		return root;
	}

	/** Reads what it can: broken entries are skipped, missing fields keep their defaults. */
	public static ProfileState fromJson(JsonObject root) {
		ProfileState state = new ProfileState();
		state.active = string(root, "active", "");
		String pending = string(root, "pendingApply", "");
		state.pendingApply = pending.isEmpty() ? null : pending;
		state.fallbackMultiplayer = string(root, "fallbackMultiplayer", DEFAULT_PROFILE);
		state.fallbackSingleplayer = string(root, "fallbackSingleplayer", DEFAULT_PROFILE);

		if (root.get("excludedMods") instanceof JsonArray excluded) {
			for (JsonElement element : excluded) {
				if (element.isJsonPrimitive()) {
					state.excludedMods.add(element.getAsString());
				}
			}
		} else {
			state.excludedMods.add("malilib");
		}

		if (root.get("profiles") instanceof JsonArray profiles) {
			for (JsonElement element : profiles) {
				if (element instanceof JsonObject obj) {
					String name = string(obj, "name", "");
					if (ProfileNames.problem(name) != null || state.find(name) != null) {
						continue;
					}
					ProfileData profile = new ProfileData(name);
					profile.locked = obj.get("locked") != null && obj.get("locked").isJsonPrimitive() && obj.get("locked").getAsBoolean();
					profile.hotkey = obj.get("hotkey") instanceof JsonObject hotkey ? hotkey.deepCopy() : null;
					state.profiles.add(profile);
				}
			}
		}

		if (root.get("rules") instanceof JsonArray rules) {
			for (JsonElement element : rules) {
				if (element instanceof JsonObject obj) {
					String pattern = string(obj, "pattern", "");
					String profile = string(obj, "profile", "");
					if (!profile.isEmpty()) {
						state.rules.add(new ServerRule(pattern, profile));
					}
				}
			}
		}
		return state;
	}

	private static String string(JsonObject obj, String key, String fallback) {
		JsonElement element = obj.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
	}

	public static String lower(String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
