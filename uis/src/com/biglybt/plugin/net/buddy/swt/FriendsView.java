/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.plugin.net.buddy.swt;

import org.eclipse.swt.widgets.Composite;

import com.biglybt.plugin.net.buddy.BuddyPlugin;
import com.biglybt.plugin.net.buddy.BuddyPluginUtils;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;

import com.biglybt.pif.ui.UIInstance;

/**
 * This is the "Friends" view, which is a multi-tabbed configuration view
 */
public class FriendsView implements UISWTViewCoreEventListener
{
	public static final String VIEW_ID = "azbuddy";

	public static final String DS_SELECT_CLASSIC_TAB = "SelectClassicTab";
	private BuddyPlugin plugin;
	private UISWTInstance ui_instance;
	private BuddyPluginView buddyPluginView;
	private BuddyPluginViewInstance		current_instance;
	private Object datasource;

	public
	FriendsView(
		BuddyPluginView _buddyPluginView,
		BuddyPlugin _plugin,
		UIInstance _ui_instance)
	{
		plugin			= _plugin;
		ui_instance		= (UISWTInstance)_ui_instance;
		buddyPluginView = _buddyPluginView;
	}
	
	public FriendsView() {
		BuddyPlugin bp = BuddyPluginUtils.getPlugin();

		if ( bp != null ){

			UIInstance[] instances = bp.getPluginInterface().getUIManager().getUIInstances();

			for ( UIInstance ui: instances ){

				if (ui.getUIType().equals(UIInstance.UIT_SWT)){
					plugin = bp;
					ui_instance = (UISWTInstance) ui;
					buddyPluginView = (BuddyPluginView) bp.getSWTUI();

					return;
				}
			}
		}
	}

	@Override
	public boolean
	eventOccurred(
		UISWTViewEvent event )
	{
		switch( event.getType() ){

			case UISWTViewEvent.TYPE_CREATE:{

				if ( current_instance != null ){

					return( false );
				}

				event.getView().setDestroyOnDeactivate(false);

				break;
			}
			case UISWTViewEvent.TYPE_INITIALIZE:{

				current_instance = new BuddyPluginViewInstance(buddyPluginView, plugin, ui_instance, (Composite)event.getData());

				if (DS_SELECT_CLASSIC_TAB.equals(datasource)) {
					current_instance.selectClassicTab();
				}

				break;
			}
			case UISWTViewEvent.TYPE_CLOSE:
			case UISWTViewEvent.TYPE_DESTROY:{

				try{
					if ( current_instance != null ){

						current_instance.destroy();
					}
				}finally{

					current_instance = null;
				}

				break;
			}
			case UISWTViewEvent.TYPE_DATASOURCE_CHANGED: {
				if (current_instance != null && DS_SELECT_CLASSIC_TAB.equals(datasource)) {
					current_instance.selectClassicTab();
				}
				break;
			}
		}

		return true;
	}
}
