/*
 * File    : TrackerImpl.java
 * Created : 08-Dec-2003
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
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.biglybt.core.tracker.host.*;
import com.biglybt.core.tracker.util.TRTrackerUtils;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.tracker.Tracker;
import com.biglybt.pif.tracker.TrackerException;
import com.biglybt.pif.tracker.TrackerListener;
import com.biglybt.pif.tracker.TrackerTorrent;
import com.biglybt.pif.tracker.web.TrackerAuthenticationAdapter;
import com.biglybt.pif.tracker.web.TrackerAuthenticationListener;
import com.biglybt.pif.tracker.web.TrackerWebContext;
import com.biglybt.pifimpl.local.torrent.TorrentImpl;

public class
TrackerImpl
	extends		TrackerWCHelper
	implements 	Tracker, TRHostListener2, TRHostAuthenticationListener
{
	private static TrackerImpl	singleton;
	private static AEMonitor 		class_mon 	= new AEMonitor( "Tracker" );

	private List	listeners	= new ArrayList();

	private TRHost		host;

	private List<TrackerAuthenticationListener>	auth_listeners	= new ArrayList<>();


	public static Tracker
	getSingleton()
	{
		try{
			class_mon.enter();

			if ( singleton == null ){

				singleton	= new TrackerImpl( TRHostFactory.getSingleton());
			}

			return( singleton );

		}finally{

			class_mon.exit();
		}
	}

	protected
	TrackerImpl(
		TRHost		_host )
	{
		setTracker( this );

		host		= _host;

		host.addListener2( this );
	}

	@Override
	public String
	getName()
	{
		return( host.getName());
	}

	@Override
	public void
	setEnableKeepAlive(
		boolean		enable )
	{
		Debug.out( "Keep alive setting ignored for tracker" );
	}

	@Override
	public URL[]
	getURLs()
	{
		URL[][]	url_sets = TRTrackerUtils.getAnnounceURLs();

		URL[]	res = new URL[url_sets.length];

		for (int i=0;i<res.length;i++){

			res[i] = url_sets[i][0];
		}

		return( res );
	}

	@Override
	public InetAddress
	getBindIP()
	{
		return( host.getBindIP());
	}

	@Override
	public TrackerTorrent
	host(
		Torrent		_torrent,
		boolean		_persistent )

		throws TrackerException
	{
		return( host( _torrent, _persistent, false ));
	}

	@Override
	public TrackerTorrent
	host(
		Torrent		_torrent,
		boolean		_persistent,
		boolean		_passive )

		throws TrackerException
	{
		TorrentImpl	torrent = (TorrentImpl)_torrent;

		try{
			return( new TrackerTorrentImpl( host.hostTorrent( torrent.getTorrent(), _persistent, _passive )));

		}catch( Throwable e ){

			throw( new TrackerException( "Tracker: host operation fails", e ));
		}
	}

	@Override
	public TrackerTorrent
	publish(
			Torrent _torrent)

		throws TrackerException
	{
		TorrentImpl	torrent = (TorrentImpl)_torrent;

		try{
			return( new TrackerTorrentImpl( host.publishTorrent( torrent.getTorrent() )));

		}catch( Throwable e ){

			throw( new TrackerException( "Tracker: publish operation fails", e ));
		}
	}

	@Override
	public TrackerTorrent[]
	getTorrents()
	{
		TRHostTorrent[]	hts = host.getTorrents();

		TrackerTorrent[]	res = new TrackerTorrent[hts.length];

		for (int i=0;i<hts.length;i++){

			res[i] = new TrackerTorrentImpl(hts[i]);
		}

		return( res );
	}

	@Override
	public TrackerTorrent
	getTorrent(
		Torrent		torrent )
	{
		TRHostTorrent	ht = host.getHostTorrent(((TorrentImpl)torrent).getTorrent());

		if ( ht == null ){

			return( null );
		}

		return( new TrackerTorrentImpl( ht ));
	}

	@Override
	public TrackerWebContext
	createWebContext(
		int		port,
		int		protocol )

		throws TrackerException
	{
		return( new TrackerWebContextImpl( this, null, port, protocol, null, null ));
	}

	@Override
	public TrackerWebContext
	createWebContext(
		String	name,
		int		port,
		int		protocol )

		throws TrackerException
	{
		return( new TrackerWebContextImpl( this, name, port, protocol, null, null ));
	}

	@Override
	public TrackerWebContext
    createWebContext(
    	String		name,
    	int			port,
		int			protocol,
		InetAddress	bind_ip )

    	throws TrackerException
    {
		return( new TrackerWebContextImpl( this, name, port, protocol, bind_ip, null ));
    }

	@Override
	public TrackerWebContext
    createWebContext(
    	String					name,
    	int						port,
		int						protocol,
		InetAddress				bind_ip,
		Map<String,Object>		properties )

    	throws TrackerException
    {
		return( new TrackerWebContextImpl( this, name, port, protocol, bind_ip, properties ));
    }

	public void
	torrentAdded(
		TRHostTorrent		t )
	{
		try{
			this_mon.enter();

			for (int i=0;i<listeners.size();i++){

				((TrackerListener)listeners.get(i)).torrentAdded(new TrackerTorrentImpl(t));
			}
		}finally{

			this_mon.exit();
		}
	}

	public void
	torrentChanged(
		TRHostTorrent		t )
	{
		for (int i=0;i<listeners.size();i++){

			((TrackerListener)listeners.get(i)).torrentChanged(new TrackerTorrentImpl(t));
		}
	}


	public void
	torrentRemoved(
		TRHostTorrent		t )
	{
		try{
			this_mon.enter();

			for (int i=0;i<listeners.size();i++){

				((TrackerListener)listeners.get(i)).torrentRemoved(new TrackerTorrentImpl(t));
			}
		}finally{

			this_mon.exit();
		}
	}


	@Override
	public void
	addListener(
		TrackerListener		listener )
	{
		try{
			this_mon.enter();

			listeners.add( listener );

			TrackerTorrent[] torrents = getTorrents();

			for (int i=0;i<torrents.length;i++){

				listener.torrentAdded( torrents[i]);
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	removeListener(
		TrackerListener		listener )
	{
		try{
			this_mon.enter();

			listeners.remove( listener );

		}finally{

			this_mon.exit();
		}
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

				host.addAuthenticationListener( this );
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

				host.removeAuthenticationListener( this );
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

		host.removeAuthenticationListener( this );

		listeners.clear();

		host.close();
	}
}
