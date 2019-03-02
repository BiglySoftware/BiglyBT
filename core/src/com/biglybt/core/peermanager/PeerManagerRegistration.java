/*
 * Created on 12 Jul 2006
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

package com.biglybt.core.peermanager;

import java.util.List;

import com.biglybt.core.peer.impl.PEPeerControl;
import com.biglybt.core.torrent.TOTorrentFile;

public interface
PeerManagerRegistration
{
	public TOTorrentFile
	getLink(
		String			link );

    // Used by the CDN

    public void
	removeLink(
		String			link );

    // Used by the CDN

    public void
	addLink(
		String			link,
		TOTorrentFile	target )

		throws Exception;

	public void
	activate(
		PEPeerControl	peer_control );

	public void
	deactivate();

	public void
	unregister();

    // XXX: Doesn't appear to be used.
    public String
	getDescription();
    
    public List<PeerManagerRegistration>
    getOtherRegistrationsForHash();
    
    public int 
    getLocalPort(
    	boolean	only_if_allocated );
}
