/*
 * Created on May 1, 2007
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


package com.biglybt.core.networkmanager.admin.impl;

import java.util.Iterator;

import com.biglybt.core.networkmanager.admin.NetworkAdminSpeedTester;
import com.biglybt.core.networkmanager.admin.NetworkAdminSpeedTesterListener;
import com.biglybt.core.networkmanager.admin.NetworkAdminSpeedTesterResult;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;

public abstract class
NetworkAdminSpeedTesterImpl
	implements NetworkAdminSpeedTester
{
    private final CopyOnWriteList	listeners = new CopyOnWriteList();

    private boolean	result_reported;

    protected abstract void
    abort(
    	String	reason );

    protected abstract void
    abort(
    	String		reason,
    	Throwable 	cause );

	@Override
	public void addListener(NetworkAdminSpeedTesterListener listener) {
		listeners.add( listener );
	}
	@Override
	public void removeListener(NetworkAdminSpeedTesterListener listener) {
		listeners.remove( listener );
	}

	/**
	 * Send a Result to all of the NetworkAdminSpeedTestListeners.
	 * @param r - Result of the test.
	 */

	protected void
	sendResultToListeners(
		NetworkAdminSpeedTesterResult r )
	{
			// just report the first result in case an implementation hits this more than once

		synchronized( this ){

			if ( result_reported ){

				return;
			}

			result_reported = true;
		}

		Iterator	it = listeners.iterator();

		while( it.hasNext()){

			try{
				((NetworkAdminSpeedTesterListener)it.next()).complete( this, r );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	protected void
	sendStageUpdateToListeners(
		String status )
	{

		Iterator	it = listeners.iterator();

		while( it.hasNext()){

			try{
				((NetworkAdminSpeedTesterListener)it.next()).stage( this, status );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}
}
