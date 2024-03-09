/*
 * Created on Aug 7, 2008
 * Created by Paul Gardner
 *
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


package com.biglybt.core.subs.impl;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.metasearch.Result;
import com.biglybt.core.subs.SubscriptionResult;
import com.biglybt.core.subs.util.SearchSubsResultBase;
import com.biglybt.core.util.*;
import com.biglybt.pif.utils.search.SearchResult;
import com.biglybt.util.JSONUtils;

public class
SubscriptionResultImpl
	implements SubscriptionResult
{
	private static final int TIME_FOUND_DEFAULT_SECS;

	static{
		int tfd = COConfigurationManager.getIntParameter( "subscription.result.time.found.default", 0 );

		if ( tfd == 0 ){

			tfd = (int)( SystemTime.getCurrentTime()/1000 );

			COConfigurationManager.setParameter( "subscription.result.time.found.default", tfd );
		}

		TIME_FOUND_DEFAULT_SECS = tfd;
	}

	final private SubscriptionHistoryImpl	history;

	private byte[]		key1;
	private byte[]		key2;
	private boolean		read;
	private boolean		deleted;
	private int			deleted_last_seen_day;
	
	private int			time_found_secs;
	
	private String		result_json;

	private WeakReference<Map<Integer,Object>>	props_ref = null;

	protected
	SubscriptionResultImpl(
		SubscriptionHistoryImpl		_history,
		Result						result )
	{
		history = _history;

		Map	map = result.toJSONMap();

		result_json 	= JSONUtils.encodeToJSON( map );
		read			= false;

		String	key1_str =  result.getEngine().getId() + ":" + result.getName();

		try{
			byte[] sha1 = new SHA1Simple().calculateHash( key1_str.getBytes( "UTF-8" ));

			key1 = new byte[10];

			System.arraycopy( sha1, 0, key1, 0, 10 );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}

		String	uid = result.getUID();

		if ( uid != null && uid.length() > 0 ){

			String	key2_str = result.getEngine().getId() + ":" + uid;

			try{
				byte[] sha1 = new SHA1Simple().calculateHash( key2_str.getBytes( "UTF-8" ));

				key2 = new byte[10];

				System.arraycopy( sha1, 0, key2, 0, 10 );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
		
		try{
			String tf = (String)map.get( "tf" );
			
			if ( tf != null ){
				
				time_found_secs = Integer.parseInt( tf );	// already in secs
				
			}else{
				
				time_found_secs = TIME_FOUND_DEFAULT_SECS;
			}
		}catch( Throwable e ){
			
			time_found_secs = TIME_FOUND_DEFAULT_SECS;
		}
	}

	protected
	SubscriptionResultImpl(
		SubscriptionHistoryImpl		_history,
		SearchSubsResultBase		_base )
	{
		history = _history;
		
		byte[] 	hash 	= _base.getHash();
		String	name	= _base.getName();
		long	size	= _base.getSize();
		
		String	key1_str;
		
		if ( hash != null ){
			
			key1_str = Base32.encode(hash);
			
		}else{
			
			key1_str = name + ":" + size;
		}

		try{
			byte[] sha1 = new SHA1Simple().calculateHash( key1_str.getBytes( "UTF-8" ));

			key1 = new byte[10];

			System.arraycopy( sha1, 0, key1, 0, 10 );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
		
		Map map = new HashMap<>();
		
		map.put( "lb", String.valueOf( size ));
		map.put( "n", name );
		
		if ( hash != null ){
			
			map.put( "h", Base32.encode(hash));
		}
		
		time_found_secs = (int)( SystemTime.getCurrentTime()/1000 );
		
		map.put( "tf", String.valueOf( time_found_secs ));
		
		result_json = JSONUtils.encodeToJSON( map );
		
		read = true;
	}
	
	protected
	SubscriptionResultImpl(
		SubscriptionHistoryImpl		_history,
		Map							map )
	{
		history = _history;

		key1		= (byte[])map.get( "key" );
		key2		= (byte[])map.get( "key2" );

		read		= ((Long)map.get( "read")).intValue()==1;

		Long	l_deleted = (Long)map.get( "deleted" );

		if ( l_deleted != null ){

			deleted	= true;

			Number l_dls = (Number)map.get( "dls" );
			
			if ( l_dls != null ){
				
				deleted_last_seen_day = l_dls.intValue();
			}else{
				
				deleted_last_seen_day = (int)( SystemTime.getCurrentTime() / (1000*60*60*24 ));
			}
		}else{

			try{
				byte[] bytes = (byte[])map.get( "result_json" );
				
				if ( bytes == null ){
					
					bytes = new byte[0];
				}
				
				result_json	= new String( bytes, "UTF-8" );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
			
			try{
				Number tfs = (Number)map.get( "tz" );
			
				if ( tfs != null ){
					
					time_found_secs = tfs.intValue();
				}
			}catch( Throwable e ){
			}
		}
	}

	protected boolean
	updateFrom(
		SubscriptionResultImpl	other,
		boolean					allow_reincarnation )
	{
		if ( deleted ){

			if ( allow_reincarnation ){
				
				deleted 				= false;
				deleted_last_seen_day	= 0;
				
				key2		= other.getKey2();
				result_json = other.getJSON();

				time_found_secs = other.time_found_secs;
				
				synchronized( this ){

					props_ref = null;
				}

				return( true );
				
			}else{
			
				deleted_last_seen_day = (int)( SystemTime.getCurrentTime() / (1000*60*60*24 ));
			
				return( false );
			}
		}

		String	my_json_str 	= getJSON();
		String	other_json_str 	= other.getJSON();

		if ( my_json_str.equals( other_json_str )){

			return( false );
		}

			// maintain time-found across an update

		Map	my_json_map 	= JSONUtils.decodeJSON( my_json_str );

		String my_tf 	= (String)my_json_map.get( "tf" );

		Map	other_json_map = null;
		
		boolean	other_json_map_updated	= false;
		
		if ( my_tf != null ){

			other_json_map 	= JSONUtils.decodeJSON( other_json_str );

			other_json_map.put( "tf", my_tf );
			
			other_json_map_updated = true;
		}
			
		List<String> my_tags 	= (List<String>)my_json_map.get( "tgs" );

		if ( my_tags != null && !my_tags.isEmpty()){

			boolean other_has_tgs = other_json_str.contains("\"tgs\"" );
		
			if ( other_has_tgs ){
							
				boolean updated = false;

				if ( other_json_map == null ){
					
					other_json_map 	= JSONUtils.decodeJSON( other_json_str );
				}
				
				List<String> other_tags = (List<String>)other_json_map.get( "tgs" );

				for ( String t: my_tags ){
					
					if ( !other_tags.contains( t )){
						
						other_tags.add( t );
						
						updated = true;
					}
				}
				
				if ( updated ){
					
					other_json_map.put( "tgs", other_tags );
					
					other_json_map_updated = true;
				}
			}else{
									
				if ( other_json_map == null ){
					
					other_json_map 	= JSONUtils.decodeJSON( other_json_str );
				}
				
				other_json_map.put( "tgs", my_tags );
				
				other_json_map_updated = true;
			}
		}
		
			// keep oldest pub date
		
		String my_ts = (String)my_json_map.get( "ts" );

		if ( my_ts != null ){
			
			if ( other_json_map == null ){
			
				other_json_map 	= JSONUtils.decodeJSON( other_json_str );
			}
			
			String other_ts = (String)other_json_map.get( "ts" );
			
			boolean keep = false;
			
			if ( other_ts == null ){
			
				keep = true;
				
			}else{
				try{
					
					long l_my_ts 	= Long.parseLong( my_ts );
					long l_other_ts = Long.parseLong( other_ts );
					
					keep = l_my_ts != 0 && ( l_other_ts == 0 || l_my_ts < l_other_ts );
					
				}catch( Throwable e ){
					
				}
			}
			
			if ( keep ){
				
				other_json_map.put( "ts", my_ts );
				
				other_json_map_updated = true;
			}
		}
		
		if ( other_json_map_updated ){
			
			other_json_str = JSONUtils.encodeToJSON( other_json_map );
		}
		
		if ( my_json_str.equals( other_json_str )){

			return( false );

		}else{

			key2		= other.getKey2();
			result_json = other_json_str;

			synchronized( this ){

				props_ref = null;
			}

			return( true );
		}
	}

	@Override
	public String
	getID()
	{
		return( Base32.encode( key1 ));
	}

	protected byte[]
	getKey1()
	{
		return( key1 );
	}

	protected byte[]
	getKey2()
	{
   		return( key2 );
   	}

	@Override
	public boolean
	getRead()
	{
		return( read );
	}

	@Override
	public void
	setRead(
		boolean	_read )
	{
		if ( read != _read ){

			read	= _read;

			history.updateResult( this );
		}
	}

	protected void
	setReadInternal(
		boolean	_read )
	{
		read	= _read;
	}

	@Override
	public void
	delete()
	{
		if ( !deleted ){

			deleted	= true;

			deleted_last_seen_day = (int)( SystemTime.getCurrentTime() / (1000*60*60*24 ));

			history.updateResult( this );
		}
	}

	protected void
	deleteInternal()
	{
		deleted = true;
	}

	@Override
	public boolean
	isDeleted()
	{
		return( deleted );
	}

	protected int
	getDeletedLastSeen()
	{
		return( deleted_last_seen_day );
	}
	
	protected Map
	toBEncodedMap()
	{
		Map<String,Object>		map	= new HashMap<>();

		map.put( "key", key1 );

		if ( key2 != null ){
			map.put( "key2", key2 );
		}

		map.put( "read", new Long(read?1:0));

		if ( deleted ){

			map.put( "deleted", new Long(1));

			if ( deleted_last_seen_day == 0 ){
				
				deleted_last_seen_day = (int)( SystemTime.getCurrentTime() / (1000*60*60*24 ));
			}
			
			map.put( "dls", deleted_last_seen_day );
			
		}else{

			try{
				byte[] bytes;
				
				if ( result_json == null ){
					
					bytes = new byte[0];
					
				}else{
					
					bytes = result_json.getBytes( "UTF-8" );
				}
				
				map.put( "result_json", bytes );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
			
				// one off to back-populate old results with time-found
			
			if ( time_found_secs == 0 ){
				
				getTimeFound();
			}
			
			if ( time_found_secs > 0 ){
				
				map.put( "tz", new Long( time_found_secs ));
			}
		}

		return( map );
	}

	@Override
	public Map
	toJSONMap()
	{
		Map	map = JSONUtils.decodeJSON( result_json );

		map.put( "subs_is_read", Boolean.valueOf(read));
		map.put( "subs_id", getID());

		Result.adjustRelativeTerms( map );

			// migration - trim digits

		String size = (String)map.get( "l" );

		if ( size != null ){

			size = DisplayFormatters.trimDigits( size, 3 );

			map.put( "l", size );
		}

		return( map );
	}

	private String
	getJSON()
	{
		return( result_json );
	}

	@Override
	public String
	getDownloadLink()
	{
		Map map = toJSONMap();

			// meh, for magnet URIs we might well have a decent magnet stored against the "dl" and a
			// link constructed from the hash only as "dlb" - check WebResult.java. So ignore the
			// default one when a better one is available

		String	link = (String)map.get( "dbl" );

		if ( link != null ){

			if ( link.toLowerCase( Locale.US ).startsWith( "magnet:" )){

				String dl_link = (String)map.get( "dl" );

				if ( dl_link != null ){ // actually always use an explicit dl link in preference to a magnet && dl_link.toLowerCase( Locale.US ).startsWith( "magnet:" )){

					link = dl_link;
				}
			}
		}

		if ( link == null ){

			link = (String)map.get( "dl" );
		}

		return( Result.adjustLink( link ));
	}

	@Override
	public String
	getPlayLink()
	{
		return (Result.adjustLink((String)toJSONMap().get( "pl" )));
	}

	@Override
	public String
	getAssetHash()
	{
		return((String)toJSONMap().get( "h" ));
	}

	@Override
	public long
	getTimeFound()
	{
		if ( time_found_secs == 0 ){

			String tfs = (String)toJSONMap().get( "tf" );

			if ( tfs == null ){

				time_found_secs = TIME_FOUND_DEFAULT_SECS;
				
			}else{
				
				time_found_secs = Integer.parseInt( tfs );
			}
		}
		
		return( time_found_secs*1000L );
	}

	@Override
	public Map<Integer,Object>
	toPropertyMap()
	{
		synchronized( this ){

			if ( props_ref != null ){

				Map<Integer,Object> cached = props_ref.get();

				if ( cached != null ){

					return( cached );
				}
			}

			Map map = toJSONMap();

			Map<Integer,Object>	result = new HashMap<>();

			String title = (String)map.get( "n" );

			result.put( SearchResult.PR_UID, getID());
			result.put( SearchResult.PR_NAME, title );

			String pub_date = (String)map.get( "ts" );
			if ( pub_date != null ){
				result.put( SearchResult.PR_PUB_DATE, new Date( Long.parseLong( pub_date )));
			}

			String size = (String)map.get( "lb" );
			if ( size != null ){
				result.put( SearchResult.PR_SIZE, Long.parseLong( size ));
			}

			String	dbl_link 	= (String)map.get( "dbl" );
			String	dl_link 	= (String)map.get( "dl" );

			if ( dbl_link == null ){

				dbl_link = dl_link;
			}

			if ( dbl_link != null ){
				result.put( SearchResult.PR_DOWNLOAD_LINK, Result.adjustLink( dbl_link ));
			}
			if ( dl_link != null ){
				result.put( SearchResult.PR_TORRENT_LINK, Result.adjustLink( dl_link ));
			}

			String	cdp_link = (String)map.get( "cdp" );

			if ( cdp_link != null ){
				result.put( SearchResult.PR_DETAILS_LINK, Result.adjustLink(cdp_link ));
			}

			String	hash = (String)map.get( "h" );

			if ( hash != null ){
				result.put( SearchResult.PR_HASH, Base32.decode( hash ));
			}

			String	seeds = (String)map.get( "s" );

			result.put( SearchResult.PR_SEED_COUNT, seeds==null?-1:Long.parseLong(seeds) );

			String	peers = (String)map.get( "p" );

			result.put( SearchResult.PR_LEECHER_COUNT, peers==null?-1:Long.parseLong(peers) );

			String	completed = (String)map.get( "gr" );

			result.put( SearchResult.PR_COMPLETED_COUNT, completed==null?-1:Long.parseLong(completed) );

			String	votes = (String)map.get( "v" );

			result.put( SearchResult.PR_VOTES, votes==null?-1:Long.parseLong(votes) );

			String	comments = (String)map.get( "co" );

			result.put( SearchResult.PR_COMMENTS, comments==null?-1:Long.parseLong(comments) );

			String	rank = (String)map.get( "r" );

			result.put( SearchResult.PR_RANK, rank==null?-1:(long)(100*Float.parseFloat( rank )));

			String	category = (String)map.get( "c" );

			if ( category != null ){

				result.put( SearchResult.PR_CATEGORY, category );
			}
			
			List<String> tags = (List<String>)map.get( "tgs" );
			
			if ( tags != null && !tags.isEmpty()){
				
				result.put( SearchResult.PR_TAGS, tags.toArray( new String[tags.size()]));
			}
			
			String contentType = (String)map.get( "ct" );

			if ( contentType != null ){
				result.put( SearchResult.PR_CONTENT_TYPE, contentType );
			}

			String ad = (String)map.get( "ad" );
			
			if ( ad != null ){
				result.put( SearchResult.PR_ASSET_DATE, new Date( Long.parseLong( ad )));
			}
			
			props_ref = new WeakReference<>(result);

			return( result );
		}
	}
}
