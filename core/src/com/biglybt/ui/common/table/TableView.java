/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.common.table;

import java.util.HashSet;
import java.util.List;

import com.biglybt.core.util.AEDiagnosticsEvidenceGenerator;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableRow;

/**
 * @author TuxPaper
 * @created Feb 2, 2007
 *
 */
public interface TableView<DATASOURCETYPE>
	extends AEDiagnosticsEvidenceGenerator
{
	/**
	 * @param listener
	 */
	void addCountChangeListener(TableCountChangeListener listener);

	/** Adds a dataSource to the table as a new row.  If the data source is
	 * already added, a new row will not be added.  This function runs
	 * asynchronously, so the rows creation is not guaranteed directly after
	 * calling this function.
	 *
	 * You can't add datasources until the table is initialized
	 *
	 * @param dataSource data source to add to the table
	 */
	void addDataSource(DATASOURCETYPE dataSource);

	/**
	 * Add a list of dataSources to the table.  The array passed in may be
	 * modified, so make sure you don't need it afterwards.
	 *
	 * You can't add datasources until the table is initialized
	 *
	 * @param dataSources
	 */
	void addDataSources(DATASOURCETYPE[] dataSources);

	void addLifeCycleListener(TableLifeCycleListener l);

	void addRefreshListener(TableRefreshListener l, boolean trigger);

	/**
	 * @param listener
	 * @param bFireSelection
	 */
	void addSelectionListener(TableSelectionListener listener, boolean trigger);

	/**
	 * The data set that this table represents has been changed.  This is not
	 * for listening on changes to data sources changing within the table
	 *
	 * @param l
	 * @param trigger
	 */
	void addTableDataSourceChangedListener(TableDataSourceChangedListener l,
			boolean trigger);

	/**
	 * Send Selected rows to the clipboard in a SpreadSheet friendly format
	 * (tab/cr delimited)
	 */
	void clipboardSelected();

	/**
	 * Invalidate all the cells in a column
	 *
	 * @param sColumnName Name of column to invalidate
	 */
	void columnInvalidate(String sColumnName);

	void columnInvalidate(String sColumnName, boolean forceRefresh );

	/**
	 * @param tableColumn
	 */
	void columnInvalidate(TableColumnCore tableColumn);

	void delete();

	/**
	 * Retrieve a list of <pre>TableCell</pre>s, in the last sorted order.
	 * The order will not be of the supplied cell's sort unless the table
	 * has been sorted by that column previously.
	 * <p>
	 * ie.  You can sort on the 5th column, and retrieve the cells for the
	 *      3rd column, but they will be in order of the 5th columns sort.
	 *
	 * @param sColumnName Which column cell's to return.  This does not sort
	 *         the array on the column.
	 * @return array of cells
	 */
	TableCellCore[] getColumnCells(String columnName);

	/**
	 * @return a new, unsorted Hashset of all datasources
	 */
	HashSet<DATASOURCETYPE> getDataSources();

	/**
	 * @return a new, unsorted Hashset of datasources
	 */
	HashSet<DATASOURCETYPE> getDataSources( boolean include_filtered );

	/**
	 * @note May not necessarily return DATASOURCETYPE if table has subrows
	 */
	Object getFirstSelectedDataSource();

	/**
	 * @return
	 */
	String getPropertiesPrefix();

	/**
	 * Get the row associated with a datasource
	 * @param dataSource a reference to a core Datasource object
	 * 										(not a plugin datasource object)
	 * @return The row, or null
	 */
	TableRowCore getRow(DATASOURCETYPE dataSource);

	/** Get all the rows for this table, in the order they are displayed
	 *
	 * @return a list of TableRowSWT objects in the order the user sees them
	 */
	TableRowCore[] getRows();

	public TableRowCore[] getRowsAndSubRows( boolean includeHidden );
	
	/** Returns an array of all selected Data Sources.  Null data sources are
	 * ommitted.
	 *
	 * @return an array containing the selected data sources
	 *
	 * @note May not necessarily return DATASOURCETYPE if table has subrows
	 */
	List<Object> getSelectedDataSources();

	/**
	 * Returns an array of all selected Data Sources.  Null data sources are
	 * ommitted.
	 *
	 * @param bCoreDataSource
	 * @return an array containing the selected data sources
	 */
	Object[] getSelectedDataSources(boolean bCoreDataSource);

	/**
	 * Returns an array of all selected TableRowSWT.  Null data sources are
	 * ommitted.
	 *
	 * @return an array containing the selected data sources
	 */
	TableRowCore[] getSelectedRows();

	boolean hasSortColumn(TableColumn column);

	int getSortColumnCount();

	TableColumnCore[] getSortColumns();

	/**
	 * Add a column to the sorting. If the column already is in the sort list,
	 * flip its order
	 */
	void addSortColumn(TableColumnCore column);

	boolean setSortColumns(TableColumnCore[] newSortColumns, boolean allowOrderChange);

	/**
	 * Sort rows using the columns specified in {@link #getSortColumns()}
	 */
	void sortRows(boolean bForceDataRefresh,boolean async);

	/**
	 * @return
	 */
	boolean isDisposed();

	/**
	 * Process the queue of datasources to be added and removed
	 *
	 */
	void processDataSourceQueue();

	/**
	 * @param bForceSort
	 */
	void refreshTable(boolean bForceSort);

	/**
	 * Remove all the data sources (table rows) from the table.
	 */
	void removeAllTableRows();

	/**
	 * @param dataSource
	 */
	void removeDataSource(DATASOURCETYPE dataSource);

	/**
	 * @param l
	 */
	void removeTableDataSourceChangedListener(TableDataSourceChangedListener l);

	/** For every row source, run the code provided by the specified
	 * parameter.
	 *
	 * @param runner Code to run for each row/datasource
	 */
	void runForAllRows(TableGroupRowRunner runner);

	/** For every row source, run the code provided by the specified
	 * parameter.
	 *
	 * @param runner Code to run for each row/datasource
	 */
	void runForAllRows(TableGroupRowVisibilityRunner runner);

	/**
	 * @param runner
	 */
	void runForSelectedRows(TableGroupRowRunner runner);

	/**
	 * Does not fire off selection events
	 */
	void selectAll();

	void setEnableTabViews(boolean enableTabViews, boolean expandByDefault);

	/**
	 * @param newDataSource
	 */
	void setParentDataSource(Object newDataSource);


	Object getParentDataSource();

	/**
	 * @param iHeight Height will be adjusted for larger DPI
	 */
	void setRowDefaultHeight(int iHeight);

	void setRowDefaultHeightEM(float lineHeight);

	void setRowDefaultHeightPX(int realPX);

	void setSelectedRows(TableRowCore[] rows);

	/**
	 * @param bIncludeQueue
	 * @return
	 */
	int size(boolean bIncludeQueue);

	/**
	 * @return
	 */
	TableRowCore getFocusedRow();

	/**
	 * @return
	 */
	String getTableID();

	public TableViewCreator
	getTableViewCreator();

	/**
	 * @param x
	 * @param y
	 * @return
	 */
	TableRowCore getRow(int x, int y);

	/**
	 * @param dataSource
	 * @return
	 */
	boolean dataSourceExists(DATASOURCETYPE dataSource);

	/**
	 * @return
	 */
	TableColumnCore[] getVisibleColumns();

	TableRowCore[] getVisibleRows();
	
	/**
	 * @param dataSources
	 */
	void removeDataSources(DATASOURCETYPE[] dataSources);

	/**
	 * @return
	 *
	 * @since 3.0.0.7
	 */
	int getSelectedRowsSize();

	public void scrollVertically( int distance );
	
	/**
	 * @param row
	 * @return
	 *
	 * @since 3.0.0.7
	 */
	int indexOf(TableRowCore row);

	/**
	 * @param row
	 * @return
	 *
	 * @since 3.0.4.3
	 */
	boolean isRowVisible(TableRowCore row);

	/**
	 * @return
	 *
	 * @since 3.0.4.3
	 */
	TableCellCore getTableCellWithCursor();

	/**
	 * Retrieves the row that has the cursor over it
	 *
	 * @return null if mouse isn't over a row
	 *
	 * @since 3.0.4.3
	 */
	TableRowCore getTableRowWithCursor();

	/**
	 * @return
	 *
	 * @since 3.0.4.3
	 */
	int getRowDefaultHeight();

	boolean isColumnVisible(TableColumn column);

	/**
	 * @param position
	 * @return
	 *
	 * @since 3.0.4.3
	 */
	TableRowCore getRow(int position);

	/**
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	Class getDataSourceType();

	/**
	 * @param columnName
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	TableColumn getTableColumn(String columnName);

	void setEnabled(boolean enable);

	boolean canHaveSubItems();

	/**
	 * @param tableRowImpl
	 * @return
	 *
	 * @since 4.4.0.5
	 */
	boolean isSelected(TableRow row);

	boolean isUnfilteredDataSourceAdded(Object ds);

	/**
	 * @param visible
	 *
	 * @since 4.6.0.5
	 */
	void setHeaderVisible(boolean visible);

	/**
	 * @return
	 *
	 * @since 4.6.0.5
	 */
	boolean getHeaderVisible();

	/**
	 *
	 *
	 * @since 4.6.0.5
	 */
	void processDataSourceQueueSync();

	/**
	 *
	 *
	 * @since 4.6.0.5
	 */
	int getMaxItemShown();

	/**
	 * @param newIndex
	 *
	 * @since 4.6.0.5
	 */
	void setMaxItemShown(int newIndex);

	int getRowCount();

	int[] getRowAndSubRowCount();
	
	void resetLastSortedOn();

	boolean hasChangesPending();
	
	TableColumnCore[] getAllColumns();

	void removeCountChangeListener(TableCountChangeListener l);

	void addExpansionChangeListener(TableExpansionChangeListener listener);

	void removeExpansionChangeListener(TableExpansionChangeListener listener);

	boolean isTableSelected();
	
	boolean canMoveBack();
	
	void moveBack();
	
	boolean canMoveForward();
	
	void moveForward();
}
