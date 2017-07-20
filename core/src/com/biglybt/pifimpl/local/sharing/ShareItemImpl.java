/*
 * File    : ShareItemImpl.java
 * Created : 31-Dec-2003
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

package com.biglybt.pifimpl.local.sharing;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.sharing.ShareException;
import com.biglybt.pif.sharing.ShareItem;
import com.biglybt.pif.torrent.Torrent;

/**
 * @author parg
 */

public class
ShareItemImpl
	implements ShareItem
{
	protected ShareResourceImpl		resource;
	protected byte[]				fingerprint;
	protected Torrent				torrent;

	protected String				torrent_save_location;

	protected
	ShareItemImpl(
		ShareResourceImpl	_resource,
		byte[]				_fingerprint,
		Torrent				_torrent )

		throws ShareException
	{
		resource	= _resource;
		fingerprint	= _fingerprint;
		torrent 	= _torrent;

		writeTorrent();
	}

	protected
	ShareItemImpl(
		ShareResourceImpl	_resource,
		byte[]				_fingerprint,
		String				_save_location )

		throws ShareException
	{
		resource				= _resource;
		fingerprint				= _fingerprint;
		torrent_save_location 	= _save_location;
	}

	@Override
	public Torrent
	getTorrent()

		throws ShareException
	{
			// TODO: we don't want to hold all torrents in memory. we probably want to
			// create a TorrentFacade that caches enough data to run, say, the sharing UI
			// and then load the torrent from file on demand.

		if( torrent == null ){

			resource.readTorrent(this);
		}

		return( torrent );
	}

	protected void
	writeTorrent()

		throws ShareException
	{
		if ( torrent_save_location == null ){

			torrent_save_location = resource.getNewTorrentLocation();
		}

		resource.writeTorrent( this );
	}

	protected void
	setTorrent(
		Torrent		_torrent )
	{
		torrent	= _torrent;
	}

	@Override
	public File
	getTorrentFile()
	{
		return( resource.getTorrentFile(this));
	}

	protected String
	getTorrentLocation()
	{
		return( torrent_save_location );
	}

	public byte[]
	getFingerPrint()
	{
		return( fingerprint );
	}

	protected void
	delete()
	{
		resource.deleteTorrent(this);
	}

	protected void
	serialiseItem(
		Map		map )
	{
		map.put( "ihash", fingerprint );

		try{
			map.put( "ifile", torrent_save_location.getBytes( Constants.DEFAULT_ENCODING ) );

		}catch( UnsupportedEncodingException e ){

			Debug.printStackTrace( e );
		}
	}

	protected static ShareItemImpl
	deserialiseItem(
		ShareResourceImpl	resource,
		Map					map )

		throws ShareException
	{
		try{
			byte[]	hash = (byte[])map.get( "ihash");

			String	save_location = new String((byte[])map.get("ifile"), Constants.DEFAULT_ENCODING );

			return( new ShareItemImpl(resource,hash,save_location));

		}catch( UnsupportedEncodingException e ){

			throw( new ShareException( "internal error", e ));
		}
	}
}
