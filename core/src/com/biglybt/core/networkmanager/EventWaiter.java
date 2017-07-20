/*
 * Created on 27-Apr-2006
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

package com.biglybt.core.networkmanager;

import com.biglybt.core.util.Debug;

public class
EventWaiter
{
	private boolean	sleeping;
	private boolean	wakeup_outstanding;

	public
	EventWaiter()
	{
	}

	public boolean
	waitForEvent(
		long	timeout )
	{
		synchronized( this ){

			if ( wakeup_outstanding ){

				wakeup_outstanding	= false;

				return( false );
			}

			try{
				sleeping	= true;

				this.wait( timeout );

			}catch( Throwable e ){

				Debug.printStackTrace( e );

			}finally{

				sleeping	= false;
			}

			return( true );
		}
	}

	public void
	eventOccurred()
	{
		synchronized( this ){

			if ( !sleeping ){

				wakeup_outstanding	= true;

			}else{

				this.notify();
			}
		}
	}
}
