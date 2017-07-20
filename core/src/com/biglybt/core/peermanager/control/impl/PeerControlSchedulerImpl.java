/*
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
 */
package com.biglybt.core.peermanager.control.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.peermanager.control.PeerControlScheduler;
import com.biglybt.core.stats.CoreStats;
import com.biglybt.core.stats.CoreStatsProvider;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;

public abstract class
PeerControlSchedulerImpl
	implements PeerControlScheduler, CoreStatsProvider, ParameterListener
{
	private static final PeerControlSchedulerImpl[]	singletons;

	static{
		int	num = COConfigurationManager.getIntParameter( "peercontrol.scheduler.parallelism", 1 );

		if ( num < 1 ){

			num = 1;

		}else if ( num > 1 ){

			if ( COConfigurationManager.getBooleanParameter( "peercontrol.scheduler.use.priorities" )){

				Debug.out( "Multiple peer schedulers not supported for prioritised scheduling" );

				num = 1;

			}else{

				System.out.println( "Peer control scheduler parallelism=" + num );
			}
		}

		singletons = new PeerControlSchedulerImpl[ num ];
	}

	protected boolean useWeights = true;

	{
		COConfigurationManager.addAndFireParameterListener("Use Request Limiting Priorities", this);
	}

	@Override
	public void parameterChanged(String parameterName) {
		useWeights = COConfigurationManager.getBooleanParameter("Use Request Limiting Priorities");
	}

	static{

		for (int i=0;i<singletons.length;i++){

			PeerControlSchedulerImpl singleton;

			if ( COConfigurationManager.getBooleanParameter( "peercontrol.scheduler.use.priorities" )){

				singleton = new PeerControlSchedulerPrioritised();

			}else{

				singleton = new PeerControlSchedulerBasic();

			}

			singletons[i] = singleton;

			singleton.start();
		}
	}

	public static PeerControlScheduler
	getSingleton(
		int		id )
	{
		return( singletons[ id%singletons.length ]);
	}

	public static void
	overrideAllWeightedPriorities(
		boolean	b )
	{
		for ( PeerControlSchedulerImpl s: singletons ){

			s.overrideWeightedPriorities(b);
		}
	}

	public static void
	updateAllScheduleOrdering()
	{
		for ( PeerControlSchedulerImpl s: singletons ){

			s.updateScheduleOrdering();
		}
	}

	protected long	schedule_count;
	protected long	wait_count;
	protected long	yield_count;
	protected long	total_wait_time;

	protected
	PeerControlSchedulerImpl()
	{
		Set	types = new HashSet();

		types.add( CoreStats.ST_PEER_CONTROL_SCHEDULE_COUNT );
		types.add( CoreStats.ST_PEER_CONTROL_LOOP_COUNT );
		types.add( CoreStats.ST_PEER_CONTROL_YIELD_COUNT );
		types.add( CoreStats.ST_PEER_CONTROL_WAIT_COUNT );
		types.add( CoreStats.ST_PEER_CONTROL_WAIT_TIME );

		CoreStats.registerProvider( types, this );
	}

	protected void
	start()
	{
		new AEThread2( "PeerControlScheduler", true )
		{
			@Override
			public void
			run()
			{
				schedule();
			}

		}.start();
	}

	@Override
	public void
	updateStats(
		Set		types,
		Map		values )
	{
		if ( types.contains( CoreStats.ST_PEER_CONTROL_SCHEDULE_COUNT )){

			values.put( CoreStats.ST_PEER_CONTROL_SCHEDULE_COUNT, new Long( schedule_count ));
		}
		if ( types.contains( CoreStats.ST_PEER_CONTROL_LOOP_COUNT )){

			values.put( CoreStats.ST_PEER_CONTROL_LOOP_COUNT, new Long( wait_count + yield_count ));
		}
		if ( types.contains( CoreStats.ST_PEER_CONTROL_YIELD_COUNT )){

			values.put( CoreStats.ST_PEER_CONTROL_YIELD_COUNT, new Long( yield_count ));
		}
		if ( types.contains( CoreStats.ST_PEER_CONTROL_WAIT_COUNT )){

			values.put( CoreStats.ST_PEER_CONTROL_WAIT_COUNT, new Long( wait_count ));
		}
		if ( types.contains( CoreStats.ST_PEER_CONTROL_WAIT_TIME )){

			values.put( CoreStats.ST_PEER_CONTROL_WAIT_TIME, new Long( total_wait_time ));
		}
	}

	protected abstract void
	schedule();

	@Override
	public void overrideWeightedPriorities(boolean override) {
		if(override)
			useWeights = false;
		else
			parameterChanged(null);
	}
}
