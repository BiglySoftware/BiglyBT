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

import com.biglybt.core.download.DownloadManager;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadTypeComplete;
import com.biglybt.pif.download.DownloadTypeIncomplete;

import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.views.MyTorrentsView;

import com.biglybt.core.torrent.PlatformTorrentUtils;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.common.table.TableView;

public class MyTorrentsView_Big
	extends MyTorrentsView
{
	private final int torrentFilterMode;

	public MyTorrentsView_Big(Core _core, int torrentFilterMode,
			Object dataSource, TableColumnCore[] basicItems, BubbleTextBox txtFilter ){
		super( true );
		this.torrentFilterMode = torrentFilterMode;
		this.filterBox = txtFilter;
		Class<?> forDataSourceType;
		switch (torrentFilterMode) {
			case SBC_LibraryView.TORRENTS_COMPLETE:
			case SBC_LibraryView.TORRENTS_UNOPENED:
				forDataSourceType = DownloadTypeComplete.class;
				break;

			case SBC_LibraryView.TORRENTS_INCOMPLETE:
				forDataSourceType = DownloadTypeIncomplete.class;
				break;

			default:
				forDataSourceType = Download.class;
				break;
		}

		TableView<DownloadManager> tv = init(_core,
				SB_Transfers.getTableIdFromFilterMode(torrentFilterMode, true,
						dataSource),
				forDataSourceType, basicItems);
		tv.setRowDefaultHeightEM(2);
	}


	@Override
	public boolean isOurDownloadManager(DownloadManager dm) {
		if (PlatformTorrentUtils.isAdvancedViewOnly(dm)) {
			return false;
		}

		if (torrentFilterMode == SBC_LibraryView.TORRENTS_UNOPENED) {
			if (PlatformTorrentUtils.getHasBeenOpened(dm)) {
				return false;
			}
		} else if (torrentFilterMode == SBC_LibraryView.TORRENTS_ALL) {
			if ( !isInCurrentTag(dm)){
				return(false );
			}
			return( isInCurrentTag(dm));
		}

		return super.isOurDownloadManager(dm);
	}

	// @see com.biglybt.ui.swt.views.MyTorrentsView#defaultSelected(TableRowCore[])
	@Override
	public void defaultSelected(TableRowCore[] rows, int stateMask, int origin ) {
		boolean neverPlay = DownloadTypeIncomplete.class.equals(getForDataSourceType());
		SBC_LibraryTableView.doDefaultClick(rows, stateMask, neverPlay, origin );
	}
}
