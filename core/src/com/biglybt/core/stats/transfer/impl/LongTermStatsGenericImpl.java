/*
 * Created on Feb 1, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.core.stats.transfer.impl;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.*;


public class
LongTermStatsGenericImpl
	extends LongTermStatsBase
{
		// totals at start of session

	private final long[]	st;

		// session offsets at start of session

	private final long[]	ss;

	private final String				generic_id;
	private final GenericStatsSource	generic_source;

	public
	LongTermStatsGenericImpl(
		String				id,
		GenericStatsSource	source )
	{
		super( source.getEntryCount());
		
		generic_id		= id;
		generic_source	= source;

		ss = new long[STAT_ENTRY_COUNT];
		st = new long[STAT_ENTRY_COUNT];

		stats_dir	= FileUtil.getUserFile( "stats" );

		stats_dir	= FileUtil.newFile( stats_dir, "gen."  + id );

		sessionStart();

	    CoreFactory.getSingleton().addLifecycleListener(
	        	new CoreLifecycleAdapter()
	        	{
	        		@Override
			        public void
	        		stopped(
	        			Core core )
	        		{
	        			if ( destroyed ){

	        				core.removeLifecycleListener( this );

	        				return;
	        			}

	        			synchronized( LongTermStatsGenericImpl.this ){

	        				closing	= true;

	        				if ( active ){

	        					sessionEnd();
	        				}
	        			}
	        		}
	        	});
	}

	@Override
	protected void
	sessionStart()
	{
		synchronized( this ){

			if ( closing ){

				return;
			}

			boolean	enabled = COConfigurationManager.getBooleanParameter( "long.term.stats.enable" );

			if ( active || !enabled ){

				return;
			}

			active = true;

			long[] current = generic_source.getStats( generic_id );

			for ( int i=0;i<current.length;i++){

				ss[i] = current[i];

				st[i] = ss[i];
			}

			write( RT_SESSION_START, st );

			if ( event == null ){	// should always be null but hey ho

			    event =
			    	SimpleTimer.addPeriodicEvent(
				    	"LongTermStats:" + generic_id,
				    	MIN_IN_MILLIS,
				    	new TimerEventPerformer()
				    	{
				    		@Override
						    public void
				    		perform(TimerEvent event)
				    		{
				    			if ( destroyed ){

				    				event.cancel();

				    				return;
				    			}

				    			updateStats();
				    		}
				    	});
			}
		}
	}


	@Override
	protected void
	updateStats(
		int	record_type )
	{
		long[] current = generic_source.getStats( generic_id );

		long[]	diffs = new long[STAT_ENTRY_COUNT];

		for ( int i=0; i<STAT_ENTRY_COUNT;i++){

			diffs[i] = current[i] - ss[i];
		}

	    write( record_type, diffs );
	}

	@Override
	protected long[] 
	getNewFileSessionStats(
		long[]	line_stats )
	{
		for ( int i=0;i<STAT_ENTRY_COUNT;i++){
			
			st[i] += line_stats[i];
			ss[i] += line_stats[i];
		}

		return( st );
	}
}
