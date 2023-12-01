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
import java.util.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.history.DownloadHistoryEvent;
import com.biglybt.core.history.DownloadHistoryListener;
import com.biglybt.core.history.DownloadHistoryManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peermanager.control.*;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagManagerListener;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.tag.TagTypeListener;
import com.biglybt.core.tracker.AllTrackersManager;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackers;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackersEvent;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackersListener;
import com.biglybt.core.tracker.host.TRHost;
import com.biglybt.core.tracker.host.TRHostListener;
import com.biglybt.core.tracker.host.TRHostTorrent;
import com.biglybt.core.util.*;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta.ChatInstance;
import com.biglybt.plugin.net.buddy.BuddyPluginUtils;
import com.biglybt.plugin.net.buddy.swt.SBC_ChatOverview;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo2;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoManager;
import com.biglybt.ui.mdi.*;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.devices.DeviceManagerUI;
import com.biglybt.ui.swt.mainwindow.IMenuConstants;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.views.*;
import com.biglybt.ui.swt.views.clientstats.ClientStatsView;
import com.biglybt.ui.swt.views.skin.*;
import com.biglybt.ui.swt.views.skin.sidebar.SideBar;
import com.biglybt.ui.swt.views.stats.StatsView;
import com.biglybt.ui.swt.views.utils.ManagerUtils;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.*;
import com.biglybt.pif.sharing.*;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.menus.MenuManager;

import static com.biglybt.ui.mdi.MultipleDocumentInterface.*;

public class MainMDISetup
{

	private static ParameterListener configBetaEnabledListener;
	private static TRHostListener trackerHostListener;
	private static TRHostListener trackerHostListener2;
	private static SB_Dashboard	sb_dashboard;
	private static SB_Transfers sb_transfers;
	private static ShareManagerListener shareManagerListener;
	private static ShareManagerListener shareManagerListener2;
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

	
	public static void setupSideBar(final MultipleDocumentInterfaceSWT mdi) {
		mdi.setDefaultEntryID(SideBar.SIDEBAR_SECTION_LIBRARY);

		if (Utils.isAZ2UI()) {
			setupSidebarClassic(mdi);
		} else {
			setupSidebarVuzeUI(mdi);
		}

		SBC_TorrentDetailsView.TorrentDetailMdiEntry.register(mdi);

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
		
		// Note: We don't use ViewManagerSWT because it adds a menu item, and we
		//       manually do that for StatsView and others
		//		ViewManagerSWT vi = ViewManagerSWT.getInstance();
		//		vi.registerView(VIEW_MAIN, 
		//	  	new UISWTViewBuilderCore(StatsView.VIEW_ID, null, StatsView.class)
		//	  	  .setParentEntryID(SIDEBAR_HEADER_PLUGINS));

		mdi.registerEntry(
			StatsView.VIEW_ID, 
			id -> {
				MdiEntry entry = 
					mdi.createEntry(
						new UISWTViewBuilderCore(id, null, StatsView.class).setParentEntryID(
						MultipleDocumentInterface.SIDEBAR_HEADER_PLUGINS),
					true);
				
				ViewTitleInfo title_info =
						new ViewTitleInfo()
						{
							@Override
							public Object
							getTitleInfoProperty(
								int propertyID)
							{
								if ( propertyID == TITLE_TEXT_ID ){
									
									return( "Stats.title.full" );
								}
								
								return( null );
							}
						};
						
				entry.setViewTitleInfo( title_info );
				
				entry.setImageLeftID("image.sidebar.stats2");
				
				return( entry );
			}
			);

		mdi.registerEntry(SIDEBAR_SECTION_ALLPEERS,
				id -> {
					UISWTViewBuilderCore builder = new UISWTViewBuilderCore(id, null,
							PeersSuperView.class);
					builder.setParentEntryID(SIDEBAR_HEADER_TRANSFERS);
					builder.setPreferredAfterID(
							SB_Transfers.getSectionPosition(mdi, SIDEBAR_SECTION_ALLPEERS));
					MdiEntry entry = mdi.createEntry(builder, true);

					PeerControlScheduler scheduler = PeerControlSchedulerFactory.getSingleton(0);

					ViewTitleInfo title_info =
							new ViewTitleInfo()
							{
								@Override
								public Object
								getTitleInfoProperty(
									int propertyID)
								{
									if ( propertyID == TITLE_INDICATOR_TEXT ){
										
										int[] counts = scheduler.getPeerCount();
										
										return( counts[0] + " | " + counts[1] );
									}
									
									return( null );
								}
							};
							
					entry.setViewTitleInfo( title_info );

					entry.setImageLeftID("image.sidebar.allpeers");
					
					final TimerEventPeriodic	timer =
							SimpleTimer.addPeriodicEvent(
									"sb:allpeers",
									1*1000,
									new TimerEventPerformer()
									{
										private int last_count1 = -1;
										private int last_count2 = -1;
										
										@Override
										public void
										perform(
											TimerEvent event)
										{
											int[] counts = scheduler.getPeerCount();
											
											int c1 = counts[0];
											int c2 = counts[1];
											
											if ( c1 != last_count1 || c2 != last_count2 ){
											
												last_count1 = c1;
												last_count2 = c2;
														
												entry.redraw();

												ViewTitleInfoManager.refreshTitleInfo( title_info );
												
												entry.redraw();
											}
										}
									});

					entry.addListener(
						new MdiCloseListener() {

							@Override
							public void
							mdiEntryClosed(
								MdiEntry entry, boolean userClosed)
							{
								timer.cancel();
							}
						});

					return( entry );
				});

		
		mdi.registerEntry(SIDEBAR_SECTION_ALLPIECES,
				id -> {
					UISWTViewBuilderCore builder = new UISWTViewBuilderCore(id, null, PiecesSuperView.class);
					
					builder.setParentEntryID(SIDEBAR_HEADER_TRANSFERS);
					
					builder.setPreferredAfterID( SB_Transfers.getSectionPosition(mdi, SIDEBAR_SECTION_ALLPIECES));
					
					MdiEntrySWT entry = mdi.createEntry(builder, true);

					PeerControlScheduler scheduler = PeerControlSchedulerFactory.getSingleton(0);

					ViewTitleInfo title_info =
							new ViewTitleInfo()
							{
								@Override
								public Object
								getTitleInfoProperty(
									int propertyID)
								{
									if ( propertyID == TITLE_INDICATOR_TEXT ){
										
										int[] counts = scheduler.getPieceCount();
										
										UISWTViewEventListener listener = entry.getEventListener();
										
										if ( listener instanceof PiecesViewBase ){
											
											int count = ((PiecesViewBase)listener).getUploadingPieceCount();
											
											if ( count >= 0 ){
											
												counts[1] = count;
											}
										}
										
										return( counts[0] + " | " + counts[1] );
									}
									
									return( null );
								}
							};
							
					entry.setViewTitleInfo( title_info );

					entry.setImageLeftID("image.sidebar.allpieces");

					final TimerEventPeriodic	timer =
							SimpleTimer.addPeriodicEvent(
									"sb:allpieces",
									1*1000,
									new TimerEventPerformer()
									{
										private int last_count1 = -1;
										private int last_count2 = -1;
										
										@Override
										public void
										perform(
											TimerEvent event)
										{
											int[] counts = scheduler.getPieceCount();
											
											UISWTViewEventListener listener = entry.getEventListener();
											
											if ( listener instanceof PiecesViewBase ){
												
												int count = ((PiecesViewBase)listener).getUploadingPieceCount();
												
												if ( count >= 0 ){
												
													counts[1] = count;
												}
											}
											
											int c1 = counts[0];
											int c2 = counts[1];
											
											if ( c1 != last_count1 || c2 != last_count2 ){
											
												last_count1 = c1;
												last_count2 = c2;
														
												entry.redraw();

												ViewTitleInfoManager.refreshTitleInfo( title_info );
												
												entry.redraw();
											}
										}
									});

					entry.addListener(
						new MdiCloseListener() {

							@Override
							public void
							mdiEntryClosed(
								MdiEntry entry, boolean userClosed)
							{
								timer.cancel();
							}
						});
					return( entry );
				});
		
		mdi.registerEntry(
				SIDEBAR_SECTION_ALLBLOCKS,
				id -> {
					UISWTViewBuilderCore builder = new UISWTViewBuilderCore(id, null, PieceBlocksView.class);
					
					builder.setParentEntryID(SIDEBAR_HEADER_TRANSFERS);
					
					builder.setPreferredAfterID( SB_Transfers.getSectionPosition(mdi, SIDEBAR_SECTION_ALLBLOCKS));
					
					builder.setInitialDatasource( "" );	// signifies all-blocks view rather than dm specific
					
					MdiEntrySWT entry = mdi.createEntry(builder, true);

					PeerControlScheduler scheduler = PeerControlSchedulerFactory.getSingleton(0);

					ViewTitleInfo title_info =
							new ViewTitleInfo()
							{
								@Override
								public Object
								getTitleInfoProperty(
									int propertyID)
								{
									if ( propertyID == TITLE_TEXT ){
										
										return( MessageText.getString( "MainWindow.menu.view.allblocks" ));
										
									}else if ( propertyID == TITLE_INDICATOR_TEXT ){
										
										int[] counts = scheduler.getPieceCount();
										
										return( "" + counts[0] );
									}
									
									return( null );
								}
							};
							
					entry.setViewTitleInfo( title_info );

					entry.setImageLeftID("image.sidebar.allblocks");

					final TimerEventPeriodic	timer =
							SimpleTimer.addPeriodicEvent(
									"sb:allblocks",
									1*1000,
									new TimerEventPerformer()
									{
										private int last_count1 = -1;
										
										@Override
										public void
										perform(
											TimerEvent event)
										{
											int[] counts = scheduler.getPieceCount();
											
											int c1 = counts[0];
											
											if ( c1 != last_count1 ){
											
												last_count1 = c1;
														
												entry.redraw();

												ViewTitleInfoManager.refreshTitleInfo( title_info );
												
												entry.redraw();
											}
										}
									});

					entry.addListener(
						new MdiCloseListener() {

							@Override
							public void
							mdiEntryClosed(
								MdiEntry entry, boolean userClosed)
							{
								timer.cancel();
							}
						});
					return( entry );
				});
		
		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_LOGGER,
				id -> {
					UISWTViewBuilderCore builder = new UISWTViewBuilderCore(id, null,
							LoggerView.class).setParentEntryID(SIDEBAR_HEADER_PLUGINS);
					return mdi.createEntry(builder, true);
				});

		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_TAGS,
				new MdiEntryCreationListener() {
					@Override
					public MdiEntry createMDiEntry(String id) {
						MdiEntry entry = mdi.createEntryFromSkinRef(
								SIDEBAR_HEADER_TRANSFERS, id,
								"tagsview", "{tags.view.heading}", null, null, true, 
								SB_Transfers.getSectionPosition(mdi, MultipleDocumentInterface.SIDEBAR_SECTION_TAGS));
						
						entry.setImageLeftID("image.sidebar.tag-overview");
						entry.setDefaultExpanded(true);

						TagManager tm = TagManagerFactory.getTagManager();

						ViewTitleInfo2 title_info =
							new ViewTitleInfo2()
							{
								@Override
								public Object
								getTitleInfoProperty(
									int propertyID)
								{
									if ( propertyID == TITLE_INDICATOR_TEXT ){
										
										int num = 0;
										
										for ( TagType tt: tm.getTagTypes()){
											
											num += tt.getTagCount();
										}
										
										return( String.valueOf( num ));
									}
									
									return( null );
								}
								
								public void titleInfoLinked(MultipleDocumentInterface mdi, MdiEntry mdiEntry){}
								
								public MdiEntry	getLinkedMdiEntry(){
									return( entry );
								}
							};

						entry.setViewTitleInfo( title_info );
												
						TagTypeListener ttl = 
							new TagTypeListener()
							{
								public void
								tagTypeChanged(
									TagType		tag_type )
								{
								}
	
								public void
								tagEventOccurred(
									TagEvent			event )
								{
									int type = event.getEventType();
									
									if ( type == TagEvent.ET_TAG_ADDED || type == TagEvent.ET_TAG_REMOVED ){
										
										ViewTitleInfoManager.refreshTitleInfo( title_info );
									}
								}

							};
							
						TagManagerListener tml = 
							new TagManagerListener()
							{
								public void
								tagTypeAdded(
									TagManager		manager,
									TagType			tag_type )
								{
									tag_type.addTagTypeListener( ttl, false );
										
								}
	
								public void
								tagTypeRemoved(
									TagManager		manager,
									TagType			tag_type )
								{
								}
							};
							
						tm.addTagManagerListener( tml, true);
												
						entry.addListener(
							new MdiCloseListener() {

								@Override
								public void
								mdiEntryClosed(
									MdiEntry entry, boolean userClosed)
								{
									tm.removeTagManagerListener( tml );
									
									for ( TagType tt: tm.getTagTypes()){
										
										tt.removeTagTypeListener( ttl );
									}
								}
							});
						
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

										if ( propertyID == TITLE_TEXT ){
											
											return( MessageText.getString( "mdi.entry.chatsoverview" ));
											
										}else if ( propertyID == TITLE_TEXT_ID ){
											
											return( "mdi.entry.chatsoverview" );
											
										}else if ( propertyID == TITLE_INDICATOR_TEXT ){

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
								"{mdi.entry.chatsoverview}", title_info, null, true, MultipleDocumentInterface.SIDEBAR_SECTION_SUBSCRIPTIONS );

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
								SIDEBAR_HEADER_TRANSFERS,
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
								SIDEBAR_HEADER_TRANSFERS,
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
							SIDEBAR_HEADER_TRANSFERS,
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
									int type = event.getEventType();
									
									if ( 	type == AllTrackersEvent.ET_TRACKER_ADDED || 
											type == AllTrackersEvent.ET_TRACKER_REMOVED ){
									
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
				id -> {
					UISWTViewBuilderCore builder = new UISWTViewBuilderCore(id, null,
							TorrentOptionsView.class).setParentEntryID(
									SIDEBAR_HEADER_TRANSFERS).setPreferredAfterID(
											SB_Transfers.getSectionPosition(mdi, id));
					MdiEntry entry = mdi.createEntry(builder, true);

					entry.setImageLeftID("image.sidebar.torrentoptions");

					return entry;
				});

		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_MY_SHARES,
				id -> {
					final ShareManager sm;
					
					{
						ShareManager temp;
						
						try{
							temp = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface().getShareManager();
							
						}catch( Throwable e ){
							
							temp = null;
						}
						
						sm = temp;
					}
	
					ViewTitleInfo title_info =
							new ViewTitleInfo()
							{
								
								@Override
								public Object
								getTitleInfoProperty(
									int propertyID)
								{
									if ( propertyID == TITLE_INDICATOR_TEXT ){

										return( sm==null?null:String.valueOf( sm.getShareCount()));
									}

									return null;
								}
							};

					UISWTViewBuilderCore builder = new UISWTViewBuilderCore(id, null,
							MySharesView.class).setParentEntryID(
									SIDEBAR_HEADER_TRANSFERS).setPreferredAfterID(
											SB_Transfers.getSectionPosition(mdi, id));
					MdiEntry entry = mdi.createEntry(builder, true);

					entry.setViewTitleInfo( title_info );
					
					entry.setImageLeftID("image.sidebar.myshares");
						
					if ( sm != null ){
						
						shareManagerListener2 = 
								new ShareManagerListener()
								{
									public void
									resourceAdded(
										ShareResource		resource )
									{
										ViewTitleInfoManager.refreshTitleInfo( title_info );
										entry.redraw();
									}
			
									public void
									resourceModified(
										ShareResource		old_resource,
										ShareResource		new_resource )
									{
									}
			
									public void
									resourceDeleted(
										ShareResource		resource )
									{
										ViewTitleInfoManager.refreshTitleInfo( title_info );
										entry.redraw();
									}
			
									public void
									reportProgress(
										int		percent_complete ){}
			
									public void
									reportCurrentTask(
										String	task_description ){}						
								};
								
						sm.addListener( shareManagerListener2 );
					}
					
					return entry;
				});

		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_MY_TRACKER,
				id -> {
					
					TRHost trackerHost = CoreFactory.getSingleton().getTrackerHost();
					
					UISWTViewBuilderCore builder = new UISWTViewBuilderCore(id, null,
							MyTrackerView.class).setParentEntryID(
									SIDEBAR_HEADER_TRANSFERS).setPreferredAfterID(
											SB_Transfers.getSectionPosition(mdi, id));
					MdiEntry entry = mdi.createEntry(builder, true);

					entry.setImageLeftID("image.sidebar.mytracker");

					ViewTitleInfo title_info =
							new ViewTitleInfo()
							{
								
								@Override
								public Object
								getTitleInfoProperty(
									int propertyID)
								{
									if ( propertyID == TITLE_INDICATOR_TEXT ){

										return( String.valueOf( trackerHost.getTorrentCount()));
									}

									return null;
								}
							};
					
					entry.setViewTitleInfo( title_info );

					trackerHostListener2 = new TRHostListener() {
			
						@Override
						public void torrentRemoved(TRHostTorrent t) {
							ViewTitleInfoManager.refreshTitleInfo( title_info );
							entry.redraw();
						}
			
						@Override
						public void torrentChanged(TRHostTorrent t) {
						}
			
						@Override
						public void torrentAdded(TRHostTorrent t) {
							ViewTitleInfoManager.refreshTitleInfo( title_info );
							entry.redraw();
						}
			
						@Override
						public boolean handleExternalRequest(InetSocketAddress client_address,
						                                     String user, String url, URL absolute_url, String header, InputStream is,
						                                     OutputStream os, AsyncController async)
								throws IOException {
							return false;
						}
					};
					
					trackerHost.addListener(trackerHostListener2);
					
					return entry;
				});

		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_CLIENT_STATS,
				id -> {
					UISWTViewBuilderCore builder = new UISWTViewBuilderCore(id, null,
							ClientStatsView.class).setParentEntryID(
									SIDEBAR_HEADER_PLUGINS);
					MdiEntry entry = mdi.createEntry(builder, true);

					entry.setImageLeftID("image.sidebar.clientstats");

					return entry;
				});

		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG,
				(mdi1, id, datasource, params) -> {

					String section = (datasource instanceof String)
							? ((String) datasource) : null;

					if (Utils.isAZ2UI() || COConfigurationManager.getBooleanParameter(
							"Show Options In Side Bar")) {
						UISWTViewBuilderCore builder = new UISWTViewBuilderCore(id, null,
								ConfigView.class).setParentEntryID(
										SIDEBAR_HEADER_PLUGINS).setInitialDatasource(
												section);
						MdiEntry entry = mdi.createEntry(builder, true);

						entry.setImageLeftID("image.sidebar.cog");

						return entry;
					}

					ConfigShell.getInstance().open(section);
					return null;
				});

		PluginInterface pi = PluginInitializer.getDefaultInterface();
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

			menuItem = uim.getMenuManager().addMenuItem(
					MenuManager.MENU_MENUBAR, "MainWindow.menu.view.allpeers");
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
			menuItem.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					UIFunctionsManager.getUIFunctions().getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_ALLPEERS );
				}
			});
			
			menuItem = uim.getMenuManager().addMenuItem(
					MenuManager.MENU_MENUBAR, IMenuConstants.MENU_ID_ALL_PIECES);
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
			menuItem.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					UIFunctionsManager.getUIFunctions().getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_ALLPIECES );
				}
			});
			
			menuItem = uim.getMenuManager().addMenuItem(
					MenuManager.MENU_MENUBAR, IMenuConstants.MENU_ID_ALL_BLOCKS);
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
			menuItem.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					UIFunctionsManager.getUIFunctions().getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_ALLBLOCKS );
				}
			});
			
			menuItem = uim.getMenuManager().addMenuItem(
					MenuManager.MENU_MENUBAR, "diskops.view.heading");
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
			menuItem.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					UIFunctionsManager.getUIFunctions().getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_DISK_OPS );
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

		DeviceManagerUI.registerDiskOps( mdi, MultipleDocumentInterface.SIDEBAR_HEADER_TRANSFERS, true );
		
		setupCatsTags( mdi );
		
		sb_transfers = new SB_Transfers(mdi, false);
		
		sb_dashboard = new SB_Dashboard(mdi, false);
		
		SBC_ActivityTableView.setupSidebarEntry(mdi);

		mdi.showEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY);
	}

	private static void setupSidebarVuzeUI(final MultipleDocumentInterfaceSWT mdi) {
		
		mdi.setPreferredOrder(preferredOrder);

		setupCatsTags( mdi );
		
		sb_dashboard = new SB_Dashboard(mdi, true);
		
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
	
						ViewTitleInfo title_info =
							new ViewTitleInfo()
							{
								@Override
								public Object
								getTitleInfoProperty(
									int propertyID)
								{
										// without this we get crap where the title is overwritten
										// from the auto-open map title in wrong locale
									
									if ( propertyID == TITLE_TEXT_ID ){
										
										return( "sidebar." + id );
										
									}else  if ( propertyID == TITLE_TEXT ){
										
										return( MessageText.getString( "sidebar." + id ));
									}
									
									return( null );
								}
							};
								
						entry.setViewTitleInfo(title_info);
						
						entry.setDefaultExpanded(true);
	
						if (id.equals(SIDEBAR_HEADER_PLUGINS)) {
							// remove header when there are no children
							entry.addListener((parent, ch, user) -> {
								if (mdi.getChildrenOf(SIDEBAR_HEADER_PLUGINS).isEmpty()) {
									mdi.closeEntry(parent,false);
								}
							});
	
							PluginInterface pi = PluginInitializer.getDefaultInterface();
							UIManager uim = pi.getUIManager();
							MenuManager menuManager = uim.getMenuManager();
							MenuItem menuItem;
	
							menuItem = menuManager.addMenuItem("sidebar."
									+ SIDEBAR_HEADER_PLUGINS,
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

	private static void
	setupCatsTags(MultipleDocumentInterfaceSWT mdi )
	{
		mdi.registerEntry("Tag\\..*", new MdiEntryCreationListener2() {

			@Override
			public MdiEntry createMDiEntry(MultipleDocumentInterface mdi, String id,
			                               Object datasource, Map params) {

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

		mdi.registerEntry("Cat\\..*", new MdiEntryCreationListener2() {

			@Override
			public MdiEntry createMDiEntry(MultipleDocumentInterface mdi, String id,
			                               Object datasource, Map params) {

				if (datasource instanceof Category) {

					return sb_transfers.setupCategory((Category)datasource);

				}else{

					try{
							// id format is "Cat." + base32 encoded name.getBytes();

						TagManager tm = TagManagerFactory.getTagManager();

						String[] bits = id.split( "\\." );

						String cat_name = new String( Base32.decode( bits[1] ));
						
						Category cat = CategoryManager.getCategory( cat_name );

						if ( cat != null ){

							return sb_transfers.setupCategory(cat);
						}
					}catch( Throwable e ){

					}
				}

				return null;
			}
		});
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
			trackerHost.removeListener(trackerHostListener);
			trackerHostListener = null;
		}
		if (trackerHostListener2 != null) {
			TRHost trackerHost = CoreFactory.getSingleton().getTrackerHost();
			trackerHost.removeListener(trackerHostListener2);
			trackerHostListener2 = null;
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
		if (shareManagerListener2 != null) {
			try {
				ShareManager share_manager = PluginInitializer.getDefaultInterface().getShareManager();
				if (share_manager != null) {
					share_manager.removeListener(shareManagerListener2);
				}
				shareManagerListener2 = null;
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
