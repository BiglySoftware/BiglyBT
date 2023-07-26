/*
 * Created on 22-Sep-2004
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

import java.util.*;

/**
 * @author parg
 *
 */

public abstract class
AEMonSem
{
	protected static final boolean	DEBUG					= AEDiagnostics.DEBUG_MONITOR_SEM_USAGE;
	protected static final boolean	DEBUG_CHECK_DUPLICATES	= false;

	protected static final long		DEBUG_TIMER				= 30000;

	private static final ThreadLocal		tls	=
		new ThreadLocal()
		{
			@Override
			public Object
			initialValue()
			{
				return( new Stack());
			}
		};

	private static long	monitor_id_next;
	private static long	semaphore_id_next;

	private static final Map 	debug_traces		= new HashMap();
	static final List	debug_recursions	= new ArrayList();
	private static final List	debug_reciprocals	= new ArrayList();
	//private static List	debug_sem_in_mon	= new ArrayList();



	private static final Map	debug_name_mapping		= new WeakHashMap();
	private static final Map	debug_monitors			= new WeakHashMap();
	private static final Map	debug_semaphores		= new WeakHashMap();

	static{
		if ( DEBUG ){

				// defer this due to initialisation problems

			Thread t = new Thread( "AEMonSem:delay debug init" )
			{
				@Override
				public void
				run()
				{
					// add known and validated exceptions
					debug_recursions.add( "ResourceDownloader" );		// known tree recursion
					debug_recursions.add( "ConnectionPool:CP" );		// known tree recursion
					debug_recursions.add( "(S)RDRretry" );				// RDretry sem left on stack after 1st d/l so appears recursive on subsequent

					try{
						Thread.sleep(DEBUG_TIMER);

					}catch( Throwable e ){
					}

					TimerEventPerformer performer =
						new TimerEventPerformer()
						{
							AEDiagnosticsLogger diag_logger;

							@Override
							public void
							perform(
								TimerEvent	event )
							{
								if ( diag_logger == null ){

									diag_logger	= AEDiagnostics.getLogger( "monsem" );
								}

								check( diag_logger );
							}
						};

					performer.perform( null );

					new Timer("AEMonSem").addPeriodicEvent(	DEBUG_TIMER, performer );
				}
			};

			t.setDaemon( true );

			t.start();
		}
	}

	protected static void
	check(
		AEDiagnosticsLogger diag_logger )
	{
		List	active				= new ArrayList();
		List	waiting_monitors	= new ArrayList();
		List	busy_monitors		= new ArrayList();
		List	waiting_semaphores	= new ArrayList();

		synchronized( AEMonSem.class ){

			// dumpTrace();

			diag_logger.log(
					"AEMonSem: mid = " + monitor_id_next +
					", sid = " + semaphore_id_next +
					", monitors = " + debug_monitors.size() +
					", semaphores = " + debug_semaphores.size() +
					", names = " + debug_name_mapping.size() +
					", traces = " + debug_traces.size());


			Iterator 	it = debug_monitors.keySet().iterator();

			long	new_mon_entries	= 0;

			while (it.hasNext()){

				AEMonitorOld	monitor = (AEMonitorOld)it.next();

				long	diff = monitor.entry_count - monitor.last_entry_count;

				if (  diff != 0 ){

					active.add( monitor );

					new_mon_entries += diff;
				}

				if (monitor.waiting > 0 ){

					waiting_monitors.add( monitor );

				}else if ( monitor.owner != null ){

					busy_monitors.add( monitor );
				}
			}

			it = debug_semaphores.keySet().iterator();

			long	new_sem_entries	= 0;

			while (it.hasNext()){

				AEMonSem	semaphore = (AEMonSem)it.next();

				long	diff = semaphore.entry_count - semaphore.last_entry_count;

				if (  diff != 0 ){

					active.add( semaphore );

					new_sem_entries += diff;
				}

				if (semaphore.waiting > 0 ){

					waiting_semaphores.add( semaphore );
				}
			}

			diag_logger.log(
					"    activity: monitors = " + new_mon_entries + " - " + (new_mon_entries / (DEBUG_TIMER/1000)) +
					"/sec, semaphores = " + new_sem_entries + " - " +
					(new_sem_entries / (DEBUG_TIMER/1000)) + "/sec ");
		}

		AEMonSem[]	x = new AEMonSem[active.size()];

		active.toArray(x);

			// sort by name and merge values

		Arrays.sort(
				x,
				new Comparator()
				{
					@Override
					public int
					compare(
						Object	o1,
						Object	o2 )
					{
						AEMonSem	a1 = (AEMonSem)o1;
						AEMonSem	a2 = (AEMonSem)o2;

						return( a1.name.compareTo( a2.name ));
					}

				});

		AEMonSem	current 		= null;
		long		current_total	= 0;

		Object[][]	total_x = new Object[x.length][];

		int	total_pos	= 0;

		for (int i=0;i<x.length;i++){

			AEMonSem	ms = x[i];

			long	diff = ms.entry_count - ms.last_entry_count;

			if ( current == null ){

				current	= ms;

			}else{

				if( current.name.equals( ms.name )){

					current_total += diff;

				}else{
					total_x[total_pos++] = new Object[]{ current.name, new Long( current_total )};

					current 		= ms;
					current_total	= diff;
				}
			}
		}

		if (current != null ){

			total_x[total_pos++] = new Object[]{ current.name, new Long( current_total )};
		}

		Arrays.sort(
			total_x,
			new Comparator()
			{
				@Override
				public int
				compare(
					Object	o1,
					Object	o2 )
				{
					Object[]	a1 = (Object[])o1;
					Object[]	a2 = (Object[])o2;

					if ( a1 == null && a2 == null){

						return(0);

					}else if ( a1 == null ){

						return( 1 );

					}else if ( a2 == null ){

						return( -1 );
					}

					long	a1_count = ((Long)a1[1]).longValue();
					long	a2_count = ((Long)a2[1]).longValue();

					return((int)(a2_count - a1_count ));
				}

			});




		String	top_act_str = "    top activity: ";

		for (int i=0;i<Math.min(10,total_x.length);i++){

			if ( total_x[i] != null ){

				top_act_str +=  (i==0?"":", ") + total_x[i][0] + " = " + (total_x[i][1]);
			}
		}

		diag_logger.log( top_act_str );

		if ( waiting_monitors.size() > 0 ){

			diag_logger.log( "    waiting monitors" );

			for (int i=0;i<waiting_monitors.size();i++){

				AEMonSem	ms = (AEMonSem)waiting_monitors.get(i);

				Thread last_waiter = ((AEMonitorOld)ms).last_waiter;

				diag_logger.log( "        [" + (last_waiter==null?"<waiter lost>":last_waiter.getName()) + "] " +  ms.name + " - " + ms.last_trace_key );
			}
		}

		if ( busy_monitors.size() > 0 ){

			diag_logger.log( "    busy monitors" );

			for (int i=0;i<busy_monitors.size();i++){

				AEMonSem	ms = (AEMonSem)busy_monitors.get(i);

				Thread owner = ((AEMonitorOld)ms).owner;

				diag_logger.log( "        [" + (owner==null?"<owner lost>":owner.getName()) + "] " + ms.name + " - " + ms.last_trace_key );
			}
		}

		if ( waiting_semaphores.size() > 0 ){

			diag_logger.log( "    waiting semaphores" );

			for (int i=0;i<waiting_semaphores.size();i++){

				AEMonSem	ms = (AEMonSem)waiting_semaphores.get(i);

				Thread last_waiter = ((AESemaphoreOld)ms).latest_waiter;

				diag_logger.log( "        [" + (last_waiter==null?"<waiter lost>":last_waiter.getName()) + "] " +  ms.name + " - " + ms.last_trace_key );
			}
		}

		for (int i=0;i<x.length;i++){

			AEMonSem	ms = x[i];

			ms.last_entry_count = ms.entry_count;
		}
	}


	protected long			entry_count;
	protected long			last_entry_count;
	protected String		last_trace_key;


	protected final String		name;
	protected final boolean		is_monitor;
	protected int			waiting		= 0;

	protected
	AEMonSem(
		String	_name,
		boolean	_monitor )
	{
		is_monitor		= _monitor;

		if ( is_monitor) {

			name		= _name;
		}else{

			name		= StringInterner.intern("(S)" + _name);
		}

		if ( DEBUG ){

			synchronized( AEMonSem.class ){

				if ( is_monitor ){
					monitor_id_next++;
				}else{
					semaphore_id_next++;
				}

				StackTraceElement	elt = new Exception().getStackTrace()[2];

				String	class_name 	= elt.getClassName();
				int		line_number	= elt.getLineNumber();

				monSemData new_entry	= new monSemData( class_name, line_number);

				if ( is_monitor ){

					debug_monitors.put( this, new_entry );

				}else{

					debug_semaphores.put( this, new_entry );
				}

				if ( DEBUG_CHECK_DUPLICATES ){

					monSemData		existing_name_entry	= (monSemData)debug_name_mapping.get( name );

					if ( existing_name_entry == null ){

						debug_name_mapping.put( name, new_entry );

					}else{

						if ( 	( !existing_name_entry.class_name.equals( class_name )) ||
								existing_name_entry.line_number != line_number ){

							Debug.out( new Exception("Duplicate AEMonSem name '" + name + "'"));
						}
					}
				}
			}
		}
	}

	protected void
	debugEntry()
	{
		/*
		if ( trace ){
			traceEntry();
		}
		*/

		try{
				// bad things are:
				// A->B and somewhere else B->A
				// or
				// A(inst1) -> A(inst2)

			Stack	stack = (Stack)tls.get();

			if ( stack.size() > 64 ){

				StringBuilder sb = new StringBuilder(1024);

				for (int i=0;i<stack.size();i++){

					AEMonSem	mon = (AEMonSem)stack.get(i);

					sb.append("$").append(mon.name);
				}

				Debug.out( "**** Whoaaaaaa, AEMonSem debug stack is getting too large!!!! **** " + sb );
			}

			if ( !stack.isEmpty()){

				String	recursion_trace = "";

				/* not very useful
				if (	(!is_monitor) &&
						((AEMonSem)stack.peek()).is_monitor ){

					if ( !debug_sem_in_mon.contains( name )){

						recursion_trace += ( recursion_trace.length()==0?"":"\r\n" ) +
											"Semaphore reservation while holding a monitor: sem = " + name+ ", mon = " + ((AEMonSem)stack.peek()).name;

						debug_sem_in_mon.add( name );
					}
				}
				*/

				StringBuilder sb = new StringBuilder();

					// not very interesting for semaphores as these tend to get left on stack traces when
					// asymmetric usage (which is often)

				boolean	check_recursion = is_monitor && !debug_recursions.contains( name );

				String	prev_name	= null;

				for (int i=0;i<stack.size();i++){

					AEMonSem	mon = (AEMonSem)stack.get(i);

					if ( check_recursion ){
						if ( 	mon.name.equals( name ) &&
								mon != this ){

							recursion_trace +=
								( recursion_trace.length()==0?"":"\r\n" ) +
								"Recursive locks on different instances: " + name;

							debug_recursions.add( name );
						}
					}

						// remove consecutive duplicates

					if ( prev_name == null || !mon.name.equals( prev_name )){

						sb.append("$");
						sb.append(mon.name);
					}

					prev_name	= mon.name;
				}

				sb.append( "$" );
				sb.append( name );
				sb.append( "$" );

				String trace_key = sb.toString();

				if ( recursion_trace.length() > 0 ){

					Debug.outNoStack( recursion_trace + "\r\n    " + trace_key );
				}

				last_trace_key	= trace_key;

				if ( !is_monitor ){

						// only add semaphores to the stack if they aren't already present.
						// This is because we can reserve a semaphore on one thread and
						// release it on another. This will grow the stack indefinitely

					boolean	match 	= false;

					for (int i=0;i<stack.size();i++){

						AEMonSem	ms = (AEMonSem)stack.get(i);

						if ( ms.name.equals( name )){

							match	= true;

							break;
						}
					}

					if ( !match ){

						stack.push( this );
					}
				}else{

					stack.push( this );
				}

				synchronized( debug_traces ){

					if ( debug_traces.get(trace_key) == null ){

						Thread	thread = Thread.currentThread();

						String	thread_name	= thread.getName() + "[" + thread.hashCode() + "]";

						String	stack_trace	= Debug.getStackTrace(true, false);

						Iterator	it = debug_traces.keySet().iterator();

						while( it.hasNext()){

							String	old_key = (String)it.next();

							String[]	data = (String[])debug_traces.get(old_key);

							String	old_thread_name	= data[0];
							String	old_trace		= data[1];

								// if identical thread then we can ignore this as
								// it can't happen concurrently

							if ( thread_name.equals( old_thread_name )){

								continue;
							}

								// find the earliest occurrence of a common monitor - no point in searching
								// beyond it
								//    e.g.  a -> b -> c -> g
							    //          x -> y -> b -> z
								// stop at b because beyond this things are "protected"


							int	earliest_common = stack.size();
							int	common_count	= 0;

							for (int i=0;i<stack.size();i++){

								String	n1 = ((AEMonSem)stack.get(i)).name;

								int	p1 = old_key.indexOf( "$" + n1 + "$");

								if ( p1 != -1 ){

									common_count++;

									earliest_common = Math.min( earliest_common, i+1 );
								}
							}

								// need at least 2 common monitors for chance of deadlock

							if ( common_count >= 2 ){

								for (int i=0;i<earliest_common;i++){

									AEMonSem	ms1 = (AEMonSem)stack.get(i);

									if ( !ms1.is_monitor ){

										continue;
									}

									String	n1 = ms1.name;

									for (int j=i+1;j<stack.size();j++){

										AEMonSem	ms2 = (AEMonSem)stack.get(j);

										if ( !ms2.is_monitor ){

											continue;
										}

										String	n2 = ms2.name;

											// same object recursion already tested above

										if ( !n1.equals( n2 )){

											int	p1 = old_key.indexOf( "$" + n1 + "$");
											int p2 = old_key.indexOf( "$" + n2 + "$");

											if ( p1 != -1 && p2 != -1 && p1 > p2 ){

												String	reciprocal_log = trace_key + " / " + old_key;

												if ( !debug_reciprocals.contains( reciprocal_log )){

													debug_reciprocals.add( reciprocal_log );

													Debug.outNoStack(
															"AEMonSem: Reciprocal usage:\r\n" +
															"    " + trace_key + "\r\n" +
															"        [" + thread_name + "] " + stack_trace + "\r\n" +
															"    " + old_key + "\r\n" +
															"        [" + old_thread_name + "] " + old_trace );
												}
											}
										}
									}
								}
							}
						}

						debug_traces.put( trace_key, new String[]{ thread_name, stack_trace });

							// look through all the traces for an A->B and B->A
					}
				}

			}else{

				last_trace_key	= "$" + name + "$";

				stack.push( this );

			}
		}catch( Throwable e ){

			try{
				Debug.printStackTrace(e);

			}catch( Throwable f ){

			}
		}
	}

	protected void
	debugExit()
	{
		try{
			Stack	stack = (Stack)tls.get();

			if ( is_monitor ){

					// skip over any sem reserves within a sync block

				while( stack.peek() != this ){

					stack.pop();
				}

				stack.pop();

			}else{

					// for semaphores we can release stuff without a matching reserve if
					// the semaphore has an initial value or if we have one thread releasing
					// a semaphore and another reserving it

				if ( !stack.isEmpty()){

					if ( stack.peek() == this ){

						stack.pop();
					}
				}
			}
		}catch( Throwable e ){

			try{
				Debug.printStackTrace(e);

			}catch( Throwable f ){

			}
		}
	}

	/*
	protected boolean			trace;
	protected static Map		trace_map = new HashMap();

	public void
	trace(
		boolean	_on )
	{
		trace	= _on;
	}

	protected void
	traceEntry()
	{
		String str = Debug.getCompressedStackTrace();

		synchronized( trace_map ){
			Map map = (Map)trace_map.get( name );
			if ( map == null ){
				map = new HashMap();
				trace_map.put( name, map );
			}
			Long l = (Long)map.get(str);

			if ( l == null ){
				l = new Long(1);
			}else{
				l = new Long(l.longValue()+1);
			}
			map.put(str,l);
		}
	}

	protected static void
	dumpTrace()
	{
		synchronized( trace_map ){

			Iterator it = trace_map.entrySet().iterator();

			while( it.hasNext()){

				Map.Entry entry = (Map.Entry)it.next();

				System.out.println( entry.getKey());

				Map map = (Map)entry.getValue();

				Iterator it2 = map.entrySet().iterator();

				while( it2.hasNext()){

					Map.Entry entry2 = (Map.Entry)it2.next();

					System.out.println( "    " + entry2.getValue() + " -> " + entry2.getKey());
				}
			}

			trace_map.clear();
		}
	}
	*/

	public String
	getName()
	{
		return( name );
	}

	protected static class
	monSemData
	{
		protected final String		class_name;
		protected final int			line_number;


		protected
		monSemData(
			String			_class_name,
			int				_line_number )
		{
			class_name		= _class_name;
			line_number		= _line_number;
		}
	}
}
