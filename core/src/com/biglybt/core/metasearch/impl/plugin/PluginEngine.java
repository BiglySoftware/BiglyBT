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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.metasearch.*;
import com.biglybt.core.metasearch.impl.EngineImpl;
import com.biglybt.core.metasearch.impl.MetaSearchImpl;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.utils.search.SearchInstance;
import com.biglybt.pif.utils.search.SearchObserver;
import com.biglybt.pif.utils.search.SearchProvider;
import com.biglybt.pif.utils.search.SearchResult;
import com.biglybt.util.MapUtils;

public class
PluginEngine
	extends EngineImpl
{
	private static int[][] FIELD_MAP = {

		{ SearchResult.PR_CATEGORY,				Engine.FIELD_CATEGORY },
		{ SearchResult.PR_COMMENTS,				Engine.FIELD_COMMENTS },
		{ SearchResult.PR_CONTENT_TYPE,			Engine.FIELD_CONTENT_TYPE },
		{ SearchResult.PR_DETAILS_LINK,			Engine.FIELD_CDPLINK },
		{ SearchResult.PR_DOWNLOAD_BUTTON_LINK,	Engine.FIELD_DOWNLOADBTNLINK },
		{ SearchResult.PR_DOWNLOAD_LINK,		Engine.FIELD_TORRENTLINK },
		{ SearchResult.PR_DRM_KEY,				Engine.FIELD_DRMKEY },
		{ SearchResult.PR_LEECHER_COUNT,		Engine.FIELD_PEERS },
		{ SearchResult.PR_NAME,					Engine.FIELD_NAME },
		{ SearchResult.PR_PLAY_LINK,			Engine.FIELD_PLAYLINK },
		{ SearchResult.PR_PRIVATE,				Engine.FIELD_PRIVATE },
		{ SearchResult.PR_PUB_DATE,				Engine.FIELD_DATE },
		{ SearchResult.PR_ASSET_DATE,			Engine.FIELD_ASSET_DATE },
		{ SearchResult.PR_SEED_COUNT,			Engine.FIELD_SEEDS },
		{ SearchResult.PR_SIZE,					Engine.FIELD_SIZE },
		{ SearchResult.PR_SUPER_SEED_COUNT,		Engine.FIELD_SUPERSEEDS },
		{ SearchResult.PR_VOTES,				Engine.FIELD_VOTES },
		{ SearchResult.PR_TORRENT_LINK,			Engine.FIELD_TORRENTLINK },
		{ SearchResult.PR_HASH,					Engine.FIELD_HASH },
	};


	public static EngineImpl
	importFromBEncodedMap(
		MetaSearchImpl		meta_search,
		Map					map )

		throws IOException
	{
		return( new PluginEngine( meta_search, map ));
	}

	private SearchProvider			provider;
	private String					plugin_id;
	
	public
	PluginEngine(
		MetaSearchImpl		_meta_search,
		long				_id,
		PluginInterface		_pi,
		SearchProvider		_provider )
	{
		super( _meta_search, Engine.ENGINE_TYPE_PLUGIN, _id, 0, 1.0f, (String)_provider.getProperty( SearchProvider.PR_NAME ));

		provider	= _provider;
		plugin_id	= _pi.getPluginID();
				
		setSource( ENGINE_SOURCE_LOCAL );
	}

	protected
	PluginEngine(
		MetaSearchImpl		_meta_search,
		Map					_map )

		throws IOException
	{
		super( _meta_search, _map );

		plugin_id = MapUtils.getMapString( _map, "plugin_id", null );
		
			// recovery from when incorrectly defaulted to 0.0

		if ( getRankBias() == 0.0f ){

			setRankBias( 1.0f );
		}

		setSource( ENGINE_SOURCE_LOCAL );
	}

	@Override
	public Map
	exportToBencodedMap()

		throws IOException
	{
		return( exportToBencodedMap( false ));
	}

	@Override
	public Map
	exportToBencodedMap(
		boolean	generic )

		throws IOException
	{
		Map	res = new HashMap();

		super.exportToBencodedMap( res, generic );

		if ( plugin_id != null ){
			
			MapUtils.setMapString( res, "plugin_id", plugin_id );
		}
		
		return( res );
	}

	public void
	setProvider(
		PluginInterface		_pi,
		SearchProvider		_provider )
	{
		provider	= _provider;
		
		if ( plugin_id == null ){
			
			plugin_id = _pi.getPluginID();
		}
	}

	public SearchProvider
	getProvider()
	{
		return( provider );
	}

	public String
	getPluginID()
	{
		return( plugin_id );
	}
	
	protected boolean
	useAccuracyForRank()
	{
		if ( provider == null ){

			return( false );
		}

		Boolean val = (Boolean)provider.getProperty( SearchProvider.PR_USE_ACCURACY_FOR_RANK );

		if ( val == null ){

			return( false );
		}

		return( val.booleanValue());
	}
	@Override
	public boolean
	isActive()
	{
		return( provider != null && super.isActive());
	}

	@Override
	public String
	getNameEx()
	{
		return( super.getName() + ": (plugin)" );
	}

	@Override
	public String
	getDownloadLinkCSS()
	{
		if ( provider == null ){

			return( null );
		}

		return((String)provider.getProperty( SearchProvider.PR_DOWNLOAD_LINK_LOCATOR ));
	}

	@Override
	public boolean
	supportsField(
		int		field )
	{
		if ( provider == null ){

			return( false );
		}

		int[] supports = (int[])provider.getProperty( SearchProvider.PR_SUPPORTS_RESULT_FIELDS );

		if ( supports == null ){

			return( true );
		}

		for (int i=0;i<FIELD_MAP.length;i++){

			int[]	entry = FIELD_MAP[i];

			if ( entry[1] == field ){

				for (int j=0;j<supports.length;j++){

					if ( supports[j] == entry[0] ){

						return( true );
					}
				}

				break;
			}
		}

		return( false );
	}

	@Override
	public boolean
	supportsContext(
		String	context_key )
	{
		return( false );
	}

	@Override
	public boolean
	isShareable()
	{
		return( false );
	}

	@Override
	public boolean
	isAnonymous()
	{
		return( false );
	}

	@Override
	public String
	getIcon()
	{
		if ( provider == null ){

			return( null );
		}

		return((String)provider.getProperty( SearchProvider.PR_ICON_URL ));
	}

	@Override
	public String
	getReferer()
	{
		if ( provider == null ){

			return( null );
		}

		return((String)provider.getProperty( SearchProvider.PR_REFERER ));
	}

	@Override
	protected Result[]
	searchSupport(
		SearchParameter[] 		params,
		Map						searchContext,
		final int 				desired_max_matches,
		final int				absolute_max_matches,
		String 					headers,
		final ResultListener 	listener )

		throws SearchException
	{
		if ( provider == null ){

			provider = getMetaSearch().resolveProvider( this );

			if ( provider == null ){

				return( new Result[0]  );
			}
		}

		Map search_parameters = new HashMap();

		String	term = null;

		for (int i=0;i<params.length;i++){

			SearchParameter param = params[i];

			String pattern 	= param.getMatchPattern();
			String value	= param.getValue();

			if ( pattern.equals( SearchProvider.SP_SEARCH_TERM )){

				term = value;

				search_parameters.put( SearchProvider.SP_SEARCH_TERM, value );

			}else if ( pattern.equals( SearchProvider.SP_MATURE )){

				search_parameters.put( SearchProvider.SP_MATURE, Boolean.valueOf(value));

			}else if ( pattern.equals( SearchProvider.SP_NETWORKS )){

				String[] networks = value.split(",");

				search_parameters.put( SearchProvider.SP_NETWORKS, networks );
				
			}else if ( pattern.equals( SearchProvider.SP_MAX_AGE_SECS )){

				search_parameters.put( SearchProvider.SP_MAX_AGE_SECS, Long.parseLong( value ));

			}else{

				Debug.out( "Unrecognised search parameter '" + pattern + "=" + value + "' ignored" );
			}
		}

		final String f_term = term;

		try{
			final List<PluginResult>	results = new ArrayList<>();

			final AESemaphore	sem = new AESemaphore( "waiter" );

			provider.search(
				search_parameters,
				new SearchObserver()
				{
					private boolean	complete = false;

					@Override
					public void
					resultReceived(
						SearchInstance 		search,
						SearchResult 		result )
					{
						PluginResult p_result = new PluginResult( PluginEngine.this, result, f_term );

						synchronized( this ){

							if ( complete ){

								return;
							}

							results.add( p_result );
						}

						if ( listener != null ){

							listener.resultsReceived( PluginEngine.this, new Result[]{ p_result });
						}

						synchronized( this ){

							if ( absolute_max_matches >= 0 && results.size() >= absolute_max_matches ){

								complete = true;

								sem.release();
							}
						}
					}

					@Override
					public void
					cancelled()
					{
						sem.release();
					}

					@Override
					public void
					complete()
					{
						sem.release();
					}

					@Override
					public Object
					getProperty(
						int property )
					{
						if ( property == PR_MAX_RESULTS_WANTED ){

							return( new Long( desired_max_matches ));
						}

						return( null );
					}
				});

			sem.reserve();

			if ( listener != null ){

				listener.resultsComplete( this );
			}

			return((Result[])results.toArray(new Result[results.size()]));

		}catch( Throwable e ){

			throw( new SearchException( "Search failed", e ));
		}
	}
}
