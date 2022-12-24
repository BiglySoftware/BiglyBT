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

package com.biglybt.core.metasearch.impl.web;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.biglybt.util.MapUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.metasearch.Result;
import com.biglybt.core.metasearch.SearchException;
import com.biglybt.core.metasearch.SearchLoginException;
import com.biglybt.core.metasearch.SearchParameter;
import com.biglybt.core.metasearch.impl.DateParser;
import com.biglybt.core.metasearch.impl.DateParserRegex;
import com.biglybt.core.metasearch.impl.EngineImpl;
import com.biglybt.core.metasearch.impl.MetaSearchImpl;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.proxy.AEProxyFactory.PluginProxy;
import com.biglybt.core.util.*;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.core.vuzefile.VuzeFileComponent;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.pif.utils.StaticUtilities;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderFactory;
import com.biglybt.util.UrlFilter;

public abstract class
WebEngine
	extends EngineImpl
{
	public static final String	AM_TRANSPARENT 	= "transparent";
	public static final String	AM_PROXY		= "proxy";

	private static final boolean NEEDS_AUTH_DEFAULT				= false;
	private static final boolean AUTOMATIC_DATE_PARSER_DEFAULT 	= true;

	static private final Pattern baseTagPattern = Pattern.compile("(?i)<base.*?href=\"([^\"]+)\".*?>");
	static private final Pattern rootURLPattern = Pattern.compile("((?:tor:)?https?://[^/]+)");
	static private final Pattern baseURLPattern = Pattern.compile("((?:tor:)?https?://.*/)");

	static private int search_timeout_secs;
	
	static{
		COConfigurationManager.addAndFireParameterListener(
				"search.rss.template.timeout",
				(name)->{ search_timeout_secs = COConfigurationManager.getIntParameter( name); });
	}

	private String 			searchURLFormat;
	private String 			timeZone;
	private boolean			automaticDateParser;
	private String 			userDateFormat;
	private String			downloadLinkCSS;
	private FieldMapping[]	mappings;


	private String rootPage;
	private String basePage;

	private DateParser dateParser;

	private boolean needsAuth;
	private String	authMethod;
	private String loginPageUrl;
	private String iconUrl;
	private String[] requiredCookies;

	private String fullCookies;
	private String local_cookies;


		// manual test constructor

	public
	WebEngine(
		MetaSearchImpl	meta_search,
		int 			type,
		long 			id,
		long 			last_updated,
		float			rank_bias,
		String 			name,
		String 			searchURLFormat,
		String 			timeZone,
		boolean 		automaticDateParser,
		String 			userDateFormat,
		FieldMapping[] 	mappings,
		boolean			needs_auth,
		String			auth_method,
		String			login_url,
		String[]		required_cookies )
	{
		super( meta_search, type, id, last_updated, rank_bias, name );

		this.searchURLFormat 		= searchURLFormat;
		this.timeZone 				= timeZone;
		this.automaticDateParser 	= automaticDateParser;
		this.userDateFormat 		= userDateFormat;
		this.mappings				= mappings;
		this.needsAuth				= needs_auth;
		this.authMethod				= auth_method;
		this.loginPageUrl			= login_url;
		this.requiredCookies		= required_cookies;

		init();
	}

		// bencoded constructor

	protected
	WebEngine(
		MetaSearchImpl	meta_search,
		Map				map )

		throws IOException
	{
		super( meta_search, map );

		searchURLFormat 	= MapUtils.getMapString( map, "web.search_url_format", null );
		timeZone			= MapUtils.getMapString( map, "web.time_zone", null );
		userDateFormat		= MapUtils.getMapString( map, "web.date_format", null );
		downloadLinkCSS		= MapUtils.getMapString( map, "web.dl_link_css", null );

		needsAuth			= MapUtils.getMapBoolean(map, "web.needs_auth", NEEDS_AUTH_DEFAULT );
		authMethod			= MapUtils.getMapString( map, "web.auth_method", WebEngine.AM_TRANSPARENT );
		loginPageUrl 		= MapUtils.getMapString( map, "web.login_page", null );
		requiredCookies 	= MapUtils.importStringArray( map, "web.required_cookies" );
		fullCookies 	= MapUtils.getMapString( map, "web.full_cookies", null );

		automaticDateParser	= MapUtils.getMapBoolean( map, "web.auto_date", AUTOMATIC_DATE_PARSER_DEFAULT );
		iconUrl 		= MapUtils.getMapString( map, "web.icon_url", null );

		List	maps = (List)map.get( "web.maps" );

		mappings = new FieldMapping[maps.size()];

		for (int i=0;i<mappings.length;i++){

			Map	m = (Map)maps.get(i);

			mappings[i] =
				new FieldMapping(
						MapUtils.getMapString( m, "name", null ),
					((Long)m.get( "field")).intValue(),
						MapUtils.getMapString(m, "post_filter", null));
		}

		init();
	}

	@Override
	protected void
	exportToBencodedMap(
		Map		map,
		boolean	generic )

		throws IOException
	{
		super.exportToBencodedMap( map, generic );

		if ( generic ){

			if ( searchURLFormat != null ){
				MapUtils.setMapString( map, "web.search_url_format", 		searchURLFormat );
			}
			if ( timeZone != null ){
				MapUtils.setMapString( map, "web.time_zone", 				timeZone );
			}
			if ( userDateFormat != null ){
				MapUtils.setMapString( map, "web.date_format", 			userDateFormat );
			}
			if ( downloadLinkCSS != null ){
				MapUtils.setMapString( map, "web.dl_link_css",				downloadLinkCSS );
			}

			if ( needsAuth != NEEDS_AUTH_DEFAULT ){
				MapUtils.exportBooleanAsLong( map, "web.needs_auth",				needsAuth );
			}
			if ( authMethod != null && !authMethod.equals( WebEngine.AM_TRANSPARENT )){
				MapUtils.setMapString( map, "web.auth_method",				authMethod );
			}
			if ( loginPageUrl != null ){
				MapUtils.setMapString( map, "web.login_page",				loginPageUrl );
			}
			if ( iconUrl != null ) {
				MapUtils.setMapString(map, "web.icon_url", iconUrl);
			}
			if ( requiredCookies != null && requiredCookies.length > 0 ){
				MapUtils.exportStringArray( map, "web.required_cookies",	requiredCookies );
			}
			if (automaticDateParser != AUTOMATIC_DATE_PARSER_DEFAULT ){
				MapUtils.exportBooleanAsLong( map, "web.auto_date", automaticDateParser );
			}

		}else{
			MapUtils.setMapString( map, "web.search_url_format", 		searchURLFormat );
			MapUtils.setMapString( map, "web.time_zone", 				timeZone );
			MapUtils.setMapString( map, "web.date_format", 			userDateFormat );
			MapUtils.setMapString( map, "web.dl_link_css",				downloadLinkCSS );

			MapUtils.exportBooleanAsLong( map, "web.needs_auth",				needsAuth );
			MapUtils.setMapString( map, "web.auth_method",				authMethod );
			MapUtils.setMapString( map, "web.login_page",				loginPageUrl );
			MapUtils.setMapString( map, "web.icon_url", iconUrl);
			MapUtils.exportStringArray( map, "web.required_cookies",	requiredCookies );
			map.put("web.full_cookies",				fullCookies );

			MapUtils.exportBooleanAsLong( map, "web.auto_date", automaticDateParser );
		}

		List	maps = new ArrayList();

		map.put( "web.maps", maps );

		for (int i=0;i<mappings.length;i++){

			FieldMapping fm = mappings[i];

			Map m = new HashMap();

			MapUtils.setMapString( m, "name", fm.getName());
			m.put( "field", new Long( fm.getField()));
			MapUtils.setMapString(m, "post_filter", fm.getPostFilter());

			maps.add( m );
		}
	}

		// json encoded constructor

	protected
	WebEngine(
		MetaSearchImpl	meta_search,
		int				type,
		long			id,
		long			last_updated,
		float			rank_bias,
		String			name,
		JSONObject		map )

		throws IOException
	{
		super( meta_search, type, id, last_updated, rank_bias, name, map );

		searchURLFormat 	= MapUtils.importURL( map, "searchURL" );
		timeZone			= MapUtils.getMapString( map, "timezone", null );
		userDateFormat		= MapUtils.getMapString( map, "time_format", null );
		downloadLinkCSS		= MapUtils.importURL( map, "download_link" );


		needsAuth			= MapUtils.getMapBoolean( map, "needs_auth", false );
		authMethod			= MapUtils.getMapString( map, "auth_method", WebEngine.AM_TRANSPARENT );
		loginPageUrl 		= MapUtils.importURL( map, "login_page" );
		iconUrl 		= MapUtils.importURL( map, "icon_url" );

		requiredCookies 	= MapUtils.importStringArray( map, "required_cookies" );

		fullCookies 	= MapUtils.getMapString( map, "full_cookies", null );

		automaticDateParser	= userDateFormat == null || userDateFormat.trim().length() == 0;

		List	maps = (List)map.get( "column_map" );

		List	conv_maps = new ArrayList();

		for (int i=0;i<maps.size();i++){

			Map	m = (Map)maps.get(i);

				// wha? getting some nulls here :(
				// from JSON like "column_map\":[null,null,{\"group_nb\":\"3

			if ( m == null ){

				continue;
			}

				// backwards compact from when there was a mapping entry

			Map test = (Map)m.get( "mapping" );

			if ( test != null ){

				m = test;
			}

			String	vuze_field 	= MapUtils.getMapString( m, "vuze_field", null ).toUpperCase();

			String	field_name	= MapUtils.getMapString( m, "group_nb", null );	// regexp case

			if ( field_name == null ){

				field_name = MapUtils.getMapString( m, "field_name", null );	// json case
			}

			if ( vuze_field == null || field_name == null ){

				log( "Missing field mapping name/value in '" + m + "'" );

			}
			int	field_id = vuzeFieldToID( vuze_field );

			if ( field_id == -1 ){

				log( "Unrecognised field mapping '" + vuze_field + "'" );

				continue;
			}

			conv_maps.add( new FieldMapping( field_name, field_id, MapUtils.getMapString(m, "post_filter", null) ));
		}

		mappings = (FieldMapping[])conv_maps.toArray( new FieldMapping[conv_maps.size()]);

		init();
	}

	@Override
	protected void
	exportToJSONObject(
		JSONObject		res )

		throws IOException
	{
		super.exportToJSONObject( res );

		MapUtils.exportJSONURL( res, "searchURL", searchURLFormat );

		if (timeZone != null) {
			res.put( "timezone", timeZone );
		}

		if ( downloadLinkCSS != null ){

			MapUtils.exportJSONURL( res, "download_link", downloadLinkCSS );
		}

		res.put( "needs_auth",				needsAuth );
		if (authMethod != null) {
			res.put( "auth_method",  authMethod );
		}
		MapUtils.exportJSONURL( res, "login_page",					loginPageUrl );
		MapUtils.exportJSONURL( res, "icon_url",					iconUrl );
		MapUtils.exportJSONStringArray( res, "required_cookies",	requiredCookies );
		if (fullCookies != null) {
			res.put( "full_cookies", fullCookies );
		}

		if ( !automaticDateParser && userDateFormat != null ){

			res.put( "time_format",	userDateFormat );
		}

		JSONArray	maps = new JSONArray();

		res.put( "column_map", maps );

		for (int i=0;i<mappings.length;i++){

			FieldMapping fm = mappings[i];

			int	field_id = fm.getField();

			String	field_value = vuzeIDToField( field_id );

			if ( field_value == null ){

				log( "JSON export: unknown field id " + field_id );

			}else{

				JSONObject entry = new JSONObject();

				maps.add( entry );

				entry.put( "vuze_field", field_value );

				if ( getType() == ENGINE_TYPE_JSON ){

					entry.put( "field_name", fm.getName());

				}else{

					entry.put( "group_nb", fm.getName());
				}

				String filter = fm.getPostFilter();
				if (filter != null) {
					entry.put("post_filter", filter);
				}
			}
		}
	}

	protected void
	init()
	{
		try {
			Matcher m = rootURLPattern.matcher(searchURLFormat);
			if(m.find()) {
				this.rootPage = m.group(1);
			}
		} catch(Exception e) {
			//Didn't find the root url within the URL
			this.rootPage = null;
		}

		try {
			Matcher m = baseURLPattern.matcher(searchURLFormat);
			if(m.find()) {
				this.basePage = m.group(1);
			}
		} catch(Exception e) {
			//Didn't find the root url within the URL
			this.basePage = null;
		}

		this.dateParser = new DateParserRegex(timeZone,automaticDateParser,userDateFormat);

		local_cookies = getLocalString( LD_COOKIES );

			// normalise to permit == to be safely used when testing method

		authMethod = authMethod.intern();

			// see if we have explicit cookie information in the URL:

		int	cook_pos = searchURLFormat.indexOf( ":COOKIE:" );

		if ( cook_pos != -1 ){

			String explicit_cookie = searchURLFormat.substring( cook_pos + 8 );

			setNeedsAuth( true );

			setCookies( explicit_cookie );

			setRequiredCookies( CookieParser.getCookiesNames( explicit_cookie ));

			searchURLFormat = searchURLFormat.substring( 0, cook_pos );

			setPublic( false );

			String	name = getName();

			int	n_pos = name.indexOf( ":COOKIE:" );

			if ( n_pos != -1 ){

				setName( name.substring( 0, n_pos ));
			}
		}
	}

	@Override
	public String
	getNameEx()
	{
		String url = getRootPage();

		if ( url == null || url.length() == 0 ){

			url = searchURLFormat;
		}

		String name = getName();

		if (!name.contains(url)){

			return( name + " (" + url + ")");

		}else{

			return( name );
		}

	}

	@Override
	public String
	getReferer()
	{
		return( getRootPage());
	}

	@Override
	public boolean
	supportsContext(
		String	context_key )
	{
		try{
			URL	url = new URL( searchURLFormat );

			String	host = url.getHost();

			if ( com.biglybt.core.util.Constants.isAppDomain( host )){

				return( true );
			}

			if ( UrlFilter.getInstance().isWhitelisted( searchURLFormat )){

				return( true );
			}


				// allow local addresses for testing purposes

			InetAddress iad = AddressUtils.getByName(host);

			if ( 	iad.isLoopbackAddress() ||
					iad.isLinkLocalAddress() ||
					iad.isSiteLocalAddress()){

				return( true );
			}
		}catch( Throwable e ){
		}

		return( false );
	}

	@Override
	public boolean
	isShareable()
	{
		try{
			return( !UrlUtils.containsPasskey( new URL( searchURLFormat )));

		}catch( Throwable e ){

			return( true );
		}
	}

	@Override
	public boolean
	isAnonymous()
	{
		try{
			return( AENetworkClassifier.categoriseAddress( new URL( searchURLFormat ).getHost()) != AENetworkClassifier.AT_PUBLIC );

		}catch( Throwable e ){

			return( false );
		}
	}

	protected pageDetails
	getWebPageContent(
		SearchParameter[] 	searchParameters,
		Map<String,String>	searchContext,
		String				headers,
		boolean				only_if_modified )

		throws SearchException
	{
		return( getWebPageContent( searchParameters, searchContext, headers, only_if_modified, null ));
	}

	protected pageDetails
	getWebPageContent(
		SearchParameter[] 		searchParameters,
		Map<String,String>		searchContext,
		String					headers,
		boolean					only_if_modified,
		pageDetailsVerifier		verifier )

		throws SearchException
	{
		if ( NetworkAdmin.getSingleton().hasMissingForcedBind()){

			throw( new SearchException( "Forced bind address is missing" ));
		}
		
		String searchURL = searchURLFormat;

		String lc_url = searchURL.toLowerCase( Locale.US );

		boolean explicit_tor = lc_url.startsWith( "tor:" );

		boolean user_tor = false;

		if ( !explicit_tor ){

			String test = Result.adjustLink( searchURL );

			if ( test.startsWith( "tor:" )){

				user_tor = true;
			}
		}
		boolean explicit_i2p = lc_url.startsWith( "i2p:" );

		boolean user_i2p = false;

		if ( !explicit_i2p ){

			String test = Result.adjustLink( searchURL );

			if ( test.startsWith( "i2p:" )){

				user_i2p = true;
			}
		}

		if ( explicit_tor || user_tor || explicit_i2p || user_i2p ){

				// strip out any stuff we probably don't want to send

			searchContext = new HashMap<>();

			String target_resource = (explicit_tor||explicit_i2p)?searchURL.substring( 4 ):searchURL;

			URL location;

			try{

				location = new URL( target_resource );

			}catch( MalformedURLException e ){

				throw( new SearchException( e ));
			}

			Map<String,Object>	options = new HashMap<>();

			options.put( AEProxyFactory.PO_PEER_NETWORKS, new String[]{ (explicit_tor || user_tor)?AENetworkClassifier.AT_TOR:AENetworkClassifier.AT_I2P });

			if ( explicit_i2p || user_i2p ){
				
				options.put( AEProxyFactory.PO_PREFERRED_PROXY_TYPE, "HTTP" );
				options.put( AEProxyFactory.PO_FORCE_PROXY, true );
			}
			
			PluginProxy plugin_proxy =
				AEProxyFactory.getPluginProxy(
					"Web engine download of '" + target_resource + "'",
					location,
					options,
					true );

			if ( plugin_proxy == null ){

				throw( new SearchException( "No Tor plugin proxy available for '" + target_resource + "'" ));
			}

			URL 	url		= plugin_proxy.getURL();
			Proxy 	proxy	= plugin_proxy.getProxy();

			boolean	ok = false;

			try{
				String proxy_host = location.getHost() + (location.getPort()==-1?"":(":" + location.getPort()));

				pageDetails	details = getWebPageContentSupport( proxy, proxy_host, url.toExternalForm(), searchParameters, searchContext, headers, only_if_modified );

				if ( verifier != null ){

					verifier.verify( details );
				}

				ok = true;

				return( details );

			}finally{

				plugin_proxy.setOK( ok );
			}
		}

		try{
			try{
				URL url = new URL( searchURL );

				if ( AENetworkClassifier.categoriseAddress( url.getHost()) != AENetworkClassifier.AT_PUBLIC ){

					// strip out any stuff we probably don't want to send

					searchContext = new HashMap<>();
				}
			}catch( Throwable e ){
			}

			pageDetails	details = getWebPageContentSupport( null, null, searchURL, searchParameters, searchContext, headers, only_if_modified );

			if ( verifier != null ){

				verifier.verify( details );
			}

			return( details );

		}catch( SearchException e ){

			boolean	pp_connected = false;
			
			try{
				URL 			original_url	= new URL( searchURL );

				Map<String,Object>	options = new HashMap<>();
				
				options.put( AEProxyFactory.PO_PREFERRED_PROXY_TYPE, "HTTP" );
				
				PluginProxy 	plugin_proxy	= AEProxyFactory.getPluginProxy( "getting search results ", original_url, options );

				if ( plugin_proxy == null ){

					throw( e );

				}else{

					URL 	url		= plugin_proxy.getURL();
					Proxy 	proxy	= plugin_proxy.getProxy();

					boolean	ok = false;

					try{
						String proxy_host = original_url.getHost() + (original_url.getPort()==-1?"":(":" + original_url.getPort()));

						pageDetails	details = getWebPageContentSupport( proxy, proxy_host, url.toExternalForm(), searchParameters, searchContext, headers, only_if_modified );

						if ( verifier != null ){

							verifier.verify( details );
						}

						ok = true;

						return( details );

					}finally{

						if ( !ok ){
							
							Throwable error = plugin_proxy.getError();
							
							if ( error != null ){
								
								e = new SearchException( error );
								
							}else if ( plugin_proxy.getConnected()){
								
								pp_connected = true;
							}
						}
						
						plugin_proxy.setOK( ok );
					}
				}
			}catch( SearchException f ){

				throw( pp_connected?f:e );
				
			}catch( Throwable f ){
				
				throw( e );
			}
		}
	}

	private pageDetails
	getWebPageContentSupport(
		Proxy				proxy,
		String				proxy_host,
		String				searchURL,
		SearchParameter[] 	searchParameters,
		Map<String,String>	searchContext,
		String				headers,
		boolean				only_if_modified )

		throws SearchException
	{

		try {
			TorrentUtils.setTLSDescription( "Search: " + getName());

			if ( requiresLogin()){

				throw new SearchLoginException("login required");
			}

			boolean vuze_file = searchURL.toLowerCase().startsWith( "vuze:" );

			if ( !vuze_file ){

				String[]	from_strs 	= new String[ searchParameters.length ];
				String[]	to_strs 	= new String[ searchParameters.length ];

				for( int i = 0 ; i < searchParameters.length ; i++ ){

					SearchParameter parameter = searchParameters[i];

					from_strs[i]	= "%" + parameter.getMatchPattern();
					to_strs[i]		= URLEncoder.encode(parameter.getValue(),"UTF-8");
				}

				searchURL = GeneralUtils.replaceAll( searchURL, from_strs, to_strs );

				Iterator<Map.Entry<String, String>>	it = searchContext.entrySet().iterator();

				while( it.hasNext()){

					Map.Entry<String, String>	entry = it.next();

					String	key 	= entry.getKey();

					if ( supportsContext( key )){

						if ( searchURL.indexOf('?') == -1 ){

							searchURL += "?";

						}else{

							searchURL += "&";
						}

						String	value 	= entry.getValue();

						searchURL += key + "=" + URLEncoder.encode( value, "UTF-8" );
					}
				}
			}

			//System.out.println(searchURL);


				// hack to support POST by encoding into URL

				// http://xxxx/index.php?main=search&azmethod=post_basic:SearchString1=%s&SearchString=&search=Search

			ResourceDownloaderFactory rdf = StaticUtilities.getResourceDownloaderFactory();

			URL					initial_url;
			ResourceDownloader 	initial_url_rd;


			int	post_pos = searchURL.indexOf( "azmethod=" );

			if ( post_pos > 0 ){

				String post_params = searchURL.substring( post_pos+9 );

				searchURL = searchURL.substring( 0, post_pos-1 );

				debugLog( "search_url: " + searchURL + ", post=" + post_params );

				initial_url = new URL(searchURL);

				int	sep = post_params.indexOf( ':' );

				String	type = post_params.substring( 0, sep );

				if ( !type.equals( "post_basic" )){

					throw( new SearchException( "Only basic type supported" ));
				}

				post_params = post_params.substring( sep+1 );

					// already URL encoded

				if ( proxy == null ){

					initial_url_rd = rdf.create( initial_url, post_params );

				}else{

					initial_url_rd = rdf.create( initial_url, post_params, proxy );
				}

				initial_url_rd.setProperty( "URL_Content-Type", "application/x-www-form-urlencoded" );

			}else{

				debugLog( "search_url: " + searchURL );

				initial_url = new URL(searchURL);

				if ( proxy == null ){

					initial_url_rd = rdf.create( initial_url );

				}else{

					initial_url_rd = rdf.create( initial_url, proxy );
				}
			}

			if ( proxy_host != null ){

				initial_url_rd.setProperty( "URL_HOST", proxy_host );
			}

			setHeaders( initial_url_rd, headers );

			if ( needsAuth && local_cookies != null ){

				initial_url_rd.setProperty( "URL_Cookie", local_cookies );
			} else if (fullCookies != null && fullCookies.length() > 0) {
				initial_url_rd.setProperty( "URL_Cookie", fullCookies );
			}


			if ( only_if_modified ){

				String last_modified 	= getLocalString( LD_LAST_MODIFIED );
				String etag				= getLocalString( LD_ETAG );

				if ( last_modified != null ){

					initial_url_rd.setProperty( "URL_If-Modified-Since", last_modified );
				}

				if ( etag != null ){

					initial_url_rd.setProperty( "URL_If-None-Match", etag );
				}
			}

			InputStream	is = null;

			try{
				String content_charset = "UTF-8";

				ResourceDownloader mr_rd = null;

				if ( initial_url.getProtocol().equalsIgnoreCase( "file" )){

						// handle file://c:/ - map to file:/c:/

					String	str = initial_url.toExternalForm();

					if ( initial_url.getAuthority() != null ){

						str = str.replaceFirst( "://", ":/" );
					}

					int	pos = str.indexOf( '?' );

					if ( pos != -1 ){

						str = str.substring( 0, pos );
					}

					is = FileUtil.newFileInputStream( FileUtil.newFile( new URL( str ).toURI()));

				}else{

					if ( proxy == null ){

						initial_url_rd.setProperty( "URL_Connect_Timeout", 10*1000 );

						
						initial_url_rd.setProperty( "URL_Read_Timeout", search_timeout_secs * 1000 );
					}

					mr_rd = rdf.getMetaRefreshDownloader( initial_url_rd );

					try{

						is = mr_rd.download();

					}catch( ResourceDownloaderException e ){

						Long	response = (Long)mr_rd.getProperty( "URL_HTTP_Response" );

						if ( response != null && response.longValue() == 304 ){

								// not modified

							return( new pageDetails( initial_url, initial_url, "" ));

						}else{
							
							throw( e );
						}
					}


					if ( needsAuth ){

						List	cookies_list = (List)mr_rd.getProperty( "URL_Set-Cookie" );

						List	cookies_set = new ArrayList();

						if ( cookies_list != null ){

							for (int i=0;i<cookies_list.size();i++){

								String[] cookies = ((String)cookies_list.get(i)).split(";");

								for (int j=0;j<cookies.length;j++){

									String	cookie = cookies[j].trim();

									if ( cookie.indexOf('=') != -1 ){

										cookies_set.add( cookie );
									}
								}
							}
						}

							// well, not much we can do with the cookies anyway as in general the ones
							// set are the ones missing/expired, not the existing ones. That is, we can't
							// deduce anything from the fact that a required cookie is not 'set' here
							// the most we could do is catch a server that explicitly deleted invalid
							// cookies by expiring it, but I doubt this is a common practice.

							// Also note the complexity of cookie syntax
							// Set-Cookie: old standard using expires=, new using MaxAge
							// Set-Cookie2:
							// Maybe use http://jcookie.sourceforge.net/ if needed
					}

					if ( only_if_modified ){

						String last_modified 	= extractProperty( mr_rd.getProperty( "URL_Last-Modified" ));
						String etag				= extractProperty( mr_rd.getProperty( "URL_ETag" ));

						if ( last_modified != null ){

							setLocalString( LD_LAST_MODIFIED, last_modified );
						}

						if ( etag != null ){

							setLocalString( LD_ETAG, etag );
						}
					}

					List cts = (List)mr_rd.getProperty( "URL_Content-Type" );

					if ( cts != null && cts.size() > 0 ){

						String	content_type = (String)cts.get(0);

						int	pos = content_type.toLowerCase().indexOf( "charset" );

						if ( pos != -1 ){

							content_type = content_type.substring( pos+1 );

							pos = content_type.indexOf('=');

							if ( pos != -1 ){

								content_type = content_type.substring( pos+1 ).trim();

								pos = content_type.indexOf(';');

								if ( pos != -1 ){

									content_type = content_type.substring(0,pos).trim();
								}

								if ( content_type.startsWith("\"" )){

									content_type = content_type.substring(1).trim();
								}

								if ( content_type.endsWith("\"" )){

									content_type = content_type.substring(0,content_type.length()-1).trim();
								}

								try{
									if ( Charset.isSupported( content_type )){

										debugLog( "charset: " + content_type );

										content_charset = content_type;
									}
								}catch( Throwable e ){

									try{
											// handle lowercase 'utf-8' for example

										content_type = content_type.toUpperCase();

										if ( Charset.isSupported( content_type )){

											debugLog( "charset: " + content_type );

											content_charset = content_type;
										}
									}catch( Throwable f ){

										log( "Content type '" + content_type + "' not supported", f );
									}
								}
							}
						}
					}
				}

				ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);

				byte[] buffer = new byte[8192];

				while( true ){

					int	len = is.read( buffer );

					if ( len <= 0 ){

						break;
					}

					baos.write( buffer, 0, len );
				}

				byte[] data = baos.toByteArray();

				if ( vuze_file ){

					try{
						VuzeFileHandler vfh = VuzeFileHandler.getSingleton();

						VuzeFile vf = vfh.loadVuzeFile( data );

						vfh.handleFiles( new VuzeFile[]{ vf }, VuzeFileComponent.COMP_TYPE_NONE );

					}catch( Throwable e ){

						Debug.out( e );
					}

					return( new pageDetails( initial_url, initial_url, null ));
				}

				String 	page = null;

				String	content = new String( data, 0, Math.min( data.length, 2048 ), content_charset );

				String	lc_content = content.toLowerCase();

				{
						// first look for xml charset

						// e.g. <?xml version="1.0" encoding="windows-1251" ?>

					int	pos1 = lc_content.indexOf( "<?xml" );

					if ( pos1 != -1 ){

						int pos2 = lc_content.indexOf( "?>" );

						if ( pos2 != -1 ){

							int pos3 = lc_content.indexOf( "encoding", pos1 );

							if ( pos3 != -1 ){

								pos3 = lc_content.indexOf( "\"", pos3 );
							}

							if ( pos3 > pos1 && pos3 < pos2 ){

								pos3++;

								int pos4 = lc_content.indexOf( "\"", pos3 );

								if ( pos4 > pos3 && pos4 < pos2 ){

									String	encoding = content.substring( pos3, pos4 ).trim();

									try{
										if ( Charset.isSupported( encoding )){

											debugLog( "charset from xml tag: " + encoding );

											content_charset = encoding;

												// some feeds have crap at the start which makes pos2 mismatch for the above '?' - adjust if necessary

											int data_start = pos2;

											int	max_skip	= 64;

											while( data[data_start] != '?' && max_skip-- > 0 ){

												data_start++;
											}

											page = content.substring( 0, pos3 ) + "utf-8" + content.substring( pos4, pos2 ) + new String( data, data_start, data.length - data_start, content_charset );
										}
									}catch( Throwable e ){

										log( "Content type '" + encoding + "' not supported", e );
									}
								}
							}
						}
					}
				}

				if ( page == null ){

						// next look for http-equiv charset

						// e.g. <meta http-equiv="Content-Type" content="text/html; charset=windows-1251" />

					int	pos = 0;

					while( true ){

						int	pos1 = lc_content.indexOf( "http-equiv", pos );

						if ( pos1 != -1 ){

							int	pos2 = lc_content.indexOf( ">", pos1 );

							if ( pos2 != -1 ){

								int	pos3 = lc_content.indexOf( "charset", pos1 );

								if ( pos3 != -1 && pos3 < pos2 ){

									pos3 = lc_content.indexOf( "=", pos3 );

									if ( pos3 != -1 ){

										pos3++;

										int pos4 = lc_content.indexOf( "\"", pos3 );

										if ( pos4 != -1 ){

											int pos5 = lc_content.indexOf( ";", pos3 );

											if ( pos5 != -1 && pos5 < pos4 ){

												pos4 = pos5;
											}

											String encoding = content.substring( pos3, pos4 ).trim();

											try{
												if ( Charset.isSupported( encoding )){

													debugLog( "charset from http-equiv : " + encoding );

													content_charset = encoding;

														// some feeds have crap at the start which makes pos2 mismatch for the above '?' - adjust if necessary

													int data_start = pos2;

													int	max_skip	= 64;

													while( data[data_start] != '?' && max_skip-- > 0 ){

														data_start++;
													}

													page = content.substring( 0, pos3 ) + "utf-8" + content.substring( pos4, pos2 ) + new String( data, data_start, data.length - data_start, content_charset );
												}
											}catch( Throwable e ){

												log( "Content type '" + encoding + "' not supported", e );
											}

											break;
										}
									}
								}

								pos = pos2;

							}else{

								break;
							}
						}else{

							break;
						}
					}
				}

				if ( page == null ){

					page = new String( data, content_charset );
				}

				debugLog( "page:" );
				debugLog( page );

				// List 	cookie = (List)url_rd.getProperty( "URL_Set-Cookie" );

				try {
					Matcher m = baseTagPattern.matcher(page);
					if(m.find()) {
						basePage = m.group(1);

						debugLog( "base_page: " + basePage );
					}
				} catch(Exception e) {
					//No BASE tag in the page
				}

				URL	final_url = initial_url;

				if ( mr_rd != null ){

					URL	x = (URL)mr_rd.getProperty( "URL_URL" );

					if ( x != null ){

						final_url = x;
					}
				}

				return( new pageDetails( initial_url, final_url, page ));

			}finally{

				if ( is != null ){

					is.close();
				}
			}

		}catch( SearchException e ){

			throw( e );

		}catch( Throwable e) {

			if ( e instanceof ResourceDownloaderException && proxy != null ){
				
					
					// exception can contain a load of crap, just use generic one
				
				e = new IOException( "I/O Exception" );
			}
			
			// e.printStackTrace();

			debugLog( "Failed to load page: " + Debug.getNestedExceptionMessageAndStack(e));

			throw( new SearchException( "Failed to load page", e ));

		}finally{

			TorrentUtils.setTLSDescription( null );
		}
	}

	protected String
	extractProperty(
		Object	o )
	{
		if ( o instanceof String ){

			return((String)o);

		}else if ( o instanceof List ){

			List	l = (List)o;

			if ( l.size() > 0 ){

				if ( l.size() > 1 ){

					Debug.out( "Property has multiple values!" );
				}

				Object x = l.get(0);

				if ( x instanceof String ){

					return((String)x);

				}else{

					Debug.out( "Property value isn't a String:" + x );
				}
			}
		}

		return( null );
	}

	protected void
	setHeaders(
		ResourceDownloader		rd,
		String					encoded_headers )
	{
		UrlUtils.setBrowserHeaders( rd, encoded_headers, rootPage );
	}

	@Override
	public String getIcon() {
		if (iconUrl != null) {
			return iconUrl;
		}
		if(rootPage != null) {
			return rootPage + "/favicon.ico";
		}
		return null;
	}

	protected FieldMapping[]
	getMappings()
	{
		return( mappings );
	}

	@Override
	public boolean
	supportsField(
		int		field_id )
	{
		for (int i=0;i<mappings.length;i++){

			if ( mappings[i].getField() == field_id ){

				return( true );
			}
		}

		return( false );
	}

	protected String
	getRootPage()
	{
		return( rootPage );
	}

	protected String
	getBasePage()
	{
		return( basePage );
	}

	protected DateParser
	getDateParser()
	{
		return( dateParser );
	}

	@Override
	public String
	getDownloadLinkCSS()
	{
		if ( downloadLinkCSS == null ){

			return( "" );
		}

		return( downloadLinkCSS );
	}

	public boolean requiresLogin() {
		return needsAuth && ! CookieParser.cookiesContain(requiredCookies, local_cookies);
	}

	public void setCookies(String cookies) {
		this.local_cookies = cookies;

		setLocalString( LD_COOKIES, cookies );
	}

	public String
	getSearchUrl(
		boolean		raw )
	{
		if ( raw ){

			return( searchURLFormat );
		}else{

			return( getSearchUrl());
		}
	}

	public String
	getSearchUrl()
	{
		return( searchURLFormat.replaceAll("%s", ""));
	}

	public void
	setSearchUrl(
		String		str )
	{
		searchURLFormat = str;

		init();

		getMetaSearch().configDirty();
	}

	public String getLoginPageUrl() {
		//Let's try with no login page url
		//return loginPageUrl;
		return searchURLFormat.replaceAll("%s", "");
	}

	public void setLoginPageUrl(String loginPageUrl) {
		this.loginPageUrl = loginPageUrl;
	}

	public String[] getRequiredCookies() {
		return requiredCookies;
	}

	public void setRequiredCookies(String[] requiredCookies) {
		this.requiredCookies = requiredCookies;
	}

	public boolean isNeedsAuth() {
		return needsAuth;
	}

	@Override
	public boolean
	isAuthenticated()
	{
		return( isNeedsAuth());
	}

	protected void
	setNeedsAuth(
		boolean	b )
	{
		needsAuth = b;
	}

	public String
	getAuthMethod()
	{
		return( authMethod );
	}

	public String getCookies() {
		return local_cookies;
	}

	@Override
	public String
	getString()
	{
		return( getString( false ));
	}

	@Override
	public String
	getString(
		boolean		full )
	{
		return( 	super.getString() +
						(full?(", url=" + searchURLFormat ):"") +
						", auth=" + isNeedsAuth() +
						(isNeedsAuth()?" [cookies=" + local_cookies + "]":"" ));
	}

	public static class
	pageDetails
	{
		private URL			initial_url;
		private URL			final_url;
		private String		content;

		private Object		verified_state;

		protected
		pageDetails(
			URL		_initial_url,
			URL		_final_url,
			String	_content )
		{
			initial_url		= _initial_url;
			final_url		= _final_url;
			content			= _content;
		}

		public URL
		getInitialURL()
		{
			return( initial_url );
		}

		public URL
		getFinalURL()
		{
			return( final_url );
		}

		public String
		getContent()
		{
			return( content );
		}

		private void
		setContent(
			String		_content )
		{
			content	= _content;
		}

		public void
		setVerifiedState(
			Object		state )
		{
			verified_state = state;
		}

		public Object
		getVerifiedState()
		{
			return( verified_state );
		}
	}

	public interface
	pageDetailsVerifier
	{
		public void
		verify(
			pageDetails	details )

			throws SearchException;
	}
}
