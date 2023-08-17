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
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.ui.Graphic;
import com.biglybt.pif.ui.tables.*;

import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTGraphic;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pifimpl.UISWTGraphicImpl;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
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
		new PiecesItem(),
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
				TABLEID_PEER_FILES, getTextPrefixID(), basicItems,
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

 			if ( temp.length == 1 && temp[0] instanceof PEPeer ){

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

			if ( peer == null || peer.getManager().isDestroyed()){

				current_peer = null;
				
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
		public void fillTableColumnInfo(TableColumnInfo info) {
			info.addCategories(new String[] {
				CAT_CONTENT,
				CAT_ESSENTIAL
			});
			info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
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
		public void fillTableColumnInfo(TableColumnInfo info) {
			info.addCategories(new String[] {
				CAT_PROGRESS
			});
			info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
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

				cell.setSortValue( -1 );
				
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
	
	private static class
	PiecesItem
		extends CoreTableColumnSWT
		implements TableCellAddedListener, TableCellDisposeListener, TableCellVisibilityListener
	{
		private static final int	borderWidth	= 1;
		
		private
		PiecesItem()
		{
			super( "pieces", TABLEID_PEER_FILES );
			initializeAsGraphic(200);
			setMinWidth(100);
		}

		@Override
		public void fillTableColumnInfo(TableColumnInfo info) {
			info.addCategories(new String[] {
				CAT_CONTENT,
				CAT_PROGRESS
			});
			info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
		}
		
		@Override
		public void cellAdded(TableCell cell) {
			new Cell(cell);
		}

		@Override
		public void cellVisibilityChanged(TableCell cell, int visibility) {
			if(visibility == VISIBILITY_HIDDEN)
				dispose(cell);
		}

		@Override
		public void dispose(TableCell cell) {
			// only dispose of image here, this method is reused in other methods
			Graphic graphic = cell.getGraphic();
			if (graphic instanceof UISWTGraphic)
			{
				final Image img = ((UISWTGraphic) graphic).getImage();
				if (img != null && !img.isDisposed()){
					Utils.execSWTThread(() -> Utils.disposeSWTObjects(img));

						// could it be that it isn't being marked as disposed after disposal and
						// being double-disposed?
					((UISWTGraphic) graphic).setImage( null );
				}
			}
		}


		private class Cell implements TableCellLightRefreshListener {
			private int		lastPercentDone	= 0;
			private boolean	bNoRed			= false;
			private long	last_draw_time	= SystemTime.getMonotonousTime();


			public Cell(TableCell cell) {
				cell.setFillCell(false);
				cell.addListeners(this);
			}

			@Override
			public void refresh(TableCell cell) {
				refresh(cell, false);
			}

			@Override
			public void refresh(TableCell cell, boolean sortOnly) {
				
				PeersFilesViewRow row = (PeersFilesViewRow) cell.getDataSource();

				if ( row == null ){

					return;
				}
				
				final DiskManagerFileInfo file = row.getFile();
				
				PEPeer peer = row.getPeer();
				
				BitFlags pieces = peer.getAvailable();

				boolean[] flags = pieces==null?null:pieces.flags;
				
				int	firstPiece = file.getFirstPieceNumber();

				int	lastPiece	= file.getLastPieceNumber();

				int	done = 0;

				if ( flags != null ){
					
					for ( int i=firstPiece;i<=lastPiece;i++){
	
						if ( flags[i] ){
	
							done++;
						}
					}
				}
				
				int percentDone = ( done * 1000 ) / (lastPiece - firstPiece + 1 );
				
				cell.setSortValue( percentDone );
				
				if (sortOnly){
				
					dispose(cell);
					return;
				}

				//Compute bounds ...
				int newWidth = cell.getWidth();
				if (newWidth <= 0)
					return;
				final int newHeight = cell.getHeight() - 2;
				final int x1 = newWidth - borderWidth - 1;
				final int y1 = newHeight - borderWidth - 1;

				if (x1 < 10 || y1 < 3)
					return;


				boolean hasGraphic = false;
				Graphic graphic = cell.getGraphic();
				if (graphic instanceof UISWTGraphic) {
					Image img = ((UISWTGraphic) graphic).getImage();
					hasGraphic = img != null && !img.isDisposed();
				}
				final boolean bImageBufferValid = (lastPercentDone == percentDone)
						&& cell.isValid() && bNoRed && hasGraphic;

				if (bImageBufferValid)
					return;

				lastPercentDone = percentDone;
				Image piecesImage = null;

				if (graphic instanceof UISWTGraphic)
					piecesImage = ((UISWTGraphic) graphic).getImage();
				if (piecesImage != null && !piecesImage.isDisposed())
					piecesImage.dispose();

				piecesImage = new Image(Utils.getDisplay(), newWidth, newHeight);
				final GC gcImage = new GC(piecesImage);

				final long now = SystemTime.getMonotonousTime();

				bNoRed = true;
				
				if (percentDone == 1000) {
					gcImage.setForeground(Colors.blues[Colors.BLUES_DARKEST]);
					gcImage.setBackground(Colors.blues[Colors.BLUES_DARKEST]);
					gcImage.fillRectangle(1, 1, newWidth - 2, newHeight - 2);
				}else{

					
					int nbPieces = file.getNbPieces();
					if ( nbPieces < 0 ){
						nbPieces = 0;	// tree view root
					}
					
						
					if ( flags != null ){
						
						DiskManagerPiece[] 	dm_pieces 	= null;
						PEPiece[] 			pe_pieces	= null;
						
						if ( peer.isMyPeer()){
							
							PEPeerManager pm = peer.getManager();
							
							if ( pm != null ){
								
								pe_pieces = pm.getPieces();
							
								DiskManager dm = pm.getDiskManager();
								
								if ( dm != null ){
									
									dm_pieces = dm.getPieces();
								}
							}
						}
						
						bNoRed = true;
						for (int i = 0; i < newWidth; i++)
						{
							final int a0 = (i * nbPieces) / newWidth;
							int a1 = ((i + 1) * nbPieces) / newWidth;
							if (a1 == a0)
								a1++;
							if (a1 > nbPieces && nbPieces != 0)
								a1 = nbPieces;
							int nbAvailable = 0;
							boolean written = false;
							boolean partially_written = false;
							if (firstPiece >= 0) {
								for (int j = a0; j < a1; j++) {
									final int this_index = j + firstPiece;
									if (dm_pieces != null) {
										DiskManagerPiece dm_piece = dm_pieces[this_index];
										if (dm_piece.isDone()) {
											nbAvailable++;
										}
									}
									if (written) {
										continue;
									}

									if (pe_pieces != null) {
										PEPiece pe_piece = pe_pieces[this_index];
										if (pe_piece != null) {
											written = (pe_piece.getLastDownloadTime(now) + 500) > last_draw_time;
										}
									}

									if (!written && !partially_written && dm_pieces != null) {
										boolean[] blocks = dm_pieces[this_index].getWritten();
										if (blocks != null) {
											for (boolean block : blocks) {
												if (block) {
													partially_written = true;
													break;
												}
											}
										}
									}
								} // for j
							} else {
								nbAvailable = 1;
							}
							gcImage.setBackground(written ? Colors.red : partially_written ? Colors.grey : Colors.blues[(nbAvailable * Colors.BLUES_DARKEST) / (a1 - a0)]);
							gcImage.fillRectangle(i, 1, 1, newHeight - 2);
							if (written)
								bNoRed = false;
						}
					}
				}

				gcImage.setForeground(Colors.grey);
				gcImage.drawRectangle(0, 0, newWidth - 1, newHeight - 1);
				gcImage.drawRectangle(0, 0, newWidth - 1, newHeight - 1);
				
				gcImage.dispose();

				last_draw_time = now;
				
				if (cell instanceof TableCellSWT)
					((TableCellSWT) cell).setGraphic(piecesImage);
				else
					cell.setGraphic(new UISWTGraphicImpl(piecesImage));
			}
		}
	}
}
