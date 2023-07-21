/*
 * Created on 03-Feb-2005
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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.*;

import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.DHTStorageKey;
import com.biglybt.core.dht.impl.DHTLog;
import com.biglybt.core.dht.transport.DHTTransportContact;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.HashWrapper;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.bloom.BloomFilter;
import com.biglybt.core.util.bloom.BloomFilterFactory;

// import com.biglybt.core.util.SHA1Hasher;

/**
 * @author parg
 *
 */

public class
DHTDBMapping
{
	private static final boolean	TRACE_ADDS		= false;

	private final DHTDBImpl			db;
	private final HashWrapper			key;
	private final ShortHash			short_key;
	private DHTStorageKey		adapter_key;

		// maps are access order, most recently used at tail, so we cycle values

	Map<HashWrapper,DHTDBValueImpl>		direct_originator_map_may_be_null;
	final Map<HashWrapper,DHTDBValueImpl>		indirect_originator_value_map		= createLinkedMap();

	private int				hits;

	int				direct_data_size;
	int				indirect_data_size;
	int				local_size;

	private byte			diversification_state	= DHT.DT_NONE;

	private static final int		IP_COUNT_BLOOM_SIZE_INCREASE_CHUNK	= 50;

		// 4 bit filter - counts up to 15

	private Object 	ip_count_bloom_filter;

	protected
	DHTDBMapping(
		DHTDBImpl			_db,
		HashWrapper			_key,
		boolean				_local )
	{
		db			= _db;
		key			= _key;

		short_key = new ShortHash( key.getBytes());

		try{
			if ( db.getAdapter() != null ){

				adapter_key = db.getAdapter().keyCreated( key, _local );

				if ( adapter_key != null ){

					diversification_state	= adapter_key.getDiversificationType();
				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	protected  Map<HashWrapper,DHTDBValueImpl>
	createLinkedMap()
	{
		return(new LinkedHashMap<>(1, 0.75f, true));
	}

	protected HashWrapper
	getKey()
	{
		return( key );
	}

	protected ShortHash
	getShortKey()
	{
		return( short_key );
	}

	protected void
	updateLocalContact(
		DHTTransportContact		contact )
	{
			// pull out all the local values, reset the originator and then
			// re-add them

		if ( direct_originator_map_may_be_null == null ){

			return;
		}

		List<DHTDBValueImpl>	changed = new ArrayList<>();

		Iterator<DHTDBValueImpl>	it = direct_originator_map_may_be_null.values().iterator();

		while( it.hasNext()){

			DHTDBValueImpl	value = it.next();

			if ( value.isLocal()){

				value.setOriginatorAndSender( contact );

				changed.add( value );

				direct_data_size -= value.getValue().length;

				local_size	-= value.getValue().length;

				it.remove();

				informDeleted( value );
			}
		}

		for (int i=0;i<changed.size();i++){

			add(changed.get(i));
		}
	}

	// All values have
	//	1) a key
	//	2) a value
	//	3) an originator (the contact who originally published it)
	//	4) a sender  (the contact who sent it, could be diff for caches)

	// rethink time :P
	// a) for a value where sender + originator are the same we store a single value
	// b) where sender + originator differ we store an entry per originator/value pair as the
	//    send can legitimately forward multiple values but their originator should differ

	// c) the code that adds values is responsible for not accepting values that are either
	//    to "far away" from our ID, or that are cache-forwards from a contact "too far"
	//    away.


	// for a given key
	//		c) we only allow up to 8 entries per sending IP address (excluding port)
	//		d) if multiple entries have the same value the value is only returned once
	// 		e) only the originator can delete an entry

	// a) prevents a single sender from filling up the mapping with garbage
	// b) prevents the same key->value mapping being held multiple times when sent by different caches
	// c) prevents multiple senders from same IP filling up, but supports multiple machines behind NAT
	// d) optimises responses.

	// Note that we can't trust the originator value in cache forwards, we therefore
	// need to prevent someone from overwriting a valid originator->value1 mapping
	// with an invalid originator->value2 mapping - that is we can't use uniqueness of
	// originator

	// a value can be "volatile" - this means that the cacher can ping the originator
	// periodically and delete the value if it is dead


	// the aim here is to
	//	1) 	reduce ability for single contacts to spam the key while supporting up to 8
	//		contacts on a given IP (assuming NAT is being used)
	//	2)	stop one contact deleting or overwriting another contact's entry
	//	3)	support garbage collection for contacts that don't delete entries on exit

	// TODO: we should enforce a max-values-per-sender restriction to stop a sender from spamming
	// lots of keys - however, for a small DHT we need to be careful

	protected void
	add(
		DHTDBValueImpl		new_value )
	{
		// don't replace a closer cache value with a further away one. in particular
		// we have to avoid the case where the original publisher of a key happens to
		// be close to it and be asked by another node to cache it!

		DHTTransportContact	originator 		= new_value.getOriginator();
		DHTTransportContact	sender 			= new_value.getSender();

		HashWrapper	originator_id = new HashWrapper( originator.getID());

		boolean	direct = Arrays.equals( originator.getID(), sender.getID());

		if ( direct ){

				// direct contact from the originator is straight forward

			addDirectValue( originator_id, new_value );

				// remove any indirect values we might already have for this

			Iterator<Map.Entry<HashWrapper,DHTDBValueImpl>>	it = indirect_originator_value_map.entrySet().iterator();

			List<HashWrapper>	to_remove = new ArrayList<>();

			while( it.hasNext()){

				Map.Entry<HashWrapper,DHTDBValueImpl>	entry = it.next();

				HashWrapper		existing_key	= entry.getKey();

				DHTDBValueImpl	existing_value	= entry.getValue();

				if ( Arrays.equals( existing_value.getOriginator().getID(), originator.getID())){

					to_remove.add( existing_key );
				}
			}

			for (int i=0;i<to_remove.size();i++){

				removeIndirectValue((HashWrapper)to_remove.get(i));
			}
		}else{

				// not direct. if we have a value already for this originator then
				// we drop the value as the originator originated one takes precedence

			if ( 	direct_originator_map_may_be_null != null &&
					direct_originator_map_may_be_null.get( originator_id ) != null ){

				return;
			}

				// rule (b) - one entry per originator/value pair

			HashWrapper	originator_value_id = getOriginatorValueID( new_value );

			DHTDBValueImpl existing_value = indirect_originator_value_map.get( originator_value_id );

			if ( existing_value != null ){

				addIndirectValue( originator_value_id, new_value );

					//System.out.println( "    replacing existing" );

			}else{

					// only add new values if not diversified

				if ( diversification_state == DHT.DT_NONE ){

					addIndirectValue( originator_value_id, new_value );
				}
			}
		}
	}

	private HashWrapper
	getOriginatorValueID(
		DHTDBValueImpl	value )
	{
		DHTTransportContact	originator	= value.getOriginator();

		byte[]	originator_id	= originator.getID();

			// relaxed this due to problems caused by multiple publishes by an originator
			// with the same key but variant values (e.g. seed/peer counts). Seeing as we
			// only accept cache-forwards from contacts that are "close" enough to us to
			// be performing such a forward, the DOS possibilities here are limited (a nasty
			// contact can only trash originator values for things it happens to be close to)

		return( new HashWrapper( originator_id ));

		/*
		byte[]	value_bytes 	= value.getValue();

		byte[]	x = new byte[originator_id.length + value_bytes.length];

		System.arraycopy( originator_id, 0, x, 0, originator_id.length );
		System.arraycopy( value_bytes, 0, x, originator_id.length, value_bytes.length );

		HashWrapper	originator_value_id = new HashWrapper( new SHA1Hasher().calculateHash( x ));

		return( originator_value_id );
		*/
	}

	protected void
	addHit()
	{
		hits++;
	}

	protected int
	getHits()
	{
		return( hits );
	}

	protected int
	getIndirectSize()
	{
		return( indirect_data_size );
	}

	protected int
	getDirectSize()
	{
			// our direct count includes local so remove that here

		return( direct_data_size - local_size );
	}

	protected int
	getLocalSize()
	{
		return( local_size );
	}

	protected DHTDBValueImpl[]
	get(
		DHTTransportContact		by_who,
		int						max,
		short					flags )
	{
		if ((flags & DHT.FLAG_STATS) != 0 ){

			if ( adapter_key != null ){

				try{
					ByteArrayOutputStream	baos = new ByteArrayOutputStream(64);

					DataOutputStream	dos = new DataOutputStream( baos );

					adapter_key.serialiseStats( dos );

					dos.close();

					return(
						new DHTDBValueImpl[]{
							new DHTDBValueImpl(
								SystemTime.getCurrentTime(),
								baos.toByteArray(),
								0,
								db.getLocalContact(),
								db.getLocalContact(),
								true,
								DHT.FLAG_STATS,
								0,
								DHT.REP_FACT_DEFAULT )});

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}

			return( new DHTDBValueImpl[0] );
		}

		List<DHTDBValueImpl>	res 		= new ArrayList<>();

		Set<HashWrapper>		duplicate_check = new HashSet<>();

		Map<HashWrapper,DHTDBValueImpl>[]	maps = new Map[]{ direct_originator_map_may_be_null, indirect_originator_value_map };

			// currently we don't filter return values by seeding/downloading flag as scraping is implemented by normal
			// get operations and if we filtered out seeds for seeds then the caller would see zero seeds. fix oneday!

		for (int i=0;i<maps.length;i++){

			Map<HashWrapper,DHTDBValueImpl>			map	= maps[i];

			if ( map == null ){

				continue;
			}

			List<HashWrapper>	keys_used 	= new ArrayList<>();

			Iterator<Map.Entry<HashWrapper,DHTDBValueImpl>>	it = map.entrySet().iterator();

			while( it.hasNext() && ( max==0 || res.size()< max )){

				Map.Entry<HashWrapper,DHTDBValueImpl>	entry = it.next();

				HashWrapper		entry_key	= entry.getKey();

				DHTDBValueImpl	entry_value = entry.getValue();

				HashWrapper	x = new HashWrapper( entry_value.getValue());

				if ( duplicate_check.contains( x )){

					continue;
				}

				duplicate_check.add( x );

					// zero length values imply deleted values so don't return them

				if ( entry_value.getValue().length > 0 ){

					res.add( entry_value );

					keys_used.add( entry_key );
				}
			}

				// now update the access order so values get cycled

			for (int j=0;j<keys_used.size();j++){

				map.get( keys_used.get(j));
			}
		}

		informRead( by_who );

		DHTDBValueImpl[]	v = new DHTDBValueImpl[res.size()];

		res.toArray( v );

		return( v );
	}

	protected DHTDBValueImpl
	get(
		DHTTransportContact 	originator )
	{
			// local get

		if ( direct_originator_map_may_be_null == null ){

			return( null );
		}

		HashWrapper originator_id = new HashWrapper( originator.getID());

		DHTDBValueImpl	res = (DHTDBValueImpl)direct_originator_map_may_be_null.get( originator_id );

		return( res );
	}

	protected DHTDBValueImpl
	getAnyValue(
		DHTTransportContact 	originator )
	{
		DHTDBValueImpl	res = null;

		try{
			Map<HashWrapper,DHTDBValueImpl> map = direct_originator_map_may_be_null;

			if ( map != null ){

				HashWrapper originator_id = new HashWrapper( originator.getID());

				res = (DHTDBValueImpl)map.get( originator_id );
			}

			if ( res == null ){

				Iterator<DHTDBValueImpl> it = indirect_originator_value_map.values().iterator();

				if ( it.hasNext()){

					res = it.next();
				}
			}
		}catch( Throwable e ){
			// slight chance of conc exception here, don't care
		}

		return( res );
	}

	protected List<DHTDBValueImpl>
	getAllValues(
		DHTTransportContact 	originator )
	{
		List<DHTDBValueImpl>	res 		= new ArrayList<>();

		Set<HashWrapper>		duplicate_check = new HashSet<>();

		Map<HashWrapper,DHTDBValueImpl>[]	maps = new Map[]{ direct_originator_map_may_be_null, indirect_originator_value_map };

		for (int i=0;i<maps.length;i++){

			Map<HashWrapper,DHTDBValueImpl>			map	= maps[i];

			if ( map == null ){

				continue;
			}

			Iterator<Map.Entry<HashWrapper,DHTDBValueImpl>>	it = map.entrySet().iterator();

			while( it.hasNext()){

				Map.Entry<HashWrapper,DHTDBValueImpl>	entry = it.next();

				DHTDBValueImpl	entry_value = entry.getValue();

				HashWrapper	x = new HashWrapper( entry_value.getValue());

				if ( duplicate_check.contains( x )){

					continue;
				}

				duplicate_check.add( x );

					// zero length values imply deleted values so don't return them

				if ( entry_value.getValue().length > 0 ){

					res.add( entry_value );
				}
			}
		}

		return( res );
	}

	protected DHTDBValueImpl
	remove(
		DHTTransportContact 	originator )
	{
			// local remove

		HashWrapper originator_id = new HashWrapper( originator.getID());

		DHTDBValueImpl	res = removeDirectValue( originator_id );

		return( res );
	}


	protected int
	getValueCount()
	{
		if ( direct_originator_map_may_be_null == null ){

			return( indirect_originator_value_map.size());
		}

		return( direct_originator_map_may_be_null.size() + indirect_originator_value_map.size());
	}

	protected int
	getDirectValueCount()
	{
		if ( direct_originator_map_may_be_null == null ){

			return( 0 );
		}

		return( direct_originator_map_may_be_null.size());
	}

	protected int
	getIndirectValueCount()
	{
		return( indirect_originator_value_map.size());
	}

	protected Iterator<DHTDBValueImpl>
	getValues()
	{
		return( new valueIterator( true, true ));
	}

	protected Iterator<DHTDBValueImpl>
	getDirectValues()
	{
		return( new valueIterator( true, false ));
	}

	protected Iterator<DHTDBValueImpl>
	getIndirectValues()
	{
		return( new valueIterator( false, true ));
	}

	protected byte
	getDiversificationType()
	{
		return( diversification_state );
	}

	protected void
	addDirectValue(
		HashWrapper		value_key,
		DHTDBValueImpl	value )
	{
		if ( direct_originator_map_may_be_null == null ){

			direct_originator_map_may_be_null = createLinkedMap();
		}

		DHTDBValueImpl	old = (DHTDBValueImpl)direct_originator_map_may_be_null.put( value_key, value );

		if ( old != null ){

			int	old_version = old.getVersion();
			int new_version = value.getVersion();

			if ( old_version != -1 && new_version != -1 && old_version >= new_version ){

				if ( old_version == new_version ){

					if ( TRACE_ADDS ){
						System.out.println( "addDirect[reset]:" + old.getString() + "/" + value.getString());
					}

					old.reset();	// update store time as this means we don't need to republish
									// as someone else has just done it

				}else{

						// its important to ignore old versions as a peer's increasing version sequence may
						// have been reset and if this is the case we want the "future" values to timeout

					if ( TRACE_ADDS ){
						System.out.println( "addDirect[ignore]:" + old.getString() + "/" + value.getString());
					}
				}

					// put the old value back!

				direct_originator_map_may_be_null.put( value_key, old );

				return;
			}

			if ( TRACE_ADDS ){
				System.out.println( "addDirect:" + old.getString() + "/" + value.getString());
			}

			direct_data_size -= old.getValue().length;

			if ( old.isLocal()){

				local_size -= old.getValue().length;
			}
		}else{

			if ( TRACE_ADDS ){
				System.out.println( "addDirect:[new]" +  value.getString());
			}
		}

		direct_data_size += value.getValue().length;

		if ( value.isLocal()){

			local_size += value.getValue().length;
		}

		if ( old == null ){

			informAdded( value );

		}else{

			informUpdated( old, value );
		}
	}

	protected DHTDBValueImpl
	removeDirectValue(
		HashWrapper		value_key )
	{
		if ( direct_originator_map_may_be_null == null ){

			return( null );
		}

		DHTDBValueImpl	old = (DHTDBValueImpl)direct_originator_map_may_be_null.remove( value_key );

		if ( old != null ){

			direct_data_size -= old.getValue().length;

			if ( old.isLocal()){

				local_size -= old.getValue().length;
			}

			informDeleted( old );
		}

		return( old );
	}

	protected void
	addIndirectValue(
		HashWrapper		value_key,
		DHTDBValueImpl	value )
	{
		DHTDBValueImpl	old = (DHTDBValueImpl)indirect_originator_value_map.put( value_key, value );

		if ( old != null ){

				// discard updates that are older than current value

			int	old_version = old.getVersion();
			int new_version = value.getVersion();

			if ( old_version != -1 && new_version != -1 && old_version >= new_version ){

				if ( old_version == new_version ){

					if ( TRACE_ADDS ){
						System.out.println( "addIndirect[reset]:" + old.getString() + "/" + value.getString());
					}

					old.reset();	// update store time as this means we don't need to republish
									// as someone else has just done it

				}else{

					if ( TRACE_ADDS ){
						System.out.println( "addIndirect[ignore]:" + old.getString() + "/" + value.getString());
					}
				}

					// put the old value back!

				indirect_originator_value_map.put( value_key, old );

				return;
			}

			// vague backwards compatibility - if the creation date of the "new" value is significantly
			// less than the old then we ignore it (given that creation date is adjusted for time-skew you can
			// see the problem with this approach...)

			if ( old_version == -1 || new_version == -1 ){

				if ( old.getCreationTime() > value.getCreationTime() + 30000 ){

					if ( TRACE_ADDS ){
						System.out.println( "backward compat: ignoring store: " + old.getString() + "/" + value.getString());
					}

					// put the old value back!

					indirect_originator_value_map.put( value_key, old );

					return;
				}
			}

			if ( TRACE_ADDS ){
				System.out.println( "addIndirect:" + old.getString() + "/" + value.getString());
			}

			indirect_data_size -= old.getValue().length;

			if ( old.isLocal()){

				local_size -= old.getValue().length;
			}
		}else{
			if ( TRACE_ADDS ){
				System.out.println( "addIndirect:[new]" +  value.getString());
			}
		}

		indirect_data_size += value.getValue().length;

		if ( value.isLocal()){

			local_size += value.getValue().length;
		}

		if ( old == null ){

			informAdded( value );

		}else{

			informUpdated( old, value );
		}
	}

	protected DHTDBValueImpl
	removeIndirectValue(
		HashWrapper		value_key )
	{
		DHTDBValueImpl	old = (DHTDBValueImpl)indirect_originator_value_map.remove( value_key );

		if ( old != null ){

			indirect_data_size -= old.getValue().length;

			if ( old.isLocal()){

				local_size -= old.getValue().length;
			}

			informDeleted( old );
		}

		return( old );
	}

	protected void
	destroy()
	{
		try{
			if ( adapter_key != null ){

				Iterator<DHTDBValueImpl>	it = getValues();

				while( it.hasNext()){

					it.next();

					it.remove();
				}

				db.getAdapter().keyDeleted( adapter_key );
			}

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	void
	informDeleted(
		DHTDBValueImpl		value )
	{
		boolean	direct =
			(!value.isLocal())&&
			Arrays.equals( value.getOriginator().getID(), value.getSender().getID());

		if ( direct ){

			removeFromBloom( value );
		}

		try{
			if ( adapter_key != null ){

				db.getAdapter().valueDeleted( adapter_key, value );

				diversification_state	= adapter_key.getDiversificationType();
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	private void
	informAdded(
		DHTDBValueImpl		value )
	{
		boolean	direct =
			(!value.isLocal()) &&
			Arrays.equals( value.getOriginator().getID(), value.getSender().getID());

		if ( direct ){

			addToBloom( value );
		}

		try{
			if ( adapter_key != null ){

				db.getAdapter().valueAdded( adapter_key, value );

				diversification_state	= adapter_key.getDiversificationType();
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	private void
	informUpdated(
		DHTDBValueImpl		old_value,
		DHTDBValueImpl		new_value)
	{
		boolean	old_direct =
			(!old_value.isLocal()) &&
			Arrays.equals( old_value.getOriginator().getID(), old_value.getSender().getID());

		boolean	new_direct =
			(!new_value.isLocal()) &&
			Arrays.equals( new_value.getOriginator().getID(), new_value.getSender().getID());

		if ( new_direct && !old_direct ){

			addToBloom( new_value );
		}

		try{
			if ( adapter_key != null ){

				db.getAdapter().valueUpdated( adapter_key, old_value, new_value );

				diversification_state	= adapter_key.getDiversificationType();
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	private void
	informRead(
		DHTTransportContact		contact ){

		try{
			if ( adapter_key != null && contact != null ){

				db.getAdapter().keyRead( adapter_key, contact );

				diversification_state	= adapter_key.getDiversificationType();
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	protected void
	addToBloom(
		DHTDBValueImpl	value )
	{
		// we don't check for flooding on indirect stores as this could be used to force a
		// direct store to be bounced (flood a node with indirect stores before the direct
		// store occurs)


		DHTTransportContact	originator = value.getOriginator();

		byte[] bloom_key = originator.getBloomKey();

		// System.out.println( "addToBloom: existing=" + ip_count_bloom_filter );

		if ( ip_count_bloom_filter == null ){

			ip_count_bloom_filter = bloom_key;

			return;
		}

		BloomFilter filter;

		if ( ip_count_bloom_filter instanceof byte[] ){

			byte[]	existing_address = (byte[])ip_count_bloom_filter;

			ip_count_bloom_filter = filter = BloomFilterFactory.createAddRemove4Bit( IP_COUNT_BLOOM_SIZE_INCREASE_CHUNK );

			filter.add( existing_address );

		}else{

			filter = (BloomFilter)ip_count_bloom_filter;
		}

		int	hit_count = filter.add( bloom_key );

		if ( DHTLog.LOCAL_BLOOM_TRACE ){

			System.out.println( "direct local add from " + originator.getAddress() + ", hit count = " + hit_count );
		}

			// allow up to 10% bloom filter utilisation

		if ( filter.getSize() / filter.getEntryCount() < 10 ){

			rebuildIPBloomFilter( true );
		}

		if ( hit_count >= 15 ){

			db.banContact( originator, "local flood on '" + DHTLog.getFullString( key.getBytes()) + "'" );
		}
	}

	protected void
	removeFromBloom(
		DHTDBValueImpl	value )
	{
		DHTTransportContact	originator = value.getOriginator();

		if ( ip_count_bloom_filter == null ){

			return;
		}

		byte[] bloom_key = originator.getBloomKey();

		if ( ip_count_bloom_filter instanceof byte[] ){

			byte[]	existing_address = (byte[])ip_count_bloom_filter;

			if ( Arrays.equals( bloom_key, existing_address )){

				ip_count_bloom_filter = null;
			}

			return;
		}

		BloomFilter filter = (BloomFilter)ip_count_bloom_filter;

		int	hit_count = filter.remove( bloom_key );

		if (  DHTLog.LOCAL_BLOOM_TRACE ){

			System.out.println( "direct local remove from " + originator.getAddress() + ", hit count = " + hit_count );
		}
	}

	protected void
	rebuildIPBloomFilter(
		boolean	increase_size )
	{
		BloomFilter	new_filter;

		int	old_size;

		if ( ip_count_bloom_filter instanceof BloomFilter ){

			old_size = ((BloomFilter)ip_count_bloom_filter).getSize();

		}else{

			old_size = IP_COUNT_BLOOM_SIZE_INCREASE_CHUNK;
		}

		if ( increase_size ){

			new_filter = BloomFilterFactory.createAddRemove4Bit( old_size + IP_COUNT_BLOOM_SIZE_INCREASE_CHUNK );

		}else{

			new_filter = BloomFilterFactory.createAddRemove4Bit( old_size );
		}

		try{
				// only do flood prevention on direct stores as we can't trust the originator
				// details for indirect and this can be used to DOS a direct store later

			Iterator<DHTDBValueImpl>	it = getDirectValues();

			int	max_hits	= 0;

			while( it.hasNext()){

				DHTDBValueImpl	val = it.next();

				if ( !val.isLocal()){

					// logger.log( "    adding " + val.getOriginator().getAddress());

					int	hits = new_filter.add( val.getOriginator().getBloomKey());

					if ( hits > max_hits ){

						max_hits	= hits;
					}
				}
			}

			if (  DHTLog.LOCAL_BLOOM_TRACE ){

				db.log( "Rebuilt local IP bloom filter, size = " + new_filter.getSize() + ", entries =" + new_filter.getEntryCount()+", max hits = " + max_hits );
			}

		}finally{

			ip_count_bloom_filter = new_filter;
		}
	}

	protected void
	print()
	{
		int	entries;

		if ( ip_count_bloom_filter == null ){

			entries = 0;

		}else if ( ip_count_bloom_filter instanceof byte[] ){

			entries = 1;

		}else{

			entries = ((BloomFilter)ip_count_bloom_filter).getEntryCount();
		}

		System.out.println(
			ByteFormatter.encodeString( key.getBytes()) + ": " +
			"dir=" + (direct_originator_map_may_be_null==null?0:direct_originator_map_may_be_null.size()) + "," +
			"indir=" + indirect_originator_value_map.size() + "," +
			"bloom=" + entries );

		System.out.println( "    indirect" );

		Iterator<DHTDBValueImpl> it = getIndirectValues();

		while( it.hasNext()){

			DHTDBValueImpl val = (DHTDBValueImpl)it.next();

			System.out.println( "        " + val.getOriginator().getString() + ": " + new String( val.getValue()));
		}
	}

	protected class
	valueIterator
		implements Iterator<DHTDBValueImpl>
	{
		private final List<Map<HashWrapper,DHTDBValueImpl>>	maps 		= new ArrayList<>(2);

		private int		map_index 	= 0;

		private Map<HashWrapper,DHTDBValueImpl>		map;
		private Iterator<DHTDBValueImpl>			it;
		private DHTDBValueImpl						value;

		protected
		valueIterator(
			boolean		direct,
			boolean		indirect )
		{
			if ( direct && direct_originator_map_may_be_null != null ){
				maps.add( direct_originator_map_may_be_null );
			}

			if ( indirect ){
				maps.add( indirect_originator_value_map );
			}
		}

		@Override
		public boolean
		hasNext()
		{
			if ( it != null && it.hasNext()){

				return( true );
			}

			while( map_index < maps.size() ){

				map = maps.get(map_index++);

				it = map.values().iterator();

				if ( it.hasNext()){

					return( true );
				}
			}

			return( false );
		}

		@Override
		public DHTDBValueImpl
		next()
		{
			if ( hasNext()){

				value = (DHTDBValueImpl)it.next();

				return( value );
			}

			throw( new NoSuchElementException());
		}

		@Override
		public void
		remove()
		{
			if ( it == null ){

				throw( new IllegalStateException());
			}

			if ( value != null ){

				if( value.isLocal()){

					local_size -= value.getValue().length;
				}

				if (  map == indirect_originator_value_map ){

					indirect_data_size -= value.getValue().length;

				}else{

					direct_data_size -= value.getValue().length;
				}

					// remove before informing

				it.remove();

				informDeleted( value );

				value = null;

			}else{

				throw( new IllegalStateException());
			}
		}
	}

	public static class
	ShortHash
	{
		private final byte[]	bytes;
		private final int		hash_code;

		protected
		ShortHash(
			byte[]		_bytes )
		{
			bytes	= _bytes;

			int	hc = 0;

			for (int i=0; i<DHTDBImpl.QUERY_STORE_REQUEST_ENTRY_SIZE; i++) {

				hc = 31*hc + bytes[i];
			}

			hash_code = hc;
		}

		public final boolean
		equals(
			Object o)
		{
			if( !( o instanceof ShortHash )){

				return false;
			}

			ShortHash other = (ShortHash)o;

			byte[]	other_hash 		= other.bytes;

			for ( int i=0;i<DHTDBImpl.QUERY_STORE_REQUEST_ENTRY_SIZE;i++){

				if ( bytes[i] != other_hash[i] ){

					return( false );
				}
			}

			return( true );
		}

		public int
		hashCode()
		{
			return( hash_code );
		}
	}
}
