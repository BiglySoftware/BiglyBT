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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagUtils;
import com.biglybt.core.tag.Taggable;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellAddedListener;
import com.biglybt.pif.ui.tables.TableCellMenuEvent;
import com.biglybt.pif.ui.tables.TableCellMenuListener;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableCellToolTipListener;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;
import com.biglybt.ui.swt.views.utils.TagUIUtils;
import com.biglybt.ui.swt.widgets.TagPainter;

public interface
TagsColumnHelper
	extends TableCellRefreshListener, TableCellToolTipListener,
			TableCellSWTPaintListener, TableCellAddedListener, TableCellMenuListener
{
	public Font[] fontOneLine 		= { null };
	public Font[] fontMultiLine 	= { null };
	
	public List<Tag>
	getTags(
		TableCell cell );

	@Override
	default public void cellAdded(TableCell cell) {
		cell.setMarginHeight(1);
		cell.setMarginHeight(1);
	}	
	
	@Override
	default public void refresh(TableCell cell) {
		StringBuilder sb1 = new StringBuilder();
		StringBuilder sb2 = new StringBuilder();

		try {
			List<Tag> tags = getTags( cell );

			if (tags.isEmpty()) {
				return;
			}

			tags = TagUtils.sortTags(tags);

			for (Tag tag : tags) {
				if ( sb1.length() > 0 ){
					sb1.append( "," );
				}
				sb1.append( tag.getTagName( true ));
				sb2.append( tag.getTagUID());
			}

		} finally {
			cell.setTextEquivalent( sb1.toString());
			sb1.append( "," );
			sb1.append( sb2 );	// we need the sort value to change even when the string value doesn't
			cell.setSortValue(sb1.toString());
			
		}
	}

	@Override
	default public void 
	menuEventOccurred(
		TableCellMenuEvent e)
	{
		TableCell	_cell = e.cell;
		
		if ( !(_cell instanceof TableCellSWT )){
			
			return;
		}
		
		TableCellSWT	cell = (TableCellSWT)_cell;
		
		List<TagLayout>	layouts = (List<TagLayout>)cell.getData( TagsColumnHelper.class );
				
		if ( layouts == null ){
			
			return;
		}

		Object	ev = e.baseEvent;
		
		if ( ev instanceof MenuDetectEvent ){
			
			Object data = ((MenuDetectEvent)ev).getSource();
			
			if ( data instanceof Control ){
				
				Point	pt = new Point( e.x, e.y );
										
				Tag target = null;
				
				for ( TagLayout entry: layouts){
									
					if ( entry.clip.contains( pt )){
						
						target = entry.painter.getTag();
						
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
	default public void cellHover(TableCell cell) {
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
	default public void cellHoverComplete(TableCell cell) {
		cell.setToolTip(null);
	}

	@Override
	default public void cellPaint(GC gc, TableCellSWT cell) {
		Object ds = PluginCoreUtils.convert( cell.getDataSource(), true );
		
		Taggable taggable = (ds instanceof Taggable)?(Taggable)ds:null;

		Point 		cellSize 	= cell.getSize();
		Rectangle 	bounds 		= cell.getBounds();

			// adjust size to leave 1 pixel gaps left+right
		
		cellSize.x 	-= 3;
		bounds.x 	+= 1;
		
		Font oldFont = gc.getFont();
		
		int maxCellLines = cell.getMaxLines();
		if (maxCellLines <= 1) {
			if (fontOneLine[0] == null) {
				fontOneLine[0] = FontUtils.getFontWithHeight(oldFont,
						(int) (FontUtils.getFontHeightInPX(oldFont) - 2), SWT.DEFAULT);
			}
			gc.setFont(fontOneLine[0]);
		} else {
			if (fontMultiLine[0] == null) {
				fontMultiLine[0] = FontUtils.getFontWithHeight(gc.getFont(),
						((cellSize.y - 2) / maxCellLines), SWT.DEFAULT);
			}
			gc.setFont(fontMultiLine[0]);
		}

		List<Tag> tags = getTags( cell );
		
		tags = TagUtils.sortTags(tags);
		int x = 0;
		int y = 0;
		int lineHeight = 0;

		List<TagLayout>	layouts		= new ArrayList<>( tags.size());
		
		cell.setData( TagsColumnHelper.class, layouts );

		try{
			int	maxLines = 1;
							
			for ( Tag tag : tags ){
								
				TagPainter painter = new TagPainter(tag);
								
				painter.setCompact(true, false);
				painter.alwaysDrawBorder = true;
				painter.paddingContentY = 1;
				painter.paddingContentX0 = 2;
				painter.paddingContentX1 = 4;
				painter.setMinWidth(16);
				
				Point size = painter.getSize(gc);
				
				if ( size == null || size.x == 0 || size.y == 0 ){
					
					painter.dispose();
					
					continue;
				}
					
				TagLayout	layout = new TagLayout();
				
				layouts.add( layout );
				
				layout.painter	= painter;
				layout.clip 	=  new Rectangle( -1, -1, size.x, size.y );
												
				int endX = x + size.x;
	
				if ( endX >= cellSize.x ){
					
					// this tag doesn't completely fit on the current line. If it is the first one
					// then retain it (and truncate it) otherwise we'll try and move it to the next line
					
					if ( x == 0 ){
												
						lineHeight = size.y;
						
					}else{
						
						y += lineHeight + 1;
						
						if ( y + size.y > cellSize.y ){
							
							layout.truncated = true;
							
							break;	// doesn't fit 
						}
						
						maxLines++;
												
						lineHeight = size.y;
					}
					
					x = size.x + 1;
					
				}else{
					
					lineHeight = Math.max(lineHeight, size.y);
					
					x = endX + 1;
				}
			}
			
				// now we know the number of valid lines we can lay out the tags
			
			x = 0;
			y = 0;
			lineHeight = 0;
			
			int lineNumber = 1;
					
			boolean forceNextLine = false;
			
			TagLayout prevLayout = null;
			
			for ( TagLayout layout: layouts ){
									
				Rectangle clip = layout.clip;
								
				int endX = x + clip.width;
	
				if ( endX >= cellSize.x || forceNextLine ){
					
					if (( x == 0 || lineNumber == maxLines ) && !forceNextLine ){
											
						lineHeight = Math.max( lineHeight, clip.height );
						
						if ( lineNumber < maxLines ){
							
							forceNextLine = true;
						}
					}else{
						
						forceNextLine = false;
						
						lineNumber++;
						
						if ( lineNumber > maxLines ){
													
							break;
						}
						
						y += lineHeight + 1;
						
						x = 0;
						
						endX = clip.width;
						
						lineHeight = clip.height;
					}
				}else{
					
					lineHeight = Math.max(lineHeight, clip.height);
				}
	
				int clipW = endX > cellSize.x ? cellSize.x - x : clip.width;
				int clipH = y + clip.height > cellSize.y ? cellSize.y - y : clip.height;
						
				if ( x > 0 && clipW < clip.width ){
					
					layout.truncated = true;
					
					if ( prevLayout != null && clipW < 5 ){
						
						prevLayout.truncated = true;
					}
				}
				
				clip.x = bounds.x + x;
				clip.y = bounds.y + y;
				clip.width	= clipW;
				clip.height	= clipH;
						
				x = endX + 1;
				
				prevLayout = layout;
			}
			
			Rectangle clipping = gc.getClipping();
	
			try{
				int endY = y + lineHeight;
				int yOfs = (cellSize.y - endY) / 2;
				if (yOfs < 0) {
					yOfs = 0;
				}
				
				for ( TagLayout layout: layouts ){
					
					Rectangle clip = layout.clip;
						
					TagPainter painter = layout.painter;
						
					clip.y += yOfs;
						
					gc.setClipping(clip);	
					
					if ( layout.truncated ){
					
						painter.setAlpha(100);
					}
						
					painter.paint(taggable, gc, clip.x, clip.y);
				}
								
			}finally{
			
				gc.setClipping(clipping);
			}
		}finally{
			
			gc.setFont(oldFont);
			
			for ( TagLayout layout: layouts ){
				
				if ( layout.painter != null ){
					
					layout.painter.dispose();
				}
			}
		}
	}
	
	static class
	TagLayout
	{
		TagPainter		painter;
		Rectangle		clip;
		boolean			truncated;
	}
}