/*
 * Created on 9 Jul 2007
 * Created by Allan Crooks
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.biglybt.ui.swt;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreOperation;
import com.biglybt.core.CoreOperationTask;
import com.biglybt.core.tag.Tag;
import com.biglybt.ui.UIFunctions.TagReturner;
import com.biglybt.ui.selectedcontent.ISelectedContent;
import com.biglybt.ui.selectedcontent.ISelectedVuzeFileContent;
import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.IMenuConstants;
import com.biglybt.ui.swt.mainwindow.MenuFactory;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.maketorrent.MultiTrackerEditor;
import com.biglybt.ui.swt.maketorrent.TrackerEditorListener;
import com.biglybt.ui.swt.maketorrent.WebSeedsEditor;
import com.biglybt.ui.swt.maketorrent.WebSeedsEditorListener;
import com.biglybt.ui.swt.views.utils.CategoryUIUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerFileInfoSet;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerInitialisationAdapter;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.ipfilter.IpFilterManagerFactory;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerSource;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentCreator;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.torrent.TOTorrentProgressListener;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.tracker.util.TRTrackerUtils;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadStub.DownloadStubEx;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pif.ui.UIPluginView;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.toolbar.UIToolBarActivationListener;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;
import com.biglybt.pif.ui.toolbar.UIToolBarManager;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.ui.swt.exporttorrent.wizard.ExportTorrentWizard;
import com.biglybt.ui.swt.minibar.DownloadBar;
import com.biglybt.ui.swt.sharing.ShareUtils;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.views.FilesViewMenuUtil;
import com.biglybt.ui.swt.views.ViewUtils;
import com.biglybt.ui.swt.views.tableitems.mytorrents.RankItem;
import com.biglybt.ui.swt.views.utils.ManagerUtils;
import com.biglybt.ui.swt.views.utils.TagUIUtils;
import com.biglybt.core.torrent.PlatformTorrentUtils;

import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.speedmanager.SpeedLimitHandler;
import com.biglybt.plugin.extseed.ExternalSeedPlugin;
import com.biglybt.plugin.net.buddy.BuddyPluginUtils;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta.ChatInstance;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;

/**
 * @author Allan Crooks
 *
 */
public class TorrentUtil
{
	private static final String TU_GROUP			= "tu.group";
	private static final String BF_GROUP			= "bf.group";
	
	public static final String	TU_ITEM_RECHECK			= "tui.recheck";
	public static final String	TU_ITEM_CHECK_FILES		= "tui.checkfiles";
	public static final String	TU_ITEM_SHOW_SIDEBAR	= "tui.showsidebar";
	
	public static final String	BF_ITEM_BACK	= "bfi.back";
	public static final String	BF_ITEM_FORWARD	= "bfi.forward";
	
	private static final String[] TB_ITEMS = {
			TU_ITEM_RECHECK,
			TU_ITEM_CHECK_FILES,
			TU_ITEM_SHOW_SIDEBAR,
			
			BF_ITEM_BACK,
			BF_ITEM_FORWARD,
	};
	
	private static boolean	initialised;
	
	public static synchronized void
	init()
	{
		if ( initialised ){
			
			return;
		}
		
		initialised = true;
		
		for ( String id: TB_ITEMS ){
		
			String key = "IconBar.visible." + id;
		
			if ( !COConfigurationManager.hasParameter( key, false )){
		
				COConfigurationManager.setParameter( key, false );
			}
		}
		
		UIManager ui_manager = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface().getUIManager();
		
		ui_manager.addUIListener(
				new UIManagerListener()
				{
					private List<UIToolBarItem>	items = new ArrayList<>();
					
					private boolean attached;
					
					@Override
					public void
					UIAttached(
						UIInstance		instance )
					{
						if ( instance.getUIType().equals(UIInstance.UIT_SWT) ){
							
							attached = true;
							
							UIToolBarManager tbm = instance.getToolBarManager();
							
							if ( tbm != null ){
								
									// back							
								
								UIToolBarItem back_item = tbm.createToolBarItem( BF_ITEM_BACK );
							
								back_item.setGroupID( BF_GROUP );
								
								back_item.setImageID( "back" );
								
								back_item.setToolTipID( "label.back" );
								
								back_item.setDefaultActivationListener(new UIToolBarActivationListener() {
									@Override
									public boolean 
									toolBarItemActivated(
										ToolBarItem 	item, 
										long 			activationType,
									    Object 			datasource) 
									{	
										TableView tv = SelectedContentManager.getCurrentlySelectedTableView();

										if ( tv != null ){
										
											tv.moveBack();
										}
										
										return( true );
									}});
								
								addItem( tbm, back_item );
								
								
									// forward							
								
								UIToolBarItem forward_item = tbm.createToolBarItem( BF_ITEM_FORWARD );
							
								forward_item.setGroupID( BF_GROUP );
								
								forward_item.setImageID( "forward" );
								
								forward_item.setToolTipID( "label.forward" );
								
								forward_item.setDefaultActivationListener(new UIToolBarActivationListener() {
									@Override
									public boolean 
									toolBarItemActivated(
										ToolBarItem 	item, 
										long 			activationType,
									    Object 			datasource) 
									{	
										TableView tv = SelectedContentManager.getCurrentlySelectedTableView();
	
										if ( tv != null ){
										
											tv.moveForward();
										}
										
										return( true );
									}});
								
								addItem( tbm, forward_item );

									// refresh
								
								UIToolBarItem refresh_item = tbm.createToolBarItem( TU_ITEM_RECHECK );
							
								refresh_item.setGroupID( TU_GROUP );
								
								refresh_item.setImageID( "recheck" );
								
								refresh_item.setToolTipID( "MyTorrentsView.menu.recheck" );
								
								refresh_item.setDefaultActivationListener(new UIToolBarActivationListener() {
									@Override
									public boolean 
									toolBarItemActivated(
										ToolBarItem 	item, 
										long 			activationType,
									    Object 			datasource) 
									{	
										List<DownloadManager>	dms = getDMs( datasource );
										
										for ( DownloadManager dm: dms ){
											
											if ( dm.canForceRecheck()){
												
												dm.forceRecheck();
											}
										}
										
										return( true );
									}});
								
								addItem( tbm, refresh_item );
								
									// check files exist
								
								UIToolBarItem cfe_item = tbm.createToolBarItem( TU_ITEM_CHECK_FILES );
								
								cfe_item.setGroupID( TU_GROUP );
								
								cfe_item.setImageID( "filesexist" );
								
								cfe_item.setToolTipID( "MyTorrentsView.menu.checkfilesexist" );
								
								cfe_item.setDefaultActivationListener(new UIToolBarActivationListener() {
									@Override
									public boolean 
									toolBarItemActivated(
										ToolBarItem 	item, 
										long 			activationType,
									    Object 			datasource) 
									{	
										List<DownloadManager>	dms = getDMs( datasource );
										
										for ( DownloadManager dm: dms ){
											
											dm.filesExist( true );
										}
										
										return( true );
									}});
								
								addItem( tbm, cfe_item );
								
									// show sidebar 
								
								UIToolBarItem ssb_item = tbm.createToolBarItem( TU_ITEM_SHOW_SIDEBAR);
								
								ssb_item.setGroupID( TU_GROUP );
								
								ssb_item.setImageID( "sidebar" );
													
								COConfigurationManager.addAndFireParameterListener( 
									"Show Side Bar",
									new ParameterListener(){
										
										@Override
										public void parameterChanged(String name ){
											
											if ( attached ){
													
												if ( COConfigurationManager.getBooleanParameter( "IconBar.visible." + TU_ITEM_SHOW_SIDEBAR )){
													
													UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
													
												  	if ( uiFunctions != null ){
												  		
												  		uiFunctions.refreshIconBar();
												  	}
												}
											}else{
												
												COConfigurationManager.removeParameterListener( name, this );
											}
	
										}
									});
																
								ssb_item.setToolTipID( "v3.MainWindow.menu.view.sidebar" );
								
								ssb_item.setDefaultActivationListener(new UIToolBarActivationListener() {
									@Override
									public boolean 
									toolBarItemActivated(
										ToolBarItem 	item, 
										long 			activationType,
									    Object 			datasource) 
									{	
										boolean ss = COConfigurationManager.getBooleanParameter( "Show Side Bar" );
										
										COConfigurationManager.setParameter( "Show Side Bar", !ss );
										
										return( true );
									}});
								
								addItem( tbm, ssb_item );
							}
						}
					}
					
					private List<DownloadManager>
					getDMs(
						Object		ds )
					{
						List<DownloadManager>	result = new ArrayList<>();
						
						if ( ds instanceof Download ){
							
							result.add( PluginCoreUtils.unwrap((Download)ds));
							
						}else if ( ds instanceof Object[]){
							
							Object[] objs = (Object[])ds;
							
							for ( Object obj: objs ){
								
								if ( obj instanceof Download ){
									
									result.add( PluginCoreUtils.unwrap((Download)obj));
								}
							}
						}
						
						return( result );
					}
					
					private void
					addItem(
						UIToolBarManager		tbm,
						UIToolBarItem			item )
					{
						items.add( item );
						
						tbm.addToolBarItem( item );
					}
					
					@Override
					public void
					UIDetached(
						UIInstance		instance )
					{
						if ( instance.getUIType().equals(UIInstance.UIT_SWT )){
							
							attached = false;
							
							UIToolBarManager tbm = instance.getToolBarManager();
							
							if ( tbm != null){
								
								for ( UIToolBarItem item: items ){
									
									tbm.removeToolBarItem( item.getID());
								}
							}
							
							items.clear();
						}
					}
				});
	}
	
	// selected_dl_types -> 0 (determine that automatically), +1 (downloading), +2 (seeding), +3 (mixed - not used by anything yet)
	public static void fillTorrentMenu(final Menu menu,
																		 final DownloadManager[] dms, final Core core,
																		 boolean include_show_details,
																		 int selected_dl_types, final TableView tv) {

		// TODO: Build submenus on the fly
		Shell shell = Utils.findAnyShell();

		Shell menu_shell = menu.getShell();
		
		final boolean isSeedingView;
		switch (selected_dl_types) {
			case 1:
				isSeedingView = false;
				break;
			case 2:
				isSeedingView = true;
				break;
			case 0: {
				if (dms.length == 1) {
					isSeedingView = dms[0].isDownloadComplete(false);
					break;
				}
			}
			default:
				// I was going to raise an error, but let's not be too hasty. :)
				isSeedingView = false;
		}

		boolean hasSelection 		= dms.length > 0;
		boolean isSingleSelection 	= dms.length == 1;

		boolean isTrackerOn = TRTrackerUtils.isTrackerEnabled();
		int userMode = COConfigurationManager.getIntParameter("User Mode");

		// Enable/Disable Logic
		boolean bChangeDir = hasSelection;

		boolean start, stop, pause, changeUrl, barsOpened, forceStart;
		boolean forceStartEnabled, recheck, lrrecheck, manualUpdate, fileMove, canSetMOC, canClearMOC, fileExport, fileRescan;

		changeUrl = barsOpened = manualUpdate = fileMove = canSetMOC = fileExport = fileRescan = true;
		forceStart = forceStartEnabled = recheck = lrrecheck = start = stop = pause = canClearMOC = false;

		boolean canSetSuperSeed = false;
		boolean superSeedAllYes = true;
		boolean superSeedAllNo = true;

		boolean upSpeedDisabled = false;
		long totalUpSpeed = 0;
		boolean upSpeedUnlimited = false;
		long upSpeedSetMax = 0;

		boolean downSpeedDisabled = false;
		long totalDownSpeed = 0;
		boolean downSpeedUnlimited = false;
		long downSpeedSetMax = 0;

		boolean allScanSelected = true;
		boolean allScanNotSelected = true;

		boolean allStopped			= true;
		boolean allAllocatable		= true;
		boolean	allResumeIncomplete	 = true;

		boolean	hasClearableLinks = false;
		boolean	hasRevertableFiles = false;

		boolean globalMask = COConfigurationManager.getBooleanParameter( ConfigKeys.Transfer.BCFG_PEERCONTROL_HIDE_PIECE );

		boolean allMaskDC 			= true;
		
		if (hasSelection) {
			for (int i = 0; i < dms.length; i++) {
				DownloadManager dm = (DownloadManager) dms[i];

				try {
					int maxul = dm.getStats().getUploadRateLimitBytesPerSecond();
					if (maxul == 0) {
						upSpeedUnlimited = true;
					} else {
						if (maxul > upSpeedSetMax) {
							upSpeedSetMax = maxul;
						}
					}
					if (maxul == -1) {
						maxul = 0;
						upSpeedDisabled = true;
					}
					totalUpSpeed += maxul;

					int maxdl = dm.getStats().getDownloadRateLimitBytesPerSecond();
					if (maxdl == 0) {
						downSpeedUnlimited = true;
					} else {
						if (maxdl > downSpeedSetMax) {
							downSpeedSetMax = maxdl;
						}
					}
					if (maxdl == -1) {
						maxdl = 0;
						downSpeedDisabled = true;
					}
					totalDownSpeed += maxdl;

				} catch (Exception ex) {
					Debug.printStackTrace(ex);
				}

				int state = dm.getState();
				
				if (barsOpened && !DownloadBar.getManager().isOpen(dm)) {
					barsOpened = false;
				}
				stop = stop || ManagerUtils.isStopable(dm);

				start = start || ManagerUtils.isStartable(dm, true );

				pause = pause || ManagerUtils.isPauseable(dm);
						
				recheck = recheck || dm.canForceRecheck();

				lrrecheck = lrrecheck || ManagerUtils.canLowResourceRecheck( dm );
						
				forceStartEnabled = forceStartEnabled
						|| ManagerUtils.isForceStartable(dm, true);

				forceStart = forceStart || dm.isForceStart();

				boolean stopped = ManagerUtils.isStopped(dm);

				allStopped &= stopped;

				allAllocatable &= stopped && !dm.isDataAlreadyAllocated() && !dm.isDownloadComplete( false );		

				fileMove = fileMove && dm.canMoveDataFiles();
				
				fileExport = fileExport && dm.canExportDownload();

				if (userMode < 2) {
					TRTrackerAnnouncer trackerClient = dm.getTrackerClient();

					if (trackerClient != null) {
						boolean update_state = ((SystemTime.getCurrentTime() / 1000
								- trackerClient.getLastUpdateTime() >= TRTrackerAnnouncer.REFRESH_MINIMUM_SECS));
						manualUpdate = manualUpdate & update_state;
					}

				}
				
				bChangeDir &= (state == DownloadManager.STATE_ERROR
						|| state == DownloadManager.STATE_STOPPED || state == DownloadManager.STATE_QUEUED);
				/**
				 * Only perform a test on disk if:
				 *    1) We are currently set to allow the "Change Data Directory" option, and
				 *    2) We've only got one item selected - otherwise, we may potentially end up checking massive
				 *       amounts of files across multiple torrents before we generate a menu.
				 */
				if (bChangeDir && dms.length == 1) {
					bChangeDir = dm.isDataAlreadyAllocated();
					if (bChangeDir && state == DownloadManager.STATE_ERROR) {
						// filesExist is way too slow!
						bChangeDir = !dm.filesExist(true);
					} else {
						bChangeDir = false;
					}
				}

				boolean incomplete = !dm.isDownloadComplete(true);

				DownloadManagerState dm_state = dm.getDownloadState();

				String moc_dir = dm_state.getAttribute( DownloadManagerState.AT_MOVE_ON_COMPLETE_DIR );
				
				canSetMOC &= incomplete;
				canClearMOC |= (moc_dir != null && moc_dir.length() > 0 );
						
				boolean scan = dm_state.getFlag(
						DownloadManagerState.FLAG_SCAN_INCOMPLETE_PIECES);

				// include DND files in incomplete stat, since a recheck may
				// find those files have been completed
				
				allScanSelected = incomplete && allScanSelected && scan;
				allScanNotSelected = incomplete && allScanNotSelected && !scan;

				PEPeerManager pm = dm.getPeerManager();

				if (pm != null) {

					if (pm.canToggleSuperSeedMode()) {

						canSetSuperSeed = true;
					}

					if (pm.isSuperSeedMode()) {

						superSeedAllYes = false;

					} else {

						superSeedAllNo = false;
					}
				} else {
					superSeedAllYes = false;
					superSeedAllNo = false;
				}

				if (dm_state.isResumeDataComplete()){
					allResumeIncomplete = false;
				}

				if ( stopped && !hasClearableLinks ){
					
					TOTorrent torrent = dm.getTorrent();
					
					if ( torrent != null && !torrent.isSimpleTorrent()){
						
						if ( dm_state.getFileLinks().hasLinks()){

							hasClearableLinks = true;
						}
					}
				}

				if ( dm_state.getFileLinks().size() > 0 ){

					hasRevertableFiles = true;
				}
				
				Boolean dmmask = dm_state.getOptionalBooleanAttribute( DownloadManagerState.AT_MASK_DL_COMP_OPTIONAL );
				
				boolean mask = dmmask==null?globalMask:dmmask;
				
				allMaskDC = allMaskDC && mask;
			}

			fileRescan = allScanSelected || allScanNotSelected;

		} else { // empty right-click
			barsOpened = false;
			forceStart = false;
			forceStartEnabled = false;

			start = false;
			stop = false;
			fileMove = false;
			fileExport = false;
			fileRescan = false;
			canSetMOC = false;
			upSpeedDisabled = true;
			downSpeedDisabled = true;
			changeUrl = false;
			recheck = false;
			manualUpdate = false;
			allMaskDC = false;
		}

		// === Root Menu ===

		if (bChangeDir) {
			MenuItem menuItemChangeDir = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(menuItemChangeDir,
					"MyTorrentsView.menu.changeDirectory");
			menuItemChangeDir.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event e) {
					changeDirSelectedTorrents(dms, shell);
				}
			});
		}

		// Open Details
		if (include_show_details) {
			final MenuItem itemDetails = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemDetails, "MyTorrentsView.menu.showdetails");
			menu.setDefaultItem(itemDetails);
			Utils.setMenuItemImage(itemDetails, "details");
			itemDetails.addListener(SWT.Selection, new ListenerDMTask(dms) {
				@Override
				public void run(DownloadManager dm) {
					UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
					if (uiFunctions != null) {
						uiFunctions.getMDI().showEntryByID(
								MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS, dm);
					}
				}
			});
			itemDetails.setEnabled(hasSelection);
		}

		// Open Bar
		final MenuItem itemBar = new MenuItem(menu, SWT.CHECK);
		Messages.setLanguageText(itemBar, "MyTorrentsView.menu.showdownloadbar");
		Utils.setMenuItemImage(itemBar, "downloadBar");
		itemBar.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager dm) {
				if (DownloadBar.getManager().isOpen(dm)) {
					DownloadBar.close(dm);
				} else {
					DownloadBar.open(dm,shell);
				}
			} // run
		});
		itemBar.setEnabled(hasSelection);
		itemBar.setSelection(barsOpened);

		// ---
		new MenuItem(menu, SWT.SEPARATOR);

		// Run Data File
		final MenuItem itemOpen = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemOpen, "MyTorrentsView.menu.open");
		Utils.setMenuItemImage(itemOpen, "run");
		itemOpen.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager[] dms) {
				runDataSources(dms);
			}
		});
		itemOpen.setEnabled(hasSelection);

		MenuItem itemOpenWith = MenuBuildUtils.addOpenWithMenu( menu, false, dms );
		itemOpenWith.setEnabled(hasSelection&& MenuBuildUtils.hasOpenWithMenu( dms ));

		// Explore (or open containing folder)
		final boolean use_open_containing_folder = COConfigurationManager.getBooleanParameter("MyTorrentsView.menu.show_parent_folder_enabled");
		final MenuItem itemExplore = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemExplore, "MyTorrentsView.menu."
				+ (use_open_containing_folder ? "open_parent_folder" : "explore"));
		itemExplore.addListener(SWT.Selection, new ListenerDMTask(dms, false) {
			@Override
			public void run(DownloadManager dm) {
				ManagerUtils.open(dm, use_open_containing_folder);
			}
		});
		itemExplore.setEnabled(hasSelection);

		// Open in browser

		final Menu menuBrowse = new Menu(menu_shell,SWT.DROP_DOWN);
		final MenuItem itemBrowse = new MenuItem(menu, SWT.CASCADE);
		Messages.setLanguageText(itemBrowse, "MyTorrentsView.menu.browse");
		itemBrowse.setMenu(menuBrowse);

		final MenuItem itemBrowsePublic = new MenuItem(menuBrowse, SWT.PUSH);
		itemBrowsePublic.setText( MessageText.getString( "label.public" ) + "..." );
		itemBrowsePublic.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager dm) {
				ManagerUtils.browse(dm,false,true);
			}
		});

		final MenuItem itemBrowseAnon = new MenuItem(menuBrowse, SWT.PUSH);
		itemBrowseAnon.setText( MessageText.getString( "label.anon" ) + "..." );
		itemBrowseAnon.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager dm) {
				ManagerUtils.browse(dm,true,true);
			}
		});

		new MenuItem(menuBrowse, SWT.SEPARATOR);

		final MenuItem itemBrowseURL = new MenuItem(menuBrowse, SWT.PUSH);
		Messages.setLanguageText(itemBrowseURL, "label.copy.url.to.clip" );
		itemBrowseURL.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event){
				Utils.getOffOfSWTThread(
					new AERunnable() {
						@Override
						public void runSupport() {
							String url = ManagerUtils.browse(dms[0], true, false );
							if ( url != null ){
								ClipboardCopy.copyToClipBoard( url );
							}
						}
					});
			}});

		itemBrowseURL.setEnabled( isSingleSelection );

		new MenuItem(menuBrowse, SWT.SEPARATOR);

		final MenuItem itemBrowseDir = new MenuItem(menuBrowse, SWT.CHECK);
		Messages.setLanguageText(itemBrowseDir, "library.launch.web.in.browser.dir.list");
		itemBrowseDir.setSelection(COConfigurationManager.getBooleanParameter( "Library.LaunchWebsiteInBrowserDirList"));
		itemBrowseDir.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				COConfigurationManager.setParameter( "Library.LaunchWebsiteInBrowserDirList", itemBrowseDir.getSelection());
			}
		});

		itemBrowse.setEnabled(hasSelection);

		// === advanced menu ===

		final MenuItem itemAdvanced = new MenuItem(menu, SWT.CASCADE);
		Messages.setLanguageText(itemAdvanced, "MyTorrentsView.menu.advancedmenu"); //$NON-NLS-1$
		itemAdvanced.setEnabled(hasSelection);

		final Menu menuAdvanced = new Menu(menu_shell, SWT.DROP_DOWN);
		itemAdvanced.setMenu(menuAdvanced);

		// advanced > Download Speed Menu //

		long kInB = DisplayFormatters.getKinB();

		long maxDownload = COConfigurationManager.getIntParameter(
				"Max Download Speed KBs", 0) * kInB;
		long maxUpload = COConfigurationManager.getIntParameter(
				"Max Upload Speed KBs", 0) * kInB;

		ViewUtils.addSpeedMenu(menu_shell, menuAdvanced, true, true, true,
				hasSelection, downSpeedDisabled, downSpeedUnlimited, totalDownSpeed,
				downSpeedSetMax, maxDownload, upSpeedDisabled, upSpeedUnlimited,
				totalUpSpeed, upSpeedSetMax, maxUpload, dms.length, null,
				new ViewUtils.SpeedAdapter() {
					@Override
					public void setDownSpeed(final int speed) {
						ListenerDMTask task = new ListenerDMTask(dms) {
							@Override
							public void run(DownloadManager dm) {
								dm.getStats().setDownloadRateLimitBytesPerSecond(speed);
							}
						};
						task.go();
					}

					@Override
					public void setUpSpeed(final int speed) {
						ListenerDMTask task = new ListenerDMTask(dms) {
							@Override
							public void run(DownloadManager dm) {
								dm.getStats().setUploadRateLimitBytesPerSecond(speed);
							}
						};
						task.go();
					}
				});

		// advanced > Speed Limits

		final Menu speedLimitsMenu = new Menu(menuAdvanced.getShell(),
				SWT.DROP_DOWN);
		final MenuItem speedLimitsMenuItem = new MenuItem(menuAdvanced, SWT.CASCADE);
		Messages.setLanguageText(speedLimitsMenuItem,
				IMenuConstants.MENU_ID_SPEED_LIMITS);
		speedLimitsMenuItem.setMenu(speedLimitsMenu);

		MenuBuildUtils.addMaintenanceListenerForMenu(speedLimitsMenu,
				new MenuBuildUtils.MenuBuilder() {
					@Override
					public void buildMenu(Menu menu, MenuEvent menuEvent) {
						addSpeedLimitsMenu(dms, speedLimitsMenu);
					}
				});

		// advanced > Tracker Menu //
		final Menu menuTracker = new Menu(menu_shell, SWT.DROP_DOWN);
		final MenuItem itemTracker = new MenuItem(menuAdvanced, SWT.CASCADE);
		Messages.setLanguageText(itemTracker, "MyTorrentsView.menu.tracker");
		itemTracker.setMenu(menuTracker);
		itemExplore.setEnabled(hasSelection);
		addTrackerTorrentMenu(menuTracker, dms, changeUrl, manualUpdate,
				allStopped, use_open_containing_folder, fileMove );

		// advanced > files

		final MenuItem itemFiles = new MenuItem(menuAdvanced, SWT.CASCADE);
		Messages.setLanguageText(itemFiles, "ConfigView.section.files");

		final Menu menuFiles = new Menu(menu_shell, SWT.DROP_DOWN);
		itemFiles.setMenu(menuFiles);

		final MenuItem itemFileMoveData = new MenuItem(menuFiles, SWT.PUSH);
		Messages.setLanguageText(itemFileMoveData, "MyTorrentsView.menu.movedata");
		itemFileMoveData.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager[] dms) {
				moveDataFiles(shell, dms,false);
			}
		});
		itemFileMoveData.setEnabled(fileMove);
		
		if ( userMode > 0 ){
			final MenuItem itemFileMoveDataBatch = new MenuItem(menuFiles, SWT.PUSH);
			Messages.setLanguageText(itemFileMoveDataBatch, "MyTorrentsView.menu.movedata.batch");
			itemFileMoveDataBatch.addListener(SWT.Selection, new ListenerDMTask(dms) {
				@Override
				public void run(DownloadManager[] dms) {
					moveDataFiles(shell, dms,true);
				}
			});
			itemFileMoveData.setEnabled(fileMove);
		}
		final MenuItem itemFileMoveTorrent = new MenuItem(menuFiles, SWT.PUSH);
		Messages.setLanguageText(itemFileMoveTorrent,
				"MyTorrentsView.menu.movetorrent");
		itemFileMoveTorrent.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager[] dms) {
				moveTorrentFile(shell, dms);
			}
		});
		itemFileMoveTorrent.setEnabled(fileMove);

			// move on complete
		
		final Menu moc_menu = new Menu( menu_shell, SWT.DROP_DOWN);

		MenuItem moc_item = new MenuItem( menuFiles, SWT.CASCADE);

		Messages.setLanguageText( moc_item, "label.move.on.comp" );

		moc_item.setMenu( moc_menu );

		MenuItem clear_item = new MenuItem( moc_menu, SWT.PUSH);

		Messages.setLanguageText( clear_item, "Button.clear" );

		clear_item.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager[] dms) {
				clearMOC(dms);
			}
		});

		clear_item.setEnabled( canClearMOC );
		
		MenuItem set_item = new MenuItem( moc_menu, SWT.PUSH);

		Messages.setLanguageText( set_item, "label.set" );
		
		set_item.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager[] dms) {
				setMOC(shell, dms);
			}
		});
		
		set_item.setEnabled( canSetMOC );
		
		moc_item.setEnabled( canClearMOC || canSetMOC );
		
			// file export
		
		final MenuItem itemFileExport = new MenuItem(menuFiles, SWT.PUSH);
		Messages.setLanguageText(itemFileExport, "MyTorrentsView.menu.exportdownload");
		itemFileExport.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager[] dms) {
				exportDownloads(shell, dms);
			}
		});
		itemFileExport.setEnabled(fileExport);

		
		final MenuItem itemCheckFilesExist = new MenuItem(menuFiles, SWT.PUSH);
		Messages.setLanguageText(itemCheckFilesExist,
				"MyTorrentsView.menu.checkfilesexist");
		itemCheckFilesExist.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager dm) {
				dm.filesExist(true);
			}
		});

		final MenuItem itemLocateFiles = new MenuItem(menuFiles, SWT.PUSH);
		Messages.setLanguageText(itemLocateFiles,
				"MyTorrentsView.menu.locatefiles");
		itemLocateFiles.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager[] dms) {
				ManagerUtils.locateFiles( dms, shell );
			}
		});

			// periodically check incomplete pieces
		
		final MenuItem itemFileRescan = new MenuItem(menuFiles, SWT.CHECK);
		Messages.setLanguageText(itemFileRescan, "MyTorrentsView.menu.rescanfile");
		itemFileRescan.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager dm) {
				dm.getDownloadState().setFlag(
						DownloadManagerState.FLAG_SCAN_INCOMPLETE_PIECES,
						itemFileRescan.getSelection());
			}
		});
		itemFileRescan.setSelection(allScanSelected);
		itemFileRescan.setEnabled(fileRescan);

			// low resource recheck

		final MenuItem itemLowResourceRecheck = new MenuItem(menuFiles, SWT.PUSH);
		Messages.setLanguageText(itemLowResourceRecheck, "MyTorrentsView.menu.lowresourcerecheck");
		itemLowResourceRecheck.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager dm) {
				
				ManagerUtils.lowResourceRecheck( dm );
			}
		});
		
		itemLowResourceRecheck.setEnabled(lrrecheck);
		
			// revert

		final MenuItem itemRevertFiles = new MenuItem(menu, SWT.PUSH);
		itemRevertFiles.setText( MessageText.getString( "MyTorrentsView.menu.revertfiles") + "..." );
		itemRevertFiles.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager[] dms)
			{
				FilesViewMenuUtil.revertFiles( tv, dms );
			}
		});

		itemRevertFiles.setEnabled(hasRevertableFiles);
		
			// view links
		
		final MenuItem itemViewLinks = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemViewLinks, "menu.view.links");
		itemViewLinks.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void 
			run(
				DownloadManager[] dms)
			{
				ManagerUtils.viewLinks( dms );
			}
		});
		
			// clear links

		final MenuItem itemClearLinks = new MenuItem(menuFiles, SWT.PUSH);
		Messages.setLanguageText(itemClearLinks, "FilesView.menu.clear.links");
		itemClearLinks.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager dm)
			{
				if ( ManagerUtils.isStopped(dm) && dm.getDownloadState().getFileLinks().hasLinks()){

					DiskManagerFileInfoSet fis = dm.getDiskManagerFileInfoSet();

					TOTorrent torrent = dm.getTorrent();
					
					if ( torrent != null && !torrent.isSimpleTorrent()){

						DiskManagerFileInfo[] files = fis.getFiles();

						for ( DiskManagerFileInfo file_info: files ){

							File file_link 		= file_info.getFile( true );
							File file_nolink 	= file_info.getFile( false );

							if ( !file_nolink.getAbsolutePath().equals( file_link.getAbsolutePath())){

								file_info.setLink( null, true );
							}
						}
					}
				}
			}
		});

		itemClearLinks.setEnabled(hasClearableLinks);

		// allocate

		MenuItem itemFileAlloc = new MenuItem(menuFiles, SWT.PUSH);
		Messages.setLanguageText(itemFileAlloc,
				"label.allocate");
		itemFileAlloc.addListener(SWT.Selection, new ListenerDMTask(
				dms) {
			@Override
			public void run(DownloadManager dm) {
				
				dm.getDownloadState().setLongAttribute( DownloadManagerState.AT_FILE_ALLOC_STRATEGY, DownloadManagerState.FAS_ZERO_NEW_STOP );
				
				dm.getDownloadState().setFlag( DownloadManagerState.FLAG_DISABLE_STOP_AFTER_ALLOC, false );
				
				ManagerUtils.queue( dm, null );
			}
		});

		itemFileAlloc.setEnabled(allAllocatable);
		
			// clear allocation

		MenuItem itemFileClearAlloc = new MenuItem(menuFiles, SWT.PUSH);
		Messages.setLanguageText(itemFileClearAlloc,
				"MyTorrentsView.menu.clear_alloc_data");
		itemFileClearAlloc.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager dm) {
				dm.setDataAlreadyAllocated(false);
			}
		});

		itemFileClearAlloc.setEnabled(allStopped);

			// clear resume

		MenuItem itemFileClearResume = new MenuItem(menuFiles, SWT.PUSH);
		Messages.setLanguageText(itemFileClearResume,
				"MyTorrentsView.menu.clear_resume_data");
		itemFileClearResume.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager dm) {
				dm.getDownloadState().clearResumeData();
			}
		});
		itemFileClearResume.setEnabled(allStopped);

		// set resume complete

		MenuItem itemFileSetResumeComplete = new MenuItem(menuFiles, SWT.PUSH);
		Messages.setLanguageText(itemFileSetResumeComplete,
				"MyTorrentsView.menu.set.resume.complete");
		itemFileSetResumeComplete.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager dm) {
				TorrentUtils.setResumeDataCompletelyValid( dm.getDownloadState());
			}
		});
		itemFileSetResumeComplete.setEnabled(allStopped&&allResumeIncomplete);

		// restore resume
		
		final Menu restore_menu = new Menu( menuFiles.getShell(), SWT.DROP_DOWN);
		
		MenuItem itemRestoreResume = new MenuItem(menuFiles, SWT.CASCADE);
		Messages.setLanguageText(itemRestoreResume,	"MyTorrentsView.menu.restore.resume.data");
		
		itemRestoreResume.setMenu( restore_menu );
		
		boolean restoreEnabled = false;
		
		if ( dms.length==1 && allStopped ){
			DownloadManagerState dmState = dms[0].getDownloadState();
			
			List<DownloadManagerState.ResumeHistory> history = dmState.getResumeDataHistory();
			
			if ( !history.isEmpty()){
				restoreEnabled = true;
			
				for ( DownloadManagerState.ResumeHistory h: history ){
					MenuItem itemHistory = new MenuItem(restore_menu, SWT.PUSH);
					itemHistory.setText( new SimpleDateFormat().format( new Date(h.getDate())));
					
					itemHistory.addListener(SWT.Selection,(ev)->{
						dmState.restoreResumeData( h );;
					});
				}
			}
		}
		
		itemRestoreResume.setEnabled( restoreEnabled);
		
		// mask dl comp
				
		MenuItem itemMaskDLComp = new MenuItem(menuFiles, SWT.CHECK);
		
		if ( dms.length> 0 ){
			itemMaskDLComp.setSelection( allMaskDC );
		}
		
		Messages.setLanguageText(itemMaskDLComp,
				"ConfigView.label.hap");
		itemMaskDLComp.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager dm) {
				dm.getDownloadState().setOptionalBooleanAttribute( DownloadManagerState.AT_MASK_DL_COMP_OPTIONAL, itemMaskDLComp.getSelection());
			}
		});

		itemMaskDLComp.setEnabled( dms.length > 0 );
		
			// Advanced -> archive

		final List<Download>	ar_dms = new ArrayList<>();

		for ( DownloadManager dm: dms ){

			Download stub = PluginCoreUtils.wrap(dm);

			if ( !stub.canStubbify()){

				continue;
			}

			ar_dms.add( stub );
		}

		MenuItem itemArchive = new MenuItem(menuAdvanced, SWT.PUSH);
		Messages.setLanguageText(itemArchive, "MyTorrentsView.menu.archive");
		Utils.setMenuItemImage(itemArchive, "archive");
		itemArchive.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager[] todo) {
				ManagerUtils.moveToArchive( ar_dms, null );
			}
		});

		itemArchive.setEnabled(ar_dms.size() > 0);


		// Advanced - > Rename
		final MenuItem itemRename = new MenuItem(menuAdvanced, SWT.DROP_DOWN);
		Messages.setLanguageText(itemRename, "MyTorrentsView.menu.rename");
		itemRename.setEnabled(hasSelection);
		itemRename.addListener(SWT.Selection,
				event -> ManagerUtils.advancedRename(dms));

		// Find more like this

		if ( ManagerUtils.canFindMoreLikeThis()){
			final MenuItem itemFindMore = new MenuItem(menuAdvanced, SWT.PUSH);
			Messages.setLanguageText(itemFindMore,
					"MyTorrentsView.menu.findmorelikethis");
			itemFindMore.addListener(SWT.Selection, new ListenerDMTask(dms) {
				@Override
				public void run(DownloadManager[] dms) {
					ManagerUtils.findMoreLikeThis( dms[0], shell );
				}
			});
			itemFindMore.setSelection(isSingleSelection);
		}

		// === advanced > quick view

		final Menu quickViewMenu = new Menu(menuAdvanced.getShell(), SWT.DROP_DOWN);
		final MenuItem quickViewMenuItem = new MenuItem(menuAdvanced, SWT.CASCADE);
		Messages.setLanguageText(quickViewMenuItem,
				IMenuConstants.MENU_ID_QUICK_VIEW);
		quickViewMenuItem.setMenu(quickViewMenu);

		MenuBuildUtils.addMaintenanceListenerForMenu(quickViewMenu,
				new MenuBuildUtils.MenuBuilder() {
					@Override
					public void buildMenu(Menu menu, MenuEvent menuEvent) {
						DownloadManager dm = dms[0];

						DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();

						int added = 0;

						for (final DiskManagerFileInfo file : files) {

							if (Utils.isQuickViewSupported(file)) {

								final MenuItem addItem = new MenuItem(menu, SWT.CHECK);

								addItem.setSelection(Utils.isQuickViewActive(file));

								addItem.setText(file.getTorrentFile().getRelativePath());

								addItem.addListener(SWT.Selection, new Listener() {
									@Override
									public void handleEvent(Event arg) {
										Utils.setQuickViewActive(file, addItem.getSelection());
									}
								});

								added++;
							}
						}

						if (added == 0) {

							final MenuItem addItem = new MenuItem(menu, SWT.PUSH);

							addItem.setText(MessageText.getString("quick.view.no.files"));

							addItem.setEnabled(false);
						}
					}
				});

		quickViewMenuItem.setEnabled( isSingleSelection);

		// Alerts

		MenuFactory.addAlertsMenu(menuAdvanced, true, dms);

		// === advanced > export ===
		// =========================

		if (userMode > 0) {
			final MenuItem itemExport = new MenuItem(menuAdvanced, SWT.CASCADE);
			Messages.setLanguageText(itemExport, "MyTorrentsView.menu.exportmenu"); //$NON-NLS-1$
			Utils.setMenuItemImage(itemExport, "export");
			itemExport.setEnabled(hasSelection);

			final Menu menuExport = new Menu( menu_shell, SWT.DROP_DOWN);
			itemExport.setMenu(menuExport);

			// Advanced > Export > Export XML
			final MenuItem itemExportXML = new MenuItem(menuExport, SWT.PUSH);
			Messages.setLanguageText(itemExportXML, "MyTorrentsView.menu.export");
			itemExportXML.addListener(SWT.Selection, new ListenerDMTask(dms) {
				@Override
				public void run(DownloadManager[] dms) {
					DownloadManager dm = dms[0]; // First only.
					if (dm != null)
						new ExportTorrentWizard(itemExportXML.getDisplay(), dm);
				}
			});

			// Advanced > Export > Export Torrent
			final MenuItem itemExportTorrent = new MenuItem(menuExport, SWT.PUSH);
			Messages.setLanguageText(itemExportTorrent,
					"MyTorrentsView.menu.exporttorrent");
			itemExportTorrent.addListener(SWT.Selection, new ListenerDMTask(dms) {
				@Override
				public void run(DownloadManager[] dms) {
					exportTorrent(dms, shell);
				} // end run()
			}); // end DMTask

			// Advanced > Export > WebSeed URL
			final MenuItem itemWebSeed = new MenuItem(menuExport, SWT.PUSH);
			Messages.setLanguageText(itemWebSeed,
					"MyTorrentsView.menu.exporthttpseeds");
			itemWebSeed.addListener(SWT.Selection, new ListenerDMTask(dms) {
				@Override
				public void run(DownloadManager[] dms) {
					exportHTTPSeeds(dms);
				}
			});
		} // export menu

		// === advanced > options ===
		// ===========================

		if (userMode > 0) {
			final MenuItem itemExportXML = new MenuItem(menuAdvanced, SWT.PUSH);
			Messages.setLanguageText(itemExportXML, "label.options.and.info");
			itemExportXML.addListener(SWT.Selection, new ListenerDMTask(dms) {
				@Override
				public void run(DownloadManager[] dms) {
					UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
					if (uiFunctions != null) {
						uiFunctions.getMDI().showEntryByID(
								MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_OPTIONS, dms);
					}
				}
			});
		}
		// === advanced > peer sources ===
		// ===============================

		if (userMode > 0) {
			final MenuItem itemPeerSource = new MenuItem(menuAdvanced, SWT.CASCADE);
			Messages.setLanguageText(itemPeerSource, "MyTorrentsView.menu.peersource"); //$NON-NLS-1$

			final Menu menuPeerSource = new Menu(menu_shell, SWT.DROP_DOWN);
			itemPeerSource.setMenu(menuPeerSource);

			addPeerSourceSubMenu(dms, menuPeerSource);
		}

		// Sequential download
		
		{

			final MenuItem dl_seq_enable = new MenuItem(menuAdvanced, SWT.CHECK);
			Messages.setLanguageText(dl_seq_enable, "menu.sequential.download");

			dl_seq_enable.addListener(SWT.Selection, new ListenerDMTask(dms) {
				@Override
				public void run(DownloadManager dm) {
					dm.getDownloadState().setFlag(
							DownloadManagerState.FLAG_SEQUENTIAL_DOWNLOAD,
							dl_seq_enable.getSelection());
				}
			});

			boolean allSeq		= true;
			boolean AllNonSeq 	= true;

			for (int j = 0; j < dms.length; j++) {
				DownloadManager dm = dms[j];

				boolean seq = dm.getDownloadState().getFlag(
						DownloadManagerState.FLAG_SEQUENTIAL_DOWNLOAD);

				if (seq) {
					AllNonSeq = false;
				} else {
					allSeq = false;
				}
			}

			boolean bChecked;

			if (allSeq) {
				bChecked = true;
			} else if (AllNonSeq) {
				bChecked = false;
			} else {
				bChecked = false;
			}

			dl_seq_enable.setSelection(bChecked);
		}
		
		// IP Filter Enable
		if (userMode > 0) {

			final MenuItem ipf_enable = new MenuItem(menuAdvanced, SWT.CHECK);
			Messages.setLanguageText(ipf_enable, "MyTorrentsView.menu.ipf_enable");

			ipf_enable.addListener(SWT.Selection, new ListenerDMTask(dms) {
				@Override
				public void run(DownloadManager dm) {
					dm.getDownloadState().setFlag(
							DownloadManagerState.FLAG_DISABLE_IP_FILTER,
							!ipf_enable.getSelection());
				}
			});

			boolean bEnabled = IpFilterManagerFactory.getSingleton().getIPFilter().isEnabled();

			if (bEnabled) {
				boolean allChecked = true;
				boolean allUnchecked = true;

				for (int j = 0; j < dms.length; j++) {
					DownloadManager dm = (DownloadManager) dms[j];

					boolean b = dm.getDownloadState().getFlag(
							DownloadManagerState.FLAG_DISABLE_IP_FILTER);

					if (b) {
						allUnchecked = false;
					} else {
						allChecked = false;
					}
				}

				boolean bChecked;

				if (allUnchecked) {
					bChecked = true;
				} else if (allChecked) {
					bChecked = false;
				} else {
					bChecked = false;
				}

				ipf_enable.setSelection(bChecked);
			}

			ipf_enable.setEnabled(bEnabled);
		}

		// === advanced > networks ===
		// ===========================

		if (userMode > 1) {
			final MenuItem itemNetworks = new MenuItem(menuAdvanced, SWT.CASCADE);
			Messages.setLanguageText(itemNetworks, "MyTorrentsView.menu.networks"); //$NON-NLS-1$

			final Menu menuNetworks = new Menu(menu_shell, SWT.DROP_DOWN);
			itemNetworks.setMenu(menuNetworks);

			addNetworksSubMenu(dms, menuNetworks);
		}

		// superseed
		if (userMode > 1 && isSeedingView) {

			final MenuItem itemSuperSeed = new MenuItem(menuAdvanced, SWT.CHECK);

			Messages.setLanguageText(itemSuperSeed, "ManagerItem.superseeding");

			boolean enabled = canSetSuperSeed && (superSeedAllNo || superSeedAllYes);

			itemSuperSeed.setEnabled(enabled);

			final boolean selected = superSeedAllNo;

			if (enabled) {

				itemSuperSeed.setSelection(selected);

				itemSuperSeed.addListener(SWT.Selection, new ListenerDMTask(dms) {
					@Override
					public void run(DownloadManager dm) {
						PEPeerManager pm = dm.getPeerManager();

						if (pm != null) {

							if (pm.isSuperSeedMode() == selected
									&& pm.canToggleSuperSeedMode()) {

								pm.setSuperSeedMode(!selected);
							}
						}
					}
				});
			}
		}

		// Advanced > Pause For..
		if (userMode > 0) {

			boolean can_pause_for = false;

			for (int i = 0; i < dms.length; i++) {

				DownloadManager dm = dms[i];

				if ( dm.isPaused() || ManagerUtils.isPauseable(dm)) {

					can_pause_for = true;

					break;
				}
			}

			final MenuItem itemPauseFor = new MenuItem(menuAdvanced, SWT.PUSH);

			itemPauseFor.setEnabled(can_pause_for);

			Messages.setLanguageText(itemPauseFor,
					"MainWindow.menu.transfers.pausetransfersfor");

			itemPauseFor.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					pauseDownloadsFor(dms);
				}
			});
		}

		// Advanced > Reposition
		final MenuItem itemPositionManual = new MenuItem(menuAdvanced, SWT.PUSH);
		Messages.setLanguageText(itemPositionManual,
				"MyTorrentsView.menu.reposition.manual");
		Utils.setMenuItemImage(itemPositionManual, "move");
		itemPositionManual.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				repositionManual(tv, dms, shell, isSeedingView);
			}
		});

		// back to main menu
		if (userMode > 0 && isTrackerOn) {
			// Host
			final MenuItem itemHost = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemHost, "MyTorrentsView.menu.host");
			Utils.setMenuItemImage(itemHost, "host");
			itemHost.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					hostTorrents(dms);
				}
			});

			// Publish
			final MenuItem itemPublish = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemPublish, "MyTorrentsView.menu.publish");
			Utils.setMenuItemImage(itemPublish, "publish");
			itemPublish.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					publishTorrents(dms);
				}
			});

			itemHost.setEnabled(hasSelection);
			itemPublish.setEnabled(hasSelection);
		}

		/*  //TODO ensure that all limits combined don't go under the min 5kbs ?
		 //Disable at the end of the list, thus the first item of the array is instanciated last.
		 itemsSpeed[0] = new MenuItem(menuSpeed,SWT.PUSH);
		 Messages.setLanguageText(itemsSpeed[0],"MyTorrentsView.menu.setSpeed.disable");
		 itemsSpeed[0].setData("maxul", new Integer(-1));
		 itemsSpeed[0].addListener(SWT.Selection,itemsSpeedListener);
		 */

		// Category

		Menu menuCategory = new Menu(menu_shell, SWT.DROP_DOWN);
		final MenuItem itemCategory = new MenuItem(menu, SWT.CASCADE);
		Messages.setLanguageText(itemCategory, "MyTorrentsView.menu.setCategory"); //$NON-NLS-1$
		//itemCategory.setImage(ImageRepository.getImage("speed"));
		itemCategory.setMenu(menuCategory);
		itemCategory.setEnabled(hasSelection);

		addCategorySubMenu(dms, menuCategory);

		// Tags

		Menu menuTags = new Menu(menu_shell, SWT.DROP_DOWN);
		final MenuItem itemTags = new MenuItem(menu, SWT.CASCADE);
		Messages.setLanguageText(itemTags, "label.tags");
		itemTags.setMenu(menuTags);
		itemTags.setEnabled(hasSelection);

		TagUIUtils.addLibraryViewTagsSubMenu(dms, menuTags);

		// personal share

		if (isSeedingView) {
			final MenuItem itemPersonalShare = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemPersonalShare,
					"MyTorrentsView.menu.create_personal_share");
			itemPersonalShare.addListener(SWT.Selection, new ListenerDMTask(dms, false) {
				@Override
				public void run(DownloadManager dm) {
					File file = dm.getSaveLocation();

					Map<String, String> properties = new HashMap<>();

					if ( Utils.setPeronalShare( properties )){

						if (file.isFile()) {
	
							ShareUtils.shareFile(file.getAbsolutePath(), properties);
	
						} else if (file.isDirectory()) {
	
							ShareUtils.shareDir(file.getAbsolutePath(), properties);
						}
					}
				}
			});


			boolean	can_share_pers = dms.length > 0;

			for ( DownloadManager dm: dms ){

				File file = dm.getSaveLocation();

				if ( !file.exists()){
					
					can_share_pers = false;
					
					break;
				}
			}

			itemPersonalShare.setEnabled( can_share_pers );
		}

		// ---
		new MenuItem(menu, SWT.SEPARATOR);

		// Queue
		final MenuItem itemQueue = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemQueue, "MyTorrentsView.menu.queue"); //$NON-NLS-1$
		Utils.setMenuItemImage(itemQueue, "start");
		itemQueue.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				Utils.getOffOfSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						queueDataSources(dms, false);
					}
				});
			}
		});
		itemQueue.setEnabled(start);

		// Force Start
		if ( isForceStartVisible( dms )){
			
			final MenuItem itemForceStart = new MenuItem(menu, SWT.CHECK);
			Messages.setLanguageText(itemForceStart, "MyTorrentsView.menu.forceStart");
			Utils.setMenuItemImage(itemForceStart, "forcestart");
			itemForceStart.addListener(SWT.Selection, new ListenerDMTask(dms) {
				@Override
				public void run(DownloadManager dm) {
					if (ManagerUtils.isForceStartable(dm, true)) {
						dm.setForceStart(itemForceStart.getSelection());
					}
				}
			});
			itemForceStart.setSelection(forceStart);
			itemForceStart.setEnabled(forceStartEnabled);
		}

		// Pause
		if (userMode > 0) {
			final MenuItem itemPause = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemPause, "v3.MainWindow.button.pause");
			Utils.setMenuItemImage(itemPause, "pause");
			itemPause.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					Utils.getOffOfSWTThread(new AERunnable() {
						@Override
						public void runSupport() {
							pauseDataSources(dms);
						}
					});
				}
			});
			itemPause.setEnabled(pause);
		}

		// Stop
		final MenuItem itemStop = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemStop, "MyTorrentsView.menu.stop");
		Utils.setMenuItemImage(itemStop, "stop");
		itemStop.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				Utils.getOffOfSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						stopDataSources(dms);
					}
				});
			}
		});
		itemStop.setEnabled(stop);

		// Force Recheck
		final MenuItem itemRecheck = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemRecheck, "MyTorrentsView.menu.recheck");
		Utils.setMenuItemImage(itemRecheck, "recheck");
		itemRecheck.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager dm) {
				if (dm.canForceRecheck()) {
					dm.forceRecheck();
				}
			}
		});
		itemRecheck.setEnabled(recheck);

		// Delete
		final MenuItem itemRemove = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemRemove, "menu.delete.options");
		Utils.setMenuItemImage(itemRemove, "delete");
		itemRemove.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				removeDownloads(dms, null, true);
			}
		});
		itemRemove.setEnabled(hasSelection);

	}

	protected static void addNetworksSubMenu(DownloadManager[] dms,
			Menu menuNetworks) {
		for (int i = 0; i < AENetworkClassifier.AT_NETWORKS.length; i++) {
			final String nn = AENetworkClassifier.AT_NETWORKS[i];
			String msg_text = "ConfigView.section.connection.networks." + nn;
			final MenuItem itemNetwork = new MenuItem(menuNetworks, SWT.CHECK);
			itemNetwork.setData("network", nn);
			Messages.setLanguageText(itemNetwork, msg_text); //$NON-NLS-1$
			itemNetwork.addListener(SWT.Selection, new ListenerDMTask(dms) {
				@Override
				public void run(DownloadManager dm) {
					dm.getDownloadState().setNetworkEnabled(nn,
							itemNetwork.getSelection());
				}
			});
			boolean bChecked = dms.length > 0;
			if (bChecked) {
				// turn on check if just one dm is not enabled
				for (int j = 0; j < dms.length; j++) {
					DownloadManager dm = dms[j];

					if (!dm.getDownloadState().isNetworkEnabled(nn)) {
						bChecked = false;
						break;
					}
				}
			}

			itemNetwork.setSelection(bChecked);
		}
	}

	protected static void addPeerSourceSubMenu(DownloadManager[] dms,
			Menu menuPeerSource) {
		boolean hasSelection = dms.length > 0;
		for (int i = 0; i < PEPeerSource.PS_SOURCES.length; i++) {

			final String p = PEPeerSource.PS_SOURCES[i];
			String msg_text = "ConfigView.section.connection.peersource." + p;
			final MenuItem itemPS = new MenuItem(menuPeerSource, SWT.CHECK);
			itemPS.setData("peerSource", p);
			Messages.setLanguageText(itemPS, msg_text); //$NON-NLS-1$
			itemPS.addListener(SWT.Selection, new ListenerDMTask(dms) {
				@Override
				public void run(DownloadManager dm) {
					dm.getDownloadState().setPeerSourceEnabled(p, itemPS.getSelection());
				}
			});
			itemPS.setSelection(true);

			boolean bChecked = hasSelection;
			boolean bEnabled = !hasSelection;
			if (bChecked) {
				bEnabled = true;

				// turn on check if just one dm is not enabled
				for (int j = 0; j < dms.length; j++) {
					DownloadManager dm = (DownloadManager) dms[j];

					if (!dm.getDownloadState().isPeerSourceEnabled(p)) {
						bChecked = false;
					}
					if (!dm.getDownloadState().isPeerSourcePermitted(p)) {
						bEnabled = false;
					}
				}
			}

			itemPS.setSelection(bChecked);
			itemPS.setEnabled(bEnabled);
		}
	}

	protected static void exportHTTPSeeds(DownloadManager[] dms) {
		final String NL = "\r\n";
		String data = "";

		boolean http_enable = COConfigurationManager.getBooleanParameter("HTTP.Data.Listen.Port.Enable");

		String port;

		if (http_enable) {

			int p = COConfigurationManager.getIntParameter("HTTP.Data.Listen.Port");
			int o = COConfigurationManager.getIntParameter("HTTP.Data.Listen.Port.Override");

			if (o == 0) {

				port = String.valueOf(p);

			} else {

				port = String.valueOf(o);
			}
		} else {

			data = "You need to enable the HTTP port or modify the URL(s) appropriately"
					+ NL + NL;

			port = "<port>";
		}

		String ip = COConfigurationManager.getStringParameter("Tracker IP",
				"");

		if (ip.length() == 0) {

			data += "You might need to modify the host address in the URL(s)"
					+ NL + NL;

			try {

				InetAddress ia = NetworkAdmin.getSingleton().getDefaultPublicAddress();

				if (ia != null) {

					ip = IPToHostNameResolver.syncResolve(ia.getHostAddress(),
							10000);
				}
			} catch (Throwable e) {

			}

			if (ip.length() == 0) {

				ip = "<host>";
			}
		}

		String base = "http://" + UrlUtils.convertIPV6Host(ip) + ":" + port
				+ "/";

		for (int i = 0; i < dms.length; i++) {

			DownloadManager dm = dms[i];

			if (dm == null) {
				continue;
			}

			TOTorrent torrent = dm.getTorrent();

			if (torrent == null) {

				continue;
			}

			data += base + "webseed" + NL;

			try {
				data += base
						+ "files/"
						+ URLEncoder.encode(new String(torrent.getHash(),
								"ISO-8859-1"), "ISO-8859-1") + "/" + NL + NL;

			} catch (Throwable e) {

			}
		}

		if (data.length() > 0) {
			ClipboardCopy.copyToClipBoard(data);
		}
	}

	protected static void exportTorrent(DownloadManager[] dms, Shell parentShell) {
		// FileDialog for single download
		// DirectoryDialog for multiple.
		File[] destinations = new File[dms.length];
		if (dms.length == 1) {
			FileDialog fd = new FileDialog(parentShell, SWT.SAVE);
			fd.setFileName(dms[0].getTorrentFileName());
			String path = fd.open();
			if (path == null) {
				return;
			}
			destinations[0] = new File(path);
		} else {
			DirectoryDialog dd = new DirectoryDialog(parentShell, SWT.SAVE);
			String path = dd.open();
			if (path == null) {
				return;
			}
			for (int i = 0; i < dms.length; i++) {
				destinations[i] = new File(path,
						new File(dms[i].getTorrentFileName()).getName());
			}
		}

		int i = 0;
		try {
			boolean applyToAll = false;
			int		applyToAllDecision = SWT.NO;
			
			for (; i < dms.length; i++) {
				File target = destinations[i];
				if (target.exists()) {
					/*
					MessageBox mb = new MessageBox(parentShell, SWT.ICON_QUESTION
							| SWT.YES | SWT.NO);
					mb.setText(MessageText.getString("exportTorrentWizard.process.outputfileexists.title"));
					mb.setMessage(MessageText.getString("exportTorrentWizard.process.outputfileexists.message")
							+ "\n" + destinations[i].getName());

					int result = mb.open();
					if (result == SWT.NO) {
						return;
					}
					*/
					
					int result;
					
					if ( applyToAll ){
						
						result = applyToAllDecision;
						
					}else{
						MessageBoxShell mb = new MessageBoxShell( SWT.YES | SWT.NO,
								MessageText.getString("exportTorrentWizard.process.outputfileexists.title"),
								dms[i].getDisplayName() + "\n\n" + 
								MessageText.getString("exportTorrentWizard.process.outputfileexists.message" ));
						
						if ( dms.length > 1 ){
							
							mb.setApplyToAllEnabled();
						}
						
						mb.open( null );
						
						result = mb.waitUntilClosed();
						
						applyToAll = mb.getApplyToAll();
							
						if ( applyToAll ){
						
							applyToAllDecision = result;
						}
					}
					
					if ( result == SWT.NO ){
						
						continue;
					}
					
					if (!target.delete()) {
						throw (new Exception("Failed to delete file"));
					}
				} // end deal with clashing torrent

				// first copy the torrent - DON'T use "writeTorrent" as this amends the
				// "filename" field in the torrent
				TorrentUtils.copyToFile(dms[i].getDownloadState().getTorrent(), target);

				// now remove the non-standard entries
				TOTorrent dest = TOTorrentFactory.deserialiseFromBEncodedFile(target);
				dest.removeAdditionalProperties();
				dest.serialiseToBEncodedFile(target);
			} // end for
		} // end try
		catch (Throwable e) {
			Logger.log(new LogAlert(dms[i], LogAlert.UNREPEATABLE,
					"Torrent export failed", e));
		}
	}

	public static void 
	exportTorrent(
		String		torrent_name,
		TOTorrent 	torrent, 
		Shell 		parentShell) 
	{
		FileDialog fd = new FileDialog(parentShell, SWT.SAVE);
		
		fd.setFileName( torrent_name );
		
		String path = fd.open();
		
		if ( path == null ){
			
			return;
		}
		
		File target = new File(path);
		
		try{

			if ( target.exists()){
				
				MessageBoxShell mb = 
					new MessageBoxShell( 
						SWT.YES | SWT.NO,
						MessageText.getString("exportTorrentWizard.process.outputfileexists.title"),
						torrent_name + "\n\n" + 
						MessageText.getString("exportTorrentWizard.process.outputfileexists.message" ));
	
				mb.open( null );
				
				int result = mb.waitUntilClosed();
	
				if ( result == SWT.NO ){
					
					return;
				}
				
				if (!target.delete()) {
					
					throw (new Exception("Failed to delete file"));
				}
			}

			Map map = torrent.serialiseToMap();
			
			TOTorrent dest = TOTorrentFactory.deserialiseFromMap(map);
			
			dest.removeAdditionalProperties();
			
			dest.serialiseToBEncodedFile(target);
			
		}catch (Throwable e) {
			Logger.log(new LogAlert(torrent_name, LogAlert.UNREPEATABLE,
					"Torrent export failed", e));
		}
	}
	
	public static void 
	exportTorrents(
		String[]		torrent_names,
		TOTorrent[] 	torrents, 
		Shell 			parentShell) 
	{
		DirectoryDialog fd = new DirectoryDialog( parentShell, SWT.SAVE );
				
		String path = fd.open();
		
		if ( path == null ){
			
			return;
		}
		
		File dir = new File(path);
		
		for ( int i=0;i<torrents.length;i++){
			
			String torrent_name = torrent_names[i];
			
			File target = new File( dir, torrent_name );
			
			try{
	
				if ( target.exists()){
					
					MessageBoxShell mb = 
						new MessageBoxShell( 
							SWT.YES | SWT.NO,
							MessageText.getString("exportTorrentWizard.process.outputfileexists.title"),
							torrent_name + "\n\n" + 
							MessageText.getString("exportTorrentWizard.process.outputfileexists.message" ));
		
					mb.open( null );
					
					int result = mb.waitUntilClosed();
		
					if ( result == SWT.NO ){
						
						continue;
					}
					
					if (!target.delete()) {
						
						throw (new Exception("Failed to delete file"));
					}
				}
	
				Map map = torrents[i].serialiseToMap();
				
				TOTorrent dest = TOTorrentFactory.deserialiseFromMap(map);
				
				dest.removeAdditionalProperties();
				
				dest.serialiseToBEncodedFile(target);
				
			}catch (Throwable e) {
				Logger.log(new LogAlert(torrent_name, LogAlert.UNREPEATABLE,
						"Torrent export failed", e));
			}
		}
	}
	
	protected static void pauseDownloadsFor(DownloadManager[] dms) {

		final List<DownloadManager> dms_to_pause = new ArrayList<>();

		for (int i = 0; i < dms.length; i++) {

			DownloadManager dm = dms[i];

			if ( dm.isPaused() || ManagerUtils.isPauseable(dm)) {

				dms_to_pause.add(dm);
			}
		}

		if (dms_to_pause.size() == 0) {

			return;
		}

		String text = MessageText.getString("dialog.pause.for.period.text");

		SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
				"dialog.pause.for.period.title", "!" + text + "!");

		int def = COConfigurationManager.getIntParameter(
				"pause.for.period.default", 10);

		entryWindow.setPreenteredText(String.valueOf(def), false);

		entryWindow.prompt(new UIInputReceiverListener() {
			@Override
			public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
				if (!entryWindow.hasSubmittedInput()) {

					return;
				}

				String sReturn = entryWindow.getSubmittedInput();

				if (sReturn == null) {

					return;
				}

				int mins = -1;

				try {

					mins = Integer.valueOf(sReturn).intValue();

				} catch (NumberFormatException er) {
					// Ignore
				}

				if (mins <= 0) {

					MessageBox mb = new MessageBox(Utils.findAnyShell(), SWT.ICON_ERROR
							| SWT.OK);

					mb.setText(MessageText.getString("MyTorrentsView.dialog.NumberError.title"));
					mb.setMessage(MessageText.getString("MyTorrentsView.dialog.NumberError.text"));

					mb.open();

					return;
				}

				COConfigurationManager.setParameter("pause.for.period.default",
						mins);

				ManagerUtils.asyncPauseForPeriod(dms_to_pause, mins * 60);
			}
		});
	}

	protected static void addSpeedLimitsMenu(DownloadManager[] dms, Menu menu) {

		Core core = CoreFactory.getSingleton();

		Shell menu_shell = menu.getShell();
		
		final SpeedLimitHandler slh = SpeedLimitHandler.getSingleton(core);

		boolean all_have_limit = true;

		Set<String> common_profiles = new HashSet<>();

		final List<byte[]> dm_hashes = new ArrayList<>();

		for (int i = 0; i < dms.length; i++) {
			DownloadManager dm = dms[i];

			int maxul = dm.getStats().getUploadRateLimitBytesPerSecond();
			int maxdl = dm.getStats().getDownloadRateLimitBytesPerSecond();

			if (maxul == 0 && maxdl == 0) {

				all_have_limit = false;
			}

			TOTorrent t = dm.getTorrent();

			if (t == null) {

				common_profiles.clear();

			} else {

				try {
					byte[] hash = t.getHash();

					dm_hashes.add(hash);

					List<String> profs = slh.getProfilesForDownload(hash);

					if (i == 0) {

						common_profiles.addAll(profs);

					} else {

						common_profiles.retainAll(profs);
					}
				} catch (TOTorrentException e) {

					Debug.out(e);

					common_profiles.clear();
				}
			}
		}

		java.util.List<String> profiles = slh.getProfileNames();

		// add to profile

		final Menu add_to_prof_menu = new Menu(menu_shell,
				SWT.DROP_DOWN);
		MenuItem add_to_prof_item = new MenuItem(menu, SWT.CASCADE);
		add_to_prof_item.setMenu(add_to_prof_menu);

		Messages.setLanguageText(add_to_prof_item,
				"MyTorrentsView.menu.sl_add_to_prof");

		if (!all_have_limit) {

			add_to_prof_item.setEnabled(false);

		} else {

			for (final String p : profiles) {

				MenuItem addItem = new MenuItem(add_to_prof_menu, SWT.PUSH);
				addItem.setText(p);

				addItem.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event arg0) {
						slh.addDownloadsToProfile(p, dm_hashes);

						Utils.showText("MainWindow.menu.speed_limits.info.title",
								MessageText.getString("MainWindow.menu.speed_limits.info.prof",
										new String[] {
											p
										}), slh.getProfile(p));
					}
				});
			}
		}

		// remove from profile

		final Menu remove_from_prof_menu = new Menu(menu_shell,
				SWT.DROP_DOWN);
		MenuItem remove_from_prof_item = new MenuItem(menu, SWT.CASCADE);
		remove_from_prof_item.setMenu(remove_from_prof_menu);

		Messages.setLanguageText(remove_from_prof_item,
				"MyTorrentsView.menu.sl_remove_from_prof");

		if (common_profiles.isEmpty()) {

			remove_from_prof_item.setEnabled(false);

		} else {

			for (final String p : common_profiles) {

				MenuItem addItem = new MenuItem(remove_from_prof_menu, SWT.PUSH);
				addItem.setText(p);

				addItem.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event arg0) {
						slh.removeDownloadsFromProfile(p, dm_hashes);

						Utils.showText("MainWindow.menu.speed_limits.info.title",
								MessageText.getString("MainWindow.menu.speed_limits.info.prof",
										new String[] {
											p
										}), slh.getProfile(p));
					}
				});
			}
		}
	}

	protected static void addTrackerTorrentMenu(final Menu menuTracker,
			final DownloadManager[] dms, boolean changeUrl, boolean manualUpdate,
			boolean allStopped, final boolean use_open_containing_folder, boolean canMove) {
		
		Shell shell = Utils.findAnyShell();
		
		boolean hasSelection = dms.length > 0;

		final MenuItem itemChangeTracker = new MenuItem(menuTracker, SWT.PUSH);
		Messages.setLanguageText(itemChangeTracker,
				"MyTorrentsView.menu.changeTracker");
		Utils.setMenuItemImage(itemChangeTracker, "add_tracker");
		itemChangeTracker.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager[] dms) {
				if (dms.length > 0) {
					new TrackerChangerWindow(dms);
				}
			}
		});
		itemChangeTracker.setEnabled(changeUrl);

			// edit tracker URLs

		final MenuItem itemEditTracker = new MenuItem(menuTracker, SWT.PUSH);
		Messages.setLanguageText(itemEditTracker, "MyTorrentsView.menu.editTracker");
		Utils.setMenuItemImage(itemEditTracker, "edit_trackers");
		itemEditTracker.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(final DownloadManager[] dms) {
				Map<String, List<DownloadManager>> same_map = new HashMap<>();

				for (DownloadManager dm : dms) {

					TOTorrent torrent = dm.getTorrent();

					if (torrent == null) {

						continue;
					}

					List<List<String>> group = TorrentUtils.announceGroupsToList(torrent);

					String str = "";

					for (List<String> l : group) {
						str += "[[";

						for (String s : l) {
							str += s + ", ";
						}
					}

					List<DownloadManager> dl = same_map.get(str);

					if (dl == null) {

						dl = new ArrayList<>();

						same_map.put(str, dl);
					}

					dl.add(dm);
				}

				for (final List<DownloadManager> set : same_map.values()) {

					TOTorrent torrent = set.get(0).getTorrent();

					List<List<String>> group = TorrentUtils.announceGroupsToList(torrent);

					new MultiTrackerEditor(null, null, group,
							new TrackerEditorListener() {
								@Override
								public void trackersChanged(String str, String str2,
								                            List<List<String>> group) {
									for (DownloadManager dm : set) {

										TOTorrent torrent = dm.getTorrent();

										TorrentUtils.listToAnnounceGroups(group, torrent);

										try {
											TorrentUtils.writeToFile(torrent);

										} catch (Throwable e) {

											Debug.printStackTrace(e);
										}

										if (dm.getTrackerClient() != null) {

											dm.getTrackerClient().resetTrackerUrl(true);
										}
									}
								}
							}, true, true);
				}
			}
		});

		itemEditTracker.setEnabled(hasSelection);

			// edit tracker URLs together

		final MenuItem itemEditTrackerMerged = new MenuItem(menuTracker, SWT.PUSH);
		Messages.setLanguageText(itemEditTrackerMerged, "MyTorrentsView.menu.editTrackerMerge");

		itemEditTrackerMerged.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(final DownloadManager[] dms) {

				final List<List<String>>	merged_trackers = new ArrayList<>();

				Set<String>	added = new HashSet<>();

				for (DownloadManager dm : dms) {

					TOTorrent torrent = dm.getTorrent();

					if (torrent == null) {

						continue;
					}

					List<List<String>> group = TorrentUtils.announceGroupsToList(torrent);

					for ( List<String> set: group ){

						List<String>	rem = new ArrayList<>();

						for ( String url_str: set ){

							try{
								URL url = new URL( url_str );

								if ( TorrentUtils.isDecentralised( url )){

									continue;
								}

								if ( !added.contains( url_str )){

									added.add( url_str );

									rem.add( url_str );
								}
							}catch( Throwable e ){

							}
						}

						if ( rem.size() > 0 ){

							merged_trackers.add( rem );
						}
					}
				}

				new MultiTrackerEditor(null, null, merged_trackers,
					new TrackerEditorListener() {
						@Override
						public void trackersChanged(String str, String str2,
						                            List<List<String>> group) {
							for (DownloadManager dm : dms) {

								TOTorrent torrent = dm.getTorrent();

								TorrentUtils.listToAnnounceGroups(group, torrent);

								try {
									TorrentUtils.writeToFile(torrent);

								} catch (Throwable e) {

									Debug.printStackTrace(e);
								}

								if (dm.getTrackerClient() != null) {

									dm.getTrackerClient().resetTrackerUrl(true);
								}
							}
						}
					}, true, true );
			}
		});

		itemEditTrackerMerged.setEnabled(dms.length > 1);

		// edit webseeds

		final MenuItem itemEditWebSeeds = new MenuItem(menuTracker, SWT.PUSH);
		Messages.setLanguageText(itemEditWebSeeds,
				"MyTorrentsView.menu.editWebSeeds");
		itemEditWebSeeds.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(final DownloadManager[] dms) {
				final TOTorrent torrent = dms[0].getTorrent();

				if (torrent == null) {

					return;
				}

				List getright = getURLList(torrent, "url-list");
				List webseeds = getURLList(torrent, "httpseeds");

				Map ws = new HashMap();

				ws.put("getright", getright);
				ws.put("webseeds", webseeds);

				ws = BDecoder.decodeStrings(ws);

				new WebSeedsEditor(null, ws, new WebSeedsEditorListener() {
					@Override
					public void webSeedsChanged(String oldName, String newName, Map ws) {
						try {
							// String -> byte[]

							ws = BDecoder.decode(BEncoder.encode(ws));

							List getright = (List) ws.get("getright");

							if (getright == null || getright.size() == 0) {

								torrent.removeAdditionalProperty("url-list");

							} else {

								torrent.setAdditionalListProperty("url-list", getright);
							}

							List webseeds = (List) ws.get("webseeds");

							if (webseeds == null || webseeds.size() == 0) {

								torrent.removeAdditionalProperty("httpseeds");

							} else {

								torrent.setAdditionalListProperty("httpseeds", webseeds);
							}

							PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByClass(
									ExternalSeedPlugin.class);

							if (pi != null) {

								ExternalSeedPlugin ext_seed_plugin = (ExternalSeedPlugin) pi.getPlugin();

								ext_seed_plugin.downloadChanged(PluginCoreUtils.wrap(dms[0]));
							}

						} catch (Throwable e) {

							Debug.printStackTrace(e);
						}
					}
				}, true);

			}

			protected List getURLList(TOTorrent torrent, String key) {
				Object obj = torrent.getAdditionalProperty(key);

				if (obj instanceof byte[]) {

					List l = new ArrayList();

					l.add(obj);

					return (l);

				} else if (obj instanceof List) {

					return ((List) obj);

				} else {

					return (new ArrayList());
				}
			}
		});

		itemEditWebSeeds.setEnabled(dms.length == 1);

		// manual update

		final MenuItem itemManualUpdate = new MenuItem(menuTracker, SWT.PUSH);
		Messages.setLanguageText(itemManualUpdate,
				"GeneralView.label.trackerurlupdate"); //$NON-NLS-1$
		//itemManualUpdate.setImage(ImageRepository.getImage("edit_trackers"));
		itemManualUpdate.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager dm) {
				dm.requestTrackerAnnounce(false);
			}
		});
		itemManualUpdate.setEnabled(manualUpdate);

		boolean scrape_enabled = COConfigurationManager.getBooleanParameter("Tracker Client Scrape Enable");

		boolean scrape_stopped = COConfigurationManager.getBooleanParameter("Tracker Client Scrape Stopped Enable");

		boolean manualScrape;
		
		if ( !scrape_enabled ){
			
			manualScrape = false;

		}else if ( !scrape_stopped ){
			
			manualScrape = false;
			
		}else{
			
			manualScrape = allStopped;
		}

		final MenuItem itemManualScrape = new MenuItem(menuTracker, SWT.PUSH);
		Messages.setLanguageText(itemManualScrape,
				"GeneralView.label.trackerscrapeupdate");
		
		itemManualScrape.addListener(SWT.Selection, new ListenerDMTask(dms, true, true) {
			@Override
			public void run(DownloadManager dm) {
				dm.requestTrackerScrape(true);
			}
		});
		itemManualScrape.setEnabled(manualScrape);

			// enable hybrid v2 swarm, create hybrid/v2
		
		List<DownloadManager>	can_v2_from_hybrid 	= new ArrayList<>();
		List<DownloadManager>	can_v1_from_hybrid 	= new ArrayList<>();
		List<DownloadManager>	can_create_from_v1 	= new ArrayList<>();
		
		for ( DownloadManager dm: dms ){
			
			TOTorrent torrent = dm.getTorrent();
			
			if ( torrent != null ){
				
				int tt = torrent.getTorrentType();
			
				if ( tt == TOTorrent.TT_V1 && !torrent.getPrivate()){
				
					boolean	all_complete = true;
					
					for ( DiskManagerFileInfo file: dm.getDiskManagerFileInfoSet().getFiles()){
					
						if ( file.isSkipped() || file.getDownloaded() != file.getLength()){
							
							all_complete = false;
							
							break;
						}
												
						if ( file.getFile(false ).length() != file.getTorrentFile().getLength()){
							
							all_complete = false;
							
							break;
						}
					}
					
					if ( all_complete ){
						
						can_create_from_v1.add( dm );	
					}
				}else if ( tt == TOTorrent.TT_V1_V2 ){
					
					int ett = torrent.getEffectiveTorrentType();
					
					try{
						byte[] truncated_v_other_hash = torrent.getTruncatedHash( ett==TOTorrent.TT_V1?TOTorrent.TT_V2:TOTorrent.TT_V1 );
											
						if ( dm.getGlobalManager().getDownloadManager( new HashWrapper( truncated_v_other_hash )) == null ){
							
							boolean compatible = true;
							
							DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
							
							for ( DiskManagerFileInfo dm_file: files ){
								
								if ( dm_file.getTorrentFile().isPadFile()){
									
								}else if ( dm_file.getStorageType() != DiskManagerFileInfo.ST_LINEAR || dm_file.isLinked()){
									
									compatible = false;
									
									break;
								}
							}
							
							if ( compatible ){
							
								if ( ett==TOTorrent.TT_V1 ){
								
									can_v2_from_hybrid.add( dm );
									
								}else{
									
									can_v1_from_hybrid.add( dm );
								}
							}
						}
					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}
			}
		}
				
		for ( int i=0;i<2;i++){
			
			List<DownloadManager>	canHybrid;
			int						canHybridTo;
			
			if ( i ==0 ){
				canHybrid		= can_v1_from_hybrid;
				canHybridTo		= TOTorrent.TT_V1;
			}else{
				canHybrid		= can_v2_from_hybrid;
				canHybridTo		= TOTorrent.TT_V2;
			}
			
			if ( canHybrid.isEmpty()){
				
				continue;
			}
			
			MenuItem itemHybridVn = new MenuItem(menuTracker, SWT.PUSH);
			Messages.setLanguageText( itemHybridVn,	i==0?"GeneralView.label.run.hybrid.v1":"GeneralView.label.run.hybrid.v2");

			itemHybridVn.addListener(SWT.Selection, new ListenerDMTask(canHybrid.toArray(new DownloadManager[0]), true, true) {
				@Override
				public void run(DownloadManager old_manager ) {
					
					TOTorrent torrent = old_manager.getTorrent();
					
					try{
						TOTorrent other = torrent.selectHybridHashType( canHybridTo );
						
						File temp_file = AETemporaryFileHandler.createTempFile();
	
						TorrentUtils.writeToFile( other, temp_file, false );
													
						File save_loc = old_manager.getSaveLocation();
							
						String	save_parent = save_loc.getParentFile().getAbsolutePath();
						String	save_file	= save_loc.getName();
						
						old_manager.getGlobalManager().addDownloadManager( 
								temp_file.getAbsolutePath(),
								other.getHash(),
								save_parent,
								save_file,
								DownloadManager.STATE_WAITING,
								true,
								old_manager.getAssumedComplete(),
								new DownloadManagerInitialisationAdapter(){
									
									@Override
									public void 
									initialised(
										DownloadManager new_manager, 
										boolean 		for_seeding)
									{
										DiskManagerFileInfoSet old_file_info_set = old_manager.getDiskManagerFileInfoSet();
										DiskManagerFileInfoSet new_file_info_set = new_manager.getDiskManagerFileInfoSet();
	
										DiskManagerFileInfo[] old_file_infos = old_file_info_set.getFiles();
										
										DownloadManagerState new_dms = new_manager.getDownloadState();
	
										new_dms.setDisplayName( 
											old_manager.getDisplayName() + 
											" ("+ MessageText.getString( "label.hybrid" ).toLowerCase() + 
											") (v" + (canHybridTo==TOTorrent.TT_V1?1:2)+ ")");
										
										try {
											new_dms.suppressStateSave(true);
										
											boolean[] to_skip = new boolean[old_file_infos.length];
											
											for ( int i=0; i < old_file_infos.length; i++ ){
												
												if ( old_file_infos[i].isSkipped()){
													
													to_skip[i] = true;
												}
											}
	
											new_file_info_set.setSkipped(to_skip, true);
		
										} finally {
		
											new_dms.suppressStateSave(false);
										}
									}
									
									@Override
									public int 
									getActions()
									{
										return 0;
									}
								});
								
						
					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}
			});
		}
						
		final MenuItem itemV1Hybrid = new MenuItem(menuTracker, SWT.PUSH);
		Messages.setLanguageText( itemV1Hybrid,	"menu.create.hybrid.from.v1");
		
		final MenuItem itemV1V2 = new MenuItem(menuTracker, SWT.PUSH);
		Messages.setLanguageText( itemV1V2,	"menu.create.v2.from.v1");
			
		ListenerDMTask	v1Listener = 
			new ListenerDMTask(can_create_from_v1.toArray(new DownloadManager[0]), true, false) 
			{
				boolean	create_hybrid;
				
				@Override
				public void 
				handleEvent(
					Event event )
				{
					create_hybrid = event.widget == itemV1Hybrid;
					
					super.handleEvent(event);
				}
				
				@Override
				public void 
				run(
					DownloadManager old_manager )
				{
					try{
						File save_loc = old_manager.getSaveLocation();
	
						TOTorrentCreator creator = 
								TOTorrentFactory.createFromFileOrDirWithComputedPieceLength(
									create_hybrid?TOTorrent.TT_V1_V2:TOTorrent.TT_V2, 
									save_loc, TorrentUtils.getDecentralisedEmptyURL(), false );
						
						int[] 		progress 	= { 0 };
						String[]	task		= { "" };
						
						creator.addListener(
							new TOTorrentProgressListener(){
								
								@Override
								public void 
								reportProgress(
									int percent_complete)
								{
									synchronized( progress ){
										
										progress[0] = percent_complete;
									}
								}
								
								@Override
								public void 
								reportCurrentTask(
									String task_description )
								{
									synchronized( task ){
										
										task[0] = task_description;
									}
								}
							});
						
						Core core = CoreFactory.getSingleton();
										
						core.executeOperation(
								CoreOperation.OP_PROGRESS,
								new CoreOperationTask() {
									
									@Override
									public String 
									getName()
									{
										return( MessageText.getString( "wizard.maketorrent.progresstitle" ));
									}
									
									@Override
									public DownloadManager 
									getDownload()
									{
										return null;
									}
									
									@Override
									public String[] 
									getAffectedFileSystems()
									{
										return( FileUtil.getFileStoreNames( save_loc ));
									}

									@Override
									public void 
									run(
										CoreOperation operation ) 
									{
										try {
	
											TOTorrent torrent = creator.create();
	
											File temp_file = AETemporaryFileHandler.createTempFile();
	
											TorrentUtils.writeToFile( torrent, temp_file, false );
																		
											File save_loc = old_manager.getSaveLocation();
												
											String	save_parent = save_loc.getParentFile().getAbsolutePath();
											String	save_file	= save_loc.getName();
											
											old_manager.getGlobalManager().addDownloadManager( 
													temp_file.getAbsolutePath(),
													torrent.getHash(),
													save_parent,
													save_file,
													DownloadManager.STATE_WAITING,
													true,
													true,
													new DownloadManagerInitialisationAdapter(){
														
														@Override
														public void 
														initialised(
															DownloadManager new_manager, 
															boolean 		for_seeding)
														{
															DownloadManagerState new_dms = new_manager.getDownloadState();
	
															String type_str = create_hybrid?MessageText.getString( "label.hybrid" ).toLowerCase():"v2";
															
															new_dms.setDisplayName( old_manager.getDisplayName() + " (" + type_str + ")");
														}
														
														@Override
														public int 
														getActions()
														{
															return 0;
														}
													});
													
										}catch( Throwable e ){
	
											Debug.printStackTrace(e);
										}
									}
	
									@Override
									public ProgressCallback 
									getProgressCallback() 
									{
										return( 
											new ProgressCallbackAdapter()
											{
												@Override
												public int 
												getSupportedTaskStates()
												{
													return( ST_SUBTASKS | ST_CANCEL );
												}
												
												@Override
												public void 
												setTaskState(
													int state )
												{
													if (( state & ST_CANCEL ) != 0 ){
														
														creator.cancel();
													}
												}
												
												
												@Override
												public int 
												getProgress()
												{
													synchronized( progress ){
														
														return( progress[0] * 10 );
													}
												}
												
												@Override
												public String 
												getSubTaskName()
												{
													synchronized( task ){
														
														return( task[0]);
													}
												}
											});
									}
								});
					}catch( Throwable e ){
						
						Debug.out( e );
					}
			}
		};
		
		itemV1Hybrid.addListener( SWT.Selection, v1Listener );
		itemV1V2.addListener( SWT.Selection, v1Listener );

		itemV1Hybrid.setEnabled( !can_create_from_v1.isEmpty());
		itemV1V2.setEnabled( !can_create_from_v1.isEmpty());

		
		// download link

		final MenuItem itemTorrentDL = new MenuItem(menuTracker, SWT.PUSH);
		Messages.setLanguageText(itemTorrentDL, "MyTorrentsView.menu.torrent.dl");
		itemTorrentDL.addListener(SWT.Selection, new ListenerDMTask(dms, false) {
			@Override
			public void run(DownloadManager dm) {

				String content;

				TOTorrent torrent = dm.getTorrent();

				String link = null;

				if (torrent == null) {

					content = "Torrent not available";

				} else {

					link = TorrentUtils.getObtainedFrom(torrent);

					if (link != null) {

						try {
							new URL(link);

						} catch (Throwable e) {

							link = null;
						}
					}

					if (link != null) {

						if (link.toLowerCase().startsWith("magnet:")) {

							link = UrlUtils.getMagnetURI(dm);

							content = "Torrent's magnet link:\r\n\r\n\t" + link;

						} else {

							content = "Torrent was obtained from\r\n\r\n\t" + link;
						}
					} else {

						if (TorrentUtils.isReallyPrivate(torrent)) {

							content = "Origin of torrent unknown and it is private so a magnet URI can't be used - sorry!";

						} else {

							link = UrlUtils.getMagnetURI(dm);

							content = "Origin unavailable but magnet URI may work:\r\n\r\n\t"
									+ link;
						}
					}
				}

				if (link != null) {

					ClipboardCopy.copyToClipBoard(link);

					content += "\r\n\r\nLink copied to clipboard";
				}

				final TextViewerWindow viewer = new TextViewerWindow(
						MessageText.getString("MyTorrentsView.menu.torrent.dl") + ": "
								+ dm.getDisplayName(), null, content, false);
			}
		});
		itemTorrentDL.setEnabled(dms.length == 1);

			// move torrent
		
		final MenuItem itemFileMoveTorrent = new MenuItem(menuTracker, SWT.PUSH);
		Messages.setLanguageText(itemFileMoveTorrent,
				"MyTorrentsView.menu.movetorrent");
		itemFileMoveTorrent.addListener(SWT.Selection,
				new ListenerDMTask(dms) {
					@Override
					public void run(DownloadManager[] dms) {
						TorrentUtil.moveTorrentFile(shell, dms);
					}
				});
		itemFileMoveTorrent.setEnabled(canMove);
		
			// switch torrent
		
		final MenuItem itemTorrentSwitch = new MenuItem(menuTracker, SWT.PUSH);
		Messages.setLanguageText(itemTorrentSwitch, "MyTorrentsView.menu.torrent.switch");
		itemTorrentSwitch.addListener(SWT.Selection, new Listener() {

			@Override
			public void handleEvent(Event event) {
				final TOTorrent torrent = dms[0].getTorrent();
				if ( torrent == null ){
					return;
				}
				
				try{
					byte[] existing_hash = torrent.getHash();
					
					FileDialog dialog = new FileDialog( shell, SWT.OPEN | SWT.MULTI);

					dialog.setText(MessageText.getString("dialog.select.torrent.file"));
					
					dialog.setFilterExtensions(new String[] {
							"*.torrent"
						});
						dialog.setFilterNames(new String[] {
							"*.torrent"
						});
						
					String path = dialog.open();
					
					if (path == null){
						return;
					}
					
					File file = new File( path );
					
					byte[] replacement_hash = TOTorrentFactory.deserialiseFromBEncodedFile( file ).getHash();
					
					if ( !Arrays.equals( existing_hash,  replacement_hash )){
						
						throw( new Exception( "Hash mismatch: old=" + ByteFormatter.encodeString( existing_hash ) + ", new=" + ByteFormatter.encodeString( replacement_hash )));
					}
					
					dms[0].setTorrentFileName( file.getAbsolutePath());
					
				}catch( Throwable e ){
					
					MessageBox mb = new MessageBox( shell, SWT.ICON_ERROR | SWT.OK);
					mb.setText(MessageText.getString("MyTorrentsView.menu.torrent.switch.fail"));
					mb.setMessage(
						MessageText.getString(
							"MyTorrentsView.menu.torrent.switch.fail.text",
							new String[] { Debug.getNestedExceptionMessage( e ) }));

					mb.open();
				}
			}
		});
		
		itemTorrentSwitch.setEnabled(dms.length == 1 && dms[0].isPersistent());

			// set source

		final MenuItem itemTorrentSource = new MenuItem(menuTracker, SWT.PUSH);
		Messages.setLanguageText(itemTorrentSource, "MyTorrentsView.menu.torrent.set.source");
		itemTorrentSource.addListener(SWT.Selection, new Listener() {

			@Override
			public void handleEvent(Event event) {
				final TOTorrent torrent = dms[0].getTorrent();
				if ( torrent == null ){
					return;
				}
				String msg_key_prefix = "MyTorrentsView.menu.edit_source.";
				SimpleTextEntryWindow text_entry = new SimpleTextEntryWindow();

				text_entry.setParentShell( shell );
				text_entry.setTitle(msg_key_prefix + "title");
				text_entry.setMessage(msg_key_prefix + "message");
				text_entry.setPreenteredText(TorrentUtils.getObtainedFrom( torrent ), false);
				text_entry.setWidthHint( 500 );
				text_entry.prompt(new UIInputReceiverListener() {
					@Override
					public void UIInputReceiverClosed(UIInputReceiver text_entry) {
						if (text_entry.hasSubmittedInput()) {
							TorrentUtils.setObtainedFrom( torrent, text_entry.getSubmittedInput());
							try{
								TorrentUtils.writeToFile( torrent );
							}catch( Throwable e ){
							}
						}
					}
				});
			}
		});
		itemTorrentSource.setEnabled(dms.length == 1);
			// set thumbnail

		final MenuItem itemTorrentThumb = new MenuItem(menuTracker, SWT.PUSH);
		Messages.setLanguageText(itemTorrentThumb, "MyTorrentsView.menu.torrent.set.thumb");
		itemTorrentThumb.addListener(SWT.Selection, new Listener() {

			@Override
			public void handleEvent(Event event) {
				FileDialog fDialog = new FileDialog( shell, SWT.OPEN | SWT.MULTI);

				fDialog.setText(MessageText.getString("MainWindow.dialog.choose.thumb"));
				String path = fDialog.open();
				if (path == null)
					return;

				File file = new File( path );

				try{
					byte[] thumbnail = FileUtil.readFileAsByteArray( file );

					String name = file.getName();

					int	pos = name.lastIndexOf( "." );

					String ext;

					if ( pos != -1 ){

						ext = name.substring( pos+1 );

					}else{

						ext = "";
					}

					String type = HTTPUtils.guessContentTypeFromFileType( ext );

					for ( DownloadManager dm: dms ){

						try{
							TOTorrent torrent = dm.getTorrent();

							PlatformTorrentUtils.setContentThumbnail( torrent, thumbnail, type );

						}catch( Throwable e ){

						}
					}
				}catch( Throwable e ){

					Debug.out( e );
				}
			}
		});

		itemTorrentThumb.setEnabled(hasSelection);

		// explore torrent file

		final MenuItem itemTorrentExplore = new MenuItem(menuTracker, SWT.PUSH);
		Messages.setLanguageText(itemTorrentExplore, "MyTorrentsView.menu."
				+ (use_open_containing_folder ? "open_parent_folder" : "explore"));
		itemTorrentExplore.addListener(SWT.Selection, new ListenerDMTask(dms, false) {
			@Override
			public void run(DownloadManager dm) {
				ManagerUtils.open(new File(dm.getTorrentFileName()),
						use_open_containing_folder);
			}
		});
	}

	protected static void moveTorrentFile(Shell shell, DownloadManager[] dms) {
		if (dms != null && dms.length > 0) {

			DirectoryDialog dd = new DirectoryDialog(shell);
			String filter_path = TorrentOpener.getFilterPathTorrent();

			// If we don't have a decent path, default to the path of the first
			// torrent.
			if (filter_path == null || filter_path.trim().length() == 0) {
				filter_path = new File(dms[0].getTorrentFileName()).getParent();
			}

			dd.setFilterPath(filter_path);

			dd.setText(MessageText.getString("MyTorrentsView.menu.movedata.dialog"));

			String path = dd.open();

			if (path != null) {

				File target = new File(path);

				TorrentOpener.setFilterPathTorrent(target.toString());

				for (int i = 0; i < dms.length; i++) {

					try {
						dms[i].moveTorrentFile(target);

					} catch (Throwable e) {

						Logger.log(new LogAlert(dms[i], LogAlert.REPEATABLE,
								"Download torrent move operation failed", e));
					}
				}
			}
		}
	}

	public static void 
	moveDataFiles(
		Shell 				shell, 
		DownloadManager[] 	dms,
		boolean				batch ) 
	{
		if (dms != null && dms.length > 0) {

			if ( batch ){
				
				Map<String,DownloadManager>		dm_name_map = new HashMap<>();
				
				StringBuilder details = new StringBuilder( 32*1024 );
				
				for ( DownloadManager dm: dms ){
										
					dm_name_map.put( dm.getInternalName(), dm );	
					
					details.append( "# " + dm.getInternalName() + " - " );
					details.append( dm.getDisplayName());
					details.append( "\n\n" );
					
					details.append( "    " );
					details.append( dm.getSaveLocation().getParentFile().getAbsolutePath());
					details.append( "\n\n" );
				}
				
				TextViewerWindow viewer =
						new TextViewerWindow(
		        			  Utils.findAnyShell(),
		        			  "batch.move.title",
		        			  "batch.move.text",
		        			  details.toString(), true, true );

				viewer.setEditable( true );
				
				viewer.setNonProportionalFont();
								
				viewer.addListener(
					new TextViewerWindow.TextViewerWindowListener() {

						@Override
						public void closed(){
							if ( !viewer.getOKPressed()){
								return;
							}
								
							String text = viewer.getText();
							
							if ( text.equals( details.toString())){
								
								return;
							}
							
							String[] lines = text.split( "\n" );
							
							StringBuilder result = new StringBuilder( 23*1024 );
							
							List<Object[]> actions = new ArrayList<>();
									
							DownloadManager current_dm = null;
							
							for ( String line: lines ){
								
								line = line.trim();
								
								if ( line.isEmpty()){
									
									continue;
								}
								
								if ( line.startsWith( "#" )){
									
									try{
										String[] bits = line.split(  "\\s+", 3 );	
									
										current_dm = dm_name_map.get( bits[1].trim());
											
										if ( current_dm == null ){
											
											result.append( "Invalid line: " + line + ": download not found\n" );
										}
									}catch( Throwable e ){
										
										result.append( "Invalid line: " + line + "\n" );
									}
								}else{
																		
									try{										
										String path = line.trim();
										
										if ( !current_dm.getSaveLocation().getParentFile().getAbsolutePath().equals( path )){
										
											actions.add( new Object[]{ current_dm, path } );
										}
									}catch( Throwable e ){
										
										result.append( "Invalid line: " + line + "\n" );
									}
								}
							}
							
							if ( result.length() > 0 ){
								
								Utils.execSWTThreadLater(
									1, 
									new Runnable()
									{
										public void
										run()
										{
											TextViewerWindow viewer =
													new TextViewerWindow(
									        			  Utils.findAnyShell(),
									        			  "batch.move.title",
									        			  "batch.retarget.error.text",
									        			  result.toString(), true, true );
											
											viewer.setNonProportionalFont();
											
											viewer.goModal();
										}
									});
								
							}else if ( !actions.isEmpty()){
								
								for ( Object[] action: actions ){
									
									DownloadManager dm = (DownloadManager)action[0];
									
									String	path 	= (String)action[1];																
										
									result.append( "# " + dm.getInternalName() + " - " );
									result.append( dm.getDisplayName());
									result.append( "\n\n" );
									
									result.append( "    " + dm.getSaveLocation().getParentFile().getAbsolutePath() +  " -> " + path + "\n\n" );
									
									AEThread2.createAndStartDaemon( 
										"File Mover" , 
										()->{
										
												// get back onto SWT thread to cause progress dialog window to be shown
											
											Utils.execSWTThread(()->{
												
												try{
													
													dm.moveDataFilesLive( new File( path) );
					
												}catch( Throwable e ){
					
													Logger.log(new LogAlert(dm, LogAlert.REPEATABLE,
															"Download data move operation failed", e));
												}});
										});
								}
								
								if ( result.length() > 0 ){
									
									result.append( MessageText.getString( "batch.move.progress" ));
									
									Utils.execSWTThreadLater(
										1, 
										new Runnable()
										{
											public void
											run()
											{
												TextViewerWindow viewer =
														new TextViewerWindow(
										        			  Utils.findAnyShell(),
										        			  "batch.move.title",
										        			  "batch.retarget.result.text",
										        			  result.toString(), true, true );
												
												viewer.setNonProportionalFont();
												
												viewer.goModal();
											}
										});
								}
							}
						}
					});
				
				viewer.goModal();
				
				
			}else{
				
				DirectoryDialog dd = new DirectoryDialog(shell);
	
				String filter_path = TorrentOpener.getFilterPathData();
	
				// If we don't have a decent path, default to the path of the first
				// torrent.
				if (filter_path == null || filter_path.trim().length() == 0) {
					filter_path = new File(dms[0].getTorrentFileName()).getParent();
				}
	
				dd.setFilterPath(filter_path);
	
				dd.setText(MessageText.getString("MyTorrentsView.menu.movedata.dialog"));
	
				String path = dd.open();
	
				if (path != null) {
	
					TorrentOpener.setFilterPathData(path);
	
					File target = new File(path);
	
						// we can do this in parallel as the core manages queueing of move ops now
					
					for (int i = 0; i < dms.length; i++) {
	
						DownloadManager dm = dms[i];
						
						AEThread2.createAndStartDaemon( "File Mover" , ()->{
									
								// get back onto SWT thread to cause progress dialog window to be shown
							
							Utils.execSWTThread(()->{
								
								try{
									
									dm.moveDataFilesLive(target);
	
								}catch( Throwable e ){
	
									Logger.log(new LogAlert(dm, LogAlert.REPEATABLE,
											"Download data move operation failed", e));
								}});
						});
					}
				}
			}
		}
	}

	protected static void clearMOC(DownloadManager[] dms) {
		if (dms != null && dms.length > 0) {

			for (int i = 0; i < dms.length; i++) {
	
				dms[i].getDownloadState().setAttribute( DownloadManagerState.AT_MOVE_ON_COMPLETE_DIR, null );
			}
		}
	}
	
	protected static void setMOC(Shell shell, DownloadManager[] dms) {
		if (dms != null && dms.length > 0) {

			DirectoryDialog dd = new DirectoryDialog(shell);

			String filter_path = TorrentOpener.getFilterPathData();

			// If we don't have a decent path, default to the path of the first
			// torrent.
			if (filter_path == null || filter_path.trim().length() == 0) {
				filter_path = new File(dms[0].getTorrentFileName()).getParent();
			}

			dd.setFilterPath(filter_path);

			dd.setText(MessageText.getString("MyTorrentsView.menu.movedata.dialog"));

			String path = dd.open();

			if ( path != null ){

				TorrentOpener.setFilterPathData(path);

				File target = new File(path);

				for (int i = 0; i < dms.length; i++) {

					dms[i].getDownloadState().setAttribute( DownloadManagerState.AT_MOVE_ON_COMPLETE_DIR, target.getAbsolutePath());
				}
			}
		}
	}
	
	protected static void exportDownloads(Shell shell, DownloadManager[] dms) {
		if (dms != null && dms.length > 0) {

			DirectoryDialog dd = new DirectoryDialog(shell);

			String filter_path = TorrentOpener.getFilterPathExport();

			// If we don't have a decent path, default to the path of the first
			// torrent.
			if (filter_path == null || filter_path.trim().length() == 0) {
				filter_path = new File(dms[0].getTorrentFileName()).getParent();
			}

			dd.setFilterPath(filter_path);

			dd.setText(MessageText.getString("MyTorrentsView.menu.exportdownload.dialog"));

			String path = dd.open();

			if (path != null) {

				TorrentOpener.setFilterPathExport(path);

				File target = new File(path);

				for (int i = 0; i < dms.length; i++) {

					int f_i = i;
					
					AEThread2.createAndStartDaemon(
						"Exporter",
						()->{
							try{
								dms[f_i].exportDownload(target);
							} catch (Throwable e) {

								Logger.log(new LogAlert(dms[f_i], LogAlert.REPEATABLE,
										"Download export operation failed", e));
							}
						});
				}
			}
		}
	}
	
	public static void repositionManual(final TableView<DownloadManager> tv,
			final DownloadManager[] dms, final Shell shell,
			final boolean isSeedingView) {
		SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
				"MyTorrentsView.dialog.setPosition.title",
				"MyTorrentsView.dialog.setPosition.text");
		entryWindow.prompt(new UIInputReceiverListener() {
			@Override
			public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
				if (!entryWindow.hasSubmittedInput()) {
					return;
				}
				String sReturn = entryWindow.getSubmittedInput();

				if (sReturn == null)
					return;

				int newPosition = -1;
				try {
					newPosition = Integer.valueOf(sReturn).intValue();
				} catch (NumberFormatException er) {
					// Ignore
				}

				Core core = CoreFactory.getSingleton();
				
				if (core == null) {
					return;
				}
				
				if (newPosition <= 0) {
					MessageBox mb = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
					mb.setText(MessageText.getString("MyTorrentsView.dialog.NumberError.title"));
					mb.setMessage(MessageText.getString("MyTorrentsView.dialog.NumberError.text"));

					mb.open();
					return;
				}

				moveSelectedTorrentsTo(tv, dms, newPosition);
			}
		});
	}

	protected static void addCategorySubMenu(final DownloadManager[] dms,
			Menu menuCategory)
	{
		MenuItem[] items = menuCategory.getItems();
		int i;
		for (i = 0; i < items.length; i++) {
			items[i].dispose();
		}

			// add cat
		
		final MenuItem itemAddCategory = new MenuItem(menuCategory, SWT.PUSH);
		Messages.setLanguageText(itemAddCategory,
				"MyTorrentsView.menu.setCategory.add");

		itemAddCategory.addListener(SWT.Selection, new ListenerDMTask(dms) {
			@Override
			public void run(DownloadManager[] dms) {
				CategoryUIUtils.showCreateCategoryDialog(new TagReturner() {
					@Override
					public void returnedTags(Tag[] tags) {
						if (tags.length == 1 && tags[0] instanceof Category) {
							assignToCategory(dms, (Category) tags[0]);
						}
					}
				});
			}
		});
		
		Category[] categories = CategoryManager.getCategories();
		Arrays.sort(categories);

		// Ensure that there is at least one user category available.
		boolean allow_category_selection = categories.length > 0;
		if (allow_category_selection) {
			boolean user_category_found = false;
			for (i = 0; i < categories.length; i++) {
				if (categories[i].getType() == Category.TYPE_USER) {
					user_category_found = true;
					break;
				}
			}
			// It may be the categories array just contains "uncategorised".
			allow_category_selection = user_category_found;
		}

		if (allow_category_selection) {
			
			new MenuItem(menuCategory, SWT.SEPARATOR);

			final Category catUncat = CategoryManager.getCategory(Category.TYPE_UNCATEGORIZED);
			if (catUncat != null) {
				final MenuItem itemCategory = new MenuItem(menuCategory, SWT.PUSH);
				Messages.setLanguageText(itemCategory, catUncat.getName());
				itemCategory.addListener(SWT.Selection, new ListenerDMTask(dms) {
					@Override
					public void run(DownloadManager dm) {
						dm.getDownloadState().setCategory(catUncat);
					}
				});

				new MenuItem(menuCategory, SWT.SEPARATOR);
			}

			for (i = 0; i < categories.length; i++) {
				final Category category = categories[i];
				if (category.getType() == Category.TYPE_USER) {
					final MenuItem itemCategory = new MenuItem(menuCategory, SWT.PUSH);
					itemCategory.setText(category.getName());
					TagUIUtils.setMenuIcon( itemCategory, categories[i] ); 
					itemCategory.addListener(SWT.Selection, new ListenerDMTask(dms) {
						@Override
						public void run(DownloadManager dm) {
							dm.getDownloadState().setCategory(category);
						}
					});
				}
			}
		}
	}
	
	private static void 
	moveSelectedTorrentsTo(
		TableView<DownloadManager> 	tv,
		DownloadManager[] 			selected_dms, 
		int 						iNewPos ) 
	{
		if ( selected_dms == null || selected_dms.length == 0 ){
			
			return;
		}
		
			// managers might be both incomplete and complete (coming from simple library view) so best
			// solution is to split and treat separately (obviously iNewPos doesn't really make sense to both
			// but whatever...)
		
		GlobalManager gm = selected_dms[0].getGlobalManager();
				
		List<DownloadManager>	selected_incomplete = new ArrayList<>();
		List<DownloadManager>	selected_complete	= new ArrayList<>();
		
		for ( DownloadManager dm: selected_dms ){
			
			if ( dm.isDownloadComplete( false )){
				selected_complete.add( dm );
			}else{
				selected_incomplete.add( dm );
			}
		}
		
		List<DownloadManager> all_dms = gm.getDownloadManagers();

		List<DownloadManager>	all_incomplete	= new ArrayList<>();
		List<DownloadManager>	all_complete	= new ArrayList<>();
		
		for ( DownloadManager dm: all_dms ){
			
			if ( dm.isDownloadComplete( false )){
				all_complete.add( dm );
			}else{
				all_incomplete.add( dm );
			}
		}
		
		if ( !selected_incomplete.isEmpty()){
			
			moveSelectedTorrentsTo( gm, all_incomplete, selected_incomplete, iNewPos );
		}
		
		if ( !selected_complete.isEmpty()){
			
			moveSelectedTorrentsTo( gm, all_complete, selected_complete, iNewPos );
		}

		TableColumnCore[] sortColumn = tv.getSortColumns();
		boolean bForceSort = sortColumn.length != 0
			&& "#".equals(sortColumn[0].getName());

		tv.columnInvalidate("#");
		
		tv.refreshTable(bForceSort);
	}
	

	private static void 
	moveSelectedTorrentsTo(
		GlobalManager				gm,
		List<DownloadManager>		all_dms, 
		List<DownloadManager>		selected_dms,
		int 						iNewPos ) 
	{		
		int num_selected = selected_dms.size();
	
		// selected downloads are in the required order relative to iNewPos
		// problem is that some of the selection's positions might screw things up - if you put download X in the right location
		// and then go to position Y next, but Y happens to be before X in the list, then X will be shunted down into the wrong place
		// when Y is removed
		// easiest fix is to fill positions < iNewPos before moving downloads into place
		 
		int num_dms = all_dms.size();
		
		if ( iNewPos > num_dms ){
		
				// invalid index -> end of selection ends up a max
			
			iNewPos = num_dms - num_selected + 1;
		}
		
		if ( iNewPos > 1 ){
						
			all_dms.sort(
				new Comparator<DownloadManager>()
				{
					@Override
					public int compare(DownloadManager o1, DownloadManager o2){
						return( o1.getPosition() - o2.getPosition());
					}
				});
			
			IdentityHashSet<DownloadManager> moving = new IdentityHashSet<>( selected_dms );
			
			int pos		= 1;
			int to_move = iNewPos-1;
			
			for ( DownloadManager dm: all_dms ){
							
				if ( !moving.contains( dm )){
					
					gm.moveTo( dm, pos++ );
					
					to_move--;
					
					if ( to_move == 0 ){
						
						break;
					}
				}
			}
		}
				
		for ( DownloadManager dm: selected_dms ){
			
			gm.moveTo(dm, iNewPos++);

			if ( iNewPos > num_dms ){
				
				iNewPos = 1;
			}
		}
	}

	protected static void changeDirSelectedTorrents(DownloadManager[] dms,
			Shell shell) {
		if (dms.length <= 0)
			return;

		String sDefPath = COConfigurationManager.getStringParameter("Default save path");

		if (sDefPath.length() > 0) {
			File f = new File(sDefPath);

			if (!f.exists()) {
				FileUtil.mkdirs(f);
			}
		}

		DirectoryDialog dDialog = new DirectoryDialog(shell, SWT.SYSTEM_MODAL);
		dDialog.setFilterPath(sDefPath);
		dDialog.setMessage(MessageText.getString("MainWindow.dialog.choose.savepath"));
		String sSavePath = dDialog.open();
		if (sSavePath != null) {
			File fSavePath = new File(sSavePath);
			for (int i = 0; i < dms.length; i++) {
				DownloadManager dm = dms[i];

				int state = dm.getState();
				if (state != DownloadManager.STATE_ERROR) {
					
						// if download is stopped and not allocated then use the 'move' operation to avoid the subsequent recheck
						
					if ( state == DownloadManager.STATE_STOPPED && !dm.isDataAlreadyAllocated()){
						
						DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
						
						boolean found_file = false;
						
						for ( DiskManagerFileInfo info : files ){
															
							if ( info.getFile(true).exists()){
								
								found_file = true;
							
								break;
							}
						}
						
						if ( !found_file ){
							
							try{
								dm.moveDataFilesLive( fSavePath );
								
							} catch (Throwable e) {
	
								Logger.log(new LogAlert(dms[i], LogAlert.REPEATABLE,
										"Download data move operation failed", e));
							}
							
							continue;
						}
					}
					
					if ( !dm.filesExist(true)){
						
						state = DownloadManager.STATE_ERROR;
					}
				}

				if (state == DownloadManager.STATE_ERROR) {

					dm.setTorrentSaveDir(FileUtil.newFile(sSavePath), false);

					boolean found = dm.filesExist(true);
					if (!found && dm.getTorrent() != null
							&& !dm.getTorrent().isSimpleTorrent()) {
						String parentPath = fSavePath.getParent();
						if (parentPath != null) {
							dm.setTorrentSaveDir(FileUtil.newFile(parentPath), false);
							found = dm.filesExist(true);
							if (!found) {
								dm.setTorrentSaveDir(FileUtil.newFile(parentPath,
									fSavePath.getName()), true);

								found = dm.filesExist(true);
								if (!found) {
									dm.setTorrentSaveDir(FileUtil.newFile(sSavePath,
										dm.getDisplayName()), true);

									found = dm.filesExist(true);
									if (!found) {
										dm.setTorrentSaveDir(FileUtil.newFile(sSavePath), false);
									}
								}
							}
						}
					}

					if (found) {
						dm.stopIt(DownloadManager.STATE_STOPPED, false, false);

						ManagerUtils.queue(dm, shell);
					}
				}
			}
		}
	}

	/**
	 * Runs a DownloadManager or DiskManagerFileInfo
	 */
	public static void runDataSources(Object[] datasources) {
		for (int i = datasources.length - 1; i >= 0; i--) {
			Object ds = PluginCoreUtils.convert(datasources[i], true);
			if (ds instanceof DownloadManager) {
				DownloadManager dm = (DownloadManager) ds;
				ManagerUtils.run(dm);
			} else if (ds instanceof DiskManagerFileInfo) {
				DiskManagerFileInfo info = (DiskManagerFileInfo) ds;
				Utils.launch(info);
			}
		}
	}

	public static void hostTorrents(final Object[] download_managers) {
		ListenerDMTask task = new ListenerDMTask(toDMS(download_managers), true,
				true) {
			@Override
			public void run(DownloadManager dm) {
				ManagerUtils.host(CoreFactory.getSingleton(), dm);
			}
		};
		task.go();
		UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
		if (uiFunctions != null) {
			uiFunctions.getMDI().showEntryByID(
					MultipleDocumentInterface.SIDEBAR_SECTION_MY_TRACKER);
		}
	}

	public static void publishTorrents(final Object[] download_managers) {
		ListenerDMTask task = new ListenerDMTask(toDMS(download_managers), true,
				true) {
			@Override
			public void run(DownloadManager dm) {
				ManagerUtils.publish(CoreFactory.getSingleton(), dm);
			}
		};
		task.go();
		UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
		if (uiFunctions != null) {
			uiFunctions.getMDI().showEntryByID(
					MultipleDocumentInterface.SIDEBAR_SECTION_MY_TRACKER);
		}
	}

	/**
	 * @param datasources DownloadManager, DiskManagerFileInfo, SelectedContent
	 *
	 * @note Takes a long time with large list
	 */
	public static void removeDataSources(final Object[] datasources) {
		DownloadManager[] dms = toDMS(datasources);
		removeDownloads(dms, null);
		DiskManagerFileInfo[] fileInfos = toDMFI(datasources);
		if (fileInfos.length > 0) {
			FilesViewMenuUtil.changePriority(FilesViewMenuUtil.PRIORITY_DELETE,
					Arrays.asList( fileInfos));
		}
		DownloadStubEx[] stubs = toDownloadStubs(  datasources );
		if ( stubs.length > 0 ){
			removeDownloadStubs(stubs,null,false);
		}
	}

	public static Boolean shouldStopGroupTest(Object[] datasources) {
		DownloadManager[] dms = toDMS(datasources);
		DiskManagerFileInfo[] dmfi = toDMFI(datasources);
		if (dms.length == 0 && dmfi.length == 0) {
			return null;
		}
		for (DownloadManager dm : dms) {
			int state = dm.getState();
			boolean stopped = state == DownloadManager.STATE_STOPPED
					|| state == DownloadManager.STATE_STOPPING;
			if (!stopped) {
				return true;
			}
		}

		for (DiskManagerFileInfo fileInfo : dmfi) {
			if (!fileInfo.isSkipped()) {
				return true;
			}
		}
		return false;
	}
	
	public static void stopOrStartDataSources(Object[] datasources, boolean extendedAction ) {
		DownloadManager[] dms = toDMS(datasources);
		DiskManagerFileInfo[] dmfi = toDMFI(datasources);
		if (dms.length == 0 && dmfi.length == 0) {
			return;
		}
		Boolean doStop = shouldStopGroupTest(datasources);
		if ( doStop == null ){
			doStop = true;
		}
		if (doStop) {
			stopDataSources(datasources, extendedAction);
		} else {
			queueDataSources(datasources, extendedAction);
		}
	}

	public static void stopDataSources(Object[] datasources) {
		stopDataSources(datasources,false);
	}
	public static void stopDataSources(Object[] datasources, boolean pause) {
		DownloadManager[] dms = toDMS(datasources);
		for (DownloadManager dm : dms) {
			if (pause){
				ManagerUtils.pause(dm, null);
			}else{
				ManagerUtils.stop(dm, null);
			}
		}
		DiskManagerFileInfo[] fileInfos = toDMFI(datasources);
		if (fileInfos.length > 0) {
			FilesViewMenuUtil.changePriority(FilesViewMenuUtil.PRIORITY_SKIPPED,
					Arrays.asList(fileInfos));
		}
	}

	public static void pauseDataSources(Object[] datasources) {
		DownloadManager[] dms = toDMS(datasources);
		for (DownloadManager dm : dms) {
			ManagerUtils.pause(dm, null);
		}
	}

	
	public static void 
	queueDataSources(
		Object[] datasources,
		boolean extendedAction ) 
	{
		DownloadManager[] dms = toDMS(datasources);
		for (DownloadManager dm : dms) {
			if ( extendedAction ){
				dm.setForceStart( true );
			}else{
				ManagerUtils.queue(dm, null);
			}
		}
		DiskManagerFileInfo[] fileInfos = toDMFI(datasources);
		if (fileInfos.length > 0) {
			FilesViewMenuUtil.changePriority(FilesViewMenuUtil.PRIORITY_NORMAL,
					Arrays.asList(fileInfos));

			if (extendedAction) {
				for (DiskManagerFileInfo fileInfo : fileInfos) {
					if (fileInfo.getDownloadManager().getState() == DownloadManager.STATE_STOPPED) {
						ManagerUtils.queue(fileInfo.getDownloadManager(), null);
					}
				}
			}
		}
	}

	public static void resumeTorrents(Object[] download_managers) {
		ListenerDMTask task = new ListenerDMTask(toDMS(download_managers)) {
			@Override
			public void run(DownloadManager dm) {
				ManagerUtils.start(dm);
			}
		};
		task.go();
	}

	// Category Stuff
	public static void assignToCategory(Object[] download_managers,
			final Category category) {
		ListenerDMTask task = new ListenerDMTask(toDMS(download_managers)) {
			@Override
			public void run(DownloadManager dm) {
				dm.getDownloadState().setCategory(category);
			}
		};
		task.go();
	}

	public static void promptUserForComment(final DownloadManager[] dms) {
		if (dms.length == 0) {
			return;
		}
		DownloadManager dm = dms[0];

		promptUserForComment( 
			dm.getDownloadState().getUserComment(),
			(value_to_set)->{
				ListenerDMTask task = new ListenerDMTask(dms) {
					@Override
					public void run(DownloadManager dm) {
						dm.getDownloadState().setUserComment(value_to_set);
					}
				};
				task.go();
			});
	}
	
	public static void promptUserForComment(
		String				text,
		Consumer<String>	consumer )
	{

		// Create dialog box.
		String msg_key_prefix = "MyTorrentsView.menu.edit_comment.enter.";
		SimpleTextEntryWindow text_entry = new SimpleTextEntryWindow();
		text_entry.setTitle(msg_key_prefix + "title");
		text_entry.setMessage(msg_key_prefix + "message");
		text_entry.setPreenteredText(text, false);
		text_entry.setWidthHint( 800 );
		text_entry.setLineHeight( 10 );
		text_entry.setMultiLine(true);
		text_entry.setResizeable( true );
		text_entry.setDetectURLs( true );
		text_entry.setRememberLocationSize("ui.torrent.comment");
		
		text_entry.prompt(new UIInputReceiverListener() {
			@Override
			public void UIInputReceiverClosed(UIInputReceiver text_entry) {
				if (text_entry.hasSubmittedInput()) {
					String value = text_entry.getSubmittedInput();
					final String value_to_set = (value.length() == 0) ? null : value;
					consumer.accept(  value_to_set );
				}
			}
		});

	}

	public static void promptUserForDescription(final DownloadManager[] dms) {
		if (dms.length == 0) {
			return;
		}
		DownloadManager dm = dms[0];

		String desc = null;

		try{
			desc = PlatformTorrentUtils.getContentDescription( dm.getTorrent());

			if ( desc == null ){
				desc = "";
			}
		}catch( Throwable e ){

		}
		String msg_key_prefix = "MyTorrentsView.menu.edit_description.enter.";
		SimpleTextEntryWindow text_entry = new SimpleTextEntryWindow();
		text_entry.setTitle(msg_key_prefix + "title");
		text_entry.setMessage(msg_key_prefix + "message");
		text_entry.setPreenteredText(desc, false);
		text_entry.setMultiLine(true);
		text_entry.setResizeable( true );
		text_entry.setWidthHint( 500 );
		text_entry.setLineHeight( 16 );
		text_entry.prompt(new UIInputReceiverListener() {
			@Override
			public void UIInputReceiverClosed(UIInputReceiver text_entry) {
				if (text_entry.hasSubmittedInput()) {
					String value = text_entry.getSubmittedInput();
					final String value_to_set = (value.length() == 0) ? null : value;
					ListenerDMTask task = new ListenerDMTask(dms) {
						@Override
						public void run(DownloadManager dm) {
							PlatformTorrentUtils.setContentDescription( dm.getTorrent(),value_to_set);
						}
					};
					task.go();
				}
			}
		});

	}
	private static DownloadManager[] toDMS(Object[] objects) {
		int count = 0;
		DownloadManager[] result = new DownloadManager[objects.length];
		for (Object object : objects) {
			if (object instanceof DownloadManager) {
				DownloadManager dm = (DownloadManager) object;
				result[count++] = dm;
			} else if (object instanceof SelectedContent) {
				SelectedContent sc = (SelectedContent) object;
				if (sc.getFileIndex() == -1 && sc.getDownloadManager() != null) {
					result[count++] = sc.getDownloadManager();
				}
			}
		}
		DownloadManager[] resultTrim = new DownloadManager[count];
		System.arraycopy(result, 0, resultTrim, 0, count);
		return resultTrim;
	}

	private static DownloadStubEx[] toDownloadStubs(Object[] objects){
		List<DownloadStubEx>	result = new ArrayList<>( objects.length );
		for ( Object o: objects ){
			if ( o instanceof DownloadStubEx ){
				result.add((DownloadStubEx)o);
			}
		}
		return( result.toArray( new DownloadStubEx[result.size()]));
	}

	private static DiskManagerFileInfo[] toDMFI(Object[] objects) {
		int count = 0;
		DiskManagerFileInfo[] result = new DiskManagerFileInfo[objects.length];
		for (Object object : objects) {
			if (object instanceof DiskManagerFileInfo) {
				DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) object;
				result[count++] = fileInfo;
			} else if (object instanceof SelectedContent) {
				SelectedContent sc = (SelectedContent) object;
				int fileIndex = sc.getFileIndex();
				if (fileIndex >= 0 && sc.getDownloadManager() != null) {
					DownloadManager dm = sc.getDownloadManager();
					if (dm != null) {
						DiskManagerFileInfo[] infos = dm.getDiskManagerFileInfo();
						if (fileIndex < infos.length) {
							result[count++] = infos[fileIndex];
						}
					}
				}
			}
		}
		DiskManagerFileInfo[] resultTrim = new DiskManagerFileInfo[count];
		System.arraycopy(result, 0, resultTrim, 0, count);
		return resultTrim;
	}

	/**
	 * quick check to see if a file might be a torrent
	 * @param torrentFile
	 * @return
	 *
	 * @since 3.0.2.3
	 */
	public static boolean isFileTorrent( String originatingLocation, File torrentFile, String torrentName, boolean warnOnError ) {
		String sFirstChunk = null;
		try {
			sFirstChunk = FileUtil.readFileAsString(torrentFile, 16384).toLowerCase();

			try{
				if (!sFirstChunk.startsWith("d")) {
					sFirstChunk = FileUtil.readGZippedFileAsString(torrentFile, 16384).toLowerCase();
				}
			}catch( Throwable e ){

			}
		} catch (IOException e) {
			Debug.out("warning", e);
		}
		if (sFirstChunk == null) {
			sFirstChunk = "";
		}

		if (!sFirstChunk.startsWith("d")) {

			boolean isHTML = sFirstChunk.contains("<html");

			String retry_url = UrlUtils.parseTextForMagnets(torrentName);
			if (retry_url == null) {
				
					// to many html files these days have non-torrent related raw hashes in them
					// so don't look for these
				
				retry_url = UrlUtils.parseTextForMagnets(sFirstChunk,false);
			}

			if (retry_url != null) {
				TorrentOpener.openTorrent(retry_url);
				return false;
			}

			if ( warnOnError ){
				String[] buttons;

				String	chat_key	= null;
				String	chat_net	= null;

				if ( originatingLocation != null && originatingLocation.toLowerCase(Locale.US).startsWith( "http" )){

					try{
						URL url = new URL( originatingLocation );

						String host = url.getHost();

						String interesting = DNSUtils.getInterestingHostSuffix( host );

						if ( interesting != null ){

							String net = AENetworkClassifier.categoriseAddress( host );

							if ( 	( net == AENetworkClassifier.AT_PUBLIC && BuddyPluginUtils.isBetaChatAvailable()) ||
									( net == AENetworkClassifier.AT_I2P && BuddyPluginUtils.isBetaChatAnonAvailable())){

								chat_key	= "Torrent Error: " + interesting;
								chat_net	= net;
							}
						}
					}catch( Throwable e ){

					}
				}

				if ( chat_key == null ){

					buttons = new String[] {
						MessageText.getString("Button.ok")
					};

				}else{

					buttons = new String[] {
							MessageText.getString("label.chat"),
							MessageText.getString("Button.ok"),

						};
				}

				MessageBoxShell boxShell = new MessageBoxShell(
						MessageText.getString("OpenTorrentWindow.mb.notTorrent.title"),
						MessageText.getString(
								"OpenTorrentWindow.mb.notTorrent.text",
								new String[] {
									torrentName,
									isHTML
											? ""
											: MessageText.getString("OpenTorrentWindow.mb.notTorrent.cannot.display")
								}), buttons, buttons.length-1);

				if (isHTML) {
					boxShell.setHtml(sFirstChunk);
				}

				final String	f_chat_key = chat_key;
				final String	f_chat_net = chat_net;

				boxShell.open(
					new UserPrompterResultListener() {

						@Override
						public void prompterClosed(int result) {
							if ( f_chat_key != null && result == 0 ){
								BuddyPluginUtils.createBetaChat(
										f_chat_net,
										f_chat_key,
										new BuddyPluginUtils.CreateChatCallback()
										{
											@Override
											public void
											complete(
												ChatInstance	chat )
											{
												if ( chat != null ){

													chat.setInteresting( true );
												}
											}
										});
							}
						}
					});
			}

			return false;
		}

		return true;
	}

	// XXX Don't think *View's need this call anymore.  ToolBarView does it fo them
	public static Map<String, Long> calculateToolbarStates(
            ISelectedContent[] currentContent, String viewID_unused) {
		//System.out.println("calculateToolbarStates(" + currentContent.length + ", " + viewID_unused + " via " + Debug.getCompressedStackTrace());
		/*
		String[] TBKEYS = new String[] {
			"download",
			"play",
			"stream",
			"run",
			"top",
			"up",
			"down",
			"bottom",
			"start",
			"stop",
			"remove"
		};
		*/

		Map<String, Long> mapNewToolbarStates = new HashMap<>();

		String[] itemsNeedingSelection = {};

		String[] itemsNeedingRealDMSelection = {
			"remove",
			"top",
			"bottom",
			"transcode",
			"startstop",
		};

		String[] itemsRequiring1DMwithHash = {
			"details",
			"comment",
			"up",
			"down",
		};

		String[] itemsRequiring1DMSelection = {};

		int numSelection = currentContent.length;
		boolean hasSelection = numSelection > 0;
		boolean has1Selection = numSelection == 1;

		for (int i = 0; i < itemsNeedingSelection.length; i++) {
			String itemID = itemsNeedingSelection[i];
			mapNewToolbarStates.put(itemID, hasSelection
					? UIToolBarItem.STATE_ENABLED : 0);
		}

		TableView tv = SelectedContentManager.getCurrentlySelectedTableView();

		boolean hasRealDM = tv != null; // not sure why we assume that the existance of any table view
		// implies we have a real DM... Anyway, if false go ahead and test

		if (!hasRealDM && numSelection > 0) {
			hasRealDM = true;
			for (int i = 0; i < currentContent.length; i++) {
				ISelectedContent content = currentContent[i];
				DownloadManager dm = content.getDownloadManager();
				if (dm == null) {
					hasRealDM = false;
					break;
				}
			}
		}
		if (!hasRealDM) {
			MultipleDocumentInterfaceSWT mdi = UIFunctionsManagerSWT.getUIFunctionsSWT().getMDISWT();
			if (mdi != null) {
				MdiEntrySWT entry = mdi.getCurrentEntry();
				if (entry != null) {
					if (entry.getDataSource() instanceof DownloadManager) {
						hasRealDM = true;
					} else if ((entry instanceof UIPluginView)
							&& (((UIPluginView) entry).getDataSource() instanceof DownloadManager)) {
						hasRealDM = true;
					}
				}
			}
		}

		boolean canStart = false;
		boolean canStop = false;
		boolean canRemoveFileInfo = false;
		boolean canRunFileInfo = false;
		boolean canCheckExist = false;
		
		boolean hasDM = false;

		boolean canRecheck = false;
		
		if (currentContent.length > 0 && hasRealDM) {

				// well, in fact, we can have hasRealDM set to true here (because tv isn't null) and actually not have a real dm.
				// fancy that - protect against null DownloadManagers...

			boolean canMoveUp = false;
			boolean canMoveDown = false;
			boolean canDownload = false;
						
			GlobalManager gm = null;
			for (int i = 0; i < currentContent.length; i++) {
				ISelectedContent content = currentContent[i];
				DownloadManager dm = content.getDownloadManager();
				if ( dm == null ){
					if (!canDownload && content.getDownloadInfo() != null) {
						canDownload = true;
					}
					continue;
				}
				if ( gm == null ){
					gm = dm.getGlobalManager();
					
					canCheckExist = true;	// first time through with a dm, setup var
				}

				int state = dm.getState();
				
				canCheckExist &= (	state == DownloadManager.STATE_ERROR ||
									state == DownloadManager.STATE_STOPPED || 
									state == DownloadManager.STATE_QUEUED );
				
				int fileIndex = content.getFileIndex();
				if (fileIndex == -1) {
					if (!canMoveUp && gm.isMoveableUp(dm)) {
						canMoveUp = true;
					}
					if (!canMoveDown && gm.isMoveableDown(dm)) {
						canMoveDown = true;
					}

					hasDM = true;
					if (!canStart && ManagerUtils.isStartable(dm)) {
						canStart = true;
					}
					if (!canStop && ManagerUtils.isStopable(dm)) {
						canStop = true;
					}
				} else {
					DiskManagerFileInfoSet fileInfos = dm.getDiskManagerFileInfoSet();
					if (fileIndex < fileInfos.nbFiles()) {
						DiskManagerFileInfo fileInfo = fileInfos.getFiles()[fileIndex];
						if (!canStart && (fileInfo.isSkipped())) {
							canStart = true;
						}

						if (!canStop && !fileInfo.isSkipped()) {
							canStop = true;
						}

						if (!canRemoveFileInfo && !fileInfo.isSkipped()) {
							int storageType = fileInfo.getStorageType();
							if (storageType == DiskManagerFileInfo.ST_LINEAR
									|| storageType == DiskManagerFileInfo.ST_COMPACT) {
								canRemoveFileInfo = true;
							}
						}

						if (!canRunFileInfo
								&& fileInfo.getAccessMode() == DiskManagerFileInfo.READ
								&& fileInfo.getDownloaded() == fileInfo.getLength()
								&& FileUtil.existsWithTimeout( fileInfo.getFile(true))) {
							canRunFileInfo = true;
						}
					}
				}
				
				canRecheck = canRecheck || dm.canForceRecheck();
			}

			boolean canRemove = hasDM || canRemoveFileInfo;
			
			mapNewToolbarStates.put("remove", canRemove ? UIToolBarItem.STATE_ENABLED : 0);

			mapNewToolbarStates.put("download", canDownload ? UIToolBarItem.STATE_ENABLED : 0);

			// actually we roll the dm indexes when > 1 selected and we get
			// to the top/bottom, so only enforce this for single selection :)

			if (currentContent.length == 1) {
				mapNewToolbarStates.put("up", canMoveUp ? UIToolBarItem.STATE_ENABLED : 0);
				mapNewToolbarStates.put("down", canMoveDown	? UIToolBarItem.STATE_ENABLED : 0);
			}			
		}

		boolean canRun = has1Selection
				&& ((hasDM && !canRunFileInfo) || (!hasDM && canRunFileInfo));
		if (canRun) {
			ISelectedContent content = currentContent[0];
			DownloadManager dm = content.getDownloadManager();

			if (dm == null) {
				canRun = false;
			} else {
				TOTorrent torrent = dm.getTorrent();

				if (torrent == null) {

					canRun = false;

				} else if (!dm.getAssumedComplete() && torrent.isSimpleTorrent()) {

					canRun = false;
					/*
									} else if (PlatformTorrentUtils.useEMP(torrent)
											&& PlatformTorrentUtils.embeddedPlayerAvail()
											&& PlayUtils.canProgressiveOrIsComplete(torrent)) {
										// play button enabled and not UMP.. don't need launch

										canRun = false;

									}
									*/
				}
			}
		}
		mapNewToolbarStates.put("run", canRun ? UIToolBarItem.STATE_ENABLED : 0);

		mapNewToolbarStates.put("start", canStart ? UIToolBarItem.STATE_ENABLED : 0);
		mapNewToolbarStates.put("stop", canStop ? UIToolBarItem.STATE_ENABLED : 0);
		mapNewToolbarStates.put("startstop",
				canStart || canStop ? UIToolBarItem.STATE_ENABLED : 0);

		for (int i = 0; i < itemsNeedingRealDMSelection.length; i++) {
			String itemID = itemsNeedingRealDMSelection[i];
			if (!mapNewToolbarStates.containsKey(itemID)) {
				mapNewToolbarStates.put(itemID, hasSelection && hasDM && hasRealDM
						? UIToolBarItem.STATE_ENABLED : 0);
			}
		}
		for (int i = 0; i < itemsRequiring1DMSelection.length; i++) {
			String itemID = itemsRequiring1DMSelection[i];
			if (!mapNewToolbarStates.containsKey(itemID)) {
				mapNewToolbarStates.put(itemID, has1Selection && hasDM
						? UIToolBarItem.STATE_ENABLED : 0);
			}
		}

		for (int i = 0; i < itemsRequiring1DMwithHash.length; i++) {
			String itemID = itemsRequiring1DMwithHash[i];
			if (!mapNewToolbarStates.containsKey(itemID)) {
				mapNewToolbarStates.put(itemID, hasDM ? UIToolBarItem.STATE_ENABLED : 0);
			}
		}

		mapNewToolbarStates.put(
				"download",
				has1Selection
						&& (!(currentContent[0] instanceof ISelectedVuzeFileContent))
						&& currentContent[0].getDownloadManager() == null
						&& (currentContent[0].getHash() != null || currentContent[0].getDownloadInfo() != null)
						? UIToolBarItem.STATE_ENABLED : 0);

		if (tv != null) {
			TableColumn tc = tv.getTableColumn(RankItem.COLUMN_ID);
			if (tc != null && !tc.isVisible()) {
				mapNewToolbarStates.put("up", 0L);
				mapNewToolbarStates.put("down", 0L);
			}
		}

		mapNewToolbarStates.put( TU_ITEM_RECHECK, canRecheck ? UIToolBarItem.STATE_ENABLED : 0);
		mapNewToolbarStates.put( TU_ITEM_CHECK_FILES, canCheckExist ? UIToolBarItem.STATE_ENABLED : 0);
		
		boolean ss = COConfigurationManager.getBooleanParameter( "Show Side Bar" );
		
		mapNewToolbarStates.put( TU_ITEM_SHOW_SIDEBAR, UIToolBarItem.STATE_ENABLED | (ss?UIToolBarItem.STATE_DOWN:0));

		mapNewToolbarStates.put( BF_ITEM_BACK, tv!=null&&tv.canMoveBack() ? UIToolBarItem.STATE_ENABLED : 0);
		mapNewToolbarStates.put( BF_ITEM_FORWARD, tv!=null&&tv.canMoveForward() ? UIToolBarItem.STATE_ENABLED : 0);

		return mapNewToolbarStates;
	}

	public static void removeDownloads(DownloadManager[] dms,
			AERunnable deleteFailed) {
		removeDownloads(dms, deleteFailed, false);
	}

	public static void removeDownloads(final DownloadManager[] dms,
			final AERunnable deleteFailed, final boolean forcePrompt) {

		TorrentUtils.runTorrentDelete(
			dms,
			new Runnable()
			{
				@Override
				public void
				run()
				{
					removeDownloadsSupport( dms, deleteFailed, forcePrompt );
				}
			});
	}

	private static void removeDownloadsSupport(final DownloadManager[] dms,
			final AERunnable deleteFailed, final boolean forcePrompt) {
		if (dms == null) {
			return;
		}

		// confusing code:
		// for loop goes through erasing published and low noise torrents until
		// it reaches a normal one.  We then prompt the user, and stop the loop.
		// When the user finally chooses an option, we act on it.  If the user
		// chose to act on all, we do immediately all and quit.
		// If the user chose an action just for the one torrent, we do that action,
		// remove that item from the array (by nulling it), and then call
		// removeDownloads again so we can prompt again (or erase more published/low noise torrents)

		boolean can_archive = false;

		for (int i = 0; i < dms.length; i++) {
			DownloadManager dm = dms[i];
			if (dm == null) {
				continue;
			}
			if ( PluginCoreUtils.wrap( dm ).canStubbify()){
				can_archive = true;
			}
		}
		for (int i = 0; i < dms.length; i++) {
			DownloadManager dm = dms[i];
			if (dm == null) {
				continue;
			}

			boolean deleteTorrent = COConfigurationManager.getBooleanParameter(ConfigKeys.File.BCFG_DEF_DELETETORRENT);

			int confirm = COConfigurationManager.getIntParameter("tb.confirm.delete.content");
			boolean doPrompt = confirm == 0 | forcePrompt;

			if (doPrompt) {
				String title = MessageText.getString("deletedata.title");
				String text = MessageText.getString("v3.deleteContent.message",
						new String[] {
							dm.getDisplayName()
						});

				if ( can_archive ){
					text += "\n\n" + MessageText.getString("v3.deleteContent.or.archive" );
				}

				String[] buttons;

				int defaultButtonPos;
				buttons = new String[] {
					MessageText.getString("Button.cancel"),
					MessageText.getString("Button.deleteContent.fromComputer"),
					MessageText.getString("Button.deleteContent.fromLibrary"),
				};
				/*
				int[] buttonVals = new int[] {
					SWT.CANCEL,
					1,
					2
				};
				*/
				defaultButtonPos = 2;

				final MessageBoxShell mb = new MessageBoxShell(title, text, buttons,
						defaultButtonPos);
				int numLeft = (dms.length - i);
				if (numLeft > 1) {
					mb.setRemember(
							"na", 
							COConfigurationManager.getBooleanParameter( ConfigKeys.File.BCFG_DEF_DELETEALLSELECTED), 
							MessageText.getString(
								"v3.deleteContent.applyToAll", new String[] {
									"" + numLeft
							}));
					
						// never store remember state
					
					mb.setRememberOnlyIfButton(-3);
				}
				mb.setRelatedObject(dm);
				mb.setLeftImage("image.trash");
				mb.addCheckBox("deletecontent.also.deletetorrent", 2, deleteTorrent);

				final int index = i;

				DownloadManager[] current_dms = dms.clone();
				
				TorrentUtils.startTorrentDelete( current_dms );

				final boolean[] endDone = { false };

				try{
					mb.open(new UserPrompterResultListener() {

						@Override
						public void prompterClosed(int result) {
							try{

								ImageLoader.getInstance().releaseImage("image.trash");

								removeDownloadsPrompterClosed(dms, index, deleteFailed, result,
										mb.isRemembered(), mb.getCheckBoxEnabled());

							}finally{

								synchronized( endDone ){

									if ( !endDone[0] ){

										TorrentUtils.endTorrentDelete( current_dms );

										endDone[0] = true;
									}
								}
							}
						}
					});
				}catch( Throwable e ){

					Debug.out(e );

					synchronized( endDone ){

						if ( !endDone[0] ){

							TorrentUtils.endTorrentDelete( dms );

							endDone[0] = true;
						}
					}
				}
				return;
			} else {
				boolean deleteData = confirm == 1;
				removeDownloadsPrompterClosed(dms, i, deleteFailed, deleteData ? 1 : 2,
						true, deleteTorrent);
			}
		}
	}

	private static void removeDownloadsPrompterClosed(final DownloadManager[] dms,
			final int index, final AERunnable deleteFailed, final int result, final boolean doAll,
			final boolean deleteTorrent) {

		TorrentUtils.runTorrentDelete(
				dms,
				new Runnable()
				{
					@Override
					public void
					run()
					{
						removeDownloadsPrompterClosedSupport( dms, index, deleteFailed, result, doAll, deleteTorrent );
					}
				});
	}

	private static void removeDownloadsPrompterClosedSupport(DownloadManager[] dms,
			int index, AERunnable deleteFailed, int result, boolean doAll,
			boolean deleteTorrent) {
		if (result == -1) {
			// user pressed ESC (as opposed to clicked Cancel), cancel whole
			// list
			return;
		}
		if (doAll) {
			if (result == 1 || result == 2) {

				for (int i = index; i < dms.length; i++) {
					DownloadManager dm = dms[i];
					boolean deleteData = result == 2 ? false
							: !dm.getDownloadState().getFlag(
									Download.FLAG_DO_NOT_DELETE_DATA_ON_REMOVE);
					ManagerUtils.asyncStopDelete(dm, DownloadManager.STATE_STOPPED,
							deleteTorrent, deleteData, deleteFailed);
				}
			} //else cancel
		} else { // not remembered
			if (result == 1 || result == 2) {
				DownloadManager dm = dms[index];
				boolean deleteData = result == 2 ? false
						: !dm.getDownloadState().getFlag(
								Download.FLAG_DO_NOT_DELETE_DATA_ON_REMOVE);

				ManagerUtils.asyncStopDelete(dm, DownloadManager.STATE_STOPPED,
						deleteTorrent, deleteData, null);
			}
			// remove the one we just did and go through loop again
			dms[index] = null;
			if (index != dms.length - 1) {
				removeDownloads(dms, deleteFailed, true);
			}
		}
	}


	public static void
	removeDownloadStubs(
		final DownloadStubEx[] 	dms,
		final AERunnable 		deleteFailed,
		final boolean 			forcePrompt)
	{
		if ( dms == null ){

			return;
		}

		for ( int i = 0; i < dms.length; i++ ){

			DownloadStubEx dm = dms[i];

			boolean deleteTorrent = COConfigurationManager.getBooleanParameter(ConfigKeys.File.BCFG_DEF_DELETETORRENT);

			int confirm = COConfigurationManager.getIntParameter("tb.confirm.delete.content");
			boolean doPrompt = confirm == 0 | forcePrompt;

			if (doPrompt) {
				String title = MessageText.getString("deletedata.title");
				String text = MessageText.getString("v3.deleteContent.message",
						new String[] {
							dm.getName()
						});

				String[] buttons;

				int defaultButtonPos;
				buttons = new String[] {
					MessageText.getString("Button.cancel"),
					MessageText.getString("Button.deleteContent.fromComputer"),
					MessageText.getString("Button.deleteContent.fromLibrary"),
				};
				/*
				int[] buttonVals = new int[] {
					SWT.CANCEL,
					1,
					2
				};
				*/
				defaultButtonPos = 2;

				final MessageBoxShell mb = new MessageBoxShell(title, text, buttons,
						defaultButtonPos);
				int numLeft = (dms.length - i);
				if (numLeft > 1) {
					mb.setRemember(
						"na", 
						COConfigurationManager.getBooleanParameter( ConfigKeys.File.BCFG_DEF_DELETEALLSELECTED),
						MessageText.getString(
							"v3.deleteContent.applyToAll", new String[] {
								"" + numLeft
							}));
					// never store remember state
					mb.setRememberOnlyIfButton(-3);
				}
				mb.setRelatedObject(dm);
				mb.setLeftImage("image.trash");
				mb.addCheckBox("deletecontent.also.deletetorrent", 2, deleteTorrent);

				final int index = i;

				mb.open(new UserPrompterResultListener() {

					@Override
					public void prompterClosed(int result) {
						ImageLoader.getInstance().releaseImage("image.trash");

						removeDownloadStubsPrompterClosed(dms, index, deleteFailed, result,
								mb.isRemembered(), mb.getCheckBoxEnabled());
					}
				});
				return;
			} else {
				boolean deleteData = confirm == 1;
				removeDownloadStubsPrompterClosed(dms, i, deleteFailed, deleteData ? 1 : 2,
						true, deleteTorrent);
			}
		}
	}

	private static void removeDownloadStubsPrompterClosed(DownloadStubEx[] dms,
			int index, AERunnable deleteFailed, int result, boolean doAll,
			boolean deleteTorrent) {
		if (result == -1) {
			// user pressed ESC (as opposed to clicked Cancel), cancel whole
			// list
			return;
		}
		if (doAll) {
			if (result == 1 || result == 2) {

				for (int i = index; i < dms.length; i++) {
					DownloadStubEx dm = dms[i];
					boolean deleteData = result == 2 ? false
							: !false; // dm.getDownloadState().getFlag(Download.FLAG_DO_NOT_DELETE_DATA_ON_REMOVE);
					//ManagerUtils.asyncStopDelete(dm, DownloadManager.STATE_STOPPED,	deleteTorrent, deleteData, deleteFailed);
					try{
						dm.remove(deleteTorrent, deleteData);
					}catch( Throwable e ){
						if ( deleteFailed != null ){
							deleteFailed.runSupport();
						}
					}
				}
			} //else cancel
		} else { // not remembered
			if (result == 1 || result == 2) {
				DownloadStubEx dm = dms[index];
				boolean deleteData = result == 2 ? false
						: !false; // dm.getDownloadState().getFlag(Download.FLAG_DO_NOT_DELETE_DATA_ON_REMOVE);
				//ManagerUtils.asyncStopDelete(dm, DownloadManager.STATE_STOPPED,	deleteTorrent, deleteData, null);
				try{
					dm.remove(deleteTorrent, deleteData);

				}catch( Throwable e ){
					// no delete failed logic here apparently...
				}
			}
			// remove the one we just did and go through loop again
			dms[index] = null;
			if (index != dms.length - 1) {
				removeDownloadStubs(dms, deleteFailed, true);
			}
		}
	}
	
	
	public static boolean
	isForceStartVisible(
		DownloadManager[]	dms )
	{
		int userMode = COConfigurationManager.getIntParameter("User Mode");
		
		if ( userMode > 0 ){
			
			return( true );
		}
		
		if ( COConfigurationManager.getBooleanParameter( "Always Show Force Start", false )){
			
			return( true );
		}
		
		for ( DownloadManager dm: dms ){
		
			TOTorrent torrent = dm.getTorrent();
			
			if ( torrent != null && torrent.getPrivate()){
				
					// some private trackers have seed time limits and to make things simple for
					// non-advanced users to keep their torrents seeding we make force start
					// visible regardless of mode
				
				COConfigurationManager.setParameter( "Always Show Force Start", true );
					
				return( true );
			}
		}
		
		return( false );
	}
}
