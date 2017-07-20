/*
 * File    : TOTorrentAnnounceURLSetImpl.java
 * Created : 5 Oct. 2003
 * By      : Parg
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

package com.biglybt.core.torrent.impl;

import java.net.URL;

import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.util.StringInterner;

public class
TOTorrentAnnounceURLSetImpl
	implements TOTorrentAnnounceURLSet
{
	private final TOTorrentImpl	torrent;
	private URL[]			urls;

	protected
	TOTorrentAnnounceURLSetImpl(
		TOTorrentImpl	_torrent,
		URL[]			_urls )
	{
		torrent	= _torrent;

		setAnnounceURLs( _urls );
	}

	@Override
	public URL[]
	getAnnounceURLs()
	{
		return( urls );
	}


	@Override
	public void
	setAnnounceURLs(
		URL[]	_urls )
	{
		urls	= new URL[_urls.length];

		for (int i=0;i<urls.length;i++){

			urls[i]	= StringInterner.internURL(torrent.anonymityTransform( _urls[i] ));
		}
	}
}