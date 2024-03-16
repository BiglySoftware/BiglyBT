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
PeerControlSchedulerPrioritised
	extends PeerControlSchedulerImpl
	implements CoreStatsProvider
{
	private Map<PeerControlInstance,instanceWrapper>	instance_map = new HashMap<>();

	final List<instanceWrapper>	pending_registrations = new ArrayList<>();

	private volatile boolean	registrations_changed;
	private volatile long		latest_time_mono;

	private final Object	instance_lock = new Object();

	private final SpeedTokenDispenserPrioritised tokenDispenser = new SpeedTokenDispenserPrioritised();

	private long	next_peer_count_time_mono 	= SystemTime.getMonotonousTime();
	private long	next_piece_count_time_mono 	= SystemTime.getMonotonousTime();
	
	private volatile long		peer_count_active_time_mono		= 0;
	private volatile long		piece_count_active_time_mono	= 0;

	private volatile int[]		last_peer_count = { 0, 0 };
	private volatile int[]		last_piece_count = { 0, 0 };

	@Override
	protected void
	schedule()
	{
		latest_time_mono	= SystemTime.getMonotonousTime();
		
		SystemTime.registerMonotonousConsumer(
			new SystemTime.TickConsumer()
			{
				@Override
				public void
				consume( long	time_mono )
				{
					boolean count_peers 	= false;
					boolean count_pieces 	= false;
					
					synchronized( PeerControlSchedulerPrioritised.this ){
						
						if ( peer_count_active_time_mono > 0 ){
							
							if ( time_mono >= next_peer_count_time_mono ){
								
								if ( time_mono - peer_count_active_time_mono > 15*1000 ){
									
									peer_count_active_time_mono = 0;
									
								}else{
									count_peers = true;
									
									next_peer_count_time_mono = time_mono+900;
								}
							}
						}
						
						if ( piece_count_active_time_mono > 0 ){
							
							if ( time_mono >= next_piece_count_time_mono ){
								
								if ( time_mono - piece_count_active_time_mono > 15*1000 ){
									
									piece_count_active_time_mono = 0;
									
								}else{
									
									count_pieces = true;
									
									next_piece_count_time_mono = time_mono+900;
								}
							}
						}

						latest_time_mono	= time_mono;

						if ( instance_map.size() > 0 || pending_registrations.size() > 0 ){

							PeerControlSchedulerPrioritised.this.notify();
						}
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


		ArrayList<instanceWrapper>	instances = new ArrayList<>();

		long	latest_time_used_mono		= 0;
		int		scheduledNext				= 0;
		long	currentScheduleStartMono	= latest_time_mono;
		long 	last_stats_time_mono		= latest_time_mono;

		while( true ){

			if ( registrations_changed ){
				synchronized( instance_lock ){
					Iterator<instanceWrapper>	it = instances.iterator();
					while( it.hasNext()){
						if (it.next().isUnregistered()){
							it.remove();
						}
					}

					instances.addAll(pending_registrations);

					pending_registrations.clear();

					// order instances by their priority (lowest number first)
					Collections.sort(instances);

					if(instances.size() > 0)
					{
						for(int i=0;i<instances.size();i++)
							((instanceWrapper)instances.get(i)).setScheduleOffset((SCHEDULE_PERIOD_MILLIS * i) / instances.size());
					}

					scheduledNext = 0;
					currentScheduleStartMono = latest_time_mono;

					registrations_changed	= false;
				}
			}

			tokenDispenser.update(latest_time_mono);

			for (int i = scheduledNext; i < instances.size(); i++)
			{
				instanceWrapper inst = instances.get(i);
				if (currentScheduleStartMono + inst.getScheduleOffset() > latest_time_used_mono)
					break; // too early for next task, continue waiting
				if (i == 0 || !useWeights)
					tokenDispenser.refill();
				// System.out.println("scheduling "+i+" time:"+latest_time);
				inst.schedule();
				schedule_count++;
				scheduledNext++;
				if (scheduledNext >= instances.size())
				{
					scheduledNext = 0;
					// try to run every task every SCHEDULE_PERIOD_MILLIS on average
					currentScheduleStartMono += SCHEDULE_PERIOD_MILLIS;
					// if tasks hog too much time then delay to prevent massive
					// catch-up-hammering
					if (latest_time_used_mono - currentScheduleStartMono > SCHEDULE_PERIOD_MAX_CATCHUP )
						currentScheduleStartMono = latest_time_used_mono + SCHEDULE_PERIOD_MILLIS;
				}
			}



			/*
			for (Iterator it=instances.iterator();it.hasNext();){
				instanceWrapper	inst = (instanceWrapper)it.next();
				long	target = inst.getNextTick();
				long	diff = target - latest_time_used;

				if ( diff <= 0 || diff > SCHEDULE_PERIOD_MILLIS ){
					inst.schedule();
					long new_target = target + SCHEDULE_PERIOD_MILLIS;
					diff = new_target - latest_time_used;
					if ( diff <= 0 || diff > SCHEDULE_PERIOD_MILLIS )
						new_target = latest_time_used + SCHEDULE_PERIOD_MILLIS;
					inst.setNextTick( new_target );
				}
			}*/

			synchronized( this ){
				if ( latest_time_mono == latest_time_used_mono ){
					wait_count++;
					try{
						long wait_start = SystemTime.getHighPrecisionCounter();
						wait( 5000 );
						long wait_time 	= SystemTime.getHighPrecisionCounter() - wait_start;
						total_wait_time += wait_time;
					}catch( Throwable e ){
						Debug.printStackTrace(e);
					}

				}else{
					yield_count++;
					Thread.yield();
				}

				latest_time_used_mono	= latest_time_mono;
			}

			long	stats_diff =  latest_time_used_mono - last_stats_time_mono;

			if ( stats_diff > 10000 ){
				// System.out.println( "stats: time = " + stats_diff + ", ticks = " + tick_count + ", inst = " + instances.size());
				last_stats_time_mono	= latest_time_used_mono;
			}
		}
	}

	@Override
	public void
	register(
		PeerControlInstance	instance )
	{
		instanceWrapper wrapper = new instanceWrapper( instance );

		synchronized( instance_lock ){

			Map<PeerControlInstance,instanceWrapper>	new_map = new HashMap<>( instance_map );

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

			Map<PeerControlInstance,instanceWrapper>	new_map = new HashMap<>( instance_map );

			instanceWrapper wrapper = (instanceWrapper)new_map.remove(instance);

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
	public void updateScheduleOrdering() {
		registrations_changed = true;
	}
	
	@Override
	public int[] getPeerCount()
	{
		peer_count_active_time_mono = SystemTime.getMonotonousTime();

		return( last_peer_count );
	}
	
	@Override
	public int[] getPieceCount()
	{
		piece_count_active_time_mono = SystemTime.getMonotonousTime();
		
		return( last_piece_count );
	}
	
	protected static class
	instanceWrapper implements Comparable<instanceWrapper>
	{
		private final PeerControlInstance		instance;
		private boolean					unregistered;

		private long					offset;

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
		setScheduleOffset(
			long	t )
		{
			offset	= t;
		}

		protected long
		getScheduleOffset()
		{
			return( offset );
		}

		protected PeerControlInstance
		getInstance()
		{
			return( instance );
		}

		protected void
		schedule()
		{
			try{
				instance.schedule();

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		@Override
		public int compareTo(instanceWrapper o) {
			return instance.getSchedulePriority()-o.instance.getSchedulePriority();
		}
	}
}
