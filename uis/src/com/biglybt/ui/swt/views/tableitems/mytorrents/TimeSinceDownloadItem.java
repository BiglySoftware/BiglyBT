/*
 * File    : SecondsDownloadingItem.java
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
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.TimeFormatter;

import com.biglybt.pif.download.DownloadTypeIncomplete;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.pif.ui.tables.TableContextMenuItem;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


/**
 *
 * @author TuxPaper
 * @since 2.0.8.5
 */
public class TimeSinceDownloadItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener, ParameterListener
{
	public static final Class DATASOURCE_TYPE = DownloadTypeIncomplete.class;

	public static final String COLUMN_ID = "timesincedownload";

	private static final String CFG_SESSION_ONLY = "ui.timesincedownload.session.only";
	
	private boolean	session;

	/** Default Constructor */
	public TimeSinceDownloadItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 70, sTableID);
		setRefreshInterval(INTERVAL_LIVE);

		COConfigurationManager.addWeakParameterListener(this, true, CFG_SESSION_ONLY);

		TableContextMenuItem menuSessionOnly = addContextMenuItem(
				"menu.session.only", MENU_STYLE_HEADER);
		menuSessionOnly.setStyle(TableContextMenuItem.STYLE_CHECK);
		menuSessionOnly.addFillListener(new MenuItemFillListener() {
			@Override
			public void menuWillBeShown(MenuItem menu, Object data) {
				menu.setData(Boolean.valueOf(session));
			}
		});

		menuSessionOnly.addMultiListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				COConfigurationManager.setParameter(CFG_SESSION_ONLY,
						((Boolean) menu.getData()).booleanValue());
			}
		});
	}

	public void parameterChanged(String parameterName) {
		session = COConfigurationManager.getBooleanParameter(CFG_SESSION_ONLY);
	}

	@Override
	public void reset() {
		super.reset();

		COConfigurationManager.removeParameter( CFG_SESSION_ONLY );
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
				CAT_TIME
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	@Override
	public void refresh(TableCell cell) {
		DownloadManager dm = (DownloadManager)cell.getDataSource();

		int value = (dm == null) ? -2 : dm.getStats().getTimeSinceLastDataReceivedInSeconds(session);

		if (!cell.setSortValue(value==-1?Integer.MAX_VALUE:value) && cell.isValid())
			return;

		cell.setText(value==-2?"":(value==-1?Constants.INFINITY_STRING:TimeFormatter.format(value)));
	}
}
