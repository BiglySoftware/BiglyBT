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

/**
 * @author parg
 *
 */

public class
ConcurrentHasherRequest
{
	private static final AEMonitor		class_mon = new AEMonitor( "ConcHashRequest:class" );

	private final ByteBuffer							buffer;
	private ConcurrentHasherRequestListener		listener;

	private final int									size;
	private byte[]								result;
	private boolean								cancelled;
	private final boolean								low_priority;

	private final AESemaphore	sem = new AESemaphore("ConcHashRequest");

	protected
	ConcurrentHasherRequest(
		ConcurrentHasher					_concurrent_hasher,
		ByteBuffer							_buffer,
		ConcurrentHasherRequestListener		_listener,
		boolean								_low_priorty )
	{
		buffer				= _buffer;
		listener			= _listener;
		low_priority		= _low_priorty;

		size				= buffer.limit() - buffer.position();
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

			try{
				class_mon.enter();

				listener_copy	= listener;

				listener	= null;

			}finally{

				class_mon.exit();
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

				try{
					class_mon.enter();

					listener_copy	= listener;

					listener	= null;

				}finally{

					class_mon.exit();
				}

				if ( listener_copy != null ){

					listener_copy.complete( this );
				}
			}
		}
	}
}
