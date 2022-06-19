/*
 * Created on 19-Apr-2004
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.pifimpl.local.ui.tables;

import com.biglybt.pif.ui.UIRuntimeException;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnCreationListener;
import com.biglybt.pif.ui.tables.TableContextMenuItem;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pifimpl.local.ui.UIManagerImpl;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableStructureEventDispatcher;
import com.biglybt.ui.common.table.impl.TableColumnImpl;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.table.impl.TableContextMenuManager;

/** Manage Tables
 *
 * There's a TableManager per plugin interface
 *
 * @author TuxPaper
 * @since 2.0.8.5
 */
public class TableManagerImpl implements TableManager
{
	private UIManagerImpl ui_manager;

	public TableManagerImpl(UIManagerImpl _ui_manager) {
		ui_manager = _ui_manager;
	}

	@Override
	public TableColumn createColumn(final String tableID, final String cellID) {
		return new TableColumnImpl(tableID, cellID);
	}

	@Override
	public void registerColumn(final Class forDataSourceType, final String cellID,
			final TableColumnCreationListener listener) {

		TableColumnManager tcManager = TableColumnManager.getInstance();

		tcManager.registerColumn(forDataSourceType, cellID, listener);

		String[] tables = tcManager.getTableIDs();

		for (String tid : tables) {

			// we don't know which tables are affected at this point to refresh all.
			// if this proves to be a performance issue then we would have to use the
			// forDataSourceType to derive affected tables somehow

			TableStructureEventDispatcher tsed = TableStructureEventDispatcher.getInstance(
					tid);

			tsed.tableStructureChanged(true, forDataSourceType);
		}

	}

	@Override
	public void unregisterColumn( Class forDataSourceType, String cellID) {

		TableColumnManager tcManager = TableColumnManager.getInstance();

		tcManager.unregisterColumn(forDataSourceType, cellID );

		String[] tables = tcManager.getTableIDs();

		for (String tid : tables) {

			TableColumnCore col = tcManager.getTableColumnCore(tid, cellID);

			if (col != null) {
				col.remove();
			}
		}
	}

	@Override
	public void addColumn(final TableColumn tableColumn) {
		if (!(tableColumn instanceof TableColumnImpl))
			throw (new UIRuntimeException(
					"TableManager.addColumn(..) can only add columns created by createColumn(..)"));

		TableColumnManager.getInstance().addColumns(new TableColumnCore[] {	(TableColumnCore) tableColumn });

		TableStructureEventDispatcher tsed = TableStructureEventDispatcher.getInstance(tableColumn.getTableID());

		tsed.tableStructureChanged(true, tableColumn.getForDataSourceType());
	}

	@Override
	public TableContextMenuItem addContextMenuItem(TableContextMenuItem parent,
			String resourceKey) {
		if (!(parent instanceof TableContextMenuItemImpl)) {
			throw new UIRuntimeException(
					"parent must have been created by addContextMenuItem");
		}
		if (parent.getStyle() != TableContextMenuItemImpl.STYLE_MENU) {
			throw new UIRuntimeException(
					"parent menu item must have the menu style associated");
		}
		TableContextMenuItemImpl item = new TableContextMenuItemImpl(
				(TableContextMenuItemImpl) parent, resourceKey);
		return item;
	}

	@Override
	public TableContextMenuItem addContextMenuItem(String tableID,
	                                               String resourceKey) {
		TableContextMenuItemImpl item = new TableContextMenuItemImpl(ui_manager.getPluginInterface(), tableID, resourceKey);

		TableContextMenuManager.getInstance().addContextMenuItem(item);

		return item;
	}
}
