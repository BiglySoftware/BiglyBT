/*
 * File    : RPTorrentManager.java
 * Created : 28-Feb-2004
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

package com.biglybt.pifimpl.remote.torrent;

/**
 * @author parg
 *
 */

import java.io.File;
import java.io.InputStream;
import java.net.URL;

import com.biglybt.pif.torrent.*;
import com.biglybt.pifimpl.remote.RPException;
import com.biglybt.pifimpl.remote.RPObject;
import com.biglybt.pifimpl.remote.RPReply;
import com.biglybt.pifimpl.remote.RPRequest;

public class
RPTorrentManager
	extends		RPObject
	implements 	TorrentManager
{
	protected transient TorrentManager		delegate;

	public static RPTorrentManager
	create(
		TorrentManager		_delegate )
	{
		RPTorrentManager	res =(RPTorrentManager)_lookupLocal( _delegate );

		if ( res == null ){

			res = new RPTorrentManager( _delegate );
		}

		return( res );
	}

	protected
	RPTorrentManager(
		TorrentManager		_delegate )
	{
		super( _delegate );
	}

	@Override
	protected void
	_setDelegate(
		Object		_delegate )
	{
		delegate = (TorrentManager)_delegate;
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

		if ( method.equals( "getURLDownloader[URL]")){

			try{
				TorrentDownloader dl = delegate.getURLDownloader((URL)params[0]);

				RPTorrentDownloader res = RPTorrentDownloader.create( dl );

				return( new RPReply( res ));

			}catch( TorrentException e ){

				return( new RPReply( e ));
			}
		}else if ( method.equals( "getURLDownloader[URL,String,String]")){

			try{
				TorrentDownloader dl = delegate.getURLDownloader((URL)params[0],(String)params[1],(String)params[2]);

				RPTorrentDownloader res = RPTorrentDownloader.create( dl );

				return( new RPReply( res ));

			}catch( TorrentException e ){

				return( new RPReply( e ));
			}
		}else if ( method.equals( "createFromBEncodedData[byte[]]")){

			try{
				return( new RPReply( RPTorrent.create( delegate.createFromBEncodedData((byte[])params[0]))));

			}catch( TorrentException e ){

				return( new RPReply(e));
			}
		}

		throw( new RPException( "Unknown method: " + method ));
	}

	// ************************************************************************

	@Override
	public TorrentDownloader
	getURLDownloader(
		URL		url )

		throws TorrentException
	{
		try{
			RPTorrentDownloader resp = (RPTorrentDownloader)_dispatcher.dispatch( new RPRequest( this, "getURLDownloader[URL]", new Object[]{url})).getResponse();

			resp._setRemote( _dispatcher );

			return( resp );

		}catch( RPException e ){

			if ( e.getCause() instanceof TorrentException ){

				throw((TorrentException)e.getCause());
			}

			throw( e );
		}
	}

	@Override
	public TorrentDownloader
	getURLDownloader(
		URL		url,
		String	user_name,
		String	password )

		throws TorrentException
	{
		try{
			RPTorrentDownloader resp = (RPTorrentDownloader)_dispatcher.dispatch( new RPRequest( this, "getURLDownloader[URL,String,String]", new Object[]{url, user_name, password})).getResponse();

			resp._setRemote( _dispatcher );

			return( resp );

		}catch( RPException e ){

			if ( e.getCause() instanceof TorrentException ){

				throw((TorrentException)e.getCause());
			}

			throw( e );
		}
	}

	@Override
	public Torrent
	createFromBEncodedFile(
		File		file )

		throws TorrentException
	{
		notSupported();

		return( null );
	}

	@Override
	public Torrent
	createFromBEncodedFile(
		File		file,
		boolean		for_seeding )

		throws TorrentException
	{
		notSupported();

		return( null );
	}
	@Override
	public Torrent
	createFromBEncodedInputStream(
		InputStream		data )

		throws TorrentException
	{
		notSupported();

		return( null );
	}

	@Override
	public Torrent
	createFromBEncodedData(
		byte[]		data )

		throws TorrentException
	{
		try{
			RPTorrent	res = (RPTorrent)_dispatcher.dispatch( new RPRequest( this, "createFromBEncodedData[byte[]]", new Object[]{data})).getResponse();

			res._setRemote( _dispatcher );

			return( res );

		}catch( RPException e ){

			if ( e.getCause() instanceof TorrentException ){

				throw((TorrentException)e.getCause());
			}

			throw( e );
		}
	}


	@Override
	public Torrent
	createFromBEncodedData(
			byte[] data,
			int preserve )

			throws TorrentException {

		notSupported();

		return( null );
	}


	@Override
	public Torrent
	createFromBEncodedFile(
			File file,
			int preserve )

			throws TorrentException {

		notSupported();

		return( null );
	}

	@Override
	public Torrent
	createFromBEncodedInputStream(
			InputStream data,
			int preserve )

			throws TorrentException {

		notSupported();

		return( null );
	}

	@Override
	public Torrent
	createFromDataFile(
		File		data,
		URL			announce_url )

		throws TorrentException
	{
		notSupported();

		return( null );
	}

	@Override
	public Torrent
	createFromDataFile(
		File		data,
		URL			announce_url,
		boolean		include_other_hashes )

		throws TorrentException
	{
		notSupported();

		return( null );
	}


	@Override
	public TorrentCreator
	createFromDataFileEx(
		File					data,
		URL						announce_url,
		boolean					include_other_hashes )

		throws TorrentException
	{
		notSupported();

		return( null );
	}

	@Override
	public TorrentAttribute[]
	getDefinedAttributes()
	{
		notSupported();

		return( null );
	}

	@Override
	public TorrentAttribute
	getAttribute(
		String		name )
	{
		notSupported();

		return( null );
	}

	@Override
	public TorrentAttribute
	getPluginAttribute(
		String		name )
	{
		notSupported();

		return( null );
	}

	@Override
	public void
	addListener(
		TorrentManagerListener	l )
	{
		notSupported();
	}

	@Override
	public void
	removeListener(
		TorrentManagerListener	l )
	{
		notSupported();
	}
}
