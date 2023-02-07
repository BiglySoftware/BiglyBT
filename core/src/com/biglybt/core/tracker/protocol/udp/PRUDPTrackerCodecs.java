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

package com.biglybt.core.tracker.protocol.udp;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.net.udp.uc.*;


/**
 * @author parg
 *
 */

public class
PRUDPTrackerCodecs
{
	private static boolean	registered	= false;

	public static void
	registerCodecs()
	{
		if ( registered ){

			return;
		}

		registered	= true;

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
					switch( action ){

						case PRUDPPacketTracker.ACT_REPLY_CONNECT:
						{
							return( new PRUDPPacketReplyConnect(is, transaction_id));
						}
						case PRUDPPacketTracker.ACT_REPLY_ANNOUNCE:
						{
							if ( PRUDPPacketTracker.VERSION == 1 ){
								return( new PRUDPPacketReplyAnnounce(is, transaction_id));
							}else{
								return( new PRUDPPacketReplyAnnounce2(is, transaction_id, originator.getAddress() instanceof Inet6Address ));
							}
						}
						case PRUDPPacketTracker.ACT_REPLY_SCRAPE:
						{
							if ( PRUDPPacketTracker.VERSION == 1 ){
								return( new PRUDPPacketReplyScrape(is, transaction_id));
							}else{
								return( new PRUDPPacketReplyScrape2(is, transaction_id));
							}
						}
						case PRUDPPacketTracker.ACT_REPLY_ERROR:
						{
							return( new PRUDPPacketReplyError(is, transaction_id));
						}
						default:
						{
							throw( new IOException( "Unrecognised action '" + action + "'" ));
						}
					}
				}
			};

		Map	reply_decoders = new HashMap();

		reply_decoders.put( new Integer( PRUDPPacketTracker.ACT_REPLY_CONNECT ), reply_decoder );
		reply_decoders.put( new Integer( PRUDPPacketTracker.ACT_REPLY_ANNOUNCE ), reply_decoder );
		reply_decoders.put( new Integer( PRUDPPacketTracker.ACT_REPLY_SCRAPE ), reply_decoder );
		reply_decoders.put( new Integer( PRUDPPacketTracker.ACT_REPLY_ERROR ), reply_decoder );

		PRUDPPacketReply.registerDecoders( reply_decoders );

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
					switch( action ){
						case PRUDPPacketTracker.ACT_REQUEST_CONNECT:
						{
							return( new PRUDPPacketRequestConnect(is, connection_id,transaction_id));
						}
						case PRUDPPacketTracker.ACT_REQUEST_ANNOUNCE:
						{
							if ( PRUDPPacketTracker.VERSION == 1 ){
								return( new PRUDPPacketRequestAnnounce(is, connection_id,transaction_id));
							}else{
								return( new PRUDPPacketRequestAnnounce2(is, connection_id,transaction_id));
							}
						}
						case PRUDPPacketTracker.ACT_REQUEST_SCRAPE:
						{
							return( new PRUDPPacketRequestScrape(is, connection_id,transaction_id));
						}
						default:
						{
							throw( new IOException( "unsupported request type"));
						}
					}
				}
			};

		Map	request_decoders = new HashMap();

		request_decoders.put( new Integer( PRUDPPacketTracker.ACT_REQUEST_CONNECT ), request_decoder );
		request_decoders.put( new Integer( PRUDPPacketTracker.ACT_REQUEST_ANNOUNCE ), request_decoder );
		request_decoders.put( new Integer( PRUDPPacketTracker.ACT_REQUEST_SCRAPE ), request_decoder );

		PRUDPPacketRequest.registerDecoders( request_decoders );

	}
}
