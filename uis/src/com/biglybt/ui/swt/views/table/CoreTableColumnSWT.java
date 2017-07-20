/*
 * Created on Jan 15, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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
 */


package com.biglybt.ui.swt.views.table;

import com.biglybt.ui.common.table.impl.CoreTableColumn;

public abstract class
CoreTableColumnSWT
	extends CoreTableColumn
{
	public
	CoreTableColumnSWT(
		String 	sName,
		int 	iAlignment,
        int 	iPosition,
        int 	iWidth,
        String 	sTableID )
	{
		super( sName, iAlignment, iPosition, iWidth, sTableID );
	}

	public
	CoreTableColumnSWT(
		Class 	forDataSourceType,
		String 	sName,
		int 	iAlignment,
		int 	iWidth,
		String 	sTableID )
	{
		super( forDataSourceType, sName, iAlignment, iWidth, sTableID );
	}

	public
	CoreTableColumnSWT(
		String 	sName,
		int 	iPosition,
		int 	iWidth,
		String 	sTableID )
	{
		super( sName, iPosition, iWidth, sTableID );
	}

	public
	CoreTableColumnSWT(
		String 	sName,
		int 	iWidth,
		String 	sTableID )
	{
		super( sName, iWidth, sTableID );
	}

	public
	CoreTableColumnSWT(
		String 	sName,
		String 	sTableID )
	{
		super( sName, sTableID );
	}

	@Override
	public void
	addListeners(
		Object listenerObject )
	{
		if ( listenerObject instanceof TableCellSWTPaintListener ){

		 	super.addCellOtherListener("SWTPaint", listenerObject);
		}

		super.addListeners(listenerObject);
	}
}
