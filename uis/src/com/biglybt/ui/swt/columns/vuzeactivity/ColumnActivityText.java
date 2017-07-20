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

package com.biglybt.ui.swt.columns.vuzeactivity;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import com.biglybt.activities.ActivitiesEntry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Display;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.shells.GCStringPrinter.URLInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;

import com.biglybt.core.util.GeneralUtils;
import com.biglybt.ui.skin.SkinConstants;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.skin.SWTSkinFactory;
import com.biglybt.ui.swt.skin.SWTSkinProperties;
import com.biglybt.util.StringCompareUtils;
import com.biglybt.util.UrlFilter;

import com.biglybt.pif.ui.tables.*;

/**
 * @author TuxPaper
 * @created Sep 25, 2008
 *
 */
public class ColumnActivityText
	extends CoreTableColumnSWT
	implements TableCellSWTPaintListener, TableCellRefreshListener,
	TableCellMouseMoveListener, TableCellToolTipListener
{
	public static final String COLUMN_ID = "activityText";

	private Color colorLinkNormal;

	private Color colorLinkHover;

	private static Font font = null;

	/**
	 * @param name
	 * @param tableID
	 */
	public ColumnActivityText(String tableID) {
		super(COLUMN_ID, tableID);

		initializeAsGraphic(600);
		SWTSkinProperties skinProperties = SWTSkinFactory.getInstance().getSkinProperties();
		colorLinkNormal = skinProperties.getColor("color.links.normal");
		colorLinkHover = skinProperties.getColor("color.links.hover");
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWTPaintListener#cellPaint(org.eclipse.swt.graphics.GC, com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void cellPaint(GC gc, TableCellSWT cell) {
		GCStringPrinter sp = setupStringPrinter(gc, cell);

		if (sp.hasHitUrl()) {
			URLInfo[] hitUrlInfo = sp.getHitUrlInfo();
			for (int i = 0; i < hitUrlInfo.length; i++) {
				URLInfo info = hitUrlInfo[i];
				info.urlUnderline = cell.getTableRow().isSelected();
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
		gc.setFont(null);
	}

	private GCStringPrinter setupStringPrinter(GC gc, TableCellSWT cell) {
		ActivitiesEntry entry = (ActivitiesEntry) cell.getDataSource();
		String text = entry.getText();
		Rectangle drawBounds = getDrawBounds(cell);

		entry.setViewed();

		if (!entry.isRead()) {
			if (font == null) {
				FontData[] fontData = gc.getFont().getFontData();
				fontData[0].setStyle(SWT.BOLD);
				font = new Font(gc.getDevice(), fontData);
			}
			gc.setFont(font);
		}

		int style = SWT.WRAP;

		GCStringPrinter sp = new GCStringPrinter(gc, text, drawBounds, true, true,
				style);

		sp.calculateMetrics();

		return sp;
	}

	// @see com.biglybt.pif.ui.tables.TableCellRefreshListener#refresh(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void refresh(TableCell cell) {
		ActivitiesEntry entry = (ActivitiesEntry) cell.getDataSource();

		cell.setSortValue(entry.getText());
	}

	// @see com.biglybt.pif.ui.tables.TableCellMouseListener#cellMouseTrigger(com.biglybt.pif.ui.tables.TableCellMouseEvent)
	@Override
	public void cellMouseTrigger(TableCellMouseEvent event) {
		String tooltip = null;
		boolean invalidateAndRefresh = false;

		ActivitiesEntry entry = (ActivitiesEntry) event.cell.getDataSource();
		//Rectangle bounds = getDrawBounds((TableCellSWT) event.cell);
		Rectangle bounds = ((TableCellSWT) event.cell).getBounds();

		String text = entry.getText();

		GC gc = new GC(Display.getDefault());
		GCStringPrinter sp = null;
		try {
			sp = setupStringPrinter(gc, (TableCellSWT) event.cell);
		} catch (Exception e) {
			Debug.out(e);
		} finally {
			gc.dispose();
		}

		if (sp != null) {
			URLInfo hitUrl = sp.getHitUrl(event.x + bounds.x, event.y + bounds.y);
			int newCursor;
			if (hitUrl != null) {
				String url = hitUrl.url;
				boolean ourUrl = UrlFilter.getInstance().urlCanRPC(url)
						|| url.startsWith("/") || url.startsWith("#");
				if (event.eventType == TableCellMouseEvent.EVENT_MOUSEDOWN && event.button == 1 ) {
					if (!ourUrl){
						if ( UrlUtils.isInternalProtocol( url )){
							try{
								UIFunctionsManagerSWT.getUIFunctionsSWT().doSearch( url );

							}catch( Throwable e ){

								Debug.out( e );
							}
						}else{
							Utils.launch(url);
						}
					} else {
						UIFunctionsSWT uif = UIFunctionsManagerSWT.getUIFunctionsSWT();
						if (uif != null) {
							String target = hitUrl.target;
							if (target == null) {
								target = SkinConstants.VIEWID_BROWSER_BROWSE;
							}
							uif.viewURL(hitUrl.url, target, "column.activity.text");
							return;
						}
					}
				}

				newCursor = SWT.CURSOR_HAND;
				if (ourUrl) {
					try {
						tooltip = hitUrl.title == null ? null : URLDecoder.decode(
								hitUrl.title, "utf-8");
					} catch (UnsupportedEncodingException e) {
					}
				} else {
					tooltip = hitUrl.url;
				}
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

	private Rectangle getDrawBounds(TableCellSWT cell) {
		Rectangle bounds = cell.getBounds();
		bounds.x += 4;
		bounds.width -= 4;

		return bounds;
	}

	// @see com.biglybt.pif.ui.tables.TableCellToolTipListener#cellHover(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void cellHover(TableCell cell) {
		if (cell.getToolTip() != null) {
			return;
		}
		if (!(cell instanceof TableCellSWT)) {
			return;
		}
		if (!Utils.isThisThreadSWT()) {
			System.err.println("you broke it");
			return;
		}
		GC gc = new GC(Display.getDefault());
		try {
			GCStringPrinter sp = setupStringPrinter(gc, (TableCellSWT) cell);

  		if (sp.isCutoff()) {
  			cell.setToolTip(GeneralUtils.stripOutHyperlinks(sp.getText()));
  		}
		} catch (Throwable t) {
			Debug.out(t);
		} finally {
			gc.dispose();
		}
	}

	// @see com.biglybt.pif.ui.tables.TableCellToolTipListener#cellHoverComplete(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void cellHoverComplete(TableCell cell) {
		cell.setToolTip(null);
	}
}
