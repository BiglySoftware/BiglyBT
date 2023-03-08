package com.biglybt.core.subs.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import com.biglybt.util.MapUtils;
import org.json.simple.JSONObject;

import com.biglybt.core.metasearch.FilterableResult;
import com.biglybt.core.metasearch.Result;
import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionException;
import com.biglybt.core.subs.SubscriptionResultFilter;
import com.biglybt.core.subs.SubscriptionUtils;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimeFormatter;
import com.biglybt.util.JSONUtils;

/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

public class
SubscriptionResultFilterImpl
	implements SubscriptionResultFilter
{
	private final SubscriptionImpl		subs;

	private String[] 	textFilters;
	private Pattern[]	textFilterPatterns;

	private String[] 	excludeTextFilters;
	private Pattern[]	excludeTextFilterPatterns;

	private long minSeeds = -1;
	private long maxSeeds = -1;
	private long minPeers = -1;
	private long minSize = -1;
	private long maxSize = -1;
	private long maxAgeSecs	= -1;
	
	private String categoryFilter = null;
	
		// If you add any more filters then make sure you update isActive()... 
	

	public 
	SubscriptionResultFilterImpl()
	{
		subs	= null;
		
		textFilters					= new String[0];
		textFilterPatterns 			= NO_PATTERNS;
		excludeTextFilters			= new String[0];
		excludeTextFilterPatterns	= NO_PATTERNS;
	}
	
	public 
	SubscriptionResultFilterImpl(
		SubscriptionImpl	_subs, 
		Map 				filters) 
	{
		subs	= _subs;

		try {
			textFilters = importStrings(filters,"text_filter"," ");

			textFilterPatterns = getPatterns( textFilters );

			excludeTextFilters = importStrings(filters,"text_filter_out"," ");

			excludeTextFilterPatterns = getPatterns( excludeTextFilters );
			
			minSize = MapUtils.importLong(filters,"min_size",-1l);

			maxSize = MapUtils.importLong(filters,"max_size",-1l);

			minSeeds = MapUtils.importLong(filters, "min_seeds",-1l);
			maxSeeds = MapUtils.importLong(filters, "max_seeds",-1l);
			minPeers = MapUtils.importLong(filters, "min_peers",-1l);
			
			maxAgeSecs = MapUtils.importLong(filters, "max_age",-1l);

			String rawCategory = MapUtils.getMapString(filters,"category", null);
			if(rawCategory != null) {
				categoryFilter = rawCategory.toLowerCase();
			}

		} catch(Exception e) {
			//Invalid filters array
		}
	}

	@Override
	public boolean 
	isActive()
	{
		return( textFilters.length > 0 ||
				excludeTextFilters.length > 0 ||
				minSize >= 0 ||
				maxSize >= 0 ||
				minSeeds >= 0 ||
				maxSeeds >= 0 ||
				minPeers >= 0 ||
				maxAgeSecs >= 0 ||
				categoryFilter != null );	
	}

	@Override
	public long
	getMinSize()
	{
		return( minSize );
	}

	@Override
	public void 
	setMinSize(
		long min_size)
	{
		if ( min_size <= 0 ){
			min_size = -1;
		}
		
		minSize = min_size;
	}
	
	@Override
	public long
	getMaxSize()
	{
		return( maxSize );
	}

	@Override
	public void 
	setMaxSize(
		long max_size)
	{
		if ( max_size <= 0 ){
			max_size = -1;
		}
		
		maxSize = max_size;
	}
	
	@Override
	public long 
	getMinSeeds()
	{
		return( minSeeds );
	}
	
	@Override
	public void 
	setMinSeeds(
		long min_seeds)
	{
		if ( min_seeds <= 0 ){
			min_seeds = -1;
		}
		
		minSeeds = min_seeds;
	}
	
	@Override
	public long 
	getMaxSeeds()
	{
		return( maxSeeds );
	}
	
	@Override
	public void 
	setMaxSeeds(
		long max_seeds)
	{
		if ( max_seeds <= 0 ){
			max_seeds = -1;
		}
		
		maxSeeds = max_seeds;
	}
	
	@Override
	public long 
	getMinPeers()
	{
		return( minPeers );
	}
	
	@Override
	public void 
	setMinPeers(
		long min_peers)
	{
		if ( min_peers <= 0 ){
			min_peers = -1;
		}
		
		minPeers = min_peers;
	}
	
	@Override
	public long
	getMaxAgeSecs()
	{
		return( maxAgeSecs );
	}
	
	@Override
	public void
	setMaxAgeSecs(
		long	max_secs )
	{
		if ( max_secs <= 0 ){
			max_secs = -1;
		}
		
		maxAgeSecs = max_secs;	
	}
	
	@Override
	public String[]
	getWithWords()
	{
		return( textFilters );
	}

	@Override
	public void 
	setWithWords(
		String[] with_words)
	{
		textFilters	= with_words;

		textFilterPatterns = getPatterns( textFilters );	
	}
	
	@Override
	public String[]
	getWithoutWords()
	{
		return( excludeTextFilters );
	}

	@Override
	public void 
	setWithoutWords(
		String[] without_words)
	{
		excludeTextFilters = without_words;

		excludeTextFilterPatterns = getPatterns( excludeTextFilters );
	}
	
	@Override
	public long 
	getDependenciesVersion()
	{
		long result = 0;
		
		List<Subscription> deps = getDependsOn();
		
		for ( Subscription dep: deps ){
			
			result += dep.getMetadataMutationIndicator();
		}
		
		return( result );
	}
	
	@Override
	public List<Subscription>
	getDependsOn()
	{
		return( SubscriptionUtils.getDependsOnClosure( subs ));
	}
	
	@Override
	public void
	save()

		throws SubscriptionException
	{
		Map map = JSONUtils.decodeJSON( subs.getJSON());

		Map filters = new JSONObject();

		map.put( "filters", filters );

		exportStrings( filters, "text_filter", textFilters );
		exportStrings( filters, "text_filter_out", excludeTextFilters );
	
		filters.put( "min_size", minSize );
		filters.put( "max_size", maxSize );
		filters.put( "min_seeds", minSeeds );
		filters.put( "max_seeds", maxSeeds );
		filters.put( "min_peers", minPeers );
		filters.put( "max_age", maxAgeSecs );

		subs.setDetails( subs.getName( false ), subs.isPublic(), map.toString());
	}

	@Override
	public String
	getString()
	{
		String	res = addString( "", "+", getString(textFilters));

		res = addString( res, "-", getString(excludeTextFilters));

		long kInB = DisplayFormatters.getKinB();
		long mInB = kInB*kInB;
		
		res = addString( res, "cat=", categoryFilter );
		res = addString( res, ">=", minSize<=0?null:(minSize/mInB)+" MB");
		res = addString( res, "<=", maxSize<=0?null:(maxSize/mInB)+" MB");
		res = addString( res, "s>=", minSeeds<=0?null:String.valueOf(minSeeds));
		res = addString( res, "s<=", maxSeeds<=0?null:String.valueOf(maxSeeds));
		res = addString( res, "p>=", minPeers<=0?null:String.valueOf(minPeers));
		res = addString( res, "a<=", maxAgeSecs<=0?null:TimeFormatter.format3( maxAgeSecs, null, true ) );

		return( res );
	}

	private String
	addString(
		String	existing,
		String	key,
		String	rest )
	{
		if ( rest == null || rest.length() == 0 ){

			return( existing );
		}

		String str = key + rest;

		if ( existing == null || existing.length() == 0){

			return( str );
		}

		return( existing + "," + str );
	}

	private String
	getString(
		String[]		strs )
	{
		String	res = "";

		for( int i=0;i<strs.length;i++){
			res += (i==0?"":"&") + strs[i];
		}

		return( res );
	}


	private static Pattern[] NO_PATTERNS = {};

	private Pattern[]
	getPatterns(
		String[]	strs )
	{
		if ( strs.length == 0 ){

			return( NO_PATTERNS );
		}

		Pattern[] pats = new Pattern[strs.length];

		for (int i=0;i<strs.length;i++){

			try{
				pats[i] = Pattern.compile( strs[i].trim());

			}catch( Throwable e ){

				// System.out.println( "Failed to compile pattern '" + strs[i] );
			}
		}

		return( pats );
	}

	private String[] importStrings(Map filters,String key,String separator) throws IOException {
		String rawStringFilter = MapUtils.getMapString(filters,key,null);
		if(rawStringFilter != null) {
			StringTokenizer st = new StringTokenizer(rawStringFilter,separator);
			String[] stringFilter = new String[st.countTokens()];
			for(int i = 0 ; i < stringFilter.length ; i++) {
				stringFilter[i] = st.nextToken().toLowerCase();
			}
			return stringFilter;
		}
		return new String[0];
	}

	private void
	exportStrings(
		Map			map,
		String		key,
		String[]	values )
	{
		if ( values == null || values.length == 0 ){

			return;
		}

		String encoded = "";

		for ( String value: values ){

			encoded += (encoded==""?"":" ") + value;
		}

		map.put( key, encoded );
	}

	public Result[] filter(Result[] results) {
		List<Result> filteredResults = new ArrayList<>(results.length);
		for(int i = 0 ; i < results.length ; i++) {
			Result result = results[i];

			if ( !isFiltered( result )){

				filteredResults.add(result);
			}
		}

		Result[] fResults = (Result[]) filteredResults.toArray(new Result[filteredResults.size()]);

		return fResults;
	}
	
	public boolean 
	isFiltered(
		FilterableResult result )
	{
		if ( isFilteredSupport( result )){
			
			return( true );
		}
		
		List<Subscription> deps = getDependsOn();
		
		for ( Subscription dep: deps ){
			
			try{
				if (((SubscriptionImpl)dep).getFilters().isFiltered( result )){
				
					return( true );
				}
			}catch( Throwable e ){
				
			}
		}
		
		return( false );
	}
	
	public boolean 
	isFilteredSupport(
		FilterableResult result )
	{
		String name = result.getName();
		//Results need a name, or they are by default invalid
		if(name == null) {
			return( true );
		}
		name = name.toLowerCase();

		boolean valid = true;
		for(int j = 0 ; j < textFilters.length ; j++) {

			//If one of the text filters do not match, let's not keep testing the others
			// and mark the result as not valid
			if(!name.contains(textFilters[j])) {

					// double check against reg-expr if exists

				Pattern p = textFilterPatterns[j];

				if ( p == null  || !p.matcher( name ).find()){

					valid = false;

					break;
				}
			}
		}

		//if invalid after name check, let's get to the next result
		
		if (!valid){
			return( true );
		}

		for(int j = 0 ; j < excludeTextFilters.length ; j++) {

			//If one of the text filters do not match, let's not keep testing the others
			// and mark the result as not valid
			if(name.contains(excludeTextFilters[j])) {
				valid = false;
				break;
			}else{
				Pattern p = excludeTextFilterPatterns[j];

				if ( p != null  && p.matcher( name ).find()){
					valid = false;
					break;
				}
			}
		}

		//if invalid after name check, let's get to the next result
		if(!valid) {
			return( true );
		}

		long size = result.getSize();

		if ( minSize > -1 ){
			
			if ( size < minSize ){
				
				return( true );
			}
		}

		if (maxSize > -1){
			
			if ( size > maxSize ){
				
				return( true );
			}
		}

		if (minSeeds > -1){
				
				if ( result.getNbSeeds() < minSeeds ){
					
					return( true );
				}
			}
	
		if (maxSeeds > -1){
			
			if ( result.getNbSeeds() > maxSeeds ){
				
				return( true );
			}
		}
	
		if (minPeers > -1){
			
			if ( result.getNbPeers() < minPeers ){
				
				return( true );
			}
		}

		if (maxAgeSecs > -1 ){
			
			long time = result.getTime();
			
			long age_secs = (SystemTime.getCurrentTime() - time)/1000;
			
			if ( age_secs > maxAgeSecs ){
				
				return( true );
			}
		}
		if(categoryFilter != null) {
			String category = result.getCategory();
			if(category == null || !category.equalsIgnoreCase(categoryFilter)) {
				return( true );
			}
		}
		
		return( false );
	}
}