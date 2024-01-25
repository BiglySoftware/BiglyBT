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


package com.biglybt.ui.swt.search;


import java.util.Date;

import org.eclipse.swt.graphics.Image;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.LightHashMap;

import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.metasearch.Result;
import com.biglybt.core.subs.util.SearchSubsResultBase;

public class
SBC_SearchResult
	implements SearchSubsResultBase, SBC_SearchResultsView.ImageLoadListener
{
	private final SBC_SearchResultsView		view;

	private final Engine			engine;
	private final Result			result;

	private final int				content_type;
	private final String			seeds_peers;
	private final long				seeds_peers_sort;
	private final int				seed_count;
	private final int				peer_count;
	private final long				votes_comments_sort;
	private final String			votes_comments;

	private LightHashMap<Object,Object>	user_data;

	public
	SBC_SearchResult(
		SBC_SearchResultsView	_view,
		Engine					_engine,
		Result					_result )
	{
		view	= _view;
		engine	= _engine;
		result	= _result;

		String type = result.getContentType();

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

		int seeds 		= result.getNbSeeds();
		int leechers 	= result.getNbPeers();
		int	super_seeds	= result.getNbSuperSeeds();

		if ( super_seeds > 0 ){
			seeds += (super_seeds*10);
		}
		
		seed_count = seeds<0?0:seeds;
		peer_count = leechers<0?0:leechers;
		
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

		long votes		= result.getVotes();
		long comments 	= result.getComments();

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
	}

	public Engine
	getEngine()
	{
		return( engine );
	}

	@Override
	public String 
	getID()
	{
		return( result.getUID());
	}
	
	@Override
	public final String
	getName()
	{
		return( result.getName());
	}

	@Override
	public byte[]
	getHash()
	{
		String base32_hash = result.getHash();

		if ( base32_hash != null ){

			return( Base32.decode( base32_hash ));
		}

		return( null );
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
		return( result.getSize());
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
		float rank = result.getRank();

		return( (int)( rank*100 ));
	}

	@Override
	public String
	getTorrentLink()
	{
		String r = result.getTorrentLink();

		if ( r == null ){

			r = result.getDownloadLink();
		}

		return( r );
	}

	@Override
	public String
	getDetailsLink()
	{
		return( result.getCDPLink() );
	}

	@Override
	public String
	getCategory()
	{
		return( result.getCategory() );
	}

	@Override
	public String[] 
	getTags()
	{
		return( result.getTags());
	}
	
	@Override
	public long
	getTime()
	{
		return( result.getTime());
	}

	@Override
	public long 
	getAssetDate()
	{
		Date d = result.getAssetDate();
				
		return( d==null?0:d.getTime());
	}
	
	public Image
	getIcon()
	{
		return( view.getIcon( engine, this ));
	}

	@Override
	public boolean
	getRead()
	{
		return( false );
	}

	@Override
	public void
	setRead(
		boolean		read )
	{
	}

	@Override
	public void
	imageLoaded(
		Image		image )
	{
		view.invalidate( this );
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
