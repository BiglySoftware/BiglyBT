/*
 * Created on Dec 2, 2016
 * Created by Paul Gardner
 *
 * Copyright 2016 Azureus Software, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.core.subs.util;

import java.util.*;

import com.biglybt.core.util.LightHashMap;
import com.biglybt.pif.utils.search.SearchResult;
import com.biglybt.core.metasearch.FilterableResult;
import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionResult;

public class
SubscriptionResultFilterable
	implements SearchSubsResultBase, FilterableResult
{
	private final Subscription		subs;
	private final String			result_id;

	private final String			name;
	private final byte[]			hash;
	private final int				content_type;
	private final long				size;
	private final String			torrent_link;
	private final String			details_link;
	private final String			category;

	private long				time;
	private boolean				read;
	private long				seeds_peers_sort;
	private String				seeds_peers;
	private int					seed_count;
	private int					peer_count;
	private int					completed_count;
	private long				votes_comments_sort;
	private String				votes_comments;
	private int					rank;
	private long				asset_date;
	private String[]			tags;

	private LightHashMap<Object,Object>	user_data;

	public
	SubscriptionResultFilterable(
		Subscription		_subs,
		SubscriptionResult	_result )
	{
		subs		= _subs;
		result_id	= _result.getID();
		
		Map<Integer,Object>	properties = _result.toPropertyMap();

		name = (String)properties.get( SearchResult.PR_NAME );

		hash = (byte[])properties.get( SearchResult.PR_HASH );

		String type = (String)properties.get( SearchResult.PR_CONTENT_TYPE );

		if ( type == null || type.length() == 0 ){
			content_type = 0;
		}else{
			char c = type.charAt(0);

			if ( c == 'v' ){
				content_type = 1;
			}else if ( c == 'a' ){
				content_type = 2;
			}else if ( c == 'g' ){
				content_type = 3;
			}else{
				content_type = 0;
			}
		}

		size = (Long)properties.get( SearchResult.PR_SIZE );

		String tl = (String)properties.get( SearchResult.PR_TORRENT_LINK );

		if ( tl == null ){

			tl = (String)properties.get( SearchResult.PR_DOWNLOAD_LINK );
		}

		torrent_link = tl;

		details_link = (String)properties.get( SearchResult.PR_DETAILS_LINK );

		category = (String)properties.get( SearchResult.PR_CATEGORY );
		
		Date	ad = (Date)properties.get( SearchResult.PR_ASSET_DATE );
		
		if ( ad != null ){
			
			asset_date = ad.getTime();
		}
		
		updateMutables( _result, properties );
	}

	private void
	updateMutables(
		SubscriptionResult		_result,
		Map<Integer,Object>		properties	)
	{
		read = _result.getRead();

		Date pub_date = (Date)properties.get( SearchResult.PR_PUB_DATE );

		if ( pub_date == null ){

			time = _result.getTimeFound();

		}else{

			long pt = pub_date.getTime();

			if ( pt <= 0 ){

				time = _result.getTimeFound();

			}else{

				time = pt;
			}
		}

		tags = (String[])properties.get( SearchResult.PR_TAGS );

		long seeds 		= (Long)properties.get( SearchResult.PR_SEED_COUNT );
		long leechers 	= (Long)properties.get( SearchResult.PR_LEECHER_COUNT );

		completed_count 	= ((Long)properties.get( SearchResult.PR_COMPLETED_COUNT )).intValue();

		seed_count = (int)(seeds<0?0:seeds);
		peer_count = (int)(leechers<0?0:leechers);
		
		seeds_peers = (seeds<0?"--":String.valueOf(seeds)) + "/" + (leechers<0?"--":String.valueOf(leechers));

		if ( seeds < 0 ){
			seeds = 0;
		}else{
			seeds++;
		}

		if ( leechers < 0 ){
			leechers = 0;
		}else{
			leechers++;
		}

		seeds_peers_sort = ((seeds&0x7fffffff)<<32) | ( leechers & 0xffffffff );

		long votes		= (Long)properties.get( SearchResult.PR_VOTES );
		long comments 	= (Long)properties.get( SearchResult.PR_COMMENTS );

		if ( votes < 0 && comments < 0 ){

			votes_comments_sort = 0;
			votes_comments		= null;

		}else{

			votes_comments = (votes<0?"--":String.valueOf(votes)) + "/" + (comments<0?"--":String.valueOf(comments));

			if ( votes < 0 ){
				votes= 0;
			}else{
				votes++;
			}
			if ( comments < 0 ){
				comments= 0;
			}else{
				comments++;
			}

			votes_comments_sort = ((votes&0x7fffffff)<<32) | ( comments & 0xffffffff );
		}

		rank	 	= ((Long)properties.get( SearchResult.PR_RANK )).intValue();
	}

	public void
	updateFrom(
		SubscriptionResult		other )
	{
		updateMutables( other, other.toPropertyMap());
	}

	public Subscription
	getSubscription()
	{
		return( subs );
	}

	public String
	getID()
	{
		return( result_id );
	}

	@Override
	public final String
	getName()
	{
		return( name );
	}

	@Override
	public byte[]
	getHash()
	{
		return( hash );
	}

	@Override
	public int
	getContentType()
	{
		return( content_type );
	}

	@Override
	public long
	getSize()
	{
		return( size );
	}
	
	@Override
	public int 
	getNbSeeds()
	{
		return( seed_count );
	}

	@Override
	public int 
	getNbPeers()
	{
		return( peer_count );
	}

	@Override
	public String
	getSeedsPeers()
	{
		return( seeds_peers );
	}

	@Override
	public long
	getSeedsPeersSortValue()
	{
		return( seeds_peers_sort );
	}

	@Override
	public int 
	getNbCompleted()
	{
		return( completed_count );
	}
	
	@Override
	public String
	getVotesComments()
	{
		return( votes_comments );
	}

	@Override
	public long
	getVotesCommentsSortValue()
	{
		return( votes_comments_sort );
	}

	@Override
	public int
	getRank()
	{
		return( rank );
	}

	@Override
	public String
	getTorrentLink()
	{
		return( torrent_link );
	}

	@Override
	public String
	getDetailsLink()
	{
		return( details_link );
	}

	@Override
	public String
	getCategory()
	{
		return( category );
	}

	@Override
	public String[] 
	getTags()
	{
		return( tags );
	}
	
	@Override
	public long
	getTime()
	{
		return( time );
	}

	@Override
	public long 
	getAssetDate()
	{
		return( asset_date==0?time:asset_date );
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
		boolean		_read )
	{
		SubscriptionResult result = subs.getHistory().getResult( result_id );

		if ( result != null ){

			result.setRead( _read );
		}
	}

	public void
	delete()
	{
		SubscriptionResult result = subs.getHistory().getResult( result_id );

		if ( result != null ){

			result.delete();
		}
	}

	@Override
	public void
	setUserData(
		Object	key,
		Object	data )
	{
		synchronized( this ){
			if ( user_data == null ){
				user_data = new LightHashMap<>();
			}
			user_data.put( key, data );
		}
	}

	@Override
	public Object
	getUserData(
		Object	key )
	{
		synchronized( this ){
			if ( user_data == null ){
				return( null );
			}
			return( user_data.get( key ));
		}
	}
}
