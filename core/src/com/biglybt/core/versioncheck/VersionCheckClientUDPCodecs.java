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


package com.biglybt.core.versioncheck;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.net.udp.uc.*;


public class
VersionCheckClientUDPCodecs
{
	public static final int ACT_VERSION_REQUEST	= 32;
	public static final int ACT_VERSION_REPLY	= 33;

	private static boolean	registered	= false;

	public static void
	registerCodecs()
	{
		if ( registered ){

			return;
		}

		registered	= true;

		PRUDPPacketReplyDecoder reply_decoder =
			new PRUDPPacketReplyDecoder()
			{
				@Override
				public PRUDPPacketReply
				decode(
					PRUDPPacketHandler handler,
					InetSocketAddress	originator,
					DataInputStream		is,
					int					action,
					int					transaction_id )

					throws IOException
				{
					switch( action ){

						case ACT_VERSION_REPLY:
						{
							return( new VersionCheckClientUDPReply(is, transaction_id ));
						}
						default:
						{
							throw( new IOException( "Unrecognised action '" + action + "'" ));
						}
					}
				}
			};

		Map<Integer,PRUDPPacketReplyDecoder>	reply_decoders = new HashMap<>();

		reply_decoders.put( new Integer( ACT_VERSION_REPLY ), reply_decoder );

		PRUDPPacketReply.registerDecoders( reply_decoders );

		PRUDPPacketRequestDecoder request_decoder =
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

						case ACT_VERSION_REQUEST:
						{
							return( new VersionCheckClientUDPRequest(is, connection_id, transaction_id ));
						}
						default:
						{
							throw( new IOException( "unsupported request type"));
						}
					}
				}
			};

		Map<Integer,PRUDPPacketRequestDecoder>	request_decoders = new HashMap<>();

		request_decoders.put( new Integer( ACT_VERSION_REQUEST ), request_decoder );

		PRUDPPacketRequest.registerDecoders( request_decoders );
	}
}
