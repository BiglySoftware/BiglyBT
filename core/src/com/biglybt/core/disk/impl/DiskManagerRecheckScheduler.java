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
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.AERunnable;
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

    private final Object				lock			= new Object();
	private final List<Object[]>		entries			= new ArrayList<>();

	public DiskManagerRecheckInstance
	register(
		DiskManagerHelper	helper,
		boolean				low_priority )
	{
		Object[] my_entry = new Object[]{ null, null };
		
		CoreOperationTask.ProgressCallback progress = 
				new CoreOperationTask.ProgressCallbackAdapter()
				{
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

							DiskManagerRecheckInstance inst = (DiskManagerRecheckInstance)my_entry[0];
							
							if ( inst == null ){
								
								return( ST_NONE );
								
							}else{
								
								if ( inst.isPaused()){
									
									return( ST_PAUSE );
									
								}else if ( inst.isActive()){
									
									return( ST_NONE );
									
								}else{
									
									return( ST_QUEUED );
								}
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
							
							DiskManagerRecheckInstance inst = (DiskManagerRecheckInstance)my_entry[0];
							
							if ( inst != null ){
								
								inst.setPaused( true );
							}
						}else if ( state == ST_RESUME ){
							
							DiskManagerRecheckInstance inst = (DiskManagerRecheckInstance)my_entry[0];
							
							if ( inst != null ){
								
								inst.setPaused( false );
							}
						}
					}
					
					@Override
					public long 
					getSize()
					{
						return( helper.getSizeExcludingDND());
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
						return( CoreOperation.OP_DOWNLOAD_CHECKING );
					}
		
					public CoreOperationTask
					getTask()
					{
						return( task );
					}
				};
			
		DiskManagerRecheckInstance	res =
				new DiskManagerRecheckInstance(
						this,
						helper.getTorrent().getSize(),
						(int)helper.getTorrent().getPieceLength(),
						low_priority );

		my_entry[0]	= res;
		my_entry[1]	= op;

		synchronized( lock ){
		
			entries.add( my_entry );
			
			if ( smallest_first ){

				Collections.sort(
						entries,
						new Comparator<Object[]>()
						{
							@Override
							public int
							compare(
								Object[] 	o1,
								Object[]	o2 )
							{
								long	comp = ((DiskManagerRecheckInstance)o1[0]).getMetric() - ((DiskManagerRecheckInstance)o2[0]).getMetric();

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
		}
		
		core.addOperation( op );	

		return( res );
	}

	public int
	getPieceConcurrency(
		DiskManagerRecheckInstance	instance )
	{
		int piece_length = instance.getPieceLength();
		
		if ( strategy <= 1 ){
		
			return( piece_length>32*1024*1024?1:2 );
			
		}else{
			
				// limit to 32MB
			
			int num = 32*1024*1024/piece_length;
			
			return( Math.min( 8, num ));
		}
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
				
				Object[] entry = (Object[])entries.get(i);
				
				DiskManagerRecheckInstance this_inst = (DiskManagerRecheckInstance)entry[0];
				
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
	
				Iterator<Object[]>	it = entries.iterator();
				
				while( it.hasNext()){
				
					Object[] entry = it.next();
					
					if ( entry[0] == instance ){
						
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
