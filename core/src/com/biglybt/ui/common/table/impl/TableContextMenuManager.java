/*
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

package com.biglybt.ui.common.table.impl;

import java.util.*;

import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.tables.*;


/**
 *
 * @author TuxPaper
 *
 */
public class TableContextMenuManager {
  private static TableContextMenuManager 	instance;
  private static final AEMonitor 					class_mon 	= new AEMonitor( "TableContextMenuManager" );


  /* Holds all the TableContextMenu objects.
   * key   = TABLE_* type (see TableColumn)
   * value = Map:
   *           key = context menu key
   *           value = TableContextMenu object
   */
  private Map<String, Map<String, TableContextMenuItem>> 		items;
  private final AEMonitor items_mon 	= new AEMonitor( "TableContextMenuManager:items" );


  private TableContextMenuManager() {
   items = new HashMap();
  }

  /** Retrieve the static TableContextMenuManager instance
   * @return the static TableContextMenuManager instance
   */
  public static TableContextMenuManager getInstance() {
  	try{
  		class_mon.enter();

  		if (instance == null)
  			instance = new TableContextMenuManager();
  		return instance;
  	}finally{

  		class_mon.exit();
  	}
  }

  public void addContextMenuItem(TableContextMenuItem item) {
    try {
      String name = item.getResourceKey();
      String sTableID = item.getTableID();
      try{
      	items_mon.enter();

        Map<String, TableContextMenuItem> mTypes = items.get(sTableID);
        if (mTypes == null) {
          // LinkedHashMap to preserve order
          mTypes = new LinkedHashMap();
          items.put(sTableID, mTypes);
        }
        mTypes.put(name, item);

      }finally{

      	items_mon.exit();
      }
    } catch (Exception e) {
      System.out.println("Error while adding Context Table Menu Item");
      Debug.printStackTrace( e );
    }
  }


	public void removeMenuItemsForDetach(String uiType) {
		List<MenuItem> toRemove = new ArrayList<>();

		try {
			items_mon.enter();

			for (Iterator<Map<String, TableContextMenuItem>> iterItemsMap = items.values().iterator(); iterItemsMap.hasNext(); ) {

				Map<String, TableContextMenuItem> mTypes = iterItemsMap.next();

				if (mTypes == null) {
					continue;
				}

				for (Iterator<TableContextMenuItem> iterTypes = mTypes.values().iterator(); iterTypes.hasNext(); ) {

					TableContextMenuItem item = iterTypes.next();

					if (uiType.equals(item.getDisposeWithUIDetach())) {
						toRemove.add(item);
					}
				}
			}
		}finally{

			items_mon.exit();
		}

		for (MenuItem menuItem : toRemove) {
			menuItem.remove();
		}
	}

	public void removeContextMenuItem(TableContextMenuItem item) {
		Map menu_item_map = (Map)this.items.get(item.getTableID());
		if (menu_item_map != null) {menu_item_map.remove(item.getResourceKey());}
	}

	public TableContextMenuItem[] getAllAsArray(String sMenuID) {
		Map local_menu_item_map = (Map)this.items.get(sMenuID);
		Map global_menu_item_map = (Map)this.items.get(null);
		if (local_menu_item_map == null && global_menu_item_map == null) {
			return new TableContextMenuItem[0];
		}

		ArrayList l = new ArrayList();
		if (local_menu_item_map != null) {l.addAll(local_menu_item_map.values());}
		if (global_menu_item_map != null) {l.addAll(global_menu_item_map.values());}
		return (TableContextMenuItem[]) l.toArray(new TableContextMenuItem[l.size()]);
	}

}