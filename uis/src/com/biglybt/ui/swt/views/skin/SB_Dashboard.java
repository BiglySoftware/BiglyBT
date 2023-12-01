/* *
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
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

import java.util.List;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolder2Adapter;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoListener;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoManager;
import com.biglybt.ui.mdi.*;
import com.biglybt.ui.skin.SkinConstants;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.mdi.BaseMdiEntry;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.shells.PopOutManager;
import com.biglybt.ui.swt.shells.main.MainMDISetup;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinFactory;
import com.biglybt.ui.swt.skin.SWTSkinObjectContainer;
import com.biglybt.ui.swt.skin.SWTSkinUtils;
import com.biglybt.ui.swt.views.IViewAlwaysInitialize;
import com.biglybt.ui.swt.views.ViewManagerSWT;
import com.biglybt.ui.swt.views.skin.sidebar.SideBar;
import com.biglybt.ui.swt.widgets.TabFolderRenderer;
import com.biglybt.ui.swt.widgets.TabFolderRenderer.Adapter;
import com.biglybt.ui.swt.widgets.TabFolderRenderer.TabbedEntry;
import com.biglybt.util.MapUtils;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.menus.MenuManager;


public class SB_Dashboard
{
	private static final String[][] examples = {
			// examples are bencoded - grab from stdout when using right-click "export to clipboard" (actual export is json)
			
		{ "dashboard.example.1", "d5:itemsld4:_uidi173e12:control_typei0e2:id7:Library3:mdi7:sidebar9:parent_id16:header.transfers7:skin_id25:com.biglybt.ui.skin.skin38:skin_ref7:library5:title7:Library8:title_id15:sidebar.Libraryed4:_uidi175e12:control_typei0e11:data_source17:TransferStatsView14:event_listenerd4:name43:com.biglybt.ui.swt.views.stats.ActivityViewe2:id9:SpeedView3:mdi6:tabbed7:skin_id25:com.biglybt.ui.skin.skin35:title8:Activity8:title_id20:SpeedView.title.fulled4:_uidi177e12:control_typei0e14:event_listenerd4:name56:com.aelitis.azureus.plugins.view3d.Plugin3D$ViewListener9:plugin_id6:3dview11:plugin_name7:3D Viewe2:id23:view3d.most.active.name3:mdi7:sidebar9:parent_id14:header.plugins7:skin_id25:com.biglybt.ui.skin.skin35:title21:3D View (Most Active)8:title_id23:view3d.most.active.nameee6:layout35:173,173,177;173,173,177;175,175,1758:use_tabsi0e7:weights15:686,309;678,321e" },
 		{ "dashboard.example.2", "d5:itemsld4:_uidi178e12:control_typei0e2:id7:Library3:mdi7:sidebar9:parent_id16:header.transfers7:skin_id25:com.biglybt.ui.skin.skin38:skin_ref7:library5:title7:Library8:title_id15:sidebar.Libraryed4:_uidi181e12:control_typei0e11:data_sourced6:exportd4:anoni1e4:h_cmi1e5:h_dlnl3:I2Pe2:id16:UHQOFCLPP5YEIFRO7:versioni2e2:voi1ee8:exporter50:com.biglybt.core.subs.impl.SubscriptionManagerImple14:event_listenerd4:name49:com.biglybt.ui.swt.subscriptions.SubscriptionViewe2:id111:Subscription_042F84BA33D7C69F5F0BBDF56771D065E56ECE405EF157C8B14389477F6260F62062DFBF5EC5FA2E4904AC0C361F8243923:mdi7:sidebar9:parent_id13:Subscriptions7:skin_id25:com.biglybt.ui.skin.skin35:title23:Anon - Shares: Receiveded4:_uidi182e12:control_typei0e11:data_sourced6:exportd2:dn12:Shares: Sent3:key27:Dashboard: Anonymous Shares7:network3:I2P2:vti2ee8:exporter44:com.biglybt.plugin.net.buddy.BuddyPluginBetae14:event_listenerd4:name41:com.biglybt.plugin.net.buddy.swt.ChatViewe2:id53:Chat_I2P:IRQXG2DCN5QXEZB2EBAW433OPFWW65LTEBJWQYLSMVZQ3:mdi7:sidebar9:parent_id12:ChatOverview7:skin_id25:com.biglybt.ui.skin.skin35:title19:Anon - Shares: Sentee6:layout35:178,182,182;178,181,181;178,181,1818:use_tabsi0e7:weights15:713,284;200,200e" },
		{ "dashboard.example.3", "d5:itemsld4:_uidi183e12:control_typei0e2:id7:Library3:mdi7:sidebar9:parent_id16:header.transfers7:skin_id25:com.biglybt.ui.skin.skin38:skin_ref7:library5:title7:Library8:title_id15:sidebar.Libraryed4:_uidi184e12:control_typei0e14:event_listenerd4:name39:com.biglybt.ui.swt.views.PeersSuperViewe2:id12:AllPeersView3:mdi7:sidebar9:parent_id16:header.transfers7:skin_id25:com.biglybt.ui.skin.skin35:title9:All Peers8:title_id23:AllPeersView.title.fulled4:_uidi185e12:control_typei0e2:id8:Activity3:mdi7:sidebar9:parent_id11:header.vuze7:skin_id25:com.biglybt.ui.skin.skin38:skin_ref8:activity5:title13:Notifications8:title_id16:sidebar.Activityed4:_uidi192e12:control_typei0e2:id12:ChatOverview3:mdi7:sidebar9:parent_id16:header.discovery7:skin_id25:com.biglybt.ui.skin.skin38:skin_ref9:chatsview5:title13:Chat Overview8:title_id18:chats.view.headinged4:_uidi193e12:control_typei0e2:id14:RelatedContent3:mdi7:sidebar9:parent_id16:header.discovery7:skin_id25:com.biglybt.ui.skin.skin38:skin_ref7:rcmview5:title17:Swarm Discoveries8:title_id25:RelatedContent.title.fulled4:_uidi194e12:control_typei0e14:event_listenerd4:name35:com.biglybt.ui.swt.views.ConfigViewe2:id10:ConfigView3:mdi7:sidebar9:parent_id14:header.plugins7:skin_id25:com.biglybt.ui.skin.skin35:title7:Options8:title_id21:ConfigView.title.fulled4:_uidi195e12:control_typei0e14:event_listenerd4:name40:com.biglybt.ui.swt.views.stats.StatsViewe2:id9:StatsView3:mdi7:sidebar9:parent_id14:header.plugins7:skin_id25:com.biglybt.ui.skin.skin35:title10:Statistics8:title_id16:Stats.title.fullee6:layout195:183,184,185,192,193,194,195;183,184,185,192,193,194,195;183,184,185,192,193,194,195;183,184,185,192,193,194,195;183,184,185,192,193,194,195;183,184,185,192,193,194,195;183,184,185,192,193,194,1958:use_tabsi1e7:weights0:e" },
 	};
	
	private DashboardInstance	main_dashboard = new DashboardInstance();
	
	private DashboardInstance	sidebar_dashboard = new DashboardInstance( "sidebar", true );
	
	private DashboardInstance	rightbar_dashboard = new DashboardInstance( "rightbarzzzzz", true );
	
	private DashboardInstance	topbar_dashboard = new DashboardInstance( "topbar", true );
	
	private final MultipleDocumentInterfaceSWT		mdi;
	private final boolean							bigly_ui;
	
	private MdiEntry mdi_entry;
		
	public 
	SB_Dashboard(
		MultipleDocumentInterfaceSWT 	_mdi,
		boolean							_bigly_ui )
	{
		mdi			= _mdi;
		bigly_ui	= _bigly_ui;
		
		if ( bigly_ui ){
			
			main_dashboard.readConfig();
			
			if ( !COConfigurationManager.getBooleanParameter( "dashboard.init.0", false )){
				
				COConfigurationManager.setParameter( "dashboard.init.0", true );
				
				main_dashboard.startOfDay();
			}

			sidebar_dashboard.readConfig();
					
			rightbar_dashboard.readConfig();
		}
		
		topbar_dashboard.readConfig();
				
		if ( !COConfigurationManager.getBooleanParameter( "dashboard.init.1", false )){
			
			COConfigurationManager.setParameter( "dashboard.init.1", true );
			
			addTopbarStartupItems( false );
			
		}else{
			
			if ( !bigly_ui ){
				
				addTopbarStartupItems( true );
			}
		}
		
		if ( bigly_ui ){
			
			PluginInterface pi = PluginInitializer.getDefaultInterface();
			
			UIManager uim = pi.getUIManager();
			
			MenuManager menuManager = uim.getMenuManager();
			
			MenuItem menuItem;
	
			{
				menuItem = menuManager.addMenuItem("sidebar." + MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD,
						"menu.add.website");
		
				menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
		
				menuItem.addListener(new MenuItemListener() {
					@Override
					public void selected(MenuItem menu, Object target) {
						
						SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
								"enter.url", "enter.website");
		
						entryWindow.prompt(new UIInputReceiverListener() {
							@Override
							public void UIInputReceiverClosed(UIInputReceiver receiver) {
								if (!receiver.hasSubmittedInput()) {
									return;
								}
		
								String key = receiver.getSubmittedInput().trim();
								
								if ( !key.isEmpty()) {
									
									Map<String,Object>	map = new HashMap<>();
									
									map.put( "mdi", "sidebar" );
									map.put( "skin_id", "com.biglybt.ui.skin.skin3" );
									map.put( "parent_id", "header.dashboard" );
									map.put( "skin_ref", "main.generic.browse" );
									map.put( "id", "Browser: " + key );
									map.put( "title", key );		
									map.put( "data_source", key );
									map.put( "control_type", 0L );
									
									main_dashboard.addItem( map );
									
									main_dashboard.fireChanged();
								}
							}});
					}
				});
			}
			
			{
				menuItem = menuManager.addMenuItem("sidebar." + MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD,	"sep0");
				
				
				menuItem.setStyle( MenuItem.STYLE_SEPARATOR );
				
				menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
			}
			
			{
				final MenuItem menuExamples = menuManager.addMenuItem("sidebar." + MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD,	"label.examples");
		
				menuExamples.setDisposeWithUIDetach(UIInstance.UIT_SWT);
		
				menuExamples.setStyle( MenuItem.STYLE_MENU );
				
				menuExamples.addFillListener( 
					new MenuItemFillListener()
					{
						@Override
						public void
						menuWillBeShown(
							MenuItem	menu,
							Object		_target )
						{
							menu.removeAllChildItems();
							
							for ( final String[] entry: examples ){
								
								MenuItem menuItem = menuManager.addMenuItem(menuExamples, entry[0]);
						
								menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
						
								menuItem.addListener(new MenuItemListener() {
									@Override
									public void selected(MenuItem menu, Object target) {
										
										main_dashboard.importDashboard( entry[1], false );
										
										SideBar sidebar = (SideBar) SkinViewManager.getByClass(SideBar.class);
	
										if ( sidebar != null && sidebar.isVisible()) {
											
											sidebar.flipSideBarVisibility();
										}
									}
								});
							}
						}
					});
			}
			
			{
				menuItem = menuManager.addMenuItem("sidebar." + MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD,	"menu.export.to.clip");
		
				menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
		
				menuItem.addListener(new MenuItemListener() {
					@Override
					public void selected(MenuItem menu, Object target) {
						
						String data = main_dashboard.exportDashboard();
						
						Clipboard cb = new Clipboard(Utils.getDisplay());
						
						try {
						
							cb.setContents(
								  new Object[] {data },
								  new Transfer[] {TextTransfer.getInstance()});
							
						}finally {
							cb.dispose();
						}
					}
				});
			}
			
			{
				menuItem = menuManager.addMenuItem("sidebar." + MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD,	"menu.import.from.clip");
		
				menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
		
				menuItem.addListener(new MenuItemListener() {
					@Override
					public void selected(MenuItem menu, Object target) {
						
						Clipboard 		cb = new Clipboard( Utils.getDisplay());
						
						try {
							TextTransfer 	transfer = TextTransfer.getInstance();
		
							String data = (String) cb.getContents(transfer);
							
							if ( data != null && !data.isEmpty()) {
								
								main_dashboard.importDashboard( data, true );
							}
						}finally {
						
							cb.dispose();
						}
					}
				});
			}
			
			{
				menuItem = menuManager.addMenuItem("sidebar." + MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD,	"sep1");
				
				
				menuItem.setStyle( MenuItem.STYLE_SEPARATOR );
				
				menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
			}
			
			{
				MenuItem resetItem = menuManager.addMenuItem("sidebar." + MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD,
						"Button.reset");
		
				resetItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
		
				resetItem.setStyle( MenuItem.STYLE_MENU );
	
				MenuItem resetDashsboard = menuManager.addMenuItem( resetItem, "sidebar.header.dashboard");
				
				resetDashsboard.addListener(new MenuItemListener() {
					@Override
					public void selected(MenuItem menu, Object target) {
						
						main_dashboard.reset();
					}
				});
				
				MenuItem resetTopbar= menuManager.addMenuItem( resetItem, "label.topbar");
				
				resetTopbar.addListener(new MenuItemListener() {
					@Override
					public void selected(MenuItem menu, Object target) {
											
						addTopbarStartupItems( false );
					}
				});
				
				MenuItem resetSidebar= menuManager.addMenuItem( resetItem, "label.sidebar");
				
				resetSidebar.addListener(new MenuItemListener() {
					@Override
					public void selected(MenuItem menu, Object target) {
						
						sidebar_dashboard.clear();
					}
				});
				
				MenuItem resetRightbar= menuManager.addMenuItem( resetItem, "label.rightbar");
				
				resetRightbar.addListener(new MenuItemListener() {
					@Override
					public void selected(MenuItem menu, Object target) {
						
						rightbar_dashboard.clear();
					}
				});
			}
			
			{		
				menuItem = menuManager.addMenuItem("sidebar." + MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD,	"sep2");
			
				menuItem.setStyle( MenuItem.STYLE_SEPARATOR );
			
				menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
			}		
			
			sidebar_dashboard.addAndFireListener(
				new DashboardListener(){
					
					private final String	VIEW_ID = "SideBarDashboard";
							
					private boolean registered;
					
					@Override
					public void itemsChanged(){
						ViewManagerSWT vi = ViewManagerSWT.getInstance();
	
						if ( sidebar_dashboard.getItemCount() > 0 ){
							
							if ( !registered ){
								
								vi.registerView(
									UISWTInstance.VIEW_SIDEBAR_AREA,
									new UISWTViewBuilderCore(VIEW_ID,
											null, SideBarDashboard.class));
								
								registered = true;
							}
						}else{
							
							if ( registered ){
								
								vi.unregisterView( UISWTInstance.VIEW_SIDEBAR_AREA, VIEW_ID );
								
								registered = false;
							}
						}
					}
				});
			
			rightbar_dashboard.addAndFireListener(
					new DashboardListener()
					{	
						private final String	VIEW_ID = "RightBarDashboard";
					
						private boolean	first_time = true;
						
						private boolean registered;
						
						@Override
						public void 
						itemsChanged()
						{
							SWTSkin skin = SWTSkinFactory.getInstance();
	
							ViewManagerSWT vi = ViewManagerSWT.getInstance();
	
							if ( rightbar_dashboard.getItemCount() > 0 ){
								
								if ( !registered ){
									
									vi.registerView(
										UISWTInstance.VIEW_RIGHTBAR_AREA,
										new UISWTViewBuilderCore(VIEW_ID,
												null, RightBarDashboard.class));
									
									registered = true;
									
								}
									
									// only force the right-bar visible when a new entry added, not at start of day
								
								if ( !first_time ){
								
									SWTSkinUtils.setVisibility(skin, SkinConstants.VIEWID_RIGHTBAR + ".visible", SkinConstants.VIEWID_RIGHTBAR, true, true);
								}
							}else{
								
								if ( registered ){
									
									vi.unregisterView( UISWTInstance.VIEW_RIGHTBAR_AREA, VIEW_ID );
									
									registered = false;
								}
								
								SWTSkinUtils.setVisibility(skin, SkinConstants.VIEWID_RIGHTBAR + ".visible", SkinConstants.VIEWID_RIGHTBAR, false, true);
							}
							
							first_time = false;
						}
					});
		}
		
		topbar_dashboard.addAndFireListener(
				new DashboardListener()
				{	
					private final String	VIEW_ID = "TopBarDashboard";
				
					private boolean	first_time = true;
					
					private boolean registered;
					
					@Override
					public void 
					itemsChanged()
					{
						SWTSkin skin = SWTSkinFactory.getInstance();

						ViewManagerSWT vi = ViewManagerSWT.getInstance();

						if ( topbar_dashboard.getItemCount() > 0 ){
							
							if ( !registered ){
								
								vi.registerView(
									UISWTInstance.VIEW_TOPBAR,
									new UISWTViewBuilderCore(VIEW_ID,
											null, TopBarDashboard.class));
								
								registered = true;
								
							}
								
								// only force the right-bar visible when a new entry added, not at start of day
							
							if ( !first_time ){
							
								SWTSkinUtils.setVisibility(skin, SkinConstants.VIEWID_PLUGINBAR + ".visible", SkinConstants.VIEWID_PLUGINBAR, true, true);
							}
						}else{
							
							if ( registered ){
								
								vi.unregisterView( UISWTInstance.VIEW_TOPBAR, VIEW_ID );
								
								registered = false;
							}
							
							SWTSkinUtils.setVisibility(skin, SkinConstants.VIEWID_PLUGINBAR + ".visible", SkinConstants.VIEWID_PLUGINBAR, false, true);
						}
						
						first_time = false;
					}
				});
		
		ViewTitleInfoManager.addListener(
			new ViewTitleInfoListener(){
				
				@Override
				public void 
				viewTitleInfoRefresh(
					ViewTitleInfo titleInfo)
				{
					if ( bigly_ui ){
					
						main_dashboard.refresh( titleInfo );
					
						sidebar_dashboard.refresh( titleInfo );
					
						rightbar_dashboard.refresh( titleInfo );
					}
					
					topbar_dashboard.refresh( titleInfo );
				}
			});
		
		MessageText.addListener((l1,l2)->{
			if ( bigly_ui ){
				
				main_dashboard.updateLocale();
			
				sidebar_dashboard.updateLocale();
			
				rightbar_dashboard.updateLocale();
			}
			
			topbar_dashboard.updateLocale();
		});
	}

	public void
	addItem(
		BaseMdiEntry		entry )
	{
		Map<String,Object> map = entry.exportStandAlone();
				
		main_dashboard.addItem( map );
		
		main_dashboard.fireChanged();
	}
	
	public void
	build(
		Composite		comp )
	{
		main_dashboard.build( comp );
	}
	
	public void
	addItemToSidebar(
		BaseMdiEntry		entry )
	{
		Map<String,Object> map = entry.exportStandAlone();
		
		sidebar_dashboard.addItem( map );
		
		sidebar_dashboard.fireChanged();
	}

	public DashboardInstance
	getSidebarDashboard()
	{
		return( sidebar_dashboard );
	}
		
	public void
	addItemToRightbar(
		BaseMdiEntry		entry )
	{
		Map<String,Object> map = entry.exportStandAlone();
				
		rightbar_dashboard.addItem( map );
		
		rightbar_dashboard.fireChanged();
	}

	public DashboardInstance
	getRightbarDashboard()
	{
		return( rightbar_dashboard );
	}
	
	public void
	addItemToTopbar(
		BaseMdiEntry		entry )
	{
		Map<String,Object> map = entry.exportStandAlone();
				
		topbar_dashboard.addItem( map );
		
		topbar_dashboard.fireChanged();
	}

	public DashboardInstance
	getTopbarDashboard()
	{
		return( topbar_dashboard );
	}
	
	private void
	addTopbarStartupItems(
		boolean only_if_all_invisible )
	{
		if ( only_if_all_invisible ){
			
			int[][] layout = topbar_dashboard.getDashboardLayout();
			
			for ( DashboardInstance.DashboardItem item: topbar_dashboard.getItems()){
				
				if ( topbar_dashboard.isUIDInLayout( layout, item.getUID())){
					
					return;
				}
			}
		}
		
		topbar_dashboard.clear();

		String[][] data = {
				{ "!TableColumn.header.downspeed!", "com.biglybt.ui.swt.views.ViewDownSpeedGraph" },
				{ "!TableColumn.header.upspeed!", "com.biglybt.ui.swt.views.ViewUpSpeedGraph" },
				{ "!label.quick.config!", "com.biglybt.ui.swt.views.ViewQuickConfig" },
				{ "!label.quick.net.info!", "com.biglybt.ui.swt.views.ViewQuickNetInfo" },
				{ "!label.quick.notifications!", "com.biglybt.ui.swt.views.ViewQuickNotifications" }
		};
		
		List<Map>	items = new ArrayList<>();

		for ( String[] entry: data ){
		
			Map<String,Object>	map = new HashMap<>();
		
			map.put( "skin_id", "com.biglybt.ui.skin.skin3" );
			map.put( "title", entry[0] );				
			map.put( "control_type", 0L );
			
			Map<String,Object> listener = new HashMap<>();
			
			listener.put( "name", entry[1] );
			
			map.put( "event_listener", listener );
			
			items.add( map );
		}
				
		topbar_dashboard.addItems( items );
		
		topbar_dashboard.fireChanged();
	}
	
	public void
	dispose()
	{
	}
	
	public MdiEntry
	setupMDIEntry()
	{
		ViewTitleInfo title_info = new ViewTitleInfo() {
			
			@Override
			public Object getTitleInfoProperty(int propertyID) {
				if (propertyID == TITLE_INDICATOR_TEXT) {
					return( String.valueOf( main_dashboard.getItemCount()));
				}

				if (propertyID == TITLE_INDICATOR_TEXT_TOOLTIP) {
					
					return( null );
				}

				return null;
			}
		};
		
		mdi_entry = mdi.createEntryFromSkinRef(
				"", MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD,
				"dashboard", "{sidebar.header.dashboard}",
				title_info, null, false, null);


		mdi_entry.setImageLeftID("image.sidebar.dashboard");

		MdiEntryVitalityImage cog = mdi_entry.addVitalityImage("image.sidebar.cog");
		
		cog.setToolTip( MessageText.getString( "configure.dashboard" ));

		final long[] cog_click_time = { 0 };
		
		cog.addListener(new MdiEntryVitalityImageListener() {
			@Override
			public void mdiEntryVitalityImage_clicked(int x, int y) {
								
				synchronized( cog_click_time ){
				
					cog_click_time[0]	= SystemTime.getMonotonousTime();

					main_dashboard.showConfig();
				}
			}});
		
		cog.setVisible(true);
		
		mdi.addListener(new MdiListener() {

			@Override
			public void mdiEntrySelected(MdiEntry newEntry,
			                             MdiEntry oldEntry) {
		
				if (mdi_entry == newEntry && mdi_entry == oldEntry) { 
						
					SimpleTimer.addEvent(
						"changed",
						SystemTime.getOffsetTime( 250 ),
						new TimerEventPerformer(){
							
							@Override
							public void perform(TimerEvent event){
							
								synchronized( cog_click_time ){
								
									if ( SystemTime.getMonotonousTime() - cog_click_time[0] < 250 ){
										
										return;
									}
								}
								
								main_dashboard.fireChanged();

							}
						});
				}
			}
			public void mdiDisposed(MultipleDocumentInterface mdi) {}
			});
		
		return( mdi_entry );
	}
	
	public void
	addListener(
		DashboardListener	l )
	{
		main_dashboard.addListener(l);
	}
	
	public void
	removeListener(
		DashboardListener	l )
	{
		main_dashboard.removeListener(l);
	}
	
	public interface
	DashboardListener
	{
		public void
		itemsChanged();
	}
	
	private class
	DashboardInstance
	{
		private final String	config_prefix;
		private final boolean	use_tabs_default;
		
		Composite main_composite;
		
		private CopyOnWriteList<DashboardItem>		items = new CopyOnWriteList<>();
		
		private boolean	config_dirty;
		
		private CopyOnWriteList<DashboardListener>	listeners = new CopyOnWriteList<>();
		
		private volatile boolean config_active;
		
		private
		DashboardInstance()
		{
			config_prefix = "dashboard";
			
			use_tabs_default = false;
		}
		
		private
		DashboardInstance(
			String		_id,
			boolean		_use_tabs_default )
		{
			config_prefix = "dashboard." + _id;
			
			use_tabs_default = _use_tabs_default;
		}
		
		private void
		startOfDay()
		{
			if ( items.isEmpty()){
				
				addStartupItem();
				
				writeConfig();
			}
		}

		private void
		reset()
		{
			synchronized( items ) {
				
				items.clear();
				
				COConfigurationManager.setParameter( config_prefix + ".layout", "" );
				 
				addStartupItem();
			}
			
			fireChanged();
		}
		
		private void
		addListener(
			DashboardListener	l )
		{
			listeners.add( l );
		}		
		
		private void
		addAndFireListener(
			DashboardListener	l )
		{
			listeners.add( l );
			
			l.itemsChanged();
		}
		
		private void
		removeListener(
			DashboardListener	l )
		{
			listeners.remove( l );
		}
		
		private void
		readConfig()
		{
			synchronized( items ){
				
				Map	config = COConfigurationManager.getMapParameter( config_prefix + ".config", new HashMap<>());
				
				config = BDecoder.decodeStrings( BEncoder.cloneMap( config ));
				
				List<Map> item_list = (List<Map>)config.get( "items" );
				
				if ( item_list != null ) {
					
					// addItems( item_list );
					// allow temporary removal of uids
					
					for ( Map map: item_list ) {

						// Hack to migrate dashboard wiki URL
						if ("https://github.com/BiglySoftware/BiglyBT/wiki/Dashboard".equals(
								MapUtils.getMapString(map, "data_source", ""))) {
							map.put("data_source", Constants.URL_WIKI + "w/Dashboard");
						}

						DashboardItem item = new DashboardItem( map );
						
						items.add( item );
					}
				}
				
				if ( config_dirty ) {
					
					writeConfig();
				}
			}
		}
		
		private void
		configDirty()
		{
			synchronized( items ){
				
				config_dirty = true;
			}
		}
		
		private void
		writeConfig()
		{
			synchronized( items ){
				
				config_dirty = false;
				
				Map config = new HashMap();
				
				List item_list = new ArrayList( items.size());
				
				config.put( "items", item_list );
				
				for ( DashboardItem item: items ){
					
					item_list.add( BEncoder.clone( item.getState()));
				}
				
				COConfigurationManager.setParameter( config_prefix + ".config", config );
				
				COConfigurationManager.setDirty();
			}
		}
		

		private String
		exportDashboard()
		{
			synchronized( items ){
			
				Map	map = new HashMap();
			
				List<Map>	l_items = new ArrayList<>();
			
				for ( DashboardItem item: items ) {
					
					l_items.add( item.getState());
				}
				
				map.put( "items", l_items );
				
				String layout_str = COConfigurationManager.getStringParameter( config_prefix + ".layout" );
			
				layout_str = encodeIAA( compactLayout( decodeIAA( layout_str), items.size()));
				
				map.put( "layout", layout_str );
				
				map.put( "weights", COConfigurationManager.getStringParameter( config_prefix + ".sash.weights" ));
				
				map.put( "use_tabs", new Long( getUseTabs()?1:0 ));
				
				try {
					System.out.println( new String( BEncoder.encode( map ), "UTF-8" ));
					
				}catch( Throwable e ) {
					
				}
				
				return( BEncoder.encodeToJSON( map ));
			}
		}
		
		private void
		importDashboard(
			String	data,
			boolean	is_json )
		{
			synchronized( items ){

				try {
					Map map = BDecoder.decodeStrings( is_json?BDecoder.decodeFromJSON( data ):BDecoder.decode( data.getBytes( "UTF-8")));
					
					List<Map>	l_items = (List<Map>)map.get( "items" );
					
					List<DashboardItem>	new_items = new ArrayList<>();
					
					for ( Map m: l_items ) {
						
						new_items.add( new DashboardItem(m));
					}
					
					String	layout = (String)map.get( "layout" );
					
					decodeIAA( layout );
					
					String weights = (String)map.get( "weights" );
					
					decodeIAA( weights );
					
					items.clear();
					
					items.addAll( new_items );
					
					COConfigurationManager.setParameter( config_prefix + ".layout", layout );
					COConfigurationManager.setParameter( config_prefix + ".sash.weights", weights );
					
					Number l_use_tabs = (Number)map.get( "use_tabs" );
					
					boolean use_tabs = l_use_tabs != null && l_use_tabs.intValue() != 0; 
					
					setUseTabs( use_tabs );
					
				}catch( Throwable e ) {
					
					Debug.out( e );
				}
			}
			
			fireChanged();
		}
		
		private void
		addStartupItem()
		{
			String	starting_url = Constants.URL_WIKI + "w/Dashboard";
			
			Map<String,Object>	map = new HashMap<>();
			
			map.put( "mdi", "sidebar" );
			map.put( "skin_id", "com.biglybt.ui.skin.skin3" );
			map.put( "parent_id", "header.dashboard" );
			map.put( "skin_ref", "main.generic.browse" );
			map.put( "id", "Browser: Dashboard" );
			map.put( "title", "Wiki: Dashboard" );		
			map.put( "data_source", starting_url );
			map.put( "control_type", 0L );
			
			addItem( map );
		}
		
		private int
		getItemCount()
		{
			return( items.size());
		}

		private List<DashboardItem>
		getItems()
		{
			return( items.getList());
		}
		
		private void
		addItem(
			Map		map )
		{
			List<Map>	list = new ArrayList<>(1);
			
			list.add( map );
			
			addItems( list );
		}
		
		private void
		addItems(
			List<Map>	item_list )
		{
			synchronized( items ) {

				int[][] initial_layout = getDashboardLayout();
				
				int[][] layout = initial_layout;
				
				for ( Map map: item_list ) {
					
					DashboardItem item = new DashboardItem( map );
					
					items.add( item );
					
					layout = ensureUIDInLayout( layout, item.getUID());
				}
				
				if ( layout != initial_layout ) {
					
					setDashboardLayout( layout, items.size(), false );
				}
			}
		}
		
		private void
		refresh(
			ViewTitleInfo		info )
		{
			for ( DashboardItem item: items ){
				
				item.refresh( info );
			}
		}
		
		private void
		updateLocale()
		{
			Utils.execSWTThread(()->{
				
				if ( main_composite == null || main_composite.isDisposed()){
					
					return;
				}
				
				updateLocale( main_composite );
			});
		}
		
		private void
		updateLocale(
			Composite	comp )
		{
			if ( comp instanceof CTabFolder ){
				
				CTabItem[] items = ((CTabFolder)comp).getItems();
				
				for ( CTabItem item: items ){
					
					String title_id = (String)item.getData( "sb:itemtitleid" );
					
					if ( title_id != null ){
						
						String new_text = MessageText.getString( title_id );
						
						if ( new_text != null ){
							
							item.setText( new_text + (item.getText().endsWith( "..." )?"...":"" ));
						}
					}
				}
			}else if ( comp instanceof Group ){
				
				String title_id = (String)comp.getData( "sb:itemtitleid" );
				
				if ( title_id != null ){
					
					String new_text = MessageText.getString( title_id );
					
					if ( new_text != null ){
						
						((Group)comp).setText(new_text);
					}
				}
			}
			
			Control[] kids = comp.getChildren();
			
			for ( Control k: kids ){
				
				if ( k instanceof Composite ){
					
					updateLocale((Composite)k);
				}
			}
		}
		private void
		clear()
		{
			List<DashboardItem> copy;
			
			synchronized( items ){
			
				copy = new ArrayList<>( items.getList());
			}
			
			for ( DashboardItem item: copy ){
					
				item.remove( false );
			}
		}
		
		private boolean
		setSashWeights(
			int[][]		weights )
		{
			synchronized( items ){
				
				String str = encodeIAA( weights );
				
				String old_str = COConfigurationManager.getStringParameter( config_prefix + ".sash.weights", "" );
				
				if ( str.equals( old_str )){
					
					return( false );
				}
				
				COConfigurationManager.setParameter( config_prefix + ".sash.weights", str );
				
				return( true );
			}
		}
		
		private int[][]
		getSashWeights()
		{
			synchronized( items ){
				
				String str = COConfigurationManager.getStringParameter( config_prefix + ".sash.weights" );
				
				return( decodeIAA( str ));
			}
		}
		
		private boolean
		setDashboardLayout(
			int[][]		layout,
			int			grid_size,
			boolean		compact )
		{
			synchronized( items ){
				
				String new_str = encodeIAA( layout );
				
				String old_str = COConfigurationManager.getStringParameter( config_prefix + ".layout" );
				
				boolean same = new_str.equals( old_str );
					
				if ( compact ){
					
					new_str = encodeIAA( compactLayout( layout, grid_size ));
				}
				
				if ( !new_str.equals( old_str )){
				
					COConfigurationManager.setParameter( config_prefix + ".layout", new_str );
				}
				
				if ( same ) {
					
					return( false );
				}
				
					// leave sash weights as they are, they'll get reset if they no longer apply
				
				return( true );
			}
		}
		
		private int[][]
		getDashboardLayout()
		{
			synchronized( items ){
				
				String str = COConfigurationManager.getStringParameter( config_prefix + ".layout" );
				
				return( decodeIAA( str ));
			}
		}	
	
		private boolean
		getAddNewHorizontal()
		{
			return( COConfigurationManager.getBooleanParameter( config_prefix + ".config.addhoriz", true ));
		}
		
		private void
		setAddNewHorizontal(
			boolean		b )
		{
			COConfigurationManager.setParameter( config_prefix + ".config.addhoriz", b );
		}
		
		private boolean
		getUseTabs()
		{
			return( COConfigurationManager.getBooleanParameter( config_prefix + ".config.usetabs", use_tabs_default ));
		}
		
		private void
		setUseTabs(
			boolean		b )
		{
			COConfigurationManager.setParameter( config_prefix + ".config.usetabs", b );
		}	

		private void
		tabSelected(
			int	folder_id,
			int	tab_index )
		{
			COConfigurationManager.setParameter( config_prefix + ".tab.selection." + folder_id, tab_index );
		}
		
		private int
		getTabSelection(
			int	folder_id )
		{
			return( COConfigurationManager.getIntParameter( config_prefix + ".tab.selection." + folder_id, 0 ));
		}
		
		private void
		setupTabFolder(
			CTabFolder	tf )
		{
			tf.setUnselectedCloseVisible( false );
			
			tf.addCTabFolder2Listener(new CTabFolder2Adapter() {
				
				@Override
				public void 
				close(
					CTabFolderEvent event)
				{
					DashboardItem di = (DashboardItem)event.item.getData( "sb:dashboarditem" );
					
					if ( di != null ){
						
						di.remove( true );
					}
				}
			});
			
			TabFolderRenderer renderer = 
				new TabFolderRenderer(
					tf, 
					new Adapter()
					{
						@Override
						public TabbedEntry 
						getTabbedEntryFromTabItem(
							CTabItem item )
						{
							DashboardItem entry = (DashboardItem)item.getData("sb:tabbedentry" );
							
							if ( entry != null ){
								
								return( entry );
							}
							
							for ( DashboardItem di: items ){
								
								if ( di.item == item ){
									
									item.setData( "sb:tabbedentry", di );
									
									return( di );
								}
							}
							
							return( null );
						}
					});
		}
		
		private void
		setupTabItem(
			CTabItem			tab_item,
			List<DashboardItem>	visible_items )
		{
			tab_item.setShowClose( tab_item.getData("sb:dashboarditem") != null && bigly_ui );
			
			DashboardItem main_item = visible_items.get(0);
			
			tab_item.setText( main_item.getTitle() + (visible_items.size()>1?"...":""));

			String title_id = main_item.getTitleID();
			
			if ( title_id != null ){
				
				tab_item.setData( "sb:itemtitleid", title_id );
			}
		}
		
		private void
		refreshTabFolder(
			DashboardItem	di )
		{
			Utils.execSWTThread(()->{
				
				TabFolderRenderer renderer = (TabFolderRenderer)di.getTabbedEntryItem().getParent().getRenderer();
				
				renderer.redraw( di );
			});
		}
		
		private void
		refreshTabFolder(
			CTabFolder	tf,
			int			index )
		{
			TabFolderRenderer renderer = (TabFolderRenderer)tf.getRenderer();
			
			renderer.redraw( index );
		}
		
		private void
		fireChanged()
		{		
			if ( mdi_entry != null ) {
				
				mdi_entry.redraw();
			}
			
			for ( DashboardListener l: listeners ){
				
				try {
					l.itemsChanged();
					
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
			
			writeConfig();
		}
		
		private int
		getItemUID()
		{
			synchronized( items ){
				
				int	next = COConfigurationManager.getIntParameter( config_prefix + ".uid.next" );
				
				int t = next + 1;
				
				if ( t < 0 ){
					
					t = 0;
				}
				
				COConfigurationManager.setParameter( config_prefix + ".uid.next", t );
				
				return( next );
			}
		}
		
		private int[][]
		ensureUIDInLayout(
			int[][]		layout,
			int			uid )
		{		
			for ( int[] row: layout ) {
				for ( int i: row ) {
					if ( i == uid ) {					
						return( layout);
					}
				}
			}
		
			boolean	add_horiz = getAddNewHorizontal();;
			
			int	size 		= layout.length;
			int	new_size	= size+1;
			
			int[][]	new_layout = new int[new_size][new_size];
			
			if ( new_size == 1 ) {
				new_layout[0][0] = uid;
			}else{
				for ( int i=0;i<new_size;i++){
					
					int[]	old_row = i<size?layout[i]:null;
					int[]	new_row	= new_layout[i];
					
					for ( int j=0;j<new_size;j++){
						if ( i < size ) {
							if ( j < size ) {
								new_row[j] = old_row[j];
							}else {
								if ( add_horiz ) {
									new_row[j] = uid;
								}else {
									new_row[j] = new_row[j-1];
								}
							}
						}else{
							if ( j < size ){
								if ( add_horiz ){
									new_row[j] = layout[i-1][j];
								}else {
									new_row[j] = uid;
								}
							}else {
								new_row[j] = uid;
							}
						}
					}
				}
			}
			
			return( new_layout );
		}
		
		private boolean
		removeUIDFromLayout(
			int[][]		layout,
			int			uid )
		{
			boolean changed = false;
			
			for ( int[] row: layout ) {
				for ( int i=0;i<row.length;i++){
					if ( row[i] == uid ) {					
						row[i] = -1;
						changed = true;
					}
				}
			}
			return( changed );
		}
		
		private boolean
		isUIDInLayout(
			int[][]		layout,
			int			uid )
		{
			for ( int[] row: layout ) {
				for ( int i=0;i<row.length;i++){
					if ( row[i] == uid ) {					
						return( true );
					}
				}
			}
			return( false );
		}
		
		private int[][]
		compactLayout(
			int[][]		layout,
			int			grid_size )
		{
			return( compactLayout( layout, grid_size, 4 ));
		}
		
		private int[][]
		compactLayout(
			int[][]		layout,
			int			grid_size,
			int			min_grid_size )
		{
			int	layout_size = layout.length;
			
			if ( layout_size > grid_size && layout_size > min_grid_size) {
				
				int	row_to_remove 	= -1;
				int col_to_remove	= -1;
				
				for ( int i=0;i<layout_size;i++){
					
					int[] row 		= layout[i];
					
					if ( i < layout_size-1 ){
						
						int[] next_row 	= layout[i+1];
						
						boolean same = true;
						
						for ( int j=0;j<layout_size;j++) {
							
							if ( row[j] != next_row[j] ) {
								same = false;
								break;
							}
						}
						
						if ( same ){
							row_to_remove = i;
						}
					}
					
					boolean	all_undef = true;
					
					for (int uid: row ) {
					
						if ( uid != -1 ) {
							all_undef = false;
							break;
						}
					}
					
					if ( all_undef ) {
						row_to_remove = i;
					}
				}
			
				for ( int j=0;j<layout_size;j++){
									
					if ( j < layout_size-1 ){
											
						boolean same = true;
						
						for ( int i=0;i<layout_size;i++) {
							
							if ( layout[i][j] != layout[i][j+1] ) {
								same = false;
								break;
							}
						}
						
						if ( same ){
							col_to_remove = j;
						}
					}
					
					boolean	all_undef = true;
					
					for ( int i=0;i<layout_size;i++) {
					
						if ( layout[i][j] != -1 ) {
							all_undef = false;
							break;
						}
					}
					
					if ( all_undef ) {
						col_to_remove = j;
					}
				}
				
				
				if ( row_to_remove != -1 && col_to_remove !=  -1 ){
									
					int[][] new_layout = new int[layout_size-1][layout_size-1];
					
					for ( int i=0;i<layout_size;i++) {
						if ( i == row_to_remove) {
							continue;
						}
						for (int j=0;j<layout_size;j++) {
							if ( j == col_to_remove ) {
								continue;
							}
							int	target_i = i<row_to_remove?i:i-1;
							int	target_j = j<col_to_remove?j:j-1;
							
							new_layout[target_i][target_j] = layout[i][j];
						}
					}
					
					return( compactLayout( new_layout, grid_size, min_grid_size ));
				}
			}
			
			return( layout );
		}
		

		
		private String
		encodeIAA(
			int[][]	data )
		{
			String str = "";
			
			for ( int[] row: data ) {
								
				String r_str = "";
					
				for ( int n: row ) {
				
					if ( !r_str.isEmpty()){
						
						r_str += ",";
					}
					
					r_str += n;
				}
				
				if ( !str.isEmpty()){
					
					str += ";";
				}

				str += r_str;
			}
			
			return( str );
		}
		
		private int[][]
		decodeIAA(
			String	str )
		{
			if ( str.isEmpty()) {
				
				return( new int[0][0] );
			}
			
			String[] rows = str.split( ";" );
			
			int[][] layout = new int[rows.length][];
			
			int	row_num = 0;
			
			for ( String row: rows ) {
				
				String[] cells = row.split(",");
				
				int[] x = new int[cells.length];
				
				layout[row_num++] = x;
				
				for ( int i=0;i<cells.length;i++) {
					
					try {
						x[i] = Integer.parseInt( cells[i]);
						
					}catch( Throwable e ){
						
						Debug.out(e);
					}
				}
			}
			
			return( layout );
		}
				
		private int	building = 0;
		
		protected void
		build(
			Composite		dashboard_composite )
		{
			main_composite = dashboard_composite;
			
			try{
				building++;
				
				if ( dashboard_composite == null ) {
					
					return;
				}
				
				Utils.disposeComposite( dashboard_composite, false );
							
				int[][] layout = getDashboardLayout();
				
				layout = compactLayout(layout, 0, 0 );
				
				boolean use_tabs = getUseTabs();

				if ( use_tabs ){
				
						// hack to ensure that a tab folder is created even when there is only one
						// item
					
					if ( layout.length == 1 && layout[0].length == 1 ){
						
						layout = new int[][]{{layout[0][0],-1},{layout[0][0],-1}};
					}
				}
				
				Map<Integer,DashboardItem>	item_map = new HashMap<>();
		
				for ( DashboardItem item: items ){
					
					item_map.put( item.getUID(),  item );
				}
				
				for ( int[] row: layout ){
					for ( int i=0;i<row.length;i++){
						int c = row[i];
						if ( !item_map.containsKey( c )){
							row[i] = -1;
						}
					}
				}
				
				final List<SashForm>	sashes 		= new ArrayList<>();
				List<Control>			controls	= new ArrayList<>();
				
				build( item_map, null, dashboard_composite, use_tabs, sashes, controls, layout, 0, 0, layout.length, layout.length, new int[1] );
				
				int[][]	sash_weights = getSashWeights();
				
				if ( sash_weights.length == sashes.size()){
					
					for ( int i=0;i<sash_weights.length;i++) {
						
						int[]	weights = sash_weights[i];
						
						SashForm sf = sashes.get( i );
						
						int[]	sf_weights = sf.getWeights();
						
						if ( sf_weights.length == weights.length ){
							
							try {
								sf.setWeights( weights );
								
							}catch( Throwable e ){
								
									// in case something borks
								
								Debug.out( e );
							}
						}
						
						sf.setData( "sb:weights", sf_weights.length );
					}
				}else{
						// something's changed
					
					setSashWeights( new int[0][0] );
					
					for ( SashForm sf: sashes ) {
						
						int[]	weights = sf.getWeights();
						
						sf.setData( "sb:weights", weights.length );
					}
				}
				
				for ( Control c: controls ) {
					
					c.addControlListener(
						new ControlListener(){
							
							@Override
							public void controlResized(ControlEvent arg0){
									
								if ( building > 0 ) {
									
									return;
								}
								
								int[][] weights = new int[sashes.size()][];
								
								for ( int i=0; i<sashes.size();i++ ){
									
									SashForm sf = sashes.get(i);
									
									if ( sf.isDisposed()) {
										
										return;
									}
									
									weights[i] = sf.getWeights();
									
									Object	d = sf.getData( "sb:weights" );
									
									if ( d == null || ((Integer)d) != weights[i].length ){
										
											// unexpected (e.g. control closed during closedown), discard weights
										
										return;
									}
								}
								
								setSashWeights( weights );
							}
							
							@Override
							public void controlMoved(ControlEvent arg0){
								// TODO Auto-generated method stub
								
							}
						});
				}
				
				dashboard_composite.getParent().layout( true, true );

			}finally {
				
				SimpleTimer.addEvent(
					"delayer",
					SystemTime.getOffsetTime( 2500 ),
					new TimerEventPerformer(){
						
						@Override
						public void perform(TimerEvent event){
							Utils.execSWTThread(
								new Runnable(){
									
									@Override
									public void run(){
										building--;
									}
								});
						}
					});
			}
		}
		
		private boolean
		testBuild(
			List<DashboardItem>			items,
			int[][]						layout )
		{
			Map<Integer,DashboardItem>	item_map = new HashMap<>();

			for ( DashboardItem item: items ){
				
				item_map.put( item.getUID(),  item );
			}
			
			for ( int[] row: layout ){
				for ( int i=0;i<row.length;i++){
					int c = row[i];
					if ( !item_map.containsKey( c )){
						row[i] = -1;
					}
				}
			}
			
			int	before = item_map.size();
			
			build( item_map, null, null, false, null, null, layout, 0, 0, layout.length, layout.length, new int[1] );
			
				// at least one works...
			
			return( item_map.size() < before );
		}
		
		private List<DashboardItem>
		build(	
			Map<Integer,DashboardItem>	item_map,
			CTabItem					parent_tab_item,
			Composite					comp,
			boolean						use_tabs,
			List<SashForm>				sashes,
			List<Control>				controls,
			int[][]						cells,
			int							x,
			int							y,
			int							width,
			int							height,
			int[]						next_tf_id )
		{		
			List<DashboardItem> result = new ArrayList<>();
			
			//System.out.println( "Processing " + x + ", " + y + ", " + width + ", " + height );
			
			int		temp = -1;
			boolean	not_same = false;
			
			for ( int i=y;i<y+height;i++) {
				for ( int j=x;j<x+width;j++){
					int val = cells[i][j];
					if ( temp == -1 ) {
						temp = val;
					}else if ( temp != val ) {
						not_same = true;
					}
				}
			}
			
			if ( !not_same ) {
				
				// System.out.println( "all cells are " + temp );
				
				DashboardItem item = item_map.remove( temp );
				
				if ( item != null && comp != null ) {
					
					controls.add( build( parent_tab_item, comp, item, use_tabs ));
					
					result.add( item );
				}
				
				return( result );
			}
			
				// look for a way to cut into two halves without cutting through a block
			
			boolean done = false;
			
			if ( height > 1 ){
				
				List<Integer>	splits = new ArrayList<>();
				
				for ( int i=y+1;i<y+height;i++){
					
					boolean	ok = true;
					
					int[] row 		= cells[i];
					int[] prev_row	= cells[i-1];
					
					for ( int j=x;j<x+width;j++){
						
						if (prev_row[j] == row[j] && row[j] != -1 ) {
							// splitting, no good
							ok = false;
							break;
						}
					}
					
					if ( ok ){
						
						splits.add( i );
					}
				}
				
				if ( splits.size() > 0 ){
					
					//System.out.println( "horizontal at " + splits + "[" + x + "-> " + (x+width-1)+ "]" );
					
					if ( use_tabs ){
						
						CTabFolder 	tf;
						int			tf_id;
						
						if ( comp != null ){
							tf = new CTabFolder( comp, SWT.TOP );
							
							setupTabFolder( tf );
							
							tf.setLayoutData( Utils.getFilledFormData());
							
							tf_id = next_tf_id[0]++;
							
						}else {
							tf 		= null;
							tf_id	= -1;
						}
						
						int	current = y;
						
						for ( int split: splits ){
							
							CTabItem 	tab_item		= null;
							Composite 	tab_composite = null;
							if ( tf != null ){
								tab_item = new CTabItem(tf, SWT.NULL);
								tab_composite = new Composite( tf, SWT.NULL );
								tab_composite.setLayout( new FormLayout());
								tab_item.setControl( tab_composite );
								tab_composite.setLayoutData( Utils.getFilledFormData());
							}
							
							List<DashboardItem> items = build( item_map, tab_item, tab_composite, use_tabs, sashes, controls, cells, x, current, width, split - current, next_tf_id );
							
							if ( items.isEmpty()){
								if ( tab_item != null ){
									tab_item.dispose();
								}
							}else{
								if ( tab_item != null ){
									setupTabItem( tab_item, items );
								}
								result.addAll( items );
							}
							
							current = split;
						}
						
						CTabItem 	tab_item		= null;
						Composite 	tab_composite = null;
						if ( tf != null ){
							tab_item = new CTabItem(tf, SWT.NULL);
							tab_composite = new Composite( tf, SWT.NULL );
							tab_composite.setLayout( new FormLayout());
							tab_item.setControl( tab_composite );
							tab_composite.setLayoutData( Utils.getFilledFormData());
						}
						
						List<DashboardItem> items = build( item_map, tab_item, tab_composite, use_tabs, sashes, controls, cells, x, current, width, height-(current-y), next_tf_id );
											
						if ( items.isEmpty()){
							if ( tab_item != null ){
								tab_item.dispose();
							}
						}else{
							if ( tab_item != null ){
								setupTabItem( tab_item, items );
							}
							result.addAll( items );
						}
						
						if ( tf != null ){
							tf.addMenuDetectListener(
								new MenuDetectListener(){
									
									@Override
									public void menuDetected(MenuDetectEvent event){
										
										final CTabItem item = tf.getItem(
												tf.toControl( event.x, event.y ));
										
										if ( item != null ) {
										
											Menu m = item.getControl().getMenu();
											
											if ( m != null ) {
												m.setVisible( true );
											}
										}
									}
								});
							
							int sel = Math.min( tf.getItemCount()-1, getTabSelection( tf_id ));
							
							tf.setSelection( sel );		
							
							refreshTabFolder( tf, sel );
							
							tf.addSelectionListener(
								new SelectionAdapter(){
									
									@Override
									public void widgetSelected(SelectionEvent ev){
										Composite c = (Composite)tf.getSelection().getControl();
										
										Object o = c.getData( "sb:reload" );
										
										if ( o instanceof Runnable ){
											
												// not yet built yet, trigger a reload
											
											((Runnable)o).run();
											
											c.setData( "sb:reload", null);
										}
										
										refreshTabFolder( tf, sel );
										
										tabSelected( tf_id, tf.getSelectionIndex());
									}
								});
						}
					}else{
						
						SashForm sf;
						
						if ( comp != null ){
							
							sf = new SashForm( comp, SWT.VERTICAL );
						
							sashes.add( sf );
							
							sf.setLayoutData( Utils.getFilledFormData());
							
						}else{
							
							sf = null;
						}
						
						int	current = y;
						
						for ( int split: splits ){
							
							result.addAll( build( item_map, null, sf, use_tabs, sashes, controls, cells, x, current, width, split - current, next_tf_id ));
							
							current = split;
						}
						
						result.addAll(( build( item_map, null, sf, use_tabs, sashes, controls, cells, x, current, width, height-(current-y), next_tf_id )));
					}
					
					done = true;
				}
			}
			
			if ( width > 1 && !done ){
				
				List<Integer>	splits = new ArrayList<>();

				for ( int j=x+1;j<x+width;j++){
					
					boolean	ok = true;
								
					for ( int i=y;i<y+height;i++){
						
						if (cells[i][j] == cells[i][j-1] && cells[i][j] != -1){
							// splitting, no good
							ok = false;
							break;
						}
					}
					if ( ok ) {
						
						splits.add( j );
					}
				}
				
				if ( splits.size() > 0 ){
					
					//System.out.println( "vertical at " + splits + "[" + y + "-> " + (y+height-1)+ "]" );
					
					{
						/*
						int	current = x;
						for ( int split: splits ){
							
							result.addAll((build( item_map, null, sashes, controls, cells, current, y, split - current, height )));
							current = split;
						}
						result.addAll((build( item_map, null, sashes, controls, cells, current, y, width-(current-x), height )));
						*/
					}
					
					if ( use_tabs ) {
						
						CTabFolder	tf;
						int			tf_id;
						
						if ( comp != null ){
							tf = new CTabFolder( comp, SWT.TOP | SWT.NO_BACKGROUND);
							
							setupTabFolder( tf );
							
							tf.setLayoutData( Utils.getFilledFormData());
							
							tf_id = next_tf_id[0]++;

						}else {
							tf 		= null;
							tf_id	= -1;
						}
						
						int	current = x;
						
						for ( int split: splits ){
		
							CTabItem 	tab_item		= null;
							Composite 	tab_composite = null;
							if ( tf != null ){
								tab_item = new CTabItem(tf, SWT.NULL);
								tab_composite = new Composite( tf, SWT.NULL );
								tab_composite.setLayout( new FormLayout());
								tab_item.setControl( tab_composite );
								tab_composite.setLayoutData( Utils.getFilledFormData());
							}
							
							List<DashboardItem> items = build( item_map, tab_item, tab_composite, use_tabs, sashes, controls, cells, current, y, split - current, height, next_tf_id );
							if ( items.isEmpty()){
								if ( tab_item != null ){
									tab_item.dispose();
								}
							}else{
								if ( tab_item != null ){
									setupTabItem( tab_item, items );
								}
								result.addAll( items );
							}

							current = split;
						}
						
						CTabItem 	tab_item		= null;
						Composite 	tab_composite = null;
						if ( tf != null ){
							tab_item = new CTabItem(tf, SWT.NULL);
							tab_composite = new Composite( tf, SWT.NULL );
							tab_composite.setLayout( new FormLayout());
							tab_item.setControl( tab_composite );
							tab_composite.setLayoutData( Utils.getFilledFormData());
						}
						
						List<DashboardItem> items = build( item_map, tab_item, tab_composite, use_tabs, sashes, controls, cells, current, y, width-(current-x), height, next_tf_id );
						
						if ( items.isEmpty()){
							if ( tab_item != null ){
								tab_item.dispose();
							}
						}else{
							if ( tab_item != null ){
								setupTabItem( tab_item, items );
							}
							result.addAll( items );
						}
						
						if ( tf != null ){
							tf.addMenuDetectListener(
								new MenuDetectListener(){
									
									@Override
									public void menuDetected(MenuDetectEvent event){
										
										final CTabItem item = tf.getItem(
												tf.toControl( event.x, event.y ));
										
										if ( item != null ) {
										
											Menu m = item.getControl().getMenu();
											
											if ( m != null ) {
												m.setVisible( true );
											}
										}
									}
								});
							
							int sel = Math.min( tf.getItemCount()-1, getTabSelection( tf_id ));
							
							tf.setSelection( sel );
							
							refreshTabFolder( tf, sel );
							
							tf.addSelectionListener(
								new SelectionAdapter(){
									
									@Override
									public void widgetSelected(SelectionEvent ev){
										Composite c = (Composite)tf.getSelection().getControl();
										
										Object o = c.getData( "sb:reload" );
										
										if ( o instanceof Runnable ){
											
												// not built yet, trigger a reload
											
											((Runnable)o).run();
											
											c.setData( "sb:reload", null );
										}
										
										refreshTabFolder( tf, sel );
										
										tabSelected( tf_id, tf.getSelectionIndex());
									}
								});
						}
					}else{
						
						SashForm sf;
						
						if ( comp != null ){
							
							sf = new SashForm( comp, SWT.HORIZONTAL );
						
							sashes.add( sf );
							
							sf.setLayoutData( Utils.getFilledFormData());
							
						
						}else{
							
							sf = null;
						}
						int	current = x;
						
						for ( int split: splits ){
		
							result.addAll((build( item_map, null, sf, use_tabs, sashes, controls, cells, current, y, split - current, height, next_tf_id )));
							
							current = split;
						}
						
						result.addAll((build( item_map, null, sf, use_tabs, sashes, controls, cells, current, y, width-(current-x), height, next_tf_id )));
					}
				}
			}
			
			Iterator<DashboardItem> it = result.iterator();
			
			while( it.hasNext()) {
				if (it.next() == null ) {
					it.remove();
				}
			}
			
			return( result );
		}
		
		private Composite
		build(
			CTabItem			parent_tab_item,
			Composite			sf,
			DashboardItem		item,
			boolean				use_tabs )
		{			
			Composite g = use_tabs?new Composite( sf, SWT.NULL ):Utils.createSkinnedGroup( sf, SWT.NULL );
			
			g.setLayoutData( Utils.getFilledFormData());
			
			g.setLayout( new GridLayout());

			g.setData( Utils.RELAYOUT_UP_STOP_HERE, true );
			
			if ( parent_tab_item != null ){
				
				parent_tab_item.setData( "sb:dashboarditem", item );
			}
			
			try {
				if ( g instanceof Group ){
					
					((Group)g).setText( item.getTitle());
					
					String title_id = item.getTitleID();
					
					if ( title_id != null ){
						
						g.setData( "sb:itemtitleid", title_id );
					}
				}
				
				Composite menu_comp = use_tabs?sf:g;
				
				Menu	menu = new Menu( menu_comp );
				
				org.eclipse.swt.widgets.MenuItem itemReload = new org.eclipse.swt.widgets.MenuItem( menu, SWT.PUSH );
				
				Messages.setLanguageText(itemReload, "Button.reload");

				final Runnable reload_action =
					new Runnable()
					{
						public void
						run()
						{	
							if ( Utils.isSWTThread()){
								
								if ( !g.isDisposed()){
									
									Utils.disposeComposite( g, false );
									
									SkinnedComposite skinned_comp =	new SkinnedComposite( g );
									
									SWTSkin skin = skinned_comp.getSkin();
									
									SWTSkinObjectContainer obj = 
										BaseMdiEntry.importStandAlone(
												(SWTSkinObjectContainer)skin.getSkinObject( "content-area" ), 
												item.getState(),
												this );
										
									if ( obj != null ){
										
										sf.setData( "sb:reload", null );
									}
									
									Control c = ((SWTSkinObjectContainer)skin.getSkinObject( "content-area" )).getControl();
									
									c.setLayoutData( Utils.getFilledFormData());
									
									g.layout( true, true );
									
									item.setCurrentTab( parent_tab_item );
								}
							}else{
								
								Utils.execSWTThread( this );
							}
						}
					};
					
				sf.setData( "sb:reload", reload_action );
				
				itemReload.addSelectionListener(
					new SelectionAdapter(){
						
						@Override
						public void widgetSelected(SelectionEvent arg0){
		
							reload_action.run();
						}
					});
				
				org.eclipse.swt.widgets.MenuItem itemPop = new org.eclipse.swt.widgets.MenuItem( menu, SWT.PUSH );
				
				Messages.setLanguageText(itemPop, "menu.pop.out");

				itemPop.addSelectionListener(
					new SelectionAdapter(){
						
						@Override
						public void widgetSelected(SelectionEvent arg0){
							
							PopOutManager.popOutStandAlone( item.getTitle(), item.getState(), null );
						}
					});
				
				new org.eclipse.swt.widgets.MenuItem( menu, SWT.SEPARATOR );
				
				if ( bigly_ui ){
					
					org.eclipse.swt.widgets.MenuItem itemRemove = new org.eclipse.swt.widgets.MenuItem( menu, SWT.PUSH );
					
					Messages.setLanguageText(itemRemove, "MySharesView.menu.remove");
	
					Utils.setMenuItemImage(itemRemove, "delete");
					
					itemRemove.addSelectionListener(
						new SelectionAdapter(){
						
							@Override
							public void widgetSelected(SelectionEvent arg0){
								item.remove( false );
							}
						});
					
					new org.eclipse.swt.widgets.MenuItem( menu, SWT.SEPARATOR );
				}
				
				org.eclipse.swt.widgets.MenuItem itemConfig = new org.eclipse.swt.widgets.MenuItem( menu, SWT.PUSH );
				
				Messages.setLanguageText(itemConfig, "device.configure");

				Utils.setMenuItemImage(itemConfig, "image.sidebar.cog" );
				
				itemConfig.addSelectionListener(
					new SelectionAdapter(){
					
						@Override
						public void widgetSelected(SelectionEvent arg0){
							
							showConfig();
						}
					});
				
				menu_comp.setMenu( menu );
				
				if ( parent_tab_item == null ){
					menu_comp.addListener( SWT.MouseDoubleClick, (ev)->{
						PopOutManager.popOutStandAlone( item.getTitle(), item.getState(), null );
					});
				}else{
					
					CTabFolder folder = parent_tab_item.getParent();
					
					folder.addListener( SWT.MouseDoubleClick, (ev)->{
						if ( folder.getItem( new Point( ev.x, ev.y )) == parent_tab_item ){
							
							PopOutManager.popOutStandAlone( item.getTitle(), item.getState(), null );
						}
					});
				}
				
				SkinnedComposite skinned_comp =	new SkinnedComposite( g );
				
				SWTSkin skin = skinned_comp.getSkin();
				
				SWTSkinObjectContainer imported = BaseMdiEntry.importStandAlone((SWTSkinObjectContainer)skin.getSkinObject( "content-area" ), item.getState(), reload_action );
					
				if ( imported != null ){
					
						// built OK, remove 
					
					sf.setData( "sb:reload", null );
				}
				
				Control c = ((SWTSkinObjectContainer)skin.getSkinObject( "content-area" )).getControl();
				
				c.setLayoutData( Utils.getFilledFormData());
					
				item.setCurrentTab( parent_tab_item );

				c.addListener(
					SWT.Show,
					new Listener(){
						
						@Override
						public void handleEvent(Event arg0){
							g.layout( true, true );
							
							item.setCurrentTab( parent_tab_item );
						}
					});
			}catch( Throwable e ) {
				
				Debug.out( e );			
			}
			
			return( g );
		}
		
		private void
		showConfig()
		{
			if ( config_active ){
				
				return;
			}
			
			config_active = true;
			
			DBConfigWindow config = new DBConfigWindow( new ArrayList<DashboardItem>( items.getList()));
			
			config.addCloseListener(()->{
			
				config_active = false;
			});
		}
		
		private class
		DBConfigWindow
		{
			final private Shell							shell;
			final private org.eclipse.swt.widgets.List 	list;
			final Composite 							cGrid;
			final Button 								btnSave;
			
			final private List<DashboardItem>	items;
			final private int					num_items;
			final private int[]					item_uids;
	
			final Map<Integer,Integer> uid_to_item_map = new HashMap<>();
			
			final boolean original_use_tabs = getUseTabs();
			
			int[][]				existing_mapping;
			int[][] 			mapping;
			Composite[][] 		cells;
	
			List<Runnable> listeners = new ArrayList<>();
			
			private
			DBConfigWindow(
				List<DashboardItem>		_items )
			{
				items	= _items;
				
				shell = ShellFactory.createMainShell(SWT.DIALOG_TRIM | SWT.RESIZE);
	
			    Messages.setLanguageText( shell, "configure.dashboard" );
			    Utils.setShellIcon(shell);
			    GridLayout layout = new GridLayout();
			    layout.numColumns = 2;
			    shell.setLayout(layout);
		
			    GridData gridData;
			    
			    	// item list
			    
			    list = new org.eclipse.swt.widgets.List(shell, SWT.SINGLE | SWT.HORIZONTAL | SWT.VERTICAL);
			    gridData = new GridData(GridData.FILL_VERTICAL);
			    list.setLayoutData(gridData);
			    
			    existing_mapping = getDashboardLayout();
			    
			    num_items = items.size();    
			    
			    item_uids = new int[num_items];
			    
				for ( int i=0; i< num_items; i++ ) {
	
					DashboardItem	item = items.get(i);
					
					int	uid = item.getUID();
					
					item_uids[i] = uid;
					
					uid_to_item_map.put( uid, i );
					
					list.add( (i+1) + ") " + item.getTitle());
				}
				
			    cGrid = new Composite(shell, SWT.NONE);
			    gridData = new GridData(GridData.FILL_HORIZONTAL);
			    cGrid.setLayoutData(gridData);
	
			    
				int grid_size = buildGrid();
				
					// add horiz
				
				final Button add_horiz = new Button( shell, SWT.CHECK );
				gridData = new GridData(GridData.FILL_HORIZONTAL);
				gridData.horizontalSpan = 2;
				add_horiz.setLayoutData(gridData);
				add_horiz.setText( MessageText.getString( "dashboard.add.right" ));
				
				add_horiz.setSelection( getAddNewHorizontal());
				
				add_horiz.addListener(SWT.Selection, new Listener() {
				      @Override
				      public void handleEvent(Event e) {
				    	  setAddNewHorizontal(  add_horiz.getSelection());
				      }});
				
					// use tabs
				
				final Button use_tabs = new Button( shell, SWT.CHECK );
				gridData = new GridData(GridData.FILL_HORIZONTAL);
				gridData.horizontalSpan = 2;
				use_tabs.setLayoutData(gridData);
				use_tabs.setText( MessageText.getString( "dashboard.use.tabs" ));
				
				use_tabs.setSelection( getUseTabs());
				
				use_tabs.addListener(SWT.Selection, new Listener() {
				      @Override
				      public void handleEvent(Event e) {
				    	  setUseTabs(  use_tabs.getSelection());
				      }});
				
					// line
				
			    Label labelSeparator = new Label(shell,SWT.SEPARATOR | SWT.HORIZONTAL);
			    gridData = new GridData(GridData.FILL_HORIZONTAL);
			    gridData.horizontalSpan = 2;
			    labelSeparator.setLayoutData(gridData);
			    
		    		// button row
	
			    Composite cButtons = new Composite(shell, SWT.NONE);
			    gridData = new GridData(GridData.FILL_HORIZONTAL);
			    gridData.horizontalSpan = 2;
			    cButtons.setLayoutData(gridData);
			    GridLayout layoutButtons = new GridLayout();
			    layoutButtons.numColumns = 6;
			    cButtons.setLayout(layoutButtons);
	
			    List<Button> buttons = new ArrayList<>();
	
			    Button btnReset = new Button(cButtons,SWT.PUSH);
			    buttons.add( btnReset );
			    gridData = new GridData();
			    gridData.horizontalAlignment = GridData.END;
			    btnReset.setLayoutData(gridData);
			    Messages.setLanguageText(btnReset,"Button.reset");
			    btnReset.addListener(SWT.Selection, new Listener() {
			      @Override
			      public void handleEvent(Event e) {
			      
			    	  existing_mapping = new int[0][0];
			    	 
			    	  for (int uid: item_uids ) {
			    		  
			    		  existing_mapping = ensureUIDInLayout( existing_mapping, uid );
			    	  }
			    	  
			    	  buildGrid();
			    	  
			    	  shell.layout( true, true );
			      }
			    });
			    
			    Button btnClear = new Button(cButtons,SWT.PUSH);
			    buttons.add( btnClear );
			    gridData = new GridData();
			    gridData.horizontalAlignment = GridData.END;
			    btnClear.setLayoutData(gridData);
			    Messages.setLanguageText(btnClear,"Button.clear");
			    btnClear.addListener(SWT.Selection, new Listener() {
			      @Override
			      public void handleEvent(Event e) {
			      
			    	  existing_mapping = new int[0][0];
			    	 			    	  
			    	  buildGrid();
			    	  
			    	  btnSave.setEnabled( false );
			    	  
			    	  shell.layout( true, true );
			      }
			    });
			    
			    Label label = new Label(cButtons,SWT.NULL);
			    gridData = new GridData(GridData.FILL_HORIZONTAL );
			    label.setLayoutData(gridData);
	
			    btnSave = new Button(cButtons,SWT.PUSH);
			    buttons.add( btnSave );
			    gridData = new GridData();
			    gridData.horizontalAlignment = GridData.END;
			    btnSave.setLayoutData(gridData);
			    Messages.setLanguageText(btnSave,"wizard.multitracker.edit.save");
			    btnSave.addListener(SWT.Selection, new Listener() {
			      @Override
			      public void handleEvent(Event e) {
			      
			    	if ( setDashboardLayout( mapping, num_items, true ) || getUseTabs() != original_use_tabs) {
			    		
			    		fireChanged();
			    	}
			    	
			        shell.dispose();
			      }
			    });
	
			    Button btnCancel = new Button(cButtons,SWT.PUSH);
			    buttons.add( btnCancel );
			    gridData = new GridData();
			    gridData.horizontalAlignment = GridData.END;
			    btnCancel.setLayoutData(gridData);
			    Messages.setLanguageText(btnCancel,"Button.cancel");
			    btnCancel.addListener(SWT.Selection, new Listener() {
			      @Override
			      public void handleEvent(Event e) {
			        shell.dispose();
			      }
			    });
	
				Utils.makeButtonsEqualWidth( buttons );
	
			    shell.setDefaultButton( btnSave );
	
			    shell.addListener(SWT.Traverse, new Listener() {
			    	@Override
				    public void handleEvent(Event e) {
			    		if ( e.character == SWT.ESC){
			    			shell.dispose();
			    		}
			    	}
			    });
	
	
			    if ( grid_size < 5 ) {
			    
			    	Point size = shell.computeSize(500,SWT.DEFAULT);
			    	shell.setSize(size);
			    	
				}else{
					
			    	shell.pack();
				}
			    
			    Utils.centreWindow( shell );
	
			    shell.addListener( SWT.Dispose, (ev)->{
			    	
			    	for ( Runnable r: listeners ){
			    		try{
			    			r.run();
			    		}catch( Throwable e ){
			    			Debug.out( e );
			    		}
			    	}
			    });
			    
			    shell.open();
			}
			
			private void
			addCloseListener(
				Runnable	r )
			{
				listeners.add( r );
			}
			
			private int
			buildGrid()
			{
				int grid_size = existing_mapping.length > num_items?existing_mapping.length:num_items;

				mapping = new int[grid_size][grid_size];

				for ( int[]row: mapping ) {
					Arrays.fill( row, -1 );
				}			

				for ( int i=0;i<existing_mapping.length;i++) {

					int[] old_row 	= existing_mapping[i];
					int[] new_row	= mapping[i];

					for ( int j=0; j<old_row.length;j++) {

						int uid = old_row[j];

						if ( uid_to_item_map.containsKey( uid )) {

							new_row[j] = uid;

						}else {

							new_row[j] = -1;
						}
					}
				}

				cells 	= new Composite[grid_size][grid_size];

				Utils.disposeComposite( cGrid, false );

				GridLayout layoutGrid = new GridLayout(grid_size,true);
				layoutGrid.horizontalSpacing	= 0;
				layoutGrid.verticalSpacing	= 0;

				cGrid.setLayout(layoutGrid);

				for ( int i=0;i<grid_size;i++) {

					for ( int j=0;j<grid_size;j++ ) {

						final Canvas cell = new Canvas( cGrid, SWT.DOUBLE_BUFFERED );
						GridData gridData = new GridData();
						gridData.widthHint = 50;
						gridData.heightHint = 50;
						cell.setLayoutData(gridData);

						cells[i][j]	= cell;

						final int f_i = i;
						final int f_j = j;

						cell.addMouseListener(
								new MouseAdapter(){

									@Override
									public void mouseDown(MouseEvent arg0){

										int uid = item_uids[list.getSelectionIndex()];

										mapping[f_i][f_j]	= mapping[f_i][f_j]==uid?-1:uid;

										btnSave.setEnabled( testBuild( items, mapping.clone()));

										cell.redraw();
									}
								});	

						cell.addPaintListener(
								new PaintListener(){

									@Override
									public void paintControl(PaintEvent ev){
										GC gc = ev.gc;

										Rectangle bounds = cell.getBounds();

										int uid = mapping[f_i][f_j];

										if ( uid != -1 && uid_to_item_map.get( uid ) == list.getSelectionIndex()) {

											gc.setBackground( Colors.dark_grey );

										}else{

											gc.setBackground( uid==-1?Colors.light_grey:Colors.grey );
										}

										gc.fillRectangle( 0, 0, bounds.width, bounds.height );

										gc.setForeground( Colors.white );

										gc.drawLine( 0, 0, 0, bounds.height-1 );

										gc.drawLine( 0, 0, bounds.width-1, 0 );

										if ( f_i == grid_size - 1 ) {
											gc.drawLine( 0, bounds.height-1 , bounds.width-1, bounds.height-1  );
										}
										if ( f_j == grid_size - 1 ) {
											gc.drawLine( bounds.width-1, 0 , bounds.width-1, bounds.height-1  );
										}


										if ( uid != -1 ){

											new GCStringPrinter(gc, String.valueOf( uid_to_item_map.get( uid ) + 1 ), new Rectangle( 0,  0, bounds.width, bounds.height), 0, SWT.CENTER ).printString();
										}
									}
								});
					}
				}

				list.select( 0 );

				list.addSelectionListener(
						new SelectionAdapter(){
							@Override
							public void widgetSelected(SelectionEvent e){

								for ( Composite[] row: cells ) {
									for ( Composite c: row ) {
										c.redraw();
									}
								}
							}
						});

				return( grid_size );
			}
		}
		
		public class
		DashboardItem
			implements TabFolderRenderer.TabbedEntry
		{
			private Map<String,Object>		map;
			
			private CTabItem		item;
			private ViewTitleInfo	title_info;
			
			private
			DashboardItem(
				Map<String,Object>		_map )
			{
				map	= _map;
				
				Long uid = (Long)map.get( "_uid" );
				
				if ( uid == null ) {
					
						// migration
					
					uid = new Long(getItemUID());
					
					map.put( "_uid", uid );
					
					configDirty();
				}
			}
			
			public int
			getUID()
			{
				return(((Long)map.get( "_uid" )).intValue());
			}
			
			public String
			getTitleID()
			{
				String title_id = (String)map.get( "title_id" );
				
				if ( title_id == null ){
					
					String title = (String)map.get( "title" );
					
					if ( title.startsWith( "!" ) && title.endsWith( "!" )){
						
						title_id = title.substring( 1, title.length()-1 );
					}
				}
				
				if ( title_id != null ){
					
					if ( MessageText.getString( title_id ) != null ){
						
						return( title_id );
					}
				}
				
				return( null );
			}
			
			public String
			getTitle()
			{
				String title_id = (String)map.get( "title_id" );
				
				if ( title_id != null ){
					
					String t = MessageText.getString( title_id );
					
					if ( t != null ){
						
						return( t );
					}
				}
				
				String title = (String)map.get( "title" );
				
				if ( title.startsWith( "!" ) && title.endsWith( "!" )){
					
					String t = MessageText.getString( title.substring( 1, title.length()-1 ));
					
					if ( t != null ){
						
						title = t;
					}
				}
				
				return( title );
			}
			
			public Map<String,Object>
			getState()
			{
				return( map );
			}
			
			public void
			setCurrentTab(
				CTabItem		_item )
			{
				item	= _item;
				
				title_info = null;
				
				if ( item == null ){
					
					return;
				}
				
				Control[] parent = new Control[]{ item.getControl()};
				
				LinkedList<Control[]> controls = new LinkedList<>();
				
				controls.add( parent );
				
				while( !controls.isEmpty()){
				
					Control[] cs = controls.removeFirst();
					
					for ( Control c: cs ){
						
						UISWTView view = (UISWTView)c.getData( "UISWTView" );
						
						if ( view instanceof UISWTViewCore ){
								
							title_info = ((UISWTViewCore)view).getViewTitleInfo();
							
							if ( title_info != null ){
								
								break;
							}
						}
						
						if ( c instanceof ViewTitleInfo ){
							
							title_info = (ViewTitleInfo)c;
							
							break;
						}
						
						if ( c instanceof Composite ){
							
							controls.add(((Composite)c).getChildren());
						}
					}
				}
			}
			
			
			private void
			refresh(
				ViewTitleInfo		info )
			{
				if ( info == title_info ){
					
					refreshTabFolder( this );
				}
			}
			
			@Override
			public CTabItem 
			getTabbedEntryItem()
			{
				return( item );
			}
			
			public ViewTitleInfo 
			getTabbedEntryViewTitleInfo()
			{
				return( title_info );
			}
			
			public List<TabFolderRenderer.TabbedEntryVitalityImage> 
			getTabbedEntryVitalityImages()
			{
				return( new ArrayList<>());
			};
			
			@Override
			public boolean 
			isTabbedEntryActive()
			{
				return( true );
			}
			
			public void
			remove(
				boolean already_disposed )
			{
				synchronized( items ) {
					
					items.remove( this );
				
					int[][] layout = getDashboardLayout();
					
					if ( removeUIDFromLayout( layout, getUID())){
											
						setDashboardLayout( layout, items.size(), true );
					}			
				}
				
				if ( items.size() == 0 || !already_disposed ){
				
					fireChanged();
				}
			}
		}		
	}
	
	public static class
	SideBarDashboard
		implements 
			UISWTViewCoreEventListener, 
			IViewAlwaysInitialize,
			DashboardListener
	{
		final SB_Dashboard db = MainMDISetup.getSb_dashboard();
		
		final DashboardInstance sb = db.getSidebarDashboard();
	
		UISWTView 		swtView;
		Composite		comp;
		
		@Override
		public void itemsChanged(){
			
			if ( sb.getItemCount() == 0 ){
				
				swtView.closeView();
				
			}else{
				
				Utils.disposeComposite( comp, false );
				
				sb.build( comp );
				
				comp.layout( true, true );
			}
		}
		
		public boolean 
		eventOccurred(
			UISWTViewEvent event)
		{
			switch (event.getType()) {
				case UISWTViewEvent.TYPE_CREATE:{
					swtView = event.getView();
	
					break;
				}
				case UISWTViewEvent.TYPE_DESTROY:{
	
					sb.removeListener( this );
					
					break;
				}
				case UISWTViewEvent.TYPE_INITIALIZE:{
					
					comp = (Composite)event.getData();
	
					comp.setLayout( new FormLayout());
						
					sb.build( comp );
					
					sb.addListener( this );

					break;
				}
				case UISWTViewEvent.TYPE_LANGUAGEUPDATE:{
					
				
					Messages.updateLanguageForControl( comp );
	
					break;
				}
				case UISWTViewEvent.TYPE_REFRESH:{
	
					break;
				}
			}

			return( true );
		}
	}
	
	public static class
	RightBarDashboard
		implements 
			UISWTViewCoreEventListener, 
			IViewAlwaysInitialize,
			DashboardListener
	{
		final SB_Dashboard db = MainMDISetup.getSb_dashboard();
		
		final DashboardInstance rb = db.getRightbarDashboard();
	
		UISWTView 		swtView;
		Composite		comp;
		
		@Override
		public void 
		itemsChanged()
		{
			if ( rb.getItemCount() == 0 ){
				
				swtView.closeView();
				
			}else{
								
				Utils.disposeComposite( comp, false );
				
				rb.build( comp );
				
				comp.layout( true, true );
			}
		}
		
		public boolean 
		eventOccurred(
			UISWTViewEvent event)
		{
			switch (event.getType()) {
				case UISWTViewEvent.TYPE_CREATE:{
					swtView = event.getView();
	
					break;
				}
				case UISWTViewEvent.TYPE_DESTROY:{
	
					rb.removeListener( this );
					
					break;
				}
				case UISWTViewEvent.TYPE_INITIALIZE:{
					
					comp = (Composite)event.getData();
	
					comp.setLayout( new FormLayout());
						
					rb.build( comp );
					
					rb.addListener( this );

					break;
				}
				case UISWTViewEvent.TYPE_LANGUAGEUPDATE:{
					
				
					Messages.updateLanguageForControl( comp );
	
					break;
				}
				case UISWTViewEvent.TYPE_REFRESH:{
	
					break;
				}
			}

			return( true );
		}
	}
	
	public static class
	TopBarDashboard
		implements 
			UISWTViewCoreEventListener, 
			IViewAlwaysInitialize,
			DashboardListener
	{
		final SB_Dashboard db = MainMDISetup.getSb_dashboard();
		
		final DashboardInstance rb = db.getTopbarDashboard();
	
		UISWTView 		swtView;
		Composite		comp;
		
		@Override
		public void 
		itemsChanged()
		{
			if ( rb.getItemCount() == 0 ){
				
				swtView.closeView();
				
			}else{
								
				Utils.disposeComposite( comp, false );
				
				rb.build( comp );
				
				comp.layout( true, true );
			}
		}
		
		public boolean 
		eventOccurred(
			UISWTViewEvent event)
		{
			switch (event.getType()) {
				case UISWTViewEvent.TYPE_CREATE:{
					swtView = event.getView();
	
					break;
				}
				case UISWTViewEvent.TYPE_DESTROY:{
	
					rb.removeListener( this );
					
					break;
				}
				case UISWTViewEvent.TYPE_INITIALIZE:{
					
					comp = (Composite)event.getData();
	
					comp.setLayout( new FormLayout());
						
					rb.build( comp );
					
					rb.addListener( this );

					break;
				}
				case UISWTViewEvent.TYPE_LANGUAGEUPDATE:{
					
				
					Messages.updateLanguageForControl( comp );
	
					break;
				}
				case UISWTViewEvent.TYPE_REFRESH:{
	
					break;
				}
			}

			return( true );
		}
	}
}
