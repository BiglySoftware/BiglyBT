/*
 * Created on 1 Nov 2006
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


package com.biglybt.core.networkmanager.admin;

import java.net.InetAddress;

public interface
NetworkAdminProtocol
{
	public static final int	PT_HTTP		= 1;
	public static final int	PT_TCP		= 2;
	public static final int	PT_UDP		= 3;

	public int
	getType();

	public int
	getPort();

	public String
	getTypeString();

	public InetAddress
	test(
		NetworkAdminNetworkInterfaceAddress	address )

		throws NetworkAdminException;

	public InetAddress
	test(
		NetworkAdminNetworkInterfaceAddress	address,
		NetworkAdminProgressListener		listener )

		throws NetworkAdminException;

	public InetAddress
	test(
		NetworkAdminNetworkInterfaceAddress	address,
		boolean								upnp_map,
		NetworkAdminProgressListener		listener )

		throws NetworkAdminException;

	public InetAddress
	test(
		NetworkAdminNetworkInterfaceAddress	address,
		boolean								ipv6,
		boolean								upnp_map,
		NetworkAdminProgressListener		listener )

		throws NetworkAdminException;

	
	public String
	getName();
}
