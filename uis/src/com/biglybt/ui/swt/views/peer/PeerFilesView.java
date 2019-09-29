/*
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

package com.biglybt.ui.swt.views.peer;

import com.biglybt.ui.common.table.*;
import org.eclipse.swt.SWT;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.util.DisplayFormatters;

import com.biglybt.pif.ui.tables.*;

import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.swt.mdi.BaseMDI;
import com.biglybt.ui.swt.mdi.BaseMdiEntry;
import com.biglybt.ui.swt.mdi.TabbedEntry;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.impl.TableViewTab;

import com.biglybt.core.peermanager.piecepicker.util.BitFlags;
import com.biglybt.ui.common.table.impl.TableColumnManager;




public class PeerFilesView
	extends TableViewTab<PeerFilesView.PeersFilesViewRow>
	implements TableDataSourceChangedListener, TableRefreshListener
{
	public static final String TABLEID_PEER_FILES	= "PeerFiles";
	public static final String MSGID_PREFIX = "PeerFilesView";

	boolean refreshing = false;

	private static final TableColumnCore[] basicItems = {

		new NameItem(),
		new PercentItem(),
	};

	static{
		TableColumnManager tcManager = TableColumnManager.getInstance();

		tcManager.setDefaultColumnNames( TABLEID_PEER_FILES, basicItems );
	}



	private TableViewSWT<PeersFilesViewRow> tv;

	private PEPeer	current_peer;

	public PeerFilesView() {
		super(MSGID_PREFIX);

	}

	@Override
	public boolean allowCreate(UISWTView swtView) {
		if (swtView instanceof MdiEntry) {
			String viewID = ((MdiEntry) swtView).getMDI().getViewID();
			if (!TableManager.TABLE_TORRENT_PEERS.equals(viewID)) {
				return false;
			}
		}
		return super.allowCreate(swtView);
	}

	@Override
	public TableViewSWT<PeersFilesViewRow>
	initYourTableView()
	{
		tv = TableViewFactory.createTableViewSWT(
				PeersFilesViewRow.class,
				TABLEID_PEER_FILES, getPropertiesPrefix(), basicItems,
				"firstpiece", SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);

		tv.addTableDataSourceChangedListener(this, true);
		tv.addRefreshListener(this, true);

		return tv;
	}


 	@Override
  public void
 	tableDataSourceChanged(
 		Object newDataSource)
 	{
 		if ( newDataSource instanceof PEPeer ){

 			current_peer = (PEPeer)newDataSource;

 		}if ( newDataSource instanceof Object[] ){

 			Object[] temp = (Object[])newDataSource;

 			if ( temp.length > 0 && temp[0] instanceof PEPeer ){

 				current_peer = (PEPeer)temp[0];

 			}else{

 				current_peer = null;
 			}
 		}else{

 			current_peer = null;
 		}
	}

	@Override
	public void
	tableRefresh()
	{
		synchronized( this ){

			if ( refreshing ){

				return;
			}

			refreshing = true;
		}

		try{
			PEPeer	peer = current_peer;

			if ( peer == null ){

				tv.removeAllTableRows();

			}else{

				if ( tv.getRowCount() == 0 ){

					DiskManagerFileInfo[] files = peer.getManager().getDiskManager().getFiles();

					PeersFilesViewRow[] rows = new PeersFilesViewRow[ files.length ];

					for ( int i=0;i<files.length;i++ ){

						rows[i] = new PeersFilesViewRow( files[i], peer );
					}

					tv.addDataSources( rows );

					tv.processDataSourceQueueSync();

				}else{

					TableRowCore[] rows = tv.getRows();

					for ( TableRowCore row: rows ){

						((PeersFilesViewRow)row.getDataSource()).setPeer( peer );
					}
				}
			}
		}finally{

			synchronized( this ){

				refreshing = false;
			}
		}
	}


	@Override
	public void tableViewTabInitComplete() {

		super.tableViewTabInitComplete();
	}

	protected static class
	PeersFilesViewRow
	{
		private DiskManagerFileInfo		file;
		private PEPeer					peer;

		private
		PeersFilesViewRow(
			DiskManagerFileInfo		_file,
			PEPeer					_peer )
		{
			file	= _file;
			peer	= _peer;
		}

		private DiskManagerFileInfo
		getFile()
		{
			return( file );
		}

		private void
		setPeer(
			PEPeer	_peer )
		{
			peer	= _peer;
		}

		private PEPeer
		getPeer()
		{
			return( peer );
		}
	}

	private static class
	NameItem
		extends CoreTableColumnSWT
		implements TableCellRefreshListener
	{
		private
		NameItem()
		{
			super( "name", ALIGN_LEAD, POSITION_LAST, 300, TABLEID_PEER_FILES );

			setType(TableColumn.TYPE_TEXT);

		}

		@Override
		public void
		refresh(
			TableCell cell )
		{
			PeersFilesViewRow row = (PeersFilesViewRow) cell.getDataSource();
			String name = (row == null) ? "" : row.getFile().getFile(true).getName();
			if (name == null)
				name = "";

			cell.setText( name );
		}
	}

	private static class
	PercentItem
		extends CoreTableColumnSWT
		implements TableCellRefreshListener
	{
		private
		PercentItem()
		{
			super( "%", ALIGN_TRAIL, POSITION_LAST, 60, TABLEID_PEER_FILES );
			setRefreshInterval(INTERVAL_LIVE);
		}

		@Override
		public void
		refresh(
			TableCell cell )
		{
			PeersFilesViewRow row = (PeersFilesViewRow) cell.getDataSource();

			if ( row == null ){

				return;
			}

			DiskManagerFileInfo	file = row.getFile();

			PEPeer peer = row.getPeer();

			BitFlags pieces = peer.getAvailable();

			if( pieces == null ){

				cell.setText( "" );

				return;
			}

			boolean[] flags = pieces.flags;

			int	first_piece = file.getFirstPieceNumber();

			int	last_piece	= file.getLastPieceNumber();

			int	done = 0;

			for ( int i=first_piece;i<=last_piece;i++){

				if ( flags[i] ){

					done++;
				}
			}

			int percent = ( done * 1000 ) / (last_piece - first_piece + 1 );

			if ( !cell.setSortValue(percent) && cell.isValid()){

				return;
			}

			cell.setText(percent < 0 ? "" : DisplayFormatters.formatPercentFromThousands((int) percent));

		}
	}
}
