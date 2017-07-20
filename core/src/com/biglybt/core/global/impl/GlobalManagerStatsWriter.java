/*
 * Created on 12-Jul-2004
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

package com.biglybt.core.global.impl;

import com.biglybt.core.Core;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.stats.StatsWriterFactory;
import com.biglybt.core.stats.StatsWriterPeriodic;
import com.biglybt.core.stats.transfer.StatsFactory;

/**
 * @author parg
 *
 */
public class
GlobalManagerStatsWriter
{
	protected final StatsWriterPeriodic	stats_writer;

	protected
	GlobalManagerStatsWriter(
		Core core,
		GlobalManagerStats		stats )
	{
	    StatsFactory.initialize( core, stats );

	    stats_writer = StatsWriterFactory.createPeriodicDumper( core );

	    core.addLifecycleListener(
	    	new CoreLifecycleAdapter()
	    	{
	    		@Override
			    public void
	    		started(
	    			Core core )
	    		{
	    			stats_writer.start();

	    			core.removeLifecycleListener( this );
	    		}
	    	});
	}


	protected void
	destroy()
	{
		if ( stats_writer != null ){

			stats_writer.stop();
		}
	}
}
