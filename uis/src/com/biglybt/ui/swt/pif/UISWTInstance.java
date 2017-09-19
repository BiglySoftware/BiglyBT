/*
 * Created on 05-Sep-2005
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

package com.biglybt.ui.swt.pif;

import com.biglybt.pif.ui.UIManagerListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.pif.ui.tables.TableManager;

/**
 * Tools to manage a SWT Instance
 *
 * @see com.biglybt.pif.ui.UIManagerListener
 * @see com.biglybt.pif.ui.UIManager#addUIListener(UIManagerListener)
 */
public interface UISWTInstance extends UIInstance {
	/** ID of main view */
	public static final String VIEW_MAIN = "Main";

	/**
	 * ID of "My Torrents" view
	 *
	 * @since 1.0.0.0
	 */
	public static final String VIEW_MYTORRENTS 	= "MyTorrents";

	/**
	 * ID of the torrent details view (The one you see when you right click on a torrent in Library and select "Details"
	 *
	 * @since 4901
	 */

	public static final String VIEW_TORRENT_DETAILS 	= "TorrentDetailsView";

	/**
	 * ID of "Peers" view
	 *
	 * @since 1.0.0.0
	 */
	public static final String VIEW_TORRENT_PEERS = TableManager.TABLE_TORRENT_PEERS;

	/**
	 * ID of "Pieces" view
	 *
	 * @since 1.0.0.0
	 */
	public static final String VIEW_TORRENT_PIECES = TableManager.TABLE_TORRENT_PIECES;

	/**
	 * ID of "Files" view
	 *
	 * @since 1.0.0.0
	 */
	public static final String VIEW_TORRENT_FILES = TableManager.TABLE_TORRENT_FILES;

	/**
	 * ID of the top bar of az3ui
	 *
	 * @since 1.0.0.0
	 */
	public static final String VIEW_TOPBAR = "TopBar";

	public static final String VIEW_STATISTICS 	= "StatsView";

	public static final String VIEW_CONFIG 		= "ConfigView";

	public static final String VIEW_SIDEBAR_AREA = "SideBarArea";


	/** Retrieve the SWT Display object that Azureus uses (when in SWT mode).
	 * If you have a thread that does some periodic/asynchronous stuff, Azureus
	 * will crashes with and 'InvalidThreadAccess' exception unless you
	 * embed your calls in a Runnable, and use getDisplay().aSyncExec(Runnable r);
	 *
	 * @return SWT Display object that Azureus uses
	 *
	 * @since 1.0.0.0
	 */
	public Display getDisplay();

	public Image
	loadImage(
		String	resource );

	/** Creates an UISWTGraphic object with the supplied SWT Image
	 *
	 * @param img Image to assign to the object
	 * @return a new UISWTGraphic object
	 *
	 * @since 1.0.0.0
	 */
	public UISWTGraphic createGraphic(Image img);

	/**
	 * Add a detail view to an Azureus parent view.  For views added to the Main
	 * window, this adds a menu option.  For the other parent views, this adds
	 * a new tab within Azureus' own detail view.
	 *
	 * @param sParentID VIEW_* constant
	 * @param sViewID of your view.  Used as part of the resource id.<br>
	 *          "Views.plugins." + ID + ".title" = title of your view
	 * @param l Listener to be triggered when parent view wants to tell you
	 *           an event has happened
	 *
	 * @note If you want the window to auto-open, use openMainView when you gain
	 *        access to the UISWTInstance
	 *
	 * @since 1.0.0.0
	 */
	public void addView(String sParentID, String sViewID, UISWTViewEventListener l);

	/**
	 * Add a view to an Apps parent view.  For views added to the {@link #VIEW_MAIN}
	 * window, this adds a menu option.
	 * <P>
	 * In comparison to {@link #addView(String, String, UISWTViewEventListener)},
	 * this method saves memory by not creating the {@link UISWTViewEventListener}
	 * until it is needed.  It also ensures that only one
	 * {@link UISWTViewEvent#TYPE_CREATE} event is triggered per instance.
	 *
	 * @param sParentID VIEW_* constant
	 * @param sViewID of your view.  Used as part of the resource id.<br>
	 *          "Views.plugins." + ID + ".title" = title of your view
	 * @param cla Class of the Listener to be created and triggered
	 *
	 * @note If you want the window to auto-open, use openMainView when you gain
	 *        access to the UISWTInstance
	 *
	 * @since 1.0.0.0
	 */
	public void addView(String sParentID, String sViewID,
			Class<? extends UISWTViewEventListener> cla, Object initalDatasource);

	/**
	 * Open a previously added view
	 *
	 * @param sParentID ParentID of the view to be shown
	 * @param sViewID id of the view to be shown
	 * @param dataSource any data you need to pass the view
	 * @return success level
	 *
	 * @since 1.0.0.0
	 */
	public boolean openView(String sParentID, String sViewID, Object dataSource);

	/**
	 * Open a previously added view
	 *
	 * @param sParentID ParentID of the view to be shown
	 * @param sViewID id of the view to be shown
	 * @param dataSource any data you need to pass the view
	 * @param setfocus <tt>true</tt> if you want to display the view immediately,
	 *   <tt>false</tt> if you want to display it in the background.
	 * @return success level
	 * @since 1.0.0.0
	 */
	public boolean openView(String sParentID, String sViewID, Object dataSource, boolean setfocus);


	/**
	 * Create and open a view in the main window immediately.  If you are calling
	 * this from {@link com.biglybt.pif.ui.UIManagerListener#UIAttached(UIInstance)},
	 * the view will not gain focus.
	 * <p>
	 * Tip: You can add a menu item to a table view, and when triggered, have
	 *      it open a new window, passing the datasources that were selected
	 *
	 * @param sViewID ID to give your view
	 * @param l Listener to be triggered when View Events occur
	 * @param dataSource objects to set {@link UISWTView#getDataSource()} with
	 *
	 * @since 1.0.0.0
	 */
	public void openMainView(String sViewID, UISWTViewEventListener l,
			Object dataSource);

	/**
	 * Create and open a view in the main window immediately.  If you are calling
	 * this from {@link com.biglybt.pif.ui.UIManagerListener#UIAttached(UIInstance)},
	 * the view will not gain focus.
	 * <p>
	 * Tip: You can add a menu item to a table view, and when triggered, have
	 *      it open a new window, passing the datasources that were selected
	 *
	 * @param sViewID ID to give your view
	 * @param l Listener to be triggered when View Events occur
	 * @param dataSource objects to set {@link UISWTView#getDataSource()} with
	 * @param setfocus <tt>true</tt> if you want to display the view immediately,
	 *   <tt>false</tt> if you want to display it in the background.
	 *
	 * @since 1.0.0.0
	 */
	public void openMainView(String sViewID, UISWTViewEventListener l,
			Object dataSource, boolean setfocus);

	/**
	 * Remove all views that belong to a specific parent and of a specific View
	 * ID.  If the parent is the main window, the menu item will be removed.<br>
	 * If you wish to remove (close) just one view, use
	 * {@link UISWTView#closeView()}
	 *
	 * @param sParentID One of VIEW_* constants
	 * @param sViewID View ID to remove
	 *
	 * @since 1.0.0.0
	 */
	public void removeViews(String sParentID, String sViewID);

	/**
	 * Get a list of views currently open on the specified VIEW_* view
	 *
	 * @param sParentID VIEW_* constant
	 * @return list of views currently open
	 *
	 * @since 1.0.0.0
	 */
	public UISWTView[] getOpenViews(String sParentID);

	/**
	 *
	 * @since 1.0.0.0
	 */
	public UISWTViewEventListenerWrapper[]
	getViewListeners(
		String sParentID );

	/**
	 * Shows or hides a download bar for a given download.
	 *
	 * @since 1.0.0.0
	 * @param download Download to use.
	 * @param display <tt>true</tt> to show a download bar, <tt>false</tt> to hide it.
	 */
	public void showDownloadBar(Download download, boolean display);

	/**
	 * Shows or hides the transfers bar.
	 *
	 * @since 1.0.0.0
	 * @param display <tt>true</tt> to show the bar, <tt>false</tt> to hide it.
	 */
	public void showTransfersBar(boolean display);

	/**
	 * Creates an entry in the status bar to display custom status information.
	 *
	 * @since 1.0.0.0
	 * @see UISWTStatusEntry
	 */
	public UISWTStatusEntry createStatusEntry();

	/**
	 * Opens the window linked to a given BasicPluginViewModel object.
	 *
	 * @return <tt>true</tt> if the view was opened successfully.
	 * @since 1.0.0.0
	 */
	@Override
	public boolean openView(BasicPluginViewModel model);

	/**
	 * Opens the window linked to a given BasicPluginViewModel object.
	 *
	 * @return <tt>true</tt> if the view was opened successfully.
	 * @since 1.0.0.0
	 */
	public void openConfig(BasicPluginConfigModel model);

	/**
	 * Creates a SWT Shell, ensuring Vuze knows about it (ie. Icon, "Window" menu)
	 *
	 * @param style
	 * @return
	 *
	 * @since 1.0.0.0
	 */
	public Shell createShell(int style);

	public interface
	UISWTViewEventListenerWrapper
		extends UISWTViewEventListener
	{
		public String
		getViewID();
	}
}
