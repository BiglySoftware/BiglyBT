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

package com.biglybt.ui.swt.columns.tag;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;


import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnExtraInfoListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagGroup;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.tag.TagUtils;
import com.biglybt.ui.swt.imageloader.ImageLoader;

/** Display Category torrent belongs to.
 *
 * @author TuxPaper
 */
public class ColumnTagGroupIcons
       implements TableCellRefreshListener, TableCellSWTPaintListener, TableColumnExtraInfoListener
{
	private static Object KEY_TAG_MUT		= new Object();
	private static Object KEY_IMAGE_FILES	= new Object();
	
	private static TagManager tag_manager = TagManagerFactory.getTagManager();

	public static final Class DATASOURCE_TYPE = Download.class;
	
	private static int[]	interesting_tts = { TagType.TT_DOWNLOAD_MANUAL, TagType.TT_DOWNLOAD_CATEGORY };

	private TagGroup	tag_group;
	
	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	public ColumnTagGroupIcons(TableColumn column, TagGroup	tg ) {
		column.initialize(TableColumn.ALIGN_LEAD, TableColumn.POSITION_INVISIBLE, 70);
		column.addListeners(this);
		column.setRefreshInterval(TableColumn.INTERVAL_LIVE);
		column.setType(TableColumn.TYPE_TEXT_ONLY);
		column.setIconReference("image.tag.column", false );

		tag_group	= tg;
	}
	
	@Override
	public void refresh(TableCell cell) {
		String sTags = null;
		Download dm = (Download)cell.getDataSource();
		if (dm != null) {
			DownloadManager core_dm = PluginCoreUtils.unwrap( dm );
			
			long mut = core_dm.getTagMutationCount();
			
			Long data = (Long)cell.getData( KEY_TAG_MUT );
			
			if ( data != null && data == mut ){
				
				return;
			}
			
			cell.setData( KEY_TAG_MUT, mut );
			
			List<Tag> tags = tag_manager.getTagsForTaggable( interesting_tts, core_dm );

			if ( tags.size() > 0 ){

				for ( Tag t: tags ){

					if ( t.getGroupContainer() == tag_group ){
						
						String file = t.getImageFile();
						
						if ( file != null ){
							
							String str = t.getTagName( true );
		
							if ( sTags == null ){
								
								sTags = tag_group.getName() + ": " + str;
								
							}else{
								
								sTags += ", " + str;
							}
						}
					}
				}
			}
		}

		cell.setSortValue( sTags==null?" ":sTags );	// need to use a space to sort sensibly
		cell.setToolTip( sTags == null?"":sTags );
	}

	@Override
	public void cellPaint(GC gc, TableCellSWT cell) {

		Download dm = (Download)cell.getDataSource();

		String[] files;
		
		if (dm != null) {

			DownloadManager core_dm = PluginCoreUtils.unwrap( dm );
			
			long mut = core_dm.getTagMutationCount();
			
			Object[] data = (Object[])cell.getData( KEY_IMAGE_FILES );
				
			if ( data == null || (Long)data[0] != mut ){
				
				List<Tag> tags = tag_manager.getTagsForTaggable( interesting_tts, core_dm );
	
				tags = TagUtils.sortTagIcons( tags );
				
				List<String> f = new  ArrayList<>( tags.size());
				
				for ( Tag tag: tags ){
	
					if ( tag.getGroupContainer() == tag_group ){
						
						String file = tag.getImageFile();
						
						if ( file != null ){
							
							f.add( file );
						}
					}
				}
				
				files = f.toArray( new String[f.size()] );
						
				cell.setData( KEY_IMAGE_FILES, new Object[]{ mut, files });
				
			}else{
				
				files = (String[])data[1];
			}
		}else{
			
			files = new String[0];
		}

		int	num_files = files.length;

		if ( num_files > 0 ){
						
			Rectangle bounds = cell.getBounds();

			bounds.x+=1;
			bounds.y+=1;
			bounds.width-=1;
			bounds.height-=2;

			int w = bounds.width / num_files;			
			
			List<Image> 	images 	= new ArrayList<>();
			List<String> 	keys 	= new ArrayList<>();
						
			for ( String file: files ){
				
				try{
					ImageLoader.getInstance().getFileImage(
							new File( file ),
							new Point( w-1, bounds.height),
							new ImageLoader.ImageDownloaderListener(){
			
								@Override
								public void imageDownloaded(Image image, String key, boolean returnedImmediately){
									  							  
									if ( image != null && returnedImmediately ){

										images.add( image );
										 
										keys.add( key );
									 }
								}
							});
					
				}catch( Throwable e ){
				}
			}
			
			int prefWidth = 0;
			
			if ( images.size() > 0 ){
				
				int	width_per_image = bounds.width / images.size();
				
				for ( int i=0;i<images.size();i++){
				
					Image image = images.get(i);
					
					int iw = image.getBounds().width;
					
					prefWidth += iw;
					
					gc.drawImage( image,  bounds.x + (width_per_image-iw)/2, bounds.y );
	
					bounds.x += width_per_image;
	
					ImageLoader.getInstance().releaseImage( keys.get(i));
				}
			}
			
			TableColumn tableColumn = cell.getTableColumn();
			
			if (tableColumn != null && tableColumn.getPreferredWidth() < prefWidth) {
				
				prefWidth = Math.max( 16, prefWidth );
				prefWidth = Math.min( 256, prefWidth );
				
				tableColumn.setPreferredWidth(prefWidth);
			}
		}
	}
}
