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

package com.biglybt.core.disk.impl;

import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreOperation;
import com.biglybt.core.CoreOperationListener;
import com.biglybt.core.CoreOperationTask;
import com.biglybt.core.CoreOperationTask.ProgressCallback;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.TimerEventPeriodic;

public class 
DiskManagerOperationScheduler
	implements CoreOperationListener
{
	public static void
	initialise(
		Core		core )
	{
		new DiskManagerOperationScheduler( core );
	}
	
	private static boolean	enabled;

	public static boolean
	isEnabled()
	{
		return( enabled );
	}

	private final Core		core;
		
	private List<Operation>		operations = new ArrayList<>();
	
	private TimerEventPeriodic	timer;
	
	
	private
	DiskManagerOperationScheduler(
		Core	_core )
	{
		core	= _core;
		
		COConfigurationManager.addAndFireParameterListener(
			ConfigKeys.File.BCFG_DISKMANAGER_ONE_OP_PER_FS,
			(n)->{
				checkConfig();
			});
	}
		
	private void
	checkConfig()
	{
		synchronized( this ){
			
			boolean _enabled = COConfigurationManager.getBooleanParameter( ConfigKeys.File.BCFG_DISKMANAGER_ONE_OP_PER_FS );
			
			if ( enabled != _enabled ){
				
				enabled = _enabled;
				
				if ( enabled ){
					
					core.addOperationListener( this );
	
					if ( timer == null ){
						
						timer = SimpleTimer.addPeriodicEvent(
								"dmos:timer",
								250,
								(ev)->{
									
									synchronized( DiskManagerOperationScheduler.this ){
										
										schedule();
									}
								});
					}
				}else{
	
					core.removeOperationListener( this );
					
					if ( timer != null ){
						
						timer.cancel();
						
						timer = null;
					}
				}
			}
		}
	}
	
	private void
	schedule()
	{
			// detect manual interference with operations
		
		for ( Operation op: operations ){
			
			if (( op.cb.getTaskState() & ProgressCallback.ST_PAUSE ) == 0 ){

				op.we_paused_it = false;
				
			}else{
				
				op.we_resumed_it = false;
			}
		}
		
			// if we have a move and a check operation for the same download (common with move-on-complete and
			// recheck-pieces-on-complete) then ensure the check 
	
			// for check operations we make an exception and will re-pause an active one if order has changed

		Map<String,Operation>	first_check_per_fs = new HashMap<>();
				
		for ( Operation op: operations ){
		
			if ( op.op.getOperationType() == CoreOperation.OP_DOWNLOAD_CHECKING ){
				
				for ( String fs: op.unique_fs ){
					
					if ( first_check_per_fs.get( fs ) == null ){
						
						first_check_per_fs.put( fs, op );
						
					}else{
						
						ProgressCallback cb = op.cb;
						
							// not first check for this FS
						
						if (( cb.getTaskState() & ProgressCallback.ST_PAUSE ) == 0 ){
							
								// operation is running, pause it but only if we ran it
							
							if ( op.we_resumed_it ){
								
								cb.setTaskState( ProgressCallback.ST_PAUSE  );
								
								cb.setAutoPause( true );
								
								op.we_paused_it 	= true;
								op.we_resumed_it 	= false;
							}
						}
					}
				}
			}
		}
				
		Map<String, int[]>	fs_queue_pos = new HashMap<>();
		
		for ( Operation op: operations ){
			
			if (( op.cb.getTaskState() & ProgressCallback.ST_PAUSE ) == 0 ){
				
				op.cb.setAutoPause( false );
				
				for ( String fs: op.unique_fs ){
					
					int[] x = fs_queue_pos.get( fs );
					
					if ( x == null ){
					
						fs_queue_pos.put( fs, new int[]{ 1 });
					}
				}
			}
		}
		
		for ( Operation op: operations ){

			ProgressCallback cb = op.cb;
			
			if (( cb.getTaskState() & ProgressCallback.ST_PAUSE ) != 0 ){
				
				if ( cb.isAutoPause()){
										
					int	pos = 0;
					
					for ( String fs: op.unique_fs ){
					
						int[]	count = fs_queue_pos.get( fs );
						
						if ( count != null ){
														
							pos = Math.max( pos, count[0] );
							
							count[0]++;
							
						}else{
							
							fs_queue_pos.put( fs, new int[]{ 1 });
						}
					}
					
					if ( pos == 0 ){
																		
						cb.setTaskState( ProgressCallback.ST_RESUME );
						
						cb.setAutoPause( false );
						
						op.we_resumed_it 	= true;
						op.we_paused_it		= false;
						
					}else{
												
						cb.setOrder( pos );
					}
				}
			}
		}
	}
	
	public boolean
	operationExecuteRequest(
		CoreOperation 		operation )
	{
		return( false );
	}
	
	public void
	operationAdded(
		CoreOperation		operation )
	{
		CoreOperationTask task = operation.getTask();
		
		ProgressCallback cb = task.getProgressCallback();
		
		if ( cb != null ){
			
			if ( ( cb.getSupportedTaskStates() & ProgressCallback.ST_PAUSE ) != 0 ){
				
				String[] fs = task.getAffectedFileSystems();
				
				if ( fs != null && fs.length > 0 ){
					
					synchronized( this ){
						
						operations.add( new Operation( operation, cb, fs ));
						
						Collections.sort( operations );
						
						schedule();
					}
				}
			}
		}
	}
	
	public void
	operationRemoved(
		CoreOperation		operation )
	{
		synchronized( this ){
			
			Iterator<Operation>	it = operations.iterator();
			
			while( it.hasNext()){
				
				if ( it.next().op == operation ){
					
					it.remove();
					
					schedule();
					
					break;
				}
			}
		}
	}
	
	private static class
	Operation
		implements Comparable<Operation>
	{
		private final CoreOperation		op;
		private final ProgressCallback	cb;
		private final String[]			unique_fs;
			
		private boolean	we_paused_it;
		private boolean we_resumed_it;
		
		Operation(
			CoreOperation		_op,
			ProgressCallback	_cb,
			String[]			_fs )
		{
			op		= _op;
			cb		= _cb;
		
			Set<String> temp_fs = new HashSet<>();
			
			temp_fs.addAll( Arrays.asList( _fs ));
			
			unique_fs = temp_fs.toArray( new String[ temp_fs.size()] );
			
			cb.setTaskState( ProgressCallback.ST_PAUSE  );
			
			cb.setAutoPause( true );
			
			we_paused_it = true;
		}
		
		@Override
		public int 
		compareTo(
			Operation other )
		{
			int t1 = op.getOperationType();
			int t2 = other.op.getOperationType();
			
			if ( t1 == t2 ){
				
				return( cb.compareTo( other.cb ));
				
			}else{
				
				return( CoreOperation.OP_SORT_ORDER[ t1 ] - CoreOperation.OP_SORT_ORDER[ t2 ]);
			}
		}
	}
}
