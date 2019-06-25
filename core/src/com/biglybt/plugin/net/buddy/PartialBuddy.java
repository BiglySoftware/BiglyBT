/*
 * Copyright 2017 Bigly Software. All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */

package com.biglybt.plugin.net.buddy;

import com.biglybt.pif.peers.Peer;
import com.biglybt.plugin.net.buddy.tracker.BuddyPluginTracker;

public class
PartialBuddy
{
	private final BuddyPluginTracker tracker;
	
	public final String		ip;
	public final int		tcp_port;
	public final int		udp_port;

	private final String 	key;
	
	public static String
	getPartialBuddyKey(
		Peer				peer )
	{
		return( peer.getIp() + "/" + peer.getTCPListenPort() + "/" + peer.getUDPListenPort());
	}
	
	public
	PartialBuddy(
		BuddyPluginTracker	_tracker,
		Peer				peer )
	{
		tracker		= _tracker;
		
		ip			= peer.getIp();
		tcp_port	= peer.getTCPListenPort();
		udp_port	= peer.getUDPListenPort();
		
		key = ip + "/" + tcp_port + "/" + udp_port;
	}
	
	public String
	getDownloadsSummary()
	{
		return( tracker.getDownloadsSummary( this ));
	}
	
	public void
	remove()
	{
		tracker.removePartialBuddy( this );
	}
	
	public String
	getKey()
	{
		return( key );
	}
	
	public String
	toString()
	{
		return( key );
	}
}