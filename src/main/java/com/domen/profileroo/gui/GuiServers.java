package com.domen.profileroo.gui;

import com.domen.profileroo.core.ProfileState.ProfileData;
import com.domen.profileroo.core.ProfileState.Resolution;
import com.domen.profileroo.core.ProfileState.ServerRule;
import com.domen.profileroo.core.RulePattern;
import com.domen.profileroo.core.SessionInfo;
import com.domen.profileroo.gui.widgets.WidgetRow;
import com.domen.profileroo.gui.widgets.WidgetRowList;
import com.domen.profileroo.profile.ProfileManager;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextInputFeedback;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Server rules, checked top to bottom, and the two "no rule matched" choices below them. */
public class GuiServers extends GuiRowListBase<GuiServers.Entry> {
	private static final int PATTERN_MAX_LENGTH = 128;

	public sealed interface Entry permits RuleEntry, FallbackEntry {
	}

	public record RuleEntry(int index, ServerRule rule) implements Entry {
	}

	public record FallbackEntry(boolean singleplayer) implements Entry {
	}

	public GuiServers() {
		super(ConfigTab.SERVERS, "profileroo.gui.title.servers");
	}

	@Override
	protected void initControls(int x, int y) {
		ProfileManager manager = ProfileManager.getInstance();
		SessionInfo session = manager.session();

		ButtonGeneric add = new ButtonGeneric(x, y, -1, 20, StringUtils.translate("profileroo.gui.button.add_rule"));
		add.setHoverStrings(WidgetRow.splitLines(StringUtils.translate("profileroo.gui.hover.pattern_help")));
		this.addButton(add, (button, mouseButton) -> this.editPattern(-1, session != null ? session.exactPattern() : ""));
		x += add.getWidth() + 4;

		ButtonGeneric bind = new ButtonGeneric(x, y, -1, 20, StringUtils.translate("profileroo.gui.button.bind_current", manager.activeName()));
		bind.setHoverStrings(WidgetRow.splitLines(StringUtils.translate(session == null ? "profileroo.gui.hover.bind_current_offline" : "profileroo.gui.hover.bind_current",
				session == null ? "" : session.exactPattern())));
		bind.setEnabled(session != null);
		this.addButton(bind, (button, mouseButton) -> {
			this.show(manager.bindCurrent(null));
			this.refresh();
		});

		this.addAutoSwitchButton(this.getScreenWidth() - 12, y);
	}

	@Override
	protected Collection<Entry> rows() {
		List<Entry> rows = new ArrayList<>();
		List<ServerRule> rules = ProfileManager.getInstance().rules();
		for (int i = 0; i < rules.size(); i++) {
			rows.add(new RuleEntry(i, rules.get(i)));
		}
		rows.add(new FallbackEntry(false));
		rows.add(new FallbackEntry(true));
		return rows;
	}

	@Override
	protected WidgetRow<Entry> createRow(int x, int y, int width, int height, boolean isOdd, Entry entry, int index, WidgetRowList<Entry> list) {
		return new Row(x, y, width, height, isOdd, entry, index, list);
	}

	/** {@code index} -1 adds a new rule for the active profile. */
	private void editPattern(int index, String initial) {
		ProfileManager manager = ProfileManager.getInstance();
		String title = index < 0 ? "profileroo.gui.title.add_rule" : "profileroo.gui.title.edit_rule";
		GuiBase.openGui(new GuiTextInputFeedback(PATTERN_MAX_LENGTH, title, initial, this, text -> {
			String pattern = text.trim();
			if (pattern.isEmpty()) {
				return false;
			}
			if (index < 0) {
				manager.addRule(pattern, manager.activeName());
			} else {
				manager.setRulePattern(index, pattern);
			}
			return true;
		}));
	}

	/** The next or previous profile name; with {@code allowNone} the cycle includes "" (keep the current profile). */
	private static String cycleProfile(String current, boolean forward, boolean allowNone) {
		List<String> options = new ArrayList<>();
		if (allowNone) {
			options.add("");
		}
		for (ProfileData profile : ProfileManager.getInstance().profiles()) {
			options.add(profile.name);
		}
		int index = 0;
		for (int i = 0; i < options.size(); i++) {
			if (options.get(i).equalsIgnoreCase(current)) {
				index = i;
				break;
			}
		}
		return options.get(Math.floorMod(index + (forward ? 1 : -1), options.size()));
	}

	/** Wide enough for "Profile: <any name>", so the columns line up. */
	private static int profileButtonWidth() {
		List<String> names = new ArrayList<>();
		names.add(StringUtils.translate("profileroo.gui.label.keep_current"));
		for (ProfileData profile : ProfileManager.getInstance().profiles()) {
			names.add(profile.name);
		}
		int width = 0;
		for (String name : names) {
			width = Math.max(width, StringUtils.getStringWidth(StringUtils.translate("profileroo.gui.button.profile", name)) + 10);
		}
		return width;
	}

	private static String profileLabel(String profile) {
		if (profile.isEmpty()) {
			return GuiBase.TXT_GRAY + StringUtils.translate("profileroo.gui.label.keep_current");
		}
		boolean exists = ProfileManager.getInstance().find(profile) != null;
		return (exists ? GuiBase.TXT_WHITE : GuiBase.TXT_RED) + profile;
	}

	private class Row extends WidgetRow<Entry> {
		private final Entry rowEntry;
		private final boolean decisive;

		Row(int x, int y, int width, int height, boolean isOdd, Entry entry, int index, WidgetRowList<Entry> list) {
			super(x, y, width, height, isOdd, entry, index, list);
			this.rowEntry = entry;
			ProfileManager manager = ProfileManager.getInstance();
			Resolution resolution = manager.resolveCurrent();
			String cycleHover = StringUtils.translate("profileroo.gui.hover.cycle_profile");

			if (entry instanceof RuleEntry(int ruleIndex, ServerRule rule)) {
				this.decisive = resolution != null && resolution.ruleIndex() == ruleIndex;
				this.addButtonLeft(GuiBase.TXT_RED + StringUtils.translate("profileroo.gui.button.delete"), (b, m) -> {
					manager.removeRule(ruleIndex);
					GuiServers.this.refresh();
				});
				this.addButtonLeft("▼", (b, m) -> {
					manager.moveRule(ruleIndex, 1);
					GuiServers.this.refresh();
				}).setEnabled(ruleIndex < manager.rules().size() - 1);
				this.addButtonLeft("▲", (b, m) -> {
					manager.moveRule(ruleIndex, -1);
					GuiServers.this.refresh();
				}).setEnabled(ruleIndex > 0);
				this.addButtonLeft(StringUtils.translate("profileroo.gui.button.profile", profileLabel(rule.profile) + GuiBase.TXT_RST), profileButtonWidth(), (b, mouseButton) -> {
					// SDL mouse buttons in 26.x: 1 is the left one, 3 the right one.
					manager.setRuleProfile(ruleIndex, cycleProfile(rule.profile, mouseButton != 3, false));
					GuiServers.this.refresh();
				}, cycleHover);
				this.addButtonLeft(StringUtils.translate("profileroo.gui.button.edit"), (b, m) -> GuiServers.this.editPattern(ruleIndex, rule.pattern),
						StringUtils.translate("profileroo.gui.hover.pattern_help"));
			} else {
				boolean singleplayer = ((FallbackEntry) entry).singleplayer();
				SessionInfo session = manager.session();
				this.decisive = resolution != null && resolution.isFallback() && session != null && session.isSingleplayer() == singleplayer;
				String current = manager.fallback(singleplayer);
				this.addButtonLeft(StringUtils.translate("profileroo.gui.button.profile", profileLabel(current) + GuiBase.TXT_RST), profileButtonWidth(), (b, mouseButton) -> {
					manager.setFallback(singleplayer, cycleProfile(current, mouseButton != 3, true));
					GuiServers.this.refresh();
				}, cycleHover);
			}
			this.setHighlighted(this.decisive && manager.isAutoSwitch());
		}

		@Override
		protected String text() {
			String mark = !this.decisive ? ""
					: ProfileManager.getInstance().isAutoSwitch()
					? GuiBase.TXT_GREEN + "  ◀ " + StringUtils.translate("profileroo.gui.label.applies_here")
					: GuiBase.TXT_GRAY + "  ◀ " + StringUtils.translate("profileroo.gui.label.would_apply_here");
			if (this.rowEntry instanceof RuleEntry(int ruleIndex, ServerRule rule)) {
				String pattern = rule.pattern.isEmpty() ? GuiBase.TXT_RED + StringUtils.translate("profileroo.gui.label.empty_pattern") : rule.pattern;
				return GuiBase.TXT_GRAY + (ruleIndex + 1) + ". " + GuiBase.TXT_WHITE + pattern + mark;
			}
			boolean singleplayer = ((FallbackEntry) this.rowEntry).singleplayer();
			return GuiBase.TXT_GOLD + StringUtils.translate(singleplayer ? "profileroo.gui.label.fallback.singleplayer" : "profileroo.gui.label.fallback.multiplayer") + mark;
		}

		@Override
		protected List<String> hoverLines() {
			List<String> lines = new ArrayList<>();
			if (this.rowEntry instanceof RuleEntry(int ruleIndex, ServerRule rule)) {
				SessionInfo session = ProfileManager.getInstance().session();
				if (session != null) {
					boolean matches = RulePattern.matches(rule.pattern, session);
					lines.add(StringUtils.translate(matches ? "profileroo.gui.hover.rule_matches" : "profileroo.gui.hover.rule_no_match", session.label()));
				}
				lines.add(StringUtils.translate("profileroo.gui.hover.pattern_help"));
			} else {
				lines.add(StringUtils.translate("profileroo.gui.hover.fallback"));
			}
			return lines;
		}
	}
}
