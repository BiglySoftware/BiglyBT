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

package com.biglybt.ui.swt.shells.main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.biglybt.core.CoreFactory;
import com.biglybt.pif.sharing.ShareException;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoManager;
import com.biglybt.ui.mdi.*;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.views.skin.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.history.DownloadHistoryEvent;
import com.biglybt.core.history.DownloadHistoryListener;
import com.biglybt.core.history.DownloadHistoryManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tracker.AllTrackersManager;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackers;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackersEvent;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackersListener;
import com.biglybt.core.tracker.host.TRHost;
import com.biglybt.core.tracker.host.TRHostListener;
import com.biglybt.core.tracker.host.TRHostTorrent;
import com.biglybt.core.util.AsyncController;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.TimerEvent;
import com.biglybt.core.util.TimerEventPerformer;
import com.biglybt.core.util.TimerEventPeriodic;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadStub;
import com.biglybt.pif.download.DownloadStubEvent;
import com.biglybt.pif.download.DownloadStubListener;
import com.biglybt.pif.sharing.ShareManager;
import com.biglybt.pif.sharing.ShareManagerListener;
import com.biglybt.pif.sharing.ShareResource;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerListener2;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.views.*;
import com.biglybt.ui.swt.views.clientstats.ClientStatsView;
import com.biglybt.ui.swt.views.stats.StatsView;
import com.biglybt.ui.swt.views.utils.ManagerUtils;

import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta;
import com.biglybt.plugin.net.buddy.BuddyPluginUtils;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta.ChatInstance;
import com.biglybt.plugin.net.buddy.swt.SBC_ChatOverview;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.swt.views.skin.sidebar.SideBar;

public class MainMDISetup
{

	private static ParameterListener configBetaEnabledListener;
	private static TRHostListener trackerHostListener;
	private static SB_Dashboard	sb_dashboard;
	private static SB_Transfers sb_transfers;
	private static ShareManagerListener shareManagerListener;
	private static SB_Vuze sb_vuze;

	public static Set<String>	hiddenTopLevelIDs = new HashSet<>();
	
	private static String[] preferredOrder = MultipleDocumentInterface.SIDEBAR_HEADER_ORDER_DEFAULT;

	static{
		String order = COConfigurationManager.getStringParameter( "Side Bar Top Level Order", "" ).trim();
		
		if ( !order.isEmpty()){
		
			hiddenTopLevelIDs.addAll( Arrays.asList( preferredOrder ));
			
			List<String> newOrder = new ArrayList<>();
			
			String[] bits = order.split( "," );
			
			for ( String bit: bits ){
				bit = bit.trim();
				if ( bit.isEmpty()){
					continue;
				}
				
				try{
					int pos = Integer.parseInt( bit );
					
					String id = preferredOrder[pos-1];
					
					if ( hiddenTopLevelIDs.remove( id )){
						
						newOrder.add( id );
					}
				}catch( Throwable e ){
					
				}
			}
			
			preferredOrder = newOrder.toArray( new String[newOrder.size()]);
		}
	}

	
	public static void setupSideBar(final MultipleDocumentInterfaceSWT mdi,
	                                final MdiListener l) {
		if (Utils.isAZ2UI()) {
			setupSidebarClassic(mdi);
		} else {
			setupSidebarVuzeUI(mdi);
		}

		SBC_TorrentDetailsView.TorrentDetailMdiEntry.register(mdi);

		PluginInterface pi = PluginInitializer.getDefaultInterface();

		pi.getUIManager().addUIListener(
				new UIManagerListener2() {
					@Override
					public void UIDetached(UIInstance instance) {
					}

					@Override
					public void UIAttached(UIInstance instance) {
					}

					@Override
					public void UIAttachedComplete(UIInstance instance) {

						PluginInitializer.getDefaultInterface().getUIManager().removeUIListener(
								this);

						MdiEntry currentEntry = mdi.getCurrentEntry();
						
						String startTab		= null;
						String datasource 	= null;

						if (currentEntry != null) {

							// User or another plugin selected an entry
							
						}else{
							
							final String CFG_STARTTAB = "v3.StartTab";
							final String CFG_STARTTAB_DS = "v3.StartTab.ds";
							boolean showWelcome = false;
	
							/** We don't have a welcoming welcome yet
							boolean showWelcome = COConfigurationManager.getBooleanParameter("v3.Show Welcome");
							if (ConfigurationChecker.isNewVersion()) {
								showWelcome = true;
							}
							**/
	
							if (showWelcome) {
								startTab = SideBar.SIDEBAR_SECTION_WELCOME;
							} else {
								if (!COConfigurationManager.hasParameter(CFG_STARTTAB, true)) {
									COConfigurationManager.setParameter(CFG_STARTTAB,
											SideBar.SIDEBAR_SECTION_LIBRARY);
								}
								startTab = COConfigurationManager.getStringParameter(CFG_STARTTAB);
								datasource = COConfigurationManager.getStringParameter(
										CFG_STARTTAB_DS, null);
							}
						}
						
						mdi.setInitialEntry( startTab, datasource, SideBar.SIDEBAR_SECTION_LIBRARY );
						
						if (l != null){
							
							mdi.addListener(l);
						}
					}
				});

		configBetaEnabledListener = new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				boolean enabled = COConfigurationManager.getBooleanParameter("Beta Programme Enabled");
				if (enabled) {
					
					boolean closed  = COConfigurationManager.getBooleanParameter("Beta Programme Sidebar Closed");
					
					if ( !closed ){
					
						mdi.loadEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_BETAPROGRAM, false);
					}
				}
			}
		};
		COConfigurationManager.addAndFireParameterListener(
				"Beta Programme Enabled", configBetaEnabledListener);

		mdi.registerEntry(StatsView.VIEW_ID, new MdiEntryCreationListener() {
			@Override
			public MdiEntry createMDiEntry(String id) {
				MdiEntry entry = mdi.createEntryFromEventListener(
						MultipleDocumentInterface.SIDEBAR_HEADER_PLUGINS, new StatsView(),
						id, true, null, null);
				return entry;
			}
		});

		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_ALLPEERS,
				new MdiEntryCreationListener() {
					@Override
					public MdiEntry createMDiEntry(String id) {
						MdiEntry entry = mdi.createEntryFromEventListener(
								MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS,
								new PeersSuperView(), id, true, null, 
								SB_Transfers.getSectionPosition(mdi, MultipleDocumentInterface.SIDEBAR_SECTION_ALLPEERS));
						entry.setImageLeftID("image.sidebar.allpeers");
						return entry;
					}
				});

		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_LOGGER,
				new MdiEntryCreationListener() {
					@Override
					public MdiEntry createMDiEntry(String id) {
						MdiEntry entry = mdi.createEntryFromEventListener(
								MultipleDocumentInterface.SIDEBAR_HEADER_PLUGINS,
								new LoggerView(), id, true, null, null);
						return entry;
					}
				});

		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_TAGS,
				new MdiEntryCreationListener() {
					@Override
					public MdiEntry createMDiEntry(String id) {
						MdiEntry entry = mdi.createEntryFromSkinRef(
								MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS, id,
								"tagsview", "{tags.view.heading}", null, null, true, 
								SB_Transfers.getSectionPosition(mdi, MultipleDocumentInterface.SIDEBAR_SECTION_TAGS));
						entry.setImageLeftID("image.sidebar.tag-overview");
						entry.setDefaultExpanded(true);
						return entry;
					}
				});
		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_TAG_DISCOVERY,
				new MdiEntryCreationListener() {
					@Override
					public MdiEntry createMDiEntry(String id) {
						MdiEntry entry = mdi.createEntryFromSkinRef(
								MultipleDocumentInterface.SIDEBAR_SECTION_TAGS, id,
								"tagdiscoveryview", "{mdi.entry.tagdiscovery}", null, null,
								true, null);
						entry.setImageLeftID("image.sidebar.tag-overview");
						return entry;
					}
				});

		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_CHAT,
				new MdiEntryCreationListener() {
					@Override
					public MdiEntry createMDiEntry(String id) {

						final ViewTitleInfo title_info =
								new ViewTitleInfo()
								{
									@Override
									public Object
									getTitleInfoProperty(
										int propertyID)
									{
										BuddyPluginBeta bp = BuddyPluginUtils.getBetaPlugin();

										if ( bp == null ){

											return( null );
										}

										if ( propertyID == TITLE_INDICATOR_TEXT ){

											int	num = 0;

											for ( ChatInstance chat: bp.getChats()){

												if ( chat.getMessageOutstanding()){

													num++;
												}
											}

											if ( num > 0 ){

												return( String.valueOf( num ));

											}else{

												return( null );
											}
										}else if ( propertyID == TITLE_INDICATOR_COLOR){

											for ( ChatInstance chat: bp.getChats()){

												if ( chat.getMessageOutstanding()){

													if ( chat.hasUnseenMessageWithNick()){

														return( SBC_ChatOverview.COLOR_MESSAGE_WITH_NICK );
													}
												}
											}
										}

										return null;
									}
								};


						MdiEntry mdi_entry = mdi.createEntryFromSkinRef(
								MultipleDocumentInterface.SIDEBAR_HEADER_DISCOVERY,
								MultipleDocumentInterface.SIDEBAR_SECTION_CHAT, "chatsview",
								"{mdi.entry.chatsoverview}", title_info, null, true, null);

						mdi_entry.setImageLeftID("image.sidebar.chat-overview");

								
						final TimerEventPeriodic	timer =
							SimpleTimer.addPeriodicEvent(
									"sb:chatup",
									5*1000,
									new TimerEventPerformer()
									{
										private String 			last_text;
										private int[]			last_colour;

										@Override
										public void
										perform(
											TimerEvent event)
										{
											String 	text	= (String)title_info.getTitleInfoProperty( ViewTitleInfo.TITLE_INDICATOR_TEXT );
											int[] 	colour 	= (int[])title_info.getTitleInfoProperty( ViewTitleInfo.TITLE_INDICATOR_COLOR );
											
											boolean changed = text != last_text && ( text == null || last_text == null || !text.equals( last_text ));
											
											if ( !changed ){
												
												changed = colour != last_colour && ( colour == null || last_colour == null || !Arrays.equals( colour, last_colour ));
											}
											
											if ( changed ){
											
												last_text	= text;
												last_colour	= colour;
												
												mdi_entry.redraw();
											}
											
											ViewTitleInfoManager.refreshTitleInfo( title_info );
										}
									});

						mdi_entry.addListener(
							new MdiCloseListener() {

								@Override
								public void
								mdiEntryClosed(
									MdiEntry entry, boolean userClosed)
								{
									timer.cancel();
								}
							});

						return mdi_entry;
					}
				});

		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_ARCHIVED_DOWNLOADS,
				new MdiEntryCreationListener() {
					@Override
					public MdiEntry createMDiEntry(String id) {

						final com.biglybt.pif.download.DownloadManager download_manager = PluginInitializer.getDefaultInterface().getDownloadManager();

						final ViewTitleInfo title_info =
							new ViewTitleInfo()
							{
								@Override
								public Object
								getTitleInfoProperty(
									int propertyID)
								{
									if ( propertyID == TITLE_INDICATOR_TEXT ){

										int num = download_manager.getDownloadStubCount();

										return( String.valueOf( num ) );
									}

									return null;
								}
							};

						MdiEntry entry = mdi.createEntryFromSkinRef(
								MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS,
								MultipleDocumentInterface.SIDEBAR_SECTION_ARCHIVED_DOWNLOADS, "archivedlsview",
								"{mdi.entry.archiveddownloadsview}",
								title_info, null, true, 
								SB_Transfers.getSectionPosition(mdi, MultipleDocumentInterface.SIDEBAR_SECTION_ARCHIVED_DOWNLOADS));

						entry.setImageLeftID( "image.sidebar.archive" );

						final DownloadStubListener stub_listener =
							new DownloadStubListener()
							{
								@Override
								public void
								downloadStubEventOccurred(
									DownloadStubEvent event )
								{
									ViewTitleInfoManager.refreshTitleInfo( title_info );
									
									entry.redraw();
								}
							};

						download_manager.addDownloadStubListener( stub_listener, false );

						entry.addListener(
							new MdiCloseListener() {

								@Override
								public void
								mdiEntryClosed(
									MdiEntry entry, boolean userClosed)
								{
									download_manager.removeDownloadStubListener( stub_listener );
								}
							});

						entry.addListener(
								new MdiEntryDropListener()
								{
									@Override
									public boolean
									mdiEntryDrop(
										MdiEntry 		entry,
										Object 			data )
									{
										if ( data instanceof String ){

											String str = (String)data;

											if ( str.startsWith( "DownloadManager\n" )){

												String[] bits = str.split( "\n" );

												com.biglybt.pif.download.DownloadManager dm = PluginInitializer.getDefaultInterface().getDownloadManager();

												List<Download> downloads = new ArrayList<>();

												boolean	failed = false;

												for ( int i=1;i<bits.length;i++ ){

													byte[]	 hash = Base32.decode( bits[i] );

													try{
														Download download = dm.getDownload( hash );

														if ( download.canStubbify()){

															downloads.add( download );

														}else{

															failed = true;
														}
													}catch( Throwable e ){
													}
												}

												final boolean f_failed = failed;

												ManagerUtils.moveToArchive(
													downloads,
													new ManagerUtils.ArchiveCallback()
													{
														boolean error = f_failed;

														@Override
														public void
														failed(
															DownloadStub		original,
															Throwable			e )
														{
															error = true;
														}

														@Override
														public void
														completed()
														{
															if ( error ){

																String title 	= MessageText.getString( "archive.failed.title" );
																String text 	= MessageText.getString( "archive.failed.text" );

																MessageBoxShell prompter =
																	new MessageBoxShell(
																		title, text,
																		new String[] { MessageText.getString("Button.ok") }, 0 );

																prompter.setAutoCloseInMS(0);

																prompter.open( null );
															}
														}
													});
											}

											return( true );
										}

										return false;
									}
								});

						return entry;
					}
				});

			// download history

		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_DOWNLOAD_HISTORY,
				new MdiEntryCreationListener() {
					@Override
					public MdiEntry createMDiEntry(String id) {

						final DownloadHistoryManager history_manager = (DownloadHistoryManager) CoreFactory.getSingleton().getGlobalManager().getDownloadHistoryManager();

						final ViewTitleInfo title_info =
							new ViewTitleInfo()
							{
								@Override
								public Object
								getTitleInfoProperty(
									int propertyID)
								{
									if ( propertyID == TITLE_INDICATOR_TEXT ){

										if ( history_manager == null ){

											return( null );

										}else if ( history_manager.isEnabled()){

											int num = history_manager.getHistoryCount();

											return( String.valueOf( num ));

										}else{

											return( MessageText.getString( "label.disabled" ));
										}
									}

									return null;
								}
							};

						MdiEntry entry = mdi.createEntryFromSkinRef(
								MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS,
								MultipleDocumentInterface.SIDEBAR_SECTION_DOWNLOAD_HISTORY, "downloadhistoryview",
								"{mdi.entry.downloadhistoryview}",
								title_info, null, true, 
								SB_Transfers.getSectionPosition(mdi, MultipleDocumentInterface.SIDEBAR_SECTION_DOWNLOAD_HISTORY));

						entry.setImageLeftID("image.sidebar.logview");

						if ( history_manager != null ){

							final DownloadHistoryListener history_listener =
								new DownloadHistoryListener()
								{
									@Override
									public void
									downloadHistoryEventOccurred(
										DownloadHistoryEvent event )
									{
										ViewTitleInfoManager.refreshTitleInfo( title_info );
									}
								};

							history_manager.addListener( history_listener, false );

							entry.addListener(
								new MdiCloseListener() {

									@Override
									public void
									mdiEntryClosed(
										MdiEntry entry, boolean userClosed)
									{
										history_manager.removeListener( history_listener );
									}
								});
						}

						return entry;
					}
				});
		
		// all trackers

	mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_ALL_TRACKERS,
			new MdiEntryCreationListener() {
				@Override
				public MdiEntry createMDiEntry(String id) {

					AllTrackers	all_trackers = AllTrackersManager.getAllTrackers();
					
					final ViewTitleInfo title_info =
						new ViewTitleInfo()
						{
							@Override
							public Object
							getTitleInfoProperty(
								int propertyID)
							{
								if ( propertyID == TITLE_INDICATOR_TEXT ){

									return( String.valueOf( all_trackers.getTrackerCount()));
								}

								return null;
							}
						};

					MdiEntry entry = mdi.createEntryFromSkinRef(
							MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS,
							MultipleDocumentInterface.SIDEBAR_SECTION_ALL_TRACKERS, "alltrackersview",
							"{mdi.entry.alltrackersview}",
							title_info, null, true, 
							SB_Transfers.getSectionPosition(mdi, MultipleDocumentInterface.SIDEBAR_SECTION_ALL_TRACKERS));

					entry.setImageLeftID("image.sidebar.alltrackers");

					AllTrackersListener at_listener =
							new AllTrackersListener()
							{
								@Override
								public void trackerEventOccurred(AllTrackersEvent event)
								{
									if ( event.getEventType() != AllTrackersEvent.ET_TRACKER_UPDATED ){
									
										ViewTitleInfoManager.refreshTitleInfo( title_info );
									}
								}
							};

					all_trackers.addListener( at_listener, false );

					entry.addListener(
						new MdiCloseListener() {

							@Override
							public void
							mdiEntryClosed(
								MdiEntry entry, boolean userClosed)
							{
								all_trackers.removeListener( at_listener );
							}
						});

					return entry;
				}
			});

			// torrent options

		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_OPTIONS,
				new MdiEntryCreationListener() {
					@Override
					public MdiEntry createMDiEntry(String id) {
						MdiEntry entry = mdi.createEntryFromEventListener(
								MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS,
								TorrentOptionsView.class,
								MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_OPTIONS, true,
								null,
								SB_Transfers.getSectionPosition(mdi, MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_OPTIONS));

						entry.setImageLeftID( "image.sidebar.torrentoptions" );

						return entry;
					}
				});

		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_MY_SHARES,
				new MdiEntryCreationListener() {
					@Override
					public MdiEntry createMDiEntry(String id) {
						MdiEntry entry = mdi.createEntryFromEventListener(
								MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS,
								MySharesView.class,
								MultipleDocumentInterface.SIDEBAR_SECTION_MY_SHARES, true,
								null, 
								SB_Transfers.getSectionPosition(mdi, MultipleDocumentInterface.SIDEBAR_SECTION_MY_SHARES));

						entry.setImageLeftID( "image.sidebar.myshares" );

						return entry;
					}
				});

		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_MY_TRACKER,
				new MdiEntryCreationListener() {
					@Override
					public MdiEntry createMDiEntry(String id) {
						MdiEntry entry = mdi.createEntryFromEventListener(
								MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS,
								MyTrackerView.class,
								MultipleDocumentInterface.SIDEBAR_SECTION_MY_TRACKER, true,
								null, 
								SB_Transfers.getSectionPosition(mdi, MultipleDocumentInterface.SIDEBAR_SECTION_MY_TRACKER));

						entry.setImageLeftID( "image.sidebar.mytracker" );

						return entry;
					}
				});

		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_CLIENT_STATS,
				new MdiEntryCreationListener() {
					@Override
					public MdiEntry createMDiEntry(String id) {
						MdiEntry entry = mdi.createEntryFromEventListener(
								MultipleDocumentInterface.SIDEBAR_HEADER_PLUGINS,
								ClientStatsView.class,
								MultipleDocumentInterface.SIDEBAR_SECTION_CLIENT_STATS, true,
								null, null);

						entry.setImageLeftID( "image.sidebar.clientstats" );

						return entry;
					}
				});

		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG,
				new MdiEntryCreationListener2() {

					@Override
					public MdiEntry createMDiEntry(MultipleDocumentInterface mdi,
					                               String id, Object datasource, Map<?, ?> params) {

						String section = (datasource instanceof String)
								? ((String) datasource) : null;

						boolean uiClassic = COConfigurationManager.getStringParameter(
								"ui").equals("az2");
						if (	uiClassic ||
								COConfigurationManager.getBooleanParameter(	"Show Options In Side Bar")) {
							MdiEntry entry = ((MultipleDocumentInterfaceSWT) mdi).createEntryFromEventListener(
									MultipleDocumentInterface.SIDEBAR_HEADER_PLUGINS,
									ConfigView.class,
									MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG, true, section,
									null);

							entry.setImageLeftID( "image.sidebar.config" );

  						return entry;
						}

						ConfigShell.getInstance().open(section);
						return null;
					}
				});

		try {
			if ( !COConfigurationManager.getBooleanParameter( "my.shares.view.auto.open.done", false )){
				
				final ShareManager share_manager = pi.getShareManager();
				if (share_manager.getShares().length > 0) {
					// stop showing this by default
					// mdi.showEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_MY_SHARES);
				} else {
					shareManagerListener = new ShareManagerListener() {
						boolean done = false;
						@Override
						public void resourceModified(ShareResource old_resource,
						                             ShareResource new_resource) {
						}
	
						@Override
						public void resourceDeleted(ShareResource resource) {
						}
	
						@Override
						public void resourceAdded(ShareResource resource) {
							if (done) {
								return;
							}
							done = true;
							share_manager.removeListener(this);
							
							COConfigurationManager.setParameter( "my.shares.view.auto.open.done", true );
							
							mdi.loadEntryByID( MultipleDocumentInterface.SIDEBAR_SECTION_MY_SHARES, false);
						}
	
						@Override
						public void reportProgress(int percent_complete) {
						}
	
						@Override
						public void reportCurrentTask(String task_description) {
						}
					};
					share_manager.addListener(shareManagerListener);
				}
			}
		} catch (Throwable t) {
		}

		try{
			if ( !COConfigurationManager.getBooleanParameter( "my.tracker.view.auto.open.done", false )){
	
				// Load Tracker View on first host of file
				TRHost trackerHost = CoreFactory.getSingleton().getTrackerHost();
				trackerHostListener = new TRHostListener() {
					boolean done = false;
		
					@Override
					public void torrentRemoved(TRHostTorrent t) {
					}
		
					@Override
					public void torrentChanged(TRHostTorrent t) {
					}
		
					@Override
					public void torrentAdded(TRHostTorrent t) {
						if (done) {
							return;
						}
						done = true;
						trackerHost.removeListener(this);
						
						COConfigurationManager.setParameter( "my.tracker.view.auto.open.done", true );
						
						mdi.loadEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_MY_TRACKER,	false);
					}
		
					@Override
					public boolean handleExternalRequest(InetSocketAddress client_address,
					                                     String user, String url, URL absolute_url, String header, InputStream is,
					                                     OutputStream os, AsyncController async)
							throws IOException {
						return false;
					}
				};
				trackerHost.addListener(trackerHostListener);
			}
		} catch (Throwable t) {
		}

		UIManager uim = pi.getUIManager();
		if (uim != null) {
			MenuItem menuItem = uim.getMenuManager().addMenuItem(
					MenuManager.MENU_MENUBAR, "tags.view.heading");
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
			menuItem.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					UIFunctionsManager.getUIFunctions().getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_TAGS);
				}
			});

			menuItem = uim.getMenuManager().addMenuItem(
					MenuManager.MENU_MENUBAR, "tag.discovery.view.heading");
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
			menuItem.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					UIFunctionsManager.getUIFunctions().getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_TAG_DISCOVERY);
				}
			});

			menuItem = uim.getMenuManager().addMenuItem(
					MenuManager.MENU_MENUBAR, "chats.view.heading");
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
			menuItem.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					UIFunctionsManager.getUIFunctions().getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_CHAT);
				}
			});

			menuItem = uim.getMenuManager().addMenuItem(
					MenuManager.MENU_MENUBAR, "archivedlsview.view.heading");
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
			menuItem.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					UIFunctionsManager.getUIFunctions().getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_ARCHIVED_DOWNLOADS );
				}
			});

			menuItem = uim.getMenuManager().addMenuItem(
					MenuManager.MENU_MENUBAR, "downloadhistoryview.view.heading");
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
			menuItem.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					UIFunctionsManager.getUIFunctions().getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_DOWNLOAD_HISTORY );
				}
			});
			
			menuItem = uim.getMenuManager().addMenuItem(
					MenuManager.MENU_MENUBAR, "alltrackersview.view.heading");
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
			menuItem.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					UIFunctionsManager.getUIFunctions().getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_ALL_TRACKERS );
				}
			});

		}

		//		System.out.println("Activate sidebar " + startTab + " took "
		//				+ (SystemTime.getCurrentTime() - startTime) + "ms");
		//		startTime = SystemTime.getCurrentTime();
	}

	private static void setupSidebarClassic(final MultipleDocumentInterfaceSWT mdi) {
		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY,
				new MdiEntryCreationListener() {

					@Override
					public MdiEntry createMDiEntry(String id) {
						boolean uiClassic = COConfigurationManager.getStringParameter("ui").equals(
								"az2");
						String title = uiClassic ? "{MyTorrentsView.mytorrents}"
								: ("{sidebar."
										+ MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY + "}");
						MdiEntry entry = mdi.createEntryFromSkinRef(null,
								MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY, "library",
								title, null, null, false,
								MultipleDocumentInterface.SIDEBAR_POS_FIRST);
						entry.setImageLeftID("image.sidebar.library");
						return entry;
					}
				});

		mdi.registerEntry("Tag\\..*", new MdiEntryCreationListener2() {

			@Override
			public MdiEntry createMDiEntry(MultipleDocumentInterface mdi, String id,
			                               Object datasource, Map<?, ?> params) {

				if (datasource instanceof Tag) {
					Tag tag = (Tag) datasource;

					return sb_transfers.setupTag(tag);

				}else{

					try{
							// id format is "Tag." + tag.getTagType().getTagType() + "." + tag.getTagID();

						TagManager tm = TagManagerFactory.getTagManager();

						String[] bits = id.split( "\\." );

						int	tag_type = Integer.parseInt( bits[1] );
						int	tag_id	 = Integer.parseInt( bits[2] );

						Tag tag = tm.getTagType( tag_type ).getTag( tag_id );

						if ( tag != null ){

							return sb_transfers.setupTag(tag);
						}
					}catch( Throwable e ){

					}
				}

				return null;
			}
		});

		sb_transfers = new SB_Transfers(mdi, false);
		
		SBC_ActivityTableView.setupSidebarEntry(mdi);

		mdi.showEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY);
	}

	private static void setupSidebarVuzeUI(final MultipleDocumentInterfaceSWT mdi) {
		
		mdi.setPreferredOrder(preferredOrder);

		sb_dashboard = new SB_Dashboard(mdi);
		
		for (int i = 0; i < preferredOrder.length; i++) {
			String id = preferredOrder[i];
			mdi.registerEntry(id, new MdiEntryCreationListener() {
				@Override
				public MdiEntry createMDiEntry(String id) {
					
					MdiEntry entry;
					
					if ( id.equals( MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD )) {
						
						entry = sb_dashboard.setupMDIEntry();
						
					}else{
						
						entry = mdi.createHeader(id, "sidebar." + id, null);
	
						if ( entry == null ){
	
							return( null );
						}
	
						entry.setDefaultExpanded(true);
	
						if (id.equals(MultipleDocumentInterface.SIDEBAR_HEADER_PLUGINS)) {
							entry.addListener(new MdiChildCloseListener() {
								@Override
								public void mdiChildEntryClosed(MdiEntry parent, MdiEntry child,
								                                boolean user) {
									if (mdi.getChildrenOf(parent.getId()).size() == 0) {
										parent.close(true);
									}
								}
							});
	
							PluginInterface pi = PluginInitializer.getDefaultInterface();
							UIManager uim = pi.getUIManager();
							MenuManager menuManager = uim.getMenuManager();
							MenuItem menuItem;
	
							menuItem = menuManager.addMenuItem("sidebar."
									+ MultipleDocumentInterface.SIDEBAR_HEADER_PLUGINS,
									"label.plugin.options");
	
							menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
	
							menuItem.addListener(new MenuItemListener() {
								@Override
								public void selected(MenuItem menu, Object target) {
									UIFunctions uif = UIFunctionsManager.getUIFunctions();
	
									if (uif != null) {
										uif.getMDI().showEntryByID(
												MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG,
												"plugins");
									}
								}
							});
						}
					}

					return entry;
				}
			});
		}

		sb_transfers = new SB_Transfers(mdi, true);
		sb_vuze = new SB_Vuze(mdi);
		
		new SB_Discovery(mdi);

		mdi.loadEntryByID(MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD,false);
		
		mdi.loadEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY, false);
		
		if ( COConfigurationManager.getBooleanParameter( "Show New In Side Bar" )){
		
			mdi.loadEntryByID( MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY_UNOPENED, false);
		}
		
		mdi.loadEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_SUBSCRIPTIONS, false);
		
		mdi.loadEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_DEVICES, false);
		
		mdi.loadEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_ACTIVITIES, false);
	}

	public static SB_Transfers getSb_transfers() {
		return sb_transfers;
	}

	public static SB_Dashboard getSb_dashboard() {
		return sb_dashboard;
	}
	
	public static void dispose() {
		if (Utils.isAZ2UI()) {
			// TODO
		} else {
			// In theory, there would be an mdi.dispose that disposed of SB_Transfers and everything else
			// MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
			//mdi.dispose();
		}
		if (sb_dashboard != null) {
			sb_dashboard.dispose();
			sb_dashboard = null;
		}
		if (sb_transfers != null) {
			sb_transfers.dispose();
			sb_transfers = null;
		}
		if (sb_vuze != null) {
			sb_vuze.dispose();
			sb_vuze = null;
		}

		if (trackerHostListener != null) {
			TRHost trackerHost = CoreFactory.getSingleton().getTrackerHost();
			if (trackerHost != null) {
				trackerHost.removeListener(trackerHostListener);
			}
			trackerHostListener = null;
		}

		if (shareManagerListener != null) {
			try {
				ShareManager share_manager = PluginInitializer.getDefaultInterface().getShareManager();
				if (share_manager != null) {
					share_manager.removeListener(shareManagerListener);
				}
				shareManagerListener = null;
			} catch (ShareException ignore) {
			}
		}

		if (configBetaEnabledListener != null) {
			COConfigurationManager.removeParameterListener(
					"Beta Programme Enabled", configBetaEnabledListener);
			configBetaEnabledListener = null;
		}

	}
}
