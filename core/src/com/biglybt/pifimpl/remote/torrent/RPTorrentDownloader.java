/*
 * File    : RPTorrentDownloader.java
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

import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentDownloader;
import com.biglybt.pif.torrent.TorrentException;
import com.biglybt.pifimpl.remote.RPException;
import com.biglybt.pifimpl.remote.RPObject;
import com.biglybt.pifimpl.remote.RPReply;
import com.biglybt.pifimpl.remote.RPRequest;

public class
RPTorrentDownloader
	extends		RPObject
	implements 	TorrentDownloader
{
	protected transient TorrentDownloader		delegate;

	public static RPTorrentDownloader
	create(
		TorrentDownloader		_delegate )
	{
		RPTorrentDownloader	res =(RPTorrentDownloader)_lookupLocal( _delegate );

		if ( res == null ){

			res = new RPTorrentDownloader( _delegate );
		}

		return( res );
	}

	protected
	RPTorrentDownloader(
		TorrentDownloader		_delegate )
	{
		super( _delegate );
	}

	@Override
	protected void
	_setDelegate(
		Object		_delegate )
	{
		delegate = (TorrentDownloader)_delegate;
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
		String	method = request.getMethod();

		if ( method.equals( "download")){

			try{
				Torrent to = delegate.download();

				RPTorrent res = RPTorrent.create( to );

				return( new RPReply( res ));

			}catch( TorrentException e ){

				return(new RPReply(e));
			}
		}else if ( method.equals( "download[String]")){

			try{
				Torrent to = delegate.download((String)request.getParams()[0]);

				RPTorrent res = RPTorrent.create( to );

				return( new RPReply( res ));

			}catch( TorrentException e ){

				return(new RPReply(e));
			}
		}

		throw( new RPException( "Unknown method: " + method ));
	}

	// ************************************************************************

	@Override
	public Torrent
	download()

		throws TorrentException
	{
		try{
			RPTorrent resp = (RPTorrent)_dispatcher.dispatch( new RPRequest( this, "download", null )).getResponse();

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
	download(
		String	encoding )

		throws TorrentException
	{
		try{
			RPTorrent resp = (RPTorrent)_dispatcher.dispatch( new RPRequest( this, "download[String]", new Object[]{encoding} )).getResponse();

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
	public void setRequestProperty(String key, Object value)
			throws TorrentException {
		delegate.setRequestProperty(key, value);
	}

	@Override
	public Object getRequestProperty(String key)
			throws TorrentException {
		return delegate.getRequestProperty(key);
	}
}
