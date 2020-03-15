/*
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

package com.biglybt.plugin.net.buddy.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Menu;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.menus.MenuContext;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.plugin.I2PHelpers;
import com.biglybt.plugin.net.buddy.BuddyPluginUtils;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.SimpleTextEntryWindow;

public class 
BuddyUIUtils
{
	public static MenuItem
	createChat(
		MenuManager		menu_manager,
		MenuContext		mc )
	{
		MenuItem mi = menu_manager.addMenuItem( mc, "chat.view.create.chat" );

		mi.setStyle( MenuItem.STYLE_MENU );

		mi.addFillListener(new com.biglybt.pif.ui.menus.MenuItemFillListener() {
			@Override
			public void menuWillBeShown(MenuItem menu, Object data){

				menu.removeAllChildItems();

				MenuItem mi = menu_manager.addMenuItem( menu, "!" + MessageText.getString( "label.public" ) + "...!" );

				mi.addListener(
					new MenuItemListener()
					{
						@Override
						public void
						selected(
							MenuItem			menu,
							Object 				target )
						{
							SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
									"chat.view.enter.key.title", "chat.view.enter.key.msg");

							entryWindow.prompt(new UIInputReceiverListener() {
								@Override
								public void UIInputReceiverClosed(UIInputReceiver receiver) {
									if (!receiver.hasSubmittedInput()) {
										return;
									}

									String key = receiver.getSubmittedInput().trim();

									BuddyPluginUtils.createBetaChat( AENetworkClassifier.AT_PUBLIC, key, null );
								}
							});

						}
					});

				mi = menu_manager.addMenuItem( menu, "!" + MessageText.getString( "label.anon" ) + "...!" );

				mi.addListener(
						new MenuItemListener()
						{
							@Override
							public void
							selected(
								MenuItem			menu,
								Object 				target )
							{
								if ( BuddyPluginUtils.getBetaPlugin().isI2PAvailable()){

									SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
											"chat.view.enter.key.title", "chat.view.enter.key.msg");

									entryWindow.prompt(new UIInputReceiverListener() {
										@Override
										public void UIInputReceiverClosed(UIInputReceiver receiver) {
											if (!receiver.hasSubmittedInput()) {
												return;
											}

											String key = receiver.getSubmittedInput().trim();

											BuddyPluginUtils.createBetaChat( AENetworkClassifier.AT_I2P, key, null );
										}
									});

								}else{

									I2PHelpers.installI2PHelper( null, null, null );
								}

							}
						});

				if ( I2PHelpers.isInstallingI2PHelper()){

					mi.setEnabled( false );
					mi.setText(  mi.getText() + " (" + MessageText.getString( "PeersView.state.pending" ) + ")" );
				}
			}});

		return( mi );
	}
	
	public static void
	createChat(
		Menu							parent_menu,
		ChatCreationListener			listener )
	{
		Menu menu = new Menu( parent_menu.getShell(), SWT.DROP_DOWN);

		org.eclipse.swt.widgets.MenuItem mi = new org.eclipse.swt.widgets.MenuItem( parent_menu, SWT.CASCADE);

		Messages.setLanguageText( mi, "chat.view.create.chat" );

		mi.setMenu( menu );
		
		org.eclipse.swt.widgets.MenuItem mi_pub = new org.eclipse.swt.widgets.MenuItem( menu, SWT.PUSH);

		mi_pub.setText( MessageText.getString( "label.public" ) + "..." );

		mi_pub.addListener( SWT.Selection, (ev)->{
			SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
					"chat.view.enter.key.title", "chat.view.enter.key.msg");

			entryWindow.prompt(new UIInputReceiverListener() {
				@Override
				public void UIInputReceiverClosed(UIInputReceiver receiver) {
					if (!receiver.hasSubmittedInput()) {
						return;
					}

					String key = receiver.getSubmittedInput().trim();

					BuddyPluginUtils.createBetaChat( AENetworkClassifier.AT_PUBLIC, key, null );
					
					listener.chatCreated( AENetworkClassifier.AT_PUBLIC + ": " + key );
				}
			});
		});
		
		org.eclipse.swt.widgets.MenuItem mi_anon = new org.eclipse.swt.widgets.MenuItem( menu, SWT.PUSH);

		mi_anon.setText( MessageText.getString( "label.anon" ) + "..." );

		mi_anon.addListener( SWT.Selection, (ev)->{
			SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
					"chat.view.enter.key.title", "chat.view.enter.key.msg");

			entryWindow.prompt(new UIInputReceiverListener() {
				@Override
				public void UIInputReceiverClosed(UIInputReceiver receiver) {
					if (!receiver.hasSubmittedInput()) {
						return;
					}

					String key = receiver.getSubmittedInput().trim();

					BuddyPluginUtils.createBetaChat( AENetworkClassifier.AT_I2P, key, null );
					
					listener.chatCreated( AENetworkClassifier.AT_I2P + ": " + key );
				}
			});
		});
	}
	
	public interface
	ChatCreationListener
	{
		public void
		chatCreated(
			String		name );
	}
}
