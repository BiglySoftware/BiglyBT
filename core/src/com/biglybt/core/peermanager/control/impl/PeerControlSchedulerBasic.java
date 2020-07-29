/*
 * Created on Oct 23, 2007
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


package com.biglybt.core.peermanager.control.impl;

import java.util.*;

import com.biglybt.core.peermanager.control.PeerControlInstance;
import com.biglybt.core.peermanager.control.SpeedTokenDispenser;
import com.biglybt.core.stats.CoreStatsProvider;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;

public class
PeerControlSchedulerBasic
	extends PeerControlSchedulerImpl
	implements CoreStatsProvider
{
	private final Random	random = new Random();

	private Map<PeerControlInstance,instanceWrapper>	instance_map = new HashMap();

	private final List<instanceWrapper>	pending_registrations = new ArrayList<>();

	private volatile boolean	registrations_changed;

	private final Object instance_lock = new Object();
	
	private final SpeedTokenDispenserBasic tokenDispenser = new SpeedTokenDispenserBasic();

	private long	latest_time;
	private long	last_lag_log;

	private long	next_peer_count_time 	= SystemTime.getMonotonousTime();
	private long	next_piece_count_time	= SystemTime.getMonotonousTime();
	
	private volatile long		peer_count_active_time 	= 0;
	private volatile long		piece_count_active_time = 0;
	
	private volatile int		last_peer_count[] 	= { 0, 0 };
	private volatile int		last_piece_count[] 	= { 0, 0 };

	
	@Override
	protected void
	schedule()
	{
		SystemTime.registerMonotonousConsumer(
			new SystemTime.TickConsumer()
			{
				@Override
				public void
				consume(
					long	time )
				{
					boolean count_peers 	= false;
					boolean count_pieces 	= false;
					
					synchronized( PeerControlSchedulerBasic.this ){

						if ( peer_count_active_time > 0 ){
							
							if ( time >= next_peer_count_time ){
								
								if ( time - peer_count_active_time > 15*1000 ){
									
									peer_count_active_time = 0;
									
								}else{
									count_peers = true;
									
									next_peer_count_time = time+900;
								}
							}
						}
						
						if ( piece_count_active_time > 0 ){
							
							if ( time >= next_piece_count_time ){
								
								if ( time - piece_count_active_time > 15*1000 ){
									
									piece_count_active_time = 0;
									
								}else{
									
									count_pieces = true;
									
									next_piece_count_time = time+900;
								}
							}
						}
						PeerControlSchedulerBasic.this.notify();
					}
					
					if ( count_peers || count_pieces ){
						
						int peer_count1 = 0;
						int peer_count2 = 0;
						
						int piece_count1 = 0;
						int piece_count2 = 0;
						
						synchronized( instance_lock ){
							
							for ( PeerControlInstance i: instance_map.keySet()){
								
								if ( count_peers ){
									
									int[] c = i.getPeerCount();
									
									peer_count1 += c[0];
									peer_count2 += c[01];
								}
								
								if ( count_pieces ){
									
									int[] c = i.getPieceCount();
									
									piece_count1 += c[0];
									piece_count2 += c[01];
								}
							}
						}
						
						if ( count_peers ){
							last_peer_count = new int[]{ peer_count1, peer_count2 };
						}
						if ( count_pieces ){
							last_piece_count = new int[]{ piece_count1, piece_count2 };
						}
					}
				}
			});


		List<instanceWrapper>	instances = new LinkedList<>();

		long	tick_count		= 0;
		long 	last_stats_time	= SystemTime.getMonotonousTime();

		while( true ){

			if ( registrations_changed ){

				synchronized( instance_lock ){

					Iterator<instanceWrapper>	it = instances.iterator();

					while( it.hasNext()){

						if ( it.next().isUnregistered()){

							it.remove();
						}
					}

					instances.addAll(pending_registrations);

					pending_registrations.clear();

					registrations_changed	= false;
				}
			}

			latest_time	= SystemTime.getMonotonousTime();

			long current_schedule_count = schedule_count;

			for ( instanceWrapper inst: instances ){

				long	target = inst.getNextTick();

				long	diff = latest_time - target;

				if ( diff >= 0 ){

					tick_count++;

					inst.schedule( latest_time );

					schedule_count++;

					long new_target = target + SCHEDULE_PERIOD_MILLIS;

					if ( new_target <= latest_time ){

						new_target = latest_time + ( target % SCHEDULE_PERIOD_MILLIS );
					}

					inst.setNextTick( new_target );
				}
			}

			synchronized( this ){

				if ( current_schedule_count == schedule_count ){

					wait_count++;

					try{
						long wait_start = SystemTime.getHighPrecisionCounter();

						wait( SCHEDULE_PERIOD_MILLIS );

						long wait_time 	= SystemTime.getHighPrecisionCounter() - wait_start;

						total_wait_time += wait_time;

					}catch( Throwable e ){

						Debug.printStackTrace(e);
					}

				}else{

					yield_count++;

					Thread.yield();
				}
			}

			long	stats_diff =  latest_time - last_stats_time;

			if ( stats_diff > 10000 ){

				// System.out.println( "stats: time = " + stats_diff + ", ticks = " + tick_count + ", inst = " + instances.size());

				last_stats_time	= latest_time;

				tick_count	= 0;
			}
		}
	}

	@Override
	public void
	register(
		PeerControlInstance	instance )
	{
		instanceWrapper wrapper = new instanceWrapper( instance );

		wrapper.setNextTick( latest_time + random.nextInt( SCHEDULE_PERIOD_MILLIS ));

		synchronized( instance_lock ){

			Map<PeerControlInstance,instanceWrapper>	new_map = new HashMap<>(instance_map);

			new_map.put( instance, wrapper );

			instance_map = new_map;

			pending_registrations.add( wrapper );

			registrations_changed = true;
		}
	}

	@Override
	public void
	unregister(
		PeerControlInstance	instance )
	{
		synchronized( instance_lock ){

			Map<PeerControlInstance,instanceWrapper>	new_map = new HashMap<>(instance_map);

			instanceWrapper wrapper = new_map.remove(instance);

			if ( wrapper == null ){

				Debug.out( "instance wrapper not found" );

				return;
			}

			wrapper.unregister();

			instance_map = new_map;

			registrations_changed = true;
		}
	}

	@Override
	public SpeedTokenDispenser
	getSpeedTokenDispenser()
	{
		return( tokenDispenser );
	}

	@Override
	public void
	updateScheduleOrdering()
	{
	}

	@Override
	public int[] getPeerCount()
	{
		peer_count_active_time = SystemTime.getMonotonousTime();
		
		return( last_peer_count );
	}
	
	@Override
	public int[] getPieceCount()
	{
		piece_count_active_time = SystemTime.getMonotonousTime();
		
		return( last_piece_count );
	}

	protected class
	instanceWrapper
	{
		private final PeerControlInstance		instance;
		private boolean					unregistered;

		private long					next_tick;

		private long					last_schedule;

		protected
		instanceWrapper(
			PeerControlInstance	_instance )
		{
			instance = _instance;
		}

		protected void
		unregister()
		{
			unregistered	= true;
		}

		protected boolean
		isUnregistered()
		{
			return( unregistered );
		}

		protected void
		setNextTick(
			long	t )
		{
			next_tick	= t;
		}

		protected long
		getNextTick()
		{
			return( next_tick );
		}

		protected String
		getName()
		{
			return( instance.getName());
		}

		protected void
		schedule(
			long	mono_now )
		{
			if ( mono_now < 100000 ){

				Debug.out("eh?");
			}

			if ( last_schedule > 0 ){


				if ( mono_now - last_schedule > 1000 ){

					if ( mono_now - last_lag_log > 1000 ){

						last_lag_log = mono_now;

						System.out.println( "Scheduling lagging: " + (mono_now - last_schedule ) + " - instances=" + instance_map.size());
					}
				}
			}

			last_schedule = mono_now;

			try{
				instance.schedule();

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}
}
