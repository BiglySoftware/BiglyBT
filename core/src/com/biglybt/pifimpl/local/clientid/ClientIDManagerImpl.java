/*
 * Created on 29-Dec-2004
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

package com.biglybt.pifimpl.local.clientid;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.*;

import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.util.*;
import com.biglybt.pif.clientid.ClientIDException;
import com.biglybt.pif.clientid.ClientIDGenerator;
import com.biglybt.pif.clientid.ClientIDManager;

/**
 * @author parg
 *
 */

public class
ClientIDManagerImpl
	implements ClientIDManager
{
	private static final LogIDs LOGID = LogIDs.PLUGIN;
	protected static ClientIDManagerImpl	singleton = new ClientIDManagerImpl();

	protected static final char		CR			= '\015';
	protected static final char		FF			= '\012';
	protected static final String	NL			= "\015\012";

	private static final int	connect_timeout;
	private static final int	read_timeout;

	static{
	  	String	connect_timeout_str = System.getProperty("sun.net.client.defaultConnectTimeout");
	  	String	read_timeout_str 	= System.getProperty("sun.net.client.defaultReadTimeout");

	  	int	ct = 60*1000;
	  	int rt = 60*1000;

	  	try{
	  		ct = Integer.parseInt( connect_timeout_str );
	  	}catch( Throwable e ){

	  	}

	  	try{
	  		rt = Integer.parseInt( read_timeout_str );
	  	}catch( Throwable e ){

	  	}

	  	connect_timeout	= ct;
	  	read_timeout	= rt;
	}

	public static ClientIDManagerImpl
	getSingleton()
	{
		return( singleton );
	}

	private ClientIDGenerator		generator;
	private volatile boolean		use_filter;
	private boolean					filter_override;
	private ThreadPool				thread_pool;

	private Object					filter_lock = new Object();
	private int						filter_port;

	public void
	setGenerator(
		ClientIDGenerator	_generator,
		boolean				_use_filter )
	{
		generator	= _generator;
		use_filter	= _use_filter;

			// we override the filter parameter here if we have a local bind IP set as
			// this is the only simple solution to enforcing the local bind (Sun's
			// HTTPConnection doesn't allow the network interface to be bound)


		if ( !use_filter ){

				// another reason for NOT doing this is if the user has a defined proxy
				// in this case the assumption is that they know what they're doing and
				// the proxy will be bound correctly to ensure that things work...

			String	http_proxy 	= System.getProperty( "http.proxyHost" );
			String	socks_proxy = System.getProperty( "socksProxyHost" );

			NetworkAdmin network_admin = NetworkAdmin.getSingleton();

		    if ( network_admin.mustBind()){

		    	filter_override = true;

		    	use_filter = true;

		    }else{

			    InetAddress bindIP = network_admin.getSingleHomedServiceBindAddress();


		        if (	( http_proxy == null || http_proxy.trim().length() == 0 ) &&
		        		( socks_proxy == null || socks_proxy.trim().length() == 0 ) &&
		        		( bindIP != null  && !bindIP.isAnyLocalAddress())){


		        	int		ips = 0;

		        		// seeing as this is a bit of a crappy way to enforce binding, add one more check to make
		        		// sure that the machine has multiple ips before going ahead in case user has set it
		        		// incorrectly

		        	try{
		    			List<NetworkInterface>	x = NetUtils.getNetworkInterfaces();

		    			for ( NetworkInterface network_interface: x ){

		        			Enumeration<InetAddress> addresses = network_interface.getInetAddresses();

		        			while( addresses.hasMoreElements()){

		        				InetAddress address = addresses.nextElement();

		        				if ( !address.isLoopbackAddress()){

		        					ips++;
		        				}
		        			}
		        		}
		        	}catch( Throwable e ){

		        		Logger.log(new LogEvent(LOGID, "", e));
		        	}

		        	if ( ips > 1 ){

			        	filter_override	= true;

			        	use_filter	= true;

			        	if (Logger.isEnabled())
			        		Logger.log(new LogEvent(LOGID,
			        				"ClientIDManager: overriding filter "
			        				+ "option to support local bind IP"));
		        	}
		        }
	        }
		}

		setupFilter( false );
	}

	private void
	setupFilter(
		boolean	force )
	{
		synchronized( filter_lock ){

			if ( !use_filter ){

				if ( force ){

					use_filter = true;

				}else{

					return;
				}
			}

			if ( filter_port != 0 ){

				return;
			}

			try{
				thread_pool = new ThreadPool( "ClientIDManager", 32 );

			  	int	timeout = connect_timeout + read_timeout;

				thread_pool.setExecutionLimit( timeout );

				final ServerSocket ss = new ServerSocket( 0, 1024, InetAddress.getByName("127.0.0.1"));

				filter_port	= ss.getLocalPort();

				ss.setReuseAddress(true);

				new AEThread2("ClientIDManager::filterloop")
				{
					@Override
					public void
					run()
					{
						long	failed_accepts		= 0;

						while(true){

							try{
								Socket socket = ss.accept();

								failed_accepts = 0;

								thread_pool.run( new httpFilter( socket ));

							}catch( Throwable e ){

								failed_accepts++;

								if (Logger.isEnabled())
									Logger.log(new LogEvent(LOGID,
											"ClientIDManager: listener failed on port "
													+ filter_port, e ));

								if ( failed_accepts > 10  ){

										// looks like its not going to work...
										// some kind of socket problem

									Logger.logTextResource(new LogAlert(LogAlert.UNREPEATABLE,
										LogAlert.AT_ERROR, "Network.alert.acceptfail"),
										new String[] { "" + filter_port, "TCP" });

									use_filter	= false;

									break;
								}
							}
						}
					}
				}.start();

				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID,
							"ClientIDManager: listener established on port " + filter_port));

			}catch( Throwable e){

				Logger.logTextResource(new LogAlert(LogAlert.UNREPEATABLE,
						LogAlert.AT_ERROR, "Tracker.alert.listenfail"), new String[] { ""
						+ filter_port + " (client-id)"});

				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID,
							"ClientIDManager: listener failed on port " + filter_port, e));

				use_filter	= false;
			}
		}
	}

	@Override
	public ClientIDGenerator
	getGenerator()
	{
		return( generator );
	}

	public byte[]
	generatePeerID(
		byte[]		hash,
		boolean		for_tracker )

		throws ClientIDException
	{
		return( generator.generatePeerID( hash, for_tracker ));
	}

	public Object
	getProperty(
		byte[]		hash,
		String		property_name )
	{
		return( generator.getProperty( hash, property_name));
	}

	public void
	generateHTTPProperties(
		byte[]		hash,
		Properties	properties )

		throws ClientIDException
	{
		Boolean sni_hack = (Boolean)properties.get( ClientIDGenerator.PR_SNI_HACK );

		if ( sni_hack != null && sni_hack ){

			if ( !use_filter ){

				setupFilter( true );
			}
		}

		boolean	filter_it = use_filter;

		if ( filter_it ){

			URL		url 	= (URL)properties.get( ClientIDGenerator.PR_URL );

			String protocol = url.getProtocol();
			String host 	= url.getHost();

			if ( 	host.equals( "127.0.0.1" ) ||
					protocol.equals( "ws" ) || protocol.equals( "wss" ) ||
					AENetworkClassifier.categoriseAddress( host ) != AENetworkClassifier.AT_PUBLIC ){

				filter_it = false;

			}else{

				Proxy	proxy 	= (Proxy)properties.get( ClientIDGenerator.PR_PROXY );

				if ( proxy != null && proxy.type() == Proxy.Type.SOCKS ){

					InetSocketAddress address = (InetSocketAddress)proxy.address();

					if ( address.getAddress().isLoopbackAddress()){

						filter_it = false;
					}
				}
			}
		}

		generator.generateHTTPProperties( hash, properties );

		if ( filter_it ){

			URL		url 	= (URL)properties.get( ClientIDGenerator.PR_URL );

			try{
				String protocol = url.getProtocol();
				
				String prefix = "";
				
				if ( protocol.equalsIgnoreCase( "trackerlist" )){
					
						// hack to prevent direct use of tracker lists
					
					String temp = url.toExternalForm();
					
					url = new URL( temp.substring( temp.indexOf( ":" ) +1 ));
					
					prefix = "trackerlist:";
				}
				
				boolean	is_ssl = url.getProtocol().toLowerCase().equals( "https" );

				String	url_str = url.toString();

				String	target_host = url.getHost();
				int		target_port	= url.getPort();

				if ( target_port == -1 ){

					target_port = url.getDefaultPort();
				}

				String hash_str;

				if ( hash == null ){
					hash_str = "";
				}else{
					hash_str = URLEncoder.encode(new String( hash, "ISO-8859-1" ), "ISO-8859-1" ).replaceAll("\\+", "%20");
				}

				int host_pos = url_str.indexOf( target_host );

				String	new_url = url_str.substring(0,host_pos) + "127.0.0.1:" + filter_port;

				if ( is_ssl ){

					new_url = "http" + new_url.substring( new_url.indexOf( ':' ));
				}

				String	rem = url_str.substring( host_pos + target_host.length());

				if ( !rem.isEmpty() && rem.charAt(0) == ':' ){

					rem = rem.substring( (""+ target_port ).length() + 1 );
				}

				int q_pos = rem.indexOf( '?' );

				String details = "cid=" + (is_ssl?".":"") + target_host + ":" + target_port + "+" + hash_str;

				if ( q_pos == -1 ){

					new_url += rem + "?" + details;

				}else{
					new_url += rem.substring(0,q_pos+1) + details + "&" + rem.substring(q_pos+1);
				}

				try{
				
					URL redirect_url = new URL( prefix + new_url );
										
					properties.put( ClientIDGenerator.PR_URL, redirect_url );

				}catch( MalformedURLException e ){
					
					// get this for borked tracker URLs, don't need to log as it'll fail later during announce
				}
			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	protected class
	httpFilter
		extends ThreadPoolTask
	{
		private Socket		socket;

		protected
		httpFilter(
			Socket		_socket )
		{
			socket	= _socket;
		}

		@Override
		public void
		runSupport()
		{
			String		report_error	= null;
			int			written			= 0;

			boolean	looks_like_tracker_request = false;

			try{

				setTaskState( "reading header" );

				InputStream	is = socket.getInputStream();

				byte[]	buffer = new byte[1024];

				String	header = "";

				while(true ){

					int	len = is.read(buffer);

					if ( len == -1 ){

						break;
					}

					header += new String( buffer, 0, len, Constants.BYTE_ENCODING_CHARSET );

					if ( 	header.endsWith( NL+NL ) ||
						header.contains(NL + NL)){

						break;
					}
				}

				List<String>	lines = new ArrayList<>();

				int	pos = 0;

				while( true){

					int	p1 = header.indexOf( NL, pos );

					String	line;

					if ( p1 == -1 ){

						line = header.substring(pos);

					}else{

						line = header.substring( pos, p1 );
					}

					line = line.trim();

					if ( line.length() > 0 ){

						lines.add( line );
					}

					if ( p1 == -1 ){

						break;
					}

					pos = p1+2;
				}


				String[]	lines_in = new String[ lines.size()];

				lines.toArray( lines_in );

				String	get = lines_in[0];

				int	p1 = get.indexOf( "?cid=" );
				int	p2 = get.indexOf( "&", p1 );

				if( p2 == -1 ){

					p2 = get.indexOf( ' ', p1 );
				}

				String	cid = get.substring( p1+5, p2 );

				int	p3 = cid.lastIndexOf( ":" );

				if ( p3 == -1 ){
					
					Debug.out( "eh?" );
				}
				
				String	target_host	= cid.substring( 0, p3 );

				String[] port_hash =  cid.substring(p3+1).split( "\\+" );

				int		target_port	= Integer.parseInt(port_hash[0]);

				String  hash_str	= port_hash.length==1?"":port_hash[1];

				byte[] hash = hash_str.length()==0?null:URLDecoder.decode( port_hash[1], "ISO-8859-1" ).getBytes( "ISO-8859-1" );

				looks_like_tracker_request = hash != null;

				boolean	is_ssl;

				if ( target_host.startsWith( "." )){

					is_ssl = true;

					target_host = target_host.substring(1);

				}else{

					is_ssl = false;
				}
					// fix up the Host: entry with the target details

				for (int i=1;i<lines_in.length;i++){

					String	line = lines_in[i];

					if (line.toLowerCase().contains("host:")){

						lines_in[i] = "Host: " + target_host + ":" + target_port;

						break;
					}
				}

					// code above makes a bit of a mess of things when the original URL
					// had no parameters (e.g. http://a.b.c/) - tidy it up
					// note we currently turn http://a.b.c/? into http://a.b.c/ 
				
				String get_lhs = get.substring( 0, p1+1 ).trim();
				String get_rhs = get.substring( p2+1 ).trim();
				
				if ( get_lhs.endsWith( "?" )){
					
					get_lhs = get_lhs.substring( 0, get_lhs.length()-1 );
				}
				
				get = get_lhs + " " + get_rhs;

				lines_in[0] = get;

				String[]	lines_out;

				if ( filter_override ){

						// bodge for ip override. we still need to take account of the correct
						// user-agent

					lines_out = lines_in;

					Properties p = new Properties();

					generator.generateHTTPProperties( hash, p );

					String	agent = p.getProperty( ClientIDGenerator.PR_USER_AGENT );

					if ( agent != null ){

						for (int i=0;i<lines_out.length;i++){

							if ( lines_out[i].toLowerCase().startsWith( "user-agent" )){

								lines_out[i] = "User-Agent: " + agent;
							}
						}
					}

					lines_out = generator.filterHTTP( hash, lines_out.clone());

				}else{

					lines_out = generator.filterHTTP( hash, lines_in );
				}

				String	header_out = "";

				for (int i=0;i<lines_out.length;i++){

					header_out += lines_out[i] + NL;
				}

				header_out += NL;

				Socket	target =
						UrlUtils.connectSocketAndWrite(
							is_ssl,
							target_host,
							target_port,
							header_out.getBytes(Constants.BYTE_ENCODING_CHARSET ),
							connect_timeout,
							read_timeout );

				try{
					target.getOutputStream().flush();

					InputStream	target_is = target.getInputStream();

						// meh, need to support 301/302 redirects here (and apparently now 307, changed to pick up 30x codes

					String reply_header = "";

					byte[] temp = new byte[1];

					while( true ){

						int	len = target_is.read( temp );

						if ( len != 1 ){

							throw( new ClientIDException( "EOF while reading reply header" ));
						}

						reply_header += new String(temp,"ISO-8859-1" );

						if ( temp[0] == '\n' && reply_header.endsWith( "\r\n\r\n" )){

							break;
						}
					}
					
					String[] reply_lines = reply_header.trim().split( "\r\n" );

					String line1 = reply_lines[0];

					line1 = line1.substring( line1.indexOf( ' ' ) + 1).trim();

					if ( line1.startsWith( "30" )){

						for ( int i=1;i<reply_lines.length;i++){

							String line = reply_lines[i];

							if ( line.toLowerCase( Locale.US ).startsWith( "location:" )){

								String redirect_url = line.substring( 9  ).trim();

								String lc_redirect_url = redirect_url.toLowerCase( Locale.US );

								if ( lc_redirect_url.startsWith( "http:" ) || lc_redirect_url.startsWith( "https:" )){

									// absolute, nothing to do

								}else{

										// relative

									String prefix = "http" + (is_ssl?"s":"") + "://" + target_host + ":" + target_port;

									if ( redirect_url.startsWith( "/" )){

										redirect_url = prefix + redirect_url;

									}else{

										String get_line = lines.get(0);

										get_line = get_line.substring( get_line.indexOf( ' ' ) + 1 ).trim();

										get_line = get_line.substring( 0, get_line.indexOf( ' ' )).trim();

										int	x_pos = get_line.indexOf( '?' );

										if ( x_pos != -1 ){

											get_line = get_line.substring( 0, x_pos );
										}

										x_pos = get_line.lastIndexOf( '/' );

										if ( x_pos == -1 ){

											redirect_url = prefix + "/" + redirect_url;

										}else{

											redirect_url = prefix + get_line.substring( 0, x_pos + 1 ) + redirect_url;
										}
									}
								}

								Properties	http_properties = new Properties();

						 		http_properties.put( ClientIDGenerator.PR_URL, new URL( redirect_url ));

						 		generateHTTPProperties( hash, http_properties );

						 		URL updated = (URL)http_properties.get( ClientIDGenerator.PR_URL );

						 		reply_lines[i] = "Location: " + updated.toExternalForm();
							}
						}
					}

					OutputStream os = socket.getOutputStream();

					for ( String str: reply_lines ){

						os.write((str + "\r\n" ).getBytes( "ISO-8859-1" ));
					}

					os.write( "\r\n" .getBytes( "ISO-8859-1" ));

					while( true ){

						int	len = target_is.read( buffer );

						if ( len == -1 ){

							break;
						}

						os.write( buffer, 0,len );

						written += len;
					}
				}finally{

					target.close();
				}
			}catch( ClientIDException e ){

				report_error = e.getMessage();

			}catch( UnknownHostException e ){

				report_error = "Unknown host " + e.getMessage();

			}catch( IOException e ){

				report_error = e.getMessage();
				
				// don't log these as common

			}catch( UnsupportedAddressTypeException e ){

				report_error = e.getMessage();

			}catch( Throwable e ){

				Debug.printStackTrace(e);

			}finally{

				if ( report_error != null && written == 0 && looks_like_tracker_request ){

					Map	failure = new HashMap();

					failure.put( "failure reason", report_error );

					try{
						byte[] x = BEncoder.encode( failure );

						OutputStream os = socket.getOutputStream();

						String[] reply_lines = {

								"HTTP/1.1 200 OK",
								"Content-Length: " + x.length,
								"Connection: close"
						};

						for ( String str: reply_lines ){

							os.write((str + "\r\n" ).getBytes( "ISO-8859-1" ));
						}

						os.write( "\r\n" .getBytes( "ISO-8859-1" ));

						os.write( x );

					}catch( Throwable f ){

						//Debug.printStackTrace(f);
					}
				}

				try{
					socket.getOutputStream().flush();

					socket.close();

				}catch( Throwable f ){

				}
			}
		}

		@Override
		public void
		interruptTask()
		{
			try{
/*
				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, "ClientIDManager - interrupting "
							+ "HTTP filter due to timeout"));
*/
				socket.close();

			}catch( Throwable e ){

			}
		}
	}
}
