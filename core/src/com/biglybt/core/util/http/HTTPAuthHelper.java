/*
 * Created on Oct 2, 2008
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


package com.biglybt.core.util.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.util.*;

public class
HTTPAuthHelper
{
	public static final boolean	TRACE	= false;

	public static final int MAX_PROCESSORS = 32;

	public static final int	CONNECT_TIMEOUT		= 30*1000;
	public static final int READ_TIMEOUT		= 30*1000;

	private final HTTPAuthHelper			parent;
	private final Map						children	= new HashMap();

	private final URL						delegate_to;
	final String					delegate_to_host;
	final int						delegate_to_port;
	final boolean					delegate_is_https;

	private Proxy	delegate_to_proxy;

	private final CopyOnWriteList			listeners = new CopyOnWriteList();

	private int		port;

	private ServerSocket	server_socket;

	private boolean			http_only_detected;

	private final Map				cookie_names_set	= new HashMap();

	final ThreadPool		thread_pool = new ThreadPool("HTTPSniffer", MAX_PROCESSORS, true );

	final List			processors = new ArrayList();

	private volatile boolean		destroyed;

	public
	HTTPAuthHelper(
		URL			url )

		throws Exception
	{
		this( null, url );
	}

	private
	HTTPAuthHelper(
		HTTPAuthHelper			_parent,
		URL						_delegate_to )

		throws Exception
	{
		parent				= _parent;
		delegate_to			= _delegate_to;

		delegate_to_host	= delegate_to.getHost();
		delegate_is_https	= delegate_to.getProtocol().toLowerCase().equals( "https" );
		delegate_to_port	= delegate_to.getPort()==-1?delegate_to.getDefaultPort():delegate_to.getPort();

		server_socket = new ServerSocket();

		server_socket.setReuseAddress( true );

		server_socket.bind( new InetSocketAddress( "127.0.0.1", 0 ));

        port = server_socket.getLocalPort();
	}

	public void
	setProxy(
		Proxy		_proxy )
	{
		delegate_to_proxy	= _proxy;
	}

	public void
	start()
	{
        new AEThread2(
        	"HTTPSniffingProxy: " + delegate_to_host + ":" + delegate_to_port + "/" + delegate_is_https + "/" + port,
        	true )
        	{
        		@Override
		        public void
        		run()
        		{
        			try{
        				while( !destroyed ){

        					Socket	socket = server_socket.accept();

        					socket.setSoTimeout( READ_TIMEOUT );

        					synchronized( HTTPAuthHelper.this ){

        						if ( processors.size() >= MAX_PROCESSORS ){

        							try{
        								Debug.out( "Too many processors" );

        								socket.close();

        							}catch( Throwable e ){
        							}
        						}else{

        							processor proc = new processor( socket );

        							processors.add( proc );

        							proc.start();
        						}
        					}
        				}
        			}catch( Throwable e ){

        				if ( !destroyed ){

        					Debug.printStackTrace( e );
        				}
        			}
        		}
        	}.start();
	}

	public int
	getPort()
	{
		return( port );
	}

	public boolean
	wasHTTPOnlyCookieDetected()
	{
		return( http_only_detected );
	}

	private void
	setHTTPOnlyCookieDetected()
	{
		http_only_detected = true;

		if ( parent != null ){

			parent.setHTTPOnlyCookieDetected();
		}
	}

	private String
	getKey(
		URL		url )
	{
		int child_port = url.getPort()==-1?url.getDefaultPort():url.getPort();

		String	key = url.getProtocol() + ":" + url.getHost() + ":" + child_port;

		return( key );
	}

	private HTTPAuthHelper
	getChild(
		String		url_str,
		boolean		optional )

		throws Exception
	{
		if ( parent != null ){

			return( parent.getChild( url_str,optional ));
		}

		String lc_url_str = url_str.toLowerCase();

		if ( lc_url_str.startsWith( "http://" ) || lc_url_str.startsWith( "https://")){

			URL child_url = new URL( url_str );

			String	child_key = getKey( child_url );

			if ( child_key.equals( getKey( delegate_to ))){

				return( this );
			}

			synchronized( this ){

				if ( destroyed ){

					throw( new Exception( "Destroyed" ));
				}

				HTTPAuthHelper child = (HTTPAuthHelper)children.get( child_key );

				if ( optional ){

						// create children for related domains

					String	base_host 	= delegate_to.getHost();
					String	child_host	= child_url.getHost();

					int	base_pos = base_host.lastIndexOf( '.' );
					base_pos = base_host.lastIndexOf( '.', base_pos-1 );

					int	child_pos = child_host.lastIndexOf( '.' );
					child_pos = child_host.lastIndexOf( '.', child_pos-1 );

					String base_dom 	= base_host.substring( base_pos, base_host.length());
					String child_dom 	= child_host.substring( child_pos, child_host.length());

					if ( base_dom.equals( child_dom )){

						optional = false;
					}
				}

				if ( child == null && !optional ){

					child = new HTTPAuthHelper( this, new URL( url_str ));

					children.put( child_key, child );

					child.start();
				}

				return( child );
			}
		}else{
				//relative

			return( this );
		}
	}

	private void
	addSetCookieName(
		String		name,
		String		value )
	{
		if ( parent != null ){

			parent.addSetCookieName( name, value );

		}else{

			boolean	new_entry;

			synchronized( cookie_names_set ){

				trace( "SetCookieName: " + name );

				String old_value = (String)cookie_names_set.put( name, value );

				new_entry = old_value==null || !old_value.equals( value );
			}

			if ( new_entry ){

				Iterator it = listeners.iterator();

				while( it.hasNext()){

					try{
						((HTTPAuthHelperListener)it.next()).cookieFound( this, name, value );

					}catch( Throwable e ){

						Debug.printStackTrace(e );
					}
				}
			}
		}
	}

	private boolean
	hasSetCookieName(
		String		name )
	{
		if ( parent != null ){

			return( parent.hasSetCookieName( name ));

		}else{

			synchronized( cookie_names_set ){

				trace( "GetCookieName: " + name );

				return( cookie_names_set.containsKey( name ));
			}
		}
	}

	public void
	addListener(
		HTTPAuthHelperListener		listener )
	{
		listeners.add( listener );
	}

	public void
	destroy()
	{
		List	processors_to_destroy;
		List	chidren_to_destroy;

		synchronized( this ){

			if ( destroyed ){

				return;
			}

			destroyed = true;

			chidren_to_destroy = new ArrayList( children.values());

			children.clear();

			processors_to_destroy = new ArrayList( processors );

			processors.clear();

			try{
				server_socket.close();

			}catch( Throwable e ){
			}
		}

		for (int i=0;i<chidren_to_destroy.size();i++){

			try{
				((HTTPAuthHelper)chidren_to_destroy.get(i)).destroy();

			}catch( Throwable e ){
			}
		}

		for (int i=0;i<processors_to_destroy.size();i++){

			try{
				((processor)processors_to_destroy.get(i)).destroy();

			}catch( Throwable e ){
			}
		}
	}

	private class
	processor
	{
		private static final String	NL = "\r\n";

		final Socket		socket_in;
		private Socket		socket_out;

		private volatile boolean	destroyed;

		private
		processor(
			Socket		_socket )
		{
			socket_in	= _socket;
		}

		private void
		start()
		{
			thread_pool.run(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						try{
							sniff();

						}finally{

							synchronized( HTTPAuthHelper.this ){

								processors.remove( processor.this );
							}
						}
					}
				});
		}

		private void
		sniff()
		{
			try{
				InputStream is = socket_in.getInputStream();

				String request_header = readHeader( is );

				connectToDelegate();

				process( request_header );

			}catch( Throwable e ){

				if ( !( e instanceof IOException )){

					Debug.out( e );
				}

				destroy();
			}
		}

		private void
		connectToDelegate()

			throws IOException
		{
			try{
				if ( delegate_is_https ){

					TrustManager[] trustAllCerts = SESecurityManager.getAllTrustingTrustManager();

					SSLContext sc = SSLContext.getInstance("SSL");

					sc.init(null, trustAllCerts, RandomUtils.SECURE_RANDOM );

					SSLSocketFactory factory = sc.getSocketFactory();

					try{
						if ( delegate_to_proxy == null ){

							socket_out = factory.createSocket();

							socket_out.connect( new InetSocketAddress( delegate_to_host, delegate_to_port ), CONNECT_TIMEOUT );

						}else{

							Socket plain_socket = new Socket( delegate_to_proxy );

							plain_socket.connect( new InetSocketAddress( delegate_to_host, delegate_to_port ), CONNECT_TIMEOUT );

							socket_out = factory.createSocket( plain_socket, delegate_to_host, delegate_to_port, true );
						}

					}catch( SSLException ssl_excep ){

						if ( socket_out != null ){

							try{
								socket_out.close();

							}catch( Throwable e ){
							}
						}

						factory = SESecurityManager.installServerCertificates( "AZ-sniffer:" + delegate_to_host + ":" + port, delegate_to_host, delegate_to_port );

						if ( delegate_to_proxy == null ){

							socket_out = factory.createSocket();

							socket_out.connect( new InetSocketAddress( delegate_to_host, delegate_to_port ), CONNECT_TIMEOUT );

						}else{

							Socket plain_socket = new Socket( delegate_to_proxy );

							plain_socket.connect( new InetSocketAddress( delegate_to_host, delegate_to_port ), CONNECT_TIMEOUT );

							socket_out = factory.createSocket( plain_socket, delegate_to_host, delegate_to_port, true );
						}
					}
				}else{

					if ( delegate_to_proxy == null ){

						socket_out = new Socket();

					}else{

						socket_out = new Socket( delegate_to_proxy );
					}

					socket_out.connect( new InetSocketAddress( delegate_to_host, delegate_to_port ), CONNECT_TIMEOUT );
				}
			}catch( Throwable e ){

				if ( e instanceof IOException ){

					throw((IOException)e );
				}

				throw( new IOException( e.toString()));

			}finally{

				if ( socket_out != null ){

					synchronized( this ){

						if ( destroyed ){

							try{
								socket_out.close();

							}catch( Throwable e ){

							}finally{

								socket_out = null;
							}

							throw( new IOException( "destroyed" ));
						}
					}
				}
			}
		}

		private void
		process(
			String		request_header )

			throws Exception
		{
			final OutputStream target_os = socket_out.getOutputStream();

			String[]	request_lines = splitHeader( request_header );

			String target_url = request_lines[0];

			int	space_pos = target_url.indexOf(' ');

			if ( space_pos == -1 ){

				System.out.println( "eh?" );
			}
			target_url = target_url.substring( space_pos ).trim();

			space_pos = target_url.indexOf(' ');

			target_url = target_url.substring( 0, space_pos ).trim();

			trace( "Page request for " + target_url );

			List	cookies_to_remove = new ArrayList();

			for (int i=0;i<request_lines.length;i++){

				String	line_out	= request_lines[i];

				String	line_in 	= line_out.trim().toLowerCase();

				String[] bits = line_in.split(":");

				if ( bits.length >= 2 ){

					String	lhs = bits[0].trim();

					if ( lhs.equals( "host" )){

						String	port_str;

						if ( delegate_to_port == 80 || delegate_to_port == 443 ){

							port_str = "";

						}else{

							port_str = ":" + delegate_to_port;
						}

						line_out = "Host: " + delegate_to_host + port_str;

					}else if ( lhs.equals( "connection" )){

						line_out = "Connection: close";

					}else if ( lhs.equals( "referer" )){

						String page = line_out.substring( line_out.indexOf( ':' )+1).trim();

						page = page.substring( page.indexOf( "://") + 3);

						int pos = page.indexOf( '/' );

						if ( pos >= 0 ){

							page = page.substring( pos );

						}else{

							page = "/";
						}

						String	port_str;

						if ( delegate_to_port == 80 || delegate_to_port == 443 ){

							port_str = "";

						}else{

							port_str = ":" + delegate_to_port;
						}

						line_out = "Referer: http" + (delegate_is_https?"s":"") + "://" + delegate_to_host + port_str + page;

					}else if ( lhs.equals( "cookie" )){

						String cookies_str = line_out.substring( line_out.indexOf( ':' )+1).trim();

						String[] cookies = cookies_str.split( ";" );

						String	cookies_out = "";

						for (int j=0;j<cookies.length;j++){

							String	cookie = cookies[j];

							String	name = cookie.split( "=" )[0].trim();

							if ( hasSetCookieName( name )){

								cookies_out += (cookies_out.length()==0?"":"; ") + cookie;

							}else{

								cookies_to_remove.add( name );
							}
						}

						if ( cookies_out.length() > 0 ){

							line_out = "Cookie: " + cookies_out;

						}else{

							line_out = null;
						}
					}
				}

				if ( line_out != null ){

					trace( "-> " + line_out );

					target_os.write((line_out+NL).getBytes());
				}
			}

			target_os.write( NL.getBytes());

			target_os.flush();

			new AEThread2( "HTTPSniffingProxy:proc:2", true )
			{
				@Override
				public void
				run()
				{
					try{
						InputStream	source_is = socket_in.getInputStream();

						byte[]	buffer = new byte[32000];

						while( !destroyed ){

							int	len = source_is.read( buffer );

							if ( len <= 0 ){

								break;
							}

							target_os.write( buffer, 0, len );

							trace( "POST:" + new String( buffer, 0, len ));

						}
					}catch( Throwable e ){
					}
				}
			}.start();

			InputStream	target_is = socket_out.getInputStream();

			OutputStream	source_os = socket_in.getOutputStream();

			String	reply_header = readHeader( target_is );

			String[]	reply_lines = splitHeader( reply_header );

			String	content_type	= null;
			String	content_charset	= "ISO-8859-1";

			for (int i=0;i<reply_lines.length;i++){

				String	line_in 	= reply_lines[i].trim().toLowerCase();

				String[] bits = line_in.split(":");

				if ( bits.length >= 2 ){

					String	lhs = bits[0].trim();

					if ( lhs.equals( "content-type" )){

						String rhs = reply_lines[i].substring( line_in.indexOf( ':' ) + 1 ).trim();

						String[] x = rhs.split( ";" );

						content_type = x[0];

						if ( x.length > 1 ){

							int	pos = rhs.toLowerCase().indexOf( "charset" );

							if ( pos >= 0 ){

								String cc = rhs.substring( pos+1 );

								pos = cc.indexOf('=');

								if ( pos != -1 ){

									cc = cc.substring( pos+1 ).trim();

									if ( Charset.isSupported( cc )){

										content_charset = cc;
									}
								}
							}
						}
					}
				}
			}

			boolean	rewrite 			= false;
			boolean	chunked				= false;
			String	content_encoding	= null;

			if ( content_type == null ){

				rewrite = true;

			}else{

				content_type = content_type.toLowerCase();

				if (content_type.contains("text/")){

					rewrite = true;
				}
			}

			for (int i=0;i<reply_lines.length;i++){

				String	line_out	= reply_lines[i];

				String	line_in 	= line_out.trim().toLowerCase();

				String[] bits = line_in.split(":");

				if ( bits.length >= 2 ){

					String	lhs = bits[0].trim();

					if ( lhs.equals( "set-cookie" )){

						String	cookies_in = line_out.substring( line_out.indexOf( ':' )+1 );

						String[] cookies;

						if (!cookies_in.toLowerCase().contains("expires")){

							cookies = cookies_in.split( "," );

						}else{

							cookies = new String[]{ cookies_in };
						}

						String	cookies_out = "";

						for (int c=0;c<cookies.length;c++){

							String	cookie = cookies[c];

							String[]	x = cookie.split( ";" );

							String	modified_cookie = "";

							for (int j=0;j<x.length;j++){

								String entry = x[j].trim();

								if ( entry.equalsIgnoreCase( "httponly" )){

									setHTTPOnlyCookieDetected();

								}else if ( entry.equalsIgnoreCase( "secure" )){

								}else if ( entry.toLowerCase().startsWith( "domain" )){

										// remove domain restriction so cookie sent to localhost

								}else if ( entry.toLowerCase().startsWith( "expires" )){

										// force to be session cookie otherwise we'll end up sending
										// cookies from multiple sites to 'localhost'
								}else{

									if ( j == 0 ){

										int pos = entry.indexOf( '=' );

										String name 	= entry.substring( 0, pos ).trim();
										String value 	= entry.substring( pos+1 ).trim();

										addSetCookieName( name, value );
									}

									modified_cookie += (modified_cookie.length()==0?"":"; ") + entry;
								}
							}

							cookies_out += (c==0?"":", " ) + modified_cookie;
						}

						line_out = "Set-Cookie: " + cookies_out;

					}else if ( lhs.equals( "set-cookie2" )){

							// http://www.ietf.org/rfc/rfc2965.txt

							// one or more comma separated

						String	cookies_in = line_out.substring( line_out.indexOf( ':' )+1 );

						String[] cookies = cookies_in.split( "," );

						String	cookies_out = "";

						for (int c=0;c<cookies.length;c++){

							String	cookie = cookies[c];

							String[]	x = cookie.split( ";" );

							String	modified_cookie = "";

							for (int j=0;j<x.length;j++){

								String entry = x[j].trim();

								if ( entry.equalsIgnoreCase( "secure" )){

								}else if ( entry.equalsIgnoreCase( "discard" )){

								}else if ( entry.toLowerCase().startsWith( "domain" )){

								}else if ( entry.toLowerCase().startsWith( "port" )){

								}else{

									if ( j == 0 ){

										int pos = entry.indexOf( '=' );

										String name = entry.substring( 0, pos ).trim();

										String value 	= entry.substring( pos+1 ).trim();

										addSetCookieName( name, value );
									}

									modified_cookie += (modified_cookie.length()==0?"":"; ") + entry;
								}
							}

							cookies_out += (c==0?"":", " ) + modified_cookie + "; Discard";
						}

						line_out = "Set-Cookie2: " + cookies_out;

					}else if ( lhs.equals( "connection" )){

						line_out = "Connection: close";

					}else if ( lhs.equals( "location" )){

						String page = line_out.substring( line_out.indexOf( ':' )+1).trim();

						String child_url = page.trim();

						HTTPAuthHelper child = getChild( child_url, false );

						int	pos = page.indexOf( "://" );

						if ( pos >= 0 ){

								// absolute

							page = page.substring( pos + 3);

							pos = page.indexOf( '/' );

							if ( pos >= 0 ){

								page = page.substring( pos );

							}else{

								page = "/";
							}
						}else{

								// relative. actually illegal as must be absolute

							if ( !page.startsWith( "/" )){

								String	temp = target_url;

								int marker = temp.indexOf( "://" );

								if ( marker != -1 ){

										// strip out absolute part

									temp = temp.substring( marker + 3 );

									marker = temp.indexOf( "/" );

									if ( marker == -1 ){

										temp = "/";

									}else{

										temp = temp.substring( marker );
									}
								}else{

									if ( !temp.startsWith( "/" )){

										temp = "/" + temp;
									}
								}

								marker = temp.lastIndexOf( "/" );

								if ( marker >= 0 ){

									temp = temp.substring( 0, marker+1 );
								}

								page = temp + page;
							}
						}

						line_out = "Location: http://127.0.0.1:" + child.getPort() + page;

					}else if ( lhs.equals( "content-encoding" )){

						if ( rewrite ){

							String	encoding = bits[1].trim();

		 					if ( 	encoding.equalsIgnoreCase( "gzip"  ) ||
		 							encoding.equalsIgnoreCase( "deflate" )){

				 				content_encoding = encoding;

				 				line_out = null;
		 					}
						}
					}else if ( lhs.equals( "content-length" )){

						if ( rewrite ){

							line_out = null;
						}
					}else if ( lhs.equals( "transfer-encoding" )){

						if (bits[1].contains("chunked")){

							chunked = true;

							if ( rewrite ){

								line_out = null;
							}
						}
	 				}
				}

				if ( line_out != null ){

					trace( "<- " + line_out );

					source_os.write((line_out+NL).getBytes());
				}
			}

			for ( int i=0;i<cookies_to_remove.size();i++ ){

				String	name = (String)cookies_to_remove.get(i);

				if ( !hasSetCookieName( name )){

					String	remove_str = "Set-Cookie: " + name + "=X; expires=Sun, 01 Jan 2000 01:00:00 GMT";

					trace( "<- (cookie removal) " + remove_str );

					source_os.write((remove_str+NL).getBytes());

					remove_str = "Set-Cookie2: " + name + "=X; Max-Age=0; Version=1";

					trace( "<- (cookie removal) " + remove_str );

					source_os.write((remove_str+NL).getBytes());
				}
			}

			byte[]	buffer = new byte[32000];


			if ( rewrite ){

				StringBuffer	sb = new StringBuffer();

				if ( chunked ){

						// chunking uses ISO-8859-1

					while( true ){

						int	len = target_is.read( buffer );

						if ( len <= 0 ){

							break;
						}

						sb.append(new String( buffer, 0, len, "ISO-8859-1" ));
					}

					StringBuilder sb_dechunked = new StringBuilder( sb.length());

					String chunk = "";

					int total_length = 0;

					int	sb_pos = 0;

					while( sb_pos < sb.length()){

						chunk += sb.charAt( sb_pos++ );

							// second time around the chunk will be prefixed with NL
							// from end of previous
							// so make sure we ignore this

						if ( chunk.endsWith( NL ) && chunk.length() > 2 ){

							int semi_pos = chunk.indexOf( ';' );

							if ( semi_pos != -1 ){

								chunk = chunk.substring( 0, semi_pos );
							}

							chunk = chunk.trim();

							int chunk_length = Integer.parseInt( chunk, 16 );

							if ( chunk_length <= 0 ){

								break;
							}

							total_length += chunk_length;

							if ( total_length > 2*1024*1024 ){

								throw (new IOException("Chunk size " + chunk_length
										+ " too large"));
							}

							char[] chunk_buffer = new char[chunk_length];

							sb.getChars( sb_pos, sb_pos + chunk_length, chunk_buffer, 0 );

							sb_dechunked.append( chunk_buffer );

							sb_pos += chunk_length;

							chunk = "";
						}
					}

						// dechunked ISO-8859-1 - unzip if required and then apply correct charset

					target_is = new ByteArrayInputStream( sb_dechunked.toString().getBytes( "ISO-8859-1" ));
				}

				if ( content_encoding != null ){

					if ( content_encoding.equalsIgnoreCase( "gzip"  )){

						target_is = new GZIPInputStream( target_is );

	 				}else if ( content_encoding.equalsIgnoreCase( "deflate" )){

	 					target_is = new InflaterInputStream( target_is );
	 				}
	 			}

				sb.setLength(0);

				while( !destroyed ){

					int	len = target_is.read( buffer );

					if ( len <= 0 ){

						break;
					}

					sb.append(new String( buffer, 0, len, content_charset ));
				}

				String 	str 	= sb.toString();
				String	lc_str 	= str.toLowerCase();

				StringBuffer	result 	= null;
				int				str_pos	= 0;

				// FileUtil.writeBytesAsFile( "C:\\temp\\xxx" + new Random().nextInt(100000) + ".txt", str.getBytes());

				while( true ){

						// http://a.b

					int	url_start = str.length() - str_pos >=10?lc_str.indexOf( "http", str_pos ):-1;

					if ( url_start == -1 ){

						break;
					}

					int	match_pos;

					if ( lc_str.charAt( url_start + 4 ) == 's' ){

						match_pos = url_start + 5;

					}else{

						match_pos = url_start + 4;
					}

					if ( lc_str.substring( match_pos, match_pos+3 ).equals( "://" )){

						int	url_end = -1;

						for (int i=match_pos+3;;i++){

							char c = lc_str.charAt(i);

							if ( c == '/' ){

								url_end = i+1;

								break;

							}else if ( c == '.' || c == '-' || c == ':' ){

							}else if ( c >= '0' && c <= '9' ){

							}else if ( c >= 'a' && c <= 'z' ){

							}else{

								url_end = i;

								break;
							}

							if ( i == lc_str.length()-1 ){

								url_end = i;
							}
						}

						if ( url_end > url_start ){

							String 	url_str = str.substring( url_start, url_end );

							boolean	appended = false;

							try{
									// make sure vald URL

								URL url = new URL( url_str );

								if ( url.getHost().length() > 0 ){

									boolean	existing_only = true;

										// override if form action or meta

									for (int i=url_start-1;i>=0&&url_start-i<512;i--){

										if ( lc_str.charAt( i ) == '<' ){

											String prefix = lc_str.substring(i, url_start);

											if (prefix.contains("form")){

												existing_only = false;

											}else if (prefix.contains("meta") &&
												prefix.contains("http-equiv")){

												existing_only = false;
											}

											break;
										}
									}

									HTTPAuthHelper child = getChild(  url_str, existing_only );

									if ( child != null ){

										String replacement = "http://127.0.0.1:" + child.getPort();

										if ( url_str.endsWith( "/" )){

											replacement += "/";
										}

										if ( result == null ){

											result = new StringBuffer( str.length());

											if ( url_start > 0 ){

												result.append( str.subSequence( 0, url_start ));
											}
										}else if ( url_start > str_pos ){

											result.append( str.subSequence( str_pos, url_start ));
										}

										trace( "Replacing " + url_str + " with " + replacement );

										result.append( replacement );

										appended = true;

									}else{

										trace( "    No child for " + url_str );
									}
								}
							}catch( Throwable e ){

							}

							if ( result != null && !appended ){

								result.append( str.subSequence( str_pos, url_end ));
							}

							str_pos = url_end;

						}else{

							break;
						}
					}else{

						if ( result != null ){

							result.append( str.subSequence( str_pos, match_pos ));
						}

						str_pos = match_pos;
					}
				}

				if ( result != null ){

					if ( str_pos < str.length() ){

						result.append( str.subSequence( str_pos, str.length()));
					}

					sb = result;
				}

				source_os.write( ( "Content-Length: " + sb.length() + NL ).getBytes());

				source_os.write( NL.getBytes());

				source_os.write( sb.toString().getBytes( content_charset ));

			}else{

				source_os.write( NL.getBytes());

				while( !destroyed  ){

					int	len = target_is.read( buffer );

					if ( len <= 0 ){

						break;
					}

					source_os.write( buffer, 0, len );
				}
			}
		}

		private String
		readHeader(
			InputStream		is )

			throws IOException
		{
			String	header = "";

			byte[]	buffer = new byte[1];

			boolean	found = false;

			while( true ){

				if ( is.read( buffer ) != 1 ){

					break;
				}

				header += (char)buffer[0];

				if ( header.endsWith( NL + NL )){

					found = true;

					break;
				}
			}

			if ( !found ){

				throw( new IOException( "End of stream reading header" ));
			}

			return( header );
		}

		private String[]
		splitHeader(
			String		str )
		{
			String[] bits = str.split( NL );

			return( bits );
		}

		private void
		destroy()
		{
			synchronized( this ){

				if ( destroyed ){

					return;
				}

				destroyed = true;
			}

			if ( socket_out != null ){

				try{
					socket_out.close();

				}catch( Throwable e ){

				}
			}
			try{
				socket_in.close();

			}catch( Throwable e ){
			}
		}
	}

	private void
	trace(
		String		str )
	{
		if ( TRACE ){

			System.out.println( str );
		}
	}
}
