/*
 * Created on 19-Dec-2005
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

package com.biglybt.core.disk.impl;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.util.AEMonitor;

public class
DiskManagerAllocationScheduler
{
	private final List		instances		= new ArrayList();
	private final AEMonitor	instance_mon	= new AEMonitor( "DiskManagerAllocationScheduler" );


	public void
	register(
		DiskManagerHelper	helper )
	{
		try{
			instance_mon.enter();

			instances.add( helper );

		}finally{

			instance_mon.exit();
		}
	}

	protected boolean
	getPermission(
		DiskManagerHelper	instance )
	{
		try{
			instance_mon.enter();

			if ( instances.get(0) == instance ){

				return( true );
			}

		}finally{

			instance_mon.exit();
		}

		try{
			Thread.sleep( 250 );

		}catch( Throwable e ){

		}

		return( false );
	}

	protected void
	unregister(
		DiskManagerHelper	instance )
	{
		try{
			instance_mon.enter();

			instances.remove( instance );
		}finally{

			instance_mon.exit();
		}
	}
}
