/*
 * Created on Sep 25, 2008
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.columns.searchsubs;

import java.net.URL;

import com.biglybt.util.StringCompareUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Display;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.subs.util.SearchSubsResultBase;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.shells.GCStringPrinter.URLInfo;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;

import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.swt.search.SBC_SearchResultsView;
import com.biglybt.ui.swt.skin.SWTSkinFactory;
import com.biglybt.ui.swt.skin.SWTSkinProperties;

/**
 * @author TuxPaper
 * @created Sep 25, 2008
 *
 */
public class ColumnSearchSubResultActions
	implements TableCellSWTPaintListener, TableCellRefreshListener,
	TableCellMouseMoveListener, TableCellAddedListener
{
	public static final String COLUMN_ID = "actions";

	private Color colorLinkNormal;

	private Color colorLinkHover;

	private static Font font = null;

	public ColumnSearchSubResultActions(TableColumn column) {
		column.initialize(TableColumn.ALIGN_LEAD, TableColumn.POSITION_LAST, 180);
		column.addListeners(this);
		column.setRefreshInterval(TableColumn.INTERVAL_INVALID_ONLY);
		column.setType(TableColumn.TYPE_GRAPHIC);

		if (column instanceof TableColumnCore) {
			((TableColumnCore) column).setUseCoreDataSource(true);
		}

		SWTSkinProperties skinProperties = SWTSkinFactory.getInstance().getSkinProperties();
		colorLinkNormal = skinProperties.getColor("color.links.normal");
		colorLinkHover = skinProperties.getColor("color.links.hover");
	}

	@Override
	public void cellPaint(GC gc, TableCellSWT cell) {
		SearchSubsResultBase entry = (SearchSubsResultBase)cell.getDataSource();
		if (entry == null) {
			return;
		}

		TableRow row = cell.getTableRow();
		if (row == null) {
			return;
		}
		String text = (String) row.getData("text");

		if (text != null && text.length() > 0) {
			if (font == null) {
				FontData[] fontData = gc.getFont().getFontData();
				fontData[0].setStyle(SWT.BOLD);
				font = new Font(gc.getDevice(), fontData);
			}
			gc.setFont(font);

			Rectangle bounds = getDrawBounds(cell);

			GCStringPrinter sp = new GCStringPrinter(gc, text, bounds, true, true,
					SWT.WRAP | SWT.CENTER);

			sp.calculateMetrics();

			if (sp.hasHitUrl()) {
				URLInfo[] hitUrlInfo = sp.getHitUrlInfo();
				for (int i = 0; i < hitUrlInfo.length; i++) {
					URLInfo info = hitUrlInfo[i];
						// handle fake row when showing in column editor

					info.urlUnderline = cell.getTableRow() == null || cell.getTableRow().isSelected();
					if (info.urlUnderline) {
						info.urlColor = null;
					} else {
						info.urlColor = colorLinkNormal;
					}
				}
				int[] mouseOfs = cell.getMouseOffset();
				if (mouseOfs != null) {
					Rectangle realBounds = cell.getBounds();
					URLInfo hitUrl = sp.getHitUrl(mouseOfs[0] + realBounds.x, mouseOfs[1]
							+ realBounds.y);
					if (hitUrl != null) {
						hitUrl.urlColor = colorLinkHover;
					}
				}
			}

			sp.printString();
		}
	}

	@Override
	public void cellAdded(TableCell cell) {
		cell.setMarginHeight(0);
	}

	@Override
	public void refresh(TableCell cell) {
		SearchSubsResultBase entry = (SearchSubsResultBase)cell.getDataSource();

		if (entry == null) return;

		String link 	= entry.getTorrentLink();
		String details 	= entry.getDetailsLink();

		if (!cell.setSortValue(link) && cell.isValid()) {
			return;
		}

		boolean canDL 		= link != null && link.length() > 0;
		boolean canDetails 	= details != null && details.length() > 0;

		StringBuilder sb = new StringBuilder();
		if (canDL) {
			if (sb.length() > 0) {
				sb.append(" | ");
			}
			String action;

			if ( link.startsWith( "chat:" )){

				action = MessageText.getString( "label.view" );

			}else if ( link.startsWith( "azplug:?id=subscription" )){

				action = MessageText.getString( "subscriptions.listwindow.subscribe" );

			}else{

				action = MessageText.getString( "label.download" );
			}

			sb.append("<A HREF=\"download\">" + action + "</A>");
		}

		if (canDetails) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append("<A HREF=\"details\">" + MessageText.getString( "label.details") + "</A>");
		}

		cell.getTableRow().setData("text", sb.toString());
	}

	@Override
	public void cellMouseTrigger(TableCellMouseEvent event) {
		SearchSubsResultBase entry = (SearchSubsResultBase)event.cell.getDataSource();

		String tooltip = null;
		boolean invalidateAndRefresh = false;

		Rectangle bounds = ((TableCellSWT) event.cell).getBounds();
		String text = (String) event.cell.getTableRow().getData("text");
		if (text == null) {
			return;
		}

		GCStringPrinter sp = null;
		GC gc = new GC(Display.getDefault());
		try {
			if (font != null) {
				gc.setFont(font);
			}
			Rectangle drawBounds = getDrawBounds((TableCellSWT) event.cell);
			sp = new GCStringPrinter(gc, text, drawBounds, true, true, SWT.WRAP
					| SWT.CENTER);
			sp.calculateMetrics();
		} catch (Exception e) {
			Debug.out(e);
		} finally {
			gc.dispose();
		}

		if (sp != null) {
			URLInfo hitUrl = sp.getHitUrl(event.x + bounds.x, event.y + bounds.y);
			int newCursor;
			if (hitUrl != null) {
				if (event.eventType == TableCellMouseEvent.EVENT_MOUSEUP && event.button == 1 ){
					if (hitUrl.url.equals("download")){

						SBC_SearchResultsView.downloadAction( entry );

					}else if ( hitUrl.url.equals("details")){

						String details_url = entry.getDetailsLink();

						try{
							Utils.launch( new URL( details_url ));

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}else{
					if (hitUrl.url.equals("download")){
						tooltip = entry.getTorrentLink();
					}else if ( hitUrl.url.equals("details")){
						tooltip = entry.getDetailsLink();
					}
				}

				newCursor = SWT.CURSOR_HAND;

			} else {
				newCursor = SWT.CURSOR_ARROW;
			}

			int oldCursor = ((TableCellSWT) event.cell).getCursorID();
			if (oldCursor != newCursor) {
				invalidateAndRefresh = true;
				((TableCellSWT) event.cell).setCursorID(newCursor);
			}
		}

		Object o = event.cell.getToolTip();
		if ((o == null) || (o instanceof String)) {
			String oldTooltip = (String) o;
			if (!StringCompareUtils.equals(oldTooltip, tooltip)) {
				invalidateAndRefresh = true;
				event.cell.setToolTip(tooltip);
			}
		}

		if (invalidateAndRefresh) {
			event.cell.invalidate();
			((TableCellSWT)event.cell).redraw();
		}
	}

	boolean bMouseDowned = false;

	private Rectangle getDrawBounds(TableCellSWT cell) {
		Rectangle bounds = cell.getBounds();
		bounds.height -= 12;
		bounds.y += 6;
		bounds.x += 4;
		bounds.width -= 4;

		return bounds;
	}


}
