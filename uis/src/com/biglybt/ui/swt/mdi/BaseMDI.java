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

import java.util.*;
import java.util.regex.Pattern;

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.util.*;
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
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.*;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.pifimpl.UISWTInstanceImpl.SWTViewListener;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.views.ViewManagerSWT;
import com.biglybt.ui.swt.views.skin.SkinView;
import com.biglybt.util.DataSourceUtils;
import com.biglybt.util.MapUtils;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;

// TODO: AutoOpen, createEntryByCreationListener, loadEntryByID, createEntryFromBuilder are entangled.
//       Needs to be simplified.
public abstract class BaseMDI
	extends SkinView
	implements MultipleDocumentInterfaceSWT, UIUpdatable
{
	public static final String CLOSEABLECONFIG_INITIALID = "InitialID";
	private final Class<?> pluginDataSourceType;
	private final String viewID;
	private final UISWTView parentView;

	private MdiEntrySWT currentEntry;

	private Map<String, MdiEntryCreationListener> mapIdToCreationListener = new LightHashMap<>();
	private Map<String, MdiEntryCreationListener2> mapIdToCreationListener2 = new LightHashMap<>();

	// Sync changes to entry maps on mapIdEntry
	private final Map<String, BaseMdiEntry> mapIdToEntry = new HashMap<>(8);

	private final List<MdiListener> listeners = new ArrayList<>();

	private final List<MdiEntryLoadedListener> listLoadListeners = new ArrayList<>();

	private List<MdiSWTMenuHackListener> listMenuHackListners;

	private final Object	autoOpenLock = new Object();

	public static final String AUTOOPENINFO_TITLE = "title";
	public static final String AUTOOPENINFO_DS = "datasource";
	public static final String AUTOOPENINFO_PARENTID = "parentID";


	/**
	 * <pre>
	 * mapAutoOpen: Map&lt;ViewID, AutoOpenInfo>
	 * AutoOpenInfo: Map&lt;String, Object>
	 *   "title" : String
	 *   "datasource" : misc
	 *   "parentID" : String
	 * </pre>
	 */
	private LinkedHashMap<String, Map> mapAutoOpen = new LinkedHashMap<>();

	private volatile boolean mapAutoOpenLoaded = false;

	private TimerEvent	autoOpenSaver;
	
	private String[] preferredOrder;

	private String closeableConfigFile = null;

	private SWTViewListener swtViewListener;

	private volatile boolean 	initialized;
	private volatile boolean	closed;

	private String lastValidViewID = null;
	private String initialID;

	public BaseMDI(Class<?> pluginDataSourceType, String viewID, UISWTView parentView) {
		this.pluginDataSourceType = pluginDataSourceType;
		this.viewID = viewID;
		this.parentView = parentView;
	}

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
			l.mdiEntryLoaded(entry);
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
	public final void closeEntry(final String id) {
		closeEntryByID(id);
	}

	@Override
	public final BaseMdiEntry closeEntry(MdiEntry entry, boolean userInitiated ) {
		if (entry == null) {
			return null;
		}
		return closeEntryByID(entry.getViewID(), userInitiated );
	}
	
	@Override
	public BaseMdiEntry closeEntryByID(String id) {
		return( closeEntryByID( id, false ));
	}

	protected BaseMdiEntry closeEntryByID(String id, boolean userInitiated) {
		// We'll get here from closeEntry (BaseMDI), skinObjectDestroyed (TabbedMDI)
		// with a 99% chance the display is disposed
		if (Utils.isDisplayDisposed()) {
			return null;
		}

		// TODO: Children
		BaseMdiEntry removedItem;

		synchronized (mapIdToEntry) {
			removedItem = mapIdToEntry.remove(id);
		}

		removeEntryAutoOpen(id);

		if (removedItem == null) {
			// already closed
			return null;
		}
		
		if (currentEntry == removedItem) {
			setCurrentEntry(null);
		}

		removedItem.closeView( userInitiated );
		
		return removedItem;
	}

	public String getMenuIdPrefix() {
		return "sidebar.";
	}

	/**
	 * @deprecated Use createEntryFromHolder(parentEntryID, null, new UISWTViewEventListenerHolder(id, l, datasource, null), id, closeable, preferedAfterID);
	 */
	@Override
	public final MdiEntry createEntryFromEventListener(String parentID,
	                                                   UISWTViewEventListener l, String id, boolean closeable, Object datasource, String preferedAfterID) {
		// The only thing that uses the method is EMP, which uses a different id based on language
		
		UISWTViewBuilderCore builder = 
			new UISWTViewBuilderCore(
				id,
				null).setInitialDatasource(datasource).setListenerInstantiator(
					new UISWTViewBuilder.UISWTViewEventListenerInstantiator()
					{
						@Override
						public boolean
						supportsMultipleViews()
						{
							return( false );
						}
						
						@Override
						public UISWTViewEventListener 
						createNewInstance(
							UISWTViewBuilder Builder, 
							UISWTView forView)
								throws Exception
						{
							return( l );
						}
						
						@Override
						public String 
						getUID()
						{
							return( parentID + "::" + id );
						}
					}).setParentEntryID(parentID);
		
		return createEntry(builder, closeable);
	}

	// @see MultipleDocumentInterface#createEntryFromSkinRef(java.lang.String, java.lang.String, java.lang.String, java.lang.String, ViewTitleInfo, java.lang.Object, boolean, java.lang.String)
	@Override
	public abstract MdiEntry createEntryFromSkinRef(String parentEntryID, String id,
	                                                String configID, String title, ViewTitleInfo titleInfo, Object params,
	                                                boolean closeable, String preferedAfterID);

	@Override
	public MdiEntrySWT getCurrentEntry() {
		if (isDisposed()) {
			return null;
		}
		return currentEntry;
	}

	protected void
	setCurrentEntry(
		MdiEntrySWT		entry )
	{
		currentEntry = entry;

		if (currentEntry != null) {
			lastValidViewID = currentEntry.getViewID();
		}
	}
	
	@Override
	public MdiEntrySWT[] getEntries() {
		return getEntries( new MdiEntrySWT[0]);
	}

	public <T extends MdiEntry> T[] getEntries( T[] array ) {
		synchronized(mapIdToEntry){
			return mapIdToEntry.values().toArray( array );
		}
	}

	@Override
	public MdiEntrySWT getEntry(String id) {
		synchronized(mapIdToEntry){
			return mapIdToEntry.get(id);
		}
	}

	@Override
	public MdiEntry 
	getEntry(
		String	id, Object datasource )
	{
		if ( datasource != null ){
			
			String hash = DataSourceUtils.getHash( datasource );
	
			if ( hash != null ){
			
				return( getEntry( id + "_" + hash ));
			}
		}
		
		return( getEntry( id ));
	}
	
	/**
	 * @param skinView
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	@Override
	public MdiEntrySWT getEntryBySkinView(Object skinView) {
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
	public String getUpdateUIName() {
		if (currentEntry == null) {
			return "MDI";
		}
		return currentEntry.getViewID();
	}

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

		Map<?, ?> autoOpenInfo;

		synchronized( autoOpenLock ){
			autoOpenInfo = mapAutoOpen.get(id);
		}
		if (autoOpenInfo != null) {
			return createEntryByCreationListener(id, autoOpenInfo) != null;
		}

		boolean created = false;
		String[] autoOpenIDs;
		synchronized( autoOpenLock ){
			autoOpenIDs = mapAutoOpen.keySet().toArray(new String[0]);
		}
		for (String autoOpenID : autoOpenIDs) {
			if (Pattern.matches(id, autoOpenID)) {
				synchronized( autoOpenLock ){
					autoOpenInfo = (Map<?, ?>) mapAutoOpen.get(autoOpenID);
				}
				created |= createEntryByCreationListener(autoOpenID,
						autoOpenInfo) != null;
			}
		}
		return created;
		
	}

	private boolean
	canCreateEntryByCreationListener(String id ){
	
		MdiEntryCreationListener mdiEntryCreationListener = null;
		
		for (String key : mapIdToCreationListener.keySet()) {
			if (Pattern.matches(key, id)) {
				mdiEntryCreationListener = mapIdToCreationListener.get(key);
				break;
			}
		}
		
		if (mdiEntryCreationListener != null) {
			
			return( true );
		}

		MdiEntryCreationListener2 mdiEntryCreationListener2 = null;
		
		for (String key : mapIdToCreationListener2.keySet()) {
			if (Pattern.matches(key, id)) {
				mdiEntryCreationListener2 = mapIdToCreationListener2.get(key);
				break;
			}
		}
		
		if (mdiEntryCreationListener2 != null) {
			
			return( true );
		}

		return( false );
	}
	
	protected MdiEntry
	createEntryByCreationListener(String id, Map<?, ?> autoOpenInfo)
	{
		Object ds = autoOpenInfo == null ? null : autoOpenInfo.get(AUTOOPENINFO_DS);
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
				MdiEntry mdiEntry = mdiEntryCreationListener2.createMDiEntry(this, id, ds, autoOpenInfo);
				if (mdiEntry == null) {
					removeEntryAutoOpen(id);
				}
				return mdiEntry;
			} catch (Exception e) {
				Debug.out(e);
			}
		}

		// Nothing was found.  Register as an Auto-open in case id gets
		// registered later
		setEntryAutoOpen(id, autoOpenInfo);

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
		return loadEntryByID(id, true, false, null);
	}
	
	public boolean
	canShowEntryByID( String id ){
	
		if ( id == null ){
			
			return( false );
		}
						
		if ( canCreateEntryByCreationListener( id )){
			
			return( true );
		}
		
		ViewManagerSWT vi = ViewManagerSWT.getInstance();
		
		UISWTViewBuilderCore builder = vi.getBuilder(viewID, id);
		
		if (builder == null) {
		
			builder = vi.getBuilder(getDataSourceType(), id);
		}	
		
		return( builder != null );
	}

	@Override
	public boolean showEntryByID(String id, Object datasource) {
		return loadEntryByID(id, true, false, datasource);
	}
	
	public boolean popoutEntryByID(String id, Object datasource, boolean onTop ) {
		
		MdiEntry existing = getEntry( id, datasource );
		
		if ( existing != null ){
			
			return( popoutEntry( existing, onTop ));
		}
		
		MdiEntry entry = loadAndGetEntryByID( id, false, false, datasource );
		
		if ( entry != null ){
				
			try{
				return( popoutEntry( entry, onTop ));
				
			}finally{
				
				entry.closeView();
			}
		}
		
		return( false );
	}
	
	public abstract boolean
	popoutEntry(
		MdiEntry	entry,
		boolean		onTop );
	
	@Override
	public Object skinObjectInitialShow(SWTSkinObject skinObject, Object params) {
		
		if ( closeableConfigFile != null ){
			
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
	
						if ( !wait_sem.reserve(20*1000)){
	
							Debug.out( "eh?");
						}
					}
				}
			});
		}

		return null;
	}

	// @see SkinView#skinObjectDestroyed(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectDestroyed(SWTSkinObject skinObject, Object params) {
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

		if ( swtViewListener != null ){
		
			ViewManagerSWT.getInstance().removeSWTViewListener( swtViewListener );
		}
		
		return null;
	}

	@Override
	public void updateUI() {
		MdiEntry currentEntry = getCurrentEntry();
		if (currentEntry != null) {
			currentEntry.updateUI( false );
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
		
		return( loadAndGetEntryByID( id, activate, onlyLoadOnce, datasource ) != null );
	}

	private MdiEntry 
	loadAndGetEntryByID(
		String		id, 
		boolean		activate,                    
		boolean		onlyLoadOnce, 
		Object		datasource ) 
	{

		if (id == null) {
			return( null );
		}

		@SuppressWarnings("deprecation")
		boolean loadedOnce = wasEntryLoadedOnce(id);
		if (loadedOnce && onlyLoadOnce) {
			return( null );
		}

		MdiEntry entry = getEntry(id);
		if (entry != null) {
			if (datasource != null) {
				entry.setDatasource(datasource);
			}
			if (activate) {
				showEntry(entry);
			}
			return( entry );
		}

		Map autoOpenInfo = new HashMap();
		// DataSourceResolves is tooled for Dashboard.. can't use it yet
		//Object value = DataSourceResolver.exportDataSource(datasource);
		//if (value == null) {
		//	value = datasource;
		//}
		// datasource may not be writable to config, but assuming a MdiEntry is
		// created, the autoopen map will be updated with a writable ds.
		autoOpenInfo.put(AUTOOPENINFO_DS, datasource);
		MdiEntry mdiEntry = createEntryByCreationListener(id, autoOpenInfo);
		if (mdiEntry == null) {
			ViewManagerSWT vi = ViewManagerSWT.getInstance();
			UISWTViewBuilderCore builder = vi.getBuilder(viewID, id);
			if (builder == null) {
				builder = vi.getBuilder(getDataSourceType(), id);
			}
			if (builder != null) {
				mdiEntry = createEntry(builder, true);
			}
		}
			
		if (mdiEntry != null) {
			if (onlyLoadOnce) {
				setEntryLoadedOnce(id);
			}
			if (activate) {
				showEntry(mdiEntry);
			}
			return( mdiEntry );
		}

		return( null );
	}

	protected abstract void setEntryLoadedOnce(String id);

	protected abstract boolean wasEntryLoadedOnce(String id);

	@Override
	public boolean entryExists(String id) {
		synchronized(mapIdToEntry){
			return mapIdToEntry.containsKey(id);
		}
	}
	
	private volatile String		initialDef;
	
	@Override
	public void setDefaultEntryID(String def){
		initialDef = def;
	}
	
	// @see MultipleDocumentInterface#setEntryAutoOpen(java.lang.String, java.lang.Object)
	@Override
	public void setEntryAutoOpen(String id, Map autoOpenInfo) {
		synchronized( autoOpenLock ){
			mapAutoOpen.put(id, autoOpenInfo);
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

	@Override
	public boolean willEntryAutoOpen(String id) {
		return mapAutoOpen.containsKey(id);
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
		
		swtViewListener = (forDSTypeOrViewID, builder) -> {
			if (forDSTypeOrViewID != null
					&& !forDSTypeOrViewID.equals(UISWTInstance.VIEW_MAIN)) {
				return;
			}

			Object o;

			synchronized (autoOpenLock) {
				o = mapAutoOpen.get(builder.getViewID());
			}

			if (o instanceof Map<?, ?>) {
				processAutoOpenMap(builder.getViewID(), (Map<?, ?>) o, builder);
			}
		};
		
		ViewManagerSWT.getInstance().addSWTViewListener( swtViewListener );
	}

	private void loadCloseables() {
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
						Map autoOpenInfo = (Map) o;
						if (!processAutoOpenMap(id, autoOpenInfo, null)) {
							synchronized( autoOpenLock ){
								mapAutoOpen.put(id, autoOpenInfo);
							}
						}
					}
				}
			}else{
				initialID = MapUtils.getMapString(loadedMap, CLOSEABLECONFIG_INITIALID, null);
				if (initialID == null) {
					String legacyStartTab = COConfigurationManager.getStringParameter("v3.StartTab", null);
					if (legacyStartTab != null) {
						initialID = legacyStartTab;
						COConfigurationManager.removeParameter("v3.StartTab");
					} else {
						initialID = initialDef;
					}
				}

				for (Map map: orderedEntries){
					String id = (String)map.get( "id" );

					//System.out.println( "loaded " + id );
					Object o = map.get( "value" );
					if (o instanceof Map<?, ?>) {
						Map autoOpenInfo = (Map) o;
						if (!processAutoOpenMap(id, autoOpenInfo, null)) {
							synchronized( autoOpenLock ){
								mapAutoOpen.put(id, autoOpenInfo);
							}
						}
					}
				}
				
				//if (currentEntry != null && !currentEntry.getViewID().equals(initialID)) {
				//	System.out.println("currentEntry set to " + currentEntry.getViewID() + " before initial " + initialID);
				//}
				if (currentEntry == null && initialDef != null) {
					SimpleTimer.addEvent("ShowDefEntry", SystemTime.getOffsetTime(3000),
							event -> {
								if (currentEntry == null) {
									showEntryByID(initialDef);
								}
							});
				}
			}
		}catch( Throwable e ){

			Debug.out( e );

		}finally{

			mapAutoOpenLoaded  = true;
		}
	}

	protected void saveCloseables(){
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
		
						if (entry != null) {

							mapAutoOpen.put(id, entry.getAutoOpenInfo());

						}else{

							mapAutoOpen.remove(id);
						}
					}
				}
				
				Map map = new HashMap();
				
				map.put(CLOSEABLECONFIG_INITIALID, lastValidViewID);
	
				List<Map> list = new ArrayList<>(mapAutoOpen.size());
	
				map.put( "_entries_", list );

				for ( Map.Entry<String,Map> entry: mapAutoOpen.entrySet()){
	
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
			UISWTViewBuilderCore builder) {
		try {
			MdiEntry entry = getEntry(id);
			if (entry != null) {
				return true;
			}

			String title = MapUtils.getMapString(autoOpenInfo, "title", id);

			MdiEntry mdiEntry = createEntryByCreationListener(id, autoOpenInfo);
			if (mdiEntry != null) {
				if (mdiEntry.getTitle().equals("") && !title.isEmpty()) {
					mdiEntry.setTitle(title);
				}
				return true;
			}

			Object datasource = autoOpenInfo.get(AUTOOPENINFO_DS);

			if (builder != null) {
				entry = createEntry(builder, true);
				if (entry != null) {
					if (entry.getTitle().equals("") && !title.isEmpty()) {
						entry.setTitle(title);
					}
					// Auto-Open stores last datasource used before close.
					// Override builder's initialDataSource with stored one.
					entry.setDatasource(datasource);
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

	public void addItem(BaseMdiEntry entry) {
		String id = entry.getViewID();

		synchronized (mapIdToEntry) {
			MdiEntry old = mapIdToEntry.put(id,entry);
			if ( old != null && old != entry ){
				Debug.out( "MDI entry " + id + " already added" );
			}
		}

		setEntryAutoOpen(id, entry.getAutoOpenInfo());

		if (currentEntry == null && id.equals(initialID)) {
			showEntryByID(initialID);
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
		String id = entry.getViewID();
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
			MdiEntrySWT[] entriesSWT = getEntries();
			for (MdiEntrySWT entry : entriesSWT) {
				if (id.equals(entry.getParentID())) {
					String kid_id = entry.getViewID();
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
			MdiEntrySWT[] entriesSWT = getEntries();
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
					getMenuIdPrefix() + entry.getViewID());

			if (menu_items.length == 0) {

				if (entry instanceof UISWTView) {

					PluginInterface pi = ((UISWTView) entry).getPluginInterface();

					if (pi != null && pi != pi.getPluginManager().getDefaultPluginInterface()){

						final List<String> relevant_sections = new ArrayList<>();

						List<ConfigSectionHolder> sections = ConfigSectionRepository.getInstance().getHolderList();

						for (ConfigSectionHolder cs : sections) {

							if (pi == cs.getPluginInterface()) {

								relevant_sections.add(cs.getConfigSectionID());
							}
						}

						if (relevant_sections.size() > 0) {

							MenuItem mi = pi.getUIManager().getMenuManager().addMenuItem(
									getMenuIdPrefix() + entry.getViewID(),
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
									getMenuIdPrefix() + entry.getViewID());
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
			if (currentEntry instanceof BaseMdiEntry) {
				menuHackListeners = ((BaseMdiEntry) entry).getMenuHackListeners();
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


	public static BaseMdiEntry getEntryFromSkinObject(PluginUISWTSkinObject pluginSkinObject) {
		if (pluginSkinObject instanceof SWTSkinObject) {
			Control control = ((SWTSkinObject) pluginSkinObject).getControl();
			while (control != null && !control.isDisposed()) {
				Object entry = control.getData("BaseMDIEntry");
				if (entry instanceof BaseMdiEntry) {
					BaseMdiEntry mdiEntry = (BaseMdiEntry) entry;
					return mdiEntry;
				}
				control = control.getParent();
			}
		}
		return null;
	}

	public UISWTView getParentView() {
		return parentView;
	}

	@Override
	public String getViewID() {
		return viewID;
	}
	
	@Override
	public Class getDataSourceType() {
		return pluginDataSourceType;
	}
}
