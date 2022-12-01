/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.views;

import java.util.*;

import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTViewBuilder.UISWTViewEventListenerInstantiator;
import com.biglybt.ui.swt.pifimpl.BasicPluginViewImpl;
import com.biglybt.ui.swt.pifimpl.UISWTInstanceImpl.SWTViewListener;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadTypeComplete;
import com.biglybt.pif.download.DownloadTypeIncomplete;
import com.biglybt.pif.ui.tables.TableManager;

public class ViewManagerSWT
{
	private static final AEMonitor class_mon = new AEMonitor("ViewManager");

	private static ViewManagerSWT instance;

	public static ViewManagerSWT getInstance() {
		try {
			class_mon.enter();

			if (instance == null) {
				instance = new ViewManagerSWT();
			}
			return instance;
		} finally {
			class_mon.exit();
		}
	}

	/**
	 * Map&lt;forDataSourceType or forParentViewID, Map&lt;ViewID, Builder>>
	 */
	private final Map<Object, Map<String, UISWTViewBuilderCore>> mapDataSourceTypeToBuilder = new HashMap<>();

	private final List<SWTViewListener> listSWTViewListeners = new ArrayList<>();

	private final Collection<Object> registeredCoreViews = new HashSet<>();

	private Map<String, UISWTViewBuilderCore> getBuilderMap(
			Object forDSTypeOrViewID) {
		synchronized (mapDataSourceTypeToBuilder) {
			return mapDataSourceTypeToBuilder.computeIfAbsent(forDSTypeOrViewID,
					k -> new LinkedHashMap<>());
		}
	}

	public void registerView(Object forDSTypeOrViewID,
			UISWTViewBuilderCore builder) {
		boolean skipWarning = false;
		if (forDSTypeOrViewID == null) {
			forDSTypeOrViewID = UISWTInstance.VIEW_MAIN;
		} else if (forDSTypeOrViewID instanceof String) {
			// Legacy convert. Older plugins registered for a parent viewid when
			// they really want to be in all views with a certain datasource type.
			// Example, registering to VIEW_MYTORRENTS when they are usefull in all
			// Download.class type views
			Class<?> cla = mapViewIDToClass((String) forDSTypeOrViewID);
			if (cla != null) {
				forDSTypeOrViewID = cla;
				skipWarning = true;
			}
		}

		synchronized (mapDataSourceTypeToBuilder) {
			Map<String, UISWTViewBuilderCore> builderMap = getBuilderMap(
					forDSTypeOrViewID);
			UISWTViewBuilderCore existingBuilder = builderMap.put(builder.getViewID(),
					builder);
			if (existingBuilder != null && existingBuilder != builder && !skipWarning) {
				Debug.out("Overiding already registered view '" + builder.getViewID()
						+ "' for " + forDSTypeOrViewID);
			}
		}

		SWTViewListener[] viewListeners = listSWTViewListeners.toArray(
				new SWTViewListener[0]);
		for (SWTViewListener l : viewListeners) {
			l.setViewRegistered(forDSTypeOrViewID, builder);
		}
	}

	private static Class<?> mapViewIDToClass(String viewID) {
		if (viewID == null) {
			return null;
		}

		Class<?> cla;
		// Convert Legacy view id's to datasourcetypes
		switch ( Utils.getBaseViewID( viewID )){

			case TableManager.TABLE_MYTORRENTS_ALL_BIG:
			case TableManager.TABLE_MYTORRENTS_ALL_SMALL:
			case TableManager.TABLE_MYTORRENTS_INCOMPLETE: // equiv UISWTInstance.VIEW_MYTORRENTS
			case TableManager.TABLE_MYTORRENTS_INCOMPLETE_BIG:
			case TableManager.TABLE_MYTORRENTS_COMPLETE:
				// While it's possible a plugin wanted the view for only incomplete or for complete,
				// there's no plugins that actually did.  So, make them for all downloads
				cla = Download.class;
				break;

			case "TagsView":
				// Plugins that use this: RCM
				cla = com.biglybt.core.tag.Tag.class; // TODO: Should be plugin one..
				break;

			case UISWTInstance.VIEW_MAIN:
			default:
				cla = null;
				break;
		}

		return cla;
	}

	/**
	 * Removes all specified views and removes builder.
	 * 
	 * @param forDSTypeOrViewID null = all matching viewID
	 * @param viewID
	 */
	public void unregisterView(Object forDSTypeOrViewID, String viewID) {
		if (forDSTypeOrViewID == null) {
			forDSTypeOrViewID = UISWTInstance.VIEW_MAIN;
		}
		disposeViews(forDSTypeOrViewID, viewID, true);
	}

	public List<UISWTViewBuilderCore> getBuilders(Object forDSTypeOrViewID) {
		return getBuilders(forDSTypeOrViewID, true);
	}

	private List<UISWTViewBuilderCore> getBuilders(Object forDSTypeOrViewID,
			boolean sort) {
		List<UISWTViewBuilderCore> list;
		synchronized (mapDataSourceTypeToBuilder) {
			list = new ArrayList<>(getBuilderMap(forDSTypeOrViewID).values());

			if (DownloadTypeComplete.class.equals(forDSTypeOrViewID)
					|| DownloadTypeIncomplete.class.equals(forDSTypeOrViewID)) {
				// Download.class also apply to incomplete and complete
				list.addAll(getBuilderMap(Download.class).values());
			} else if (Download.class.equals(forDSTypeOrViewID)) {
				// incomplete and complete views also apply to Download.class
				list.addAll(getBuilderMap(DownloadTypeComplete.class).values());
				list.addAll(getBuilderMap(DownloadTypeIncomplete.class).values());
			}
		}

		if (sort) {
			sortBuilders(list);
		}
		return list;
	}

	public int getBuildersCount(Object forDSTypeOrViewID) {
		return getBuilderMap(forDSTypeOrViewID).size();
	}

	/**
	 * Helper method that gets a sorted list of Builders for both a ParentViewID 
	 * and a DataSourceType
	 * 
	 * @param forDataSourceType null skips check for datasourcetype
	 * @param parentViewID null skips check for parent View ID
	 */
	public List<UISWTViewBuilderCore> getBuilders(String parentViewID,
			Class forDataSourceType) {
		List<UISWTViewBuilderCore> list = new ArrayList<>();
		if (forDataSourceType != null) {
			list.addAll(getBuilders(forDataSourceType, false));
		}
		if (parentViewID != null) {
			list.addAll(getBuilders(parentViewID));
		}

		sortBuilders(list);
		return list;
	}

	private static void sortBuilders(List<UISWTViewBuilderCore> list) {
		// Sort core plugins first.  Need better ordering, esp when MDI can insert before/after
		// TODO: use preferredAfterID param
		list.sort((o1, o2) -> {
			if ((o1.getPluginInterface() == null)
					&& (o2.getPluginInterface() == null)) {
				return 0;
			}
			if ((o1.getPluginInterface() != null)
					&& (o2.getPluginInterface() != null)) {
				return 0;
			}
			return o1.getPluginInterface() == null ? -1 : 1;
		});
	}

	public void addSWTViewListener(SWTViewListener l) {
		listSWTViewListeners.add(l);

		List<UISWTViewBuilderCore> builders = getBuilders(UISWTInstance.VIEW_MAIN);
		for (UISWTViewBuilderCore builder : builders) {
			l.setViewRegistered(UISWTInstance.VIEW_MAIN, builder);
		}
	}

	public void removeSWTViewListener(SWTViewListener l) {
		listSWTViewListeners.remove(l);
	}

	public void disposeAll() {
		List<UISWTViewBuilderCore> listBuildersToDispose = new ArrayList<>();
		synchronized (mapDataSourceTypeToBuilder) {
			Collection<Map<String, UISWTViewBuilderCore>> values = mapDataSourceTypeToBuilder.values();
			for (Map<String, UISWTViewBuilderCore> value : values) {
				listBuildersToDispose.addAll(value.values());
			}
			mapDataSourceTypeToBuilder.clear();
		}
		registeredCoreViews.clear();

		for (UISWTViewBuilderCore builder : listBuildersToDispose) {
			builder.dispose();
		}
	}

	public void dispose(PluginInterface pi) {
		List<UISWTViewBuilderCore> listBuildersToDispose = new ArrayList<>();
		synchronized (mapDataSourceTypeToBuilder) {
			Collection<Map<String, UISWTViewBuilderCore>> values = mapDataSourceTypeToBuilder.values();
			for (Map<String, UISWTViewBuilderCore> value : values) {
				for (Iterator<String> iterator = value.keySet().iterator(); iterator.hasNext();) {
					String key = iterator.next();
					UISWTViewBuilderCore builder = value.get(key);
					if (builder != null && builder.getPluginInterface() == pi) {
						listBuildersToDispose.add(builder);
						iterator.remove();
					}
				}
			}
		}

		for (UISWTViewBuilderCore builder : listBuildersToDispose) {
			builder.dispose();
		}
	}

	/**
	 * Disposes of existing views.  Does not dispose of builder, which means new
	 * views can still be created.  To prevent new views being created use
	 * {@link #unregisterView(Object, String)}
	 * 
	 * @param parentViewID null for all
	 * @param viewID view id to dispose of
	 * @param unregister <br/>
	 *    true: disposes of builder, preventing new views being created<br/>
	 *    false: Keeps builder, allows new views to be created.
	 * @return Builders that had their views disposed of
	 */
	public List<UISWTViewBuilderCore> disposeViews(Object forDSTypeOrViewID,
			String viewID, boolean unregister) {
		List<UISWTViewBuilderCore> listDisposeViewsIn = new ArrayList<>();
		synchronized (mapDataSourceTypeToBuilder) {
			if (forDSTypeOrViewID == null) {
				for (Map<String, UISWTViewBuilderCore> mapViewIDtoBuilder : mapDataSourceTypeToBuilder.values()) {
					UISWTViewBuilderCore builder = unregister
							? mapViewIDtoBuilder.remove(viewID)
							: mapViewIDtoBuilder.get(viewID);
					if (builder != null) {
						listDisposeViewsIn.add(builder);
					}
				}
			} else {
				Map<String, UISWTViewBuilderCore> mapViewIDtoBuilder = getBuilderMap(
						forDSTypeOrViewID);
				UISWTViewBuilderCore builder = unregister
						? mapViewIDtoBuilder.remove(viewID)
						: mapViewIDtoBuilder.get(viewID);
				if (builder != null) {
					listDisposeViewsIn.add(builder);
				}
			}
		}

		SWTViewListener[] viewListeners = unregister
				? listSWTViewListeners.toArray(new SWTViewListener[0]) : null;

		for (UISWTViewBuilderCore builder : listDisposeViewsIn) {
			builder.disposeViews();
			if (unregister) {
				for (SWTViewListener l : viewListeners) {
					l.setViewDeregistered(forDSTypeOrViewID, builder);
				}
			}
		}
		return listDisposeViewsIn;
	}

	public UISWTViewBuilderCore getBuilder(Object forDSTypeOrViewID,
			String viewID) {
		if (forDSTypeOrViewID instanceof String) {
			Class<?> cla = mapViewIDToClass((String) forDSTypeOrViewID);
			if (cla != null) {
				forDSTypeOrViewID = cla;
			}
		}

		synchronized (mapDataSourceTypeToBuilder) {
			Map<String, UISWTViewBuilderCore> builderMap = getBuilderMap(
					forDSTypeOrViewID);
			return builderMap.get(viewID);
		}
	}

	public void setCoreViewsRegistered(Object forTypeOrID) {
		registeredCoreViews.add(forTypeOrID);
	}

	public boolean areCoreViewsRegistered(Object forTypeID) {
		return registeredCoreViews.contains(forTypeID);
	}

	/**
	 * 
	 * @param forDSTypeOrViewID null = any
	 * @param ofClass
	 * @return
	 */
	public List<UISWTViewBuilderCore> getBuildersOfClass(Object forDSTypeOrViewID,
			Class<BasicPluginViewImpl> ofClass) {
		List<UISWTViewBuilderCore> list = new ArrayList<>();
		synchronized (mapDataSourceTypeToBuilder) {
			if (forDSTypeOrViewID == null) {
				for (Map<String, UISWTViewBuilderCore> mapViewIDtoBuilder : mapDataSourceTypeToBuilder.values()) {
					for (UISWTViewBuilderCore builder : mapViewIDtoBuilder.values()) {
						if (builder.isListenerOfClass(ofClass)) {
							list.add(builder);
						}
					}
				}
			} else {
				for (UISWTViewBuilderCore builder : getBuilders(forDSTypeOrViewID)) {
					if (builder.isListenerOfClass(ofClass)) {
						list.add(builder);
					}
				}
			}
		}
		return list;
	}
	
	public List<UISWTViewBuilderCore> 
	getBuildersForInstantiatorUID( String uid )
	{
		List<UISWTViewBuilderCore> list = new ArrayList<>();
		synchronized (mapDataSourceTypeToBuilder) {
			for (Map<String, UISWTViewBuilderCore> mapViewIDtoBuilder : mapDataSourceTypeToBuilder.values()) {
				for (UISWTViewBuilderCore builder : mapViewIDtoBuilder.values()) {
					UISWTViewEventListenerInstantiator instantiator = builder.getListenerInstantiator();
					if ( instantiator != null && instantiator.getUID().equals( uid )){
						list.add( builder );
					}
				}
			}
		}
		return list;
	}
}
