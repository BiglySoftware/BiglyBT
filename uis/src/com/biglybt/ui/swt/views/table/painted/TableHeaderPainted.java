/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.views.table.painted;

import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableStructureEventDispatcher;
import com.biglybt.ui.swt.ConfigKeysSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.utils.DragDropUtils;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.views.table.impl.TableTooltips;

import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pifimpl.local.ui.tables.TableContextMenuItemImpl;

public class TableHeaderPainted
	implements ParameterListener
{
	private static final int DEFAULT_HEADER_HEIGHT = 27;

	private static boolean gradientHeader;
	
	static{
		COConfigurationManager.addAndFireWeakParameterListener(
				"Table Header Gradient Fill",
			(n)->{
				gradientHeader = COConfigurationManager.getBooleanParameter("Table Header Gradient Fill");
			});
	}
	
	private final TableViewPainted tv;

	private final Canvas cHeaderArea;

	private int headerHeight;

	/** null if not dragging */
	private TableColumnCore draggingHeader;

	private TableColumnCore droppingOnHeader;

	private boolean droppingAfterHeader = false;

	private Font fontHeaderSmall;

	private Font fontHeader;

	public TableHeaderPainted(TableViewPainted tv, Canvas cHeaderArea) {
		this.tv = tv;
		this.cHeaderArea = cHeaderArea;
		if (cHeaderArea.isDisposed()) {
			return;
		}

		fontHeader = FontUtils.cache( FontUtils.getFontPercentOf(cHeaderArea.getFont(), 0.9f));
		fontHeaderSmall = FontUtils.cache( FontUtils.getFontPercentOf(fontHeader, 0.8f));
		cHeaderArea.setFont(fontHeader);

		COConfigurationManager.addParameterListener(ConfigKeysSWT.ICFG_TABLE_HEADER_HEIGHT,	this);
		headerHeight = COConfigurationManager.getIntParameter( ConfigKeysSWT.ICFG_TABLE_HEADER_HEIGHT);
		if (headerHeight <= 0) {
			headerHeight = DEFAULT_HEADER_HEIGHT;
		}

		Object layoutData = cHeaderArea.getLayoutData();
		if (layoutData instanceof GridData) {
			((GridData) layoutData).heightHint = headerHeight;
		}

		cHeaderArea.addPaintListener(this::paintHeader);

		Listener l = new MouseListeners(cHeaderArea, tv);

		cHeaderArea.addListener(SWT.MouseDown, l);
		cHeaderArea.addListener(SWT.MouseUp, l);
		cHeaderArea.addListener(SWT.MouseMove, l);

		Transfer[] types = new Transfer[] {
			TextTransfer.getInstance()
		};

		final DragSource ds = DragDropUtils.createDragSource(cHeaderArea, DND.DROP_MOVE);
		ds.setTransfer(types);
		ds.addDragListener(new HeaderDragSourceListener());

		final DropTarget dt = new DropTarget(cHeaderArea, DND.DROP_MOVE);
		dt.setTransfer(types);
		dt.addDropListener(new HeaderDropTargetListener());

		cHeaderArea.addDisposeListener(
				e -> {
					FontUtils.uncache( fontHeader, fontHeaderSmall );
					fontHeaderSmall = null;
					fontHeader = null;
					Utils.disposeSWTObjects(ds, dt );
				});
	}

	private void paintHeader(PaintEvent e) {
		if (cHeaderArea.isDisposed() || e.gc == null) {
			return;
		}

		Rectangle clientArea = tv.getClientArea();
		Rectangle ca = cHeaderArea.getClientArea();
		Color c1, c2, fg;

		if (tv.isEnabled()) {
			c1 = TablePaintedUtils.getColour(e.display, SWT.COLOR_LIST_BACKGROUND);
			c2 = TablePaintedUtils.getColour(e.display, SWT.COLOR_WIDGET_BACKGROUND);
			fg = TablePaintedUtils.getColour(e.display, SWT.COLOR_LIST_FOREGROUND);
		} else {
			c1 = TablePaintedUtils.getColour(e.display, SWT.COLOR_WIDGET_BACKGROUND);
			c2 = TablePaintedUtils.getColour(e.display, SWT.COLOR_WIDGET_LIGHT_SHADOW);
			fg = TablePaintedUtils.getColour(e.display, SWT.COLOR_WIDGET_NORMAL_SHADOW);
		}

		Color hline;
		Color vline;

		Pattern patternUp;
		Pattern patternDown;
		
		if ( gradientHeader ){
			hline = c2;
			vline = c2;

			patternUp 	= new Pattern(e.display, 0, 0, 0, ca.height, c1, c2);
			patternDown = new Pattern(e.display, 0, -ca.height, 0, 0, c2, c1);
		}else{
			hline = c2;
			vline = c1;

			patternUp 	= new Pattern(e.display, 0, 0, 0, ca.height, c2, c2);
			patternDown = new Pattern(e.display, 0, -ca.height, 0, 0, c1, c1);
		}
		
		//e.gc.setBackgroundPattern(patternUp);
		//e.gc.fillRectangle(ca);

		e.gc.setForeground(hline);
		//e.gc.drawLine(0, 0, clientArea.width, 0);
		e.gc.drawLine(0, headerHeight - 1, clientArea.width, headerHeight - 1);

		TableColumnCore[] visibleColumns = tv.getVisibleColumns();
		GCStringPrinter sp;
		TableColumnCore[] sortColumns = tv.getSortColumns();
		int x = -clientArea.x;
		for (TableColumnCore column : visibleColumns) {
			int w = column.getWidth();

			//squeeze last column's text into available visible space
			if (x + w > ca.width) {
				w = ca.width - x;
				if (w <= 16) {
					break;
				}
			}

			int sortColumnPos = -1;
			for (int i = 0; i < sortColumns.length; i++) {
				if (column.equals(sortColumns[i])) {
					sortColumnPos = i;
					break;
				}
			}

			e.gc.setBackgroundPattern(sortColumnPos >= 0 ? patternDown : patternUp);
			e.gc.fillRectangle(x, 1, w, headerHeight - 2);
			e.gc.setForeground(vline);
			boolean doingDrop = column.equals(droppingOnHeader);
			if (doingDrop && droppingAfterHeader) {
				e.gc.setForeground(fg);
				e.gc.setLineWidth(2);
			} else {
				e.gc.setForeground(vline);
			}
			e.gc.drawLine(x + w - 1, 1, x + w - 1, headerHeight - 1);
			if (doingDrop && !droppingAfterHeader) {
				e.gc.setLineWidth(2);
				e.gc.setForeground(fg);
				e.gc.drawLine(x, 0, x, headerHeight - 1);
			}
			e.gc.setLineWidth(1);

			e.gc.setForeground(fg);
			int yOfs = 0;
			int wText = w;
/* Top Center
			if (isSortColumn) {
				int arrowY = 2;
				int arrowHeight = 6;
				yOfs = 8;
				// draw sort indicator
				int middle = w / 2;
				int y1, y2;
				int arrowHalfW = 4;
				if (column.isSortAscending()) {
					y2 = arrowY;
					y1 = y2 + arrowHeight;
				} else {
					y1 = arrowY;
					y2 = y1 + arrowHeight;
				}
				e.gc.setAntialias(SWT.ON);
				e.gc.setBackground(ColorCache.getColor(e.display, 0, 0, 0));
				e.gc.fillPolygon(new int[] {
					x + middle - arrowHalfW,
					y1,
					x + middle + arrowHalfW,
					y1,
					x + middle,
					y2
				});
			}
*/
			if (sortColumnPos >= 0) {
				// draw sort indicator
				int arrowHeight = 6;
				int arrowY = (headerHeight / 2) - (arrowHeight / 2);
				int arrowHalfW = 4;
				int middle = w - arrowHalfW - 4;
				wText = w - (arrowHalfW * 2) - 5;
				int y1, y2;
				if (column.isSortAscending()) {
					y2 = arrowY;
					y1 = y2 + arrowHeight;
				} else {
					y1 = arrowY;
					y2 = y1 + arrowHeight;
				}
				e.gc.setAntialias(SWT.ON);
				e.gc.setBackground(fg);
				if (sortColumnPos > 0) {
					e.gc.setAlpha((sortColumns.length - sortColumnPos + 1) * 140
							/ sortColumns.length);
				}
				e.gc.fillPolygon(new int[] {
					x + middle - arrowHalfW,
					y1,
					x + middle + arrowHalfW,
					y1,
					x + middle,
					y2
				});
				if (sortColumnPos > 0) {
					e.gc.setAlpha(255);
				}
			}

			int xOfs = x + 2;

			boolean onlyShowImage = column.showOnlyImage();
			String text = "";
			if (!onlyShowImage) {
				text = MessageText.getString(column.getTitleLanguageKey());
			}

			int style = SWT.WRAP | SWT.CENTER;
			Image image = null;
			String imageID = column.getIconReference();
			if (imageID != null) {
				image = ImageLoader.getInstance().getImage(imageID);
				if (ImageLoader.isRealImage(image)) {
					if (onlyShowImage) {
						text = null;
						Rectangle imageBounds = image.getBounds();
						e.gc.drawImage(image,
								(int) (x + (w / 2.0) - (imageBounds.width / 2.0) + 0.5),
								(headerHeight / 2) - (imageBounds.height / 2));
					} else {
						text = "%0 " + text;
					}
				} else {
					image = null;
				}
			}

			if (text != null) {
				sp = new GCStringPrinter(e.gc, text,
						new Rectangle(xOfs, yOfs - 1, wText - 4, headerHeight - yOfs + 2),
						true, false, style);
				if (image != null) {
					sp.setImages(new Image[] {
						image
					});
				}
				sp.calculateMetrics();
				if (sp.isWordCut() || sp.isCutoff()) {
					Font font = e.gc.getFont();
					e.gc.setFont(fontHeaderSmall);
					sp.printString();
					e.gc.setFont(font);
				} else {
					sp.printString();
				}
			}

			if (imageID != null) {
				ImageLoader.getInstance().releaseImage(imageID);
			}

			x += w;
		}

		e.gc.setBackgroundPattern(patternUp);
		e.gc.fillRectangle(x, 1, clientArea.width - x, headerHeight - 2);

		patternUp.dispose();
		patternDown.dispose();
		e.gc.setBackgroundPattern(null);
	}

	public void setHeaderVisible(boolean visible) {
		Utils.execSWTThread(() -> {
			if (cHeaderArea.isDisposed()) {
				return;
			}
			cHeaderArea.setVisible(visible);
			Object ld = cHeaderArea.getLayoutData();
			if ( ld instanceof FormData ){
				FormData fd = Utils.getFilledFormData();
				fd.height = visible ? headerHeight : 1;
				fd.bottom = null;
				cHeaderArea.setLayoutData(fd);
			}else{
				GridData gd = (GridData)ld;
				gd.heightHint=visible?headerHeight : 1;
			}
			Composite parent = cHeaderArea.getParent();
			if (parent != null) {
				parent.layout(true);
			}
		});
	}

	public void delete() {
		COConfigurationManager.removeParameterListener(ConfigKeysSWT.ICFG_TABLE_HEADER_HEIGHT, this);
	}

	public TableColumnCore getTableColumnByOffset(int mouseX,
			Point outRelativePos) {
		Rectangle clientArea = tv.getClientArea();
		int x = -clientArea.x;
		TableColumnCore[] visibleColumns = tv.getVisibleColumns();
		for (TableColumnCore column : visibleColumns) {
			int w = column.getWidth();

			if (mouseX >= x && mouseX < x + w) {
				outRelativePos.x = mouseX - x;
				return column;
			}

			x += w;
		}
		return null;
	}

	@Override
	public void parameterChanged(String parameterName) {
		if (parameterName == null || parameterName.equals(ConfigKeysSWT.ICFG_TABLE_HEADER_HEIGHT)) {
			headerHeight = COConfigurationManager.getIntParameter(ConfigKeysSWT.ICFG_TABLE_HEADER_HEIGHT);
			if (headerHeight == 0) {
				headerHeight = DEFAULT_HEADER_HEIGHT;
			}
			tv.setHeaderVisible(tv.getHeaderVisible());
		}
	}

	public void setEnabled(boolean enable) {
		if (cHeaderArea.isDisposed()) {
			return;
		}
		cHeaderArea.setEnabled(enable);
		cHeaderArea.redraw();
	}

	public void redraw() {
		if (cHeaderArea.isDisposed()) {
			return;
		}
		cHeaderArea.redraw();
	}

	public Composite getHeaderArea() {
		return cHeaderArea;
	}

	public void createMenu(Menu menu) {
		cHeaderArea.addListener(SWT.MenuDetect, event -> {
			menu.setData(TableViewPainted.MENUKEY_IN_BLANK_AREA, false);
			menu.setData(TableViewPainted.MENUKEY_IS_HEADER, true);
			Point pt = cHeaderArea.toControl(event.x, event.y);
			menu.setData(TableContextMenuItemImpl.MENUKEY_TABLE_VIEW, tv );
			menu.setData(TableViewPainted.MENUKEY_COLUMN, tv.getTableColumnByOffset(pt.x));
		});
		cHeaderArea.setMenu(menu);
	}

	private static class MouseListeners
		implements Listener
	{
		private final Canvas cHeaderArea;

		private final TableViewPainted tv;

		boolean mouseDown;

		TableColumnCore columnSizing;

		int columnSizingStart;

		public MouseListeners(Canvas cHeaderArea, TableViewPainted tv) {
			this.cHeaderArea = cHeaderArea;
			this.tv = tv;
			mouseDown = false;
			columnSizingStart = 0;
		}

		@Override
		public void handleEvent(Event e) {
			if (cHeaderArea.isDisposed()) {
				return;
			}
			switch (e.type) {
				case SWT.MouseDown: {
					if (e.button != 1) {
						return;
					}
					mouseDown = true;

					columnSizing = null;
					Rectangle clientArea = tv.getClientArea();
					int x = -clientArea.x;
					TableColumnCore[] visibleColumns = tv.getVisibleColumns();
					for (TableColumnCore column : visibleColumns) {
						int w = column.getWidth();
						x += w;

						if (e.x >= x - 3 && e.x <= x + 3) {
							columnSizing = column;
							columnSizingStart = e.x;
							break;
						}
					}

					break;
				}

				case SWT.MouseUp: {
					if (e.button != 1) {
						return;
					}
					if (mouseDown) {
						if (columnSizing == null) {
							TableColumnCore column = tv.getTableColumnByOffset(e.x);
							if (column != null) {
								boolean addColumn = (e.stateMask & SWT.MOD1) > 0;
								if (addColumn) {
									tv.addSortColumn(column);
								} else {
									tv.setSortColumns(new TableColumnCore[] { column }, true);
								}
							}
						} else {
							int diff = (e.x - columnSizingStart);
							columnSizing.setWidthPX(columnSizing.getWidth() + diff);
						}
					}
					columnSizing = null;
					mouseDown = false;
					break;
				}

				case SWT.MouseMove: {
					if (columnSizing != null) {
						int diff = (e.x - columnSizingStart);
						columnSizing.setWidthPX(columnSizing.getWidth() + diff);
						columnSizingStart = e.x;
					} else {
						int cursorID = SWT.CURSOR_HAND;
						Rectangle clientArea = tv.getClientArea();
						int x = -clientArea.x;
						TableColumnCore[] visibleColumns = tv.getVisibleColumns();
						for (TableColumnCore column : visibleColumns) {
							int w = column.getWidth();
							x += w;

							if (e.x >= x - 3 && e.x <= x + 3) {
								cursorID = SWT.CURSOR_SIZEWE;
								break;
							}
						}
						if (e.display != null) {
							cHeaderArea.setCursor(e.display.getSystemCursor(cursorID));
						}
						TableColumnCore column = tv.getTableColumnByOffset(e.x);

						if (column == null || (TableTooltips.tooltips_disabled
								&& !column.doesAutoTooltip())) {
							Utils.setTT(cHeaderArea, null);
						} else {
							String info = MessageText.getString(
									column.getTitleLanguageKey() + ".info", (String) null);
							if (column.showOnlyImage()) {
								String tt = MessageText.getString(column.getTitleLanguageKey());
								if (info != null) {
									tt += "\n" + info;
								}
								Utils.setTT(cHeaderArea, tt);
							} else {
								Utils.setTT(cHeaderArea, info);
							}
						}
					}
				}

			}
		}
	}

	private class HeaderDropTargetListener
		implements DropTargetListener
	{
		private DropTargetEvent lastDropTargetEvent;

		@Override
		public void dropAccept(DropTargetEvent event) {
		}

		@Override
		public void drop(final DropTargetEvent event) {
			try {
				if (droppingOnHeader == null) {
					return;
				}
				TableColumn tcOrig = draggingHeader == null
						? tv.getTableColumn((String) DragDropUtils.getLastDraggedObject())
						: draggingHeader;
				if (tcOrig == null) {
					return;
				}
				int origPos = tcOrig.getPosition();
				int destPos = droppingOnHeader.getPosition();
				if (droppingAfterHeader) {
					destPos++;
				}
				boolean columnAdded = !tcOrig.isVisible();
				if (columnAdded) {
					tcOrig.setVisible(true);
				}

				if (origPos == destPos && !columnAdded) {
					return;
				}

				TableColumnCore[] visibleColumns = tv.getVisibleColumns();
				if (columnAdded) {
					TableColumnCore[] tmp = new TableColumnCore[visibleColumns.length + 1];
					System.arraycopy(visibleColumns, 0, tmp, 0, visibleColumns.length);
					tmp[visibleColumns.length] = (TableColumnCore) tcOrig;
					visibleColumns = tmp;
				}
				((TableColumnCore) tcOrig).setPositionNoShift(destPos);

				Arrays.sort(visibleColumns, (o1, o2) -> {
					if (o1 == o2) {
						return 0;
					}
					int diff = o1.getPosition() - o2.getPosition();
					return diff != 0 ? diff : o2 == tcOrig ? 1 : -1;
				});

				for (int i = 0; i < visibleColumns.length; i++) {
					TableColumnCore tc = visibleColumns[i];
					tc.setPositionNoShift(i);
				}
				tv.setColumnsOrdered(visibleColumns);

				TableStructureEventDispatcher.getInstance(
						tv.getTableID()).tableStructureChanged(columnAdded,
								tv.getDataSourceType());
			} finally {
				draggingHeader = null;
				droppingOnHeader = null;
				lastDropTargetEvent = null;
				redraw();
			}
		}

		@Override
		public void dragOver(DropTargetEvent event) {
			TableColumn tcOrig = draggingHeader == null
					? tv.getTableColumn((String) DragDropUtils.getLastDraggedObject())
					: draggingHeader;
			if (tcOrig == null) {
				return;
			}

			if (lastDropTargetEvent != null && lastDropTargetEvent.x == event.x
					&& lastDropTargetEvent.y == event.y
					&& lastDropTargetEvent.detail == event.detail
					&& lastDropTargetEvent.operations == event.operations) {
				return;
			}
			lastDropTargetEvent = event;
			Point offset = new Point(0, 0);
			Point relativeMousePos = cHeaderArea.toControl(event.x, event.y);
			droppingOnHeader = getTableColumnByOffset(relativeMousePos.x, offset);
			if (droppingOnHeader != null) {
				int dropPos = droppingOnHeader.getPosition();
				int ourPos = tcOrig.isVisible() ? tcOrig.getPosition() : -2;
				if (dropPos == ourPos) {
					droppingOnHeader = null;
				} else if (dropPos == ourPos + 1) {
					droppingAfterHeader = true;
				} else if (dropPos == ourPos - 1) {
					droppingAfterHeader = false;
				} else {
					droppingAfterHeader = offset.x > droppingOnHeader.getWidth() / 2;
				}
			}
			redraw();
		}

		@Override
		public void dragOperationChanged(DropTargetEvent event) {
		}

		@Override
		public void dragLeave(DropTargetEvent event) {
			redraw();
		}

		@Override
		public void dragEnter(DropTargetEvent event) {
			redraw();
		}
	}

	private class HeaderDragSourceListener
		implements DragSourceListener
	{
		private String eventData;

		@Override
		public void dragStart(DragSourceEvent event) {
			if (cHeaderArea.isDisposed() || event.display == null) {
				return;
			}
			Cursor cursor = cHeaderArea.getCursor();
			if (cursor != null
					&& cursor.equals(event.display.getSystemCursor(SWT.CURSOR_SIZEWE))) {
				event.doit = false;
				return;
			}

			cHeaderArea.setCursor(null);
			TableColumnCore tc = tv.getTableColumnByOffset(event.x);
			if (tc != null) {
				draggingHeader = tc;
				eventData = tc.getName();
			} else {
				eventData = null;
				draggingHeader = null;
			}
		}

		@Override
		public void dragSetData(DragSourceEvent event) {
			event.data = eventData;
		}

		@Override
		public void dragFinished(DragSourceEvent event) {
			draggingHeader = null;
			eventData = null;
		}
	}
}
