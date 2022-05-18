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

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadTypeComplete;
import com.biglybt.pif.download.DownloadTypeIncomplete;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManagerListener;
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
	 * <p/>
	 * If you are making a view that applies to any view showing Downloads, 
	 * use {@link #registerView(Class, UISWTViewBuilder)} with
	 * <code>{@link Download}.class</code> instead 
	 *
	 * @since Azureus 2.3.0.7
	 */
	public static final String VIEW_MYTORRENTS 	= "MyTorrents";

	/**
	 * ID of the torrent details view (The one you see when you right click on a torrent in Library and select "Details"
	 * <p/>
	 * If you are making a view that applies to any view showing Downloads, 
	 * use {@link #registerView(Class, UISWTViewBuilder)} with
	 * <code>{@link Download}.class</code> instead 
	 *
	 * @since Vuze 4.9.0.1
	 */
	public static final String VIEW_TORRENT_DETAILS 	= "TorrentDetailsView";

	/**
	 * ID of "Peers" view
	 * <p/>
	 * If you are making a view that applies to any view showing Peers, 
	 * use {@link #registerView(Class, UISWTViewBuilder)} with 
	 * <code>{@link com.biglybt.pif.peers.Peer}.class</code>
	 * 
	 * @since Azureus 2.3.0.7
	 */
	public static final String VIEW_TORRENT_PEERS = TableManager.TABLE_TORRENT_PEERS;

	/**
	 * ID of the top bar, usually located just above the Open/Find Torrents bar
	 *
	 * @since Vuze 3.0.1.3
	 */
	public static final String VIEW_TOPBAR = "TopBar";

	public static final String VIEW_RIGHTBAR = "RightBar";

	/**
	 * Statistics View, usually invoked from Tools->Statistics
	 */
	public static final String VIEW_STATISTICS 	= "StatsView";

	/**
	 * ID for adding views to bottom of the sidebar
	 */
	public static final String VIEW_SIDEBAR_AREA = "SideBarArea";

	/**
	 * ID for adding views to the rightbar
	 */
	
	public static final String VIEW_RIGHTBAR_AREA = "RightBarArea";

	/** Retrieve the SWT Display object that Azureus uses (when in SWT mode).
	 * If you have a thread that does some periodic/asynchronous stuff, Azureus
	 * will crashes with and 'InvalidThreadAccess' exception unless you
	 * embed your calls in a Runnable, and use getDisplay().aSyncExec(Runnable r);
	 *
	 * @return SWT Display object that Azureus uses
	 *
	 * @since 2.3.0.5
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
	 * @since 2.3.0.5
	 */
	public UISWTGraphic createGraphic(Image img);

	/**
	 * Register a view that will be created when a certain datasource type is
	 * displayed
	 * <p/>
	 * For example, you can register an image rendering view for {@link com.biglybt.pif.disk.DiskManagerFileInfo}
	 * <p/>
	 * When plugin is unloaded, any registrations will be automatically unregistered, 
	 * and any views created from the builder will be automatically disposed
	 *
	 * @param forDataSourceType Class of datasource you want to add a view to,
	 *                          such as {@link Download}, {@link DownloadTypeComplete},
	 *                          {@link DownloadTypeIncomplete}
	 * @param viewBuilder use {@link #createViewBuilder(String, Class)}
	 * 
	 * @since BiglyBT 2.1.0.1
	 */
	void registerView(Class forDataSourceType, UISWTViewBuilder viewBuilder);

	/**
	 * Register a view that will be created when the specified view is displayed
	 * <p/>
	 * When plugin is unloaded, any registrations will be automatically unregistered, 
	 * and any views created from the builder will be automatically disposed
	 *
	 * @param forViewID VIEW_ Constant
	 * @param viewBuilder use {@link #createViewBuilder(String, Class)}
	 * 
	 * @since BiglyBT 2.1.0.1
	 */
	void registerView(String forViewID, UISWTViewBuilder viewBuilder);

	/**
	 * Creates an object representing how your view is to be created
	 * 
	 * @param viewID  Unique ID of your view
	 * @param cla 
	 *    A {@link UISWTViewEventListener} class that will be created when the UI shows the view.
	 *    <br/>
	 *    Since this class will be instantiated with cla.newInstance(), the class
	 *    must be a top-level class, and not a local or non-static nested class.
	 * 
	 * @return UISWTViewBuilder which has additional values you can set, such as initial datasource
	 *
	 * @since BiglyBT 2.1.0.1
	 */
	UISWTViewBuilder createViewBuilder(String viewID,
		Class<? extends UISWTViewEventListener> cla);

	/**
	 * Creates an object representing how your view is to be created.
	 * <p/>
	 * At minimum, you will need to specify a way to instanatiate your listener, with
	 * one of the following methods:
	 * <li>{@link UISWTViewBuilder#setListenerClass(Class)}</li>
	 * <li>{@link UISWTViewBuilder#setListenerInstantiator(boolean, UISWTViewBuilder.UISWTViewEventListenerInstantiator)}</li>
	 *
	 * @param viewID  Unique ID of your view
	 *
	 * @return UISWTViewBuilder which has additional values you can set, such as initial datasource
	 *
	 * @since BiglyBT 2.1.0.1
	 */
	UISWTViewBuilder createViewBuilder(String viewID);

	/**
	 * Unregister and dispose of any previously created views of this viewID
	 * @since BiglyBT 2.1.0.1
	 */
	void unregisterView(Class forDataSourceType, String viewID);

	/**
	 * Unregister and dispose of any previously created views of this viewID
	 * @since BiglyBT 2.1.0.1
	 */
	void unregisterView(String forViewID, String viewID);

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
	 * @since Azureus 2.3.0.6
	 * 
	 * @deprecated Use {@link #registerView(Class, UISWTViewBuilder)}
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
	 * @param sParentViewID VIEW_* constant
	 * @param sViewID of your view.  Used as part of the resource id.<br>
	 *          "Views.plugins." + ID + ".title" = title of your view
	 * @param cla Class of the Listener to be created and triggered
	 *
	 * @note If you want the window to auto-open, use openMainView when you gain
	 *        access to the UISWTInstance
	 *
	 * @since Vuze 4.6.0.5
	 * 
	 * @deprecated use {@link #registerView(Class, UISWTViewBuilder)} with
	 * {@link #createViewBuilder(String, Class)}<code>.setInitialDataSource(Object)</code>
	 */
	public void addView(String sParentViewID, String sViewID,
			Class<? extends UISWTViewEventListener> cla, Object initalDatasource);

	/**
	 * Open a previously added view
	 *
	 * @param sParentID ParentID of the view to be shown
	 * @param sViewID id of the view to be shown
	 * @param dataSource any data you need to pass the view
	 * @return success level
	 *
	 * @since 2.5.0.1
	 */
	public boolean openView(String sParentID, String sViewID, Object dataSource);

	/**
	 * Open a previously added view
	 *
	 * @param forViewID View that the opened view belongs to
	 * @param sViewID id of the view to be shown
	 * @param dataSource any data you need to pass the view
	 * @param setfocus <tt>true</tt> if you want to display the view immediately,
	 *   <tt>false</tt> if you want to display it in the background.
	 * @return success level
	 * @since 3.0.5.3
	 */
	public boolean openView(String forViewID, String sViewID, Object dataSource, boolean setfocus);


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
	 * @since Azureus 2.3.0.6
	 * 
	 * @deprecated Use {@link #registerView(String, UISWTViewBuilder)} and {@link #openView(String VIEW_MAIN, String, Object)}
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
	 * @since Vuze 3.0.5.3
	 * 
	 * @deprecated Use {@link #registerView(String, UISWTViewBuilder)} and {@link #openView(String VIEW_MAIN, String, Object, boolean)}
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
	 * @since 2.3.0.6
	 */
	public void removeViews(String sParentID, String sViewID);

	/**
	 * Get a list of views currently open on the specified VIEW_* view
	 *
	 * @param sParentID VIEW_* constant
	 * @return list of views currently open
	 *
	 * @since 2.3.0.6
	 */
	public UISWTView[] getOpenViews(String sParentID);

	/**
	 * Shows or hides a download bar for a given download.
	 *
	 * @since 3.0.0.5
	 * @param download Download to use.
	 * @param display <tt>true</tt> to show a download bar, <tt>false</tt> to hide it.
	 */
	public void showDownloadBar(Download download, boolean display);

	/**
	 * Shows or hides the transfers bar.
	 *
	 * @since 3.0.1.3
	 * @param display <tt>true</tt> to show the bar, <tt>false</tt> to hide it.
	 */
	public void showTransfersBar(boolean display);

	/**
	 * Creates an entry in the status bar to display custom status information.
	 *
	 * @since 3.0.0.7
	 * @see UISWTStatusEntry
	 */
	public UISWTStatusEntry createStatusEntry();

	/**
	 * Opens the window linked to a given BasicPluginViewModel object.
	 *
	 * @return <tt>true</tt> if the view was opened successfully.
	 * @since 3.0.5.3
	 */
	@Override
	public boolean openView(BasicPluginViewModel model);

	/**
	 * Opens the window linked to a given BasicPluginViewModel object.
	 *
	 * @return <tt>true</tt> if the view was opened successfully.
	 * @since 3.0.5.3
	 */
	public void openConfig(BasicPluginConfigModel model);

	/**
	 * Creates a SWT Shell, ensuring Vuze knows about it (ie. Icon, "Window" menu)
	 *
	 * @param style
	 * @return
	 *
	 * @since 4.2.0.9
	 */
	public Shell createShell(int style);
}
