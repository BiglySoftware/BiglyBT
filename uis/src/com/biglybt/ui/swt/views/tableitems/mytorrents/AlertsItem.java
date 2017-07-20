/*
 * File    : CategoryItem.java
 * Created : 01 feb. 2004
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

import java.util.Map;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.ui.swt.pif.UISWTGraphic;
import com.biglybt.ui.swt.pifimpl.UISWTGraphicImpl;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;

import com.biglybt.ui.swt.imageloader.ImageLoader;

/** Display Category torrent belongs to.
 *
 * @author TuxPaper
 */
public class AlertsItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	private static UISWTGraphic black_tick_icon;
	private static UISWTGraphic gray_tick_icon;

	public static final String COLUMN_ID = "alerts";

	public AlertsItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_CENTER, 60, sTableID);
		addDataSourceType(DiskManagerFileInfo.class);
		setRefreshInterval(INTERVAL_LIVE);
		initializeAsGraphic(POSITION_INVISIBLE, 60);
		setMinWidth(20);
		if (black_tick_icon == null || black_tick_icon.getImage() == null) {
			black_tick_icon = new UISWTGraphicImpl(ImageLoader.getInstance().getImage("blacktick"));
		}
		if (gray_tick_icon == null || gray_tick_icon.getImage() == null) {
			gray_tick_icon 	= new UISWTGraphicImpl(ImageLoader.getInstance().getImage("graytick"));
		}
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
				CAT_CONNECTION,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE );
	}

	@Override
	public void
	refresh(
		TableCell cell)
	{
		UISWTGraphic	icon 	= null;
		int				sort	= 0;

		Object ds = cell.getDataSource();


		if ( ds instanceof DownloadManager ){

			DownloadManager dm =  (DownloadManager)ds;

			Map<String,String> map =  dm.getDownloadState().getMapAttribute( DownloadManagerState.AT_DL_FILE_ALERTS  );

			if ( map != null && map.size() > 0 ){

				for ( String k: map.keySet()){

					if ( k.length() > 0 ){

						if ( Character.isDigit( k.charAt(0))){

							icon 	= gray_tick_icon;
							sort	= 1;

						}else{

							icon 	= black_tick_icon;
							sort	= 2;

							break;
						}
					}
				}
			}
		}else if ( ds instanceof DiskManagerFileInfo ){

			DiskManagerFileInfo fi = (DiskManagerFileInfo)ds;

			DownloadManager dm = fi.getDownloadManager();

			Map<String,String> map =  dm.getDownloadState().getMapAttribute( DownloadManagerState.AT_DL_FILE_ALERTS  );

			if ( map != null && map.size() > 0 ){

				String prefix = fi.getIndex() + ".";

				for ( String k: map.keySet()){

					if ( k.startsWith( prefix )){

						icon 	= black_tick_icon;
						sort	= 2;

						break;
					}
				}
			}
		}

		cell.setSortValue( sort );

		if ( cell.getGraphic() != icon ){

			cell.setGraphic( icon );
		}
	}
}
