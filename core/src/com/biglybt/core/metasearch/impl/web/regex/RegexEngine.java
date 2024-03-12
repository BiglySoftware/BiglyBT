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

package com.biglybt.core.metasearch.impl.web.regex;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONObject;

import com.biglybt.core.metasearch.*;
import com.biglybt.core.metasearch.impl.EngineImpl;
import com.biglybt.core.metasearch.impl.MetaSearchImpl;
import com.biglybt.core.metasearch.impl.web.FieldMapping;
import com.biglybt.core.metasearch.impl.web.WebEngine;
import com.biglybt.core.metasearch.impl.web.WebResult;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.TimeLimitedTask;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.util.MapUtils;

public class
RegexEngine
	extends WebEngine
{
	private final static boolean DEBUG_MAPPINGS = false;
	private final static String variablePattern = "\\$\\{[^}]+\\}";
	final static Pattern patternVariable = Pattern.compile(variablePattern);

	public static EngineImpl
	importFromBEncodedMap(
		MetaSearchImpl		meta_search,
		Map					map )

		throws IOException
	{
		return( new RegexEngine( meta_search, map ));
	}

	public static Engine
	importFromJSONString(
		MetaSearchImpl		meta_search,
		long				id,
		long				last_updated,
		float				rank_bias,
		String				name,
		JSONObject			map )

		throws IOException
	{
		return( new RegexEngine( meta_search, id, last_updated, rank_bias, name, map ));
	}

	private String		pattern_str;
	Pattern[] 	patterns = {};


		// explicit test constructor

	public
	RegexEngine(
		MetaSearchImpl		meta_search,
		long 				id,
		long 				last_updated,
		float				rank_bias,
		String 				name,
		String 				searchURLFormat,
		String 				resultPattern,
		String 				timeZone,
		boolean 			automaticDateFormat,
		String 				userDateFormat,
		FieldMapping[] 		mappings,
		boolean				needs_auth,
		String				auth_method,
		String				login_url,
		String[]			required_cookies )
	{
		super( 	meta_search,
				Engine.ENGINE_TYPE_REGEX,
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

		init( resultPattern );

		setSource( ENGINE_SOURCE_LOCAL );

		setSelectionState( SEL_STATE_MANUAL_SELECTED );
	}

		// bencoded

	protected
	RegexEngine(
		MetaSearchImpl		meta_search,
		Map					map )

		throws IOException
	{
		super( meta_search, map );

		String	resultPattern = MapUtils.getMapString( map, "regex.pattern", null );

		init( resultPattern );
	}

		// json

	protected
	RegexEngine(
		MetaSearchImpl		meta_search,
		long				id,
		long				last_updated,
		float				rank_bias,
		String				name,
		JSONObject			map )

		throws IOException
	{
		super( meta_search, Engine.ENGINE_TYPE_REGEX, id, last_updated, rank_bias, name, map );

		String	resultPattern = MapUtils.getMapString( map, "regexp", null );

		resultPattern = URLDecoder.decode( resultPattern, "UTF-8" );

		init( resultPattern );
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

		MapUtils.setMapString( res, "regex.pattern", pattern_str );

		super.exportToBencodedMap( res, generic );

		return( res );
	}

	@Override
	protected void
	exportToJSONObject(
		JSONObject		res )

		throws IOException
	{
		res.put( "regexp", UrlUtils.encode( pattern_str ));

		super.exportToJSONObject( res );
	}

	protected void
	init(
		String			resultPattern )
	{
		pattern_str 	= resultPattern.trim();
		if (pattern_str.length() == 0) {
			patterns = new Pattern[0];
		} else {
  		patterns = new Pattern[]{

  			Pattern.compile( pattern_str),
  			Pattern.compile( pattern_str, Pattern.DOTALL | Pattern.MULTILINE )
  		};
		}
	}

	@Override
	protected Result[]
	searchSupport(
		final SearchParameter[] 	searchParameters,
		Map							searchContext,
		final int					desired_max_matches,
		final int					o_absolute_max_matches,
		final String				headers,
		final ResultListener		listener )

		throws SearchException
	{
		debugStart();

		final pageDetails page_details = getWebPageContent( searchParameters, searchContext, headers, false );

		final String	page = page_details.getContent();

		if ( listener != null ){

			listener.contentReceived( this, page );
		}

		debugLog( "pattern: " + pattern_str );

		/*
		if ( getId() == 3 ){

			writeToFile( "C:\\temp\\template.txt", page );
			writeToFile( "C:\\temp\\pattern.txt", pattern.pattern());

			String page2 = readFile( "C:\\temp\\template.txt" );

			Set s1 = new HashSet();
			Set s2 = new HashSet();

			for (int i=0;i<page.length();i++){
				s1.add( new Character( page.charAt(i)));
			}
			for (int i=0;i<page2.length();i++){
				s2.add( new Character( page2.charAt(i)));
			}

			s1.removeAll(s2);

			Iterator it = s1.iterator();

			while( it.hasNext()){

				Character c = (Character)it.next();

				System.out.println( "diff: " + c + "/" + (int)c.charValue());
			}

		}

		try{
			regexptest();
		}catch( Throwable e ){

		}
		 */

		try{
			TimeLimitedTask task = new TimeLimitedTask(
				"MetaSearch:regexpr",
				30*1000,
				Thread.NORM_PRIORITY - 1,
				new TimeLimitedTask.task()
				{
					@Override
					public Object
					run()

						throws Exception
					{
						int	max_matches = o_absolute_max_matches;

						if ( max_matches < 0 || max_matches > 1024 ){

							max_matches = 1024;
						}

						String searchQuery = null;

						for(int i = 0 ; i < searchParameters.length ; i++) {
							if(searchParameters[i].getMatchPattern().equals("s")) {
								searchQuery = searchParameters[i].getValue();
							}
						}


						FieldMapping[] mappings = getMappings();

						try{
							List<WebResult> results = new ArrayList<>();

							for ( int pat_num=0;pat_num<patterns.length;pat_num++){

									// only try subsequent patterns if all previous have failed to
									// find results

								if ( results.size() > 0 ){

									break;
								}

								Pattern pattern = patterns[pat_num];

								Matcher m = pattern.matcher( page );

								while( m.find()){

									if ( max_matches >= 0 ){
										if ( --max_matches < 0 ){
											break;
										}
									}


									String[]	groups = new String[m.groupCount()];

									for (int i=0;i<groups.length;i++){

										groups[i] = m.group(i+1);
									}


									if ( listener != null ){

										listener.matchFound( RegexEngine.this, groups );
									}

									debugLog( "Found match:" );

									WebResult result = new WebResult(RegexEngine.this,getRootPage(),getBasePage(),getDateParser(),searchQuery);

									int	fields_matched = 0;

									for(int i = 0 ; i < mappings.length ; i++) {
										String fieldFrom = mappings[i].getName();

										String fieldContent = null;
										Matcher matcher = patternVariable.matcher(fieldFrom);
										if (matcher.find()) {
											fieldContent = fieldFrom;
											do {
												String key = matcher.group();
												key = key.substring(2, key.length() - 1);
												String[] keys = key.split(",", -1);
												try {
													int groupNo = Integer.parseInt(keys[0]);

													// Default: Replace ${1} with groups[0]
													String replaceWith = groups[groupNo-1];

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
																				simpleReplace, Matcher.quoteReplacement(
																						simpleReplacement));
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
											try {
												int groupNo = Integer.parseInt(fieldFrom);
												fieldContent = groups[groupNo-1];
											} catch(Exception e) {
												//In "Debug/Test" mode, we should fire an exception / notification
											}
										}

										if (fieldContent != null) {

											Pattern filter = mappings[i].getPostFilterPattern(searchQuery);
											if (filter != null) {
												Matcher postMatch = filter.matcher(fieldContent);
												if (!postMatch.find()) {
													fields_matched = 0; // 0 so it won't be added
													continue;
												}
											}

											int fieldTo = mappings[i].getField();

											debugLog( "    " + fieldTo + "=" + fieldContent );

											fields_matched++;

											switch(fieldTo) {
												case FIELD_NAME :
													result.setNameFromHTML(fieldContent);
													break;
												case FIELD_SIZE :
													result.setSizeFromHTML(fieldContent, 0);
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
												case FIELD_COMMENTS :
													result.setCommentsFromHTML(fieldContent);
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
												default:
													fields_matched--;
													break;
											}
										}
									}

										// ignore "matches" that don't actually populate any fields

									if ( fields_matched > 0 ){

										if (result.getHash() == null) {
											String downloadLink = result.getDownloadLink();
											String possibleMagnet = UrlUtils.parseTextForMagnets(downloadLink);
											byte[] hash = UrlUtils.getTruncatedHashFromMagnetURI(possibleMagnet);
											if (hash != null) {
												result.setHash(ByteFormatter.nicePrint(hash, true));
											}
										}

										results.add(result);
									}
								}
							}

								// hack - if no results and redirected to https and auth required then
								// assume we need to log in...

							if ( results.size() == 0 && isNeedsAuth()){

								if ( 	page_details.getInitialURL().getProtocol().equalsIgnoreCase( "http" ) &&
										page_details.getFinalURL().getProtocol().equalsIgnoreCase( "https" )){

									throw new SearchLoginException("login possibly required");
								}
							}

							return (Result[]) results.toArray(new Result[results.size()]);

						}catch (Throwable e){

							log( "Failed process result", e );

							if ( e instanceof SearchException ){

								throw((SearchException)e );
							}

							throw new SearchException(e);
						}
					}
				});

			Result[] res = (Result[])task.run();

			debugLog( "success: found " + res.length + " results" );

			return( res );

		}catch( Throwable e ){

			debugLog( "failed: " + Debug.getNestedExceptionMessageAndStack( e ));

			if ( e instanceof SearchException ){

				throw((SearchException)e );
			}

			throw( new SearchException( "Regex matching failed", e ));
		}
	}

	/*
	protected void
	writeToFile(
		String		file,
		String		str )
	{
		try{
			PrintWriter pw = new PrintWriter( new FileWriter( new File( file )));

			pw.println( str );

			pw.close();

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}

	private static String
	readFile(
		String	file )
	{
		try{
			StringBuffer sb = new StringBuffer();

			LineNumberReader lnr = new LineNumberReader( new FileReader( new File( file )));

			while( true ){

				String 	line = lnr.readLine();

				if ( line == null ){

					break;
				}

				sb.append( line );
			}

			return( sb.toString());

		}catch( Throwable e ){

			e.printStackTrace();

			return( null );
		}
	}

	private static void
	regexptest()

		throws Exception
	{
		Pattern pattern = Pattern.compile( readFile( "C:\\temp\\pattern.txt" ));

		String	page = readFile( "C:\\temp\\template.txt" );

		Matcher m = pattern.matcher( page);

		while(m.find()) {

			int groups = m.groupCount();

			System.out.println( "found match: groups = " + groups );
		}
	}
	*/
}
