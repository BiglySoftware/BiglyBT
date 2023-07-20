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
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AsyncDispatcher;

public class
DiskManagerAllocationScheduler
{
	private static Core core = CoreFactory.getSingleton();
	
	private static AsyncDispatcher async = new AsyncDispatcher(2000);

	private final Object lock = new Object();
	
	private final List<AllocationInstance>	instances		= new ArrayList<>();

	private static boolean alloc_smallest_first;
	
	static{
		COConfigurationManager.addAndFireParameterListener(
			ConfigKeys.File.BCFG_DISKMANAGER_ALLOC_SMALLESTFIRST, (n)->{
				alloc_smallest_first = COConfigurationManager.getBooleanParameter(n);
			});
	}
	
	public AllocationInstance
	register(
		DiskManagerHelper	helper )
	{		
		AllocationInstance instance = new AllocationInstance( helper );
				
		synchronized( lock ){

			instances.add( instance );
		}
		
		CoreOperation op = instance.getOperation();
		
		core.addOperation( op );	
		
		return( instance );
	}

	private boolean
	canRun(
		AllocationInstance		instance )
	{
		boolean	result;
		
		if ( DiskManagerOperationScheduler.isEnabled()){
		
				// scheduler is managing things via pause/resume so we defer to it
			
			result = !instance.isPaused();
			
		}else{
		
			result = false;
			
			synchronized( lock ){
	
				for ( AllocationInstance this_inst: instances ){
					
					if ( this_inst.isPaused()){
						
						continue;	// paused
					}
				
					if ( this_inst == instance ){
	
						result = true;
					}
					
					break;
				}
			}
		}

		return( result );
	}

	private void
	unregister(
		AllocationInstance		instance )
	{
		CoreOperation	to_remove = null;
		
		try{
			synchronized( lock ){
	
				Iterator<AllocationInstance> it = instances.iterator();
				
				while( it.hasNext()){
					
					AllocationInstance this_inst = it.next();
					
					if (  this_inst == instance ){
					
						it.remove();
						
						to_remove = this_inst.getOperation();
						
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
	
	public class
	AllocationInstance
	{
		private final DiskManagerHelper		helper;
		private final CoreOperation			operation;
		
		private final boolean				always_run;
		
		private volatile boolean 			paused;
		
		AllocationInstance(
			DiskManagerHelper		_helper )
		{
			helper	= _helper;
			
			always_run = 	helper.getTotalLength() < 1024*1024 ||
							helper.getDownloadState().getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD );
			
			Callback progress = new Callback();
				
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
				
			operation = 
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
		}
		
		protected CoreOperation
		getOperation()
		{
			return( operation );
		}
		
		protected boolean
		isPaused()
		{
			return( paused );
		}
		
		protected void
		setPaused(
			boolean		b )
		{
			paused	= b;
		}
		
		public boolean
		getPermission()
		{
			if ( always_run ){
				
				return( true );
				
			}else{
			
				boolean result = DiskManagerAllocationScheduler.this.canRun( this );
				
				if ( !result ){
					
					try{
						Thread.sleep( 250 );
			
					}catch( Throwable e ){
			
					}
				}
			
				return( result );
			}
		}
		
		public void
		unregister()
		{
			DiskManagerAllocationScheduler.this.unregister( this );
		}
	
		class
		Callback
			extends CoreOperationTask.ProgressCallbackAdapter
		{
			final DownloadManager dm = helper.getDownload();

			boolean	cancelled;
			
			Callback()
			{
				super( alloc_smallest_first );
			}
			
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
			public String 
			getSubTaskName()
			{
				return( helper.getAllocationTask());
			}				
			
			@Override
			public int 
			getSupportedTaskStates()
			{
				if ( always_run ){
				
					return( ST_CANCEL | ST_SUBTASKS );

				}else{
					
					return( ST_PAUSE | ST_RESUME | ST_CANCEL | ST_SUBTASKS );
				}
			}
			
			@Override
			public int 
			getTaskState()
			{
				synchronized( lock ){

					if ( cancelled ){
						
						return( ST_CANCEL );
					}
					
					if ( isPaused()){
						
						return( ST_PAUSE );
						
					}else if ( canRun( AllocationInstance.this )){
							
						return( ST_NONE );
						
					}else{
						
						return( ST_QUEUED );
					}
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
				}else if ( !always_run ){
					
					if ( state == ST_PAUSE ){
					
						setPaused( true );
					
					}else if ( state == ST_RESUME ){
						
						setPaused( false );
					}
				}
			}
		}
	}
}
