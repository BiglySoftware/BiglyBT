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

package com.biglybt.ui.swt.columns.utils;

import java.lang.reflect.Constructor;
import java.util.*;

import com.biglybt.activities.ActivitiesEntry;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.LightHashMap;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadTypeComplete;
import com.biglybt.pif.download.DownloadTypeIncomplete;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.plugin.net.buddy.swt.columns.ColumnChatMessageCount;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableColumnCoreCreationListener;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.swt.columns.peer.ColumnPeerBoost;
import com.biglybt.ui.swt.columns.torrent.*;
import com.biglybt.ui.swt.columns.vuzeactivity.*;
import com.biglybt.ui.swt.views.table.utils.TableColumnCreator;
import com.biglybt.ui.swt.views.tableitems.mytorrents.*;

/**
 * A utility class for creating some common column sets; this is a virtual clone of <code>TableColumnCreator</code>
 * with slight modifications
 * @author khai
 *
 */
public class TableColumnCreatorV3
{
	/**
	 * @since 4.0.0.1
	 */
	public static TableColumnCore[] createAllDM(String tableID, boolean big) {
		final String[] oldVisibleOrder = {
			ColumnUnopened.COLUMN_ID,
			ColumnThumbAndName.COLUMN_ID,
			ColumnStream.COLUMN_ID,
			SizeItem.COLUMN_ID,
			ColumnProgressETA.COLUMN_ID,
			"azsubs.ui.column.subs",
			"azbuddy.ui.column.msgpending",
			StatusItem.COLUMN_ID,
			ColumnTorrentSpeed.COLUMN_ID,
			SeedsItem.COLUMN_ID,
			PeersItem.COLUMN_ID,
			ShareRatioItem.COLUMN_ID
		};
		final String[] defaultVisibleOrder = {
			RankItem.COLUMN_ID,
			ColumnThumbAndName.COLUMN_ID,
			ColumnStream.COLUMN_ID,
			ColumnProgressETA.COLUMN_ID,
			SizeItem.COLUMN_ID,
			ColumnTorrentSpeed.COLUMN_ID,
			ETAItem.COLUMN_ID,
			//DateCompletedItem.COLUMN_ID,
			"RatingColumn",
			"azsubs.ui.column.subs",
			"azbuddy.ui.column.msgpending",
			DateAddedItem.COLUMN_ID
		};

		TableColumnManager tcManager = TableColumnManager.getInstance();
		Map<String, TableColumnCore> mapTCs = tcManager.getTableColumnsAsMap(
				Download.class, tableID);

		tcManager.setDefaultColumnNames(tableID, defaultVisibleOrder);

		if (!tcManager.loadTableColumnSettings(Download.class, tableID)
				|| areNoneVisible(mapTCs)) {
			setVisibility(mapTCs, defaultVisibleOrder, true);
			RankItem tc = (RankItem) mapTCs.get(RankItem.COLUMN_ID);
			if (tc != null) {
				tcManager.setDefaultSortColumnName(tableID, RankItem.COLUMN_ID);
				tc.setSortAscending(true);
			}
		} else {
			upgradeColumns(oldVisibleOrder, defaultVisibleOrder, mapTCs);
		}

		// special changes
		StatusItem tcStatusItem = (StatusItem) mapTCs.get(StatusItem.COLUMN_ID);
		if (tcStatusItem != null) {
			tcStatusItem.setChangeRowFG(false);
			if (big) {
				tcStatusItem.setChangeCellFG(false);
				tcStatusItem.setShowTrackerErrors(true);
			}
		}
		if (big) {
			ShareRatioItem tcShareRatioItem = (ShareRatioItem) mapTCs.get(ShareRatioItem.COLUMN_ID);
			if (tcShareRatioItem != null) {
				tcShareRatioItem.setChangeFG(false);
			}
		}

		return mapTCs.values().toArray(new TableColumnCore[0]);
	}

	private static void upgradeColumns(String[] oldOrder, String[] newOrder,
			Map<String, TableColumnCore> mapTCs) {
		List<String> listCurrentOrder = new ArrayList<>();

		for (TableColumnCore tc : mapTCs.values()) {
			if (tc.isVisible()) {
				listCurrentOrder.add(tc.getName());
			}
		}

		if (oldOrder.length == listCurrentOrder.size()) {
			List<String> listOldOrder = Arrays.asList(oldOrder);
			if (listOldOrder.containsAll(listCurrentOrder)) {
				// exactly the same items (perhaps in different order) -- upgrade to new list
				//System.out.println("upgradeColumns: SAME -> UPGRADING!");
				setVisibility(mapTCs, newOrder, true);
			}
		} else if (listCurrentOrder.size() > oldOrder.length) {
			List<String> listNewOrder = Arrays.asList(newOrder);
			if (listCurrentOrder.containsAll(listNewOrder)) {
				//System.out.println("upgradeColumns: has all old plus -> UPGRADING!");
				// Current visible has all of old order, plus some added ones
				// make all new columns visible (keep old ones visible too)
				for (String id : newOrder) {
					TableColumnCore tc = mapTCs.get(id);
					if (tc != null) {
						tc.setVisible(true);
					}
				}
			}
		}
	}

	public static TableColumnCore[] createIncompleteDM(String tableID, boolean big) {
		final String[] oldVisibleOrder = {
			ColumnThumbAndName.COLUMN_ID,
			ColumnStream.COLUMN_ID,
			SizeItem.COLUMN_ID,
			ColumnFileCount.COLUMN_ID,
			ColumnProgressETA.COLUMN_ID,
			SeedsItem.COLUMN_ID,
			PeersItem.COLUMN_ID,
			"azsubs.ui.column.subs",
			"azbuddy.ui.column.msgpending",
		};
		final String[] defaultVisibleOrder = {
			RankItem.COLUMN_ID,
			ColumnThumbAndName.COLUMN_ID,
			ColumnStream.COLUMN_ID,
			ColumnProgressETA.COLUMN_ID,
			SizeItem.COLUMN_ID,
			ColumnTorrentSpeed.COLUMN_ID,
			ETAItem.COLUMN_ID,
			"RatingColumn",
			"azsubs.ui.column.subs",
			"azbuddy.ui.column.msgpending",
			DateAddedItem.COLUMN_ID
		};

		TableColumnManager tcManager = TableColumnManager.getInstance();
		Map<String, TableColumnCore> mapTCs = tcManager.getTableColumnsAsMap(
				DownloadTypeIncomplete.class, tableID);

		tcManager.setDefaultColumnNames(tableID, defaultVisibleOrder);

		if (!tcManager.loadTableColumnSettings(DownloadTypeIncomplete.class,
				tableID) || areNoneVisible(mapTCs)) {
			setVisibility(mapTCs, defaultVisibleOrder, true);
			RankItem tc = (RankItem) mapTCs.get(RankItem.COLUMN_ID);
			if (tc != null) {
				tcManager.setDefaultSortColumnName(tableID, RankItem.COLUMN_ID);
				tc.setSortAscending(true);
			}
		} else {
			upgradeColumns(oldVisibleOrder, defaultVisibleOrder, mapTCs);
		}

		// special changes
		StatusItem tcStatusItem = (StatusItem) mapTCs.get(StatusItem.COLUMN_ID);
		if (tcStatusItem != null) {
			tcStatusItem.setChangeRowFG(false);
			if (big) {
				tcStatusItem.setChangeCellFG(false);
			}
		}

		if (big) {
			ShareRatioItem tcShareRatioItem = (ShareRatioItem) mapTCs.get(ShareRatioItem.COLUMN_ID);
			if (tcShareRatioItem != null) {
				tcShareRatioItem.setChangeFG(false);
			}
		}

		return mapTCs.values().toArray(new TableColumnCore[0]);
	}
	
	/**
	 * @param mapTCs
	 * @param defaultVisibleOrder
	 */
	private static void setVisibility(Map<String, TableColumnCore> mapTCs,
			String[] defaultVisibleOrder, boolean reorder) {
		for (TableColumnCore tc : mapTCs.values()) {
			Long force_visible = (Long) tc.getUserData(TableColumn.UD_FORCE_VISIBLE);
			if (force_visible == null || force_visible == 0) {

				tc.setVisible(false);
			}
		}

		for (int i = 0; i < defaultVisibleOrder.length; i++) {
			String id = defaultVisibleOrder[i];
			TableColumnCore tc = mapTCs.get(id);
			if (tc != null) {
				tc.setVisible(true);
				if (reorder) {
					tc.setPositionNoShift(i);
				}
			}
		}
	}

	public static TableColumnCore[] createCompleteDM(String tableID, boolean big) {
		final String[] oldVisibleOrder = {
			ColumnUnopened.COLUMN_ID,
			ColumnThumbAndName.COLUMN_ID,
			"RatingColumn",
			"azsubs.ui.column.subs",
			"azbuddy.ui.column.msgpending",
			SizeItem.COLUMN_ID,
			StatusItem.COLUMN_ID,
			ShareRatioItem.COLUMN_ID,
			DateCompletedItem.COLUMN_ID,
		};
		final String[] defaultVisibleOrder = {
			RankItem.COLUMN_ID,
			ColumnThumbAndName.COLUMN_ID,
			ColumnStream.COLUMN_ID,
			StatusItem.COLUMN_ID,
			SizeItem.COLUMN_ID,
			ColumnTorrentSpeed.COLUMN_ID,
			"RatingColumn",
			"azsubs.ui.column.subs",
			"azbuddy.ui.column.msgpending",
			DateCompletedItem.COLUMN_ID
		};

		TableColumnManager tcManager = TableColumnManager.getInstance();
		Map<String, TableColumnCore> mapTCs = tcManager.getTableColumnsAsMap(DownloadTypeComplete.class,
				tableID);

		tcManager.setDefaultColumnNames(tableID, defaultVisibleOrder);

		if (!tcManager.loadTableColumnSettings(DownloadTypeComplete.class, tableID)
				|| areNoneVisible(mapTCs)) {
			setVisibility(mapTCs, defaultVisibleOrder, true);
			DateCompletedItem tc = (DateCompletedItem) mapTCs.get(DateCompletedItem.COLUMN_ID);
			if (tc != null) {
				tcManager.setDefaultSortColumnName(tableID, DateCompletedItem.COLUMN_ID);
				tc.setSortAscending(false);
			}
		} else {
			upgradeColumns(oldVisibleOrder, defaultVisibleOrder, mapTCs);
		}

		// special changes
		StatusItem tcStatusItem = (StatusItem) mapTCs.get(StatusItem.COLUMN_ID);
		if (tcStatusItem != null) {
			tcStatusItem.setChangeRowFG(false);
			if (big) {
				tcStatusItem.setChangeCellFG(false);
			}
		}
		if (big) {
			ShareRatioItem tcShareRatioItem = (ShareRatioItem) mapTCs.get(ShareRatioItem.COLUMN_ID);
			if (tcShareRatioItem != null) {
				tcShareRatioItem.setChangeFG(false);
			}
		}

		return mapTCs.values().toArray(new TableColumnCore[0]);
	}

	public static TableColumnCore[] createUnopenedDM(String tableID, boolean big) {
		final String[] oldVisibleOrder = {
			ColumnUnopened.COLUMN_ID,
			ColumnThumbAndName.COLUMN_ID,
			"azsubs.ui.column.subs",
			"azbuddy.ui.column.msgpending",
			SizeItem.COLUMN_ID,
			ColumnProgressETA.COLUMN_ID,
			StatusItem.COLUMN_ID,
			DateCompletedItem.COLUMN_ID,
		};
		final String[] defaultVisibleOrder = {
			ColumnUnopened.COLUMN_ID,
			ColumnThumbAndName.COLUMN_ID,
			ColumnStream.COLUMN_ID,
			SizeItem.COLUMN_ID,
			"RatingColumn",
			"azsubs.ui.column.subs",
			"azbuddy.ui.column.msgpending",
			DateCompletedItem.COLUMN_ID,
		};

		TableColumnManager tcManager = TableColumnManager.getInstance();
		Map<String, TableColumnCore> mapTCs = tcManager.getTableColumnsAsMap(DownloadTypeComplete.class,
				tableID);

		tcManager.setDefaultColumnNames(tableID, defaultVisibleOrder);

		if (!tcManager.loadTableColumnSettings(DownloadTypeComplete.class, tableID)
				|| areNoneVisible(mapTCs)) {
			setVisibility(mapTCs, defaultVisibleOrder, true);
			DateCompletedItem tc = (DateCompletedItem) mapTCs.get(DateCompletedItem.COLUMN_ID);
			if (tc != null) {
				tcManager.setDefaultSortColumnName(tableID, DateCompletedItem.COLUMN_ID);
				tc.setSortAscending(false);
			}
		} else {
			upgradeColumns(oldVisibleOrder, defaultVisibleOrder, mapTCs);
		}

		// special changes
		StatusItem tcStatusItem = (StatusItem) mapTCs.get(StatusItem.COLUMN_ID);
		if (tcStatusItem != null) {
			tcStatusItem.setChangeRowFG(false);
			if (big) {
				tcStatusItem.setChangeCellFG(false);
			}
		}
		if (big) {
			ShareRatioItem tcShareRatioItem = (ShareRatioItem) mapTCs.get(ShareRatioItem.COLUMN_ID);
			if (tcShareRatioItem != null) {
				tcShareRatioItem.setChangeFG(false);
			}
		}

		return mapTCs.values().toArray(new TableColumnCore[0]);
	}
	
	
	public static TableColumnCore[] createActivitySmall(String tableID) {
		final String[] defaultVisibleOrder = {
			ColumnActivityNew.COLUMN_ID,
			ColumnActivityType.COLUMN_ID,
			ColumnActivityText.COLUMN_ID,
			ColumnActivityActions.COLUMN_ID,
			ColumnActivityDate.COLUMN_ID,
		};
		TableColumnManager tcManager = TableColumnManager.getInstance();
		Map<String, TableColumnCore> mapTCs = tcManager.getTableColumnsAsMap(ActivitiesEntry.class,
				tableID);

		tcManager.setDefaultColumnNames(tableID, defaultVisibleOrder);

		if (!tcManager.loadTableColumnSettings(ActivitiesEntry.class, tableID)
				|| areNoneVisible(mapTCs)) {
			setVisibility(mapTCs, defaultVisibleOrder, true);
			ColumnActivityDate tc = (ColumnActivityDate) mapTCs.get(ColumnActivityDate.COLUMN_ID);
			if (tc != null) {
				tcManager.setDefaultSortColumnName(tableID,
						ColumnActivityDate.COLUMN_ID);
				tc.setSortAscending(false);
			}
			ColumnActivityText tcText = (ColumnActivityText) mapTCs.get(ColumnActivityText.COLUMN_ID);
			if (tcText != null) {
				tcText.setWidth(445);
			}
		}

		return mapTCs.values().toArray(new TableColumnCore[0]);
	}

	public static TableColumnCore[] createActivityBig(String tableID) {
		final String[] defaultVisibleOrder = {
			ColumnActivityNew.COLUMN_ID,
			ColumnActivityType.COLUMN_ID,
			ColumnActivityText.COLUMN_ID,
			ColumnThumbnail.COLUMN_ID,
			ColumnActivityActions.COLUMN_ID,
			ColumnActivityDate.COLUMN_ID,
		};
		TableColumnManager tcManager = TableColumnManager.getInstance();
		Map<String, TableColumnCore> mapTCs = tcManager.getTableColumnsAsMap(ActivitiesEntry.class,
				tableID);

		tcManager.setDefaultColumnNames(tableID, defaultVisibleOrder);

		if (!tcManager.loadTableColumnSettings(ActivitiesEntry.class, tableID)
				|| areNoneVisible(mapTCs)) {
			setVisibility(mapTCs, defaultVisibleOrder, true);

			ColumnActivityText tcText = (ColumnActivityText) mapTCs.get(ColumnActivityText.COLUMN_ID);
			if (tcText != null) {
				tcText.setWidth(350);
			}
			ColumnActivityDate tc = (ColumnActivityDate) mapTCs.get(ColumnActivityDate.COLUMN_ID);
			if (tc != null) {
				tcManager.setDefaultSortColumnName(tableID,
						ColumnActivityDate.COLUMN_ID);
				tc.setSortAscending(false);
			}
		}

		return mapTCs.values().toArray(new TableColumnCore[0]);
	}

	/**
	 *
	 *
	 * @since 3.1.1.1
	 */
	private static boolean areNoneVisible(Map<String, TableColumnCore> mapTCs) {
		boolean noneVisible = true;
		for (TableColumnCore tc : mapTCs.values()) {
			if (tc.isVisible()) {
				noneVisible = false;
				break;
			}
		}
		return noneVisible;
	}

	/**
	 *
	 *
	 * @since 3.1.1.1
	 */
	public static void initCoreColumns() {

 		TableColumnCreator.initCoreColumns();

		// short variable names to reduce wrapping
		final Map<String, cInfo> c = new LightHashMap<>(7);

		c.put(ColumnUnopened.COLUMN_ID, new cInfo(ColumnUnopened.class,
				ColumnUnopened.DATASOURCE_TYPES));
		c.put(ColumnThumbAndName.COLUMN_ID, new cInfo(ColumnThumbAndName.class,
				ColumnThumbAndName.DATASOURCE_TYPES));
		c.put(ColumnStream.COLUMN_ID, new cInfo(ColumnStream.class,
				ColumnStream.DATASOURCE_TYPES));
		c.put(DateAddedItem.COLUMN_ID, new cInfo(DateAddedItem.class,
				DateAddedItem.DATASOURCE_TYPE));
		c.put(DateCompletedItem.COLUMN_ID, new cInfo(DateCompletedItem.class,
				DateCompletedItem.DATASOURCE_TYPE));
		c.put(ColumnProgressETA.COLUMN_ID, new cInfo(ColumnProgressETA.class,
				ColumnProgressETA.DATASOURCE_TYPE));
		c.put(ColumnChatMessageCount.COLUMN_ID, new cInfo(
				ColumnChatMessageCount.class, Download.class));

			// Peers
		
		c.put(ColumnPeerBoost.COLUMN_ID, new cInfo(
				ColumnPeerBoost.class, Peer.class));
		
			// Activities

		final Class ac = ActivitiesEntry.class;

		c.put(ColumnActivityNew.COLUMN_ID, new cInfo(ColumnActivityNew.class, ac));
		c.put(ColumnActivityType.COLUMN_ID, new cInfo(ColumnActivityType.class, ac));
		c.put(ColumnActivityText.COLUMN_ID, new cInfo(ColumnActivityText.class, ac));
		c.put(ColumnActivityActions.COLUMN_ID, new cInfo(
				ColumnActivityActions.class, ac));
		c.put(ColumnActivityDate.COLUMN_ID, new cInfo(ColumnActivityDate.class, ac));

		c.put(ColumnThumbnail.COLUMN_ID, new cInfo(ColumnThumbnail.class,
				new Class[] {
					ac,
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
				cInfo info = c.get(columnID);

				if (info.cla.isAssignableFrom(TableColumnCore.class)) {
					return null;
				}

				try {
					Constructor<TableColumnCore> constructor = info.cla.getDeclaredConstructor(
							String.class);
					TableColumnCore column = constructor.newInstance(tableID);
					return column;
				} catch (NoSuchMethodException ignored) {
				} catch (Exception e) {
					Debug.out(e);
				}

				return null;
			}

			@Override
			public void tableColumnCreated(TableColumn column) {
				cInfo info = c.get(column.getName());
				if (column.getClass().equals(info.cla)){
					return;
				}

				try {
					Constructor constructor = info.cla.getDeclaredConstructor(TableColumn.class);
					if (constructor != null) {
  					constructor.newInstance(column);
					}
				} catch (NoSuchMethodException e) {
				} catch (Exception e) {
					Debug.out(e);
				}

			}
		};

		tcManager.unregisterColumn(NameItem.DATASOURCE_TYPE, NameItem.COLUMN_ID );

		for (String id : c.keySet()) {
			cInfo info = c.get(id);

			for (int i = 0; i < info.forDataSourceTypes.length; i++) {
				Class cla = info.forDataSourceTypes[i];

				tcManager.registerColumn(cla, id, tcCreator);
			}
		}

	}

	private static class cInfo
	{
		public final Class cla;

		public final Class[] forDataSourceTypes;

		public cInfo(Class cla, Class forDataSourceType) {
			this.cla = cla;
			this.forDataSourceTypes = new Class[] {
				forDataSourceType
			};
		}

		public cInfo(Class cla, Class[] forDataSourceTypes) {
			this.cla = cla;
			this.forDataSourceTypes = forDataSourceTypes;
		}
	}
}
