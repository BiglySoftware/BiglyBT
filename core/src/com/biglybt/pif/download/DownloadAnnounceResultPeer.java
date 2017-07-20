/*
 * Created on 07-Feb-2005
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

package com.biglybt.pif.download;

import com.biglybt.core.peer.PEPeerSource;

/**
 * @author parg
 *
 */

public interface
DownloadAnnounceResultPeer
{
	public static final short	PROTOCOL_NORMAL		= 1;
	public static final short	PROTOCOL_CRYPT		= 2;


	/**
	 * Peer source for all peers discovered through a tracker announce
	 */
	public static final String	PEERSOURCE_BT_TRACKER		= PEPeerSource.PS_BT_TRACKER;
	/**
	 * Peer source for all peers discovered through other peers from a globally accessible gossiping protocl (usually a DHT)
	 */
	public static final String	PEERSOURCE_DHT				= PEPeerSource.PS_DHT;
	/**
	 * Peer source for all peers discovered through other peers in the same swarm via gossiping protocols
	 */
	public static final String	PEERSOURCE_PEX		= PEPeerSource.PS_OTHER_PEER;


	/**
	 * Peer source for all peers discovered by plugins that do not fall into the other categories
	 */
	public static final String	PEERSOURCE_PLUGIN			= PEPeerSource.PS_PLUGIN;

	/**
	 * Peer source for all peers which already connected to us but we know nothing about since the connection is incoming
	 */
	public static final String	PEERSOURCE_INCOMING			= PEPeerSource.PS_INCOMING;

	public String
	getSource();

	public int
	getPort();

	public int
	getUDPPort();

	public String
	getAddress();

	public byte[]
	getPeerID();

	public short
	getProtocol();
}
