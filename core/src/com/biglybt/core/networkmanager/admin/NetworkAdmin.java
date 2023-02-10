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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.List;

import com.biglybt.core.Core;
import com.biglybt.core.networkmanager.admin.impl.NetworkAdminImpl;
import com.biglybt.core.util.IndentWriter;

public abstract class
NetworkAdmin
{
	private static NetworkAdmin	singleton;

	public static final String PR_NETWORK_INTERFACES			= "Network Interfaces";
	public static final String PR_DEFAULT_BIND_ADDRESS			= "Default Bind IP";
	public static final String PR_ADDITIONAL_SERVICE_ADDRESS	= "Additional Service Address";
	public static final String PR_AS							= "AS";

	public static final int			IP_PROTOCOL_VERSION_AUTO		= 0;
	public static final int			IP_PROTOCOL_VERSION_REQUIRE_V4	= 1;
	public static final int			IP_PROTOCOL_VERSION_REQUIRE_V6	= 2;

	public static final String[]	PR_NAMES = {
		PR_NETWORK_INTERFACES,
		PR_DEFAULT_BIND_ADDRESS,
		PR_AS
	};

		// this is not supposed to exist, it is used to test that resolution is being
		// intercepted
	
	public static final String 		DNS_SPI_TEST_HOST	= "http://dns.test.client.biglybittorrent.com/";
	
	public static synchronized NetworkAdmin
	getSingleton()
	{
		if ( singleton == null ){

			singleton = new NetworkAdminImpl();
		}

		return( singleton );
	}

	public InetAddress getSingleHomedServiceBindAddress() {return getSingleHomedServiceBindAddress(IP_PROTOCOL_VERSION_AUTO);}

	/**
	 * @throws UnsupportedAddressTypeException when no address matching the v4/v6 requirements is found, always returns an address when auto is selected
	 */
	public abstract InetAddress
	getSingleHomedServiceBindAddress(int protocolVersion) throws UnsupportedAddressTypeException;

	/**
	 * Selects a bind address based on available host address and bind protocol families
	 * 
	 * @param host
	 * @return Array with 2 entries, first is selected host address, second is selected bind address (possibly null of course)
	 */
	public abstract InetAddress[]
	getSingleHomedServiceBinding( String host ) throws UnknownHostException, UnsupportedAddressTypeException;
	
	public abstract List<InetAddress[]>
	getSingleHomedServiceBindings( String host ) throws UnknownHostException, UnsupportedAddressTypeException;
	
	public abstract InetAddress[]
	getMultiHomedServiceBindAddresses(boolean forNIO);

	public abstract InetAddress
	getMultiHomedOutgoingRoundRobinBindAddress(InetAddress target);

	public abstract String
	getNetworkInterfacesAsString();

	public abstract InetAddress[]
	getAllBindAddresses(
		boolean	include_wildcard );

	/**
	 * Given an interface name etc this will return the corresponding ip addresses
	 * @param bind_to
	 * @return
	 */

	public abstract InetAddress[]
	resolveBindAddresses(
		String	bind_to );

	public abstract InetAddress
	guessRoutableBindAddress();

	public abstract InetAddress
	getAlternativeProtocolBindAddress(
		InetAddress		address );
	
		/**
		 * Returns the list of current addresses that can successfully be bound
		 * to with an ephemeral port
		 * @return
		 */

	public abstract InetAddress[]
	getBindableAddresses();

	public abstract int
	getBindablePort(
		int		preferred_port )

		throws IOException;

	public abstract boolean
	mustBind();

	public abstract boolean
	hasMissingForcedBind();

	public abstract String
	getBindStatus();

	public abstract NetworkAdminNetworkInterface[]
	getInterfaces();

	public abstract boolean
	hasIPV4Potential();

	public abstract boolean
	isIPV6Enabled();

	public boolean hasIPV6Potential() {return hasIPV6Potential(false);}

	public abstract boolean
	hasIPV6Potential(boolean forNIO);

	public abstract InetAddress
	getLoopbackAddress();
	
	public abstract NetworkAdminProtocol[]
	getOutboundProtocols(
		Core core);

	public abstract NetworkAdminProtocol[]
	getInboundProtocols(
		Core core );

	public abstract NetworkAdminProtocol
	createInboundProtocol(
		Core core,
		int				type,
		int				port );

	public abstract InetAddress
	testProtocol(
		NetworkAdminProtocol	protocol )

		throws NetworkAdminException;

	public abstract NetworkAdminSocksProxy
	createSocksProxy(
		String		host,
		int			port,
		String		username,
		String		password );

	public abstract boolean
	isSocksActive();

	public abstract NetworkAdminSocksProxy[]
	getSocksProxies();

	public abstract NetworkAdminHTTPProxy
	getHTTPProxy();

	public abstract NetworkAdminNATDevice[]
	getNATDevices(
			Core core);

		/**
		 * Only call if the supplied address is believed to be the current public address
		 * @param address
		 * @return
		 * @throws NetworkAdminException
		 */

	public abstract NetworkAdminASN
	lookupCurrentASN(
		InetAddress		address )

		throws NetworkAdminException;

	public abstract NetworkAdminASN
	getCurrentASN();

		/**
		 * ad-hoc query
		 * @param address
		 * @return
		 * @throws NetworkAdminException
		 */

	public abstract NetworkAdminASN
	lookupASN(
		InetAddress		address )

		throws NetworkAdminException;

	public abstract void
	lookupASN(
		InetAddress					address,
		NetworkAdminASNListener		listener );


	public abstract String
	classifyRoute(
		InetAddress					address );

	public abstract boolean
	canTraceRoute();

	public abstract void
	getRoutes(
		final InetAddress					target,
		final int							max_millis,
		final NetworkAdminRoutesListener	listener )

		throws NetworkAdminException;

	public abstract boolean
	canPing();

	public abstract void
	pingTargets(
		final InetAddress					target,
		final int							max_millis,
		final NetworkAdminRoutesListener	listener )

		throws NetworkAdminException;

	public abstract InetAddress
	getDefaultPublicAddress();

	public abstract InetAddress
	getDefaultPublicAddress(
		boolean	peek );

	public abstract InetAddress
	getDefaultPublicAddressV6();

	public abstract boolean	hasDHTIPV4();
	
	public abstract boolean	hasDHTIPV6();

		/**
		 * 
		 * @param address
		 * @return { NetworkInterface, InetAddress (best intf address)}, {NetworkInterface} or {InetAddress (nomatch, same as arg)}
		 */
	
	public abstract Object[]
	getInterfaceForAddress(
		InetAddress					address );
	
	public abstract void
	addPropertyChangeListener(
		NetworkAdminPropertyChangeListener	listener );

	public abstract void
	addAndFirePropertyChangeListener(
		NetworkAdminPropertyChangeListener	listener );

	public abstract void
	removePropertyChangeListener(
		NetworkAdminPropertyChangeListener	listener );

	public abstract void
	runInitialChecks(
			Core core);

	public abstract void
	logNATStatus(
		IndentWriter		iw );

	public abstract void
	generateDiagnostics(
		IndentWriter		iw );
}
