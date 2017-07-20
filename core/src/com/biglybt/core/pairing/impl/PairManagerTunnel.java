/*
 * Created on Dec 6, 2012
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


package com.biglybt.core.pairing.impl;

import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.AlgorithmParameters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.biglybt.core.pairing.PairedServiceRequestHandler;
import com.biglybt.core.util.*;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderFactory;
import com.biglybt.pifimpl.local.utils.resourcedownloader.ResourceDownloaderFactoryImpl;

public class
PairManagerTunnel
{
	static final ResourceDownloaderFactory rdf = ResourceDownloaderFactoryImpl.getSingleton();

	final PairingManagerTunnelHandler		tunnel_handler;
	private final String							tunnel_key;

	private final InetAddress						originator;
	private final String							sid;
	private final PairedServiceRequestHandler		request_handler;
	private final SecretKeySpec					key;
	final String							tunnel_url;
	private final String							endpoint_url;

	private long	last_active = SystemTime.getMonotonousTime();

	private volatile boolean				close_requested;

	private final long	create_time	= SystemTime.getMonotonousTime();

	private long	last_request_time;

	private long	request_count;
	private long	bytes_in;
	private long	bytes_out;

	private long	last_fail_duration_secs = 0;
	private int		consec_fails			= 0;

	protected
	PairManagerTunnel(
		PairingManagerTunnelHandler		_tunnel_handler,
		String							_tunnel_key,
		InetAddress						_originator,
		String							_sid,
		PairedServiceRequestHandler		_request_handler,
		SecretKeySpec					_key,
		String							_tunnel_url,
		String							_endpoint_url )
	{
		tunnel_handler	= _tunnel_handler;
		tunnel_key		= _tunnel_key;
		originator		= _originator;
		sid				= _sid;
		request_handler	= _request_handler;
		key				= _key;
		tunnel_url		= _tunnel_url;
		endpoint_url	= _endpoint_url;

		new AEThread2( "PairManagerTunnel:runner" )
		{
			@Override
			public void
			run()
			{
				try{
					String	current_reply_params	= null;
					byte[]	current_reply_data 	 	= null;

					while( !close_requested ){

						if ( consec_fails > 1 ){

							try{
								Thread.sleep(( 1 << (consec_fails-1)) * 1000 );

							}catch( Throwable e ){
							}
						}

						long	start_time = SystemTime.getMonotonousTime();

						try{

							String url_str = tunnel_url + "?server=true" + (current_reply_params==null?"":current_reply_params );

							if ( last_fail_duration_secs > 0 ){

								url_str += "&last_fail=" + last_fail_duration_secs;

								last_fail_duration_secs = 0;
							}

							byte[]	bytes_to_send = current_reply_data==null?new byte[0]:current_reply_data;

							bytes_out += bytes_to_send.length;

							ResourceDownloader rd = rdf.create( new URL( url_str ), bytes_to_send );

							rd.setProperty( "URL_Connection", "Keep-Alive" );
							rd.setProperty( "URL_Read_Timeout", 5*60*1000 );

							byte[] data = FileUtil.readInputStreamAsByteArray( rd.download());

							if ( close_requested ){

								break;
							}

							bytes_in += data.length;

							long now = SystemTime.getMonotonousTime();

							last_active = now;

							current_reply_params 	= null;
							current_reply_data		= null;

							List<String> cookies = (List<String>)rd.getProperty( "URL_Set-Cookie" );

							boolean cookie_found = false;

							if ( cookies != null ){

								for ( String cookie: cookies ){

									final String name = "vuze_pair_server_reqs=";

									if ( cookie.startsWith( name )){

										cookie_found = true;

										String value = cookie.substring( name.length());

										int	pos = value.indexOf( ';' );

										value = value.substring( 0, pos );

										String[]	bits = value.split( "&" );

										if ( bits.length > 0 ){

											current_reply_params	= "";

											int	data_pos = 0;

											List<byte[]>	replies 		= new ArrayList<>();
											int				reply_length 	= 0;

											for ( String bit: bits ){

												String[] temp = bit.split( "=" );

												if ( temp.length == 2 ){

													String	lhs = temp[0].toLowerCase();

													if ( lhs.startsWith( "seq" )){

														int	seq = Integer.parseInt( lhs.substring( 3 ));
														int	len = Integer.parseInt( temp[1]);

														last_request_time = now;

														request_count++;

														byte[] reply = processRequest( data, data_pos, len );

														replies.add( reply );

														reply_length += reply.length;

														data_pos += len;

														current_reply_params += "&seq" + seq + "=" + reply.length;

													}else if ( lhs.equals( "keepalive" )){

													}else if ( lhs.equals( "close" )){

														close_requested = true;
													}
												}
											}

											current_reply_data = new byte[reply_length];

											data_pos = 0;

											for ( byte[] reply: replies ){

												System.arraycopy( reply, 0, current_reply_data, data_pos, reply.length );

												data_pos += reply.length;
											}
										}
									}
								}
							}

							if ( !cookie_found ){

								throw( new Exception( "Cookie missing from reply" ));
							}

							consec_fails = 0;

						}catch( Throwable e ){

							long	fail_time = SystemTime.getMonotonousTime();

							last_fail_duration_secs = (fail_time - start_time)/1000;

							if ( isTimeout( e ) && last_fail_duration_secs >= 20 ){

									// 'expected' failure if we're getting dumped on by proxies etc.

								consec_fails = 0;

							}else{

								Debug.out( e );

								consec_fails++;

								if ( consec_fails > 3 ){

									break;
								}
							}
						}
					}
				}finally{

					tunnel_handler.closeTunnel( PairManagerTunnel.this );
				}
			}
		}.start();
	}

	private boolean
	isTimeout(
		Throwable e )
	{
		if ( e == null ){

			return( false );
		}

		if ( e instanceof SocketTimeoutException ){

			return( true );
		}

		String message = e.getMessage();

		if ( message != null ){

			message = message.toLowerCase( Locale.US );

			if ( message.contains( "timed out") || message.contains( "timeout" )){

				return( true );
			}
		}
		return( isTimeout( e.getCause()));
	}

	private byte[]
	processRequest(
		byte[]		request,
		int			offset,
		int			length )
	{
		try{
			byte[] decrypted;

			{
				byte[]	IV = new byte[16];

				System.arraycopy( request, offset, IV, 0, IV.length );

				Cipher decipher = Cipher.getInstance ("AES/CBC/PKCS5Padding");

				decipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec( IV ));

				decrypted = decipher.doFinal( request, offset+16, length-16 );
			}

			byte[] reply_bytes = request_handler.handleRequest( originator, endpoint_url, decrypted );

			{
				Cipher encipher = Cipher.getInstance ("AES/CBC/PKCS5Padding");

				encipher.init( Cipher.ENCRYPT_MODE, key );

				AlgorithmParameters params = encipher.getParameters ();

				byte[] IV = params.getParameterSpec(IvParameterSpec.class).getIV();

				byte[] enc = encipher.doFinal( reply_bytes );

				byte[] rep_bytes = new byte[ IV.length + enc.length ];

				System.arraycopy( IV, 0, rep_bytes, 0, IV.length );
				System.arraycopy( enc, 0, rep_bytes, IV.length, enc.length );

				return( rep_bytes );
			}
		}catch( Throwable e ){

			Debug.out( e );

			return( new byte[0] );
		}
	}

	protected String
	getKey()
	{
		return( tunnel_key );
	}

	protected long
	getLastActive()
	{
		return( last_active );
	}

	protected void
	destroy()
	{
		close_requested = true;
	}

	protected String
	getString()
	{
		long	now = SystemTime.getMonotonousTime();

		return(
			"url=" + tunnel_url +
			", age=" + (now - create_time ) +
			", last_req=" + (last_request_time==0?"never":String.valueOf( now - last_request_time )) +
			", reqs=" + request_count +
			", in=" + DisplayFormatters.formatByteCountToKiBEtc( bytes_in ) +
			", out=" + DisplayFormatters.formatByteCountToKiBEtc( bytes_out ) +
			", lf_secs=" + last_fail_duration_secs + ", consec_fail=" + consec_fails );
	}
}
