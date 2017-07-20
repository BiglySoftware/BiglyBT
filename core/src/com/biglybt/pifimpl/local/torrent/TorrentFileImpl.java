/*
 * File    : TorrentFileImpl.java
 * Created : 12-Dec-2003
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

package com.biglybt.pifimpl.local.torrent;

/**
 * @author parg
 *
 */

import com.biglybt.pif.torrent.TorrentFile;

public class
TorrentFileImpl
	implements TorrentFile
{
	protected String		name;
	protected long			size;

	protected
	TorrentFileImpl(
		String	_name,
		long	_size )
	{
		name	= _name;
		size	= _size;
	}
	@Override
	public String
	getName()
	{
		return( name );
	}

	@Override
	public long
	getSize()
	{
		return( size );
	}

}
