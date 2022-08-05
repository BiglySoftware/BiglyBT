/*
 * File    : CategoryItem.java
 * Created : 01 feb. 2004
 * By      : TuxPaper
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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.pif.ui.tables.TableContextMenuItem;
import com.biglybt.pif.ui.tables.TableRow;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;
import com.biglybt.ui.swt.views.table.TableRowSWT;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.utils.ColorCache;

/** Display Category torrent belongs to.
 *
 * @author TuxPaper
 */
public class TagColorsItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener, TableCellSWTPaintListener, ParameterListener
{
	private static TagManager tag_manager = TagManagerFactory.getTagManager();

	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "tag_colors";

	private static String CFG_SHOW_DEF_COLOURS = "TagColorsItem.showdefcolours";
	
	private static TableViewSWT.ColorRequester	color_requester = ()-> 10;
	
	private static boolean show_default_colours;
	
	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_CONTENT });
	}

	/** Default Constructor */
	public TagColorsItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_LEAD, 70, sTableID);
		setRefreshInterval(INTERVAL_LIVE);
				
		show_default_colours	 = COConfigurationManager.getBooleanParameter(CFG_SHOW_DEF_COLOURS);

		COConfigurationManager.addWeakParameterListener(this, false, CFG_SHOW_DEF_COLOURS);

		
		TableContextMenuItem menuShowIcon = addContextMenuItem(
				"menu.show.default.colors", MENU_STYLE_HEADER);
		menuShowIcon.setStyle(TableContextMenuItem.STYLE_CHECK);
		menuShowIcon.addFillListener(new MenuItemFillListener() {
			@Override
			public void menuWillBeShown(MenuItem menu, Object data) {
				menu.setData(Boolean.valueOf(show_default_colours));
			}
		});

		menuShowIcon.addMultiListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				COConfigurationManager.setParameter(CFG_SHOW_DEF_COLOURS,
						((Boolean) menu.getData()).booleanValue());
			}
		});
	}

	@Override
	public void remove() {
		super.remove();
	}

	@Override
	public void reset() {
		super.reset();

		COConfigurationManager.removeParameter( CFG_SHOW_DEF_COLOURS );
	}


	@Override
	public void parameterChanged(String parameterName) {
		setShowDefaultColours( COConfigurationManager.getBooleanParameter(CFG_SHOW_DEF_COLOURS));
	}

	public void setShowDefaultColours(boolean b) {
		show_default_colours = b;
		invalidateCells();
	}
	
	@Override
	public void refresh(TableCell cell) {
		String sTags = null;
		DownloadManager dm = (DownloadManager)cell.getDataSource();
		if (dm != null) {
			List<Tag> tags = tag_manager.getTagsForTaggable( TagType.TT_DOWNLOAD_MANUAL, dm );

			if ( tags.size() > 0 ){

				for ( Tag tag: tags ){

					String str = tag.getTagName( true );

					if ( sTags == null ){
						sTags = str;
					}else{
						sTags += ", " + str;
					}
										
	
				}
			}
			
			int[] 	row_fg_color 		= null;
			int[] 	row_bg_color 		= null;
			
			long	row_fg_color_priority 	= -1;
			long	row_fg_color_uuid		= -1;
			
			long	row_bg_color_priority 	= -1;
			long	row_bg_color_uuid		= -1;
			
			for ( Tag tag: tags ){
						
				long[] colours = tag.getColors();
				
				if ( colours.length > 0 ){

					long	priority;
					
					long	uuid = tag.getTagUID();
					
					if ( colours.length >= 3 ){
						
						priority = colours[2];
						
					}else{
						
						priority = uuid;
					}
				
					boolean	set_fg = false;
					boolean	set_bg = false;
					
					long	fg = colours[0];

					if ( fg >= 0 ){
						
						if ( row_fg_color_priority == -1 || row_fg_color_priority < priority ){
												
							set_fg = true;
							
						}else if ( row_fg_color_priority == priority ){
							
							if ( row_fg_color_uuid <  uuid ){
															
								set_fg = true;
							}
						}
					}
					
					long	bg = colours.length>1?colours[1]:-1;
					
					if ( bg >= 0 ){
						
						if ( row_bg_color_priority == -1 || row_bg_color_priority < priority ){
												
							set_bg = true;
							
						}else if ( row_bg_color_priority == priority ){
							
							if ( row_bg_color_uuid <  uuid ){
															
								set_bg = true;
							}
						}
					}
					
					if ( set_fg ){
																			
						row_fg_color = new int[]{ (int)((fg>>16)&0x00ff), (int)((fg>>8)&0x00ff), ((int)fg&0x00ff)};
						
						row_fg_color_priority	= priority;
						row_fg_color_uuid		= uuid;

					}
					
					if ( set_bg ){
									
						row_bg_color = new int[]{ (int)((bg>>16)&0x00ff), (int)((bg>>8)&0x00ff), ((int)bg&0x00ff)};
			
						row_bg_color_priority	= priority;
						row_bg_color_uuid		= uuid;
					}
				}
			}
			
			TableRow row = cell.getTableRow();
			
			if ( row instanceof TableRowSWT ){
						
				boolean	missing_fg = false;
				boolean	missing_bg = false;
				
				Color fg = null;
				Color bg = null;
				
				if ( row_fg_color != null ){
					
					fg = ColorCache.getColor( null, row_fg_color );
					
					if ( fg == null ){
						
						missing_fg = true;
					}
				}

				
				if ( row_bg_color != null ){
					
					bg = ColorCache.getColor( null, row_bg_color );
					
					if ( bg == null ){
						
						missing_bg = true;
					}
				}
				
				if ( missing_fg ){
					
					int[] f_row_color = row_fg_color;
					
					Utils.execSWTThread(
						()->{
							((TableRowSWT)row).requestForegroundColor( color_requester, ColorCache.getColor( Display.getCurrent(), f_row_color ));
						});
				}else{
				
					((TableRowSWT)row).requestForegroundColor( color_requester, fg);
				}
				
				if ( missing_bg ){
					
					int[] f_row_color = row_bg_color;
					
					Utils.execSWTThread(
						()->{
							((TableRowSWT)row).requestBackgroundColor( color_requester, ColorCache.getColor( Display.getCurrent(), f_row_color ));
						});
				}else{
				
					((TableRowSWT)row).requestBackgroundColor( color_requester, bg );
				}
			}
		}
		
		cell.setSortValue( sTags );
		cell.setToolTip((sTags == null) ? "" : sTags );
	}

	@Override
	public void cellPaint(GC gc, TableCellSWT cell) {

		DownloadManager dm = (DownloadManager)cell.getDataSource();

		List<Color> colors = new ArrayList<>();

		if ( dm != null ){

			List<Tag> tags = tag_manager.getTagsForTaggable( TagType.TT_DOWNLOAD_MANUAL, dm );
			
			for ( Tag tag: tags ){

				int[] rgb = tag.getColor();

				if ( rgb != null && rgb.length == 3 && ( show_default_colours || !tag.isColorDefault())){

					Color color = ColorCache.getColor( gc.getDevice(), rgb );
					
					if ( color != null ){
					
						colors.add( color );
					}
				}						
			}
		}

		int	num_colors = colors.size();

		if ( num_colors > 0 ){

			Rectangle bounds = cell.getBounds();

			Utils.setClipping(gc, new Rectangle( bounds.x, bounds.y+1, bounds.width, bounds.height-2 ));

			if ( num_colors == 1 ){

				gc.setBackground( colors.get(0));

				gc.fillRectangle( bounds );

			}else{

				int	width = bounds.width;
				int	chunk = width/num_colors;

				if ( chunk == 0 ){
					chunk = 1;
				}

				bounds.width = chunk;

				for ( int i=0;i<num_colors;i++){

					if ( i == num_colors-1 ){

						int	rem = width - ( chunk * (num_colors-1 ));

						if ( rem > 0 ){

							bounds.width = rem;
						}
					}

					gc.setBackground( colors.get(i));

					gc.fillRectangle( bounds );

					bounds.x += chunk;
				}
			}
		}
	}
	
	
}
