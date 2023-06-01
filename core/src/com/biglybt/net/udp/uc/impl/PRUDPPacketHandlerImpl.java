/*
 * File    : PRUDPPacketReceiverImpl.java
 * Created : 20-Jan-2004
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.net.udp.uc.impl;

import java.util.*;
import java.net.*;

import com.biglybt.core.util.*;
import com.biglybt.net.udp.uc.*;

public class
PRUDPPacketHandlerImpl
	implements PRUDPPacketHandler
{
	private static List<PRUDPPacketHandlerImpl>	handlers = new ArrayList<>();
	
	protected static PRUDPPacketHandlerImpl
	createPacketHandler(
		int					port,
		InetAddress			bind_ip,
		PacketTransformer	packet_transformer )
	{
		PRUDPPacketHandlerImpl handler = new PRUDPPacketHandlerImpl( port, bind_ip, packet_transformer );
		
		synchronized( handlers ){
			
			handlers.add( handler );
			
			System.out.println( "Handler created: " + port + "/" + bind_ip + "/" + packet_transformer );
		}
		
		return( handler );
	}

		// alt-protocol delegate constructor
	
	protected static PRUDPPacketHandlerImpl
	createPacketHandler(
		int										port,
		InetAddress								bind_ip,
		PacketTransformer						packet_transformer,
		PRUDPPacketHandlerStatsImpl				stats,
		CopyOnWriteList<PRUDPPrimordialHandler>	primordial_handlers,
		PRUDPRequestHandler						request_handler )
	{
		PRUDPPacketHandlerImpl handler = new PRUDPPacketHandlerImpl( port, bind_ip, packet_transformer, stats, primordial_handlers, request_handler );
		
		synchronized( handlers ){
			
			handlers.add( handler );
		}
		
		return( handler );
	}
		
	private final PRUDPPacketHandlerSupport		impl;
	
	private boolean destroyed;
	
	private
	PRUDPPacketHandlerImpl(
		int					port,
		InetAddress			bind_ip,
		PacketTransformer	packet_transformer )
	{
		impl = new PRUDPPacketHandlerSupport( port, bind_ip, packet_transformer );
	}
	
	private
	PRUDPPacketHandlerImpl(
		int										port,
		InetAddress								bind_ip,
		PacketTransformer						packet_transformer,
		PRUDPPacketHandlerStatsImpl				stats,
		CopyOnWriteList<PRUDPPrimordialHandler>	primordial_handlers,
		PRUDPRequestHandler						request_handler )
	{
		impl = new PRUDPPacketHandlerSupport( port, bind_ip, packet_transformer, stats, primordial_handlers, request_handler );
	}
	
	public void
	sendAndReceive(
		PRUDPPacket					request_packet,
		InetSocketAddress			destination_address,
		PRUDPPacketReceiver			receiver,
		long						timeout,
		int							priority )

		throws PRUDPPacketHandlerException
	{
		impl.sendAndReceive (request_packet, destination_address, receiver, timeout, priority );
	}

	public PRUDPPacket
	sendAndReceive(
		PasswordAuthentication		auth,
		PRUDPPacket					request_packet,
		InetSocketAddress			destination_address )

		throws PRUDPPacketHandlerException
	{
		return( impl.sendAndReceive( auth, request_packet, destination_address ));
	}

	public PRUDPPacket
	sendAndReceive(
		PasswordAuthentication		auth,
		PRUDPPacket					request_packet,
		InetSocketAddress			destination_address,
		long						timeout_millis )

		throws PRUDPPacketHandlerException
	{
		return( impl.sendAndReceive( auth, request_packet, destination_address, timeout_millis ));
	}

	public PRUDPPacket
	sendAndReceive(
		PasswordAuthentication		auth,
		PRUDPPacket					request_packet,
		InetSocketAddress			destination_address,
		long						timeout_millis,
		int							priority )

		throws PRUDPPacketHandlerException
	{
		return( impl.sendAndReceive( auth, request_packet, destination_address, timeout_millis, priority ));
	}

	protected PRUDPPacketHandlerRequestImpl
	sendAndReceive(
		PasswordAuthentication		auth,
		PRUDPPacket					request_packet,
		InetSocketAddress			destination_address,
		PRUDPPacketReceiver			receiver,
		long						timeout,
		int							priority )

		throws PRUDPPacketHandlerException
	{
		return( impl.sendAndReceive( auth, request_packet, destination_address, receiver, timeout, priority ));
	}
	
	public void
	send(
		PRUDPPacket					request_packet,
		InetSocketAddress			destination_address )

		throws PRUDPPacketHandlerException
	{
		impl.send(request_packet, destination_address);
	}

	public PRUDPRequestHandler
	getRequestHandler()
	{
		return( impl.getRequestHandler());
	}

	public void
	setRequestHandler(
		PRUDPRequestHandler	request_handler )
	{
		impl.setRequestHandler(request_handler);
	}

	public void
	primordialSend(
		byte[]				data,
		InetSocketAddress	target )

		throws PRUDPPacketHandlerException
	{
		impl.primordialSend(data, target);
	}

	public boolean
	hasPrimordialHandler()
	{
		return( impl.hasPrimordialHandler());
	}

	public void
	addPrimordialHandler(
		PRUDPPrimordialHandler	handler )
	{
		impl.addPrimordialHandler(handler);
	}

	public void
	removePrimordialHandler(
		PRUDPPrimordialHandler	handler )
	{
		impl.removePrimordialHandler(handler);
	}

	public int
	getPort()
	{
		return( impl.getPort());
	}

	public InetAddress
	getBindIP()
	{
		return( impl.getBindIP());
	}

	public InetAddress
	getCurrentBindAddress()
	{
		return( impl.getCurrentBindAddress());
	}

	public InetAddress
	getExplicitBindAddress()
	{
		return( impl.getExplicitBindAddress());
	}

	public void
	setExplicitBindAddress(
		InetAddress	address,
		boolean		autoDelegate )
	{
		impl.setExplicitBindAddress(address, autoDelegate);
	}

	public void
	setDelays(
		int		send_delay,
		int		receive_delay,
		int		queued_request_timeout )
	{
		impl.setDelays(send_delay, receive_delay, queued_request_timeout);
	}

	protected long
	getSendQueueLength()
	{
		return( impl.getSendQueueLength());
	}
	
	protected long
	getReceiveQueueLength()
	{
		return( impl.getReceiveQueueLength());
	}
	
	public PRUDPPacketHandlerStats
	getStats()
	{
		return( impl.getStats());
	}

	public PRUDPPacketHandler
	openSession(
		InetSocketAddress	target )

		throws PRUDPPacketHandlerException
	{
		return( impl.openSession(target));
	}

	public void
	closeSession()

		throws PRUDPPacketHandlerException
	{
		impl.closeSession();
	}

	public void
	destroy()
	{
		synchronized( handlers ){
		
			destroyed = true;
			
			handlers.remove( this );
		}
		
		impl.destroy();
	}
	
	protected interface
	PacketTransformer
	{
		public void
		transformSend(
			DatagramPacket	packet );

		public void
		transformReceive(
			DatagramPacket	packet );

	}
}
