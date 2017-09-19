/*
 * File    : PluginException.java
 * Created : 25-Jan-2004
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

package com.biglybt.pif;

/**
 *
 * @author parg
 * @since 1.0.0.0
 */

public class
PluginException
	extends Exception
{
	public
	PluginException(
		String		str )
	{
		super( str );
	}

	public
	PluginException(
		String		str,
		Throwable 	e )
	{
		super( str, e );
	}

	public PluginException(Throwable e) {
		super(e);
	}

}
