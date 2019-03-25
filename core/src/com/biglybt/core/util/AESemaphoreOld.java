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

package com.biglybt.core.util;

public class 
AESemaphoreOld
	extends AEMonSem
{
	private int		dont_wait	= 0;

	private int		total_reserve	= 0;
	private int		total_release	= 0;

	private boolean	released_forever	= false;

	protected Thread	latest_waiter;

	public
	AESemaphoreOld(
		String		_name )
	{
		this( _name, 0 );
	}

	public
	AESemaphoreOld(
		String		_name,
		int			count )
	{
		super( _name, false );

		dont_wait		= count;
		total_release	= count;
	}

	public void
	reserve()
	{
		if ( !reserve(0)){

			Debug.out( "AESemaphore: reserve completed without acquire [" + getString() + "]" );
		}
	}

	public boolean
	reserve(
		long	millis )
	{
		return( reserveSupport( millis, 1 ) == 1 );
	}

	public boolean
	reserveIfAvailable()
	{
		synchronized(this){

			if ( released_forever || dont_wait > 0 ){

				reserve();

				return( true );

			}else{

				return( false );
			}
		}
	}

	public int
	reserveSet(
		int		max_to_reserve,
		long	millis )
	{
		return( reserveSupport( millis, max_to_reserve));
	}

	public int
	reserveSet(
		int	max_to_reserve )
	{
		return( reserveSupport( 0, max_to_reserve));
	}

	protected int
	reserveSupport(
		long	millis,
		int		max_to_reserve )
	{
		if ( DEBUG ){

			super.debugEntry();
		}

		synchronized(this){

			entry_count++;

			//System.out.println( name + "::reserve");

			if ( released_forever ){

				return(1);
			}

			if ( dont_wait == 0 ){

				try{
					waiting++;

					latest_waiter	= Thread.currentThread();

					if ( waiting > 1 ){

						// System.out.println( "AESemaphore: " + name + " contended" );
					}

					if ( millis == 0 ){

							// we can get spurious wakeups (see Object javadoc) so we need to guard against
							// their possibility

						int	spurious_count	= 0;

						while( true ){

							wait();

							if ( total_reserve == total_release ){

								spurious_count++;

								if ( spurious_count > 1024 ){

									Debug.out( "AESemaphore: spurious wakeup limit exceeded" );

									throw( new Throwable( "die die die" ));

								}else{

									// Debug.out("AESemaphore: spurious wakeup, ignoring" );
								}
							}else{

								break;
							}
						}
					}else{

							// we don't hugely care about spurious wakeups here, it'll just appear
							// as a failed reservation a bit early

						wait(millis);
					}

					if ( total_reserve == total_release ){

							// here we have timed out on the wait without acquiring

						waiting--;

						return( 0 );
					}

					total_reserve++;

					return( 1 );

				}catch( Throwable e ){

					waiting--;

					Debug.out( "**** semaphore operation interrupted ****" );

					throw( new RuntimeException("Semaphore: operation interrupted", e ));

				}finally{

					latest_waiter = null;
				}
			}else{
				int	num_to_get = max_to_reserve>dont_wait?dont_wait:max_to_reserve;

				dont_wait -= num_to_get;

				total_reserve += num_to_get;

				return( num_to_get );
			}
		}
	}

	public void
	release()
	{
		try{
			synchronized(this){

				//System.out.println( name + "::release");

				total_release++;

				if ( waiting != 0 ){

					waiting--;

					notify();

				}else{
					dont_wait++;
				}
			}
		}finally{

			if ( DEBUG ){

				debugExit();
			}
		}
	}

	public void
	releaseAllWaiters()
	{
		synchronized(this){

			int	x	= waiting;

			for ( int i=0;i<x;i++ ){
				release();
			}
		}
	}

	public void
	releaseForever()
	{
		synchronized(this){

			releaseAllWaiters();

			released_forever	= true;
		}
	}

	public boolean
	isReleasedForever()
	{
		synchronized(this){

			return( released_forever );
		}
	}

	public int
	getValue()
	{
		synchronized(this){

			return( dont_wait - waiting );
		}
	}

	public String
	getString()
	{
		synchronized(this){

			return( "value=" + dont_wait + ",waiting=" + waiting + ",res=" + total_reserve + ",rel=" + total_release );
		}
	}
}
