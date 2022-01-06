/*
 * Created on Jun 20, 2008
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


package com.biglybt.core.metasearch.impl.plugin;

import java.util.Date;
import java.util.Map;

import com.biglybt.core.metasearch.Result;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.LightHashMap;
import com.biglybt.pif.utils.search.SearchResult;

public class
PluginResult
	extends Result
{
	private static final Object NULL_OBJECT = PluginResult.class;

	private SearchResult			result;
	private String					search_term;

	private Map		property_cache = new LightHashMap();

	protected
	PluginResult(
		PluginEngine		_engine,
		SearchResult		_result,
		String				_search_term )
	{
		super( _engine );

		result			= _result;
		search_term		= _search_term;
	}

	@Override
	public Date
	getPublishedDate()
	{
		return((Date)getResultProperty( SearchResult.PR_PUB_DATE ));
	}

	@Override
	public Date
	getAssetDate()
	{
		return((Date)getResultProperty( SearchResult.PR_ASSET_DATE ));
	}
	
	@Override
	public String
	getCategory()
	{
		return(getStringProperty( SearchResult.PR_CATEGORY ));
	}

	@Override
	public String[] 
	getTags()
	{
		
		return(getStringListProperty( SearchResult.PR_TAGS ));
	}
	
	@Override
	public void
	setCategory(
		String category )
	{
	}

	@Override
	public String
	getContentType()
	{
		String ct = getStringProperty( SearchResult.PR_CONTENT_TYPE );

		if ( ct == null || ct.length() == 0 ){

			ct = guessContentTypeFromCategory( getCategory());
		}

		return( ct );
	}

	@Override
	public void
	setContentType(
		String contentType )
	{
	}

	@Override
	public String
	getName()
	{
		return(getStringProperty( SearchResult.PR_NAME ));
	}

	@Override
	public long
	getSize()
	{
		return(getLongProperty( SearchResult.PR_SIZE ));
	}

	@Override
	public int
	getNbPeers()
	{
		return(getIntProperty( SearchResult.PR_LEECHER_COUNT ));
	}

	@Override
	public int
	getNbSeeds()
	{
		return(getIntProperty( SearchResult.PR_SEED_COUNT ));
	}

	@Override
	public int
	getNbSuperSeeds()
	{
		return(getIntProperty( SearchResult.PR_SUPER_SEED_COUNT ));
	}

	@Override
	public int
	getComments()
	{
		return(getIntProperty( SearchResult.PR_COMMENTS ));
	}

	@Override
	public int
	getVotes()
	{
		return(getIntProperty( SearchResult.PR_VOTES ));
	}

	@Override
	public int
	getVotesDown()
	{
		return(getIntProperty( SearchResult.PR_VOTES_DOWN ));
	}

	@Override
	public boolean
	isPrivate()
	{
		return( getBooleanProperty( SearchResult.PR_PRIVATE ));
	}


	@Override
	public String
	getDRMKey()
	{
		return(getStringProperty( SearchResult.PR_DRM_KEY ));
	}

	@Override
	public String
	getDownloadLink()
	{
		return( adjustLink( getStringProperty( SearchResult.PR_DOWNLOAD_LINK )));
	}

	@Override
	public String
	getDownloadButtonLink()
	{
		return(adjustLink( getStringProperty( SearchResult.PR_DOWNLOAD_BUTTON_LINK )));
	}

	@Override
	public String
	getCDPLink()
	{
		return( adjustLink( getStringProperty( SearchResult.PR_DETAILS_LINK )));

	}

	@Override
	public String
	getPlayLink()
	{
		return( adjustLink( getStringProperty( SearchResult.PR_PLAY_LINK )));
	}

	@Override
	public String
	getTorrentLink()
	{
		return( adjustLink( getStringProperty( SearchResult.PR_TORRENT_LINK )));
	}

	@Override
	public String
	getUID()
	{
		return(getStringProperty( SearchResult.PR_UID ));
	}

	@Override
	public String
	getHash()
	{
		byte[] hash = getByteArrayProperty( SearchResult.PR_HASH );

		if ( hash == null ){

			return( null );
		}

		return( Base32.encode( hash ));
	}

	@Override
	public float
	getRank()
	{
		if (((PluginEngine)getEngine()).useAccuracyForRank()){

			return( applyRankBias( getAccuracy()));
		}

		long	l_rank = getLongProperty( SearchResult.PR_RANK );

			// if we have seeds/peers just use the usual mechanism

		if ( getLongProperty( SearchResult.PR_SEED_COUNT ) >= 0 && getLongProperty( SearchResult.PR_LEECHER_COUNT ) >= 0 ){

			l_rank = Long.MIN_VALUE;
		}

		if ( l_rank == Long.MIN_VALUE ){

			return( super.getRank());
		}

		float rank = l_rank;

		if ( rank > 100 ){

			rank = 100;

		}else if ( rank < 0 ){

			rank = 0;
		}

		return( applyRankBias( rank / 100 ));
	}

	@Override
	public float
	getAccuracy()
	{
		long	l_accuracy = getLongProperty( SearchResult.PR_ACCURACY );

		if ( l_accuracy == Long.MIN_VALUE ){

			return( -1 );
		}

		float accuracy = l_accuracy;

		if ( accuracy > 100 ){

			accuracy = 100;

		}else if ( accuracy < 0 ){

			accuracy = 0;
		}

		return( accuracy / 100 );
	}

	@Override
	public String
	getSearchQuery()
	{
		return( search_term );
	}

	protected int
	getIntProperty(
		int		name )
	{
		return((int)getLongProperty( name ));
	}

	protected long
	getLongProperty(
		int		name )
	{
		return( getLongProperty( name, Long.MIN_VALUE ));
	}

	protected long
	getLongProperty(
		int		name,
		long	def )
	{
		try{
			Long	l = (Long)getResultProperty( name );

			if ( l == null ){

				return( def );
			}

			return( l.longValue());

		}catch( Throwable e ){

			Debug.out( "Invalid value returned for Long property " + name );

			return( def );
		}
	}

	protected boolean
	getBooleanProperty(
		int		name )
	{
		return( getBooleanProperty( name, false ));
	}

	protected boolean
	getBooleanProperty(
		int		name,
		boolean	def )
	{
		try{
			Boolean	b = (Boolean)getResultProperty( name );

			if ( b == null ){

				return( def );
			}

			return( b.booleanValue());

		}catch( Throwable e ){

			Debug.out( "Invalid value returned for Boolean property " + name );

			return( def );
		}
	}

	protected String
	getStringProperty(
		int		name )
	{
		return( getStringProperty( name, "" ));
	}

	protected String
	getStringProperty(
		int		name,
		String	def )
	{
		try{
			String	l = (String)getResultProperty( name );

			if ( l == null ){

				return( def );
			}

			return( unescapeEntities( removeHTMLTags( l )));

		}catch( Throwable e ){

			Debug.out( "Invalid value returned for String property " + name );

			return( def );
		}
	}

	protected String[]
	getStringListProperty(
		int		name )
	{
		return( getStringListProperty( name, new String[0] ));
	}

	protected String[]
	getStringListProperty(
		int		name,
		String[]	def )
	{
		try{
			String[]	l = (String[])getResultProperty( name );

			if ( l == null ){

				return( def );
			}

			return( l );

		}catch( Throwable e ){

			Debug.out( "Invalid value returned for String[] property " + name );

			return( def );
		}
	}
	
	protected byte[]
	getByteArrayProperty(
		int		name )
	{
		try{
			return((byte[])getResultProperty( name ));

		}catch( Throwable e ){

			Debug.out( "Invalid value returned for byte[] property " + name );

			return( null );
		}
	}

	protected synchronized Object
	getResultProperty(
		int		prop )
	{
		Integer i_prop = new Integer( prop );

		Object	res = property_cache.get( i_prop );

		if ( res == null ){

			res = result.getProperty( prop );

			if ( res == null ){

				res = NULL_OBJECT;
			}

			property_cache.put( i_prop, res );
		}

		if ( res == NULL_OBJECT ){

			return( null );
		}

		return( res );
	}
}
