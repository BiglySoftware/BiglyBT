/*
 * File    : MaxUploadsItem.java
 * Created : 01 febv. 2004
 * By      : TuxPaper
 *
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

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.menus.*;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.pifimpl.local.PluginInitializer;

import com.biglybt.ui.common.table.TableRowCore;

public class MaxUploadsItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

  public static final String COLUMN_ID = "maxuploads";

	/** Default Constructor */
  public MaxUploadsItem(String sTableID) {
    super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 30, sTableID);
    setRefreshInterval(INTERVAL_LIVE);
    setMinWidthAuto(true);

    TableContextMenuItem menuItem = addContextMenuItem("TableColumn.menu.maxuploads");
    menuItem.setStyle(TableContextMenuItem.STYLE_MENU);
    menuItem.addFillListener(new MenuItemFillListener() {

			@Override
			public void menuWillBeShown(MenuItem menu, Object data) {
				menu.removeAllChildItems();

				PluginInterface pi = PluginInitializer.getDefaultInterface();
				UIManager uim = pi.getUIManager();
				MenuManager menuManager = uim.getMenuManager();

	      int iStart = COConfigurationManager.getIntParameter("Max Uploads") - 2;
	      if (iStart < 2) iStart = 2;
	      for (int i = iStart; i < iStart + 6; i++) {
					MenuItem item = menuManager.addMenuItem(menu, "MaxUploads." + i);
					item.setText(String.valueOf(i));
					item.setData(new Long(i));
					item.addMultiListener(new MenuItemListener() {
						@Override
						public void selected(MenuItem item, Object target) {
							if (target instanceof Object[]) {
								Object[] targets = (Object[]) target;
								for (Object object : targets) {
									if (object instanceof TableRowCore) {
										TableRowCore rowCore = (TableRowCore) object;
										object = rowCore.getDataSource(true);
									}
									DownloadManager dm = (DownloadManager) object;
									int value = ((Long) item.getData()).intValue();
									dm.setMaxUploads(value);
								}
							} // run
						}
					}); // listener
				} // for
			}
		});
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_SETTINGS
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_ADVANCED);
	}

  @Override
  public void refresh(TableCell cell) {
    DownloadManager dm = (DownloadManager)cell.getDataSource();
    long value = (dm == null) ? 0 : dm.getEffectiveMaxUploads();

    if (!cell.setSortValue(value) && cell.isValid())
      return;
    cell.setText(String.valueOf(value));
  }
}
