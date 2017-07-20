/*
 * Created on 21-Jan-2005
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

package com.biglybt.core.dht.transport.udp.impl;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.impl.DHTLog;
import com.biglybt.core.dht.netcoords.DHTNetworkPosition;
import com.biglybt.core.dht.netcoords.DHTNetworkPositionManager;
import com.biglybt.core.dht.netcoords.vivaldi.ver1.VivaldiPositionProvider;
import com.biglybt.core.dht.transport.*;
import com.biglybt.core.dht.transport.udp.DHTTransportUDP;
import com.biglybt.core.dht.transport.udp.DHTTransportUDPContact;
import com.biglybt.core.util.AERunStateHandler;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.net.udp.uc.PRUDPPacketHandler;


/**
 * @author parg
 *
 */

public class
DHTTransportUDPContactImpl
	implements DHTTransportUDPContact
{
	public static final int			NODE_STATUS_UNKNOWN		= 0xffffffff;
	public static final int			NODE_STATUS_ROUTABLE	= 0x00000001;

	static{
		AERunStateHandler.addListener(
			new AERunStateHandler.RunStateChangeListener()
			{
				private VivaldiPositionProvider provider = null;

				@Override
				public void
				runStateChanged(
					long run_state )
				{
					synchronized( this ){

						if ( AERunStateHandler.isDHTSleeping()){

							if ( provider != null ){

								DHTNetworkPositionManager.unregisterProvider( provider );

								provider = null;
							}
						}else{

							if ( provider == null ){

								provider = new VivaldiPositionProvider();

								DHTNetworkPositionManager.registerProvider( provider );
							}
						}
					}
				}
			},
			true );
	}

	final DHTTransportUDPImpl		transport;
	private InetSocketAddress		external_address;
	private InetSocketAddress		transport_address;

	private byte[]				id;
	private byte				protocol_version;
	private int					instance_id;
	private final long				skew;
	private byte				generic_flags;

	private int					random_id;
	private int					node_status	= NODE_STATUS_UNKNOWN;

	private DHTNetworkPosition[]		network_positions;

	protected
	DHTTransportUDPContactImpl(
		boolean					_is_local,
		DHTTransportUDPImpl		_transport,
		InetSocketAddress		_transport_address,
		InetSocketAddress		_external_address,
		byte					_protocol_version,
		int						_instance_id,
		long					_skew,
		byte					_generic_flags )

		throws DHTTransportException
	{
		transport				= _transport;
		transport_address		= _transport_address;
		external_address		= _external_address;
		protocol_version		= _protocol_version;

		if ( transport_address.equals( external_address )){

			external_address	= transport_address;
		}

		instance_id		=		_instance_id;
		skew			= 		_skew;
		generic_flags	= 		_generic_flags;

		if ( 	transport_address == external_address ||
				transport_address.getAddress().equals( external_address.getAddress())){

			id = DHTUDPUtils.getNodeID( external_address, protocol_version );
		}

		createNetworkPositions( _is_local );
	}

	@Override
	public DHTTransport
	getTransport()
	{
		return( transport );
	}

	@Override
	public byte
	getProtocolVersion()
	{
		return( protocol_version );
	}

	protected void
	setProtocolVersion(
		byte		v )
	{
		protocol_version 	= v;
	}

	@Override
	public long
	getClockSkew()
	{
		return( skew );
	}

	@Override
	public int
	getRandomIDType()
	{
		return( RANDOM_ID_TYPE1 );
	}

	@Override
	public void
	setRandomID(
		int		_random_id )
	{
		random_id	= _random_id;
	}

	@Override
	public int
	getRandomID()
	{
		return( random_id );
	}

	@Override
	public void
	setRandomID2(
		byte[]		id )
	{
	}

	@Override
	public byte[]
	getRandomID2()
	{
		return( null );
	}

	protected int
	getNodeStatus()
	{
		return( node_status );
	}

	protected void
	setNodeStatus(
		int		ns )
	{
		node_status	= ns;
	}

	@Override
	public boolean
	isValid()
	{
		return( 	addressMatchesID() &&
					!transport.invalidExternalAddress( external_address.getAddress()));
	}

	@Override
	public boolean
	isSleeping()
	{
		return(( generic_flags & DHTTransportUDP.GF_DHT_SLEEPING ) != 0 );
	}

	protected void
	setGenericFlags(
		byte		flags )
	{
		generic_flags = flags;
	}

	protected boolean
	addressMatchesID()
	{
		return( id != null );
	}

	@Override
	public InetSocketAddress
	getTransportAddress()
	{
		return( transport_address );
	}

	@Override
	public void
	setTransportAddress(
		InetSocketAddress	address )
	{
		transport_address = address;
	}

	@Override
	public InetSocketAddress
	getExternalAddress()
	{
		return( external_address );
	}

	@Override
	public String
	getName()
	{
		return( DHTLog.getString2( id  ));
	}

	@Override
	public byte[]
	getBloomKey()
	{
		return( getAddress().getAddress().getAddress());
	}

	@Override
	public InetSocketAddress
	getAddress()
	{
		return( getExternalAddress());
	}

	@Override
	public int
	getMaxFailForLiveCount()
	{
		return( transport.getMaxFailForLiveCount() );
	}

	@Override
	public int
	getMaxFailForUnknownCount()
	{
		return( transport.getMaxFailForUnknownCount() );
	}

	@Override
	public int
	getInstanceID()
	{
		return( instance_id );
	}

	protected void
	setInstanceIDAndVersion(
		int		_instance_id,
		byte	_protocol_version )
	{
		instance_id	= _instance_id;

			// target supports a higher version than we thought, update

		if ( _protocol_version > protocol_version ){

			protocol_version = _protocol_version;
		}
	}

	@Override
	public boolean
	isAlive(
		long		timeout )
	{
		final AESemaphore	sem = new AESemaphore( "DHTTransportContact:alive");

		final boolean[]	alive = { false };

		try{
			sendPing(
				new DHTTransportReplyHandlerAdapter()
				{
					@Override
					public void
					pingReply(
						DHTTransportContact contact )
					{
						alive[0]	= true;

						sem.release();
					}

					@Override
					public void
					failed(
						DHTTransportContact 	contact,
						Throwable 				cause )
					{
						sem.release();
					}
				});

			sem.reserve( timeout );

			return( alive[0] );

		}catch( Throwable e ){

			return( false );
		}
	}

	@Override
	public void
	isAlive(
		DHTTransportReplyHandler 	handler,
		long 						timeout )
	{
		transport.sendPing( this, handler, timeout, PRUDPPacketHandler.PRIORITY_IMMEDIATE );
	}

	@Override
	public void
	sendPing(
		DHTTransportReplyHandler	handler )
	{
		transport.sendPing( this, handler );
	}

	@Override
	public void
	sendImmediatePing(
		DHTTransportReplyHandler	handler,
		long						timeout )
	{
		transport.sendImmediatePing( this, handler, timeout );
	}

	@Override
	public void
	sendStats(
		DHTTransportReplyHandler	handler )
	{
		transport.sendStats( this, handler );
	}

	@Override
	public void
	sendStore(
		DHTTransportReplyHandler	handler,
		byte[][]					keys,
		DHTTransportValue[][]		value_sets,
		boolean						immediate )
	{
		transport.sendStore(
				this, handler, keys, value_sets,
				immediate?PRUDPPacketHandler.PRIORITY_IMMEDIATE:PRUDPPacketHandler.PRIORITY_LOW );
	}

	@Override
	public void
	sendQueryStore(
		DHTTransportReplyHandler 	handler,
		int							header_length,
		List<Object[]>			 	key_details )
	{
		transport.sendQueryStore( this, handler, header_length, key_details);
	}

	@Override
	public void
	sendFindNode(
		DHTTransportReplyHandler	handler,
		byte[]						nid,
		short						flags )
	{
		transport.sendFindNode( this, handler, nid );
	}

	@Override
	public void
	sendFindValue(
		DHTTransportReplyHandler	handler,
		byte[]						key,
		int							max_values,
		short						flags )
	{
		transport.sendFindValue( this, handler, key, max_values, flags );
	}

	@Override
	public void
	sendKeyBlock(
		final DHTTransportReplyHandler	handler,
		final byte[]					request,
		final byte[]					signature )
	{
			// gotta do anti-spoof

		sendFindNode(
			new DHTTransportReplyHandlerAdapter()
			{
				@Override
				public void
				findNodeReply(
					DHTTransportContact 	contact,
					DHTTransportContact[]	contacts )
				{
					transport.sendKeyBlockRequest( DHTTransportUDPContactImpl.this, handler, request, signature );
				}
				@Override
				public void
				failed(
					DHTTransportContact 	_contact,
					Throwable				_error )
				{
					handler.failed( _contact, _error );
				}
			},
			new byte[0],
			DHT.FLAG_NONE );

	}

	@Override
	public DHTTransportFullStats
	getStats()
	{
		return( transport.getFullStats( this ));
	}

	@Override
	public byte[]
	getID()
	{
		if ( id == null ){

			throw( new RuntimeException( "Invalid contact" ));
		}

		return( id );
	}

	@Override
	public void
	exportContact(
		DataOutputStream	os )

		throws IOException, DHTTransportException
	{
		transport.exportContact( this, os );
	}

	@Override
	public Map<String, Object>
	exportContactToMap()
	{
		return( transport.exportContactToMap( this ));
	}

	@Override
	public void
	remove()
	{
		transport.removeContact( this );
	}

	protected void
    setNetworkPositions(
    	DHTNetworkPosition[]	positions )
  	{
  		network_positions	= positions;
  	}

	@Override
	public void
	createNetworkPositions(
		boolean  is_local )
	{
		network_positions	= DHTNetworkPositionManager.createPositions( id==null?DHTUDPUtils.getBogusNodeID():id, is_local );
	}

	@Override
	public DHTNetworkPosition[]
  	getNetworkPositions()
	{
		return( network_positions );
	}

  	@Override
	  public DHTNetworkPosition
  	getNetworkPosition(
  		byte	position_type )
  	{
  		for (int i=0;i<network_positions.length;i++){

  			if ( network_positions[i].getPositionType() == position_type ){

  				return( network_positions[i] );
  			}
  		}

  		return( null );
  	}

	@Override
	public String
	getString()
	{
		if ( transport_address.equals( external_address )){

			return( DHTLog.getString2(id) + "["+transport_address.toString()+",V" + getProtocolVersion() +"]");
		}

		return( DHTLog.getString2(id) + "[tran="+transport_address.toString()+",ext="+external_address+",V" + getProtocolVersion() +"]");
	}
}
