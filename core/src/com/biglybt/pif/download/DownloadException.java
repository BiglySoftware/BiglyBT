/*
 * File    : DownloadException.java
 * Created : 08-Jan-2004
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

package com.biglybt.pif.download;

/** Throws by various Download methods to indicate errors
 *
 * @author parg
 * @since 2.0.7.0
 */

public class
DownloadException
	extends Exception
{
	public
	DownloadException(
			String	str )
	{
		super(str);
	}

	public
	DownloadException(
		String		str,
		Throwable 	cause )
	{
		super(str,cause);
	}
}
