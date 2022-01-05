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

package com.biglybt.core.metasearch.impl.web.rss;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
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
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.pif.utils.StaticUtilities;
import com.biglybt.pif.utils.xml.rss.RSSChannel;
import com.biglybt.pif.utils.xml.rss.RSSFeed;
import com.biglybt.pif.utils.xml.rss.RSSItem;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentAttribute;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentNode;

public class
RSSEngine
	extends WebEngine
{
	private Pattern seed_leecher_pat 	= Pattern.compile("([0-9]+)\\s+(seed|leecher)s", Pattern.CASE_INSENSITIVE);
	private Pattern size_pat 			= Pattern.compile("([0-9\\.]+)\\s+(B|KB|KiB|MB|MiB|GB|GiB|TB|TiB)", Pattern.CASE_INSENSITIVE);

	public static EngineImpl
	importFromBEncodedMap(
		MetaSearchImpl		meta_search,
		Map					map )

		throws IOException
	{
		return( new RSSEngine( meta_search, map ));
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
		return( new RSSEngine( meta_search, id, last_updated, rank_bias, name, map ));
	}

		// explicit constructor

	public
	RSSEngine(
		MetaSearchImpl		meta_search,
		long 				id,
		long 				last_updated,
		float				rank_bias,
		String 				name,
		String 				searchURLFormat,
		boolean				needs_auth,
		String				auth_method,
		String				login_url,
		String[]			required_cookies )
	{
		super( 	meta_search,
				Engine.ENGINE_TYPE_RSS,
				id,
				last_updated,
				rank_bias,
				name,
				searchURLFormat,
				"GMT",
				false,
				"EEE, d MMM yyyy HH:mm:ss Z",
				new FieldMapping[0],
				needs_auth,
				auth_method,
				login_url,
				required_cookies );
	}

	protected
	RSSEngine(
		MetaSearchImpl		meta_search,
		Map					map )

		throws IOException
	{
		super( meta_search, map );
	}

		// json

	protected
	RSSEngine(
		MetaSearchImpl		meta_search,
		long				id,
		long				last_updated,
		float				rank_bias,
		String				name,
		JSONObject			map )

		throws IOException
	{
		super( meta_search, Engine.ENGINE_TYPE_REGEX, id, last_updated, rank_bias, name, map );
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

		return( res );
	}

	@Override
	public boolean
	supportsField(
		int		field_id )
	{
			// don't know about optional fields (such as direct download - be optimistic)

		switch( field_id ){
			case FIELD_NAME:
			case FIELD_DATE:
			case FIELD_CATEGORY:
			case FIELD_COMMENTS:
			case FIELD_CDPLINK:
			case FIELD_TORRENTLINK:
			case FIELD_DOWNLOADBTNLINK:
			{
				return( true );
			}
		}

		return( false );
	}

	@Override
	public int
	getAutoDownloadSupported()
	{
			// unknown until a successful feed download has occurred so that we know the
			// status of the feed tag

		return((int)getLocalLong( LD_AUTO_DL_SUPPORTED, AUTO_DL_SUPPORTED_UNKNOWN ));
	}

	@Override
	protected Result[]
	searchSupport(
		SearchParameter[] 	searchParameters,
		Map					searchContext,
		int 				desired_max_matches,
		int					absolute_max_matches,
		String 				headers,
		ResultListener 		listener)

		throws SearchException
	{
		debugStart();

		boolean	only_if_mod = !searchContext.containsKey( Engine.SC_FORCE_FULL );

		pageDetails page_details =
			super.getWebPageContent(
				searchParameters,
				searchContext,
				headers,
				only_if_mod,
				new pageDetailsVerifier() {

					@Override
					public void
					verify(
						pageDetails details )

						throws SearchException
					{
						try{
							String	page = details.getContent();

							if ( page != null && page.length() > 0 ){

								ByteArrayInputStream bais = new ByteArrayInputStream( page.getBytes("UTF-8"));

								RSSFeed rssFeed = StaticUtilities.getRSSFeed( details.getInitialURL(), bais );

								details.setVerifiedState( rssFeed );
							}
						}catch( Throwable e ){

							debugLog( "failed: " + Debug.getNestedExceptionMessageAndStack( e ));

							if ( e instanceof SearchException ){

								throw((SearchException)e );
							}

							throw( new SearchException( "RSS matching failed", e ));
						}
					}
				});

		String	page = page_details.getContent();

		if ( listener != null ){

			listener.contentReceived( this, page );
		}

		if ( page == null || page.length() == 0 ){

			return( new Result[0]);
		}

		try{
			RSSFeed rssFeed = (RSSFeed)page_details.getVerifiedState();

			RSSChannel[] channels = rssFeed.getChannels();

			List results = new ArrayList();

			for ( int i=0; i<channels.length; i++ ){

				RSSChannel channel = channels[i];

				SimpleXMLParserDocumentNode[] channel_kids = channel.getNode().getChildren();

				int	auto_dl_state = AUTO_DL_SUPPORTED_YES;

				for ( int j=0; j<channel_kids.length; j++ ){

					SimpleXMLParserDocumentNode child = channel_kids[j];

					String	lc_full_child_name 	= child.getFullName().toLowerCase();

					if ( lc_full_child_name.equals( "vuze:auto_dl_enabled" )){

						if ( !child.getValue().equalsIgnoreCase( "true" )){

							auto_dl_state = AUTO_DL_SUPPORTED_NO;
						}
					}
				}

				setLocalLong( LD_AUTO_DL_SUPPORTED, auto_dl_state );

				RSSItem[] items = channel.getItems();

				for ( int j=0 ; j<items.length; j++ ){

					RSSItem item = items[j];

					WebResult result = new WebResult(this,getRootPage(),getBasePage(),getDateParser(),"");

					result.setPublishedDate(item.getPublicationDate());

					result.setNameFromHTML(item.getTitle());

					URL cdp_link = item.getLink();

					boolean	cdp_set = false;

					if ( cdp_link != null ){

						String link_url = cdp_link.toExternalForm();

						String lc_url = link_url.toLowerCase( Locale.US );

						if ( lc_url.startsWith( "http" ) || lc_url.startsWith( "tor:http" )){

							result.setCDPLink( link_url );

							cdp_set = true;
						}
					}

					String uid = item.getUID();

					if ( uid != null ){

						result.setUID( uid );

							// some feeds don't use the link field correctly, fallback to trying the guid for cdp
							// as probably better than nothing


						if ( !cdp_set ){

							try{
								String test_url = new URL( uid ).toExternalForm();

								if ( test_url.toLowerCase().startsWith( "http" )){

									result.setCDPLink( test_url );
								}
							}catch( Throwable e ){
							}
						}
					}

					boolean got_seeds_peers = false;

					int	item_seeds		= -1;
					int item_peers		= -1;
					String item_hash	= null;
					String item_magnet	= null;

					String	desc_size = null;

					SimpleXMLParserDocumentNode node = item.getNode();

					SimpleXMLParserDocumentNode[] children = node.getChildren();

					boolean vuze_feed = false;

					for ( int k=0; k<children.length; k++ ){

						SimpleXMLParserDocumentNode child = children[k];

						String	lc_full_child_name 	= child.getFullName().toLowerCase();

						if ( lc_full_child_name.startsWith( "vuze:" )){

							vuze_feed = true;

							break;
						}
					}

					for ( int k=0; k<children.length; k++ ){

						SimpleXMLParserDocumentNode child = children[k];

						String	lc_child_name 		= child.getName().toLowerCase();
						String	lc_full_child_name 	= child.getFullName().toLowerCase();

						String	value = child.getValue().trim();

						if (lc_child_name.equals( "enclosure" )){

							SimpleXMLParserDocumentAttribute typeAtt = child.getAttribute("type");

							if( typeAtt != null && typeAtt.getValue().equalsIgnoreCase( "application/x-bittorrent")) {

								SimpleXMLParserDocumentAttribute urlAtt = child.getAttribute("url");

								if( urlAtt != null ){

									result.setTorrentLink(urlAtt.getValue());
								}

								SimpleXMLParserDocumentAttribute lengthAtt = child.getAttribute("length");

								if (lengthAtt != null){

									result.setSizeFromHTML(lengthAtt.getValue());
								}
							}
						}else if(lc_child_name.equals( "category" )) {

							result.setCategoryFromHTML( value );

						}else if(lc_child_name.equals( "comments" )){

							result.setCommentsFromHTML( value );

						}else if ( lc_child_name.equals( "link" ) || lc_child_name.equals( "guid" )) {

							String lc_value = value.toLowerCase();

							try{
								URL url = new URL(value);

								if ( 	lc_value.endsWith( ".torrent" ) ||
										lc_value.startsWith( "magnet:" ) ||
										lc_value.startsWith( "bc:" ) ||
										lc_value.startsWith( "bctp:" ) ||
										lc_value.startsWith( "dht:" )){


									result.setTorrentLink(value);

								}else if ( lc_child_name.equals( "link" ) && !vuze_feed ){

									long	test = getLocalLong( LD_LINK_IS_TORRENT, 0 );

									if ( test == 1 ){

										result.setTorrentLink( value );

									}else if ( test == 0 || SystemTime.getCurrentTime() - test > 60*1000 ){

										if ( linkIsToTorrent( url )){

											result.setTorrentLink(value);

											setLocalLong( LD_LINK_IS_TORRENT, 1 );

										}else{

											setLocalLong( LD_LINK_IS_TORRENT, SystemTime.getCurrentTime());
										}
									}
								}
							}catch( Throwable e ){

									// see if this is an atom feed
									//  <link rel="alternate" type="application/x-bittorrent" href="http://asdasd/

								SimpleXMLParserDocumentAttribute typeAtt = child.getAttribute( "type" );

								if ( typeAtt != null && typeAtt.getValue().equalsIgnoreCase("application/x-bittorrent")) {

									SimpleXMLParserDocumentAttribute hrefAtt = child.getAttribute( "href" );

									if ( hrefAtt != null ){

										String	href = hrefAtt.getValue().trim();

										try{

											result.setTorrentLink( new URL( href ).toExternalForm() );

										}catch( Throwable f ){

										}
									}
								}
							}
						}else if ( lc_child_name.equals( "content" ) && rssFeed.isAtomFeed()){

							SimpleXMLParserDocumentAttribute srcAtt = child.getAttribute( "src" );

							String	src = srcAtt==null?null:srcAtt.getValue();

							if ( src != null ){

								boolean	is_dl_link = false;

								SimpleXMLParserDocumentAttribute typeAtt = child.getAttribute( "type" );

								if ( typeAtt != null && typeAtt.getValue().equalsIgnoreCase("application/x-bittorrent")) {

									is_dl_link = true;
								}

								if ( !is_dl_link ){

									is_dl_link = src.toLowerCase().contains(".torrent");
								}

								if ( is_dl_link ){

									try{
										new URL( src );

										result.setTorrentLink( src );

									}catch( Throwable e ){
									}
								}
							}
						}else if ( lc_full_child_name.equals( "vuze:size" )){

							result.setSizeFromHTML( value );

						}else if ( lc_full_child_name.equals( "vuze:seeds" )){

							got_seeds_peers = true;

							result.setNbSeedsFromHTML( value );

						}else if ( lc_full_child_name.equals( "vuze:superseeds" )){

							got_seeds_peers = true;

							result.setNbSuperSeedsFromHTML( value );

						}else if ( lc_full_child_name.equals( "vuze:peers" )){

							got_seeds_peers = true;

							result.setNbPeersFromHTML( value );

						}else if ( lc_full_child_name.equals( "vuze:rank" )){

							result.setRankFromHTML( value );

						}else if ( lc_full_child_name.equals( "vuze:contenttype" )){

							String	type = value.toLowerCase();

							if ( type.startsWith( "video" )){

								type = Engine.CT_VIDEO;

							}else if ( type.startsWith( "audio" )){

								type = Engine.CT_AUDIO;

							}else if ( type.startsWith( "games" )){

								type = Engine.CT_GAME;
							}

							result.setContentType( type );

						}else if ( lc_full_child_name.equals( "vuze:downloadurl" )){

							result.setTorrentLink( value);

						}else if ( lc_full_child_name.equals( "vuze:playurl" )){

							result.setPlayLink( value);

						}else if ( lc_full_child_name.equals( "vuze:drmkey" )){

							result.setDrmKey( value);

						}else if ( lc_full_child_name.equals( "vuze:assethash" )){

							result.setHash( value);
							
						}else if ( lc_full_child_name.equals( "vuze:assetdate" )){

							result.setAssetDate( value);

						}else if( lc_child_name.equals( "seeds" ) || lc_child_name.equals( "seeders" )){

							try{
								item_seeds = Integer.parseInt( value );

							}catch( Throwable e ){

							}
						}else if( lc_child_name.equals( "peers" ) || lc_child_name.equals( "leechers" )){

							try{
								item_peers = Integer.parseInt( value );

							}catch( Throwable e ){

							}
						}else if( lc_child_name.equals( "infohash" ) || lc_child_name.equals( "info_hash" )){

							item_hash = value;

						}else if( lc_child_name.equals( "magneturi" )){

							item_magnet = value;
						}
					}

					if ( !got_seeds_peers ){

						if ( item_peers >= 0 && item_seeds >= 0 ){

							result.setNbSeedsFromHTML( String.valueOf( item_seeds ));
							result.setNbPeersFromHTML( String.valueOf( item_peers ));

							got_seeds_peers = true;
						}
					}

					if ( !got_seeds_peers ){

						try{
							SimpleXMLParserDocumentNode desc_node = node.getChild( "description" );

							if ( desc_node != null ){

								String desc = desc_node.getValue().trim();

									// see if we can pull from description

								desc = desc.replaceAll( "\\(s\\)", "s" );

								desc = desc.replaceAll( "seeders", "seeds" );

								Matcher m = seed_leecher_pat.matcher( desc );

								while( m.find()){

									String	num = m.group(1);

									String	type = m.group(2);

									if ( type.toLowerCase().charAt(0) == 's' ){

										result.setNbSeedsFromHTML( num );

									}else{

										result.setNbPeersFromHTML( num );
									}
								}

								m = size_pat.matcher( desc );

								if ( m.find()){

									desc_size = m.group(1) + " " + m.group(2);
								}
							}
						}catch( Throwable e ){

						}
					}
					
						// override existing values with explicit <torrent> entry if present

					try{
						SimpleXMLParserDocumentNode torrent_node = node.getChild( "torrent" );

						if ( torrent_node != null ){

							if ( result.getSize() <= 0 ){

								SimpleXMLParserDocumentNode n = torrent_node.getChild( "contentLength" );

								if ( n != null ){

									try{
										long l = Long.parseLong( n.getValue().trim());

										result.setSizeFromHTML( l + " B" );

									}catch( Throwable e ){

									}
								}
							}

							String dlink = result.getDownloadLink();

							if ( dlink == null || dlink.length() == 0 ){

								SimpleXMLParserDocumentNode n = torrent_node.getChild( "magnetURI" );

								if ( n != null ){

									dlink = n.getValue().trim();

									result.setTorrentLink( dlink );
								}
							}

							String hash = result.getHash();

							if ( hash == null || hash.length() == 0 ){

								SimpleXMLParserDocumentNode n = torrent_node.getChild( "infoHash" );

								if ( n != null ){

									String h = n.getValue().trim();

									result.setHash( h );

									if ( dlink == null || dlink.length() == 0 ){

										String uri = UrlUtils.normaliseMagnetURI( h );

										if ( uri != null ){

											result.setTorrentLink( uri );
										}
									}
								}
							}

							SimpleXMLParserDocumentNode trackers_node = torrent_node.getChild( "trackers" );

							if ( trackers_node != null && !got_seeds_peers ){

								SimpleXMLParserDocumentNode[] groups = trackers_node.getChildren();

								int	max_total = -1;

								int	best_seeds		= 0;
								int	best_leechers	= 0;

								for ( SimpleXMLParserDocumentNode group: groups ){

									SimpleXMLParserDocumentNode[] g_kids = group.getChildren();

									for ( SimpleXMLParserDocumentNode t: g_kids ){

										if ( t.getName().equalsIgnoreCase( "tracker" )){

											SimpleXMLParserDocumentAttribute a_seeds 	= t.getAttribute( "seeds" );
											SimpleXMLParserDocumentAttribute a_leechers = t.getAttribute( "peers" );

											int	seeds 		= a_seeds==null?-1:Integer.parseInt( a_seeds.getValue().trim());
											int	leechers 	= a_leechers==null?-1:Integer.parseInt( a_leechers.getValue().trim());

											int	total = seeds + leechers;

											if ( total > max_total ){

												max_total = total;

												best_seeds 		= seeds;
												best_leechers	= leechers;
											}
										}
									}
								}

								if ( max_total >= 0 ){

									result.setNbSeedsFromHTML( String.valueOf( Math.max( 0, best_seeds )));
									result.setNbPeersFromHTML( String.valueOf( Math.max( 0, best_leechers )));
									
									got_seeds_peers = true;
								}
							}
						}
					}catch( Throwable e ){

						e.printStackTrace();
					}

						// look for attributes
					
					try{
						for ( int k=0; k<children.length; k++ ){

							SimpleXMLParserDocumentNode child = children[k];

							String	lc_child_name 		= child.getName().toLowerCase();
							
							if ( lc_child_name.equals( "attr" )){
								
								SimpleXMLParserDocumentAttribute attr_name = child.getAttribute( "name" );
								SimpleXMLParserDocumentAttribute attr_value = child.getAttribute( "value" );
								
								if ( attr_name != null && attr_value != null ){
									
									String name 	= attr_name.getValue();
									String value	= attr_value.getValue();
									
									if ( name.equals( "seeders" )){
										
										if ( !got_seeds_peers ){
											
											result.setNbSeedsFromHTML( value );
										}
									}else if ( name.equals( "peers" )){
										
										if ( !got_seeds_peers ){
											
											result.setNbPeersFromHTML( value );
										}
									}else if ( name.equals( "infohash" )){
									
										if ( item_hash == null ){
											
											item_hash = value;
										}
									}else if ( name.equals( "magneturl" )){
										
										if ( item_magnet == null ){
											
											item_magnet = value;
										}
									}
								}
							}
						}
					}catch( Throwable e ){
						
					}
					
					if ( result.getSize() <= 0 ){
						
						SimpleXMLParserDocumentNode n = node.getChild( "size" );

						if ( n != null ){

							result.setSizeFromHTML( n.getValue().trim());
						}
					}

							
					if ( item_hash != null && result.getHash() == null ){

						result.setHash( item_hash );
					}

					if ( item_magnet != null ){

						String existing = result.getTorrentLinkRaw();

						if ( existing == null || existing.length() == 0 ){

							result.setTorrentLink( item_magnet );
						}
					}

						// if we still have no download link see if the magnet is in the title

					String dlink = result.getDownloadLink();

					if ( dlink == null || dlink.length() == 0 ){

						String name = result.getName();

						if ( name != null ){

							String magnet = UrlUtils.parseTextForMagnets( name );

							if ( magnet != null ){

								result.setTorrentLink( magnet );
							}
						}
					}

					dlink = result.getDownloadLink();

					if ( dlink == null || dlink.length() == 0 ){

							// last ditch effort, sometimes the download link is the <link> so stuff it in and hope (could test download once I guess and then
							// record the outcome if it is a torrent but, meh)

						result.setTorrentLink( result.getCDPLink());
					}

						// some feeds have the cdp link as guid - grab this if cdp link is blank or same as dl link
					
					String latest_cdp_link  	= result.getCDPLink();
					String latest_torrent_link	= result.getTorrentLink();
					
					if ( 	latest_cdp_link == null || 
							( latest_cdp_link.length() > 0 && latest_cdp_link.equals( latest_torrent_link ))){
						
						try{
							String test_url = new URL( uid ).toExternalForm();

							if ( test_url.toLowerCase().startsWith( "http" )){

								result.setCDPLink( test_url );
							}
						}catch( Throwable e ){
						}
					}
					
					if ( result.getSize() <= 0 ){

						if ( desc_size != null ){

							result.setSizeFromHTML( desc_size );

						}
					}

					if ( result.getHash() == null ){

						if ( dlink != null ){

							String mag = UrlUtils.parseTextForMagnets(dlink);

							if ( mag == null ){

								String tlink = result.getTorrentLinkRaw();

								if ( tlink != null ){

									mag = UrlUtils.parseTextForMagnets(tlink);
								}
							}

							if ( mag != null ){

								byte[] hash = UrlUtils.getTruncatedHashFromMagnetURI( mag );

								if ( hash != null ){

									result.setHash( ByteFormatter.encodeString( hash ));
								}
							}
						}
					}
					results.add(result);

					if ( absolute_max_matches >= 0 && results.size() == absolute_max_matches ){

						break;
					}
				}
			}

			Result[] res = (Result[]) results.toArray(new Result[results.size()]);

			debugLog( "success: found " + res.length + " results" );

			return( res );


		}catch( Throwable e ){

			debugLog( "failed: " + Debug.getNestedExceptionMessageAndStack( e ));

			if ( e instanceof SearchException ){

				throw((SearchException)e );
			}

			throw( new SearchException( "RSS matching failed", e ));
		}
	}

	protected boolean
	linkIsToTorrent(
		URL		url )
	{
		try{
			HttpURLConnection con = (HttpURLConnection)url.openConnection();

			con.setRequestMethod( "HEAD" );

			con.setConnectTimeout( 10*1000 );

			con.setReadTimeout( 10*1000 );

			String content_type = con.getContentType();

			if ( content_type != null ){

				log( "Testing link " + url + " to see if torrent link -> content type=" + content_type );

				if ( content_type.equalsIgnoreCase( "application/x-bittorrent" )){

					return( true );
				}
			}

			return( false );

		}catch( Throwable e ){

			return( false );
		}
	}
}
