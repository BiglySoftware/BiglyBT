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
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;

public class
PeerControlSchedulerPrioritised
	extends PeerControlSchedulerImpl
	implements CoreStatsProvider
{
	private Map	instance_map = new HashMap();

	final List	pending_registrations = new ArrayList();

	private volatile boolean	registrations_changed;
	private volatile long		latest_time;

	protected final AEMonitor	this_mon = new AEMonitor( "PeerControlSchedulerPrioritised" );


	private final SpeedTokenDispenserPrioritised tokenDispenser = new SpeedTokenDispenserPrioritised();



	@Override
	protected void
	schedule()
	{
		latest_time	= SystemTime.getMonotonousTime();
		SystemTime.registerMonotonousConsumer(
			new SystemTime.TickConsumer()
			{
				@Override
				public void
				consume( long	time )
				{
					synchronized( PeerControlSchedulerPrioritised.this ){
						latest_time	= time;
						if ( instance_map.size() > 0 || pending_registrations.size() > 0 ){

							PeerControlSchedulerPrioritised.this.notify();
						}
					}
				}
			});


		ArrayList	instances = new ArrayList();

		long	latest_time_used	= 0;
		int scheduledNext = 0;
		long	currentScheduleStart = latest_time;
		long 	last_stats_time	= latest_time;

		while( true ){

			if ( registrations_changed ){
				try{
					this_mon.enter();
					Iterator	it = instances.iterator();
					while( it.hasNext()){
						if (((instanceWrapper)it.next()).isUnregistered()){
							it.remove();
						}
					}

					for (int i=0;i<pending_registrations.size();i++)
						instances.add( pending_registrations.get(i));

					pending_registrations.clear();

					// order instances by their priority (lowest number first)
					Collections.sort(instances);

					if(instances.size() > 0)
					{
						for(int i=0;i<instances.size();i++)
							((instanceWrapper)instances.get(i)).setScheduleOffset((SCHEDULE_PERIOD_MILLIS * i) / instances.size());
					}

					scheduledNext = 0;
					currentScheduleStart = latest_time;

					registrations_changed	= false;
				}finally{
					this_mon.exit();
				}
			}

			tokenDispenser.update(latest_time);

			for (int i = scheduledNext; i < instances.size(); i++)
			{
				instanceWrapper inst = (instanceWrapper) instances.get(i);
				if (currentScheduleStart + inst.getScheduleOffset() > latest_time_used)
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
					currentScheduleStart += SCHEDULE_PERIOD_MILLIS;
					// if tasks hog too much time then delay to prevent massive
					// catch-up-hammering
					if (latest_time_used - currentScheduleStart > SCHEDULE_PERIOD_MAX_CATCHUP )
						currentScheduleStart = latest_time_used + SCHEDULE_PERIOD_MILLIS;
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
				if ( latest_time == latest_time_used ){
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

				latest_time_used	= latest_time;
			}

			long	stats_diff =  latest_time_used - last_stats_time;

			if ( stats_diff > 10000 ){
				// System.out.println( "stats: time = " + stats_diff + ", ticks = " + tick_count + ", inst = " + instances.size());
				last_stats_time	= latest_time_used;
			}
		}
	}

	@Override
	public void
	register(
		PeerControlInstance	instance )
	{
		instanceWrapper wrapper = new instanceWrapper( instance );

		try{
			this_mon.enter();

			Map	new_map = new HashMap( instance_map );

			new_map.put( instance, wrapper );

			instance_map = new_map;

			pending_registrations.add( wrapper );

			registrations_changed = true;

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	unregister(
		PeerControlInstance	instance )
	{
		try{
			this_mon.enter();

			Map	new_map = new HashMap( instance_map );

			instanceWrapper wrapper = (instanceWrapper)new_map.remove(instance);

			if ( wrapper == null ){

				Debug.out( "instance wrapper not found" );

				return;
			}

			wrapper.unregister();

			instance_map = new_map;

			registrations_changed = true;

		}finally{

			this_mon.exit();
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

	protected static class
	instanceWrapper implements Comparable
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
		public int compareTo(Object o) {
			return instance.getSchedulePriority()-((instanceWrapper)o).instance.getSchedulePriority();
		}
	}
}
