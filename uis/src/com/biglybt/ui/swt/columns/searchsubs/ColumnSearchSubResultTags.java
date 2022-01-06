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

package com.biglybt.ui.swt.columns.searchsubs;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.biglybt.core.subs.util.SearchSubsResultBase;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.tableitems.TagsColumn;


public class ColumnSearchSubResultTags
	extends TagsColumn
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "tags";

	private static final TagManager tag_manager = TagManagerFactory.getTagManager();

	private static final TagType	tag_type =  tag_manager.getTagType( TagType.TT_SWARM_TAG );

	public 
	ColumnSearchSubResultTags( 
		Class ds, String tableID, String columnID ) 
	{
		super( ds, tableID, columnID );
	}
	
	@Override
	public List<Tag> 
	getTags(
		TableCell cell )
	{
		SearchSubsResultBase result = (SearchSubsResultBase)cell.getDataSource();
		
		if ( result != null ){

			String[] tag_names = result.getTags();

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
