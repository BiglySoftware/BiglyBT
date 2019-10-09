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

import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.pif.peers.Peer;
import com.biglybt.plugin.net.buddy.tracker.BuddyPluginTracker;

public class
PartialBuddy
{
	private final BuddyPluginTracker tracker;
	
	private final String	ip;
	private final int		tcp_port;
	private final int		udp_port;

	private final String 	key;
	private final String	name;
	
	public static String
	getPartialBuddyKey(
		Peer				peer )
	{
		String ip = peer.getIp();
		
		if ( AENetworkClassifier.categoriseAddress( ip ) == AENetworkClassifier.AT_PUBLIC ){

			return( ip + "/" + peer.getTCPListenPort() + "/" + peer.getUDPListenPort());
			
		}else{
			
			return( ip );
		}
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
				
		String n = ip;
		
		if ( AENetworkClassifier.categoriseAddress( ip ) == AENetworkClassifier.AT_PUBLIC ){
		
			key = ip + "/" + tcp_port + "/" + udp_port;

			if ( n.contains( ":" )){
			
					// ipv6
				
				n = "[" + ip + "]";
			}
			
			if ( tcp_port==0){
				if ( udp_port!=0){
					n += ":0/" + udp_port;
				}
			}else{
				n += ":" + tcp_port;
				if ( tcp_port != udp_port ){
					n+= "/" + udp_port;
				}
			}
		}else{
			
			key = ip;
		}
		
		name = n;
	}
	
	public String
	getIP()
	{
		return( ip );
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
	getName()
	{
		return( name );
	}
	
	public String
	toString()
	{
		return( key );
	}
}