/*
 * Created on 2 Oct 2006
 * Created by Paul Gardner
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
 *
 */

package com.biglybt.core.networkmanager.impl.http;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.*;

import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.NetworkConnection;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.networkmanager.impl.TransportHelper;
import com.biglybt.core.networkmanager.impl.tcp.IncomingSocketChannelManager;
import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.core.peermanager.PeerManager;
import com.biglybt.core.peermanager.PeerManagerRegistration;
import com.biglybt.core.peermanager.PeerManagerRoutingListener;
import com.biglybt.core.peermanager.messaging.MessageStreamDecoder;
import com.biglybt.core.peermanager.messaging.MessageStreamEncoder;
import com.biglybt.core.peermanager.messaging.MessageStreamFactory;
import com.biglybt.core.stats.CoreStats;
import com.biglybt.core.stats.CoreStatsProvider;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.BEncoder;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;

public class
HTTPNetworkManager
{
	private static final String	NL			= "\r\n";

	static final LogIDs LOGID = LogIDs.NWMAN;

	private static final HTTPNetworkManager instance = new HTTPNetworkManager();

	public static HTTPNetworkManager getSingleton(){ return( instance ); }


	final IncomingSocketChannelManager http_incoming_manager;

	long	total_requests;
	long	total_webseed_requests;
	long	total_getright_requests;
	long	total_invalid_requests;
	long	total_ok_requests;

	final CopyOnWriteList<URLHandler>	url_handlers = new CopyOnWriteList<>();

	private
	HTTPNetworkManager()
	{
		  Set	types = new HashSet();

		  types.add( CoreStats.ST_NET_HTTP_IN_REQUEST_COUNT );
		  types.add( CoreStats.ST_NET_HTTP_IN_REQUEST_OK_COUNT );
		  types.add( CoreStats.ST_NET_HTTP_IN_REQUEST_INVALID_COUNT );
		  types.add( CoreStats.ST_NET_HTTP_IN_REQUEST_WEBSEED_COUNT );
		  types.add( CoreStats.ST_NET_HTTP_IN_REQUEST_GETRIGHT_COUNT );

		  CoreStats.registerProvider(
				  types,
				  new CoreStatsProvider()
				  {
						@Override
						public void
						updateStats(
							Set		types,
							Map		values )
						{
							if ( types.contains( CoreStats.ST_NET_HTTP_IN_REQUEST_COUNT )){

								values.put( CoreStats.ST_NET_HTTP_IN_REQUEST_COUNT, new Long( total_requests ));
							}

							if ( types.contains( CoreStats.ST_NET_HTTP_IN_REQUEST_OK_COUNT )){

								values.put( CoreStats.ST_NET_HTTP_IN_REQUEST_OK_COUNT, new Long( total_ok_requests ));
							}

							if ( types.contains( CoreStats.ST_NET_HTTP_IN_REQUEST_INVALID_COUNT )){

								values.put( CoreStats.ST_NET_HTTP_IN_REQUEST_INVALID_COUNT, new Long( total_invalid_requests ));
							}

							if ( types.contains( CoreStats.ST_NET_HTTP_IN_REQUEST_WEBSEED_COUNT )){

								values.put( CoreStats.ST_NET_HTTP_IN_REQUEST_WEBSEED_COUNT, new Long( total_webseed_requests ));
							}

							if ( types.contains( CoreStats.ST_NET_HTTP_IN_REQUEST_GETRIGHT_COUNT )){

								values.put( CoreStats.ST_NET_HTTP_IN_REQUEST_GETRIGHT_COUNT, new Long( total_getright_requests));							}

						}
				  });
		/*
		try{
			System.out.println( "/webseed?info_hash=" + URLEncoder.encode( new String( ByteFormatter.decodeString("C9C04D96F11FB5C5ECC99D418D3575FBFC2208B0"), "ISO-8859-1"), "ISO-8859-1" ));

		}catch( Throwable e ){

			e.printStackTrace();
		}
		*/

		http_incoming_manager = new IncomingSocketChannelManager( "HTTP.Data.Listen.Port", "HTTP.Data.Listen.Port.Enable" );

		NetworkManager.ByteMatcher matcher =
		   	new NetworkManager.ByteMatcher()
		    {
				@Override
				public int matchThisSizeOrBigger(){	return( 4 + 1 + 11 ); } // GET ' ' <url of 1> ' HTTP/1.1<cr><nl>'

		    	@Override
			    public int maxSize() { return 256; }	// max GET <url> size - boiler plate plus small url plus hash
		    	@Override
			    public int minSize() { return 3; }		// enough to match GET

		    	@Override
			    public Object
		    	matches(
		    		TransportHelper		transport,
		    		ByteBuffer 			to_compare,
		    		int 				port )
		    	{
		    		total_requests++;

		    		InetSocketAddress	address = transport.getAddress();

		    		int old_limit 		= to_compare.limit();
		    		int old_position 	= to_compare.position();

		    		boolean 	ok = false;

		    		try{
			    		byte[]	head = new byte[3];

			    		to_compare.get( head );

			    			// note duplication of this in min-matches below

			    		if ( head[0] != 'G' || head[1] != 'E' || head[2] != 'T' ){

			    			return( null );
			    		}

			    		byte[]	line_bytes = new byte[to_compare.remaining()];

			    		to_compare.get( line_bytes );

			    		try{
			    				// format is GET url HTTP/1.1<NL>

				    		String	url = new String( line_bytes, "ISO-8859-1" );

				    		int	space = url.indexOf(' ');

				    		if ( space == -1 ){

				    			return( null );
				    		}

				    			// note that we don't insist on a full URL here, just the start of one

				    		url = url.substring( space + 1 ).trim();

				    		if (url.contains("/index.html")){

				    			ok	= true;

					    		return( new Object[]{ transport, getIndexPage() });

				    		}else if (url.contains("/ping.html")){

				    				// ping is used for inbound HTTP port checking

				    			ok	= true;

						    	return( new Object[]{ transport, getPingPage( url ) });

				    		}else if (url.contains("/test503.html")){

				    			ok	= true;

					    		return( new Object[]{ transport, getTest503()});
				    		}

				    		String	hash_str = null;

				    		int	hash_pos = url.indexOf( "?info_hash=" );

				    		if ( hash_pos != -1 ){

				    			int	hash_start = hash_pos + 11;

				    			int	hash_end = url.indexOf( '&', hash_pos );

				    			if ( hash_end == -1 ){

				    					// not read the end yet

				    				return( null );

				    			}else{

				    				hash_str = url.substring( hash_start, hash_end );
				    			}
				    		}else{

				    			hash_pos = url.indexOf( "/files/" );

					    		if ( hash_pos != -1 ){

					    			int	hash_start = hash_pos + 7;

					    			int	hash_end = url.indexOf('/', hash_start );

					    			if ( hash_end == -1 ){

					    					// not read the end of the hash yet

					    				return( null );

					    			}else{

					    				hash_str = url.substring( hash_start, hash_end );
					    			}
					    		}
				    		}

				    		if ( hash_str != null ){

			    				byte[]	hash = URLDecoder.decode( hash_str, "ISO-8859-1" ).getBytes( "ISO-8859-1" );

			    				PeerManagerRegistration reg_data = PeerManager.getSingleton().manualMatchHash( address, hash );

			    				if ( reg_data != null ){

			    						// trim back URL as it currently has header in it too

			    					int	pos = url.indexOf( ' ' );

			    					String	trimmed = pos==-1?url:url.substring(0,pos);

			    					ok	= true;

			    					return( new Object[]{ trimmed, reg_data });
			    				}
				    		}else{

				    			int	link_pos = url.indexOf( "/links/" );

				    			if ( link_pos != -1 ){

			    					int	pos = url.indexOf( ' ', link_pos );

			    					if ( pos == -1 ){

			    						return( null );
			    					}

			    					String link = url.substring(0,pos).substring( link_pos+7 );

				    				link = URLDecoder.decode( link, "UTF-8" );

				    				PeerManagerRegistration reg_data = PeerManager.getSingleton().manualMatchLink( address, link );

				    				if ( reg_data != null ){

				    					TOTorrentFile	file = reg_data.getLink( link );

				    					if ( file != null ){

					    					StringBuilder target_url = new StringBuilder( 512 );

					    					target_url.append( "/files/" );

					    					target_url.append( URLEncoder.encode(  new String( file.getTorrent().getHash(), "ISO-8859-1" ), "ISO-8859-1" ));

					    					byte[][]	bits = file.getPathComponents();

					    					for (int i=0;i<bits.length;i++){

					    						target_url.append( "/" );

					    						target_url.append( URLEncoder.encode( new String( bits[i], "ISO-8859-1" ), "ISO-8859-1" ));
					    					}

					    					ok	= true;

					    					return( new Object[]{ target_url.toString(), reg_data });
				    					}
				    				}
				    			}
				    		}

				    		String trimmed = url;

				    		int	pos = trimmed.indexOf( ' ' );

				    		if ( pos != -1 ){

				    			trimmed = trimmed.substring( 0, pos );
				    		}

				    		for ( URLHandler handler: url_handlers ){

				    			if ( handler.matches( trimmed )){

			    					ok	= true;

			    					return( new Object[]{ handler, transport, "GET " + url });
				    			}
				    		}
		   					if (Logger.isEnabled()){
	    						Logger.log(new LogEvent(LOGID, "HTTP decode from " + address + " failed: no match for " + url ));
	    					}

				    		return( new Object[]{ transport, getNotFound() });

			    		}catch( Throwable e ){

		   					if (Logger.isEnabled()){
	    						Logger.log(new LogEvent(LOGID, "HTTP decode from " + address + " failed, " + e.getMessage()));
	    					}

		   					return( null );
			    		}
		    		}finally{

		    			if ( ok ){

		    				total_ok_requests++;

		    			}else{

		    				total_invalid_requests++;
		    			}
			    			//	restore buffer structure

			    		to_compare.limit( old_limit );
			    		to_compare.position( old_position );
		    		}
		    	}

		    	@Override
			    public Object
		    	minMatches(
		    		TransportHelper		transport,
		    		ByteBuffer 			to_compare,
		    		int 				port )
		    	{
		    		byte[]	head = new byte[3];

		    		to_compare.get( head );

		    		if (head[0] != 'G' || head[1] != 'E' || head[2] != 'T' ){

		    			return( null );
		    		}

		    		return( "" );
		    	}

		    	@Override
			    public byte[][]
		    	getSharedSecrets()
		    	{
		    		return( null );
		    	}

		    	 @Override
			     public int
		    	 getSpecificPort()
		    	 {
		    		 return( http_incoming_manager.getTCPListeningPortNumber());
		    	 }
		    	 
		    	 @Override
		    	 public String 
		    	 getDescription()
		    	 {
		    		 return( "HTTP" );
		    	 }
		    };

	    // register for incoming connection routing
	    NetworkManager.getSingleton().requestIncomingConnectionRouting(
	        matcher,
	        new NetworkManager.RoutingListener()
	        {
	        	@Override
		        public void
	        	connectionRouted(
	        		final NetworkConnection 	connection,
	        		Object 						_routing_data )
	        	{
	        		Object[]	x = (Object[])_routing_data;

	        		Object	entry1 = x[0];

	        		if ( entry1 instanceof TransportHelper ){

	        				// routed on failure

	        			writeReply(connection, (TransportHelper)x[0], (String)x[1]);

	        			return;

	        		}else if ( entry1 instanceof URLHandler ){

	        			((URLHandler)entry1).handle(
	        					(TransportHelper)x[1],
	        					(String)x[2] );

	        			return;
	        		}

	        		final String					url 			= (String)entry1;
	        		final PeerManagerRegistration	routing_data 	= (PeerManagerRegistration)x[1];

   					if (Logger.isEnabled()){
						Logger.log(new LogEvent(LOGID, "HTTP connection from " + connection.getEndpoint().getNotionalAddress() + " routed successfully on '" + url + "'" ));
   					}

	        		PeerManager.getSingleton().manualRoute(
	        				routing_data,
	        				connection,
	        				new PeerManagerRoutingListener()
	        				{
	        					@Override
						        public boolean
	        					routed(
	        						PEPeerTransport		peer )
	        					{
	        						if (url.contains("/webseed")){

	        							total_webseed_requests++;

	        							new HTTPNetworkConnectionWebSeed( HTTPNetworkManager.this, connection, peer );

	        							return( true );

	        						}else if (url.contains("/files/")){

	        							total_getright_requests++;

	        							new HTTPNetworkConnectionFile( HTTPNetworkManager.this, connection, peer );

	        							return( true );
	        						}

	        						return( false );
	        					}
	        				});
	        	}

	        	@Override
		        public boolean
	      	  	autoCryptoFallback()
	        	{
	        		return( false );
	        	}
	        	},
	        new MessageStreamFactory() {
	          @Override
	          public MessageStreamEncoder createEncoder() {  return new HTTPMessageEncoder();  }
	          @Override
	          public MessageStreamDecoder createDecoder() {  return new HTTPMessageDecoder();  }
	        });
	}


	protected void
	reRoute(
		final HTTPNetworkConnection		old_http_connection,
		final byte[]					old_hash,
		final byte[]					new_hash,
		final String					header )
	{
		final NetworkConnection	old_connection = old_http_connection.getConnection();

		PeerManagerRegistration reg_data =
			PeerManager.getSingleton().manualMatchHash(
					old_connection.getEndpoint().getNotionalAddress(),
					new_hash );

		if ( reg_data == null ){

			old_http_connection.close( "Re-routing failed - registration not found" );

			return;
		}

		final Transport transport = old_connection.detachTransport();

		old_http_connection.close( "Switching torrents" );

		final NetworkConnection new_connection =
			NetworkManager.getSingleton().bindTransport(
					transport,
					new HTTPMessageEncoder(),
					new HTTPMessageDecoder( header ));

		PeerManager.getSingleton().manualRoute(
				reg_data,
				new_connection,
				new PeerManagerRoutingListener()
				{
					@Override
					public boolean
					routed(
						PEPeerTransport		peer )
					{
						HTTPNetworkConnection new_http_connection;

						if (header.contains("/webseed")){

							new_http_connection = new HTTPNetworkConnectionWebSeed( HTTPNetworkManager.this, new_connection, peer );

						}else if (header.contains("/files/")){

							new_http_connection = new HTTPNetworkConnectionFile( HTTPNetworkManager.this, new_connection, peer );

						}else{

							return( false );
						}

							// fake a wakeup so pre-read header is processed

						new_http_connection.readWakeup();

						/*
						System.out.println(
								"Re-routed " + new_connection.getEndpoint().getNotionalAddress() +
								" from " + ByteFormatter.encodeString( old_hash ) + " to " +
								 ByteFormatter.encodeString( new_hash ) );
						*/

						return( true );
					}
				});
	}

	public boolean
	isHTTPListenerEnabled()
	{
		return( http_incoming_manager.isEnabled());
	}

	public int
	getHTTPListeningPortNumber()
	{
		return( http_incoming_manager.getTCPListeningPortNumber());
	}

	public void
	setExplicitBindAddress(
		InetAddress	address )
	{
		http_incoming_manager.setExplicitBindAddress( address );
	}

	public void
	clearExplicitBindAddress()
	{
		http_incoming_manager.clearExplicitBindAddress();
	}

	public boolean
	isEffectiveBindAddress(
		InetAddress		address )
	{
		return( http_incoming_manager.isEffectiveBindAddress( address ));
	}

	protected String
	getIndexPage()
	{
		return( "HTTP/1.1 200 OK" + NL +
				"Connection: Close" + NL +
				"Content-Length: 0" + NL +
				NL );
	}

	protected String
	getPingPage(
		String	url )
	{
		int	pos = url.indexOf( ' ' );

		if ( pos != -1 ){

			url = url.substring( 0, pos );
		}

		pos = url.indexOf( '?' );

		Map	response = new HashMap();

		boolean	ok = false;

		if ( pos != -1 ){

			StringTokenizer tok = new StringTokenizer(url.substring(pos+1), "&");

			while( tok.hasMoreTokens()){

				String	token = tok.nextToken();

				pos	= token.indexOf('=');

				if ( pos != -1 ){

					String	lhs = token.substring(0,pos);
					String	rhs = token.substring(pos+1);

					if ( lhs.equals( "check" )){

						response.put( "check", rhs );

						ok = true;
					}
				}
			}
		}

		if ( ok ){

			try{
				byte[]	bytes = BEncoder.encode( response );

				byte[]	length = new byte[4];

				ByteBuffer.wrap( length ).putInt( bytes.length );

				return( "HTTP/1.1 200 OK" + NL +
						"Connection: Close" + NL +
						"Content-Length: " + ( bytes.length + 4 )+ NL +
						NL +
						new String( length, "ISO-8859-1" ) + new String( bytes, "ISO-8859-1" ) );

			}catch( Throwable e ){
			}
		}

		return( getNotFound());

	}

	protected String
	getTest503()
	{
		return( "HTTP/1.1 503 Service Unavailable" + NL +
				"Connection: Close" + NL +
				"Content-Length: 4" + NL +
				NL +
				"1234" );
	}

	protected String
	getNotFound()
	{
		return( "HTTP/1.1 404 Not Found" + NL +
				"Connection: Close" + NL +
				"Content-Length: 0" + NL +
				NL );
	}

	protected String
	getRangeNotSatisfiable()
	{
		return( "HTTP/1.1 416 Not Satisfiable" + NL +
				"Connection: Close" + NL +
				"Content-Length: 0" + NL +
				NL );
	}


	protected void
	writeReply(
		final NetworkConnection		connection,
		final TransportHelper		transport,
		final String				data )
	{
		byte[]	bytes;

		try{
			bytes = data.getBytes( "ISO-8859-1" );

		}catch( UnsupportedEncodingException e ){

			bytes = data.getBytes();
		}

		final ByteBuffer bb = ByteBuffer.wrap( bytes );

		try{
			transport.write( bb, false );

			if ( bb.remaining() > 0 ){

				transport.registerForWriteSelects(
					new TransportHelper.selectListener()
					{
					  	@Override
						  public boolean
				    	selectSuccess(
				    		TransportHelper	helper,
				    		Object 			attachment )
					  	{
					  		try{
					  			int written = helper.write( bb, false );

					  			if ( bb.remaining() > 0 ){

					  				helper.registerForWriteSelects( this, null );

					  			}else{

				  					if (Logger.isEnabled()){
										Logger.log(new LogEvent(LOGID, "HTTP connection from " + connection.getEndpoint().getNotionalAddress() + " closed" ));
				   					}

									connection.close( null );
					  			}

					  			return( written > 0 );

					  		}catch( Throwable e ){

					  			helper.cancelWriteSelects();

			  					if (Logger.isEnabled()){
									Logger.log(new LogEvent(LOGID, "HTTP connection from " + connection.getEndpoint().getNotionalAddress() + " failed to write error '" + data + "'" ));
			   					}

					  			connection.close(e==null?null:Debug.getNestedExceptionMessage(e));

					  			return( false );
					  		}
					  	}

				        @Override
				        public void
				        selectFailure(
				        	TransportHelper	helper,
				        	Object 			attachment,
				        	Throwable 		msg)
				        {
				        	helper.cancelWriteSelects();

		  					if (Logger.isEnabled()){
								Logger.log(new LogEvent(LOGID, "HTTP connection from " + connection.getEndpoint().getNotionalAddress() + " failed to write error '" + data + "'" ));
		   					}

				        	connection.close(msg==null?null:Debug.getNestedExceptionMessage(msg));
				        }
					},
					null );
			}else{

				if (Logger.isEnabled()){
					Logger.log(new LogEvent(LOGID, "HTTP connection from " + connection.getEndpoint().getNotionalAddress() + " closed" ));
   				}

				connection.close( null );
			}
		}catch( Throwable e ){

			if (Logger.isEnabled()){
				Logger.log(new LogEvent(LOGID, "HTTP connection from " + connection.getEndpoint().getNotionalAddress() + " failed to write error '" + data + "'" ));
			}

			connection.close(e==null?null:Debug.getNestedExceptionMessage(e));
		}
	}

	public void
	addURLHandler(
		URLHandler	handler )
	{
		url_handlers.add( handler );
	}

	public void
	removeURLHandler(
		URLHandler	handler )
	{
		url_handlers.remove( handler );
	}

	public interface
	URLHandler
	{
		public boolean
		matches(
			String		url );

		public void
		handle(
			TransportHelper		transport,
			String				header_so_far );

	}
}
