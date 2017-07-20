/*
 * File    : TrackerWebContextImpl.java
 * Created : 23-Jan-2004
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

package com.biglybt.pifimpl.local.tracker;

/**
 * @author parg
 *
 */

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.biglybt.core.tracker.server.*;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.tracker.Tracker;
import com.biglybt.pif.tracker.TrackerException;
import com.biglybt.pif.tracker.web.TrackerAuthenticationAdapter;
import com.biglybt.pif.tracker.web.TrackerAuthenticationListener;

public class
TrackerWebContextImpl
	extends		TrackerWCHelper
	implements 	TRTrackerServerListener2, TRTrackerServerAuthenticationListener
{
	protected TRTrackerServer		server;

	protected List<TrackerAuthenticationListener>			auth_listeners	= new ArrayList<>();

	public
	TrackerWebContextImpl(
		TrackerImpl				_tracker,
		String					name,
		int						port,
		int						protocol,
		InetAddress				bind_ip,
		Map<String,Object>		properties )

		throws TrackerException
	{
		setTracker( _tracker );

		try{

			if ( protocol == Tracker.PR_HTTP ){

				server = TRTrackerServerFactory.create( name, TRTrackerServerFactory.PR_TCP, port, bind_ip, false, false, properties );

			}else{

				server = TRTrackerServerFactory.createSSL( name, TRTrackerServerFactory.PR_TCP, port, bind_ip, false, false, properties );
			}

			server.addListener2( this );

		}catch( TRTrackerServerException e ){

			throw( new TrackerException("TRTrackerServerFactory failed", e ));
		}
	}

	@Override
	public String
	getName()
	{
		return( server.getName());
	}

	@Override
	public void
	setEnableKeepAlive(
		boolean		enable )
	{
		server.setEnableKeepAlive( enable );
	}

	@Override
	public URL[]
	getURLs()
	{
		try{
			URL	url = new URL((server.isSSL()?"https":"http") + "://" +	server.getHost() + ":" + server.getPort() + "/" );

				// quick fix for badly specified host whereby a valid URL is constructed but the port lost. For example, if#
				// someone has entered http://1.2.3.4 as the host

			if ( url.getPort() != server.getPort()){

				Debug.out( "Invalid URL '" + url + "' - check tracker configuration" );

				url = new URL( "http://i.am.invalid:" + server.getPort() + "/" );
			}

			return(	new URL[]{ url });

		}catch( MalformedURLException e ){

			Debug.printStackTrace( e );

			return( null );
		}
	}

	@Override
	public InetAddress
	getBindIP()
	{
		return( server.getBindIP());
	}

	@Override
	public boolean
	authenticate(
		String		headers,
		URL			resource,
		String		user,
		String		password )
	{
		for (int i=0;i<auth_listeners.size();i++){

			try{
				TrackerAuthenticationListener listener = auth_listeners.get(i);

				boolean res;

				if ( listener instanceof TrackerAuthenticationAdapter ){

					res = ((TrackerAuthenticationAdapter)listener).authenticate( headers, resource, user, password );

				}else{

					res = listener.authenticate( resource, user, password );
				}

				if ( res ){

					return(true );
				}
			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}

		return( false );
	}

	@Override
	public byte[]
	authenticate(
		URL			resource,
		String		user )
	{
		for (int i=0;i<auth_listeners.size();i++){

			try{
				byte[] res = ((TrackerAuthenticationListener)auth_listeners.get(i)).authenticate( resource, user );

				if ( res != null ){

					return( res );
				}
			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}

		return( null );
	}

	@Override
	public void
	addAuthenticationListener(
		TrackerAuthenticationListener	l )
	{
		try{
			this_mon.enter();

			auth_listeners.add(l);

			if ( auth_listeners.size() == 1 ){

				server.addAuthenticationListener( this );
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	removeAuthenticationListener(
		TrackerAuthenticationListener	l )
	{
		try{
			this_mon.enter();

			auth_listeners.remove(l);

			if ( auth_listeners.size() == 0 ){

				server.removeAuthenticationListener( this );
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	destroy()
	{
		super.destroy();

		auth_listeners.clear();

		server.removeAuthenticationListener( this );

		server.close();
	}
}
