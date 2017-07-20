/*
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

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;

public class FileExtensionItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "fileext";

	public
	FileExtensionItem(String sTableID)
	{
		super( DATASOURCE_TYPE, COLUMN_ID, ALIGN_CENTER, 50, sTableID);
		addDataSourceType(DiskManagerFileInfo.class);
		setMinWidthAuto(true);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	@Override
	public void
	refresh( TableCell cell )
	{
		Object ds = cell.getDataSource();

		String	text = "";

		DownloadManager dm;

		if ( ds instanceof DownloadManager ){

			dm = (DownloadManager) ds;

			DiskManagerFileInfo prim = dm.getDownloadState().getPrimaryFile();

			text = prim==null?"":prim.getFile( true ).getName();

		}else if ( ds instanceof DiskManagerFileInfo ){

			DiskManagerFileInfo fileInfo = (DiskManagerFileInfo)ds;

			dm = fileInfo.getDownloadManager();

			text = fileInfo.getFile( true ).getName();

		}else{

			return;
		}

		String incomp_suffix = dm==null?null:dm.getDownloadState().getAttribute( DownloadManagerState.AT_INCOMP_FILE_SUFFIX );

		if ( incomp_suffix != null && text.endsWith( incomp_suffix )){

			text = text.substring( 0, text.length() - incomp_suffix.length());
		}

		int	pos = text.lastIndexOf( "." );

		if ( pos >= 0 ){

			text = text.substring( pos+1 );

		}else{

			text = "";
		}

		cell.setText( text );
	}
}
