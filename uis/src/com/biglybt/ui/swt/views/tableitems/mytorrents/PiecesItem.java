/*
 * File    : PiecesItem.java
 * Created : 24 nov. 2003
 * By      : Olivier
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

import com.biglybt.core.disk.impl.resume.RDResumeHandler;
import com.biglybt.ui.swt.Utils;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.mainwindow.SWTThread;
import com.biglybt.ui.swt.pif.UISWTGraphic;
import com.biglybt.ui.swt.pifimpl.UISWTGraphicImpl;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.util.MapUtils;

import com.biglybt.pif.download.DownloadTypeIncomplete;
import com.biglybt.pif.ui.Graphic;
import com.biglybt.pif.ui.tables.*;

/**
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class PiecesItem
	extends CoreTableColumnSWT
	implements TableCellAddedListener, TableCellRefreshListener,
	TableCellDisposeListener
{
	public static final Class DATASOURCE_TYPE = DownloadTypeIncomplete.class;

	private final static int INDEX_COLOR_NONEAVAIL = Colors.BLUES_DARKEST + 1;

	// only supports 0 or 1 border size
	private final static int borderHorizontalSize = 1;

	private final static int borderVerticalSize = 1;

	private final static int borderSplit = 1;

	// height of little completion bar above piece bar.
	private final static int completionHeight = 2;

	public static final String COLUMN_ID = "pieces";

	private int marginHeight = -1;

	/** Default Constructor */
	public PiecesItem(String sTableID) {
		this(sTableID,-1);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
			CAT_PROGRESS
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	/**
	 *
	 * @param sTableID
	 * @param marginHeight -- Margin height above and below the pieces graphic; used in cases where the row is very tall
	 */
	public PiecesItem(String sTableID, int marginHeight) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_LEAD, 100, sTableID);
		this.marginHeight = marginHeight;
		initializeAsGraphic(POSITION_INVISIBLE, 100);
		setMinWidth(100);
	}

	@Override
	public void cellAdded(TableCell cell) {
		if (marginHeight != -1) {
			cell.setMarginHeight(marginHeight);
		}
		cell.setFillCell(true);
	}

	@Override
	public void dispose(TableCell cell) {
		// Named infoObj so code can be copied easily to the other PiecesItem
		DownloadManager infoObj = (DownloadManager) cell.getDataSource();
		if (infoObj == null)
			return;

		Image img = (Image) infoObj.getUserData("PiecesImage");
		if (img != null && !img.isDisposed()) {
			Utils.execSWTThread(() -> Utils.disposeSWTObjects(img));
		}

		infoObj.setUserData("PiecesImageBuffer", null);
		infoObj.setUserData("PiecesImage", null);
	}

	@Override
	public void refresh(TableCell cell) {
		/* Notes:
		 * We store our image and imageBufer in DownloadManager using
		 * setData & getData.
		 */

		// Named infoObj so code can be copied easily to the other PiecesItem
		DownloadManager infoObj = (DownloadManager) cell.getDataSource();
		long lCompleted = (infoObj == null) ? 0 : infoObj.getStats().getCompleted();

		boolean bForce = infoObj != null && infoObj.getUserData("PiecesImage") == null;

		if (!cell.setSortValue(lCompleted) && cell.isValid() && !bForce) {
			return;
		}

		if (infoObj == null)
			return;

		//Compute bounds ...
		int newWidth = cell.getWidth();
		if (newWidth <= 0)
			return;
		int newHeight = cell.getHeight();

		int x0 = borderVerticalSize;
		int x1 = newWidth - 1 - borderVerticalSize;
		int y0 = completionHeight + borderHorizontalSize + borderSplit;
		int y1 = newHeight - 1 - borderHorizontalSize;
		int drawWidth = x1 - x0 + 1;
		if (drawWidth < 10 || y1 < 3)
			return;
		boolean bImageBufferValid = true;
		int[] imageBuffer = (int[]) infoObj.getUserData("PiecesImageBuffer");
		if (imageBuffer == null || imageBuffer.length != drawWidth) {
			imageBuffer = new int[drawWidth];
			bImageBufferValid = false;
		}

		Image image = (Image) infoObj.getUserData("PiecesImage");
		GC gcImage;
		boolean bImageChanged;
		Rectangle imageBounds;
		if (image == null || image.isDisposed()) {
			bImageChanged = true;
		} else {
			imageBounds = image.getBounds();
			bImageChanged = imageBounds.width != newWidth
					|| imageBounds.height != newHeight;
		}
		if (bImageChanged) {
			if (image != null && !image.isDisposed()) {
				image.dispose();
			}
			image = new Image(Utils.getDisplay(), newWidth, newHeight);
			imageBounds = image.getBounds();
			bImageBufferValid = false;

			// draw border
			gcImage = new GC(image);
			gcImage.setForeground(Colors.grey);
			if (borderHorizontalSize > 0) {
				if (borderVerticalSize > 0) {
					gcImage.drawRectangle(0, 0, newWidth - 1, newHeight - 1);
				} else {
					gcImage.drawLine(0, 0, newWidth - 1, 0);
					gcImage.drawLine(0, newHeight - 1, newWidth - 1, newHeight - 1);
				}
			} else if (borderVerticalSize > 0) {
				gcImage.drawLine(0, 0, 0, newHeight - 1);
				gcImage.drawLine(newWidth - 1, 0, newWidth - 1, newHeight - 1);
			}

			if (borderSplit > 0) {
				gcImage.setForeground(Colors.white);
				gcImage.drawLine(x0, completionHeight + borderHorizontalSize, x1,
						completionHeight + borderHorizontalSize);
			}
		} else {
			gcImage = new GC(image);
		}

		DiskManager disk_manager = infoObj.getDiskManager();

		DiskManagerPiece[] pieces = disk_manager == null ? null
				: disk_manager.getPieces();
		byte[] resume_data = pieces == null ? MapUtils.getMapByteArray(
				MapUtils.getMapMap(infoObj.getDownloadState().getResumeData(), "data",
						null),
				"resume data", null) : null;

		int nbPieces = infoObj.getNbPieces();

		try {

			int nbComplete = 0;
			int a0;
			int a1 = 0;
			for (int i = 0; i < drawWidth; i++) {
				if (i == 0) {
					// always start out with one piece
					a0 = 0;
					a1 = nbPieces / drawWidth;
					if (a1 == 0)
						a1 = 1;
				} else {
					// the last iteration, a1 will be nbPieces
					a0 = a1;
					a1 = ((i + 1) * nbPieces) / (drawWidth);
				}

				int index;

				if (a1 <= a0) {
					index = imageBuffer[i - 1];
				} else {
					int nbAvailable = 0;
					if (pieces != null) {
						for (int j = a0; j < a1; j++) {
							if (pieces[j].isDone()) {
								nbAvailable++;
							}
						}
					} else if (resume_data != null) {
						for (int j = a0; j < a1; j++) {
							if (resume_data[j] == RDResumeHandler.PIECE_DONE) {
								nbAvailable++;
							}
						}
					}
					nbComplete += nbAvailable;
					index = (nbAvailable * Colors.BLUES_DARKEST) / (a1 - a0);
					//System.out.println("i="+i+";nbAvailable="+nbAvailable+";nbComplete="+nbComplete+";nbPieces="+nbPieces+";a0="+a0+";a1="+a1);
				}

				if (!bImageBufferValid || imageBuffer[i] != index) {
					imageBuffer[i] = index;
					bImageChanged = true;
					gcImage.setForeground(index == INDEX_COLOR_NONEAVAIL ? Colors.red
							: Colors.blues[index]);
					gcImage.drawLine(i + x0, y0, i + x0, y1);
				}
			}

			// pieces can sometimes be 0 due to timing or bad torrent (well, there's a bug with a /0 error
			// so it can happen somehow :)

			int limit = nbPieces == 0 ? 0 : ((drawWidth * nbComplete) / nbPieces);

			if (limit < drawWidth) {
				gcImage.setBackground(Colors.blues[Colors.BLUES_LIGHTEST]);
				gcImage.fillRectangle(limit + x0, borderHorizontalSize, x1 - limit,
						completionHeight);
			}

			gcImage.setBackground(Colors.colorProgressBar);
			gcImage.fillRectangle(x0, borderHorizontalSize, limit, completionHeight);
		} catch (Exception e) {
			System.out.println("Error Drawing PiecesItem");
			Debug.printStackTrace(e);
		}
		gcImage.dispose();

		Image oldImage = null;
		Graphic graphic = cell.getGraphic();
		if (graphic instanceof UISWTGraphic) {
			oldImage = ((UISWTGraphic) graphic).getImage();
		}
		if (bImageChanged || image != oldImage || !cell.isValid()) {
			if (cell instanceof TableCellSWT) {
				((TableCellSWT) cell).setGraphic(image);
			} else {
				cell.setGraphic(new UISWTGraphicImpl(image));
			}
			if (bImageChanged) {
				cell.invalidate();
			}
			infoObj.setUserData("PiecesImage", image);
			infoObj.setUserData("PiecesImageBuffer", imageBuffer);
		}
	}
}
