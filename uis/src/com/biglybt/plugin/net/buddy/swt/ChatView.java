/*
 * Created on Jul 10, 2015
 * Created by Paul Gardner
 *
 * Copyright 2015 Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.plugin.net.buddy.swt;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.widgets.Composite;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.plugin.net.buddy.BuddyPluginUtils;
import com.biglybt.plugin.net.buddy.BuddyPluginViewInterface;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta.ChatInstance;

public class
ChatView
	implements UISWTViewCoreEventListener
{
	private ChatInstance	current_chat;
	private ChatInstance	initialized_chat;

	private BuddyPluginViewInterface.View chat_view;

	public
	ChatView()
	{	
	}
	
	private void
	initialize(
		Composite	_parent_composite )
	{
		try{
			if ( current_chat != null ){

				Map<String,Object>	chat_properties = new HashMap<>();

				chat_properties.put( BuddyPluginViewInterface.VP_SWT_COMPOSITE, _parent_composite );

					//
				chat_properties.put( BuddyPluginViewInterface.VP_CHAT, current_chat.getClone());

				chat_view =
					BuddyPluginUtils.buildChatView(
						chat_properties,
						new BuddyPluginViewInterface.ViewListener() {

							@Override
							public void
							chatActivated(
								ChatInstance chat)
							{

							}
						});

				ChatMDIEntry	mdi_entry = (ChatMDIEntry)current_chat.getUserData( SBC_ChatOverview.MDI_KEY );

				if ( mdi_entry != null ){

					mdi_entry.setView( this );
				}
			}else{

				Debug.out( "No current chat" );
			}
		}catch( Throwable e ){

			Debug.out( e );
		}
	}

	protected void
	handleDrop(
		String		drop )
	{
		chat_view.handleDrop( drop );
	}

	private void
	viewActivated()
	{
		if ( chat_view != null ){

			chat_view.activate();
		}
	}

	private void
	viewDeactivated()
	{
	}

	private void
	dataSourceChanged(
		Object data )
	{
		synchronized( this ){

			if ( data instanceof ChatInstance ){

				ChatInstance chat = (ChatInstance)data;

				current_chat = chat;
			}
		}
	}

	@Override
	public boolean
	eventOccurred(
		UISWTViewEvent event)
	{
		switch (event.getType()) {
		case UISWTViewEvent.TYPE_CREATE:

			break;

		case UISWTViewEvent.TYPE_DESTROY:

			synchronized( this ){

				if ( current_chat != null ){

					current_chat.destroy();

					current_chat = null;
				}
			}

			break;

		case UISWTViewEvent.TYPE_INITIALIZE:{

			synchronized( this ){

				if ( current_chat != null ){

					try{
						current_chat.getClone();

					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			}
			initialize((Composite)event.getData());

			break;
		}
		case UISWTViewEvent.TYPE_LANGUAGEUPDATE:

			break;

		case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
			dataSourceChanged(event.getData());
			break;

		case UISWTViewEvent.TYPE_FOCUSGAINED:
			viewActivated();
			break;

		case UISWTViewEvent.TYPE_FOCUSLOST:
			viewDeactivated();
			break;

		case UISWTViewEvent.TYPE_REFRESH:
			break;
		}

		return true;
	}
}
