/*
 * File    : DownloadManager.java
 * Created : 06-Jan-2004
 * By      : parg
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

package com.biglybt.pif.download;

import java.io.File;
import java.net.URL;
import java.util.Map;

import com.biglybt.pif.download.savelocation.DefaultSaveLocationManager;
import com.biglybt.pif.download.savelocation.SaveLocationManager;
import com.biglybt.pif.torrent.Torrent;

/**
 * The DownloadManager gives access to functions used to monitor and manage the client's downloads
 * @author parg
 */

public interface
DownloadManager
{
	/**
	 * Add a torrent from a file. This may prompt the user for a download location etc. if required.
	 * This is an async operation, so no Download is returned.
	 *
	 * If you want to force a download to be added without prompting the user, you should create a
	 * Torrent object first, and then use an alternative addDownload method.
	 *
	 * @see #addDownload(Torrent)
	 * @see #addDownload(Torrent, File, File)
	 * @see com.biglybt.pif.torrent.TorrentManager#createFromBEncodedFile(File) TorrentManager.createFromBEncodedFile
	 * @param torrent_file
	 * @throws DownloadException
   *
   * @since 2.0.7.0
	 */
	public void
	addDownload(
		File 	torrent_file )

		throws DownloadException;

	/**
	 * add a torrent from a URL. This will prompt the user for download location etc. if required
	 * This is an async operation so no Download returned
	 * @param url
	 * @throws DownloadException
   *
   * @since 2.0.7.0
	 */
	public void
	addDownload(
		URL		url )

		throws DownloadException;

	/**
	 * Add a torrent from a URL with explicit auto-download option
	 * @param url
	 * @param auto_download
	 * @throws DownloadException
	 * @since 2403
	 */

	public void
	addDownload(
		URL			url,
		boolean		auto_download )

		throws DownloadException;

	/**
	 * add a torrent from a URL. This will prompt the user for download location etc. if required
	 * This is an async operation so no Download returned
	 * @param url
	 * @param referer
	 * @throws DownloadException
	 *
	 * @since 2.1.0.6
	 */
	public void
	addDownload(
		URL		url,
		URL 	referer);

		/**
		 * add a torrent from a URL and use the supplied request properties
		 * @param url
		 * @param request_properties
		 * @since 3.0.5.3
		 */

	public void
	addDownload(
		URL		url,
		Map		request_properties );

	/**
	 * Add a torrent from a "Torrent" object. The default torrent file and data locations will be
	 * used if defined - a DownloadException will be thrown if they're not. You can explicitly set
	 * these values by using the {@link #addDownload(Torrent, File, File) addDownload(Torrent, File, File)} method.
	 * @param torrent
	 * @see #addDownload(Torrent, File, File)
	 * @return
   *
   * @since 2.0.8.0
	 */
	public Download
	addDownload(
		Torrent		torrent )

		throws DownloadException;


	/**
	 * Add a torrent from a "Torrent" object and point it at the data location.
	 *
	 * The torrent_location should be the location of where the torrent file is on disk.
	 * This will be the torrent file that the client will use internally. If null is passed,
	 * then a file to store the torrent data in will be automatically created by the client.
	 *
	 * @param torrent The torrent object to create a download with.
	 * @param torrent_location The location of the file on disk - if  <code>null</code>,
	 *   a file to store the torrent data into will be created automatically.
	 * @param data_location null -> user default data save location if defined
	 * @return
   * support for null params for torrent_location/data_location since 2.1.0.4
   * @since 2.0.7.0
	 */
	public Download
	addDownload(
		Torrent		torrent,
		File		torrent_location,
		File		data_location )

		throws DownloadException;

	/**
	 * Explicit way of adding a download in a stopped state
	 * @since 3013
	 * @param torrent
	 * @param torrent_location
	 * @param data_location
	 * @return
	 * @throws DownloadException
	 */

	public Download
	addDownloadStopped(
		Torrent		torrent,
		File		torrent_location,
		File		data_location )

		throws DownloadException;

	/**
	 * Add a non-persistent download. Such downloads are not persisted by the client and as such will
	 * not be remembered across an client close and restart.
	 * @param torrent
	 * @param torrent_location
	 * @param data_location
	 * @return
	 * @throws DownloadException
   *
   * @since 2.0.7.0
	 */
	public Download
	addNonPersistentDownload(
		Torrent		torrent,
		File		torrent_location,
		File		data_location )

		throws DownloadException;

	public Download
	addNonPersistentDownloadStopped(
		Torrent		torrent,
		File		torrent_location,
		File		data_location )

		throws DownloadException;

	/**
	 * Although non-persistent downloads themselves aren't rememebered across restarts, some internal stats
	 * are (for continuity, like total up/down, file allocation state) - this method allows this to be removed
	 * @param hash
	 */

	public void
	clearNonPersistentDownloadState(
		byte[]		hash );

	/**
	 * Gets the download for a particular torrent, returns null if not found
	 * @param torrent
	 * @return
   *
   * @since 2.0.7.0
	 */
	public Download
	getDownload(
		Torrent		torrent );

	/**
	 * Gets a download given its hash
	 * @param hash
	 * @return
	 * @throws DownloadException
	 * @since 2.3.0.7
	 */

	public Download
	getDownload(
		byte[]		hash )

		throws DownloadException;

	/**
	 * Gets all the downloads. Returned in Download "index" order
	 * @return
   *
   * @since 2.0.7.0
	 */
	public Download[]
	getDownloads();

	/**
	 * Gets all the downloads.
	 * @param bSorted true - Returned in Download "index" order.<BR>
	 *                false - Order not guaranteed.  Faster retrieval.
	 * @return array of Download object
   *
   * @since 2.0.8.0
	 */
	public Download[]
	getDownloads(boolean bSorted);

	/**
	 * pause all running downloads
	 * @since 2.1.0.5
	 *
	 */

	public void
	pauseDownloads();

	public boolean
	canPauseDownloads();

	/**
	 * resume previously paused downloads
	 * @since 2.1.0.5
	 */

	public void
	resumeDownloads();

	public boolean
	canResumeDownloads();

	/**
	 * starts all non-running downloads
	 * @since 2.1.0.5
	 */

	public void
	startAllDownloads();

	/**
	 * stops all running downloads
	 * @since 2.1.0.5
	 */

	public void
	stopAllDownloads();

	/**
	 * Get the download manager statistics
	 * @return
	 */

	public DownloadManagerStats
	getStats();

		/**
		 * indicates whether or not all active downloads are in a seeding (or effective) seeding state
		 * @since 2.3.0.5
		 * @return
		 */

	public boolean
	isSeedingOnly();

    /**
	 * Add a listener that will be informed when a download is added to and removed
	 * from the client.
	 * <p />
	 * Invoking this method is equivalent to <code>addListener(l, true)</code>.
	 *
	 * @param l The listener to add.
	 * @since 2.0.7.0
	 * @see #addListener(DownloadManagerListener, boolean)
	 */
	public void addListener(DownloadManagerListener l);

	/**
	 * Add a listener that will be informed when a download is added to and removed
	 * from the client.
	 * @param l The listener to add.
	 * @param notify_of_current_downloads <tt>true</tt> - if you want the listener to
	 *   have its {@link DownloadManagerListener#downloadAdded(Download) downloadAdded}
	 *   method invoked immediately with all downloads currently managed by the client.
	 *   <tt>false</tt> - if you only want to be notified about new downloads added after
	 *   this method is called.
	 * @since 3.0.0.7
	 */
	public void addListener(DownloadManagerListener l, boolean notify_of_current_downloads);

	/**
	 * Removes a previously added listener.
     * @param l The listener to remove.
	 * @param notify_of_current_downloads <tt>true</tt> - if you want the listener to
	 *   have its {@link DownloadManagerListener#downloadRemoved(Download) downloadRemoved}
	 *   method invoked immediately with all downloads currently managed by the client,
	 *   <tt>false</tt> otherwise.
	 *  @since 3.0.0.7
	 */
	public void removeListener(DownloadManagerListener l, boolean notify_of_current_downloads);

    /**
     * Removes a previously added listener.
     * <p />
     * Invoking this method is equivalent to <code>removeListener(l, false)</code>.
     * @see #removeListener(DownloadManagerListener, boolean)
     * @param l The listener to remove.
     * @since 2.0.7.0
     */
	public void	removeListener(DownloadManagerListener l);

	public void
	addDownloadWillBeAddedListener(
		DownloadWillBeAddedListener		listener );

	public void
	removeDownloadWillBeAddedListener(
		DownloadWillBeAddedListener		listener );

	/**
	 * Return a {@link DownloadEventNotifier} object which can be used as
	 * an easy way to register listeners against all downloads handled by
	 * the client.
	 *
	 * @since 3.0.1.5
	 */
	public DownloadEventNotifier getGlobalDownloadEventNotifier();

	/**
	 * Registers an object to be in control of determining the default save
	 * location for downloads. You can set it to <tt>null</tt> to remove any
	 * object which was previously set.
	 *
	 * <p>
	 *
	 * By default, the client will use its default save location manager which
	 * handles <tt>on-completion</tt> and <tt>on-removal</tt> events.
	 *
	 * @param manager The new manager object to use.
	 * @since 3.0.5.3
	 */
	public void setSaveLocationManager(SaveLocationManager manager);

	/**
	 * Returns the current {@link SaveLocationManager} object which decides
	 * where downloads should be placed.
	 *
	 * @return The manager object currently in use.
	 * @since 3.0.5.3
	 */
	public SaveLocationManager getSaveLocationManager();

	/**
	 * Returns the default {@link SaveLocationManager} object that controls
	 * where downloads should be placed.
	 *
	 * @return The default save location manager object.
	 * @since 3.0.5.3
	 */
	public DefaultSaveLocationManager getDefaultSaveLocationManager();

	public DownloadStub[]
	getDownloadStubs();

	public DownloadStub
	lookupDownloadStub(
		byte[]		hash );

	public int
	getDownloadStubCount();

	public void addDownloadStubListener( DownloadStubListener l, boolean inform_of_current );

	public void removeDownloadStubListener( DownloadStubListener l );
}
