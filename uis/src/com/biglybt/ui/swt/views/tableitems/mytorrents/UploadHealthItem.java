/*
 * File    : PeakItem.java
 * Created : 01/07/2013
 * By      : Parg
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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
 */

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.util.DisplayFormatters;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


public class
UploadHealthItem
	extends CoreTableColumnSWT
    implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "upload.health";

	public
	UploadHealthItem(
		String sTableID)
	{
		super( DATASOURCE_TYPE, COLUMN_ID, ALIGN_CENTER, 70, sTableID );
		setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONNECTION
		});
	}


	@Override
	public void
	refresh(
		TableCell cell)
	{
		DownloadManager dm = (DownloadManager)cell.getDataSource();

	  	PEPeerManager pm = (dm==null)?null:dm.getPeerManager();
	  	
	  	long	sort_val;
	  	String	str;
	  	
	  	if ( pm == null ){
	  	
	  		sort_val 	= -1;
	  		str			= "";
	  	}else{
			  int queued_bytes = pm.getBytesQueuedForUpload();
			  
			  int peers_with_upq 	= pm.getNbPeersWithUploadQueued();
			  int peers_with_block	= pm.getNbPeersWithUploadBlocked();

			  sort_val = ( queued_bytes << 32 ) | ( peers_with_upq << 16 ) | peers_with_block;
			  
			  str =  DisplayFormatters.formatByteCountToKiBEtc( queued_bytes ) + "/" + peers_with_upq + "/" + peers_with_block;
	  	}

	    if ( !cell.setSortValue(sort_val) && cell.isValid()){

	    	return;
	    }

	    cell.setText( str );
	}
}
