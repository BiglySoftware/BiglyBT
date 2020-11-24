/*
 * File : CompletionItem.java Created : 24 nov. 2003 By : Olivier
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 2 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 */
package com.biglybt.ui.swt.views.tableitems.files;

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.util.SystemTime;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTGraphic;
import com.biglybt.ui.swt.pifimpl.UISWTGraphicImpl;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;

import com.biglybt.pif.ui.Graphic;
import com.biglybt.pif.ui.tables.*;


/**
 * Torrent Completion Level Graphic Cell for My Torrents.
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class ProgressGraphItem extends CoreTableColumnSWT implements TableCellAddedListener, TableCellDisposeListener, TableCellVisibilityListener {
	private static final int	borderWidth	= 1;

	/** Default Constructor */
	public ProgressGraphItem() {
		super("pieces", TableManager.TABLE_TORRENT_FILES);
		initializeAsGraphic(POSITION_LAST, 200);
		setMinWidth(100);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_PROGRESS,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
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

					// see http://forum.vuze.com/thread.jspa?threadID=117243
					// could it be that it isn't being marked as disposed after disposal and
					// being double-disposed?
				((UISWTGraphic) graphic).setImage( null );
			}
		}
	}


	private class Cell implements TableCellLightRefreshListener {
		int				lastPercentDone	= 0;
		private long	last_draw_time	= SystemTime.getCurrentTime();
		private boolean	bNoRed			= false;
		private boolean	was_running		= false;

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
			final DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) cell.getDataSource();
			
			DownloadManager dm = fileInfo==null?null:fileInfo.getDownloadManager();
			
			final DiskManager diskManager = dm == null ? null : dm.getDiskManager();

			int percentDone = 0;
			int sortOrder;
			if (fileInfo == null){
				sortOrder = -1;
			}else{
				long length = fileInfo.getLength();
				if (length != 0) {
					percentDone = (int) ((1000 * fileInfo.getDownloaded()) / length);
				}

				sortOrder = percentDone;
			}
			cell.setSortValue(sortOrder);
			if (sortOnly)
			{
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

			// we want to run through the image part once one the transition from with a disk diskManager (running)
			// to without a disk diskManager (stopped) in order to clear the pieces view
			boolean running = diskManager != null;
			boolean hasGraphic = false;
			Graphic graphic = cell.getGraphic();
			if (graphic instanceof UISWTGraphic) {
				Image img = ((UISWTGraphic) graphic).getImage();
				hasGraphic = img != null && !img.isDisposed();
			}
			final boolean bImageBufferValid = (lastPercentDone == percentDone)
					&& cell.isValid() && bNoRed && running == was_running && hasGraphic;

			if (bImageBufferValid)
				return;

			was_running = running;
			lastPercentDone = percentDone;
			Image piecesImage = null;

			if (graphic instanceof UISWTGraphic)
				piecesImage = ((UISWTGraphic) graphic).getImage();
			if (piecesImage != null && !piecesImage.isDisposed())
				piecesImage.dispose();

			piecesImage = new Image(Utils.getDisplay(), newWidth, newHeight);
			final GC gcImage = new GC(piecesImage);
			final long now = SystemTime.getCurrentTime();

			if (percentDone == 1000) {
				gcImage.setForeground(Colors.blues[Colors.BLUES_DARKEST]);
				gcImage.setBackground(Colors.blues[Colors.BLUES_DARKEST]);
				gcImage.fillRectangle(1, 1, newWidth - 2, newHeight - 2);
			} else if (fileInfo != null) {
				// dm may be null if this is a skeleton file view
				PEPeerManager peer_manager = dm == null ? null : dm.getPeerManager();
				PEPiece[] pe_pieces = peer_manager == null ? null : peer_manager.getPieces();

				int firstPiece = fileInfo.getFirstPieceNumber();
				int nbPieces = fileInfo.getNbPieces();
				if ( nbPieces < 0 ){
					nbPieces = 0;	// tree view root
				}
				DiskManagerPiece[] dm_pieces = diskManager == null ? (dm==null?null:dm.getDiskManagerPiecesSnapshot()) : diskManager.getPieces();

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
					// nbPieces > 0 check case: last file in torrent is 0 byte and starts on a new piece
					if (firstPiece >= 0 && nbPieces > 0) {
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

			gcImage.setForeground(Colors.grey);
			gcImage.drawRectangle(0, 0, newWidth - 1, newHeight - 1);
			if (diskManager != null) {
				gcImage.drawRectangle(0, 0, newWidth - 1, newHeight - 1);
			}
			gcImage.dispose();

			last_draw_time = now;

			if (cell instanceof TableCellSWT)
				((TableCellSWT) cell).setGraphic(piecesImage);
			else
				cell.setGraphic(new UISWTGraphicImpl(piecesImage));
		}
	}
}
