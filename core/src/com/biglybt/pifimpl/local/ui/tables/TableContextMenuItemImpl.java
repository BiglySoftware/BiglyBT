/*
 * Azureus - a Java Bittorrent client
 * 2004/May/16 TuxPaper
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

package com.biglybt.pifimpl.local.ui.tables;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.pif.ui.tables.TableContextMenuItem;
import com.biglybt.pifimpl.local.ui.menus.MenuItemImpl;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.common.table.impl.TableContextMenuManager;

public class TableContextMenuItemImpl extends MenuItemImpl implements TableContextMenuItem {

public static final String MENUKEY_TABLE_VIEW = "table-view";

  private String sTableID;
  private TableView<?>	table;
  
  public TableContextMenuItemImpl(PluginInterface pi, String tableID, String key) {
	  super(pi, MenuManager.MENU_TABLE, key);
	  sTableID = tableID;
  }

  public TableContextMenuItemImpl(TableContextMenuItemImpl ti, String key) {
	  super(ti, key);
	  this.sTableID = ti.getTableID();
  }

  @Override
  public String getTableID() {
    return sTableID;
  }

	@Override
	protected void removeSelf() {
		TableContextMenuManager.getInstance().removeContextMenuItem(this);
	}
	
	public void
	setTable(
		TableView<?>	_table )
	{
		table = _table;
	}
	@Override
	public TableView<?> 
	getTable()
	{
		return( table );
	}
}