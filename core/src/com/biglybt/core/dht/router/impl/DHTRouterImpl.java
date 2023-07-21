/*
 * Created on 11-Jan-2005
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

package com.biglybt.core.dht.router.impl;

import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.dht.DHTLogger;
import com.biglybt.core.dht.impl.DHTLog;
import com.biglybt.core.dht.router.*;
import com.biglybt.core.util.*;
import com.biglybt.core.util.bloom.BloomFilter;
import com.biglybt.core.util.bloom.BloomFilterFactory;

/**
 * @author parg
 *
 */

public class
DHTRouterImpl
	implements DHTRouter
{
	private static final int	SMALLEST_SUBTREE_MAX_EXCESS	= 10*1024;

	private boolean		is_bootstrap_proxy;

	private int		K;
	private int		B;
	private int		max_rep_per_node;

	private DHTLogger		logger;

	private int		smallest_subtree_max;

	private DHTRouterAdapter		adapter;

	private DHTRouterContactImpl	local_contact;
	private byte[]					router_node_id;

	private DHTRouterNodeImpl		root;
	private DHTRouterNodeImpl		smallest_subtree;

	private int						consecutive_dead;

	private static long				random_seed	= SystemTime.getCurrentTime();
	private Random					random;

	private List<DHTRouterContactImpl>					outstanding_pings	= new ArrayList<>();
	private List<DHTRouterContactImpl>					outstanding_adds	= new ArrayList<>();

	private final DHTRouterStatsImpl		stats	= new DHTRouterStatsImpl( this );

	private final AEMonitor	this_mon	= new AEMonitor( "DHTRouter" );

	private static final AEMonitor	class_mon	= new AEMonitor( "DHTRouter:class" );

	private final CopyOnWriteList<DHTRouterObserver>	observers = new CopyOnWriteList<>();

	private boolean	sleeping;
	boolean	suspended;

	private final BloomFilter recent_contact_bloom =
		BloomFilterFactory.createRotating(
			BloomFilterFactory.createAddOnly(10*1024),
			2 );

	private TimerEventPeriodic	timer_event;

	volatile int seed_in_ticks;

	private static final int	TICK_PERIOD 		= 10*1000;
	private static final int	SEED_DELAY_PERIOD	= 60*1000;
	private static final int	SEED_DELAY_TICKS	= SEED_DELAY_PERIOD/TICK_PERIOD;


	public
	DHTRouterImpl(
		int										_K,
		int										_B,
		int										_max_rep_per_node,
		byte[]									_router_node_id,
		DHTRouterContactAttachment				_attachment,
		DHTLogger								_logger )
	{
		try{
				// only needed for in-process multi-router testing :P

			class_mon.enter();

			random = new Random( random_seed++ );

		}finally{

			class_mon.exit();
		}

		is_bootstrap_proxy = COConfigurationManager.getBooleanParameter( "dht.bootstrap.is.proxy", false );

		K					= _K;
		B					= _B;
		max_rep_per_node	= _max_rep_per_node;
		logger				= _logger;


		smallest_subtree_max	= 1;

		for (int i=0;i<B;i++){

			smallest_subtree_max	*= 2;
		}

		smallest_subtree_max	+= SMALLEST_SUBTREE_MAX_EXCESS;

		router_node_id	= _router_node_id;

		List	buckets = new ArrayList();

		local_contact = new DHTRouterContactImpl( router_node_id, _attachment, true );

		buckets.add( local_contact );

		root	= new DHTRouterNodeImpl( this, 0, true, buckets );

		timer_event = SimpleTimer.addPeriodicEvent(
			"DHTRouter:pinger",
			TICK_PERIOD,
			new TimerEventPerformer()
			{
				@Override
				public void
				perform(
					TimerEvent event )
				{
					if ( suspended ){

						return;
					}

					pingeroonies();

					if ( seed_in_ticks > 0 ){

						seed_in_ticks--;

						if ( seed_in_ticks == 0 ){

							AEThread2.createAndStartDaemon( "router:seed", ()->{
								seedSupport();
							});
						}
					}
				}
			});
	}

	protected void notifyAdded(DHTRouterContact contact) {
		for (Iterator<DHTRouterObserver> i = observers.iterator(); i.hasNext(); ) {
			DHTRouterObserver rto = i.next();
			try{
				rto.added(contact);
			}catch( Throwable e ){
				Debug.printStackTrace(e);
			}
		}
	}

	protected void notifyRemoved(DHTRouterContact contact) {
		for (Iterator<DHTRouterObserver> i = observers.iterator(); i.hasNext(); ) {
			DHTRouterObserver rto = i.next();
			try{
				rto.removed(contact);
			}catch( Throwable e ){
				Debug.printStackTrace(e);
			}
		}
	}

	protected void notifyLocationChanged(DHTRouterContact contact) {
		for (Iterator<DHTRouterObserver> i = observers.iterator(); i.hasNext(); ) {
			DHTRouterObserver rto = i.next();
			try{
				rto.locationChanged(contact);
			}catch( Throwable e ){
				Debug.printStackTrace(e);
			}
		}
	}

	protected void notifyNowAlive(DHTRouterContact contact) {
		for (Iterator<DHTRouterObserver> i = observers.iterator(); i.hasNext(); ) {
			DHTRouterObserver rto = i.next();
			try{
				rto.nowAlive(contact);
			}catch( Throwable e ){
				Debug.printStackTrace(e);
			}
		}
	}

	protected void notifyNowFailing(DHTRouterContact contact) {
		for (Iterator<DHTRouterObserver> i = observers.iterator(); i.hasNext(); ) {
			DHTRouterObserver rto = i.next();
			try{
				rto.nowFailing(contact);
			}catch( Throwable e ){
				Debug.printStackTrace(e);
			}
		}
	}

	protected void notifyDead() {
		for (Iterator<DHTRouterObserver> i = observers.iterator(); i.hasNext(); ) {
			DHTRouterObserver rto = i.next();
			try{
				rto.destroyed(this);
			}catch( Throwable e ){
				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public boolean addObserver(DHTRouterObserver rto) {
		if ((rto != null) && !observers.contains(rto)) {
			observers.add(rto);
			return true;
		}
		return false;
	}

	@Override
	public boolean containsObserver(DHTRouterObserver rto) {
		return ((rto != null) && observers.contains(rto));
	}

	@Override
	public boolean removeObserver(DHTRouterObserver rto) {
		return ((rto != null) && observers.remove(rto));
	}

	@Override
	public DHTRouterStats
	getStats()
	{
		return( stats );
	}

	@Override
	public int
	getK()
	{
		return( K );
	}


	@Override
	public byte[]
	getID()
	{
		return( router_node_id );
	}

	@Override
	public boolean
	isID(
		byte[]	id )
	{
		return( Arrays.equals( id, router_node_id ));
	}

	@Override
	public DHTRouterContact
	getLocalContact()
	{
		return( local_contact );
	}

	@Override
	public void
	setAdapter(
		DHTRouterAdapter	_adapter )
	{
		adapter	= _adapter;
	}

	@Override
	public void
	setSleeping(
		boolean	_sleeping )
	{
		sleeping = _sleeping;
	}

	@Override
	public void
	setSuspended(
		boolean			_suspended )
	{
		suspended = _suspended;

		if ( !suspended ){

			seed_in_ticks = 1;
		}
	}

	@Override
	public void
	contactKnown(
		byte[]						node_id,
		DHTRouterContactAttachment	attachment,
		boolean						force )
	{
			// especially for small DHTs we don't want to prevent a contact from being re-added as long as they've been away for
			// a bit

		if ( SystemTime.getMonotonousTime() - recent_contact_bloom.getStartTimeMono() > 10*60*1000 ){

			recent_contact_bloom.clear();
		}

		if ( recent_contact_bloom.contains( node_id )){

			if ( !force ){

				return;
			}
		}

		recent_contact_bloom.add( node_id );

		addContact( node_id, attachment, false );
	}

	@Override
	public void
	contactAlive(
		byte[]						node_id,
		DHTRouterContactAttachment	attachment )
	{
		addContact( node_id, attachment, true );
	}

	// all incoming node actions come through either contactDead or addContact
	// A side effect of processing
	// the node is that either a ping can be requested (if a replacement node
	// is available and the router wants to check the liveness of an existing node)
	// or a new node can be added (either directly to a node or indirectly via
	// a replacement becoming "live"
	// To avoid requesting these actions while synchronised these are recorded
	// in lists and then kicked off separately here


	@Override
	public DHTRouterContact
	contactDead(
		byte[]						node_id,
		boolean						force )
	{
		if ( suspended ){

			return( null );
		}

		if ( Arrays.equals( router_node_id, node_id )){

				// we should never become dead ourselves as this screws up things like
				// checking that stored values are close enough to the K livest nodes (as if we are
				// dead we don't return ourselves and it all goes doo daa )

			Debug.out( "DHTRouter: contactDead called on router node!" );

			return( local_contact );
		}

		try{
			try{
				this_mon.enter();

				consecutive_dead++;

				/*
				if ( consecutive_dead != 0 && consecutive_dead % 10 == 0 ){

					System.out.println( "consecutive_dead: " + consecutive_dead );
				}
				*/

				Object[]	res = findContactSupport( node_id );

				DHTRouterNodeImpl		node	= (DHTRouterNodeImpl)res[0];
				DHTRouterContactImpl	contact = (DHTRouterContactImpl)res[1];

				if ( contact != null ){

					// some protection against network drop outs - start ignoring dead
					// notifications if we're getting significant continuous fails

					if ( consecutive_dead < 100 || force ){

						contactDeadSupport( node, contact, force );
					}
				}

				return( contact );

			}finally{

				this_mon.exit();
			}
		}finally{

			dispatchPings();

			dispatchNodeAdds();
		}
	}

	private void
	contactDeadSupport(
		DHTRouterNodeImpl		node,
		DHTRouterContactImpl	contact,
		boolean					force )
	{
			// bootstrap proxy has no network so we can't detect liveness of contacts. simply allow replacement of bucket
			// entries when possible to rotate somewhat

		if ( is_bootstrap_proxy ){

			List<DHTRouterContactImpl> replacements = node.getReplacements();

			if ( replacements == null || replacements.size() == 0 ){

				return;
			}
		}

		node.dead( contact, force );
	}

	public void
	contactRemoved(
		byte[]						node_id )
	{

	}

	public void
	addContact(
		byte[]						node_id,
		DHTRouterContactAttachment	attachment,
		boolean						known_to_be_alive )
	{
		if ( attachment.isSleeping()){

				// sleeping nodes are removed from the router as they're not generally
				// available for doing stuff

			if ( Arrays.equals( router_node_id, node_id )){

				return;
			}

			try{
				this_mon.enter();

				Object[]	res = findContactSupport( node_id );

				DHTRouterNodeImpl		node	= (DHTRouterNodeImpl)res[0];
				DHTRouterContactImpl	contact = (DHTRouterContactImpl)res[1];

				if ( contact != null ){

					contactDeadSupport( node, contact, true );
				}
			}finally{

				this_mon.exit();
			}

			return;
		}

		try{
			try{

				this_mon.enter();

				if ( known_to_be_alive ){

					consecutive_dead	= 0;
				}

				addContactSupport( node_id, attachment, known_to_be_alive );

			}finally{

				this_mon.exit();
			}
		}finally{

			dispatchPings();

			dispatchNodeAdds();
		}
	}

	private DHTRouterContact
	addContactSupport(
		byte[]						node_id,
		DHTRouterContactAttachment	attachment,
		boolean						known_to_be_alive )
	{
		if ( Arrays.equals( router_node_id, node_id )){

				// as we have reduced node id space the chance of us sharing a node id is higher. Easiest way to handle this is
				// just to bail out here

			return( local_contact );
		}

		DHTRouterNodeImpl	current_node = root;

		boolean	part_of_smallest_subtree	= false;

		for (int i=0;i<node_id.length;i++){

			byte	b = node_id[i];

			int	j = 7;

			while( j >= 0 ){

				if ( current_node == smallest_subtree ){

					part_of_smallest_subtree	= true;
				}

				boolean	bit = ((b>>j)&0x01)==1?true:false;

				DHTRouterNodeImpl	next_node;

				if ( bit ){

					next_node = current_node.getLeft();

				}else{

					next_node = current_node.getRight();
				}

				if ( next_node == null ){

					DHTRouterContact	existing_contact = current_node.updateExistingNode( node_id, attachment, known_to_be_alive );

					if ( existing_contact != null ){

						return( existing_contact );
					}

					List	buckets = current_node.getBuckets();

					int	buckets_size = buckets.size();

					if ( sleeping && buckets_size >= K/4 && !current_node.containsRouterNodeID()){

							// keep non-important buckets less full when sleeping

						DHTRouterContactImpl new_contact = new DHTRouterContactImpl( node_id, attachment, known_to_be_alive );

						return( current_node.addReplacement( new_contact, 1 ));

					}else if ( buckets_size == K ){

							// split if either
							// 1) this list contains router_node_id or
							// 2) depth % B is not 0
							// 3) this is part of the smallest subtree

						boolean	contains_router_node_id = current_node.containsRouterNodeID();
						int		depth					= current_node.getDepth();

						boolean	too_deep_to_split = depth % B == 0;	// note this will be true for 0 but other
																	// conditions will allow the split

						if ( 	contains_router_node_id ||
								(!too_deep_to_split)	||
								part_of_smallest_subtree ){

								// the smallest-subtree bit is to ensure that we remember all of
								// our closest neighbours as ultimately they are the ones responsible
								// for returning our identity to queries (due to binary choppery in
								// general the query will home in on our neighbours before
								// hitting us. It is therefore important that we keep ourselves live
								// in their tree by refreshing. If we blindly chopped at K entries
								// (down to B levels) then a highly unbalanced tree would result in
								// us dropping some of them and therefore not refreshing them and
								// therefore dropping out of their trees. There are also other benefits
								// of maintaining this tree regarding stored value refresh

								// Note that it is rare for such an unbalanced tree.
								// However, a possible DOS here would be for a rogue node to
								// deliberately try and create such a tree with a large number
								// of entries.

							if ( 	part_of_smallest_subtree &&
									too_deep_to_split &&
									( !contains_router_node_id ) &&
									getContactCount( smallest_subtree ) > smallest_subtree_max ){

								Debug.out( "DHTRouter: smallest subtree max size violation" );

								return( null );
							}

								// split!!!!

							List	left_buckets 	= new ArrayList();
							List	right_buckets 	= new ArrayList();

							for (int k=0;k<buckets.size();k++){

								DHTRouterContactImpl	contact = (DHTRouterContactImpl)buckets.get(k);

								byte[]	bucket_id = contact.getID();

								if (((bucket_id[depth/8]>>(7-(depth%8)))&0x01 ) == 0 ){

									right_buckets.add( contact );

								}else{

									left_buckets.add( contact );
								}
							}

							boolean	right_contains_rid = false;
							boolean left_contains_rid = false;

							if ( contains_router_node_id ){

								right_contains_rid =
										((router_node_id[depth/8]>>(7-(depth%8)))&0x01 ) == 0;

								left_contains_rid	= !right_contains_rid;
							}

							DHTRouterNodeImpl	new_left 	= new DHTRouterNodeImpl( this, depth+1, left_contains_rid, left_buckets );
							DHTRouterNodeImpl	new_right 	= new DHTRouterNodeImpl( this, depth+1, right_contains_rid, right_buckets );

							current_node.split( new_left, new_right );

							if ( right_contains_rid ){

									// we've created a new smallest subtree
									// TODO: tidy up old smallest subtree - remember to factor in B...

								smallest_subtree = new_left;

							}else if ( left_contains_rid ){

									// TODO: tidy up old smallest subtree - remember to factor in B...

								smallest_subtree = new_right;
							}

								// not complete, retry addition

						}else{

								// split not appropriate, add as a replacemnet

							DHTRouterContactImpl new_contact = new DHTRouterContactImpl( node_id, attachment, known_to_be_alive );

							return( current_node.addReplacement( new_contact, sleeping?1:max_rep_per_node ));
						}
					}else{

							// bucket space free, just add it

						DHTRouterContactImpl new_contact = new DHTRouterContactImpl( node_id, attachment, known_to_be_alive );

						current_node.addNode( new_contact );	// complete - added to bucket

						return( new_contact );
					}
				}else{

					current_node = next_node;

					j--;
				}
			}
		}

		Debug.out( "DHTRouter inconsistency" );

		return( null );
	}

	@Override
	public List
	findClosestContacts(
		byte[]		node_id,
		int			num_to_return,
		boolean		live_only )
	{
			// find the num_to_return-ish closest nodes - consider all buckets, not just the closest

		try{
			this_mon.enter();

			List res = new ArrayList();

			findClosestContacts( node_id, num_to_return, 0, root, live_only, res );

			return( res );

		}finally{

			this_mon.exit();
		}
	}

	protected void
	findClosestContacts(
		byte[]					node_id,
		int						num_to_return,
		int						depth,
		DHTRouterNodeImpl		current_node,
		boolean					live_only,
		List					res )
	{
		List	buckets = current_node.getBuckets();

		if ( buckets != null ){

				// add everything from the buckets - caller will sort and select
				// the best ones as required

			for (int i=0;i<buckets.size();i++){

				DHTRouterContactImpl	contact = (DHTRouterContactImpl)buckets.get(i);

					// use !failing at the moment to include unknown ones

				if ( ! ( live_only && contact.isFailing())){

					res.add( contact );
				}
			}
		}else{

			boolean bit = ((node_id[depth/8]>>(7-(depth%8)))&0x01 ) == 1;

			DHTRouterNodeImpl	best_node;
			DHTRouterNodeImpl	worse_node;

			if ( bit ){

				best_node = current_node.getLeft();

				worse_node = current_node.getRight();
			}else{

				best_node = current_node.getRight();

				worse_node = current_node.getLeft();
			}

			findClosestContacts( node_id, num_to_return, depth+1, best_node, live_only, res  );

			if ( res.size() < num_to_return ){

				findClosestContacts( node_id, num_to_return, depth+1, worse_node, live_only, res );
			}
		}
	}

	@Override
	public DHTRouterContact
	findContact(
		byte[]		node_id )
	{
		Object[]	res = findContactSupport( node_id );

		return((DHTRouterContact)res[1]);
	}

	protected DHTRouterNodeImpl
	findNode(
		byte[]	node_id )
	{
		Object[]	res = findContactSupport( node_id );

		return((DHTRouterNodeImpl)res[0]);
	}

	protected Object[]
	findContactSupport(
		byte[]		node_id )
	{
		try{
			this_mon.enter();

			DHTRouterNodeImpl	current_node	= root;

			for (int i=0;i<node_id.length;i++){

				if ( current_node.getBuckets() != null ){

					break;
				}

				byte	b = node_id[i];

				int	j = 7;

				while( j >= 0 ){

					boolean	bit = ((b>>j)&0x01)==1?true:false;

					if ( current_node.getBuckets() != null ){

						break;
					}

					if ( bit ){

						current_node = current_node.getLeft();

					}else{

						current_node = current_node.getRight();
					}

					j--;
				}
			}

			List	buckets = current_node.getBuckets();

			for (int k=0;k<buckets.size();k++){

				DHTRouterContactImpl	contact = (DHTRouterContactImpl)buckets.get(k);

				if ( Arrays.equals(node_id, contact.getID())){

					return( new Object[]{ current_node, contact });
				}
			}

			return( new Object[]{ current_node, null });

		}finally{

			this_mon.exit();
		}
	}

	protected long
	getNodeCount()
	{
		return( getNodeCount( root ));
	}

	protected long
	getNodeCount(
		DHTRouterNodeImpl	node )
	{
		if ( node.getBuckets() != null ){

			return( 1 );

		}else{

			return( 1 + getNodeCount( node.getLeft())) + getNodeCount( node.getRight());
		}
	}

	protected long
	getContactCount()
	{
		return( getContactCount( root ));
	}

	protected long
	getContactCount(
		DHTRouterNodeImpl	node )
	{
		if ( node.getBuckets() != null ){

			return( node.getBuckets().size());

		}else{

			return( getContactCount( node.getLeft())) + getContactCount( node.getRight());
		}
	}

	@Override
	public List
	findBestContacts(
		int		max )
	{
		Set	set =
			new TreeSet(
					new Comparator()
					{
						@Override
						public int
						compare(
							Object	o1,
							Object	o2 )
						{
							DHTRouterContactImpl	c1 = (DHTRouterContactImpl)o1;
							DHTRouterContactImpl	c2 = (DHTRouterContactImpl)o2;

							return((int)( c2.getTimeAlive() - c1.getTimeAlive()));
						}
					});


		try{
			this_mon.enter();

			findAllContacts( set, root );

		}finally{

			this_mon.exit();
		}

		List	result = new ArrayList( max );

		Iterator	it = set.iterator();

		while( it.hasNext() && (max <= 0 || result.size() < max )){

			result.add( it.next());
		}

		return( result );
	}

	@Override
	public List
	getAllContacts()
	{
		try{
			this_mon.enter();

			List	l = new ArrayList();

			findAllContacts( l, root );

			return( l );

		}finally{

			this_mon.exit();
		}
	}

	protected void
	findAllContacts(
		Set					set,
		DHTRouterNodeImpl	node )
	{
		List	buckets = node.getBuckets();

		if ( buckets == null ){

			findAllContacts( set, node.getLeft());

			findAllContacts( set, node.getRight());
		}else{

			for (int i=0;i<buckets.size();i++){

				DHTRouterContactImpl	contact = (DHTRouterContactImpl)buckets.get(i);

				set.add( contact );
			}
		}
	}

	protected void
	findAllContacts(
		List				list,
		DHTRouterNodeImpl	node )
	{
		List	buckets = node.getBuckets();

		if ( buckets == null ){

			findAllContacts( list, node.getLeft());

			findAllContacts( list, node.getRight());
		}else{

			for (int i=0;i<buckets.size();i++){

				DHTRouterContactImpl	contact = (DHTRouterContactImpl)buckets.get(i);

				list.add( contact );
			}
		}
	}

	@Override
	public void
	seed()
	{
			// defer this a while to see how much refreshing is done by the normal DHT traffic

		seed_in_ticks = SEED_DELAY_TICKS;
	}

	protected void
	seedSupport()
	{

			// refresh all buckets apart from closest neighbour

		byte[]	path = new byte[router_node_id.length];

		List	ids = new ArrayList();

		try{
			this_mon.enter();

			refreshNodes( ids, root, path, true, SEED_DELAY_PERIOD * 2 );

		}finally{

			this_mon.exit();
		}

		for (int i=0;i<ids.size();i++){

			requestLookup((byte[])ids.get(i), "Seeding DHT" );
		}
	}

	protected void
	refreshNodes(
		List				nodes_to_refresh,
		DHTRouterNodeImpl	node,
		byte[]				path,
		boolean				seeding,
		long				max_permitted_idle )	// 0 -> don't check
	{
			// when seeding we don't do the smallest subtree

		if ( seeding && node == smallest_subtree ){

			return;
		}

		if ( max_permitted_idle != 0 ){

			if ( node.getTimeSinceLastLookup() <= max_permitted_idle ){

				return;
			}
		}

		if ( node.getBuckets() != null ){

				// and we also don't refresh the bucket containing the router id when seeding

			if ( seeding && node.containsRouterNodeID()){

				return;
			}

			refreshNode( nodes_to_refresh, node, path );
		}

			// synchronous refresh may result in this bucket being split
			// so we retest here to refresh sub-buckets as required

		if ( node.getBuckets() == null ){

			int	depth = node.getDepth();

			byte	mask = (byte)( 0x01<<(7-(depth%8)));

			path[depth/8] = (byte)( path[depth/8] | mask );

			refreshNodes( nodes_to_refresh, node.getLeft(), path,seeding, max_permitted_idle  );

			path[depth/8] = (byte)( path[depth/8] & ~mask );

			refreshNodes( nodes_to_refresh, node.getRight(), path,seeding, max_permitted_idle  );
		}
	}

	protected void
	refreshNode(
		List				nodes_to_refresh,
		DHTRouterNodeImpl	node,
		byte[]				path )
	{
			// pick a random id in the node's range.

		byte[]	id = new byte[router_node_id.length];

		random.nextBytes( id );

		int	depth = node.getDepth();

		for (int i=0;i<depth;i++){

			byte	mask = (byte)( 0x01<<(7-(i%8)));

			boolean bit = ((path[i/8]>>(7-(i%8)))&0x01 ) == 1;

			if ( bit ){

				id[i/8] = (byte)( id[i/8] | mask );

			}else{

				id[i/8] = (byte)( id[i/8] & ~mask );
			}
		}

		nodes_to_refresh.add( id );
	}

	protected DHTRouterNodeImpl
	getSmallestSubtree()
	{
		return( smallest_subtree );
	}

	@Override
	public void
	recordLookup(
		byte[]	node_id )
	{
		findNode( node_id ).setLastLookupTime();
	}

	@Override
	public void
	refreshIdleLeaves(
		long	idle_max)
	{
		// while we are synchronously refreshing the smallest subtree the tree can mutate underneath us
		// as new contacts are discovered. We NEVER merge things back together

		byte[]	path = new byte[router_node_id.length];

		List	ids = new ArrayList();

		try{
			this_mon.enter();

			refreshNodes( ids, root, path, false, idle_max );

		}finally{

			this_mon.exit();
		}

		for (int i=0;i<ids.size();i++){

			requestLookup((byte[])ids.get(i), "Idle leaf refresh" );
		}
	}

	@Override
	public boolean
	requestPing(
		byte[]		node_id )
	{
		Object[] res = findContactSupport( node_id );

		DHTRouterContactImpl	contact = (DHTRouterContactImpl)res[1];

		if ( contact != null ){

			adapter.requestPing( contact );

			return( true );
		}

		return( false );
	}

	protected void
	requestPing(
		DHTRouterContactImpl	contact )
	{
		if ( suspended ){

			return;
		}

			// make sure we don't do the ping when synchronised

		DHTLog.log( "DHTRouter: requestPing:" + DHTLog.getString( contact.getID()));

		if ( contact == local_contact ){

			Debug.out( "pinging local contact" );
		}

		try{
			this_mon.enter();

			if ( !outstanding_pings.contains( contact )){

				outstanding_pings.add( contact );
			}
		}finally{

			this_mon.exit();
		}
	}

	protected void
	dispatchPings()
	{
		if ( outstanding_pings.size() == 0 ){

			return;
		}

		List	pings;

		try{
			this_mon.enter();

			pings	= outstanding_pings;

			outstanding_pings = new ArrayList();

		}finally{

			this_mon.exit();
		}

		if ( suspended ){

			return;
		}

		for (int i=0;i<pings.size();i++){

			adapter.requestPing((DHTRouterContactImpl)pings.get(i));
		}
	}

	protected void
	pingeroonies()
	{
		try{
			this_mon.enter();

			DHTRouterNodeImpl	node = root;

			LinkedList	stack = new LinkedList();

			while( true ){

				List	buckets = node.getBuckets();

				if ( buckets == null ){

					if ( random.nextBoolean()){

						stack.add( node.getRight());

						node = node.getLeft();

					}else{

						stack.add( node.getLeft());

						node = node.getRight();
					}
				}else{

					int 					max_fails 			= 0;
					DHTRouterContactImpl	max_fails_contact	= null;

					for (int i=0;i<buckets.size();i++){

						DHTRouterContactImpl	contact = (DHTRouterContactImpl)buckets.get(i);

						if ( !contact.getPingOutstanding()){

							int	fails = contact.getFailCount();

							if ( fails > max_fails ){

								max_fails			= fails;
								max_fails_contact	= contact;
							}
						}
					}

					if ( max_fails_contact != null ){

						requestPing( max_fails_contact );

						return;
					}

					if ( stack.size() == 0 ){

						break;
					}

					node = (DHTRouterNodeImpl)stack.removeLast();
				}
			}
		}finally{

			this_mon.exit();

			dispatchPings();
		}
	}

	protected void
	requestNodeAdd(
		DHTRouterContactImpl	contact )
	{
		if ( suspended ){

			return;
		}

			// make sure we don't do the addition when synchronised

		DHTLog.log( "DHTRouter: requestNodeAdd:" + DHTLog.getString( contact.getID()));

		if ( contact == local_contact ){

			Debug.out( "adding local contact" );
		}

		try{
			this_mon.enter();

			if ( !outstanding_adds.contains( contact )){

				outstanding_adds.add( contact );
			}
		}finally{

			this_mon.exit();
		}
	}

	protected void
	dispatchNodeAdds()
	{
		if ( outstanding_adds.size() == 0 ){

			return;
		}

		List	adds;

		try{
			this_mon.enter();

			adds	= outstanding_adds;

			outstanding_adds = new ArrayList();

		}finally{

			this_mon.exit();
		}

		if ( suspended ){

			return;
		}

		for (int i=0;i<adds.size();i++){

			adapter.requestAdd((DHTRouterContactImpl)adds.get(i));
		}
	}

	@Override
	public byte[]
	refreshRandom()
	{
		byte[]	id = new byte[router_node_id.length];

		random.nextBytes( id );

		requestLookup( id, "Random Refresh" );

		return( id );
	}

	protected void
	requestLookup(
		byte[]		id,
		String		description )
	{
		DHTLog.log( "DHTRouter: requestLookup:" + DHTLog.getString( id ));

		adapter.requestLookup( id, description );
	}

	protected void
	getStatsSupport(
		long[]				stats_array,
		DHTRouterNodeImpl	node )
	{
		stats_array[DHTRouterStats.ST_NODES]++;

		List	buckets = node.getBuckets();

		if ( buckets == null ){

			getStatsSupport( stats_array, node.getLeft());

			getStatsSupport( stats_array, node.getRight());

		}else{

			stats_array[DHTRouterStats.ST_LEAVES]++;

			stats_array[DHTRouterStats.ST_CONTACTS] += buckets.size();

			for (int i=0;i<buckets.size();i++){

				DHTRouterContactImpl	contact = (DHTRouterContactImpl)buckets.get(i);

				if ( contact.getFirstFailTime() > 0 ){

					stats_array[DHTRouterStats.ST_CONTACTS_DEAD]++;

				}else if ( contact.hasBeenAlive()){

					stats_array[DHTRouterStats.ST_CONTACTS_LIVE]++;

				}else{

					stats_array[DHTRouterStats.ST_CONTACTS_UNKNOWN]++;
				}
			}

			List	rep = node.getReplacements();

			if ( rep != null ){

				stats_array[DHTRouterStats.ST_REPLACEMENTS] += rep.size();
			}
		}
	}

	protected long[]
	getStatsSupport()
	{
		 /* number of nodes
		 * number of leaves
		 * number of contacts
		 * number of replacements
		 * number of live contacts
		 * number of unknown contacts
		 * number of dying contacts
		 */

		try{
			this_mon.enter();

			long[]	res = new long[7];

			getStatsSupport( res, root );

			return( res );

		}finally{

			this_mon.exit();
		}
	}

	protected void
	log(
		String	str )
	{
		logger.log( str );
	}

	@Override
	public void
	print()
	{
		try{
			this_mon.enter();

			log( "DHT: " + DHTLog.getString2(router_node_id) + ", node count=" + getNodeCount()+ ", contacts=" + getContactCount());

			root.print( "", "" );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	destroy()
	{
		timer_event.cancel();

		notifyDead();
	}
}
