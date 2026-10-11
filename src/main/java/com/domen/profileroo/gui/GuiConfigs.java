package com.domen.profileroo.gui;

import com.domen.profileroo.Reference;
import com.domen.profileroo.config.Configs;
import com.domen.profileroo.profile.ProfileManager;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.util.StringUtils;
import java.util.List;

/**
 * The MaLiLib option lists: generic options, the mod's hotkeys and one hotkey per profile.
 * <p>
 * MaLiLib's config switcher creates an instance of every registered config screen to find out which one is open, so
 * the constructor must not do anything.
 */
public class GuiConfigs extends GuiConfigsBase {
	private static ConfigTab tab = ConfigTab.GENERIC;

	public GuiConfigs() {
		super(10, 50, Reference.MOD_ID, null, "profileroo.gui.title.configs", StringUtils.getModVersionString(Reference.MOD_ID));
	}

	GuiConfigs(ConfigTab tab) {
		this();
		GuiConfigs.tab = tab;
	}

	@Override
	public void initGui() {
		ProfileManager.getInstance().ensureReady();
		super.initGui();
		this.clearOptions();
		ConfigTab.addButtons(this, 10, 26, tab);
	}

	@Override
	protected int getConfigWidth() {
		return tab == ConfigTab.GENERIC ? 200 : 204;
	}

	@Override
	protected boolean useKeybindSearch() {
		return tab != ConfigTab.GENERIC;
	}

	@Override
	public List<ConfigOptionWrapper> getConfigs() {
		return switch (tab) {
			case HOTKEYS -> ConfigOptionWrapper.createFor(Configs.Hotkeys.HOTKEY_LIST);
			case PROFILE_HOTKEYS -> ConfigOptionWrapper.createFor(ProfileManager.getInstance().profileHotkeys());
			default -> ConfigOptionWrapper.createFor(Configs.Generic.OPTIONS);
		};
	}
}
