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

import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.*;

public abstract class 
TagsColumn
	extends CoreTableColumnSWT
	implements TagsColumnHelper
{
	public static final Class<Download> DATASOURCE_TYPE = Download.class;


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
}
