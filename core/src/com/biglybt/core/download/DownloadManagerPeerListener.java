/*
 * File    : DownloadManagerListener.java
 * Created : 19-Oct-2003
 * By      : stuff
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

package com.biglybt.core.download;

/**
 * @author parg
 *
 */

import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;

public interface
DownloadManagerPeerListener
{
		/**
		 * Called when a peer manager is created but not yet started
		 * @param manager
		 */

	public void
	peerManagerWillBeAdded(
		PEPeerManager	manager );

	public void
	peerManagerAdded(
		PEPeerManager	manager );

	public void
	peerManagerRemoved(
		PEPeerManager	manager );

	public void
	peerAdded(
		PEPeer 	peer );

	public void
	peerRemoved(
		PEPeer	peer );
}
