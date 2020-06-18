/*
 * Created on 09-Sep-2004
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

package com.biglybt.core.util;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;

/**
 * @author parg
 *
 */

public class
ConcurrentHasherRequest
{
	private final int							hash_version;
	private final ByteBuffer					buffer;
	private final int							piece_size;
	private final long							v2_file_size;
	
	private List<List<byte[]>>					v2_hash_tree;
	
	private ConcurrentHasherRequestListener		listener;

	private final int							size;
	private byte[]								result;
	private boolean								cancelled;
	private final boolean						low_priority;

	private final AESemaphore	sem = new AESemaphore("ConcHashRequest");

	protected
	ConcurrentHasherRequest(
		ConcurrentHasher					_concurrent_hasher,
		ByteBuffer							_buffer,
		int									_hash_version,
		int									_piece_size,
		long								_v2_file_size,
		ConcurrentHasherRequestListener		_listener,
		boolean								_low_priorty )
	{
		hash_version		= _hash_version;
		buffer				= _buffer;
		piece_size			= _piece_size;
		v2_file_size		= _v2_file_size;
		listener			= _listener;
		low_priority		= _low_priorty;

		size				= buffer.limit() - buffer.position();
	}

	public int
	getHashVersion()
	{
		return( hash_version );
	}
	
		/**
		 * synchronously get the result of the hash - null returned if it is cancelled
		 * @return
		 */

	public byte[]
	getResult()
	{
		sem.reserve();

		return( result );
	}

	public List<List<byte[]>>
	getHashTree()
	{
		return( v2_hash_tree );
	}
	
		/**
		 * cancel the hash request. If it is cancelled before it is completed then
		 * a subsequent call to getResult will return null
		 */

	public void
	cancel()
	{
		if ( !cancelled ){

			cancelled	= true;

			sem.releaseForever();

			ConcurrentHasherRequestListener	listener_copy;

			synchronized( this ){

				listener_copy	= listener;

				listener	= null;
			}

			if ( listener_copy != null ){

				listener_copy.complete( this );
			}
		}
	}

	public boolean
	getCancelled()
	{
		return( cancelled );
	}

	public int
	getSize()
	{
		return( size );
	}

	public boolean
	isLowPriority()
	{
		return( low_priority );
	}

	protected void
	run(
		SHA1Hasher	hasher )
	{
		if ( !cancelled ){

			if ( AEDiagnostics.ALWAYS_PASS_HASH_CHECKS ){

				result = new byte[0];

			}else{

				result = hasher.calculateHash( buffer );
			}

			sem.releaseForever();

			if ( !cancelled ){

				ConcurrentHasherRequestListener	listener_copy;

				synchronized( this ){

					listener_copy	= listener;

					listener	= null;
				}

				if ( listener_copy != null ){

					listener_copy.complete( this );
				}
			}
		}
	}
	
	protected void
	run(
		MessageDigest	hasher )
	{
		if ( !cancelled ){

			if ( AEDiagnostics.ALWAYS_PASS_HASH_CHECKS ){

				result = new byte[0];

			}else{

				int block_size = 16*1024;
				
				int	rem = buffer.remaining();
				int pos	= buffer.position();
								
				List<byte[]> leaf_digests = new ArrayList<>( piece_size / block_size );
				
				while( rem > 0 ){
				
					buffer.position( pos );
					
					int len = Math.min( rem, block_size );
					
					buffer.limit( pos + len );
					
					hasher.update( buffer );
				
					byte[] digest = hasher.digest();
					
					leaf_digests.add( digest );
					
					rem -= len;
					pos += len;
				}
					
				byte[]	zero_buffer = new byte[hasher.getDigestLength()];

				long leaf_count;
				
				if ( v2_file_size < piece_size ){
					
						// files smaller than a piece just use the hash tree root 
					
					long highestOneBit = Long.highestOneBit( v2_file_size );
					
					long leaf_width;
					
					if ( v2_file_size == highestOneBit ) {
						
						leaf_width = v2_file_size;
						
					}else{
						
						leaf_width =  highestOneBit << 1;
					}
					
					leaf_count = leaf_width / block_size;
					
					if ( leaf_count == 0 ){
						
						leaf_count = 1;
					}
				}else{
					
					leaf_count = piece_size / block_size;
				}
				
				while( leaf_digests.size() < leaf_count ){
					
					leaf_digests.add( zero_buffer );
				}
				
				List<byte[]> current_level = leaf_digests;
					
				v2_hash_tree = new ArrayList<>(10);
				
				while( current_level.size() > 1 ){
						
					v2_hash_tree.add( current_level );
					
					List<byte[]> next_level = new ArrayList<byte[]>(current_level.size()/2);
																	
					for ( int i=0;i<current_level.size();i+=2 ){
						
						hasher.update( current_level.get(i));
						hasher.update( current_level.get(i+1));
						
						byte[] hash = hasher.digest();
						
						next_level.add( hash );
					}
					
					current_level = next_level;
				}
				
				result = current_level.get(0);
			}

			sem.releaseForever();

			if ( !cancelled ){

				ConcurrentHasherRequestListener	listener_copy;

				synchronized( this ){

					listener_copy	= listener;

					listener	= null;
				}

				if ( listener_copy != null ){

					listener_copy.complete( this );
				}
			}
		}
	}
	
}
