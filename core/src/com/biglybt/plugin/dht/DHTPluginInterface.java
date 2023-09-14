/*
 * Created on Sep 3, 2014
 * Created by Paul Gardner
 *
 * Copyright 2014 Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.plugin.dht;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import com.biglybt.core.dht.DHT;
import com.biglybt.core.util.Debug;

public interface
DHTPluginInterface
	extends DHTPluginBasicInterface
{
	public static final byte		FLAG_SINGLE_VALUE	= DHT.FLAG_SINGLE_VALUE;
	public static final byte		FLAG_DOWNLOADING	= DHT.FLAG_DOWNLOADING;
	public static final byte		FLAG_SEEDING		= DHT.FLAG_SEEDING;
	public static final byte		FLAG_MULTI_VALUE	= DHT.FLAG_MULTI_VALUE;
	public static final byte		FLAG_STATS			= DHT.FLAG_STATS;
	public static final byte		FLAG_ANON			= DHT.FLAG_ANON;
	public static final byte		FLAG_PRECIOUS		= DHT.FLAG_PRECIOUS;
	public static final byte		FLAG_BRIDGED		= DHT.FLAG_BRIDGED;

	public static final short		FLAG_PUT_AND_FORGET	= DHT.FLAG_PUT_AND_FORGET;

	public static final int			MAX_VALUE_SIZE		= DHT.MAX_VALUE_SIZE;

	public String
	getNetwork();

	public default String
	getAENetwork()
	{
		return( getNetwork());
	}
	
	public boolean
	isExtendedUseAllowed();

	public DHTPluginContact
	getLocalAddress();

	public default DHTPluginContact[]
	getLocalAddresses()
	{
		return( new DHTPluginContact[]{ getLocalAddress() }  );
	}

	public InetSocketAddress
	getConnectionOrientedEndpoint();
	
	public default InetSocketAddress[]
	getConnectionOrientedEndpoints()
	{
		return( new InetSocketAddress[]{ getConnectionOrientedEndpoint()});
	}
	
	public DHTPluginKeyStats
	decodeStats(
		DHTPluginValue		value );

	public void
	registerHandler(
		byte[]							handler_key,
		DHTPluginTransferHandler		handler,
		Map<String,Object>				options );

	public void
	unregisterHandler(
		byte[]							handler_key,
		DHTPluginTransferHandler		handler );

	public DHTPluginContact
	importContact(
		InetSocketAddress				address );

	public DHTPluginContact
	importContact(
		InetSocketAddress				address,
		byte							version );

	/**
	 **@deprecated use {@link #importContact(InetSocketAddress, byte, int)}
	 */
	
	public DHTPluginContact
	importContact(
		InetSocketAddress				address,
		byte							version,
		boolean							is_cvs );

	public default DHTPluginContact
	importContact(
		InetSocketAddress				address,
		byte							version,
		int								preferred_net )
	{
			// migration until deprecated version above is removed
		
		return( importContact( address, version, preferred_net==DHT.NW_AZ_CVS ));
	}
	
	public DHTPluginContact
	importContact(
		Map<String,Object>				map );
	
	public List<DHTPluginValue>
	getValues(
		byte[]		key );

	public void
	remove(
		byte[]						key,
		String						description,
		DHTPluginOperationListener	listener );

	public default void
	remove(
		byte[]						key,
		String						description,
		short						flags,
		DHTPluginOperationListener	listener )
	{
		if ( flags != 0 ){
			
			Debug.out( "Flag loss!" );
		}
		
		remove( key, description, listener );
	}

	public void
	remove(
		DHTPluginContact[]			targets,
		byte[]						key,
		String						description,
		DHTPluginOperationListener	listener );

	public void
	addListener(
		DHTPluginListener	l );


	public void
	removeListener(
		DHTPluginListener	l );


	public void
	log(
		String	str );

	public interface
	DHTInterface
	{
		public byte[]
		getID();

		public boolean
		isIPV6();

		public int
		getNetwork();

		public DHTPluginContact[]
		getReachableContacts();

		public DHTPluginContact[]
		getRecentContacts();

		public List<DHTPluginContact>
		getClosestContacts(
			byte[]		to_id,
			boolean		live_only );
	}

}
