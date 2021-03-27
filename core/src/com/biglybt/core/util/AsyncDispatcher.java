/*
 * Created on 17 Jul 2006
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

import java.util.LinkedList;

public class
AsyncDispatcher
{
	private final String					name;
	private AEThread2				thread;
	private int						priority	= Thread.NORM_PRIORITY;
	private AERunnable				queue_head;
	private LinkedList<AERunnable>	queue_tail;
	final AESemaphore				queue_sem 	= new AESemaphore( "AsyncDispatcher" );

	private int						num_priority;

	final int quiesce_after_millis;

	public
	AsyncDispatcher()
	{
		this( "AsyncDispatcher: " + Debug.getLastCallerShort(), 10000 );
	}

	public
	AsyncDispatcher(
		String		name )
	{
		this( name, 10000 );
	}

	public
	AsyncDispatcher(
		int		quiesce_after_millis )
	{
		this( "AsyncDispatcher: " + Debug.getLastCallerShort(), quiesce_after_millis );
	}

	public
	AsyncDispatcher(
		String		_name,
		int			_quiesce_after_millis )
	{
		name					= _name;
		quiesce_after_millis	= _quiesce_after_millis;
	}

	public void
	dispatch(
		Runnable	r )
	{
		dispatch( AERunnable.create( r ));
	}
	
	public void
	dispatch(
		AERunnable	target )
	{
		dispatch( target, false );
	}

	public void
	dispatch(
		AERunnable	target,
		boolean		is_priority )
	{
		synchronized( this ){

			if ( queue_head == null ){

				queue_head = target;

				if ( is_priority ){

					num_priority++;
				}
			}else{

				if ( queue_tail == null ){

					queue_tail = new LinkedList<>();
				}

				if ( is_priority ){

					if ( num_priority == 0 ){

						queue_tail.add( 0, queue_head );

						queue_head = target;

					}else{

						queue_tail.add( num_priority-1, target );
					}

					num_priority++;

				}else{

					queue_tail.add( target );
				}
			}

			if ( thread == null ){

				thread =
					new AEThread2( name, true )
					{
						@Override
						public void
						run()
						{
							while( true ){

								queue_sem.reserve( quiesce_after_millis );

								AERunnable	to_run = null;

								synchronized( AsyncDispatcher.this ){

									if ( queue_head == null){

										queue_tail = null;

										thread = null;

										break;
									}

									to_run = queue_head;

									if ( queue_tail != null && !queue_tail.isEmpty()){

										queue_head = queue_tail.removeFirst();

									}else{

										queue_head = null;
									}

									if ( num_priority > 0 ){

										num_priority--;
									}
								}

								try{
									to_run.runSupport();

								}catch( Throwable e ){

									Debug.printStackTrace(e);
								}
							}
						}
					};

				thread.setPriority( priority );

				thread.start();
			}
		}

		queue_sem.release();
	}

	public boolean
	isQuiescent()
	{
		synchronized( this ){

			return( thread == null );
		}
	}

	public int
	getQueueSize()
	{
		synchronized( this ){

			int	result = queue_head == null?0:1;

			if ( queue_tail != null ){

				result += queue_tail.size();
			}

			return( result );
		}
	}

	public void
	setPriority(
		int		p )
	{
		priority = p;
	}

	public boolean
	isDispatchThread()
	{
		synchronized( this ){

			return( thread != null && thread.isCurrentThread());
		}
	}
}
