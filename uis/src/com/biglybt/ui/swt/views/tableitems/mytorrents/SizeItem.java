/*
 * File    : SizeItem.java
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

import java.text.NumberFormat;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableCellToolTipListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.utils.TableColumnSWTUtils;

/** Size of Torrent cell
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class SizeItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener, TableCellToolTipListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "size";

	/** Default Constructor */
	public SizeItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 70, sTableID);
		addDataSourceType(DiskManagerFileInfo.class);
		setRefreshInterval(INTERVAL_GRAPHIC);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_ESSENTIAL,
			CAT_CONTENT,
			CAT_BYTES
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	@Override
	public void refresh(TableCell cell) {
		Object ds = cell.getDataSource();
		boolean simpleSort = cell.useSimpleSortValue();
		long remaining;
		long size;
		if (ds instanceof DownloadManager) {
			DownloadManager dm = (DownloadManager) ds;

			remaining = dm.getStats().getRemainingExcludingDND();
			size = dm.getStats().getSizeExcludingDND();
		} else if (ds instanceof DiskManagerFileInfo) {
			DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) ds;
			remaining = fileInfo.getLength() - fileInfo.getDownloaded();
			size = fileInfo.getLength();
		} else {
			return;
		}

		boolean showSecondLine = cell.getMaxLines() > 1 && remaining > 0
				&& cell.getWidth() > 150;
		sizeitemsort value = new sizeitemsort(size, simpleSort ? 0 : remaining,
				showSecondLine);

		// cell.setSortValue(value) always returns true and if I change it,
		// I'm afraid something will break.. so use compareTo
		if (value.compareTo(cell.getSortValue()) == 0 && cell.isValid())
			return;

		cell.setSortValue(value);

		String s = DisplayFormatters.formatCustomSize( "column.size", value.size );

		if ( s == null ){

			s = DisplayFormatters.formatByteCountToKiBEtc(value.size);
		}

		if (showSecondLine) {
			s += "\n"
					+ DisplayFormatters.formatByteCountToKiBEtc(remaining, false,
							false, 0) + " "
					+ MessageText.getString("TableColumn.header.remaining");
		}
		cell.setText(s);

		TableColumnSWTUtils.setSizeAlpha( cell, size );
	}

	private static class sizeitemsort
		implements Comparable
	{
		private final long size;

		private final long remaining;

		private final boolean showSecondLine;

		public sizeitemsort(long size, long remaining, boolean showSecondLine) {
			this.size = size;
			this.remaining = remaining;
			this.showSecondLine = showSecondLine;
		}

		@Override
		public int compareTo(Object arg0) {
			if (!(arg0 instanceof sizeitemsort)) {
				return 1;
			}

			sizeitemsort otherObj = (sizeitemsort) arg0;
			if (size == otherObj.size) {
				if (remaining == otherObj.remaining) {
					return showSecondLine == otherObj.showSecondLine ? 0
							: showSecondLine ? 1 : -1;
				}
				return remaining > otherObj.remaining ? 1 : -1;
			}
			return size > otherObj.size ? 1 : -1;
		}
	}

	@Override
	public void cellHover(TableCell cell) {
		Comparable sortValue = cell.getSortValue();
		if (!(sortValue instanceof sizeitemsort)) {
			return;
		}
		sizeitemsort value = (sizeitemsort) sortValue;
		String tooltip = NumberFormat.getInstance().format(value.size) + " "
				+ MessageText.getString("DHTView.transport.bytes");
		if (value.remaining > 0) {
			tooltip += "\n"
					+ DisplayFormatters.formatByteCountToKiBEtc(value.remaining, false,
							false) + " "
					+ MessageText.getString("TableColumn.header.remaining");
		}
		Object ds = cell.getDataSource();
		if (ds instanceof DownloadManager) {
			DownloadManager dm = (DownloadManager) ds;
			long fullSize = dm.getSize();
			if (fullSize > value.size) {
				tooltip += "\n"
						+ DisplayFormatters.formatByteCountToKiBEtc(fullSize - value.size,
								false, false) + " "
						+ MessageText.getString("FileView.BlockView.Skipped");
			}
		}

		cell.setToolTip(tooltip);
	}

	@Override
	public void cellHoverComplete(TableCell cell) {
		cell.setToolTip(null);
	}
}
