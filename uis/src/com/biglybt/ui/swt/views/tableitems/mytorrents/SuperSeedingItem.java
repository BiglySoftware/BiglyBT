/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
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

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.ui.swt.pif.UISWTGraphic;
import com.biglybt.ui.swt.pifimpl.UISWTGraphicImpl;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.pif.download.DownloadTypeComplete;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;

import com.biglybt.ui.swt.imageloader.ImageLoader;


public class SuperSeedingItem
extends CoreTableColumnSWT
implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = DownloadTypeComplete.class;

	private static UISWTGraphic tick_icon;

	public static final String COLUMN_ID = "superseeding";

	/** Default Constructor */
	public SuperSeedingItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_CENTER, 100, sTableID);
		setRefreshInterval(INTERVAL_LIVE);
		initializeAsGraphic(POSITION_INVISIBLE, 100);
		setMinWidth(20);

		tick_icon = new UISWTGraphicImpl(ImageLoader.getInstance().getImage("tick_mark_s"));
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
				CAT_CONNECTION,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE );
	}

	@Override
	public void refresh(TableCell cell) {
		UISWTGraphic	icon 	= null;
		int				sort	= 0;

		DownloadManager dm = (DownloadManager)cell.getDataSource();
		if (dm != null) {
			PEPeerManager pm = dm.getPeerManager();

			if ( pm != null && pm.isSuperSeedMode()){
				icon 	= tick_icon;
				sort	= 2; 
			}
		}

		cell.setSortValue( sort );

		if ( cell.getGraphic() != icon ){

			cell.setGraphic( icon );
		}
	}
}
