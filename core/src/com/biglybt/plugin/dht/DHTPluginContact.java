/*
 * Created on 25-Feb-2005
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

package com.biglybt.plugin.dht;

import java.net.InetSocketAddress;
import java.util.Map;


/**
 * @author parg
 *
 */

public interface
DHTPluginContact
{
	public byte[]
	getID();

	public String
	getName();

	public InetSocketAddress
	getAddress();

	public byte
	getProtocolVersion();

	public int
	getNetwork();

	public Map<String,Object>
	exportToMap();

	public boolean
	isAlive(
		long		timeout );

	public void
	isAlive(
		long						timeout,
		DHTPluginOperationListener	listener );

	public boolean
	isOrHasBeenLocal();

	public Map
	openTunnel();

	public byte[]
	read(
		DHTPluginProgressListener	listener,
		byte[]						handler_key,
		byte[]						key,
		long						timeout );

	public void
	write(
		DHTPluginProgressListener	listener,
		byte[]						handler_key,
		byte[]						key,
		byte[]						data,
		long						timeout );

	public byte[]
	call(
		DHTPluginProgressListener	listener,
		byte[]						handler_key,
		byte[]						data,
		long						timeout );

	public String
	getString();
}
