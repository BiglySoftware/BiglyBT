/*
 * File    : CommentItem.java
 * Created : 14 Nov 2006
 * By      : Allan Crooks
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

import org.eclipse.swt.SWT;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.ui.swt.TorrentUtil;
import com.biglybt.ui.swt.pif.UISWTGraphic;
import com.biglybt.ui.swt.pifimpl.UISWTGraphicImpl;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;

import com.biglybt.ui.swt.imageloader.ImageLoader;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.Graphic;
import com.biglybt.pif.ui.tables.*;

/**
 * @author amc1
 */
public class CommentIconItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener, TableCellMouseListener, TableCellAddedListener, TableCellToolTipListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	final UISWTGraphic graphicComment;

	final UISWTGraphic noGraphicComment;

	public static final String COLUMN_ID = "commenticon";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_CONTENT });
	}

  /** Default Constructor */
  public CommentIconItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, CommentIconItem.POSITION_LAST, 20, sTableID);
		setRefreshInterval(INTERVAL_LIVE);
		initializeAsGraphic(POSITION_LAST, 20);
		setMinWidth(20);
		setIconReference("comment", true);

	  graphicComment = new UISWTGraphicImpl(
			  ImageLoader.getInstance().getImage("comment"));
	  noGraphicComment = new UISWTGraphicImpl(
			  ImageLoader.getInstance().getImage("no_comment"));
	}

  @Override
  public void cellAdded(TableCell cell) {
	  if ( cell instanceof TableCellSWT ){
		  ((TableCellSWT)cell).setCursorID(SWT.CURSOR_HAND);
	  }
  }

  @Override
  public void cellMouseTrigger(TableCellMouseEvent event) {
		DownloadManager dm = (DownloadManager) event.cell.getDataSource();
		if (dm == null) {return;}

		if (event.eventType != TableCellMouseEvent.EVENT_MOUSEUP) {return;}

		// Only activate on LMB.
		if (event.button != 1) {return;}
		event.skipCoreFunctionality = true;

		TorrentUtil.promptUserForComment(new DownloadManager[]{dm});
		refresh(event.cell);
  }

  @Override
  public void refresh(TableCell cell) {
	  if (cell.isDisposed()) {return;}


	  DownloadManager dm = (DownloadManager)cell.getDataSource();
	  String comment = null;
	  if (dm != null) {
		  comment = dm.getDownloadState().getUserComment();
		  if (comment!=null && comment.length()==0) {comment = null;}
	  }

	  Graphic oldGraphic = cell.getGraphic();
	  if (comment == null && oldGraphic != noGraphicComment) {
	  	cell.setGraphic(noGraphicComment);
		  cell.setSortValue(null);
	  }
	  else if (comment != null && oldGraphic != graphicComment) {
	  	cell.setGraphic(graphicComment);
		  cell.setSortValue(comment);
	  }

  }

  @Override
  public void cellHover(TableCell cell) {
	  DownloadManager dm = (DownloadManager)cell.getDataSource();
	  String comment = null;
	  if (dm != null) {
		  comment = dm.getDownloadState().getUserComment();
		  if (comment!=null && comment.length()==0) {comment = null;}
	  }
	  cell.setToolTip(comment);
  }

  @Override
  public void cellHoverComplete(TableCell cell) {
	  cell.setToolTip(null);
  }
}
