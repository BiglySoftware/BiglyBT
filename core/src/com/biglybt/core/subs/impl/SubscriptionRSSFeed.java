/*
 * Created on Jul 13, 2009
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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Date;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.TimeFormatter;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.core.xml.util.XUXmlWriter;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;
import com.biglybt.pif.tracker.web.TrackerWebPageResponse;
import com.biglybt.pif.utils.search.SearchResult;
import com.biglybt.pif.utils.subscriptions.Subscription;
import com.biglybt.pif.utils.subscriptions.SubscriptionManager;
import com.biglybt.pif.utils.subscriptions.SubscriptionResult;
import com.biglybt.plugin.rssgen.RSSGeneratorPlugin;

public class
SubscriptionRSSFeed
	implements RSSGeneratorPlugin.Provider
{
	private static final String PROVIDER = "subscriptions";

	private SubscriptionManagerImpl		manager;
	private PluginInterface				plugin_interface;

	private RSSGeneratorPlugin		generator;

	protected
	SubscriptionRSSFeed(
		SubscriptionManagerImpl	_manager,
		PluginInterface			_plugin_interface )
	{
		manager 			= _manager;
		plugin_interface	= _plugin_interface;

		generator	= RSSGeneratorPlugin.getSingleton();

		if ( generator != null ){

			generator.registerProvider( PROVIDER, this );
		}
	}

	@Override
	public boolean
	isEnabled()
	{
		return( manager.isRSSPublishEnabled());
	}

	public String
	getFeedURL()
	{
		return( generator==null?"Feature Disabled":( generator.getURL() + PROVIDER ));
	}

	@Override
	public boolean
	generate(
		TrackerWebPageRequest		request,
		TrackerWebPageResponse		response )

		throws IOException
	{
		InetSocketAddress	local_address = request.getLocalAddress();

		if ( local_address == null ){

			return( false );
		}

		URL	url	= request.getAbsoluteURL();

		String path = url.getPath();

		path = path.substring( PROVIDER.length()+1);

		try{
			SubscriptionManager sman = plugin_interface.getUtilities().getSubscriptionManager();

			Subscription[] 	subs = sman.getSubscriptions();

			OutputStream os = response.getOutputStream();

			PrintWriter pw = new PrintWriter(new OutputStreamWriter( os, "UTF-8" ));

			if ( path.length() <= 1 ){

				response.setContentType( "text/html; charset=UTF-8" );

				pw.println( "<HTML><HEAD><TITLE>" + Constants.APP_NAME + " Subscription Feeds</TITLE></HEAD><BODY>" );

				for ( Subscription s: subs ){

					if ( s.isSearchTemplate()){

						continue;
					}

					String	name = s.getName();

					pw.println( "<LI><A href=\"" + PROVIDER + "/" + s.getID() + "\">" + name + "</A></LI>" );
				}

				pw.println( "</BODY></HTML>" );

			}else{

				String	id = path.substring( 1 );

				Subscription	subscription = null;

				for ( Subscription s: subs ){

					if ( s.getID().equals( id )){

						subscription = s;

						break;
					}
				}

				if ( subscription == null ){

					response.setReplyStatus( 404 );

					return( true );
				}

				URL	feed_url = url;

					// absolute url is borked as it doesn't set the host properly. hack

				String	host = (String)request.getHeaders().get( "host" );

				if ( host != null ){

					host = UrlUtils.extractURLHost( host );

					feed_url = UrlUtils.setHost( url, host );
				}

				response.setContentType( "application/xml; charset=UTF-8" );

				pw.println( "<?xml version=\"1.0\" encoding=\"utf-8\"?>" );

				pw.println(
						"<rss version=\"2.0\" " +
						Constants.XMLNS_VUZE + " " +
						"xmlns:media=\"http://search.yahoo.com/mrss/\" " +
						"xmlns:atom=\"http://www.w3.org/2005/Atom\" " +
						"xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\">" );

				pw.println( "<channel>" );

				String channel_title = Constants.APP_NAME + " Subscription: " + escape( subscription.getName());

				pw.println( "<title>" + channel_title + "</title>" );
				pw.println( "<link>http://biglybt.com</link>" );
				pw.println( "<atom:link href=\"" + escape( feed_url.toExternalForm()) + "\" rel=\"self\" type=\"application/rss+xml\" />" );

				pw.println( "<description>" + Constants.APP_NAME + " RSS Feed for subscription " + escape( subscription.getName()) + "</description>" );

				pw.println("<itunes:image href=\"https://www.biglybt.com/img/biglybt128.png\"/>");
				pw.println("<image><url>https://www.biglybt.com/img/biglybt128.png</url><title>" + channel_title + "</title><link>http://biglybt.com</link></image>");


				SubscriptionResult[] results = subscription.getResults();


				String	feed_date_key = "subscriptions.feed_date." + subscription.getID();

				long feed_date = COConfigurationManager.getLongParameter( feed_date_key );

				boolean new_date = false;

				for ( SubscriptionResult result: results ){

					Date date = (Date)result.getProperty( SearchResult.PR_PUB_DATE );

					long 	millis = date.getTime();

					if ( millis > feed_date ){

						feed_date = millis;

						new_date = true;
					}
				}

				if ( new_date ){

					COConfigurationManager.setParameter( feed_date_key, feed_date );
				}

				pw.println(	"<pubDate>" + TimeFormatter.getHTTPDate( feed_date ) + "</pubDate>" );


				for ( SubscriptionResult result: results ){

					try{
		  				pw.println( "<item>" );

		  				String	name = (String)result.getProperty( SearchResult.PR_NAME );

		  				pw.println( "<title>" + escape( name ) + "</title>" );

						Date date = (Date)result.getProperty( SearchResult.PR_PUB_DATE );

						if ( date != null ){

							pw.println(	"<pubDate>" + TimeFormatter.getHTTPDate( date.getTime()) + "</pubDate>" );
						}

						String	uid = (String)result.getProperty( SearchResult.PR_UID );

						if ( uid != null ){

							pw.println( "<guid isPermaLink=\"false\">" + escape(uid ) + "</guid>" );
						}

						String	link = (String)result.getProperty( SearchResult.PR_DOWNLOAD_LINK );
						Long	size = (Long)result.getProperty( SearchResult.PR_SIZE );

						if ( link != null ){

							pw.println( "<link>" + escape( link ) + "</link>" );


							if ( size != null ){

								pw.println( "<media:content fileSize=\"" + size + "\" url=\"" + escape( link ) + "\"/>" );
							}
						}

						if ( size != null ){

							pw.println( "<vuze:size>" + size + "</vuze:size>" );
						}

						Long	seeds = (Long)result.getProperty( SearchResult.PR_SEED_COUNT );

						if ( seeds != null ){

							pw.println( "<vuze:seeds>" + seeds + "</vuze:seeds>" );
						}

						Long	peers = (Long)result.getProperty( SearchResult.PR_LEECHER_COUNT );

						if ( peers != null ){

							pw.println( "<vuze:peers>" + peers + "</vuze:peers>" );
						}

						Long	rank = (Long)result.getProperty( SearchResult.PR_RANK );

						if ( rank != null ){

							pw.println( "<vuze:rank>" + rank + "</vuze:rank>" );
						}
						
						Long	completed = (Long)result.getProperty( SearchResult.PR_COMPLETED_COUNT );

						if ( completed != null && completed >= 0){

							pw.println( "<completed>" + completed + "</completed>" );
						}
						
						String	cat = (String)result.getProperty( SearchResult.PR_CATEGORY );
						
						if ( cat != null && !cat.isEmpty()){
															
							pw.println( "<category>" + escape( cat ) + "</category>" );
						}
						
						String[]	tags = (String[])result.getProperty( SearchResult.PR_TAGS );
						
						if ( tags != null ){
							
							for ( String tag: tags ){
								
								pw.println( "<tag>" + escape( tag ) + "</tag>" );
							}
						}
						
		  				pw.println( "</item>" );

					}catch( Throwable e ){

						Debug.out(e);
					}
				}

				pw.println( "</channel>" );

				pw.println( "</rss>" );
			}

			pw.flush();

		}catch( Throwable e ){

			Debug.out( e );

			throw( new IOException( Debug.getNestedExceptionMessage( e )));
		}

		return( true );
	}

	protected String
	escape(
		String	str )
	{
		return( XUXmlWriter.escapeXML(str));
	}

	protected String
	escapeMultiline(
		String	str )
	{
		return( XUXmlWriter.escapeXML(str.replaceAll("[\r\n]+", "<BR>")));
	}
}
