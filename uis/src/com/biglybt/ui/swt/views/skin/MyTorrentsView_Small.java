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

import com.biglybt.core.Core;


import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import com.biglybt.pif.download.Download;

import com.biglybt.ui.swt.views.MyTorrentsView;
import com.biglybt.ui.common.table.TableColumnCore;

public class MyTorrentsView_Small
	extends MyTorrentsView
{
	public 
	MyTorrentsView_Small(
		Core 				_core, 
		int 				torrentFilterMode, 
		Object 				dataSource,
        TableColumnCore[] 	basicItems,
	    Text 				txtFilter, 
	    Composite 			cCatsTags) 
	{
		super( true );

		this.txtFilter 			= txtFilter;
		this.cCategoriesAndTags = cCatsTags;
		
		init( _core, SB_Transfers.getTableIdFromFilterMode(torrentFilterMode, false, dataSource), Download.class, basicItems );
	}
}
