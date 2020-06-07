/*
 * File    : TOTorrentException.java
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

package com.biglybt.core.torrent;

public class
TOTorrentException
	extends Exception
{
	public static final int		RT_FILE_NOT_FOUND			= 1;
	public static final int		RT_ZERO_LENGTH				= 2;
	public static final int		RT_TOO_BIG					= 3;
	public static final int		RT_READ_FAILS				= 4;
	public static final int		RT_WRITE_FAILS				= 5;
	public static final int		RT_DECODE_FAILS				= 6;
	public static final int		RT_UNSUPPORTED_ENCODING		= 7;
	public static final int		RT_HASH_FAILS				= 8;
	public static final int		RT_CANCELLED				= 9;
	public static final int		RT_CREATE_FAILED			= 10;

	protected final int	reason;

	public
	TOTorrentException(
		String		_str,
		int			_reason )
	{
		super( _str );

		reason	= _reason;
	}

	public
	TOTorrentException(
		String		_str,
		int			_reason,
		Throwable cause )
	{
		this(_str, _reason);

		initCause(cause);
	}

	public int
	getReason()
	{
		return( reason );
	}
}
