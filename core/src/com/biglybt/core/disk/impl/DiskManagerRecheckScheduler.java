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
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreOperation;
import com.biglybt.core.CoreOperationTask;
import com.biglybt.core.CoreOperationTask.ProgressCallback;
import com.biglybt.core.CoreOperationTask.ProgressCallbackAdapter;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.AsyncDispatcher;
import com.biglybt.core.util.RealTimeInfo;

public class
DiskManagerRecheckScheduler
{
	private static Core core = CoreFactory.getSingleton();

	static int	 	strategy;
	static boolean 	smallest_first;
	static int		max_active;
	
	private static AsyncDispatcher async = new AsyncDispatcher(2000);
	
    static{

    	 ParameterListener param_listener = new ParameterListener() {
    	    @Override
	        public void
			parameterChanged(
				String  str )
    	    {
    	    	strategy	 	= COConfigurationManager.getIntParameter( "diskmanager.hashchecking.strategy" );
    	   	    smallest_first	= COConfigurationManager.getBooleanParameter( "diskmanager.hashchecking.smallestfirst" );
    	   	    max_active		= COConfigurationManager.getIntParameter( "diskmanager.hashchecking.maxactive" );
    	   	      
    	   	    if ( max_active <= 0 ){
    	   	    	  
    	   	    	max_active = Integer.MAX_VALUE;
    	   	    }
    	    }
    	 };

 		COConfigurationManager.addAndFireParameterListeners(
 				new String[]{
 					"diskmanager.hashchecking.strategy",
 					"diskmanager.hashchecking.smallestfirst",
 					"diskmanager.hashchecking.maxactive"},
 				param_listener );
    }

    private final Object								lock			= new Object();
	private final List<DiskManagerRecheckInstance>		entries			= new ArrayList<>();

	public DiskManagerRecheckInstance
	register(
		DiskManagerHelper	helper,
		boolean				low_priority )
	{			
		DiskManagerRecheckInstance	instance = new DiskManagerRecheckInstance( helper, low_priority );

		synchronized( lock ){
		
			entries.add( instance );
			
				// time to recalculate metrics if required - note this can cause consistency issued
				// if other code also sorting, see DiskManagerOperationScheduler
			
			for ( DiskManagerRecheckInstance inst: entries ){
				
				inst.updateMetric();
			}
			
			Collections.sort(
				entries,
				new Comparator<DiskManagerRecheckInstance>()
				{
					@Override
					public int
					compare(
						DiskManagerRecheckInstance 	o1,
						DiskManagerRecheckInstance	o2 )
					{
						long	comp = o1.getMetric() - o2.getMetric();

						if ( comp < 0 ){

							return( -1 );

						}else if ( comp == 0 ){

							return( 0 );

						}else{
							return( 1 );
						}
					}
				});
		}
		
		core.addOperation( instance.getOperation());	

		return( instance );
	}
	
	protected boolean
	getPermission(
		DiskManagerRecheckInstance	instance )
	{
		boolean	result 	= false;
		int		delay	= 250;

		synchronized( lock ){

			int	to_process = max_active;
			
			if ( to_process <= 0 ){
				
				to_process = Integer.MAX_VALUE;		// 0 -> unlimited
			}
			
			for ( int i=0; to_process > 0 && i<entries.size();i++){
				
				DiskManagerRecheckInstance this_inst = entries.get(i);
								
				if ( this_inst.isPaused()){
					
					continue;
				}
				
				to_process--;
				
				if ( this_inst == instance ){
	
					boolean	low_priority = instance.isLowPriority();
	
						// defer low priority activities if we are running a real-time task
	
					if ( low_priority && RealTimeInfo.isRealTimeTaskActive()){
	
						result = false;
	
					}else{
	
			            if ( strategy == 0 ){
	
			            	delay	= 0;	// delay introduced elsewhere
	
			            }else if ( strategy != 1 || !low_priority ){
	
			            	delay	= 1;	// high priority recheck, just a smidge of a delay
	
			            }else{
	
				            	//delay a bit normally anyway, as we don't want to kill the user's system
				            	//during the post-completion check (10k of piece = 1ms of sleep)
	
			            	delay = instance.getPieceLength() /1024 /10;
	
			            	delay = Math.min( delay, 409 );
	
			            	delay = Math.max( delay, 12 );
		  				}
	
			            result	= true;
					}
					
					instance.setActive( result );
					
					break;
				}
			}
		}

		if ( delay > 0 ){

			try{
				Thread.sleep( delay );

			}catch( Throwable e ){

			}
		}

		return( result );
	}

	protected void
	unregister(
		DiskManagerRecheckInstance	instance )
	{
		CoreOperation	to_remove = null;

		try{
			synchronized( lock ){
	
				Iterator<DiskManagerRecheckInstance>	it = entries.iterator();
				
				while( it.hasNext()){
				
					DiskManagerRecheckInstance entry = it.next();
					
					if ( entry == instance ){
						
						it.remove();
						
						to_remove = (CoreOperation)entry.getOperation();
						
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
	DiskManagerRecheckInstance
	{
		private final DiskManagerHelper				helper;
		private final Callback						progress;
		private final CoreOperation					op;
		private final int							piece_length;
		private final boolean						low_priority;

		private final AESemaphore	 				slot_sem;
		
		private long							metric;

		private volatile boolean		active;
		private volatile boolean		paused;
		
		protected
		DiskManagerRecheckInstance(
			DiskManagerHelper			_helper,
			boolean						_low_priority )
		{
			helper		= _helper;
			
			TOTorrent	torrent = helper.getTorrent();
									
			piece_length	= (int)torrent.getPieceLength();
			low_priority	= _low_priority;
						
			slot_sem		= new AESemaphore( "DiskManagerRecheckInstance::slotsem", getPieceConcurrency());
						
			progress = new Callback();
				
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
				
			op = 
				new CoreOperation.CoreOperationAdapter()
				{
					public int
					getOperationType()
					{
						return( CoreOperation.OP_DOWNLOAD_CHECKING );
					}
		
					public CoreOperationTask
					getTask()
					{
						return( task );
					}
				};
				
				updateMetric();
		}

		private void
		updateMetric()
		{
			long _metric			= (low_priority?0x7000000000000000L:0L);

			if ( smallest_first ){
				
					// size only of relevance if smallest first (otherwise addition order rules)
				
				TOTorrent	torrent = helper.getTorrent();

				long	size_remaining = torrent.getSize();
				
				if ( helper.getDownload().getState() == DownloadManager.STATE_CHECKING ){
					
					int progress = op.getTask().getProgressCallback().getProgress();
					
					if ( progress > 0 ){
						
						size_remaining = (size_remaining * (1000-progress))/1000;
					}
				}
				
				_metric += size_remaining;
			}
			
			metric			= _metric;

		}
		private int
		getPieceConcurrency()
		{
			int piece_length = getPieceLength();
			
			if ( strategy <= 1 ){
			
				return( piece_length>32*1024*1024?1:2 );
				
			}else{
				
					// limit to 32MB
				
				int num = 32*1024*1024/piece_length;
				
				return( Math.min( 8, num ));
			}
		}

		protected CoreOperation
		getOperation()
		{
			return( op );
		}
		
		protected long
		getMetric()
		{
			return( metric );
		}
		
		protected int
		getPieceLength()
		{
			return( piece_length );
		}

		protected boolean
		isLowPriority()
		{
			return( low_priority );
		}

		public void
		reserveSlot()
		{
			slot_sem.reserve();
		}
		
		public void
		releaseSlot()
		{
			slot_sem.release();
		}
		
		public boolean
		getPermission()
		{
			return( DiskManagerRecheckScheduler.this.getPermission( this ));
		}

		protected boolean
		isActive()
		{
			return( active );
		}
		
		protected void
		setActive(
			boolean		b )
		{
			active	= b;
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
		isCancelled()
		{
			return( progress.getTaskState() == ProgressCallbackAdapter.ST_CANCEL );
		}
		
		public void
		unregister()
		{
			DiskManagerRecheckScheduler.this.unregister( this );
		}

		class
		Callback
			extends ProgressCallbackAdapter
		{
			final DiskManagerRecheckInstance	inst = DiskManagerRecheckInstance.this;
			
			final DownloadManager dm = helper.getDownload();
				
			volatile boolean cancelled;
		
			
			@Override
			public int 
			getProgress()
			{
				int complete_recheck_status = helper.getCompleteRecheckStatus();
				
				if ( complete_recheck_status != -1 ){
					
						// rechecking when a download completes (i.e. not a manual recheck )
					
					return( complete_recheck_status );
				}
				
				return( dm==null?-1:dm.getStats().getCompleted());
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
				if ( cancelled ){
					
					return( ST_CANCEL );
				}
				
				synchronized( lock ){

					if ( isPaused()){
						
						return( ST_PAUSE );
						
					}else if ( isActive()){
						
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
				}else if ( state == ST_PAUSE ){
					
					setPaused( true );
					
				}else if ( state == ST_RESUME ){
					
					setPaused( false );
				}
			}
			
			@Override
			public long 
			getSize()
			{
				return( helper.getSizeExcludingDND());
			}
			
			@Override
			public int 
			compareTo(
				ProgressCallback o )
			{
				if ( o instanceof Callback ){
				
					Callback other = (Callback)o;
					
					long l = getMetric() - other.inst.getMetric();
					
					if ( l < 0 ){
						return(-1 );
					}else if ( l > 0 ){
						return( 1 );
					}else{
						return( 0 );
					}
				}else{
					
					return( 0 );
				}
			}
		}
	}
}
