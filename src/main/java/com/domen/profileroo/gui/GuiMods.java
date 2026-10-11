package com.domen.profileroo.gui;

import com.domen.profileroo.gui.widgets.WidgetRow;
import com.domen.profileroo.gui.widgets.WidgetRowList;
import com.domen.profileroo.profile.ManagedMods;
import com.domen.profileroo.profile.ProfileManager;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.util.StringUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Which MaLiLib mods the profiles switch. */
public class GuiMods extends GuiRowListBase<String> {
	public GuiMods() {
		super(ConfigTab.MODS, "profileroo.gui.title.mods");
		// Saving each mod once shows which files it uses; done when the screen opens, not on every refresh.
		ProfileManager.getInstance().refreshKnownFiles();
	}

	@Override
	protected void initControls(int x, int y) {
		this.addLabel(x, y + 1, -1, 9, 0xFFC0C0C0, StringUtils.translate("profileroo.gui.label.mods_help_1"));
		this.addLabel(x, y + 11, -1, 9, 0xFFC0C0C0, StringUtils.translate("profileroo.gui.label.mods_help_2"));
	}

	@Override
	protected Collection<String> rows() {
		return ManagedMods.allModIds();
	}

	@Override
	protected WidgetRow<String> createRow(int x, int y, int width, int height, boolean isOdd, String entry, int index, WidgetRowList<String> list) {
		return new Row(x, y, width, height, isOdd, entry, index, list);
	}

	private class Row extends WidgetRow<String> {
		private final String modId;
		private final boolean excluded;

		Row(int x, int y, int width, int height, boolean isOdd, String modId, int index, WidgetRowList<String> list) {
			super(x, y, width, height, isOdd, modId, index, list);
			this.modId = modId;
			ProfileManager manager = ProfileManager.getInstance();
			this.excluded = manager.isExcluded(modId);
			String label = this.excluded
					? GuiBase.TXT_RED + StringUtils.translate("profileroo.gui.button.mod_excluded")
					: GuiBase.TXT_GREEN + StringUtils.translate("profileroo.gui.button.mod_included");
			this.addButtonLeft(label, (b, m) -> {
				manager.setExcluded(modId, !this.excluded);
				GuiMods.this.refresh();
			}, StringUtils.translate("profileroo.gui.hover.mod_toggle"));
		}

		@Override
		protected String text() {
			String color = this.excluded ? GuiBase.TXT_GRAY : GuiBase.TXT_WHITE;
			List<String> files = new ArrayList<>();
			Path configDir = ProfileManager.getInstance().store().configDir();
			for (Path file : ProfileManager.getInstance().knownFiles(this.modId)) {
				files.add(configDir.relativize(file).toString().replace('\\', '/'));
			}
			String filesText = files.isEmpty() ? StringUtils.translate("profileroo.gui.label.no_files") : String.join(", ", files);
			return color + ManagedMods.displayName(this.modId) + GuiBase.TXT_DARK_GRAY + " (" + this.modId + ")  " + GuiBase.TXT_GRAY + filesText;
		}

		@Override
		protected List<String> hoverLines() {
			ProfileManager manager = ProfileManager.getInstance();
			boolean inActive = manager.storedMods(manager.activeName()).contains(this.modId);
			return List.of(StringUtils.translate(inActive ? "profileroo.gui.hover.mod_in_active" : "profileroo.gui.hover.mod_not_in_active", manager.activeName()));
		}
	}
}
