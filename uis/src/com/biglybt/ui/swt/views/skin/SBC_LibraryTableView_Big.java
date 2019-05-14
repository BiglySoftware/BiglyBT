/*
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

package com.biglybt.ui.swt.views.skin;

import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.swt.columns.utils.TableColumnCreatorV3;


public class SBC_LibraryTableView_Big
	extends SBC_LibraryTableView
{

	@Override
	public String getUpdateUIName() {
		return "SBC_LibraryTableView_Big";
	}

	@Override
	public int getTableMode() {
		return SBC_LibraryView.MODE_BIGTABLE;
	}

	@Override
	public boolean useBigTable() {
		return true;
	}

	@Override
	public TableColumnCore[] getColumns() {
		String tableID = SB_Transfers.getTableIdFromFilterMode(torrentFilterMode, true, initialDataSource );
		if ( tableID != null ){
			return( TableColumnCreatorV3.createCompleteDM( tableID, true ));
		}else{
			return( null );
		}
	}
}
