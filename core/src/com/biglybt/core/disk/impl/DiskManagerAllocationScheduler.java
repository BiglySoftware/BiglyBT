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
import java.util.Iterator;
import java.util.List;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreOperation;
import com.biglybt.core.CoreOperationTask;
import com.biglybt.core.util.AEMonitor;

public class
DiskManagerAllocationScheduler
{
	private static Core core = CoreFactory.getSingleton();
	
	private final Object lock = new Object();
	
	private final List<Object[]>	instances		= new ArrayList<>();


	public void
	register(
		DiskManagerHelper	helper )
	{
		CoreOperationTask.ProgressCallback progress = 
			new CoreOperationTask.ProgressCallbackAdapter()
			{
				@Override
				public int 
				getProgress()
				{
					return( helper.getPercentAllocated());
				}
				
				@Override
				public long 
				getSize()
				{
					return( helper.getSizeExcludingDND());
				}
				
				@Override
				public int 
				getTaskState()
				{
					synchronized( lock ){

						if ( instances.get(0)[0] == helper ){
							
							return( ST_NONE );
							
						}else{
							
							return( ST_QUEUED );
						}
					}
				}
			};
			
		CoreOperationTask task =
			new CoreOperationTask()
			{
				public String
				getName()
				{
					return( helper.getDisplayName());
				}
				
				public ProgressCallback
				getProgressCallback()
				{
					return( progress );
				}
			};
			
		CoreOperation op = 
			new CoreOperation()
			{
				public int
				getOperationType()
				{
					return( CoreOperation.OP_DOWNLOAD_ALLOCATION );
				}
	
				public CoreOperationTask
				getTask()
				{
					return( task );
				}
			};
			
		synchronized( lock ){

			instances.add( new Object[]{ helper, op });

			core.addOperation( op );	
		}
	}

	protected boolean
	getPermission(
		DiskManagerHelper	helper )
	{
		synchronized( lock ){

			if ( instances.get(0)[0] == helper ){

				return( true );
			}
		}

		try{
			Thread.sleep( 250 );

		}catch( Throwable e ){

		}

		return( false );
	}

	protected void
	unregister(
		DiskManagerHelper	helper )
	{
		synchronized( lock ){

			Iterator<Object[]> it = instances.iterator();
			
			while( it.hasNext()){
				
				Object[] entry = it.next();
				
				if ( entry[0] == helper ){
				
					it.remove();
					
					core.removeOperation((CoreOperation)entry[1]);
					
					break;
				}
			}
		}
	}
}
