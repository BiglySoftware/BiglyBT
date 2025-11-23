/*
 * File    : ThreadPool.java
 * Created : 21-Nov-2003
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
import java.util.Iterator;
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;


public class
ThreadPoolVirtual<T extends AERunnable>
	implements ThreadPoolParent<T>
{
	private static final boolean	NAME_THREADS = Constants.IS_CVS_VERSION && System.getProperty( "az.thread.pool.naming.enable", "true" ).equals( "true" );

	private static final boolean	LOG_WARNINGS	= false;
	private static final int		WARN_TIME		= 10000;

	private static final List<ThreadPoolVirtual<?>>		busy_pools			= new ArrayList<>();
	private static boolean	busy_pool_timer_set	= false;

	private static boolean	debug_thread_pool;
	private static boolean	debug_thread_pool_log_on;

	static{
		if ( System.getProperty("transitory.startup", "0").equals("0")){

			AEDiagnostics.addEvidenceGenerator(
				new AEDiagnosticsEvidenceGenerator()
				{
					@Override
					public void
					generate(
						IndentWriter		writer )
					{
						writer.println( "Thread Pools" );

						try{
							writer.indent();

							List<ThreadPoolVirtual<?>>	pools;

							synchronized( busy_pools ){

								pools	= new ArrayList<>( busy_pools );
							}

							for (int i=0;i<pools.size();i++){

								pools.get(i).generateEvidence( writer );
							}
						}finally{

							writer.exdent();
						}
					}
				});
		}
	}

	static final ThreadLocal		tls	=
		new ThreadLocal()
		{
			@Override
			public Object
			initialValue()
			{
				return( null );
			}
		};

	protected static void
	checkAllTimeouts()
	{
		List<ThreadPoolVirtual<?>>	pools;

			// copy the busy pools to avoid potential deadlock due to synchronization
			// nestings

		synchronized( busy_pools ){

			pools	= new ArrayList<>( busy_pools );
		}

		for (int i=0;i<pools.size();i++){

			pools.get(i).checkTimeouts();
		}
	}

	private final String	name;
	private int				thread_name_index	= 1;

	private long	execution_limit;

	private final List<threadPoolWorker>	busy;
	private final boolean	queue_when_full;
	private final List<T>	task_queue	= new ArrayList<>();

	private final AESemaphore	thread_sem;
	private int					target_permits;
	private int					current_permits;

	private int			thread_priority	= Thread.NORM_PRIORITY;
	private boolean		warn_when_full;

	private long		task_total;
	private long		task_total_last;
	private final Average		task_average	= Average.getInstance( WARN_TIME, 120 );

	private boolean		log_cpu	= AEThread2.TRACE_TIMES;

	public
	ThreadPoolVirtual(
		String	_name,
		int		_max_size )
	{
		this( _name, _max_size, false );
	}

	public
	ThreadPoolVirtual(
		String	_name,
		int		_max_size,
		boolean	_queue_when_full )
	{
		name				= _name;
		
		if ( _max_size < 1 ){
			Debug.out( "Invalid thread pool max: " + _max_size );
			_max_size = 1;
		}
		
		target_permits		= _max_size;
		queue_when_full		= _queue_when_full;

		thread_sem = new AESemaphore( "ThreadPool::" + name, target_permits );

		current_permits = target_permits;
		
		busy		= new ArrayList( _max_size );
	}

	private void
	generateEvidence(
		IndentWriter		writer )
	{
		writer.println( name + ": max=" + target_permits +",qwf=" + queue_when_full + ",queue=" + task_queue.size() + ",busy=" + busy.size() + ",total=" + task_total + ":" + DisplayFormatters.formatDecimal(task_average.getDoubleAverage(),2) + "/sec");
	}

	public void
	setWarnWhenFull()
	{
		warn_when_full	= true;
	}

	public void
	setLogCPU()
	{
		log_cpu	= true;
	}

	public int
	getMaxThreads()
	{
		return( target_permits );
	}

	public void
	setThreadPriority(
		int	_priority )
	{
		thread_priority	= _priority;
	}

	public void
	setExecutionLimit(
		long		millis )
	{
		synchronized( this ){

			execution_limit	= millis;
		}
	}

	public void 
	runTask(T runnable) 
	{
		run(runnable, false, false);
	}
	
	public void 
	runTask(
		T 			runnable, 
		boolean		high_priority, 
		boolean 	manualRelease)
	{
		run(runnable, high_priority, manualRelease);	
	}
	
	public void 
	run(T runnable) 
	{
		run(runnable, false, false);
	}


	/**
	 *
	 * @param runnable
	 * @param high_priority
	 *            inserts at front if tasks queueing
	 */
	public void run(T runnable, boolean high_priority, boolean manualRelease) {
		
		if(manualRelease && !(runnable instanceof ThreadPoolTask))
			throw new IllegalArgumentException("manual release only allowed for ThreadPoolTasks");
		else if(manualRelease){
			manualRelease = false;	// not supported for virtual thread pool

			((ThreadPoolTask)runnable).setManualReleaseUnsupported();
		}
		// System.out.println( "Thread pool:" + name + " - sem = " + thread_sem.getValue() + ", queue = " + task_queue.size());

			// not queueing, grab synchronous sem here

		if ( !queue_when_full ){

			if ( !thread_sem.reserveIfAvailable()){

					// defend against recursive entry when in queuing mode (yes, it happens)

					// Replace TLS with ScopedValue one day...
				
				threadPoolWorker	recursive_worker = (threadPoolWorker)tls.get();

				if ( recursive_worker == null || recursive_worker.getOwner() != this ){

						// do a blocking reserve here, not recursive

					checkWarning();

					thread_sem.reserve();

				}else{
						// run immediately

					if ( runnable instanceof ThreadPoolTask ){

						ThreadPoolTask task = (ThreadPoolTask)runnable;

						task.worker = recursive_worker;

						try{
							task.taskStarted();

							runIt( runnable );

							task.join();

						}finally{

							task.taskCompleted();
						}
					}else{

						runIt( runnable );
					}

					return;
				}
			}
		}

		synchronized( this ){

			if ( high_priority ){
				task_queue.add( 0, runnable );
			}else{
				task_queue.add( runnable );
			}

			// reserve if available is non-blocking

			if ( queue_when_full && !thread_sem.reserveIfAvailable()){

				checkWarning();

			}else{

				new threadPoolWorker();
			}
		}
	}

	protected void
	runIt(
		AERunnable	runnable )
	{
		if ( log_cpu ){

			long	start_cpu = log_cpu?AEJavaManagement.getThreadCPUTime():0;
			long	start_time	= SystemTime.getHighPrecisionCounter();

			runnable.run();

			if ( start_cpu > 0 ){

				long	end_cpu = log_cpu?AEJavaManagement.getThreadCPUTime():0;

				long	diff_cpu = ( end_cpu - start_cpu ) / 1000000;

				long	end_time	= SystemTime.getHighPrecisionCounter();

				long	diff_millis = ( end_time - start_time ) / 1000000;

				if ( diff_cpu > 10 || diff_millis > 10){

					System.out.println( TimeFormatter.milliStamp() + ": Thread: " + Thread.currentThread().getName() + ": " + runnable + " -> " + diff_cpu + "/" + diff_millis );
				}
			}
		}else{

			runnable.run();
		}
	}

	protected void checkWarning() {
		if (warn_when_full)
		{
			String task_names = "";
			try
			{
				synchronized (ThreadPoolVirtual.this)
				{
					for (int i = 0; i < busy.size(); i++)
					{
						threadPoolWorker x = (threadPoolWorker) busy.get(i);
						AERunnable r = x.runnable;
						if (r != null)
						{
							String name;
							if (r instanceof ThreadPoolTask){
								name = ((ThreadPoolTask) r).getName();
							}else if ( r instanceof AERunnable.AERunnableNamed ){
								name = ((AERunnable.AERunnableNamed)r).getName();
							}else{
								name = r.getClass().getName();
							}
							task_names += (task_names.length() == 0 ? "" : ",") + name;
						}
					}
				}
			} catch (Throwable e)
			{}
			Debug.out("Thread pool '" + getName() + "' is full (busy=" + task_names + ")");
			warn_when_full = false;
		}
	}

	public AERunnable[] getQueuedTasks() {
		synchronized (this)
		{
			AERunnable[] res = new AERunnable[task_queue.size()];
			task_queue.toArray(res);
			return (res);
		}
	}

	public AERunnable
	getOldestQueuedTask()
	{
		synchronized (this){
			int num = task_queue.size();
			
			if ( num > 0 ){
				return( task_queue.get( num-1 ));
			}else{
				return( null );
			}
		}
	}
	
	public int getQueueSize() {
		synchronized (this)
		{
			return task_queue.size();
		}
	}

	public boolean isQueued(AERunnable task) {
		synchronized (this)
		{
			return task_queue.contains(task);
		}
	}

	public List<T>
	getRunningTasks()
	{
		List<T>	tasks;

		synchronized( this ){

			tasks	= new ArrayList<>( busy.size());

			Iterator<threadPoolWorker>	it = busy.iterator();

			while( it.hasNext()){

				threadPoolWorker	worker = (threadPoolWorker)it.next();

				T	runnable = worker.getRunnable();

				if ( runnable != null ){

					tasks.add( runnable );
				}
			}
		}

		return( tasks );
	}

	public int
  	getRunningCount()
  	{
  		int	res = 0;

  		synchronized( this ){

  			Iterator	it = busy.iterator();

  			while( it.hasNext()){

  				threadPoolWorker	worker = (threadPoolWorker)it.next();

  				AERunnable	runnable = worker.getRunnable();

  				if ( runnable != null ){

  					res++;
  				}
  			}
  		}

  		return( res );
  	}

	public boolean
	isFull()
	{
		return( thread_sem.getValue() == 0 );
	}

	public void
	setMaxThreads(
		int		max )
	{
		if ( max < 1 ){
			Debug.out( "Invalid thread pool max: " + max );
			max = 1;
		}
		
		synchronized( this ){

			if ( max == target_permits ){
	
				return;
			}
		
			target_permits = max;
			
			while( target_permits < current_permits ){

				if ( thread_sem.reserveIfAvailable()){

					current_permits--;

				}else{

					break;
				}
			}
			
			while( target_permits > current_permits ){

				thread_sem.release();
				
				current_permits++;
			}
		}
	}

	protected void
	checkTimeouts()
	{
		synchronized( this ){

			long	diff = task_total - task_total_last;

			task_average.addValue( diff );

			task_total_last = task_total;

			if ( debug_thread_pool_log_on ){

				System.out.println( "ThreadPool '" + getName() + "'/" + thread_name_index + ": max=" + target_permits + ",sem=[" + thread_sem.getString() + "],busy=" + busy.size() + ",queue=" + task_queue.size());
			}

			long	now = SystemTime.getMonotonousTime();

			for (int i=0;i<busy.size();i++){

				threadPoolWorker	x = (threadPoolWorker)busy.get(i);

				long	elapsed = now - x.run_start_time;

				if ( elapsed > ( (long)WARN_TIME * (x.warn_count+1))){

					x.warn_count++;

					if ( LOG_WARNINGS ){

						DebugLight.out( x.getWorkerName() + ": running, elapsed = " + elapsed + ", state = " + x.state );
					}

					if ( execution_limit > 0 && elapsed > execution_limit ){

						if ( LOG_WARNINGS ){

							DebugLight.out( x.getWorkerName() + ": interrupting" );
						}

						AERunnable r = x.runnable;

						if ( r != null ){

							try{
								if ( r instanceof ThreadPoolTask ){

									((ThreadPoolTask)r).interruptTask();

								}else{

									x.interrupt();
								}
							}catch( Throwable e ){

								DebugLight.printStackTrace( e );
							}
						}
					}
				}
			}
		}
	}

	public String getName() {
		return (name);
	}

	void releaseManual(ThreadPoolTask toRelease) {
		Debug.out( "eh?" );
	}

	public void registerThreadAsChild(ThreadPoolTask.Worker parentt)
	{
		Debug.out( "eh?" );
	}

	public void deregisterThreadAsChild(ThreadPoolTask.Worker parent)
	{
		Debug.out( "eh?" );
	}


	class threadPoolWorker
		implements ThreadPoolTask.Worker, Runnable
	{
		private final String		worker_name;
		private volatile T			runnable;
		private long				run_start_time;
		private int					warn_count;
		private String				state	= "<none>";

		private final AEThreadVirtual		thread;
		
		protected threadPoolWorker()
		{
			String thread_name = NAME_THREADS?(name + " " + (thread_name_index)):name;
			
			thread = new AEThreadVirtual( thread_name);
			
			thread_name_index++;
			// setPriority(thread_priority);
			worker_name = thread_name;
			
			
			thread.start( threadPoolWorker.this );
		}

		@Override
		public void run() {
			tls.set(threadPoolWorker.this);

			try
			{
				do
				{
					try
					{
						synchronized (ThreadPoolVirtual.this)
						{
							if (task_queue.size() > 0)
								runnable = task_queue.remove(0);
							else
								break;
						}

						synchronized (ThreadPoolVirtual.this)
						{
							run_start_time = SystemTime.getMonotonousTime();
							warn_count = 0;
							busy.add(threadPoolWorker.this);
							task_total++;
							if (busy.size() == 1)
							{
								synchronized (busy_pools)
								{
									if (!busy_pools.contains(ThreadPoolVirtual.this))
									{
										busy_pools.add(ThreadPoolVirtual.this);
										if (!busy_pool_timer_set)
										{
											// we have to defer this action rather
											// than running as a static initialiser
											// due to the dependency between
											// ThreadPool, Timer and ThreadPool again
											COConfigurationManager.addAndFireParameterListeners(new String[] { "debug.threadpool.log.enable", "debug.threadpool.debug.trace" }, new ParameterListener()
											{
												@Override
												public void parameterChanged(String name) {
													debug_thread_pool = COConfigurationManager.getBooleanParameter("debug.threadpool.log.enable", false);
													debug_thread_pool_log_on = COConfigurationManager.getBooleanParameter("debug.threadpool.debug.trace", false);
												}
											});
											busy_pool_timer_set = true;
											SimpleTimer.addPeriodicEvent("ThreadPool:timeout", WARN_TIME, new TimerEventPerformer()
											{
												@Override
												public void perform(TimerEvent event) {
													checkAllTimeouts();
												}
											});
										}
									}
								}
							}
						}

						if (runnable instanceof ThreadPoolTask){
							
							ThreadPoolTask tpt = (ThreadPoolTask) runnable;
							
							tpt.worker = this;
							
							String task_name = NAME_THREADS?tpt.getName():null;
							
							try{
								if (task_name != null){
									thread.setName(worker_name + "{" + task_name + "}");
								}
								
								tpt.taskStarted();
								
								runIt(runnable);
								
							}finally{
								
								if (task_name != null){
									
									thread.setName(worker_name);
								}
								
								tpt.taskCompleted();
							}
						}else{
							
							runIt(runnable);
						}
					}catch (Throwable e){
						
						DebugLight.printStackTrace(e);
						
					}finally{
						
						synchronized (ThreadPoolVirtual.this){
							
							long elapsed = SystemTime.getMonotonousTime() - run_start_time;
							
							if (elapsed > WARN_TIME && LOG_WARNINGS){
								DebugLight.out(getWorkerName() + ": terminated, elapsed = " + elapsed + ", state = " + state);
							}
							
							busy.remove(threadPoolWorker.this);

								// if debug is on we leave the pool registered so that we
								// can trace on the timeout events
							
							if ( busy.size() == 0 && !debug_thread_pool){
								
								synchronized (busy_pools){
									
									busy_pools.remove(ThreadPoolVirtual.this);
								}
							}
						}
					}
				}while (runnable != null);
				
			}catch (Throwable e){
				
				DebugLight.printStackTrace(e);
				
			}finally{
				
				synchronized (ThreadPoolVirtual.this){

					if ( current_permits > target_permits ){

						current_permits--;

					}else{

						thread_sem.release();
					}
				}

				tls.set(null);
			}
		}

		@Override
		public long 
		getStartTime()
		{
			return( run_start_time );
		}
				
		public void setState(String _state) {
			//System.out.println( "state = " + _state );
			state = _state;
		}

		public String getState() {
			return (state);
		}

		public String getWorkerName() {
			return (worker_name);
		}

		public ThreadPoolVirtual<T> getOwner() {
			return (ThreadPoolVirtual.this);
		}

		@Override
		public void 
		releaseManual(
			ThreadPoolTask task)
		{
			ThreadPoolVirtual.this.releaseManual(task);
		}
		
		protected T getRunnable() {
			return (runnable);
		}
		
		@Override
		public void interrupt(){
			thread.interrupt();
		}
	}
}
