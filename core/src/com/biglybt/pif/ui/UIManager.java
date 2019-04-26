/*
 * Created on 19-Apr-2004
 * Created by Paul Gardner
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

package com.biglybt.pif.ui;

import java.io.File;
import java.net.URL;
import java.util.Map;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.pif.ui.model.PluginConfigModel;
import com.biglybt.pif.ui.tables.TableManager;

/**
 * Management tools for the user interface.
 * <P>
 * To get an UIManager, see {@link PluginInterface#getUIManager()}
 *
 * @author parg
 */
public interface
UIManager
{
	/**
	 * Creates a basic plugin view model and places it inside the
	 * plugins section of the configuration page.
	 *
	 * @param section_name Message Bundle resource id for the config model.  This will be used as the name of the config page, as well as an unique id
	 * @return BasicPluginConfigModel
	 * @since 2.1.0.0
	 */
	public BasicPluginConfigModel
	createBasicPluginConfigModel(
		String		section_name );


	/**
	 * Creates a basic plugin view model and adds it to the plugin in one step.
	 * view is placed inside the plugins section of the configuration page.
	 *
	 * @param parent_section see {@link com.biglybt.pif.ui.config.ConfigSection}.SECTION_*
	 * @param section_name  Message Bundle resource id for the config model.  This will be used as the name of the config page, as well as an unique id
	 * @return BasicPluginConfigModel
	 * @since 2.1.0.0
	 */
	public BasicPluginConfigModel
	createBasicPluginConfigModel(
		String		parent_section,
		String		section_name );

	/**
	 *
	 * @return
	 * @since 2.5.0.1
	 */
	public PluginConfigModel[] getPluginConfigModels();

	/**
	 * Creates a basic plugin view model and adds it to the plugin in one step.
	 *
	 * @param name The name for the view (this should be localised text, rather
	 *     than a message string).
	 * @return BasicPluginViewModel
	 * @since 2.1.0.2
	 */
	public BasicPluginViewModel
	createBasicPluginViewModel(
		String			name );

	/**
	 * Creates a {@link BasicPluginViewModel} object primarily to be used for
	 * storing logging output. This is just a shortcut way of creating a log
	 * view for the logger channel.
	 *
	 * @param channel The {@link LoggerChannel} to associate with.
	 * @param use_plugin_name If set to <tt>true</tt>, the log view will be
	 *   taken from the plugin name, if <tt>false</tt>, it will be taken from
	 *   the channel name.
	 * @since 3.1.1.1
	 */
	public BasicPluginViewModel createLoggingViewModel(
		LoggerChannel channel, boolean use_plugin_name
	);

	/**
	 *
	 * @param data
	 * @throws UIException
	 * @since 2.1.0.0
	 */
	public void
	copyToClipBoard(
		String		data )

		throws UIException;

	/**
	 * Retrieve the Table Manager
	 *
	 * @return Table management functions
	 * @since 2.1.0.0
	 */
	public TableManager getTableManager();

	/**
	 *
	 * @param title_resource
	 * @param message_resource
	 * @param contents
	 * @since 2.3.0.5
	 */
	public void
	showTextMessage(
		String		title_resource,
		String		message_resource,
		String		contents );

	/**
	 *
	 * @param title_resource
	 * @param message_resource
	 * @param message_map - see UIManagerEvent.MT_x
	 * @return selected value
	 * @since 3.0.5.3
	 */
	public long
	showMessageBox(
		String					title_resource,
		String					message_resource,
		long					message_map );

	/**
	 * @param title_resource
	 * @param message_resource
	 * @param message_map - see UIManagerEvent.MT_x
	 * @param params - [ remember-id (String), remember-by-default (Boolean), remember-resource (String) ]
	 * @return selected value
	 * @since 4.8.1.3
	 */
	public long
	showMessageBox(
		String					title_resource,
		String					message_resource,
		long					message_map,
		Object[]				params );

	public static final String MB_PARAM_REMEMBER_ID		= "remember-id";		// String
	public static final String MB_PARAM_REMEMBER_BY_DEF	= "remember-by-def";	// Boolean
	public static final String MB_PARAM_REMEMBER_RES	= "remember-res";		// String
	public static final String MB_PARAM_REMEMBER_IF_ONLY_BUTTON	= "remember-if";		// Number
	public static final String MB_PARAM_AUTO_CLOSE_MS	= "auto-close-ms";		// Number

		/**
		 * @since 5621
		 * @param title_resource
		 * @param message_resource
		 * @param message_map
		 * @param params
		 * @return
		 */
	public long
	showMessageBox(
		String					title_resource,
		String					message_resource,
		long					message_map,
		Map<String,Object>		params );

		/**
		 * @since 2.3.0.6
		 * @param url
		 */
	public void
	openURL(
		URL		url )

		throws UIException;

		/**
		 *
		 * @param torrent
		 * @since 3.0.5.3
		 */
	public void
	openTorrent(
		Torrent		torrent );

	/**
	 * Open Config View to the section specified
	 *
	 * @param sectionID ID of section to open to.
	 *         {@link com.biglybt.pif.ui.config.ConfigSection}.SECTION_* constants
	 * @return true-Section opened; false-Section invalid or UI does not support config views
	 *
	 * @since 2.3.0.7
	 */
	public boolean showConfigSection(String sectionID);

	/**
	 * Retrieve the menu manager.
	 *
	 * @return Menu management functions
	 * @since 3.0.0.7
	 */
    public MenuManager getMenuManager();

		/**
		 * UIs should support generic UI-agnostic views such as the basic config model by default. The can also
		 * expose a UI-specific plugin interface to plugins via the UIInstance (see interface for details).
		 * To get access to this it is necessary to use the UIManagerListener
		 */

	/**
	 * attach a new UI
	 *
	 * @param factory
	 * @throws UIException
	 *
	 * @since 2.3.0.5
	 */
	public void
	attachUI(
		UIInstanceFactory		factory )

		throws UIException;

	/**
	 * detach a UI - can fail if the UI doesn't support detaching
	 *
	 * @param factory
	 * @throws UIException
	 *
	 * @since 2.3.0.5
	 */
	public void
	detachUI(
		UIInstanceFactory		factory )

		throws UIException;

	/**
	 * Listen for {@link UIManagerListener#UIAttached(UIInstance)} and
	 * {@link UIManagerListener#UIDetached(UIInstance)} events.  Typically,
	 * you hook this listener so you can access {@link UISWTInstance} when it
	 * gets created.
	 * <p/>
	 * Will fire UIAttached for managers already attached
	 *
	 * @since 2.3.0.5
	 */
  	public void
  	addUIListener(
  		UIManagerListener listener );

	/**
	 * Remove an existing {@link UIManagerListener}
	 *
	 * @param listener Listener to remove
	 *
	 * @since 2.3.0.5
	 */
 	public void
  	removeUIListener(
  		UIManagerListener listener );

	/**
	 * Add a listener that's triggered on when core/plugins need to do an
	 * UI-only task, such as display a message to a user, or open an URL in a
	 * browser.
	 * <p/>
	 * See {@link UIManagerEvent} for types of events
	 * 
	 * @since 2.3.0.5
	 */
 	public void
  	addUIEventListener(
  		UIManagerEventListener listener );

	/**
	 * Remove previously added UIManagerEventListener
	 * 
	 * @since 2.3.0.5
	 */
 	public void
  	removeUIEventListener(
  		UIManagerEventListener listener );

 	/**
 	 * Returns <tt>true</tt> if there any user interfaces currently attached to
 	 * Azureus.
 	 *
 	 * Note that this value may change over time, and that at the point when a
 	 * plugin is being initialised, there is no guarantee over whether if the
 	 * user interfaces will have been created yet or not.
 	 *
 	 * If you want to monitor what user interfaces are being attached / detached
 	 * from Azureus, you should add a <tt>UIListener</tt> to this object.
 	 *
 	 * @since 3.0.0.7
 	 */
 	public boolean hasUIInstances();

 	/**
 	 * Returns an array of all currently attached user interfaces.
 	 *
 	 * Note that this value may change over time, and that at the point when a
 	 * plugin is being initialised, there is no guarantee over whether if the
 	 * user interfaces will have been created yet or not.
 	 *
 	 * If you want to monitor what user interfaces are being attached / detached
 	 * from Azureus, you should add a <tt>UIListener</tt> to this object.
 	 *
 	 * @since 3.0.0.7
 	 */
 	public UIInstance[] getUIInstances();

 	/**
 	 * Retrieves a {@link UIInputReceiver} from any interface available, or
 	 * returns <tt>null</tt> if one is not available. This is a convenience
 	 * method to allow you to grab an instance without having to iterate over
 	 * any attached interfaces.
 	 *
 	 * @see UIInstance#getInputReceiver()
 	 * @since 3.0.5.3
 	 */
 	public UIInputReceiver getInputReceiver();

 	/**
 	 * Retrieves a {@link UIMessage} from any interface available, or
 	 * returns <tt>null</tt> if one is not available. This is a convenience
 	 * method to allow you to grab an instance without having to iterate over
 	 * any attached interfaces.
 	 *
 	 * @see UIInstance#createMessage()
 	 * @since 3.0.5.3
 	 */
 	public UIMessage createMessage();

 	/**
 	 * Opens up the file using the associated application.
 	 *
 	 * @param file The file to open.
 	 * @since 3.0.5.3
 	 */
 	public void openFile(File file);

 	/**
 	 * Shows the file in a file explorer application in its parent folder.
 	 *
 	 * @param file The file to show.
 	 * @since 3.0.5.3
 	 */
 	public void showFile(File file);

 	public void addDataSourceListener(UIDataSourceListener l, boolean triggerNow);
 	public void removeDataSourceListener(UIDataSourceListener l);
 	public Object getDataSource();

 	public void setEverythingHidden( boolean hidden );
 	
 	/**
 	 * @since BiglyBT 1.2.0.1
 	 */
 	
 	public void toggleEverythingHidden();
}
