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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.dht.transport.udp.impl.DHTTransportUDPImpl;
import com.biglybt.core.dht.transport.udp.impl.DHTUDPPacketRequest;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;
import com.biglybt.net.udp.uc.PRUDPPacketHandler;
import com.biglybt.net.udp.uc.PRUDPPacketHandlerFactory;

public class
DHTUDPPacketHandlerFactory
{
	private static final DHTUDPPacketHandlerFactory	singleton = new DHTUDPPacketHandlerFactory();

	private final Map 			port_map = new HashMap();

	protected final AEMonitor	this_mon = new AEMonitor("DHTUDPPacketHandlerFactory" );



	public static DHTUDPPacketHandler
	getHandler(
		DHTTransportUDPImpl		transport,
		DHTUDPRequestHandler	request_handler )

		throws DHTUDPPacketHandlerException
	{
		return( singleton.getHandlerSupport( transport, request_handler ));
	}

	protected DHTUDPPacketHandler
	getHandlerSupport(
		DHTTransportUDPImpl		transport,
		DHTUDPRequestHandler	request_handler )

		throws DHTUDPPacketHandlerException
	{
		try{
			this_mon.enter();

			int	port	= transport.getPort();
			int	network = transport.getNetwork();

			Object[]	port_details = (Object[])port_map.get( new Integer( port ));

			if ( port_details == null ){

				PRUDPPacketHandler  packet_handler =
					PRUDPPacketHandlerFactory.getHandler(
							port,
							new DHTUDPPacketNetworkHandler( this, port ));


				port_details = new Object[]{ packet_handler, new HashMap()};

				port_map.put( new Integer( port ), port_details );
			}

			Map					network_map 	= (Map)port_details[1];

			Object[]	network_details = (Object[])network_map.get( new Integer( network ));

			if ( network_details != null ){

				throw( new DHTUDPPacketHandlerException( "Network already added" ));
			}

			DHTUDPPacketHandler ph = new DHTUDPPacketHandler( this, network, (PRUDPPacketHandler)port_details[0], request_handler );

			network_map.put( new Integer( network ), new Object[]{ transport, ph });

			return( ph );

		}finally{

			this_mon.exit();
		}
	}

	protected void
	destroy(
		DHTUDPPacketHandler	handler )
	{
		PRUDPPacketHandler	packet_handler = handler.getPacketHandler();

		int	port 	= packet_handler.getPort();
		int	network = handler.getNetwork();

		try{
			this_mon.enter();

			Object[]	port_details = (Object[])port_map.get( new Integer( port ));

			if ( port_details == null ){

				return;
			}

			Map network_map = (Map)port_details[1];

			network_map.remove( new Integer( network ));

			if ( network_map.size() == 0 ){

				port_map.remove( new Integer( port ));

				try{
					packet_handler.setRequestHandler(null);

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	protected void
	process(
		int					port,
		DHTUDPPacketRequest	request )
	{
		try{
			int	network = request.getNetwork();

			/*
			if ( network != 0 ){

				System.out.println( "process:" + network + ":" + request.getString());
			}
			*/

			Object[]	port_details = (Object[])port_map.get( new Integer( port ));

			if ( port_details == null ){

				throw( new IOException( "Port '" + port + "' not registered" ));
			}

			Map network_map = (Map)port_details[1];

			Object[]	network_details = (Object[])network_map.get( new Integer( network ));

			if ( network_details == null ){

				throw( new IOException( "Network '" + network + "' not registered" ));
			}

			DHTUDPPacketHandler	res = (DHTUDPPacketHandler)network_details[1];

			res.receive( request );

		}catch( IOException e ){

			Debug.printStackTrace( e );
		}
	}

	public DHTTransportUDPImpl
	getTransport(
		int		port,
		int		network )

		throws IOException
	{
		Object[]	port_details = (Object[])port_map.get( new Integer( port ));

		if ( port_details == null ){

			throw( new IOException( "Port '" + port + "' not registered" ));
		}

		Map network_map = (Map)port_details[1];

		Object[]	network_details = (Object[])network_map.get( new Integer( network ));

		if ( network_details == null ){

			throw( new IOException( "Network '" + network + "' not registered" ));
		}

		return((DHTTransportUDPImpl)network_details[0]);
	}
}
