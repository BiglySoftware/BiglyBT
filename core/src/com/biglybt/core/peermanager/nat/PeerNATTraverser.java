/*
 * Created on 10 Jul 2006
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

package com.biglybt.core.peermanager.nat;

import java.net.InetSocketAddress;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.nat.NATTraversal;
import com.biglybt.core.nat.NATTraversalHandler;
import com.biglybt.core.nat.NATTraversalObserver;
import com.biglybt.core.nat.NATTraverser;
import com.biglybt.core.util.*;
import com.biglybt.core.util.bloom.BloomFilter;
import com.biglybt.core.util.bloom.BloomFilterFactory;

public class
PeerNATTraverser
	implements NATTraversalHandler
{
	private static final LogIDs LOGID = LogIDs.PEER;

	private static final int	OUTCOME_SUCCESS				= 0;
	private static final int	OUTCOME_FAILED_NO_REND		= 1;
	private static final int	OUTCOME_FAILED_OTHER		= 2;

	private static PeerNATTraverser		singleton;

	public static void
	initialise(
		Core core )
	{
		singleton	= new PeerNATTraverser( core );
	}

	public static PeerNATTraverser
	getSingleton()
	{
		return( singleton );
	}

	private static int MAX_ACTIVE_REQUESTS;

	static{
		COConfigurationManager.addAndFireParameterListener(
			"peer.nat.traversal.request.conc.max",
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String name )
				{
					MAX_ACTIVE_REQUESTS = COConfigurationManager.getIntParameter( name );
				}
			});
	}

	private static final int TIMER_PERIOD			= 10*1000;
	private static final int USAGE_PERIOD			= TIMER_PERIOD;
	private static final int USAGE_DURATION_SECS	= 60;
	private static final int MAX_USAGE_PER_MIN		= MAX_ACTIVE_REQUESTS*5*1000;

	private static final int STATS_TICK_COUNT		= 120*1000 / TIMER_PERIOD;

	final NATTraverser	nat_traverser;

	final Map			initiators			= new HashMap();
	final LinkedList	pending_requests 	= new LinkedList();
	final List		active_requests		= new ArrayList();

	final Average	usage_average 		= Average.getInstance(USAGE_PERIOD,USAGE_DURATION_SECS);

	private int		attempted_count			= 0;
	private int		success_count			= 0;
	private int		failed_no_rendezvous	= 0;
	private int		failed_negative_bloom	= 0;

	private BloomFilter	negative_result_bloom = BloomFilterFactory.createAddOnly( BLOOM_SIZE );

	private static final int BLOOM_SIZE				= MAX_ACTIVE_REQUESTS*1024;
	private static final int BLOOM_REBUILD_PERIOD	= 5*60*1000;
	private static final int BLOOM_REBUILD_TICKS	= BLOOM_REBUILD_PERIOD/TIMER_PERIOD;

	private
	PeerNATTraverser(
		Core core )
	{
		nat_traverser = core.getNATTraverser();

		nat_traverser.registerHandler( this );

		SimpleTimer.addPeriodicEvent(
			"PeerNAT:stats",
			TIMER_PERIOD,
			new TimerEventPerformer()
			{
				private int	ticks;

				@Override
				public void
				perform(
					TimerEvent	event )
				{
					ticks++;

					List	to_run = null;

					synchronized( initiators ){

						if ( ticks % BLOOM_REBUILD_TICKS == 0 ){

							int	size = negative_result_bloom.getEntryCount();

					      	if (Logger.isEnabled()){

					      		if ( size > 0 ){

					      			Logger.log(	new LogEvent(LOGID,	"PeerNATTraverser: negative bloom size = " + size ));
					      		}
				          	}

					      	negative_result_bloom = BloomFilterFactory.createAddOnly( BLOOM_SIZE );
						}

						if ( ticks % STATS_TICK_COUNT == 0 ){

							String	msg =
								"NAT traversal stats: active=" +  active_requests.size() +
								",pending=" + pending_requests.size() + ",attempted=" + attempted_count +
								",no rendezvous=" + failed_no_rendezvous + ",negative bloom=" + failed_negative_bloom +
								",successful=" + success_count;

							// System.out.println( msg );

					      	if (Logger.isEnabled()){
								Logger.log(	new LogEvent(LOGID,	msg ));
				          	}
						}

						int	used = 0;

						for (int i=0;i<active_requests.size();i++){

							used += ((PeerNATTraversal)active_requests.get(i)).getTimeUsed();
						}

						usage_average.addValue( used );

						int	usage = (int)usage_average.getAverage();

						if ( usage > MAX_USAGE_PER_MIN ){

							return;
						}

						// System.out.println( "usage = " + usage );

						while( true ){

							if ( 	pending_requests.size() == 0 ||
									active_requests.size() >= MAX_ACTIVE_REQUESTS ){

								break;
							}

								// TODO: prioritisation based on initiator connections etc?

							PeerNATTraversal traversal = (PeerNATTraversal)pending_requests.removeFirst();

							active_requests.add( traversal );

							if ( to_run == null ){

								to_run = new ArrayList();
							}

							to_run.add( traversal );

							attempted_count++;
						}
					}

					if ( to_run != null ){

						for (int i=0;i<to_run.size();i++){

							PeerNATTraversal	traversal = (PeerNATTraversal)to_run.get(i);

							boolean	bad = false;

							synchronized( initiators ){

								if ( negative_result_bloom.contains( traversal.getTarget().toString().getBytes())){

									bad = true;

									failed_negative_bloom++;
								}
							}

							if ( bad ){

								removeRequest( traversal, OUTCOME_FAILED_OTHER );

								traversal.getAdapter().failed();
							}else{

								traversal.run();
							}
						}
					}
				}
			});
	}

	@Override
	public int
	getType()
	{
		return(  NATTraverser.TRAVERSE_REASON_PEER_DATA );
	}

	@Override
	public String
	getName()
	{
		return( "Peer Traversal" );
	}

	public void
	register(
		PeerNATInitiator	initiator )
	{
		synchronized( initiators ){

			if ( initiators.put( initiator, new LinkedList()) != null ){

				Debug.out( "initiator already present" );
			}
		}
	}

	public void
	unregister(
		PeerNATInitiator	initiator )
	{
		List	to_cancel;

		synchronized( initiators ){

			LinkedList	requests = (LinkedList)initiators.remove( initiator );

			if ( requests == null ){

				Debug.out( "initiator not present" );

				return;

			}else{

				to_cancel = requests;
			}
		}

		Iterator it = to_cancel.iterator();

		while( it.hasNext()){

			PeerNATTraversal	traversal = (PeerNATTraversal)it.next();

			traversal.cancel();
		}
	}

	public void
	create(
		PeerNATInitiator		initiator,
		InetSocketAddress		target,
		PeerNATTraversalAdapter	adapter )
	{
		boolean	bad = false;

		synchronized( initiators ){

			if ( negative_result_bloom.contains( target.toString().getBytes() )){

				bad	= true;

				failed_negative_bloom++;

			}else{

				if ( AERunStateHandler.isDHTSleeping()){
					
					bad = true;
					
				}else{
					
					LinkedList	requests = (LinkedList)initiators.get( initiator );
	
					if ( requests == null ){
	
						// we get here when download stopped at same time
						// Debug.out( "initiator not found" );
	
						bad	= true;
	
					}else{
	
						PeerNATTraversal	traversal = new PeerNATTraversal( initiator, target, adapter );
	
						requests.addLast( traversal );
	
						pending_requests.addLast( traversal );
	
			          	if (Logger.isEnabled()){
							Logger.log(
								new LogEvent(
									LOGID,
									"created NAT traversal for " + initiator.getDisplayName() + "/" + target ));
			          	}
					}
				}
			}
		}

		if ( bad ){

			adapter.failed();
		}
	}

	public List
	getTraversals(
		PeerNATInitiator	initiator )
	{
		List result = new ArrayList();

		synchronized( initiators ){

			LinkedList	requests = (LinkedList)initiators.get( initiator );

			if ( requests != null ){

				Iterator it = requests.iterator();

				while( it.hasNext()){

					PeerNATTraversal	x = (PeerNATTraversal)it.next();

					result.add( x.getTarget());
				}
			}
		}

		return( result );
	}

	protected void
	removeRequest(
		PeerNATTraversal		request,
		int						outcome )
	{
		synchronized( initiators ){

			LinkedList	requests = (LinkedList)initiators.get( request.getInitiator());

			if ( requests != null ){

				requests.remove( request );
			}

			pending_requests.remove( request );

			if ( active_requests.remove( request )){

				usage_average.addValue( request.getTimeUsed());

				if ( outcome == OUTCOME_SUCCESS ){

					success_count++;

				}else{

					InetSocketAddress	target = request.getTarget();

					negative_result_bloom.add( target.toString().getBytes());

					if ( outcome == OUTCOME_FAILED_NO_REND ){

						failed_no_rendezvous++;
					}
				}
			}
		}
	}

	@Override
	public Map
	process(
		InetSocketAddress	originator,
		Map					data )
	{
		// System.out.println( "PeerNAT: received traversal from " + originator );

		return( null );
	}

	protected class
	PeerNATTraversal
		implements NATTraversalObserver
	{
		private final PeerNATInitiator		initiator;
		private final InetSocketAddress		target;
		private final PeerNATTraversalAdapter	adapter;

		private NATTraversal	traversal;
		private boolean			cancelled;

		private long			time;

		protected
		PeerNATTraversal(
			PeerNATInitiator		_initiator,
			InetSocketAddress		_target,
			PeerNATTraversalAdapter	_adapter )
		{
			initiator	= _initiator;
			target		= _target;
			adapter		= _adapter;
		}

		protected PeerNATInitiator
		getInitiator()
		{
			return( initiator );
		}

		protected InetSocketAddress
		getTarget()
		{
			return( target );
		}

		protected PeerNATTraversalAdapter
		getAdapter()
		{
			return( adapter );
		}

		protected long
		getTimeUsed()
		{
			long now = SystemTime.getCurrentTime();

			long elapsed = now - time;

			time = now;

			if ( elapsed < 0 ){

				elapsed = 0;

			}else{
					// sanity check

				elapsed = Math.min( elapsed, TIMER_PERIOD );
			}

			return( elapsed );
		}

		protected void
		run()
		{
			synchronized( this ){

				if ( !cancelled ){

					time = SystemTime.getCurrentTime();

					traversal =
						nat_traverser.attemptTraversal(
							PeerNATTraverser.this,
							target,
							null,
							false,
							this );
				}
			}
		}

		@Override
		public void
		succeeded(
			InetSocketAddress	rendezvous,
			InetSocketAddress	target,
			Map					reply )
		{
			removeRequest( this, OUTCOME_SUCCESS );

        	if (Logger.isEnabled()){
				Logger.log(
					new LogEvent(
						LOGID,
						"NAT traversal for " + initiator.getDisplayName() + "/" + target + " succeeded" ));
          	}

			adapter.success( target );
		}


		@Override
		public void
		failed(
			int			reason )
		{
			removeRequest( this, reason == NATTraversalObserver.FT_NO_RENDEZVOUS?OUTCOME_FAILED_NO_REND:OUTCOME_FAILED_OTHER );

			adapter.failed();
		}

		@Override
		public void
		failed(
			Throwable 	cause )
		{
			removeRequest( this, OUTCOME_FAILED_OTHER );

			adapter.failed();
		}

		@Override
		public void
		disabled()
		{
			removeRequest( this, OUTCOME_FAILED_OTHER );

			adapter.failed();
		}

		protected void
		cancel()
		{
			NATTraversal active_traversal;

			synchronized( this ){

				cancelled = true;

				active_traversal = traversal;
			}

			if ( active_traversal == null ){

				removeRequest( this, OUTCOME_FAILED_OTHER );

			}else{

				active_traversal.cancel();
			}

			adapter.failed();
		}
	}
}
