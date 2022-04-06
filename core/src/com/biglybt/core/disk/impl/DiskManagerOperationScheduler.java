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
import java.util.concurrent.atomic.AtomicLong;

import com.biglybt.core.Core;
import com.biglybt.core.CoreOperation;
import com.biglybt.core.CoreOperationListener;
import com.biglybt.core.CoreOperationTask;
import com.biglybt.core.CoreOperationTask.ProgressCallback;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.IdentityHashSet;
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
	
	private boolean	concurrent_reads;
	
	private
	DiskManagerOperationScheduler(
		Core	_core )
	{
		core	= _core;
		
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				ConfigKeys.File.BCFG_DISKMANAGER_ONE_OP_PER_FS,
				ConfigKeys.File.BCFG_DISKMANAGER_ONE_OP_PER_FS_CONC_READ,
			},
			(n)->{
				checkConfig();
			});
	}
		
	private void
	checkConfig()
	{
		synchronized( this ){
			
			boolean _enabled = COConfigurationManager.getBooleanParameter( ConfigKeys.File.BCFG_DISKMANAGER_ONE_OP_PER_FS );
			
			concurrent_reads = COConfigurationManager.getBooleanParameter( ConfigKeys.File.BCFG_DISKMANAGER_ONE_OP_PER_FS_CONC_READ );
			
			if ( enabled != _enabled ){
				
				enabled = _enabled;
				
				if ( enabled ){
					
					core.addOperationListener( this );
	
					if ( timer == null ){
						
						timer = SimpleTimer.addPeriodicEvent(
								"dmos:timer",
								500,
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
		if ( operations.isEmpty()){
			
			return;
		}
		
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

		boolean	conc_read = concurrent_reads;
		
		Map<String,Operation>	first_check_per_fs = new HashMap<>();
				
		for ( Operation op: operations ){
		
			if ( op.op.getOperationType() == CoreOperation.OP_DOWNLOAD_CHECKING ){
				
				for ( String fs: conc_read?op.unique_fs_for_conc_read:op.unique_fs_normal ){
					
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
				
				for ( String fs: conc_read?op.unique_fs_for_conc_read:op.unique_fs_normal ){
					
					int[] x = fs_queue_pos.get( fs );
					
					if ( x == null ){
					
						fs_queue_pos.put( fs, new int[]{ 1 });
					}
				}
			}
		}
		
			// if we have concurrent reads then we need to explicitly enforce the one-op-active
			// per download constraint
		
		Set<DownloadManager>	active_downloads = conc_read?new IdentityHashSet<>():null;
		
		for ( Operation op: operations ){

			ProgressCallback cb = op.cb;
			
			if (( cb.getTaskState() & ProgressCallback.ST_PAUSE ) != 0 ){
				
				if ( cb.isAutoPause()){
						
					if ( active_downloads != null ){
						
						DownloadManager dm = op.op.getTask().getDownload();
						
						if ( active_downloads.contains( dm )){
							
							cb.setOrder( 0 );
							
							continue;
						}
					}
					
					int	pos = 0;
					
					for ( String fs: conc_read?op.unique_fs_for_conc_read:op.unique_fs_normal ){
					
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
			}else{
				if ( active_downloads != null ){
					
					DownloadManager dm = op.op.getTask().getDownload();
					
					if ( dm != null ){
					
						active_downloads.add( dm );
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
						
							// small chance of sort throwing error due to metrics updating due to
							// 'check smallest first' relying on amount remaining to be rechecked 
							// rather than absolute size
						
						for ( int i=0;i<10;i++){
							
							try{
								Collections.sort( operations );
								
								break;
								
							}catch( IllegalArgumentException e ){
								
								try{
									Thread.sleep(50);
									
								}catch( Throwable f ){
									
								}
							}
						}
						
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
	
	private static final AtomicLong fakeNumber = new AtomicLong();
	
	private static class
	Operation
		implements Comparable<Operation>
	{
		private final CoreOperation		op;
		private final ProgressCallback	cb;
		private final String[]			unique_fs_normal;
		private final String[]			unique_fs_for_conc_read;
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
			
			unique_fs_normal = temp_fs.toArray( new String[ temp_fs.size()] );
			
				// for conc reads we replace all read fs with a uniquely names one so they
				// never interfere with anything
			
			switch( op.getOperationType()){
			
				case CoreOperation.OP_FILE_MOVE:
				case CoreOperation.OP_DOWNLOAD_COPY:
				case CoreOperation.OP_DOWNLOAD_EXPORT:{

					if ( _fs.length != 2 ){
						
						Debug.out( "2 file systems expected" );
						
						unique_fs_for_conc_read = unique_fs_normal;
						
					}else{
						
						unique_fs_for_conc_read = new String[]{ "::fake::" + fakeNumber.incrementAndGet(), _fs[1] };
					}
					
					break;
				}
				case CoreOperation.OP_DOWNLOAD_ALLOCATION:{
					
					unique_fs_for_conc_read = unique_fs_normal;
					
					break;
				}
				case CoreOperation.OP_DOWNLOAD_CHECKING:{
				
					unique_fs_for_conc_read = new String[]{ "::fake::" + fakeNumber.incrementAndGet()};
					
					break;
				}
				default:{
					
					Debug.out( "Unknown operation type" );
					
					unique_fs_for_conc_read = unique_fs_normal;
				}
			}
						
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
