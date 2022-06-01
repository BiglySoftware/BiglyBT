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

package com.biglybt.ui.swt.views.table.impl;

import java.util.*;

import org.eclipse.swt.graphics.*;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableRowSWT;
import com.biglybt.ui.swt.views.table.TableViewSWT;

import com.biglybt.ui.common.table.TableCellCore;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.common.table.TableView;

@SuppressWarnings("rawtypes")
public abstract class TableRowSWTBase
	implements TableRowSWT
{
	public static boolean DEBUG_ROW_PAINT = false;

	protected final Object lock;

	private final TableViewSWT tv;

	private final TableRowCore parentRow;

	private final Object coreDataSource;

	private int lastIndex = -1;

	private int visibleRowIndex = -1;
	
	protected Map<String, TableCellSWTBase> mTableCells;

	private boolean bDisposed;

	private Object pluginDataSource;

	protected boolean wasShown = false;

	private boolean bSetNotUpToDateLastRefresh;

	private ArrayList<TableRowMouseListener> mouseListeners;

	private Map<String, Object> dataList;

	private int alpha = 255;

	private int fontStyle;

	private boolean expanded;

	private boolean isAttention;

	public TableRowSWTBase(Object lock, TableRowCore parentRow, TableViewSWT tv,
			Object dataSource) {
		this.lock = lock;
		this.parentRow = parentRow;
		this.tv = tv;
		this.coreDataSource = dataSource;
	}

	/* (non-Javadoc)
	 * @see TableRowCore#invalidate()
	 */
	@Override
	public void invalidate() {
		invalidate(false);
	}
	@Override
	public void invalidate(boolean mustRefersh) {
		synchronized (lock) {
  		if (bDisposed || mTableCells == null) {
  			return;
  		}

  		for (TableCellCore cell : mTableCells.values()) {
  			if (cell != null) {
  				cell.invalidate(mustRefersh);
  			}
  		}
		}
	}

	public boolean doesAnyCellHaveFlag(int flag) {
		synchronized (lock) {
  		if (bDisposed || mTableCells == null) {
  			return false;
  		}

  		for (TableCellCore cell : mTableCells.values()) {
  			if ((cell instanceof TableCellSWTBase)
  					&& ((TableCellSWTBase) cell).hasFlag(flag)) {
  				return true;
  			}
  		}
  		return false;
		}
	}


	public void setCellFlag(int flag) {
		synchronized (lock) {
  		if (bDisposed || mTableCells == null) {
  			return;
  		}

  		for (TableCellCore cell : mTableCells.values()) {
  			if (cell != null) {
  				((TableCellSWTBase) cell).setFlag(flag);
  			}
  		}
		}
	}

	public void clearCellFlag(int flag, boolean subRows) {
		synchronized (lock) {
  		if (bDisposed || mTableCells == null) {
  			return;
  		}

  		for (TableCellCore cell : mTableCells.values()) {
  			if (cell != null) {
  				((TableCellSWTBase) cell).clearFlag(flag);
  			}
  		}
  		if (subRows) {
  			TableRowCore[] subRowsWithNull = getSubRowsWithNull();
  			for (TableRowCore row : subRowsWithNull) {
  				((TableRowSWTBase) row).clearCellFlag(flag, false);
  			}
  		}
		}
	}

	/* (non-Javadoc)
	 * @see TableRowCore#delete()
	 */
	@Override
	public void delete() {
		synchronized (lock) {

			if (bDisposed) {
				return;
			}

			if (mTableCells != null) {
  			for (TableCellCore cell : mTableCells.values()) {
  				try {
  					if (cell != null && !cell.isDisposed()) {
  						cell.dispose();
  					}
  				} catch (Exception e) {
  					Debug.out(e);
  				}
  			}
			}

			setHeight(0);

			bDisposed = true;
		}
	}

	/* (non-Javadoc)
	 * @see TableRowCore#refresh(boolean)
	 */
	@Override
	public List refresh(boolean bDoGraphics) {
		if (bDisposed) {
			return Collections.EMPTY_LIST;
		}

		boolean bVisible = isVisible();

		return refresh(bDoGraphics, bVisible);
	}

	/* (non-Javadoc)
	 * @see TableRowCore#locationChanged(int)
	 */
	@Override
	public void locationChanged(int iStartColumn) {
		if (bDisposed || !isVisible()) {
			return;
		}
		synchronized (lock) {
			if (mTableCells == null) {
				return;
			}

  		for (TableCellCore cell : mTableCells.values()) {
  			if (cell != null && cell.getTableColumn().getPosition() > iStartColumn) {
  				cell.locationChanged();
  			}
  		}
		}
	}

	/* (non-Javadoc)
	 * @see TableRowCore#getDataSource(boolean)
	 */
	@Override
	public Object getDataSource(boolean bCoreObject) {
		// we don't want to do this because we need callers to be able to get access to the
		// underlying datasource during the disposal process so they can release any
		// associated resources
		//if (bDisposed) {
		//	return null;
		//}

		if (bCoreObject) {
			return coreDataSource;
		}

		if (pluginDataSource != null) {
			return pluginDataSource;
		}

		pluginDataSource = PluginCoreUtils.convert(coreDataSource, bCoreObject);

		return pluginDataSource;
	}

	/* (non-Javadoc)
	 * @see TableRowCore#getIndex()
	 */
	@Override
	public int getIndex() {
		if (bDisposed) {
			return -1;
		}

		if (lastIndex >= 0) {
			if (parentRow != null) {
				return lastIndex;
			}
			TableRowCore row = tv.getRowQuick(lastIndex);
			if (row == this) {
				return lastIndex;
			}
		}

		// don't set directly to lastIndex, so setTableItem will eventually do
		// its job
		return tv.indexOf(this);
	}

	public boolean
	setVisibleRowIndex( int index )
	{
		if ( index != visibleRowIndex ){
			visibleRowIndex = index;
			invalidate();
			return( true );
		}
		return(false);
	}
		
	public int
	getVisibleRowIndex()
	{
		return( visibleRowIndex );
	}
	
	/* (non-Javadoc)
	 * @see TableRowCore#getTableCellCore(java.lang.String)
	 */
	@Override
	public TableCellCore getTableCellCore(String name) {
		synchronized (lock) {
  		if (bDisposed || mTableCells == null) {
  			return null;
  		}

  		return mTableCells.get(name);
		}
	}

	/* (non-Javadoc)
	 * @see TableRowCore#isVisible()
	 */
	@Override
	public boolean isVisible() {
		return tv.isRowVisible(this);
	}

	/* (non-Javadoc)
	 * @see TableRowCore#setTableItem(int, boolean)
	 */
	@Override
	public boolean setTableItem(int newIndex) {
		if (bDisposed) {
			System.out.println("XXX setTI: bDisposed from "
					+ Debug.getCompressedStackTrace());
			return false;
		}
		boolean changedIndex = lastIndex != newIndex;
		if (changedIndex) {
			//System.out.println("row " + newIndex + " from " + lastIndex + ";"
			//		+ getSortColumnCells(null)[0].getSortValue() + ";"
			//		+ getView().isRowVisible(this) + ";" + getDataSource(true) + ";"
			//		+ Debug.getCompressedStackTrace(6));
			lastIndex = newIndex;
		}

		return changedIndex;
	}

	/* (non-Javadoc)
	 * @see TableRowCore#setSelected(boolean)
	 */
	@Override
	public void setSelected(boolean selected) {
		TableView tableView = getView();
		if (tableView instanceof TableViewSWT) {
			((TableViewSWT<?>) tableView).setRowSelected(this, selected, true);
		}
	}

	/* (non-Javadoc)
	 * @see TableRowCore#isRowDisposed()
	 */
	@Override
	public boolean isRowDisposed() {
		return bDisposed;
	}

	/* (non-Javadoc)
	 * @see TableRowCore#setUpToDate(boolean)
	 */
	@Override
	public void setUpToDate(boolean upToDate) {
		synchronized (lock) {
  		if (bDisposed || mTableCells == null) {
  			return;
  		}

  		for (TableCellCore cell : mTableCells.values()) {
  			if (cell != null) {
  				cell.setUpToDate(upToDate);
  			}
  		}
		}
	}

	/* (non-Javadoc)
	 * @see TableRowCore#refresh(boolean, boolean)
	 */
	@Override
	public List<TableCellCore> refresh(boolean bDoGraphics, boolean bVisible) {
		// If this were called from a plugin, we'd have to refresh the sorted column
		// even if we weren't visible
		List<TableCellCore> list = Collections.EMPTY_LIST;

		if (bDisposed) {
			return list;
		}

		if (!bVisible) {
			if (!bSetNotUpToDateLastRefresh) {
				setUpToDate(false);
				bSetNotUpToDateLastRefresh = true;
			}
			return list;
		}

		bSetNotUpToDateLastRefresh = false;

		//System.out.println(SystemTime.getCurrentTime() + "refresh " + getIndex() + ";vis=" + bVisible + " via " + Debug.getCompressedStackTrace(8));

		tv.invokeRefreshListeners(this);

		// Make a copy of cells so we don't lock while refreshing
		Collection<TableCellCore> lTableCells = null;
		synchronized (lock) {
			if (mTableCells != null) {
				lTableCells = new ArrayList<>(mTableCells.values());
			}
		}

		if (lTableCells != null) {
  		for (TableCellCore cell : lTableCells) {
  			if (cell == null || cell.isDisposed()) {
  				continue;
  			}
  			TableColumn column = cell.getTableColumn();
  			//System.out.println(column);
			  if (!tv.hasSortColumn(column) && !tv.isColumnVisible(column)) {
  				//System.out.println("skip " + column);
  				continue;
  			}
  			boolean cellVisible = bVisible && cell.isShown();
				boolean changed = cell.refresh(bDoGraphics, bVisible, cellVisible);
  			if (changed) {
  				if (list == Collections.EMPTY_LIST) {
  					list = new ArrayList<>(lTableCells.size());
  				}
  				list.add(cell);
  			}

  		}
		}

		//System.out.println();
		return list;
	}

	/* (non-Javadoc)
	 * @see TableRowCore#getView()
	 */
	@Override
	public TableViewSWT<?> getView() {
		return tv;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableRow#addMouseListener(com.biglybt.pif.ui.tables.TableRowMouseListener)
	 */
	@Override
	public void addMouseListener(TableRowMouseListener listener) {
		synchronized (lock) {

			if (mouseListeners == null) {
				mouseListeners = new ArrayList<>(1);
			}

			mouseListeners.add(listener);

		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableRow#removeMouseListener(com.biglybt.pif.ui.tables.TableRowMouseListener)
	 */
	@Override
	public void removeMouseListener(TableRowMouseListener listener) {
		synchronized (lock) {

			if (mouseListeners == null) {
				return;
			}

			mouseListeners.remove(listener);

		}
	}

	/* (non-Javadoc)
	 * @see TableRowCore#invokeMouseListeners(com.biglybt.pif.ui.tables.TableRowMouseEvent)
	 */
	@Override
	public void invokeMouseListeners(TableRowMouseEvent event) {
		ArrayList<TableRowMouseListener> listeners;

		synchronized (lock) {
			if ( mouseListeners == null ){
				listeners = null;
			}else{
				listeners = new ArrayList<>(mouseListeners);
			}
		}

		if (listeners == null) {
			return;
		}

		for (int i = 0; i < listeners.size(); i++) {
			try {
				TableRowMouseListener l = listeners.get(i);

				l.rowMouseTrigger(event);

			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	/* (non-Javadoc)
	 * @see TableRowCore#isMouseOver()
	 */
	@Override
	public boolean isMouseOver() {
		return tv.getTableRowWithCursor() == this;
	}

	public boolean
	canExpand()
	{
		return( tv.isExpandEnabled());
	}

	/* (non-Javadoc)
	 * @see TableRowCore#isExpanded()
	 */
	@Override
	public boolean isExpanded() {
		return expanded;
	}

	/* (non-Javadoc)
	 * @see TableRowCore#setExpanded(boolean)
	 */
	@Override
	public void setExpanded(boolean b) {
		if ( canExpand() ){

			if ( expanded != b ){

				expanded = b;

				tv.invokeExpansionChangeListeners( this, b );
			}
		}
	}

	/* (non-Javadoc)
	 * @see TableRowCore#getParentRowCore()
	 */
	@Override
	public TableRowCore getParentRowCore() {
		return parentRow;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableRow#getDataSource()
	 */
	@Override
	public Object getDataSource() {
		return getDataSource(false);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableRow#getTableID()
	 */
	@Override
	public String getTableID() {
		return tv.getTableID();
	}

	@Override
	public void setRequestAttention(boolean on){
		if ( on != isAttention ){
		
			isAttention = on;
			
			redraw();
		}
	}
	
	public boolean
	isRequestAttention()
	{
		return( isAttention );
	}
	
	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableRow#isValid()
	 */
	@Override
	public boolean isValid() {
		synchronized (lock) {
  		if (bDisposed || mTableCells == null) {
  			return true;
  		}

  		boolean valid = true;
  		for (TableCell cell : mTableCells.values()) {
  			if (cell != null && cell.isValid()) {
  				return false;
  			}
  		}

 		return valid;
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableRow#getTableCell(java.lang.String)
	 */
	@Override
	public TableCell getTableCell(String field) {
		synchronized (lock) {
  		if (bDisposed || mTableCells == null) {
  			return null;
  		}

  		return mTableCells.get(field);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableRow#isSelected()
	 */
	@Override
	public boolean isSelected() {
		return getView().isSelected(this);
	}

	public boolean isFocused() {
		return getView().getFocusedRow() == this;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableRow#getData(java.lang.String)
	 */
	@Override
	public Object getData(String id) {
		synchronized (this) {
			return dataList == null ? null : dataList.get(id);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableRow#setData(java.lang.String, java.lang.Object)
	 */
	@Override
	public void setData(String id, Object data) {
		synchronized (this) {
			if (dataList == null) {
				dataList = new HashMap<>(1);
			}
			if (data == null) {
				dataList.remove(id);
			} else {
				dataList.put(id, data);
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableRowSWT#setIconSize(org.eclipse.swt.graphics.Point)
	 */
	@Override
	public abstract boolean setIconSize(Point pt);

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableRowSWT#getForeground()
	 */
	@Override
	public abstract Color getForeground();

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableRowSWT#getBackground()
	 */
	@Override
	public abstract Color getBackground();

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableRowSWT#getTableCellSWT(java.lang.String)
	 */
	@Override
	public TableCellSWT getTableCellSWT(String name) {
		synchronized (lock) {
  		if (bDisposed || mTableCells == null) {
  			return null;
  		}

  		TableCellCore cell = mTableCells.get(name);
  		if (cell instanceof TableCellSWT) {
  			return (TableCellSWT) cell;
  		}
  		return null;
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableRowSWT#getBounds()
	 */
	@Override
	public abstract Rectangle getBounds();

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableRowSWT#setBackgroundImage(org.eclipse.swt.graphics.Image)
	 */
	@Override
	public abstract void setBackgroundImage(Image image);

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableRowSWT#getFontStyle()
	 */
	@Override
	public int getFontStyle() {
		return fontStyle;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableRowSWT#setFontStyle(int)
	 */
	@Override
	public boolean setFontStyle(int style) {
		if (fontStyle == style) {
			return false;
		}

		fontStyle = style;
		invalidate();

		return true;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableRowSWT#getAlpha()
	 */
	@Override
	public int getAlpha() {
		return alpha;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableRowSWT#setAlpha(int)
	 */
	@Override
	public boolean setAlpha(int alpha) {
		if (alpha == this.alpha) {
			return false;
		}
		this.alpha = alpha;
		return true;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableRowSWT#setWidgetSelected(boolean)
	 */
	@Override
	public abstract void setWidgetSelected(boolean selected);

	@Override
	public boolean isShown() {
		return wasShown;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.TableRowSWT#setShown(boolean, boolean)
	 */
	@Override
	public boolean setShown(boolean b, boolean force) {
		if (bDisposed) {
			return false;
		}
		//System.out.println(SystemTime.getCurrentTime() + "swtShown " + getIndex() + ";b=" + b + " via " + Debug.getCompressedStackTrace(8));

		if (b == wasShown && !force) {
			return false;
		}
		wasShown = b;

		Collection<TableCellCore> lTableCells = null;
		synchronized (lock) {
			if (mTableCells != null) {
				lTableCells = new ArrayList<>(mTableCells.values());
			}
		}

		if (lTableCells != null) {
  		for (TableCellCore cell : lTableCells) {
  			if (cell != null) {
  				cell.invokeVisibilityListeners(b
  						? TableCellVisibilityListener.VISIBILITY_SHOWN
  						: TableCellVisibilityListener.VISIBILITY_HIDDEN, true);
  			}
  		}
		}

		return true;

		/* Don't need to refresh; paintItem will trigger a refresh on
		 * !cell.isUpToDate()
		 *
		if (b) {
			refresh(b, true);
		}
		/**/
	}

	/* (non-Javadoc)
	 * @see TableRowCore#redraw()
	 */
	@Override
	public void redraw() {
		redraw(false);
	}

	/*
	public abstract void setSubItemCount(int length);

	public abstract int getSubItemCount();

	public abstract TableRowCore linkSubItem(int indexOf);

	public abstract void setSubItems(Object[] datasources);

	public abstract TableRowCore[] getSubRowsWithNull();

	public abstract void removeSubRow(Object datasource);
	*/
}
