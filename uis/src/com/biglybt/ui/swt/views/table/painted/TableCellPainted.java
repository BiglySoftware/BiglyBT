/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.views.table.painted;

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.ui.Graphic;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableRow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pifimpl.UISWTGraphicImpl;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.impl.TableCellSWTBase;
import com.biglybt.ui.swt.views.table.impl.TableColumnSWTBase;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableRowCore;

public class TableCellPainted
	extends TableCellSWTBase
{
	private static final boolean DEBUG_CELLPAINT = false;

	private Rectangle bounds;

	private String text = "";

	private int marginWidth;

	private int marginHeight;

	private boolean redrawScheduled;

	private Color colorFG;

	// private Color colorBG;

	public TableCellPainted(TableRowPainted row, TableColumnPainted column, int pos) {
		super(row, column);
		constructionCompleter();
	}

	@Override
	protected void
	constructionCompleter()
	{
		constructionComplete();

		tableColumnCore.invokeCellAddedListeners(TableCellPainted.this);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableCell#getDataSource()
	 */
	@Override
	public Object getDataSource() {
		// remove this because if a disposal-listener needs to get its hands on the datasource to clean up
		// properly we need to return it to them! (happens with the peers view PiecesItem for example)
		//if (isDisposed()) {
		//	return null;
		//}
		TableRowCore row = tableRowSWT;
		TableColumnCore col = tableColumnCore;

		if (row == null || col == null) {
			return (null);
		}
		return row.getDataSource(col.getUseCoreDataSource());
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableCell#getTableColumn()
	 */
	@Override
	public TableColumn getTableColumn() {
		return tableColumnCore;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableCell#getTableRow()
	 */
	@Override
	public TableRow getTableRow() {
		return tableRowSWT;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableCell#getTableID()
	 */
	@Override
	public String getTableID() {
		return tableRowSWT==null?null:tableRowSWT.getTableID();
	}

	@SuppressWarnings("null")
	public static boolean stringEquals(String s0, String s1) {
		boolean s0Null = s0 == null;
		boolean s1Null = s1 == null;
		if (s0Null || s1Null) {
			return s0Null == s1Null;
		}
		return s0.equals(s1);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableCell#getText()
	 */
	@Override
	public String getText() {
		if (hasFlag(FLAG_SORTVALUEISTEXT) && sortValue instanceof String) {
			return (String) sortValue;
		}

		return text;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableCell#getSortValue()
	 */
	@Override
	public Comparable<?> getSortValue() {
		Comparable<?> value = super.getSortValue();
		return value == null ? "" : value;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableCell#isShown()
	 */
	@Override
	public boolean isShown() {
		return !isDisposed() && tableRowSWT != null && tableRowSWT.getView().isColumnVisible(tableColumnCore);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableCell#getMaxLines()
	 */
	@Override
	public int getMaxLines() {
		int lineHeight = tableRowSWT.getLineHeight();
		if (lineHeight == 0) {
			return 1;
		}
		int maxLines = getHeight() / lineHeight;
		if (maxLines < 1) {
			return 1;
		}
		return maxLines;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableCell#getWidth()
	 */
	@Override
	public int getWidth() {
		if (isDisposed()) {
			return -1;
		}
		return tableColumnCore.getWidth() - 2 - (getMarginWidth() * 2);
	}

	@Override
	public int getWidthRaw() {
		if (isDisposed()) {
			return -1;
		}
		return tableColumnCore.getWidth() - 2;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableCell#getHeight()
	 */
	@Override
	public int getHeight() {
		if (bounds == null) {
			if (tableRowSWT == null) {
				return 20; // probably disposed
			}
			return tableRowSWT.getView().getRowDefaultHeight();
		}
		return bounds.height - (getMarginHeight() * 2);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableCell#getMarginHeight()
	 */
	@Override
	public int getMarginHeight() {
		return marginHeight;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableCell#setMarginHeight(int)
	 */
	@Override
	public void setMarginHeight(int height) {
		marginHeight = height;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableCell#getMarginWidth()
	 */
	@Override
	public int getMarginWidth() {
		return marginWidth;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableCell#setMarginWidth(int)
	 */
	@Override
	public void setMarginWidth(int width) {
		marginWidth = width;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableCell#getBackgroundGraphic()
	 */
	@Override
	public Graphic getBackgroundGraphic() {
		// WARNING: requires SWT Thread!
		return new UISWTGraphicImpl(getBackgroundImage());
	}

	/* (non-Javadoc)
	 * @see TableCellCore#locationChanged()
	 */
	@Override
	public void locationChanged() {
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.impl.TableCellSWTBase#setCursorID(int)
	 */
	@Override
	public boolean setCursorID(int cursorID) {
		if (!super.setCursorID(cursorID)) {
			return false;
		}
		if (!isMouseOver()) {
			return true;
		}
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (isDisposed() || tableRowSWT == null) {
					return;
				}
				if (isMouseOver()) {
					TableViewSWT<?> view = (TableViewSWT<?>) tableRowSWT.getView();
					if (view != null) {
						Composite composite = view.getComposite();
						if (composite != null && !composite.isDisposed()) {
							composite.setCursor(composite.getDisplay().getSystemCursor(
									getCursorID()));
						}
					}
				}
			}
		});
		return true;
	}

	/* (non-Javadoc)
	 * @see TableCellCore#redraw()
	 */
	@Override
	public void redraw() {

		if (tableRowSWT==null || !tableRowSWT.isVisible() || redrawScheduled) {
			return;
		}
		redrawScheduled = true;
		if (DEBUG_CELLPAINT) {
			System.out.println(SystemTime.getCurrentTime() + "r"
					+ tableRowSWT.getIndex() + "c" + tableColumnCore.getPosition()
					+ "} cellredraw via " + Debug.getCompressedStackTrace());
		}
		Utils.execSWTThread(new AERunnable() {

			@Override
			public void runSupport() {
				if (isDisposed()) {
					return;
				}
				redrawScheduled = false;
				if (DEBUG_CELLPAINT) {
					System.out.println(SystemTime.getCurrentTime() + "r"
							+ tableRowSWT.getIndex() + "c" + tableColumnCore.getPosition()
							+ "] cellredraw @ " + bounds);
				}
				if (bounds != null && tableRowSWT != null) {
					TableViewPainted view = (TableViewPainted) tableRowSWT.getView();
					if (view != null) {
						view.swt_updateCanvasImage(bounds, false);
					}
				}
			}
		});
	}

	@Override
	public boolean setForeground(Color color) {
		// Don't need to set when not visible
		if (isInvisibleAndCanRefresh()) {
			return false;
		}

		if (color == colorFG || (color != null && color.equals(colorFG))
				|| (colorFG != null && colorFG.equals(color))) {
			return false;
		}

		colorFG = color;
		setFlag(FLAG_VISUALLY_CHANGED_SINCE_REFRESH);

		return true;
	}

	@Override
	public Point getSize() {
		if (bounds == null) {
			return new Point(0, 0);
		}
		return new Point(bounds.width - (marginWidth * 2), bounds.height
				- (marginHeight * 2));
	}

	@Override
	public Rectangle getBounds() {
		if (bounds == null) {
			return new Rectangle(0, 0, 0, 0);
		}
		return new Rectangle(bounds.x + marginWidth, bounds.y + marginHeight,
				bounds.width - (marginWidth * 2), bounds.height - (marginHeight * 2));
	}

	public Rectangle getBoundsRaw() {
		if (bounds == null) {
			return null;
		}
		return new Rectangle(bounds.x, bounds.y, bounds.width, bounds.height);
	}

	@Override
	public Rectangle getBoundsOnDisplay() {
		if (isDisposed() || tableRowSWT == null) {
			return null;
		}
		Rectangle bounds = getBoundsRaw();
		if (bounds == null) {
			return null;
		}
		TableViewPainted tv = ((TableViewPainted) tableRowSWT.getView());
		if (tv == null) {
			return null;
		}
		Composite c = tv.getTableComposite();
		if (c == null || c.isDisposed()) {
			return null;
		}
		Point pt = c.toDisplay(bounds.x, bounds.y);
		bounds.x = pt.x;
		bounds.y = pt.y;
		bounds.height = getHeight();
		bounds.width = getWidthRaw();
		return bounds;
	}

	@Override
	public Image getBackgroundImage() {
		if (bounds == null || bounds.isEmpty()) {
			return null;
		}

		Image image = new Image(Display.getDefault(), bounds.width
				- (marginWidth * 2), bounds.height - (marginHeight * 2));

		GC gc = new GC(image);
		gc.setForeground(getBackgroundSWT());
		gc.setBackground(getBackgroundSWT());
		gc.fillRectangle(0, 0, bounds.width, bounds.height);
		gc.dispose();

		return image;
	}

	@Override
	public Color getForegroundSWT() {
		return colorFG;
	}

	@Override
	public Color getBackgroundSWT() {
		return null;
	}

	public void setBoundsRaw(Rectangle bounds) {
		this.bounds = bounds;
	}

	@Override
	public boolean uiSetText(String text) {
		boolean bChanged = !stringEquals(this.text, text);
		if (bChanged) {
			this.text = text;
		}
		return bChanged;
	}
}
