/*
 * Created on 10-Dec-2004
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.views.tableitems.myshares;

import java.util.*;

import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagUtils;
import com.biglybt.pif.sharing.ShareManager;
import com.biglybt.pif.sharing.ShareResource;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 * @author parg
 *
 */

public class
TagsItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	private static TagManager tag_manager = TagManagerFactory.getTagManager();

	public
	TagsItem()
	{
		super("tags", POSITION_LAST, 100, TableManager.TABLE_MYSHARES);

		setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void
	refresh(TableCell cell)
	{
		ShareResource item = (ShareResource)cell.getDataSource();

		if (item == null){

			cell.setText("");

		}else{

			Map<String,String> properties = item.getProperties();
			
			String	value = properties==null?null:properties.get( ShareManager.PR_TAGS );

			if ( value == null ){
				
				value = "";
				
			}else{
				
				String[] bits = value.split( "," );
				
				value = "";
				
				List<Tag>	tags = new ArrayList<>();
				
				for ( String bit: bits ){
					
					try{
						Tag tag = tag_manager.lookupTagByUID( Long.parseLong( bit ));
						
							// might have been deleted
						
						if ( tag != null ){
						
							tags.add( tag );
						}					
					}catch( Throwable e ){
						
					}
				}
				
				tags = TagUtils.sortTags( tags );
				
				for ( Tag tag: tags ){
					
					value += (value.isEmpty()?"":", ") + tag.getTagName( true );
				}
			}
			
			cell.setText( value );
		}
	}
}
