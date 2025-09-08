/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.net.udp.uc.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.proxy.AEProxyFactory.PluginProxy;
import com.biglybt.core.util.BDecoder;
import com.biglybt.core.util.BEncoder;
import com.biglybt.core.util.Debug;
import com.biglybt.net.udp.uc.PRUDPPacket;
import com.biglybt.net.udp.uc.PRUDPPacketHandler;
import com.biglybt.net.udp.uc.PRUDPPacketHandlerException;
import com.biglybt.net.udp.uc.PRUDPPacketHandlerStats;
import com.biglybt.net.udp.uc.PRUDPPacketReceiver;
import com.biglybt.net.udp.uc.PRUDPPacketReply;
import com.biglybt.net.udp.uc.PRUDPPrimordialHandler;
import com.biglybt.net.udp.uc.PRUDPRequestHandler;

public class 
PRUDPPacketHandlerPluginProxy
	implements PRUDPPacketHandler
{
	private static final int DEFAULT_TIMEOUT = 60*1000;
			
	private final PluginProxy 				pp;
	private final PRUDPPacketHandler		delegate;
	private final InetSocketAddress			target;
	
	private final Socket					socket;
	
	protected
	PRUDPPacketHandlerPluginProxy(
		PRUDPPacketHandler	_delegate,	
		InetSocketAddress	_target,
		PluginProxy			_pp )
	
		throws Exception
	{
		delegate	= _delegate;
		target		= _target;
		pp 			= _pp;
				
		Proxy proxy = pp.getProxy();
		
		socket = new Socket( proxy );
		
		socket.setSoTimeout( 30*1000 );
		
		socket.connect( new InetSocketAddress( pp.getHost(), pp.getPort()), 60*1000 );
	}
	
	public PRUDPPacket
	sendAndReceive(
		PasswordAuthentication		auth,
		PRUDPPacket					request_packet,
		InetSocketAddress			destination_address )

		throws PRUDPPacketHandlerException
	{
		return( sendAndReceive(auth, request_packet, destination_address, DEFAULT_TIMEOUT ));
	}

	public PRUDPPacket
	sendAndReceive(
		PasswordAuthentication		auth,
		PRUDPPacket					request_packet,
		InetSocketAddress			destination_address,
		long						timeout_millis )

		throws PRUDPPacketHandlerException
	{
		return( sendAndReceive(auth, request_packet, destination_address, timeout_millis, 0 ));
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
		ByteArrayOutputStream baos = new ByteArrayOutputStream( 2048 );
	
		DataOutputStream dos = new DataOutputStream( baos );
		
		try{
		
			request_packet.serialise( dos );
			
			dos.close();
			
			byte[] data = baos.toByteArray();
			
			Map<String,Object> payload_out = new HashMap<>();
			
			payload_out.put( "packet", data );
			payload_out.put( "action", request_packet.getAction());
			payload_out.put( "txn", request_packet.getTransactionId());
			
			byte[] payload_out_bytes = BEncoder.encode(payload_out);
			
			OutputStream os = socket.getOutputStream();
			
			dos = new DataOutputStream(os);
			
			dos.writeInt( payload_out_bytes.length );
			
			dos.write( payload_out_bytes );
			
			dos.flush();
			
			InputStream  is = socket.getInputStream();
			
			socket.setSoTimeout((int)timeout_millis );
			
			DataInputStream dis = new DataInputStream( is );
			
			int len = dis.readInt();
			
			byte[] payload_in_bytes = new byte[len];
			
			int pos = 0;
			
			while( pos < len ){
				
				pos += dis.read( payload_in_bytes, pos, len - pos );
			}
			
			Map<String,Object> payload_in = BDecoder.decode(payload_in_bytes);
			
			PRUDPPacket reply_packet  = 
				PRUDPPacketReply.deserialiseReply(
					this, target,
					new DataInputStream(new ByteArrayInputStream( (byte[])payload_in.get( "packet" ), 0, len)));
			
			return( reply_packet );
			
		}catch( Throwable e ){
		
			throw( new PRUDPPacketHandlerException( "Proxy write fails", e ));
		}
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
		throw( new RuntimeException( "Not supported" ));
	}

	public void
	send(
		PRUDPPacket					request_packet,
		InetSocketAddress			destination_address )
	
			throws PRUDPPacketHandlerException
	{
		throw( new RuntimeException( "Not supported" ));
	}


	public PRUDPRequestHandler
	getRequestHandler()
	{
		throw( new RuntimeException( "Not supported" ));
	}

	public void
	setRequestHandler(
		PRUDPRequestHandler	request_handler )
	{
		throw( new RuntimeException( "Not supported" ));
	}

	public void
	primordialSend(
		byte[]				data,
		InetSocketAddress	target )

		throws PRUDPPacketHandlerException
	{
		throw( new RuntimeException( "Not supported" ));
	}

	public boolean
	hasPrimordialHandler()
	{
		throw( new RuntimeException( "Not supported" ));
	}

	public void
	addPrimordialHandler(
		PRUDPPrimordialHandler	handler )
	{
		throw( new RuntimeException( "Not supported" ));
	}

	public void
	removePrimordialHandler(
		PRUDPPrimordialHandler	handler )
	{
		throw( new RuntimeException( "Not supported" ));
	}

	public int
	getPort()
	{
		throw( new RuntimeException( "Not supported" ));
	}

	public InetAddress
	getCurrentBindAddress()
	{
		throw( new RuntimeException( "Not supported" ));
	}

	public InetAddress
	getExplicitBindAddress()
	{
		throw( new RuntimeException( "Not supported" ));
	}

	public void
	setExplicitBindAddress(
		InetAddress	address,
		boolean		autoDelegate )
	{
		throw( new RuntimeException( "Not supported" ));
	}

	public void
	setDelays(
		int		send_delay,
		int		receive_delay,
		int		queued_request_timeout )
	{
		throw( new RuntimeException( "Not supported" ));
	}

	public PRUDPPacketHandlerStats
	getStats()
	{
		throw( new RuntimeException( "Not supported" ));
	}

	public PRUDPPacketHandler
	openSession(
		InetSocketAddress	target,
		String[]			networks,
		String				reason )

		throws PRUDPPacketHandlerException
	{
		throw( new RuntimeException( "Not supported" ));
	}

	public void
	closeSession()

		throws PRUDPPacketHandlerException
	{
		try{
			socket.close();
			
		}catch( Throwable e ){
			
		}
		
		try{
			delegate.closeSession();
			
		}finally{
			
			pp.setOK( true );
		}
	}

	public void
	destroy()
	{
		throw( new RuntimeException( "Not supported" ));
	}
}
