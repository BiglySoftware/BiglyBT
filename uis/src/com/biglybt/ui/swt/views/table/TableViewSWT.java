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

package com.biglybt.ui.swt.views.table;

import com.biglybt.ui.common.table.*;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import com.biglybt.pif.ui.tables.TableRowMouseEvent;
import com.biglybt.pif.ui.tables.TableRowMouseListener;
import com.biglybt.pif.ui.tables.TableRowRefreshListener;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.views.table.impl.TableViewSWT_TabsCommon;

/**
 * @author TuxPaper
 * @created Feb 2, 2007
 *
 */
public interface TableViewSWT<DATASOURCETYPE>
	extends TableView<DATASOURCETYPE>
{
	void addKeyListener(KeyListener listener);

	public void addMenuFillListener(TableViewSWTMenuFillListener l);

	DragSource createDragSource(int style);

	DropTarget createDropTarget(int style);

	public Composite getComposite();

	TableRowCore getRow(DropTargetEvent event);

	/**
	 * @param dataSource
	 * @return
	 *
	 * @since 1.0.0.0
	 */
	TableRowSWT getRowSWT(DATASOURCETYPE dataSource);

	Composite getTableComposite();

	void initialize(Composite composite);

	void initialize(UISWTView parent, Composite composite);

	/**
	 * @param image
	 * @return
	 */
	Image obfuscatedImage(Image image);

	/**
	 * @param listener
	 */
	void removeKeyListener(KeyListener listener);

	/**
	 * @param mainPanelCreator
	 */
	void setMainPanelCreator(TableViewSWTPanelCreator mainPanelCreator);


	/**
	 * @param x
	 * @param y
	 * @return
	 *
	 * @since 1.0.0.0
	 */
	TableCellCore getTableCell(int x, int y);

	/**
	 * @return Offset potision of the cursor relative to the cell the cursor is in
	 *
	 * @since 1.0.0.0
	 */
	Point getTableCellMouseOffset(TableCellSWT tableCell);

	/**
	 * @param listener
	 *
	 * @since 1.0.0.0
	 */
	void removeRefreshListener(TableRowRefreshListener listener);

	/**
	 * @param listener
	 *
	 * @since 1.0.0.0
	 */
	void addRefreshListener(TableRowRefreshListener listener);

	/**
	 * @return
	 *
	 * @since 1.0.0.0
	 */
	String getFilterText();

	/**
	 * @param filterCheck
	 *
	 * @since 1.0.0.0
	 */
	void enableFilterCheck(Text txtFilter, com.biglybt.ui.common.table.TableViewFilterCheck<DATASOURCETYPE> filterCheck);

	Text getFilterControl();

	/**
	 * @since 1.0.0.0
	 */
	void disableFilterCheck();

	boolean isFiltered( DATASOURCETYPE ds );

	/**
	 * @param s
	 *
	 * @since 1.0.0.0
	 */
	void setFilterText(String s);

	/**
	 * @param composite
	 * @param min
	 * @param max
	 *
	 * @since 1.0.0.0
	 */
	boolean enableSizeSlider(Composite composite, int min, int max);

	void disableSizeSlider();

	/**
	 * @param listener
	 *
	 * @since 1.0.0.0
	 */
	void addRowPaintListener(TableRowSWTPaintListener listener);

	/**
	 * @param listener
	 *
	 * @since 1.0.0.0
	 */
	void removeRowPaintListener(TableRowSWTPaintListener listener);

	/**
	 * @param listener
	 *
	 * @since 1.0.0.0
	 */
	void removeRowMouseListener(TableRowMouseListener listener);

	/**
	 * @param listener
	 *
	 * @since 1.0.0.0
	 */
	void addRowMouseListener(TableRowMouseListener listener);

	/**
	 * @since 1.0.0.0
	 */
	void refilter();

	/**
	 * @param menuEnabled
	 *
	 * @since 1.0.0.0
	 */
	void setMenuEnabled(boolean menuEnabled);

	/**
	 * @return
	 *
	 * @since 1.0.0.0
	 */
	boolean isMenuEnabled();

	void packColumns();

	void visibleRowsChanged();

	void invokePaintListeners(GC gc, TableRowCore row, TableColumnCore column,
			Rectangle cellArea);

	boolean isVisible();

	TableColumnCore getTableColumnByOffset(int x);

	TableRowSWT getTableRow(int x, int y, boolean anyX);

	public void setRowSelected(final TableRowCore row, boolean selected, boolean trigger);

	void editCell(TableColumnCore column, int row);

	void invokeRowMouseListener(TableRowMouseEvent event);

	boolean isDragging();

	KeyListener[] getKeyListeners();

	TableViewSWTFilter getSWTFilter();

	void triggerDefaultSelectedListeners(TableRowCore[] selectedRows,
			int stateMask);

	void sortColumn(boolean bForceDataRefresh);

	void openFilterDialog();

	boolean isSingleSelection();

	void expandColumns();

	void showColumnEditor();

	boolean isTabViewsEnabled();

	boolean getTabViewsExpandedByDefault();

	String[] getTabViewsRestrictedTo();

	Composite createMainPanel(Composite composite);

	void tableInvalidate();

	void showRow(TableRowCore rowToShow);

	TableRowCore getRowQuick(int index);

	void invokeRefreshListeners(TableRowCore row);

	TableViewSWT_TabsCommon getTabsCommon();

	void invokeExpansionChangeListeners( TableRowCore row, boolean expanded );
}
