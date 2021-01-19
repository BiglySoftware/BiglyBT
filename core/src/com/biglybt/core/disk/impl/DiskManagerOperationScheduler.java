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
		
	private List<Operation>		operations = new LinkedList<>();
	
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
		Map<String, int[]>	fs_queue_pos = new HashMap<>();
		
		for ( Operation op: operations ){
			
			if (( op.cb.getTaskState() & ProgressCallback.ST_PAUSE ) == 0 ){
				
				op.cb.setAutoPause( false );
				
				for ( String fs: op.fs ){
					
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
					
					for ( String fs: op.fs ){
					
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
	{
		private final CoreOperation		op;
		private final ProgressCallback	cb;
		private final String[]			fs;
				
		Operation(
			CoreOperation		_op,
			ProgressCallback	_cb,
			String[]			_fs )
		{
			op		= _op;
			cb		= _cb;
			fs		= _fs;
			
			cb.setTaskState( ProgressCallback.ST_PAUSE  );
			
			cb.setAutoPause( true );
		}
	}
}
