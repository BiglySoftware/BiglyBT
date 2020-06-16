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

package com.biglybt.core.peermanager.messaging.bittorrent;

import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.DirectByteBufferPool;
import com.biglybt.core.util.SHA256;

public class 
BTHashes
	implements BTMessage
{
	private static int WIRE_SIZE_FIXED = SHA256.DIGEST_LENGTH + 4+4+4+4;
	
	private final byte version;
	
	private DirectByteBuffer buffer = null;
	
	private String description = null;

	
	final private byte[]		pieces_root;
	final private int			base_layer;
	final private int			index;
	final private int			length;
	final private int			proof_layers;
	final private byte[][]		hashes;
	
	public 
	BTHashes( 
		byte[]		_pieces_root,
		int			_base_layer,
		int			_index,
		int			_length,
		int			_proof_layers,
		byte[][]	_hashes,
		byte 		_version )
	{
		pieces_root		= _pieces_root;
		base_layer		= _base_layer;
		index			= _index;
		length			= _length;
		proof_layers	= _proof_layers;
		hashes			= _hashes;
		version 		= _version;
	}
	  
	@Override
	public String getID() {  return BTMessage.ID_BT_HASHES;  }
	
	@Override
	public byte[] getIDBytes() {  return BTMessage.ID_BT_HASHES_BYTES;  }

	@Override
	public String getFeatureID() {  return BTMessage.BT_FEATURE_ID;  }

	@Override
	public int getFeatureSubID() {  return BTMessage.SUBID_BT_HASHES;  }

	@Override
	public int getType() {  return Message.TYPE_PROTOCOL_PAYLOAD;  }

	@Override
	public byte getVersion() { return version; }

	@Override
	public String 
	getDescription() {
		if ( description == null ){
			
			description = BTMessage.ID_BT_HASHES + " " + (pieces_root==null?"null":ByteFormatter.encodeString(pieces_root)) + ": b=" + base_layer + ",i=" + index + ",l=" +length + ",p=" + proof_layers;
		}

		return description;
	}

	@Override
	public DirectByteBuffer[] 
	getData() 
	{
		if ( buffer == null ){
			
			int wire_size = WIRE_SIZE_FIXED + hashes.length * SHA256.DIGEST_LENGTH;
			
			buffer = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_MSG_BT_HASHES, wire_size );
			
			buffer.put( DirectByteBuffer.SS_MSG, pieces_root );
			
			buffer.putInt( DirectByteBuffer.SS_MSG, base_layer );
			buffer.putInt( DirectByteBuffer.SS_MSG, index );
			buffer.putInt( DirectByteBuffer.SS_MSG, length );
			buffer.putInt( DirectByteBuffer.SS_MSG, proof_layers );
			
			for ( byte[] hash: hashes ){
			
				buffer.put(  DirectByteBuffer.SS_MSG, hash );
			}
			
			buffer.flip( DirectByteBuffer.SS_MSG );
		}

		return new DirectByteBuffer[]{ buffer };
	}

	@Override
	public Message 
	deserialize(
		DirectByteBuffer 	data, 
		byte 				version ) 
	
		throws MessageException 
	{
		if ( data == null ){
			
			throw new MessageException( "[" +getID() + "] decode error: data == null" );
		}

		int remaining = data.remaining( DirectByteBuffer.SS_MSG );
		
		if (  remaining < WIRE_SIZE_FIXED ){
			
			throw new MessageException( "[" +getID() + "] decode error: payload.remaining[" + remaining+ "] < " + WIRE_SIZE_FIXED );
		}

		byte[] pieces_root = new byte[ SHA256.DIGEST_LENGTH ];
		
		data.get( DirectByteBuffer.SS_MSG, pieces_root );
		
		int base_layer 		= data.getInt( DirectByteBuffer.SS_MSG );
		int index 			= data.getInt( DirectByteBuffer.SS_MSG );
		int length 			= data.getInt( DirectByteBuffer.SS_MSG );
		int proof_layers 	= data.getInt( DirectByteBuffer.SS_MSG );

		int	hashes_length = data.remaining( DirectByteBuffer.SS_MSG );
		
		if ( hashes_length % SHA256.DIGEST_LENGTH != 0 ){
			
			throw new MessageException( "[" +getID() + "] decode error: payload.remaining[" + hashes_length+ "] not multiple of hash size" );

		}
		
		int hash_count = hashes_length / SHA256.DIGEST_LENGTH;
		
		byte[][] hashes = new byte[hash_count][];
		
		for ( int i=0;i<hash_count;i++){
			
			hashes[i] = new byte[SHA256.DIGEST_LENGTH];
			
			data.get( DirectByteBuffer.SS_MSG, hashes[i] );
		}
		
		data.returnToPool();

		return new BTHashes( pieces_root, base_layer, index, length, proof_layers, hashes, version );
	}

	public byte[]
	getPiecesRoot()
	{
		return( pieces_root );
	}
	
	public int
	getBaseLayer()
	{
		return( base_layer );
	}
	
	public int
	getIndex()
	{
		return( index );
	}
	
	public int
	getLength()
	{
		return( length );
	}
	
	public int
	getProofLayers()
	{
		return( proof_layers );
	}
	
	public byte[][]
	getHashes()
	{
		return( hashes );
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
