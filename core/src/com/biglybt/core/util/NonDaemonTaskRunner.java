/*
 * File    : NonDaemonTaskRunner.java
 * Created : 29-Dec-2003
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.util;

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;

public class
NonDaemonTaskRunner
{	
	public static final int	LINGER_PERIOD	= 2500;

	protected static NonDaemonTaskRunner	singleton;
	protected static final AEMonitor				class_mon		= new AEMonitor( "NonDaemonTaskRunner:class" );

	protected final List<taskWrapper>	tasks		= new ArrayList<>();
	protected final AEMonitor			tasks_mon	= new AEMonitor( "NonDaemonTaskRunner:tasks" );
	protected final AESemaphore			task_sem	= new AESemaphore("NonDaemonTaskRunner");

	protected final List<AESemaphore>		wait_until_idle_list	= new ArrayList<>();

	protected AEThread2	current_thread;

	protected static NonDaemonTaskRunner
	getSingleton()
	{
		try{
			class_mon.enter();

			if ( singleton == null ){

				singleton = new NonDaemonTaskRunner();
			}

			return( singleton );

		}finally{

			class_mon.exit();
		}
	}

	public static Object
	run(
		NonDaemonTask	target )

		throws Throwable
	{
		return(getSingleton().runSupport( target ));
	}

	protected Object
	runSupport(
		NonDaemonTask	target )
		throws Throwable
	{
			// is this a recursive call? if so, run directly
		
		boolean run_now;
		
		try{
			tasks_mon.enter();
		
			run_now = current_thread != null && current_thread.isCurrentThread();
		
		}finally {
			
			tasks_mon.exit();
		}
		
		if ( run_now ){
			
			return( target.run());
		}

		taskWrapper	wrapper = new taskWrapper( target );

		try{
			tasks_mon.enter();

			tasks.add( wrapper );

			task_sem.release();

			if ( current_thread == null ){

				final AESemaphore wait_sem = new AESemaphore("NonDaemonTaskRunnerTask: " + target.getName());

					// NON-DAEMON!!!

				current_thread =
					new AEThread2( "NonDaemonTaskRunner", false )
					{
						@Override
						public void
						run()
						{
							wait_sem.release();

							// System.out.println( "non daemon starts" );

							while(true){

								task_sem.reserve(LINGER_PERIOD);

								taskWrapper t			= null;

								try{
									tasks_mon.enter();

									if ( tasks.isEmpty()){

										current_thread = null;

										for (int i=0;i<wait_until_idle_list.size();i++){

											((AESemaphore)wait_until_idle_list.get(i)).release();
										}

										wait_until_idle_list.clear();

										break;

									}else{

										t = (taskWrapper)tasks.remove( 0 );
									}
								}finally{

									tasks_mon.exit();
								}

								t.run();
							}

							// System.out.println( "non daemon ends" );
						}
					};

				current_thread.start();

				wait_sem.reserve();
			}
		}finally{

			tasks_mon.exit();
		}

		return( wrapper.waitForResult());
	}

	protected static class
	taskWrapper
	{
		protected final NonDaemonTask		task;
		protected final AESemaphore			sem;

		protected Object	  	result;
		protected Throwable  	exception;

		protected
		taskWrapper(
			NonDaemonTask	_task )
		{
			task		= _task;
			sem			= new AESemaphore("NonDaemonTaskRunner::taskWrapper");
		}

		protected void
		run()
		{
			try{
				if (Logger.isEnabled())	Logger.log(new LogEvent(LogIDs.CORE, "Starting non-daemon task: " + task.getName()));
				
				result = task.run();

			}catch( Throwable e ){

				exception	= e;

			}finally{

				if (Logger.isEnabled())	Logger.log(new LogEvent(LogIDs.CORE, "Completed non-daemon task: " + task.getName()));

				sem.release();
			}
		}

		protected Object
		waitForResult()

			throws Throwable
		{
			sem.reserve();

			if ( exception != null ){

				throw( exception );
			}

			return( result );
		}
		
		protected String
		getName()
		{
			return( task.getName());
		}
	}

	public static void
	waitUntilIdle()
	{
		getSingleton().waitUntilIdleSupport();
	}

	protected void
	waitUntilIdleSupport()
	{
		AESemaphore	sem;

		try{
			tasks_mon.enter();

			if ( Logger.isEnabled()){
				
				String str = "";
				
				for (taskWrapper t: tasks ){
					
					str += (str.isEmpty()?"":",") + t.getName();
				}
				
				Logger.log(new LogEvent(LogIDs.CORE, "Non-daemon wait for idle: thread=" + current_thread + ", tasks=" + str ));
			}

			if ( current_thread == null ){

				return;
			}

			sem = new AESemaphore("NDTR::idleWaiter");

			wait_until_idle_list.add( sem );

		}finally{

			tasks_mon.exit();
		}

		while( true ){

			if ( sem.reserve( 2500 )){

				break;
			}

			if (Logger.isEnabled()){

				try{
					tasks_mon.enter();

					String str = "";
					
					for (taskWrapper t: tasks ){
						
						str += (str.isEmpty()?"":",") + t.getName();
					}
					
					Logger.log(new LogEvent(LogIDs.CORE, "Non-daemon wait for idle 2: thread=" + current_thread + ", tasks=" + str ));

					for (int i=0;i<wait_until_idle_list.size();i++){

						AESemaphore pending = (AESemaphore)wait_until_idle_list.get(i);

						if ( pending != sem ){

							Logger.log(new LogEvent(LogIDs.CORE, "Waiting for " + pending.getName() + " to complete" ));
						}
					}
				}finally{

					tasks_mon.exit();
				}
			}
		}
	}
}
