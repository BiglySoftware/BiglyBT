/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.columns.subscriptions;

import com.biglybt.util.StringCompareUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Display;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionDownloadListener;
import com.biglybt.core.subs.SubscriptionException;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.shells.GCStringPrinter.URLInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;

import com.biglybt.ui.swt.skin.SWTSkinFactory;
import com.biglybt.ui.swt.skin.SWTSkinProperties;


public class 
ColumnSubscriptionActions
	extends CoreTableColumnSWT
	implements TableCellSWTPaintListener, TableCellRefreshListener,
	TableCellMouseMoveListener, TableCellAddedListener
{
	public static final String COLUMN_ID = "actions";

	private static final Object UPDATING_KEY = new Object();
	
	private Color colorLinkNormal;

	private Color colorLinkHover;

	private static Font font = null;

	public 
	ColumnSubscriptionActions( String sTableID )
	{
		super(COLUMN_ID, POSITION_LAST, 100, sTableID);
		
		setRefreshInterval(INTERVAL_INVALID_ONLY);
		setAlignment( ALIGN_LEAD  );

		addListeners(this);
		setType(TableColumn.TYPE_GRAPHIC);

		setUseCoreDataSource(true);

		SWTSkinProperties skinProperties = SWTSkinFactory.getInstance().getSkinProperties();
		colorLinkNormal = skinProperties.getColor("color.links.normal");
		colorLinkHover = skinProperties.getColor("color.links.hover");
	}

	@Override
	public void
	cellPaint(
		GC gc, TableCellSWT cell) 
	{
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
			
			Point p = sp.getCalculatedPreferredSize();
			
			int pref = p.x + 10;
			
			TableColumn tableColumn = cell.getTableColumn();
			
			if (tableColumn != null && tableColumn.getPreferredWidth() < pref) {
				
				tableColumn.setPreferredWidth(pref);
			}
		}
	}

	@Override
	public void cellAdded(TableCell cell) {
		cell.setMarginHeight(0);
	}

	@Override
	public void 
	refresh(TableCell cell) 
	{
		Subscription sub = (Subscription)cell.getDataSource();

		if (sub == null) return;

		boolean canView		= !sub.isSearchTemplate();
		boolean canUpdate	= !( sub.isSearchTemplate() || sub.isSubscriptionTemplate());
		
		int sort = (canView?2:0) + (canUpdate?1:0);
		
		if (!cell.setSortValue(sort) && cell.isValid()) {
			return;
		}


		StringBuilder sb = new StringBuilder();
		if (canView) {

			sb.append("<A HREF=\"view\">" + MessageText.getString( "label.view") + "</A>");
		}

		if (canUpdate) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			if ( sub.getUserData( UPDATING_KEY ) == null ){
				sb.append("<A HREF=\"update\">" + MessageText.getString( "UpdateWindow.ok") + "</A>");
			}else{
				sb.append( MessageText.getString( "UpdateWindow.ok"));
			}
		}

		cell.getTableRow().setData("text", sb.toString());
	}

	@Override
	public void 
	cellMouseTrigger(
		TableCellMouseEvent event)
	{
		TableCellSWT cell = (TableCellSWT)event.cell;
		
		Subscription sub = (Subscription)cell.getDataSource();

		String tooltip = null;
		boolean invalidateAndRefresh = false;

		Rectangle bounds = cell.getBounds();
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
			Rectangle drawBounds = getDrawBounds(cell);
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
					if (hitUrl.url.equals("view")){

						String key = "Subscription_" + ByteFormatter.encodeString(sub.getPublicKey());
						MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
						if (mdi != null) {
							mdi.showEntryByID(key);
						}

					}else if ( hitUrl.url.equals("update")){

						if ( sub.getUserData( UPDATING_KEY ) != null ){
							
							return;
						}
						try{
							sub.setUserData( UPDATING_KEY, true );
							
							cell.getTableRowSWT().invalidate( true );
							
							sub.getManager().getScheduler().download(
								sub,
								true,
								new SubscriptionDownloadListener(){
									
									public void
									complete(
										Subscription		subs )
									{
										done();
									}

									public void
									failed(
										Subscription			subs,
										SubscriptionException	error )
									{
										done();
									}
							
									private void
									done()
									{
										sub.setUserData( UPDATING_KEY, null );
										
										cell.getTableRowSWT().invalidate( true );
									}
								});
								
						}catch( Throwable e ){
							
							sub.setUserData( UPDATING_KEY, null );
							
							cell.getTableRowSWT().invalidate( true );
							
							Debug.out( e );
						}
					}
				}else{
					if (hitUrl.url.equals("view")){
						tooltip = "";	//
					}else if ( hitUrl.url.equals("update")){
						tooltip = "";	//
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
