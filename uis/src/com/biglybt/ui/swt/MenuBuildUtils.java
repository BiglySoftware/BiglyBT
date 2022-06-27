/*
 * Created on 25-Jan-2007
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
 *
 */
package com.biglybt.ui.swt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ColorDialog;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Menu;
import com.biglybt.pif.ui.Graphic;
import com.biglybt.pif.ui.GraphicURI;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.pifimpl.local.ui.menus.MenuItemImpl;
import com.biglybt.pifimpl.local.ui.tables.TableContextMenuItemImpl;
import com.biglybt.pifimpl.local.utils.FormattersImpl;
import com.biglybt.pif.ui.tables.TableContextMenuItem;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.swt.pif.UISWTGraphic;
import com.biglybt.ui.swt.shells.main.MainMenuV3;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;

import com.biglybt.plugin.I2PHelpers;
import com.biglybt.plugin.net.buddy.BuddyPluginUtils;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta.ChatInstance;
import com.biglybt.plugin.net.buddy.swt.BuddyUIUtils;


/**
 * A class which helps generate build SWT menus.
 *
 * It provides two main functions. The first provides the ability to create
 * regenerateable menus (it will dispose old items when not displayed and
 * invoke a callback method to regenerate it).
 *
 * The second provides the ability to create SWT menus based on the plugin
 * API for menu creation - this allows internal code that generates menus
 * to include plugins to append to their own internal menus.
 *
 * @author Allan Crooks
 */
public class MenuBuildUtils {

	/**
	 * An interface to be used for addMaintenanceListenerForMenu.
	 */
	public static interface MenuBuilder {
		public void buildMenu(Menu root_menu, MenuEvent menuEvent);
	}


	public static void addMaintenanceListenerForMenu(final Menu menu,
		final MenuBuilder builder) {
		addMaintenanceListenerForMenu(menu, builder, false);
	}

	/**
	 * Creates and adds a listener object to implement regeneratable menus.
	 *
	 * The first piece of functionality it offers is the ability to call a
	 * callback method to generate the menu when it is about to be displayed
	 * (the callback method is done by passing an object implementing the
	 * MenuBuilder interface).
	 *
	 * This means that the caller of this method only needs to provide the
	 * logic to construct the menu's contents. This is helpful if you want
	 * to update an existing menu.
	 *
	 * The second piece of functionality is that it automatically does what
	 * is required to dispose of existing menu items when the menu is hidden.
	 */
	public static void addMaintenanceListenerForMenu(final Menu menu,
			final MenuBuilder builder, boolean alwaysBuilt) {

		// Was taken from TableView.java
		MenuListener menuListener = new MenuListener() {
			boolean bShown = false;

			@Override
			public void menuHidden(MenuEvent e) {
				bShown = false;

				if (Constants.isOSX || alwaysBuilt) {
					return;
				}

				// Must dispose in an asyncExec, otherwise SWT.Selection doesn't
				// get fired (async workaround provided by Eclipse Bug #87678)
				e.widget.getDisplay().asyncExec(new AERunnable() {
					@Override
					public void runSupport() {
						if (bShown || menu.isDisposed()) {
							return;
						}
						Utils.disposeSWTObjects(menu.getItems());

						if (Constants.isLinux) { // Hack for Ubuntu Unity -- Show not called when no items
							new org.eclipse.swt.widgets.MenuItem(menu, SWT.SEPARATOR);
						}
					}
				});
			}

			@Override
			public void menuShown(MenuEvent e) {
				Utils.disposeSWTObjects(menu.getItems());

				bShown = true;
				builder.buildMenu(menu, e);

				if (Constants.isLinux) { // Hack for Ubuntu Unity -- Show not called when no items
					if (menu.getItemCount() == 0) {
						new org.eclipse.swt.widgets.MenuItem(menu, SWT.SEPARATOR);
					}
				}
			}
		};
		menu.addMenuListener(menuListener);

		if (alwaysBuilt) {
			Event e = new Event();
			e.type = SWT.Show;
			e.display = menu.getDisplay();
			e.widget = menu;
			menuListener.menuShown(new MenuEvent(e));
		} else if (Constants.isLinux) { // Hack for Ubuntu Unity -- Show not called when no items
			new org.eclipse.swt.widgets.MenuItem(menu, SWT.SEPARATOR);
		}
	}

	/**
	 * This is an interface used by addPluginMenuItems.
	 */
	public static interface PluginMenuController {

		/**
		 * This method should create a listener object which should be invoked
		 * when the given menu item is selected.
		 */
		public Listener makeSelectionListener(MenuItem plugin_menu_item);

		/**
		 * This method will be invoked just before the given menu item is
		 * displayed in a menu.
		 */
		public void notifyFillListeners(MenuItem menu_item);

		public void buildSubmenu(MenuItem parent);
		
		public void buildComplete( Menu menu );
	}

	/**
	 * This is an implementation of the PluginMenuController interface for use with
	 * MenuItemImpl classes - note that this is not intended for use by subclasses of
	 * MenuItemImpl (like TableContextMenuItemImpl).
	 *
	 * The object passed at construction time is the object to be passed when selection
	 * listeners and fill listeners are notified.
	 */
	public static class MenuItemPluginMenuControllerImpl implements
			PluginMenuController {

		private Menu	parentMenu;
		
		private Object[] objects;

		public MenuItemPluginMenuControllerImpl(Object[] o) {
			this.objects = o;
		}

		@Override
		public Listener makeSelectionListener(MenuItem menu_item) {
			final MenuItemImpl mii = (MenuItemImpl) menu_item;
			return new Listener() {
				@Override
				public void handleEvent(Event e) {
					if ( mii instanceof TableContextMenuItemImpl ){
						TableView<?> tv = (TableView<?>)parentMenu.getData( TableContextMenuItemImpl.MENUKEY_TABLE_VIEW );
						((TableContextMenuItemImpl)mii).setTable(tv);
					}
					mii.invokeListenersMulti(objects);
				}
			};
		}

		@Override
		public void notifyFillListeners(MenuItem menu_item) {
			((MenuItemImpl) menu_item).invokeMenuWillBeShownListeners(objects);
		}

		// @see com.biglybt.ui.swt.MenuBuildUtils.PluginMenuController#buildSubmenu(com.biglybt.pif.ui.menus.MenuItem)
		@Override
		public void buildSubmenu(MenuItem parent) {
			com.biglybt.pif.ui.menus.MenuBuilder submenuBuilder = ((MenuItemImpl) parent).getSubmenuBuilder();
			if (submenuBuilder != null) {
				try {
					parent.removeAllChildItems();
					submenuBuilder.buildSubmenu(parent, objects);
				} catch (Throwable t) {
					Debug.out(t);
				}
			}
		}
		
		@Override
		public void buildComplete(Menu menu){
			parentMenu = menu;
		}
	}

	/**
	 * An instance of MenuItemPluginMenuControllerImpl with a default value of
	 * null - this will be the value passed when notifying selection and fill
	 * listeners.
	 */
	public static final PluginMenuController BASIC_MENU_ITEM_CONTROLLER = new MenuItemPluginMenuControllerImpl(null);

	/**
	 * Creates menu items inside the given menu based on the plugin API MenuItem
	 * instances provided. This method is provided mainly as a utility method to
	 * make it easier for menus to incorporate menu components specified by
	 * plugins.
	 *
	 * Usually, the list of array items will be extracted from a class like
	 * MenuItemManager or TableContextMenuManager, where plugins will usually
	 * register menu items they have created.
	 *
	 * @param items The list of plugin MenuItem to add
	 * @param parent The SWT Menu to add to.
	 * @param prev_was_separator Indicates if the previous item in the menu is
	 *            a separator or not
	 * @param enable_items Indicates whether you want generated items to be
	 *            enabled or not. If false, all items will be disabled. If true,
	 *            then the items *may* be enabled, depending on how each MenuItem
	 *            is configured.
	 * @param controller The callback object used by this method when creating the
	 *            SWT menus (used for invoking fill and selection listeners).
	 */
	public static void addPluginMenuItems(MenuItem[] items, Menu parent,
			boolean prev_was_separator,
			final boolean enable_items, final PluginMenuController controller) {

		for (int i = 0; i < items.length; i++) {
			final MenuItemImpl az_menuitem = (MenuItemImpl) items[i];

			controller.notifyFillListeners(az_menuitem);
			if (!az_menuitem.isVisible()) {continue;}

			final int style = az_menuitem.getStyle();
			final int swt_style;

			boolean this_is_separator = false;

			// Do we have any children? If so, we override any manually defined
			// style.
			boolean is_container = false;


			if (style == TableContextMenuItem.STYLE_MENU) {
				swt_style = SWT.CASCADE;
				is_container = true;
			} else if (style == TableContextMenuItem.STYLE_PUSH) {
				swt_style = SWT.PUSH;
			} else if (style == TableContextMenuItem.STYLE_CHECK) {
				swt_style = SWT.CHECK;
			} else if (style == TableContextMenuItem.STYLE_RADIO) {
				swt_style = SWT.RADIO;
			} else if (style == TableContextMenuItem.STYLE_SEPARATOR) {
				this_is_separator = true;
				swt_style = SWT.SEPARATOR;
			} else {
				swt_style = SWT.PUSH;
			}

			if (prev_was_separator && this_is_separator) {continue;} // Skip contiguous separators
			if (this_is_separator && i == items.length - 1) {continue;} // Skip trailing separator

			prev_was_separator = this_is_separator;

			final org.eclipse.swt.widgets.MenuItem menuItem;
			
			if ( this_is_separator ){
				
				addSeparator( parent );
				
				continue;
				
			}else{
				
				menuItem = new org.eclipse.swt.widgets.MenuItem( parent, swt_style );
			}
			
			if (enable_items) {

				if (style == TableContextMenuItem.STYLE_CHECK
						|| style == TableContextMenuItem.STYLE_RADIO) {

					Boolean selection_value = (Boolean) az_menuitem.getData();
					if (selection_value == null) {
						throw new RuntimeException(
								"MenuItem with resource name \""
										+ az_menuitem.getResourceKey()
										+ "\" needs to have a boolean value entered via setData before being used!");
					}
					menuItem.setSelection(selection_value.booleanValue());
				}
			}

			final Listener main_listener = controller.makeSelectionListener(az_menuitem);
			menuItem.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event e) {
					if (az_menuitem.getStyle() == MenuItem.STYLE_CHECK
							|| az_menuitem.getStyle() == MenuItem.STYLE_RADIO) {
						if (!menuItem.isDisposed()) {
							az_menuitem.setData(Boolean.valueOf(menuItem.getSelection()));
						}
					}
					main_listener.handleEvent(e);
				}
			});

			if (is_container) {
				Menu this_menu = new Menu(parent);
				menuItem.setMenu(this_menu);

				addMaintenanceListenerForMenu(this_menu, new MenuBuilder() {
					@Override
					public void buildMenu(Menu root_menu, MenuEvent menuEvent) {
						controller.buildSubmenu(az_menuitem);
						addPluginMenuItems(az_menuitem.getItems(), root_menu, false,
								enable_items, controller);
					}
				});
			}

			String custom_title = az_menuitem.getText();
			menuItem.setText(custom_title);

			Graphic g = az_menuitem.getGraphic();
			if (g instanceof UISWTGraphic) {
				Utils.setMenuItemImage(menuItem, ((UISWTGraphic) g).getImage());
			} else if (g instanceof GraphicURI) {
				Utils.setMenuItemImage(menuItem, ((GraphicURI) g).getURI().toString());
			}

			menuItem.setEnabled(enable_items && az_menuitem.isEnabled());

		}
		
		controller.buildComplete( parent );
	}

	/**
	 *
	 * @param flat_entries		Overall list of menu entry names
	 * @param split_after		Split if more than this
	 * @return					Entries are either a String or Object[]{ submeuname, List<String> submenu entries }
	 */

	public static List<Object>
	splitLongMenuListIntoHierarchy(
		List<String>	flat_entries,
		int				split_after )
	{
		List<Object>	result = new ArrayList<>();

		int	flat_entry_count = flat_entries.size();

		if ( flat_entry_count == 0 ){

			return( result );
		}

		Collections.sort(
			flat_entries,
			new Comparator<String>()
			{
				final Comparator<String> comp = new FormattersImpl().getAlphanumericComparator( true );

				@Override
				public int
				compare(
					String o1, String o2)
				{
					return( comp.compare( o1, o2 ));
				}
			});

		int[] buckets = new int[split_after];

		for ( int i=0;i<flat_entry_count;i++){

			buckets[i%buckets.length]++;
		}

		List<char[]>	edges = new ArrayList<>();

		int	pos = 0;

		for ( int i=0;i<buckets.length;i++){

			int	entries = buckets[i];

			edges.add( flat_entries.get( pos ).toCharArray());

			if ( entries > 1 ){

				edges.add( flat_entries.get( pos + entries - 1 ).toCharArray());

				pos += entries;

			}else{

				break;
			}
		}

		int[]	edge_lens = new int[edges.size()];

		for ( int i=0;i<edges.size()-1;i++){

			char[] c1 = edges.get(i);
			char[] c2 = edges.get(i+1);

			int	j;

			for ( j=0;j<Math.min(Math.min(c1.length,c2.length),5); j++ ){

				if ( c1[j] != c2[j]){

					String label = new String( c1, 0, j+1 );
					
					boolean clash = false;
					
					for ( int k=0;k<i;k++){
						
						if ( label.equals( new String( edges.get(k), 0, edge_lens[k] ))){
							
							clash = true;
						}
					}
					
					if ( !clash ){
						
						break;
					}
				}
			}

			j++;

			int e1 = edge_lens[i] 	= Math.min( c1.length,Math.max( edge_lens[i], j ));
			
			if ( e1 > 0 && e1 < c1.length && Character.isHighSurrogate( c1[e1-1] )){
				edge_lens[i]++;
			}
			
			int e2 = edge_lens[i+1] 	= Math.min( c2.length, j );
			
			if ( e2 > 0 && e2 < c2.length && Character.isHighSurrogate( c2[e2-1] )){
				edge_lens[i+1]++;
			}

		}

		int	bucket_pos 	= 0;
		int	edge_pos	= 0;

		Iterator<String>tag_it = flat_entries.iterator();

		while( tag_it.hasNext()){

			int	bucket_entries = buckets[bucket_pos++];

			List<String>	bucket_tags = new ArrayList<>();

			for ( int i=0;i<bucket_entries;i++){

				bucket_tags.add( tag_it.next());
			}

			if ( bucket_entries == 1 ){

				result.add( bucket_tags.get(0));

			}else{

				String level_name = new String( edges.get( edge_pos ), 0, edge_lens[ edge_pos++ ]) + " - " + new String( edges.get( edge_pos ), 0, edge_lens[ edge_pos++ ]);

				result.add( new Object[]{ level_name, bucket_tags });
			}
		}

		return( result );
	}

	private static AtomicBoolean	pub_chat_pending 	= new AtomicBoolean();
	private static AtomicBoolean	anon_chat_pending 	= new AtomicBoolean();

	public static void
	addCommunityChatMenu(
		Menu			menu )
	{
		final Menu chat_menu = new Menu(menu.getShell(), SWT.DROP_DOWN);

		final org.eclipse.swt.widgets.MenuItem chat_item = new org.eclipse.swt.widgets.MenuItem(menu, SWT.CASCADE);

		Messages.setLanguageText( chat_item, MainMenuV3.MENU_ID_COMMUNITY_CHAT );

		chat_item.setMenu(chat_menu);

		chat_menu.addMenuListener(
			new MenuAdapter()
			{
				@Override
				public void
				menuShown(
					MenuEvent e)
				{
					for ( org.eclipse.swt.widgets.MenuItem mi: chat_menu.getItems()){

						mi.dispose();
					}

					if ( !BuddyPluginUtils.isBetaChatAvailable()){

						return;
					}

						// BiglyBT
					
					{
						org.eclipse.swt.widgets.MenuItem chat_pub = new org.eclipse.swt.widgets.MenuItem(chat_menu, SWT.PUSH);
	
						Messages.setLanguageText(chat_pub, "label.public");
	
						chat_pub.addListener(SWT.Selection, new Listener() {
							@Override
							public void handleEvent(Event event){
	
								pub_chat_pending.set( true );
	
								BuddyPluginUtils.createBetaChat(
									AENetworkClassifier.AT_PUBLIC,
									BuddyPluginBeta.COMMUNITY_CHAT_KEY,
									new BuddyPluginUtils.CreateChatCallback()
									{
										@Override
										public void
										complete(
											ChatInstance	chat )
										{
											pub_chat_pending.set( false );
										}
									});
							}});
	
						if ( pub_chat_pending.get()){
	
							chat_pub.setEnabled( false );
							chat_pub.setText( chat_pub.getText() + " (" + MessageText.getString( "PeersView.state.pending" ) + ")" );
						}
	
						if ( BuddyPluginUtils.isBetaChatAnonAvailable()){
	
							org.eclipse.swt.widgets.MenuItem chat_priv = new org.eclipse.swt.widgets.MenuItem(chat_menu, SWT.PUSH);
	
							Messages.setLanguageText(chat_priv, "label.anon");
	
							chat_priv.addListener(SWT.Selection, new Listener() {
								@Override
								public void handleEvent(Event event){
	
									anon_chat_pending.set( true );
	
									BuddyPluginUtils.createBetaChat(
										AENetworkClassifier.AT_I2P,
										BuddyPluginBeta.COMMUNITY_CHAT_KEY,
										new BuddyPluginUtils.CreateChatCallback()
										{
											@Override
											public void
											complete(
												ChatInstance	chat )
											{
												anon_chat_pending.set( false );
											}
										});
								}});
	
							if ( anon_chat_pending.get()){
	
								chat_priv.setEnabled( false );
								chat_priv.setText( chat_priv.getText() + " (" + MessageText.getString( "PeersView.state.pending" ) + ")" );
	
							}
						}else{
	
							org.eclipse.swt.widgets.MenuItem chat_priv = new org.eclipse.swt.widgets.MenuItem(chat_menu, SWT.PUSH);
	
							chat_priv.setText( MessageText.getString("label.anon") + "..." );
	
							chat_priv.addListener(SWT.Selection, new Listener() {
								@Override
								public void handleEvent(Event event){
	
									I2PHelpers.installI2PHelper( null, null, null );
								}});
	
							if ( I2PHelpers.isInstallingI2PHelper()){
	
								chat_priv.setEnabled( false );
								chat_priv.setText( chat_priv.getText() + " (" + MessageText.getString( "PeersView.state.pending" ) + ")" );
	
							}
						}
						
							// country chat
						
						final ChatInstance cc = BuddyPluginUtils.getCountryChat();
						
						org.eclipse.swt.widgets.MenuItem chat_cc = new org.eclipse.swt.widgets.MenuItem(chat_menu, SWT.PUSH);

						if ( cc != null ){
														
							String cc_text = MessageText.getString( "menu.your.country", new String[] { (String)cc.getUserData( BuddyPluginUtils.CK_CC )});
							
							chat_cc.setText( cc_text );
		
							chat_cc.addListener(SWT.Selection, new Listener() {
								@Override
								public void handleEvent(Event event){
									
									try{
										BuddyPluginUtils.getBetaPlugin().showChat( cc );
										
									}catch( Throwable e ){
										
										Debug.out( e );
									}
								}});
						}else {
							
							String cc_text = MessageText.getString( "menu.your.country", new String[]{ "..." });
							
							chat_cc.setText( cc_text );

							chat_cc.setEnabled( false );
						}
						
						// language chat
						
						final ChatInstance lang = BuddyPluginUtils.getLanguageChat();
						
						org.eclipse.swt.widgets.MenuItem chat_lang = new org.eclipse.swt.widgets.MenuItem(chat_menu, SWT.PUSH);
	
						if ( lang != null ){
														
							String lang_text = MessageText.getString( "menu.your.language", new String[] { (String)lang.getUserData( BuddyPluginUtils.CK_LANG )});
							
							chat_lang.setText( lang_text );
		
							chat_lang.addListener(SWT.Selection, new Listener() {
								@Override
								public void handleEvent(Event event){
									
									try{
										BuddyPluginUtils.getBetaPlugin().showChat( lang );
										
									}catch( Throwable e ){
										
										Debug.out( e );
									}
								}});
						}else {
							
							String lang_text = MessageText.getString( "menu.your.language", new String[]{ "..." });
							
							chat_lang.setText( lang_text );
	
							chat_lang.setEnabled( false );
						}
					}
					
					new org.eclipse.swt.widgets.MenuItem(chat_menu, SWT.SEPARATOR );
					
					// Vuze
					
					{
						org.eclipse.swt.widgets.MenuItem chat_pub = new org.eclipse.swt.widgets.MenuItem(chat_menu, SWT.PUSH);

						chat_pub.setText( MessageText.getString( "ConfigView.section.interface.legacy") + " " + MessageText.getString( "label.public" ));
	
						chat_pub.addListener(SWT.Selection, new Listener() {
							@Override
							public void handleEvent(Event event){
	
								pub_chat_pending.set( true );
	
								BuddyPluginUtils.createBetaChat(
									AENetworkClassifier.AT_PUBLIC,
									BuddyPluginBeta.LEGACY_COMMUNITY_CHAT_KEY,
									new BuddyPluginUtils.CreateChatCallback()
									{
										@Override
										public void
										complete(
											ChatInstance	chat )
										{
											pub_chat_pending.set( false );
										}
									});
							}});
	
						if ( pub_chat_pending.get()){
	
							chat_pub.setEnabled( false );
							chat_pub.setText( chat_pub.getText() + " (" + MessageText.getString( "PeersView.state.pending" ) + ")" );
						}
	
						if ( BuddyPluginUtils.isBetaChatAnonAvailable()){
	
							org.eclipse.swt.widgets.MenuItem chat_priv = new org.eclipse.swt.widgets.MenuItem(chat_menu, SWT.PUSH);
		
							chat_priv.setText( MessageText.getString( "ConfigView.section.interface.legacy") + " " + MessageText.getString( "label.anon" ));

							chat_priv.addListener(SWT.Selection, new Listener() {
								@Override
								public void handleEvent(Event event){
	
									anon_chat_pending.set( true );
	
									BuddyPluginUtils.createBetaChat(
										AENetworkClassifier.AT_I2P,
										BuddyPluginBeta.LEGACY_COMMUNITY_CHAT_KEY,
										new BuddyPluginUtils.CreateChatCallback()
										{
											@Override
											public void
											complete(
												ChatInstance	chat )
											{
												anon_chat_pending.set( false );
											}
										});
								}});
	
							if ( anon_chat_pending.get()){
	
								chat_priv.setEnabled( false );
								chat_priv.setText( chat_priv.getText() + " (" + MessageText.getString( "PeersView.state.pending" ) + ")" );
	
							}
						}else{
	
							org.eclipse.swt.widgets.MenuItem chat_priv = new org.eclipse.swt.widgets.MenuItem(chat_menu, SWT.PUSH);
	
							chat_priv.setText( MessageText.getString("label.anon") + "..." );
	
							chat_priv.addListener(SWT.Selection, new Listener() {
								@Override
								public void handleEvent(Event event){
	
									I2PHelpers.installI2PHelper( null, null, null );
								}});
	
							if ( I2PHelpers.isInstallingI2PHelper()){
	
								chat_priv.setEnabled( false );
								chat_priv.setText( chat_priv.getText() + " (" + MessageText.getString( "PeersView.state.pending" ) + ")" );
	
							}
						}
					}
				}
			});
	}
	
	public static void
	addChatMenu(
		Menu			menu,
		String			chat_resource,
		String			chat_key )
	{
		final Menu top_menu = new Menu(menu.getShell(), SWT.DROP_DOWN);

		top_menu.setEnabled( chat_key != null );
			
		final org.eclipse.swt.widgets.MenuItem top_item = new org.eclipse.swt.widgets.MenuItem(menu, SWT.CASCADE);

		Messages.setLanguageText( top_item, "label.chat" );

		top_item.setMenu(top_menu);

		final Menu chat_menu = new Menu(menu.getShell(), SWT.DROP_DOWN);

		final org.eclipse.swt.widgets.MenuItem chat_item = new org.eclipse.swt.widgets.MenuItem(top_menu, SWT.CASCADE);

		Messages.setLanguageText( chat_item, chat_resource );

		chat_item.setMenu( chat_menu );
		
		chat_menu.addMenuListener(
			new MenuAdapter()
			{
				@Override
				public void
				menuShown(
					MenuEvent e)
				{
					for ( org.eclipse.swt.widgets.MenuItem mi: chat_menu.getItems()){

						mi.dispose();
					}

					if ( !BuddyPluginUtils.isBetaChatAvailable()){

						return;
					}

					org.eclipse.swt.widgets.MenuItem chat_pub = new org.eclipse.swt.widgets.MenuItem(chat_menu, SWT.PUSH);

					Messages.setLanguageText(chat_pub, "label.public");

					chat_pub.addListener(SWT.Selection, new Listener() {
						@Override
						public void handleEvent(Event event){

							pub_chat_pending.set( true );

							BuddyPluginUtils.createBetaChat(
								AENetworkClassifier.AT_PUBLIC,
								chat_key,
								new BuddyPluginUtils.CreateChatCallback()
								{
									@Override
									public void
									complete(
										ChatInstance	chat )
									{
										pub_chat_pending.set( false );
									}
								});
						}});

					if ( pub_chat_pending.get()){

						chat_pub.setEnabled( false );
						chat_pub.setText( chat_pub.getText() + " (" + MessageText.getString( "PeersView.state.pending" ) + ")" );
					}

					if ( BuddyPluginUtils.isBetaChatAnonAvailable()){

						org.eclipse.swt.widgets.MenuItem chat_priv = new org.eclipse.swt.widgets.MenuItem(chat_menu, SWT.PUSH);

						Messages.setLanguageText(chat_priv, "label.anon");

						chat_priv.addListener(SWT.Selection, new Listener() {
							@Override
							public void handleEvent(Event event){

								anon_chat_pending.set( true );

								BuddyPluginUtils.createBetaChat(
									AENetworkClassifier.AT_I2P,
									chat_key,
									new BuddyPluginUtils.CreateChatCallback()
									{
										@Override
										public void
										complete(
											ChatInstance	chat )
										{
											anon_chat_pending.set( false );
										}
									});
							}});

						if ( anon_chat_pending.get()){

							chat_priv.setEnabled( false );
							chat_priv.setText( chat_priv.getText() + " (" + MessageText.getString( "PeersView.state.pending" ) + ")" );

						}
					}else{

						org.eclipse.swt.widgets.MenuItem chat_priv = new org.eclipse.swt.widgets.MenuItem(chat_menu, SWT.PUSH);

						chat_priv.setText( MessageText.getString("label.anon") + "..." );

						chat_priv.addListener(SWT.Selection, new Listener() {
							@Override
							public void handleEvent(Event event){

								I2PHelpers.installI2PHelper( null, null, null );
							}});

						if ( I2PHelpers.isInstallingI2PHelper()){

							chat_priv.setEnabled( false );
							chat_priv.setText( chat_priv.getText() + " (" + MessageText.getString( "PeersView.state.pending" ) + ")" );

						}
					}
				}
			});
	}

	public interface
	ChatKeyResolver
	{
		public String
		getResourceKey();
		
		public String
		getChatKey(
			Object		target );
		
		public default boolean
		canShareMessage()
		{
			return( false );
		}
		
		public default void
		shareMessage(
			Object				target,
			ChatInstance		chat )
			
		{	
		}
	}

	public static MenuItem
	addChatMenu(
		final MenuManager		menu_manager,
		final MenuItem			chat_item,
		final ChatKeyResolver	chat_key_resolver )
	{
		chat_item.setStyle( MenuItem.STYLE_MENU );

		chat_item.addFillListener(
			new MenuItemFillListener()
			{

				@Override
				public void
				menuWillBeShown(
					MenuItem 	menu,
					Object 		data)
				{
					menu.removeAllChildItems();

					if ( chat_key_resolver.canShareMessage()){
							
						MenuItem chat_share = menu_manager.addMenuItem(chat_item, "menu.share.download");
						
						addChatSelectionMenu( 
							menu_manager, 
							chat_share, 
							null, 
							new ChatSelectionListener(){
								public void
								chatSelected(
									Object	target,
									String	chat )
								{
								}
								
								public void
								chatAvailable(
									Object			target,
									ChatInstance	chat )
								{
									chat_key_resolver.shareMessage(target, chat );
								}
							});
						
						MenuItem mi = menu_manager.addMenuItem( chat_item, "sep" );
						
						mi.setStyle( MenuItem.STYLE_SEPARATOR );
					}
					
					MenuItem discuss_menu = menu_manager.addMenuItem(chat_item, chat_key_resolver.getResourceKey());

					discuss_menu.setStyle( MenuItem.STYLE_MENU );
					
					{
						MenuItem chat_pub = menu_manager.addMenuItem(discuss_menu,  "label.public");

						chat_pub.addMultiListener(
								new MenuItemListener() {

									@Override
									public void
									selected(
										MenuItem 	menu,
										Object 		target)
									{
										Object[]	rows = (Object[])target;

										if ( rows.length > 0 ){

											final AtomicInteger count = new AtomicInteger( rows.length );

											pub_chat_pending.set( true );

											for ( Object obj: rows ){

												String chat_key = chat_key_resolver.getChatKey( obj );

												if ( chat_key != null ){

													BuddyPluginUtils.createBetaChat(
														AENetworkClassifier.AT_PUBLIC,
														chat_key,
														new BuddyPluginUtils.CreateChatCallback()
														{
															@Override
															public void
															complete(
																ChatInstance	chat )
															{
																if ( count.decrementAndGet() == 0 ){

																	pub_chat_pending.set( false );
																}
															}
														});
												}
											}
										}
									}
								});

						if ( pub_chat_pending.get()){

							chat_pub.setEnabled( false );
							chat_pub.setText( chat_pub.getText() + " (" + MessageText.getString( "PeersView.state.pending" ) + ")" );
						}
					}

					if ( BuddyPluginUtils.isBetaChatAnonAvailable()){

						MenuItem chat_priv = menu_manager.addMenuItem(discuss_menu,  "label.anon");

						chat_priv.addMultiListener(
							new MenuItemListener()
							{
								@Override
								public void
								selected(
									MenuItem menu,
									Object target)
								{
									Object[]	rows = (Object[])target;

									if ( rows.length > 0 ){

										final AtomicInteger count = new AtomicInteger( rows.length );

										anon_chat_pending.set( true );

										for ( Object obj: rows ){

											String chat_key = chat_key_resolver.getChatKey( obj );

											if ( chat_key != null ){

												BuddyPluginUtils.createBetaChat(
													AENetworkClassifier.AT_I2P,
													chat_key,
													new BuddyPluginUtils.CreateChatCallback()
													{
														@Override
														public void
														complete(
															ChatInstance	chat )
														{
															if ( count.decrementAndGet() == 0 ){

																anon_chat_pending.set( false );
															}
														}
													});
											}
										}
									}
								}
							});

						if ( anon_chat_pending.get()){

							chat_priv.setEnabled( false );
							chat_priv.setText( chat_priv.getText() + " (" + MessageText.getString( "PeersView.state.pending" ) + ")" );
						}

					}else{

						MenuItem chat_priv = menu_manager.addMenuItem(chat_item,  "label.anon");

						chat_priv.setText( MessageText.getString("label.anon") + "..." );

						chat_priv.addMultiListener(
							new MenuItemListener()
							{
								@Override
								public void
								selected(
									MenuItem menu,
									Object target)
								{

									I2PHelpers.installI2PHelper( null, null, null );
								}
							});

						if ( I2PHelpers.isInstallingI2PHelper()){

							chat_priv.setEnabled( false );
							chat_priv.setText( chat_priv.getText() + " (" + MessageText.getString( "PeersView.state.pending" ) + ")" );
						}
					}
				}
			});


		return( chat_item );
	}
	
	public interface
	ChatSelectionListener
	{
		public void
		chatSelected(
			Object		target,
			String		chat );
		
		public void
		chatAvailable(
			Object			target,
			ChatInstance	chat );
	}
	
	private static class
	ChatSelectionDelegator
		implements ChatSelectionListener
	{
		private ChatSelectionListener	delegate;
				
		private TimerEventPeriodic	timer;
		
		protected
		ChatSelectionDelegator(
			ChatSelectionListener	_delegate )
		{
			delegate = _delegate;
		}
		
		public void
		chatSelected(
			Object		target,
			String		name )
		{
			delegate.chatSelected(target, name);
		}
		
		public void
		chatAvailable(
			Object			target,
			ChatInstance	chat )
		{
			synchronized( ChatSelectionDelegator.this ){
				
				if ( timer != null ){
					
					return;
				}
				
				long start = SystemTime.getMonotonousTime();
				
				timer = SimpleTimer.addPeriodicEvent( "availcheck", 1000, (ev)->{
					
					if ( chat.isAvailable()){
						
						if ( !chat.isDestroyed()){
						
							delegate.chatAvailable(target, chat);
						}
						
						synchronized( ChatSelectionDelegator.this ){
							
							timer.cancel();
						}
					}
					
					if ( SystemTime.getMonotonousTime() - start > 3*60*1000 ){

						Debug.out( "Gave up waiting for " + chat.getNetAndKey() + " to become available" );
						
						synchronized( ChatSelectionDelegator.this ){
							
							timer.cancel();
						}
					}
				});
			}
		}
	}
	
	public static void
	addChatSelectionMenu(
		final MenuManager		menu_manager,
		final MenuItem			chat_item,
		String					existing_chat,
		ChatSelectionListener	_listener )
	{
		ChatSelectionListener listener = new ChatSelectionDelegator( _listener );
		
		chat_item.setStyle( MenuItem.STYLE_MENU );

		chat_item.addFillListener(
			new MenuItemFillListener()
			{

				@Override
				public void
				menuWillBeShown(
					MenuItem 	menu,
					Object 		data)
				{
					menu.removeAllChildItems();

					MenuItem create_menu = menu_manager.addMenuItem( chat_item, "chat.view.create.chat" );

					create_menu.setStyle( MenuItem.STYLE_MENU );
					
					BuddyUIUtils.createChat( 
						menu_manager,
						create_menu,
						true,
						new BuddyUIUtils.ChatCreationListener(){
							
							@Override
							public void chatCreated(Object target, String name){
								listener.chatSelected( target, name);
							}
							
							@Override
							public void chatAvailable(Object target,ChatInstance chat){
								listener.chatAvailable( target,chat );
							}
						});
					
					MenuItem mi = menu_manager.addMenuItem( chat_item, "sep" );
					
					mi.setStyle( MenuItem.STYLE_SEPARATOR );
					
					List<ChatInstance> chats = BuddyPluginUtils.getChats();			
					
					for ( int i=0;i<2;i++){
						
						MenuItem chat_menu = menu_manager.addMenuItem( chat_item, i==0?"label.public":"label.anon" );

						chat_menu.setStyle( MenuItem.STYLE_MENU );
		
						String net = i==0?AENetworkClassifier.AT_PUBLIC:AENetworkClassifier.AT_I2P;

						for ( ChatInstance chat: chats ){
							
							if ( chat.getNetwork() == net ){
								
								mi = menu_manager.addMenuItem( chat_menu, "!" + chat.getKey() + "!" );
																
								mi.addMultiListener(
									new MenuItemListener()
									{
										@Override
										public void
										selected(
											MenuItem			menu,
											Object 				target )
										{
											listener.chatSelected( target, chat.getNetAndKey());
											
											listener.chatAvailable( target, chat );
											
											try{
												BuddyPluginUtils.getBetaPlugin().showChat( chat );
												
											}catch( Throwable e ){
												
												Debug.out( e );
											}
										}
									});
							}
						}
					}
				}
			});
	}
	
	public static void
	addChatSelectionMenu(
		Menu					menu,
		String					resource_key,
		String					existing_chat,
		ChatSelectionListener	_listener )
	{
		ChatSelectionListener listener = new ChatSelectionDelegator( _listener );

		final Menu chat_menu = new Menu(menu.getShell(), SWT.DROP_DOWN);

		final org.eclipse.swt.widgets.MenuItem chat_item = new org.eclipse.swt.widgets.MenuItem(menu, SWT.CASCADE);

		Messages.setLanguageText( chat_item, resource_key );

		chat_item.setMenu(chat_menu);

		chat_menu.addMenuListener(
			new MenuAdapter()
			{
				@Override
				public void
				menuShown(
					MenuEvent e)
				{
					for ( org.eclipse.swt.widgets.MenuItem mi: chat_menu.getItems()){

						mi.dispose();
					}

					if ( !BuddyPluginUtils.isBetaChatAvailable()){

						org.eclipse.swt.widgets.MenuItem mi = new org.eclipse.swt.widgets.MenuItem(chat_menu, SWT.PUSH);
						
						Messages.setLanguageText(mi, "tps.status.unavailable");

						mi.setEnabled( false );
						
						return;
					}
					
					BuddyUIUtils.createChat( 
						chat_menu,
						new BuddyUIUtils.ChatCreationListener(){
							@Override
							public void chatCreated(Object target, String name){
								listener.chatSelected( target, name );
							}
							@Override
							public void chatAvailable(Object target, ChatInstance chat){
								listener.chatAvailable( target,chat );
							}
						});
					
					new org.eclipse.swt.widgets.MenuItem( chat_menu, SWT.SEPARATOR );
					
					org.eclipse.swt.widgets.MenuItem mi_none = new org.eclipse.swt.widgets.MenuItem(chat_menu, SWT.RADIO);
					
					Messages.setLanguageText(mi_none, "label.none");
					
					mi_none.setSelection( existing_chat==null || existing_chat.trim().isEmpty());
					
					mi_none.addListener(SWT.Selection, new Listener() {
						@Override
						public void handleEvent(Event event){
							
							listener.chatSelected( null, "" );
						}});
					
					for ( int i=0;i<2;i++){
						
						final Menu sub_menu = new Menu(chat_menu.getShell(), SWT.DROP_DOWN);

						final org.eclipse.swt.widgets.MenuItem sub_item = new org.eclipse.swt.widgets.MenuItem(chat_menu, SWT.CASCADE);

						Messages.setLanguageText( sub_item, i==0?"label.public":"label.anon" );

						sub_item.setMenu(sub_menu);
	
						String net = i==0?AENetworkClassifier.AT_PUBLIC:AENetworkClassifier.AT_I2P;

						sub_menu.addMenuListener(
							new MenuAdapter()
							{
								@Override
								public void
								menuShown(
									MenuEvent e)
								{
									for ( org.eclipse.swt.widgets.MenuItem mi: sub_menu.getItems()){

										mi.dispose();
									}

									List<ChatInstance> chats = BuddyPluginUtils.getChats();

									for ( ChatInstance chat: chats ){
										
										if ( chat.getNetwork() == net ){
											
											org.eclipse.swt.widgets.MenuItem mi = new org.eclipse.swt.widgets.MenuItem(sub_menu, SWT.RADIO);

											mi.setText(chat.getKey());

											mi.setSelection( existing_chat!=null && existing_chat.equals(chat.getNetAndKey()));
											
											mi.addListener(SWT.Selection, new Listener() {
												@Override
												public void handleEvent(Event event){
													
													listener.chatSelected( null, chat.getNetAndKey());
													
													listener.chatAvailable( null, chat);
												}});
										}
									}
								}
							});
					}
				}
			});
	}
	
		/**
		 * Adds a separator if the current last item isn't one already
		 * @param menu
		 */
	
	public static void
	addSeparator(
		Menu		menu )
	{
		if ( menu.isDisposed() || menu.getItemCount() == 0 ){
			return;
		}
		
		org.eclipse.swt.widgets.MenuItem[] items = menu.getItems();
		
		if ( items[items.length-1].getStyle() != SWT.SEPARATOR ){
			
			new org.eclipse.swt.widgets.MenuItem( menu, SWT.SEPARATOR );
		}
	}
	
	public static void
	addColourChooser(
		Menu			menu,
		String			item_resource,
		boolean			can_clear,
		List<RGB>		existing,
		Consumer<RGB>	receiver )
	{
		final Menu sel_menu = new Menu( menu.getShell(), SWT.DROP_DOWN);

		org.eclipse.swt.widgets.MenuItem sel_item = new org.eclipse.swt.widgets.MenuItem( menu, SWT.CASCADE);

		Messages.setLanguageText( sel_item, item_resource );

		sel_item.setMenu( sel_menu );
		
		org.eclipse.swt.widgets.MenuItem itemSetColor = new org.eclipse.swt.widgets.MenuItem(sel_menu, SWT.PUSH);
		Messages.setLanguageText(itemSetColor, "label.set");
		itemSetColor.addListener(SWT.Selection, event -> {
			ColorDialog cd = new ColorDialog(menu.getShell());

			List<RGB> customColors = Utils.getCustomColors();
			
			Map<RGB, Long> mapDupCount = new HashMap<>();
			for ( RGB rgb: existing ){
				if (!customColors.contains(rgb)) {
					customColors.add(0, rgb);
				} else {
					Long count = mapDupCount.get(rgb);
					mapDupCount.put(rgb, count == null ? 1 : count + 1);
				}
			}
			
			long maxCount = 0;
			RGB selectRGB = null;
			for (RGB rgb : mapDupCount.keySet()) {
				Long count = mapDupCount.get(rgb);
				if (count != null && count > maxCount) {
					maxCount = count;
					selectRGB = rgb;
				}
			}
			
			cd.setRGBs(customColors.toArray(new RGB[0]));
			if (selectRGB != null) {
				cd.setRGB(selectRGB);
			}

			RGB rgbChosen = cd.open();
			
			if ( rgbChosen != null ){
			
				receiver.accept(rgbChosen);
			}	
		});
		
		org.eclipse.swt.widgets.MenuItem clear_item = new org.eclipse.swt.widgets.MenuItem( sel_menu, SWT.PUSH);
		Messages.setLanguageText(clear_item, "Button.clear");
		clear_item.addListener(SWT.Selection, event -> {
			receiver.accept(null);
		});
		clear_item.setEnabled( can_clear );
	}
}
