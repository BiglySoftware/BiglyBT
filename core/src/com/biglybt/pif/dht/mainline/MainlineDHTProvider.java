/*
 * Created on 14 Jan 2008
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
package com.biglybt.pif.dht.mainline;

/**
 * The interface that a plugin (or a helper class) must implement to
 * enable DHT support inside the client. By implementing this class, and
 * then registering it via {@link MainlineDHTManager#setProvider(MainlineDHTProvider)},
 * The client will indicate DHT support via the BT handshake, and exchange PORT
 * messages with other clients - passing received messages to this class.
 */
public interface MainlineDHTProvider {

	/**
	 * This method is called by the client when a PORT message is received
	 * by a peer. <b>Note:</b> When this method is called, try to make
	 * sure that it won't take long to return - if it needs to do something
	 * time-consuming, then the work should be delegated to another thread.
	 *
	 * @param ip_addr IP address of peer.
	 * @param port DHT port of peer.
	 */
	public void notifyOfIncomingPort(String ip_addr, int port);

	/**
	 * Returns the DHT port used by the plugin.
	 */
	public int getDHTPort();
}
