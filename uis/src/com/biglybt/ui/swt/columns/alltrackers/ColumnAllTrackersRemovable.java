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

package com.biglybt.ui.swt.columns.alltrackers;

import com.biglybt.pif.ui.tables.TableColumn;

import com.biglybt.ui.swt.columns.ColumnCheckBox;
import com.biglybt.ui.swt.views.skin.SBC_AllTrackersView.AllTrackersViewEntry;



public class
ColumnAllTrackersRemovable
	extends ColumnCheckBox
{
	public static String COLUMN_ID = "alltrackers.removable";

	public
	ColumnAllTrackersRemovable(
		TableColumn column )
	{
		super( column, 80, true );

		column.setPosition( TableColumn.POSITION_INVISIBLE );
		column.setRefreshInterval( TableColumn.INTERVAL_LIVE );
	}

	@Override
	protected Boolean
	getCheckBoxState(
		Object datasource )
	{
		AllTrackersViewEntry tr = (AllTrackersViewEntry)datasource;

		if ( tr != null ){

			return( tr.isRemovable());
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
