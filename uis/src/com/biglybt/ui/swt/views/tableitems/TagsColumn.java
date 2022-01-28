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

package com.biglybt.ui.swt.views.tableitems;

import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.*;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;
import com.biglybt.ui.swt.views.utils.TagUIUtils;
import com.biglybt.ui.swt.widgets.TagPainter;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.*;


public abstract class 
TagsColumn
	extends CoreTableColumnSWT
	implements TableCellRefreshListener, TableCellToolTipListener,
	TableCellSWTPaintListener, TableCellAddedListener, TableCellMenuListener
{
	private static final Object TAG_MAPPING_KEY = new Object();
	
	public static final Class<Download> DATASOURCE_TYPE = Download.class;

	private static Font fontOneLine = null;

	private static Font fontMultiLine = null;

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_CONTENT });
	}

	public 
	TagsColumn(
		String 	tableID,
		String	columnID ) 
	{
		super(DATASOURCE_TYPE, columnID, ALIGN_LEAD, 70, tableID);
		
		setRefreshInterval(INTERVAL_LIVE);
	}

	public 
	TagsColumn(
		Class	ds,
		String 	tableID,
		String	columnID ) 
	{
		super( ds, columnID, ALIGN_LEAD, 70, tableID);
		
		setRefreshInterval(INTERVAL_LIVE);
	}
	
	@Override
	public void cellAdded(TableCell cell) {
		cell.setMarginHeight(1);
		cell.setMarginHeight(1);
	}

	public abstract List<Tag>
	getTags(
		TableCell cell );
	
	@Override
	public void refresh(TableCell cell) {
		StringBuilder sb = new StringBuilder();

		try {
			List<Tag> tags = getTags( cell );

			if (tags.isEmpty()) {
				return;
			}

			tags = TagUtils.sortTags(tags);

			for (Tag tag : tags) {
				if (sb.length() != 0) {
					sb.append(", ");
				}
				sb.append(tag.getTagName(true));
			}

		} finally {
			cell.setSortValue(sb.toString());
		}
	}

	@Override
	public void 
	menuEventOccurred(
		TableCellMenuEvent e)
	{
		TableCell	_cell = e.cell;
		
		if ( !(_cell instanceof TableCellSWT )){
			
			return;
		}
		
		TableCellSWT	cell = (TableCellSWT)_cell;
		
		Map<TagPainter, Rectangle> tagMapping = (Map<TagPainter, Rectangle>)cell.getData( TAG_MAPPING_KEY );
				
		if ( tagMapping == null ){
			
			return;
		}

		Object	ev = e.baseEvent;
		
		if ( ev instanceof MenuDetectEvent ){
			
			Object data = ((MenuDetectEvent)ev).getSource();
			
			if ( data instanceof Control ){
				
				Point	pt = new Point( e.x, e.y );
										
				Tag target = null;
				
				for ( Map.Entry<TagPainter, Rectangle> entry: tagMapping.entrySet()){
					
					if ( entry.getValue().contains( pt )){
						
						target = entry.getKey().getTag();
						
						break;
					}
				}
				
				if ( target != null ){
					
					Menu menu = new Menu((Control)data);
				
					menu.addMenuListener(
						new MenuAdapter(){
							@Override
							public void menuHidden(MenuEvent e){
									// can't dispose immediately as it causes the menu hierarchy to be trashed
									// before having the chance to fire a selected item
								Utils.execSWTThreadLater(100, ()->{
									menu.dispose();
								});
							}
						});
					
					org.eclipse.swt.widgets.MenuItem mi = new org.eclipse.swt.widgets.MenuItem( menu, SWT.PUSH );
					
					mi.setText( MessageText.getString( "label.tag" ) + ": " + target.getTagName( true ));
					
					mi.setEnabled( false );;
					
					new org.eclipse.swt.widgets.MenuItem( menu, SWT.SEPARATOR );
					
					TagUIUtils.createSideBarMenuItems( menu, target );
				
					menu.setVisible( true );
							
					e.skipCoreFunctionality = true;
				}
			}
		}
	}
	
	@Override
	public void cellHover(TableCell cell) {
		List<Tag> tags = getTags( cell );
		tags = TagUtils.sortTags(tags);
		String group = null;
		boolean firstInGroup = true;
		StringBuilder sb = new StringBuilder();
		for (Tag tag : tags) {
			String newGroup = tag.getGroup();
			if (!Objects.equals(group, newGroup)) {
				group = newGroup;
				if (sb.length() > 0) {
					sb.append('\n');
					if (group == null) {
						sb.append('\n');
					}
				}
				if (group != null) {
					sb.append(group).append(": ");
				}
				firstInGroup = true;
			}
			if (firstInGroup) {
				firstInGroup = false;
			} else {
				sb.append(", ");
			}
			sb.append(tag.getTagName(true));
		}

		cell.setToolTip(sb.toString());
	}

	@Override
	public void cellHoverComplete(TableCell cell) {
		cell.setToolTip(null);
	}

	@Override
	public void cellPaint(GC gc, TableCellSWT cell) {
		Object ds = cell.getDataSource();
		
		Taggable taggable = (ds instanceof Taggable)?(Taggable)ds:null;

		Point 		cellSize 	= cell.getSize();
		Rectangle 	bounds 		= cell.getBounds();

			// adjust size to leave 1 pixel gaps left+right
		
		cellSize.x 	-= 3;
		bounds.x 	+= 1;
		
		Font oldFont = gc.getFont();
		

		int maxLines = cell.getMaxLines();
		if (maxLines <= 1) {
			if (fontOneLine == null) {
				fontOneLine = FontUtils.getFontWithHeight(oldFont,
						(int) (FontUtils.getFontHeightInPX(oldFont) - 2), SWT.DEFAULT);
			}
			gc.setFont(fontOneLine);
		} else {
			if (fontMultiLine == null) {
				fontMultiLine = FontUtils.getFontWithHeight(gc.getFont(),
						((cellSize.y - 2) / maxLines), SWT.DEFAULT);
			}
			gc.setFont(fontMultiLine);
		}

		List<Tag> tags = getTags( cell );
		
		tags = TagUtils.sortTags(tags);
		int x = 0;
		int y = 0;
		int lineHeight = 0;

		Map<TagPainter, Rectangle> mapTagPainting = new LinkedHashMap<>();

		for (Tag tag : tags) {
			TagPainter painter = new TagPainter(tag);
			painter.setCompact(true, false);
			painter.paddingContentY = 1;
			painter.paddingContentX0 = 2;
			painter.paddingContentX1 = 4;
			painter.setMinWidth(16);
			Point size = painter.getSize(gc);
			if (size == null) {
				continue;
			}
			int endX = x + size.x;

			if (endX > cellSize.x
					&& y + lineHeight + (lineHeight * 0.8) <= cellSize.y) {
				x = 0;
				endX = size.x;
				y += lineHeight + 1;
				lineHeight = size.y;
			} else {
				lineHeight = Math.max(lineHeight, size.y);
			}
			if (y > cellSize.y) {
				break;
			}

			int clipW = endX > cellSize.x ? cellSize.x - x : size.x;
			int clipH = y + size.y > cellSize.y ? cellSize.y - y : size.y;

			mapTagPainting.put(painter,
					new Rectangle(bounds.x + x, bounds.y + y, clipW, clipH));

			x = endX + 1;
			if (x > cellSize.x) {
				break;
			}
		}
		Rectangle clipping = gc.getClipping();

		int endY = y + lineHeight;
		int yOfs = (cellSize.y - endY) / 2;
		if (yOfs < 0) {
			yOfs = 0;
		}
		for (TagPainter painter : mapTagPainting.keySet()) {
			Rectangle clip = mapTagPainting.get(painter);
			clip.y += yOfs;
			gc.setClipping(clip);
			painter.paint(taggable, gc, clip.x, clip.y);
			painter.dispose();
		}

		cell.setData( TAG_MAPPING_KEY, mapTagPainting );
		gc.setClipping(clipping);
		gc.setFont(oldFont);
	}
}
