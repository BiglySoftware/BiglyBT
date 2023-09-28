/*
 * File    : SeedingRankColumnListener.java
 * Created : Sep 27, 2005
 * By      : TuxPaper
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

package com.biglybt.plugin.startstoprules.defaultplugin;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.Download.SeedingRank;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;

/** A "My Torrents" column for displaying Seeding Rank.
 */
public class 
SeedingRankColumnListener 
	implements TableCellRefreshListener
{
	private static final Object		KEY = new Object();
	
	@Override
	public void refresh(TableCell cell) {
		Download dl = (Download) cell.getDataSource();
		if (dl == null)
			return;

		DefaultRankCalculator dlData;
		Object o = cell.getSortValue();
		if (o instanceof DefaultRankCalculator)
			dlData = (DefaultRankCalculator) o;
		else {
			dlData = StartStopRulesDefaultPlugin.getRankCalculator( dl );
		}
		
		if (dlData == null){
			cell.setSortValue( null );
			return;
		}
			
		Sorter	comp = (Sorter)dlData.getUserData( KEY );
		
		if ( comp == null ){
			
			comp = new Sorter( dlData );
			
			dlData.setUserData( KEY, comp );
		}
		
		cell.setSortValue( comp );	

		SeedingRank sr = dl.getSeedingRank();
		
		String[] status = sr.getStatus( false );

		cell.setText(status[0]);
		
		/* 
 		int state = dlData.getState();

		if ( state == Download.ST_STOPPED || state == Download.ST_ERROR ){
			
			cell.setText( "" );
			
		}else{
						
			cell.setText(status[0]);
		}
		*/
		
		cell.setToolTip(status[1] );
	}
	
	private class
	Sorter
		implements Comparable<Sorter>
	{
		private DefaultRankCalculator		drc;
		
		Sorter(
			DefaultRankCalculator		_drc )
		{
			drc = _drc;
		}
		
		@Override
		public int 
		compareTo(
			Sorter o)
		{
			return( drc.compareToIgnoreStopped( o.drc ));
		}
	}
}
