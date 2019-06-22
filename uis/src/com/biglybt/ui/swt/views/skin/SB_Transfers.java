/*
 * Created on Oct 21, 2010
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

package com.biglybt.ui.swt.views.skin;

import java.io.File;
import java.util.*;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryListener;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.category.CategoryManagerListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerListener;
import com.biglybt.core.download.impl.DownloadManagerAdapter;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerAdapter;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.stats.transfer.OverallStats;
import com.biglybt.core.stats.transfer.StatsFactory;
import com.biglybt.core.tag.*;
import com.biglybt.core.torrent.HasBeenOpenedListener;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.utils.FormattersImpl;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoManager;
import com.biglybt.ui.mdi.*;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.swt.TorrentUtil;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.mdi.MdiSWTMenuHackListener;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.shells.CoreWaiterSWT;
import com.biglybt.ui.swt.shells.CoreWaiterSWT.TriggerInThread;
import com.biglybt.ui.swt.views.MyTorrentsView;
import com.biglybt.ui.swt.views.PeersGeneralView;
import com.biglybt.ui.swt.views.skin.sidebar.SideBar;
import com.biglybt.ui.swt.views.skin.sidebar.SideBarEntrySWT;
import com.biglybt.ui.swt.views.utils.CategoryUIUtils;
import com.biglybt.ui.swt.views.utils.TagUIUtils;

/**
 * Transfers Sidebar aka "My Torrents" aka "Files"
 * @author TuxPaper
 * @created Oct 21, 2010
 *
 */
public class SB_Transfers
{
	private static final Object AUTO_CLOSE_KEY 		= new Object();
	private static final Object TAG_DATA_KEY		= new Object();
	private static final Object TAG_INDICATOR_KEY	= new Object();
	private static final Object TAG_IMAGE_KEY		= new Object();

	private static final String ID_VITALITY_ACTIVE = "image.sidebar.vitality.dl";

	private static final String ID_VITALITY_ALERT = "image.sidebar.vitality.alert";
	private final HasBeenOpenedListener hasBeenOpenedListener;

	private CategoryListener categoryListener;
	private DownloadManagerListener dmListener;
	private GlobalManagerAdapter gmListener;
	private TimerEventPeriodic timerEventPeriodic;
	private CategoryManagerListener categoryManagerListener;

	private TagManagerListener	tagManagerListener;
	private TagTypeListener tagTypeListener;
	private TagListener tagListener;

	private final Object	tag_listener_lock = new Object();
	private ParameterListener paramTagsInSidebarListener;
	private ParameterListener paramTagGroupsInSidebarListener;
	private ParameterListener paramCatInSidebarListener;

	private long last_dl_entry_load;
	
	private Set<MdiEntry>				redraw_pending = new HashSet<>();
		
	private FrequencyLimitedDispatcher	redraw_disp = 
		new FrequencyLimitedDispatcher(
			new AERunnable(){
				
				@Override
				public void runSupport(){
					
					List<MdiEntry> to_do;
					
					synchronized( redraw_pending ){
						
						to_do = new ArrayList<MdiEntry>( redraw_pending );
						
						redraw_pending.clear();
					}
					
					for ( MdiEntry e: to_do ){
						
						e.redraw();
					}
				}
			}, 2500 );
	
	private void
	requestRedraw(
		MdiEntry	entry )
	{
		synchronized( redraw_pending ){
			
			if ( !redraw_pending.contains( entry )){
				
				redraw_pending.add( entry );
				
				redraw_disp.dispatch();
			}
		}
	}
	
	public static class stats
	{
		int total = 0;
		
		int numSeeding = 0;

		int numDownloading = 0;

		int numQueued = 0;

		int numComplete = 0;

		int numIncomplete = 0;

		int numErrorComplete = 0;

		String errorInCompleteTooltip;

		int numErrorInComplete = 0;

		String errorCompleteTooltip;

		int numUnOpened = 0;

		int numStoppedIncomplete = 0;

		boolean includeLowNoise;

		long	newestIncompleteDownloadTime = 0;

		private boolean
		sameAs(
			stats		other )
		{
			return(
					total							== other.total &&
					numSeeding 						== other.numSeeding &&
					numDownloading 					== other.numDownloading &&
					numQueued 						== other.numQueued &&
					numComplete 					== other.numComplete &&
					numIncomplete 					== other.numIncomplete &&
					numErrorComplete 				== other.numErrorComplete &&
					numErrorInComplete 				== other.numErrorInComplete &&
					numUnOpened 					== other.numUnOpened &&
					numStoppedIncomplete 			== other.numStoppedIncomplete &&
					newestIncompleteDownloadTime 	== other.newestIncompleteDownloadTime );
		}

		private void
		copyFrom(
			stats		other )
		{
			total								= other.total;
			numSeeding 							= other.numSeeding;
			numDownloading 						= other.numDownloading;
			numQueued 							= other.numQueued;
			numComplete 						= other.numComplete;
			numIncomplete 						= other.numIncomplete;
			numErrorComplete 					= other.numErrorComplete;
			errorInCompleteTooltip 				= other.errorInCompleteTooltip;
			numErrorInComplete 					= other.numErrorInComplete;
			errorCompleteTooltip 				= other.errorCompleteTooltip;
			numUnOpened	 						= other.numUnOpened;
			numStoppedIncomplete				= other.numStoppedIncomplete;
			includeLowNoise 					= other.includeLowNoise;
			newestIncompleteDownloadTime	 	= other.newestIncompleteDownloadTime;
		}
	}

	private final Object	statsLock = new Object();

	private stats statsWithLowNoise;

	private stats statsNoLowNoise;

	private final CopyOnWriteList<countRefreshListener> listeners = new CopyOnWriteList<>();

	private boolean first = true;

	private Core core;
	private long			coreCreateTime;

	private FrequencyLimitedDispatcher refresh_limiter;

	private TimerEventPeriodic timerEventShowUptime;

	private ParameterListener configListenerShow;

	protected boolean	header_show_uptime;
	protected boolean	header_show_rates;
	protected volatile OverallStats totalStats;

	private boolean	show_tag_groups;
	private boolean	show_tag_tab_views;

	public SB_Transfers(final MultipleDocumentInterfaceSWT mdi, boolean vuze_ui ) {
		statsNoLowNoise = new stats();
		statsNoLowNoise.includeLowNoise = false;
		statsWithLowNoise = new stats();
		statsWithLowNoise.includeLowNoise = true;
		
		if ( vuze_ui ){
			refresh_limiter = new FrequencyLimitedDispatcher(
					new AERunnable() {
						@Override
						public void runSupport() {
							refreshAllLibrariesSupport();
						}
					}, 250);
			refresh_limiter.setSingleThreaded();
	
			MdiEntryCreationListener libraryCreator = new MyMdiEntryCreationListener(mdi);
			mdi.registerEntry(SideBar.SIDEBAR_SECTION_LIBRARY, libraryCreator);
			mdi.registerEntry("library", libraryCreator);
	
			mdi.registerEntry(SideBar.SIDEBAR_SECTION_LIBRARY_DL,
					new MdiEntryCreationListener() {
						@Override
						public MdiEntry createMDiEntry(String id) {
							return createDownloadingEntry(mdi);
						}
					});
	
			mdi.registerEntry(SideBar.SIDEBAR_SECTION_LIBRARY_CD,
					new MdiEntryCreationListener() {
						@Override
						public MdiEntry createMDiEntry(String id) {
							return createSeedingEntry(mdi);
						}
					});
	
			mdi.registerEntry(SideBar.SIDEBAR_SECTION_LIBRARY_UNOPENED,
					new MdiEntryCreationListener() {
						@Override
						public MdiEntry createMDiEntry(String id) {
							return createUnopenedEntry(mdi);
						}
					});
	
	
	
			CoreFactory.addCoreRunningListener(new CoreRunningListener() {
				@Override
				public void coreRunning(Core core) {
					totalStats = StatsFactory.getStats();
					setupViewTitleWithCore(core);
				}
			});
	
			hasBeenOpenedListener = new HasBeenOpenedListener() {
				@Override
				public void hasBeenOpenedChanged(DownloadManager dm, boolean opened) {
					synchronized (statsLock) {
						recountItems();
						refreshAllLibraries();
					}
				}
			};
			PlatformTorrentUtils.addHasBeenOpenedListener(hasBeenOpenedListener);
		
			mdi.addListener(new MdiEntryLoadedListener() {
				@Override
				public void mdiEntryLoaded(MdiEntry entry) {
					if (MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS.equals(entry.getId())) {
						addHeaderMenu();
					}
				}
			});
		}else{
			
			CoreFactory.addCoreRunningListener(new CoreRunningListener() {
				@Override
				public void coreRunning(Core core) {
					totalStats = StatsFactory.getStats();
					setupViewTitleWithCore(core);
				}
			});
			
			hasBeenOpenedListener = null;
		}

		timerEventShowUptime = SimpleTimer.addPeriodicEvent(
				"SBLV:updater",
				60 * 1000,
				new TimerEventPerformer() {
					@Override
					public void
					perform(
							TimerEvent event) {
						if (header_show_uptime) {

							triggerCountRefreshListeners();
						}
					}
				});

		configListenerShow = new ParameterListener() {
			private TimerEventPeriodic rate_event;

			@Override
			public void
			parameterChanged(
					String name) {
				header_show_uptime = COConfigurationManager.getBooleanParameter("MyTorrentsView.showuptime");
				header_show_rates = COConfigurationManager.getBooleanParameter("MyTorrentsView.showrates");

				triggerCountRefreshListeners();

				refreshAllLibraries();
				
				synchronized (this) {

					if (header_show_rates) {

						if (rate_event == null) {

							rate_event = SimpleTimer.addPeriodicEvent(
									"SBLV:rate-updater",
									1 * 1000,
									new TimerEventPerformer() {
										@Override
										public void
										perform(
												TimerEvent event) {
											triggerCountRefreshListeners();
										}
									});
						}
					} else {

						if (rate_event != null) {

							rate_event.cancel();

							rate_event = null;
						}
					}
				}
			}
		};
		COConfigurationManager.addAndFireParameterListeners(
				new String[]{
						"MyTorrentsView.showuptime", 
						"MyTorrentsView.showrates",
						SideBar.SIDEBAR_SECTION_LIBRARY + ".viewmode"

				}, configListenerShow);

	}

	protected void addHeaderMenu() {
		PluginInterface pi = PluginInitializer.getDefaultInterface();
		UIManager uim = pi.getUIManager();

		assert uim != null;
		final MenuManager menuManager = uim.getMenuManager();

		MenuItem menuItem;

		menuItem = menuManager.addMenuItem("sidebar."
				+ MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS,
				"MyTorrentsView.menu.setCategory.add");
		menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
		menuItem.addListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				CategoryUIUtils.showCreateCategoryDialog(null);
			}
		});

			// cats in sidebar

		menuItem.addFillListener(new MenuItemFillListener() {
			@Override
			public void menuWillBeShown(MenuItem menu, Object data) {
				menu.setVisible(COConfigurationManager.getBooleanParameter("Library.CatInSideBar"));
			}
		});

		menuItem = menuManager.addMenuItem("sidebar."
				+ MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS,
				"ConfigView.section.style.CatInSidebar");
		menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
		menuItem.setStyle(MenuItem.STYLE_CHECK);
		menuItem.addListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				boolean b = COConfigurationManager.getBooleanParameter("Library.CatInSideBar");
				COConfigurationManager.setParameter("Library.CatInSideBar", !b);
			}
		});
		menuItem.addFillListener(new MenuItemFillListener() {
			@Override
			public void menuWillBeShown(MenuItem menu, Object data) {
				menu.setVisible(CategoryManager.getCategories().length > 0);
				menu.setData(COConfigurationManager.getBooleanParameter("Library.CatInSideBar"));
			}
		});

			// show tags in sidebar

		TagUIUtils.setupSideBarMenus( menuManager );
	}

	protected MdiEntry createUnopenedEntry(MultipleDocumentInterface mdi) {
		MdiEntry infoLibraryUn = mdi.createEntryFromSkinRef(
				SideBar.SIDEBAR_HEADER_TRANSFERS,
				SideBar.SIDEBAR_SECTION_LIBRARY_UNOPENED, "library",
				"{sidebar.LibraryUnopened}", null, null, true,
				SideBar.SIDEBAR_SECTION_LIBRARY);
		
		infoLibraryUn.setImageLeftID("image.sidebar.unopened");

		addGeneralLibraryMenus(infoLibraryUn,SideBar.SIDEBAR_SECTION_LIBRARY_UNOPENED);
		
		infoLibraryUn.setViewTitleInfo(new ViewTitleInfo() {
			@Override
			public Object getTitleInfoProperty(int propertyID) {
				if (propertyID == TITLE_INDICATOR_TEXT
						&& statsNoLowNoise.numUnOpened > 0) {
					return "" + statsNoLowNoise.numUnOpened;
				}
				return null;
			}
		});
		
		infoLibraryUn.addListener(
			new MdiCloseListener(){
				
				@Override
				public void 
				mdiEntryClosed(
					MdiEntry entry, 
					boolean userClosed)
				{
					if ( userClosed ){
						if ( Constants.isCVSVersion()){
							Debug.out( "New entry closed by user" );
						}
						COConfigurationManager.setParameter( "Show New In Side Bar", false );
					}
				}
			});
		
		return infoLibraryUn;
	}

	private static void addGeneralLibraryMenus( MdiEntry entry, String id ){
		addMenuUnwatched( id );
		
		addMenuCollapseAll( entry, id );
	}
	
	private static void addMenuUnwatched(String id) {
		PluginInterface pi = PluginInitializer.getDefaultInterface();
		UIManager uim = pi.getUIManager();
		MenuManager menuManager = uim.getMenuManager();

		MenuItem menuItem = menuManager.addMenuItem("sidebar." + id,
				"v3.activity.button.watchall");
		menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
		menuItem.addListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				CoreWaiterSWT.waitForCore(TriggerInThread.ANY_THREAD,
						new CoreRunningListener() {
							@Override
							public void coreRunning(Core core) {
								GlobalManager gm = core.getGlobalManager();
								List<?> downloadManagers = gm.getDownloadManagers();
								for (Iterator<?> iter = downloadManagers.iterator(); iter.hasNext();) {
									DownloadManager dm = (DownloadManager) iter.next();

									if (!PlatformTorrentUtils.getHasBeenOpened(dm)
											&& dm.getAssumedComplete()) {
										PlatformTorrentUtils.setHasBeenOpened(dm, true);
									}
								}
							}
						});
			}
		});
	}
	
	private static void addMenuCollapseAll( MdiEntry entry, String id ){
		PluginInterface pi = PluginInitializer.getDefaultInterface();
		UIManager uim = pi.getUIManager();
		MenuManager menuManager = uim.getMenuManager();
		MenuItem menuItem = menuManager.addMenuItem("sidebar." + id, "menu.collapse.all");
		menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
		menuItem.addListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				CoreWaiterSWT.waitForCore(TriggerInThread.ANY_THREAD,
						new CoreRunningListener() {
							@Override
							public void coreRunning(Core core) {
								Composite comp = ((MdiEntrySWT)entry).getComposite();
								process( comp );
							}	
							
							private void
							process(
								Composite	comp )
							{
									// don't like this but meh
								
								Object obj = comp.getData( "MyTorrentsView.instance" );
								
								if ( obj != null ){
									
									((MyTorrentsView)obj).collapseAll();
								}
								
								Control[] kids = comp.getChildren();
								
								for ( Control k: kids ){
									
									if ( k instanceof Composite ){
										
										process((Composite)k);
									}
								}
							}
						});
			}
		});
	}

	/**
	 * @param mdi
	 * @return
	 *
	 * @since 4.5.1.1
	 */
	protected MdiEntry createSeedingEntry(MultipleDocumentInterface mdi) {
		ViewTitleInfo titleInfoSeeding = new ViewTitleInfo() {
			@Override
			public Object getTitleInfoProperty(int propertyID) {
				if (propertyID == TITLE_INDICATOR_TEXT) {
					return null; //numSeeding + " of " + numComplete;
				}

				if (propertyID == TITLE_INDICATOR_TEXT_TOOLTIP) {
					return MessageText.getString("sidebar.LibraryCD.tooltip",
							new String[] {
								"" + statsNoLowNoise.numComplete,
								"" + statsNoLowNoise.numSeeding
							});
				}
				return null;
			}
		};

		MdiEntry entry = mdi.createEntryFromSkinRef(
				SideBar.SIDEBAR_HEADER_TRANSFERS, SideBar.SIDEBAR_SECTION_LIBRARY_CD,
				"library", "{sidebar.LibraryDL}",
				titleInfoSeeding, null, false, null);
		entry.setImageLeftID("image.sidebar.downloading");

		addGeneralLibraryMenus(entry,SideBar.SIDEBAR_SECTION_LIBRARY_CD);
		
		MdiEntryVitalityImage vitalityImage = entry.addVitalityImage(ID_VITALITY_ALERT);
		vitalityImage.setVisible(false);

		entry.setViewTitleInfo(titleInfoSeeding);

		return entry;
	}

	protected MdiEntry createDownloadingEntry(MultipleDocumentInterface mdi) {
		final MdiEntry[] entry_holder = { null };

		ViewTitleInfo titleInfoDownloading = new ViewTitleInfo() {
			private long	max_incomp_dl_time;
			@Override
			public Object getTitleInfoProperty(int propertyID) {
				if (propertyID == TITLE_INDICATOR_TEXT) {
					if ( COConfigurationManager.getBooleanParameter( "Request Attention On New Download" )){

						if ( coreCreateTime > 0 ){

							if ( max_incomp_dl_time == 0 ){

								max_incomp_dl_time = coreCreateTime;
							}

							if ( statsNoLowNoise.newestIncompleteDownloadTime > max_incomp_dl_time ){

								MdiEntry entry = entry_holder[0];

								if ( entry != null ){

									max_incomp_dl_time = statsNoLowNoise.newestIncompleteDownloadTime;

									entry.requestAttention();
								}
							}
						}
					}

					int	current = statsNoLowNoise.numIncomplete;

					if (current > 0){
						return current + ""; // + " of " + numIncomplete;
					}
				}else if (propertyID == TITLE_INDICATOR_TEXT_TOOLTIP) {
					return MessageText.getString("sidebar.LibraryDL.tooltip",
							new String[] {
								"" + statsNoLowNoise.numIncomplete,
								"" + statsNoLowNoise.numDownloading
							});
				}else if (propertyID == TITLE_INDICATOR_COLOR) {
					
					if ( COConfigurationManager.getBooleanParameter("LibraryDL.UseDefaultIndicatorColor")){
						return( null );
					}else{
						if ( statsNoLowNoise.numDownloading > 0 ){
							return( new int[]{ 96, 160, 96 });
						}else if ( statsNoLowNoise.numErrorInComplete > 0 ){
							return( new int[]{ 132, 16, 58 } );
						}else{
							return( null );
						}
					}
				}

				return null;
			}
		};
		MdiEntry entry = mdi.createEntryFromSkinRef(
				SideBar.SIDEBAR_HEADER_TRANSFERS, SideBar.SIDEBAR_SECTION_LIBRARY_DL,
				"library", "{sidebar.LibraryDL}",
				titleInfoDownloading, null, true, null);

		entry_holder[0] = entry;

		entry.setImageLeftID("image.sidebar.downloading");

		addGeneralLibraryMenus(entry,SideBar.SIDEBAR_SECTION_LIBRARY_DL);
		
		entry.addListener(
			new MdiCloseListener(){
				
				@Override
				public void 
				mdiEntryClosed(MdiEntry entry, boolean userClosed){
				
					if ( userClosed ){
						if ( Constants.isCVSVersion()){
							Debug.out( "Downloading entry closed by user" );
						}
						
						COConfigurationManager.setParameter( "Show Downloading In Side Bar", false );
					}
				}
			});
		
		MdiEntryVitalityImage vitalityImage = entry.addVitalityImage(ID_VITALITY_ACTIVE);
		vitalityImage.setVisible(false);

		vitalityImage = entry.addVitalityImage(ID_VITALITY_ALERT);
		vitalityImage.setVisible(false);

		String parentID = "sidebar." + SideBar.SIDEBAR_SECTION_LIBRARY_DL;

		MenuManager menu_manager = PluginInitializer.getDefaultInterface().getUIManager().getMenuManager();
		
		MenuItem mi = menu_manager.addMenuItem( parentID, "menu.use.default.indicator.color" );
		mi.setDisposeWithUIDetach(UIInstance.UIT_SWT);
		mi.setStyle( MenuItem.STYLE_CHECK );
		mi.setData( COConfigurationManager.getBooleanParameter("LibraryDL.UseDefaultIndicatorColor"));
		
		mi.addListener(
			new MenuItemListener()
			{
				@Override
				public void
				selected(
					MenuItem mi, Object target )
				{
					COConfigurationManager.setParameter("LibraryDL.UseDefaultIndicatorColor", mi.isSelected());
					
					entry.redraw();
				}
			});
		
		return entry;
	}

	protected void
	setupViewTitleWithCore(Core _core) {

		synchronized( SB_Transfers.class ){
			if (!first) {
				return;
			}
			first = false;

			core			= _core;
			coreCreateTime 	= core.getCreateTime();
		}

		categoryListener = new CategoryListener() {

			@Override
			public void downloadManagerRemoved(Category cat, DownloadManager removed) {
				RefreshCategorySideBar(cat);
			}

			@Override
			public void downloadManagerAdded(Category cat, DownloadManager manager) {
				RefreshCategorySideBar(cat);
			}
		};

		paramCatInSidebarListener = new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				if (Utils.isAZ2UI()) {
					return;
				}

				Category[] categories = CategoryManager.getCategories();
				if (categories.length == 0) {
					return;
				}

				boolean catInSidebar = COConfigurationManager.getBooleanParameter("Library.CatInSideBar");
				if (catInSidebar) {
					if (categoryManagerListener != null) {
						return;
					}

					categoryManagerListener = new CategoryManagerListener() {

						@Override
						public void categoryRemoved(Category category) {
							removeCategory(category);
						}

						@Override
						public void categoryChanged(Category category) {
							RefreshCategorySideBar(category);
						}

						@Override
						public void categoryAdded(Category category) {
							Category[] categories = CategoryManager.getCategories();
							if (categories.length == 3) {
								for (Category cat : categories) {
									setupCategory(cat);
								}
							} else {
								setupCategory(category);
							}
						}
					};
					CategoryManager.addCategoryManagerListener(categoryManagerListener);
					if (categories.length > 2) {
						for (Category category : categories) {
							category.addCategoryListener(categoryListener);
							setupCategory(category);
						}
					}

				} else {

					if (categoryManagerListener != null) {
						CategoryManager.removeCategoryManagerListener(categoryManagerListener);
						categoryManagerListener = null;
					}
					for (Category category : categories) {
						category.removeCategoryListener(categoryListener);
						removeCategory(category);
					}
				}
			}
		};
		COConfigurationManager.addAndFireParameterListener("Library.CatInSideBar",	paramCatInSidebarListener);

		show_tag_groups = Utils.isAZ3UI() && COConfigurationManager.getBooleanParameter("Library.TagGroupsInSideBar");

		paramTagsInSidebarListener = new ParameterListener() {

			@Override
			public void parameterChanged(String parameter ) {

				if ( parameter == null || parameter.equals( "Library.TagInTabBar" )){
				
					show_tag_tab_views = COConfigurationManager.getBooleanParameter("Library.TagInTabBar");
					
					if ( parameter != null ){
						
							// this will cause us to come back through here with the tags-in-sidebar change and
							// remove/add tag views as necessary
						
						COConfigurationManager.setParameter( "Library.TagInSideBar", show_tag_tab_views );
					}
				}
				
				if ( parameter == null || parameter.equals( "Library.TagInSideBar")){
					
					boolean tagInSidebar = COConfigurationManager.getBooleanParameter("Library.TagInSideBar");
	
					if (tagInSidebar) {
	
						addTagManagerListeners();
	
					} else {
	
						removeTagManagerListeners(true);
					}
				}
			}

		};
		
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{ "Library.TagInSideBar", "Library.TagInTabBar" }, paramTagsInSidebarListener);

		paramTagGroupsInSidebarListener = new ParameterListener() {

			@Override
			public void parameterChanged(String parameterName) {
							
				removeTagManagerListeners(true);
					
				show_tag_groups = Utils.isAZ3UI() && COConfigurationManager.getBooleanParameter("Library.TagGroupsInSideBar");

				addTagManagerListeners();
			}
		};
				
		COConfigurationManager.addParameterListener("Library.TagGroupsInSideBar", paramTagGroupsInSidebarListener);

		
		final GlobalManager gm = core.getGlobalManager();
		dmListener = new DownloadManagerAdapter() {
			@Override
			public void stateChanged(DownloadManager dm, int state) {
				stateChanged(dm, state, statsNoLowNoise);
				stateChanged(dm, state, statsWithLowNoise);
			}

			public void stateChanged(DownloadManager dm, int state, stats stats) {
				if (!stats.includeLowNoise
						&& PlatformTorrentUtils.isAdvancedViewOnly(dm)) {
					return;
				}

				synchronized( statsLock ){
					updateDMCounts(dm);

					boolean complete = dm.getAssumedComplete();
					Boolean wasErrorStateB = (Boolean) dm.getUserData("wasErrorState");
					boolean wasErrorState = wasErrorStateB != null && wasErrorStateB.booleanValue();
					boolean isErrorState = state == DownloadManager.STATE_ERROR;
					if (isErrorState != wasErrorState) {
						int rel = isErrorState ? 1 : -1;
						if (complete) {
							stats.numErrorComplete += rel;
						} else {
							stats.numErrorInComplete += rel;
						}
						updateErrorTooltip(gm,stats);
						dm.setUserData("wasErrorState", Boolean.valueOf(isErrorState));
					}
					refreshAllLibraries();
				}
			}

			@Override
			public void completionChanged(DownloadManager dm, boolean completed) {
				completionChanged(dm, completed, statsNoLowNoise);
				completionChanged(dm, completed, statsWithLowNoise);
			}

			public void completionChanged(DownloadManager dm, boolean completed,
					stats stats) {
				if (!stats.includeLowNoise
						&& PlatformTorrentUtils.isAdvancedViewOnly(dm)) {
					return;
				}

				synchronized( statsLock ){
					int dm_state = updateDMCounts(dm);

					if (completed) {
						stats.numComplete++;
						stats.numIncomplete--;
						if (dm_state == DownloadManager.STATE_ERROR) {
							stats.numErrorComplete++;
							stats.numErrorInComplete--;
						}
						if (dm_state == DownloadManager.STATE_STOPPED) {
							statsNoLowNoise.numStoppedIncomplete--;
						}

					} else {
						stats.numComplete--;
						stats.numIncomplete++;

						if (dm_state == DownloadManager.STATE_ERROR) {
							stats.numErrorComplete--;
							stats.numErrorInComplete++;
						}
						if (dm_state == DownloadManager.STATE_STOPPED) {
							statsNoLowNoise.numStoppedIncomplete++;
						}
					}
					updateErrorTooltip( gm, stats);
					recountItems();
					refreshAllLibraries();
				}
			}
		};

		gmListener = new GlobalManagerAdapter() {
			@Override
			public void downloadManagerRemoved(DownloadManager dm) {
				downloadManagerRemoved(dm, statsNoLowNoise);
				downloadManagerRemoved(dm, statsWithLowNoise);
			}

			public void downloadManagerRemoved(DownloadManager dm, stats stats) {
				if (!stats.includeLowNoise
						&& PlatformTorrentUtils.isAdvancedViewOnly(dm)) {
					return;
				}

				synchronized (statsLock) {
					if (dm.getAssumedComplete()) {
						stats.numComplete--;
						Boolean wasDownloadingB = (Boolean) dm.getUserData("wasDownloading");
						if (wasDownloadingB != null && wasDownloadingB.booleanValue()) {
							stats.numDownloading--;
						}
					} else {
						stats.numIncomplete--;
						Boolean wasSeedingB = (Boolean) dm.getUserData("wasSeeding");
						if (wasSeedingB != null && wasSeedingB.booleanValue()) {
							stats.numSeeding--;
						}
					}

					Boolean wasStoppedB = (Boolean) dm.getUserData("wasStopped");
					boolean wasStopped = wasStoppedB != null && wasStoppedB.booleanValue();
					if (wasStopped) {
						if (!dm.getAssumedComplete()) {
							stats.numStoppedIncomplete--;
						}
					}
					Boolean wasQueuedB = (Boolean) dm.getUserData("wasQueued");
					boolean wasQueued = wasQueuedB != null && wasQueuedB.booleanValue();
					if (wasQueued) {
						stats.numQueued--;
					}
					
					recountItems();
					refreshAllLibraries();
				}

				dm.removeListener(dmListener);
			}

			@Override
			public void downloadManagerAdded(DownloadManager dm) {
				dm.addListener(dmListener, false);

				synchronized (statsLock) {
					downloadManagerAdded(dm, statsNoLowNoise);
					downloadManagerAdded(dm, statsWithLowNoise);
				}
			}

			public void downloadManagerAdded(DownloadManager dm, stats stats) {
				if (!stats.includeLowNoise
						&& PlatformTorrentUtils.isAdvancedViewOnly(dm)) {
					return;
				}
				boolean assumed_complete = dm.getAssumedComplete();

				synchronized (statsLock) {
					if (dm.isPersistent() && dm.getTorrent() != null && !assumed_complete) {  // ignore borked torrents as their create time is inaccurate
						stats.newestIncompleteDownloadTime = Math.max(stats.newestIncompleteDownloadTime, dm.getCreationTime());
					}
					int dm_state = dm.getState();
					if (assumed_complete) {
						stats.numComplete++;
						if (dm_state == DownloadManager.STATE_SEEDING) {
							stats.numSeeding++;
						}
					} else {
						stats.numIncomplete++;
						if (dm_state == DownloadManager.STATE_DOWNLOADING) {
							dm.setUserData("wasDownloading", Boolean.TRUE);
							stats.numDownloading++;
						} else {
							dm.setUserData("wasDownloading", Boolean.FALSE);
						}
					}
					
					recountItems();
					refreshAllLibraries();
				}
			}
		};
		gm.addListener(gmListener, false);

		resetStats( gm, dmListener, statsWithLowNoise, statsNoLowNoise );

		refreshAllLibraries();

		timerEventPeriodic = SimpleTimer.addPeriodicEvent(
				"header:refresh",
				60 * 1000,
				new TimerEventPerformer() {

					@Override
					public void
					perform(
							TimerEvent event) {
						stats withNoise = new stats();
						stats noNoise = new stats();

						noNoise.includeLowNoise = false;
						withNoise.includeLowNoise = true;

						synchronized (statsLock) {

							resetStats(gm, null, withNoise, noNoise);

							boolean fixed = false;

							if (!withNoise.sameAs(statsWithLowNoise)) {
								statsWithLowNoise.copyFrom(withNoise);
								fixed = true;
							}

							if (!noNoise.sameAs(statsNoLowNoise)) {
								statsNoLowNoise.copyFrom(noNoise);
								fixed = true;
							}

							if (fixed) {

								updateErrorTooltip(gm, statsWithLowNoise);
								updateErrorTooltip(gm, statsNoLowNoise);

								refreshAllLibraries();
							}
						}
					}
				});
	}

	private void
	resetStats(
		GlobalManager				gm,
		DownloadManagerListener		listener,
		stats						statsWithLowNoise,
		stats						statsNoLowNoise )
	{
		List<DownloadManager> downloadManagers = gm.getDownloadManagers();
		for (Iterator<DownloadManager> iter = downloadManagers.iterator(); iter.hasNext();) {
			DownloadManager dm = iter.next();
			boolean lowNoise = PlatformTorrentUtils.isAdvancedViewOnly(dm);
			boolean assumed_complete = dm.getAssumedComplete();
			if ( dm.isPersistent() && dm.getTorrent() != null && !assumed_complete ){	// ignore borked torrents as their create time is inaccurate
				long createTime = dm.getCreationTime();
				statsWithLowNoise.newestIncompleteDownloadTime = Math.max( statsWithLowNoise.newestIncompleteDownloadTime, createTime);
				if (!lowNoise) {
					statsNoLowNoise.newestIncompleteDownloadTime = Math.max( statsNoLowNoise.newestIncompleteDownloadTime, createTime);
				}
			}
			if ( listener != null ){
				dm.addListener(listener, false);
			}

			int dm_state = dm.getState();
			if (dm_state == DownloadManager.STATE_STOPPED) {
				dm.setUserData("wasStopped", Boolean.TRUE);
				if (!dm.getAssumedComplete()) {
					statsWithLowNoise.numStoppedIncomplete++;
				}
				if (!lowNoise) {
					if (!dm.getAssumedComplete()) {
						statsNoLowNoise.numStoppedIncomplete++;
					}
				}
			} else {
				dm.setUserData("wasStopped", Boolean.FALSE);
			}

			if (dm_state == DownloadManager.STATE_QUEUED) {
				dm.setUserData("wasQueued", Boolean.TRUE);
				statsWithLowNoise.numQueued++;
				if (!lowNoise) {
					statsNoLowNoise.numQueued++;
				}
			} else {
				dm.setUserData("wasQueued", Boolean.FALSE);
			}
			if (dm.getAssumedComplete()) {
				statsWithLowNoise.numComplete++;
				if (!lowNoise) {
					statsNoLowNoise.numComplete++;
				}
				if (dm_state == DownloadManager.STATE_SEEDING) {
					dm.setUserData("wasSeeding", Boolean.TRUE);
					statsWithLowNoise.numSeeding++;
					if (!lowNoise) {
						statsNoLowNoise.numSeeding++;
					}
				} else {
					dm.setUserData("wasSeeding", Boolean.FALSE);
				}
			} else {
				statsWithLowNoise.numIncomplete++;
				if (!lowNoise) {
					statsNoLowNoise.numIncomplete++;
				}
				if (dm_state == DownloadManager.STATE_DOWNLOADING) {
					statsWithLowNoise.numDownloading++;
					if (!lowNoise) {
						statsNoLowNoise.numDownloading++;
					}
				}
			}

			if (!PlatformTorrentUtils.getHasBeenOpened(dm) && dm.getAssumedComplete()) {
				statsNoLowNoise.numUnOpened++;
			}
			
			statsWithLowNoise.total++;
			if ( !lowNoise ){
				statsNoLowNoise.total++;
			}
		}

		statsWithLowNoise.numUnOpened = statsNoLowNoise.numUnOpened;
	}

	private void
	updateErrorTooltip(GlobalManager gm, stats stats)
	{
		if (stats.numErrorComplete < 0) {
			stats.numErrorComplete = 0;
		}
		if (stats.numErrorInComplete < 0) {
			stats.numErrorInComplete = 0;
		}

		if (stats.numErrorComplete > 0 || stats.numErrorInComplete > 0) {

			String comp_error = null;
			String incomp_error = null;

			List<?> downloads = gm.getDownloadManagers();

			for (int i = 0; i < downloads.size(); i++) {

				DownloadManager download = (DownloadManager) downloads.get(i);

				if (download.getState() == DownloadManager.STATE_ERROR) {

					if (download.getAssumedComplete()) {

						if (comp_error == null) {

							comp_error = download.getDisplayName() + ": "
									+ download.getErrorDetails();
						} else {

							comp_error += "...";
						}
					} else {
						if (incomp_error == null) {

							incomp_error = download.getDisplayName() + ": "
									+ download.getErrorDetails();
						} else {

							incomp_error += "...";
						}
					}
				}
			}

			stats.errorCompleteTooltip = comp_error;
			stats.errorInCompleteTooltip = incomp_error;
		}
	}

		// category stuff

	private void RefreshCategorySideBar(Category category) {
		MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
		if (mdi == null) {
			return;
		}

		MdiEntry entry = mdi.getEntry("Cat." + Base32.encode(category.getName().getBytes()));
		
		if (entry == null) {
			return;
		}

		requestRedraw( entry );
		
		ViewTitleInfoManager.refreshTitleInfo(entry.getViewTitleInfo());
	}

	private void setupCategory(final Category category) {
		MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
		if (mdi == null) {
			return;
		}

		String name = category.getName();
		String id = "Cat." + Base32.encode(name.getBytes());
		if (category.getType() != Category.TYPE_USER) {
			name = "{" + name + "}";
		}

		ViewTitleInfo viewTitleInfo = new ViewTitleInfo() {

			@Override
			public Object getTitleInfoProperty(int propertyID) {
				if (propertyID == TITLE_INDICATOR_TEXT) {
					if (statsNoLowNoise.numIncomplete > 0) {
						List<?> dms = category.getDownloadManagers(null);
						if (dms != null) {
							return "" + dms.size();
						}
					}
				}
				return null;
			}
		};

		MdiEntry entry = mdi.createEntryFromSkinRef(
				MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS, id, "library",
				name, viewTitleInfo, category, false, null);
		if (entry != null) {
			entry.setImageLeftID("image.sidebar.library");

			addGeneralLibraryMenus( entry, id );
			
			entry.addListener(new MdiEntryDropListener() {
				@Override
				public boolean mdiEntryDrop(MdiEntry entry, Object payload) {
					if (!(payload instanceof String)) {
						return false;
					}

					String dropped = (String) payload;
					String[] split = RegExUtil.PAT_SPLIT_SLASH_N.split(dropped);
					if (split.length > 1) {
						String type = split[0];
						if (type.startsWith("DownloadManager")) {
							GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
							for (int i = 1; i < split.length; i++) {
								String hash = split[i];

								try {
									DownloadManager dm = gm.getDownloadManager(new HashWrapper(
											Base32.decode(hash)));

									if (dm != null) {
										TorrentUtil.assignToCategory(new Object[] {
											dm
										}, category);
									}

								} catch (Throwable t) {

								}
							}
						}
					}

					return true;
				}
			});
		}

		if (entry instanceof SideBarEntrySWT) {
			final SideBarEntrySWT entrySWT = (SideBarEntrySWT) entry;
			entrySWT.addListener(new MdiSWTMenuHackListener() {
				@Override
				public void menuWillBeShown(MdiEntry entry, Menu menuTree) {
					CategoryUIUtils.createMenuItems(menuTree, category);
				}
			});
		}

	}

	private void removeCategory(Category category) {
		MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
		if (mdi == null) {
			return;
		}

		MdiEntry entry = mdi.getEntry("Cat."
				+ Base32.encode(category.getName().getBytes()));

		if (entry != null) {
			entry.close(true);
		}
	}

		// tag stuff

	private void refreshTagSideBar(Tag tag) {
		UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
		MultipleDocumentInterface mdi = uiFunctions != null ? uiFunctions.getMDI() : null;
		if (mdi == null) {
			return;
		}

		String tag_id = "Tag." + tag.getTagType().getTagType() + "." + tag.getTagID();
		
		MdiEntry entry = mdi.getEntry( tag_id );

		if ( entry == null ){

			if ( tag.isVisible()){

				setupTag( tag );
			}

			return;
		}

		if ( !tag.isVisible()){

			removeTag( tag );

			return;
		}

		String tag_title = tag.getTagName( true );

		if ( show_tag_groups ){
			
			String group = tag.getGroup();
			
			String parent_id = entry.getParentID();
			
			boolean is_group		= group != null && !group.isEmpty();
			
			boolean parent_is_group = parent_id.startsWith( "Tag." + tag.getTagType().getTagType() + ".group." + (is_group?group:"" ));
			
			if ( is_group != parent_is_group ){
				
				resetTag( tag );
			}
		}
		
		String old_title = entry.getTitle();

		if ( !old_title.equals( tag_title )){

			entry.setTitle( tag_title );
		}
		
		setTagIcon( tag, entry, false );
		
		Object[] tik = (Object[])entry.getUserData( TAG_INDICATOR_KEY );

		int 	tag_count 	= tag.getTaggedCount();
		int[] 	tag_colour	= tag.getColor();
		
		if ( tik == null || (Integer)tik[0] != tag_count || !Arrays.equals((int[])tik[1], tag_colour )){
			
			entry.setUserData( TAG_INDICATOR_KEY, new Object[]{ tag_count, tag_colour });
			
			requestRedraw( entry );
		}
			
		ViewTitleInfoManager.refreshTitleInfo(entry.getViewTitleInfo());

		Object[] tag_data = (Object[])entry.getUserData( TAG_DATA_KEY );

		if ( tag_data != null ){

			boolean[]	auto_tag 		= tag.isTagAuto();
			boolean[]	old_auto_tag 	= (boolean[])tag_data[1];

			if ( !Arrays.equals( auto_tag, old_auto_tag )){

				tag_data[1] = auto_tag;

				if ( auto_tag[0] && auto_tag[1] ){

					entry.removeListener((MdiEntryDropListener)tag_data[0]);

				}else{

					entry.addListener((MdiEntryDropListener)tag_data[0]);
				}
			}
		}
	}

	private final static Object	tag_setup_lock = new Object();

	public MdiEntry
	setupTag(
		final Tag tag )
	{
		MultipleDocumentInterfaceSWT mdi = UIFunctionsManagerSWT.getUIFunctionsSWT().getMDISWT();

		if ( mdi == null ){

			return null;
		}
		
		if ( !show_tag_tab_views ){
			
			return( null );
		}
		
			/*
			 * Can get hit here concurrently due to various threads interacting with tags...
			 */

		synchronized( tag_setup_lock ){

			String id = "Tag." + tag.getTagType().getTagType() + "." + tag.getTagID();

			String parent_id = MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS;
			
			String group_id  = null;
			
			if ( show_tag_groups ){
				
				String tag_group = tag.getGroup();
				
				if ( tag_group != null && !tag_group.isEmpty()){
					
					if ( tag.getTaggableTypes() == Taggable.TT_DOWNLOAD ){
						
						group_id = "Tag." + tag.getTagType().getTagType() + ".group." + tag_group;
						
						if ( mdi.getEntry( group_id ) == null ){
						
							ViewTitleInfo viewTitleInfo =
									new ViewTitleInfo()
									{
										@Override
										public Object
										getTitleInfoProperty(
											int pid )
										{
											if ( pid == TITLE_TEXT ) {
												
												return( tag_group );
												
											}else if ( pid == TITLE_INDICATOR_TEXT ){
	
												
	
											}else if ( pid == TITLE_INDICATOR_COLOR ){
	
	
											}else if ( pid == TITLE_INDICATOR_TEXT_TOOLTIP ){
	
												
											}
	
											return null;
										}
									};
									
									// find where to locate this in the sidebar

							TreeMap<String,String>	name_map = new TreeMap<>(FormattersImpl.getAlphanumericComparator2(true));

							name_map.put( tag_group, group_id );

							List<MdiEntry> kids = mdi.getChildrenOf( parent_id );
							
							for ( MdiEntry kid: kids ){
								
								String prefix = "Tag." + tag.getTagType().getTagType() + ".group.";
								
								String kid_id = kid.getId();

								if ( kid_id.startsWith( prefix )){
									
									name_map.put( kid_id.substring( prefix.length()), kid_id );
								}
							}

							String	prev_id = null;

							for ( String this_id: name_map.values()){

								if ( this_id == group_id ){

									break;
								}

								prev_id = this_id;
							}

							if ( prev_id == null && name_map.size() > 1 ){

								Iterator<String>	it = name_map.values().iterator();

								it.next();

								prev_id = "~" + it.next();
							}
							
							MdiEntry entry = mdi.createEntryFromSkinRef(
									parent_id, group_id, "library", tag_group, viewTitleInfo, tag.getGroupContainer(), false, prev_id );
							
							setTagIcon( tag, entry, true );
							
							if ( entry instanceof SideBarEntrySWT ){
								final SideBarEntrySWT entrySWT = (SideBarEntrySWT) entry;
								entrySWT.addListener(new MdiSWTMenuHackListener() {
									@Override
									public void menuWillBeShown(MdiEntry entry, Menu menuTree) {
										
										TagGroup tg = tag.getGroupContainer();
										
										TagUIUtils.createSideBarMenuItems(menuTree, tg );
									}
								});
							}
						}
						
						parent_id = group_id;
					}
				}
			}
			
			if ( mdi.getEntry( id ) != null ){

				return null;
			}

			
				// find where to locate this in the sidebar

			TreeMap<Tag,String>	name_map = new TreeMap<>(TagUtils.getTagComparator());

			name_map.put( tag, id );

			for ( Tag t: tag.getTagType().getTags()){

				if ( t.isVisible()){

					String tid = "Tag." + tag.getTagType().getTagType() + "." + t.getTagID();

					MdiEntry entry = mdi.getEntry( tid );
					
					if ( entry  != null ){

						String this_group = t.getGroup();
						
						if ( 	( group_id == null && ( this_group==null || this_group.isEmpty() )) ||
								( group_id != null && entry.getParentID().equals( group_id ))){
						
							name_map.put( t, tid );
						}
					}
				}
			}

			String	prev_id = null;

			for ( String this_id: name_map.values()){

				if ( this_id == id ){

					break;
				}

				prev_id = this_id;
			}

			if ( prev_id == null && name_map.size() > 1 ){

				Iterator<String>	it = name_map.values().iterator();

				it.next();

				prev_id = "~" + it.next();
			}

			boolean auto = tag.getTagType().isTagTypeAuto();

			ViewTitleInfo viewTitleInfo =
				new ViewTitleInfo()
				{
					@Override
					public Object
					getTitleInfoProperty(
						int pid )
					{
						if ( pid == TITLE_TEXT ) {
							
							return( tag.getTagName( true ));
							
						}else if ( pid == TITLE_INDICATOR_TEXT ){

							return( String.valueOf( tag.getTaggedCount()));

						}else if ( pid == TITLE_INDICATOR_COLOR ){

							TagType tag_type = tag.getTagType();

							int[] def_color = tag_type.getColorDefault();

							int[] tag_color = tag.getColor();

							if ( tag_color != def_color ){

								return( tag_color );
							}

						}else if ( pid == TITLE_INDICATOR_TEXT_TOOLTIP ){

							return( TagUtils.getTagTooltip( tag ));
						}

						return null;
					}
				};

			MdiEntry entry;

			boolean closable = auto;

			if ( tag.getTaggableTypes() == Taggable.TT_DOWNLOAD ){

				closable = true;

				String name = tag.getTagName( true );

				entry = mdi.createEntryFromSkinRef(
						parent_id, id, "library", name, viewTitleInfo, tag, closable, prev_id );
				
				addGeneralLibraryMenus( entry, id );
				
			}else{

				entry = mdi.createEntryFromEventListener(
							parent_id, new PeersGeneralView( tag ), id, closable, null, prev_id );

				entry.setViewTitleInfo( viewTitleInfo );
			}

			if ( closable ){

				entry.addListener(
					new MdiCloseListener()
					{
						@Override
						public void
						mdiEntryClosed(
							MdiEntry 	entry,
							boolean 	userClosed )
						{
							if ( userClosed && entry.getUserData( AUTO_CLOSE_KEY ) == null ){

									// userClosed isn't all we want - it just means we're not closing the app... So to prevent
									// a deselection of 'show tags in sidebar' 'user-closing' the entries we need this test

								
								if ( show_tag_groups ){
									
									String parent_id = entry.getParentID();
									
									if ( parent_id.startsWith( "Tag." + tag.getTagType().getTagType() + ".group." )){
										
										if ( mdi.getChildrenOf( parent_id ).isEmpty()){
											
											MdiEntry parent_entry = mdi.getEntry( parent_id );
											
											if ( parent_entry != null ){
											
												parent_entry.close( true );
											}
										}
									}
								}
								
								if ( COConfigurationManager.getBooleanParameter("Library.TagInSideBar")){

									tag.setVisible( false );
								}
							}
						}
					});
			}

			if (entry != null) {

				setTagIcon( tag, entry, false );
			}

			if (entry instanceof SideBarEntrySWT) {
				final SideBarEntrySWT entrySWT = (SideBarEntrySWT) entry;
				entrySWT.addListener(new MdiSWTMenuHackListener() {
					@Override
					public void menuWillBeShown(MdiEntry entry, Menu menuTree) {
						TagUIUtils.createSideBarMenuItems(menuTree, tag);
					}
				});
			}

			if ( !auto && entry != null ){

				MdiEntryDropListener dl = new MdiEntryDropListener() {
					@Override
					public boolean mdiEntryDrop(MdiEntry entry, Object payload) {
						if (!(payload instanceof String)) {
							return false;
						}

						boolean[] auto = tag.isTagAuto();

						if ( auto[0] && auto[1] ){

							return( false );
						}

						final String dropped = (String) payload;

						new AEThread2("Tagger") {
							@Override
							public void run() {
								dropTorrentOnTag(tag, dropped);
							}
						}.start();

						return true;
					}

					private void dropTorrentOnTag(Tag tag, String dropped) {
						String[] split = RegExUtil.PAT_SPLIT_SLASH_N.split(dropped);
						if (split.length <= 1) {
							return;
						}

						String type = split[0];
						if (!type.startsWith("DownloadManager") && !type.startsWith( "DiskManagerFileInfo" )) {
							return;
						}
						GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
						List<DownloadManager> listDMs = new ArrayList<>();
						boolean doAdd = false;
						for (int i = 1; i < split.length; i++) {
							String hash = split[i];

							int sep = hash.indexOf( ";" );	// for files

							if ( sep != -1 ){

								hash = hash.substring( 0, sep );
							}

							try {
								DownloadManager dm = gm.getDownloadManager(new HashWrapper(
										Base32.decode(hash)));

								if ( dm != null ){

									listDMs.add(dm);

									if (!doAdd && !tag.hasTaggable(dm)) {
										doAdd = true;
									}
								}
							}catch ( Throwable t ){

							}

							boolean	auto[] = tag.isTagAuto();

							for (DownloadManager dm : listDMs) {
								if ( doAdd ){

									if ( !auto[0] ){

										tag.addTaggable( dm );
									}
								}else{

									if ( !( auto[0] && auto[1] )){

										tag.removeTaggable( dm );
									}
								}
							}
						}
					}
				};

				boolean[] tag_auto = tag.isTagAuto();

				entry.setUserData( TAG_DATA_KEY, new Object[]{ dl, tag_auto });

				if ( !( tag_auto[0] && tag_auto[1] )){

					entry.addListener( dl );
				}
			}
			return entry;
		}
	}

	private void
	removeTag(
		Tag tag ) 
	{
		MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
		
		if ( mdi == null ){
			
			return;
		}

		synchronized( tag_setup_lock ){
			
			MdiEntry entry = mdi.getEntry("Tag." + tag.getTagType().getTagType() + "." + tag.getTagID());
	
			if (entry != null) {
	
				entry.setUserData( AUTO_CLOSE_KEY, "" );
	
				entry.close( true );
				
				if ( show_tag_groups ){
				
					String parent_id = entry.getParentID();
					
					if ( parent_id.startsWith( "Tag." + tag.getTagType().getTagType() + ".group." )){
						
						if ( mdi.getChildrenOf( parent_id ).isEmpty()){
							
							MdiEntry parent_entry = mdi.getEntry( parent_id );
							
							parent_entry.close( true );
						}
					}
				}
			}
		}
	}

	private void
	resetTag(
		Tag	tag )
	{
		synchronized( tag_setup_lock ){
		
			removeTag( tag );
		
			setupTag( tag );
		}
	}
	
	private void
	setTagIcon(
		Tag			tag,
		MdiEntry	entry,
		boolean		default_only )
	{
		if ( !default_only ){
			
			String image_file = tag.getImageFile();
			
			if ( image_file == null ){
				
				image_file = "";
			}
			
			String existing = (String)entry.getUserData( TAG_IMAGE_KEY );
			
			if ( existing == image_file || ( existing != null && existing.equals( image_file ))){
				
				return;
			}
			
			entry.setUserData( TAG_IMAGE_KEY, image_file );
			
			if ( !image_file.isEmpty()){
				
				String fif = image_file;
						
				Utils.execSWTThread(
					new Runnable(){
						
						@Override
						public void run(){
							try{
								String resource = new File( fif ).toURI().toURL().toExternalForm();
								
								ImageLoader.getInstance().getUrlImage(
									resource, 
									new Point( 20, 14 ),
									new ImageLoader.ImageDownloaderListener(){
										
										@Override
										public void imageDownloaded(Image image, String key, boolean returnedImmediately){
											((MdiEntrySWT)entry).setImageLeftID( key );
											
										}
									});
								
							}catch( Throwable e ){
								
								Debug.out( e );
							}
						}
					});
				
				return;
			}
		}
		
		((MdiEntrySWT)entry).setImageLeft( null );
		
		String image_id = tag.getImageID();

		if ( image_id != null ){
			entry.setImageLeftID( image_id );
		}else if ( tag.getTagType().getTagType() == TagType.TT_PEER_IPSET ){
			entry.setImageLeftID("image.sidebar.tag-red");
		}else if ( tag.getTagType().isTagTypePersistent()){
			entry.setImageLeftID("image.sidebar.tag-green");
		}else{
			entry.setImageLeftID("image.sidebar.tag-blue");
		}
	}

		// -------------------

	private int updateDMCounts(DownloadManager dm) {
		boolean isSeeding;
		boolean isDownloading;
		boolean isQueued;
		boolean isStopped;

		Boolean wasSeedingB = (Boolean) dm.getUserData("wasSeeding");
		boolean wasSeeding = wasSeedingB != null && wasSeedingB.booleanValue();
		Boolean wasDownloadingB = (Boolean) dm.getUserData("wasDownloading");
		boolean wasDownloading = wasDownloadingB != null && wasDownloadingB.booleanValue();
		Boolean wasStoppedB = (Boolean) dm.getUserData("wasStopped");
		boolean wasStopped = wasStoppedB != null && wasStoppedB.booleanValue();
		Boolean wasQueuedB = (Boolean) dm.getUserData("wasQueued");
		boolean wasQueued = wasQueuedB != null && wasQueuedB.booleanValue();

		int dm_state = dm.getState();

		if (dm.getAssumedComplete()) {
			isSeeding = dm_state == DownloadManager.STATE_SEEDING;
			isDownloading = false;
		} else {
			isDownloading = dm_state == DownloadManager.STATE_DOWNLOADING;
			isSeeding = false;
		}

		isStopped 	= dm_state == DownloadManager.STATE_STOPPED;
		isQueued	= dm_state == DownloadManager.STATE_QUEUED;

		boolean lowNoise = PlatformTorrentUtils.isAdvancedViewOnly(dm);

		if (isDownloading != wasDownloading) {
			if (isDownloading) {
				statsWithLowNoise.numDownloading++;
				if (!lowNoise) {
					statsNoLowNoise.numDownloading++;
				}
			} else {
				statsWithLowNoise.numDownloading--;
				if (!lowNoise) {
					statsNoLowNoise.numDownloading--;
				}
			}
			dm.setUserData("wasDownloading", Boolean.valueOf(isDownloading));
		}

		if (isSeeding != wasSeeding) {
			if (isSeeding) {
				statsWithLowNoise.numSeeding++;
				if (!lowNoise) {
					statsNoLowNoise.numSeeding++;
				}
			} else {
				statsWithLowNoise.numSeeding--;
				if (!lowNoise) {
					statsNoLowNoise.numSeeding--;
				}
			}
			dm.setUserData("wasSeeding", Boolean.valueOf(isSeeding));
		}

		if (isStopped != wasStopped) {
			if (isStopped) {
				if (!dm.getAssumedComplete()) {
					statsWithLowNoise.numStoppedIncomplete++;
				}
				if (!lowNoise) {
					if (!dm.getAssumedComplete()) {
						statsNoLowNoise.numStoppedIncomplete++;
					}
				}
			} else {
				if (!dm.getAssumedComplete()) {
					statsWithLowNoise.numStoppedIncomplete--;
				}
				if (!lowNoise) {
					if (!dm.getAssumedComplete()) {
						statsNoLowNoise.numStoppedIncomplete--;
					}
				}
			}
			dm.setUserData("wasStopped", Boolean.valueOf(isStopped));
		}

		if (isQueued != wasQueued) {
			if (isQueued) {
				statsWithLowNoise.numQueued++;
				if (!lowNoise) {
					statsNoLowNoise.numQueued++;
				}
			} else {
				statsWithLowNoise.numQueued--;
				if (!lowNoise) {
					statsNoLowNoise.numQueued--;
				}
			}
			dm.setUserData("wasQueued", Boolean.valueOf(isQueued));
		}
		return( dm_state );
	}

	void recountItems() {
		if (!CoreFactory.isCoreRunning()) {
			return;
		}
		GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
		List<DownloadManager> dms = gm.getDownloadManagers();
		statsNoLowNoise.total = 0;
		statsWithLowNoise.total = 0;
		statsNoLowNoise.numUnOpened = 0;
		for (Iterator<DownloadManager> iter = dms.iterator(); iter.hasNext();) {
			DownloadManager dm = iter.next();
			if (!PlatformTorrentUtils.getHasBeenOpened(dm) && dm.getAssumedComplete()) {
				statsNoLowNoise.numUnOpened++;
			}
			statsWithLowNoise.total++;
			if ( !PlatformTorrentUtils.isAdvancedViewOnly(dm)){
				statsNoLowNoise.total++;
			}
		}
		statsWithLowNoise.numUnOpened = statsNoLowNoise.numUnOpened;
	}

	protected void addCountRefreshListener(countRefreshListener l) {
		l.countRefreshed(statsWithLowNoise, statsNoLowNoise);
		listeners.add(l);
	}

	public void triggerCountRefreshListeners() {
		for (countRefreshListener l : listeners) {
			l.countRefreshed(statsWithLowNoise, statsNoLowNoise);
		}
	}

	/**
	 *
	 *
	 * @since 3.1.1.1
	 */

	void refreshAllLibraries() {
		if (refresh_limiter != null) {
			refresh_limiter.dispatch();
		}
	}

	void refreshAllLibrariesSupport() {
		for (countRefreshListener l : listeners) {
			l.countRefreshed(statsWithLowNoise, statsNoLowNoise);
		}
		MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
		if (mdi == null) {
			return;
		}

			// don't mess with things until MDI initialized as it might be auto-opening views
		
		if ( mdi.isInitialized()){
			
			boolean showDownloading = COConfigurationManager.getBooleanParameter( "Show Downloading In Side Bar" );
	
			if ( showDownloading && statsNoLowNoise.numIncomplete > 0){
				
				MdiEntry entry = mdi.getEntry(SideBar.SIDEBAR_SECTION_LIBRARY_DL);
				
				if (entry == null) {
					long now = SystemTime.getMonotonousTime();
					
						// prevent rapid show/hides and also trt and work around bug where 2 Downloading entries have
						// been seen to get created (lame fix I know)
					
					if ( now - last_dl_entry_load > 5000 ){
						
						last_dl_entry_load = now;
											
						mdi.loadEntryByID(SideBar.SIDEBAR_SECTION_LIBRARY_DL, false);
					}
				}
			} else {
				MdiEntry entry = mdi.getEntry(SideBar.SIDEBAR_SECTION_LIBRARY_DL);
				if (entry != null) {
					entry.close(true, false);
				}
			}
		}else{
			refreshAllLibraries();
		}
		
		MdiEntry entry = mdi.getEntry(SideBar.SIDEBAR_SECTION_LIBRARY_DL);
		if (entry != null) {
			MdiEntryVitalityImage[] vitalityImages = entry.getVitalityImages();
			for (int i = 0; i < vitalityImages.length; i++) {
				MdiEntryVitalityImage vitalityImage = vitalityImages[i];
				String imageID = vitalityImage.getImageID();
				if (imageID == null) {
					continue;
				}
				if (imageID.equals(ID_VITALITY_ACTIVE)) {
					//replace this annoying spinner with color change
					//vitalityImage.setVisible(statsNoLowNoise.numDownloading > 0);

				} else if (imageID.equals(ID_VITALITY_ALERT)) {
					vitalityImage.setVisible(statsNoLowNoise.numErrorInComplete > 0);
					if (statsNoLowNoise.numErrorInComplete > 0) {
						vitalityImage.setToolTip(statsNoLowNoise.errorInCompleteTooltip);
					}
				}
			}
			ViewTitleInfoManager.refreshTitleInfo(entry.getViewTitleInfo());
			
			requestRedraw( entry );
		}

		entry = mdi.getEntry(SideBar.SIDEBAR_SECTION_LIBRARY_CD);
		if (entry != null) {
			MdiEntryVitalityImage[] vitalityImages = entry.getVitalityImages();
			for (int i = 0; i < vitalityImages.length; i++) {
				MdiEntryVitalityImage vitalityImage = vitalityImages[i];
				String imageID = vitalityImage.getImageID();
				if (imageID == null) {
					continue;
				}
				if (imageID.equals(ID_VITALITY_ALERT)) {
					vitalityImage.setVisible(statsNoLowNoise.numErrorComplete > 0);
					if (statsNoLowNoise.numErrorComplete > 0) {
						vitalityImage.setToolTip(statsNoLowNoise.errorCompleteTooltip);
					}
				}
			}
		}

		entry = mdi.getEntry(SideBar.SIDEBAR_SECTION_LIBRARY_UNOPENED);
		if (entry != null) {
			ViewTitleInfoManager.refreshTitleInfo(entry.getViewTitleInfo());
			
			requestRedraw( entry );
		}
		
		entry = mdi.getEntry(SideBar.SIDEBAR_SECTION_LIBRARY);
		if (entry != null) {
			ViewTitleInfoManager.refreshTitleInfo(entry.getViewTitleInfo());
			
			requestRedraw( entry );
		}
	}

	private static boolean	TABLE_SUBCONFIG_ENABLE = COConfigurationManager.getBooleanParameter( "Library.EnableSepColConfig" );
	
	private static String
	getTableSubID(
		Object		ds )
	{
		if ( ds instanceof Tag ){
			
			Tag tag = (Tag)ds;
			
			if ( tag.getTagType().getTagType() == TagType.TT_DOWNLOAD_MANUAL ){
			
				return( "Tag_" + ((Tag)ds).getTagUID());
			}
		}else if ( ds instanceof TagGroup ){
			
			TagGroup tg = (TagGroup)ds;
			
			return( "TagGroup_" + Base32.encode( tg.getName().getBytes( Constants.UTF_8 )));
		}
			
		return( null );
	}
	
	public static String
	getTableIdFromFilterMode(
		int 		torrentFilterMode,
		boolean 	big,
		Object		dataSource) 
	{
		String	baseTableID = null;
		
		if (torrentFilterMode == SBC_LibraryView.TORRENTS_COMPLETE) {
			baseTableID = big ? TableManager.TABLE_MYTORRENTS_COMPLETE_BIG
					: TableManager.TABLE_MYTORRENTS_COMPLETE;
		} else if (torrentFilterMode == SBC_LibraryView.TORRENTS_INCOMPLETE) {
			baseTableID =  big ? TableManager.TABLE_MYTORRENTS_INCOMPLETE_BIG
					: TableManager.TABLE_MYTORRENTS_INCOMPLETE;
		} else if (torrentFilterMode == SBC_LibraryView.TORRENTS_ALL) {
			baseTableID =  TableManager.TABLE_MYTORRENTS_ALL_BIG;
		} else if (torrentFilterMode == SBC_LibraryView.TORRENTS_UNOPENED) {
			baseTableID =  big ? TableManager.TABLE_MYTORRENTS_UNOPENED_BIG
					: TableManager.TABLE_MYTORRENTS_UNOPENED;
		}
	
		if ( baseTableID == null ){
			
			return( null );
			
		}else{
			
			if (  TABLE_SUBCONFIG_ENABLE ){
				
				String sub = getTableSubID( dataSource);
				
				if ( sub == null ){
					
					return( baseTableID );
				}else{
					
					return( Utils.createSubViewID( baseTableID, sub ));
				}
			}else{
			
				return( baseTableID );
			}
		}
	}
	
	public static String
	getTableIdFromDataSource(
		String		baseTableID,
		Object		dataSource) 
	{
		if (  TABLE_SUBCONFIG_ENABLE ){
			
			String sub = getTableSubID( dataSource);
			
			if ( sub == null ){
				
				return( baseTableID );
			}else{
				
				return( Utils.createSubViewID( baseTableID, sub ));
			}
		}else{
		
			return( baseTableID );
		}
	}
	
	private void addTagManagerListeners() {
		synchronized (tag_listener_lock) {
			if (tagManagerListener != null) {

				return;
			}
			
			tagListener = new TagListener() {
				@Override
				public void
				taggableAdded(
					Tag tag,
					Taggable tagged )
				{
					refreshTagSideBar( tag );
				}

				@Override
				public void
				taggableSync(
					Tag 		tag )
				{
					refreshTagSideBar( tag );
				}

				@Override
				public void
				taggableRemoved(
					Tag			tag,
					Taggable	tagged )
				{
					refreshTagSideBar( tag );
				}
			};

			tagTypeListener = new TagTypeListener() {
				@Override
				public void tagTypeChanged(TagType tag_type) {
					for (Tag tag : tag_type.getTags()) {

						if (tag.isVisible()) {

							setupTag(tag);

						} else {

							refreshTagSideBar(tag);
						}
					}
				}

				@Override
				public void tagEventOccurred(TagEvent event) {
					int type = event.getEventType();
					Tag tag = event.getTag();
					if (type == TagEvent.ET_TAG_ADDED) {
						tagAdded(tag);
					} else if ( type == TagEvent.ET_TAG_MEMBERSHIP_CHANGED || type == TagEvent.ET_TAG_METADATA_CHANGED ) {
						tagChanged(tag);
					} else if (type == TagEvent.ET_TAG_REMOVED) {
						tagRemoved(tag);
					} else if (type == TagEvent.ET_TAG_ATTENTION_REQUESTED) {
						MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
						if (mdi == null) {
							return;
						}

						MdiEntry entry = mdi.getEntry(
								"Tag." + tag.getTagType().getTagType() + "." + tag.getTagID());

						if (entry != null) {
							mdi.showEntry(entry);
						}
					}
				}

				public void tagAdded(Tag tag) {
					synchronized (tag_listener_lock) {
						if (tag.isVisible() && tagListener != null) {

							setupTag(tag);

							tag.addTagListener(tagListener, false);
						}
					}
				}

				public void tagChanged(Tag tag) {
					refreshTagSideBar(tag);
				}

				public void tagRemoved(Tag tag) {
					removeTag(tag);
				}
			};

			tagManagerListener = new TagManagerListener() {
				@Override
				public void tagTypeAdded(TagManager manager, TagType tag_type) {
					synchronized (tag_listener_lock) {
						int tt = tag_type.getTagType();
						
						if (	( tt != TagType.TT_DOWNLOAD_CATEGORY && tt != TagType.TT_DOWNLOAD_INTERNAL ) &&
								tagTypeListener != null) {

							tag_type.addTagTypeListener(tagTypeListener, true);
						}
					}
				}

				@Override
				public void tagTypeRemoved(TagManager manager, TagType tag_type) {
					for (Tag t : tag_type.getTags()) {

						removeTag(t);
					}
				}
			};

			TagManagerFactory.getTagManager().addTagManagerListener(
					tagManagerListener, true);
		}
	}

	private void removeTagManagerListeners(boolean removeFromSidebar) {
		synchronized (tag_listener_lock) {
			if (tagManagerListener == null) {
				return;
			}

			TagManagerFactory.getTagManager().removeTagManagerListener(tagManagerListener);

			List<TagType> tag_types = TagManagerFactory.getTagManager().getTagTypes();

			for (TagType tt : tag_types) {

				if (tt.getTagType() != TagType.TT_DOWNLOAD_CATEGORY) {

					tt.removeTagTypeListener(tagTypeListener);
				}

				for (Tag t : tt.getTags()) {

					t.removeTagListener(tagListener);

					if (removeFromSidebar) {
						removeTag(t);
					}
				}
			}

			tagManagerListener = null;
			tagListener = null;
			tagTypeListener = null;
		}
	}

	public void dispose() {

		if (categoryListener != null) {
			Category[] categories = CategoryManager.getCategories();
			if (categories.length >= 0) {
				for (Category cat: categories) {
					cat.removeCategoryListener(categoryListener);
				}
			}
			CategoryManager.removeCategoryManagerListener(categoryManagerListener);
		}

		if (tagManagerListener != null) {
			removeTagManagerListeners(false);
		}

		if (hasBeenOpenedListener != null) {
			PlatformTorrentUtils.removeHasBeenOpenedListener(hasBeenOpenedListener);
		}

		refresh_limiter = null;

		if (dmListener != null || gmListener != null) {
			if (core != null) {
				GlobalManager gm = core.getGlobalManager();
				if (gm != null) {
					if (gmListener != null) {
						gm.removeListener(gmListener);
					}

					if (dmListener != null) {
						List<DownloadManager> dms = gm.getDownloadManagers();
						for (DownloadManager dm : dms) {
							dm.removeListener(dmListener);
						}
					}
				}
			}
			gmListener = null;
			dmListener = null;
		}

		if (timerEventPeriodic != null) {
			timerEventPeriodic.cancel();
			timerEventPeriodic = null;
		}

		// should already be empty if everyone removed their listeners..
		listeners.clear();

		COConfigurationManager.removeParameterListener("MyTorrentsView.showuptime",	configListenerShow);
		COConfigurationManager.removeParameterListener("MyTorrentsView.showrates", configListenerShow);
		COConfigurationManager.removeParameterListener(SideBar.SIDEBAR_SECTION_LIBRARY + ".viewmode", configListenerShow);
		
		if (timerEventShowUptime != null) {
			timerEventShowUptime.cancel();
			timerEventShowUptime = null;
		}

		COConfigurationManager.removeParameterListener("Library.TagInSideBar", paramTagsInSidebarListener);
		COConfigurationManager.removeParameterListener("Library.TagGroupsInSideBar", paramTagGroupsInSidebarListener);
		COConfigurationManager.removeParameterListener("Library.CatInSideBar", paramCatInSidebarListener);
	}

	protected interface countRefreshListener
	{
		void countRefreshed(stats statsWithLowNoise, stats statsNoLowNoise);
	}

	private class MyMdiEntryCreationListener implements MdiEntryCreationListener {
		private final MultipleDocumentInterfaceSWT mdi;

		public MyMdiEntryCreationListener(MultipleDocumentInterfaceSWT mdi) {
			this.mdi = mdi;
		}

		@Override
		public MdiEntry createMDiEntry(String id) {
			
			ViewTitleInfo titleInfo = new ViewTitleInfo() {
				@Override
				public Object getTitleInfoProperty(int propertyID) {
					
					int	total_wln	= statsWithLowNoise.total;
					int	total_nln 	= statsNoLowNoise.total;

					if (propertyID == TITLE_INDICATOR_TEXT) {

						int viewmode = COConfigurationManager.getIntParameter( SideBar.SIDEBAR_SECTION_LIBRARY + ".viewmode", SBC_LibraryView.MODE_BIGTABLE );
						
						if ( 	total_wln == total_nln || 
								viewmode == SBC_LibraryView.MODE_SMALLTABLE ||
								!COConfigurationManager.getBooleanParameter( "Library.EnableSimpleView" )){
							
							return( String.valueOf( total_wln ));
							
						}else{
							
							return( total_nln + " | " + total_wln );
						}
					}else if (propertyID == TITLE_INDICATOR_TEXT_TOOLTIP) {
						
						if ( total_wln != total_nln  ){
							
							return(
								MessageText.getString( "v3.MainWindow.menu.view.asSimpleList") + "=" + total_nln + ", " +
								MessageText.getString( "v3.MainWindow.menu.view.asAdvancedList") + "=" + total_wln );
						}
					}else if (propertyID == TITLE_INDICATOR_COLOR) {
						
					}

					return null;
				}
			};
			
			MdiEntry entry = mdi.createEntryFromSkinRef(
					SideBar.SIDEBAR_HEADER_TRANSFERS,
					SideBar.SIDEBAR_SECTION_LIBRARY, "library", "{sidebar."
							+ SideBar.SIDEBAR_SECTION_LIBRARY + "}", titleInfo, null, false,
					"");
			entry.setImageLeftID("image.sidebar.library");
			
			addGeneralLibraryMenus(entry,SideBar.SIDEBAR_SECTION_LIBRARY);

			return entry;
		}
	}
}
