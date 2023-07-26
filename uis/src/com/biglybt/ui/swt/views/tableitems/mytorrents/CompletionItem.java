/*
 * File    : CompletionItem.java
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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTGraphic;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;

import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.utils.ColorCache;

import com.biglybt.pif.download.DownloadTypeIncomplete;
import com.biglybt.pif.ui.Graphic;
import com.biglybt.pif.ui.tables.*;

/** Torrent Completion Level Graphic Cell for My Torrents.
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class CompletionItem
	extends CoreTableColumnSWT
	implements TableCellAddedListener, TableCellRefreshListener,
	TableCellDisposeListener, TableCellSWTPaintListener
{
	public static final Class DATASOURCE_TYPE = DownloadTypeIncomplete.class;

	private static final int borderWidth = 1;

	public static final String COLUMN_ID = "completion";

	private static Font fontText;

	private Map mapCellLastPercentDone = new HashMap();

	private int marginHeight = -1;

	Color textColor;


	/** Default Constructor */
	public CompletionItem(String sTableID) {
		this(sTableID, -1);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_PROGRESS,
		});
	}

	/**
	 *
	 * @param sTableID
	 * @param marginHeight -- Margin height above and below the progress bar; used in cases where the row is very tall
	 */
	public CompletionItem(String sTableID, int marginHeight) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_LEAD, 150, sTableID);
		this.marginHeight = marginHeight;
		initializeAsGraphic(POSITION_INVISIBLE, 150);
		setMinWidth(50);
	}

	// @see com.biglybt.pif.ui.tables.TableCellAddedListener#cellAdded(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void cellAdded(TableCell cell) {
		if (marginHeight != -1) {
			cell.setMarginHeight(marginHeight);
		} else {
			cell.setMarginHeight(2);
		}
	}

	// @see com.biglybt.pif.ui.tables.TableCellDisposeListener#dispose(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void dispose(TableCell cell) {
		mapCellLastPercentDone.remove(cell);
		Graphic graphic = cell.getGraphic();
		if (graphic instanceof UISWTGraphic) {
			Image img = ((UISWTGraphic) graphic).getImage();
			if (img != null && !img.isDisposed()) {
				Utils.execSWTThread(() -> Utils.disposeSWTObjects(img));
			}
		}
	}

	// @see com.biglybt.pif.ui.tables.TableCellRefreshListener#refresh(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void refresh(TableCell cell) {
		int percentDone = getPercentDone(cell);

		Integer intObj = (Integer) mapCellLastPercentDone.get(cell);
		int lastPercentDone = intObj == null ? 0 : intObj.intValue();

		if (!cell.setSortValue(percentDone) && cell.isValid()
				&& lastPercentDone == percentDone) {
			return;
		}
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWTPaintListener#cellPaint(org.eclipse.swt.graphics.GC, com.biglybt.ui.swt.views.table.TableCellSWT)
	@Override
	public void cellPaint(GC gcImage, TableCellSWT cell) {
		int percentDone = getPercentDone(cell);

		Rectangle bounds = cell.getBounds();

		int yOfs = (bounds.height - 13) / 2 ;
		int x1 = bounds.width - borderWidth - 2;
		int y1 = bounds.height - 3 - yOfs;

		if (x1 < 10 || y1 < 3) {
			return;
		}
		int textYofs = 0;

		if (y1 >= 28) {
			yOfs = 2;
			y1 = 16;
			//textYofs = yOfs;
		}



		mapCellLastPercentDone.put(cell, new Integer(percentDone));


    ImageLoader imageLoader = ImageLoader.getInstance();
		Image imgEnd = imageLoader.getImage("dl_bar_end");
		Image img0 = imageLoader.getImage("dl_bar_0");
		Image img1 = imageLoader.getImage(percentDone<1000?"dl_bar_1":"dl_bar_1g");

		//draw beginning and end
		if (!imgEnd.isDisposed()) {
			gcImage.drawImage(imgEnd, bounds.x , bounds.y + yOfs);
			gcImage.drawImage(imgEnd, bounds.x + x1 + 1, bounds.y + yOfs);
		}



		// draw border
//		Color fg = gcImage.getForeground();
//		gcImage.setForeground(Colors.grey);
//		gcImage.drawRectangle(bounds.x, bounds.y + yOfs, x1 + 1, y1 + 1);
//		gcImage.setForeground(fg);

		int limit = (x1 * percentDone) / 1000;

		if (!img1.isDisposed() && limit > 0) {
			Rectangle imgBounds = img1.getBounds();
			gcImage.drawImage(img1, 0, 0, imgBounds.width, imgBounds.height,
					bounds.x + 1, bounds.y + yOfs, limit, imgBounds.height);
		}
		if (percentDone < 1000 && !img0.isDisposed()) {
			Rectangle imgBounds = img0.getBounds();
			gcImage.drawImage(img0, 0, 0, imgBounds.width, imgBounds.height, bounds.x
					+ limit + 1, bounds.y + yOfs, x1 - limit, imgBounds.height);
		}

		imageLoader.releaseImage("dl_bar_end");
		imageLoader.releaseImage("dl_bar_0");
		imageLoader.releaseImage("dl_bar_1");

//		gcImage.setBackground(Colors.blues[Colors.BLUES_DARKEST]);
//		gcImage.fillRectangle(bounds.x + 1, bounds.y + 1 + yOfs, limit, y1);
//		if (limit < x1) {
//			gcImage.setBackground(Colors.blues[Colors.BLUES_LIGHTEST]);
//			gcImage.fillRectangle(bounds.x + limit + 1, bounds.y + 1 + yOfs, x1
//					- limit, y1);
//		}

		if(textColor == null) {
			textColor = ColorCache.getColor(gcImage.getDevice(), "#005ACF" );
		}

//		if (textYofs == 0) {
//			if (fontText == null) {
//				fontText = Utils.getFontWithHeight(gcImage.getFont(), gcImage, y1);
//			}
//			gcImage.setFont(fontText);
			gcImage.setForeground(textColor);
//		}

		String sPercent = DisplayFormatters.formatPercentFromThousands(percentDone);
		GCStringPrinter.printString(gcImage, sPercent, new Rectangle(bounds.x + 4,
				bounds.y + yOfs, bounds.width - 4,13), true,
				false, SWT.CENTER);
	}

	private int getPercentDone(TableCell cell) {
		DownloadManager dm = (DownloadManager) cell.getDataSource();
		if (dm == null) {
			return 0;
		}

		return dm.getStats().getPercentDoneExcludingDND();
	}
}
