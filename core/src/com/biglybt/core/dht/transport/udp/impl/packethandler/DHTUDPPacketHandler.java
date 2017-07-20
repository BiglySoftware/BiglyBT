/*
 * Created on 12-Jun-2005
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

package com.biglybt.core.dht.transport.udp.impl.packethandler;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;

import com.biglybt.core.dht.transport.udp.impl.DHTUDPPacketHelper;
import com.biglybt.core.dht.transport.udp.impl.DHTUDPPacketReply;
import com.biglybt.core.dht.transport.udp.impl.DHTUDPPacketRequest;
import com.biglybt.core.util.*;
import com.biglybt.core.util.bloom.BloomFilter;
import com.biglybt.core.util.bloom.BloomFilterFactory;
import com.biglybt.net.udp.uc.*;


public class
DHTUDPPacketHandler
	implements DHTUDPPacketHandlerStub
{
	private final DHTUDPPacketHandlerFactory	factory;
	final int							network;

	private final PRUDPPacketHandler		packet_handler;
	private final DHTUDPRequestHandler	request_handler;

	final DHTUDPPacketHandlerStats	stats;

	private boolean						test_network_alive	= true;

	private static final int							BLOOM_FILTER_SIZE		= 10000;
	private static final int			BLOOM_ROTATION_PERIOD	= 3*60*1000;
	private BloomFilter					bloom1;
	private BloomFilter					bloom2;
	private long						last_bloom_rotation_time;

	private boolean 	destroyed;

	protected
	DHTUDPPacketHandler(
		DHTUDPPacketHandlerFactory	_factory,
		int							_network,
		PRUDPPacketHandler			_packet_handler,
		DHTUDPRequestHandler		_request_handler )
	{
		factory			= _factory;
		network			= _network;
		packet_handler	= _packet_handler;
		request_handler	= _request_handler;

		bloom1	= BloomFilterFactory.createAddOnly( BLOOM_FILTER_SIZE );
		bloom2	= BloomFilterFactory.createAddOnly( BLOOM_FILTER_SIZE );

		stats = new DHTUDPPacketHandlerStats( packet_handler );
	}

	public boolean
	isDestroyed()
	{
		return( destroyed );
	}

	public void
	testNetworkAlive(
		boolean		alive )
	{
		test_network_alive	= alive;
	}

	public DHTUDPRequestHandler
	getRequestHandler()
	{
		return( request_handler );
	}

	public PRUDPPacketHandler
	getPacketHandler()
	{
		return( packet_handler );
	}

	protected int
	getNetwork()
	{
		return( network );
	}

	protected void
	updateBloom(
		InetSocketAddress		destination_address )
	{
			// allow unresolved through (e.g. ipv6 dht seed) as handled later

		if ( !destination_address.isUnresolved()){

		    long diff = SystemTime.getCurrentTime() - last_bloom_rotation_time;

		    if( diff < 0 || diff > BLOOM_ROTATION_PERIOD ) {

		    	// System.out.println( "bloom rotate: entries = " + bloom1.getEntryCount() + "/" + bloom2.getEntryCount());

		    	bloom1 = bloom2;

		    	bloom2 = BloomFilterFactory.createAddOnly( BLOOM_FILTER_SIZE );

		        last_bloom_rotation_time = SystemTime.getCurrentTime();
		    }

		    byte[]	address_bytes = destination_address.getAddress().getAddress();

		    bloom1.add( address_bytes );
		    bloom2.add( address_bytes );
		}
	}

	public void
	sendAndReceive(
		DHTUDPPacketRequest					request,
		InetSocketAddress					destination_address,
		final DHTUDPPacketReceiver			receiver,
		long								timeout,
		int									priority )

		throws DHTUDPPacketHandlerException
	{
		if ( destroyed ){
			throw( new DHTUDPPacketHandlerException( "packet handler is destroyed" ));
		}

			// send and receive pair

		destination_address	= AddressUtils.adjustDHTAddress( destination_address, true );

		try{
			request.setNetwork( network );

			if ( test_network_alive ){

				if ( destination_address.isUnresolved() && destination_address.getHostName().equals( Constants.DHT_SEED_ADDRESS_V6 )){

					tunnelIPv6SeedRequest( request, destination_address, receiver );

				}else{

					updateBloom( destination_address );

					packet_handler.sendAndReceive(
						request,
						destination_address,
						new PRUDPPacketReceiver()
						{
							@Override
							public void
							packetReceived(
								PRUDPPacketHandlerRequest	request,
								PRUDPPacket					packet,
								InetSocketAddress			from_address )
							{
								DHTUDPPacketReply	reply = (DHTUDPPacketReply)packet;

								stats.packetReceived( reply.getSerialisedSize() );

								if ( reply.getNetwork() == network ){

									receiver.packetReceived(reply, from_address, request.getElapsedTime());

								}else{

									Debug.out( "Non-matching network reply received: expected=" + network + ", actual=" + reply.getNetwork());

									receiver.error( new DHTUDPPacketHandlerException( new Exception( "Non-matching network reply received" )));
								}
							}

							@Override
							public void
							error(
								PRUDPPacketHandlerException	e )
							{
								receiver.error( new DHTUDPPacketHandlerException( e ));
							}
						},
						timeout,
						priority );
				}
			}else{

				receiver.error( new DHTUDPPacketHandlerException( new Exception( "Test network disabled" )));
			}

		}catch( PRUDPPacketHandlerException e ){

			throw( new DHTUDPPacketHandlerException(e ));

		}finally{

			stats.packetSent( request.getSerialisedSize() );
		}
	}

	public void
	send(
		DHTUDPPacketRequest			request,
		InetSocketAddress			destination_address )

		throws DHTUDPPacketHandlerException

	{
		if ( destroyed ){
			throw( new DHTUDPPacketHandlerException( "packet handler is destroyed" ));
		}

		destination_address	= AddressUtils.adjustDHTAddress( destination_address, true );

		updateBloom( destination_address );

			// one way send (no matching reply expected )

		try{

			request.setNetwork( network );

			if ( test_network_alive ){

				packet_handler.send( request, destination_address );
			}

		}catch( PRUDPPacketHandlerException e ){

			throw( new DHTUDPPacketHandlerException( e ));

		}finally{

			stats.packetSent( request.getSerialisedSize() );
		}
	}

	@Override
	public void
	send(
		DHTUDPPacketReply			reply,
		InetSocketAddress			destination_address )

		throws DHTUDPPacketHandlerException
	{
		if ( destroyed ){
			throw( new DHTUDPPacketHandlerException( "packet handler is destroyed" ));
		}

		destination_address	= AddressUtils.adjustDHTAddress( destination_address, true );

			// send reply to a request

		try{
			reply.setNetwork( network );

				// outgoing request

			if ( test_network_alive ){

				packet_handler.send( reply, destination_address );
			}

		}catch( PRUDPPacketHandlerException e ){

			throw( new DHTUDPPacketHandlerException( e ));

		}finally{

			stats.packetSent( reply.getSerialisedSize());
		}
	}

	protected void
	receive(
		DHTUDPPacketRequest	request )
	{
		if ( destroyed ){
			return;
		}

			// incoming request

		if ( test_network_alive ){

			request.setAddress( AddressUtils.adjustDHTAddress( request.getAddress(), false ));

				// an alien request is one that originates from a peer that we haven't recently
				// talked to

			byte[] bloom_key = request.getAddress().getAddress().getAddress();

			boolean	alien = !bloom1.contains( bloom_key );

			if ( alien ){

					// avoid counting consecutive requests from same contact more than once

				bloom1.add( bloom_key );
				bloom2.add( bloom_key );
			}

			stats.packetReceived( request.getSerialisedSize());

			request_handler.process( request, alien );
		}
	}

	public void
	setDelays(
		int		send_delay,
		int		receive_delay,
		int		queued_request_timeout )
	{
			// TODO: hmm

		packet_handler.setDelays( send_delay, receive_delay, queued_request_timeout );
	}

	public void
	destroy()
	{
		factory.destroy( this );

		destroyed = true;
	}

	public DHTUDPPacketHandlerStats
	getStats()
	{
		return( stats );
	}


	private void
	tunnelIPv6SeedRequest(
		DHTUDPPacketRequest			request,
		InetSocketAddress			destination_address,
		DHTUDPPacketReceiver		receiver )

		throws DHTUDPPacketHandlerException
	{
		if ( destroyed ){
			throw( new DHTUDPPacketHandlerException( "packet handler is destroyed" ));
		}

		if ( request.getAction() != DHTUDPPacketHelper.ACT_REQUEST_FIND_NODE ){

			return;
		}

		try{
			long start = SystemTime.getMonotonousTime();

			ByteArrayOutputStream baos_req = new ByteArrayOutputStream();

			DataOutputStream dos = new DataOutputStream( baos_req );

			request.serialise( dos );

			dos.close();

			byte[] request_bytes = baos_req.toByteArray();

			String host = Constants.DHT_SEED_ADDRESS_V6_TUNNEL;

			DNSUtils.DNSUtilsIntf dns_utils = DNSUtils.getSingleton();

			if ( dns_utils != null ){

				try{
					host = dns_utils.getIPV6ByName( host ).getHostAddress();

					host = UrlUtils.convertIPV6Host( host );

				}catch( Throwable e ){
				}
			}

			URL url = new URL( "http://" + host + "/dht?port=" + packet_handler.getPort() + "&request=" + Base32.encode( request_bytes ));

			HttpURLConnection connection = (HttpURLConnection)url.openConnection();

			connection.setConnectTimeout( 10*1000 );
			connection.setReadTimeout( 20*1000 );

			InputStream is = connection.getInputStream();

			ByteArrayOutputStream baos_rep = new ByteArrayOutputStream( 1000 );

			byte[]	buffer = new byte[8*1024];

			while( true ){

				int len = is.read( buffer );

				if ( len <= 0  ){

					break;
				}

				baos_rep.write( buffer, 0, len );
			}

			byte[] reply_bytes = baos_rep.toByteArray();

			if ( reply_bytes.length > 0 ){

				DHTUDPPacketReply reply = (DHTUDPPacketReply)PRUDPPacketReply.deserialiseReply(
						packet_handler, destination_address,
						new DataInputStream(new ByteArrayInputStream( reply_bytes )));

				receiver.packetReceived( reply, destination_address, SystemTime.getMonotonousTime() - start );
			}
		}catch( Throwable e ){

			throw( new DHTUDPPacketHandlerException( "Tunnel failed", e ));
		}
	}
}
