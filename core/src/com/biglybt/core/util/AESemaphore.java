/*
 * Created on 18-Sep-2004
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

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author parg
 *
 */
public class
AESemaphore
{
	private final String		name;
	
	private final Semaphore		sem;

	private volatile boolean released_forever;
	
	public
	AESemaphore(
		String		_name )
	{
		name	= _name;
		
		sem 	= new Semaphore(0);
	}

	public
	AESemaphore(
		String		_name,
		int			_permits )
	{
		name	= _name;
		
		sem 	= new Semaphore( _permits);
	}
	
	public String
	getName()
	{
		return( name );
	}
	
	public void
	reserve()
	{
		if ( !released_forever ){
			
			sem.acquireUninterruptibly();
		}
	}

	public boolean
	reserve(
		long	max_millis )
	{
		if ( released_forever ){
			
			return( true );
			
		}else{
			
			try{
				return( sem.tryAcquire( max_millis, TimeUnit.MILLISECONDS ));
				
			}catch( InterruptedException e ){
				
				throw( new RuntimeException( e ));
			}
		}
	}
	
	public boolean
	reserveIfAvailable()
	{
		if ( released_forever ){
			
			return( true );
		}
		
		return( sem.tryAcquire());
	}
	
	public void
	release()
	{
		if ( !released_forever ){
			
			sem.release();
		}
	}
	
	public int
	getValue()
	{
		return( sem.availablePermits());
	}
	
		/**
		 * Try not to use this unless you know that there's no possible race going on
		 */
	
	public void
	releaseAllWaiters()
	{
			// yah, hacky
		
		sem.release( sem.getQueueLength());
	}
	
	public void
	releaseForever()
	{
		if ( !released_forever ){
			
			released_forever = true;
			
			sem.release( Integer.MAX_VALUE/2 );	// release all waiters safely
		}
	}
	
	public boolean
	isReleasedForever()
	{
		return( released_forever );
	}
	
	public String
	getString()
	{
		synchronized(this){

			return( "value=" + sem.availablePermits() + ",waiting=" + sem.getQueueLength()); // + ",res=" + total_reserve + ",rel=" + total_release );
		}
	}
}
