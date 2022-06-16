/*
 * File    : SizeItem.java
 * Created : 24 nov. 2003
 * By      : Olivier
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

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.ui.swt.columns.ColumnCheckBox;

public class PrivateItem
	extends ColumnCheckBox
{
	public static final Class DATASOURCE_TYPE = Download.class;
	
	public static String COLUMN_ID = "private.torrent";

	public
	PrivateItem(
		TableColumn column )
	{
		super( column, 80, true );

		column.setRefreshInterval( TableColumn.INTERVAL_INVALID_ONLY );
	}

	@Override
	protected Boolean
	getCheckBoxState(
		Object datasource )
	{
		Download dl = (Download)datasource;

		if ( dl != null ){

			return( dl.getTorrent().isPrivate());
		}

		return( null );
	}

	@Override
	protected void
	setCheckBoxState(
		Object 	datasource,
		boolean set )
	{
	}
}
