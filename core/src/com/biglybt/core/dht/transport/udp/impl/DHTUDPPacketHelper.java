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

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;
import com.biglybt.net.udp.uc.*;


/**
 * @author parg
 *
 */

public class
DHTUDPPacketHelper
{
	public static final int		PACKET_MAX_BYTES		= 1400;

		// these actions have to co-exist with the tracker ones when the connection
		// is shared, hence 1024

	public static final int		ACT_REQUEST_PING		= 1024;
	public static final int		ACT_REPLY_PING			= 1025;
	public static final int		ACT_REQUEST_STORE		= 1026;
	public static final int		ACT_REPLY_STORE			= 1027;
	public static final int		ACT_REQUEST_FIND_NODE	= 1028;
	public static final int		ACT_REPLY_FIND_NODE		= 1029;
	public static final int		ACT_REQUEST_FIND_VALUE	= 1030;
	public static final int		ACT_REPLY_FIND_VALUE	= 1031;
	public static final int		ACT_REPLY_ERROR			= 1032;
	public static final int		ACT_REPLY_STATS			= 1033;
	public static final int		ACT_REQUEST_STATS		= 1034;
	public static final int		ACT_DATA				= 1035;
	public static final int		ACT_REQUEST_KEY_BLOCK	= 1036;
	public static final int		ACT_REPLY_KEY_BLOCK		= 1037;
	public static final int		ACT_REQUEST_QUERY_STORE	= 1038;
	public static final int		ACT_REPLY_QUERY_STORE	= 1039;


	private static boolean	registered				= false;

	protected static void
	registerCodecs()
	{
		if ( registered ){

			return;
		}

		registered	= true;

		PRUDPPacketRequestDecoder	request_decoder =
			new PRUDPPacketRequestDecoder()
			{
				@Override
				public PRUDPPacketRequest
				decode(
					PRUDPPacketHandler	handler,
					DataInputStream		is,
					long				connection_id,
					int					action,
					int					transaction_id )

					throws IOException
				{
					if ( handler == null ){

							// most likely cause is DHT packet ending up on the UDP tracker as it'll get
							// router here but with a null-handler

						throw( new IOException( "No handler available for DHT packet decode" ));
					}

					DHTUDPPacketNetworkHandler	network_handler = (DHTUDPPacketNetworkHandler)handler.getRequestHandler();

					if ( network_handler == null ){

							// we an get this after a port change and the old port listener is still running (e.g.
							// its still doing UDP tracker)

						throw( new IOException( "No network handler available for DHT packet decode" ));
					}

					switch( action ){
						case ACT_REQUEST_PING:
						{
							return( new DHTUDPPacketRequestPing(network_handler,is, connection_id,transaction_id));
						}
						case ACT_REQUEST_STORE:
						{
							return( new DHTUDPPacketRequestStore(network_handler,is, connection_id,transaction_id));
						}
						case ACT_REQUEST_FIND_NODE:
						{
							return( new DHTUDPPacketRequestFindNode(network_handler,is, connection_id,transaction_id));
						}
						case ACT_REQUEST_FIND_VALUE:
						{
							return( new DHTUDPPacketRequestFindValue(network_handler,is, connection_id,transaction_id));
						}
						case ACT_REQUEST_STATS:
						{
							return( new DHTUDPPacketRequestStats(network_handler,is, connection_id, transaction_id));
						}
						case ACT_DATA:
						{
							return( new DHTUDPPacketData(network_handler,is, connection_id, transaction_id));
						}
						case ACT_REQUEST_KEY_BLOCK:
						{
							return( new DHTUDPPacketRequestKeyBlock(network_handler,is, connection_id, transaction_id));
						}
						case ACT_REQUEST_QUERY_STORE:
						{
							return( new DHTUDPPacketRequestQueryStorage(network_handler,is, connection_id, transaction_id));
						}
						default:
						{
							throw( new IOException( "Unknown action type" ));
						}
					}
				}
			};

		Map	request_decoders = new HashMap();

		request_decoders.put( new Integer( ACT_REQUEST_PING ), request_decoder );
		request_decoders.put( new Integer( ACT_REQUEST_STORE ), request_decoder );
		request_decoders.put( new Integer( ACT_REQUEST_FIND_NODE ), request_decoder );
		request_decoders.put( new Integer( ACT_REQUEST_FIND_VALUE ), request_decoder );
		request_decoders.put( new Integer( ACT_REQUEST_STATS ), request_decoder );
		request_decoders.put( new Integer( ACT_DATA ), request_decoder );
		request_decoders.put( new Integer( ACT_REQUEST_KEY_BLOCK ), request_decoder );
		request_decoders.put( new Integer( ACT_REQUEST_QUERY_STORE ), request_decoder );

		PRUDPPacketRequest.registerDecoders( request_decoders );



		PRUDPPacketReplyDecoder	reply_decoder =
			new PRUDPPacketReplyDecoder()
			{
				@Override
				public PRUDPPacketReply
				decode(
					PRUDPPacketHandler	handler,
					InetSocketAddress	originator,
					DataInputStream		is,
					int					action,
					int					transaction_id )

					throws IOException
				{
					if ( handler == null ){

							// most likely cause is DHT packet ending up on the UDP tracker as it'll get
							// router here but with a null-handler

						throw( new IOException( "No handler available for DHT packet decode" ));
					}

					DHTUDPPacketNetworkHandler	network_handler = (DHTUDPPacketNetworkHandler)handler.getRequestHandler();

					if ( network_handler == null ){

						// we an get this after a port change and the old port listener is still running (e.g.
						// its still doing UDP tracker)

						throw( new IOException( "No network handler available for DHT packet decode" ));
					}

					switch( action ){

						case ACT_REPLY_PING:
						{
							return( new DHTUDPPacketReplyPing(network_handler, originator, is, transaction_id));
						}
						case ACT_REPLY_STORE:
						{
							return( new DHTUDPPacketReplyStore(network_handler, originator, is, transaction_id));
						}
						case ACT_REPLY_FIND_NODE:
						{
							return( new DHTUDPPacketReplyFindNode(network_handler, originator, is, transaction_id));
						}
						case ACT_REPLY_FIND_VALUE:
						{
							return( new DHTUDPPacketReplyFindValue(network_handler, originator, is, transaction_id));
						}
						case ACT_REPLY_ERROR:
						{
							return( new DHTUDPPacketReplyError(network_handler, originator, is, transaction_id));
						}
						case ACT_REPLY_STATS:
						{
							return( new DHTUDPPacketReplyStats( network_handler, originator, is, transaction_id));
						}
						case ACT_REPLY_KEY_BLOCK:
						{
							return( new DHTUDPPacketReplyKeyBlock( network_handler, originator, is, transaction_id));
						}
						case ACT_REPLY_QUERY_STORE:
						{
							return( new DHTUDPPacketReplyQueryStorage( network_handler, originator, is, transaction_id));
						}
						default:
						{
							throw( new IOException( "Unknown action type" ));
						}
					}
				}
			};

		Map	reply_decoders = new HashMap();

		reply_decoders.put( new Integer( ACT_REPLY_PING ), reply_decoder );
		reply_decoders.put( new Integer( ACT_REPLY_STORE ), reply_decoder );
		reply_decoders.put( new Integer( ACT_REPLY_FIND_NODE ), reply_decoder );
		reply_decoders.put( new Integer( ACT_REPLY_FIND_VALUE ), reply_decoder );
		reply_decoders.put( new Integer( ACT_REPLY_ERROR ), reply_decoder );
		reply_decoders.put( new Integer( ACT_REPLY_STATS ), reply_decoder );
		reply_decoders.put( new Integer( ACT_REPLY_KEY_BLOCK ), reply_decoder );
		reply_decoders.put( new Integer( ACT_REPLY_QUERY_STORE ), reply_decoder );

		PRUDPPacketReply.registerDecoders( reply_decoders );
	}
}
