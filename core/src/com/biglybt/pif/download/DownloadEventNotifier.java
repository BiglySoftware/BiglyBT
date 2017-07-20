/*
 * Created on 12 Feb 2007.
 * Created by Allan Crooks.
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it
 * and/or modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the License,
 * or (at your option) any later version. This program is distributed in the
 * hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details. You should have received a
 * copy of the GNU General Public License along with this program; if not, write
 * to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston,
 * MA 02111-1307, USA.
 */
package com.biglybt.pif.download;

import com.biglybt.pif.torrent.TorrentAttribute;

/**
 * This interface defines what methods an object should have defined to be able
 * to inform listener objects about various events which occur on a download.
 *
 * In previous versions of Azureus, the {@link Download} class was the only
 * interface which defined these methods - now
 * {@link DownloadManager#getGlobalDownloadEventNotifier} supports these methods too.
 *
 * @since 3.0.0.9
 * @author amc1
 */
public interface DownloadEventNotifier {

	/**
	 * Adds a listener that will be informed of changes to a download's state.
	 *
	 * @param l The listener to add.
	 * @since 2.0.7.0
	 */
	public void addListener(DownloadListener l);

	/**
	 * Removes a listener object added via the
	 * {@link #addListener(DownloadListener)} method.
	 *
	 * @param l The listener to remove.
	 * @since 2.0.7.0
	 */
	public void removeListener(DownloadListener l);

	/**
	 * Adds a listener that will be informed when the latest announce/scrape
	 * results change.
	 * <p>
	 * Listener events will be immediately triggered after the listener has been
	 * added.
	 *
	 * @param l The listener to add.
	 * @since 2.0.7.0
	 */
	public void addTrackerListener(DownloadTrackerListener l);

	/**
	 * Adds a listener that will be informed when the latest announce/scrape
	 * results change.
	 *
	 * @param l The listener to add.
	 * @param immediateTrigger Whether to immediately trigger listener's events.
	 * @since 2.4.0.3
	 */
	public void addTrackerListener(DownloadTrackerListener l, boolean immediateTrigger);

	/**
	 * Removes a listener object added via the
	 * {@link #addTrackerListener(DownloadTrackerListener)} or
	 * {@link #addTrackerListener(DownloadTrackerListener, boolean)} method.
	 *
	 * @param l The listener to remove.
	 * @since 2.0.7.0
	 */
	public void removeTrackerListener(DownloadTrackerListener l);

	/**
	 * Adds a listener that will be informed when a download is about to be
	 * removed. This gives the implementor the opportunity to veto the removal.
	 *
	 * @param l The listener to add.
	 * @since 2.0.7.0
	 */
	public void addDownloadWillBeRemovedListener(DownloadWillBeRemovedListener l);

	/**
	 * Removes a listener object added via the
	 * {@link #addDownloadWillBeRemovedListener(DownloadWillBeRemovedListener)}
	 * method.
	 *
	 * @param l The listener to remove.
	 * @since 2.0.7.0
	 */
	public void removeDownloadWillBeRemovedListener(
			DownloadWillBeRemovedListener l);

	/**
	 * Adds a listener that will be invoked when a request is made to activate a
	 * torrent.
	 *
	 * @param l The listener to add.
	 * @since 2.4.0.3
	 */

	public void addActivationListener(DownloadActivationListener l);

	/**
	 * Removes a listener object added via the
	 * {@link #addActivationListener(DownloadActivationListener)} method.
	 *
	 * @param l The listener to remove.
	 * @since 2.4.0.3
	 */

	public void removeActivationListener(DownloadActivationListener l);

	/**
	 * Adds a listener that will be informed of when peer managers have been
	 * added / removed for a download.
	 *
	 * @param l The listener to add.
	 * @since 2.1.0.0
	 */
	public void addPeerListener(DownloadPeerListener l);

	/**
	 * Removes a listener object added via the
	 * {@link #addPeerListener(DownloadPeerListener)} method.
	 *
	 * @param l The listener to remove.
	 * @since 2.1.0.0
	 */
	public void removePeerListener(DownloadPeerListener l);

	/**
	 * Adds a listener that will be informed of when attributes on a download
	 * have been read / modified.
	 *
	 * @param l The listener to add.
	 * @param attr The torrent attribute to be notified about.
	 * @param event_type The type of event to be notified of, as defined on {@link DownloadAttributeListener}.
	 * @since 3.0.3.5
	 */
	public void addAttributeListener(DownloadAttributeListener l, TorrentAttribute attr, int event_type);

	/**
	 * Removes a listener object added via the
	 * {@link #addAttributeListener(DownloadAttributeListener, TorrentAttribute, int) method.
	 *
	 * @param l The listener to remove.
	 * @param attr The torrent attribute which it registered with.
	 * @param event_type The type of event which it registered with.
	 * @since 3.0.3.5
	 */
	public void removeAttributeListener(DownloadAttributeListener l, TorrentAttribute attr, int event_type);

	/**
	 * Adds a listener that will be informed when a download moves into a
	 * completed state.
	 *
	 * @param l The listener to add.
	 * @since 3.0.5.3
	 */
	public void addCompletionListener(DownloadCompletionListener l);

	/**
	 * Removes a listener object removed via the
	 * {@link #addCompletionListener(DownloadCompletionListener)} method.
	 *
	 * @param l The listener to remove.
	 * @since 3.0.5.3
	 */
	public void removeCompletionListener(DownloadCompletionListener l);
}
