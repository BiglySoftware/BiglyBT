/*
 * Created on 11-Oct-2004
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.core.util;

/**
 * @author parg
 *
 */

import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;

public class
UnresolvableHostManager
{
	// sometimes we need an IP address for a host in order to function (for example, when banning
	// peers) but the name we have got a host is unresolvable (for whatever reason, e.g. its the
	// name of an TOR hidden service). Here we map such hosts onto class E (i.e. experimental) IP
	// addresses so things mainly work

	protected static int	next_address	= 0xf0000000 + RandomUtils.nextInt(0x00ffffff);

	protected static final Map	host_map	= new HashMap();

	public static int
	getPseudoAddress(
		String		str )
	{
		synchronized( host_map ){

			Integer	res = (Integer)host_map.get(str);

			if ( res == null ){

				res = new Integer( next_address++ );

				if (Logger.isEnabled())
					Logger.log(new LogEvent(LogIDs.NET, "Allocated pseudo IP address '"
							+ Integer.toHexString(res.intValue()) + "' for host '" + str
							+ "'"));

				host_map.put( str, res );
			}

			return( res.intValue());
		}
	}

	public static boolean
	isPseudoAddress(
		String	str )
	{
		synchronized( host_map ){

			return( host_map.get(str) != null );
		}
	}
}
