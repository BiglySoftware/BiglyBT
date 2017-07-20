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
import java.util.LinkedList;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.AEPriorityMixin;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.AEThread;
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

	final LinkedList	msg_queue			= new LinkedList();
	final AESemaphore	msg_queue_sem		= new AESemaphore( "NetworkGlueUDP" );
	final AESemaphore	msg_queue_slot_sem	= new AESemaphore( "NetworkGlueUDP", 128 );

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

		new AEThread( "NetworkGlueUDP", true )
		{
			@Override
			public void
			runSupport()
			{
				while( true ){


					InetSocketAddress	target_address 	= null;
					byte[]				data			= null;

					msg_queue_sem.reserve();

					synchronized( msg_queue ){

						Object[]	entry = (Object[])msg_queue.removeFirst();

						target_address 	= (InetSocketAddress)entry[0];
						data			= (byte[])entry[1];
					}

					msg_queue_slot_sem.release();

					total_packets_sent++;
					total_bytes_sent	+= data.length;

					try{
						handler.primordialSend( data, target_address );

					}catch( Throwable e ){

						Logger.log(new LogEvent( LOGID, "Primordial UDP send failed: " + Debug.getNestedExceptionMessage(e)));

					}finally{

						try{
							Thread.sleep(3);

						}catch( Throwable e ){

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
		msg_queue_slot_sem.reserve();

		synchronized( msg_queue ){

			msg_queue.add( new Object[]{ target, data });
		}

		msg_queue_sem.release();

		return( data.length );
	}

	@Override
	public long[]
	getStats()
	{
		return( new long[]{ total_packets_sent, total_bytes_sent, total_packets_received, total_bytes_received });
	}
}
