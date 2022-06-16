/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.views.table.utils;

import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.Map;

import com.biglybt.core.util.Debug;
import com.biglybt.core.util.LightHashMap;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadTypeComplete;
import com.biglybt.pif.download.DownloadTypeIncomplete;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnCreationListener;
import com.biglybt.plugin.net.buddy.swt.columns.ColumnChatStatus;
import com.biglybt.ui.swt.views.columnsetup.ColumnTC_ChosenColumn;
import com.biglybt.ui.swt.views.columnsetup.ColumnTC_NameInfo;
import com.biglybt.ui.swt.views.columnsetup.ColumnTC_Sample;
import com.biglybt.ui.swt.views.tableitems.mytorrents.*;

import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableColumnCoreCreationListener;
import com.biglybt.ui.common.table.impl.TableColumnManager;

/**
 * @author TuxPaper
 * @created Dec 19, 2007
 *
 */
public class TableColumnCreator
{
	public static int DATE_COLUMN_WIDTH = 110;

	public static TableColumnCore[] createIncompleteDM(String tableID) {
		final String[] defaultVisibleOrder = {
			HealthItem.COLUMN_ID,
			RankItem.COLUMN_ID,
			NameItem.COLUMN_ID,
			"TorrentStream",
			"azsubs.ui.column.subs",
			"azbuddy.ui.column.msgpending",
			"RatingColumn",
			"Info",
			CommentIconItem.COLUMN_ID,
			SizeItem.COLUMN_ID,
			DownItem.COLUMN_ID,
			DoneItem.COLUMN_ID,
			StatusItem.COLUMN_ID,
			SeedsItem.COLUMN_ID,
			PeersItem.COLUMN_ID,
			DownSpeedItem.COLUMN_ID,
			UpSpeedItem.COLUMN_ID,
			ETAItem.COLUMN_ID,
			ShareRatioItem.COLUMN_ID,
			TrackerStatusItem.COLUMN_ID,
		};

		TableColumnManager tcManager = TableColumnManager.getInstance();
		Map mapTCs = tcManager.getTableColumnsAsMap(DownloadTypeIncomplete.class, tableID);

		tcManager.setDefaultColumnNames(tableID, defaultVisibleOrder);

		if (!tcManager.loadTableColumnSettings(DownloadTypeIncomplete.class,
				tableID)
				|| areNoneVisible(mapTCs)) {
			setVisibility(mapTCs, defaultVisibleOrder);
			RankItem tc = (RankItem) mapTCs.get(RankItem.COLUMN_ID);
			if (tc != null) {
				tcManager.setDefaultSortColumnName(tableID, RankItem.COLUMN_ID);
				tc.setSortAscending(true);
			}
		}

		return (TableColumnCore[]) mapTCs.values().toArray(new TableColumnCore[0]);
	}

	public static TableColumnCore[] createCompleteDM(String tableID) {
		final String[] defaultVisibleOrder = {
			HealthItem.COLUMN_ID,
			RankItem.COLUMN_ID,
			"SeedingRank",
			NameItem.COLUMN_ID,
			"azsubs.ui.column.subs",
			"azbuddy.ui.column.msgpending",
			"RatingColumn",
			"Info",
			"RateIt",
			CommentIconItem.COLUMN_ID,
			SizeItem.COLUMN_ID,
			StatusItem.COLUMN_ID,
			SeedsItem.COLUMN_ID,
			PeersItem.COLUMN_ID,
			UpSpeedItem.COLUMN_ID,
			ShareRatioItem.COLUMN_ID,
			UpItem.COLUMN_ID,
			TrackerStatusItem.COLUMN_ID,
		};

		TableColumnManager tcManager = TableColumnManager.getInstance();
		Map mapTCs = tcManager.getTableColumnsAsMap(DownloadTypeComplete.class, tableID);

		tcManager.setDefaultColumnNames(tableID, defaultVisibleOrder);

		if (!tcManager.loadTableColumnSettings(DownloadTypeComplete.class,
				tableID)
				|| areNoneVisible(mapTCs)) {
			setVisibility(mapTCs, defaultVisibleOrder);

			RankItem tc = (RankItem) mapTCs.get(RankItem.COLUMN_ID);
			if (tc != null) {
				tcManager.setDefaultSortColumnName(tableID, RankItem.COLUMN_ID);
				tc.setSortAscending(true);
			}
		}

		return (TableColumnCore[]) mapTCs.values().toArray(new TableColumnCore[0]);
	}

	public static TableColumnCore[] createAllDM(String tableID) {
		final String[] defaultVisibleOrder = {
				HealthItem.COLUMN_ID,
				RankItem.COLUMN_ID,
				"SeedingRank",
				NameItem.COLUMN_ID,
				"TorrentStream",
				"azsubs.ui.column.subs",
				"azbuddy.ui.column.msgpending",
				"RatingColumn",
				"Info",
				"RateIt",
				CommentIconItem.COLUMN_ID,
				SizeItem.COLUMN_ID,
				DownItem.COLUMN_ID,
				DoneItem.COLUMN_ID,
				StatusItem.COLUMN_ID,
				SeedsItem.COLUMN_ID,
				PeersItem.COLUMN_ID,
				DownSpeedItem.COLUMN_ID,
				UpSpeedItem.COLUMN_ID,
				ETAItem.COLUMN_ID,
				ShareRatioItem.COLUMN_ID,
				TrackerStatusItem.COLUMN_ID,
		};

		TableColumnManager tcManager = TableColumnManager.getInstance();
		Map mapTCs = tcManager.getTableColumnsAsMap(Download.class, tableID);

		tcManager.setDefaultColumnNames(tableID, defaultVisibleOrder);

		if (!tcManager.loadTableColumnSettings(Download.class,
				tableID)
				|| areNoneVisible(mapTCs)) {
			setVisibility(mapTCs, defaultVisibleOrder);

			RankItem tc = (RankItem) mapTCs.get(RankItem.COLUMN_ID);
			if (tc != null) {
				tcManager.setDefaultSortColumnName(tableID, RankItem.COLUMN_ID);
				tc.setSortAscending(true);
			}
		}

		return (TableColumnCore[]) mapTCs.values().toArray(new TableColumnCore[0]);
	}
	
	private static boolean areNoneVisible(Map mapTCs) {
		boolean noneVisible = true;
		for (Iterator iter = mapTCs.values().iterator(); iter.hasNext();) {
			TableColumn tc = (TableColumn) iter.next();
			if (tc.isVisible()) {
				noneVisible = false;
				break;
			}
		}
		return noneVisible;
	}

	private static void setVisibility(Map mapTCs, String[] defaultVisibleOrder) {
		for (Iterator iter = mapTCs.values().iterator(); iter.hasNext();) {
			TableColumnCore tc = (TableColumnCore) iter.next();
			Long force_visible = (Long)tc.getUserData( TableColumn.UD_FORCE_VISIBLE );
			if ( force_visible == null || force_visible==0 ){

				tc.setVisible(false);
			}
		}

		for (int i = 0; i < defaultVisibleOrder.length; i++) {
			String id = defaultVisibleOrder[i];
			TableColumnCore tc = (TableColumnCore) mapTCs.get(id);
			if (tc != null) {
				tc.setVisible(true);
				tc.setPositionNoShift(i);
			}
		}
	}

	/**
	 *
	 *
	 * @since 3.1.1.1
	 */
	public static void initCoreColumns() {
		// short variable names to reduce wrapping
		final Map<String, cInfo> c = new LightHashMap(50);
		final Class tc = TableColumn.class;

		c.put(RankItem.COLUMN_ID, new cInfo(RankItem.class, RankItem.DATASOURCE_TYPE));
		c.put(NameItem.COLUMN_ID, new cInfo(NameItem.class, NameItem.DATASOURCE_TYPE));
		c.put(SizeItem.COLUMN_ID, new cInfo(SizeItem.class, SizeItem.DATASOURCE_TYPE));
		c.put(ColumnSizeWithDND.COLUMN_ID, new cInfo(ColumnSizeWithDND.class, ColumnSizeWithDND.DATASOURCE_TYPE));
		c.put(DoneItem.COLUMN_ID, new cInfo(DoneItem.class, DoneItem.DATASOURCE_TYPE));
		c.put(DoneBytesItem.COLUMN_ID, new cInfo(DoneBytesItem.class, DoneItem.DATASOURCE_TYPE));
		c.put(ColumnDoneWithDND.COLUMN_ID, new cInfo(ColumnDoneWithDND.class, ColumnDoneWithDND.DATASOURCE_TYPE));
		c.put(StatusItem.COLUMN_ID, new cInfo(StatusItem.class, StatusItem.DATASOURCE_TYPE));
		c.put(ETAItem.COLUMN_ID, new cInfo(ETAItem.class, ETAItem.DATASOURCE_TYPE));
		c.put(HealthItem.COLUMN_ID, new cInfo(HealthItem.class, HealthItem.DATASOURCE_TYPE));
		c.put(CommentIconItem.COLUMN_ID, new cInfo(CommentIconItem.class, CommentIconItem.DATASOURCE_TYPE));
		c.put(DownItem.COLUMN_ID, new cInfo(DownItem.class, DownItem.DATASOURCE_TYPE));
		c.put(SeedsItem.COLUMN_ID, new cInfo(SeedsItem.class, SeedsItem.DATASOURCE_TYPE));
		c.put(PeersItem.COLUMN_ID, new cInfo(PeersItem.class, PeersItem.DATASOURCE_TYPE));
		c.put(DownSpeedItem.COLUMN_ID, new cInfo(DownSpeedItem.class, DownSpeedItem.DATASOURCE_TYPE));
		c.put(UpSpeedItem.COLUMN_ID, new cInfo(UpSpeedItem.class, UpSpeedItem.DATASOURCE_TYPE));
		c.put(UpSpeedLimitItem.COLUMN_ID, new cInfo(UpSpeedLimitItem.class, UpSpeedLimitItem.DATASOURCE_TYPE));
		c.put(TrackerStatusItem.COLUMN_ID, new cInfo(TrackerStatusItem.class, TrackerStatusItem.DATASOURCE_TYPE));
		c.put(CompletedItem.COLUMN_ID, new cInfo(CompletedItem.class, CompletedItem.DATASOURCE_TYPE));
		c.put(ShareRatioItem.COLUMN_ID, new cInfo(ShareRatioItem.class, ShareRatioItem.DATASOURCE_TYPE));
		c.put(ShareRatioProgressItem.COLUMN_ID, new cInfo(ShareRatioProgressItem.class, ShareRatioProgressItem.DATASOURCE_TYPE));
		c.put(UpItem.COLUMN_ID, new cInfo(UpItem.class, UpItem.DATASOURCE_TYPE));
		c.put(RemainingItem.COLUMN_ID, new cInfo(RemainingItem.class, RemainingItem.DATASOURCE_TYPE));
		c.put(PiecesItem.COLUMN_ID, new cInfo(PiecesItem.class, PiecesItem.DATASOURCE_TYPE));
		c.put(CompletionItem.COLUMN_ID, new cInfo(CompletionItem.class, CompletionItem.DATASOURCE_TYPE));
		c.put(CommentItem.COLUMN_ID, new cInfo(CommentItem.class, CommentItem.DATASOURCE_TYPE));
		c.put(MaxUploadsItem.COLUMN_ID, new cInfo(MaxUploadsItem.class, MaxUploadsItem.DATASOURCE_TYPE));
		c.put(TotalSpeedItem.COLUMN_ID, new cInfo(TotalSpeedItem.class, TotalSpeedItem.DATASOURCE_TYPE));
		c.put(FilesDoneItem.COLUMN_ID, new cInfo(FilesDoneItem.class, FilesDoneItem.DATASOURCE_TYPE));
		c.put(FilesLinkedItem.COLUMN_ID, new cInfo(FilesLinkedItem.class, FilesLinkedItem.DATASOURCE_TYPE));
		c.put(FileExtensionItem.COLUMN_ID, new cInfo(FileExtensionItem.class, FileExtensionItem.DATASOURCE_TYPE));
		c.put(SavePathItem.COLUMN_ID, new cInfo(SavePathItem.class, SavePathItem.DATASOURCE_TYPE));
		c.put(TorrentPathItem.COLUMN_ID, new cInfo(TorrentPathItem.class, TorrentPathItem.DATASOURCE_TYPE));
		c.put(CategoryItem.COLUMN_ID, new cInfo(CategoryItem.class, CategoryItem.DATASOURCE_TYPE));
		c.put(TagsItem.COLUMN_ID, new cInfo(TagsItem.class, TagsItem.DATASOURCE_TYPE));
		c.put(TagColorsItem.COLUMN_ID, new cInfo(TagColorsItem.class, TagColorsItem.DATASOURCE_TYPE));
		c.put(TagIconsItem.COLUMN_ID, new cInfo(TagIconsItem.class, TagIconsItem.DATASOURCE_TYPE));
		c.put(CategoryIconsItem.COLUMN_ID, new cInfo(CategoryIconsItem.class, CategoryIconsItem.DATASOURCE_TYPE));
		c.put(TagAddedToDateItem.COLUMN_ID, new cInfo(TagAddedToDateItem.class, TagAddedToDateItem.DATASOURCE_TYPE));
		c.put(NetworksItem.COLUMN_ID, new cInfo(NetworksItem.class, NetworksItem.DATASOURCE_TYPE));
		c.put(PeerSourcesItem.COLUMN_ID, new cInfo(PeerSourcesItem.class, PeerSourcesItem.DATASOURCE_TYPE));
		c.put(AvailabilityItem.COLUMN_ID, new cInfo(AvailabilityItem.class, AvailabilityItem.DATASOURCE_TYPE));
		c.put(AvgAvailItem.COLUMN_ID, new cInfo(AvgAvailItem.class, AvgAvailItem.DATASOURCE_TYPE));
		c.put(SecondsSeedingItem.COLUMN_ID, new cInfo(SecondsSeedingItem.class, SecondsSeedingItem.DATASOURCE_TYPE));
		c.put(SecondsDownloadingItem.COLUMN_ID, new cInfo(SecondsDownloadingItem.class, SecondsDownloadingItem.DATASOURCE_TYPE));
		c.put(TimeSinceDownloadItem.COLUMN_ID, new cInfo(TimeSinceDownloadItem.class, TimeSinceDownloadItem.DATASOURCE_TYPE));
		c.put(TimeSinceUploadItem.COLUMN_ID, new cInfo(TimeSinceUploadItem.class, TimeSinceUploadItem.DATASOURCE_TYPE));
		c.put(OnlyCDing4Item.COLUMN_ID, new cInfo(OnlyCDing4Item.class, OnlyCDing4Item.DATASOURCE_TYPE));
		c.put(TrackerNextAccessItem.COLUMN_ID, new cInfo(TrackerNextAccessItem.class, TrackerNextAccessItem.DATASOURCE_TYPE));
		c.put(TrackerNameItem.COLUMN_ID, new cInfo(TrackerNameItem.class, TrackerNameItem.DATASOURCE_TYPE));
		c.put(SeedToPeerRatioItem.COLUMN_ID, new cInfo(SeedToPeerRatioItem.class, SeedToPeerRatioItem.DATASOURCE_TYPE));
		c.put(DownSpeedLimitItem.COLUMN_ID, new cInfo(DownSpeedLimitItem.class, DownSpeedLimitItem.DATASOURCE_TYPE));
		c.put(SwarmAverageSpeed.COLUMN_ID, new cInfo(SwarmAverageSpeed.class, SwarmAverageSpeed.DATASOURCE_TYPE));
		c.put(SwarmAverageCompletion.COLUMN_ID, new cInfo(SwarmAverageCompletion.class, SwarmAverageCompletion.DATASOURCE_TYPE));
		c.put(SwarmMaxCompletion.COLUMN_ID, new cInfo(SwarmMaxCompletion.class, SwarmMaxCompletion.DATASOURCE_TYPE));
		c.put(LeecherMaxCompletion.COLUMN_ID, new cInfo(LeecherMaxCompletion.class, LeecherMaxCompletion.DATASOURCE_TYPE));
		c.put(BadAvailTimeItem.COLUMN_ID, new cInfo(BadAvailTimeItem.class, BadAvailTimeItem.DATASOURCE_TYPE));
		c.put(ColumnFileCount.COLUMN_ID, new cInfo(ColumnFileCount.class, ColumnFileCount.DATASOURCE_TYPE));
		c.put(ColumnTorrentSpeed.COLUMN_ID, new cInfo(ColumnTorrentSpeed.class, ColumnTorrentSpeed.DATASOURCE_TYPE));
		c.put(MoveOnCompleteItem.COLUMN_ID, new cInfo(MoveOnCompleteItem.class, MoveOnCompleteItem.DATASOURCE_TYPE));

		c.put(DateCompletedItem.COLUMN_ID, new cInfo(DateCompletedItem.class, DateCompletedItem.DATASOURCE_TYPE));
		c.put(DateFileCompletedItem.COLUMN_ID, new cInfo(DateFileCompletedItem.class, DateFileCompletedItem.DATASOURCE_TYPE));
		c.put(DateAddedItem.COLUMN_ID, new cInfo(DateAddedItem.class, DateAddedItem.DATASOURCE_TYPE));
		c.put(DateLastActiveItem.COLUMN_ID, new cInfo(DateLastActiveItem.class, DateLastActiveItem.DATASOURCE_TYPE));
		
		c.put(IPFilterItem.COLUMN_ID, new cInfo(IPFilterItem.class, IPFilterItem.DATASOURCE_TYPE));
		c.put(AlertsItem.COLUMN_ID, new cInfo(AlertsItem.class, AlertsItem.DATASOURCE_TYPE));
		c.put(TorrentCreateDateItem.COLUMN_ID, new cInfo(TorrentCreateDateItem.class, TorrentCreateDateItem.DATASOURCE_TYPE));

		c.put(ColumnTC_NameInfo.COLUMN_ID, new cInfo(ColumnTC_NameInfo.class, tc));
		c.put(ColumnTC_Sample.COLUMN_ID, new cInfo(ColumnTC_Sample.class, tc));
		c.put(ColumnTC_ChosenColumn.COLUMN_ID, new cInfo(ColumnTC_ChosenColumn.class, tc));

		c.put(PeakUpItem.COLUMN_ID, new cInfo(PeakUpItem.class, PeakUpItem.DATASOURCE_TYPE));
		c.put(PeakDownItem.COLUMN_ID, new cInfo(PeakDownItem.class, PeakDownItem.DATASOURCE_TYPE));
		c.put(SmoothedUpItem.COLUMN_ID, new cInfo(SmoothedUpItem.class, SmoothedUpItem.DATASOURCE_TYPE));
		c.put(SmoothedDownItem.COLUMN_ID, new cInfo(SmoothedDownItem.class, SmoothedDownItem.DATASOURCE_TYPE));
		c.put(SmoothedETAItem.COLUMN_ID, new cInfo(SmoothedETAItem.class, SmoothedETAItem.DATASOURCE_TYPE));
		c.put(MinSRItem.COLUMN_ID, new cInfo(MinSRItem.class, MinSRItem.DATASOURCE_TYPE));
		c.put(MaxSRItem.COLUMN_ID, new cInfo(MaxSRItem.class, MaxSRItem.DATASOURCE_TYPE));

		c.put(SessionUpItem.COLUMN_ID, new cInfo(SessionUpItem.class, SessionUpItem.DATASOURCE_TYPE));
		c.put(SessionDownItem.COLUMN_ID, new cInfo(SessionDownItem.class, SessionDownItem.DATASOURCE_TYPE));
		c.put(MergedDataItem.COLUMN_ID, new cInfo(MergedDataItem.class, MergedDataItem.DATASOURCE_TYPE));

		c.put(DescriptionItem.COLUMN_ID, new cInfo(DescriptionItem.class, DescriptionItem.DATASOURCE_TYPE));

		c.put(SuperSeedingItem.COLUMN_ID, new cInfo(SuperSeedingItem.class, SuperSeedingItem.DATASOURCE_TYPE));

		c.put(DiskLatencyItem.COLUMN_ID, new cInfo(DiskLatencyItem.class, DiskLatencyItem.DATASOURCE_TYPE));
		c.put(UploadHealthItem.COLUMN_ID, new cInfo(UploadHealthItem.class, UploadHealthItem.DATASOURCE_TYPE));
		c.put(DownloadHealthItem.COLUMN_ID, new cInfo(DownloadHealthItem.class, DownloadHealthItem.DATASOURCE_TYPE));
		c.put(ConnectHealthItem.COLUMN_ID, new cInfo(ConnectHealthItem.class, ConnectHealthItem.DATASOURCE_TYPE));
		c.put(SwarmTagsItem.COLUMN_ID, new cInfo(SwarmTagsItem.class, SwarmTagsItem.DATASOURCE_TYPE));
		c.put(MaskedItem.COLUMN_ID, new cInfo(MaskedItem.class, MaskedItem.DATASOURCE_TYPE));
		c.put(UploadPriorityItem.COLUMN_ID, new cInfo(UploadPriorityItem.class, UploadPriorityItem.DATASOURCE_TYPE));

		c.put(	PrivateItem.COLUMN_ID, 
				new cInfo(
					PrivateItem.class, 
					PrivateItem.DATASOURCE_TYPE,
					new TableColumnCreationListener() {
						@Override
						public void tableColumnCreated(TableColumn column) {
							new PrivateItem(column);
						}
					}));

		
		// Core columns are implementors of TableColumn to save one class creation
		// Otherwise, we'd have to create a generic TableColumnImpl class, pass it
		// to another class so that it could manipulate it and act upon changes.

		TableColumnManager tcManager = TableColumnManager.getInstance();

		TableColumnCoreCreationListener tcCreator = new TableColumnCoreCreationListener() {
			// @see com.biglybt.ui.swt.views.table.TableColumnCoreCreationListener#createTableColumnCore(java.lang.Class, java.lang.String, java.lang.String)
			@Override
			public TableColumnCore createTableColumnCore(Class forDataSourceType,
			                                             String tableID, String columnID) {
				cInfo info = (cInfo) c.get(columnID);

				if ( info.listener == null ){
					try {
						Constructor constructor = info.cla.getDeclaredConstructor(new Class[] {
							String.class
						});
						TableColumnCore column = (TableColumnCore) constructor.newInstance(new Object[] {
							tableID
						});
						return column;
					} catch (Exception e) {
						Debug.out(e);
					}
				}
				return null;
			}

			@Override
			public void tableColumnCreated(TableColumn column) {
				cInfo info = (cInfo) c.get(column.getName());
				if ( info.listener != null ){
					info.listener.tableColumnCreated(column);
				}
			}
		};

		for (Iterator<String> iter = c.keySet().iterator(); iter.hasNext();) {
			String id = iter.next();
			cInfo info = c.get(id);

			tcManager.registerColumn(info.forDataSourceType, id, tcCreator);
		}

	}

	private static class cInfo {
		public Class cla;
		public Class forDataSourceType;
		public TableColumnCreationListener	listener;

		public cInfo(Class cla, Class forDataSourceType) {
			this.cla = cla;
			this.forDataSourceType = forDataSourceType;
		}
		
		public cInfo(Class cla, Class forDataSourceType, TableColumnCreationListener l) {
			this.cla = cla;
			this.forDataSourceType = forDataSourceType;
			this.listener = l;
		}
		
	}
}
