package com.domen.profileroo.profile;

import com.domen.profileroo.core.ProfileState;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import fi.dy.masa.malilib.util.StringUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * One MaLiLib hotkey per profile. They are ordinary {@link ConfigHotkey}s, so the "Profile hotkeys" tab edits them like
 * any other bind, but the values are kept in {@code state.json} next to the profile.
 */
final class ProfileHotkeys {
	private final List<ConfigHotkey> hotkeys = new ArrayList<>();

	List<ConfigHotkey> list() {
		return this.hotkeys;
	}

	/** Copies the binds from the hotkey objects into the profile data. */
	void writeBack(List<ProfileState.ProfileData> profiles) {
		for (ConfigHotkey hotkey : this.hotkeys) {
			for (ProfileState.ProfileData profile : profiles) {
				if (profile.name.equals(hotkey.getName())) {
					profile.hotkey = hotkey.getKeybind().getAsJsonElement().getAsJsonObject();
				}
			}
		}
	}

	/** New hotkey objects for the current profile list, with the binds stored in the profile data. */
	void rebuild(List<ProfileState.ProfileData> profiles) {
		this.writeBack(profiles);
		this.hotkeys.clear();
		for (ProfileState.ProfileData profile : profiles) {
			String name = profile.name;
			ConfigHotkey hotkey = new ConfigHotkey(name, "", KeybindSettings.DEFAULT,
					StringUtils.translate("profileroo.config.profile_hotkey.comment", name), name, name);
			if (profile.hotkey != null) {
				hotkey.getKeybind().setValueFromJsonElement(profile.hotkey);
			}
			hotkey.getKeybind().setCallback((action, key) -> {
				ProfileManager.getInstance().switchByHotkey(name);
				return true;
			});
			this.hotkeys.add(hotkey);
		}
	}
}
