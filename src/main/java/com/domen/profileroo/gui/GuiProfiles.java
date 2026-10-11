package com.domen.profileroo.gui;

import com.domen.profileroo.core.ProfileNames;
import com.domen.profileroo.core.ProfileState.ProfileData;
import com.domen.profileroo.core.ProfileState.ServerRule;
import com.domen.profileroo.gui.widgets.WidgetRow;
import com.domen.profileroo.gui.widgets.WidgetRowList;
import com.domen.profileroo.profile.ManagedMods;
import com.domen.profileroo.profile.OpResult;
import com.domen.profileroo.profile.ProfileManager;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.GuiTextInputFeedback;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.file_ops.FileOpener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class GuiProfiles extends GuiRowListBase<ProfileData> {
	private static String newName = "";

	public GuiProfiles() {
		super(ConfigTab.PROFILES, "profileroo.gui.title.profiles");
	}

	@Override
	protected void initControls(int x, int y) {
		GuiTextFieldGeneric field = new GuiTextFieldGeneric(x, y, 120, 20, this.font);
		field.setMaxLength(ProfileNames.MAX_LENGTH);
		field.setValue(newName);
		field.setHoverTooltip("profileroo.gui.hover.new_profile_name");
		this.addTextField(field, textField -> {
			newName = textField.getValue();
			return true;
		});
		x += 124;

		ButtonGeneric create = new ButtonGeneric(x, y, -1, 20, StringUtils.translate("profileroo.gui.button.create"));
		create.setHoverStrings(WidgetRow.splitLines(StringUtils.translate("profileroo.gui.hover.create")));
		this.addButton(create, (button, mouseButton) -> {
			OpResult result = ProfileManager.getInstance().create(newName.trim());
			if (result.ok()) {
				newName = "";
			}
			this.show(result);
			this.refresh();
		});

		ButtonGeneric folder = new ButtonGeneric(this.getScreenWidth() - 12, y, -1, 20, StringUtils.translate("profileroo.gui.button.open_folder"));
		folder.setX(this.getScreenWidth() - 12 - folder.getWidth());
		this.addButton(folder, (button, mouseButton) -> {
			ProfileManager manager = ProfileManager.getInstance();
			new FileOpener(manager.store().configDir()).openPath(manager.store().profilesDir());
		});
	}

	@Override
	protected Collection<ProfileData> rows() {
		return new ArrayList<>(ProfileManager.getInstance().profiles());
	}

	@Override
	protected WidgetRow<ProfileData> createRow(int x, int y, int width, int height, boolean isOdd, ProfileData entry, int index, WidgetRowList<ProfileData> list) {
		return new Row(x, y, width, height, isOdd, entry, index, list);
	}

	private void confirm(String titleKey, String messageKey, Runnable action, Object... args) {
		IConfirmationListener listener = new IConfirmationListener() {
			@Override
			public boolean onActionConfirmed() {
				action.run();
				return true;
			}

			@Override
			public boolean onActionCancelled() {
				return true;
			}
		};
		GuiBase.openGui(new GuiConfirmAction(320, titleKey, listener, this, messageKey, args));
	}

	private void askName(String titleKey, String initial, Function<String, OpResult> action) {
		GuiBase.openGui(new GuiTextInputFeedback(ProfileNames.MAX_LENGTH, titleKey, initial, this, text -> {
			OpResult result = action.apply(text.trim());
			this.show(result);
			return result.ok();
		}));
	}

	private class Row extends WidgetRow<ProfileData> {
		private final ProfileData profile;
		private final boolean active;

		Row(int x, int y, int width, int height, boolean isOdd, ProfileData profile, int index, WidgetRowList<ProfileData> list) {
			super(x, y, width, height, isOdd, profile, index, list);
			this.profile = profile;
			ProfileManager manager = ProfileManager.getInstance();
			this.active = profile.name.equals(manager.activeName());
			this.setHighlighted(this.active);
			String name = profile.name;

			this.addButtonLeft(GuiBase.TXT_RED + StringUtils.translate("profileroo.gui.button.delete"),
					(b, m) -> GuiProfiles.this.confirm("profileroo.gui.title.confirm_delete", "profileroo.gui.message.confirm_delete",
							() -> GuiProfiles.this.show(manager.delete(name)), name),
					StringUtils.translate(this.active ? "profileroo.gui.hover.delete_active" : "profileroo.gui.hover.delete")).setEnabled(!this.active);
			this.addButtonLeft(StringUtils.translate("profileroo.gui.button.rename"),
					(b, m) -> GuiProfiles.this.askName("profileroo.gui.title.rename", name, text -> manager.rename(name, text)));
			this.addButtonLeft(StringUtils.translate("profileroo.gui.button.copy"),
					(b, m) -> GuiProfiles.this.askName("profileroo.gui.title.copy",
							ProfileNames.unique(name + "-copy", n -> manager.find(n) != null), text -> manager.duplicate(name, text)),
					StringUtils.translate("profileroo.gui.hover.copy"));
			this.addButtonLeft(StringUtils.translate("profileroo.gui.button.save_here"),
					(b, m) -> {
						if (this.active && !profile.locked) {
							GuiProfiles.this.show(manager.overwrite(name));
						} else {
							GuiProfiles.this.confirm("profileroo.gui.title.confirm_save", "profileroo.gui.message.confirm_save",
									() -> GuiProfiles.this.show(manager.overwrite(name)), name);
						}
					},
					StringUtils.translate("profileroo.gui.hover.save_here"));
			this.addButtonLeft(StringUtils.translate(profile.locked ? "profileroo.gui.button.unlock" : "profileroo.gui.button.lock"),
					(b, m) -> {
						GuiProfiles.this.show(manager.setLocked(name, !profile.locked));
						GuiProfiles.this.refresh();
					},
					StringUtils.translate("profileroo.gui.hover.lock"));
			this.addButtonLeft("▼", (b, m) -> {
				manager.move(name, 1);
				GuiProfiles.this.refresh();
			}, StringUtils.translate("profileroo.gui.hover.move"));
			this.addButtonLeft("▲", (b, m) -> {
				manager.move(name, -1);
				GuiProfiles.this.refresh();
			}, StringUtils.translate("profileroo.gui.hover.move"));
			ButtonGeneric use = this.addButtonLeft(StringUtils.translate(this.active ? "profileroo.gui.button.in_use" : "profileroo.gui.button.use"),
					(b, m) -> {
						GuiProfiles.this.show(manager.switchTo(name, ProfileManager.Cause.MANUAL));
						GuiProfiles.this.refresh();
					},
					StringUtils.translate("profileroo.gui.hover.use"));
			use.setEnabled(!this.active);
		}

		@Override
		protected String text() {
			ProfileManager manager = ProfileManager.getInstance();
			StringBuilder text = new StringBuilder();
			text.append(this.active ? GuiBase.TXT_GREEN + "● " : GuiBase.TXT_GRAY + "○ ").append(GuiBase.TXT_WHITE).append(this.profile.name);
			if (this.profile.locked) {
				text.append(' ').append(GuiBase.TXT_GOLD).append(StringUtils.translate("profileroo.label.locked_tag"));
			}
			String hotkey = this.hotkeyText();
			if (!hotkey.isEmpty()) {
				text.append(GuiBase.TXT_GRAY).append("  [").append(hotkey).append(']');
			}
			int rules = manager.ruleCount(this.profile.name);
			if (rules > 0) {
				text.append(GuiBase.TXT_GRAY).append("  ").append(StringUtils.translate("profileroo.gui.label.rule_count", rules));
			}
			return text.toString();
		}

		private String hotkeyText() {
			for (ConfigHotkey hotkey : ProfileManager.getInstance().profileHotkeys()) {
				if (hotkey.getName().equals(this.profile.name)) {
					return hotkey.getKeybind().getKeysDisplayString();
				}
			}
			return "";
		}

		@Override
		protected List<String> hoverLines() {
			ProfileManager manager = ProfileManager.getInstance();
			List<String> lines = new ArrayList<>();
			lines.add(GuiBase.TXT_WHITE + this.profile.name);
			Set<String> mods = manager.storedMods(this.profile.name);
			List<String> names = new ArrayList<>();
			for (String modId : mods) {
				names.add(ManagedMods.displayName(modId));
			}
			lines.add(StringUtils.translate("profileroo.gui.hover.profile_mods", names.isEmpty() ? "—" : String.join(", ", names)));
			List<String> patterns = new ArrayList<>();
			for (ServerRule rule : manager.rules()) {
				if (rule.profile.equalsIgnoreCase(this.profile.name)) {
					patterns.add(rule.pattern);
				}
			}
			if (!patterns.isEmpty()) {
				lines.add(StringUtils.translate("profileroo.gui.hover.profile_rules", String.join(", ", patterns)));
			}
			for (boolean singleplayer : new boolean[] {false, true}) {
				if (manager.fallback(singleplayer).equalsIgnoreCase(this.profile.name)) {
					lines.add(StringUtils.translate(singleplayer ? "profileroo.gui.hover.fallback_singleplayer" : "profileroo.gui.hover.fallback_multiplayer"));
				}
			}
			if (this.profile.locked) {
				lines.add(GuiBase.TXT_GOLD + StringUtils.translate("profileroo.gui.hover.locked"));
			}
			return lines;
		}
	}
}
