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
import com.biglybt.ui.mdi.*;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.mdi.BaseMdiEntry;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinObjectContainer;
import com.biglybt.ui.swt.views.skin.sidebar.SideBar;
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
			{ "dashboard.example.1", "d5:itemsld4:_uidi76e12:control_typei0e2:id7:Library3:mdi7:sidebar9:parent_id16:header.transfers7:skin_id25:com.biglybt.ui.skin.skin38:skin_ref7:library5:title7:Libraryed4:_uidi81e12:control_typei0e14:event_listenerd4:name43:com.biglybt.ui.swt.views.stats.ActivityViewe2:id9:SpeedView3:mdi6:tabbed7:skin_id25:com.biglybt.ui.skin.skin35:title8:Activityed4:_uidi84e12:control_typei0e14:event_listenerd10:ipc_method17:cloneViewListener9:plugin_id6:3dview11:plugin_name7:3D Viewe2:id23:view3d.most.active.name3:mdi7:sidebar9:parent_id14:header.plugins7:skin_id25:com.biglybt.ui.skin.skin35:title21:3D View (Most active)ee6:layout47:76,76,84,84;76,76,84,84;76,76,84,84;81,81,81,817:weights15:643,352;645,352e" },
			{ "dashboard.example.2", "d5:itemsld4:_uidi76e12:control_typei0e2:id7:Library3:mdi7:sidebar9:parent_id16:header.transfers7:skin_id25:com.biglybt.ui.skin.skin38:skin_ref7:library5:title7:Libraryed4:_uidi96e12:control_typei0e11:data_sourced6:exportd2:dn12:Shares: Sent3:key27:Dashboard: Anonymous Shares7:network3:I2P2:vti2ee8:exporter44:com.biglybt.plugin.net.buddy.BuddyPluginBetae14:event_listenerd4:name41:com.biglybt.plugin.net.buddy.swt.ChatViewe2:id53:Chat_I2P:IRQXG2DCN5QXEZB2EBAW433OPFWW65LTEBJWQYLSMVZQ3:mdi7:sidebar9:parent_id12:ChatOverview7:skin_id25:com.biglybt.ui.skin.skin35:title19:Anon - Shares: Sented4:_uidi100e12:control_typei0e11:data_sourced6:exportd4:anoni1e4:h_cmi1e5:h_dlnl3:I2Pe2:id16:UHQOFCLPP5YEIFRO7:versioni2e2:voi1ee8:exporter50:com.biglybt.core.subs.impl.SubscriptionManagerImple14:event_listenerd4:name49:com.biglybt.ui.swt.subscriptions.SubscriptionViewe2:id111:Subscription_042F84BA33D7C69F5F0BBDF56771D065E56ECE405EF157C8B14389477F6260F62062DFBF5EC5FA2E4904AC0C361F8243923:mdi7:sidebar9:parent_id13:Subscriptions7:skin_id25:com.biglybt.ui.skin.skin35:title23:Anon - Shares: Receivedee6:layout48:76,96,-1,-1;76,100,-1,-1;-1,-1,-1,-1;-1,-1,-1,-17:weights15:672,325;500,500e" },
			{ "dashboard.example.3", "d5:itemsld4:_uidi76e12:control_typei0e2:id7:Library3:mdi7:sidebar9:parent_id16:header.transfers7:skin_id25:com.biglybt.ui.skin.skin38:skin_ref7:library5:title7:Libraryed4:_uidi103e12:control_typei0e14:event_listenerd4:name39:com.biglybt.ui.swt.views.PeersSuperViewe2:id12:AllPeersView3:mdi7:sidebar9:parent_id16:header.transfers7:skin_id25:com.biglybt.ui.skin.skin35:title9:All Peersed4:_uidi104e12:control_typei0e2:id8:Activity3:mdi7:sidebar9:parent_id11:header.vuze7:skin_id25:com.biglybt.ui.skin.skin38:skin_ref8:activity5:title13:Notificationsed4:_uidi105e12:control_typei0e2:id12:ChatOverview3:mdi7:sidebar9:parent_id16:header.discovery7:skin_id25:com.biglybt.ui.skin.skin38:skin_ref9:chatsview5:title13:Chat Overviewed4:_uidi106e12:control_typei0e2:id14:RelatedContent3:mdi7:sidebar9:parent_id16:header.discovery7:skin_id25:com.biglybt.ui.skin.skin38:skin_ref7:rcmview5:title17:Swarm Discoveriesed4:_uidi107e12:control_typei0e14:event_listenerd4:name35:com.biglybt.ui.swt.views.ConfigViewe2:id10:ConfigView3:mdi7:sidebar9:parent_id14:header.plugins7:skin_id25:com.biglybt.ui.skin.skin35:title7:Optionsed4:_uidi108e12:control_typei0e14:event_listenerd4:name40:com.biglybt.ui.swt.views.stats.StatsViewe2:id9:StatsView3:mdi7:sidebar9:parent_id14:header.plugins7:skin_id25:com.biglybt.ui.skin.skin35:title10:Statisticsee6:layout188:76,103,104,105,106,107,108;76,103,104,105,106,107,108;76,103,104,105,106,107,108;76,103,104,105,106,107,108;76,103,104,105,106,107,108;76,103,104,105,106,107,108;76,103,104,105,106,107,1088:use_tabsi1e7:weights0:e" },
	};
	
	private final MultipleDocumentInterfaceSWT		mdi;
	
	private MdiEntry mdi_entry;
	
	private List<DashboardItem>		items = new ArrayList<>();
	
	private boolean	config_dirty;
	
	private CopyOnWriteList<DashboardListener>	listeners = new CopyOnWriteList<>();
	
	public 
	SB_Dashboard(
		final MultipleDocumentInterfaceSWT _mdi ) 
	{
		mdi		= _mdi;
		
		readConfig();
		
		if ( !COConfigurationManager.getBooleanParameter( "dashboard.init.0", false )){
			
			COConfigurationManager.setParameter( "dashboard.init.0", true );
			
			if ( items.isEmpty()){
				
				addStartupItem();
				
				writeConfig();
			}
		}
		
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
								
								addItem( map );
								
								fireChanged();
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
									
									importDashboard( entry[1], false );
									
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
					
					String data = exportDashboard();
					
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
							
							importDashboard( data, true );
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
			menuItem = menuManager.addMenuItem("sidebar." + MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD,
					"Button.reset");
	
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
	
			menuItem.addListener(new MenuItemListener() {
				@Override
				public void selected(MenuItem menu, Object target) {
					
					synchronized( items ) {
						
						items.clear();
						
						COConfigurationManager.setParameter( "dashboard.layout", "" );
						 
						addStartupItem();
					}
					
					fireChanged();
				}
			});
		}
		
		{		
			menuItem = menuManager.addMenuItem("sidebar." + MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD,	"sep2");
		
			menuItem.setStyle( MenuItem.STYLE_SEPARATOR );
		
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
		}
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
	
	public void
	addItem(
		BaseMdiEntry		entry )
	{
		Map<String,Object> map = entry.exportStandAlone();
				
		addItem( map );
		
		fireChanged();
	}

	public List<DashboardItem>
	getCurrent()
	{
		return( items );
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
					return( String.valueOf( items.size()));
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
								
				synchronized( items ){
				
					cog_click_time[0]	= SystemTime.getMonotonousTime();

					new DBConfigWindow( new ArrayList<DashboardItem>( items ));
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
							
								synchronized( items ){
								
									if ( SystemTime.getMonotonousTime() - cog_click_time[0] < 250 ){
										
										return;
									}
								}
								
								fireChanged();

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
		listeners.add( l );
	}
	
	public void
	removeListener(
		DashboardListener	l )
	{
		listeners.remove( l );
	}
	
	private boolean
	getAddNewHorizontal()
	{
		return( COConfigurationManager.getBooleanParameter( "dashboard.config.addhoriz", true ));
	}
	
	private void
	setAddNewHorizontal(
		boolean		b )
	{
		COConfigurationManager.setParameter( "dashboard.config.addhoriz", b );
	}
	
	private boolean
	getUseTabs()
	{
		return( COConfigurationManager.getBooleanParameter( "dashboard.config.usetabs", false ));
	}
	
	private void
	setUseTabs(
		boolean		b )
	{
		COConfigurationManager.setParameter( "dashboard.config.usetabs", b );
	}
	
	private int
	getItemUID()
	{
		synchronized( items ){
			
			int	next = COConfigurationManager.getIntParameter( "dashboard.uid.next" );
			
			int t = next + 1;
			
			if ( t < 0 ){
				
				t = 0;
			}
			
			COConfigurationManager.setParameter( "dashboard.uid.next", t );
			
			return( next );
		}
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
	
	private void
	readConfig()
	{
		synchronized( items ){
			
			Map	config = COConfigurationManager.getMapParameter( "dashboard.config", new HashMap<>());
			
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
			
			COConfigurationManager.setParameter( "dashboard.config", config );
			
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
			
			String layout_str = COConfigurationManager.getStringParameter( "dashboard.layout" );
		
			layout_str = encodeIAA( compactLayout( decodeIAA( layout_str), items.size()));
			
			map.put( "layout", layout_str );
			
			map.put( "weights", COConfigurationManager.getStringParameter( "dashboard.sash.weights" ));
			
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
				
				COConfigurationManager.setParameter( "dashboard.layout", layout );
				COConfigurationManager.setParameter( "dashboard.sash.weights", weights );
				
				Number l_use_tabs = (Number)map.get( "use_tabs" );
				
				boolean use_tabs = l_use_tabs != null && l_use_tabs.intValue() != 0; 
				
				setUseTabs( use_tabs );
				
			}catch( Throwable e ) {
				
				Debug.out( e );
			}
		}
		
		fireChanged();
	}
	
	private int	building = 0;
	
	
	protected void
	build(
		Composite		dashboard_composite )
	{
		try{
			building++;
			
			if ( dashboard_composite == null ) {
				
				return;
			}
			
			Utils.disposeComposite( dashboard_composite, false );
						
			int[][] layout = getDashboardLayout();
			
			layout = compactLayout(layout, 0, 0 );
			
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
			
			boolean use_tabs = getUseTabs();
			
			final List<SashForm>	sashes 		= new ArrayList<>();
			List<Control>			controls	= new ArrayList<>();
			
			build( item_map, dashboard_composite, use_tabs, sashes, controls, layout, 0, 0, layout.length, layout.length );
			
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
					
					sf.setData( sf_weights.length );
				}
			}else{
					// something's changed
				
				setSashWeights( new int[0][0] );
				
				for ( SashForm sf: sashes ) {
					
					int[]	weights = sf.getWeights();
					
					sf.setData( weights.length );
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
								
								Object	d = sf.getData();
								
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
		
		build( item_map, null, false, null, null, layout, 0, 0, layout.length, layout.length );
		
			// at least one works...
		
		return( item_map.size() < before );
	}
	
	private List<DashboardItem>
	build(	
		Map<Integer,DashboardItem>	item_map,
		Composite					comp,
		boolean						use_tabs,
		List<SashForm>				sashes,
		List<Control>				controls,
		int[][]						cells,
		int							x,
		int							y,
		int							width,
		int							height )
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
				
				controls.add( build( comp, item, use_tabs ));
				
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
					
					CTabFolder tf;
					
					if ( comp != null ){
						tf = new CTabFolder( comp, SWT.TOP );
						tf.setLayoutData( Utils.getFilledFormData());
					}else {
						tf = null;
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
						
						List<DashboardItem> items = build( item_map, tab_composite, use_tabs, sashes, controls, cells, x, current, width, split - current );
						
						if ( items.isEmpty()){
							tab_item.dispose();
						}else{
							if ( tab_item != null ){
								tab_item.setText( items.get(0).getTitle() + (items.size()>1?"...":""));
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
					
					List<DashboardItem> items = build( item_map, tab_composite, use_tabs, sashes, controls, cells, x, current, width, height-(current-y));
										
					if ( items.isEmpty()){
						tab_item.dispose();
					}else{
						if ( tab_item != null ){
							tab_item.setText( items.get(0).getTitle() + (items.size()>1?"...":""));
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
						
						tf.setSelection( 0 );
						
						tf.addSelectionListener(
							new SelectionAdapter(){
								
								@Override
								public void widgetSelected(SelectionEvent ev){
									Composite c = (Composite)tf.getSelection().getControl();
									
									Object o = c.getData();
									
									if ( o instanceof Runnable ){
										
										((Runnable)o).run();
										c.setData(null);
									}
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
						
						result.addAll( build( item_map, sf, use_tabs, sashes, controls, cells, x, current, width, split - current ));
						
						current = split;
					}
					
					result.addAll(( build( item_map, sf, use_tabs, sashes, controls, cells, x, current, width, height-(current-y) )));
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
					
					CTabFolder tf;
					
					if ( comp != null ){
						tf = new CTabFolder( comp, SWT.TOP | SWT.NO_BACKGROUND);
						tf.setLayoutData( Utils.getFilledFormData());
					}else {
						tf = null;
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
						
						List<DashboardItem> items = build( item_map, tab_composite, use_tabs, sashes, controls, cells, current, y, split - current, height );
						if ( items.isEmpty()){
							tab_item.dispose();
						}else{
							if ( tab_item != null ){
								tab_item.setText( items.get(0).getTitle() + (items.size()>1?"...":""));
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
					
					List<DashboardItem> items = build( item_map, tab_composite, use_tabs, sashes, controls, cells, current, y, width-(current-x), height );
					
					if ( items.isEmpty()){
						tab_item.dispose();
					}else{
						if ( tab_item != null ){
							tab_item.setText( items.get(0).getTitle() + (items.size()>1?"...":""));
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
						
						tf.setSelection( 0 );
						
						tf.addSelectionListener(
							new SelectionAdapter(){
								
								@Override
								public void widgetSelected(SelectionEvent ev){
									Composite c = (Composite)tf.getSelection().getControl();
									
									Object o = c.getData();
									
									if ( o instanceof Runnable ){
										
										((Runnable)o).run();
										c.setData(null);
									}
								}
							});
					}
				}else {
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
	
						result.addAll((build( item_map, sf, use_tabs, sashes, controls, cells, current, y, split - current, height )));
						
						current = split;
					}
					
					result.addAll((build( item_map, sf, use_tabs, sashes, controls, cells, current, y, width-(current-x), height )));
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
		Composite				sf,
		final DashboardItem		item,
		boolean					use_tabs )
	{
		Composite g = use_tabs?new Composite( sf, SWT.NULL ):new Group( sf, SWT.NULL );
		
		g.setLayoutData( Utils.getFilledFormData());
		
		g.setLayout( new GridLayout());

		g.setData( Utils.RELAYOUT_UP_STOP_HERE, true );
		
		try {
			if ( g instanceof Group ){
				
				((Group)g).setText( item.getTitle());
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
								
								BaseMdiEntry.importStandAlone(
									(SWTSkinObjectContainer)skin.getSkinObject( "content-area" ), 
									item.getState(),
									this );
									
								Control c = ((SWTSkinObjectContainer)skin.getSkinObject( "content-area" )).getControl();
								
								c.setLayoutData( Utils.getFilledFormData());
								
								g.layout( true, true );
							}
						}else{
							
							Utils.execSWTThread( this );
						}
					}
				};
				
			sf.setData( reload_action );
			
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
						
						BaseMdiEntry.popoutStandAlone( item.getTitle(), item.getState(), null );
					}
				});
			
			new org.eclipse.swt.widgets.MenuItem( menu, SWT.SEPARATOR );
			
			org.eclipse.swt.widgets.MenuItem itemRemove = new org.eclipse.swt.widgets.MenuItem( menu, SWT.PUSH );
			
			Messages.setLanguageText(itemRemove, "MySharesView.menu.remove");

			Utils.setMenuItemImage(itemRemove, "delete");
			
			itemRemove.addSelectionListener(
				new SelectionAdapter(){
				
					@Override
					public void widgetSelected(SelectionEvent arg0){
						item.remove();
					}
				});
			
			menu_comp.setMenu( menu );
			
			SkinnedComposite skinned_comp =	new SkinnedComposite( g );
			
			SWTSkin skin = skinned_comp.getSkin();
			
			BaseMdiEntry.importStandAlone((SWTSkinObjectContainer)skin.getSkinObject( "content-area" ), item.getState(), reload_action );
				
			Control c = ((SWTSkinObjectContainer)skin.getSkinObject( "content-area" )).getControl();
			
			c.setLayoutData( Utils.getFilledFormData());
				
			c.addListener(
				SWT.Show,
				new Listener(){
					
					@Override
					public void handleEvent(Event arg0){
						g.layout( true, true );
					}
				});
		}catch( Throwable e ) {
			
			Debug.out( e );			
		}
		
		return( g );
	}
	
	
	
	public class
	DashboardItem
	{
		private Map<String,Object>		map;
		
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
		getTitle()
		{
			return((String)map.get( "title" ));
		}
		
		public Map<String,Object>
		getState()
		{
			return( map );
		}
		
		public void
		remove()
		{
			synchronized( items ) {
				
				items.remove( this );
			}
			
			fireChanged();
		}
	}
	
	private boolean
	setSashWeights(
		int[][]		weights )
	{
		synchronized( items ){
			
			String str = encodeIAA( weights );
			
			String old_str = COConfigurationManager.getStringParameter( "dashboard.sash.weights", "" );
			
			if ( str.equals( old_str )){
				
				return( false );
			}
			
			COConfigurationManager.setParameter( "dashboard.sash.weights", str );
			
			return( true );
		}
	}
	
	private int[][]
	getSashWeights()
	{
		synchronized( items ){
			
			String str = COConfigurationManager.getStringParameter( "dashboard.sash.weights" );
			
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
			
			String old_str = COConfigurationManager.getStringParameter( "dashboard.layout" );
			
			boolean same = new_str.equals( old_str );
				
			if ( compact ){
				
				new_str = encodeIAA( compactLayout( layout, grid_size ));
			}
			
			if ( !new_str.equals( old_str )){
			
				COConfigurationManager.setParameter( "dashboard.layout", new_str );
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
			
			String str = COConfigurationManager.getStringParameter( "dashboard.layout" );
			
			return( decodeIAA( str ));
		}
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
	
	public interface
	DashboardListener
	{
		public void
		itemsChanged();
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
		    layoutButtons.numColumns = 5;
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

		    shell.open();
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
}
