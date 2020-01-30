/*
 * Created on 14-Jun-2005
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

package com.biglybt.plugin.dht.impl;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.Map;

import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.nat.DHTNATPuncher;
import com.biglybt.core.dht.transport.DHTTransportContact;
import com.biglybt.core.dht.transport.DHTTransportReplyHandlerAdapter;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.AERunStateHandler;
import com.biglybt.plugin.dht.DHTPluginContact;
import com.biglybt.plugin.dht.DHTPluginOperationListener;
import com.biglybt.plugin.dht.DHTPluginProgressListener;

public class
DHTPluginContactImpl
	implements DHTPluginContact
{
	private DHTPluginImpl		plugin;
	private DHTTransportContact	contact;

	protected
	DHTPluginContactImpl(
		DHTPluginImpl		_plugin,
		DHTTransportContact	_contact )
	{
		plugin	= _plugin;
		contact	= _contact;
	}

	public DHTPluginImpl
	getDHT()
	{
		return( plugin );
	}

	protected DHTTransportContact
	getContact()
	{
		return( contact );
	}

	@Override
	public byte[]
	getID()
	{
		return( contact.getID());
	}

	@Override
	public String
	getName()
	{
		return( contact.getName());
	}

	@Override
	public int
	getNetwork()
	{
		return( plugin.getDHT().getTransport().getNetwork());
	}

	@Override
	public byte
	getProtocolVersion()
	{
		return( contact.getProtocolVersion());
	}

	@Override
	public InetSocketAddress
	getAddress()
	{
		return( contact.getAddress());
	}

	@Override
	public Map<String, Object>
	exportToMap()
	{
		return( contact.exportContactToMap());
	}

	@Override
	public boolean
	isAlive(
		long		timeout )
	{
		return( contact.isAlive( timeout ));
	}

	@Override
	public void
	isAlive(
		long								timeout,
		final DHTPluginOperationListener	listener )
	{
		contact.isAlive(
			new DHTTransportReplyHandlerAdapter()
			{
				@Override
				public void
				pingReply(
					DHTTransportContact contact )
				{
					listener.complete( null, false );
				}

				@Override
				public void
				failed(
					DHTTransportContact 	contact,
					Throwable 				error )
				{
					listener.complete( null, true );
				}
			},
			timeout );
	}

	@Override
	public boolean
	isOrHasBeenLocal()
	{
		return( plugin.isRecentAddress( contact.getAddress().getAddress().getHostAddress()));
	}

	@Override
	public Map
	openTunnel()
	{
		if ( AERunStateHandler.isDHTSleeping() || Logger.isClosingTakingTooLong()){
			
			return( null );
		}
		
		DHT dht = plugin.getDHT();
		
		if (( contact.getAddress().getAddress() instanceof Inet6Address ) != dht.getTransport().isIPV6()){
		
				// no point in trying to tunnel to an ipv6 contact over non-ipv6 or vice-versa
			
			return( null );
		}
		
		DHTNATPuncher puncher = dht.getNATPuncher();

		if ( puncher == null ){

			return( null );
		}

		return( puncher.punch( "Tunnel", contact, null, null ));
	}

	public Map
	openTunnel(
		DHTPluginContact[]	rendezvous,
		Map					client_data )
	{
		if ( AERunStateHandler.isDHTSleeping() || Logger.isClosingTakingTooLong()){
			
			return( null );
		}
		
		DHT dht = plugin.getDHT();
		
		if (( contact.getAddress().getAddress() instanceof Inet6Address ) != dht.getTransport().isIPV6()){
		
				// no point in trying to tunnel to an ipv6 contact over non-ipv6 or vice-versa
			
			return( null );
		}
		
		DHTNATPuncher puncher = dht.getNATPuncher();

		if ( puncher == null ){

			return( null );
		}

		if ( rendezvous == null || rendezvous.length == 0 ){

			return( puncher.punch( "Tunnel", contact, null, client_data ));

		}else{

			DHTTransportContact[] r = new DHTTransportContact[rendezvous.length];

			for ( int i=0;i<r.length;i++){

				r[0] = ((DHTPluginContactImpl)rendezvous[i]).contact;
			}

			Map result = puncher.punch( "Tunnel", contact, r, client_data );

			DHTTransportContact used = r[0];

			if ( used != null ){

				rendezvous[0] = new DHTPluginContactImpl( plugin, used );
			}

			return( result );
		}
	}

	@Override
	public byte[]
    read(
    	DHTPluginProgressListener	listener,
    	byte[]						handler_key,
    	byte[]						key,
    	long						timeout )
	{
		return( plugin.read( listener, this, handler_key, key, timeout ));
	}

	@Override
	public void
    write(
    	DHTPluginProgressListener	listener,
    	byte[]						handler_key,
    	byte[]						key,
    	byte[]						data,
    	long						timeout )
	{
		plugin.write( listener, this, handler_key, key, data, timeout );
	}

	@Override
	public byte[]
    call(
    	DHTPluginProgressListener	listener,
    	byte[]						handler_key,
    	byte[]						data,
    	long						timeout )
	{
		return( plugin.call( listener, this, handler_key, data, timeout ));
	}

	@Override
	public String
	getString()
	{
		return( contact.getString());
	}
}