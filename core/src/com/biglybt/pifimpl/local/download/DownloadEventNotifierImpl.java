/*
 * Created on 12 Feb 2007
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
 */
package com.biglybt.pifimpl.local.download;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.download.*;
import com.biglybt.pif.peers.PeerManager;
import com.biglybt.pif.torrent.TorrentAttribute;

/**
 * This is an implementation of DownloadEventNotifier to be simplify life for
 * plugins if they want to register event listeners across all downloads managed
 * by the client.
 */
public class DownloadEventNotifierImpl implements DownloadEventNotifier {

	private DownloadActivationNotifier download_activation_notifier;
	private DownloadNotifier download_notifier;
	private DownloadPeerNotifier download_peer_notifier;
	private DownloadTrackerNotifier download_tracker_notifier;
	private DownloadTrackerNotifier download_tracker_notifier_instant;
	private DownloadWillBeRemovedNotifier download_will_be_removed_notifier;
	private DownloadCompletionNotifier download_completion_notifier;
	private DownloadManager dm;

	private HashMap read_attribute_listeners;
	private HashMap write_attribute_listeners;

	public DownloadEventNotifierImpl(DownloadManager dm) {
		this.dm = dm;
		this.download_activation_notifier = new DownloadActivationNotifier();
		this.download_notifier = new DownloadNotifier();
		this.download_peer_notifier = new DownloadPeerNotifier();
		this.download_tracker_notifier = new DownloadTrackerNotifier(false);
		this.download_tracker_notifier_instant = new DownloadTrackerNotifier(true);
		this.download_will_be_removed_notifier = new DownloadWillBeRemovedNotifier();
		this.download_completion_notifier = new DownloadCompletionNotifier();

		this.read_attribute_listeners = new HashMap();
		this.write_attribute_listeners = new HashMap();
	}

	@Override
	public void addActivationListener(DownloadActivationListener l) {
		this.download_activation_notifier.addListener(l);
	}

	@Override
	public void addCompletionListener(DownloadCompletionListener l) {
		this.download_completion_notifier.addListener(l);
	}

	@Override
	public void addDownloadWillBeRemovedListener(DownloadWillBeRemovedListener l) {
		this.download_will_be_removed_notifier.addListener(l);
	}

	@Override
	public void addListener(DownloadListener l) {
		this.download_notifier.addListener(l);
	}

	@Override
	public void addPeerListener(DownloadPeerListener l) {
		this.download_peer_notifier.addListener(l);
	}

	@Override
	public void addTrackerListener(DownloadTrackerListener l) {
		this.download_tracker_notifier.addListener(l);
	}

	@Override
	public void addTrackerListener(DownloadTrackerListener l, boolean immediateTrigger) {
		(immediateTrigger ? this.download_tracker_notifier_instant : this.download_tracker_notifier).addListener(l);
	}

	@Override
	public void removeActivationListener(DownloadActivationListener l) {
		this.download_activation_notifier.removeListener(l);
	}

	@Override
	public void removeCompletionListener(DownloadCompletionListener l) {
		this.download_completion_notifier.removeListener(l);
	}

	@Override
	public void removeDownloadWillBeRemovedListener(DownloadWillBeRemovedListener l) {
		this.download_will_be_removed_notifier.removeListener(l);
	}

	@Override
	public void removeListener(DownloadListener l) {
		this.download_notifier.removeListener(l);
	}

	@Override
	public void removePeerListener(DownloadPeerListener l) {
		this.download_peer_notifier.removeListener(l);
	}

	@Override
	public void removeTrackerListener(DownloadTrackerListener l) {
		// We don't know which notifier we added it to, so remove it from both.
		this.download_tracker_notifier.removeListener(l);
		this.download_tracker_notifier_instant.removeListener(l);
	}

	@Override
	public void addAttributeListener(DownloadAttributeListener listener, TorrentAttribute ta, int event_type) {
		Map attr_map = getAttributeListenerMap(event_type);
		DownloadAttributeNotifier l = (DownloadAttributeNotifier)attr_map.get(ta);
		if (l == null) {
			l = new DownloadAttributeNotifier(ta, event_type);
			attr_map.put(ta, l);
		}
		l.addListener(listener);
	}

	@Override
	public void removeAttributeListener(DownloadAttributeListener listener, TorrentAttribute ta, int event_type) {
		Map attr_map = getAttributeListenerMap(event_type);
		DownloadAttributeNotifier l = (DownloadAttributeNotifier)attr_map.get(ta);
		if (l == null) {return;}
		l.removeListener(listener);
	}

	private abstract class BaseDownloadListener implements DownloadManagerListener {
		protected ArrayList listeners = new ArrayList();
		private AEMonitor this_mon;

		private BaseDownloadListener() {
			this_mon = new AEMonitor(getClass().getName());
		}

		void addListener(Object o) {
			boolean register_with_downloads = false;
			try {
				this_mon.enter();
				register_with_downloads = listeners.isEmpty();
				ArrayList new_listeners = new ArrayList(listeners);
				new_listeners.add(o);
				this.listeners = new_listeners;
			}
			finally {
				this_mon.exit();
			}
			if (register_with_downloads) {
				dm.addListener(this, true);
			}
		}

		void removeListener(Object o) {
			boolean unregister_from_downloads = false;
			try {
				this_mon.enter();
				ArrayList new_listeners = new ArrayList(listeners);
				new_listeners.remove(o);
				this.listeners = new_listeners;
				unregister_from_downloads = this.listeners.isEmpty();
			}
			finally {
				this_mon.exit();
			}
			if (unregister_from_downloads) {
				dm.removeListener(this, true);
			}
		}

	}

	public class DownloadActivationNotifier extends BaseDownloadListener implements DownloadActivationListener {
		@Override
		public void downloadAdded(Download download) {download.addActivationListener(this);}
		@Override
		public void downloadRemoved(Download download) {download.removeActivationListener(this);}
		@Override
		public boolean activationRequested(DownloadActivationEvent event) {
			Iterator itr = this.listeners.iterator();
			while (itr.hasNext()) {
				try {if (((DownloadActivationListener)itr.next()).activationRequested(event)) {return true;}}
				catch (Throwable t) {Debug.printStackTrace(t);}
			}
			return false;
		}
	}

	public class DownloadCompletionNotifier extends BaseDownloadListener implements DownloadCompletionListener {
		@Override
		public void downloadAdded(Download download) {download.addCompletionListener(this);}
		@Override
		public void downloadRemoved(Download download) {download.removeCompletionListener(this);}
		@Override
		public void onCompletion(Download download) {
			Iterator itr = this.listeners.iterator();
			while (itr.hasNext()) {
				try {((DownloadCompletionListener)itr.next()).onCompletion(download);}
				catch (Throwable t) {Debug.printStackTrace(t);}
			}
		}
	}

	public class DownloadNotifier extends BaseDownloadListener implements DownloadListener {
		@Override
		public void downloadAdded(Download download) {download.addListener(this);}
		@Override
		public void downloadRemoved(Download download) {download.removeListener(this);}
		@Override
		public void stateChanged(Download download, int old_state, int new_state) {
			Iterator itr = this.listeners.iterator();
			while (itr.hasNext()) {
				try {((DownloadListener)itr.next()).stateChanged(download, old_state, new_state);}
				catch (Throwable t) {Debug.printStackTrace(t);}
			}
		}
		@Override
		public void positionChanged(Download download, int old_position, int new_position) {
			Iterator itr = this.listeners.iterator();
			while (itr.hasNext()) {
				try {((DownloadListener)itr.next()).positionChanged(download, old_position, new_position);}
				catch (Throwable t) {Debug.printStackTrace(t);}
			}
		}
	}

	public class DownloadPeerNotifier extends BaseDownloadListener implements DownloadPeerListener {
		@Override
		public void downloadAdded(Download download) {download.addPeerListener(this);}
		@Override
		public void downloadRemoved(Download download) {download.removePeerListener(this);}
		@Override
		public void	peerManagerAdded(Download download, PeerManager peer_manager) {
			Iterator itr = this.listeners.iterator();
			while (itr.hasNext()) {
				try {((DownloadPeerListener)itr.next()).peerManagerAdded(download, peer_manager);}
				catch (Throwable t) {Debug.printStackTrace(t);}
			}
		}
		@Override
		public void	peerManagerRemoved(Download download, PeerManager peer_manager) {
			Iterator itr = this.listeners.iterator();
			while (itr.hasNext()) {
				try {((DownloadPeerListener)itr.next()).peerManagerRemoved(download, peer_manager);}
				catch (Throwable t) {Debug.printStackTrace(t);}
			}
		}
	}

	public class DownloadWillBeRemovedNotifier extends BaseDownloadListener implements DownloadWillBeRemovedListener {
		@Override
		public void downloadAdded(Download download) {download.addDownloadWillBeRemovedListener(this);}
		@Override
		public void downloadRemoved(Download download) {download.removeDownloadWillBeRemovedListener(this);}
		@Override
		public void	downloadWillBeRemoved(Download download) throws DownloadRemovalVetoException {
			Iterator itr = this.listeners.iterator();
			while (itr.hasNext()) {
				try {((DownloadWillBeRemovedListener)itr.next()).downloadWillBeRemoved(download);}
				catch (DownloadRemovalVetoException e) {throw e;}
				catch (Throwable t) {Debug.printStackTrace(t);}
			}
		}
	}

	public class DownloadAttributeNotifier extends BaseDownloadListener implements DownloadAttributeListener {
		private TorrentAttribute ta;
		private int event_type;
		public DownloadAttributeNotifier(TorrentAttribute ta, int event_type) {
			this.ta = ta; this.event_type = event_type;
		}

		@Override
		public void downloadAdded(Download d) {d.addAttributeListener(this, ta, event_type);}
		@Override
		public void downloadRemoved(Download d) {d.removeAttributeListener(this, ta, event_type);}
		@Override
		public void attributeEventOccurred(Download d, TorrentAttribute ta, int event_type) {
			Iterator itr = this.listeners.iterator();
			while (itr.hasNext()) {
				try {((DownloadAttributeListener)itr.next()).attributeEventOccurred(d, ta, event_type);}
				catch (Throwable t) {Debug.printStackTrace(t);}
			}
		}
	}

	public class DownloadTrackerNotifier extends BaseDownloadListener implements DownloadTrackerListener {
		private boolean instant_notify;
		public DownloadTrackerNotifier(boolean instant_notify) {this.instant_notify = instant_notify;}
		@Override
		public void downloadAdded(Download download) {download.addTrackerListener(this, this.instant_notify);}
		@Override
		public void downloadRemoved(Download download) {download.removeTrackerListener(this);}
		@Override
		public void scrapeResult(DownloadScrapeResult result) {
			Iterator itr = this.listeners.iterator();
			while (itr.hasNext()) {
				try {((DownloadTrackerListener)itr.next()).scrapeResult(result);}
				catch (Throwable t) {Debug.printStackTrace(t);}
			}
		}
		@Override
		public void announceResult(DownloadAnnounceResult result) {
			Iterator itr = this.listeners.iterator();
			while (itr.hasNext()) {
				try {((DownloadTrackerListener)itr.next()).announceResult(result);}
				catch (Throwable t) {Debug.printStackTrace(t);}
			}
		}
	}

	private Map getAttributeListenerMap(int event_type) {
		if (event_type == DownloadAttributeListener.WRITTEN)
			return this.write_attribute_listeners;
		else if (event_type == DownloadAttributeListener.WILL_BE_READ)
			return this.read_attribute_listeners;
		throw new IllegalArgumentException("invalid event type " + event_type);
	}

}
