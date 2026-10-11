package com.domen.profileroo.gui.widgets;

import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import java.util.Collection;
import java.util.function.Supplier;

/** A plain scrolling list: rows come from a supplier, each row widget from a factory. */
public class WidgetRowList<T> extends WidgetListBase<T, WidgetRow<T>> {
	public interface RowFactory<T> {
		WidgetRow<T> create(int x, int y, int width, int height, boolean isOdd, T entry, int index, WidgetRowList<T> list);
	}

	private final Supplier<? extends Collection<T>> entries;
	private final RowFactory<T> factory;

	public WidgetRowList(int x, int y, int width, int height, Supplier<? extends Collection<T>> entries, RowFactory<T> factory) {
		super(x, y, width, height, null);
		this.entries = entries;
		this.factory = factory;
		this.browserEntryHeight = 22;
	}

	@Override
	protected Collection<T> getAllEntries() {
		return this.entries.get();
	}

	@Override
	protected WidgetRow<T> createListEntryWidget(int x, int y, int listIndex, boolean isOdd, T entry) {
		return this.factory.create(x, y, this.browserEntryWidth, this.getBrowserEntryHeightFor(entry), isOdd, entry, listIndex, this);
	}
}
