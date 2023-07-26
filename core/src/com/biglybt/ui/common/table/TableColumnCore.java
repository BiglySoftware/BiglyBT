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

package com.biglybt.ui.common.table;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.biglybt.core.util.IndentWriter;
import com.biglybt.pif.ui.tables.*;

/**
 * Core Table Column functions are those available to plugins plus
 * some core-only functions.  The core-only functions are listed here.
 *
 * @see com.biglybt.ui.common.table.impl.TableColumnManager
 */
public interface TableColumnCore
	extends TableColumn, Comparator<TableRowCore>
{
	/**
	 * Set the internal flag specifying whether the column has been added to the
	 * TableColumnManager.  Some functions can not be run after a column has been
	 * added.
	 *
	 * @since 2.1.0.0
	 */
	public void setColumnAdded();

	/**
	 * Retrieve whether the column has been added to the TableColumnManager
	 *
	 * @return true - Column has been added<br>
	 *         false - Column has not been added
	 *
	 * @since 2.1.0.0
	 */
	public boolean getColumnAdded();

	/**
	 * Changes what {@link TableCellCore.getDataSource()} and
	 * {@link TableRowCore.getDataSource()} return.
	 *
	 * @param bCoreDataSource true - returns a core object<br>
	 *                        false - returns a plugin object (if available)
	 *
	 * @since 2.1.0.0
	 */
	public void setUseCoreDataSource(boolean bCoreDataSource);

	/**
	 * Retrieve whether a core or plugin object is sent via getDataSource()
	 *
	 * @return true - returns a core object<br>
	 *         false - returns a plugin object (if available)
	 *
	 * @since 2.1.0.0
	 */
	public boolean getUseCoreDataSource();

	/**
	 * Send a refresh trigger to all listeners stored in TableColumn
	 *
	 * @param cell the cell is being refreshed
	 * @throws Throwable
	 *
	 * @since 2.1.0.0
	 */
	public void invokeCellRefreshListeners(TableCell cell, boolean fastRefresh) throws Throwable;

	/**
	 * Retrieve all the refresh listeners for the cell
	 *
	 * @return Cell refresh listeners
	 *
	 * @since 2.5.0.0
	 */
	public List getCellRefreshListeners();

	/**
	 * Send a cellAdded trigger to all listeners stored in TableColumn
	 *
	 * @param cell the cell is being added
	 *
	 * @since 2.1.0.0
	 */
	public void invokeCellAddedListeners(TableCell cell);

	/**
	 * Retreive all the Cell Added listeners
	 *
	 * @return cell added listeners for this cell
	 *
	 * @since 2.5.0.0
	 */
	public List getCellAddedListeners();

	/**
	 * Send a dispose trigger to all listeners stored in TableColumn
	 *
	 * @param cell the cell is being disposed
	 *
	 * @since 2.1.0.0
	 */
	public void invokeCellDisposeListeners(TableCell cell);

	/**
	 * Send a tool tip event to the tool tip listeners
	 *
	 * @param cell Cell to get the tool tip event
	 * @param type
	 *
	 * @since 2.1.0.2
	 */
	public void invokeCellToolTipListeners(TableCellCore cell, int type);

	/**
	 * Send a mouse event to the cell mouse listeners
	 *
	 * @param event Mouse Event to send
	 *
	 * @since 2.4.0.0
	 */
	public void invokeCellMouseListeners(TableCellMouseEvent event);

	public void invokeCellMenuListeners(TableCellMenuEvent event);

	/**
	 * Send a visibility event to the cell's visibility listeners
	 *
	 * @param visibility Visibility state
	 *
	 * @since 2.5.0.2
	 */
	public void invokeCellVisibilityListeners(TableCellCore cell, int visibility);

	/**
	 * Sets the position of the column without adjusting the other columns.
	 * This will cause duplicate columns, and is only useful if you are
	 * adjusting the positions of multiple columns at once.
	 *
	 * @param position new position (0 based)
	 *
	 * @see TableColumnManager.ensureIntegrity()
	 *
	 * @since 2.1.0.0
	 */
	public void setPositionNoShift(int position);

	/**
	 * Load width and position settings from config.
	 * @param mapSettings map to place settings into
	 *
	 * @since 2.1.0.0
	 */
	public void loadSettings(Map mapSettings);

	/**
	 * Save width and position settings to config.
	 * @param mapSettings map to place settings into
	 *
	 * @since 2.1.0.0
	 */
	public void saveSettings(Map mapSettings);

	/**
	 * Returns the key in the properties bundle that has the title of the
	 * column.
	 *
	 * @return Title's language key
	 */
	public String getTitleLanguageKey();

	public String getTitleLanguageKey( boolean with_renames );

	/**
	 * @return # of consecutive errors
	 *
	 * @since 2.1.0.0
	 */
	public int getConsecutiveErrCount();

	/**
	 * @param iCount # of consecutive errors
	 *
	 * @since 2.1.0.0
	 */
	public void setConsecutiveErrCount(int iCount);

	/**
	 * @param menuItem
	 *
	 * @since 2.1.0.0
	 */
	public void removeContextMenuItem(TableContextMenuItem menuItem);

	/**
	 *
	 * @return
	 *
	 * @since 2.1.0.0
	 */
	public TableContextMenuItem[] getContextMenuItems(int menuStyle);

	/**
	 * @return
	 *
	 * @since 2.5.0.0
	 */
	boolean hasCellRefreshListener();

	/**
	 * @return
	 *
	 * @since 2.5.0.0
	 */
	long getLastSortValueChange();

	/**
	 * @param lastSortValueChange
	 *
	 * @since 2.5.0.0
	 */
	void setLastSortValueChange(long lastSortValueChange);

	/**
	 * @param live
	 *
	 * @since 2.5.0.0
	 */
	public void setSortValueLive(boolean live);

	/**
	 * @return
	 *
	 * @since 2.5.0.0
	 */
	public boolean isSortValueLive();

	/**
	 * @param ms
	 *
	 * @since 2.5.0.0
	 */
	public void addRefreshTime(long ms);

	/**
	 * @param writer
	 *
	 * @since 2.5.0.0
	 */
	void generateDiagnostics(IndentWriter writer);

	/**
	 * @param tableID
	 *
	 * @since 2.5.0.2
	 */
	void setTableID(String tableID);

	/**
	 * @return
	 *
	 * @since 2.5.0.2
	 */
	boolean isSortAscending();

	/**
	 * @param bAscending
	 *
	 * @since 2.5.0.2
	 */
	void setSortAscending(boolean bAscending);

	/**
	 * @since 4.7.2.1
	 * @param bAscending
	 */

	void setDefaultSortAscending( boolean bAscending );

	/**
	 * @return
	 *
	 * @since 3.0.1.1
	 */
	boolean hasCellMouseMoveListener();

	void triggerColumnSizeChange(int diff);

	void setAutoTooltip(boolean auto_tooltip);
	boolean doesAutoTooltip();



	/**
	 * @param listenerID
	 * @param listener
	 *
	 * @since 3.1.1.1
	 */
	void addCellOtherListener(String listenerID, Object listener);

	public void removeCellOtherListener(String listenerID,
			Object l);

	/**
	 * @param listenerID
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	Object[] getCellOtherListeners(String listenerID);

	/**
	 * @param listenerID
	 * @return
	 *
	 * @since 4.1.0.5
	 */
	boolean hasCellOtherListeners(String listenerID);

	/**
	 * @since 4005
	 * @return
	 */
	public boolean
	isRemoved();

	/**
	 * @return
	 *
	 * @since 4.0.0.5
	 */
	List<TableColumnExtraInfoListener> getColumnExtraInfoListeners();

	void reset();

	String getClipboardText(TableCell cell);

	boolean handlesDataSourceType(Class<?> cla);

	/**
	 * @param forDataSourceType
	 *
	 * @since 4.6.0.1
	 */
	public void addDataSourceType(Class<?> forDataSourceType);

	public boolean showOnlyImage();

	public TableCellInplaceEditorListener getInplaceEditorListener();

	public boolean hasInplaceEditorListener();

	void setInplaceEditorListener(TableCellInplaceEditorListener l);
	
	public int[]
	getForegroundColor();
	
	public void
	setForegroundColor(
		int[]		rgb );
	
	public int[]
	getBackgroundColor();
	
	public void
	setBackgroundColor(
		int[]		rgb );
	
	boolean isDirty();
}
