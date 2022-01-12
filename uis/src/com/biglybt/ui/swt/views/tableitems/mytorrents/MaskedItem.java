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
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.PEPeerManager;

public class MaskedItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "masked";

	private static boolean globalMask;
	
	static{
		COConfigurationManager.addAndFireParameterListener(
			ConfigKeys.Transfer.BCFG_PEERCONTROL_HIDE_PIECE,
			(n)->{
				globalMask = COConfigurationManager.getBooleanParameter(n);
			});
	}
	
	public
	MaskedItem(String sTableID)
	{
		super( DATASOURCE_TYPE, COLUMN_ID, ALIGN_CENTER, 80, sTableID);
		setRefreshInterval(INTERVAL_LIVE);
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

		if ( ds instanceof DownloadManager ){

			DownloadManager dm =(DownloadManager)ds;

			boolean masked = globalMask || dm.getDownloadState().getBooleanAttribute( DownloadManagerState.AT_MASK_DL_COMP );
			
			if ( masked ){
				
				text = MessageText.getString( "GeneralView.yes" );
				
				PEPeerManager pm = dm.getPeerManager();
				
				if ( pm != null ){
					
					text += " (" + pm.getHiddenPiece() + ")";
				}
			}
		}
		
		cell.setText( text );
	}
}