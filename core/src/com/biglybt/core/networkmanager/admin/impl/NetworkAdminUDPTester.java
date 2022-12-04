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


package com.biglybt.core.networkmanager.admin.impl;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.biglybt.core.Core;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.admin.NetworkAdminASN;
import com.biglybt.core.networkmanager.admin.NetworkAdminException;
import com.biglybt.core.networkmanager.admin.NetworkAdminProgressListener;
import com.biglybt.core.util.Constants;
import com.biglybt.core.versioncheck.VersionCheckClient;
import com.biglybt.net.udp.uc.PRUDPPacketHandler;
import com.biglybt.net.udp.uc.PRUDPPacketHandlerFactory;
import com.biglybt.net.udp.uc.PRUDPReleasablePacketHandler;
import com.biglybt.pif.PluginInterface;
import com.biglybt.plugin.upnp.UPnPPlugin;
import com.biglybt.plugin.upnp.UPnPPluginService;

public class
NetworkAdminUDPTester
	implements NetworkAdminProtocolTester
{
	public static final String 	UDP_SERVER_ADDRESS		= Constants.NAT_TEST_UDP_SERVER;
	public static final String 	UDP_SERVER_ADDRESS_V6	= Constants.NAT_TEST_UDP_SERVER_V6;
	public static final int		UDP_SERVER_PORT_V4		= 2085;
	public static final int		UDP_SERVER_PORT_V6		= 2086;

	static{
		NetworkAdminNATUDPCodecs.registerCodecs();
	}

	private final Core core;
	private final NetworkAdminProgressListener	listener;

	protected
	NetworkAdminUDPTester(
		Core _core,
		NetworkAdminProgressListener	_listener )
	{
		core		= _core;
		listener	= _listener;
	}

	@Override
	public InetAddress
	testOutbound(
		InetAddress		bind_ip,
		int				bind_port,
		boolean			ipv6 )

		throws NetworkAdminException
	{
		try{
			return( VersionCheckClient.getSingleton().getExternalIpAddressUDP(bind_ip, bind_port,ipv6));

		}catch( Throwable e ){

			throw( new NetworkAdminException( "Outbound test failed", e ));
		}
	}

	@Override
	public InetAddress
	testInbound(
		InetAddress		bind_ip,
		int				bind_port,
		boolean			ipv6 )

		throws NetworkAdminException
	{
		PRUDPReleasablePacketHandler handler = PRUDPPacketHandlerFactory.getReleasableHandler( bind_port );

		PRUDPPacketHandler	packet_handler = handler.getHandler();

		HashMap	data_to_send = new HashMap();

		PluginInterface pi_upnp = core.getPluginManager().getPluginInterfaceByClass( UPnPPlugin.class );

		String	upnp_str = null;

		if( pi_upnp != null ) {

			UPnPPlugin upnp = (UPnPPlugin)pi_upnp.getPlugin();

			/*
			UPnPMapping mapping = upnp.getMapping( true, port );

			if ( mapping == null ) {

				new_mapping = mapping = upnp.addMapping( "NAT Tester", true, port, true );

				// give UPnP a chance to work

				try {
					Thread.sleep( 500 );

				}
				catch (Throwable e) {

					Debug.printStackTrace( e );
				}
			}
			*/

			UPnPPluginService[]	services = upnp.getServices();

			if ( services.length > 0 ){

				upnp_str = "";

				for (int i=0;i<services.length;i++){

					UPnPPluginService service = services[i];

					upnp_str += (i==0?"":",") + service.getInfo();
				}
			}
		}

		if ( upnp_str != null ){

			data_to_send.put( "upnp", upnp_str );
		}

	    NetworkAdminASN net_asn = NetworkAdmin.getSingleton().getCurrentASN();

		String	as 	= net_asn.getAS();
		String	asn = net_asn.getASName();

		if ( as.length() > 0 ){

			data_to_send.put( "as", as );
		}

		if ( asn.length() > 0 ){

			data_to_send.put( "asn", asn );
		}

		data_to_send.put( "locale", MessageText.getCurrentLocale().toString());

		Random 	random = new Random();

		data_to_send.put( "id", new Long( random.nextLong()));

		data_to_send.put( "port", bind_port );
		
		try{
			packet_handler.setExplicitBindAddress( bind_ip, false );

			Throwable last_error = null;

			long 	timeout 	= 5000;
			long 	timeout_inc = 5000;

			try{
				for (int i=0;i<3;i++){

					data_to_send.put( "seq", new Long(i));

					try{

						// connection ids for requests must always have their msb set...
						// apart from the original darn udp tracker spec....

						long connection_id = 0x8000000000000000L | random.nextLong();

						NetworkAdminNATUDPRequest	request_packet = new NetworkAdminNATUDPRequest( connection_id );

						request_packet.setPayload( data_to_send );

						if ( listener != null ){

							listener.reportProgress( "Sending outbound packet and waiting for reply probe (timeout=" + timeout + ")" );
						}

						NetworkAdminNATUDPReply reply_packet =
							(NetworkAdminNATUDPReply)packet_handler.sendAndReceive(
									null,
									request_packet,
									new InetSocketAddress( ipv6?UDP_SERVER_ADDRESS_V6:UDP_SERVER_ADDRESS, ipv6?UDP_SERVER_PORT_V6:UDP_SERVER_PORT_V4 ),
									timeout,
									PRUDPPacketHandler.PRIORITY_IMMEDIATE );

						Map	reply = reply_packet.getPayload();

						byte[]	ip_bytes = (byte[])reply.get( "ip_address" );

						if ( ip_bytes == null ){

							throw( new NetworkAdminException( "IP address missing in reply" ));
						}

						byte[] reason = (byte[])reply.get( "reason" );

						if ( reason != null ) {

							throw( new NetworkAdminException( new String( reason, "UTF8")));
						}

						return( InetAddress.getByAddress( ip_bytes ));

					}catch( Throwable e){

						last_error	= e;

						timeout += timeout_inc;
					}
				}

				if ( last_error != null ){

					throw( last_error );
				}

				throw( new NetworkAdminException( "Timeout" ));

			}finally{

				try{
					data_to_send.put( "seq", new Long(99));

					long connection_id = 0x8000000000000000L | random.nextLong();

					NetworkAdminNATUDPRequest	request_packet = new NetworkAdminNATUDPRequest( connection_id );

					request_packet.setPayload( data_to_send );

					// fire off one last packet in attempt to inform server of completion

					if ( listener != null ){
						listener.reportProgress( "Sending completion event" );
					}

					packet_handler.send( request_packet, new InetSocketAddress( ipv6?UDP_SERVER_ADDRESS_V6:UDP_SERVER_ADDRESS, ipv6?UDP_SERVER_PORT_V6:UDP_SERVER_PORT_V4 ));

				}catch( Throwable e){
				}
			}
		}catch( NetworkAdminException e ){

			throw( e );

		}catch( Throwable e ){

			throw( new NetworkAdminException( "Inbound test failed", e ));

		}finally{

			packet_handler.setExplicitBindAddress( null, false );

			handler.release();
		}
	}
}
