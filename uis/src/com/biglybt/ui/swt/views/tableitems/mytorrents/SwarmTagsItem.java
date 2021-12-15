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

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import java.util.*;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.ui.swt.views.tableitems.TagsColumn;

public class 
SwarmTagsItem
	extends TagsColumn
{
	private static final TagManager tag_manager = TagManagerFactory.getTagManager();

	private static final TagType	tag_type =  tag_manager.getTagType( TagType.TT_SWARM_TAG );
	
	public static final String COLUMN_ID = "swarm.tags";


	public SwarmTagsItem(String sTableID) {
		super(sTableID, COLUMN_ID );
	}

	@Override
	public List<Tag> 
	getTags(
		TableCell cell)
	{
		DownloadManager dm = (DownloadManager)cell.getDataSource();
		
		if ( dm != null ){

			String[] tag_names = dm.getDownloadState().getListAttribute( DownloadManagerState.AT_SWARM_TAGS );

			if ( tag_names != null && tag_names.length > 0 ){
				
				Set<Tag>	tags = new HashSet<>(tag_names.length);
				
				for ( String tag_name: tag_names ){
					
					try{
						Tag tag = tag_type.getTag( tag_name, true );
						
						if ( tag == null ){
							
							tag = tag_type.createTag(tag_name, true );
						}
						
						tags.add( tag );
						
					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}
				
				return( new ArrayList<>( tags ));
			}
		}
		
		return( Collections.emptyList());
	}
}