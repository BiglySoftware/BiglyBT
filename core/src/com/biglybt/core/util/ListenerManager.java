/*
 * File    : ListenerManager.java
 * Created : 15-Jan-2004
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

/**
 * This class exists to support the invocation of listeners while *not* synchronized.
 * This is important as in general it is a bad idea to invoke an "external" component
 * whilst holding a lock on something as unexpected deadlocks can result.
 * It has been introduced to reduce the likelyhood of such deadlocks
 */

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;


public class
ListenerManager<T>
{
	private static final boolean TIME_LISTENERS = false;

	public static <T>ListenerManager<T>
	createManager(
		String										name,
		ListenerManagerDispatcher<T>				target )
	{
		return(new ListenerManager<>(name, target, false));
	}

	public static <T>ListenerManager<T>
	createAsyncManager(
		String							name,
		ListenerManagerDispatcher<T>	target )
	{
		return(new ListenerManager<>(name, target, true));
	}


	private final String	name;

	private final ListenerManagerDispatcher<T>				target;
	private ListenerManagerDispatcherWithException<T>		target_with_exception;

	private final boolean		async;
	private AEThread2	async_thread;

	private CopyOnWriteList<T>			listeners		= new CopyOnWriteList<>();

	private List<Object[]>	dispatch_queue;
	private AESemaphore		dispatch_sem;

	private int		logged_too_many_count;
	
	protected
	ListenerManager(
		String								_name,
		ListenerManagerDispatcher<T>		_target,
		boolean								_async )
	{
		name	= _name;
		target	= _target;
		async	= _async;

		if ( target instanceof ListenerManagerDispatcherWithException ){

			target_with_exception = (ListenerManagerDispatcherWithException<T>)target;
		}

		if ( async ){

			dispatch_sem	= new AESemaphore("ListenerManager::"+name);
			dispatch_queue 	= new LinkedList<>();

			if ( target_with_exception != null ){

				throw( new RuntimeException( "Can't have an async manager with exceptions!"));
			}
		}
	}

	public void
	addListener(
		T		listener )
	{
		if (listener == null) {

			Debug.out("Trying to add null listener to " + name);
			return;
		}

		synchronized( this ){

			if ( listeners.contains(listener)) {
				if ( Constants.IS_CVS_VERSION ){
					Debug.out( "check this out: listener added twice" );
				}
				Logger.log(new LogEvent(LogIDs.CORE, LogEvent.LT_WARNING,
						"addListener called but listener already added for " + name
								+ "\n\t" + Debug.getStackTrace(true, false)));
			}
			
			listeners.add( listener );

			int num_listeners = listeners.size();
			
			if ( num_listeners % 50 == 0 && num_listeners > logged_too_many_count ){
				
				logged_too_many_count = num_listeners;
				
				Logger.log(
					new LogEvent(
						LogIDs.CORE, LogEvent.LT_WARNING,
						num_listeners + " listeners added for " + name + "\n\t" + Debug.getStackTrace(true, false)));

				if ( Constants.IS_CVS_VERSION ){
					Debug.outNoStack( num_listeners + " listeners added for " + name + "\n\t" + Debug.getStackTrace(true, false) + "\n\t" + listeners.getList());
				}
			}

			if ( async && async_thread == null ){

				async_thread = new AEThread2( name, true )
					{
						@Override
						public void
						run()
						{
							dispatchLoop();
						}
					};

				async_thread.start();
			}
		}
	}

	public void
	removeListener(
		T		listener )
	{
		synchronized( this ){

			listeners.remove( listener );

			if ( async && listeners.isEmpty()){

				async_thread = null;

					// try and wake up the thread so it kills itself

				dispatch_sem.release();
			}
		}
	}

	public boolean
	hasListener(
		T		listener )
	{
		return( listeners.contains( listener ));
	}

	public void
	clear()
	{
		synchronized( this ){

			listeners.clear();

			if ( async ){

				async_thread = null;

					// try and wake up the thread so it kills itself

				dispatch_sem.release();
			}
		}
	}

	public List<T>
	getListenersCopy()
	{
			// we can just return the listeners as we copy on update
		return( listeners.getList());
	}

	public void
	dispatch(
		int		type,
		Object	value )
	{
		dispatch( type, value, false );
	}

	public void
	dispatch(
		int			type,
		Object		value,
		boolean		blocking )
	{
		if ( async ){

			AESemaphore	sem = null;

			if ( blocking ){

				sem = new AESemaphore( "ListenerManager:blocker");
			}

			synchronized( this ){

					// if there's nobody listening then no point in queueing

				if ( listeners.isEmpty()){

					return;
				}

					// listeners are "copy on write" updated, hence we grab a reference to the
					// current listeners here. Any subsequent change won't affect our listeners

				dispatch_queue.add(new Object[]{listeners.getList(), new Integer(type), value, sem });

				if ( async_thread == null ){

					async_thread = new AEThread2( name, true )
						{
							@Override
							public void
							run()
							{
								dispatchLoop();
							}
						};

					async_thread.start();
				}
			}

			dispatch_sem.release();

			if ( sem != null ){

				sem.reserve();
			}
		}else{

			if ( target_with_exception != null ){

				throw( new RuntimeException( "call dispatchWithException, not dispatch"));
			}

			try{
				dispatchInternal( listeners.getList(), type, value );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	public void
	dispatchWithException(
		int		type,
		Object	value )

		throws Throwable
	{
		dispatchInternal( listeners.getList(), type, value );
	}

	public void
	dispatch(
		T		listener,
		int		type,
		Object	value )
	{
		dispatch( listener, type, value, false );
	}

	public void
	dispatch(
		T		listener,
		int		type,
		Object	value,
		boolean	blocking )
	{
		if ( async ){

			AESemaphore	sem = null;

			if ( blocking ){

				sem = new AESemaphore( "ListenerManager:blocker");
			}

			synchronized( this ){

					// 5 entries to denote single listener

				dispatch_queue.add(new Object[]{ listener, new Integer(type), value, sem, null });

				if ( async_thread == null ){

					async_thread = new AEThread2( name, true )
						{
							@Override
							public void
							run()
							{
								dispatchLoop();
							}
						};

					async_thread.start();
				}
			}

			dispatch_sem.release();

			if ( sem != null ){

				sem.reserve();
			}
		}else{

			if ( target_with_exception != null ){

				throw( new RuntimeException( "call dispatchWithException, not dispatch"));
			}

			doDispatch( listener, type, value );
		}
	}

	protected String
	getListenerName(
		T	 listener )
	{
		Class listener_class = listener.getClass();

		String	res = listener_class.getName();

		try{
			Method getString = listener_class.getMethod( "getString", new Class[0]);

			if ( getString != null ){

				String s = (String)getString.invoke( listener, new Object[0] );

				res += " (" + s + ")";
			}
		}catch( Throwable e ){

		}

		return( res );
	}

	protected void
	doDispatch(
		T			listener,
		int			type,
		Object		value )
	{
		try{
			if ( TIME_LISTENERS ){

				long	start = SystemTime.getCurrentTime();

				try{

					target.dispatch( listener, type, value );

				}finally{

					long duration = SystemTime.getCurrentTime() - start;

					System.out.println( name + "/" + type + ": " + getListenerName( listener ) + " - " + duration );
				}
			}else{

				target.dispatch( listener, type, value );
			}

		}catch( Throwable e ){

			Debug.printStackTrace( e );
		}
	}

	protected void
	doDispatchWithException(
		T			listener,
		int			type,
		Object		value )

		throws Throwable
	{
		if ( TIME_LISTENERS ){

			long	start = SystemTime.getCurrentTime();

			try{
				target_with_exception.dispatchWithException( listener, type, value );

			}finally{

				long duration = SystemTime.getCurrentTime() - start;

				System.out.println( name + "/" + type + ": " + getListenerName( listener ) + " - " + duration );
			}
		}else{

			target_with_exception.dispatchWithException( listener, type, value );
		}
	}

	protected void
	dispatchInternal(
		List<T>		listeners_ref,
		int			type,
		Object		value )

		throws Throwable
	{
		for (int i=0;i<listeners_ref.size();i++){


			if ( target_with_exception != null ){

				// System.out.println( name + ":dispatchWithException" );

					// DON'T catch and handle exceptions here are they are permitted to
					// occur!

				doDispatchWithException( listeners_ref.get(i), type, value );

			}else{

				doDispatch( listeners_ref.get(i), type, value );
			}
		}
	}

	protected void
	dispatchInternal(
		T			listener,
		int			type,
		Object		value )

		throws Throwable
	{
		if ( target_with_exception != null ){

				// System.out.println( name + ":dispatchWithException" );

				// DON'T catch and handle exceptions here are they are permitted to
				// occur!

			doDispatchWithException( listener, type, value );

		}else{

			doDispatch( listener, type, value );
		}
	}

	public void
	dispatchLoop()
	{
		// System.out.println( "ListenerManager::dispatch thread '" + Thread.currentThread() + "' starts");

		while(true){

			boolean got_sem = dispatch_sem.reserve(20*1000);

			Object[] data = null;

			synchronized( this ){

				if ( async_thread == null || !async_thread.isCurrentThread()){

						// we've been asked to close. this sem reservation must be
						// "returned" to the pool in case it represents a valid  entry
						// to be picked up by another thread

					if ( got_sem ){
					
						dispatch_sem.release();
					}

					break;
				}

				if ( dispatch_queue.isEmpty()){

					if ( !got_sem ){
						
						if ( async_thread != null && async_thread.isCurrentThread()){
							
								// idle timeout
						
							async_thread = null;
														
							break;
						}
					}
				}else{
					
					data = (Object[])dispatch_queue.remove(0);
				}
			}

			if ( data != null ){
				
				try{
					if ( data.length == 4 ){

						dispatchInternal((List<T>)data[0], ((Integer)data[1]).intValue(), data[2] );

					}else{

						dispatchInternal((T)data[0], ((Integer)data[1]).intValue(), data[2] );
					}

				}catch( Throwable e ){

					Debug.printStackTrace( e );

				}finally{

					if ( data[3] != null ){

						((AESemaphore)data[3]).release();
					}
				}
			}
		}

		// System.out.println( "ListenerManager::dispatch thread '" + Thread.currentThread() + "' ends");
	}

	public static <T>void
	dispatchWithTimeout(
		List<T>								_listeners,
		final ListenerManagerDispatcher<T>	_dispatcher,
		long								_timeout )
	{
		final List<T>	listeners = new ArrayList<>(_listeners);

		final boolean[]	completed = new boolean[listeners.size()];

		final AESemaphore	timeout_sem = new AESemaphore("ListenerManager:dwt:timeout");

		for (int i=0;i<listeners.size();i++){

			final int f_i	= i;

			new AEThread2( "ListenerManager:dwt:dispatcher", true ){
				@Override
				public void
				run()
				{
					try{
						_dispatcher.dispatch( listeners.get(f_i), -1, null );

					}catch( Throwable e ){

						Debug.printStackTrace(e);

					}finally{

						completed[f_i]	= true;

						timeout_sem.release();
					}
				}
			}.start();
		}

		boolean	timeout_occurred = false;

		for (int i=0;i<listeners.size() ;i++){

			if ( _timeout <= 0 ){

				timeout_occurred	= true;

				break;
			}

			long start = SystemTime.getCurrentTime();

			if ( !timeout_sem.reserve( _timeout )){

				timeout_occurred	= true;

				break;
			}

			long end = SystemTime.getCurrentTime();

			if ( end > start ){

				_timeout = _timeout - ( end - start );
			}
		}

		if ( timeout_occurred ){

			String	str = "";

			for (int i=0;i<completed.length;i++){

				if ( !completed[i] ){

					str += (str.length()==0?"":",") + listeners.get(i);
				}
			}

			if ( str.length() > 0 ){

				Debug.out( "Listener dispatch timeout: failed = " + str );
			}
		}
	}

	public long
	size()
	{
		return( listeners.size());
	}
}

