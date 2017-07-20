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
 * Used for registering and unregistering plugins which connect to the mainline DHT
 * network. Plugins must implement the {@link MainlineDHTProvider} interface and
 * register with this class so that the client can indicate it provides DHT support.
 *
 * @since 3.0.4.3
 */
public interface MainlineDHTManager {

	/**
	 * Registers an object to be used for mainline DHT support. There is only one
	 * <i>slot</i> available, so if multiple plugins attempt to register themselves,
	 * only the last one will be used. If you pass <tt>null</tt> as an argument, it
	 * will cause the client to disable support for mainline DHT.
	 */
	public void setProvider(MainlineDHTProvider provider);

	/**
	 * Returns the current DHT provider, or <tt>null</tt> if there isn't one.
	 */
	public MainlineDHTProvider getProvider();

}
