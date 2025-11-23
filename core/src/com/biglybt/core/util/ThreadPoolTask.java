/*
 * Created on 11-Jul-2004
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

package com.biglybt.core.util;

import java.util.concurrent.locks.ReentrantLock;

/**
 * @author parg
 *
 */

public abstract class
ThreadPoolTask
	extends AERunnable
{
	static final int RELEASE_AUTO = 0x00;
	static final int RELEASE_MANUAL = 0x01;
	static final int RELEASE_MANUAL_ALLOWED = 0x02;

	private final ReentrantLock	lock = new ReentrantLock();
	
	private volatile int manualRelease;
	private volatile boolean manualReleaseUnsupported;
	
	protected Worker		worker;

	public void
	setTaskState(
		String		state )
	{
		worker.setState( state );
	}

	public String
	getTaskState()
	{
		return( worker == null ? "" : worker.getState());
	}

	public abstract void
	interruptTask();

	public void
	taskStarted()
	{
	}

	public void
	taskCompleted()
	{
	}

	/**
	 * only invoke this method after the first run of the threadpooltask as it is only meant to join
	 * on a task when it has child tasks and thus is running in manual release mode
	 */
	final void 
	join()
	{
		if ( manualReleaseUnsupported ){
			Debug.out( "eh?" );
		}
		while( manualRelease != RELEASE_AUTO ){
			synchronized( this ){
				try{
					wait();
				}catch (Exception e){
					
					Debug.printStackTrace(e);
				}
			}
		}
	}

	final void
	setManualReleaseUnsupported()
	{
			// virtual pool doesn't support manual release
		
		manualReleaseUnsupported = true;
	}
	
	final void
	setManualRelease()
	{
		if ( !manualReleaseUnsupported ){
			try{
				lock.lock();
				
				manualRelease = ThreadPoolTask.RELEASE_MANUAL;
				
			}finally{
				
				lock.unlock();
			}
		}
	}

	final boolean
	canManualRelease()
	{
		if ( manualReleaseUnsupported ){
			return( false );
		}else{
			return( manualRelease == ThreadPoolTask.RELEASE_MANUAL_ALLOWED );
		}
	}

	/**
	 * only invoke this method after the first run of the threadpooltask as it is only meant to
	 * update the state of a task when it has child tasks and thus is running in manual release mode
	 */
	final boolean isAutoReleaseAndAllowManual()
	{
		if ( manualReleaseUnsupported ){
			return( true );
		}
		try{
			lock.lock();
			
			if(manualRelease == RELEASE_MANUAL)
				manualRelease = RELEASE_MANUAL_ALLOWED;
			return manualRelease == RELEASE_AUTO;
			
		}finally{
			
			lock.unlock();
		}
	}

	public final void releaseToPool()
	{
		if ( manualReleaseUnsupported ){
			return;
		}
		// releasing before the initial run finished, so just let the runner do the cleanup
		
		try{
			lock.lock();
			if(manualRelease == RELEASE_MANUAL)
				manualRelease = RELEASE_AUTO;
			else if(manualRelease == RELEASE_MANUAL_ALLOWED)
			{
				taskCompleted();
				worker.releaseManual(this);
				manualRelease = RELEASE_AUTO;
			} else if(manualRelease == RELEASE_AUTO)
				Debug.out("this should not happen");
		}finally{
			
			lock.unlock();
		}
		synchronized( this ){
			notifyAll();
		}
	}
	
	public interface
	Worker
	{
		public long
		getStartTime();
		
		public String
		getState();
		
		public void
		setState(
			String	str );
		
		public String 
		getWorkerName();
		
		public void
		interrupt();
		
		public void
		releaseManual(
			ThreadPoolTask		task );
	}
}
