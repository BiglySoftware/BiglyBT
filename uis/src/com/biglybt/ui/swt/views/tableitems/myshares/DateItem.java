/*
 * Created on 05-May-2006
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


import com.biglybt.ui.swt.views.table.utils.TableColumnCreator;
import com.biglybt.ui.swt.views.tableitems.ColumnDateSizer;

import com.biglybt.pif.sharing.ShareResource;
import com.biglybt.pif.sharing.ShareResourceFile;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.pif.ui.tables.TableManager;

public class DateItem
	extends ColumnDateSizer
{
	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_TIME, CAT_CONTENT });
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	public 
	DateItem()
	{
		super( "date_added", ALIGN_TRAIL, POSITION_INVISIBLE, TableColumnCreator.DATE_COLUMN_WIDTH, TableManager.TABLE_MYSHARES );
	}

	@Override
	public void 
	refresh(
		TableCell cell, 
		long timestamp) 
	{
		ShareResource item = (ShareResource)cell.getDataSource();
	
		try{
			timestamp = (item instanceof ShareResourceFile)?((ShareResourceFile)item).getItem().getTorrent().getCreationDate()*1000:0;
			
		}catch( Throwable e ){
		}
				
		super.refresh(cell, timestamp);
	}
}
