/*
 * Created on 16 Jun 2006
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

package com.biglybt.core.networkmanager;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import com.biglybt.core.networkmanager.Transport.ConnectListener;

public interface
ProtocolEndpoint
{
	public static final int	PROTOCOL_TCP	= 1;
	public static final int	PROTOCOL_UDP	= 2;
	public static final int	PROTOCOL_UTP	= 3;

	public static final int CONNECT_PRIORITY_SUPER_HIGHEST	= 0;
	public static final int CONNECT_PRIORITY_HIGHEST		= 1;
	public static final int CONNECT_PRIORITY_HIGH			= 2;
	public static final int CONNECT_PRIORITY_MEDIUM			= 3;
	public static final int CONNECT_PRIORITY_LOW			= 4;


	public int
	getType();

	public ConnectionEndpoint
	getConnectionEndpoint();

	public void
	setConnectionEndpoint(
		ConnectionEndpoint	ce );

	public InetSocketAddress
	getAddress();

	public InetSocketAddress
	getAdjustedAddress(
		boolean		to_lan );

	public Transport
	connectOutbound(
		boolean				connect_with_crypto,
		boolean 			allow_fallback,
		byte[][]			shared_secrets,
		ByteBuffer			initial_data,
		int					priority,
		ConnectListener 	listener );

	public String
	getDescription();
}
