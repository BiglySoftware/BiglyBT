/*
 * Created on 19-Oct-2005
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

package com.biglybt.pifimpl.local.utils.resourceuploader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import com.biglybt.core.security.SEPasswordListener;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.util.AddressUtils;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.pif.clientid.ClientIDGenerator;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException;
import com.biglybt.pif.utils.resourceuploader.ResourceUploader;
import com.biglybt.pif.utils.resourceuploader.ResourceUploaderException;
import com.biglybt.pifimpl.local.clientid.ClientIDManagerImpl;

public class
ResourceUploaderURLImpl
	implements ResourceUploader, SEPasswordListener
{
	private URL				target;
	private InputStream		data;
	private String			user_name;
	private String			password;

	private Map				properties = new HashMap();


	protected
	ResourceUploaderURLImpl(
		URL			_target,
		InputStream	_data,
		String		_user_name,
		String		_password )
	{
		target		= _target;
		data		= _data;
		user_name	= _user_name;
		password	= _password;
	}

	@Override
	public void
	setProperty(
		String		name,
		Object		value )

		throws ResourceDownloaderException
	{
		properties.put( name, value );
	}

	@Override
	public Object
	getProperty(
		String		name )

		throws ResourceDownloaderException
	{
		return( properties.get( name ));
	}

	@Override
	public InputStream
	upload()

		throws ResourceUploaderException
	{
		try{

			try{
				URL	url = new URL( target.toString().replaceAll( " ", "%20" ));

					// some authentications screw up without an explicit port number here

				String	protocol = url.getProtocol().toLowerCase();

				if ( url.getPort() == -1 ){

					int	target_port;

					if ( protocol.equals( "http" )){

						target_port = 80;

					}else{

						target_port = 443;
					}

					try{
						String str = target.toString().replaceAll( " ", "%20" );

						int	pos = str.indexOf( "://" );

						pos = str.indexOf( "/", pos+4 );

							// might not have a trailing "/"

						if ( pos == -1 ){

							url = new URL( str + ":" + target_port + "/" );

						}else{

							url = new URL( str.substring(0,pos) + ":" + target_port + str.substring(pos));
						}

					}catch( Throwable e ){

						Debug.printStackTrace( e );
					}
				}

				url = AddressUtils.adjustURL( url );

				try{
					if ( user_name != null ){

						SESecurityManager.setPasswordHandler( url, this );
					}

					for (int i=0;i<2;i++){

						try{
							HttpURLConnection	con;

							if ( url.getProtocol().equalsIgnoreCase("https")){

									// see ConfigurationChecker for SSL client defaults

								HttpsURLConnection ssl_con = (HttpsURLConnection)url.openConnection();

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

								con = ssl_con;

							}else{

								con = (HttpURLConnection) url.openConnection();

							}

							con.setRequestMethod( "POST" );

							Properties	props = new Properties();

							ClientIDManagerImpl.getSingleton().getGenerator().generateHTTPProperties( null, props );

							String ua = props.getProperty( ClientIDGenerator.PR_USER_AGENT );

							con.setRequestProperty("User-Agent", ua );

							setRequestProperties( con, false );

							con.setDoOutput( true );
							con.setDoInput( true );

							OutputStream	os = con.getOutputStream();

							byte[]	buffer = new byte[65536];

							while( true ){

								int	len = data.read( buffer );

								if (len <= 0 ){

									break;
								}

								os.write( buffer, 0, len );
							}

							con.connect();

							int response = con.getResponseCode();

							if ((response != HttpURLConnection.HTTP_ACCEPTED) && (response != HttpURLConnection.HTTP_OK )){

								throw( new ResourceUploaderException("Error on connect for '" + url.toString() + "': " + Integer.toString(response) + " " + con.getResponseMessage()));
							}

							InputStream is = con.getInputStream();

							getRequestProperties( con );

							return( is );

						}catch( SSLException e ){

							if ( i == 0 ){

								if ( SESecurityManager.installServerCertificates( url ) != null ){

										// certificate has been installed

									continue;	// retry with new certificate
								}
							}

							throw( e );

						}catch( IOException e ){

							if ( i == 0 ){

					      		URL retry_url = UrlUtils.getIPV4Fallback( url );

					      		if ( retry_url != null ){

					      			url = retry_url;

					      			continue;
					      		}
							}

							throw( e );
						}
					}

					throw( new ResourceUploaderException("Should never get here" ));

				}finally{

					if ( user_name != null ){

						SESecurityManager.setPasswordHandler( url, null );
					}
				}
			}catch (java.net.MalformedURLException e){

				throw( new ResourceUploaderException("Exception while parsing URL '" + target + "':" + e.getMessage(), e));

			}catch (java.net.UnknownHostException e){

				throw( new ResourceUploaderException("Exception while initializing download of '" + target + "': Unknown Host '" + e.getMessage() + "'", e));

			}catch (java.io.IOException e ){

				throw( new ResourceUploaderException("I/O Exception while downloading '" + target + "':" + e.toString(), e ));
			}
		}catch( Throwable e ){

			ResourceUploaderException	rde;

			if ( e instanceof ResourceUploaderException ){

				rde = (ResourceUploaderException)e;

			}else{

				rde = new ResourceUploaderException( "Unexpected error", e );
			}

			throw( rde );

		}finally{

			try{
				data.close();

			}catch( Throwable e ){

				e.printStackTrace();
			}

		}
	}

	protected void
	setRequestProperties(
		HttpURLConnection		con,
		boolean					use_compression )
	{
		Iterator	it = properties.entrySet().iterator();

		while( it.hasNext()){

			Map.Entry entry = (Map.Entry)it.next();

			String	key 	= (String)entry.getKey();
			Object	value	= entry.getValue();

			if ( key.startsWith( "URL_" ) && value instanceof String ){

				key = key.substring(4);

				if ( key.equalsIgnoreCase( "Accept-Encoding" ) && !use_compression ){

					//skip

				}else{

					con.setRequestProperty(key,(String)value);
				}
			}
		}
	}

	protected void
	getRequestProperties(
		HttpURLConnection		con )
	{
		try{
			setProperty( ResourceDownloader.PR_STRING_CONTENT_TYPE, con.getContentType() );

			Map	headers = con.getHeaderFields();

			Iterator it = headers.entrySet().iterator();

			while( it.hasNext()){

				Map.Entry	entry = (Map.Entry)it.next();

				String	key = (String)entry.getKey();
				Object	val	= entry.getValue();

				if ( key != null ){

					setProperty( "URL_" + key, val );
				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	@Override
	public PasswordAuthentication
	getAuthentication(
		String		realm,
		URL			tracker )
	{
		if ( user_name == null || password == null ){

			String user_info = tracker.getUserInfo();

			if ( user_info == null ){

				return( null );
			}

			String	user_bit	= user_info;
			String	pw_bit		= "";

			int	pos = user_info.indexOf(':');

			if ( pos != -1 ){

				user_bit	= user_info.substring(0,pos);
				pw_bit		= user_info.substring(pos+1);
			}

			return( new PasswordAuthentication( user_bit, pw_bit.toCharArray()));
		}

		return( new PasswordAuthentication( user_name, password.toCharArray()));
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
}
