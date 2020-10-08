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
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AsyncDispatcher;

public class
DiskManagerAllocationScheduler
{
	private static Core core = CoreFactory.getSingleton();
	
	private static AsyncDispatcher async = new AsyncDispatcher(2000);

	private final Object lock = new Object();
	
	private final List<Object[]>	instances		= new ArrayList<>();


	public void
	register(
		DiskManagerHelper	helper )
	{
		Object[] my_entry = { helper, null, false };
		
		CoreOperationTask.ProgressCallback progress = 
			new CoreOperationTask.ProgressCallbackAdapter()
			{
				final DownloadManager dm = helper.getDownload();

				boolean	cancelled;
				
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
				getSupportedTaskStates()
				{
					return( ST_PAUSE | ST_RESUME | ST_CANCEL );
				}
				
				@Override
				public int 
				getTaskState()
				{
					synchronized( lock ){

						if ( cancelled ){
							
							return( ST_CANCEL );
						}
						
						if ((Boolean)my_entry[2]){
							
							return( ST_PAUSE );
						}
						
						for ( Object[] entry: instances ){
													
							if (!(Boolean)entry[2]){

									// first not paused entry
								
								if ( entry[0] == helper ){
							
									return( ST_NONE );
								}
								
								break;
							}
						}
							
						return( ST_QUEUED );
					}
				}
				
				@Override
				public void 
				setTaskState(
					int state )
				{
					if ( state == ST_CANCEL ){
						
						cancelled = true;
						
						if ( dm != null ){
							
							async.dispatch( AERunnable.create( ()->{
									dm.stopIt( DownloadManager.STATE_STOPPED, false, false );
							}));							
						}
					}else if ( state == ST_PAUSE ){
						
						my_entry[2] = true;
						
					}else if ( state == ST_RESUME ){
						
						my_entry[2]	= false;
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
				
				@Override
				public DownloadManager 
				getDownload()
				{
					return( helper.getDownload());
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
			
		my_entry[1] = op;
		
		synchronized( lock ){

			instances.add( my_entry );
		}
		
		core.addOperation( op );	
	}

	protected boolean
	getPermission(
		DiskManagerHelper	helper )
	{
		synchronized( lock ){

			for ( Object[] entry: instances ){
				
				if ((Boolean)entry[2]){
					
					continue;	// paused
				}
			
				if ( entry[0] == helper ){

					return( true );
				}
				
				break;
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
		CoreOperation	to_remove = null;
		
		try{
			synchronized( lock ){
	
				Iterator<Object[]> it = instances.iterator();
				
				while( it.hasNext()){
					
					Object[] entry = it.next();
					
					if ( entry[0] == helper ){
					
						it.remove();
						
						to_remove = (CoreOperation)entry[1];
						
						break;
					}
				}
			}
		}finally{
			
			if ( to_remove != null ){
			
				core.removeOperation( to_remove );
			}
		}
	}
}
