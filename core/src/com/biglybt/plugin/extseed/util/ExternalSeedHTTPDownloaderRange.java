/*
 * Created on 16-Dec-2005
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

package com.biglybt.plugin.extseed.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import javax.net.ssl.*;

import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.proxy.AEProxyFactory.PluginProxy;
import com.biglybt.core.security.SEPasswordListener;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.RandomUtils;
import com.biglybt.plugin.extseed.ExternalSeedException;

public class
ExternalSeedHTTPDownloaderRange
	implements ExternalSeedHTTPDownloader, SEPasswordListener
{
	public static final String	NL = "\r\n";


	private final URL		very_original_url;
	private String			user_agent;

	private URL			redirected_url;
	private int			consec_redirect_fails;

	private int			last_response;
	private int			last_response_retry_after_secs;

	public
	ExternalSeedHTTPDownloaderRange(
		URL		_url,
		String	_user_agent )
	{
		very_original_url	= _url;
		user_agent			= _user_agent;
	}

	public URL
	getURL()
	{
		return( very_original_url );
	}

	@Override
	public void
	download(
		int									length,
		ExternalSeedHTTPDownloaderListener	listener,
		boolean								con_fail_is_perm_fail )

		throws ExternalSeedException
	{
		download( new String[0], new String[0], length, listener, con_fail_is_perm_fail );
	}

	@Override
	public void
	downloadRange(
		long								offset,
		int									length,
		ExternalSeedHTTPDownloaderListener	listener,
		boolean								con_fail_is_perm_fail )

		throws ExternalSeedException
	{
		// Android sets "Accept-Encoding" to "gzip" by default, which causes some servers to ignore the range request
		download(new String[] {
			"Range",
			"Accept-Encoding"
		}, new String[] {
			"bytes=" + offset + "-" + (offset + length - 1),
			"identity"
		}, length, listener, con_fail_is_perm_fail);
	}

	public void
	download(
		String[]							prop_names,
		String[]							prop_values,
		int									length,
		ExternalSeedHTTPDownloaderListener	listener,
		boolean								con_fail_is_perm_fail )

		throws ExternalSeedException
	{
		boolean	connected = false;

		InputStream	is	= null;

		String	outcome = "";

		PluginProxy		plugin_proxy	= null;

		boolean proxy_ok = false;

		try{
			SESecurityManager.setThreadPasswordHandler( this );

			if ( NetworkAdmin.getSingleton().hasMissingForcedBind()){

				throw( new ExternalSeedException( "Forced bind address is missing" ));
			}

			// System.out.println( "Connecting to " + url + ": " + Thread.currentThread().getId());

			HttpURLConnection	connection;
			int					response;

			Set<String>	redirect_urls = new HashSet<>();

redirect_loop:
			while( true ){

				URL	original_url 	= redirected_url==null?very_original_url:redirected_url;
				URL current_url		= original_url;

				if ( plugin_proxy != null ){

					plugin_proxy.setOK( true );

					plugin_proxy = null;
				}

				Proxy	current_proxy = null;

				if ( AENetworkClassifier.categoriseAddress( original_url.getHost()) != AENetworkClassifier.AT_PUBLIC ){

					plugin_proxy = AEProxyFactory.getPluginProxy( "webseed", original_url );

					if ( plugin_proxy != null ){

						current_url		= plugin_proxy.getURL();

						current_proxy	= plugin_proxy.getProxy();
					}
				}

				for ( int ssl_loop=0; ssl_loop<2; ssl_loop++ ){

					try{
						if ( current_proxy == null ){

							connection = (HttpURLConnection)current_url.openConnection();

						}else{

							connection = (HttpURLConnection)current_url.openConnection( current_proxy );
						}

						if ( connection instanceof HttpsURLConnection ){

							HttpsURLConnection ssl_con = (HttpsURLConnection)connection;

								// allow for certs that contain IP addresses rather than dns names

							ssl_con.setHostnameVerifier(
								new HostnameVerifier()
								{
									@Override
									public boolean
									verify(
										String		host,
										SSLSession	session )
									{
										return( true );
									}
								});

							TrustManager[] tms_delegate = SESecurityManager.getAllTrustingTrustManager();

							SSLContext sc = SSLContext.getInstance("SSL");

							sc.init( null, tms_delegate, RandomUtils.SECURE_RANDOM );

							SSLSocketFactory factory = sc.getSocketFactory();

							ssl_con.setSSLSocketFactory(factory);
						}

						connection.setRequestProperty( "Connection", "Keep-Alive" );
						connection.setRequestProperty( "User-Agent", user_agent );

						for (int i=0;i<prop_names.length;i++){

							connection.setRequestProperty( prop_names[i], prop_values[i] );
						}

						if ( plugin_proxy != null ){

							connection.setRequestProperty( "HOST", plugin_proxy.getURLHostRewrite() + (original_url.getPort()==-1?"":(":" + original_url.getPort())));
						}

						int	time_remaining	= listener.getPermittedTime();

						if ( time_remaining > 0 ){

							connection.setConnectTimeout( time_remaining );
						}

						connection.connect();

						time_remaining	= listener.getPermittedTime();

						if ( time_remaining < 0 ){

							throw( new IOException( "Timeout during connect" ));
						}

						connection.setReadTimeout( time_remaining );

						connected	= true;

						response = connection.getResponseCode();

						if (	response == HttpURLConnection.HTTP_ACCEPTED ||
								response == HttpURLConnection.HTTP_OK ||
								response == HttpURLConnection.HTTP_PARTIAL ){

							if ( redirected_url != null ){

								consec_redirect_fails = 0;
							}

							break redirect_loop;

						}else if ( 	response == HttpURLConnection.HTTP_MOVED_TEMP ||
									response == HttpURLConnection.HTTP_MOVED_PERM ){

								// auto redirect doesn't work from http to https or vice-versa

							String	move_to = connection.getHeaderField( "location" );

							if ( move_to != null ){

								if ( redirect_urls.contains( move_to ) || redirect_urls.size() > 32 ){

									throw( new ExternalSeedException( "redirect loop" ));
								}

								redirect_urls.add( move_to );

								redirected_url = new URL( move_to );

								continue redirect_loop;
							}
						}

						if ( redirected_url == null ){

							break redirect_loop;
						}

							// try again with original URL

						consec_redirect_fails++;

						redirected_url = null;

					}catch( SSLException e ){

						if ( ssl_loop == 0 ){

							if ( SESecurityManager.installServerCertificates( current_url ) != null ){

									// certificate has been installed

								continue;	// retry with new certificate
							}
						}

						throw( e );
					}

						// don't need another SSL loop

					break;
				}
			}

			if ( plugin_proxy == null ){

				URL final_url = connection.getURL();

				if ( consec_redirect_fails < 10 && !very_original_url.toExternalForm().equals( final_url.toExternalForm())){

					redirected_url = final_url;
				}
			}

			last_response	= response;

			last_response_retry_after_secs	= -1;

            if ( response == 503 ){

                	// webseed support for temp unavail - read the retry_after

            	long retry_after_date = connection.getHeaderFieldDate("Retry-After", -1L);

                if ( retry_after_date <= -1 ){

                	last_response_retry_after_secs = connection.getHeaderFieldInt("Retry-After", -1);

                }else{

                	last_response_retry_after_secs = (int)((retry_after_date - System.currentTimeMillis())/1000);

                	if ( last_response_retry_after_secs < 0 ){

                		last_response_retry_after_secs = -1;
                	}
                }
            }

			is = connection.getInputStream();

			proxy_ok = true;

			if ( 	response == HttpURLConnection.HTTP_ACCEPTED ||
					response == HttpURLConnection.HTTP_OK ||
					response == HttpURLConnection.HTTP_PARTIAL ){

				int	pos = 0;

				byte[]	buffer 		= null;
				int		buffer_pos	= 0;
				int		buffer_len	= 0;

				while( pos < length ){

					if ( buffer == null ){

						buffer 		= listener.getBuffer();
						buffer_pos	= listener.getBufferPosition();
						buffer_len	= listener.getBufferLength();
					}

					listener.setBufferPosition( buffer_pos );

					int	to_read = buffer_len - buffer_pos;

					int	permitted = listener.getPermittedBytes();

					if ( permitted < to_read ){

						to_read	= permitted;
					}

					int	len = is.read( buffer, buffer_pos, to_read );

					if ( len < 0 ){

						break;
					}

					listener.reportBytesRead( len );

					pos	+= len;

					buffer_pos	+= len;

					if ( buffer_pos == buffer_len ){

						listener.done();

						buffer		= null;
						buffer_pos	= 0;
					}
				}

				if ( pos != length ){

					String	log_str;

					if ( buffer == null ){

						log_str = "No buffer assigned";

					}else{

						log_str =  new String( buffer, 0, length );

						if ( log_str.length() > 64 ){

							log_str = log_str.substring( 0, 64 );
						}
					}

					outcome = "Connection failed: data too short - " + length + "/" + pos + " [" + log_str + "]";

					throw( new ExternalSeedException( outcome ));
				}

				outcome = "read " + pos + " bytes";

				// System.out.println( "download length: " + pos );

			}else{

				outcome = "Connection failed: " + connection.getResponseMessage();

				ExternalSeedException	error = new ExternalSeedException( outcome );

				error.setPermanentFailure( true );

				throw( error );
			}
		}catch( IOException e ){

			if ( con_fail_is_perm_fail && !connected ){

				outcome = "Connection failed: " + e.getMessage();

				ExternalSeedException	error = new ExternalSeedException( outcome );

				error.setPermanentFailure( true );

				throw( error );

			}else{

				outcome =  "Connection failed: " + Debug.getNestedExceptionMessage( e );

                if ( last_response_retry_after_secs >= 0){

                    outcome += ", Retry-After: " + last_response_retry_after_secs + " seconds";
                }

				ExternalSeedException excep = new ExternalSeedException( outcome, e );

				if ( e instanceof FileNotFoundException ){

					excep.setPermanentFailure( true );
				}

				throw( excep );
			}
		}catch( Throwable e ){

			if ( e instanceof ExternalSeedException ){

				throw((ExternalSeedException)e);
			}

			outcome = "Connection failed: " + Debug.getNestedExceptionMessage( e );

			throw( new ExternalSeedException("Connection failed", e ));

		}finally{

			SESecurityManager.unsetThreadPasswordHandler();

			// System.out.println( "Done to " + url + ": " + Thread.currentThread().getId() + ", outcome=" + outcome );

			if ( is != null ){

				try{
					is.close();

				}catch( Throwable e ){

				}
			}

			if ( plugin_proxy != null ){

				plugin_proxy.setOK( proxy_ok );
			}
		}
	}

	@Override
	public void
	downloadSocket(
		int									length,
		ExternalSeedHTTPDownloaderListener	listener,
		boolean								con_fail_is_perm_fail )

	    throws ExternalSeedException
	{
		downloadSocket( new String[0], new String[0], length, listener, con_fail_is_perm_fail );
	}

	public void
	downloadSocket(
		String[]							prop_names,
		String[]							prop_values,
		int									length,
		ExternalSeedHTTPDownloaderListener	listener,
		boolean								con_fail_is_perm_fail )

		throws ExternalSeedException
	{
		Socket	socket	= null;

		boolean	connected = false;

		PluginProxy		plugin_proxy 	= null;
		boolean			proxy_ok		= false;

		try{
			String	output_header =
				"GET " + very_original_url.getPath() + "?" + very_original_url.getQuery() + " HTTP/1.1" + NL +
				"Host: " + very_original_url.getHost() + (very_original_url.getPort()==-1?"":( ":" + very_original_url.getPort())) + NL +
				"Accept: */*" + NL +
				"Connection: Close" + NL +	// if we want to support keep-alive we'll need to implement a socket cache etc.
				"User-Agent: " + user_agent + NL;

			for (int i=0;i<prop_names.length;i++){

				output_header += prop_names[i] + ":" + prop_values[i] + NL;
			}

			output_header += NL;

			int	time_remaining	= listener.getPermittedTime();

			URL	original_url 	= very_original_url;
			URL current_url		= original_url;

			Proxy	current_proxy = null;

			if ( AENetworkClassifier.categoriseAddress( very_original_url.getHost()) != AENetworkClassifier.AT_PUBLIC ){

				plugin_proxy = AEProxyFactory.getPluginProxy( "webseed", original_url );

				if ( plugin_proxy != null ){

					current_url		= plugin_proxy.getURL();

					current_proxy	= plugin_proxy.getProxy();
				}
			}

			if ( time_remaining > 0 ){

				if ( current_proxy == null ){

					socket = new Socket();

				}else{

					socket = new Socket( current_proxy );
				}

				socket.connect( new InetSocketAddress( current_url.getHost(), current_url.getPort()==-1?current_url.getDefaultPort():current_url.getPort()), time_remaining );

			}else{

				if ( current_proxy == null ){

					socket = new Socket(  current_url.getHost(), current_url.getPort()==-1?current_url.getDefaultPort():current_url.getPort());

				}else{

					socket = new Socket( current_proxy );

					socket.connect( new InetSocketAddress( current_url.getHost(), current_url.getPort()==-1?current_url.getDefaultPort():current_url.getPort()));
				}
			}

			connected	= true;

			proxy_ok = true;

			time_remaining	= listener.getPermittedTime();

			if ( time_remaining < 0 ){

				throw( new IOException( "Timeout during connect" ));

			}else if ( time_remaining > 0 ){

				socket.setSoTimeout( time_remaining );
			}

			OutputStream	os = socket.getOutputStream();

			os.write( output_header.getBytes( "ISO-8859-1" ));

			os.flush();

			InputStream is = socket.getInputStream();

			try{
				String	input_header = "";

				while( true ){

					byte[]	buffer = new byte[1];

					int	len = is.read( buffer );

					if ( len < 0 ){

						throw( new IOException( "input too short reading header" ));
					}

					input_header	+= (char)buffer[0];

					if ( input_header.endsWith(NL+NL)){

						break;
					}
				}

				// HTTP/1.1 403 Forbidden

				int	line_end = input_header.indexOf(NL);

				if ( line_end == -1 ){

					throw( new IOException( "header too short" ));
				}

				String	first_line = input_header.substring(0,line_end);

				StringTokenizer	tok = new StringTokenizer(first_line, " " );

				tok.nextToken();

				int	response = Integer.parseInt( tok.nextToken());

				last_response	= response;

				last_response_retry_after_secs	= -1;

				String	response_str	= tok.nextToken();

				if ( 	response == HttpURLConnection.HTTP_ACCEPTED ||
						response == HttpURLConnection.HTTP_OK ||
						response == HttpURLConnection.HTTP_PARTIAL ){

					byte[]	buffer 		= null;
					int		buffer_pos	= 0;
					int		buffer_len	= 0;

					int	pos = 0;

					while( pos < length ){

						if ( buffer == null ){

							buffer 		= listener.getBuffer();
							buffer_pos	= listener.getBufferPosition();
							buffer_len	= listener.getBufferLength();
						}

						int	to_read = buffer_len - buffer_pos;

						int	permitted = listener.getPermittedBytes();

						if ( permitted < to_read ){

							to_read	= permitted;
						}

						int	len = is.read( buffer, buffer_pos, to_read );

						if ( len < 0 ){

							break;
						}

						listener.reportBytesRead( len );

						pos	+= len;

						buffer_pos	+= len;

						if ( buffer_pos == buffer_len ){

							listener.done();

							buffer		= null;
							buffer_pos	= 0;
						}
					}

					if ( pos != length ){

						String	log_str;

						if ( buffer == null ){

							log_str = "No buffer assigned";

						}else{

							log_str =  new String( buffer, 0, buffer_pos>64?64:buffer_pos );
						}

						throw( new ExternalSeedException("Connection failed: data too short - " + length + "/" + pos + " [last=" + log_str + "]" ));
					}

					// System.out.println( "download length: " + pos );

				}else if ( 	response == 503 ){

						// webseed support for temp unavail - read the data

					String	data_str = "";

					while( true ){

						byte[]	buffer = new byte[1];

						int	len = is.read( buffer );

						if ( len < 0 ){

							break;
						}

						data_str += (char)buffer[0];
					}

					last_response_retry_after_secs = Integer.parseInt( data_str );

						// this gets trapped below and turned into an appropriate ExternalSeedException

					throw( new IOException( "Server overloaded" ));

				}else{

					ExternalSeedException	error = new ExternalSeedException("Connection failed: " + response_str );

					error.setPermanentFailure( true );

					throw( error );
				}
			}finally{

				is.close();
			}

		}catch( IOException e ){

			if ( con_fail_is_perm_fail && !connected ){

				ExternalSeedException	error = new ExternalSeedException("Connection failed: " + e.getMessage());

				error.setPermanentFailure( true );

				throw( error );

			}else{

				String outcome =  "Connection failed: " + Debug.getNestedExceptionMessage( e );

				if ( last_response_retry_after_secs >= 0 ){

					outcome += ", Retry-After: " + last_response_retry_after_secs + " seconds";
	            }

				throw( new ExternalSeedException( outcome, e ));
			}
		}catch( Throwable e ){

			if ( e instanceof ExternalSeedException ){

				throw((ExternalSeedException)e);
			}

			throw( new ExternalSeedException("Connection failed", e ));

		}finally{

			if ( socket != null ){

				try{
					socket.close();

				}catch( Throwable e ){
				}
			}

			if ( plugin_proxy != null ){

				plugin_proxy.setOK( proxy_ok );
			}
		}
	}

	@Override
	public void
	deactivate()
	{
	}

	@Override
	public PasswordAuthentication
	getAuthentication(
		String		realm,
		URL			tracker )
	{
		return( null );
	}

	@Override
	public void
	setAuthenticationOutcome(
		String		realm,
		URL			tracker,
		boolean		success )
	{
	}

	@Override
	public void
	clearPasswords()
	{
	}

	@Override
	public int
	getLastResponse()
	{
		return( last_response );
	}

	@Override
	public int
	getLast503RetrySecs()
	{
		return( last_response_retry_after_secs );
	}

	public static void
	main(
		String[]		args )
	{
		try{
			String	url_str = "";

			ExternalSeedHTTPDownloader downloader =

				new ExternalSeedHTTPDownloaderRange(
						new URL( url_str ),
						Constants.APP_NAME);

			downloader.downloadRange(
				0, 1,
				new ExternalSeedHTTPDownloaderListener()
				{
					private int	position;

					@Override
					public byte[]
		        	getBuffer()

		        		throws ExternalSeedException
		        	{
						return( new byte[1024] );
		        	}

		        	@Override
			        public void
		        	setBufferPosition(
		        		int	_position )
		        	{
		        		position = _position;
		        	}

		        	@Override
			        public int
		        	getBufferPosition()
		        	{
		        		return( position );
		        	}

		        	@Override
			        public int
		        	getBufferLength()
		        	{
		        		return( 1024 );
		        	}

		        	@Override
			        public int
		        	getPermittedBytes()

		        		throws ExternalSeedException
		        	{
		        		return( 1024 );
		        	}

		        	@Override
			        public int
		           	getPermittedTime()
		        	{
		        		return( Integer.MAX_VALUE );
		        	}

		        	@Override
			        public void
		        	reportBytesRead(
		        		int		num )
		        	{
		        		System.out.println( "read " + num );
		        	}

		        	@Override
			        public boolean
		        	isCancelled()
		        	{
		        		return false;
		        	}

		        	@Override
			        public void
		        	done()
		        	{
		        		System.out.println( "done" );
		        	}
				},
				true );

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}
}
