/*
 * Created on 10-Dec-2004
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.views.tableitems.mytracker;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.category.Category;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.host.TRHostTorrent;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.ui.common.table.impl.CoreTableColumn;

/**
 * @author parg
 *
 */

public class
CategoryItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
		/** Default Constructor */

	protected static GlobalManager gm;

	public
	CategoryItem()
	{
		super("category", CoreTableColumn.POSITION_INVISIBLE, 400, TableManager.TABLE_MYTRACKER);

		setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void
	refresh(TableCell cell)
	{
	    TRHostTorrent tr_torrent = (TRHostTorrent)cell.getDataSource();

		if ( tr_torrent == null ){

			cell.setText("");

		}else{

			TOTorrent	torrent = tr_torrent.getTorrent();

			if (gm == null) {
				if (CoreFactory.isCoreRunning()) {
					return;
				}
				gm = CoreFactory.getSingleton().getGlobalManager();
			}

			DownloadManager dm = gm.getDownloadManager( torrent );

			String	cat_str = null;

			if ( dm != null ){

			    Category cat = dm.getDownloadState().getCategory();

				if (cat != null){

					cat_str = cat.getName();
				}
			}else{

					// pick up specific torrent category, bit 'o' a hack tis

				cat_str = TorrentUtils.getPluginStringProperty( torrent, "azcoreplugins.category" );
			}

			cell.setText( cat_str==null?"":cat_str);
		}
	}
}
