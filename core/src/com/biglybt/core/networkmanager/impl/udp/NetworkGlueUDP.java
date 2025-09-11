/*
 * Created on 22 Jun 2006
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

package com.biglybt.core.networkmanager.impl.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.concurrent.LinkedBlockingQueue;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.AEPriorityMixin;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;
import com.biglybt.net.udp.uc.PRUDPPacketHandler;
import com.biglybt.net.udp.uc.PRUDPPacketHandlerFactory;
import com.biglybt.net.udp.uc.PRUDPPrimordialHandler;

public class
NetworkGlueUDP
	implements NetworkGlue, PRUDPPrimordialHandler, AEPriorityMixin

{
	static final LogIDs LOGID = LogIDs.NET;

	private final NetworkGlueListener		listener;

	PRUDPPacketHandler handler;

	final LinkedBlockingQueue<Object[]>		msg_queue = new LinkedBlockingQueue<>( 256 );
	
	private long total_packets_received;
	private long total_bytes_received;
	long total_packets_sent;
	long total_bytes_sent;

	protected
	NetworkGlueUDP(
		NetworkGlueListener		_listener )
	{
		listener	= _listener;

		COConfigurationManager.addAndFireParameterListeners(
			new String[]{ "UDP.Listen.Port", "UDP.Listen.Port.Enable" },
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String	name )
				{
					boolean	enabled = COConfigurationManager.getBooleanParameter( "UDP.Listen.Port.Enable" );

					if ( enabled ){

						int	port = COConfigurationManager.getIntParameter( "UDP.Listen.Port" );

						if ( handler == null || port != handler.getPort()){

							if ( handler != null ){

								Logger.log(new LogEvent(LOGID, "Deactivating UDP listener on port " + handler.getPort()));

								handler.removePrimordialHandler( NetworkGlueUDP.this );
							}

							Logger.log(new LogEvent(LOGID, "Activating UDP listener on port " + port ));

							handler = PRUDPPacketHandlerFactory.getHandler( port );

							handler.addPrimordialHandler( NetworkGlueUDP.this );
						}
					}else{

						if ( handler != null ){

							Logger.log(new LogEvent(LOGID, "Deactivating UDP listener on port " + handler.getPort()));

							handler.removePrimordialHandler( NetworkGlueUDP.this );
						}
					}
				}
			});

		new AEThread2("NetworkGlueUDP", true )
		{
			@Override
			public void
			run()
			{
				while( true ){


					InetSocketAddress	target_address 	= null;
					byte[]				data			= null;

					try{
						Object[]	entry = msg_queue.take();
	
						target_address 	= (InetSocketAddress)entry[0];
						data			= (byte[])entry[1];
	
						total_packets_sent++;
						total_bytes_sent	+= data.length;
	
						try{
							handler.primordialSend( data, target_address );
	
						}catch( Throwable e ){
	
							Logger.log(new LogEvent( LOGID, "Primordial UDP send failed: " + Debug.getNestedExceptionMessage(e)));
	
						}
					}catch( Throwable e ){
						
						Debug.out( e );
						
						try{
							Thread.sleep( 1000 );
							
						}catch( Throwable f ){
							
							Debug.out( f );
							
							break;
						}
					}
				}
			}
		}.start();
	}

	@Override
	public int
	getPriority()
	{
		return( AEPriorityMixin.PRIORITY_LOW );
	}

	@Override
	public boolean
	packetReceived(
		DatagramPacket	packet )
	{
		if ( packet.getLength() >= 12 ){

			byte[]	data = packet.getData();

				// first and third word must have something set in mask: 0xfffff800

			if ( 	(	( data[0] & 0xff ) != 0 ||
						( data[1] & 0xff ) != 0 ||
						( data[2] & 0xf8 ) != 0 ) &&

					(	( data[8] & 0xff ) != 0 ||
						( data[9] & 0xff ) != 0 ||
						( data[10]& 0xf8 ) != 0 )){

				total_packets_received++;
				total_bytes_received += packet.getLength();

				listener.receive( handler.getPort(), new InetSocketAddress( packet.getAddress(), packet.getPort()), packet.getData(), packet.getLength());

					// consume this packet

				return( true );
			}
		}

			// don't consume it, allow it to be passed on for further processing

		return( false );
	}

	@Override
	public int
	send(
		int					local_port,
		InetSocketAddress	target,
		byte[]				data )

		throws IOException
	{
		try{
			msg_queue.put( new Object[]{ target, data });
			
		}catch( Throwable e ){
			
			throw( new IOException( "Failed to add to msg queue", e ));
		}

		return( data.length );
	}

	@Override
	public long[]
	getStats()
	{
		return( new long[]{ total_packets_sent, total_bytes_sent, total_packets_received, total_bytes_received });
	}
}
