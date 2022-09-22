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

/**
 * @author parg
 *
 */

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class
AEMonitor
{
	final ReentrantLock	lock;

	public
	AEMonitor(
		String			_name )
	{
		lock = new ReentrantLock();
	}

	public
	AEMonitor(
		String			_name,
		boolean			_fair )
	{
		lock = new ReentrantLock( _fair );
	}

	
	public void
	enter()
	{
		lock.lock();
	}

		/*
		 * Try and obtain it
		 * @return true if got monitor, false otherwise
		 */

	public boolean
	enter(
		int	max_millis )
	{
		try{
			if ( lock.tryLock( max_millis, TimeUnit.MILLISECONDS )){

				return( true );

			}else{

				return( false );
			}
		}catch( Throwable e ){

			Debug.out( e );

			return( false );
		}
	}

	public void
	exit()
	{
		try{
			lock.unlock();

		}finally{

		}
	}

	public boolean
	isHeld()
	{
		return( lock.isHeldByCurrentThread());
	}

	public boolean
	hasWaiters()
	{
		return( lock.getQueueLength() > 0 );
	}
}