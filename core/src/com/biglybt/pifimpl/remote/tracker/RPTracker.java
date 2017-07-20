/*
 * Created on 21-Jun-2004
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

package com.biglybt.pifimpl.remote.tracker;

/**
 * @author parg
 *
 */


import java.net.InetAddress;
import java.net.URL;
import java.util.Map;

import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.tracker.Tracker;
import com.biglybt.pif.tracker.TrackerException;
import com.biglybt.pif.tracker.TrackerListener;
import com.biglybt.pif.tracker.TrackerTorrent;
import com.biglybt.pif.tracker.web.TrackerAuthenticationListener;
import com.biglybt.pif.tracker.web.TrackerWebContext;
import com.biglybt.pif.tracker.web.TrackerWebPageGenerator;
import com.biglybt.pifimpl.remote.RPException;
import com.biglybt.pifimpl.remote.RPObject;
import com.biglybt.pifimpl.remote.RPReply;
import com.biglybt.pifimpl.remote.RPRequest;
import com.biglybt.pifimpl.remote.torrent.RPTorrent;


public class
RPTracker
	extends		RPObject
	implements 	Tracker
{
	protected transient Tracker		delegate;

	public static RPTracker
	create(
		Tracker		_delegate )
	{
		RPTracker	res =(RPTracker)_lookupLocal( _delegate );

		if ( res == null ){

			res = new RPTracker( _delegate );
		}

		return( res );
	}

	protected
	RPTracker(
		Tracker		_delegate )
	{
		super( _delegate );
	}

	@Override
	protected void
	_setDelegate(
		Object		_delegate )
	{
		delegate = (Tracker)_delegate;
	}

	@Override
	public Object
	_setLocal()

		throws RPException
	{
		return( _fixupLocal());
	}


	@Override
	public RPReply
	_process(
		RPRequest	request	)
	{
		String		method 	= request.getMethod();
		Object[]	params	= request.getParams();

		if ( method.equals( "host[Torrent,boolean]")){

			try{
				Torrent	torrent = params[0]==null?null:(Torrent)((RPTorrent)params[0])._setLocal();

				if ( torrent == null ){

					throw( new RPException( "Invalid torrent" ));
				}

				TrackerTorrent tt = delegate.host(torrent,((Boolean)params[1]).booleanValue());

				RPTrackerTorrent res = RPTrackerTorrent.create( tt );

				return( new RPReply( res ));

			}catch( TrackerException e ){

				return( new RPReply( e ));
			}
		}else if ( method.equals( "getTorrents")){

			TrackerTorrent[]	torrents = delegate.getTorrents();

			RPTrackerTorrent[]	res = new RPTrackerTorrent[torrents.length];

			for (int i=0;i<res.length;i++){

				res[i] = RPTrackerTorrent.create( torrents[i]);
			}

			return( new RPReply( res ));
		}

		throw( new RPException( "Unknown method: " + method ));
	}

	// ************************************************************************

	@Override
	public TrackerTorrent
	host(
		Torrent		torrent,
		boolean		persistent )

		throws TrackerException
	{
		try{
			RPTrackerTorrent resp = (RPTrackerTorrent)_dispatcher.dispatch( new RPRequest( this, "host[Torrent,boolean]", new Object[]{torrent,
				Boolean.valueOf(persistent)
			})).getResponse();

			resp._setRemote( _dispatcher );

			return( resp );

		}catch( RPException e ){

			if ( e.getCause() instanceof TrackerException ){

				throw((TrackerException)e.getCause());
			}

			throw( e );
		}
	}

	@Override
	public TrackerTorrent
	host(
		Torrent		torrent,
		boolean		persistent,
		boolean		passive )

		throws TrackerException
	{
		notSupported();

		return( null );
	}

	@Override
	public TrackerTorrent
	publish(
		Torrent		torrent )

		throws TrackerException
	{
		notSupported();

		return( null );
	}

    @Override
    public TrackerTorrent[]
    getTorrents()
    {
		RPTrackerTorrent[]	res = (RPTrackerTorrent[])_dispatcher.dispatch( new RPRequest( this, "getTorrents", null )).getResponse();

		for (int i=0;i<res.length;i++){

			res[i]._setRemote( _dispatcher );
		}

		return( res );
    }

    @Override
    public TrackerTorrent
    getTorrent(
    	Torrent	t )
    {
    	notSupported();

    	return( null );
    }

    @Override
    public TrackerWebContext
    createWebContext(
    	int		port,
		int		protocol )

    	throws TrackerException
	{
       	notSupported();

		return( null );
	}

    @Override
    public TrackerWebContext
    createWebContext(
    	String	name,
    	int		port,
		int		protocol )

    	throws TrackerException
	{
    	notSupported();

		return( null );
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
    	notSupported();

		return( null );
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
    	notSupported();

		return( null );
	}

    @Override
    public void
    addListener(
   		TrackerListener		listener )
    {

    }

    @Override
    public void
    removeListener(
   		TrackerListener		listener )
    {

    }

	@Override
	public String
	getName()
	{
	   	notSupported();

		return( null );
	}

	@Override
	public void
	setEnableKeepAlive(
		boolean		enable )
	{
	   	notSupported();
	}

	@Override
	public URL[]
	getURLs()
	{
	   	notSupported();

		return( null );
	}

	@Override
	public InetAddress
	getBindIP()
	{
	   	notSupported();

		return( null );
	}

	@Override
	public void
	addPageGenerator(
		TrackerWebPageGenerator	generator )
	{

	}

	@Override
	public void
	removePageGenerator(
		TrackerWebPageGenerator	generator )
	{
	}

	@Override
	public TrackerWebPageGenerator[]
	getPageGenerators()
	{
	   	notSupported();

		return( null );
	}

	@Override
	public void
	addAuthenticationListener(
		TrackerAuthenticationListener l )
	{

	}

	@Override
	public void
	removeAuthenticationListener(
		TrackerAuthenticationListener l )
	{

	}

	@Override
	public void
	destroy()
	{
		notSupported();
	}
}
