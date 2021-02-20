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
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;

/** A "My Torrents" column for displaying Seeding Rank.
 */
public class
DownloadingRankColumnListener
	implements TableCellRefreshListener
{
	private StartStopRulesDefaultPlugin		plugin;

	public
	DownloadingRankColumnListener(
		StartStopRulesDefaultPlugin		_plugin )
	{
		plugin	= _plugin;
	}

	@Override
	public void refresh(TableCell cell) {
		Download dl = (Download) cell.getDataSource();
		if (dl == null)
			return;

		DefaultRankCalculator dlData = null;
		Object o = cell.getSortValue();
		if (o instanceof DefaultRankCalculator)
			dlData = (DefaultRankCalculator) o;
		else {
			dlData = StartStopRulesDefaultPlugin.getRankCalculator( dl );
		}
		if (dlData == null)
			return;

		int position = dlData.getPosition();

		cell.setSortValue( position );

		cell.setText( "" + position );
		if (plugin.bDebugLog) {
			String dlr = dlData.getDLRTrace();
			if ( dlr.length() > 0 ){
				dlr = "AR: " + dlr + "\n";
			}
			cell.setToolTip(
				dlr +
				"TRACE:\n" + dlData.getTrace());
		} else {
			cell.setToolTip(null);
		}
	}
}
