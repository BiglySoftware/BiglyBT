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

package com.biglybt.ui.swt.shells.opentorrent;

import java.text.NumberFormat;

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.impl.TorrentOpenFileOptions;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;
import com.biglybt.ui.swt.views.table.utils.TableColumnSWTUtils;
import com.biglybt.pif.ui.tables.*;

public class TableColumnOTOF_Size
	implements TableCellRefreshListener, TableColumnExtraInfoListener,
	TableCellSWTPaintListener, TableCellToolTipListener
{
	public static final String COLUMN_ID = "size";

  /** Default Constructor */
  public TableColumnOTOF_Size(TableColumn column) {
  	column.initialize(TableColumn.ALIGN_TRAIL, TableColumn.POSITION_LAST, 80);
  	column.addListeners(this);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

  @Override
  public void refresh(TableCell cell) {
  	Object ds = cell.getDataSource();
  	if (!(ds instanceof TorrentOpenFileOptions)) {
  		return;
  	}
  	TorrentOpenFileOptions tfi = (TorrentOpenFileOptions) ds;
  	cell.setSortValue(tfi.lSize);
  	cell.setText(DisplayFormatters.formatByteCountToKiBEtc(tfi.lSize));
  	TableColumnSWTUtils.setSizeAlpha( cell, tfi.lSize );
  }

	@Override
	public void cellPaint(GC gc, TableCellSWT cell) {
  	Object ds = cell.getDataSource();
  	if (!(ds instanceof TorrentOpenFileOptions)) {
  		return;
  	}
  	TorrentOpenFileOptions tfi = (TorrentOpenFileOptions) ds;

  	float pct = tfi.lSize / (float) tfi.parent.getTorrent().getSize();

  	Rectangle bounds = cell.getBounds();

  	bounds.width = (int) (bounds.width * pct);
  	if (bounds.width > 2) {
  		bounds.x++;
  		bounds.y++;
  		bounds.height -= 2;
  		bounds.width -= 2;
  		gc.setBackground(gc.getForeground());
  		int alpha = gc.getAlpha();
  		gc.setAlpha(10);
  		gc.fillRectangle(bounds);
  		gc.setAlpha(alpha);
  	}
	}

	@Override
	public void cellHover(TableCell cell) {
		Object ds = cell.getDataSource();
		if (!(ds instanceof TorrentOpenFileOptions)) {
			return;
		}
		TorrentOpenFileOptions tfi = (TorrentOpenFileOptions) ds;

		String tooltip = NumberFormat.getInstance().format(tfi.lSize) + " "
			+ MessageText.getString("DHTView.transport.bytes");

		cell.setToolTip(tooltip);
	}

	@Override
	public void cellHoverComplete(TableCell cell) {
		cell.setToolTip(null);
	}
}
