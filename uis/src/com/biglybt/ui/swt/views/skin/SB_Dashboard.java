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

import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.BDecoder;
import com.biglybt.core.util.BEncoder;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MdiEntryVitalityImage;
import com.biglybt.ui.mdi.MdiEntryVitalityImageListener;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.mdi.BaseMdiEntry;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;


public class SB_Dashboard
{
	private final MultipleDocumentInterfaceSWT		mdi;
	
	private MdiEntry mdi_entry;
	
	private List<DashboardItem>		items = new ArrayList<>();

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

		menuItem = menuManager.addMenuItem("sidebar." + MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD,
				"menu.add.website");

		menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);

		menuItem.addListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				
				SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
						"chat.view.enter.key.title", "chat.view.enter.key.msg");

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
							
							synchronized( items ) {
								
								items.add( new DashboardItem( map ));
							}
							
							fireChanged();
						}
					}});
			}
		});
		
		
		menuItem = menuManager.addMenuItem("sidebar." + MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD,
				"Button.reset");

		menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);

		menuItem.addListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				
				synchronized( items ) {
					
					items.clear();
					
					addStartupItem();
				}
				
				fireChanged();
			}
		});
		
		menuItem = menuManager.addMenuItem("sidebar." + MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD,
				"sep1");
		
		menuItem.setStyle( MenuItem.STYLE_SEPARATOR );
	}

	private void
	addStartupItem()
	{
		String	starting_url = "https://github.com/BiglySoftware/BiglyBT/wiki/Dashboard";
		
		Map<String,Object>	map = new HashMap<>();
		
		map.put( "mdi", "sidebar" );
		map.put( "skin_id", "com.biglybt.ui.skin.skin3" );
		map.put( "parent_id", "header.dashboard" );
		map.put( "skin_ref", "main.generic.browse" );
		map.put( "id", "Browser: Dashboard" );
		map.put( "title", "Wiki: Dashboard" );		
		map.put( "data_source", starting_url );
		map.put( "control_type", 0L );
		
		synchronized( items ) {
		
			items.add( new DashboardItem( map ));
		}
	}
	
	public void
	addItem(
		BaseMdiEntry		entry )
	{
		Map<String,Object> map = entry.exportStandAlone();
		
		System.out.println( "dbi: " + map );
		
		synchronized( items ) {
		
			items.add( new DashboardItem( map ) );
		}
		
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
		
		cog.setToolTip( MessageText.getString( "configure.dashboard.tooltip" ));

		cog.addListener(new MdiEntryVitalityImageListener() {
			@Override
			public void mdiEntryVitalityImage_clicked(int x, int y) {
				
			}});
		
		cog.setVisible(true);
		
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
				
				for ( Map map: item_list ) {
					
					items.add( new DashboardItem( map ));
				}
			}
		}
	}
	
	private void
	writeConfig()
	{
		synchronized( items ){
			
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
	
	public class
	DashboardItem
	{
		private Map<String,Object>		map;
		
		private
		DashboardItem(
			Map<String,Object>		_map )
		{
			map	= _map;
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
	
	public interface
	DashboardListener
	{
		public void
		itemsChanged();
	}
}
