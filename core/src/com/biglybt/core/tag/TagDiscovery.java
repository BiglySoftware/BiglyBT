/*
 * Created on Oct 3, 2014
 *
 * Copyright Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.tag;

import com.biglybt.core.util.SystemTime;

/**
 * @author TuxPaper
 * @created Oct 3, 2014
 *
 */
public class TagDiscovery
{
	public static int DISCOVERY_TYPE_RCM = 0;
	public static int DISCOVERY_TYPE_META_PARSE = 1; // derived from torrent (name, tracker, etc)
	//public static int DISCOVERY_TYPE_PEER = 2; // Not used, but if we ever get tags from connected peers..


	private final String name;
	private final String torrentName;
	private final String network;
	private final byte[] hash;

	private final long timestamp;
	private int discoveryType;

	public TagDiscovery(String name, String network, String torrentName, byte[] hash) {
		super();
		this.name = name;
		this.network = network;
		this.torrentName = torrentName;
		this.hash = hash;
		this.timestamp = SystemTime.getCurrentTime();
	}

	public String getName() {
		return name;
	}

	public String getNetwork(){
		return network;
	}

	public String getTorrentName() {
		return torrentName;
	}

	public byte[] getHash() {
		return hash;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public long getDiscoveryType() {
		return discoveryType;
	}
}
