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

package com.biglybt.plugin.net.buddy.swt;


import java.util.Arrays;

import com.biglybt.plugin.net.buddy.BuddyPluginBeta.ChatAdapter;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta.ChatInstance;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoManager;
import com.biglybt.ui.mdi.MdiCloseListener;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MdiEntryDropListener;
import com.biglybt.ui.mdi.MultipleDocumentInterface;


public class ChatMDIEntry implements ViewTitleInfo
{
	private final MdiEntry mdi_entry;

	private final ChatInstance chat;

	private ChatView		view;
	private String			drop_outstanding;

	private String 			last_text;
	private int[]			last_colour;
	
	private final ChatAdapter adapter =
		new ChatAdapter()
		{
			@Override
			public void
			updated()
			{
				update();
			}
		};

	public
	ChatMDIEntry(
		ChatInstance 	_chat,
		MdiEntry 		_entry)
	{
		chat		= _chat;

		mdi_entry 	= _entry;

		setupMdiEntry();
	}

	private void
	setupMdiEntry()
	{
		mdi_entry.setViewTitleInfo( this );

		MdiEntryDropListener drop_listener =
			new MdiEntryDropListener()
			{
				@Override
				public boolean
				mdiEntryDrop(
					MdiEntry 	entry,
					Object		payload )
				{
					if ( payload instanceof String[] ){

						String[] derp = (String[])payload;

						if ( derp.length > 0 ){

							payload = derp[0];
						}
					}

					if (!(payload instanceof String)){

						return false;
					}

					MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();

					if ( mdi != null ){

						String drop = (String)payload;

						if ( view == null ){

							drop_outstanding = drop;

						}else{

							view.handleDrop( drop );
						}

						mdi.showEntry( mdi_entry );

						return( true );

					}else{

						return( false );
					}
				}
			};

		mdi_entry.addListener( drop_listener );

		mdi_entry.addListener(
			new MdiCloseListener()
			{
				@Override
				public void
				mdiEntryClosed(
					MdiEntry 	entry,
					boolean 	user)
				{
					chat.removeListener(adapter);
					chat.destroy();
				}
			});

		/*
		UIManager ui_manager = PluginInitializer.getDefaultInterface().getUIManager();
		
		MenuManager menu_manager = ui_manager.getMenuManager();

		MenuItem menu_item;

		menu_item = menu_manager.addMenuItem( "sidebar." + mdi_entry.getId(), "dasd.ad.ad." );
		menu_item.setDisposeWithUIDetach(UIInstance.UIT_SWT);
		menu_item.addListener(
				new MenuItemListener() 
				{
					@Override
					public void
					selected(
						MenuItem menu, Object target ) 
					{
				      	
					}
				});
		*/
		
		mdi_entry.setImageLeftID("image.sidebar.chat-overview");
		
		chat.addListener( adapter );
	}

	protected void
	setView(
		ChatView		_view )
	{
		view = _view;

		String drop = drop_outstanding;

		if ( drop != null ){

			drop_outstanding = null;

			view.handleDrop( drop );
		}
	}

	private void
	update()
	{
		String 	text	= (String)getTitleInfoProperty( ViewTitleInfo.TITLE_INDICATOR_TEXT );
		int[] 	colour 	= (int[])getTitleInfoProperty( ViewTitleInfo.TITLE_INDICATOR_COLOR );
		
		boolean changed = text != last_text && ( text == null || last_text == null || !text.equals( last_text ));
		
		if ( !changed ){
			
			changed = colour != last_colour && ( colour == null || last_colour == null || !Arrays.equals( colour, last_colour ));
		}
		
		if ( changed ){
		
			last_text	= text;
			last_colour	= colour;
			
			mdi_entry.redraw();
		}
		
		ViewTitleInfoManager.refreshTitleInfo( mdi_entry.getViewTitleInfo());
	}

	@Override
	public Object
	getTitleInfoProperty(
		int propertyID )
	{
		switch( propertyID ){

			case ViewTitleInfo.TITLE_INDICATOR_TEXT_TOOLTIP:{

				return( chat.getName());
			}
			case ViewTitleInfo.TITLE_TEXT:{

				return( chat.getName( true ));
			}
			case ViewTitleInfo.TITLE_INDICATOR_COLOR:{

				if ( chat.getMessageOutstanding()){

					if ( chat.hasUnseenMessageWithNick()){

						return( SBC_ChatOverview.COLOR_MESSAGE_WITH_NICK );
					}
				}

				return( null );
			}
			case ViewTitleInfo.TITLE_INDICATOR_TEXT:{

				if ( chat.getMessageOutstanding()){

					return( "*" );

				}else{

					return( null );
				}

			}
		}

		return( null );
	}
}
