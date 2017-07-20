/*
 * File    : PRTorrent.java
 * Created : 28-Jan-2004
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

import java.io.File;
import java.net.URL;
import java.util.Map;

import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAnnounceURLList;
import com.biglybt.pif.torrent.TorrentException;
import com.biglybt.pif.torrent.TorrentFile;
import com.biglybt.pifimpl.remote.RPException;
import com.biglybt.pifimpl.remote.RPObject;
import com.biglybt.pifimpl.remote.RPReply;
import com.biglybt.pifimpl.remote.RPRequest;


public class
RPTorrent
	extends		RPObject
	implements 	Torrent
{
	protected transient Torrent		delegate;

		// don't change these field names as they are visible on XML serialisation

	public String		name;
	public long			size;
	public byte[]		hash;


	public static RPTorrent
	create(
		Torrent		_delegate )
	{
		RPTorrent	res =(RPTorrent)_lookupLocal( _delegate );

		if ( res == null ){

			res = new RPTorrent( _delegate );
		}

		return( res );
	}

	protected
	RPTorrent(
		Torrent		_delegate )
	{
		super( _delegate );

		delegate	= _delegate;
	}

	@Override
	protected void
	_setDelegate(
		Object		_delegate )
	{
		delegate = (Torrent)_delegate;

		name		= delegate.getName();
		size		= delegate.getSize();
		hash		= delegate.getHash();
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

		/*
		 if ( method.equals( "getPluginProperties")){

		 return( new RPReply( delegate.getPluginProperties()));
		 }
		 */

		throw( new RPException( "Unknown method: " + method ));
	}

	@Override
	public String
	getName()
	{
		return( name );
	}

	@Override
	public URL
	getAnnounceURL()
	{
		notSupported();

		return( null );
	}

	@Override
	public void
	setAnnounceURL(
		URL		url )
	{
		notSupported();
	}

	@Override
	public TorrentAnnounceURLList
	getAnnounceURLList()
	{
		notSupported();

		return( null );
	}

	@Override
	public boolean
	isDecentralised()
	{
		notSupported();

		return( false );
	}

	@Override
	public boolean
	isDecentralisedBackupEnabled()
	{
		notSupported();

		return( false );
	}

	@Override
	public void
	setDecentralisedBackupRequested(
		boolean	requested )
	{
		notSupported();
	}

	@Override
	public boolean
	isDecentralisedBackupRequested()
	{
		notSupported();

		return( false );
	}

	@Override
	public boolean
	isPrivate()
	{
		notSupported();

		return( false );
	}

	@Override
	public boolean
	wasCreatedByUs()
	{
		notSupported();

		return( false );
	}

	@Override
	public void
	setPrivate(
		boolean	priv )
	{
		notSupported();
	}

	@Override
	public byte[]
	getHash()
	{
		return( hash );
	}

	@Override
	public long
	getSize()
	{
		return( size );
	}

	@Override
	public String
	getComment()
	{
		notSupported();

		return(null);
	}

	@Override
	public void
	setComment(
		String	comment )
	{
		notSupported();
	}

	@Override
	public long
	getCreationDate()
	{
		notSupported();

		return(0);
	}

	@Override
	public String
	getCreatedBy()
	{
		notSupported();

		return(null);
	}
	@Override
	public long
	getPieceSize()
	{
		notSupported();

		return(0);
	}
	@Override
	public long
	getPieceCount()
	{
		notSupported();

		return(0);
	}

	@Override
	public byte[][]
	getPieces()
	{
		notSupported();

		return(null);
	}

	@Override
	public URL
	getMagnetURI()
	{
		notSupported();

		return( null );
	}

	@Override
	public String
	getEncoding()
	{
		notSupported();

		return( null );
	}

	@Override
	public void
	setEncoding(String encoding)
	{
		notSupported();
	}

	@Override
	public void
	setDefaultEncoding()
	{
		notSupported();
	}

	@Override
	public TorrentFile[]
	getFiles()
	{
		notSupported();

		return(null);
	}

	@Override
	public Object
	getAdditionalProperty(
		String		name )
	{
		notSupported();

		return(null);
	}

	@Override
	public Torrent
	removeAdditionalProperties()
	{
		notSupported();

		return( null );
	}

	@Override
	public void
	setPluginStringProperty(
		String		name,
		String		value )
	{
		notSupported();
	}

	@Override
	public String
	getPluginStringProperty(
		String		name )
	{
		notSupported();

		return( null );
	}

	@Override
	public void
	setMapProperty(
		String		name,
		Map			value )
	{
		notSupported();
	}

	@Override
	public Map
	getMapProperty(
		String		name )
	{
		notSupported();

		return( null );
	}

	@Override
	public Map
	writeToMap()

		throws TorrentException
	{
		notSupported();

		return( null );
	}

	@Override
	public byte[]
	writeToBEncodedData()

		throws TorrentException
	{
		notSupported();

		return( null );
	}

	@Override
	public void
	writeToFile(
		File		file )

		throws TorrentException
	{
		notSupported();
	}

	@Override
	public void
	save()
		throws TorrentException
	{
		notSupported();
	}

	@Override
	public void
	setComplete(
		File		data_dir )

		throws TorrentException
	{
		notSupported();
	}

	@Override
	public boolean
	isComplete()
	{
		notSupported();

		return( false );
	}
	@Override
	public boolean isSimpleTorrent() {notSupported(); return false;}

	@Override
	public Torrent getClone() throws TorrentException {
		notSupported();
		return null;
	}
}
