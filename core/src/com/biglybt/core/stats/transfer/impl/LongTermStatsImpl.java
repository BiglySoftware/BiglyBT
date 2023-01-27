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
import com.biglybt.core.CoreComponent;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.transport.DHTTransportStats;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.stats.transfer.StatsFactory;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.plugin.dht.DHTPlugin;

public class
LongTermStatsImpl
	extends LongTermStatsBase
{
	private Core 				core;
	private GlobalManagerStats	gm_stats;
	private DHT[]				dhts;

		// totals at start of session

	private long	st_p_sent;
	private long	st_d_sent;
	private long	st_p_received;
	private long	st_d_received;
	private long	st_dht_sent;
	private long	st_dht_received;

		// session offsets at start of session

	private long	ss_p_sent;
	private long	ss_d_sent;
	private long	ss_p_received;
	private long	ss_d_received;
	private long	ss_dht_sent;
	private long	ss_dht_received;

	public
	LongTermStatsImpl(
		Core _core,
		GlobalManagerStats	_gm_stats )
	{
		super( 6 );
		
		core		= _core;
		gm_stats 	= _gm_stats;

		stats_dir		= FileUtil.getUserFile( "stats" );

	    _core.addLifecycleListener(
	        	new CoreLifecycleAdapter()
	        	{
	        		@Override
			        public void
	        		componentCreated(
	        			Core core,
	        			CoreComponent component )
	        		{
	        			if ( destroyed ){

	        				core.removeLifecycleListener( this );

	        				return;
	        			}

	        			if ( component instanceof GlobalManager ){

	        				synchronized( LongTermStatsImpl.this ){

	        					sessionStart();
	        				}
	        			}
	        		}

	        		@Override
			        public void
	        		stopped(
	        			Core core )
	        		{
	        			if ( destroyed ){

	        				core.removeLifecycleListener( this );

	        				return;
	        			}

	        			synchronized( LongTermStatsImpl.this ){

	        				closing	= true;

	        				if ( active ){

	        					sessionEnd();
	        				}
	        			}
	        		}
	        	});
	}

	private DHT[]
	getDHTs()
	{
	    if ( dhts == null ){

		    try{
		    	PluginManager pm = core.getPluginManager();

		    	if ( pm.isInitialized()){

			        PluginInterface dht_pi = pm.getPluginInterfaceByClass( DHTPlugin.class );

			        if ( dht_pi == null ){

			        	dhts = new DHT[0];

			        }else{

			        	DHTPlugin plugin = (DHTPlugin)dht_pi.getPlugin();

			        	if ( !plugin.isInitialising()){

				        	if ( plugin.isEnabled()){

				        		dhts = ((DHTPlugin)dht_pi.getPlugin()).getDHTs();

				        	}else{

				        		dhts = new DHT[0];
				        	}
			        	}
			        }
		    	}
		    }catch( Throwable e ){

		    	dhts = new DHT[0];
		    }
	    }

	    return( dhts );
	}

	@Override
	protected void
	sessionStart()
	{
		OverallStatsImpl stats = (OverallStatsImpl)StatsFactory.getStats();

		synchronized( this ){

			if ( closing ){

				return;
			}

			boolean	enabled = COConfigurationManager.getBooleanParameter( "long.term.stats.enable" );

			if ( active || !enabled ){

				return;
			}

			active = true;

			long[] snap = stats.getLastSnapshot();

			ss_d_received 	= gm_stats.getTotalDataBytesReceived();
			ss_p_received 	= gm_stats.getTotalProtocolBytesReceived();

			ss_d_sent		= gm_stats.getTotalDataBytesSent();
			ss_p_sent		= gm_stats.getTotalProtocolBytesSent();

			ss_dht_sent		= 0;
			ss_dht_received	= 0;

			if ( core.isStarted()){

				DHT[]	dhts = getDHTs();

				if ( dhts != null ){

					for ( DHT dht: dhts ){

				    	DHTTransportStats dht_stats = dht.getTransport().getStats();

				    	ss_dht_sent 		+= dht_stats.getBytesSent();
				    	ss_dht_received 	+= dht_stats.getBytesReceived();
					}
				}
			}

			st_p_sent 		= snap[0] + ( ss_p_sent - snap[6]);
			st_d_sent 		= snap[1] + ( ss_d_sent - snap[7]);
			st_p_received 	= snap[2] + ( ss_p_received - snap[8]);
			st_d_received 	= snap[3] + ( ss_d_received - snap[9]);
			st_dht_sent		= snap[4] + ( ss_dht_sent - snap[10]);
			st_dht_received = snap[5] + ( ss_dht_received - snap[11]);

			write( 	RT_SESSION_START,
					new long[]{
						st_p_sent,
						st_d_sent,
						st_p_received,
						st_d_received,
						st_dht_sent,
						st_dht_received });

			if ( event == null ){	// should always be null but hey ho

			    event =
			    	SimpleTimer.addPeriodicEvent(
				    	"LongTermStats",
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
	    long	current_d_received 	= gm_stats.getTotalDataBytesReceived();
	    long	current_p_received 	= gm_stats.getTotalProtocolBytesReceived();

	    long	current_d_sent		= gm_stats.getTotalDataBytesSent();
	    long	current_p_sent		= gm_stats.getTotalProtocolBytesSent();

		long	current_dht_sent		= 0;
		long	current_dht_received	= 0;

		DHT[]	dhts = getDHTs();

		if ( dhts != null ){

			for ( DHT dht: dhts ){

		    	DHTTransportStats dht_stats = dht.getTransport().getStats();

		    	current_dht_sent 		+= dht_stats.getBytesSent();
		    	current_dht_received 	+= dht_stats.getBytesReceived();
			}
		}

	    write(	record_type,
	    		new long[]{
		    		( current_p_sent - ss_p_sent ),
		    		( current_d_sent - ss_d_sent ),
		    		( current_p_received - ss_p_received ),
		    		( current_d_received - ss_d_received ),
		    		( current_dht_sent - ss_dht_sent ),
		    		( current_dht_received - ss_dht_received )});
	}
	
	@Override
	protected long[] 
	getNewFileSessionStats(
		long[]	line_stats )
	{
	
		st_p_sent		+= line_stats[0];
		st_d_sent		+= line_stats[1];
		st_p_received	+= line_stats[2];
		st_d_received	+= line_stats[3];
		st_dht_sent		+= line_stats[4];
		st_dht_received	+= line_stats[5];
	
		ss_p_sent		+= line_stats[0];
		ss_d_sent		+= line_stats[1];
		ss_p_received	+= line_stats[2];
		ss_d_received	+= line_stats[3];
		ss_dht_sent		+= line_stats[4];
		ss_dht_received	+= line_stats[5];
	
		long[] st_stats =  new long[]{ st_p_sent,st_d_sent, st_p_received,st_d_received, st_dht_sent, st_dht_received };

		return( st_stats );
	}
}
