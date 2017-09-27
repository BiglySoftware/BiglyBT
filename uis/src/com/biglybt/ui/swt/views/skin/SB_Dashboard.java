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

import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.mdi.BaseMdiEntry;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;


public class SB_Dashboard
{

	
	private List<DashboardItem>		items = new ArrayList<>();

	private CopyOnWriteList<DashboardListener>	listeners = new CopyOnWriteList<>();
	
	public 
	SB_Dashboard(
		final MultipleDocumentInterfaceSWT mdi ) 
	{
		PluginInterface pi = PluginInitializer.getDefaultInterface();
		UIManager uim = pi.getUIManager();
		MenuManager menuManager = uim.getMenuManager();
		MenuItem menuItem;

		menuItem = menuManager.addMenuItem("sidebar." + MultipleDocumentInterface.SIDEBAR_HEADER_DASHBOARD,
				"Button.reset");

		menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);

		menuItem.addListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				
				synchronized( items ) {
					
					items.clear();
				}
				
				fireChanged();
			}
		});
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
		for ( DashboardListener l: listeners ){
			
			try {
				l.itemsChanged();
				
			}catch( Throwable e ){
				
				Debug.out( e );
			}
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
