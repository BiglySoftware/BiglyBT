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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.peermanager.messaging.MessagingUtil;
import com.biglybt.core.peermanager.messaging.azureus.AZUTMetaData;
import com.biglybt.core.util.BDecoder;
import com.biglybt.core.util.BEncoder;
import com.biglybt.core.util.DirectByteBuffer;

public class
UTMetaData
	implements LTMessage, AZUTMetaData
{
	private final byte version;
	private DirectByteBuffer buffer = null;

	private int					msg_type;
	private int					piece;
	private DirectByteBuffer	metadata;
	private int					total_size;

	public
	UTMetaData(
		int		_piece,
		byte	_version )
	{
		msg_type	= MSG_TYPE_REQUEST;
		piece		= _piece;
		version		= _version;
	}

	public
	UTMetaData(
		int				_piece,
		ByteBuffer		_data,
		int				_total_size,
		byte			_version )
	{
		msg_type	= _data==null?MSG_TYPE_REJECT:MSG_TYPE_DATA;
		piece		= _piece;
		total_size	= _total_size;
		version		= _version;

		if ( _data != null ){

			metadata = new DirectByteBuffer( _data );
		}
	}

	public
	UTMetaData(
		Map					map,
		DirectByteBuffer	data,
		byte				_version )
	{
		if ( map != null ){

			msg_type = ((Long)map.get( "msg_type" )).intValue();
			piece	 = ((Long)map.get( "piece" )).intValue();
		}

		metadata	= data;
		version		= _version;
	}

	@Override
	public String
	getID()
	{
		return( ID_UT_METADATA );
	}

	@Override
	public byte[]
	getIDBytes()
	{
		return( ID_UT_METADATA_BYTES );
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
		return SUBID_UT_METADATA;
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
		return( ID_UT_METADATA );
	}

	@Override
	public int
	getMessageType()
	{
		return( msg_type );
	}

	@Override
	public int
	getPiece()
	{
		return( piece );
	}

	@Override
	public DirectByteBuffer
	getMetadata()
	{
		return( metadata );
	}

	@Override
	public void
	setMetadata(
		DirectByteBuffer	b )
	{
		metadata = b;
	}

	@Override
	public DirectByteBuffer[]
	getData()
	{
		if ( buffer == null ){

			Map payload_map = new HashMap();

			payload_map.put( "msg_type", new Long( msg_type ));
			payload_map.put( "piece", new Long(piece));

			if ( total_size > 0 ){

				payload_map.put( "total_size", total_size );
			}

			buffer = MessagingUtil.convertPayloadToBencodedByteStream(payload_map, DirectByteBuffer.AL_MSG_UT_METADATA );
		}

		if ( msg_type == MSG_TYPE_DATA ){

			return new DirectByteBuffer[]{ buffer, metadata };

		}else{

			return new DirectByteBuffer[]{ buffer };
		}
	}



	@Override
	public Message
	deserialize(
		DirectByteBuffer 	data,
		byte 				version )

		throws MessageException
	{
		int	pos = data.position( DirectByteBuffer.SS_MSG );

		byte[] dict_bytes = new byte[ Math.min( 128, data.remaining( DirectByteBuffer.SS_MSG )) ];

		data.get( DirectByteBuffer.SS_MSG, dict_bytes );

		try{
			Map root = BDecoder.decode( dict_bytes );

			data.position( DirectByteBuffer.SS_MSG, pos + BEncoder.encode( root ).length );

			UTMetaData result = new UTMetaData( root, data, version );
			
				// data is assigned to metadata and will be destroyed when "destroy" called
			
			return( result );
			
		}catch( Throwable e ){

			e.printStackTrace();

			throw( new MessageException( "decode failed", e ));
		}
	}


	@Override
	public void
	destroy()
	{
		if ( buffer != null ){

			buffer.returnToPool();
		}

		if ( metadata != null ){

			metadata.returnToPool();
		}
	}
}