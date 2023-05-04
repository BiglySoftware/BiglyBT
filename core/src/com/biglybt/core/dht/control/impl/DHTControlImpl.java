/*
 * Created on 12-Jan-2005
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.core.dht.control.impl;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

import org.gudy.bouncycastle.crypto.CipherParameters;
import org.gudy.bouncycastle.crypto.engines.RC4Engine;
import org.gudy.bouncycastle.crypto.params.KeyParameter;

import com.biglybt.core.dht.*;
import com.biglybt.core.dht.control.*;
import com.biglybt.core.dht.control.DHTControlActivity.ActivityNode;
import com.biglybt.core.dht.db.DHTDB;
import com.biglybt.core.dht.db.DHTDBFactory;
import com.biglybt.core.dht.db.DHTDBLookupResult;
import com.biglybt.core.dht.db.DHTDBValue;
import com.biglybt.core.dht.impl.DHTLog;
import com.biglybt.core.dht.netcoords.DHTNetworkPosition;
import com.biglybt.core.dht.netcoords.DHTNetworkPositionManager;
import com.biglybt.core.dht.router.*;
import com.biglybt.core.dht.transport.*;
import com.biglybt.core.dht.transport.udp.DHTTransportUDP;
import com.biglybt.core.util.*;

/**
 * @author parg
 *
 */

public class
DHTControlImpl
	implements DHTControl, DHTTransportRequestHandler
{
	private static final boolean DISABLE_REPLICATE_ON_JOIN	= true;

	@SuppressWarnings("CanBeFinal")		// accessed from plugin

	public  static int EXTERNAL_LOOKUP_CONCURRENCY			= 16;

	private static final int EXTERNAL_PUT_CONCURRENCY				= 8;
	private static final int EXTERNAL_SLEEPING_PUT_CONCURRENCY		= 4;

	private static final int RANDOM_QUERY_PERIOD			= 5*60*1000;

	private static final int INTEGRATION_TIME_MAX			= 15*1000;


	final DHTControlAdapter		adapter;
	private final DHTTransport			transport;
	DHTTransportContact		local_contact;

	DHTRouter		router;

	final DHTDB			database;

	private final DHTControlStatsImpl	stats;

	final DHTLogger	logger;

	private final int			node_id_byte_count;
	final int			search_concurrency;
	private final int			lookup_concurrency;
	private final int			cache_at_closest_n;
	final int			K;
	private final int			B;
	private final int			max_rep_per_node;

	private final boolean	encode_keys;
	private final boolean	enable_random_poking;

	private long		router_start_time;
	private int			router_count;

	final ThreadPool	internal_lookup_pool;
	final ThreadPool	external_lookup_pool;
	final ThreadPool	internal_put_pool;
	private final ThreadPool	external_put_pool;

	private final Map<HashWrapper, Object>			imported_state	= new HashMap<>();

	volatile boolean	seeded;

	long		last_lookup;


	final ListenerManager<DHTControlListener>	listeners 	= ListenerManager.createAsyncManager(
			"DHTControl:listenDispatcher",
			new ListenerManagerDispatcher<DHTControlListener>()
			{
				@Override
				public void
				dispatch(
					DHTControlListener		listener,
					int			type,
					Object		value )
				{
					listener.activityChanged((DHTControlActivity)value, type );
				}
			});

	final List<DHTControlActivity>		activities		= new ArrayList<>();
	final AEMonitor	activity_mon	= new AEMonitor( "DHTControl:activities" );

	protected final AEMonitor	estimate_mon		= new AEMonitor( "DHTControl:estimate" );
	private long		last_dht_estimate_time;
	private long		local_dht_estimate;
	private long		combined_dht_estimate;
	private int			combined_dht_estimate_mag;

	private static final int	LOCAL_ESTIMATE_HISTORY	= 32;

	private final Map<HashWrapper, Long>	local_estimate_values =
		new LinkedHashMap<HashWrapper, Long>(LOCAL_ESTIMATE_HISTORY,0.75f,true)
		{
			@Override
			protected boolean
			removeEldestEntry(
		   		Map.Entry eldest)
			{
				return( size() > LOCAL_ESTIMATE_HISTORY );
			}
		};

	private static final int	REMOTE_ESTIMATE_HISTORY	= 128;

	private final List<Integer>	remote_estimate_values = new LinkedList<>();

	protected final AEMonitor	spoof_mon		= new AEMonitor( "DHTControl:spoof" );

	//private Cipher 			spoof_cipher;
	//private SecretKey		spoof_key;
	MessageDigest	spoof_digest;
	byte[]			spoof_key;

	private static final int	SPOOF_GEN_HISTORY_SIZE	= 256;

	private final Map<InetAddress,Integer>	spoof_gen_history =
		new LinkedHashMap<InetAddress,Integer>(SPOOF_GEN_HISTORY_SIZE,0.75f,true)
		{
			@Override
			protected boolean
			removeEldestEntry(
		   		Map.Entry<InetAddress,Integer> eldest)
			{
				return( size() > SPOOF_GEN_HISTORY_SIZE );
			}
		};

	private final Map<HashWrapper,byte[]>	spoof_gen_history2 =
			new LinkedHashMap<HashWrapper,byte[]>(SPOOF_GEN_HISTORY_SIZE,0.75f,true)
			{
				@Override
				protected boolean
				removeEldestEntry(
			   		Map.Entry<HashWrapper,byte[]> eldest)
				{
					return( size() > SPOOF_GEN_HISTORY_SIZE );
				}
			};

	private final static int SPOOF_ID2_SIZE	= 8;

	private long			last_node_add_check;
	private byte[]			node_add_check_uninteresting_limit;

	private long			rbs_time;
	private byte[]			rbs_id	= {};

	private boolean			sleeping;
	private boolean			suspended;

	public
	DHTControlImpl(
		DHTControlAdapter	_adapter,
		DHTTransport		_transport,
		int					_K,
		int					_B,
		int					_max_rep_per_node,
		int					_search_concurrency,
		int					_lookup_concurrency,
		int					_original_republish_interval,
		int					_cache_republish_interval,
		int					_cache_at_closest_n,
		boolean				_encode_keys,
		boolean				_enable_random_poking,
		DHTLogger 			_logger )
	{
		adapter		= _adapter;
		transport	= _transport;
		logger		= _logger;

		K								= _K;
		B								= _B;
		max_rep_per_node				= _max_rep_per_node;
		search_concurrency				= _search_concurrency;
		lookup_concurrency				= _lookup_concurrency;
		cache_at_closest_n				= _cache_at_closest_n;
		encode_keys						= _encode_keys;
		enable_random_poking			= _enable_random_poking;

			// set this so we don't do initial calculation until reasonably populated

		last_dht_estimate_time	= SystemTime.getCurrentTime();

		database = DHTDBFactory.create(
						adapter.getStorageAdapter(),
						_original_republish_interval,
						_cache_republish_interval,
						transport.getProtocolVersion(),
						logger );

		internal_lookup_pool 	= new ThreadPool("DHTControl:internallookups", lookup_concurrency );
		internal_put_pool 		= new ThreadPool("DHTControl:internalputs", lookup_concurrency );

			// external pools queue when full ( as opposed to blocking )

		external_lookup_pool 	= new ThreadPool("DHTControl:externallookups", EXTERNAL_LOOKUP_CONCURRENCY, true );
		external_put_pool 		= new ThreadPool("DHTControl:puts", EXTERNAL_PUT_CONCURRENCY, true );

		createRouter( transport.getLocalContact());

		node_id_byte_count	= router.getID().length;

		stats = new DHTControlStatsImpl( this );

			// don't bother computing anti-spoof stuff if we don't support value storage

		if ( transport.supportsStorage()){

			try{
				/*
				spoof_cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");

				KeyGenerator keyGen = KeyGenerator.getInstance("DESede");

				spoof_key = keyGen.generateKey();
				*/

				spoof_digest 	= MessageDigest.getInstance( "MD5" );
				spoof_key		= new byte[16];
				RandomUtils.nextSecureBytes( spoof_key );
			}catch( Throwable e ){

				Debug.printStackTrace( e );

				logger.log( e );
			}
		}

		transport.setRequestHandler( this );

		transport.addListener(
			new DHTTransportListener()
			{
				@Override
				public void
				localContactChanged(
					DHTTransportContact	new_local_contact )
				{
					logger.log( "Transport ID changed, recreating router" );

					List	old_contacts = router.findBestContacts( 0 );

					byte[]	old_router_id = router.getID();

					createRouter( new_local_contact );

						// sort for closeness to new router id

					Set<DHTTransportContact>	sorted_contacts = new sortedTransportContactSet( router.getID(), true ).getSet();

					for (Object old_contact : old_contacts) {

						DHTRouterContact contact = (DHTRouterContact) old_contact;

						if (!Arrays.equals(old_router_id, contact.getID())) {

							if (contact.isAlive()) {

								DHTTransportContact t_contact = ((DHTControlContact) contact.getAttachment()).getTransportContact();

								sorted_contacts.add(t_contact);
							}
						}
					}

						// fill up with non-alive ones to lower limit in case this is a start-of-day
						// router change and we only have imported contacts in limbo state

					for (int i=0;sorted_contacts.size() < 32 && i<old_contacts.size();i++){

						DHTRouterContact	contact = (DHTRouterContact)old_contacts.get(i);

						if ( !Arrays.equals( old_router_id, contact.getID())){

							if ( !contact.isAlive()){

								DHTTransportContact	t_contact = ((DHTControlContact)contact.getAttachment()).getTransportContact();

								sorted_contacts.add( t_contact );
							}
						}
					}

					Iterator	it = sorted_contacts.iterator();

					int	added = 0;

						// don't add them all otherwise we can skew the smallest-subtree. better
						// to seed with some close ones and then let the normal seeding process
						// populate it correctly

					while( it.hasNext() && added < 128 ){

						DHTTransportContact	contact = (DHTTransportContact)it.next();

						router.contactAlive( contact.getID(), new DHTControlContactImpl( contact ));

						added++;
					}

					seed( false );
				}

				@Override
				public void
				resetNetworkPositions()
				{
					List<DHTRouterContact>	contacts = router.getAllContacts();

					for (DHTRouterContact rc : contacts) {

						if (!router.isID(rc.getID())) {

							((DHTControlContact) rc.getAttachment()).getTransportContact().createNetworkPositions(false);
						}
					}
				}

				@Override
				public void
				currentAddress(
					String		address )
				{
				}

				@Override
				public void
				reachabilityChanged(
					boolean	reacheable )
				{
				}
			});
	}

	public
	DHTControlImpl(
		DHTControlAdapter	_adapter,
		DHTTransport		_transport,
		DHTRouter			_router,
		DHTDB				_database,
		int					_K,
		int					_B,
		int					_max_rep_per_node,
		int					_search_concurrency,
		int					_lookup_concurrency,
		int					_original_republish_interval,
		int					_cache_republish_interval,
		int					_cache_at_closest_n,
		boolean				_encode_keys,
		boolean				_enable_random_poking,
		DHTLogger 			_logger )
	{
		adapter		= _adapter;
		transport	= _transport;
		logger		= _logger;

		K								= _K;
		B								= _B;
		max_rep_per_node				= _max_rep_per_node;
		search_concurrency				= _search_concurrency;
		lookup_concurrency				= _lookup_concurrency;
		cache_at_closest_n				= _cache_at_closest_n;
		encode_keys						= _encode_keys;
		enable_random_poking			= _enable_random_poking;

			// set this so we don't do initial calculation until reasonably populated

		last_dht_estimate_time	= SystemTime.getCurrentTime();

		database = _database;

		internal_lookup_pool 	= new ThreadPool("DHTControl:internallookups", lookup_concurrency );
		internal_put_pool 		= new ThreadPool("DHTControl:internalputs", lookup_concurrency );

			// external pools queue when full ( as opposed to blocking )

		external_lookup_pool 	= new ThreadPool("DHTControl:externallookups", EXTERNAL_LOOKUP_CONCURRENCY, true );
		external_put_pool 		= new ThreadPool("DHTControl:puts", EXTERNAL_PUT_CONCURRENCY, true );

		router				= _router;
		router_start_time	= SystemTime.getCurrentTime();

		local_contact = transport.getLocalContact();

		database.setControl( this );

		node_id_byte_count	= router.getID().length;

		stats = new DHTControlStatsImpl( this );

			// don't bother computing anti-spoof stuff if we don't support value storage

		if ( transport.supportsStorage()){

			try{
				/*
				spoof_cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");

				KeyGenerator keyGen = KeyGenerator.getInstance("DESede");

				spoof_key = keyGen.generateKey();
				*/

				spoof_digest 	= MessageDigest.getInstance( "MD5" );
				spoof_key		= new byte[16];
				RandomUtils.nextSecureBytes( spoof_key );
			}catch( Throwable e ){

				Debug.printStackTrace( e );

				logger.log( e );
			}
		}

		transport.setRequestHandler( this );
	}

	protected void
	createRouter(
		DHTTransportContact		_local_contact)
	{
		router_start_time	= SystemTime.getCurrentTime();
		router_count++;

		local_contact	= _local_contact;

		if ( router != null ){

			router.destroy();
		}

		router	= DHTRouterFactory.create(
					K, B, max_rep_per_node,
					local_contact.getID(),
					new DHTControlContactImpl( local_contact ),
					logger);

		router.setSleeping( sleeping );

		if ( suspended ){

			router.setSuspended( true );
		}

		router.setAdapter(
			new DHTRouterAdapter()
			{
				@Override
				public void
				requestPing(
					DHTRouterContact	contact )
				{
					DHTControlImpl.this.requestPing( contact );
				}

				@Override
				public void
				requestLookup(
					byte[]		id,
					String		description )
				{
					lookup( internal_lookup_pool, false,
							id,
							description,
							(byte)0,
							false,
							5*60*1000,		// upper bound on this refresh/seeding operation
							search_concurrency,
							1,
							router.getK()/2,	// (parg - removed this; re-added the /2 on May 2014 to see how it performs) decrease search accuracy for refreshes
							new lookupResultHandler(new DHTOperationAdapter())
							{
								@Override
								public void
								diversify(
									DHTTransportContact	cause,
									byte				diversification_type )
								{
								}

								@Override
								public void
								closest(
									List<DHTTransportContact>		res )
								{
								}
							});
				}

				@Override
				public void
				requestAdd(
					DHTRouterContact	contact )
				{
					nodeAddedToRouter( contact );
				}
			});

		database.setControl( this );
	}

	public long
	getRouterUptime()
	{
		long	now = SystemTime.getCurrentTime();

		if ( now < router_start_time ){

			router_start_time	= now;
		}

		return(  now - router_start_time );
	}

	public int
	getRouterCount()
	{
		return( router_count );
	}

	@Override
	public void
	setSleeping(
		boolean	asleep )
	{
		if ( asleep != sleeping ){

			logger.log( "Sleep mode changed to " + asleep );
		}

		sleeping	= asleep;

		DHTRouter current_router = router;

		if ( current_router != null ){

			current_router.setSleeping( asleep );
		}

		transport.setGenericFlag( DHTTransport.GF_DHT_SLEEPING, asleep );

		if ( asleep ){

			external_put_pool.setMaxThreads( EXTERNAL_SLEEPING_PUT_CONCURRENCY );

		}else{

			external_put_pool.setMaxThreads( EXTERNAL_PUT_CONCURRENCY );
		}

		database.setSleeping( asleep );
	}

	@Override
	public void
	setSuspended(
		boolean			susp )
	{
		suspended	= susp;

		if ( susp ){

			transport.setSuspended( true );

			DHTRouter current_router = router;

			if ( current_router != null ){

				current_router.setSuspended( true );
			}

			database.setSuspended( true );

		}else{

			database.setSuspended( false );

			DHTRouter current_router = router;

			if ( current_router != null ){

				current_router.setSuspended( false );
			}

			transport.setSuspended( false );
		}
	}

	@Override
	public DHTControlStats
	getStats()
	{
		return( stats );
	}

	@Override
	public DHTTransport
	getTransport()
	{
		return( transport );
	}

	@Override
	public DHTRouter
	getRouter()
	{
		return( router );
	}

	@Override
	public DHTDB
	getDataBase()
	{
		return( database );
	}

	@Override
	public void
	contactImported(
		DHTTransportContact	contact,
		boolean				is_bootstrap )
	{
		router.contactKnown( contact.getID(), new DHTControlContactImpl(contact), is_bootstrap );
	}

	@Override
	public void
	contactRemoved(
		DHTTransportContact	contact )
	{
			// obviously we don't want to remove ourselves

		if ( !router.isID( contact.getID())){

			router.contactDead( contact.getID(), true );
		}
	}

	@Override
	public void
	exportState(
		DataOutputStream	daos,
		int					max )

		throws IOException
	{
			/*
			 * We need to be a bit smart about exporting state to deal with the situation where a
			 * DHT is started (with good import state) and then stopped before the goodness of the
			 * state can be re-established. So we remember what we imported and take account of this
			 * on a re-export
			 */

			// get all the contacts

		List<DHTRouterContact>	contacts = router.findBestContacts( 0 );

			// give priority to any that were alive before and are alive now

		List<DHTRouterContact>	to_save 	= new ArrayList<>();
		List<DHTRouterContact>	reserves	= new ArrayList<>();

		//System.out.println( "Exporting" );

		for (DHTRouterContact contact : contacts) {

			Object[] imported = (Object[]) imported_state.get(new HashWrapper(contact.getID()));

			if (imported != null) {

				if (contact.isAlive()) {

					// definitely want to keep this one

					to_save.add(contact);

				} else if (!contact.isFailing()) {

					// dunno if its still good or not, however its got to be better than any
					// new ones that we didn't import who aren't known to be alive

					reserves.add(contact);
				}
			}
		}

		//System.out.println( "    initial to_save = " + to_save.size() + ", reserves = " + reserves.size());

			// now pull out any live ones

		for (DHTRouterContact contact : contacts) {

			if (contact.isAlive() && !to_save.contains(contact)) {

				to_save.add(contact);
			}
		}

		//System.out.println( "    after adding live ones = " + to_save.size());

			// now add any reserve ones

		for (DHTRouterContact contact : reserves) {

			if (!to_save.contains(contact)) {

				to_save.add(contact);
			}
		}

		//System.out.println( "    after adding reserves = " + to_save.size());

			// now add in the rest!

		for (DHTRouterContact contact : contacts) {

			if (!to_save.contains(contact)) {

				to_save.add(contact);
			}
		}

			// and finally remove the invalid ones

		Iterator	it = to_save.iterator();

		while( it.hasNext()){

			DHTRouterContact	contact	= (DHTRouterContact)it.next();

			DHTTransportContact	t_contact = ((DHTControlContact)contact.getAttachment()).getTransportContact();

			if ( !t_contact.isValid()){

				it.remove();
			}
		}

		//System.out.println( "    finally = " + to_save.size());

		int	num_to_write = Math.min( max, to_save.size());

		daos.writeInt( num_to_write );

		for (int i=0;i<num_to_write;i++){

			DHTRouterContact	contact = to_save.get(i);

			//System.out.println( "export:" + contact.getString());

			daos.writeLong( contact.getTimeAlive());

			DHTTransportContact	t_contact = ((DHTControlContact)contact.getAttachment()).getTransportContact();

			try{

				t_contact.exportContact( daos );

			}catch( DHTTransportException e ){

					// shouldn't fail as for a contact to make it to the router
					// it should be valid...

				Debug.printStackTrace( e );

				throw( new IOException( e.getMessage()));
			}
		}

		daos.flush();
	}

	@Override
	public void
	importState(
		DataInputStream		dais )

		throws IOException
	{
		int	num = dais.readInt();

		for (int i=0;i<num;i++){

			try{

				long	time_alive = dais.readLong();

				DHTTransportContact	contact = transport.importContact( dais, false );

				if ( contact != null ){
				
					imported_state.put( new HashWrapper( contact.getID()), new Object[]{time_alive, contact });
				}

			}catch( DHTTransportException e ){

				Debug.printStackTrace( e );
			}
		}
	}

	@Override
	public void
	seed(
		final boolean		full_wait )
	{
		final AESemaphore	sem = new AESemaphore( "DHTControl:seed" );

		lookup( internal_lookup_pool, false,
				router.getID(),
				"Seeding DHT",
				(byte)0,
				false,
				0,
				search_concurrency*4,
				1,
				router.getK(),
				new lookupResultHandler(new DHTOperationAdapter())
				{
					@Override
					public void
					diversify(
						DHTTransportContact	cause,
						byte				diversification_type )
					{
					}

					@Override
					public void
					closest(
						List<DHTTransportContact>		res )
					{
						if ( !full_wait ){

							sem.release();
						}

						seeded = true;

						try{

							router.seed();

						}finally{

							if ( full_wait ){

								sem.release();
							}
						}
					}
				});

			// we always wait at least a minimum amount of time before returning

		long	start = SystemTime.getCurrentTime();

		sem.reserve( INTEGRATION_TIME_MAX );

		long	now = SystemTime.getCurrentTime();

		if ( now < start ){

			start	= now;
		}

		long	remaining = INTEGRATION_TIME_MAX - ( now - start );

		if ( remaining > 500 && !full_wait ){

			logger.log( "Initial integration completed, waiting " + remaining + " ms for second phase to start" );

			try{
				Thread.sleep( remaining );

			}catch( Throwable e ){

				Debug.out(e);
			}
		}
	}

	@Override
	public boolean
	isSeeded()
	{
		return( seeded );
	}

	@Override
	public void
	setSeeded()
	{
			// manually set as seeded

		seeded = true;

		router.seed();
	}

	protected void
	poke()
	{
		if ( !enable_random_poking ){

			return;
		}

		long	now = SystemTime.getCurrentTime();

		if ( 	now < last_lookup ||
				now - last_lookup > RANDOM_QUERY_PERIOD ){

			last_lookup	= now;

				// we don't want this to be blocking as it'll stuff the stats

			external_lookup_pool.run(
				new DhtTask(external_lookup_pool)
				{
					private byte[]	target = {};

					@Override
					public void
					runSupport()
					{
						target = router.refreshRandom();
					}

					@Override
					protected void
					cancel()
					{
					}
					@Override
					public byte[]
					getTarget()
					{
						return( target );
					}

					@Override
					public DHTControlActivity.ActivityState
					getCurrentState()
					{
						return( null );
					}

					@Override
					public String
					getDescription()
					{
						return( "Random Query" );
					}
				});
		}
	}

	@Override
	public void
	put(
		byte[]					_unencoded_key,
		String					_description,
		byte[]					_value,
		short					_flags,
		byte					_life_hours,
		byte					_replication_control,
		boolean					_high_priority,
		DHTOperationListener	_listener )
	{
			// public entry point for explicit publishes

		if ( _value.length == 0 ){

				// zero length denotes value removal

			throw( new RuntimeException( "zero length values not supported"));
		}

		byte[]	encoded_key = encodeKey( _unencoded_key );

		if ( DHTLog.isOn()){
			DHTLog.log( "put for " + DHTLog.getString( encoded_key ));
		}

		DHTDBValue	value = database.store( new HashWrapper( encoded_key ), _value, _flags, _life_hours, _replication_control );

		put( 	external_put_pool,
				_high_priority,
				encoded_key,
				_description,
				value,
				_flags,
				0,
				true,
				new HashSet(),
				1,
				_listener instanceof DHTOperationListenerDemuxer?
						(DHTOperationListenerDemuxer)_listener:
						new DHTOperationListenerDemuxer(_listener));
	}

	@Override
	public void
	putEncodedKey(
		byte[]				encoded_key,
		String				description,
		DHTTransportValue	value,
		long				timeout,
		boolean				original_mappings )
	{
		put( 	internal_put_pool,
				false,
				encoded_key,
				description,
				value,
				(byte)0,
				timeout,
				original_mappings,
				new HashSet(),
				1,
				new DHTOperationListenerDemuxer( new DHTOperationAdapter()));
	}


	protected void
	put(
		ThreadPool					thread_pool,
		boolean						high_priority,
		byte[]						initial_encoded_key,
		String						description,
		DHTTransportValue			value,
		short						flags,
		long						timeout,
		boolean						original_mappings,
		Set							things_written,
		int							put_level,
		DHTOperationListenerDemuxer	listener )
	{
		put( 	thread_pool,
				high_priority,
				initial_encoded_key,
				description,
				new DHTTransportValue[]{ value },
				flags,
				timeout,
				original_mappings,
				things_written,
				put_level,
				listener );
	}

	protected void
	put(
		final ThreadPool					thread_pool,
		final boolean						high_priority,
		final byte[]						initial_encoded_key,
		final String						description,
		final DHTTransportValue[]			values,
		final short							flags,
		final long							timeout,
		final boolean						original_mappings,
		final Set							things_written,
		final int							put_level,
		final DHTOperationListenerDemuxer	listener )
	{

			// get the initial starting point for the put - may have previously been diversified

		byte[][]	encoded_keys	=
			adapter.diversify(
					description,
					null,
					true,
					true,
					initial_encoded_key,
					DHT.DT_NONE,
					original_mappings,
					getMaxDivDepth());

		if ( encoded_keys.length == 0 ){

				// over-diversified

			listener.diversified( "Over-diversification of [" + description + "]" );

			listener.complete( false );

			return;
		}

			// may be > 1 if diversification is replicating (for load balancing)

		boolean	reported_div = false;
		
		for (final byte[] encoded_key : encoded_keys) {

			HashWrapper hw = new HashWrapper(encoded_key);

			synchronized (things_written) {

				if (things_written.contains(hw)) {

					// System.out.println( "put: skipping key as already written" );

					continue;
				}

				things_written.add(hw);
			}

			boolean	is_non_div = Arrays.equals(encoded_key, initial_encoded_key);
		
			final String this_description = is_non_div?description : ("Diversification of [" + description + "]");

			if ( !is_non_div ){
				
				if ( !reported_div ){
					
					reported_div = true;
					
					listener.diversified( this_description );
				}
			}
			
			lookup(thread_pool,
					high_priority,
					encoded_key,
					this_description,
					(short) (flags | DHT.FLAG_LOOKUP_FOR_STORE),
					false,
					timeout,
					search_concurrency,
					1,
					router.getK(),
					new lookupResultHandler(listener) {
						@Override
						public void
						diversify(
								DHTTransportContact cause,
								byte diversification_type) {
							Debug.out("Shouldn't get a diversify on a lookup-node");
						}

						@Override
						public void
						closest(
								List<DHTTransportContact> _closest) {
							put(thread_pool,
									high_priority,
									new byte[][]{encoded_key},
									"Store of [" + this_description + "]",
									new DHTTransportValue[][]{values},
									flags,
									_closest,
									timeout,
									listener,
									true,
									things_written,
									put_level,
									false);
						}
					});
		}
	}

	@Override
	public void
	putDirectEncodedKeys(
		byte[][]				encoded_keys,
		String					description,
		DHTTransportValue[][]	value_sets,
		List<DHTTransportContact>	contacts )
	{
			// we don't consider diversification for direct puts (these are for republishing
			// of cached mappings and we maintain these as normal - its up to the original
			// publisher to diversify as required)

		put( 	internal_put_pool,
				false,
				encoded_keys,
				description,
				value_sets,
				(byte)0,
				contacts,
				0,
				new DHTOperationListenerDemuxer( new DHTOperationAdapter()),
				false,
				new HashSet(),
				1,
				false );
	}

	@Override
	public void
	putDirectEncodedKeys(
		byte[][]				encoded_keys,
		String					description,
		DHTTransportValue[][]	value_sets,
		DHTTransportContact		contact,
		DHTOperationListener	listener )
	{
			// we don't consider diversification for direct puts (these are for republishing
			// of cached mappings and we maintain these as normal - its up to the original
			// publisher to diversify as required)

		List<DHTTransportContact> contacts = new ArrayList<>(1);

		contacts.add( contact );

		put( 	internal_put_pool,
				false,
				encoded_keys,
				description,
				value_sets,
				(byte)0,
				contacts,
				0,
				new DHTOperationListenerDemuxer( listener ),
				false,
				new HashSet(),
				1,
				false );
	}

	@Override
	public byte[]
	getObfuscatedKey(
		byte[]		plain_key )
	{
		int	length = plain_key.length;

		byte[]	obs_key = new byte[ length ];

		System.arraycopy( plain_key, 0, obs_key, 0, 5 );

			// ensure plain key and obfuscated one differ at subsequent bytes to prevent potential
			// clashes with code that uses 'n' byte prefix (e.g. DB survey code)

		for (int i=6;i<length;i++){

			if ( plain_key[i] == 0 ){

				obs_key[i] = 1;
			}
		}

			// finally copy over last two bytes for code that uses challenge-response on this
			// (survey code)

		obs_key[length-2] = plain_key[length-2];
		obs_key[length-1] = plain_key[length-1];

		return( obs_key );
	}

	protected byte[]
	getObfuscatedValue(
		byte[]		plain_key )
	{
        RC4Engine	engine = new RC4Engine();

		CipherParameters	params = new KeyParameter( new SHA1Simple().calculateHash( plain_key ));

		engine.init( true, params );

		byte[]	temp = new byte[1024];

		engine.processBytes( temp, 0, 1024, temp, 0 );

		final byte[] obs_value = new byte[ plain_key.length ];

		engine.processBytes( plain_key, 0, plain_key.length, obs_value, 0 );

		return( obs_value );
	}

	protected DHTTransportValue
	getObfuscatedValue(
		final DHTTransportValue		basis,
		byte[]						plain_key )
	{
		final byte[] obs_value = getObfuscatedValue( plain_key );

		return(
			new DHTTransportValue()
			{
				@Override
				public boolean
				isLocal()
				{
					return( basis.isLocal());
				}

				@Override
				public long
				getCreationTime()
				{
					return( basis.getCreationTime());
				}

				@Override
				public byte[]
				getValue()
				{
					return( obs_value );
				}

				@Override
				public int
				getVersion()
				{
					return( basis.getVersion());
				}

				@Override
				public DHTTransportContact
				getOriginator()
				{
					return( basis.getOriginator());
				}

				@Override
				public int
				getFlags()
				{
					return( basis.getFlags());
				}

				@Override
				public int
				getLifeTimeHours()
				{
					return( basis.getLifeTimeHours());
				}

				@Override
				public byte
				getReplicationControl()
				{
					return( basis.getReplicationControl());
				}

				@Override
				public byte
				getReplicationFactor()
				{
					return( basis.getReplicationFactor());
				}

				@Override
				public byte
				getReplicationFrequencyHours()
				{
					return( basis.getReplicationFrequencyHours());
				}

				@Override
				public String
				getString()
				{
					return( "obs: " + basis.getString());
				}
			});
	}

	protected void
	put(
		final ThreadPool						thread_pool,
		final boolean							high_priority,
		byte[][]								initial_encoded_keys,
		final String							description,
		final DHTTransportValue[][]				initial_value_sets,
		final short								flags,
		final List<DHTTransportContact>								contacts,
		final long								timeout,
		final DHTOperationListenerDemuxer		listener,
		final boolean							consider_diversification,
		final Set								things_written,
		final int								put_level,
		final boolean							immediate )
	{
		int max_depth = getMaxDivDepth();

		if ( put_level > max_depth ){

			Debug.out( "Put level exceeded, terminating diversification (level=" + put_level + ",max=" + max_depth + ")" );

			listener.incrementCompletes();

			listener.complete( false );

			return;
		}

		boolean[]	ok = new boolean[initial_encoded_keys.length];

		int	failed = 0;

		for (int i=0;i<initial_encoded_keys.length;i++){

			if ( ! (ok[i] = !database.isKeyBlocked( initial_encoded_keys[i]))){

				failed++;
			}
		}

			// if all failed then nothing to do

		if ( failed == ok.length ){

			listener.incrementCompletes();

			listener.complete( false );

			return;
		}

		final byte[][] 				encoded_keys 	= failed==0?initial_encoded_keys:new byte[ok.length-failed][];
		final DHTTransportValue[][] value_sets 		= failed==0?initial_value_sets:new DHTTransportValue[ok.length-failed][];

		if ( failed > 0 ){

			int	pos = 0;

			for (int i=0;i<ok.length;i++){

				if ( ok[i] ){

					encoded_keys[ pos ] = initial_encoded_keys[i];
					value_sets[ pos ] 	= initial_value_sets[i];

					pos++;
				}
			}
		}

		final byte[][] 				obs_keys;
		final DHTTransportValue[][]	obs_vals;

		if ( ( flags & DHT.FLAG_OBFUSCATE_LOOKUP ) != 0 ){

			if ( encoded_keys.length != 1 ){

				Debug.out( "inconsistent - expected one key" );
			}

			if ( value_sets[0].length != 1 ){

				Debug.out( "inconsistent - expected one value" );
			}

			obs_keys	= new byte[1][];
			obs_vals	= new DHTTransportValue[1][1];

			obs_keys[0] 	= getObfuscatedKey( encoded_keys[0] );
			obs_vals[0][0]	= getObfuscatedValue( value_sets[0][0], encoded_keys[0] );
		}else{

			obs_keys	= null;
			obs_vals	= null;
		}

			// only diversify on one hit as we're storing at closest 'n' so we only need to
			// do it once for each key

		final boolean[]	diversified = new boolean[encoded_keys.length];

		int	skipped = 0;

		for (final DHTTransportContact contact : contacts) {

			if (router.isID(contact.getID())) {

				// don't send to ourselves!

				skipped++;

			} else {

				boolean skip_this = false;

				synchronized (things_written) {

					if (things_written.contains(contact)) {


						// if we've come back to an already hit contact due to a diversification loop
						// then ignore it

						// Debug.out( "Put: contact encountered for a second time, ignoring" );

						skipped++;

						skip_this = true;

					} else {

						things_written.add(contact);
					}
				}

				if (!skip_this) {

					try {

						for (DHTTransportValue[] value_set : value_sets) {

							for (DHTTransportValue aValue_set : value_set) {

								listener.wrote(contact, aValue_set);
							}
						}

							// each store is going to report its complete event

						listener.incrementCompletes();

						contact.sendStore(
							new DHTTransportReplyHandlerAdapter()
							{
								@Override
								public void
								storeReply(
									DHTTransportContact _contact,
									byte[]				_diversifications )
								{
									boolean	complete_is_async = false;

									try{
										if ( DHTLog.isOn()){
											DHTLog.log( "Store OK " + DHTLog.getString( _contact ));
										}

										router.contactAlive( _contact.getID(), new DHTControlContactImpl(_contact));

											// can be null for old protocol versions

										boolean div_done = false;

										if ( consider_diversification && _diversifications != null ){

											for (int j=0;j<_diversifications.length;j++){

												if ( _diversifications[j] != DHT.DT_NONE && !diversified[j] ){

													div_done = true;

													diversified[j]	= true;

													byte[][]	diversified_keys =
														adapter.diversify( description, _contact, true, false, encoded_keys[j], _diversifications[j], false, getMaxDivDepth());


													logDiversification( _contact, encoded_keys, diversified_keys );

													for (int k=0;k<diversified_keys.length;k++){

														put( 	thread_pool,
																high_priority,
																diversified_keys[k],
																"Diversification of [" + description + "]",
																value_sets[j],
																flags,
																timeout,
																false,
																things_written,
																put_level + 1,
																listener );
													}
												}
											}
										}

										if ( !div_done ){

											if ( obs_keys != null ){

												contact.sendStore(
														new DHTTransportReplyHandlerAdapter()
														{
															@Override
															public void
															storeReply(
																DHTTransportContact _contact,
																byte[]				_diversifications )
															{
																if ( DHTLog.isOn()){
																	DHTLog.log( "Obs store OK " + DHTLog.getString( _contact ));
																}

																listener.complete( false );
															}

															@Override
															public void
															failed(
																DHTTransportContact 	_contact,
																Throwable 				_error )
															{
																if ( DHTLog.isOn()){
																	DHTLog.log( "Obs store failed " + DHTLog.getString( _contact ) + " -> failed: " + _error.getMessage());
																}

																listener.complete( true );
															}
														},
														obs_keys,
														obs_vals,
														immediate );

												complete_is_async = true;
											}
										}
									}finally{

										if ( !complete_is_async ){

											listener.complete( false );
										}
									}
								}

								@Override
								public void
								failed(
									DHTTransportContact 	_contact,
									Throwable 				_error )
								{
									try{
										if ( DHTLog.isOn()){
											DHTLog.log( "Store failed " + DHTLog.getString( _contact ) + " -> failed: " + _error.getMessage());
										}

										router.contactDead( _contact.getID(), false );

									}finally{

										listener.complete( true );
									}
								}

								@Override
								public void
								keyBlockRequest(
									DHTTransportContact		contact,
									byte[]					request,
									byte[]					key_signature )
								{
									DHTStorageBlock	key_block = database.keyBlockRequest( null, request, key_signature );

									if ( key_block != null ){

											// remove this key for any subsequent publishes. Quickest hack
											// is to change it into a random key value - this will be rejected
											// by the recipient as not being close enough anyway

										for (int i=0;i<encoded_keys.length;i++){

											if ( Arrays.equals( encoded_keys[i], key_block.getKey())){

												byte[]	dummy = new byte[encoded_keys[i].length];

												RandomUtils.nextBytes( dummy );

												encoded_keys[i] = dummy;
											}
										}
									}
								}
							},
							encoded_keys,
							value_sets,
							immediate );

					}catch( Throwable e ){

						Debug.printStackTrace(e);

					}
				}
			}
		}

		if ( skipped == contacts.size()){

			listener.incrementCompletes();

			listener.complete( false );
		}
	}

	protected int
	getMaxDivDepth()
	{
		if ( combined_dht_estimate == 0 ){

			getEstimatedDHTSize();
		}

		int max = Math.max( 2, combined_dht_estimate_mag );

		// System.out.println( "net:" + transport.getNetwork() + " - max_div_depth=" + max );

		return( max );
	}

	protected void
	logDiversification(
		final DHTTransportContact		contact,
		final byte[][]					keys,
		final byte[][]					div )
	{
		/*
		System.out.println( "Div check starts for " + contact.getString());

		String	keys_str = "";

		for (int i=0;i<keys.length;i++){

			keys_str += (i==0?"":",") + ByteFormatter.encodeString( keys[i] );
		}

		String	div_str = "";

		for (int i=0;i<div.length;i++){

			div_str += (i==0?"":",") + ByteFormatter.encodeString( div[i] );
		}

		System.out.println( "    " + keys_str + " -> " + div_str );

		new AEThread2( "sdsd", true )
		{
			public void
			run()
			{
				DHTTransportFullStats stats = contact.getStats();

				System.out.println( contact.getString() + "-> " +(stats==null?"<null>":stats.getString()));
			}
		}.start();
		*/
	}

	@Override
	public DHTTransportValue
	getLocalValue(
		byte[]		unencoded_key )
	{
		final byte[]	encoded_key = encodeKey( unencoded_key );

		if ( DHTLog.isOn()){
			DHTLog.log( "getLocalValue for " + DHTLog.getString( encoded_key ));
		}

		DHTDBValue	res = database.get( new HashWrapper( encoded_key ));

		if ( res == null ){

			return( null );
		}

		return( res );
	}

	@Override
	public List<DHTTransportValue>
	getStoredValues(
		byte[]		unencoded_key )
	{
		final byte[]	encoded_key = encodeKey( unencoded_key );

		if ( DHTLog.isOn()){
			DHTLog.log( "getStoredValues for " + DHTLog.getString( encoded_key ));
		}

		List<DHTDBValue>	res = database.getAllValues( new HashWrapper( encoded_key ));

		if ( res == null ){

			return( null );
		}

		ArrayList<DHTTransportValue>	temp = new ArrayList<>(res.size());

		temp.addAll( res );

		return( temp );
	}

	@Override
	public void
	get(
		byte[]						unencoded_key,
		String						description,
		short						flags,
		int							max_values,
		long						timeout,
		boolean						exhaustive,
		boolean						high_priority,
		final DHTOperationListener	get_listener )
	{
		final byte[]	encoded_key = encodeKey( unencoded_key );

		if ( DHTLog.isOn()){
			DHTLog.log( "get for " + DHTLog.getString( encoded_key ));
		}

		final DhtTaskSet[] task_set = { null };

		DHTOperationListenerDemuxer demuxer =
			new DHTOperationListenerDemuxer(
				new DHTOperationListener()
				{
					private AtomicBoolean done = new AtomicBoolean();
					
					@Override
					public void
					searching(
						DHTTransportContact	contact,
						int					level,
						int					active_searches )
					{
						get_listener.searching(contact, level, active_searches);
					}

					@Override
					public boolean
					diversified(
						String				desc )
					{
						return( get_listener.diversified(desc));
					}

					@Override
					public void
					found(
						DHTTransportContact	contact,
						boolean				is_closest )
					{
						get_listener.found(contact,is_closest);
					}

					@Override
					public void
					read(
						DHTTransportContact	contact,
						DHTTransportValue	value )
					{
						get_listener.read(contact, value);
					}

					@Override
					public void
					wrote(
						DHTTransportContact	contact,
						DHTTransportValue	value )
					{
						get_listener.wrote(contact, value);
					}

					@Override
					public void
					complete(
						boolean				timeout )
					{
						if ( !done.compareAndSet( false, true )){
							
							return;
						}
						
						try{
							get_listener.complete(timeout);

						}catch( Throwable e ){

							Debug.out( e );

						}finally{

							if ( task_set[0] != null ){

								task_set[0].cancel();
							}
						}
					}
				});


		task_set[0] = getSupport( encoded_key, description, flags, max_values, timeout, exhaustive, high_priority, demuxer );
	}

	@Override
	public boolean
	isDiversified(
		byte[]		unencoded_key )
	{
		final byte[]	encoded_key = encodeKey( unencoded_key );

		return( adapter.isDiversified( encoded_key ));
	}

	@Override
	public boolean
   	lookup(
   		byte[]							unencoded_key,
   		String							description,
   		long							timeout,
   		final DHTOperationListener		lookup_listener )
	{
		return( lookupEncoded( encodeKey( unencoded_key ), description, timeout, false, lookup_listener ));
	}

	@Override
	public boolean
   	lookupEncoded(
   		byte[]							encoded_key,
   		String							description,
   		long							timeout,
   		boolean							high_priority,
   		final DHTOperationListener		lookup_listener )
	{
		if ( DHTLog.isOn()){
			DHTLog.log( "lookup for " + DHTLog.getString( encoded_key ));
		}

		final AESemaphore	sem = new AESemaphore( "DHTControl:lookup" );

		final	boolean[]	diversified = { false };

		DHTOperationListener	delegate =
			new DHTOperationListener()
			{
				@Override
				public void
				searching(
					DHTTransportContact	contact,
					int					level,
					int					active_searches )
				{
					lookup_listener.searching( contact, level, active_searches );
				}

				@Override
				public void
				found(
					DHTTransportContact	contact,
					boolean				is_closest )
				{
				}

				@Override
				public boolean
				diversified(
					String		desc )
				{
					return( lookup_listener.diversified( desc ));
				}

				@Override
				public void
				read(
					DHTTransportContact	contact,
					DHTTransportValue	value )
				{
				}

				@Override
				public void
				wrote(
					DHTTransportContact	contact,
					DHTTransportValue	value )
				{
				}

				@Override
				public void
				complete(
					boolean				timeout )
				{
					lookup_listener.complete( timeout );

					sem.release();
				}
			};

		lookup( 	external_lookup_pool,
					high_priority,
					encoded_key,
					description,
					(byte)0,
					false,
					timeout,
					search_concurrency,
					1,
					router.getK(),
					new lookupResultHandler( delegate )
					{
						@Override
						public void
						diversify(
							DHTTransportContact	cause,
							byte				diversification_type )
						{
							diversified( "Diversification of [lookup]" );

							diversified[0] = true;
						}

						@Override
						public void
						closest(
							List<DHTTransportContact>	closest )
						{
							for (Object aClosest : closest) {

								lookup_listener.found((DHTTransportContact) aClosest, true);
							}
						}
					});

		sem.reserve();

		return( diversified[0] );
	}

	protected DhtTaskSet
	getSupport(
		final byte[]						initial_encoded_key,
		final String						description,
		final short							flags,
		final int							max_values,
		final long							timeout,
		final boolean						exhaustive,
		final boolean						high_priority,
		final DHTOperationListenerDemuxer	get_listener )
	{
		final DhtTaskSet result = new DhtTaskSet();

			// get the initial starting point for the get - may have previously been diversified

		byte[][]	encoded_keys	= adapter.diversify( description, null, false, true, initial_encoded_key, DHT.DT_NONE, exhaustive, getMaxDivDepth());

		if  ( encoded_keys.length == 0 ){

				// over-diversified

			get_listener.diversified( "Over-diversification of [" + description + "]" );

			get_listener.complete( false );

			return( result );
		}

		for (final byte[] encoded_key : encoded_keys) {

			final boolean[]	diversified = { false };

			boolean	div = !Arrays.equals( encoded_key, initial_encoded_key );

			final String	this_description =
				div?("Diversification of [" + description + "]" ):description;

			if ( div ){

				if ( !get_listener.diversified( this_description )){

					continue;
				}
			}

			boolean	is_stats_query = (flags & DHT.FLAG_STATS ) != 0;

			result.add(
				lookup( external_lookup_pool,
					high_priority,
					encoded_key,
					this_description,
					flags,
					true,
					timeout,
					is_stats_query?search_concurrency*2:search_concurrency,
					max_values,
					router.getK(),
					new lookupResultHandler( get_listener )
					{
						private final List<DHTTransportValue>	found_values	= new ArrayList<>();

						@Override
						public void
						diversify(
							DHTTransportContact	cause,
							byte				diversification_type )
						{
							boolean ok_to_div = diversified( "Diversification of [" + this_description + "]" );

								// we only want to follow one diversification

							if ( ok_to_div && !diversified[0]){

								diversified[0] = true;

								int	rem = max_values==0?0:( max_values - found_values.size());

								if ( max_values == 0 || rem > 0 ){

									byte[][]	diversified_keys = adapter.diversify( description, cause, false, false, encoded_key, diversification_type, exhaustive, getMaxDivDepth());

									if ( diversified_keys.length > 0 ){

											// should return a max of 1 (0 if diversification refused)
											// however, could change one day to search > 1

										for (int j=0;j<diversified_keys.length;j++){

											if ( !result.isCancelled()){

												result.add(
													getSupport( diversified_keys[j], "Diversification of [" + this_description + "]", flags, rem,  timeout, exhaustive, high_priority, get_listener ));
											}
										}
									}
								}
							}
						}

						@Override
						public void
						read(
							DHTTransportContact	contact,
							DHTTransportValue	value )
						{
							found_values.add( value );

							super.read( contact, value );
						}

						@Override
						public void
						closest(
							List	closest )
						{
							/* we don't use teh cache-at-closest kad feature
							if ( found_values.size() > 0 ){

								DHTTransportValue[]	values = new DHTTransportValue[found_values.size()];

								found_values.toArray( values );

									// cache the values at the 'n' closest seen locations

								for (int k=0;k<Math.min(cache_at_closest_n,closest.size());k++){

									DHTTransportContact	contact = (DHTTransportContact)(DHTTransportContact)closest.get(k);

									for (int j=0;j<values.length;j++){

										wrote( contact, values[j] );
									}

									contact.sendStore(
											new DHTTransportReplyHandlerAdapter()
											{
												public void
												storeReply(
													DHTTransportContact _contact,
													byte[]				_diversifications )
												{
														// don't consider diversification for cache stores as we're not that
														// bothered

													DHTLog.log( "Cache store OK " + DHTLog.getString( _contact ));

													router.contactAlive( _contact.getID(), new DHTControlContactImpl(_contact));
												}

												public void
												failed(
													DHTTransportContact 	_contact,
													Throwable 				_error )
												{
													DHTLog.log( "Cache store failed " + DHTLog.getString( _contact ) + " -> failed: " + _error.getMessage());

													router.contactDead( _contact.getID(), false );
												}
											},
											new byte[][]{ encoded_key },
											new DHTTransportValue[][]{ values });
								}
							}
							*/
						}
					}));
		}

		return( result );
	}

	@Override
	public byte[]
	remove(
		byte[]					unencoded_key,
		String					description,
		DHTOperationListener	listener )
	{
		final byte[]	encoded_key = encodeKey( unencoded_key );

		if ( DHTLog.isOn()){
			DHTLog.log( "remove for " + DHTLog.getString( encoded_key ));
		}

		DHTDBValue	res = database.remove( local_contact, new HashWrapper( encoded_key ));

		if ( res == null ){

				// not found locally, nothing to do

			return( null );

		}else{

				// we remove a key by pushing it back out again with zero length value

			put( 	external_put_pool,
					false,
					encoded_key,
					description,
					res,
					(byte)res.getFlags(),
					0,
					true,
					new HashSet(),
					1,
					new DHTOperationListenerDemuxer( listener ));

			return( res.getValue());
		}
	}

	@Override
	public byte[]
	remove(
		DHTTransportContact[]	contacts,
		byte[]					unencoded_key,
		String					description,
		DHTOperationListener	listener )
	{
		final byte[]	encoded_key = encodeKey( unencoded_key );

		if ( DHTLog.isOn()){
			DHTLog.log( "remove for " + DHTLog.getString( encoded_key ));
		}

		DHTDBValue	res = database.remove( local_contact, new HashWrapper( encoded_key ));

		if ( res == null ){

				// not found locally, nothing to do

			return( null );

		}else{

			List<DHTTransportContact>	contacts_l = new ArrayList<>( contacts.length );

			Collections.addAll(contacts_l, contacts);

			put( 	external_put_pool,
					true,
					new byte[][]{ encoded_key },
					"Store of [" + description + "]",
					new DHTTransportValue[][]{{ res }},
					(byte)res.getFlags(),
					contacts_l,
					0,
					new DHTOperationListenerDemuxer( listener ),
					true,
					new HashSet(),
					1,
					true );

			return( res.getValue());
		}
	}

		/**
		 * The lookup method returns up to K closest nodes to the target
		 */


	protected DhtTask
	lookup(
		final ThreadPool 			thread_pool,
		boolean 					high_priority,
		final byte[] 				_lookup_id,
		final String 				description,
		final short					flags,
		final boolean 				value_search,
		final long 					timeout,
		final int 					concurrency,
		final int 					max_values,
		final int 					search_accuracy,
		final lookupResultHandler 	handler )
	{
		final byte[] 	lookup_id;
		final byte[]	obs_value;

		if (( flags & DHT.FLAG_OBFUSCATE_LOOKUP ) != 0 ){

			lookup_id 	= getObfuscatedKey( _lookup_id );
			obs_value	= getObfuscatedValue( _lookup_id );

		}else{

			lookup_id 	= _lookup_id;
			obs_value	= null;
		}

		DhtTask	task =
			new DhtTask(thread_pool)
			{
				boolean timeout_occurred = false;

				// keep querying successively closer nodes until we have got responses from the K
				// closest nodes that we've seen. We might get a bunch of closer nodes that then
				// fail to respond, which means we have reconsider further away nodes
				// we keep a list of nodes that we have queried to avoid re-querying them
				// we keep a list of nodes discovered that we have yet to query
				// we have a parallel search limit of A. For each A we effectively loop grabbing
				// the currently closest unqueried node, querying it and adding the results to the
				// yet-to-query-set (unless already queried)
				// we terminate when we have received responses from the K closest nodes we know
				// about (excluding failed ones)
				// Note that we never widen the root of our search beyond the initial K closest
				// that we know about - this could be relaxed
				// contacts remaining to query
				// closest at front

				Set<DHTTransportContact> contacts_to_query;
				AEMonitor contacts_to_query_mon;
				Map<DHTTransportContact,Object[]> level_map;

				// record the set of contacts we've queried to avoid re-queries
				ByteArrayHashMap<DHTTransportContact> contacts_queried;
				// record the set of contacts that we've had a reply from
				// furthest away at front
				Set<DHTTransportContact> ok_contacts;
				// this handles the search concurrency

				int idle_searches;
				int active_searches;
				int values_found;
				int value_replies;
				Set<HashWrapper> values_found_set;
				boolean key_blocked;
				long start;

				TimerEvent timeoutEvent;

				private int runningState = 1; // -1 terminated, 0 waiting, 1 running
				private int freeTasksCount = concurrency;


				private boolean	cancelled;

				// start the lookup
				@Override
				public void	runSupport()
				{
					startLookup();
				}


				private void startLookup()
				{
					contacts_to_query_mon = new AEMonitor("DHTControl:ctq");

					contacts_to_query = getClosestContactsSet(lookup_id, K, false);

					level_map = new LightHashMap<>();

					// record the set of contacts we've queried to avoid re-queries
					contacts_queried = new ByteArrayHashMap<>();

					// record the set of contacts that we've had a reply from
					// furthest away at front
					ok_contacts = new sortedTransportContactSet(lookup_id, false).getSet();
					// this handles the search concurrency

					values_found_set = new HashSet<>();


					start = SystemTime.getMonotonousTime();

					last_lookup = SystemTime.getCurrentTime();
					handler.incrementCompletes();

					for (DHTTransportContact contact : contacts_to_query) {
						handler.found(contact, false);
						level_map.put(contact, new Object[]{0, null});
					}

					if ( DHTLog.isOn()){
						DHTLog.log("lookup for " + DHTLog.getString(lookup_id));
					}

					if (value_search && database.isKeyBlocked(lookup_id)){

						DHTLog.log("lookup: terminates - key blocked");
						// bail out and pretend everything worked with zero results
						terminateLookup(false);
						return;
					}

					if (timeout > 0)
					{
						timeoutEvent = SimpleTimer.addEvent("DHT lookup timeout", SystemTime.getCurrentTime()+timeout, new TimerEventPerformer() {
							@Override
							public void perform(TimerEvent event) {
								if ( DHTLog.isOn()){
									DHTLog.log("lookup: terminates - timeout");
								}
								//System.out.println("timeout");
								timeout_occurred = true;
								terminateLookup(false);
							}
						});
					}



					lookupSteps();
				}

				void terminateLookup(boolean error)
				{
					if(timeoutEvent != null)
						timeoutEvent.cancel();

					synchronized (this)
					{
						if(runningState == -1)
							return;
						runningState = -1;
					}

					try{
						if(!error){

							// maybe unterminated searches still going on so protect ourselves
							// against concurrent modification of result set
							List<DHTTransportContact> closest_res;
							try
							{
								contacts_to_query_mon.enter();

								if (DHTLog.isOn())
								{
									DHTLog.log("lookup complete for " + DHTLog.getString(lookup_id));
									DHTLog.log("    queried = " + DHTLog.getString(contacts_queried));
									DHTLog.log("    to query = " + DHTLog.getString(contacts_to_query));
									DHTLog.log("    ok = " + DHTLog.getString(ok_contacts));
								}

								closest_res = new ArrayList<>(ok_contacts);
								// we need to reverse the list as currently closest is at the end
								Collections.reverse(closest_res);

								if (timeout <= 0 && !value_search)
									// we can use the results of this to estimate the DHT size
									estimateDHTSize(lookup_id, contacts_queried.values(), search_accuracy);

							} finally
							{
								contacts_to_query_mon.exit();
							}

							handler.closest(closest_res);
						}

						handler.complete(timeout_occurred);

					}finally{

						releaseToPool();
					}
				}

				private synchronized boolean reserve()
				{
					if(freeTasksCount <= 0 || runningState == -1)
					{
						//System.out.println("reserve-exit");
						if(runningState == 1)
							runningState = 0;
						return false;
					}

					freeTasksCount--;
					return true;
				}

				synchronized void release()
				{
					freeTasksCount++;
					if(runningState == 0)
					{
						//System.out.println("release-start");
						runningState = 1;
						new AEThread2("DHT lookup runner",true) {
							@Override
							public void run() {
								thread_pool.registerThreadAsChild(worker);
								lookupSteps();
								thread_pool.deregisterThreadAsChild(worker);
							}
						}.start();
					}
				}

				@Override
				protected synchronized void
				cancel()
				{
					//if ( runningState != -1 ){

						// System.out.println( "Task cancelled" );
					//}

					cancelled = true;
				}

				// individual lookup steps
				void lookupSteps() {
					try
					{
						boolean terminate = false;

						while ( !cancelled )
						{
							if (timeout > 0)
							{
								long now = SystemTime.getMonotonousTime();

								long remaining = timeout - (now - start);
								if (remaining <= 0)
								{
									if ( DHTLog.isOn()){
										DHTLog.log("lookup: terminates - timeout");
									}

									timeout_occurred = true;
									terminate = true;
									break;
								}

								if(!reserve())
									break; // temporary stop, will be revived by release() or until a timeout occurs


							} else if(!reserve())
								break; // temporary stop, will be revived by release()*/

							try
							{
								contacts_to_query_mon.enter();

									// for stats queries the values returned are unique to target so don't assume 2 replies sufficient

								if (values_found >= max_values || (( flags & DHT.FLAG_STATS ) == 0 &&  value_replies >= 2 ))
								{
									// all hits should have the same values anyway...
									terminate = true;
									break;
								}

								// if we've received a key block then easiest way to terminate the query is to
								// dump any outstanding targets
								if (key_blocked)
									contacts_to_query.clear();

								// if nothing pending then we need to wait for the results of a previous
								// search to arrive. Of course, if there are no searches active then
								// we've run out of things to do
								if (contacts_to_query.size() == 0)
								{
									if (active_searches == 0)
									{
										if ( DHTLog.isOn()){
											DHTLog.log("lookup: terminates - no contacts left to query");
										}

										terminate = true;
										break;
									}
									idle_searches++;
									continue;
								}

								// select the next contact to search
								DHTTransportContact closest = contacts_to_query.iterator().next();

								// if the next closest is further away than the furthest successful hit so
								// far and we have K hits, we're done
								if (ok_contacts.size() == search_accuracy)
								{
									DHTTransportContact furthest_ok = ok_contacts.iterator().next();
									int distance = computeAndCompareDistances(furthest_ok.getID(), closest.getID(), lookup_id);
									if (distance <= 0)
									{
										if ( DHTLog.isOn()){
											DHTLog.log("lookup: terminates - we've searched the closest " + search_accuracy + " contacts");
										}

										terminate = true;
										break;
									}
								}
								// we optimise the first few entries based on their Vivaldi distance. Only a few
								// however as we don't want to start too far away from the target.
								if (contacts_queried.size() < concurrency)
								{
									DHTNetworkPosition[] loc_nps = local_contact.getNetworkPositions();
									DHTTransportContact vp_closest = null;
									Iterator vp_it = contacts_to_query.iterator();
									int vp_count_limit = (concurrency * 2) - contacts_queried.size();
									int vp_count = 0;
									float best_dist = Float.MAX_VALUE;
									while (vp_it.hasNext() && vp_count < vp_count_limit)
									{
										vp_count++;
										DHTTransportContact entry = (DHTTransportContact) vp_it.next();
										DHTNetworkPosition[] rem_nps = entry.getNetworkPositions();

										float dist = DHTNetworkPositionManager.estimateRTT(loc_nps, rem_nps);
										if ((!Float.isNaN(dist)) && dist < best_dist)
										{
											best_dist = dist;
											vp_closest = entry;
											// System.out.println( start + ": lookup for " + DHTLog.getString2( lookup_id ) + ": vp override (dist = " + dist + ")");
										}

										if (vp_closest != null) // override ID closest with VP closes
											closest = vp_closest;
									}
								}

								final DHTTransportContact f_closest = closest;

								contacts_to_query.remove(closest);
								contacts_queried.put( closest.getID(), closest);
								// never search ourselves!
								if (router.isID(closest.getID()))
								{
									release();
									continue;
								}
								final int search_level = (Integer) level_map.get(closest)[0];
								active_searches++;
								handler.searching(closest, search_level, active_searches);


								DHTTransportReplyHandlerAdapter replyHandler = new DHTTransportReplyHandlerAdapter() {
									private boolean	value_reply_received	= false;

									@Override
									public void findNodeReply(DHTTransportContact target_contact, DHTTransportContact[] reply_contacts) {
										try
										{
											if ( DHTLog.isOn()){
												DHTLog.log("findNodeReply: " + DHTLog.getString(reply_contacts));
											}

											router.contactAlive(target_contact.getID(), new DHTControlContactImpl(target_contact));
											for (DHTTransportContact contact : reply_contacts) {
												// ignore responses that are ourselves
												if (compareDistances(router.getID(), contact.getID()) == 0)
													continue;

												// dunno if its alive or not, however record its existance
												router.contactKnown(contact.getID(), new DHTControlContactImpl(contact), false);
											}
											try
											{
												contacts_to_query_mon.enter();
												ok_contacts.add(target_contact);
												if (ok_contacts.size() > search_accuracy)
												{
													// delete the furthest away
													Iterator ok_it = ok_contacts.iterator();
													ok_it.next();
													ok_it.remove();
												}
												for (DHTTransportContact contact : reply_contacts) {
													// ignore responses that are ourselves
													if (compareDistances(router.getID(), contact.getID()) == 0)
														continue;

													if (contacts_queried.get(contact.getID()) == null && (!contacts_to_query.contains(contact))) {
														if (DHTLog.isOn()) {
															DHTLog.log("    new contact for query: " + DHTLog.getString(contact));
														}

														contacts_to_query.add(contact);
														handler.found(contact, false);
														level_map.put(contact, new Object[]{search_level + 1, target_contact});
														if (idle_searches > 0) {
															idle_searches--;
															release();
														}
													} else {
														// DHTLog.log( "    already queried: " + DHTLog.getString( contact ));
													}
												}
											} finally
											{
												contacts_to_query_mon.exit();
											}
										} finally
										{
											try
											{
												contacts_to_query_mon.enter();
												active_searches--;
											} finally
											{
												contacts_to_query_mon.exit();
											}
											release();
										}
									}

									@Override
									public void
									findValueReply(
										DHTTransportContact 	contact,
										DHTTransportValue[] 	values,
										byte 					diversification_type, 	// hack - this is set to 99 when recursing here during obsfuscated lookup
										boolean 				more_to_come )
									{
										if ( DHTLog.isOn()){
											DHTLog.log("findValueReply: " + DHTLog.getString(values) + ",mtc=" + more_to_come + ", dt=" + diversification_type);
										}

										boolean	obs_recurse = false;

										if ( diversification_type == 99 ){

											obs_recurse = true;

											diversification_type = DHT.DT_NONE;
										}

										try
										{
											if (!key_blocked && diversification_type != DHT.DT_NONE){

													// diversification instruction

												if (( flags & DHT.FLAG_STATS ) == 0 ){

														// ignore for stats queries as we're after the
														// target key's stats, not the diversification
														// thereof

													handler.diversify(contact, diversification_type);
												}
											}

											value_reply_received = true;
											router.contactAlive(contact.getID(), new DHTControlContactImpl(contact));
											int new_values = 0;
											if (!key_blocked)
											{
												for (DHTTransportValue value : values) {
													DHTTransportContact originator = value.getOriginator();
													// can't just use originator id as this value can be DOSed (see DB code)
													byte[] originator_id = originator.getID();
													byte[] value_bytes = value.getValue();
													byte[] value_id = new byte[originator_id.length + value_bytes.length];
													System.arraycopy(originator_id, 0, value_id, 0, originator_id.length);
													System.arraycopy(value_bytes, 0, value_id, originator_id.length, value_bytes.length);
													HashWrapper x = new HashWrapper(value_id);

													if (!values_found_set.contains(x)) {

														if (obs_value != null && !obs_recurse) {

															// we have read the marker value, now issue a direct read with the
															// real key

															if (Arrays.equals(obs_value, value_bytes)) {

																more_to_come = true;

																final DHTTransportReplyHandlerAdapter f_outer = this;

																f_closest.sendFindValue(
																	new DHTTransportReplyHandlerAdapter()
																	{
																		@Override
																		public void
																		findValueReply(
																			DHTTransportContact 	contact,
																			DHTTransportValue[] 	values,
																			byte 					diversification_type,
																			boolean 				more_to_come )
																		{
																			if ( diversification_type == DHT.DT_NONE ){

																				f_outer.findValueReply( contact, values, (byte)99, false );
																			}
																		}

																		@Override
																		public void
																		failed(
																			DHTTransportContact 	contact,
																			Throwable 				error )
																		{
																			f_outer.failed( contact, error );
																		}
																	},
																	_lookup_id, 1, flags );

																break;
															}
														}else{
															new_values++;
															values_found_set.add(x);
															handler.read(contact, value);
														}
													}
												}
											}
											try
											{
												contacts_to_query_mon.enter();
												if (!more_to_come)
													value_replies++;
												values_found += new_values;
											} finally
											{
												contacts_to_query_mon.exit();
											}
										} finally
										{
											if (!more_to_come)
											{
												try
												{
													contacts_to_query_mon.enter();
													active_searches--;
												} finally
												{
													contacts_to_query_mon.exit();
												}
												release();
											}
										}
									}

									@Override
									public void findValueReply(DHTTransportContact contact, DHTTransportContact[] contacts) {
										findNodeReply(contact, contacts);
									}

									@Override
									public void failed(DHTTransportContact target_contact, Throwable error) {
										try
										{
											// if at least one reply has been received then we
											// don't treat subsequent failure as indication of
											// a contact failure (just packet loss)
											if (!value_reply_received)
											{
												if ( DHTLog.isOn()){
													DHTLog.log("findNode/findValue " + DHTLog.getString(target_contact) + " -> failed: " + error.getMessage());
												}

												router.contactDead(target_contact.getID(), false);
											}
										} finally
										{
											try
											{
												contacts_to_query_mon.enter();
												active_searches--;
											} finally
											{
												contacts_to_query_mon.exit();
											}
											release();
										}
									}

									@Override
									public void keyBlockRequest(DHTTransportContact contact, byte[] request, byte[] key_signature) {
										// we don't want to kill the contact due to this so indicate that
										// it is ok by setting the flag
										if (database.keyBlockRequest(null, request, key_signature) != null)
											key_blocked = true;
									}
								};


								router.recordLookup(lookup_id);
								if (value_search)
								{
									int rem = max_values - values_found;
									if (rem <= 0)
									{
										Debug.out("eh?");
										rem = 1;
									}
									closest.sendFindValue(replyHandler, lookup_id, rem, flags);
								} else
								{
									closest.sendFindNode(replyHandler, lookup_id, flags );
								}
							} finally
							{
								contacts_to_query_mon.exit();
							}
						}

						if(terminate){
							terminateLookup(false);
						}else if ( cancelled ){
							terminateLookup( true );
						}
					} catch (Throwable e) {
						Debug.printStackTrace(e);
						terminateLookup(true);
					}
				}



				@Override
				public byte[] getTarget() {
					return (lookup_id);
				}

				@Override
				public String getDescription() {
					return (description);
				}

				@Override
				public DHTControlActivity.ActivityState
				getCurrentState()
				{
					DHTTransportContact	local = local_contact;

					ANImpl root_node = new ANImpl( local );

					String result_str;

					if ( timeout_occurred ){
						result_str = "Timeout";
					}else{
						long elapsed = SystemTime.getMonotonousTime() - start;

						String	elapsed_str = elapsed<1000?(elapsed+"ms"):((elapsed/1000)+"s");

						if ( value_search ){

							result_str = values_found + " hits, time=" + elapsed_str;

						}else{

							result_str = "time=" + elapsed_str;
						}
					}

					ASImpl result = new ASImpl( root_node, result_str );

						// can be null if 'added' listener callback runs before class init complete...

					if ( contacts_to_query_mon != null ){

						contacts_to_query_mon.enter();

						try{

							if ( contacts_queried != null && level_map != null ){

								List<Map.Entry<DHTTransportContact, Object[]>> lm_entries = new ArrayList<>(level_map.entrySet());

								Collections.sort(
									lm_entries,
									new Comparator<Map.Entry<DHTTransportContact, Object[]>>()
									{
										@Override
										public int compare(
												Entry<DHTTransportContact, Object[]> o1,
												Entry<DHTTransportContact, Object[]> o2)
										{
											int	l1 = (Integer)o1.getValue()[0];
											int	l2 = (Integer)o2.getValue()[0];

											return( l1 - l2 );
										}
									});

								Set<DHTTransportContact>	qd = new HashSet<>(contacts_queried.values());

								Map<DHTTransportContact,ANImpl>	node_map = new HashMap<>();

								node_map.put( local, root_node );

								for ( Map.Entry<DHTTransportContact, Object[]> lme: lm_entries ){

									DHTTransportContact contact = lme.getKey();

									if ( !qd.contains( contact )){

										continue;
									}


									Object[] entry = lme.getValue();

									DHTTransportContact parent 	= (DHTTransportContact)entry[1];

									if ( parent == null ){

										parent = local;
									}

									ANImpl p_node = node_map.get( parent );

									if ( p_node == null ){

										Debug.out( "eh" );

									}else{

										ANImpl new_node = new ANImpl( contact );

										node_map.put( contact, new_node );

										p_node.add( new_node );
									}
								}
							}

						}finally{

							contacts_to_query_mon.exit();
						}
					}

					return( result );
				}
			};

		thread_pool.run( task, high_priority, true);

		return( task );
	}

	private static class
	ASImpl
		implements DHTControlActivity.ActivityState
	{
		private final ANImpl	root;
		private int				depth 	= -1;
		private final String			result;

		ASImpl(
			ANImpl	_root,
			String	_result )
		{
			root 	= _root;
			result	= _result;
		}

		@Override
		public ActivityNode
		getRootNode()
		{
			return( root );
		}

		@Override
		public String
		getResult()
		{
			return( result );
		}

		@Override
		public int
		getDepth()
		{
			if ( depth == -1 ){

				depth = root.getMaxDepth() - 1;

				if ( depth == 0 ){

					depth = 1;
				}
			}

			return( depth );
		}

		@Override
		public String
		getString()
		{
			return( root.getString());
		}
	}

	private static class
	ANImpl
		implements DHTControlActivity.ActivityNode
	{
		private final DHTTransportContact		contact;
		private final List<ActivityNode>		kids = new ArrayList<>();

		ANImpl(
			DHTTransportContact	_contact )
		{
			contact = _contact;
		}

		@Override
		public DHTTransportContact
		getContact()
		{
			return( contact );
		}

		@Override
		public List<ActivityNode>
		getChildren()
		{
			return( kids );
		}

		int
		getMaxDepth()
		{
			int	max = 0;

			for ( ActivityNode n: kids ){

				max = Math.max( max, ((ANImpl)n).getMaxDepth());
			}

			return( max + 1 );
		}

		void
		add(
			ActivityNode	n )
		{
			kids.add( n );
		}
		String
		getString()
		{
			String str = "";

			if ( kids.size() > 0 ){

				for ( ActivityNode k: kids ){

					ANImpl a = (ANImpl)k;

					str += (str.length()==0?"":",") + a.getString();
				}

				str = "["  + str + "]";
			}

			return( AddressUtils.getHostAddress( contact.getAddress()) + " - " + str );
		}
	}

		// Request methods

	@Override
	public void
	pingRequest(
		DHTTransportContact originating_contact )
	{
		if ( DHTLog.isOn()){
			DHTLog.log( "pingRequest from " + DHTLog.getString( originating_contact.getID()));
		}

		router.contactAlive( originating_contact.getID(), new DHTControlContactImpl(originating_contact));
	}

	@Override
	public void
	keyBlockRequest(
		DHTTransportContact originating_contact,
		byte[]				request,
		byte[]				sig )
	{
		if ( DHTLog.isOn()){
			DHTLog.log( "keyBlockRequest from " + DHTLog.getString( originating_contact.getID()));
		}

		router.contactAlive( originating_contact.getID(), new DHTControlContactImpl(originating_contact));

		database.keyBlockRequest( originating_contact, request, sig );
	}

	@Override
	public DHTTransportStoreReply
	storeRequest(
		DHTTransportContact 	originating_contact,
		byte[][]				keys,
		DHTTransportValue[][]	value_sets )
	{
		byte[] originator_id = originating_contact.getID();

		router.contactAlive( originator_id, new DHTControlContactImpl(originating_contact));

		if ( DHTLog.isOn()){

			DHTLog.log( "storeRequest from " + DHTLog.getString( originating_contact )+ ", keys = " + keys.length );
		}

		byte[]	diverse_res = new byte[ keys.length];

		Arrays.fill( diverse_res, DHT.DT_NONE );

		if ( keys.length != value_sets.length ){

			Debug.out( "DHTControl:storeRequest - invalid request received from " + originating_contact.getString() + ", keys and values length mismatch");

			return( new DHTTransportStoreReplyImpl(  diverse_res ));
		}

		DHTStorageBlock	blocked_details	= null;

		// System.out.println( "storeRequest: received " + originating_contact.getRandomID() + " from " + originating_contact.getAddress());

		//System.out.println( "store request: keys=" + keys.length );

		if ( keys.length > 0 ){

			boolean	cache_forward = false;

			for ( DHTTransportValue[] values: value_sets ){

				for ( DHTTransportValue value: values ){

					if ( !Arrays.equals( originator_id, value.getOriginator().getID())){

						cache_forward	= true;

						break;
					}
				}

				if ( cache_forward ){

					break;
				}
			}

				// don't start accepting cache forwards until we have a good idea of our
				// acceptable key space

			if ( cache_forward && !isSeeded()){

				//System.out.println( "not seeded" );

				if ( DHTLog.isOn()){
					DHTLog.log( "Not storing keys as not yet seeded" );
				}

			}else if ( !verifyContact( originating_contact, !cache_forward )){

				//System.out.println( "verification fail" );

				logger.log( "Verification of contact '" + originating_contact.getName() + "' failed for store operation" );

			}else{

					// get the closest contacts to me

				byte[]	my_id	= local_contact.getID();

				int	c_factor = router.getK();

				DHTStorageAdapter sad = adapter.getStorageAdapter();

				if ( sad != null && DHTFactory.isLargeNetwork( sad.getNetwork())){

					c_factor += ( c_factor/2 );
				}

				boolean store_it = true;

				if ( cache_forward ){

					long	now = SystemTime.getMonotonousTime();

					if ( now - rbs_time < 10*1000 && Arrays.equals( originator_id, rbs_id )){

						// System.out.println( "contact too far away - repeat" );

						store_it = false;

					}else{
							// make sure the originator is in our group

						List<DHTTransportContact>closest_contacts = getClosestContactsList( my_id, c_factor, true );

						DHTTransportContact	furthest = closest_contacts.get( closest_contacts.size()-1);

						if ( computeAndCompareDistances( furthest.getID(), originator_id, my_id ) < 0 ){

							rbs_id 		= originator_id;
							rbs_time	= now;

							// System.out.println( "contact too far away" );

							if ( DHTLog.isOn()){
								DHTLog.log( "Not storing keys as cache forward and sender too far away" );
							}

							store_it	= false;
						}
					}
				}

				if ( store_it ){

					for (int i=0;i<keys.length;i++){

						byte[]			key = keys[i];

						HashWrapper		hw_key		= new HashWrapper( key );

						DHTTransportValue[]	values 	= value_sets[i];

						if ( DHTLog.isOn()){
							DHTLog.log( "    key=" + DHTLog.getString(key) + ", value=" + DHTLog.getString(values));
						}

							// make sure the key isn't too far away from us

						if ( 	!( 	database.hasKey( hw_key ) ||
									isIDInClosestContacts( my_id, key, c_factor, true ))){

							// System.out.println( "key too far away" );

							if ( DHTLog.isOn()){
								DHTLog.log( "Not storing keys as cache forward and sender too far away" );
							}
						}else{

							diverse_res[i] = database.store( originating_contact, hw_key, values );

							if ( blocked_details == null ){

								blocked_details = database.getKeyBlockDetails( key );
							}
						}
					}
				}
			}
		}

			// fortunately we can get away with this as diversifications are only taken note of by initial, single value stores
			// and not by the multi-value cache forwards...

		if ( blocked_details == null ){

			return( new DHTTransportStoreReplyImpl( diverse_res ));

		}else{

			return( new DHTTransportStoreReplyImpl( blocked_details.getRequest(), blocked_details.getCertificate()));
		}
	}

	@Override
	public DHTTransportQueryStoreReply
	queryStoreRequest(
		DHTTransportContact 		originating_contact,
		int							header_len,
		List<Object[]>				keys )
	{
		router.contactAlive( originating_contact.getID(), new DHTControlContactImpl(originating_contact));

		if ( DHTLog.isOn()){

			DHTLog.log( "queryStoreRequest from " + DHTLog.getString( originating_contact )+ ", header_len=" + header_len + ", keys=" + keys.size());
		}

		if ( originating_contact.getRandomIDType() == DHTTransportContact.RANDOM_ID_TYPE1 ){

			int	rand = generateSpoofID( originating_contact );

			originating_contact.setRandomID( rand );

		}else{

			byte[]	rand = generateSpoofID2( originating_contact );

			originating_contact.setRandomID2( rand );
		}

		return( database.queryStore( originating_contact, header_len, keys ));
	}

	@Override
	public DHTTransportContact[]
	findNodeRequest(
   		DHTTransportContact originating_contact,
   		byte[]				id )
	{
		return( findNodeRequest( originating_contact, id, false ));
	}

	private DHTTransportContact[]
	findNodeRequest(
		DHTTransportContact originating_contact,
		byte[]				id,
		boolean				already_logged )
	{
		if ( !already_logged && DHTLog.isOn()){
			DHTLog.log( "findNodeRequest from " + DHTLog.getString( originating_contact.getID()));
		}

		router.contactAlive( originating_contact.getID(), new DHTControlContactImpl(originating_contact));

		List	l;

		if ( id.length == router.getID().length ){

			l = getClosestKContactsList( id, true );	// parg: switched to live-only to reduce client lookup steps 2013/02/06

		}else{

				// this helps both protect against idiot queries and also saved bytes when we use findNode
				// to just get a random ID prior to cache-forwards

			l = new ArrayList();
		}

		final DHTTransportContact[]	res = new DHTTransportContact[l.size()];

		l.toArray( res );

		if ( originating_contact.getRandomIDType() == DHTTransportContact.RANDOM_ID_TYPE1 ){

			int	rand = generateSpoofID( originating_contact );

			originating_contact.setRandomID( rand );

		}else{

			byte[]	rand = generateSpoofID2( originating_contact );

			originating_contact.setRandomID2( rand );
		}

		return( res );
	}

	@Override
	public DHTTransportFindValueReply
	findValueRequest(
		DHTTransportContact originating_contact,
		byte[]				key,
		int					max_values,
		short				flags )
	{
		if ( DHTLog.isOn()){
			DHTLog.log( "findValueRequest from " + DHTLog.getString( originating_contact.getID()));
		}

		DHTDBLookupResult	result	= database.get( originating_contact, new HashWrapper( key ), max_values, flags, true );

		if ( result != null ){

			if ( originating_contact.getRandomIDType() == DHTTransportContact.RANDOM_ID_TYPE2 ){

				byte[]	rand = generateSpoofID2( originating_contact );

				originating_contact.setRandomID2( rand );
			}

			router.contactAlive( originating_contact.getID(), new DHTControlContactImpl(originating_contact));

			DHTStorageBlock	block_details = database.getKeyBlockDetails( key );

			if ( block_details == null ){

				return( new DHTTransportFindValueReplyImpl( result.getDiversificationType(), result.getValues()));

			}else{

				return( new DHTTransportFindValueReplyImpl( block_details.getRequest(), block_details.getCertificate()));

			}
		}else{

			return( new DHTTransportFindValueReplyImpl( findNodeRequest( originating_contact, key, true )));
		}
	}

	@Override
	public DHTTransportFullStats
	statsRequest(
		DHTTransportContact	contact )
	{
		return( stats );
	}

	protected void
	requestPing(
		DHTRouterContact	contact )
	{
		((DHTControlContact)contact.getAttachment()).getTransportContact().sendPing(
				new DHTTransportReplyHandlerAdapter()
				{
					@Override
					public void
					pingReply(
						DHTTransportContact _contact )
					{
						if ( DHTLog.isOn()){
							DHTLog.log( "ping OK " + DHTLog.getString( _contact ));
						}

						router.contactAlive( _contact.getID(), new DHTControlContactImpl(_contact));
					}

					@Override
					public void
					failed(
						DHTTransportContact 	_contact,
						Throwable				_error )
					{
						if ( DHTLog.isOn()){
							DHTLog.log( "ping " + DHTLog.getString( _contact ) + " -> failed: " + _error.getMessage());
						}

						router.contactDead( _contact.getID(), false );
					}
				});
	}

	protected void
	nodeAddedToRouter(
		DHTRouterContact	new_contact )
	{
		if ( DISABLE_REPLICATE_ON_JOIN ){

			if ( !new_contact.hasBeenAlive()){

				requestPing( new_contact );
			}

			return;
		}

			// ignore ourselves

		if ( router.isID( new_contact.getID())){

			return;
		}

		// when a new node is added we must check to see if we need to transfer
		// any of our values to it.

		Map	keys_to_store	= new HashMap();

		DHTStorageBlock[]	direct_key_blocks = database.getDirectKeyBlocks();

		if ( database.isEmpty() && direct_key_blocks.length == 0 ){

			// nothing to do, ping it if it isn't known to be alive

			if ( !new_contact.hasBeenAlive()){

				requestPing( new_contact );
			}

			return;
		}

			// see if we're one of the K closest to the new node

			// optimise to avoid calculating for things obviously too far away

		boolean	perform_closeness_check = true;

		byte[] router_id 	= router.getID();
		byte[] contact_id	= new_contact.getID();

		byte[] distance = computeDistance( router_id, contact_id );

		long now = SystemTime.getCurrentTime();

		byte[] nacul = node_add_check_uninteresting_limit;

			// time limit to pick up router changes caused by contacts being deleted

		if ( now - last_node_add_check < 30*1000 && nacul != null ){

			int res = compareDistances( nacul, distance );

			/*
			System.out.println(
				"r=" + ByteFormatter.encodeString( router_id ) +
				",c=" +	ByteFormatter.encodeString( contact_id ) +
				",d=" + ByteFormatter.encodeString( distance ) +
				",l=" + ByteFormatter.encodeString( nacul ) +
				",r=" + res );
			*/

			if ( res < 0 ){

				perform_closeness_check = false;
			}
		}else{

			last_node_add_check					= now;
			node_add_check_uninteresting_limit 	= nacul = null;
		}

		boolean	close	= false;

		if ( perform_closeness_check ){

			List	closest_contacts = getClosestKContactsList( new_contact.getID(), false );

			for (int i=0;i<closest_contacts.size();i++){

				if ( router.isID(((DHTTransportContact)closest_contacts.get(i)).getID())){

					close	= true;

					break;
				}
			}

			if ( !close ){

				if ( nacul == null ){

					node_add_check_uninteresting_limit = distance;

				}else{

					if ( compareDistances( nacul, distance ) > 0 ){

						node_add_check_uninteresting_limit = distance;
					}
				}
			}
		}

		if ( !close ){

			if ( !new_contact.hasBeenAlive()){

				requestPing( new_contact );
			}

			return;
		}

		// System.out.println( "Node added to router: id=" + ByteFormatter.encodeString( contact_id ));

			// ok, we're close enough to worry about transferring values

		Iterator	it = database.getKeys();

		while( it.hasNext()){

			HashWrapper	key		= (HashWrapper)it.next();

			byte[]	encoded_key		= key.getHash();

			if ( database.isKeyBlocked( encoded_key )){

				continue;
			}

			DHTDBLookupResult	result = database.get( null, key, 0, (byte)0, false );

			if ( result == null  ){

					// deleted in the meantime

				continue;
			}

				// even if a result has been diversified we continue to maintain the base value set
				// until the original publisher picks up the diversification (next publish period) and
				// publishes to the correct place

			DHTDBValue[]	values = result.getValues();

			if ( values.length == 0 ){

				continue;
			}

				// we don't consider any cached further away than the initial location, for transfer
				// however, we *do* include ones we originate as, if we're the closest, we have to
				// take responsibility for xfer (as others won't)

			List		sorted_contacts	= getClosestKContactsList( encoded_key, false );

					// if we're closest to the key, or the new node is closest and
					// we're second closest, then we take responsibility for storing
					// the value

			boolean	store_it	= false;

			if ( sorted_contacts.size() > 0 ){

				DHTTransportContact	first = (DHTTransportContact)sorted_contacts.get(0);

				if ( router.isID( first.getID())){

					store_it = true;

				}else if ( Arrays.equals( first.getID(), new_contact.getID()) && sorted_contacts.size() > 1 ){

					store_it = router.isID(((DHTTransportContact)sorted_contacts.get(1)).getID());
				}
			}

			if ( store_it ){

				List	values_to_store = new ArrayList(values.length);

				Collections.addAll(values_to_store, values);

				keys_to_store.put( key, values_to_store );
			}
		}

		final DHTTransportContact	t_contact = ((DHTControlContact)new_contact.getAttachment()).getTransportContact();

		final boolean[]	anti_spoof_done	= { false };

		if ( keys_to_store.size() > 0 ){

			it = keys_to_store.entrySet().iterator();

			final byte[][]				keys 		= new byte[keys_to_store.size()][];
			final DHTTransportValue[][]	value_sets 	= new DHTTransportValue[keys.length][];

			int		index = 0;

			while( it.hasNext()){

				Map.Entry	entry = (Map.Entry)it.next();

				HashWrapper	key		= (HashWrapper)entry.getKey();

				List		values	= (List)entry.getValue();

				keys[index] 		= key.getHash();
				value_sets[index]	= new DHTTransportValue[values.size()];


				for (int i=0;i<values.size();i++){

					value_sets[index][i] = ((DHTDBValue)values.get(i)).getValueForRelay( local_contact );
				}

				index++;
			}

				// move to anti-spoof for cache forwards. we gotta do a findNode to update the
				// contact's latest random id

			t_contact.sendFindNode(
					new DHTTransportReplyHandlerAdapter()
					{
						@Override
						public void
						findNodeReply(
							DHTTransportContact 	contact,
							DHTTransportContact[]	contacts )
						{
							// System.out.println( "nodeAdded: pre-store findNode OK" );

							anti_spoof_done[0]	= true;

							t_contact.sendStore(
									new DHTTransportReplyHandlerAdapter()
									{
										@Override
										public void
										storeReply(
											DHTTransportContact _contact,
											byte[]				_diversifications )
										{
											// System.out.println( "nodeAdded: store OK" );

												// don't consider diversifications for node additions as they're not interested
												// in getting values from us, they need to get them from nodes 'near' to the
												// diversification targets or the originator

											if ( DHTLog.isOn()){
												DHTLog.log( "add store ok" );
											}

											router.contactAlive( _contact.getID(), new DHTControlContactImpl(_contact));
										}

										@Override
										public void
										failed(
											DHTTransportContact 	_contact,
											Throwable				_error )
										{
											// System.out.println( "nodeAdded: store Failed" );

											if ( DHTLog.isOn()){
												DHTLog.log( "add store failed " + DHTLog.getString( _contact ) + " -> failed: " + _error.getMessage());
											}

											router.contactDead( _contact.getID(), false);
										}

										@Override
										public void
										keyBlockRequest(
											DHTTransportContact		contact,
											byte[]					request,
											byte[]					signature )
										{
											database.keyBlockRequest( null, request, signature );
										}
									},
									keys,
									value_sets,
									false );
						}

						@Override
						public void
						failed(
							DHTTransportContact 	_contact,
							Throwable				_error )
						{
							// System.out.println( "nodeAdded: pre-store findNode Failed" );

							if ( DHTLog.isOn()){
								DHTLog.log( "pre-store findNode failed " + DHTLog.getString( _contact ) + " -> failed: " + _error.getMessage());
							}

							router.contactDead( _contact.getID(), false);
						}
					},
					t_contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ANTI_SPOOF2?new byte[0]:new byte[20],
					DHT.FLAG_LOOKUP_FOR_STORE );

		}else{

			if ( !new_contact.hasBeenAlive()){

				requestPing( new_contact );
			}
		}

			// finally transfer any key-blocks

		if ( t_contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_BLOCK_KEYS ){

			for (final DHTStorageBlock key_block : direct_key_blocks) {

				List	contacts = getClosestKContactsList( key_block.getKey(), false );

				boolean	forward_it = false;

					// ensure that the key is close enough to us

				for (int j=0;j<contacts.size();j++){

					final DHTTransportContact	contact = (DHTTransportContact)contacts.get(j);

					if ( router.isID( contact.getID())){

						forward_it	= true;

						break;
					}
				}

				if ( !forward_it || key_block.hasBeenSentTo( t_contact )){

					continue;
				}

				final Runnable task =
					new Runnable()
					{
						@Override
						public void
						run()
						{
							t_contact.sendKeyBlock(
									new DHTTransportReplyHandlerAdapter()
									{
										@Override
										public void
										keyBlockReply(
											DHTTransportContact 	_contact )
										{
											if ( DHTLog.isOn()){
												DHTLog.log( "key block forward ok " + DHTLog.getString( _contact ));
											}

											key_block.sentTo( _contact );
										}

										@Override
										public void
										failed(
											DHTTransportContact 	_contact,
											Throwable				_error )
										{
											if ( DHTLog.isOn()){
												DHTLog.log( "key block forward failed " + DHTLog.getString( _contact ) + " -> failed: " + _error.getMessage());
											}
										}
									},
									key_block.getRequest(),
									key_block.getCertificate());
						}
					};

				if ( anti_spoof_done[0] ){

					task.run();

				}else{

					t_contact.sendFindNode(
							new DHTTransportReplyHandlerAdapter()
							{
								@Override
								public void
								findNodeReply(
									DHTTransportContact 	contact,
									DHTTransportContact[]	contacts )
								{
									task.run();
								}
								@Override
								public void
								failed(
									DHTTransportContact 	_contact,
									Throwable				_error )
								{
									// System.out.println( "nodeAdded: pre-store findNode Failed" );

									if ( DHTLog.isOn()){
										DHTLog.log( "pre-kb findNode failed " + DHTLog.getString( _contact ) + " -> failed: " + _error.getMessage());
									}

									router.contactDead( _contact.getID(), false);
								}
							},
							t_contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ANTI_SPOOF2?new byte[0]:new byte[20],
							DHT.FLAG_LOOKUP_FOR_STORE );
				}
			}
		}
	}

	protected Set<DHTTransportContact>
	getClosestContactsSet(
		byte[]		id,
		int			num_to_return,
		boolean		live_only )
	{
		List<DHTRouterContact>	l = router.findClosestContacts( id, num_to_return, live_only );

		Set<DHTTransportContact>	sorted_set	= new sortedTransportContactSet( id, true ).getSet();

		for (DHTRouterContact aL : l) {

			sorted_set.add(((DHTControlContact) aL.getAttachment()).getTransportContact());
		}

		return( sorted_set );
	}

	@Override
	public List<DHTTransportContact>
	getClosestKContactsList(
		byte[]		id,
		boolean		live_only )
	{
		return( getClosestContactsList( id, K, live_only ));
	}

	@Override
	public List<DHTTransportContact>
	getClosestContactsList(
		byte[]		id,
		int			num_to_return,
		boolean		live_only )
	{
		Set<DHTTransportContact>	sorted_set	= getClosestContactsSet( id, num_to_return, live_only );

		List<DHTTransportContact>	res = new ArrayList<>(num_to_return);

		Iterator<DHTTransportContact>	it = sorted_set.iterator();

		while( it.hasNext() && res.size() < num_to_return ){

			res.add( it.next());
		}

		return( res );
	}

	protected boolean
	isIDInClosestContacts(
		byte[]		test_id,
		byte[]		target_id,
		int			num_to_consider,
		boolean		live_only )
	{
		List<DHTRouterContact>	l = router.findClosestContacts( target_id, num_to_consider, live_only );

		boolean	found		= false;
		int		num_closer 	= 0;

		for ( DHTRouterContact c: l ){

			byte[]	c_id = c.getID();

			if ( Arrays.equals( test_id, c_id )){

				found = true;

			}else{

				if ( computeAndCompareDistances( c_id, test_id, target_id ) < 0 ){

					num_closer++;
				}
			}
		}

		return( found && num_closer < num_to_consider );
	}

	protected byte[]
	encodeKey(
		byte[]		key )
	{
		if ( encode_keys ){

			byte[]	temp = new SHA1Simple().calculateHash( key );

			byte[]	result =  new byte[node_id_byte_count];

			System.arraycopy( temp, 0, result, 0, node_id_byte_count );

			return( result );

		}else{

			if ( key.length == node_id_byte_count ){

				return( key );

			}else{

				byte[]	result =  new byte[node_id_byte_count];

				System.arraycopy( key, 0, result, 0, Math.min( node_id_byte_count, key.length ));

				return( result );
			}
		}
	}

	@Override
	public int
	computeAndCompareDistances(
		byte[]		t1,
		byte[]		t2,
		byte[]		pivot )
	{
		return( computeAndCompareDistances2( t1, t2, pivot ));
	}

	protected static int
	computeAndCompareDistances2(
		byte[]		t1,
		byte[]		t2,
		byte[]		pivot )
	{
		for (int i=0;i<t1.length;i++){

			byte d1 = (byte)( t1[i] ^ pivot[i] );
			byte d2 = (byte)( t2[i] ^ pivot[i] );

			int diff = (d1&0xff) - (d2&0xff);

			if ( diff != 0 ){

				return( diff );
			}
		}

		return( 0 );
	}

	@Override
	public byte[]
	computeDistance(
		byte[]		n1,
		byte[]		n2 )
	{
		return( computeDistance2( n1, n2 ));
	}

	protected static byte[]
	computeDistance2(
		byte[]		n1,
		byte[]		n2 )
	{
		byte[]	res = new byte[n1.length];

		for (int i=0;i<res.length;i++){

			res[i] = (byte)( n1[i] ^ n2[i] );
		}

		return( res );
	}

		/**
		 * -ve -> n1 < n2
		 */

	@Override
	public int
	compareDistances(
		byte[]		n1,
		byte[]		n2 )
	{
		return( compareDistances2( n1,n2 ));
	}

	protected static int
	compareDistances2(
		byte[]		n1,
		byte[]		n2 )
	{
		for (int i=0;i<n1.length;i++){

			int diff = (n1[i]&0xff) - (n2[i]&0xff);

			if ( diff != 0 ){

				return( diff );
			}
		}

		return( 0 );
	}

	@Override
	public void
	addListener(
		DHTControlListener	l )
	{
		try{
			activity_mon.enter();

			listeners.addListener( l );

			for (Object activity : activities) {

				listeners.dispatch(DHTControlListener.CT_ADDED, activity);
			}

		}finally{

			activity_mon.exit();
		}
	}

	@Override
	public void
	removeListener(
		DHTControlListener	l )
	{
		listeners.removeListener( l );
	}

	@Override
	public DHTControlActivity[]
	getActivities()
	{
		List<DHTControlActivity>	res;

		try{

			activity_mon.enter();

			res = new ArrayList<>( activities );

		}finally{

			activity_mon.exit();
		}

		DHTControlActivity[]	x = new DHTControlActivity[res.size()];

		res.toArray( x );

		return( x );
	}

	@Override
	public void
	setTransportEstimatedDHTSize(
		int						size )
	{
		if ( size > 0 ){

			try{
				estimate_mon.enter();

				remote_estimate_values.add(size);

				if ( remote_estimate_values.size() > REMOTE_ESTIMATE_HISTORY ){

					remote_estimate_values.remove(0);
				}
			}finally{

				estimate_mon.exit();
			}
		}
		// System.out.println( "estimated dht size: " + size );
	}

	@Override
	public int
	getTransportEstimatedDHTSize()
	{
		return((int)local_dht_estimate );
	}

	public int
	getEstimatedDHTSize()
	{
			// public method, trigger actual computation periodically

		long	now = SystemTime.getCurrentTime();

		long	diff = now - last_dht_estimate_time;

		if ( diff < 0 || diff > 60*1000 ){

			estimateDHTSize( router.getID(), null, router.getK());
		}

			// with recent changes we pretty much have a router that only contains routeable contacts
			// therefore the apparent size of the DHT is less than real and we need to adjust by the
			// routeable percentage to get an accurate figure

		int	percent = transport.getStats().getRouteablePercentage();

			// current assumption is that around 50% are firewalled, so if less (at least during migration) assume unusable

		if ( percent < 25 ){

			return((int)combined_dht_estimate );
		}

		double	mult = 100.0 / percent;

		return((int)( mult * combined_dht_estimate ));
	}

	protected void
	estimateDHTSize(
		byte[]							id,
		List<DHTTransportContact>		contacts,
		int								contacts_to_use )
	{
			// if called with contacts then this is in internal estimation based on lookup values

		long	now = SystemTime.getCurrentTime();

		long	diff = now - last_dht_estimate_time;

			// 5 second limiter here

		if ( diff < 0 || diff > 5*1000 ){

			try{
				estimate_mon.enter();

				last_dht_estimate_time	= now;

				List<DHTTransportContact>	l;

				if ( contacts == null ){

					l = getClosestKContactsList( id, false );

				}else{

					Set<DHTTransportContact>	sorted_set	= new sortedTransportContactSet( id, true ).getSet();

					sorted_set.addAll( contacts );

					l = new ArrayList<>( sorted_set );

					if ( l.size() > 0 ){

							// algorithm works relative to a starting point in the ID space so we grab
							// the first here rather than using the initial lookup target

						id = l.get(0).getID();
					}

					/*
					String	str = "";
					for (int i=0;i<l.size();i++){
						str += (i==0?"":",") + DHTLog.getString2( ((DHTTransportContact)l.get(i)).getID());
					}
					System.out.println( "trace: " + str );
					*/
				}

					// can't estimate with less than 2

				if ( l.size() > 2 ){


					/*
					<Gudy> if you call N0 yourself, N1 the nearest peer, N2 the 2nd nearest peer ... Np the pth nearest peer that you know (for example, N1 .. N20)
					<Gudy> and if you call D1 the Kad distance between you and N1, D2 between you and N2 ...
					<Gudy> then you have to compute :
					<Gudy> Dc = sum(i * Di) / sum( i * i)
					<Gudy> and then :
					<Gudy> NbPeers = 2^160 / Dc
					*/

					BigInteger	sum1 = new BigInteger("0");
					BigInteger	sum2 = new BigInteger("0");

						// first entry should be us

					for (int i=1;i<Math.min( l.size(), contacts_to_use );i++){

						DHTTransportContact	node = l.get(i);

						byte[]	dist = computeDistance( id, node.getID());

						BigInteger b_dist = IDToBigInteger( dist );

						BigInteger	b_i = new BigInteger(""+i);

						sum1 = sum1.add( b_i.multiply(b_dist));

						sum2 = sum2.add( b_i.multiply( b_i ));
					}

					byte[]	max = new byte[id.length+1];

					max[0] = 0x01;

					long this_estimate;

					if ( sum1.compareTo( new BigInteger("0")) == 0 ){

						this_estimate = 0;

					}else{

						this_estimate = IDToBigInteger(max).multiply( sum2 ).divide( sum1 ).longValue();
					}

						// there's always us!!!!

					if ( this_estimate < 1 ){

						this_estimate	= 1;
					}

					local_estimate_values.put( new HashWrapper( id ), this_estimate);

					long	new_estimate	= 0;

					Iterator<Long>	it = local_estimate_values.values().iterator();

					String	sizes = "";

					while( it.hasNext()){

						long	estimate = it.next();

						sizes += (sizes.length()==0?"":",") + estimate;

						new_estimate += estimate;
					}

					local_dht_estimate = new_estimate/local_estimate_values.size();

					// System.out.println( "getEstimatedDHTSize: " + sizes + "->" + dht_estimate + " (id=" + DHTLog.getString2(id) + ",cont=" + (contacts==null?"null":(""+contacts.size())) + ",use=" + contacts_to_use );
				}

				List<Integer> rems = new ArrayList<>(new TreeSet<>( remote_estimate_values ));

				// ignore largest and smallest few values

				long	rem_average = local_dht_estimate;
				int		rem_vals	= 1;

				for (int i=3;i<rems.size()-3;i++){

					rem_average += rems.get(i);

					rem_vals++;
				}

				long[] router_stats = router.getStats().getStats();

				long	router_contacts = router_stats[DHTRouterStats.ST_CONTACTS] + router_stats[DHTRouterStats.ST_REPLACEMENTS];

				combined_dht_estimate = Math.max( rem_average / rem_vals, router_contacts );

				long	test_val 	= 10;
				int		test_mag	= 1;

				while( test_val < combined_dht_estimate ){

					test_val *= 10;
					test_mag++;
				}

				combined_dht_estimate_mag = test_mag+1;

				// System.out.println( "estimateDHTSize: loc =" + local_dht_estimate + ", comb = " + combined_dht_estimate + " [" + remote_estimate_values.size() + "]");
			}finally{

				estimate_mon.exit();
			}
		}
	}

	protected BigInteger
	IDToBigInteger(
		byte[]		data )
	{
		StringBuilder	str_key = new StringBuilder( data.length*2 );

		for (int i=0;i<data.length;i++){

			String	hex = Integer.toHexString( data[i]&0xff );

			if ( hex.length() < 2 ){

				str_key.append( "0" );
			}

			str_key.append( hex );
		}

		BigInteger	res		= new BigInteger( str_key.toString(), 16 );

		return( res );
	}

	private int
	generateSpoofID(
		DHTTransportContact	contact )
	{
		if ( spoof_digest == null  ){

			return( 0 );
		}

		InetAddress iad = contact.getAddress().getAddress();

		try{
			spoof_mon.enter();

				// during cache forwarding we get a lot of consecutive requests from the
				// same contact so we can save CPU by caching the latest result and optimising for this

			Integer existing = spoof_gen_history.get( iad );

			if ( existing != null ){

				//System.out.println( "anti-spoof: cached " + existing + " for " + contact.getAddress() + " - total=" + spoof_gen_history.size());

				return( existing );
			}

			byte[]	address = iad.getAddress();

			//spoof_cipher.init(Cipher.ENCRYPT_MODE, spoof_key );
			//byte[]	data_out = spoof_cipher.doFinal( address );

			byte[]	data_in = spoof_key.clone();

			for ( int i=0;i<address.length;i++){
				data_in[i] ^= address[i];
			}

			byte[] data_out = spoof_digest.digest( data_in );

			int	res =  	(data_out[0]<<24)&0xff000000 |
						(data_out[1] << 16)&0x00ff0000 |
						(data_out[2] << 8)&0x0000ff00 |
						data_out[3]&0x000000ff;

			//System.out.println( "anti-spoof: generating " + res + " for " + contact.getAddress() + " - total=" + spoof_gen_history.size());

			spoof_gen_history.put( iad, res );

			return( res );

		}catch( Throwable e ){

			logger.log(e);

		}finally{

			spoof_mon.exit();
		}

		return( 0 );
	}

	private byte[]
	generateSpoofID2(
		DHTTransportContact	contact )
	{
		if ( spoof_digest == null  ){

			return( new byte[ SPOOF_ID2_SIZE ]);
		}

		HashWrapper cid = new HashWrapper( contact.getID());

		try{
			spoof_mon.enter();

				// during cache forwarding we get a lot of consecutive requests from the
				// same contact so we can save CPU by caching the latest result and optimising for this

			byte[] existing = spoof_gen_history2.get( cid );

			if ( existing != null ){

				//System.out.println( "anti-spoof: cached " + existing + " for " + contact.getAddress() + " - total=" + spoof_gen_history.size());

				return( existing );
			}

			// spoof_cipher.init(Cipher.ENCRYPT_MODE, spoof_key );
			// byte[]	data_out = spoof_cipher.doFinal( cid.getBytes());

			byte[] 	cid_bytes 	= cid.getBytes();
			byte[]	data_in 	= spoof_key.clone();

			int	byte_count = Math.min( cid_bytes.length, data_in.length );

			for ( int i=0;i<byte_count;i++){
				data_in[i] ^= cid_bytes[i];
			}

			byte[] data_out = spoof_digest.digest( data_in );

			byte[] res = new byte[SPOOF_ID2_SIZE];

			System.arraycopy( data_out, 0, res, 0, SPOOF_ID2_SIZE );

			//System.out.println( "anti-spoof: generating " + res + " for " + contact.getAddress() + " - total=" + spoof_gen_history.size());

			spoof_gen_history2.put( cid, res );

			return( res );

		}catch( Throwable e ){

			logger.log(e);

		}finally{

			spoof_mon.exit();
		}

		return( new byte[ SPOOF_ID2_SIZE ]);
	}

	@Override
	public boolean
	verifyContact(
		DHTTransportContact 	c,
		boolean					direct )
	{
		boolean	ok;

		if ( c.getRandomIDType() == DHTTransportContact.RANDOM_ID_TYPE1 ){

			ok = c.getRandomID() == generateSpoofID( c );

		}else{

			byte[]	r1 = c.getRandomID2();
			byte[]	r2 = generateSpoofID2( c );

			if ( r1 == null && r2 == null ){

				ok = true;

			}else if ( r1 == null || r2 == null ){

				ok = false;

			}else{

				ok = Arrays.equals( r1, r2 );
			}
		}

		if ( DHTLog.CONTACT_VERIFY_TRACE ){

			System.out.println( "    net " + transport.getNetwork() +"," + (direct?"direct":"indirect") + " verify for " + c.getName() + " -> " + ok + ", version = " + c.getProtocolVersion());
		}

		return( ok );
	}

	@Override
	public List<DHTControlContact>
	getContacts()
	{
		List<DHTRouterContact>	contacts = router.getAllContacts();

		List<DHTControlContact>	res = new ArrayList<>( contacts.size());

		for (DHTRouterContact rc : contacts) {

			DHTRouterContactAttachment attachment = rc.getAttachment();

			if (attachment instanceof DHTControlContact) {
				// attachment almost always DHTControlContactImpl
				res.add((DHTControlContact) attachment);
			}
		}

		return( res );
	}

	@Override
	public void
	pingAll()
	{
		List<DHTRouterContact>	contacts = router.getAllContacts();

		final AESemaphore sem = new AESemaphore( "pingAll", 32 );

		final int[]	results = {0,0};

		for (int i=0;i<contacts.size();i++){

			sem.reserve();

			DHTRouterContact	rc = contacts.get(i);

			((DHTControlContact)rc.getAttachment()).getTransportContact().sendPing(
					new DHTTransportReplyHandlerAdapter()
					{
						@Override
						public void
						pingReply(
							DHTTransportContact _contact )
						{
							results[0]++;

							print();

							sem.release();
						}

						@Override
						public void
						failed(
							DHTTransportContact 	_contact,
							Throwable				_error )
						{
							results[1]++;

							print();

							sem.release();
						}

						protected void
						print()
						{
							System.out.println( "ok=" + results[0] + ",bad=" + results[1] );
						}
					});
		}
	}

	@Override
	public void
	destroy()
	{

		if ( router != null ){

			router.destroy();
		}

		if ( database != null ){

			database.destroy();
		}
	}

	@Override
	public void
	print(
		boolean		full )
	{
		DHTNetworkPosition[]	nps = transport.getLocalContact().getNetworkPositions();

		String	np_str = "";

		for (int j=0;j<nps.length;j++){
			np_str += (j==0?"":",") + nps[j];
		}

		logger.log( "DHT Details: external address=" + transport.getLocalContact().getAddress() +
						", network=" + transport.getNetwork() +
						", protocol=V" + transport.getProtocolVersion() +
						", nps=" + np_str + ", est_size=" + getTransportEstimatedDHTSize());

		router.print();

		database.print( full );

		/*
		List	c = getContacts();

		for (int i=0;i<c.size();i++){

			DHTControlContact	cc = (DHTControlContact)c.get(i);

			System.out.println( "    " + cc.getTransportContact().getVivaldiPosition());
		}
		*/
	}

	protected static class
	sortedTransportContactSet
	{
		private final TreeSet<DHTTransportContact>	tree_set;

		final byte[]	pivot;
		final boolean	ascending;

		protected
		sortedTransportContactSet(
			byte[]		_pivot,
			boolean		_ascending )
		{
			pivot		= _pivot;
			ascending	= _ascending;

			tree_set = new TreeSet<>(
					new Comparator<DHTTransportContact>() {
						@Override
						public int
						compare(
								DHTTransportContact t1,
								DHTTransportContact t2) {
							// this comparator ensures that the closest to the key
							// is first in the iterator traversal

							int distance = computeAndCompareDistances2(t1.getID(), t2.getID(), pivot);

							if (ascending) {

								return (distance);

							} else {

								return (-distance);
							}
						}
					});
		}

		public Set<DHTTransportContact>
		getSet()
		{
			return( tree_set );
		}
	}

	protected static class
	DHTOperationListenerDemuxer
		implements DHTOperationListener
	{
		private final AEMonitor	this_mon = new AEMonitor( "DHTOperationListenerDemuxer" );

		private final DHTOperationListener	delegate;

		private boolean		complete_fired;
		private boolean		complete_included_ok;

		private int			complete_count	= 0;

		protected
		DHTOperationListenerDemuxer(
			DHTOperationListener	_delegate )
		{
			delegate	= _delegate;

			if ( delegate == null ){

				Debug.out( "invalid: null delegate" );
			}
		}

		public void
		incrementCompletes()
		{
			try{
				this_mon.enter();

				complete_count++;

			}finally{

				this_mon.exit();
			}
		}

		@Override
		public void
		searching(
			DHTTransportContact	contact,
			int					level,
			int					active_searches )
		{
			delegate.searching( contact, level, active_searches );
		}

		@Override
		public boolean
		diversified(
			String		desc )
		{
			return( delegate.diversified( desc ));
		}

		@Override
		public void
		found(
			DHTTransportContact	contact,
			boolean				is_closest )
		{
			delegate.found( contact, is_closest );
		}

		@Override
		public void
		read(
			DHTTransportContact	contact,
			DHTTransportValue	value )
		{
			delegate.read( contact, value );
		}

		@Override
		public void
		wrote(
			DHTTransportContact	contact,
			DHTTransportValue	value )
		{
			delegate.wrote( contact, value );
		}

		@Override
		public void
		complete(
			boolean				timeout )
		{
			boolean	fire	= false;

			try{
				this_mon.enter();

				if ( !timeout ){

					complete_included_ok	= true;
				}

				complete_count--;

				if (complete_count <= 0 && !complete_fired ){

					complete_fired	= true;
					fire			= true;
				}
			}finally{

				this_mon.exit();
			}

			if ( fire ){

				delegate.complete( !complete_included_ok );
			}
		}
	}

	abstract static class
	lookupResultHandler
		extends DHTOperationListenerDemuxer
	{
		protected
		lookupResultHandler(
			DHTOperationListener	delegate )
		{
			super( delegate );
		}

		public abstract void
		closest(
			List<DHTTransportContact>		res );

		public abstract void
		diversify(
			DHTTransportContact	cause,
			byte				diversification_type );

	}


	protected static class
	DHTTransportFindValueReplyImpl
		implements DHTTransportFindValueReply
	{
		private byte					dt = DHT.DT_NONE;
		private DHTTransportValue[]		values;
		private DHTTransportContact[]	contacts;
		private byte[]					blocked_key;
		private byte[]					blocked_sig;

		protected
		DHTTransportFindValueReplyImpl(
			byte				_dt,
			DHTTransportValue[]	_values )
		{
			dt		= _dt;
			values	= _values;

			boolean	copied = false;

			for (int i=0;i<values.length;i++){

				DHTTransportValue	value = values[i];

				if ( ( value.getFlags() & DHT.FLAG_ANON ) != 0 ){

					if ( !copied ){

						values = new DHTTransportValue[ _values.length ];

						System.arraycopy( _values, 0, values, 0, values.length );

						copied = true;
					}

					values[i] = new anonValue( value );
				}
			}
		}

		protected
		DHTTransportFindValueReplyImpl(
			DHTTransportContact[]	_contacts )
		{
			contacts	= _contacts;
		}

		protected
		DHTTransportFindValueReplyImpl(
			byte[]		_blocked_key,
			byte[]		_blocked_sig )
		{
			blocked_key	= _blocked_key;
			blocked_sig	= _blocked_sig;
		}

		@Override
		public byte
		getDiversificationType()
		{
			return( dt );
		}

		@Override
		public boolean
		hit()
		{
			return( values != null );
		}

		@Override
		public boolean
		blocked()
		{
			return( blocked_key != null );
		}

		@Override
		public DHTTransportValue[]
		getValues()
		{
			return( values );
		}

		@Override
		public DHTTransportContact[]
		getContacts()
		{
			return( contacts );
		}

		@Override
		public byte[]
		getBlockedKey()
		{
			return( blocked_key );
		}

		@Override
		public byte[]
		getBlockedSignature()
		{
			return( blocked_sig );
		}
	}

	protected static class
	anonValue
		implements DHTTransportValue
	{
		private final DHTTransportValue delegate;

		protected
		anonValue(
			DHTTransportValue		v )
		{
			delegate = v;
		}

		@Override
		public boolean
		isLocal()
		{
			return( delegate.isLocal());
		}

		@Override
		public long
		getCreationTime()
		{
			return( delegate.getCreationTime());
		}

		@Override
		public byte[]
		getValue()
		{
			return( delegate.getValue());
		}

		@Override
		public int
		getVersion()
		{
			return( delegate.getVersion());
		}

		@Override
		public DHTTransportContact
		getOriginator()
		{
			return( new anonContact( delegate.getOriginator()));
		}

		@Override
		public int
		getFlags()
		{
			return( delegate.getFlags());
		}

		@Override
		public int
		getLifeTimeHours()
		{
			return( delegate.getLifeTimeHours());
		}

		@Override
		public byte
		getReplicationControl()
		{
			return( delegate.getReplicationControl());
		}

		@Override
		public byte
		getReplicationFactor()
		{
			return( delegate.getReplicationFactor());
		}

		@Override
		public byte
		getReplicationFrequencyHours()
		{
			return( delegate.getReplicationFrequencyHours());
		}

		@Override
		public String
		getString()
		{
			return( delegate.getString());
		}
	}

	protected static class
	anonContact
		implements DHTTransportContact
	{
		private static InetSocketAddress anon_address;

		static{
			try{
				anon_address = new InetSocketAddress( InetAddress.getByName( "0.0.0.0" ), 0);

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		private final DHTTransportContact	delegate;

		protected
		anonContact(
			DHTTransportContact		c )
		{
			delegate = c;
		}

		@Override
		public int
		getMaxFailForLiveCount()
		{
			return( delegate.getMaxFailForLiveCount());
		}

		@Override
		public int
		getMaxFailForUnknownCount()
		{
			return( delegate.getMaxFailForUnknownCount());
		}

		@Override
		public int
		getInstanceID()
		{
			return( delegate.getInstanceID());
		}

		@Override
		public byte[]
		getID()
		{
			Debug.out( "hmm" );

			return( delegate.getID());
		}

		@Override
		public byte
		getProtocolVersion()
		{
			return( delegate.getProtocolVersion());
		}

		@Override
		public long
		getClockSkew()
		{
			return( delegate.getClockSkew());
		}

		@Override
		public int
		getRandomIDType()
		{
			return( delegate.getRandomIDType());
		}

		@Override
		public void
		setRandomID(
			int	id )
		{
			delegate.setRandomID( id );
		}

		@Override
		public int
		getRandomID()
		{
			return( delegate.getRandomID());
		}

		@Override
		public void
		setRandomID2(
			byte[]		id )
		{
			delegate.setRandomID2( id );
		}

		@Override
		public byte[]
		getRandomID2()
		{
			return(delegate.getRandomID2());
		}

		@Override
		public String
		getName()
		{
			return( delegate.getName());
		}

		@Override
		public byte[]
		getBloomKey()
		{
			return(delegate.getBloomKey());
		}

		@Override
		public InetSocketAddress
		getAddress()
		{
			return( anon_address );
		}

		@Override
		public InetSocketAddress
		getTransportAddress()
		{
			return( getAddress());
		}

		@Override
		public InetSocketAddress
		getExternalAddress()
		{
			return( getAddress());
		}

		@Override
		public Map<String, Object>
		exportContactToMap()
		{
			return( delegate.exportContactToMap());
		}

		@Override
		public boolean
		isAlive(
			long		timeout )
		{
			return( delegate.isAlive( timeout ));
		}

		@Override
		public void
		isAlive(
			DHTTransportReplyHandler	handler,
			long						timeout )
		{
			delegate.isAlive( handler, timeout );
		}

		@Override
		public boolean
		isValid()
		{
			return( delegate.isValid());
		}

		@Override
		public boolean
		isSleeping()
		{
			return( delegate.isSleeping());
		}

		@Override
		public void
		sendPing(
			DHTTransportReplyHandler	handler )
		{
			delegate.sendPing(handler);
		}

		@Override
		public void
		sendImmediatePing(
			DHTTransportReplyHandler	handler,
			long						timeout )
		{
			delegate.sendImmediatePing(handler, timeout);
		}

		@Override
		public void
		sendStats(
			DHTTransportReplyHandler	handler )
		{
			delegate.sendStats(handler);
		}

		@Override
		public void
		sendStore(
			DHTTransportReplyHandler	handler,
			byte[][]					keys,
			DHTTransportValue[][]		value_sets,
			boolean						immediate )
		{
			delegate.sendStore(handler, keys, value_sets, immediate);
		}

		@Override
		public void
		sendQueryStore(
			DHTTransportReplyHandler 	handler,
			int							header_length,
			List<Object[]>			 	key_details )
		{
			delegate.sendQueryStore( handler, header_length, key_details );
		}

		@Override
		public void
		sendFindNode(
			DHTTransportReplyHandler	handler,
			byte[]						id,
			short						flags )
		{
			delegate.sendFindNode( handler, id, flags );
		}

		@Override
		public void
		sendFindValue(
			DHTTransportReplyHandler	handler,
			byte[]						key,
			int							max_values,
			short						flags )
		{
			delegate.sendFindValue( handler, key, max_values, flags);
		}

		@Override
		public void
		sendKeyBlock(
			DHTTransportReplyHandler	handler,
			byte[]						key_block_request,
			byte[]						key_block_signature )
		{
			delegate.sendKeyBlock(handler, key_block_request, key_block_signature);
		}

		@Override
		public DHTTransportFullStats
		getStats()
		{
			return( delegate.getStats());
		}

		@Override
		public void
		exportContact(
			DataOutputStream	os )

			throws IOException, DHTTransportException
		{
			delegate.exportContact( os );
		}

		@Override
		public void
		remove()
		{
			delegate.remove();
		}

		@Override
		public void
		createNetworkPositions(
			boolean is_local)
		{
			delegate.createNetworkPositions(is_local);
		}

		@Override
		public DHTNetworkPosition[]
		getNetworkPositions()
		{
			return( delegate.getNetworkPositions());
		}

		@Override
		public DHTNetworkPosition
		getNetworkPosition(
			byte	position_type )
		{
			return( delegate.getNetworkPosition( position_type ));
		}

		@Override
		public DHTTransport
		getTransport()
		{
			return( delegate.getTransport());
		}

		@Override
		public String
		getString()
		{
			return( delegate.getString());
		}
	}

	protected static class
	DHTTransportStoreReplyImpl
		implements DHTTransportStoreReply
	{
		private byte[]	divs;
		private byte[]	block_request;
		private byte[]	block_sig;

		protected
		DHTTransportStoreReplyImpl(
			byte[]		_divs )
		{
			divs	= _divs;
		}

		protected
		DHTTransportStoreReplyImpl(
			byte[]		_bk,
			byte[]		_bs )
		{
			block_request	= _bk;
			block_sig		= _bs;
		}

		@Override
		public byte[]
		getDiversificationTypes()
		{
			return( divs );
		}

		@Override
		public boolean
		blocked()
		{
			return( block_request != null );
		}

		@Override
		public byte[]
		getBlockRequest()
		{
			return( block_request );
		}

		@Override
		public byte[]
		getBlockSignature()
		{
			return( block_sig );
		}
	}

	protected abstract class
	DhtTask
		extends ThreadPoolTask
	{
		private final controlActivity	activity;

		protected
		DhtTask(
			ThreadPool	thread_pool )
		{
			activity = new controlActivity( thread_pool, this );

			try{

				activity_mon.enter();

				activities.add( activity );

				listeners.dispatch( DHTControlListener.CT_ADDED, activity );

				// System.out.println( "activity added:" + activities.size());

			}finally{

				activity_mon.exit();
			}
		}

		@Override
		public void
		taskStarted()
		{
			listeners.dispatch( DHTControlListener.CT_CHANGED, activity );

			//System.out.println( "activity changed:" + activities.size());
		}

		@Override
		public void
		taskCompleted()
		{
			try{
				activity_mon.enter();

				activities.remove( activity );

				listeners.dispatch( DHTControlListener.CT_REMOVED, activity );

				// System.out.println( "activity removed:" + activities.size());

			}finally{

				activity_mon.exit();
			}
		}

		@Override
		public void
		interruptTask()
		{
		}

		protected abstract void
		cancel();

		public abstract byte[]
		getTarget();

		public abstract String
		getDescription();

		public abstract DHTControlActivity.ActivityState
		getCurrentState();
	}

	protected static class
	DhtTaskSet
	{
		private boolean cancelled;

		private Object	things;

		void
		add(
				DhtTask task)
		{
			synchronized( this ){

				if ( cancelled ){

					task.cancel();

					return;
				}

				addToThings( task );
			}
		}

		void
		add(
				DhtTaskSet task_set)
		{
			synchronized( this ){

				if ( cancelled ){

					task_set.cancel();

					return;
				}

				addToThings( task_set );
			}
		}

		private void
		addToThings(
			Object	obj )
		{
			if ( things == null ){

				things = obj;

			}else{

				if ( things instanceof List ){

					((List)things).add( obj );

				}else{

					List	l = new ArrayList(2);

					l.add( things );
					l.add( obj );

					things = l;
				}
			}
		}

		void
		cancel()
		{
			Object	to_cancel;

			synchronized( this ){

				if ( cancelled ){

					return;
				}

				cancelled = true;

				to_cancel = things;

				things = null;
			}

			if ( to_cancel != null ){

				if ( to_cancel instanceof DhtTask ){

					((DhtTask)to_cancel).cancel();

				}else if ( to_cancel instanceof DhtTaskSet ){

					((DhtTaskSet)to_cancel).cancel();

				}else{

					List	l = (List)to_cancel;

					for (Object o : l) {

						if (o instanceof DhtTask) {

							((DhtTask) o).cancel();

						} else {

							((DhtTaskSet) o).cancel();
						}
					}
				}
			}
		}

		boolean
		isCancelled()
		{
			synchronized( this ){

				return( cancelled);
			}
		}
	}

	protected class
	controlActivity
		implements DHTControlActivity
	{
		protected final ThreadPool	tp;
		protected final DhtTask		task;
		protected final int			type;

		protected
		controlActivity(
			ThreadPool	_tp,
			DhtTask		_task )
		{
			tp		= _tp;
			task	= _task;

			if ( _tp == internal_lookup_pool ){

				type	= DHTControlActivity.AT_INTERNAL_GET;

			}else if ( _tp == external_lookup_pool ){

				type	= DHTControlActivity.AT_EXTERNAL_GET;

			}else if ( _tp == internal_put_pool ){

				type	= DHTControlActivity.AT_INTERNAL_PUT;

			}else{

				type	= DHTControlActivity.AT_EXTERNAL_PUT;
			}
		}

		@Override
		public byte[]
		getTarget()
		{
			return( task.getTarget());
		}

		@Override
		public String
		getDescription()
		{
			return( task.getDescription());
		}

		@Override
		public int
		getType()
		{
			return( type );
		}

		@Override
		public boolean
		isQueued()
		{
			return( tp.isQueued( task ));
		}

		@Override
		public ActivityState
		getCurrentState()
		{
			return( task.getCurrentState());
		}

		@Override
		public String
		getString()
		{
			return( type + ":" + DHTLog.getString( getTarget()) + "/" + getDescription() + ", q = " + isQueued());
		}
	}
}
