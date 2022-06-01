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

package com.biglybt.ui.swt.views.tableitems.pieces;

import java.util.Arrays;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTGraphic;
import com.biglybt.ui.swt.pifimpl.UISWTGraphicImpl;
import com.biglybt.ui.swt.views.PiecesView;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableRowSWT;
import com.biglybt.core.diskmanager.cache.CacheFileManagerFactory;
import com.biglybt.core.diskmanager.cache.CacheFileManagerStats;

import com.biglybt.pif.ui.Graphic;
import com.biglybt.pif.ui.tables.*;

/**
 *
 * @author TuxPaper
 * @since 2.0.8.5
 */
public class BlocksItem
	extends CoreTableColumnSWT
	implements TableCellAddedListener, TableCellRefreshListener,
	TableCellDisposeListener
{
	private static final int COLOR_REQUESTED = 0;

	private static final int COLOR_WRITTEN = 1;

	private static final int COLOR_DOWNLOADED = 2;

	private static final int COLOR_INCACHE = 3;
	
	private static final int COLOR_EGM = 4;
	
	private static final int COLOR_UPLOADING = 5;

	public static final Color[] colors = new Color[] {
		Colors.bluesFixed[Colors.BLUES_MIDLIGHT],
		Colors.bluesFixed[Colors.BLUES_DARKEST],
		Colors.red,
		Colors.grey,
		Colors.fadedGreen,
		Colors.yellow,
	};

	private static CacheFileManagerStats cacheStats = null;

	/** Default Constructor */
	public BlocksItem(String table_id) {
		super("blocks", table_id);
		initializeAsGraphic(POSITION_LAST, 200);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_PROGRESS,
		});
	}

	@Override
	public void cellAdded(TableCell cell) {
		if (cacheStats == null) {
			try {
				cacheStats = CacheFileManagerFactory.getSingleton().getStats();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		cell.setFillCell(true);
	}

	@Override
	public void dispose(final TableCell cell) {
		Utils.execSWTThread(new AERunnable() {

			@Override
			public void runSupport() {
				Image img = ((TableCellSWT) cell).getGraphicSWT();
				if (img != null && !img.isDisposed()) {
					img.dispose();
				}
			}
		});
	}

	@Override
	public void refresh(final TableCell cell) {
		final PEPiece pePiece = (PEPiece) cell.getDataSource();
		if (pePiece == null) {
			cell.setSortValue(0);
			dispose(cell);
			cell.setGraphic(null);
			return;
		}

		boolean is_uploading = pePiece instanceof PiecesView.PEPieceUploading;
		
		cell.setSortValue(pePiece.getNbWritten());

		Utils.execSWTThread(new AERunnable() {

			@Override
			public void runSupport() {

				PiecePicker picker = pePiece.getPiecePicker();
				
				long lNumBlocks = pePiece.getNbBlocks();

				int newWidth = cell.getWidth();
				if (newWidth <= 0) {
					dispose(cell);
					cell.setGraphic(null);
					return;
				}
				int newHeight = cell.getHeight();

				int x1 = newWidth - 2;
				int y1 = newHeight - 3;
				if (x1 < 10 || y1 < 3) {
					dispose(cell);
					cell.setGraphic(null);
					return;
				}
				Image image = new Image(Utils.getDisplay(), newWidth, newHeight);
				Color color;
				GC gcImage = new GC(image);
								
				Color bgColor = Colors.white;

				if ( Utils.isDarkAppearanceNative() && cell instanceof TableCellSWT ){
					
					TableRowSWT row = ((TableCellSWT)cell).getTableRowSWT();
					
					if ( row != null ){
					
						Color bg = row.getView().getComposite().getBackground();
						
						if ( bg != null ){
							
							bgColor = bg;
							
							gcImage.setBackground( bgColor );
							gcImage.fillRectangle(0,0,newWidth,newHeight);
						}
					}
				};
				
				gcImage.setForeground(Utils.isDarkAppearanceNative()?Colors.dark_grey:Colors.grey);
				gcImage.drawRectangle(0, 0, x1 + 1, y1 + 1);
				int blocksPerPixel = 0;
				int iPixelsPerBlock = 0;
				int pxRes = 0;
				long pxBlockStep = 0;
				int factor = 4;

				while (iPixelsPerBlock <= 0) {
					blocksPerPixel++;
					iPixelsPerBlock = (int) ((x1 + 1) / (lNumBlocks / blocksPerPixel));
				}

				pxRes = (int) (x1 - ((lNumBlocks / blocksPerPixel) * iPixelsPerBlock)); 
				if (pxRes <= 0)
					pxRes = 1;
				pxBlockStep = (lNumBlocks * factor) / pxRes;
				long addBlocks = (lNumBlocks * factor) / pxBlockStep;
				if ((addBlocks * iPixelsPerBlock) > pxRes)
					pxBlockStep += 1;

				/*      String msg = "iPixelsPerBlock = "+iPixelsPerBlock + ", blocksPerPixel = " + blocksPerPixel;
				      msg += ", pxRes = " + pxRes + ", pxBlockStep = " + pxBlockStep + ", addBlocks = " + addBlocks + ", x1 = " + x1;
				      Debug.out(msg);*/

				TOTorrent torrent = pePiece.getManager().getDiskManager().getTorrent();

				boolean[] written = pePiece.getDMPiece().getWritten();
				boolean piece_written = pePiece.isWritten();
				int drawnWidth = 0;
				int blockStep = 0;

				int pieceNumber = pePiece.getPieceNumber();
				long[] offsets = new long[(int) lNumBlocks];
				long[] lengths = (long[]) offsets.clone();
				Arrays.fill(offsets,
						(long) pePiece.getManager().getDiskManager().getPieceLength()
								* (long) pieceNumber);
				for (int i = 0; i < lNumBlocks; lengths[i] = pePiece.getBlockSize(i), offsets[i] += DiskManager.BLOCK_SIZE
						* i, i++)
					;

				boolean	egm = picker.isInEndGameMode();
				
				boolean[] isCached = cacheStats == null ? new boolean[(int)lNumBlocks]
						: cacheStats.getBytesInCache(torrent, offsets, lengths);

				for (int i = 0; i < lNumBlocks; i += blocksPerPixel) {
					int nextWidth = iPixelsPerBlock;

					blockStep += blocksPerPixel * factor;
					if (blockStep >= pxBlockStep) { 
						nextWidth += (int) (blockStep / pxBlockStep);
						blockStep -= pxBlockStep;
					}

					if (i >= lNumBlocks - blocksPerPixel) { 
						nextWidth = x1 - drawnWidth;
					}
					color = bgColor;
					
					int num = -1;
					
					if ( (written == null && piece_written)	|| (written != null && written[i])) {

						color = colors[COLOR_WRITTEN];

					} else if (pePiece.isDownloaded(i)) {

						color = colors[is_uploading?COLOR_UPLOADING:COLOR_DOWNLOADED];

					} else if (pePiece.isRequested(i)) {

						if ( egm ){
							
							int req_count = picker.getEGMRequestCount( pieceNumber, i );
							
							if ( req_count < 2 ){
								
								color = colors[COLOR_REQUESTED];
								
							}else{
								
								color = colors[COLOR_EGM ];
								
								num = req_count;
							}
						}else{
							
							color = colors[COLOR_REQUESTED];
						}
					}

					gcImage.setBackground(color);
					gcImage.fillRectangle(drawnWidth + 1, 1, nextWidth, y1);

					if (isCached[i]) {
						gcImage.setBackground(colors[COLOR_INCACHE]);
						gcImage.fillRectangle(drawnWidth + 1, 1, nextWidth, 3);
					}

					if ( num >= 0 ){
						gcImage.setForeground( Colors.black );
						gcImage.drawString( String.valueOf( num ),drawnWidth + 1, 0, true ); 
					}
					
					drawnWidth += nextWidth;

				}
				gcImage.dispose();

				Image oldImage = null;
				Graphic graphic = cell.getGraphic();
				if (graphic instanceof UISWTGraphic) {
					oldImage = ((UISWTGraphic) graphic).getImage();
				}

				if (cell instanceof TableCellSWT) {
					((TableCellSWT) cell).setGraphic(image);
				} else {
					cell.setGraphic(new UISWTGraphicImpl(image));
				}
				if (oldImage != null && !oldImage.isDisposed())
					oldImage.dispose();

				gcImage.dispose();
			}
		});
	}
}
