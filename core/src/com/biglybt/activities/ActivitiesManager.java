/*
 * Created on Jan 28, 2008
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.activities;

import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.messenger.config.PlatformVuzeActivitiesMessenger;
import com.biglybt.core.util.*;
import com.biglybt.util.MapUtils;

/**
 * Manage Vuze News Entries.  Loads, Saves, and expires them
 *
 * @author TuxPaper
 * @created Jan 28, 2008
 *
 */
public class ActivitiesManager
{
	private static final long MAX_LIFE_MS = 1000L * 60 * 60 * 24 * 365 * 2;

	private static final long DEFAULT_PLATFORM_REFRESH = 60 * 60 * 1000L * 24;

	private static final String SAVE_FILENAME = "VuzeActivities.config";

	private static final ArrayList<ActivitiesListener> listeners = new ArrayList<>();

	private static ArrayList<ActivitiesLoadedListener> listenersLoaded = new ArrayList<>();
	private static final Object listenersLoadedLock = new Object();

	private static final CopyOnWriteList<ActivitiesEntry> allEntries = new CopyOnWriteList<>();

	private static final AEMonitor allEntries_mon = new AEMonitor("VuzeActivityMan");

	private static final List<ActivitiesEntry> removedEntries = new ArrayList<>();

	private static PlatformVuzeActivitiesMessenger.GetEntriesReplyListener replyListener;

	static AEDiagnosticsLogger diag_logger;

	/** Key: NetworkID, Value: last time we pulled news **/
	private static Map<String, Long> lastNewsAt = new HashMap<>();

	private static boolean skipAutoSave = true;

	private static final AEMonitor config_mon = new AEMonitor("ConfigMon");

	static boolean saveEventsOnClose = false;

	static {
		if (System.getProperty("debug.vuzenews", "0").equals("1")) {
			diag_logger = AEDiagnostics.getLogger("v3.vuzenews");
			diag_logger.log("\n\nVuze News Logging Starts");
		} else {
			diag_logger = null;
		}
	}

	public static void initialize(final Core core) {
		new AEThread2("lazy init", true) {
			@Override
			public void run() {
				_initialize(core);
			}
		}.start();
	}

	static void _initialize(Core core) {
		if (diag_logger != null) {
			diag_logger.log("Initialize Called");
		}

		core.addLifecycleListener(new CoreLifecycleAdapter() {
			@Override
			public void stopping(Core core) {
				if (saveEventsOnClose) {
					saveEventsNow();
				}
			}
		});

		loadEvents();

		replyListener = new PlatformVuzeActivitiesMessenger.GetEntriesReplyListener() {
			@Override
			public void gotVuzeNewsEntries(ActivitiesEntry[] entries,
			                               long refreshInMS) {
				if (diag_logger != null) {
					diag_logger.log("Received Reply from platform with " + entries.length
							+ " entries.  Refresh in " + refreshInMS);
				}

				addEntries(entries);

				if (refreshInMS <= 0) {
					refreshInMS = DEFAULT_PLATFORM_REFRESH;
				}

				SimpleTimer.addEvent("GetVuzeNews",
						SystemTime.getOffsetTime(refreshInMS), new TimerEventPerformer() {
							@Override
							public void perform(TimerEvent event) {
								pullActivitiesNow(5000, "timer", false);
							}
						});
			}
		};

		pullActivitiesNow(5000, "initial", false);
	}

	/**
	 * Pull entries from webapp
	 *
	 * @param delay max time to wait before running request
	 *
	 * @since 3.0.4.3
	 */
	public static void pullActivitiesNow(long delay, String reason,
			boolean alwaysPull) {
		{
			String id = "1";
			Long oLastPullTime = lastNewsAt.get(id);
			long lastPullTime = oLastPullTime != null ? oLastPullTime : 0;
			long now = SystemTime.getCurrentTime();
			long diff = now - lastPullTime;
			if (!alwaysPull && diff < 5000) {
				return;
			}
			if (diff > MAX_LIFE_MS) {
				diff = MAX_LIFE_MS;
			}
			PlatformVuzeActivitiesMessenger.getEntries(diff, delay,
					reason, replyListener);
			// broken..
			//lastNewsAt.put(id, new Long(now));
		}
	}

	public static void clearLastPullTimes() {
		lastNewsAt = new HashMap<>();
	}

	/**
	 * Clear the removed entries list so that an entry that was once deleted will
	 * will be able to be added again
	 *
	 *
	 * @since 3.0.4.3
	 */
	public static void resetRemovedEntries() {
		removedEntries.clear();
		saveEvents();
	}

	/**
	 *
	 *
	 * @since 3.1.1.1
	 */
	private static void saveEvents() {
		saveEventsOnClose  = true;
	}

	/**
	 *
	 *
	 * @since 3.0.4.3
	 */
	@SuppressWarnings({
		"rawtypes",
		"unchecked"
	})
	private static void loadEvents() {
		skipAutoSave = true;

		try {
			Map<?,?> map = FileUtil.readResilientConfigFile(SAVE_FILENAME);

			// Clear all entries if we aren't on v2
			if (map != null && map.size() > 0
					&& MapUtils.getMapLong(map, "version", 0) < 2) {
				clearLastPullTimes();
				skipAutoSave = false;
				saveEventsNow();
				return;
			}

			long cutoffTime = getCutoffTime();

			try {
				lastNewsAt = MapUtils.getMapMap(map, "LastChecks", new HashMap());
			} catch (Exception e) {
				Debug.out(e);
			}

			// "LastCheck" backward compat
			if (lastNewsAt.size() == 0) {
  			long lastVuzeNewsAt = MapUtils.getMapLong(map, "LastCheck", 0);
  			if (lastVuzeNewsAt > 0) {
    			if (lastVuzeNewsAt < cutoffTime) {
    				lastVuzeNewsAt = cutoffTime;
    			}
  				lastNewsAt.put("1", lastVuzeNewsAt);
  			}
			}

			Object value;

			List newRemovedEntries = (List) MapUtils.getMapObject(map,
					"removed-entries", null, List.class);
			if (newRemovedEntries != null) {
				for (Object newRemovedEntry : newRemovedEntries) {
					value = newRemovedEntry;
					if (!(value instanceof Map)) {
						continue;
					}
					ActivitiesEntry entry = createEntryFromMap((Map) value, true);

					if (entry != null && entry.getTimestamp() > cutoffTime) {
						removedEntries.add(entry);
					}
				}
			}

			value = map.get("entries");
			if (!(value instanceof List)) {
				return;
			}

			List entries = (List) value;
			List<ActivitiesEntry> entriesToAdd = new ArrayList<>(entries.size());
			for (Object entry1 : entries) {
				value = entry1;
				if (!(value instanceof Map)) {
					continue;
				}

				ActivitiesEntry entry = createEntryFromMap((Map) value, true);

				if (entry != null) {
					if (entry.getTimestamp() > cutoffTime) {
						entriesToAdd.add(entry);
					}
				}
			}

			int num = entriesToAdd.size();
			if (num > 0) {
				addEntries(entriesToAdd.toArray(new ActivitiesEntry[num]));
			}
		} finally {
			skipAutoSave = false;

			synchronized (listenersLoadedLock) {
				if (listenersLoaded != null) {
					for (ActivitiesLoadedListener l : listenersLoaded) {
						try {
							l.vuzeActivitiesLoaded();
						} catch (Exception e) {
							Debug.out(e);
						}
					}
					listenersLoaded = null;
				}
			}

		}
	}

	static void saveEventsNow() {
		if (skipAutoSave) {
			return;
		}

		try {
			config_mon.enter();

			Map<String, Object> mapSave = new HashMap<>();
			mapSave.put("LastChecks", lastNewsAt);
			mapSave.put("version", 2L);

			List<Object> entriesList = new ArrayList<>();

			List<ActivitiesEntry> allEntries = getAllEntries();
			for ( ActivitiesEntry entry: allEntries ){
				if (entry == null) {
					continue;
				}

				boolean isHeader = ActivitiesConstants.TYPEID_HEADER.equals(entry.getTypeID());
				if (!isHeader) {
					entriesList.add(entry.toMap());
				}
			}
			mapSave.put("entries", entriesList);

			List<Object> removedEntriesList = new ArrayList<>();
			for (ActivitiesEntry entry : removedEntries) {
				removedEntriesList.add(entry.toDeletedMap());
			}
			mapSave.put("removed-entries", removedEntriesList);

			FileUtil.writeResilientConfigFile(SAVE_FILENAME, mapSave);

		} catch (Throwable t) {
			Debug.out(t);
		} finally {
			config_mon.exit();
		}
	}

	private static long getCutoffTime() {
		return SystemTime.getOffsetTime(-MAX_LIFE_MS);
	}

	public static void addListener(ActivitiesListener l) {
		listeners.add(l);
	}

	public static void removeListener(ActivitiesListener l) {
		listeners.remove(l);
	}

	public static void addListener(ActivitiesLoadedListener l) {
		synchronized (listenersLoadedLock) {
			if (listenersLoaded != null) {
				listenersLoaded.add(l);
			} else {
				try {
					l.vuzeActivitiesLoaded();
				} catch (Exception e) {
					Debug.out(e);
				}
			}
		}
	}

	public static void removeListener(ActivitiesLoadedListener l) {
		synchronized (listenersLoadedLock) {
			if (listenersLoaded != null) {
				listenersLoaded.remove(l);
			}
		}
	}

	/**
	 *
	 * @return list of entries actually added (no dups)
	 *
	 * @since 3.0.4.3
	 */
	public static ActivitiesEntry[] addEntries(ActivitiesEntry[] entries) {
		long cutoffTime = getCutoffTime();

		ArrayList<ActivitiesEntry> newEntries = new ArrayList<>(entries.length);
		ArrayList<ActivitiesEntry> existingEntries = new ArrayList<>(0);

		try {
			allEntries_mon.enter();

			for (ActivitiesEntry entry : entries) {
				boolean isHeader = ActivitiesConstants.TYPEID_HEADER.equals(entry.getTypeID());
				if ((entry.getTimestamp() >= cutoffTime || isHeader)
						&& !removedEntries.contains(entry)) {

					ActivitiesEntry existing_entry = allEntries.get(entry);
					if (existing_entry != null) {
						existingEntries.add(existing_entry);
						if (existing_entry.getTimestamp() < entry.getTimestamp()) {
							existing_entry.updateFrom(entry);
						}
					} else {
						newEntries.add(entry);
						allEntries.add(entry);
					}
				}
			}
		} finally {
			allEntries_mon.exit();
		}

		ActivitiesEntry[] newEntriesArray = newEntries.toArray(new ActivitiesEntry[newEntries.size()]);

		if (newEntriesArray.length > 0) {
			saveEventsNow();

			Object[] listenersArray = listeners.toArray();
			for (Object aListenersArray : listenersArray) {
				ActivitiesListener l = (ActivitiesListener) aListenersArray;
				l.vuzeNewsEntriesAdded(newEntriesArray);
			}
		}

		if (existingEntries.size() > 0) {
			if (newEntriesArray.length == 0) {
				saveEvents();
			}

			for (ActivitiesEntry entry : existingEntries) {
				triggerEntryChanged(entry);
			}
		}

		return newEntriesArray;
	}

	public static void removeEntries(ActivitiesEntry[] entries) {
		removeEntries(entries, false);
	}

	public static void removeEntries(ActivitiesEntry[] entries, boolean allowReAdd) {
		long cutoffTime = getCutoffTime();

		try {
			allEntries_mon.enter();

			for (ActivitiesEntry entry : entries) {
				if (entry == null) {
					continue;
				}
				allEntries.remove(entry);
				boolean isHeader = ActivitiesConstants.TYPEID_HEADER.equals(entry.getTypeID());
				if (!allowReAdd && entry.getTimestamp() > cutoffTime && !isHeader) {
					if (!entry.allowReAdd()) {

						removedEntries.add(entry);
					}
				}
			}
		} finally {
			allEntries_mon.exit();
		}

		Object[] listenersArray = listeners.toArray();
		for (Object aListenersArray : listenersArray) {
			ActivitiesListener l = (ActivitiesListener) aListenersArray;
			l.vuzeNewsEntriesRemoved(entries);
		}
		saveEventsNow();
	}

	public static ActivitiesEntry getEntryByID(String id) {
		try {
			allEntries_mon.enter();

			for (ActivitiesEntry entry : allEntries) {
				if (entry == null) {
					continue;
				}
				String entryID = entry.getID();
				if (entryID != null && entryID.equals(id)) {
					return entry;
				}
			}
		} finally {
			allEntries_mon.exit();
		}

		return null;
	}

	public static boolean isEntryIdRemoved(String id) {
		for (ActivitiesEntry entry : removedEntries) {
			if (entry.getID().equals(id)) {
				return true;
			}
		}
		return false;
	}

	public static List<ActivitiesEntry> getAllEntries() {
		return allEntries.getList();
	}

	public static Object[]
	getMostRecentUnseen()
	{
		ActivitiesEntry newest		= null;
		long					newest_time	= 0;

		int	num_unseen = 0;

		for ( ActivitiesEntry entry: allEntries ){

			if ( !entry.getViewed()){

				num_unseen++;

				long t = entry.getTimestamp();

				if ( t > newest_time ){

					newest		= entry;
					newest_time	= t;
				}
			}
		}

		return( new Object[]{ newest, num_unseen });
	}

	public static int getNumEntries() {
		return allEntries.size();
	}

	public static void log(String s) {
		if (diag_logger != null) {
			diag_logger.log(s);
		}
	}

	/**
	 * @since 3.0.4.3
	 */
	public static void triggerEntryChanged(ActivitiesEntry entry) {
		Object[] listenersArray = listeners.toArray();
		for (Object aListenersArray : listenersArray) {
			ActivitiesListener l = (ActivitiesListener) aListenersArray;
			l.vuzeNewsEntryChanged(entry);
		}
		saveEvents();
	}

	/**
	 * @since 3.0.5.3
	 */
	public static ActivitiesEntry createEntryFromMap(Map<?, ?> map,
	                                                 boolean internalMap) {
		ActivitiesEntry entry;
		entry = new ActivitiesEntry();
		if (internalMap) {
			entry.loadFromInternalMap(map);
		} else {
			entry.loadFromExternalMap(map);
		}
		return entry;
	}
}
