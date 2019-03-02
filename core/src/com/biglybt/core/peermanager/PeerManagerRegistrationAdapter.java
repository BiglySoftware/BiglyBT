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

import java.net.InetSocketAddress;

import com.biglybt.core.networkmanager.NetworkConnection;

public interface
PeerManagerRegistrationAdapter
{
	public byte[]
	getHashOverride();
	
	public byte[][]
	getSecrets();

	public int
	getLocalPort(
		boolean	only_if_allocated );
	
	public boolean
	manualRoute(
		NetworkConnection		connection );

	public boolean
	isPeerSourceEnabled(
		String		peer_source );

	public boolean
	activateRequest(
		InetSocketAddress		remote_address );

	public void
	deactivateRequest(
		InetSocketAddress		remote_address );

	public String
	getDescription();
}
