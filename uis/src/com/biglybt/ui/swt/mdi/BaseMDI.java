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

package com.biglybt.ui.swt.mdi;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.regex.Pattern;

import org.eclipse.swt.widgets.Menu;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.ui.config.ConfigSectionHolder;
import com.biglybt.pifimpl.local.ui.config.ConfigSectionRepository;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.common.util.MenuItemManager;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.mdi.*;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.PluginsMenuHelper;
import com.biglybt.ui.swt.mainwindow.PluginsMenuHelper.IViewInfo;
import com.biglybt.ui.swt.mainwindow.PluginsMenuHelper.PluginAddedViewListener;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.pifimpl.UISWTViewCore;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.views.skin.SkinView;
import com.biglybt.ui.swt.views.skin.sidebar.SideBar;
import com.biglybt.ui.swt.views.skin.sidebar.SideBarEntrySWT;
import com.biglybt.util.MapUtils;

public abstract class BaseMDI
	extends SkinView
	implements MultipleDocumentInterfaceSWT, UIUpdatable
{
	private MdiEntrySWT currentEntry;

	private Map<String, MdiEntryCreationListener> mapIdToCreationListener = new LightHashMap<>();
	private Map<String, MdiEntryCreationListener2> mapIdToCreationListener2 = new LightHashMap<>();

	// Sync changes to entry maps on mapIdEntry
	private Map<String, MdiEntry> mapIdToEntry = new LinkedHashMap<>(8);

	private List<MdiListener> listeners = new ArrayList<>();

	private List<MdiEntryLoadedListener> listLoadListeners = new ArrayList<>();

	private List<MdiSWTMenuHackListener> listMenuHackListners;

	private Object	autoOpenLock = new Object();
	
	private LinkedHashMap<String, Object> mapAutoOpen = new LinkedHashMap<>();

	private volatile boolean mapAutoOpenLoaded = false;

	private TimerEvent	autoOpenSaver;
	
	private String[] preferredOrder;

	private String closeableConfigFile = "sidebarauto.config";

	private volatile boolean 	initialized;
	private volatile boolean	closed;
	
	@Override
	public void addListener(MdiListener l) {
		synchronized (listeners) {
			if (listeners.contains(l)) {
				return;
			}
			listeners.add(l);
		}
	}

	@Override
	public void removeListener(MdiListener l) {
		synchronized (listeners) {
			listeners.remove(l);
		}
	}

	@Override
	public void addListener(MdiEntryLoadedListener l) {
		synchronized (listLoadListeners) {
			if (listLoadListeners.contains(l)) {
				return;
			}
			listLoadListeners.add(l);
		}
		// might be a very rare thread issue here if entry gets loaded while
		// we are walking through entries
		MdiEntry[] entries = getEntries();
		for (MdiEntry entry : entries) {
			if (entry.isAdded()) {
				l.mdiEntryLoaded(entry);
			}
		}
	}

	@Override
	public void removeListener(MdiEntryLoadedListener l) {
		synchronized (listLoadListeners) {
			listLoadListeners.remove(l);
		}
	}

	protected void triggerSelectionListener(MdiEntry newEntry, MdiEntry oldEntry) {
		MdiListener[] array;
		synchronized (listeners) {
			array = listeners.toArray(new MdiListener[0]);
		}
		for (MdiListener l : array) {
			try {
				l.mdiEntrySelected(newEntry, oldEntry);
			} catch (Exception e) {
				Debug.out(e);
			}
		}

		itemSelected( newEntry );
	}

	public void triggerEntryLoadedListeners(MdiEntry entry) {
		MdiEntryLoadedListener[] array;
		synchronized (listLoadListeners) {
			array = listLoadListeners.toArray(new MdiEntryLoadedListener[0]);
		}
		for (MdiEntryLoadedListener l : array) {
			try {
				l.mdiEntryLoaded(entry);
			} catch (Exception e) {
				Debug.out(e);
			}
		}
	}

	@Override
	public void closeEntry(final String id) {
		MdiEntry entry = getEntry(id);
		if (entry != null) {
			entry.close(false);
		} else {
			removeEntryAutoOpen(id);
		}
	}

	public String getMenuIdPrefix() {
		return "sidebar.";
	}

	// @see MultipleDocumentInterfaceSWT#createEntryFromEventListener(java.lang.String, com.biglybt.ui.swt.pif.UISWTViewEventListener, java.lang.String, boolean, java.lang.Object, java.lang.String)
	@Override
	public final MdiEntry createEntryFromEventListener(String parentID,
	                                                   UISWTViewEventListener l, String id, boolean closeable, Object datasource, String preferedAfterID) {
		return createEntryFromEventListener(parentID, null, l, id, closeable, datasource, preferedAfterID);
	}

	/* (non-Javadoc)
	 * @see MultipleDocumentInterfaceSWT#createEntryFromEventListener(java.lang.String, java.lang.String, com.biglybt.ui.swt.pif.UISWTViewEventListener, java.lang.String, boolean, java.lang.Object, java.lang.String)
	 */
	@Override
	public abstract MdiEntry createEntryFromEventListener(String parentEntryID,
	                                                      String parentViewID, UISWTViewEventListener l, String id,
	                                                      boolean closeable, Object datasource, String preferredAfterID);

	// @see MultipleDocumentInterface#createEntryFromSkinRef(java.lang.String, java.lang.String, java.lang.String, java.lang.String, ViewTitleInfo, java.lang.Object, boolean, java.lang.String)
	@Override
	public abstract MdiEntry createEntryFromSkinRef(String parentID, String id,
	                                                String configID, String title, ViewTitleInfo titleInfo, Object params,
	                                                boolean closeable, String preferedAfterID);

	// @see MultipleDocumentInterfaceSWT#createEntryFromEventListener(java.lang.String, java.lang.Class, java.lang.String, boolean, java.lang.Object, java.lang.String)
	@Override
	public MdiEntry createEntryFromEventListener(final String parentID,
	                                             Class<? extends UISWTViewEventListener> cla, String id, boolean closeable,
	                                             Object data, String preferedAfterID) {
		final MultipleDocumentInterfaceSWT mdi = UIFunctionsManagerSWT.getUIFunctionsSWT().getMDISWT();
		if (mdi == null) {
			return null;
		}

		if (id == null) {
			id = cla.getName();
			int i = id.lastIndexOf('.');
			if (i > 0) {
				id = id.substring(i + 1);
			}
		}

		MdiEntry entry = mdi.getEntry(id);
		if (entry != null) {
			if (data != null) {
				entry.setDatasource(data);
			}
			return entry;
		}
		UISWTViewEventListener l = null;
		if (data != null) {
			try {
				Constructor<?> constructor = cla.getConstructor(new Class[] {
					data.getClass()
				});
				l = (UISWTViewEventListener) constructor.newInstance(new Object[] {
					data
				});
			} catch (Exception e) {
			}
		}

		try {
			if (l == null) {
				l = cla.newInstance();
			}
			return mdi.createEntryFromEventListener(parentID, l, id, closeable, data,
					preferedAfterID);
		} catch (Exception e) {
			Debug.out(e);
		}

		return null;
	}

	@Override
	public MdiEntry getCurrentEntry() {
		return currentEntry;
	}

	@Override
	public MdiEntrySWT getCurrentEntrySWT() {
		return currentEntry;
	}

	protected void
	setCurrentEntry(
		MdiEntrySWT		entry )
	{
		currentEntry = entry;
	}
	
	@Override
	public MdiEntry[] getEntries() {
		return getEntries( new MdiEntry[0]);
	}

	public MdiEntrySWT[] getEntriesSWT() {
		return getEntries( new MdiEntrySWT[0]);
	}

	public <T extends MdiEntry> T[] getEntries( T[] array ) {
		synchronized(mapIdToEntry){
			return mapIdToEntry.values().toArray( array );
		}
	}

	@Override
	public MdiEntry getEntry(String id) {
		synchronized(mapIdToEntry){
			MdiEntry entry = mapIdToEntry.get(id);
			return entry;
		}
	}

	@Override
	public MdiEntrySWT getEntrySWT(String id) {
		synchronized(mapIdToEntry){
			MdiEntrySWT entry = (MdiEntrySWT)mapIdToEntry.get(id);
			return entry;
		}
	}

	/**
	 * @param skinView
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	@Override
	public MdiEntry getEntryBySkinView(Object skinView) {
		SWTSkinObject so = ((SkinView)skinView).getMainSkinObject();
		BaseMdiEntry[] sideBarEntries = getEntries( new BaseMdiEntry[0] );
		for (int i = 0; i < sideBarEntries.length; i++) {
			BaseMdiEntry entry = sideBarEntries[i];
			SWTSkinObject entrySO = entry.getSkinObject();
			SWTSkinObject entrySOParent = entrySO == null ? entrySO
					: entrySO.getParent();
			if (entrySO == so || entrySO == so.getParent() || entrySOParent == so) {
				return entry;
			}
		}
		return null;
	}

	@Override
	public UISWTViewCore getCoreViewFromID(String id) {
		if (id == null) {
			return null;
		}
		MdiEntrySWT entry = getEntrySWT(id);
		if (entry instanceof UISWTViewCore) {
			return (UISWTViewCore) entry;
		}
		return null;
	}

	@Override
	public String getUpdateUIName() {
		if (currentEntry == null) {
			return "MDI";
		}
		return currentEntry.getId();
	}

	// @see MultipleDocumentInterface#registerEntry(java.lang.String, MdiEntryCreationListener2)
	@Override
	public void registerEntry(String id, MdiEntryCreationListener2 l) {
		if (mapIdToCreationListener.containsKey(id)) {
			System.err.println("Warning: MDIEntry " + id
					+ " Creation Listener being registered twice. "
					+ Debug.getCompressedStackTrace());
		}
		mapIdToCreationListener2.put(id, l);

		createIfAutoOpen(id);
	}

	// @see MultipleDocumentInterface#deregisterEntry(java.lang.String, MdiEntryCreationListener2)
	@Override
	public void deregisterEntry(String id, MdiEntryCreationListener2 l) {
		MdiEntryCreationListener2 l2 = mapIdToCreationListener2.get(id);
		if (l == l2) {
			mapIdToCreationListener2.remove(id);
		}
	}

	private boolean createIfAutoOpen(String id) {
		
		
			// carefull with scope of locking on autoOpenLock - make it larger and you'll
			// get deadlocks...
		
		Object o;
		
		synchronized( autoOpenLock ){
			o= mapAutoOpen.get(id);
		}
		if (o instanceof Map<?, ?>) {
			Map<?, ?> autoOpenMap = (Map<?, ?>) o;

			return createEntryByCreationListener(id, autoOpenMap.get("datasource"),
					autoOpenMap) != null;
		}

		boolean created = false;
		String[] autoOpenIDs;
		synchronized( autoOpenLock ){
			autoOpenIDs = mapAutoOpen.keySet().toArray(new String[0]);
		}
		for (String autoOpenID : autoOpenIDs) {
			if (Pattern.matches(id, autoOpenID)) {
				Map<?, ?> autoOpenMap;
				synchronized( autoOpenLock ){
					autoOpenMap = (Map<?, ?>) mapAutoOpen.get(autoOpenID);
				}
				created |= createEntryByCreationListener(autoOpenID,
						autoOpenMap.get("datasource"), autoOpenMap) != null;
			}
		}
		return created;
		
	}

	protected MdiEntry
	createEntryByCreationListener(String id, Object ds, Map<?, ?> autoOpenMap)
	{
		MdiEntryCreationListener mdiEntryCreationListener = null;
		for (String key : mapIdToCreationListener.keySet()) {
			if (Pattern.matches(key, id)) {
				mdiEntryCreationListener = mapIdToCreationListener.get(key);
				break;
			}
		}
		if (mdiEntryCreationListener != null) {
			try {
				MdiEntry mdiEntry = mdiEntryCreationListener.createMDiEntry(id);

				if (mdiEntry != null && ds != null) {
					mdiEntry.setDatasource(ds);
				}
				return mdiEntry;
			} catch (Exception e) {
				Debug.out(e);
			}
		}

		MdiEntryCreationListener2 mdiEntryCreationListener2 = null;
		for (String key : mapIdToCreationListener2.keySet()) {
			if (Pattern.matches(key, id)) {
				mdiEntryCreationListener2 = mapIdToCreationListener2.get(key);
				break;
			}
		}
		if (mdiEntryCreationListener2 != null) {
			try {
				MdiEntry mdiEntry = mdiEntryCreationListener2.createMDiEntry(this, id, ds, autoOpenMap);
				if (mdiEntry == null) {
					removeEntryAutoOpen(id);
				}
				return mdiEntry;
			} catch (Exception e) {
				Debug.out(e);
			}
		}

		setEntryAutoOpen(id, ds);

		return null;
	}

	// @see MultipleDocumentInterface#registerEntry(java.lang.String, MdiEntryCreationListener)
	@Override
	public void registerEntry(String id, MdiEntryCreationListener l) {
		if (mapIdToCreationListener.containsKey(id)
				|| mapIdToCreationListener2.containsKey(id)) {
			System.err.println("Warning: MDIEntry " + id
					+ " Creation Listener being registered twice. "
					+ Debug.getCompressedStackTrace());
		}
		mapIdToCreationListener.put(id, l);

		createIfAutoOpen(id);
	}

	// @see MultipleDocumentInterface#deregisterEntry(java.lang.String, MdiEntryCreationListener)
	@Override
	public void deregisterEntry(String id, MdiEntryCreationListener l) {
		MdiEntryCreationListener l2 = mapIdToCreationListener.get(id);
		if (l == l2) {
			mapIdToCreationListener.remove(id);
		}
	}

	@Override
	public boolean showEntryByID(String id) {
		return loadEntryByID(id, true);
	}

	@Override
	public boolean showEntryByID(String id, Object datasource) {
		return loadEntryByID(id, true, false, datasource);
	}

	@Override
	public Object skinObjectInitialShow(SWTSkinObject skinObject, Object params) {
		final UIManager ui_manager = PluginInitializer.getDefaultInterface().getUIManager();
		ui_manager.addUIListener(new UIManagerListener() {
			@Override
			public void UIDetached(UIInstance instance) {
			}

			@Override
			public void UIAttached(UIInstance instance) {
				if (instance instanceof UISWTInstance) {
					ui_manager.removeUIListener(this);

					final AESemaphore wait_sem = new AESemaphore( "SideBar:wait" );

					Utils.execSWTThread(new AERunnable() {
						@Override
						public void runSupport() {
							try{
								try {
									loadCloseables();
								} catch (Throwable t) {
									Debug.out(t);
								}

								setupPluginViews();

							}finally{

								initialized = true;
								
								wait_sem.release();
							}
						}
					});

						// we need to wait for the loadCloseables to complete as there is code in MainMDISetup that runs on the 'UIAttachedComplete'
						// callback that needs the closables to be loaded (when setting 'start tab') otherwise the order gets broken

					if ( !wait_sem.reserve(10*1000)){

						Debug.out( "eh?");
					}
				}
			}
		});

		return null;
	}

	// @see SkinView#skinObjectDestroyed(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectDestroyed(SWTSkinObject skinObject, Object params) {
		if ( closeableConfigFile != null ){
				// only persist this for the main MDI, not subtabs
			MdiEntry entry = getCurrentEntry();
			if (entry != null) {
				COConfigurationManager.setParameter("v3.StartTab",
						entry.getId());
				String ds = entry.getExportableDatasource();
				COConfigurationManager.setParameter("v3.StartTab.ds", ds == null ? null : ds.toString());
			}
		}
		
		super.skinObjectDestroyed(skinObject, params);


		MdiListener[] array;
		synchronized (listeners) {
			array = listeners.toArray(new MdiListener[0]);
		}
		for (MdiListener l : array) {
			try {
				l.mdiDisposed(this);
			} catch (Exception e) {
				Debug.out(e);
			}
		}

		return null;
	}

	@Override
	public void updateUI() {
		MdiEntry currentEntry = getCurrentEntry();
		if (currentEntry != null) {
			currentEntry.updateUI();
		}
	}

	// @see MultipleDocumentInterface#loadEntryByID(java.lang.String, boolean)
	@Override
	public boolean loadEntryByID(String id, boolean activate) {
		return loadEntryByID(id, activate, false, null);
	}

	/* (non-Javadoc)
	 * @see MultipleDocumentInterface#loadEntryByID(java.lang.String, boolean, boolean)
	 */
	@Override
	public boolean loadEntryByID(String id, boolean activate,
	                             boolean onlyLoadOnce, Object datasource) {
		if (id == null) {
			return false;
		}

		@SuppressWarnings("deprecation")
		boolean loadedOnce = wasEntryLoadedOnce(id);
		if (loadedOnce && onlyLoadOnce) {
			return false;
		}

		MdiEntry entry = getEntry(id);
		if (entry != null) {
			if (datasource != null) {
				entry.setDatasource(datasource);
			}
			if (activate) {
				showEntry(entry);
			}
			return true;
		}

		MdiEntry mdiEntry = createEntryByCreationListener(id, datasource, null);
		if (mdiEntry != null) {
			if (onlyLoadOnce) {
				setEntryLoadedOnce(id);
			}
			if (activate) {
				showEntry(mdiEntry);
			}
			return true;
		}

		return false;
	}


	protected abstract void setEntryLoadedOnce(String id);

	protected abstract boolean wasEntryLoadedOnce(String id);

	@Override
	public boolean entryExists(String id) {
		synchronized(mapIdToEntry){
			MdiEntry entry = mapIdToEntry.get(id);
			if (entry == null) {
				return false;
			}
			return entry.isAdded();
		}
	}
		
	private volatile boolean		initialEntrySet;
	
	private volatile String		initialID;
	private volatile String		initialDef;
	private volatile long		initialEntrySetFailTime;
	
	@Override
	public void setInitialEntry(String id, Object datasource, String def){
		if ( id != null ){
			if (!loadEntryByID( id, true, false, datasource)){
				
				initialID = id;
				initialDef = def;
				initialEntrySetFailTime = SystemTime.getMonotonousTime();
				
				showEntryByID( def );
			}	
		}
		initialEntrySet = true;
	}
	
	@Override
	public boolean isInitialEntrySet(){
	
		return( initialEntrySet );
	}
	
	// @see MultipleDocumentInterface#setEntryAutoOpen(java.lang.String, java.lang.Object)
	@Override
	public void setEntryAutoOpen(String id, Object datasource) {
		synchronized( autoOpenLock ){
			Map<String, Object> map = (Map<String, Object>) mapAutoOpen.get(id);
			if (map == null) {
				map = new LightHashMap<>(1);
			}
			map.put("datasource", datasource);
			mapAutoOpen.put(id, map);
			autoOpenUpdated();
		}
	}

	// @see MultipleDocumentInterface#removeEntryAutoOpen(java.lang.String)
	@Override
	public void removeEntryAutoOpen(String id) {
		synchronized( autoOpenLock ){
			mapAutoOpen.remove(id);
			autoOpenUpdated();
		}
	}

	private void
	autoOpenUpdated()
	{
		if ( closed || !initialized ){
			
			return;
		}
		
		synchronized( autoOpenLock ){
			
			if ( autoOpenSaver != null ){
				
				return;	
			}
			
			autoOpenSaver = SimpleTimer.addEvent(
				"autoopensaver",
				SystemTime.getOffsetTime( 60*1000 ),
				(ev)->{
					
					synchronized( autoOpenLock ){
						
						autoOpenSaver = null;
					}					
					
					saveCloseables( true );
				});
		}
	}
	
	protected void setupPluginViews() {

		// When a new Plugin View is added, check out auto-open list to see if
		// the user had it open
		PluginsMenuHelper.getInstance().addPluginAddedViewListener(
				new PluginAddedViewListener() {
					// @see com.biglybt.ui.swt.mainwindow.PluginsMenuHelper.PluginAddedViewListener#pluginViewAdded(com.biglybt.ui.swt.mainwindow.PluginsMenuHelper.IViewInfo)
					@Override
					public void pluginViewAdded(IViewInfo viewInfo) {
						//System.out.println("PluginView Added: " + viewInfo.viewID);
						Object o;
						
						synchronized( autoOpenLock ){
							o = mapAutoOpen.get(viewInfo.viewID);
						}
						
						if (o instanceof Map<?, ?>) {
							processAutoOpenMap(viewInfo.viewID, (Map<?, ?>) o, viewInfo);
						}
					}
				});
	}

	@Override
	public void informAutoOpenSet(MdiEntry entry, Map<String, Object> autoOpenInfo) {
		synchronized( autoOpenLock ){
		
			mapAutoOpen.put(entry.getId(), autoOpenInfo);
			autoOpenUpdated();
		}
	}

	public void loadCloseables() {
		if (closeableConfigFile == null) {
			return;
		}
		try{
			Map<?,?> loadedMap = FileUtil.readResilientConfigFile(closeableConfigFile , true);
			if (loadedMap.isEmpty()) {
				return;
			}
			BDecoder.decodeStrings(loadedMap);

			List<Map> orderedEntries = (List<Map>)loadedMap.get( "_entries_" );

			if ( orderedEntries == null ){
					// migrate old format
				for (Iterator<?> iter = loadedMap.keySet().iterator(); iter.hasNext();) {
					String id = (String) iter.next();
					Object o = loadedMap.get(id);

					if (o instanceof Map<?, ?>) {
						if (!processAutoOpenMap(id, (Map<?, ?>) o, null)) {
							synchronized( autoOpenLock ){
								mapAutoOpen.put(id, o);
							}
						}
					}
				}
			}else{
				for (Map map: orderedEntries){
					String id = (String)map.get( "id" );

					//System.out.println( "loaded " + id );
					Object o = map.get( "value" );
					if (o instanceof Map<?, ?>) {
						if (!processAutoOpenMap(id, (Map<?, ?>) o, null)) {
							synchronized( autoOpenLock ){
								mapAutoOpen.put(id, o);
							}
						}
					}
				}
			}
		}catch( Throwable e ){

			Debug.out( e );

		}finally{

			mapAutoOpenLoaded  = true;
		}
	}

	public void saveCloseables(){
		saveCloseables( false );
	}
	
	@SuppressWarnings({
		"unchecked",
		"rawtypes"
	})
	private void saveCloseables( boolean interim ) {
		if (closeableConfigFile == null) {
			return;
		}

		synchronized( autoOpenLock ){
			if (!mapAutoOpenLoaded) {
				return;
			}
				
			if ( !initialized ){
				
				return;
			}
			
			try{
								
				if ( interim ){
					
					if ( closed ){
						
						return;
					}
				}else{
					
					closed = true;
					
						// update auto open info for closedown
					
					for (Iterator<String> iter = new ArrayList<>(mapAutoOpen.keySet()).iterator(); iter.hasNext();) {
						
						String id = (String) iter.next();
		
						MdiEntry entry = getEntry(id);
		
							// entries that are 'dispose-on-focus-lost' will report as 'not added' if this has occurred
						
						if ( entry != null && ( entry.isAdded() || !entry.isReallyDisposed())){
		
							mapAutoOpen.put(id, entry.getAutoOpenInfo());
		
						}else{
		
							mapAutoOpen.remove(id);
						}
					}
				}
				
				Map map = new HashMap();
	
				List<Map> list = new ArrayList<>(mapAutoOpen.size());
	
				map.put( "_entries_", list );
	
				for ( Map.Entry<String,Object> entry: mapAutoOpen.entrySet()){
	
					Map m = new HashMap();
	
					list.add( m );
	
					String id = entry.getKey();
	
					m.put( "id", id );
					m.put( "value", entry.getValue());
	
					//System.out.println( "saved " + id );
				}
	
				FileUtil.writeResilientConfigFile(closeableConfigFile, map );
	
			}catch( Throwable e ){
	
				Debug.out( e );
			}
		}
	}

	private boolean processAutoOpenMap(String id, Map<?, ?> autoOpenInfo,
			IViewInfo viewInfo) {
		try {
			MdiEntry entry = getEntry(id);
			if (entry != null) {
				return true;
			}

			Object datasource = autoOpenInfo.get("datasource");
			String title = MapUtils.getMapString(autoOpenInfo, "title", id);

			MdiEntry mdiEntry = createEntryByCreationListener(id, datasource, autoOpenInfo);
			if (mdiEntry != null) {
				if (mdiEntry.getTitle().equals("")) {
					mdiEntry.setTitle(title);
				}
				return true;
			}

			String parentID = MapUtils.getMapString(autoOpenInfo, "parentID", SIDEBAR_HEADER_PLUGINS);

			if (viewInfo != null) {
				if (viewInfo.event_listener != null) {
					entry = createEntryFromEventListener(parentID,
							viewInfo.event_listener, id, true, datasource,null);
					if (entry != null) {
						entry.setTitle(title);
					}
				}
			}

			if (entry != null && datasource == null) {
				final MdiEntry fEntry = entry;
				final String dmHash = MapUtils.getMapString(autoOpenInfo, "dm", null);
				if (dmHash != null) {
					CoreFactory.addCoreRunningListener(new CoreRunningListener() {
						@Override
						public void coreRunning(Core core) {
							GlobalManager gm = core.getGlobalManager();
							HashWrapper hw = new HashWrapper(Base32.decode(dmHash));
							DownloadManager dm = gm.getDownloadManager(hw);
							if (dm != null) {
								fEntry.setDatasource(dm);
							}
						}
					});
				} else {
					final List<?> listHashes = MapUtils.getMapList(autoOpenInfo, "dms",
							null);
					if (listHashes != null) {
						CoreFactory.addCoreRunningListener(new CoreRunningListener() {
							@Override
							public void coreRunning(Core core) {
								List<DownloadManager> listDMS = new ArrayList<>(
										1);
								GlobalManager gm = core.getGlobalManager();
								for (Object oDM : listHashes) {
									if (oDM instanceof String) {
										String hash = (String) oDM;
										DownloadManager dm = gm.getDownloadManager(new HashWrapper(
												Base32.decode(hash)));
										if (dm != null) {
											listDMS.add(dm);
										}
									}
									fEntry.setDatasource(listDMS.toArray(new DownloadManager[0]));
								}
							}
						});
					}
				}
			}

			return entry != null;
		} catch (Throwable e) {
			Debug.out(e);
		}
		return false;
	}

	public void addItem(MdiEntry entry) {
		String id = entry.getId();

		synchronized (mapIdToEntry) {
			MdiEntry old = mapIdToEntry.put(id,entry);
			if ( old != null && old != entry ){
				Debug.out( "MDI entry " + id + " already added" );
			}
		}
		
		if ( initialEntrySetFailTime > 0 ){
			
			if ( SystemTime.getMonotonousTime() - initialEntrySetFailTime > 10*1000 ){
				
				initialEntrySetFailTime = 0;
				
			}else{
				
				boolean show = false;
													
				if ( id.equals( initialID )){
					
					initialEntrySetFailTime = 0;
					
					MdiEntry current = getCurrentEntry();

					if ( current == null || current.getId().equals( initialDef )){
						
						show = true;
					}
				}
				
				if ( show ){
					
					showEntry( entry );
				}
			}
		}
	}

	protected void
	itemSelected(MdiEntry entry ){
	}

	@Override
	public void removeItem(MdiEntry entry) {
		removeItem( entry, true );
	}
	
	protected void removeItem(MdiEntry entry, boolean removeChildren ) {
		String id = entry.getId();
		synchronized (mapIdToEntry) {
			mapIdToEntry.remove(id);

			if ( removeChildren ){
				removeChildrenOf(id);
			}
		}
	}
	
	private void removeChildrenOf(String id) {
		if (id == null) {
			return;
		}
		synchronized (mapIdToEntry) {
			MdiEntrySWT[] entriesSWT = getEntriesSWT();
			for (MdiEntrySWT entry : entriesSWT) {
				if (id.equals(entry.getParentID())) {
					String kid_id = entry.getId();
					mapIdToEntry.remove(kid_id);
					removeChildrenOf(kid_id);
				}
			}
		}
	}

	@Override
	public List<MdiEntry> getChildrenOf(String id) {
		if (id == null) {
			return Collections.emptyList();
		}
		List<MdiEntry> list = new ArrayList<>(1);
		synchronized (mapIdToEntry) {
			MdiEntrySWT[] entriesSWT = getEntriesSWT();
			for (MdiEntrySWT entry : entriesSWT) {
				if (id.equals(entry.getParentID())) {
					list.add(entry);
				}
			}
		}
		return list;
	}

	@Override
	public Object updateLanguage(SWTSkinObject skinObject, Object params) {
		MdiEntry[] entries = getEntries();

		for (MdiEntry entry : entries) {
			if (entry instanceof BaseMdiEntry) {
				BaseMdiEntry baseEntry = (BaseMdiEntry) entry;
				baseEntry.updateLanguage();
			}
		}

		return null;
	}

	@Override
	public void setPreferredOrder(String[] preferredOrder) {
		this.preferredOrder = preferredOrder;
	}

	@Override
	public String[] getPreferredOrder() {
		return preferredOrder == null ? new String[0] : preferredOrder;
	}

	@Override
	public int getEntriesCount() {
		synchronized( mapIdToEntry ){
			return mapIdToEntry.size();
		}
	}

	@Override
	public void setCloseableConfigFile(String closeableConfigFile) {
		this.closeableConfigFile = closeableConfigFile;
	}

	public void addListener(MdiSWTMenuHackListener l) {
		synchronized (this) {
			if (listMenuHackListners == null) {
				listMenuHackListners = new ArrayList<>(1);
			}
			if (!listMenuHackListners.contains(l)) {
				listMenuHackListners.add(l);
			}
		}
	}

	public void removeListener(MdiSWTMenuHackListener l) {
		synchronized (this) {
			if (listMenuHackListners == null) {
				listMenuHackListners = new ArrayList<>(1);
			}
			listMenuHackListners.remove(l);
		}
	}

	public MdiSWTMenuHackListener[] getMenuHackListeners() {
		synchronized (this) {
			if (listMenuHackListners == null) {
				return new MdiSWTMenuHackListener[0];
			}
			return listMenuHackListners.toArray(new MdiSWTMenuHackListener[0]);
		}
	}


	public void fillMenu(Menu menu, final MdiEntry entry, String menuID) {
		com.biglybt.pif.ui.menus.MenuItem[] menu_items;

		menu_items = MenuItemManager.getInstance().getAllAsArray(menuID);

		MenuBuildUtils.addPluginMenuItems(menu_items, menu, false, true,
				new MenuBuildUtils.MenuItemPluginMenuControllerImpl(new Object[] {
					entry
				}));

		if (entry != null) {

			menu_items = MenuItemManager.getInstance().getAllAsArray(
					getMenuIdPrefix() + entry.getId());

			if (menu_items.length == 0) {

				if (entry instanceof UISWTView) {

					PluginInterface pi = ((UISWTView) entry).getPluginInterface();

					if (pi != null) {

						final List<String> relevant_sections = new ArrayList<>();

						List<ConfigSectionHolder> sections = ConfigSectionRepository.getInstance().getHolderList();

						for (ConfigSectionHolder cs : sections) {

							if (pi == cs.getPluginInterface()) {

								relevant_sections.add(cs.getConfigSectionID());
							}
						}

						if (relevant_sections.size() > 0) {

							MenuItem mi = pi.getUIManager().getMenuManager().addMenuItem(
									getMenuIdPrefix() + entry.getId(),
									"MainWindow.menu.view.configuration");
							mi.setDisposeWithUIDetach(UIInstance.UIT_SWT);

							mi.addListener(new MenuItemListener() {
								@Override
								public void selected(MenuItem menu, Object target) {
									UIFunctions uif = UIFunctionsManager.getUIFunctions();

									if (uif != null) {

										for (String s : relevant_sections) {

											uif.getMDI().showEntryByID(
                                                    SIDEBAR_SECTION_CONFIG, s);
										}
									}
								}
							});

							menu_items = MenuItemManager.getInstance().getAllAsArray(
									getMenuIdPrefix() + entry.getId());
						}
					}
				}
			}

			MenuBuildUtils.addPluginMenuItems(menu_items, menu, false, true,
					new MenuBuildUtils.MenuItemPluginMenuControllerImpl(new Object[] {
						entry
					}));

			MdiSWTMenuHackListener[] menuHackListeners = getMenuHackListeners();
			for (MdiSWTMenuHackListener l : menuHackListeners) {
				try {
					l.menuWillBeShown(entry, menu);
				} catch (Exception e) {
					Debug.out(e);
				}
			}
			if (currentEntry instanceof SideBarEntrySWT) {
				menuHackListeners = ((SideBarEntrySWT) entry).getMenuHackListeners();
				for (MdiSWTMenuHackListener l : menuHackListeners) {
					try {
						l.menuWillBeShown(entry, menu);
					} catch (Exception e) {
						Debug.out(e);
					}
				}
			}
		}

		menu_items = MenuItemManager.getInstance().getAllAsArray(menuID + "._end_");

		if ( menu_items.length > 0 ){

			MenuBuildUtils.addPluginMenuItems(menu_items, menu, false, true,
					new MenuBuildUtils.MenuItemPluginMenuControllerImpl(new Object[] {
						entry
					}));
		}
	}

	@Override
	public boolean isInitialized(){
		return initialized;
	}

}
