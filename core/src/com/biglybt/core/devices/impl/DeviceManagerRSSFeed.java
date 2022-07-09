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


package com.biglybt.core.devices.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.devices.Device;
import com.biglybt.core.devices.DeviceManager;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.TimeFormatter;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.core.xml.util.XMLEscapeWriter;
import com.biglybt.core.xml.util.XUXmlWriter;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;
import com.biglybt.pif.tracker.web.TrackerWebPageResponse;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.plugin.rssgen.RSSGeneratorPlugin;

public class
DeviceManagerRSSFeed
	implements RSSGeneratorPlugin.Provider
{
	private static final String PROVIDER = "devices";

	private DeviceManagerImpl		manager;

	private RSSGeneratorPlugin		generator;

	protected
	DeviceManagerRSSFeed(
		DeviceManagerImpl	_manager )
	{
		manager 	= _manager;
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

		DeviceImpl[] devices = manager.getDevices();

		OutputStream os = response.getOutputStream();

		XMLEscapeWriter pw = new XMLEscapeWriter( new PrintWriter(new OutputStreamWriter( os, "UTF-8" )));

		pw.setEnabled( false );

		boolean hide_generic = COConfigurationManager.getBooleanParameter( DeviceManager.CONFIG_VIEW_HIDE_REND_GENERIC, true );

		boolean show_only_tagged = COConfigurationManager.getBooleanParameter( DeviceManager.CONFIG_VIEW_SHOW_ONLY_TAGGED, false);

		if ( path.length() <= 1 ){

			response.setContentType( "text/html; charset=UTF-8" );

			pw.println( "<HTML><HEAD><TITLE>" + Constants.APP_NAME + " Device Feeds</TITLE></HEAD><BODY>" );

			for ( DeviceImpl d: devices ){

				if ( 	d.getType() != Device.DT_MEDIA_RENDERER ||
						d.isHidden() ||
						!d.isRSSPublishEnabled() ||
						( hide_generic && d.isNonSimple()) ||
						( show_only_tagged && !d.isTagged())){

					continue;
				}

				String	name = d.getName();

				String	device_url = PROVIDER + "/" + URLEncoder.encode( name, "UTF-8" );

				pw.println( "<LI><A href=\"" + device_url + "\">" + name + "</A>&nbsp;&nbsp;-&nbsp;&nbsp;<font size=\"-1\"><a href=\"" + device_url + "?format=html\">html</a></font></LI>" );
			}

			pw.println( "</BODY></HTML>" );

		}else{

			String	device_name = URLDecoder.decode( path.substring( 1 ), "UTF-8" );

			DeviceImpl	device = null;

			for ( DeviceImpl d: devices ){

				if ( d.getName().equals( device_name ) && d.isRSSPublishEnabled()){

					device = d;

					break;
				}
			}

			if ( device == null ){

				response.setReplyStatus( 404 );

				return( true );
			}

			TranscodeFileImpl[] _files = device.getFiles();

			List<TranscodeFileImpl>	files = new ArrayList<>(_files.length);

			files.addAll( Arrays.asList( _files ));

			Collections.sort(
				files,
				new Comparator<TranscodeFileImpl>()
				{
					@Override
					public int
					compare(
						TranscodeFileImpl f1,
						TranscodeFileImpl f2)
					{
						long	added1 = f1.getCreationDateMillis()/1000;
						long	added2 = f2.getCreationDateMillis()/1000;

						return((int)(added2 - added1 ));
					}
				});

			URL	feed_url = url;

				// absolute url is borked as it doesn't set the host properly. hack

			String	host = (String)request.getHeaders().get( "host" );

			if ( host != null ){

				int	pos = host.indexOf( ':' );

				if ( pos != -1 ){

					host = host.substring( 0, pos );
				}

				feed_url = UrlUtils.setHost( url, host );
			}

			if ( device instanceof DeviceMediaRendererImpl ){

				((DeviceMediaRendererImpl)device).browseReceived();
			}

			String channel_title = Constants.APP_NAME + " Device: " + escape( device.getName());

			boolean	html = request.getURL().contains( "format=html" );

			if ( html ){

				response.setContentType( "text/html; charset=UTF-8" );

				pw.println( "<HTML><HEAD><TITLE>" + channel_title + "</TITLE></HEAD><BODY>" );


				for ( TranscodeFileImpl file: files ){

		  			if ( !file.isComplete()){

		  				if ( !file.isTemplate()){

		  					continue;
		  				}
		  			}

	  				URL stream_url = file.getStreamURL( feed_url.getHost() );

	  				if ( stream_url != null ){

	  					String url_ext = stream_url.toExternalForm();

	  					pw.println( "<p>" );

	  					pw.println( "<a href=\"" + url_ext + "\">" + escape( file.getName()) + "</a>" );

	  					url_ext += url_ext.indexOf('?') == -1?"?":"&";

	  					url_ext += "action=download";

	  					pw.println( "&nbsp;&nbsp;-&nbsp;&nbsp;<font size=\"-1\"><a href=\"" + url_ext + "\">save</a></font>" );

	  				}
				}

				pw.println( "</BODY></HTML>" );

			}else{
				boolean	debug = request.getURL().contains( "format=debug" );

				if ( debug ){

					response.setContentType( "text/html; charset=UTF-8" );

					pw.println( "<HTML><HEAD><TITLE>" + channel_title + "</TITLE></HEAD><BODY>" );

					pw.println( "<pre>" );

					pw.setEnabled( true );

				}else{

					response.setContentType( "application/xml; charset=UTF-8" );
				}

				try{
					pw.println( "<?xml version=\"1.0\" encoding=\"utf-8\"?>" );

					pw.println(
							"<rss version=\"2.0\" " +
							Constants.XMLNS_VUZE + " " +
							"xmlns:media=\"http://search.yahoo.com/mrss/\" " +
							"xmlns:atom=\"http://www.w3.org/2005/Atom\" " +
							"xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\">" );

					pw.println( "<channel>" );

					pw.println( "<title>" + channel_title + "</title>" );
					pw.println( "<link>http://biglybt.com</link>" );
					pw.println( "<atom:link href=\"" + feed_url.toExternalForm() + "\" rel=\"self\" type=\"application/rss+xml\" />" );

					pw.println( "<description>" + Constants.APP_NAME + " RSS Feed for device " + escape( device.getName()) + "</description>" );

					pw.println("<itunes:image href=\"http://biglybt.com/img/biglybt128.png\"/>");
					pw.println("<image><url>https://www.biglybt.com/img/biglybt128.png</url><title>" + channel_title + "</title><link>http://biglybt.com</link></image>");




					String	feed_date_key = "devices.feed_date." + device.getID();

					long feed_date = COConfigurationManager.getLongParameter( feed_date_key );

					boolean new_date = false;

					for ( TranscodeFileImpl file: files ){

						long	file_date = file.getCreationDateMillis();

						if ( file_date > feed_date ){

							new_date = true;

							feed_date = file_date;
						}
					}

					if ( new_date ){

						COConfigurationManager.setParameter( feed_date_key, feed_date );
					}

					pw.println(	"<pubDate>" + TimeFormatter.getHTTPDate( feed_date ) + "</pubDate>" );

					for ( TranscodeFileImpl file: files ){

			  			if ( !file.isComplete()){

			  				if ( !file.isTemplate()){

			  					continue;
			  				}
			  			}

						try{
			  				pw.println( "<item>" );

			  				pw.println( "<title>" + escape( file.getName()) + "</title>" );

			  				pw.println(	"<pubDate>" + TimeFormatter.getHTTPDate( file.getCreationDateMillis()) + "</pubDate>" );

			  				pw.println( "<guid isPermaLink=\"false\">" + escape( file.getKey()) + "</guid>" );

			  				String[] categories = file.getCategories();

			  				for ( String category: categories ){

			  					pw.println( "<category>" + escape(category) + "</category>" );
			  				}

			  				String[] tags = file.getTags( true );

			  				for ( String tag: tags ){

			  					pw.println( "<tag>" + escape( tag ) + "</tag>" );
			  				}

			  				String mediaContent = "";

			  				URL stream_url = file.getStreamURL( feed_url.getHost() );

			  				if ( stream_url != null ){

			  					String url_ext = escape( stream_url.toExternalForm());

			  					long fileSize = file.getTargetFile().getLength();

			  					pw.println( "<link>" + url_ext + "</link>" );

			  					mediaContent = "<media:content medium=\"video\" fileSize=\"" +
													fileSize + "\" url=\"" + url_ext + "\"";

			  					String	mime_type = file.getMimeType();

			  					if ( mime_type != null ){

			  						mediaContent += " type=\"" + mime_type + "\"";
			  					}

								pw.println("<enclosure url=\"" + url_ext
										+ "\" length=\"" + fileSize
										+ (mime_type == null ? "" : "\" type=\"" + mime_type)
										+ "\"></enclosure>");
			  				}

			   				String	thumb_url		= null;
			  				String	author			= null;
			  				String	description		= null;

			  				try{
			  					Torrent torrent = file.getSourceFile().getDownload().getTorrent();

			  					TOTorrent toTorrent = PluginCoreUtils.unwrap(torrent);

			  					long duration_secs = PlatformTorrentUtils.getContentVideoRunningTime(toTorrent);

			  					if ( mediaContent.length() > 0 && duration_secs > 0 ){

			  						mediaContent += " duration=\"" + duration_secs + "\"";
			  					}

			  					thumb_url = PlatformTorrentUtils.getContentThumbnailUrl(toTorrent);

			  					author = PlatformTorrentUtils.getContentAuthor(toTorrent);

			  					description= PlatformTorrentUtils.getContentDescription(toTorrent);

			  					if ( description != null ){

			  						description = escapeMultiline( description );

			  						/*
			  						if ( thumb_url != null ){


			  							pw.println( "<description type=\"text/html\">" +
			  								escape( "<div style=\"text-align: justify;padding: 5px;\"><img style=\"float: left;margin-right: 15px;margin-bottom: 15px;\" src=\"" + thumb_url + "\"/>" ) +
			  								description +
			  								escape( "</div>" ) +
			  								"</description>" );
			  						}else{
			  						*/
			  							pw.println( "<description>" + description + "</description>");
			  						//}
			   					}
			  				}catch( Throwable e ){
			  				}

			  					// media elements

			  				if ( mediaContent.length() > 0 ){

			  					pw.println( mediaContent += "></media:content>" );
			  				}

			  				pw.println( "<media:title>" + escape( file.getName()) + "</media:title>" );

							if ( description != null ){

								pw.println( "<media:description>" + description + "</media:description>" );
							}

							if ( thumb_url != null ) {

								pw.println("<media:thumbnail url=\"" + thumb_url + "\"/>" );
							}

			 					// iTunes elements

							if ( thumb_url != null ) {

								pw.println("<itunes:image href=\"" + thumb_url + "\"/>");
							}

			 				if ( author != null ){

			  					pw.println("<itunes:author>" + escape(author) + "</itunees:author>");
			  				}

			  				pw.println( "<itunes:summary>" + escape( file.getName()) + "</itunes:summary>" );
			  				pw.println( "<itunes:duration>" + TimeFormatter.formatColon( file.getDurationMillis()/1000 ) + "</itunes:duration>" );

			  				pw.println( "</item>" );

						}catch( Throwable e ){

							Debug.out(e);
						}
					}

					pw.println( "</channel>" );

					pw.println( "</rss>" );

				}finally{

					if ( debug ){

						pw.setEnabled( false );

						pw.println( "</pre>" );

						pw.println( "</BODY></HTML>" );
					}
				}
			}
		}

		pw.flush();

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
