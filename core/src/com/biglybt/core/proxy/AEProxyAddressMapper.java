/*
 * Created on 15-Dec-2004
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

package com.biglybt.core.proxy;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Map;

/**
 * @author parg
 *
 */

public interface
AEProxyAddressMapper
{
	public static final String	MAP_PROPERTY_DISABLE_AZ_MESSAGING	= "AEProxyAddressMapper.disable.az.msg";
	public static final String	MAP_PROPERTY_PROTOCOL_QUALIFIER		= "AEProxyAddressMapper.prot.qual";
	public static final String	MAP_PROPERTY_CONNECTION_INCOMING	= "AEProxyAddressMapper.con.in";

		/**
		 * SOCKS 5 is limited to 255 char DNS names. So for longer ones (e.g. I2P 'names')
		 * we have to replace then with somethin shorter to get through the SOCKS layer
		 * and then remap them on the otherside.
		 * These functions are only active if a SOCKS proxy is enabled and looping back
		 * (in process is the assumption)
		 * @param address
		 * @return
		 */

	public String
	internalise(
		String	address );

	public String
	externalise(
		String	address );

	public URL
	internalise(
		URL		url );

	public URL
	externalise(
		URL		url );

	public PortMapping
	registerPortMapping(
		int						proxy_port,
		String					remote_ip );

	public PortMapping
	registerPortMapping(
		int						proxy_port,
		String					remote_ip,
		Map<String,Object>		properties );

	public PortMapping
	registerPortMapping(
		int						proxy_port,
		int						local_port,
		String					local_ip,
		int						remote_port,
		String					remote_ip,
		Map<String,Object>		properties );

	public AppliedPortMapping
	applyPortMapping(
		InetAddress		address,
		int				port );

	public interface
	PortMapping
	{
		public void
		unregister();
	}

	public interface
	AppliedPortMapping
	{
		public InetSocketAddress
		getLocalAddress();

		public InetSocketAddress
		getRemoteAddress();

		public Map<String,Object>
		getProperties();
	}
}
