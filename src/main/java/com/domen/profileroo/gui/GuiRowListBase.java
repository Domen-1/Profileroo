package com.domen.profileroo.gui;

import com.domen.profileroo.core.ProfileState.Resolution;
import com.domen.profileroo.core.SessionInfo;
import com.domen.profileroo.gui.widgets.WidgetRow;
import com.domen.profileroo.gui.widgets.WidgetRowList;
import com.domen.profileroo.profile.OpResult;
import com.domen.profileroo.profile.ProfileManager;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import java.util.Collection;

/** Common frame of the list screens: tab row, a row of controls, a status line and the list. */
public abstract class GuiRowListBase<T> extends GuiListBase<T, WidgetRow<T>, WidgetRowList<T>> {
	protected static final int CONTROLS_Y = 50;
	private static final int STATUS_Y = 76;
	private static final int LIST_Y = 86;

	private final ConfigTab tab;

	protected GuiRowListBase(ConfigTab tab, String titleKey) {
		super(10, LIST_Y);
		this.tab = tab;
		this.title = StringUtils.translate(titleKey);
	}

	@Override
	public void initGui() {
		ProfileManager.getInstance().ensureReady();
		super.initGui();
		ConfigTab.addButtons(this, 10, 26, this.tab);
		this.initControls(12, CONTROLS_Y);
	}

	/** Buttons and fields above the list. */
	protected abstract void initControls(int x, int y);

	protected abstract Collection<T> rows();

	protected abstract WidgetRow<T> createRow(int x, int y, int width, int height, boolean isOdd, T entry, int index, WidgetRowList<T> list);

	@Override
	protected WidgetRowList<T> createListWidget(int listX, int listY) {
		return new WidgetRowList<>(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this::rows, this::createRow);
	}

	@Override
	protected int getBrowserWidth() {
		return this.getScreenWidth() - 20;
	}

	@Override
	protected int getBrowserHeight() {
		return this.getScreenHeight() - LIST_Y - 6;
	}

	/** Rebuilds the rows and the controls after something changed. */
	public void refresh() {
		this.initGui();
	}

	/** The "auto switch: on/off" toggle, right-aligned at {@code right}. Returns its left edge. */
	protected int addAutoSwitchButton(int right, int y) {
		ProfileManager manager = ProfileManager.getInstance();
		String state = manager.isAutoSwitch()
				? GuiBase.TXT_GREEN + StringUtils.translate("profileroo.gui.label.on")
				: GuiBase.TXT_RED + StringUtils.translate("profileroo.gui.label.off");
		ButtonGeneric button = new ButtonGeneric(right, y, -1, 20, StringUtils.translate("profileroo.gui.button.auto_switch", state + GuiBase.TXT_RST));
		button.setX(right - button.getWidth());
		button.setHoverStrings(WidgetRow.splitLines(StringUtils.translate("profileroo.gui.hover.auto_switch")));
		this.addButton(button, (b, mouseButton) -> {
			this.show(manager.setAutoSwitch(!manager.isAutoSwitch()));
			this.refresh();
		});
		return button.getX();
	}

	public void show(OpResult result) {
		InfoUtils.showGuiMessage(result.type(), result.key(), result.args());
	}

	/** "Now: play.example.com → profile smp (rule *.example.com) · active: smp". */
	protected String statusLine() {
		ProfileManager manager = ProfileManager.getInstance();
		String active = StringUtils.translate("profileroo.gui.label.active", GuiBase.TXT_GREEN + manager.activeName() + GuiBase.TXT_RST);
		if (!manager.isAutoSwitch()) {
			active += GuiBase.TXT_GRAY + "  ·  " + GuiBase.TXT_RED + StringUtils.translate("profileroo.gui.label.auto_off");
		}
		SessionInfo session = manager.session();
		if (session == null) {
			return active + GuiBase.TXT_GRAY + "  ·  " + StringUtils.translate("profileroo.gui.label.not_in_world");
		}
		Resolution resolution = manager.resolveCurrent();
		String profile = resolution == null || resolution.profile() == null
				? StringUtils.translate("profileroo.gui.label.keep_current")
				: resolution.profile();
		String by = resolution == null || resolution.rule() == null
				? StringUtils.translate("profileroo.gui.label.by_fallback")
				: StringUtils.translate("profileroo.gui.label.by_rule", resolution.rule().pattern);
		return active + GuiBase.TXT_GRAY + "  ·  " + StringUtils.translate("profileroo.gui.label.here",
				GuiBase.TXT_YELLOW + session.label() + GuiBase.TXT_GRAY, GuiBase.TXT_WHITE + profile + GuiBase.TXT_GRAY, by);
	}

	@Override
	public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
		super.drawContents(ctx, mouseX, mouseY, partialTicks);
		this.drawString(ctx, this.statusLine(), 12, STATUS_Y, 0xFFFFFFFF);
	}
}
