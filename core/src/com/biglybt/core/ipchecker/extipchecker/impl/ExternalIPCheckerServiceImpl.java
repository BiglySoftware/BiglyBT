/*
 * File    : ExternalIPCheckerServiceImpl.java
 * Created : 09-Nov-2003
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.ipchecker.extipchecker.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.Vector;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.internat.StringSupplier;
import com.biglybt.core.ipchecker.extipchecker.ExternalIPCheckerService;
import com.biglybt.core.ipchecker.extipchecker.ExternalIPCheckerServiceListener;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.AEThread;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.clientid.ClientIDException;
import com.biglybt.pif.clientid.ClientIDGenerator;
import com.biglybt.pifimpl.local.clientid.ClientIDManagerImpl;

public abstract class
ExternalIPCheckerServiceImpl
	implements ExternalIPCheckerService, Cloneable
{
	private static final int		MAX_PAGE_SIZE	= 4096;

	private final String name;
	private final StringSupplier description;
	private final String url;

	boolean		completed;

	private final Vector		listeners	= new Vector();
	private final AEMonitor		this_mon	= new AEMonitor( "ExtIPCheckServ");

	final AESemaphore	timeout_sem	= new AESemaphore( "ExtIPCheckServ" );

	protected
	ExternalIPCheckerServiceImpl(
      String serviceName,
			String serviceUrl,
			StringSupplier serviceDescription)
	{
		name = serviceName;
		description = serviceDescription;
		url = serviceUrl;
	}

	@Override
	public void
	initiateCheck(
		long		timeout )
	{
		_clone().initiateCheckSupport(timeout);
	}

	protected ExternalIPCheckerServiceImpl
	_clone()
	{
		try{
			return((ExternalIPCheckerServiceImpl)clone());

		}catch( CloneNotSupportedException e){

			Debug.printStackTrace( e );

			return( null );
		}
	}

	protected void
	initiateCheckSupport(
		final long		timeout )
	{

		Thread	t =
			new AEThread("IPChecker")
			{
				@Override
				public void
				runSupport()
				{
					try{

						initiateCheckSupport();

					}finally{

						setComplete();
					}
				}
			};

		t.setDaemon( true );

		t.start();

		if ( timeout > 0 ){

			Thread	t2 =
				new AEThread("IPChecker2")
				{
					@Override
					public void
					runSupport()
					{
						try{

							if ( !timeout_sem.reserve( timeout )){

								if ( !completed ){

									informFailure("IPChecker.external.timeout");

									setComplete();
								}
							}
						}catch( Throwable e ){

							Debug.printStackTrace( e );
						}
					}
				};

			t2.setDaemon( true );

			t2.start();

		}
	}

	protected abstract void
	initiateCheckSupport();

	protected void
	setComplete()
	{
		completed = true;
	}

	protected String loadPage(URL url) {
		try{

			HttpURLConnection	connection 	= null;
			InputStream			is			= null;

			try{
				Properties	http_properties = new Properties();

				http_properties.put( ClientIDGenerator.PR_URL, url );

				try{
					ClientIDManagerImpl.getSingleton().generateHTTPProperties( null, http_properties );

				}catch( ClientIDException e ){

					throw( new IOException( e.getMessage()));
				}

				url = (URL)http_properties.get( ClientIDGenerator.PR_URL );

				
				connection = (HttpURLConnection)url.openConnection();

				int	response = connection.getResponseCode();

				if( response == HttpURLConnection.HTTP_ACCEPTED || response == HttpURLConnection.HTTP_OK ){

					is = connection.getInputStream();

					String	page = "";

					while( page.length() < MAX_PAGE_SIZE ){

						byte[]	buffer = new byte[2048];

						int	len = is.read( buffer );

						if ( len < 0 ){

							break;
						}

						page += new String(buffer, 0, len);
					}

					return( page );

				}else{

					informFailure( "IPChecker.external.httpinvalidresponse", String.valueOf(response) );

					return( null );
				}
			}finally{

				try{

					if ( is != null ){

						is.close();
					}

					if ( connection != null ){

						connection.disconnect();
					}
				}catch( Throwable e){

					Debug.printStackTrace( e );
				}
			}
		}catch( Throwable e ){

			informFailure("IPChecker.external.httploadfail", e.toString());

			return( null );
		}
	}

	protected String
	extractIPAddress(
		String		str )
	{
		int		pos = 0;

		while(pos < str.length()){

			int	p1 = str.indexOf( '.', pos );

			if ( p1 == -1 ){

				informFailure("IPChecker.external.ipnotfound");

				return( null );
			}

			if ( p1 > 0 ){

				if ( Character.isDigit(str.charAt(p1-1))){

					int	p2 = p1-1;

					while(p2>=0&&Character.isDigit(str.charAt(p2))){

						p2--;
					}

					p2++;

					int	p3 = p2+1;

					int	dots = 0;

					while(p3<str.length()){

						char	c = str.charAt(p3);

						if ( c == '.' ){

							dots++;

						}else if ( Character.isDigit( c )){

						}else{

							break;
						}

						p3++;
					}

					if ( dots == 3 ){

						return( str.substring(p2,p3));
					}
				}
			}

			pos	= p1+1;
		}

		informFailure("IPChecker.external.ipnotfound");

		return( null );
	}

	@Override
	public String
	getName()
	{
		return( name );
	}

	@Override
	public String
	getDescription()
	{
		return description.get();
	}

	@Override
	public String
	getURL()
	{
		return( url );
	}

	protected  void
	informSuccess(
		String		ip )
	{
		try{
			this_mon.enter();

			if ( !completed ){

				timeout_sem.releaseForever();

				for ( int i=0;i<listeners.size();i++){

					((ExternalIPCheckerServiceListener)listeners.elementAt(i)).checkComplete( this, ip );
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	protected void
	informFailure(
		String		msg_key )
	{
		try{
			this_mon.enter();

			informFailure( msg_key, null );

		}finally{

			this_mon.exit();
		}
	}

	protected void
	informFailure(
		String		msg_key,
		String		extra )
	{
		try{
			this_mon.enter();

			if ( !completed ){

				timeout_sem.releaseForever();

				String message = MessageText.getString(msg_key);

				if ( extra != null ){
					message += ": " + extra;
				}

				for ( int i=0;i<listeners.size();i++){

					((ExternalIPCheckerServiceListener)listeners.elementAt(i)).checkFailed( this, message );
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	protected void
	reportProgress(
		String		msg_key )
	{
		try{
			this_mon.enter();

			reportProgress( msg_key,  null );

		}finally{

			this_mon.exit();
		}
	}

	protected void
	reportProgress(
			String		msg_key,
			Object		extra )
	{
		try{
			this_mon.enter();

			if ( !completed ){

				String message = MessageText.getString(msg_key);

				if (extra != null) {
					message += ": " + extra;
				}

				for ( int i=0;i<listeners.size();i++){

					((ExternalIPCheckerServiceListener)listeners.elementAt(i)).reportProgress( this, message );
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	addListener(
		ExternalIPCheckerServiceListener	l )
	{
		try{
			this_mon.enter();

			listeners.addElement( l );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	removeListener(
		ExternalIPCheckerServiceListener	l )
	{
		try{
			this_mon.enter();

			listeners.removeElement( l );

		}finally{

			this_mon.exit();
		}
	}

	/**
	 * Constructs an url without throwing checked exceptions.
	 */
	protected static URL url(String urlSpec) {
		try {
			return new URL(urlSpec);
		} catch (MalformedURLException e) {
			Debug.out(e);
			throw new RuntimeException(e);
		}
	}
}
