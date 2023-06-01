/*
 * Created on Jun 8, 2010
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.net.udp.uc.impl;

import java.io.*;
import java.net.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.proxy.AEProxySelector;
import com.biglybt.core.proxy.AEProxySelectorFactory;
import com.biglybt.core.util.AddressUtils;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.HostNameToIPResolver;
import com.biglybt.net.udp.uc.*;

public class
PRUDPPacketHandlerSocks
	implements PRUDPPacketHandler, PRUDPPacketHandlerImpl.PacketTransformer
{
	private static String	socks_host;
	private static int		socks_port;
	private static String	socks_user;
	private static String	socks_password;

	static{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"Proxy.Host",
				"Proxy.Port",
				"Proxy.Username",
				"Proxy.Password",
			},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String parameter_name )
				{
				    socks_host 		= COConfigurationManager.getStringParameter("Proxy.Host").trim();
					socks_port 		= Integer.parseInt(COConfigurationManager.getStringParameter("Proxy.Port").trim());
					socks_user 		= COConfigurationManager.getStringParameter("Proxy.Username").trim();
					socks_password 	= COConfigurationManager.getStringParameter("Proxy.Password").trim();

					if ( socks_user.equalsIgnoreCase("<none>")){
						socks_user = "";
					}

				}
			});
	}

	final private InetSocketAddress		target;

	private Socket					control_socket;

	private InetSocketAddress		relay;

	private PRUDPPacketHandler		delegate;

	private byte[]	packet_out_header;

	protected
	PRUDPPacketHandlerSocks(
		InetSocketAddress		_target )

		throws PRUDPPacketHandlerException
	{
		target	= _target;

		boolean	ok = false;

		AEProxySelector	proxy_selector = AEProxySelectorFactory.getSelector();

		Proxy proxy = proxy_selector.getSOCKSProxy( socks_host, socks_port, target );

		boolean		proxy_connected = false;
		Throwable	error			= null;

		try{
		    delegate = PRUDPPacketHandlerImpl.createPacketHandler( 0, null, this );

			control_socket = new Socket( Proxy.NO_PROXY );

			InetSocketAddress proxy_address = (InetSocketAddress)proxy.address();

			control_socket.connect( proxy_address );

			proxy_connected	= true;

			DataOutputStream 	dos = new DataOutputStream( new BufferedOutputStream( control_socket.getOutputStream(), 256 ));
			DataInputStream 	dis = new DataInputStream( control_socket.getInputStream());

			dos.writeByte( (byte)5 ); // socks 5
			dos.writeByte( (byte)2 ); // 2 methods
			dos.writeByte( (byte)0 ); // no auth
			dos.writeByte( (byte)2 ); // user/pw

			dos.flush();

		    dis.readByte();  // version byte

		    byte method = dis.readByte();

		    if ( method != 0 && method != 2 ){

		        throw new IOException( "SOCKS 5: no valid method [" + method + "]" );
		    }

		      // auth

		    if ( method == 2 ) {

		    	dos.writeByte( (byte)1 ); // user/pw version
		    	dos.writeByte( (byte)socks_user.length() ); // user length
		    	dos.write( socks_user.getBytes() );
		    	dos.writeByte( (byte)socks_password.length() ); // password length
		    	dos.write( socks_password.getBytes() );

		    	dos.flush();

		    	dis.readByte();  // version byte

		    	byte status = dis.readByte();

		        if ( status != 0 ){

		        	throw( new IOException( "SOCKS 5: authentication fails [status=" +status+ "]" ));
		        }
		    }

		    String	mapped_ip;

		    if ( target.isUnresolved() || target.getAddress() == null ){

		    		// deal with long "hostnames" that we get for, e.g., I2P destinations

		      	mapped_ip = AEProxyFactory.getAddressMapper().internalise( target.getHostName() );

		    }else{

		      	mapped_ip = AddressUtils.getHostNameNoResolve( target );
		    }

		    dos.writeByte( (byte)5 ); // version
		    dos.writeByte( (byte)3 ); // udp associate
		    dos.writeByte( (byte)0 ); // reserved

	    	dos.writeByte((byte)1);
	    	dos.write( new byte[4] );

		    dos.writeShort( (short)delegate.getPort()); // port

		    dos.flush();

		    dis.readByte();	// ver

		    byte reply = dis.readByte();

		    if ( reply != 0 ){

		    		// special hack for internal socks servers just being used for plumbing connections
		    		// for other protocols - 0x45 means 'go transparent'

		    	if ( reply == 0x45 && proxy_address.getAddress().isLoopbackAddress()){

		    		control_socket.close();

		    		control_socket = null;

		    		ok = true;

		    			// relay is null here - this drives other direct behaviour

		    		return;
		    	}

	        	throw( new IOException( "SOCKS 5: udp association fails [reply=" +reply+ "]" ));
		    }

		    dis.readByte();	// reserved

		    InetAddress	relay_address;

		    byte atype = dis.readByte();

		    if ( atype == 1 ){

		    	byte[]	bytes = new byte[4];

		    	dis.readFully( bytes );

		    	relay_address = InetAddress.getByAddress( bytes );

		    }else if ( atype == 3 ){

		    	byte	len = dis.readByte();

		    	byte[] bytes = new byte[(int)len&0xff ];

		    	dis.readFully( bytes );

		    	relay_address = InetAddress.getByName( new String( bytes ));

		    }else{

		    	byte[]	bytes = new byte[16];

		    	dis.readFully( bytes );

		    	relay_address = InetAddress.getByAddress( bytes );

		    }

		    int	relay_port = ((dis.readByte()<<8)&0xff00) | (dis.readByte() & 0x00ff );

		    if ( relay_address.isAnyLocalAddress()){

		    	relay_address = control_socket.getInetAddress();
		    }

		    relay = new InetSocketAddress( relay_address, relay_port );

		    	// use the maped ip for dns resolution so we don't leak the
		    	// actual address if this is a secure one (e.g. I2P one)

		    ByteArrayOutputStream	baos_temp 		= new ByteArrayOutputStream();
		    DataOutputStream		dos_temp	= new DataOutputStream( baos_temp );

		    dos_temp.writeByte(0);	// resv
		    dos_temp.writeByte(0);	// resv
		    dos_temp.writeByte(0);	// frag (none)

		    try {
		    	byte[] ip_bytes = HostNameToIPResolver.syncResolve( mapped_ip ).getAddress();

		    	dos_temp.writeByte( ip_bytes.length==4?(byte)1:(byte)4 );
		    	dos_temp.write( ip_bytes );


		    }catch( Throwable e ){

		    	dos_temp.writeByte( (byte)3 );  // address type = domain name
		    	dos_temp.writeByte( (byte)mapped_ip.length() );  // address type = domain name
		    	dos_temp.write( mapped_ip.getBytes() );

		    }

		    dos_temp.writeShort( (short)target.getPort() ); // port

		    dos_temp.flush();
		    packet_out_header = baos_temp.toByteArray();


			ok = true;

			Thread.sleep(1000);

		}catch( Throwable e ){

			error = e;

			throw( new PRUDPPacketHandlerException( "socks setup failed: " + Debug.getNestedExceptionMessage(e), e));

		}finally{

			if ( !proxy_connected ){

				proxy_selector.connectFailed( proxy, error );
			}

			if ( !ok ){

				try{
					control_socket.close();

				}catch( Throwable e ){

					Debug.out( e );

				}finally{

					control_socket = null;
				}

				if ( delegate != null ){

					try{
					    delegate.destroy();

					}finally{

						delegate = null;
					}
				}
			}
		}
	}

	@Override
	public void
	transformSend(
		DatagramPacket	packet )
	{
		if ( relay == null ){

			return;
		}

		byte[]	data 		= packet.getData();
		int		data_len	= packet.getLength();

		byte[]	new_data = new byte[data_len+packet_out_header.length];

		System.arraycopy( packet_out_header, 0, new_data, 0, packet_out_header.length );
		System.arraycopy( data, 0, new_data, packet_out_header.length, data_len);

		packet.setData( new_data );
	}

	@Override
	public void
	transformReceive(
		DatagramPacket	packet )
	{
		if ( relay == null ){

			return;
		}

		byte[]	data 		= packet.getData();
		int		data_len	= packet.getLength();

		DataInputStream dis = new DataInputStream( new ByteArrayInputStream( data, 0, data_len ));

		try{
			dis.readByte();	// res
			dis.readByte();	// res
			dis.readByte();	// assume no frag

			byte	atype = dis.readByte();

			int	encap_len = 4;
			if ( atype == 1 ){

				encap_len += 4;

			}else if ( atype == 3 ){

				encap_len += 1 + (dis.readByte()&0xff);

			}else{

				encap_len += 16;
			}

			encap_len += 2;	// port

			byte[]	new_data = new byte[data_len-encap_len];

			System.arraycopy( data, encap_len, new_data, 0, data_len - encap_len );

			packet.setData( new_data );

		}catch( IOException e ){

			Debug.out( e );
		}
	}

	private void
	checkAddress(
		InetSocketAddress			destination )

		throws PRUDPPacketHandlerException
	{
		if ( !destination.equals( target )){

			throw( new PRUDPPacketHandlerException( "Destination mismatch" ));
		}
	}

	@Override
	public void
	sendAndReceive(
		PRUDPPacket					request_packet,
		InetSocketAddress			destination_address,
		PRUDPPacketReceiver			receiver,
		long						timeout,
		int							priority )

		throws PRUDPPacketHandlerException
	{
		checkAddress( destination_address );

		if ( relay != null ){

			destination_address = relay;
		}

		delegate.sendAndReceive( request_packet, destination_address, receiver, timeout, priority );
	}

	@Override
	public PRUDPPacket
	sendAndReceive(
		PasswordAuthentication		auth,
		PRUDPPacket					request_packet,
		InetSocketAddress			destination_address )

		throws PRUDPPacketHandlerException
	{
		checkAddress( destination_address );

		if ( relay != null ){

			destination_address = relay;
		}

		return( delegate.sendAndReceive( auth, request_packet, destination_address));
	}

	@Override
	public PRUDPPacket
	sendAndReceive(
		PasswordAuthentication		auth,
		PRUDPPacket					request_packet,
		InetSocketAddress			destination_address,
		long						timeout_millis )

		throws PRUDPPacketHandlerException
	{
		checkAddress( destination_address );

		if ( relay != null ){

			destination_address = relay;
		}

		return( delegate.sendAndReceive(auth, request_packet, destination_address, timeout_millis ));
	}

	@Override
	public PRUDPPacket
	sendAndReceive(
		PasswordAuthentication		auth,
		PRUDPPacket					request_packet,
		InetSocketAddress			destination_address,
		long						timeout_millis,
		int							priority )

		throws PRUDPPacketHandlerException
	{
		checkAddress( destination_address );

		if ( relay != null ){

			destination_address = relay;
		}

		return( delegate.sendAndReceive(auth, request_packet, destination_address, timeout_millis, priority ));
	}

	@Override
	public void
	send(
		PRUDPPacket					request_packet,
		InetSocketAddress			destination_address )

		throws PRUDPPacketHandlerException
	{
		checkAddress( destination_address );

		if ( relay != null ){

			destination_address = relay;
		}

		delegate.send( request_packet, destination_address );
	}

	@Override
	public PRUDPRequestHandler
	getRequestHandler()
	{
		return( delegate.getRequestHandler());
	}

	@Override
	public void
	setRequestHandler(
		PRUDPRequestHandler	request_handler )
	{
		delegate.setRequestHandler( request_handler );
	}

	@Override
	public void
	primordialSend(
		byte[]				data,
		InetSocketAddress	target )

		throws PRUDPPacketHandlerException
	{
		throw( new PRUDPPacketHandlerException( "not imp" ));
	}

	@Override
	public boolean
	hasPrimordialHandler()
	{
		return( delegate.hasPrimordialHandler());
	}

	@Override
	public void
	addPrimordialHandler(
		PRUDPPrimordialHandler	handler )
	{
	}

	@Override
	public void
	removePrimordialHandler(
		PRUDPPrimordialHandler	handler )
	{
	}

	@Override
	public int
	getPort()
	{
		return( delegate.getPort());
	}

	@Override
	public InetAddress
	getCurrentBindAddress()
	{
		return( delegate.getCurrentBindAddress());
	}

	@Override
	public void
	setDelays(
		int		send_delay,
		int		receive_delay,
		int		queued_request_timeout )
	{
		delegate.setDelays(send_delay, receive_delay, queued_request_timeout);
	}

	@Override
	public void
	setExplicitBindAddress(
		InetAddress	address,
		boolean		autoDelegate )
	{
		delegate.setExplicitBindAddress( address, autoDelegate );
	}

	@Override
	public InetAddress
	getExplicitBindAddress()
	{
		return( delegate.getExplicitBindAddress());
	}
	
	@Override
	public PRUDPPacketHandlerStats
	getStats()
	{
		return( delegate.getStats());
	}

	@Override
	public PRUDPPacketHandler
	openSession(
		InetSocketAddress	target )

		throws PRUDPPacketHandlerException
	{
		throw( new PRUDPPacketHandlerException( "not supported" ));
	}

	@Override
	public void
	closeSession()

		throws PRUDPPacketHandlerException
	{
		if ( control_socket != null ){

			try{
				control_socket.close();

				control_socket = null;

			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		if ( delegate != null ){

			delegate.destroy();
		}
	}

	@Override
	public void
	destroy()
	{
		try{
			closeSession();

		}catch( Throwable e ){

			Debug.out( e );
		}
	}
}
