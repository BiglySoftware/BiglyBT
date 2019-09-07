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

package com.biglybt.ui.mdi;

import java.util.List;
import java.util.Map;

import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;

public interface MultipleDocumentInterface
{
	public static final String SIDEBAR_POS_FIRST = "";

	public static final String SIDEBAR_HEADER_VUZE = "header.vuze";

	public static final String SIDEBAR_HEADER_DASHBOARD = "header.dashboard";

	public static final String SIDEBAR_HEADER_TRANSFERS = "header.transfers";

	public static final String SIDEBAR_HEADER_DISCOVERY = "header.discovery";

	public static final String SIDEBAR_HEADER_DEVICES = "header.devices";

	public static final String SIDEBAR_HEADER_PLUGINS = "header.plugins";

	
	public static final String[] SIDEBAR_HEADER_ORDER_DEFAULT = {
			SIDEBAR_HEADER_DASHBOARD,
			SIDEBAR_HEADER_TRANSFERS,
			SIDEBAR_HEADER_VUZE,
			SIDEBAR_HEADER_DISCOVERY,
			SIDEBAR_HEADER_DEVICES,
			SIDEBAR_HEADER_PLUGINS,
	};
	
	
	public static final String SIDEBAR_SECTION_PLUGINS = "Plugins";

	public static final String SIDEBAR_SECTION_LIBRARY = "Library";

	public static final String SIDEBAR_SECTION_BETAPROGRAM = "BetaProgramme";

	public static final String SIDEBAR_SECTION_LIBRARY_DL = "LibraryDL";

	public static final String SIDEBAR_SECTION_LIBRARY_CD = "LibraryCD";

	public static final String SIDEBAR_SECTION_TAGS = "TagsOverview";

	public static final String SIDEBAR_SECTION_TAG_DISCOVERY = "TagDiscovery";

	public static final String SIDEBAR_SECTION_CHAT = "ChatOverview";

	public static final String SIDEBAR_SECTION_LIBRARY_UNOPENED = "LibraryUnopened";

	public static final String SIDEBAR_SECTION_TORRENT_DETAILS = "DMDetails";

	public static final String SIDEBAR_SECTION_WELCOME = "Welcome";

	public static final String SIDEBAR_SECTION_SUBSCRIPTIONS = "Subscriptions";

	public static final String SIDEBAR_SECTION_DEVICES = "Devices";

	public static final String SIDEBAR_SECTION_ACTIVITIES = "Activity";

	public static final String SIDEBAR_SECTION_SEARCH = "Search";

	public static final String SIDEBAR_SECTION_ALLPEERS = "AllPeersView";

	public static final String SIDEBAR_SECTION_TORRENT_OPTIONS = "TorrentOptionsView";

	public static final String SIDEBAR_SECTION_MY_SHARES = "MySharesView";

	public static final String SIDEBAR_SECTION_MY_TRACKER = "MyTrackerView";

	public static final String SIDEBAR_SECTION_CLIENT_STATS = "ClientStatsView";

	public static final String SIDEBAR_SECTION_LOGGER = "LoggerView";

	public static final String SIDEBAR_SECTION_CONFIG = "ConfigView";

	public static final String SIDEBAR_SECTION_ARCHIVED_DOWNLOADS = "ArchivedDownloads";

	public static final String SIDEBAR_SECTION_DOWNLOAD_HISTORY = "DownloadHistory";
	
	public static final String SIDEBAR_SECTION_ALL_TRACKERS = "AllTrackers";

	public static final String SIDEBAR_SECTION_LIBRARY_CAT_INSTANCES	= "Cat.";
	public static final String SIDEBAR_SECTION_LIBRARY_TAG_INSTANCES	= "Tag.";
	
	public static final String[] SIDEBAR_TRANSFERS_SECTION_ORDER = {
			SIDEBAR_SECTION_LIBRARY,
			SIDEBAR_SECTION_LIBRARY_DL,
			SIDEBAR_SECTION_LIBRARY_CD,
			SIDEBAR_SECTION_LIBRARY_UNOPENED,
			SIDEBAR_SECTION_LIBRARY_CAT_INSTANCES,
			SIDEBAR_SECTION_LIBRARY_TAG_INSTANCES,
			SIDEBAR_SECTION_TAGS,
			SIDEBAR_SECTION_ALL_TRACKERS,
			SIDEBAR_SECTION_ALLPEERS,
			SIDEBAR_SECTION_MY_TRACKER,
			SIDEBAR_SECTION_MY_SHARES,
			SIDEBAR_SECTION_ARCHIVED_DOWNLOADS,
			SIDEBAR_SECTION_DOWNLOAD_HISTORY,
			SIDEBAR_SECTION_TORRENT_OPTIONS,
			SIDEBAR_SECTION_TORRENT_DETAILS,
	};
	
	
	public boolean showEntryByID(String id);

	public boolean showEntryByID(String id, Object datasource);

		/**
		 * If you prefix the 'preferedAfterID' string with '~' then the operation will actually
		 * switch to 'preferedBeforeID'
		 * @param parentID
		 * @param id
		 * @param configID
		 * @param title
		 * @param titleInfo
		 * @param params
		 * @param closeable
		 * @param preferedAfterID
		 * @return
		 */
	public MdiEntry createEntryFromSkinRef(String parentID, String id,
			String configID, String title, ViewTitleInfo titleInfo, Object params,
			boolean closeable, String preferedAfterID);

	public MdiEntry getCurrentEntry();

	public MdiEntry getEntry(String id);

	public void addListener(MdiListener l);

	public void removeListener(MdiListener l);

	public void addListener(MdiEntryLoadedListener l);

	public void removeListener(MdiEntryLoadedListener l);

	public boolean isVisible();

	public void closeEntry(String id);

	public MdiEntry[] getEntries();

	public void registerEntry(String id, MdiEntryCreationListener l);

	public void registerEntry(String id,
			MdiEntryCreationListener2 mdiEntryCreationListener2);

	public void deregisterEntry(String id, MdiEntryCreationListener l);

	public void deregisterEntry(String id,
			MdiEntryCreationListener2 mdiEntryCreationListener2);

	public boolean entryExists(String id);

	public void removeItem(MdiEntry entry);

	/**
	 * When an entry can not be opened (ie. creation listener isn't registered yet),
	 * call this to store your open request
	 */
	public void setEntryAutoOpen(String id, Object datasource);

	public void removeEntryAutoOpen(String id);

	public void showEntry(MdiEntry newEntry);

	public void informAutoOpenSet(MdiEntry entry, Map<String, Object> autoOpenInfo);

	public boolean loadEntryByID(String id, boolean activate);

	public void setPreferredOrder(String[] preferredOrder);

	public String[] getPreferredOrder();

	public MdiEntry createHeader(String id, String title, String preferredAfterID);

	public List<MdiEntry> getChildrenOf(String id);

	public boolean loadEntryByID(String id, boolean activate,
			boolean onlyLoadOnce, Object datasource);

	/**
	 * @return
	 * @since 5.6.0.1
	 */
	public int getEntriesCount();

	public boolean isInitialized();
	
	public boolean isDisposed();

}
