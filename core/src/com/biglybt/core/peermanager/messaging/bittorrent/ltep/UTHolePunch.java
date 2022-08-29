/*
 * Created on Mar 7, 2012
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


package com.biglybt.core.peermanager.messaging.bittorrent.ltep;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.DirectByteBufferPool;

public class
UTHolePunch
	implements LTMessage
{
	public static byte MT_RENDEZVOUS	= 0;
	public static byte MT_CONNECT		= 1;
	public static byte MT_ERROR			= 2;
	
	public static int	ERR_NONE				= 0;
	public static int	ERR_NO_SUCH_PEER		= 1;
	public static int	ERR_NOT_CONNECTED		= 2;
	public static int	ERR_NO_SUPPORT			= 3;
	public static int	ERR_NO_SELF				= 4;
	
	private final byte version;
	private DirectByteBuffer buffer = null;

	private final byte			message_type;;
	private final InetAddress	address;
	private final int			port;
	private final int			error_code;
	
	public
	UTHolePunch(
		byte		_message_type,
		InetAddress	_address,
		int			_port,
		int			_error,
		byte		_version )
	{
		message_type	= _message_type;
		address			= _address;
		port			= _port;
		error_code		= _error;
		version			= _version;
	}

	public
	UTHolePunch(
		UTHolePunch		_request,
		int				_error_code,
		byte			_version )
	{
		message_type	= MT_ERROR;
		address			= _request.address;
		port			= _request.port;
		error_code		= _error_code;
		version			= _version;
	}
	
	@Override
	public String
	getID()
	{
		return( ID_UT_HOLEPUNCH );
	}

	@Override
	public byte[]
	getIDBytes()
	{
		return( ID_UT_HOLEPUNCH_BYTES );
	}

	@Override
	public String
	getFeatureID()
	{
		return LTMessage.LT_FEATURE_ID;
	}

	@Override
	public int
	getFeatureSubID()
	{
		return SUBID_UT_HOLEPUNCH;
	}

	@Override
	public int
	getType()
	{
		return Message.TYPE_PROTOCOL_PAYLOAD;
	}

	@Override
	public byte
	getVersion()
	{
		return version;
	}

	@Override
	public String
	getDescription()
	{
		return( ID_UT_HOLEPUNCH );
	}

	public int
	getMessageType()
	{
		return( message_type );
	}
	
	public int
	getPort()
	{
		return( port );
	}

	public InetAddress
	getAddress()
	{
		return( address );
	}
	
	public int
	getErrorCode()
	{
		return( error_code );
	}
	
	@Override
	public DirectByteBuffer[]
	getData()
	{
		if ( buffer == null) {

			boolean v4 = address instanceof Inet4Address;
			
			buffer = DirectByteBufferPool.getBuffer(DirectByteBuffer.AL_MSG_UT_HOLEPUNCH, 1+1+(v4?4:16)+2+4 );

			buffer.put(DirectByteBuffer.SS_MSG, message_type );
			
			buffer.put(DirectByteBuffer.SS_MSG, (byte)(v4?0:1));
			
			buffer.put(DirectByteBuffer.SS_MSG,address.getAddress());
			
			buffer.putShort(DirectByteBuffer.SS_MSG, (short)port);
			
			if ( message_type == MT_ERROR ){
			
				buffer.putInt(DirectByteBuffer.SS_MSG, error_code );
			}
			
			buffer.flip(DirectByteBuffer.SS_MSG);
		}

		return( new DirectByteBuffer[]{ buffer });
	}

	@Override
	public Message
	deserialize(
		DirectByteBuffer 	data,
		byte 				version )

		throws MessageException
	{
		try{
			byte message_type = data.get( DirectByteBuffer.SS_MSG );
	
			byte address_type = data.get( DirectByteBuffer.SS_MSG );
			
			byte[] address_bytes = new byte[ address_type==0?4:16 ];
			
			data.get( DirectByteBuffer.SS_MSG, address_bytes );
			
			InetAddress address = InetAddress.getByAddress( address_bytes );
			
			int port = data.getShort( DirectByteBuffer.SS_MSG )&0x0000ffff;
			
				// BEP seems to indicate that error is always encoded, however the reality
				// is that it is only encoded for error messages
			
			int error_code = message_type==MT_ERROR?data.getInt( DirectByteBuffer.SS_MSG ):0;
			
			data.returnToPool();
						
			return( new UTHolePunch( message_type, address, port, error_code, version ));
			
		}catch( UnknownHostException e ){
			
			throw( new MessageException( "invalid address", e ));
		}
	}

	@Override
	public void
	destroy()
	{
		if ( buffer != null ){

			buffer.returnToPool();
		}
	}
}