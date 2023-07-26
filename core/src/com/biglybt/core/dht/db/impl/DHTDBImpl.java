/*
 * Created on 28-Jan-2005
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

package com.biglybt.core.dht.db.impl;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import com.biglybt.core.dht.*;
import com.biglybt.core.dht.control.DHTControl;
import com.biglybt.core.dht.db.DHTDB;
import com.biglybt.core.dht.db.DHTDBLookupResult;
import com.biglybt.core.dht.db.DHTDBStats;
import com.biglybt.core.dht.db.DHTDBValue;
import com.biglybt.core.dht.impl.DHTLog;
import com.biglybt.core.dht.router.DHTRouter;
import com.biglybt.core.dht.transport.DHTTransportContact;
import com.biglybt.core.dht.transport.DHTTransportQueryStoreReply;
import com.biglybt.core.dht.transport.DHTTransportReplyHandlerAdapter;
import com.biglybt.core.dht.transport.DHTTransportValue;
import com.biglybt.core.dht.transport.udp.DHTTransportUDP;
import com.biglybt.core.ipfilter.IpFilter;
import com.biglybt.core.ipfilter.IpFilterManagerFactory;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;
import com.biglybt.core.util.bloom.BloomFilter;
import com.biglybt.core.util.bloom.BloomFilterFactory;

/**
 * @author parg
 *
 */

public class
DHTDBImpl
	implements DHTDB, DHTDBStats
{
	private static final int MAX_VALUE_LIFETIME	= 3*24*60*60*1000;


	private final int			original_republish_interval;

		// the grace period gives the originator time to republish their data as this could involve
		// some work on their behalf to find closest nodes etc. There's no real urgency here anyway

	public static final int			ORIGINAL_REPUBLISH_INTERVAL_GRACE	= 60*60*1000;

	private static final boolean	ENABLE_PRECIOUS_STUFF			= false;
	private static final int		PRECIOUS_CHECK_INTERVAL			= 2*60*60*1000;

	private final int			cache_republish_interval;

	private static final long		MIN_CACHE_EXPIRY_CHECK_INTERVAL		= 60*1000;
	private long		last_cache_expiry_check;

	private static final long	IP_BLOOM_FILTER_REBUILD_PERIOD		= 15*60*1000;
	private static final int	IP_COUNT_BLOOM_SIZE_INCREASE_CHUNK	= 1000;

	private BloomFilter	ip_count_bloom_filter = BloomFilterFactory.createAddRemove8Bit( IP_COUNT_BLOOM_SIZE_INCREASE_CHUNK );

	private static final int	VALUE_VERSION_CHUNK = 128;
	private int	next_value_version;
	private int next_value_version_left;


	protected static final int		QUERY_STORE_REQUEST_ENTRY_SIZE	= 6;
	protected static final int		QUERY_STORE_REPLY_ENTRY_SIZE	= 2;

	final Map<HashWrapper,DHTDBMapping>				stored_values 				= new HashMap<>();
	private final Map<DHTDBMapping.ShortHash,DHTDBMapping>	stored_values_prefix_map	= new HashMap<>();

	DHTControl				control;
	private final DHTStorageAdapter		adapter;
	DHTRouter				router;
	DHTTransportContact		local_contact;
	final DHTLogger				logger;

	private static final long	MAX_TOTAL_SIZE	= 4*1024*1024;

	int		total_size;
	int		total_values;
	int		total_keys;
	private int		total_local_keys;


	boolean force_original_republish;

	private final IpFilter	ip_filter	= IpFilterManagerFactory.getSingleton().getIPFilter();

	final AEMonitor	this_mon	= new AEMonitor( "DHTDB" );

	private static final boolean	DEBUG_SURVEY		= false;
	private static final boolean	SURVEY_ONLY_RF_KEYS	= true;


	private static final int	SURVEY_PERIOD					= DEBUG_SURVEY?1*60*1000:15*60*1000;
	private static final int	SURVEY_STATE_INACT_TIMEOUT		= DEBUG_SURVEY?5*60*1000:60*60*1000;
	private static final int	SURVEY_STATE_MAX_LIFE_TIMEOUT	= 3*60*60*1000 + 30*60*1000;
	private static final int	SURVEY_STATE_MAX_LIFE_RAND		= 1*60*60*1000;

	private static final int	MAX_SURVEY_SIZE			= 100;
	private static final int	MAX_SURVEY_STATE_SIZE	= 150;

	private volatile boolean survey_in_progress;

	private final Map<HashWrapper,Long>	survey_mapping_times = new HashMap<>();

	private final Map<HashWrapper,SurveyContactState>	survey_state =
		new LinkedHashMap<HashWrapper,SurveyContactState>(MAX_SURVEY_STATE_SIZE,0.75f,true)
		{
			@Override
			protected boolean
			removeEldestEntry(
		   		Map.Entry<HashWrapper,SurveyContactState> eldest)
			{
				return size() > MAX_SURVEY_STATE_SIZE;
			}
		};

	private TimerEventPeriodic		precious_timer;
	private TimerEventPeriodic		original_republish_timer;
	private TimerEventPeriodic		cache_republish_timer;
	private final TimerEventPeriodic		bloom_timer;
	private TimerEventPeriodic		survey_timer;


	private boolean	sleeping;
	private boolean	suspended;

	private volatile boolean	destroyed;

	public
	DHTDBImpl(
		DHTStorageAdapter	_adapter,
		int					_original_republish_interval,
		int					_cache_republish_interval,
		byte				_protocol_version,
		DHTLogger			_logger )
	{
		adapter							= _adapter==null?null:new adapterFacade( _adapter );
		original_republish_interval		= _original_republish_interval;
		cache_republish_interval		= _cache_republish_interval;
		logger							= _logger;

		boolean survey_enabled = _protocol_version >= DHTTransportUDP.PROTOCOL_VERSION_REPLICATION_CONTROL3 &&
				(adapter == null ||
						adapter.getNetwork() == DHT.NW_AZ_CVS ||
						FeatureAvailability.isDHTRepV2Enabled());

		if ( ENABLE_PRECIOUS_STUFF ){

			precious_timer = SimpleTimer.addPeriodicEvent(
				"DHTDB:precious",
				PRECIOUS_CHECK_INTERVAL/4,
				true, // absolute, we don't want effective time changes (computer suspend/resume) to shift these
				new TimerEventPerformer()
				{
					@Override
					public void
					perform(
						TimerEvent	event )
					{
						checkPreciousStuff();
					}
				});
		}

		if ( original_republish_interval > 0 ){

			original_republish_timer = SimpleTimer.addPeriodicEvent(
				"DHTDB:op",
				original_republish_interval,
				true, // absolute, we don't want effective time changes (computer suspend/resume) to shift these
				new TimerEventPerformer()
				{
					@Override
					public void
					perform(
						TimerEvent	event )
					{
						AEThread2.createAndStartDaemon( 
							"DHTDB:op",
							()->{
								logger.log( "Republish of original mappings starts" );
		
								long	start 	= SystemTime.getCurrentTime();
		
								int	stats = republishOriginalMappings();
		
								long	end 	= SystemTime.getCurrentTime();
		
								logger.log( "Republish of original mappings completed in " + (end-start) + ": " +
											"values = " + stats );
							});
					}
				});
		}

		if ( cache_republish_interval > 0 ){

					// random skew here so that cache refresh isn't very synchronised, as the optimisations
					// regarding non-republising benefit from this

			cache_republish_timer = SimpleTimer.addPeriodicEvent(
					"DHTDB:cp",
					cache_republish_interval + 10000 - RandomUtils.nextInt(20000),
					true,	// absolute, we don't want effective time changes (computer suspend/resume) to shift these
					new TimerEventPerformer()
					{
						@Override
						public void
						perform(
							TimerEvent	event )
						{
							AEThread2.createAndStartDaemon(
								"DHTDB:cp",
								()->{
									logger.log( "Republish of cached mappings starts" );
		
									long	start 	= SystemTime.getCurrentTime();
		
									int[]	stats = republishCachedMappings();
		
									long	end 	= SystemTime.getCurrentTime();
		
									logger.log( "Republish of cached mappings completed in " + (end-start) + ": " +
												"values = " + stats[0] + ", keys = " + stats[1] + ", ops = " + stats[2]);
		
									if ( force_original_republish ){
		
										force_original_republish	= false;
		
										logger.log( "Force republish of original mappings due to router change starts" );
		
										start 	= SystemTime.getCurrentTime();
		
										int stats2 = republishOriginalMappings();
		
										end 	= SystemTime.getCurrentTime();
		
										logger.log( "Force republish of original mappings due to router change completed in " + (end-start) + ": " +
													"values = " + stats2 );
									}
								});
						}
					});
		}


		bloom_timer = SimpleTimer.addPeriodicEvent(
				"DHTDB:bloom",
				IP_BLOOM_FILTER_REBUILD_PERIOD,
				new TimerEventPerformer()
				{
					@Override
					public void
					perform(
						TimerEvent	event )
					{
						try{
							this_mon.enter();

							rebuildIPBloomFilter( false );

						}finally{

							this_mon.exit();
						}
					}
				});

		if (survey_enabled){

			survey_timer = SimpleTimer.addPeriodicEvent(
					"DHTDB:survey",
					SURVEY_PERIOD,
					true,
					new TimerEventPerformer()
					{
						@Override
						public void
						perform(
							TimerEvent	event )
						{
							survey();
						}
					});
		}
	}


	@Override
	public void
	setControl(
		DHTControl		_control )
	{
		control			= _control;

			// trigger an "original value republish" if router has changed

		force_original_republish = router != null;

		router			= control.getRouter();
		local_contact	= control.getTransport().getLocalContact();

			// our ID has changed - amend the originator of all our values

		try{
			this_mon.enter();

			survey_state.clear();

			Iterator<DHTDBMapping>	it = stored_values.values().iterator();

			while( it.hasNext()){

				DHTDBMapping	mapping = it.next();

				mapping.updateLocalContact( local_contact );
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public DHTDBValue
	store(
		HashWrapper		key,
		byte[]			value,
		short			flags,
		byte			life_hours,
		byte			replication_control )
	{

			// local store

		if ( (flags & DHT.FLAG_PUT_AND_FORGET ) == 0 ){

			if (( flags & DHT.FLAG_OBFUSCATE_LOOKUP ) != 0 ){

				Debug.out( "Obfuscated puts without 'put-and-forget' are not supported as original-republishing of them is not implemented" );
			}

			if ( life_hours > 0 ){

				if ( life_hours*60*60*1000 < original_republish_interval ){

					Debug.out( "Don't put persistent values with a lifetime less than republish period - lifetime over-ridden" );

					life_hours = 0;
				}
			}

			try{
				this_mon.enter();

				total_local_keys++;

					// don't police max check for locally stored data
					// only that received

				DHTDBMapping	mapping = stored_values.get( key );

				if ( mapping == null ){

					mapping = new DHTDBMapping( this, key, true );

					stored_values.put( key, mapping );

					addToPrefixMap( mapping );
				}

				DHTDBValueImpl res =
					new DHTDBValueImpl(
							SystemTime.getCurrentTime(),
							value,
							getNextValueVersion(),
							local_contact,
							local_contact,
							true,
							flags,
							life_hours,
							replication_control );

				mapping.add( res );

				return( res );

			}finally{

				this_mon.exit();
			}
		}else{

			DHTDBValueImpl res =
				new DHTDBValueImpl(
						SystemTime.getCurrentTime(),
						value,
						getNextValueVersion(),
						local_contact,
						local_contact,
						true,
						flags,
						life_hours,
						replication_control );

			return( res );
		}
	}

	/*
	private long store_ops;
	private long store_ops_bad1;
	private long store_ops_bad2;

	private void
	logStoreOps()
	{
		System.out.println( "sops (" + control.getTransport().getNetwork() + ")=" + store_ops + ",bad1=" + store_ops_bad1 + ",bad2=" + store_ops_bad2 );
	}
	*/

	@Override
	public byte
	store(
		DHTTransportContact 	sender,
		HashWrapper				key,
		DHTTransportValue[]		values )
	{
			// allow 4 bytes per value entry to deal with overhead (prolly should be more but we're really
			// trying to deal with 0-length value stores)

		if ( total_size + ( total_values*4 ) > MAX_TOTAL_SIZE ){

			DHTLog.log( "Not storing " + DHTLog.getString2(key.getHash()) + " as maximum storage limit exceeded" );

			return( DHT.DT_SIZE );
		}

		// logStoreOps();

		try{
			this_mon.enter();

			if ( sleeping || suspended ){

				return( DHT.DT_NONE );
			}

			checkCacheExpiration( false );

			DHTDBMapping	mapping = stored_values.get( key );

			if ( mapping == null ){

				mapping = new DHTDBMapping( this, key, false );

				stored_values.put( key, mapping );

				addToPrefixMap( mapping );
			}

				// we carry on an update as its ok to replace existing entries
				// even if diversified

			for (DHTTransportValue value : values) {

				DHTDBValueImpl mapping_value	= new DHTDBValueImpl( sender, value, false );

				mapping.add( mapping_value );
			}

			return( mapping.getDiversificationType());

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public DHTDBLookupResult
	get(
		DHTTransportContact		reader,
		HashWrapper				key,
		int						max_values,	// 0 -> all
		short					flags,
		boolean					external_request )
	{
		try{
			this_mon.enter();

			checkCacheExpiration( false );

			final DHTDBMapping mapping = stored_values.get(key);

			if ( mapping == null ){

				return( null );
			}

			if ( external_request ){

				mapping.addHit();
			}

			final DHTDBValue[]	values = mapping.get( reader, max_values, flags );

			return(
				new DHTDBLookupResult()
				{
					@Override
					public DHTDBValue[]
					getValues()
					{
						return( values );
					}

					@Override
					public byte
					getDiversificationType()
					{
						return( mapping.getDiversificationType());
					}
				});

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public DHTDBValue
	get(
		HashWrapper				key )
	{
			// local get

		try{
			this_mon.enter();

			DHTDBMapping mapping = stored_values.get( key );

			if ( mapping != null ){

				return( mapping.get( local_contact ));
			}

			return( null );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public DHTDBValue
	getAnyValue(
		HashWrapper				key )
	{
		try{
			this_mon.enter();

			DHTDBMapping mapping = stored_values.get( key );

			if ( mapping != null ){

				return( mapping.getAnyValue( local_contact ));
			}

			return( null );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public List<DHTDBValue>
	getAllValues(
		HashWrapper				key )
	{
		try{
			this_mon.enter();

			DHTDBMapping mapping = stored_values.get( key );

			List<DHTDBValue> result = new ArrayList<>();

			if ( mapping != null ){

				result.addAll( mapping.getAllValues( local_contact ));
			}

			return( result  );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public boolean
	hasKey(
		HashWrapper		key )
	{
		try{
			this_mon.enter();

			return( stored_values.containsKey( key ));

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public DHTDBValue
	remove(
		DHTTransportContact 	originator,
		HashWrapper				key )
	{
		return( remove( originator, key, (short)0));
	}
	
	@Override
	public DHTDBValue
	remove(
		DHTTransportContact 	originator,
		HashWrapper				key,
		short					flags )
	{
			// local remove

		try{
			
			DHTDBValue	result;
			
			this_mon.enter();

			DHTDBMapping mapping = stored_values.get( key );

			if ( mapping != null ){

				DHTDBValueImpl	res = mapping.remove( originator );

				if ( res != null ){

					total_local_keys--;

					if ( !mapping.getValues().hasNext()){

						stored_values.remove( key );

						removeFromPrefixMap( mapping );

						mapping.destroy();
					}

					result = res.getValueForDeletion( getNextValueVersion());
				}else{
					
					result = null;
				}
			}else{
				
				result = null;
			}

			if ( result == null ){
				
				if ((flags & DHT.FLAG_PUT_AND_FORGET) != 0 ){
					
						// wouldn't expect to find a local value for this, make one up
			
					DHTDBValueImpl temp =
							new DHTDBValueImpl(
									SystemTime.getCurrentTime(),
									new byte[1],
									getNextValueVersion(),
									local_contact,
									local_contact,
									true,
									flags,
									(byte)0, 
									DHT.REP_FACT_DEFAULT );
					
					result = temp.getValueForDeletion( getNextValueVersion());
				}
			}
			
			return( result );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public DHTStorageBlock
	keyBlockRequest(
		DHTTransportContact		direct_sender,
		byte[]					request,
		byte[]					signature )
	{
		if ( adapter == null ){

			return( null );
		}

			// for block requests sent to us (as opposed to being returned from other operations)
			// make sure that the key is close enough to us

		if ( direct_sender != null ){

			byte[]	key = adapter.getKeyForKeyBlock( request );

			List<DHTTransportContact> closest_contacts = control.getClosestKContactsList( key, true );

			boolean	process_it	= false;

			for (int i=0;i<closest_contacts.size();i++){

				if ( router.isID(closest_contacts.get(i).getID())){

					process_it	= true;

					break;
				}
			}

			if ( !process_it ){

				DHTLog.log( "Not processing key block for  " + DHTLog.getString2(key) + " as key too far away" );

				return( null );
			}

			if ( ! control.verifyContact( direct_sender, true )){

				DHTLog.log( "Not processing key block for  " + DHTLog.getString2(key) + " as verification failed" );

				return( null );
			}
		}

		return( adapter.keyBlockRequest( direct_sender, request, signature ));
	}

	@Override
	public DHTStorageBlock
	getKeyBlockDetails(
		byte[]		key )
	{
		if ( adapter == null ){

			return( null );
		}

		return( adapter.getKeyBlockDetails( key ));
	}

	@Override
	public boolean
	isKeyBlocked(
		byte[]		key )
	{
		return( getKeyBlockDetails(key) != null );
	}

	@Override
	public DHTStorageBlock[]
	getDirectKeyBlocks()
	{
		if ( adapter == null ){

			return( new DHTStorageBlock[0] );
		}

		return( adapter.getDirectKeyBlocks());
	}

	@Override
	public boolean
	isEmpty()
	{
		return( total_keys == 0 );
	}

	@Override
	public int
	getKeyCount()
	{
		return total_keys;
	}

	@Override
	public int
	getLocalKeyCount()
	{
		return( total_local_keys );
	}

	@Override
	public int
	getValueCount()
	{
		return total_values;
	}

	@Override
	public int
	getSize()
	{
		return total_size;
	}

	@Override
	public int[]
	getValueDetails()
	{
		try{
			this_mon.enter();

			int[]	res = new int[6];

			for (DHTDBMapping mapping : stored_values.values()) {

				res[DHTDBStats.VD_VALUE_COUNT] += mapping.getValueCount();
				res[DHTDBStats.VD_LOCAL_SIZE] += mapping.getLocalSize();
				res[DHTDBStats.VD_DIRECT_SIZE] += mapping.getDirectSize();
				res[DHTDBStats.VD_INDIRECT_SIZE] += mapping.getIndirectSize();

				int	dt = mapping.getDiversificationType();

				if ( dt == DHT.DT_FREQUENCY ){

					res[DHTDBStats.VD_DIV_FREQ]++;

				}else if ( dt == DHT.DT_SIZE ){

					res[DHTDBStats.VD_DIV_SIZE]++;

					/*
					Iterator<DHTDBValueImpl> it2 = mapping.getIndirectValues();

					System.out.println( "values=" + mapping.getValueCount());

					while( it2.hasNext()){

						DHTDBValueImpl val = it2.next();

						System.out.println( new String( val.getValue()) + " - " + val.getOriginator().getAddress());
					}
					*/
				}
			}

			return( res );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public int
	getKeyBlockCount()
	{
		if ( adapter == null ){

			return( 0 );
		}

		return( adapter.getDirectKeyBlocks().length );
	}

	@Override
	public Iterator<HashWrapper>
	getKeys()
	{
		try{
			this_mon.enter();

			return(new ArrayList<>(stored_values.keySet()).iterator());

		}finally{

			this_mon.exit();
		}
	}

	protected int
	republishOriginalMappings()
	{
		if ( suspended ){

			logger.log( "Original republish skipped as suspended" );

			return( 0 );
		}

		int	values_published	= 0;

		Map<HashWrapper,List<DHTDBValueImpl>>	republish = new HashMap<>();

		try{
			this_mon.enter();

			for (Entry<HashWrapper, DHTDBMapping> entry : stored_values.entrySet()) {

				HashWrapper		key		= entry.getKey();

				DHTDBMapping	mapping	= entry.getValue();

				Iterator<DHTDBValueImpl>	it2 = mapping.getValues();

				List<DHTDBValueImpl>	values = new ArrayList<>();

				while( it2.hasNext()){

					DHTDBValueImpl	value = it2.next();

					if ( value != null && value.isLocal()){

						// we're republising the data, reset the creation time

						value.setCreationTime();

						values.add( value );
					}
				}

				if ( values.size() > 0 ){

					republish.put( key, values );

				}
			}
		}finally{

			this_mon.exit();
		}

		Iterator<Map.Entry<HashWrapper,List<DHTDBValueImpl>>>	it = republish.entrySet().iterator();

		int key_tot	= republish.size();
		int	key_num = 0;

		while( it.hasNext()){

			key_num++;

			Map.Entry<HashWrapper,List<DHTDBValueImpl>>	entry = it.next();

			HashWrapper			key		= entry.getKey();

			List<DHTDBValueImpl>		values	= entry.getValue();

				// no point in worry about multi-value puts here as it is extremely unlikely that
				// > 1 value will locally stored, or > 1 value will go to the same contact

			for (int i=0;i<values.size();i++){

				values_published++;

				if ( Logger.isClosingTakingTooLong()){
					
					break;
				}
				
				control.putEncodedKey( key.getHash(), "Republish orig: " + key_num + " of " + key_tot, values.get(i), 0, true );
			}
		}

		return( values_published );
	}

	protected int[]
	republishCachedMappings()
	{
		if ( suspended ){

			logger.log( "Cache republish skipped as suspended" );

			return( new int[]{ 0, 0, 0 } );
		}

			// first refresh any leaves that have not performed at least one lookup in the
			// last period

		router.refreshIdleLeaves( cache_republish_interval );

		final Map<HashWrapper,List<DHTDBValueImpl>>	republish = new HashMap<>();

		List<DHTDBMapping>	republish_via_survey = new ArrayList<>();

		long	now = System.currentTimeMillis();

		try{
			this_mon.enter();

			checkCacheExpiration( true );

			for (Entry<HashWrapper, DHTDBMapping> entry : stored_values.entrySet()) {

				HashWrapper			key		= entry.getKey();

				DHTDBMapping		mapping	= entry.getValue();

					// assume that if we've diversified then the other k-1 locations are under similar
					// stress and will have done likewise - no point in republishing cache values to them
					// New nodes joining will have had stuff forwarded to them regardless of diversification
					// status

				if ( mapping.getDiversificationType() != DHT.DT_NONE ){

					continue;
				}

				Iterator<DHTDBValueImpl>	it2 = mapping.getValues();

				boolean	all_rf_values = it2.hasNext();

				List<DHTDBValueImpl>	values = new ArrayList<>();

				while( it2.hasNext()){

					DHTDBValueImpl	value = it2.next();

					if ( value.isLocal()){

						all_rf_values = false;

					}else{

						if ( value.getReplicationFactor() == DHT.REP_FACT_DEFAULT ){

							all_rf_values = false;
						}

							// if this value was stored < period ago then we assume that it was
							// also stored to the other k-1 locations at the same time and therefore
							// we don't need to re-store it

						if ( now < value.getStoreTime()){

								// deal with clock changes

							value.setStoreTime( now );

						}else if ( now - value.getStoreTime() <= cache_republish_interval ){

							// System.out.println( "skipping store" );

						}else{

							values.add( value );
						}
					}
				}

				if ( all_rf_values ){

						// if surveying is disabled then we swallow values here to prevent them
						// from being replicated using the existing technique and muddying the waters

					values.clear();	// handled by the survey process

					republish_via_survey.add( mapping );
				}

				if ( values.size() > 0 ){

					republish.put( key, values );
				}
			}
		}finally{

			this_mon.exit();
		}

		if ( republish_via_survey.size() > 0 ){

				// we still check for being too far away here

			List<HashWrapper>	stop_caching = new ArrayList<>();

			for ( DHTDBMapping mapping: republish_via_survey ){

				HashWrapper			key		= mapping.getKey();

				byte[]	lookup_id	= key.getHash();

				List<DHTTransportContact>	contacts = control.getClosestKContactsList( lookup_id, false );

					// if we are no longer one of the K closest contacts then we shouldn't
					// cache the value

				boolean	keep_caching	= false;

				for (int j=0;j<contacts.size();j++){

					if ( router.isID(contacts.get(j).getID())){

						keep_caching	= true;

						break;
					}
				}

				if ( !keep_caching ){

					DHTLog.log( "Dropping cache entry for " + DHTLog.getString( lookup_id ) + " as now too far away" );

					stop_caching.add( key );
				}
			}

			if ( stop_caching.size() > 0 ){

				try{
					this_mon.enter();

					for (int i=0;i<stop_caching.size();i++){

						DHTDBMapping	mapping = stored_values.remove( stop_caching.get(i));

						if ( mapping != null ){

							removeFromPrefixMap( mapping );

							mapping.destroy();
						}
					}
				}finally{

					this_mon.exit();
				}
			}
		}

		final int[]	values_published	= {0};
		final int[]	keys_published		= {0};
		final int[]	republish_ops		= {0};

		final HashSet<DHTTransportContact>	anti_spoof_done	= new HashSet<>();

		if ( republish.size() > 0 ){

			// System.out.println( "cache replublish" );

				// The approach is to refresh all leaves in the smallest subtree, thus populating the tree with
				// sufficient information to directly know which nodes to republish the values
				// to.

				// However, I'm going to rely on the "refresh idle leaves" logic above
				// (that's required to keep the DHT alive in general) to ensure that all
				// k-buckets are reasonably up-to-date

			Iterator<Map.Entry<HashWrapper,List<DHTDBValueImpl>>>	it1 = republish.entrySet().iterator();

			List<HashWrapper>	stop_caching = new ArrayList<>();

				// build a map of contact -> list of keys to republish

			Map<HashWrapper,Object[]>	contact_map	= new HashMap<>();

			while( it1.hasNext()){

				Map.Entry<HashWrapper,List<DHTDBValueImpl>>	entry = it1.next();

				HashWrapper			key		= entry.getKey();

				byte[]	lookup_id	= key.getHash();

					// just use the closest contacts - if some have failed then they'll
					// get flushed out by this operation. Grabbing just the live ones
					// is a bad idea as failures may rack up against the live ones due
					// to network problems and kill them, leaving the dead ones!

				List<DHTTransportContact>	contacts = control.getClosestKContactsList( lookup_id, false );

					// if we are no longer one of the K closest contacts then we shouldn't
					// cache the value

				boolean	keep_caching	= false;

				for (int j=0;j<contacts.size();j++){

					if ( router.isID(contacts.get(j).getID())){

						keep_caching	= true;

						break;
					}
				}

				if ( !keep_caching ){

					DHTLog.log( "Dropping cache entry for " + DHTLog.getString( lookup_id ) + " as now too far away" );

					stop_caching.add( key );

						// we carry on and do one last publish

				}

				for (int j=0;j<contacts.size();j++){

					DHTTransportContact	contact = contacts.get(j);

					if ( router.isID( contact.getID())){

						continue;	// ignore ourselves
					}

					Object[]	data = contact_map.get( new HashWrapper(contact.getID()));

					if ( data == null ){

						data	= new Object[]{ contact, new ArrayList<HashWrapper>()};

						contact_map.put( new HashWrapper(contact.getID()), data );
					}

					((List<HashWrapper>)data[1]).add( key );
				}
			}

			Iterator<Object[]> it2 = contact_map.values().iterator();

			final int	con_tot 	= contact_map.size();
			int con_num 	= 0;

			while( it2.hasNext()){

				con_num++;

				final int f_con_num = con_num;

				final Object[]	data = it2.next();

				final DHTTransportContact	contact = (DHTTransportContact)data[0];

					// move to anti-spoof on cache forwards - gotta do a find-node first
					// to get the random id

				final AESemaphore	sem = new AESemaphore( "DHTDB:cacheForward" );

				contact.sendFindNode(
						new DHTTransportReplyHandlerAdapter()
						{
							@Override
							public void
							findNodeReply(
								DHTTransportContact 	_contact,
								DHTTransportContact[]	_contacts )
							{
								anti_spoof_done.add( _contact );

								try{
									// System.out.println( "cacheForward: pre-store findNode OK" );

									List<HashWrapper>				keys	= (List<HashWrapper>)data[1];

									byte[][]				store_keys 		= new byte[keys.size()][];
									DHTTransportValue[][]	store_values 	= new DHTTransportValue[store_keys.length][];

									keys_published[0] += store_keys.length;

									for (int i=0;i<store_keys.length;i++){

										HashWrapper	wrapper = keys.get(i);

										store_keys[i] = wrapper.getHash();

										List<DHTDBValueImpl>		values	= republish.get( wrapper );

										store_values[i] = new DHTTransportValue[values.size()];

										values_published[0] += store_values[i].length;

										for (int j=0;j<values.size();j++){

											DHTDBValueImpl	value	= values.get(j);

												// we reduce the cache distance by 1 here as it is incremented by the
												// recipients

											store_values[i][j] = value.getValueForRelay(local_contact);
										}
									}

									List<DHTTransportContact>	contacts = new ArrayList<>();

									contacts.add( contact );

									republish_ops[0]++;

									control.putDirectEncodedKeys(
											store_keys,
											"Republish cache: " + f_con_num + " of " + con_tot,
											store_values,
											contacts );
								}finally{

									sem.release();
								}
							}

							@Override
							public void
							failed(
								DHTTransportContact 	_contact,
								Throwable				_error )
							{
								try{
									// System.out.println( "cacheForward: pre-store findNode Failed" );

									DHTLog.log( "cacheForward: pre-store findNode failed " + DHTLog.getString( _contact ) + " -> failed: " + _error.getMessage());

									router.contactDead( _contact.getID(), false);

								}finally{

									sem.release();
								}
							}
						},
						contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ANTI_SPOOF2?new byte[0]:new byte[20],
						DHT.FLAG_LOOKUP_FOR_STORE );

				sem.reserve();
				
				if ( Logger.isClosingTakingTooLong()){
					
					break;
				}
			}

			try{
				this_mon.enter();

				for (int i=0;i<stop_caching.size();i++){

					DHTDBMapping	mapping = stored_values.remove( stop_caching.get(i));

					if ( mapping != null ){

						removeFromPrefixMap( mapping );

						mapping.destroy();
					}
				}
			}finally{

				this_mon.exit();
			}
		}

		DHTStorageBlock[]	direct_key_blocks = getDirectKeyBlocks();

		if ( direct_key_blocks.length > 0 ){

			for (int i=0;i<direct_key_blocks.length;i++){

				final DHTStorageBlock	key_block = direct_key_blocks[i];

				List	contacts = control.getClosestKContactsList( key_block.getKey(), false );

				boolean	forward_it = false;

					// ensure that the key is close enough to us

				for (int j=0;j<contacts.size();j++){

					final DHTTransportContact	contact = (DHTTransportContact)contacts.get(j);

					if ( router.isID( contact.getID())){

						forward_it	= true;

						break;
					}
				}

				for (int j=0; forward_it && j<contacts.size();j++){

					final DHTTransportContact	contact = (DHTTransportContact)contacts.get(j);

					if ( key_block.hasBeenSentTo( contact )){

						continue;
					}

					if ( router.isID( contact.getID())){

						continue;	// ignore ourselves
					}

					if ( contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_BLOCK_KEYS ){

						final Runnable task =
							new Runnable()
							{
								@Override
								public void
								run()
								{
									contact.sendKeyBlock(
										new DHTTransportReplyHandlerAdapter()
										{
											@Override
											public void
											keyBlockReply(
												DHTTransportContact 	_contact )
											{
												DHTLog.log( "key block forward ok " + DHTLog.getString( _contact ));

												key_block.sentTo( _contact );
											}

											@Override
											public void
											failed(
												DHTTransportContact 	_contact,
												Throwable				_error )
											{
												DHTLog.log( "key block forward failed " + DHTLog.getString( _contact ) + " -> failed: " + _error.getMessage());
											}
										},
										key_block.getRequest(),
										key_block.getCertificate());
								}
							};

							if ( anti_spoof_done.contains( contact )){

								task.run();

							}else{

								contact.sendFindNode(
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

												DHTLog.log( "pre-kb findNode failed " + DHTLog.getString( _contact ) + " -> failed: " + _error.getMessage());

												router.contactDead( _contact.getID(), false);
											}
										},
										contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ANTI_SPOOF2?new byte[0]:new byte[20],
										DHT.FLAG_LOOKUP_FOR_STORE );
							}
					}
				}
			}
		}

		return( new int[]{ values_published[0], keys_published[0], republish_ops[0] });
	}

	protected void
	checkCacheExpiration(
		boolean		force )
	{
		long	 now = SystemTime.getCurrentTime();

		if ( !force ){

			long elapsed = now - last_cache_expiry_check;

			if ( elapsed > 0 && elapsed < MIN_CACHE_EXPIRY_CHECK_INTERVAL ){

				return;
			}
		}

		try{
			this_mon.enter();

			last_cache_expiry_check	= now;

			Iterator<DHTDBMapping>	it = stored_values.values().iterator();

			while( it.hasNext()){

				DHTDBMapping	mapping = it.next();

				if ( mapping.getValueCount() == 0 ){

					it.remove();

					removeFromPrefixMap( mapping );

					mapping.destroy();

				}else{

					Iterator<DHTDBValueImpl>	it2 = mapping.getValues();

					while( it2.hasNext()){

						DHTDBValueImpl	value = it2.next();

						if ( !value.isLocal()){

								// distance 1 = initial store location. We use the initial creation date
								// when deciding whether or not to remove this, plus a bit, as the
								// original publisher is supposed to republish these

							int life_hours = value.getLifeTimeHours();

							int	max_age;

							if ( life_hours < 1 ){

								max_age = original_republish_interval;

							}else{

								max_age = life_hours * 60*60*1000;

								if ( max_age > MAX_VALUE_LIFETIME ){

									max_age = MAX_VALUE_LIFETIME;
								}
							}

							int	grace;

							if (( value.getFlags() & DHT.FLAG_PUT_AND_FORGET ) != 0 ){

								grace = 0;

							}else{

									// scale the grace period for short lifetimes

								grace = Math.min( ORIGINAL_REPUBLISH_INTERVAL_GRACE, max_age/4 );
							}

							if ( now > value.getCreationTime() + max_age + grace ){

								DHTLog.log( "removing cache entry (" + value.getString() + ")" );

								it2.remove();
							}
						}
					}
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	protected void
	addToPrefixMap(
		DHTDBMapping		mapping )
	{
		DHTDBMapping.ShortHash key = mapping.getShortKey();

		DHTDBMapping existing = stored_values_prefix_map.get( key );

			// possible to have clashes, be consistent in which one we use to avoid
			// confusing other nodes

		if ( existing != null ){

			byte[]	existing_full 	= existing.getKey().getBytes();
			byte[]	new_full		= mapping.getKey().getBytes();

			if ( control.computeAndCompareDistances( existing_full, new_full, local_contact.getID()) < 0 ){

				return;
			}
		}

		stored_values_prefix_map.put( key, mapping );

		if ( stored_values_prefix_map.size() > stored_values.size()){

			Debug.out( "inconsistent" );
		}
	}

	protected void
	removeFromPrefixMap(
		DHTDBMapping		mapping )
	{
		DHTDBMapping.ShortHash key = mapping.getShortKey();

		DHTDBMapping existing = stored_values_prefix_map.get( key );

		if ( existing == mapping ){

			stored_values_prefix_map.remove( key );
		}
	}

	protected void
	checkPreciousStuff()
	{
		long	 now = SystemTime.getCurrentTime();

		Map<HashWrapper,List<DHTDBValueImpl>>	republish = new HashMap<>();

		try{

			this_mon.enter();

			Iterator<Map.Entry<HashWrapper,DHTDBMapping>>	it = stored_values.entrySet().iterator();

			while( it.hasNext()){

				Map.Entry<HashWrapper,DHTDBMapping>	entry = it.next();

				HashWrapper		key		= entry.getKey();

				DHTDBMapping	mapping	= entry.getValue();

				Iterator<DHTDBValueImpl>	it2 = mapping.getValues();

				List<DHTDBValueImpl>	values = new ArrayList<>();

				while( it2.hasNext()){

					DHTDBValueImpl	value = it2.next();

					if ( value.isLocal()){

						if (( value.getFlags() & DHT.FLAG_PRECIOUS ) != 0 ){

							if ( now - value.getCreationTime() > PRECIOUS_CHECK_INTERVAL ){

								value.setCreationTime();

								values.add( value );
							}
						}
					}
				}

				if ( values.size() > 0 ){

					republish.put( key, values );

				}
			}
		}finally{

			this_mon.exit();
		}

		Iterator<Map.Entry<HashWrapper,List<DHTDBValueImpl>>>	it = republish.entrySet().iterator();

		while( it.hasNext()){

			Map.Entry<HashWrapper,List<DHTDBValueImpl>>	entry = it.next();

			HashWrapper			key		= entry.getKey();

			List<DHTDBValueImpl>		values	= entry.getValue();

				// no point in worry about multi-value puts here as it is extremely unlikely that
				// > 1 value will locally stored, or > 1 value will go to the same contact

			for (int i=0;i<values.size();i++){

				control.putEncodedKey( key.getHash(), "Precious republish", values.get(i), 0, true );
			}
		}
	}

	protected DHTTransportContact
	getLocalContact()
	{
		return( local_contact );
	}

	protected DHTStorageAdapter
	getAdapter()
	{
		return( adapter );
	}

	protected void
	log(
		String	str )
	{
		logger.log( str );
	}

	@Override
	public DHTDBStats
	getStats()
	{
		return( this );
	}

	protected void
	survey()
	{
		if ( survey_in_progress ){

			return;
		}

		if ( DEBUG_SURVEY ){
			System.out.println( "surveying" );
		}

		checkCacheExpiration( false );

		final byte[]	my_id = router.getID();

		if ( DEBUG_SURVEY ){
			System.out.println( "    my_id=" + ByteFormatter.encodeString( my_id ));
		}

		final ByteArrayHashMap<DHTTransportContact>	id_map = new ByteArrayHashMap<>();

		List<DHTTransportContact> all_contacts = control.getClosestContactsList( my_id, router.getK()*3, true );

		for ( DHTTransportContact contact: all_contacts ){

			id_map.put( contact.getID(), contact );
		}

		byte[]	max_key 	= my_id;
		byte[]	max_dist	= null;

		final List<HashWrapper> applicable_keys = new ArrayList<>();

		try{
			this_mon.enter();

			long	now = SystemTime.getMonotonousTime();

			Iterator<SurveyContactState> s_it = survey_state.values().iterator();

			while( s_it.hasNext()){

				if ( s_it.next().timeout( now )){

					s_it.remove();
				}
			}

			Iterator<DHTDBMapping>	it = stored_values.values().iterator();

			Set<HashWrapper>	existing_times = new HashSet<>(survey_mapping_times.keySet());

			while( it.hasNext()){

				DHTDBMapping	mapping = it.next();

				HashWrapper hw = mapping.getKey();

				if ( existing_times.size() > 0 ){

					existing_times.remove( hw );
				}

				if ( !applyRF( mapping )){

					continue;
				}

				applicable_keys.add( hw );

				byte[] key = hw.getBytes();

				/*
				List<DHTTransportContact>	contacts = control.getClosestKContactsList( key, true );

				for ( DHTTransportContact c: contacts ){

					id_map.put( c.getID(), c );
				}
				*/

				byte[] distance = control.computeDistance( my_id, key );

				if ( max_dist == null || control.compareDistances( distance, max_dist  ) > 0 ){

					max_dist	= distance;
					max_key 	= key;
				}
			}

				// remove dead mappings

			for ( HashWrapper hw: existing_times ){

				survey_mapping_times.remove( hw );
			}

			logger.log( "Survey starts: state size=" + survey_state.size() + ", all keys=" + stored_values.size() + ", applicable keys=" + applicable_keys.size());

		}finally{

			this_mon.exit();
		}

		if ( DEBUG_SURVEY ){
			System.out.println( "    max_key=" + ByteFormatter.encodeString( max_key ) + ", dist=" + ByteFormatter.encodeString( max_dist ) + ", initial_contacts=" + id_map.size());
		}

		if ( max_key == my_id ){

			logger.log( "Survey complete - no applicable values" );

			return;
		}

			// obscure key so we don't leak any keys

		byte[]	obscured_key = control.getObfuscatedKey( max_key );

		final int[]	requery_count = { 0 };

		final boolean[]	processing = { false };

		try{
			survey_in_progress = true;

			control.lookupEncoded(
				obscured_key,
				"Neighbourhood survey: basic",
				0,
				true,
				new DHTOperationAdapter()
				{
					private final List<DHTTransportContact> contacts = new ArrayList<>();

					private boolean	survey_complete;

					@Override
					public void
					found(
						DHTTransportContact	contact,
						boolean				is_closest )
					{
						if ( is_closest ){

							synchronized( contacts ){

								if ( !survey_complete ){

									contacts.add( contact );
								}
							}
						}
					}

					@Override
					public void
					complete(
						boolean				timeout )
					{
						boolean	requeried = false;

						try{
							int	hits	= 0;
							int	misses	= 0;

								// find the closest miss to us and recursively search

							byte[]	min_dist 	= null;
							byte[]	min_id		= null;

							synchronized( contacts ){

								for ( DHTTransportContact c: contacts ){

									byte[]	id = c.getID();

									if ( id_map.containsKey( id )){

										hits++;

									}else{

										misses++;

										if ( id_map.size() >= MAX_SURVEY_SIZE ){

											log( "Max survery size exceeded" );

											break;
										}

										id_map.put( id, c );

										byte[] distance = control.computeDistance( my_id, id );

										if ( min_dist == null || control.compareDistances( distance, min_dist  ) < 0 ){

											min_dist	= distance;
											min_id		= id;
										}
									}
								}

								contacts.clear();
							}

								// if significant misses then re-query

							if ( misses > 0 && misses*100/(hits+misses) >= 25 && id_map.size()< MAX_SURVEY_SIZE ){

								if ( requery_count[0]++ < 5 ){

									if ( DEBUG_SURVEY ){
										System.out.println( "requery at " + ByteFormatter.encodeString( min_id ));
									}

										// don't need to obscure here as its a node-id

									control.lookupEncoded(
										min_id,
										"Neighbourhood survey: level=" + requery_count[0],
										0,
										true,
										this );

									requeried = true;

								}else{

									if ( DEBUG_SURVEY ){
										System.out.println( "requery limit exceeded" );
									}
								}
							}else{

								if ( DEBUG_SURVEY ){
									System.out.println( "super-neighbourhood=" + id_map.size() + " (hits=" + hits + ", misses=" + misses + ", level=" + requery_count[0] + ")" );
								}
							}
						}finally{

							if ( !requeried ){

								synchronized( contacts ){

									survey_complete = true;
								}

								if ( DEBUG_SURVEY ){
									System.out.println( "survey complete: nodes=" + id_map.size());
								}

								processSurvey( my_id, applicable_keys, id_map );

								processing[0] = true;
							}
						}
					}
				});

		}catch( Throwable e ){

			if ( !processing[0] ){

				logger.log( "Survey complete - no applicable nodes" );

				survey_in_progress = false;
			}
		}
	}

	protected void
	processSurvey(
		byte[]									survey_my_id,
		List<HashWrapper>						applicable_keys,
		ByteArrayHashMap<DHTTransportContact>	survey )
	{
		boolean went_async = false;

		try{
			byte[][]	node_ids = new byte[survey.size()][];

			int	pos = 0;

			for ( byte[] id: survey.keys()){

				node_ids[pos++] = id;
			}

			ByteArrayHashMap<List<DHTDBMapping>>	value_map = new ByteArrayHashMap<>();

			Map<DHTTransportContact,ByteArrayHashMap<List<DHTDBMapping>>> request_map = new HashMap<>();

			Map<DHTDBMapping,List<DHTTransportContact>>	mapping_to_node_map = new HashMap<>();

			int max_nodes = Math.min( node_ids.length, router.getK());

			try{
				this_mon.enter();

				Iterator<HashWrapper>	it = applicable_keys.iterator();

				int	value_count = 0;

				while( it.hasNext()){

					DHTDBMapping	mapping = stored_values.get( it.next());

					if ( mapping == null ){

						continue;
					}

					value_count++;

					final byte[] key = mapping.getKey().getBytes();

						// find closest nodes to this key in order to asses availability

					Arrays.sort(
						node_ids,
						new Comparator<byte[]>()
						{
							@Override
							public int
							compare(
								byte[] o1,
								byte[] o2 )
							{
								return( control.computeAndCompareDistances( o1, o2, key ));
							}
						});

					boolean	found_myself = false;

					for ( int i=0;i<max_nodes;i++ ){

						byte[]	id = node_ids[i];

						if ( Arrays.equals( survey_my_id, id )){

							found_myself = true;

							break;
						}
					}

						// if we're not in the closest set to this key then ignore it

					if ( !found_myself ){

						if ( DEBUG_SURVEY ){
							System.out.println( "we're not in closest set for " + ByteFormatter.encodeString( key ) + " - ignoring" );
						}

						continue;
					}

					List<DHTTransportContact>	node_list = new ArrayList<>(max_nodes);

					mapping_to_node_map.put( mapping, node_list );

					for ( int i=0;i<max_nodes;i++ ){

						byte[]	id = node_ids[i];

							// remove ourselves from the equation here as we don't want to end
							// up querying ourselves and we account for the replica we have later
							// on

						if ( Arrays.equals( survey_my_id, id )){

							continue;
						}

						List<DHTDBMapping> list = value_map.get( id );

						if ( list == null ){

							list = new ArrayList<>();

							value_map.put( id, list );
						}

						list.add( mapping );

						node_list.add( survey.get( id ));
					}
				}

				if ( DEBUG_SURVEY ){
					System.out.println( "Total values: " + value_count );
				}

					// build a list of requests to send to nodes to check their replicas

				for ( byte[] id: node_ids ){

					final int MAX_PREFIX_TEST = 3;

					List<DHTDBMapping> all_entries = value_map.remove( id );

					ByteArrayHashMap<List<DHTDBMapping>> prefix_map = new ByteArrayHashMap<>();

					if ( all_entries != null ){

						prefix_map.put( new byte[0], all_entries );

						for (int i=0;i<MAX_PREFIX_TEST;i++){

							List<byte[]> prefixes = prefix_map.keys();

							for ( byte[] prefix: prefixes ){

								if ( prefix.length == i ){

									List<DHTDBMapping> list = prefix_map.get( prefix );

									if ( list.size() < 2 ){

										continue;
									}

									ByteArrayHashMap<List<DHTDBMapping>> temp_map = new ByteArrayHashMap<>();

									for ( DHTDBMapping mapping: list ){

										byte[] key = mapping.getKey().getBytes();

										byte[] sub_prefix = new byte[ i+1 ];

										System.arraycopy( key, 0, sub_prefix, 0, i+1 );

										List<DHTDBMapping> entries = temp_map.get( sub_prefix );

										if ( entries == null ){

											entries = new ArrayList<>();

											temp_map.put( sub_prefix, entries );
										}

										entries.add( mapping );
									}

									List<DHTDBMapping> new_list = new ArrayList<>(list.size());

									List<byte[]> temp_keys = temp_map.keys();

									for ( byte[] k: temp_keys ){

										List<DHTDBMapping> entries = temp_map.get( k );

										int	num	= entries.size();

											// prefix spread over multiple entries so ignore and just count suffix cost

										int outer_cost 	= num * ( QUERY_STORE_REQUEST_ENTRY_SIZE - i );

											// include new prefix, one byte prefix len, 2 bytes num-suffixes, then suffixes
											// yes, this code should be elsewhere, but whatever

										int inner_cost	= i+4 + num * (QUERY_STORE_REQUEST_ENTRY_SIZE - i - 1 );

										if ( inner_cost < outer_cost ){

											prefix_map.put( k, entries );

										}else{

											new_list.addAll( entries );
										}
									}

									if ( new_list.size() == 0 ){

										prefix_map.remove( prefix );

									}else{

										prefix_map.put( prefix, new_list );
									}
								}
							}
						}

						String str = "";

						int encoded_size = 1;	// header size

						List<byte[]> prefixes = prefix_map.keys();

						for ( byte[] prefix: prefixes ){

							encoded_size += 3 + prefix.length;

							List<DHTDBMapping> entries = prefix_map.get( prefix );

							encoded_size += ( QUERY_STORE_REQUEST_ENTRY_SIZE - prefix.length ) * entries.size();

							str += (str.length()==0?"":", ")+ ByteFormatter.encodeString( prefix ) + "->" + entries.size();
						}

						if ( DEBUG_SURVEY ){
							System.out.println( "node " + ByteFormatter.encodeString( id ) + " -> " + (all_entries==null?0:all_entries.size()) + ", encoded=" + encoded_size + ", prefix=" + str );
						}

						if ( prefixes.size() > 0 ){

							request_map.put( survey.get( id ), prefix_map );
						}
					}
				}
			}finally{

				this_mon.exit();
			}

			LinkedList<Map.Entry<DHTTransportContact,ByteArrayHashMap<List<DHTDBMapping>>>> to_do = new LinkedList<>(request_map.entrySet());

			Map<DHTTransportContact,Object[]>	replies = new HashMap<>();

			for ( int i=0;i<Math.min(3,to_do.size());i++ ){

				went_async = true;

				doQuery( survey_my_id, request_map.size(), mapping_to_node_map, to_do, replies, null, null, null );
			}

		}finally{

			if ( !went_async ){

				logger.log( "Survey complete - no applicable queries" );

				survey_in_progress = false;
			}
		}
	}

	protected boolean
	applyRF(
		DHTDBMapping	mapping )
	{
		if ( mapping.getDiversificationType() != DHT.DT_NONE ){

			return( false );
		}

		if ( SURVEY_ONLY_RF_KEYS ){

			Iterator<DHTDBValueImpl>	it2 = mapping.getValues();

			if ( !it2.hasNext()){

				return( false );
			}

			int	min_period = Integer.MAX_VALUE;

			long	min_create = Long.MAX_VALUE;

			while( it2.hasNext()){

				DHTDBValueImpl value = it2.next();

				byte rep_fact = value.getReplicationFactor();

				if ( rep_fact == DHT.REP_FACT_DEFAULT || rep_fact == 0 ){

					return( false );
				}

				int hours = value.getReplicationFrequencyHours()&0xff;

				if ( hours < min_period ){

					min_period = hours;
				}

				min_create = Math.min( min_create, value.getCreationTime());
			}

			if ( min_period > 0 ){

				HashWrapper hw = mapping.getKey();

				Long	next_time = survey_mapping_times.get( hw );

				long now = SystemTime.getMonotonousTime();

				if ( next_time != null && next_time > now ){

					return( false );
				}

				long	period		= min_period*60*60*1000;

				long	offset_time = ( SystemTime.getCurrentTime() - min_create ) % period;

				long	rand		= RandomUtils.nextInt( 30*60*1000 ) - 15*60*1000;

				long	new_next_time = now - offset_time + period + rand;

				if ( new_next_time < now + 30*60*1000 ){

					new_next_time += period;
				}

				if ( DEBUG_SURVEY ){
					System.out.println( "allocated next time with value relative " + (new_next_time-now) + ": period=" + period + ", offset=" + offset_time + ", rand=" + rand );
				}

				survey_mapping_times.put( hw, new_next_time );

				if ( next_time == null ){

					return( false );
				}
			}
		}

		return( true );
	}

	protected void
	doQuery(
		final byte[]			survey_my_id,
		final int				total,
		final Map<DHTDBMapping,List<DHTTransportContact>>										mapping_to_node_map,
		final LinkedList<Map.Entry<DHTTransportContact,ByteArrayHashMap<List<DHTDBMapping>>>>	to_do,
		final Map<DHTTransportContact,Object[]>													replies,
		DHTTransportContact		done_contact,
		List<DHTDBMapping>		done_mappings,
		List<byte[]>			done_reply )
	{
		Map.Entry<DHTTransportContact,ByteArrayHashMap<List<DHTDBMapping>>>	entry;

		synchronized( to_do ){

			if ( done_contact != null ){

				replies.put( done_contact, new Object[]{ done_mappings, done_reply });
			}

			if ( to_do.size() == 0 ){

				if ( replies.size() == total ){

					queriesComplete( survey_my_id, mapping_to_node_map, replies );
				}

				return;
			}

			entry = to_do.removeFirst();
		}

		DHTTransportContact contact = entry.getKey();

		boolean	handled = false;

		try{
			if ( contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_REPLICATION_CONTROL3 ){

				if ( DEBUG_SURVEY ){
					System.out.println( "Hitting " + contact.getString());
				}

				final List<DHTDBMapping>	mapping_list = new ArrayList<>();

				ByteArrayHashMap<List<DHTDBMapping>>	map = entry.getValue();

				List<byte[]> prefixes = map.keys();

				List<Object[]> encoded = new ArrayList<>(prefixes.size());

				try{
					this_mon.enter();

					SurveyContactState contact_state = survey_state.get( new HashWrapper( contact.getID()));

					for ( byte[] prefix: prefixes ){

						int	prefix_len = prefix.length;
						int	suffix_len = QUERY_STORE_REQUEST_ENTRY_SIZE - prefix_len;

						List<DHTDBMapping> mappings = map.get( prefix );

						List<byte[]> l = new ArrayList<>(mappings.size());

						encoded.add( new Object[]{ prefix, l });

							// remove entries that we know the contact already has
							// and then add them back in in the query-reply. note that we
							// still need to hit the contact if we end up with no values to
							// query as need to ascertain liveness. We might want to wait until,
							// say, 2 subsequent fails before treating contact as dead

						for ( DHTDBMapping m: mappings ){

							if ( contact_state != null ){

								if ( contact_state.testMapping(m)){

									if ( DEBUG_SURVEY ){
										System.out.println( "    skipping " + ByteFormatter.encodeString( m.getKey().getBytes()) + " as contact already has" );
									}

									continue;
								}
							}

							mapping_list.add( m );

							byte[]	k = m.getKey().getBytes();

							byte[]	suffix = new byte[ suffix_len ];

							System.arraycopy( k, prefix_len, suffix, 0, suffix_len );

							l.add( suffix );
						}
					}

					if ( Arrays.equals( contact.getID(), survey_my_id )){

						Debug.out( "inconsistent - we shouldn't query ourselves!" );
					}

					contact.sendQueryStore(
						new DHTTransportReplyHandlerAdapter()
						{
							@Override
							public void
							queryStoreReply(
								DHTTransportContact contact,
								List<byte[]>		response )
							{
								try{
									if ( DEBUG_SURVEY ){
										System.out.println( "response " + response.size());

										for (int i=0;i<response.size();i++){

											System.out.println( "    " + ByteFormatter.encodeString( response.get(i)));
										}
									}
								}finally{

									doQuery( survey_my_id, total, mapping_to_node_map, to_do, replies, contact, mapping_list, response );
								}
							}

							@Override
							public void
							failed(
								DHTTransportContact 	contact,
								Throwable				error )
							{
								try{
									if ( DEBUG_SURVEY ){
										System.out.println( "Failed: " + Debug.getNestedExceptionMessage( error ));
									}
								}finally{

									doQuery( survey_my_id, total, mapping_to_node_map, to_do, replies, contact, mapping_list, null );
								}
							}
						}, QUERY_STORE_REQUEST_ENTRY_SIZE, encoded );

					handled = true;

				}finally{

					this_mon.exit();
				}
			}else{
				if ( DEBUG_SURVEY ){
					System.out.println( "Not hitting " + contact.getString());
				}
			}
		}finally{

			if ( !handled ){

				final List<DHTDBMapping>	mapping_list = new ArrayList<>();

				ByteArrayHashMap<List<DHTDBMapping>>	map = entry.getValue();

				List<byte[]> prefixes = map.keys();

				for ( byte[] prefix: prefixes ){

					mapping_list.addAll( map.get( prefix ));
				}

				doQuery( survey_my_id, total, mapping_to_node_map, to_do, replies, contact, mapping_list, null );
			}
		}
	}

	protected void
	queriesComplete(
		byte[]											survey_my_id,
		Map<DHTDBMapping,List<DHTTransportContact>>		mapping_to_node_map,
		Map<DHTTransportContact,Object[]>				replies )
	{
		Map<SurveyContactState,List<DHTDBMapping>>	store_ops = new HashMap<>();

		try{
			this_mon.enter();

			if ( !Arrays.equals( survey_my_id, router.getID())){

				logger.log( "Survey abandoned - router changed" );

				return;
			}

			if ( DEBUG_SURVEY ){
				System.out.println( "Queries complete (replies=" + replies.size() + ")" );
			}

			Map<DHTDBMapping,int[]>	totals = new HashMap<>();

			for ( Map.Entry<DHTTransportContact,Object[]> entry: replies.entrySet()){

				DHTTransportContact	contact = entry.getKey();

				HashWrapper hw = new HashWrapper( contact.getID());

				SurveyContactState	contact_state = survey_state.get( hw );

				if ( contact_state != null ){

					contact_state.updateContactDetails( contact );

				}else{

					contact_state = new SurveyContactState( contact );

					survey_state.put( hw, contact_state );
				}

				contact_state.updateUseTime();

				Object[]			temp	= entry.getValue();

				List<DHTDBMapping>	mappings 	= (List<DHTDBMapping>)temp[0];
				List<byte[]>		reply		= (List<byte[]>)temp[1];

				if ( reply == null ){

					contact_state.contactFailed();

				}else{

					contact_state.contactOK();

					if ( mappings.size() != reply.size()){

						Debug.out( "Inconsistent: mappings=" + mappings.size() + ", reply=" + reply.size());

						continue;
					}

					Iterator<DHTDBMapping>	it1 = mappings.iterator();
					Iterator<byte[]>		it2 = reply.iterator();

					while( it1.hasNext()){

						DHTDBMapping	mapping = it1.next();
						byte[]			rep		= it2.next();

						if ( rep == null ){

							contact_state.removeMapping( mapping );

						}else{

								// must match against our short-key mapping for consistency

							DHTDBMapping mapping_to_check = stored_values_prefix_map.get( mapping.getShortKey());

							if ( mapping_to_check == null ){

								// deleted

							}else{

								byte[] k = mapping_to_check.getKey().getBytes();

								int	rep_len = rep.length;

								if ( rep_len < 2 || rep_len >= k.length ){

									Debug.out( "Invalid rep_len: " + rep_len );

									continue;
								}

								boolean	match = true;

								int	offset = k.length-rep_len;

								for (int i=0;i<rep_len;i++){

									if ( rep[i] != k[i+offset] ){

										match = false;

										break;
									}
								}

								if ( match ){

									contact_state.addMapping( mapping );

								}else{

									contact_state.removeMapping( mapping );
								}
							}
						}
					}

					Set<DHTDBMapping> contact_mappings = contact_state.getMappings();

					for ( DHTDBMapping m: contact_mappings ){

						int[] t = totals.get( m );

						if ( t == null ){

							t = new int[]{ 2 };		// one for local node + 1 for them

							totals.put( m, t );

						}else{

							t[0]++;
						}
					}
				}
			}

			for (Map.Entry<DHTDBMapping,List<DHTTransportContact>> entry: mapping_to_node_map.entrySet()){

				DHTDBMapping				mapping 	= entry.getKey();
				List<DHTTransportContact>	contacts 	= entry.getValue();

				int[]	t = totals.get( mapping );

				int	copies;

				if ( t == null ){

					copies = 1;		// us!

				}else{

					copies = t[0];
				}

				Iterator<DHTDBValueImpl> values = mapping.getValues();

				if ( values.hasNext()){

					int	max_replication_factor = -1;

					while( values.hasNext()){

						DHTDBValueImpl value = values.next();

						int	rf = value.getReplicationFactor();

						if ( rf > max_replication_factor ){

							max_replication_factor = rf;
						}
					}

					if ( max_replication_factor == 0 ){

						continue;
					}

					if ( max_replication_factor > router.getK()){

						max_replication_factor = router.getK();
					}

					if ( copies < max_replication_factor ){

						int	required = max_replication_factor - copies;

						List<SurveyContactState> potential_targets = new ArrayList<>();

						List<byte[]>	addresses = new ArrayList<>(contacts.size());

						for ( DHTTransportContact c: contacts ){

							if ( c.getProtocolVersion() < DHTTransportUDP.PROTOCOL_VERSION_REPLICATION_CONTROL3 ){

								continue;
							}

							addresses.add( AddressUtils.getAddressBytes( c.getAddress()));

							SurveyContactState	contact_state = survey_state.get( new HashWrapper( c.getID()));

							if ( contact_state != null && !contact_state.testMapping( mapping )){

								potential_targets.add( contact_state );
							}
						}

						Set<HashWrapper>	bad_addresses = new HashSet<>();

						for ( byte[] a1: addresses ){

							for ( byte[] a2: addresses ){

									// ignore ipv6 for the moment...

								if ( a1 == a2 || a1.length != a2.length || a1.length != 4 ){

									continue;
								}

									// ignore common /16 s

								if ( a1[0] == a2[0] && a1[1] == a2[1] ){

									log( "/16 match on " + ByteFormatter.encodeString( a1 ) + "/" + ByteFormatter.encodeString( a2 ));

									bad_addresses.add( new HashWrapper( a1 ));
									bad_addresses.add( new HashWrapper( a2 ));
								}
							}
						}

						final byte[] key = mapping.getKey().getBytes();

						Collections.sort(
							potential_targets,
							new Comparator<SurveyContactState>()
							{
								@Override
								public int
								compare(
									SurveyContactState o1,
									SurveyContactState o2)
								{
									boolean o1_bad = o1.getConsecFails() >= 2;
									boolean o2_bad = o2.getConsecFails() >= 2;

									if ( o1_bad == o2_bad ){

											// switch from age based to closest as per Roxana's advice

										if ( false ){

											long res = o2.getCreationTime() - o1.getCreationTime();

											if ( res < 0 ){

												return( -1 );

											}else if ( res > 0 ){

												return( 1 );

											}else{

												return( 0 );
											}
										}else{

											return(
												control.computeAndCompareDistances(
														o1.getContact().getID(),
														o2.getContact().getID(),
														key ));
										}
									}else{

										if ( o1_bad ){

											return( 1 );

										}else{

											return( -1 );
										}
									}
								}
							});

						int	avail = Math.min( required, potential_targets.size());

						for (int i=0;i<avail;i++){

							SurveyContactState target = potential_targets.get( i );

							if ( 	bad_addresses.size() > 0 &&
									bad_addresses.contains( new HashWrapper( AddressUtils.getAddressBytes( target.getContact().getAddress())))){

									// make it look like this target has the mapping as we don't want to store it there but we want to treat it as
									// if it has it, effectively reducing availability but not skewing storage in favour of potentially malicious nodes

								target.addMapping( mapping );

							}else{

								List<DHTDBMapping> m = store_ops.get( target );

								if ( m == null ){

									m = new ArrayList<>();

									store_ops.put( target, m );
								}

								m.add( mapping );
							}
						}
					}
				}
			}
		}finally{

			this_mon.exit();

			survey_in_progress = false;
		}

		logger.log( "Survey complete - " + store_ops.size() + " store ops" );

		if ( DEBUG_SURVEY ){
			System.out.println( "Store ops: " + store_ops.size());
		}

		for ( Map.Entry<SurveyContactState,List<DHTDBMapping>> store_op: store_ops.entrySet()){

			final SurveyContactState 	contact = store_op.getKey();
			final List<DHTDBMapping>	keys	= store_op.getValue();

			final byte[][]				store_keys 		= new byte[keys.size()][];
			final DHTTransportValue[][]	store_values 	= new DHTTransportValue[store_keys.length][];

			for (int i=0;i<store_keys.length;i++){

				DHTDBMapping	mapping = keys.get(i);

				store_keys[i] = mapping.getKey().getBytes();

				List<DHTTransportValue> v = new ArrayList<>();

				Iterator<DHTDBValueImpl> it = mapping.getValues();

				while( it.hasNext()){

					DHTDBValueImpl value = it.next();

					if ( !value.isLocal()){

						v.add( value.getValueForRelay(local_contact));
					}
				}

				store_values[i] = v.toArray( new DHTTransportValue[v.size()]);
			}

			final DHTTransportContact d_contact = contact.getContact();

			final Runnable	store_exec =
				new Runnable()
				{
					@Override
					public void
					run()
					{
						if ( DEBUG_SURVEY ){
							System.out.println( "Storing " + keys.size() + " on " + d_contact.getString() + " - rand=" + d_contact.getRandomID());
						}

						control.putDirectEncodedKeys(
								store_keys,
								"Replication forward",
								store_values,
								d_contact,
								new DHTOperationAdapter()
								{
									@Override
									public void
									complete(
										boolean				timeout )
									{
										try{
											this_mon.enter();

											if ( timeout ){

												contact.contactFailed();

											}else{

												contact.contactOK();

												for ( DHTDBMapping m: keys ){

													contact.addMapping( m );
												}
											}
										}finally{

											this_mon.exit();
										}
									}
								});
					}
				};

			if ( d_contact.getRandomIDType() != DHTTransportContact.RANDOM_ID_TYPE1 ){

				Debug.out( "derp" );
			}

			if ( d_contact.getRandomID() == 0 ){

				d_contact.sendFindNode(
					new DHTTransportReplyHandlerAdapter()
					{
						@Override
						public void
						findNodeReply(
							DHTTransportContact 	_contact,
							DHTTransportContact[]	_contacts )
						{
							store_exec.run();
						}

						@Override
						public void
						failed(
							DHTTransportContact 	_contact,
							Throwable				_error )
						{
							try{
								this_mon.enter();

								contact.contactFailed();

							}finally{

								this_mon.exit();
							}
						}
					},
					d_contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ANTI_SPOOF2?new byte[0]:new byte[20],
					DHT.FLAG_LOOKUP_FOR_STORE );
			}else{

				store_exec.run();
			}
		}
	}

	private void
	sleep()
	{
		Iterator<Map.Entry<HashWrapper,DHTDBMapping>>	it = stored_values.entrySet().iterator();

		while( it.hasNext()){

			Map.Entry<HashWrapper,DHTDBMapping>	entry = it.next();

			HashWrapper			key		= entry.getKey();

			DHTDBMapping		mapping	= entry.getValue();

			Iterator<DHTDBValueImpl>	it2 = mapping.getValues();

			boolean	all_remote = it2.hasNext();

			while( it2.hasNext()){

				DHTDBValueImpl	value = it2.next();

				if ( value.isLocal()){

					all_remote = false;

					break;
				}
			}

			if ( all_remote ){

				it.remove();

				removeFromPrefixMap( mapping );

				mapping.destroy();
			}
		}
	}

	@Override
	public void
	setSleeping(
		boolean	asleep )
	{
		try{
			this_mon.enter();

			sleeping = asleep;

			if ( asleep ){

				sleep();
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	setSuspended(
		boolean			susp )
	{
		boolean	waking_up;

		try{
			this_mon.enter();

			waking_up = suspended && !susp;

			suspended = susp;

			if ( susp ){

				sleep();
			}

		}finally{

			this_mon.exit();
		}

		if ( waking_up ){

			new AEThread2( "DHTB:resume" )
			{
				@Override
				public void
				run()
				{
					try{
							// give things a chance to get running again

						Thread.sleep(15*1000 );

					}catch( Throwable e ){
					}

					logger.log( "Force republish of original mappings due to resume from suspend" );

					long start 	= SystemTime.getMonotonousTime();

					int stats = republishOriginalMappings();

					long end 	= SystemTime.getMonotonousTime();

					logger.log( "Force republish of original mappings due to resume from suspend completed in " + (end-start) + ": " +
								"values = " + stats );
				}
			}.start();
		}
	}

	@Override
	public DHTTransportQueryStoreReply
	queryStore(
		DHTTransportContact 		originating_contact,
		int							header_len,
		List<Object[]>				keys )
	{
		final List<byte[]> reply = new ArrayList<>();

		try{
			this_mon.enter();

			SurveyContactState	existing_state = survey_state.get( new HashWrapper( originating_contact.getID()));

			if ( existing_state != null ){

				existing_state.updateContactDetails( originating_contact );
			}

			for (Object[] entry: keys ){

				byte[]			prefix 		= (byte[])entry[0];
				List<byte[]>	suffixes 	= (List<byte[]>)entry[1];

				byte[]	header = new byte[header_len];

				int		prefix_len	= prefix.length;
				int		suffix_len	= header_len - prefix_len;

				System.arraycopy( prefix, 0, header, 0, prefix_len );

				for ( byte[] suffix: suffixes ){

					System.arraycopy( suffix, 0, header, prefix_len, suffix_len );

					DHTDBMapping mapping = stored_values_prefix_map.get( new DHTDBMapping.ShortHash( header ));

					if ( mapping == null ){

						reply.add( null );

					}else{

						if ( existing_state != null ){

							existing_state.addMapping( mapping );
						}

						byte[] k = mapping.getKey().getBytes();

						byte[] r = new byte[QUERY_STORE_REPLY_ENTRY_SIZE];

						System.arraycopy( k, k.length-QUERY_STORE_REPLY_ENTRY_SIZE, r, 0, QUERY_STORE_REPLY_ENTRY_SIZE );

						reply.add( r );
					}
				}
			}

			return(
				new DHTTransportQueryStoreReply()
				{
					@Override
					public int
					getHeaderSize()
					{
						return( QUERY_STORE_REPLY_ENTRY_SIZE );
					}

					@Override
					public List<byte[]>
					getEntries()
					{
						return( reply );
					}
				});

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	print(
		boolean	full )
	{
		Map<Integer,Object[]>	count = new TreeMap<>();

		try{
			this_mon.enter();

			logger.log( "Stored keys = " + stored_values.size() + ", values = " + getValueDetails()[DHTDBStats.VD_VALUE_COUNT]);

			if ( !full ){

				return;
			}

			Iterator<Map.Entry<HashWrapper,DHTDBMapping>>	it1 = stored_values.entrySet().iterator();

			// ByteArrayHashMap<Integer> blah = new ByteArrayHashMap<>();

			while( it1.hasNext()){

				Map.Entry<HashWrapper,DHTDBMapping>		entry = it1.next();

				HashWrapper		value_key	= entry.getKey();

				DHTDBMapping	mapping 	= entry.getValue();

				/*
				if ( mapping.getIndirectSize() > 1000 ){
					mapping.print();
				}
				*/

				DHTDBValue[]	values = mapping.get(null,0,(byte)0);

				for (int i=0;i<values.length;i++){

					DHTDBValue	value = values[i];

					/*
					byte[] v = value.getValue();

					Integer y = blah.get( v );

					if ( y == null ){
						blah.put( v, 1 );
					}else{
						blah.put( v, y+1 );
					}
					*/

					Integer key = new Integer( value.isLocal()?0:1);

					Object[]	data = count.get( key );

					if ( data == null ){

						data = new Object[2];

						data[0] = new Integer(1);

						data[1] = "";

						count.put( key, data );

					}else{

						data[0] = new Integer(((Integer)data[0]).intValue() + 1 );
					}

					String	s = (String)data[1];

					s += (s.length()==0?"":", ") + "key=" + DHTLog.getString2(value_key.getHash()) + ",val=" + value.getString();

					data[1]	= s;
				}
			}

			/*
			long	total_dup = 0;

			for ( byte[] k: blah.keys()){

				int c = blah.get( k );

				if ( c > 1 ){

					total_dup += ( c * k.length );

					System.out.println( "Dup: " + new String(k) + " -> " + c );
				}
			}

			System.out.println( "Total dup: " + total_dup );
			*/

			Iterator<Integer> it2 = count.keySet().iterator();

			while( it2.hasNext()){

				Integer	k = it2.next();

				Object[]	data = count.get(k);

				logger.log( "    " + k + " -> " + data[0] + " entries" ); // ": " + data[1]);
			}

			Iterator<Map.Entry<HashWrapper,DHTDBMapping>> it3 = stored_values.entrySet().iterator();

			StringBuilder	sb = new StringBuilder( 1024 );

			int		str_entries	= 0;

			while( it3.hasNext()){

				Map.Entry<HashWrapper,DHTDBMapping>		entry = it3.next();

				HashWrapper		value_key	= entry.getKey();

				DHTDBMapping	mapping 	= entry.getValue();

				if ( str_entries == 16 ){

					logger.log( sb.toString());

					sb = new StringBuilder( 1024 );

					sb.append( "    " );

					str_entries	= 0;
				}

				str_entries++;

				if ( str_entries > 1 ){
					sb.append( ", ");
				}
				sb.append( DHTLog.getString2(value_key.getHash()));
				sb.append( " -> " );
				sb.append( mapping.getValueCount());
				sb.append( "/" );
				sb.append( mapping.getHits());
				sb.append( "[" );
				sb.append( mapping.getLocalSize());
				sb.append( "," );
				sb.append( mapping.getDirectSize());
				sb.append( "," );
				sb.append( mapping.getIndirectSize());
				sb.append( "]" );
			}

			if ( str_entries > 0 ){

				logger.log( sb.toString());
			}
		}finally{

			this_mon.exit();
		}
	}

	protected void
	banContact(
		final DHTTransportContact	contact,
		final String				reason )
	{
		// CVS DHT can be significantly smaller than mainline (e.g. 1000) so will trigger
		// un-necessary banning which then obviously affects the main DHTs. So we disable
		// banning for CVS

		// same is currently true of the IPv6 one :(

		final boolean ban_ip = DHTFactory.isLargeNetwork( control.getTransport().getNetwork());

		new AEThread2( "DHTDBImpl:delayed flood delete", true )
		{
			@Override
			public void
			run()
			{
					// delete their data on a separate thread so as not to
					// interfere with the current action

				try{
					this_mon.enter();

					Iterator<DHTDBMapping>	it = stored_values.values().iterator();

					boolean	overall_deleted = false;

					HashWrapper value_id = new HashWrapper( contact.getID());

					while( it.hasNext()){

						DHTDBMapping	mapping = it.next();

						boolean	deleted = false;

						if ( mapping.removeDirectValue( value_id ) != null ){

							deleted = true;
						}

						if ( mapping.removeIndirectValue( value_id ) != null ){

							deleted = true;
						}


						if ( deleted && !ban_ip ){

								// if we're not banning then rebuild bloom to avoid us continually
								// going through this ban code

							mapping.rebuildIPBloomFilter( false );

							overall_deleted = true;
						}
					}

					if ( overall_deleted && !ban_ip ){

						rebuildIPBloomFilter( false );
					}
				}finally{

					this_mon.exit();

				}
			}
		}.start();

		if ( ban_ip ){

			logger.log( "Banning " + contact.getString() + " due to store flooding (" + reason + ")" );

			ip_filter.ban(
					AddressUtils.getHostAddress( contact.getAddress()),
					"DHT: Sender stored excessive entries at this node (" + reason + ")", false );
		}
	}

	protected void
	incrementValueAdds(
		DHTTransportContact	contact )
	{
			// assume a node stores 1000 values at 20 (K) locations -> 20,000 values
			// assume a DHT size of 100,000 nodes
			// that is, on average, 1 value per 5 nodes
			// assume NAT of up to 30 ports per address
			// this gives 6 values per address
			// with a factor of 10 error this is still only 60 per address

			// However, for CVS DHTs we can have sizes of 1000 or less.

		byte[] bloom_key = contact.getBloomKey();

		int	hit_count = ip_count_bloom_filter.add( bloom_key );

		if ( DHTLog.GLOBAL_BLOOM_TRACE ){

			System.out.println( "direct add from " + contact.getAddress() + ", hit count = " + hit_count );
		}

			// allow up to 10% bloom filter utilisation

		if ( ip_count_bloom_filter.getSize() / ip_count_bloom_filter.getEntryCount() < 10 ){

			rebuildIPBloomFilter( true );
		}

		if ( hit_count > 64 ){

				// obviously being spammed, drop all data originated by this IP and ban it

			banContact( contact, "global flood" );
		}
	}

	protected void
	decrementValueAdds(
		DHTTransportContact	contact )
	{
		byte[] bloom_key = contact.getBloomKey();

		int	hit_count = ip_count_bloom_filter.remove( bloom_key );

		if ( DHTLog.GLOBAL_BLOOM_TRACE ){

			System.out.println( "direct remove from " + contact.getAddress() + ", hit count = " + hit_count );
		}
	}

	protected void
	rebuildIPBloomFilter(
		boolean	increase_size )
	{
		BloomFilter	new_filter;

		if ( increase_size ){

			new_filter = BloomFilterFactory.createAddRemove8Bit( ip_count_bloom_filter.getSize() + IP_COUNT_BLOOM_SIZE_INCREASE_CHUNK );

		}else{

			new_filter = BloomFilterFactory.createAddRemove8Bit( ip_count_bloom_filter.getSize());

		}

		try{

			//Map		sender_map	= new HashMap();
			//List	senders		= new ArrayList();

			Iterator<DHTDBMapping>	it = stored_values.values().iterator();

			int	max_hits = 0;

			while( it.hasNext()){

				DHTDBMapping	mapping = it.next();

				mapping.rebuildIPBloomFilter( false );

				Iterator<DHTDBValueImpl>	it2 = mapping.getDirectValues();

				while( it2.hasNext()){

					DHTDBValueImpl	val = it2.next();

					if ( !val.isLocal()){

						// logger.log( "    adding " + val.getOriginator().getAddress());

						byte[] bloom_key = val.getOriginator().getBloomKey();

						int	hits = new_filter.add( bloom_key );

						if ( hits > max_hits ){

							max_hits = hits;
						}
					}
				}

					// survey our neighbourhood

				/*
				 * its is non-trivial to do anything about nodes that get "close" to us and then
				 * spam us with crap. Ultimately, of course, to take a key out you "just" create
				 * the 20 closest nodes to the key and then run nodes that swallow all registrations
				 * and return nothing.
				 * Protecting against one or two such nodes that flood crap requires crap to be
				 * identified. Tracing shows a large disparity between number of values registered
				 * per neighbour (factors of 100), so an approach based on number of registrations
				 * is non-trivial (assuming future scaling of the DHT, what do we consider crap?)
				 * A further approach would be to query the claimed originators of values (obviously
				 * a low bandwidth approach, e.g. query 3 values from the contact with highest number
				 * of forwarded values). This requires originators to support long term knowledge of
				 * what they've published (we don't want to blacklist a neighbour because an originator
				 * has deleted a value/been restarted). We also then have to consider how to deal with
				 * non-responses to queries (assuming an affirmative Yes -> value has been forwarded
				 * correnctly, No -> probably crap). We can't treat non-replies as No. Thus a bad
				 * neighbour only has to forward crap with originators that aren't AZ nodes (very
				 * easy to do!) to break this approach.
				 *
				 *
				it2 = mapping.getIndirectValues();

				while( it2.hasNext()){

					DHTDBValueImpl	val = (DHTDBValueImpl)it2.next();

					DHTTransportContact sender = val.getSender();

					HashWrapper	hw = new HashWrapper( sender.getID());

					Integer	sender_count = (Integer)sender_map.get( hw );

					if ( sender_count == null ){

						sender_count = new Integer(1);

						senders.add( sender );

					}else{

						sender_count = new Integer( sender_count.intValue() + 1 );
					}

					sender_map.put( hw, sender_count );
				}
				*/
			}

			logger.log( "Rebuilt global IP bloom filter, size=" + new_filter.getSize() + ", entries=" + new_filter.getEntryCount()+", max hits=" + max_hits );

			/*
			senders = control.sortContactsByDistance( senders );

			for (int i=0;i<senders.size();i++){

				DHTTransportContact	sender = (DHTTransportContact)senders.get(i);

				System.out.println( i + ":" + sender.getString() + " -> " + sender_map.get(new HashWrapper(sender.getID())));
			}
			*/

		}finally{

			ip_count_bloom_filter	= new_filter;
		}
	}

	protected void
	reportSizes(
		String	op )
	{
		/*
		if ( !this_mon.isHeld()){

			Debug.out( "Monitor not held" );
		}

		int	actual_keys 	= stored_values.size();
		int	actual_values 	= 0;
		int actual_size		= 0;

		Iterator it = stored_values.values().iterator();

		while( it.hasNext()){

			DHTDBMapping	mapping = (DHTDBMapping)it.next();

			int	reported_size = mapping.getLocalSize() + mapping.getDirectSize() + mapping.getIndirectSize();

			actual_values += mapping.getValueCount();

			Iterator	it2 = mapping.getValues();

			int	sz = 0;

			while( it2.hasNext()){

				DHTDBValue	val = (DHTDBValue)it2.next();

				sz += val.getValue().length;
			}

			if ( sz != reported_size ){

				Debug.out( "Reported mapping size != actual: " + reported_size + "/" + sz );
			}

			actual_size += sz;
		}

		if ( actual_keys != total_keys ){

			Debug.out( "Actual keys != total: " + actual_keys + "/" + total_keys );
		}

		if ( adapter.getKeyCount() != actual_keys ){

			Debug.out( "SM keys != total: " + actual_keys + "/" + adapter.getKeyCount());
		}

		if ( actual_values != total_values ){

			Debug.out( "Actual values != total: " + actual_values + "/" + total_values );
		}

		if ( actual_size != total_size ){

			Debug.out( "Actual size != total: " + actual_size + "/" + total_size );
		}

		if ( actual_values < actual_keys ){

			Debug.out( "Actual values < actual keys: " + actual_values + "/" + actual_keys );
		}

		System.out.println( "DHTDB: " + op + " - keys=" + total_keys + ", values=" + total_values + ", size=" + total_size );
		*/
	}

	protected int
	getNextValueVersion()
	{
		try{
			this_mon.enter();

			if ( next_value_version_left == 0 ){

				next_value_version_left = VALUE_VERSION_CHUNK;

				if ( adapter == null ){

						// no persistent manager, just carry on incrementing

				}else{

					next_value_version = adapter.getNextValueVersions( VALUE_VERSION_CHUNK );
				}

				//System.out.println( "next chunk:" + next_value_version );
			}

			next_value_version_left--;

			int	res = next_value_version++;

			//System.out.println( "next value version = " + res );

			return( res  );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	destroy()
	{
		destroyed	= true;

		if ( precious_timer != null ){

			precious_timer.cancel();
		}
		if ( original_republish_timer != null ){

			original_republish_timer.cancel();
		}
		if ( cache_republish_timer != null ){

			cache_republish_timer.cancel();
		}
		if ( bloom_timer != null ){

			bloom_timer.cancel();
		}
		if ( survey_timer != null ){

			survey_timer.cancel();
		}
	}

	protected class
	adapterFacade
		implements DHTStorageAdapter
	{
		private final DHTStorageAdapter		delegate;

		protected
		adapterFacade(
			DHTStorageAdapter	_delegate )
		{
			delegate = _delegate;
		}

		@Override
		public int
		getNetwork()
		{
			return( delegate.getNetwork());
		}

		@Override
		public DHTStorageKey
		keyCreated(
			HashWrapper		key,
			boolean			local )
		{
				// report *before* incrementing as this occurs before the key is locally added

			reportSizes( "keyAdded" );

			total_keys++;

			return( delegate.keyCreated( key, local ));
		}

		@Override
		public void
		keyDeleted(
			DHTStorageKey	adapter_key )
		{
			total_keys--;

			delegate.keyDeleted( adapter_key );

			reportSizes( "keyDeleted" );
		}

		@Override
		public int
		getKeyCount()
		{
			return( delegate.getKeyCount());
		}

		@Override
		public void
		keyRead(
			DHTStorageKey			adapter_key,
			DHTTransportContact		contact )
		{
			reportSizes( "keyRead" );

			delegate.keyRead( adapter_key, contact );
		}

		@Override
		public DHTStorageKeyStats
		deserialiseStats(
			DataInputStream			is )

			throws IOException
		{
			return( delegate.deserialiseStats( is ));
		}

		@Override
		public void
		valueAdded(
			DHTStorageKey		key,
			DHTTransportValue	value )
		{
			total_values++;
			total_size += value.getValue().length;

			reportSizes( "valueAdded");

			if ( !value.isLocal() ){

				DHTDBValueImpl	val = (DHTDBValueImpl)value;

				boolean	direct = Arrays.equals( value.getOriginator().getID(), val.getSender().getID());

				if ( direct ){

					incrementValueAdds( value.getOriginator());
				}
			}

			delegate.valueAdded( key, value );
		}

		@Override
		public void
		valueUpdated(
			DHTStorageKey		key,
			DHTTransportValue	old_value,
			DHTTransportValue	new_value )
		{
			total_size += (new_value.getValue().length - old_value.getValue().length );

			reportSizes("valueUpdated");

			delegate.valueUpdated( key, old_value, new_value );
		}

		@Override
		public void
		valueDeleted(
			DHTStorageKey		key,
			DHTTransportValue	value )
		{
			total_values--;
			total_size -= value.getValue().length;

			reportSizes("valueDeleted");

			if ( !value.isLocal() ){

				DHTDBValueImpl	val = (DHTDBValueImpl)value;

				boolean	direct = Arrays.equals( value.getOriginator().getID(), val.getSender().getID());

				if ( direct ){

					decrementValueAdds( value.getOriginator());
				}
			}

			delegate.valueDeleted( key, value );
		}

			// local lookup/put operations

		@Override
		public boolean
		isDiversified(
			byte[]		key )
		{
			return( delegate.isDiversified( key ));
		}

		@Override
		public byte[][]
		getExistingDiversification(
			byte[]			key,
			boolean			put_operation,
			boolean			exhaustive_get,
			int				max_depth )
		{
			return( delegate.getExistingDiversification( key, put_operation, exhaustive_get, max_depth ));
		}

		@Override
		public byte[][]
		createNewDiversification(
			String				description,
			DHTTransportContact	cause,
			byte[]				key,
			boolean				put_operation,
			byte				diversification_type,
			boolean				exhaustive_get,
			int					max_depth )
		{
			return( delegate.createNewDiversification( description, cause, key, put_operation, diversification_type, exhaustive_get, max_depth ));
		}

		@Override
		public int
		getNextValueVersions(
			int		num )
		{
			return( delegate.getNextValueVersions(num));
		}

		@Override
		public DHTStorageBlock
		keyBlockRequest(
			DHTTransportContact		direct_sender,
			byte[]					request,
			byte[]					signature )
		{
			return( delegate.keyBlockRequest( direct_sender, request, signature ));
		}

		@Override
		public DHTStorageBlock
		getKeyBlockDetails(
			byte[]		key )
		{
			return( delegate.getKeyBlockDetails(key));
		}

		@Override
		public DHTStorageBlock[]
		getDirectKeyBlocks()
		{
			return( delegate.getDirectKeyBlocks());
		}

		@Override
		public byte[]
    	getKeyForKeyBlock(
    		byte[]	request )
		{
			return( delegate.getKeyForKeyBlock( request ));
		}

		@Override
		public void
		setStorageForKey(
			String	key,
			byte[]	data )
		{
			delegate.setStorageForKey( key, data );
		}

		@Override
		public byte[]
		getStorageForKey(
			String	key )
		{
			return( delegate.getStorageForKey(key));
		}

		@Override
		public int
		getRemoteFreqDivCount()
		{
			return( delegate.getRemoteFreqDivCount());
		}

		@Override
		public int
		getRemoteSizeDivCount()
		{
			return( delegate.getRemoteSizeDivCount());
		}
	}

	protected static class
	SurveyContactState
	{
		private DHTTransportContact		contact;

		private final long					creation_time	= SystemTime.getMonotonousTime();
		private final long					timeout			= creation_time + SURVEY_STATE_MAX_LIFE_TIMEOUT + RandomUtils.nextInt( SURVEY_STATE_MAX_LIFE_RAND );

		private long					last_used		= creation_time;

		private final Set<DHTDBMapping>		mappings = new HashSet<>();

		private int	consec_fails;

		protected
		SurveyContactState(
			DHTTransportContact		c )
		{
			contact = c;

			log( "new" );
		}

		protected boolean
		timeout(
			long	now )
		{
			 return( now - last_used > SURVEY_STATE_INACT_TIMEOUT || now > timeout );
		}

		protected DHTTransportContact
		getContact()
		{
			return( contact );
		}

		protected long
		getCreationTime()
		{
			return( creation_time );
		}

		protected void
		updateContactDetails(
			DHTTransportContact		c )
		{
			if ( c.getInstanceID() != contact.getInstanceID()){

				log( "instance id changed" );

				mappings.clear();
			}

			contact	= c;
		}

		protected void
		updateUseTime()
		{
			last_used = SystemTime.getMonotonousTime();
		}

		protected long
		getLastUseTime()
		{
			return( last_used );
		}

		protected void
		contactOK()
		{
			log( "contact ok" );

			consec_fails	= 0;
		}

		protected void
		contactFailed()
		{
			consec_fails++;

			log( "failed, consec=" + consec_fails );

			if ( consec_fails >= 2 ){

				mappings.clear();
			}
		}

		protected int
		getConsecFails()
		{
			return( consec_fails );
		}

		protected boolean
		testMapping(
			DHTDBMapping	mapping )
		{
			return( mappings.contains( mapping ));
		}

		protected Set<DHTDBMapping>
		getMappings()
		{
			return( mappings );
		}

		protected void
		addMapping(
			DHTDBMapping	mapping )
		{
			if ( mappings.add( mapping )){

				log( "add mapping" );
			}
		}

		protected void
		removeMapping(
			DHTDBMapping	mapping )
		{
			if ( mappings.remove( mapping )){

				log( "remove mapping" );
			}
		}

		protected void
		log(
			String	str )
		{
			if ( DEBUG_SURVEY ){
				System.out.println( "s_state: " + contact.getString() + ": " + str );
			}
		}
	}
}
