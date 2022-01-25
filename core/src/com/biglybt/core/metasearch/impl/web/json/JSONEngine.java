/*
 * Created on May 6, 2008
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

package com.biglybt.core.metasearch.impl.web.json;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.biglybt.core.metasearch.*;
import com.biglybt.core.metasearch.impl.EngineImpl;
import com.biglybt.core.metasearch.impl.MetaSearchImpl;
import com.biglybt.core.metasearch.impl.web.FieldMapping;
import com.biglybt.core.metasearch.impl.web.WebEngine;
import com.biglybt.core.metasearch.impl.web.WebResult;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.util.MapUtils;

public class
JSONEngine
	extends WebEngine
{
	private final static String variablePattern = "\\$\\{[^}]+\\}";
	private final static Pattern patternVariable = Pattern.compile(variablePattern);
	private static final boolean DEBUG_MAPPINGS = false;

	public static EngineImpl
	importFromBEncodedMap(
		MetaSearchImpl	meta_search,
		Map				map )

		throws IOException
	{
		return( new JSONEngine( meta_search, map ));
	}

	public static Engine
	importFromJSONString(
		MetaSearchImpl	meta_search,
		long			id,
		long			last_updated,
		float			rank_bias,
		String			name,
		JSONObject		map )

		throws IOException
	{
		return( new JSONEngine( meta_search, id, last_updated, rank_bias, name, map ));
	}

	private String resultsEntryPath;
	private String rankDivisorPath;

	private float rankDivisor = 1.0f;


		// explicit test constructor

	public
	JSONEngine(
		MetaSearchImpl		meta_search,
		long 				id,
		long 				last_updated,
		float				rank_bias,
		String 				name,
		String 				searchURLFormat,
		String 				timeZone,
		boolean 			automaticDateFormat,
		String 				userDateFormat,
		String 				resultsEntryPath,
		FieldMapping[] 		mappings,
		boolean				needs_auth,
		String				auth_method,
		String				login_url,
		String[]			required_cookies )
	{
		super( 	meta_search,
				Engine.ENGINE_TYPE_JSON,
				id,
				last_updated,
				rank_bias,
				name,
				searchURLFormat,
				timeZone,
				automaticDateFormat,
				userDateFormat,
				mappings,
				needs_auth,
				auth_method,
				login_url,
				required_cookies );

		this.resultsEntryPath = resultsEntryPath;

		setSource( Engine.ENGINE_SOURCE_LOCAL );

		setSelectionState( SEL_STATE_MANUAL_SELECTED );
	}

		// bencoded constructor

	protected
	JSONEngine(
		MetaSearchImpl	meta_search,
		Map				map )

		throws IOException
	{
		super( meta_search, map );

		resultsEntryPath = MapUtils.getMapString( map, "json.path", null );
		rankDivisorPath = MapUtils.getMapString( map, "rank.divisor.path", null );
	}

		// json constructor

	protected
	JSONEngine(
		MetaSearchImpl	meta_search,
		long			id,
		long			last_updated,
		float			rank_bias,
		String			name,
		JSONObject		map )

		throws IOException
	{
		super( meta_search, Engine.ENGINE_TYPE_JSON, id, last_updated, rank_bias, name, map );

		resultsEntryPath = MapUtils.getMapString( map, "json_result_key", null );
		resultsEntryPath = UrlUtils.decode(resultsEntryPath);
		rankDivisorPath = MapUtils.getMapString( map, "rank_divisor_key", null );
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
		boolean		generic )

		throws IOException
	{
		Map	res = new HashMap();

		MapUtils.setMapString( res, "json.path", resultsEntryPath );

		MapUtils.setMapString(res, "rank.divisor.path", rankDivisorPath);

		super.exportToBencodedMap( res, generic );

		return( res );
	}

	@Override
	protected void
	exportToJSONObject(
		JSONObject		res )

		throws IOException
	{
		res.put( "json_result_key", resultsEntryPath );

		res.put("rank_divisor_key", rankDivisorPath);

		super.exportToJSONObject( res );
	}

	@Override
	protected Result[]
	searchSupport(
		SearchParameter[] 	searchParameters,
		Map					searchContext,
		int					desired_max_matches,
		int					absolute_max_matches,
		String				headers,
		ResultListener		listener )

		throws SearchException
	{
		debugStart();

		pageDetails page_details = super.getWebPageContent( searchParameters, searchContext, headers, false );

		String	page = page_details.getContent();

		if ( listener != null ){
			listener.contentReceived( this, page );
		}


		String searchQuery = null;

		for(int i = 0 ; i < searchParameters.length ; i++) {
			if(searchParameters[i].getMatchPattern().equals("s")) {
				searchQuery = searchParameters[i].getValue();
			}
		}

		FieldMapping[] mappings = getMappings();

		try {
			Object jsonObject;

			try{
				jsonObject = JSONValue.parse(page);

			}catch( Throwable e ){

					// fix a vaguely common error: trailing \ before end-of-string:    - \",

				String temp_page = page.replaceAll( "\\\\\",", "\"," );

				try{
					jsonObject = JSONValue.parse( temp_page );

				}catch( Throwable f ){

					throw( e );
				}
			}

			if (rankDivisorPath != null) {
				String[] split = rankDivisorPath.split("\\.");
				try {
					if (split.length > 0) {
						Object jsonRankDivisor = jsonObject;
	  					for (int i = 0; i < split.length - 1; i++) {
	  						String key = split[i];
	  						if ( jsonRankDivisor instanceof JSONObject ){

	  							jsonRankDivisor = ((JSONObject)jsonRankDivisor).get(key);

	  						}else{
	  							break;
	  						}
	  					}

	  					if (jsonRankDivisor instanceof Map) {
	  						jsonRankDivisor = ((Map) jsonRankDivisor).get(split[split.length - 1]);
	  					}

	  					if ( jsonRankDivisor instanceof Number ){
	  						rankDivisor = ((Number) jsonRankDivisor).floatValue();
	  					}
					}
				} catch (Exception e) {
				}
			}

			JSONArray resultArray = null;

			if(resultsEntryPath != null && resultsEntryPath.length() > 0) {
				String[] split = resultsEntryPath.split("\\.");
				if(jsonObject instanceof JSONArray && split.length > 0 && !split[0].startsWith("[")) {
					JSONArray array = (JSONArray) jsonObject;
					if(array.size() == 1) {
						jsonObject = array.get(0);
					}
				}
				for (String pathEntry : split) {
					if ( jsonObject == null ){
						throw new SearchException("Invalid entry path : " + resultsEntryPath );
					}

					try{
						if (pathEntry.startsWith("[") && pathEntry.endsWith("]")) {
							int idx = Integer.parseInt(pathEntry.substring(1, pathEntry.length() - 1));
							jsonObject = ((JSONArray) jsonObject).get(idx);
						} else {
							jsonObject = ((JSONObject)jsonObject).get(pathEntry);
						}

					}catch( Throwable t ){

						throw new SearchException("Invalid entry path : " + resultsEntryPath,t);
					}
				}
			}

			try{
				resultArray = (JSONArray) jsonObject;

			}catch(Throwable t){

				throw new SearchException("Object is not a result array. Check the JSON service and/or the entry path");
			}


			if ( resultArray != null ){

				List results = new ArrayList();

				Throwable	decode_failure 		= null;

				for(int i = 0 ; i < resultArray.size() ; i++) {

					Object obj = resultArray.get(i);

					if(obj instanceof JSONObject) {
						JSONObject jsonEntry = (JSONObject) obj;

						if ( absolute_max_matches >= 0 ){
							if ( --absolute_max_matches < 0 ){
								break;
							}
						}

						if ( listener != null ){

								// sort for consistent order

							Iterator it = new TreeMap( jsonEntry ).entrySet().iterator();

							String[]	groups = new String[ jsonEntry.size()];

							int	pos = 0;

							while( it.hasNext()){

								Map.Entry entry = (Map.Entry)it.next();

								Object key 		= entry.getKey();
								Object value 	= entry.getValue();

								if ( key != null && value != null ){

									groups[pos++] = key.toString() + "=" + UrlUtils.encode( value.toString());

								}else{

									groups[pos++] = "";
								}
							}

							listener.matchFound( this, groups );
						}

						WebResult result = new WebResult(this,getRootPage(),getBasePage(),getDateParser(),searchQuery);

						try{
							boolean addResult = true;
							
							Set<Integer>	fields_mapped = new HashSet<>();
							
							for(int j = 0 ; j < mappings.length ; j++) {
								String fieldFrom = mappings[j].getName();
								if(fieldFrom == null) {
									continue;
								}

								int fieldTo = mappings[j].getField();

								String fieldContent = null;
								Matcher matcher = patternVariable.matcher(fieldFrom);
								if (matcher.find()) {
									fieldContent = fieldFrom;
									do {
										String key = matcher.group();
										key = key.substring(2, key.length() - 1);

										String[] keys = key.split(",", -1);
										try {
											Object replaceWithObject = jsonEntry.get(keys[0]);
											String replaceWith = replaceWithObject == null ? ""
													: replaceWithObject.toString();

											if (keys.length > 1) {
												String[] commands = keys[1].split("\\+");
												int keyPos = 2;
												for (String command : commands) {
													try {
														if (DEBUG_MAPPINGS) {
															System.out.println("command " + command);
														}
														if (command.equals("replace")) {
															if (keyPos + 2 > keys.length) {
	  														if (DEBUG_MAPPINGS) {
	  															System.out.println("not enough keys. have " + keys.length + "; need " + (keyPos + 3));
	  														}
																break;
															}
															String simpleReplace = keys[keyPos];
															keyPos++;
															String simpleReplacement = keys[keyPos];
															keyPos++;

															replaceWith = replaceWith.replaceAll(
																	simpleReplace,
																	Matcher.quoteReplacement(simpleReplacement));
														} else if (command.equals("ucase")) {
															replaceWith = replaceWith.toUpperCase();
														} else if (command.equals("lcase")) {
															replaceWith = replaceWith.toLowerCase();
														} else if (command.equals("urldecode")) {
															replaceWith = UrlUtils.decode(replaceWith);
														}
														if (DEBUG_MAPPINGS) {
															System.out.println("replaceWith now " + replaceWith);
														}
													} catch (Exception e) {
														if (DEBUG_MAPPINGS) {
															System.out.println(e.toString());
														}
													}
												}
											}

											fieldContent = fieldContent.replaceFirst(variablePattern,
													replaceWith);

										} catch (Exception e) {

										}
									} while (matcher.find());
								} else {
									Object fieldContentObj = jsonEntry.get(fieldFrom);
									fieldContent = fieldContentObj == null ? ""
											: fieldContentObj.toString();
								}

								if(fieldContent == null) {
									continue;
								}else if ( fieldContent.isEmpty()){
										// support multiple mappings for the same entry such that
										// earlier ones take priority over later empty ones
									if ( fields_mapped.contains( fieldTo )){
										continue;
									}
								}

								Pattern filter = mappings[j].getPostFilterPattern(searchQuery);
								if (filter != null) {
									Matcher postMatch = filter.matcher(fieldContent);
									if (!postMatch.find()) {
										addResult = false;
										continue;
									}
								}

								if (!fieldContent.isEmpty()){
									fields_mapped.add( fieldTo );
								}
								
								switch(fieldTo) {
									case FIELD_NAME :
										result.setNameFromHTML(fieldContent);
										break;
									case FIELD_SIZE :
										result.setSizeFromHTML(fieldContent);
										break;
									case FIELD_PEERS :
										result.setNbPeersFromHTML(fieldContent);
										break;
									case FIELD_SEEDS :
										result.setNbSeedsFromHTML(fieldContent);
										break;
									case FIELD_CATEGORY :
										result.setCategoryFromHTML(fieldContent);
										break;
									case FIELD_DATE :
										result.setPublishedDateFromHTML(fieldContent);
										break;
									case FIELD_COMMENTS :
										result.setCommentsFromHTML(fieldContent);
										break;
									case FIELD_CDPLINK :
										result.setCDPLink(fieldContent);
										break;
									case FIELD_TORRENTLINK :
										result.setTorrentLink(fieldContent);
										break;
									case FIELD_PLAYLINK :
										result.setPlayLink(fieldContent);
										break;
									case FIELD_DOWNLOADBTNLINK :
										result.setDownloadButtonLink(fieldContent);
										break;
									case FIELD_VOTES :
										result.setVotesFromHTML(fieldContent);
										break;
									case FIELD_SUPERSEEDS :
										result.setNbSuperSeedsFromHTML(fieldContent);
										break;
									case FIELD_PRIVATE :
										result.setPrivateFromHTML(fieldContent);
										break;
									case FIELD_DRMKEY :
										result.setDrmKey(fieldContent);
										break;
									case FIELD_VOTES_DOWN :
										result.setVotesDownFromHTML(fieldContent);
										break;
									case FIELD_HASH :
											// seen a magnet being returned as hash!
										if ( fieldContent.startsWith( "magnet:")){
											byte[] hash = UrlUtils.getTruncatedHashFromMagnetURI(fieldContent);
											if ( hash != null ){
												fieldContent = ByteFormatter.encodeString( hash );
											}else{
												fieldContent = null;
											}
										}
										if ( fieldContent != null ){
											result.setHash(fieldContent);
										}
										break;
									case FIELD_RANK : {
										result.setRankFromHTML(fieldContent, rankDivisor);
										break;
									}
									default:
										break;
								}
							}

							if (result.getHash() == null) {
								String downloadLink = result.getDownloadLink();
								String possibleMagnet = UrlUtils.parseTextForMagnets(downloadLink);
								byte[] hash = UrlUtils.getTruncatedHashFromMagnetURI(possibleMagnet);
								if (hash != null) {
									result.setHash(ByteFormatter.nicePrint(hash, true));
								}
							}

							if (addResult) {
								results.add(result);
							}

						}catch( Throwable e ){

							decode_failure = e;
						}
					}
				}

				if ( results.size() == 0 && decode_failure != null ){

					throw( decode_failure );
				}

				Result[] res = (Result[]) results.toArray(new Result[results.size()]);

				debugLog( "success: found " + res.length + " results" );

				return( res );

			}else{

				debugLog( "success: no result array found so no results" );

				return( new Result[0]);
			}

		}catch( Throwable e ){

			debugLog( "failed: " + Debug.getNestedExceptionMessageAndStack( e ));

			if ( e instanceof SearchException ){

				throw((SearchException)e );
			}

			String content_str = page;

			if ( content_str.length() > 256 ){

				content_str = content_str.substring( 0, 256 ) + "...";
			}

			//System.out.println( page );

			throw( new SearchException( "JSON matching failed for " + getName() + ", content=" + content_str, e ));
		}
	}


}
