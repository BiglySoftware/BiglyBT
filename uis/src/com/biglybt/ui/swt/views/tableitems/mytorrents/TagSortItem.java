/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import java.util.Map;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.util.Constants;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.common.table.TableViewCreator;
import com.biglybt.ui.swt.views.MyTorrentsView;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;



public class TagSortItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;
	
	public static final String COLUMN_ID = "tag_sort";
	
	public 
	TagSortItem(
		String sTableID) 
	{
		super( DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 70, sTableID);
		
		setRefreshInterval( INTERVAL_LIVE );
	}


	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
				CAT_SETTINGS
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	@Override
	public void 
	refresh(
		TableCell cell )
	{
		Long	value	= null; 
		boolean reverse = false;
		
		DownloadManager dm = (DownloadManager) cell.getDataSource();
		TableView<?> tv =  cell.getTableRow().getView();
		TableViewCreator tvc =  tv==null?null:cell.getTableRow().getView().getTableViewCreator();
		if ( dm != null && tvc instanceof MyTorrentsView ){
			Map<Long,Object[]> tag_sort = (Map<Long,Object[]>)dm.getDownloadState().getTransientAttribute( DownloadManagerState.AT_TRANSIENT_TAG_SORT );
			if ( tag_sort != null ){
				MyTorrentsView mtv = (MyTorrentsView)tvc;
				Tag[] tags = mtv.getCurrentTags();
				if ( tags != null && tags.length == 1 ){
					Object[] entry = tag_sort.get(tags[0].getTagUID());
					if ( entry != null ){
						value = (Long)entry[1];
						if ( entry.length > 2 ){
							String options = (String)entry[2];
							reverse = options != null && options.equals( "r" );
						}
					}
				}
			}
		}
		
		Long sort;
		
		if ( reverse ){
			
			if ( value == Long.MAX_VALUE ){
				
				sort = Long.MIN_VALUE;
				
			}else if ( value == Long.MIN_VALUE ){
				
				sort = Long.MAX_VALUE;
				
			}else{
				
				sort = -value;
			}
		}else{
			
			sort = value;
		}
		
		if ( !cell.setSortValue( sort==null?Long.MIN_VALUE:sort) && cell.isValid()){
			
			return;
		}
		
		cell.setSortValue( sort==null?Long.MIN_VALUE:sort );
		
		String text = "";
		
		if ( value != null ){
			
			if ( sort == Long.MAX_VALUE ){
				
				text = Constants.INFINITY_STRING;
				
			}else if ( sort == Long.MIN_VALUE ){
				
				text = "-" + Constants.INFINITY_STRING;
				
			}else{
				
				text = String.valueOf( sort );
			}
		}
		
		cell.setText( text );
	}
}
