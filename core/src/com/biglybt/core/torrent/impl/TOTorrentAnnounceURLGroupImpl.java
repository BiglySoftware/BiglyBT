/*
 * File    : TOTorrentAnnounceURLGroupImpl.java
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

import com.biglybt.core.torrent.TOTorrentAnnounceURLGroup;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.torrent.TOTorrentListener;
import com.biglybt.core.util.TorrentUtils;

public class
TOTorrentAnnounceURLGroupImpl
	implements TOTorrentAnnounceURLGroup
{
	private final TOTorrentImpl	torrent;
	private TOTorrentAnnounceURLSet[]		sets;

	private volatile long	uid = TorrentUtils.getAnnounceGroupUID();

	protected
	TOTorrentAnnounceURLGroupImpl(
		TOTorrentImpl	_torrent )
	{
		torrent	= _torrent;

		sets = new TOTorrentAnnounceURLSet[0];
	}
	
	@Override
	public long 
	getUID()
	{
		return( uid );
	}

	protected void
	addSet(
		TOTorrentAnnounceURLSet	set )
	{
		TOTorrentAnnounceURLSet[] old = sets;
		
		TOTorrentAnnounceURLSet[]	new_sets = new TOTorrentAnnounceURLSet[sets.length+1];

		System.arraycopy( sets, 0, new_sets, 0, sets.length );

		new_sets[new_sets.length-1] = set;

		sets = new_sets;

		uid = TorrentUtils.getAnnounceGroupUID();
		
		torrent.fireChanged( TOTorrentListener.CT_ANNOUNCE_URLS, new Object[]{ old, sets } );
	}

	@Override
	public TOTorrentAnnounceURLSet[]
	getAnnounceURLSets()
	{
		return( sets );
	}

	@Override
	public void
	setAnnounceURLSets(
		TOTorrentAnnounceURLSet[]	_sets )
	{
		TOTorrentAnnounceURLSet[] old = sets;
		
		sets = _sets;

		uid = TorrentUtils.getAnnounceGroupUID();
		
		torrent.fireChanged( TOTorrentListener.CT_ANNOUNCE_URLS, new Object[]{ old, sets });
	}


	@Override
	public TOTorrentAnnounceURLSet
	createAnnounceURLSet(
		URL[]	urls )
	{
		return( new TOTorrentAnnounceURLSetImpl( torrent, urls ));
	}

}
