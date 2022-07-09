/*
 * Created on Aug 6, 2008
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.metasearch.Result;
import com.biglybt.core.metasearch.SearchLoginException;
import com.biglybt.core.metasearch.SearchParameter;
import com.biglybt.core.subs.SubscriptionException;
import com.biglybt.core.subs.SubscriptionResultFilter;
import com.biglybt.core.subs.util.SubscriptionResultFilterable;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.utils.search.SearchProvider;
import com.biglybt.util.JSONUtils;

public class
SubscriptionDownloader
{
	private SubscriptionManagerImpl		manager;
	private SubscriptionImpl			subs;

	protected
	SubscriptionDownloader(
		SubscriptionManagerImpl	_manager,
		SubscriptionImpl		_subs )

		throws SubscriptionException
	{
		manager	= _manager;
		subs	= _subs;
	}

	protected void
	download()

		throws SubscriptionException
	{
		log( "Downloading" );

		Map map = JSONUtils.decodeJSON( subs.getJSON());

		Long 	engine_id 	= (Long)map.get( "engine_id" );
		String	search_term	= (String)map.get( "search_term" );
		String	networks	= (String)map.get( "networks" );
		Map		filters		= (Map)map.get( "filters" );

		Engine engine = manager.getEngine( subs, map, false );

		if ( engine == null ){

			throw( new SubscriptionException( "Download failed, search engine " + engine_id + " not found" ));
		}

		List	sps = new ArrayList();

		if ( search_term != null ){

			sps.add( new SearchParameter( SearchProvider.SP_SEARCH_TERM, search_term ));

			log( "    Using search term '" + search_term + "' for engine " + engine.getString());
		}

		if ( networks != null && networks.length() > 0 ){

			sps.add( new SearchParameter( SearchProvider.SP_NETWORKS, networks ));
		}

		SubscriptionHistoryImpl history = (SubscriptionHistoryImpl)subs.getHistory();

		long max_age_secs = history.getMaxAgeSecs();
		
		if ( max_age_secs > 0 ){
			
			sps.add( new SearchParameter( SearchProvider.SP_MAX_AGE_SECS, String.valueOf( max_age_secs )));
		}
		
		/*
		if ( mature != null ){

			sps.add( new SearchParameter( "m", mature.toString()));
		}
		*/

		SearchParameter[] parameters = (SearchParameter[])sps.toArray(new SearchParameter[ sps.size()] );

		try{
			Map	context = new HashMap();

			context.put( Engine.SC_SOURCE, 	"subscription" );

			Result[] results = engine.search( parameters, context, -1, -1, null, null );

			log( "    Got " + results.length + " results" );

			SubscriptionResultFilterImpl result_filter = new SubscriptionResultFilterImpl( subs, filters );

			results = result_filter.filter( results );

			log( "    Post-filter: " + results.length + " results" );

			SubscriptionResultImpl[]	s_results = new SubscriptionResultImpl[results.length];

			for( int i=0;i<results.length;i++){

				SubscriptionResultImpl	s_result = new SubscriptionResultImpl( history, results[i] );

				s_results[i] = s_result;
			}

			SubscriptionResultImpl[] all_results = history.reconcileResults( engine, s_results, false );

			checkAutoDownload( all_results );

			history.setLastError( null, false );

		}catch( Throwable e ){

			log( "    Download failed", e);

			history.setLastError( Debug.getNestedExceptionMessage( e ),e instanceof SearchLoginException );

			throw( new SubscriptionException( "Search failed", e ));
		}
	}

	protected void
	checkAutoDownload(
		SubscriptionResultImpl[]	results )
	{
		if ( !subs.getHistory().isAutoDownload()){

			return;
		}

		SubscriptionResultFilter filter;
		
		try{
			filter = subs.getFilters();
			
			if ( !filter.isActive()){
				
				filter = null;
			}
		}catch( Throwable e ){
			
			filter = null;
		}
		
		for (int i=0;i<results.length;i++){

			SubscriptionResultImpl	result = results[i];

			if ( result.isDeleted() || result.getRead()){

				continue;
			}

			if ( filter != null && filter.isFiltered( new SubscriptionResultFilterable( subs, result ))){
				
				continue;
			}
			
			manager.getScheduler().download( subs, result );
		}
	}

	protected void
	log(
		String		str )
	{
		manager.log( str );
	}

	protected void
	log(
		String		str,
		Throwable	e )
	{
		manager.log( str, e );
	}
}
