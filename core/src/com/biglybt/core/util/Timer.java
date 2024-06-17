/*
 * File    : Timer.java
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

import java.lang.ref.WeakReference;
import java.util.*;

import com.biglybt.core.util.TimerEvent.TimerEventLogged;

public class Timer
	extends 	AERunnable
	implements	SystemTime.ChangeListener
{
	private static final boolean DEBUG_TIMERS = true;
	private static ArrayList<WeakReference<Timer>> timers = null;
	static final AEMonitor timers_mon = new AEMonitor("timers list");

	private ThreadPool<TimerEvent>	thread_pool;

	private Set<TimerEvent>	events = new TreeSet<>();

	private long	unique_id_next	= 0;

	private long				current_when;
	private volatile boolean	destroyed;
	private boolean				indestructable;

	private boolean		log;
	private int			max_events_logged;

	private int			slow_event_limit;
	
	public
	Timer(
		String	name )
	{
		this( name, 1 );
	}

	public
	Timer(
		String	name,
		int		thread_pool_size )
	{
		this(name, thread_pool_size, Thread.NORM_PRIORITY);
	}

	public
	Timer(
		String	name,
		int		thread_pool_size,
		int		thread_priority )
	{
		if (DEBUG_TIMERS) {
			try {
				timers_mon.enter();
				if (timers == null) {
					timers = new ArrayList<>();
					AEDiagnostics.addEvidenceGenerator(new evidenceGenerator());
				}
				timers.add(new WeakReference<>(this));
			} finally {
				timers_mon.exit();
			}
		}

		thread_pool = new ThreadPool<TimerEvent>(name,thread_pool_size);

		SystemTime.registerClockChangeListener( this );

		Thread t = new Thread(this, "Timer:" + name );

		t.setDaemon( true );

		t.setPriority(thread_priority);

		t.start();
	}

	public ThreadPool<TimerEvent>
	getThreadPool()
	{
		return( thread_pool );
	}
	
	public void
	setIndestructable()
	{
		indestructable	= true;
	}
	
	public synchronized long
	getLag()
	{
		if ( events.isEmpty()){
			
			return( 0 );
			
		}else{
			
			TimerEvent ev = events.iterator().next();
		
			long lag = SystemTime.getCurrentTime()-ev.getWhen();
		
			if ( lag < 0 ){
			
				return( 0 );
				
			}else{
				
				return( lag );
			}
		}
	}
	
	public List<TimerEvent>
	getActiveEvents()
	{
		return( thread_pool.getRunningTasks());
	}
	
	public synchronized List<TimerEvent>
	getEvents()
	{
		return(new ArrayList<>(events));
	}
	
	public synchronized List<TimerEvent>
	getEvents(
		long	up_to_when )
	{
		List<TimerEvent>	result = new ArrayList<>( events.size());
				
		for ( TimerEvent ev: events ){
			
			if ( ev.getWhen() < up_to_when ){
				
				result.add( ev );
				
			}else{
				
				break;
			}
		}
		
		return( result );
	}
	
	
	public int
	getEventCount()
	{
		return( events.size());
	}
	
	public synchronized int
	getEventCount(
		long	up_to_when )
	{
		int	result = 0;
		
		for ( TimerEvent ev: events ){
			
			if ( ev.getWhen() >= up_to_when ){
			
				break;
			}
			
			result++;
		}
		
		return( result );
	}
	
	public void
	setLogging(
		boolean	_log )
	{
		log	= _log;
	}

	public boolean getLogging() {
		return log;
	}

	public void
	setWarnWhenFull()
	{
		thread_pool.setWarnWhenFull();
	}

	public void
	setSlowEventLimit(
		int		millis )
	{
		slow_event_limit = millis;
	}
	
	public long
	getSlowEventLimit()
	{
		return( slow_event_limit );
	}
	
	public void
	setLogCPU()
	{
		thread_pool.setLogCPU();
	}

	@Override
	public void
	runSupport()
	{
		while( true ){

			try{
				TimerEvent	event_to_run = null;

				synchronized(this){

					if ( destroyed ){

						break;
					}

					if ( events.isEmpty()){

						// System.out.println( "waiting forever" );

						try{
							current_when = Integer.MAX_VALUE;

							this.wait();

						}finally{

							current_when = 0;
						}
					}else{

						long	now = SystemTime.getCurrentTime();

						TimerEvent	next_event = (TimerEvent)events.iterator().next();

						long	when = next_event.getWhen();

						long	delay = when - now;

						if ( delay > 0 ){

							// System.out.println( "waiting for " + delay );

							try{
								current_when = when;

								this.wait(delay);

							}finally{

								current_when = 0;
							}
						}
					}

					if ( destroyed ){

						break;
					}

					if ( events.isEmpty()){

						continue;
					}

					long	now = SystemTime.getCurrentTime();

					Iterator<TimerEvent>	it = events.iterator();

					TimerEvent	next_event = it.next();

					long rem = next_event.getWhen() - now;

					if ( rem <= SystemTime.TIME_GRANULARITY_MILLIS ){

						event_to_run = next_event;

						it.remove();

						/*
						if ( rem < -100 ){

							System.out.println( "Late scheduling [" + (-rem) + "] of " + event_to_run.getString());
						}
						*/
					}

					// System.out.println( getName() +": events=" + events.size() + ", to_run=" +  (event_to_run==null?"null":event_to_run.getString()));
				}

				if ( event_to_run != null ){

					event_to_run.setHasRun();

					if ( log ){
						
						System.out.println( "running: " + event_to_run.getString() );
					}
					
					event_to_run.execute();
				}

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	protected void
	execute(
		TimerEvent		event )
	{
		thread_pool.run( event );
	}
	
	@Override
	public void
	clockChangeDetected(
		long	current_time,
		long	offset )
	{
		if ( Math.abs( offset ) >= 60*1000 ){

				// fix up the timers

			synchronized( this ){

				Iterator<TimerEvent>	it = events.iterator();

				List<TimerEvent>	updated_events = new ArrayList<>(events.size());

				while (it.hasNext()){

					TimerEvent	event = (TimerEvent)it.next();

						// absolute events don't have their timings fiddled with

					if ( !event.isAbsolute()){

						long	old_when = event.getWhen();
						long	new_when = old_when + offset;

						TimerEventPerformer performer = event.getPerformer();

							// sanity check for periodic events

						if ( performer instanceof TimerEventPeriodic ){

							TimerEventPeriodic	periodic_event = (TimerEventPeriodic)performer;

							long	freq = periodic_event.getFrequency();

							if ( new_when > current_time + freq + 5000 ){

								long	adjusted_when = current_time + freq;

								//Debug.outNoStack( periodic_event.getName() + ": clock change sanity check. Reduced schedule time from " + old_when + "/" + new_when + " to " +  adjusted_when );

								new_when = adjusted_when;
							}
						}

						// don't wrap around by accident although this really shouldn't happen

						if ( old_when > 0 && new_when < 0 && offset > 0 ){

							// Debug.out( "Ignoring wrap around for " + event.getName());

						}else{

							// System.out.println( "    adjusted: " + old_when + " -> " + new_when );

							event.setWhen( new_when );
						}
					}

					updated_events.add( event );
				}

					// resort - we have to use an alternative list of events as input because if we just throw the
					// treeset in the constructor optimises things under the assumption that the original set
					// was correctly sorted...

				events = new TreeSet<>(updated_events);
			}
		}
	}

	@Override
	public void
	clockChangeCompleted(
		long	current_time,
		long	offset )
	{
		if ( Math.abs( offset ) >= 60*1000 ){

				// there's a chance that between the change being notified and completed an event was scheduled
				// using an un-modified current time. Nothing can be done for non-periodic events but for periodic
				// ones we can sanitize them to at least be within the periodic time period of the current time
				// important for when clock goes back but not forward obviously

			synchronized( this ){

				Iterator<TimerEvent>	it = events.iterator();

				boolean	updated = false;

				while ( it.hasNext()){

					TimerEvent	event = (TimerEvent)it.next();

						// absolute events don't have their timings fiddled with

					if ( !event.isAbsolute()){

						TimerEventPerformer performer = event.getPerformer();

							// sanity check for periodic events

						if ( performer instanceof TimerEventPeriodic ){

							TimerEventPeriodic	periodic_event = (TimerEventPeriodic)performer;

							long	freq = periodic_event.getFrequency();

							long old_when = event.getWhen();

							if ( old_when > current_time + freq + 5000 ){

								long	adjusted_when = current_time + freq;

								//Debug.outNoStack( periodic_event.getName() + ": clock change sanity check. Reduced schedule time from " + old_when + " to " +  adjusted_when );

								event.setWhen( adjusted_when );

								updated = true;
							}
						}
					}
				}

				if ( updated ){

					events = new TreeSet<>(new ArrayList<>(events));
				}

				// must have this notify here as the scheduling code uses the current time to calculate
				// how long to sleep for and this needs to be guaranteed to be using the correct (new) time

				notify();
			}
		}
	}

	public void
	adjustAllBy(
		long	offset )
	{
		// fix up the timers

		synchronized (this) {

				// as we're adjusting all events by the same amount the ordering remains valid

			Iterator<TimerEvent> it = events.iterator();

			boolean resort = false;

			while (it.hasNext()) {

				TimerEvent event = it.next();

				long old_when = event.getWhen();
				long new_when = old_when + offset;

					// don't wrap around by accident

				if ( old_when > 0 && new_when < 0 && offset > 0 ){

					// Debug.out( "Ignoring wrap around for " + event.getName());

					resort = true;

				}else{

					// System.out.println( "    adjusted: " + old_when + " -> " + new_when );

					event.setWhen( new_when );
				}
			}

			if ( resort ){

				events = new TreeSet<>(new ArrayList<>(events));
			}

			notify();
		}
	}

	public void
	modifyWhen(
		TimerEvent			event,
		long				new_when )
	{
		synchronized( this ){
			
			if ( events.remove( event )){
				
				event.setWhen( new_when );
				
				events.add( event );
				
				if ( current_when == Integer.MAX_VALUE || new_when < current_when ){

					notify();
				}
			}
		}
	}
	
	public synchronized TimerEvent
	addEvent(
		long				when,
		TimerEventPerformer	performer )
	{
		return( addEvent( SystemTime.getCurrentTime(), when, performer ));
	}

	public synchronized TimerEvent
	addEvent(
		String				name,
		long				when,
		TimerEventPerformer	performer )
	{
		return( addEvent( name, SystemTime.getCurrentTime(), when, performer ));
	}

	public synchronized TimerEvent
	addEvent(
		String				name,
		long				when,
		boolean				absolute,
		TimerEventPerformer	performer )
	{
		return( addEvent( name, SystemTime.getCurrentTime(), when, absolute, performer ));
	}

	public synchronized TimerEvent
	addEvent(
		long				creation_time,
		long				when,
		TimerEventPerformer	performer )
	{
		return( addEvent( null, creation_time, when, performer ));
	}

	public synchronized TimerEvent
	addEvent(
		long				creation_time,
		long				when,
		boolean				absolute,
		TimerEventPerformer	performer )
	{
		return( addEvent( null, creation_time, when, absolute, performer ));
	}

	public synchronized TimerEvent
	addEvent(
		String				name,
		long				creation_time,
		long				when,
		TimerEventPerformer	performer )
	{
		return( addEvent( name, creation_time, when, false, performer ));
	}

	public synchronized TimerEvent
	addEvent(
		String				name,
		long				creation_time,
		long				when,
		boolean				absolute,
		TimerEventPerformer	performer )
	{
		TimerEvent	event;
		
		if ( Constants.IS_CVS_VERSION && slow_event_limit > 0 ){
			
			event = new TimerEventLogged( this, unique_id_next++, creation_time, when, absolute, performer );
			
		}else{
		
			event = new TimerEvent( this, unique_id_next++, creation_time, when, absolute, performer );
		}
		
		if ( name != null ){

			event.setName( name );
		}

		events.add( event );

		if ( log ){

			if ( events.size() > max_events_logged ){

				max_events_logged = events.size();

				System.out.println( "Timer '" + thread_pool.getName() + "' - events = " + max_events_logged );
			}
		}

		// System.out.println( "event added (" + when + ") - queue = " + events.size());

		if ( current_when == Integer.MAX_VALUE || when < current_when ){

			notify();
		}

		return( event );
	}

	public synchronized TimerEventPeriodic
	addPeriodicEvent(
		long				frequency,
		TimerEventPerformer	performer )
	{
		return( addPeriodicEvent( null, frequency, performer ));
	}

	public synchronized TimerEventPeriodic
	addPeriodicEvent(
		String				name,
		long				frequency,
		TimerEventPerformer	performer )
	{
		return( addPeriodicEvent( name, frequency, false, performer ));
	}

	public synchronized TimerEventPeriodic
	addPeriodicEvent(
		String				name,
		long				frequency,
		boolean				absolute,
		TimerEventPerformer	performer )
	{
		TimerEventPeriodic periodic_performer = new TimerEventPeriodic( this, frequency, absolute, performer );

		if ( name != null ){

			periodic_performer.setName( name );
		}

		if ( log ){

			System.out.println( "Timer '" + thread_pool.getName() + "' - added " + periodic_performer.getString());
		}

		return( periodic_performer );
	}

	protected synchronized void
	cancelEvent(
		TimerEvent	event )
	{
		if ( events.contains( event )){

			events.remove( event );

			// System.out.println( "event cancelled (" + event.getWhen() + ") - queue = " + events.size());

			notify();
		}
	}

	public synchronized void
	destroy()
	{
		if ( indestructable ){

			Debug.out( "Attempt to destroy indestructable timer '" + getName() + "'" );

		}else{

			destroyed	= true;

			notify();

			SystemTime.unregisterClockChangeListener( this );
		}

		if (DEBUG_TIMERS) {
			try {
				timers_mon.enter();
				// crappy
				for (Iterator<WeakReference<Timer>> iter = timers.iterator(); iter.hasNext();) {
					WeakReference<Timer> timerRef = iter.next();
					Object timer = timerRef.get();
					if (timer == null || timer == this) {
						iter.remove();
					}
				}
			} finally {
				timers_mon.exit();
			}
		}
	}

	public String
	getName()
	{
		return( thread_pool.getName());
	}

	public synchronized void
	dump()
	{
		System.out.println( "Timer '" + thread_pool.getName() + "': dump" );

		Iterator<TimerEvent>	it = events.iterator();

		while(it.hasNext()){

			TimerEvent	ev = it.next();

			System.out.println( "\t" + ev.getString());
		}
	}

	private static class
	evidenceGenerator implements AEDiagnosticsEvidenceGenerator
	{
		@Override
		public void generate(IndentWriter writer) {
			if (!DEBUG_TIMERS) {
				return;
			}

			ArrayList<String> lines = new ArrayList<>();
			int count = 0;
			try {
				try {
					timers_mon.enter();
					// crappy
					for (Iterator<WeakReference<Timer>> iter = timers.iterator(); iter.hasNext();) {
						
						WeakReference<Timer> timerRef = iter.next();
						Timer timer = timerRef.get();
						if (timer == null) {
							iter.remove();
						} else {
							count++;

							List<TimerEvent>	events = timer.getEvents();

							lines.add(timer.thread_pool.getName() + ", "
									+ events.size() + " events:");

							Iterator<TimerEvent> it = events.iterator();
							
							while (it.hasNext()){
								
								TimerEvent ev = it.next();

								lines.add("  " + ev.getString());
							}
						}
					}
				} finally {
					timers_mon.exit();
				}

				writer.println("Timers: " + count + " (time=" + SystemTime.getCurrentTime() + "/" + SystemTime.getMonotonousTime() + ")" );
				writer.indent();
				for (Iterator<String> iter = lines.iterator(); iter.hasNext();) {
					String line = iter.next();
					writer.println(line);
				}
				writer.exdent();
			} catch (Throwable e) {
				writer.println(e.toString());
			}
		}
	}
}
