package com.domen.profileroo.config;

import com.domen.profileroo.Reference;
import com.domen.profileroo.profile.ProfileManager;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import java.nio.file.Files;
import java.nio.file.Path;

public class Configs implements IConfigHandler {
	private static final String CONFIG_FILE_NAME = Reference.MOD_ID + ".json";

	public static class Generic {
		private static final String KEY = Reference.MOD_ID + ".config.generic";

		public static final ConfigBoolean AUTO_SWITCH = new ConfigBoolean("autoSwitch", true).apply(KEY);
		public static final ConfigBoolean REMEMBER_MANUAL_CHOICE = new ConfigBoolean("rememberManualChoice", true).apply(KEY);
		public static final ConfigOptionList MESSAGE_ON_JOIN = new ConfigOptionList("messageOnJoin", MessageMode.CHAT).apply(KEY);
		public static final ConfigOptionList MESSAGE_ON_SWITCH = new ConfigOptionList("messageOnSwitch", MessageMode.ACTIONBAR).apply(KEY);
		public static final ConfigStringList SHARED_OPTIONS = new ConfigStringList("sharedOptions", ImmutableList.of()).apply(KEY);
		public static final ConfigBoolean DEBUG_LOG = new ConfigBoolean("debugLog", false).apply(KEY);

		public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
				AUTO_SWITCH,
				REMEMBER_MANUAL_CHOICE,
				MESSAGE_ON_JOIN,
				MESSAGE_ON_SWITCH,
				SHARED_OPTIONS,
				DEBUG_LOG
		);
	}

	public static class Hotkeys {
		private static final String KEY = Reference.MOD_ID + ".config.hotkey";

		public static final ConfigHotkey OPEN_GUI = new ConfigHotkey("openGui", "B,C").apply(KEY);
		public static final ConfigHotkey NEXT_PROFILE = new ConfigHotkey("nextProfile", "").apply(KEY);
		public static final ConfigHotkey PREVIOUS_PROFILE = new ConfigHotkey("previousProfile", "").apply(KEY);
		public static final ConfigHotkey SAVE_PROFILE = new ConfigHotkey("saveProfile", "").apply(KEY);
		public static final ConfigHotkey RESET_PROFILE = new ConfigHotkey("resetProfile", "").apply(KEY);
		public static final ConfigHotkey TOGGLE_AUTO_SWITCH = new ConfigHotkey("toggleAutoSwitch", "").apply(KEY);

		public static final ImmutableList<ConfigHotkey> HOTKEY_LIST = ImmutableList.of(
				OPEN_GUI,
				NEXT_PROFILE,
				PREVIOUS_PROFILE,
				SAVE_PROFILE,
				RESET_PROFILE,
				TOGGLE_AUTO_SWITCH
		);
	}

	public static void loadFromFile() {
		Path configFile = FileUtils.getConfigDirectory().resolve(CONFIG_FILE_NAME);
		if (Files.isRegularFile(configFile)) {
			JsonElement element = JsonUtils.parseJsonFile(configFile);
			if (element != null && element.isJsonObject()) {
				JsonObject root = element.getAsJsonObject();
				ConfigUtils.readConfigBase(root, "Generic", Generic.OPTIONS);
				ConfigUtils.readConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);
			}
		}
	}

	public static void saveToFile() {
		Path dir = FileUtils.getConfigDirectory();
		if (!Files.exists(dir)) {
			FileUtils.createDirectoriesIfMissing(dir);
		}
		JsonObject root = new JsonObject();
		ConfigUtils.writeConfigBase(root, "Generic", Generic.OPTIONS);
		ConfigUtils.writeConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);
		JsonUtils.writeJsonToFile(root, dir.resolve(CONFIG_FILE_NAME));
	}

	@Override
	public void load() {
		loadFromFile();
	}

	@Override
	public void save() {
		saveToFile();
		// Profile hotkeys are edited in the same config screen but live in state.json next to the profiles.
		ProfileManager.getInstance().saveState();
	}

	@Override
	public void onLanguageChanged(String newLang) {
		// Profile hotkey comments are built from a translation with the profile name in it.
		ProfileManager.getInstance().rebuildHotkeys();
	}
}
