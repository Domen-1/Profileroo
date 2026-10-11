package com.domen.profileroo.gui.widgets;

import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * A list row: text on the left, buttons added from the right edge leftwards. Rows are not selectable; everything is
 * done with the buttons.
 */
public abstract class WidgetRow<T> extends WidgetListEntryBase<T> {
	protected final WidgetRowList<T> list;
	private final boolean isOdd;
	private int nextButtonRight;
	private boolean highlighted;

	protected WidgetRow(int x, int y, int width, int height, boolean isOdd, T entry, int index, WidgetRowList<T> list) {
		super(x, y, width, height, entry, index);
		this.isOdd = isOdd;
		this.list = list;
		this.nextButtonRight = x + width - 2;
	}

	/** Adds a button to the left of the previous one. The label is already translated. */
	protected ButtonGeneric addButtonLeft(String label, IButtonActionListener listener, String... hoverLines) {
		return this.addButtonLeft(label, 0, listener, hoverLines);
	}

	/** {@code minWidth} keeps buttons with changing labels the same size in every row. */
	protected ButtonGeneric addButtonLeft(String label, int minWidth, IButtonActionListener listener, String... hoverLines) {
		ButtonGeneric button = new ButtonGeneric(this.nextButtonRight, this.y + 1, -1, 20, label);
		button.setWidth(Math.max(minWidth, button.getWidth()));
		button.setX(this.nextButtonRight - button.getWidth());
		if (hoverLines.length > 0) {
			button.setHoverStrings(splitLines(hoverLines));
		} else {
			button.clearHoverStrings();
		}
		this.addButton(button, listener);
		this.nextButtonRight -= button.getWidth() + 2;
		return button;
	}

	/** Translated texts may hold several lines; MaLiLib wants one string per line. */
	public static List<String> splitLines(String... texts) {
		List<String> lines = new ArrayList<>();
		for (String text : texts) {
			lines.addAll(List.of(text.split("\n")));
		}
		return lines;
	}

	protected int textAreaRight() {
		return this.nextButtonRight - 4;
	}

	protected void setHighlighted(boolean highlighted) {
		this.highlighted = highlighted;
	}

	/** The row's text, drawn left of the buttons and cut to fit. */
	protected abstract String text();

	/** Lines shown when hovering the text part; empty for none. */
	protected List<String> hoverLines() {
		return new ArrayList<>();
	}

	@Override
	public boolean canSelectAt(MouseButtonEvent click) {
		return false;
	}

	@Override
	public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
		int background = this.isMouseOver(mouseX, mouseY) ? 0x30FFFFFF : this.isOdd ? 0x20FFFFFF : 0x50FFFFFF;
		RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, background);
		if (this.highlighted) {
			RenderUtils.drawOutline(ctx, this.x, this.y, this.width, this.height, 0xFF55FF55);
		}
		int maxWidth = this.textAreaRight() - (this.x + 4);
		String text = StringUtils.clampTextToRenderLength(this.text(), maxWidth, LeftRight.RIGHT, "...");
		this.drawString(ctx, this.x + 4, this.y + 7, 0xFFFFFFFF, text);
		super.render(ctx, mouseX, mouseY, selected);
	}

	@Override
	public void postRenderHovered(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
		super.postRenderHovered(ctx, mouseX, mouseY, selected);
		if (mouseX >= this.x && mouseX < this.textAreaRight() && mouseY >= this.y && mouseY < this.y + this.height) {
			List<String> lines = splitLines(this.hoverLines().toArray(String[]::new));
			if (!lines.isEmpty()) {
				RenderUtils.drawHoverText(ctx, mouseX, mouseY, lines);
			}
		}
	}
}
