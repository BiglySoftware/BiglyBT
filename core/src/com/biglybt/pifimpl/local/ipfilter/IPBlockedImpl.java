/*
 * File    : IPBlocked.java
 * Created : 05-Mar-2004
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

package com.biglybt.pifimpl.local.ipfilter;

/**
 * @author parg
 *
 */

import com.biglybt.core.ipfilter.BlockedIp;
import com.biglybt.pif.ipfilter.IPBlocked;
import com.biglybt.pif.ipfilter.IPFilter;
import com.biglybt.pif.ipfilter.IPRange;

public class
IPBlockedImpl
	implements IPBlocked
{
	protected IPFilter	filter;
	protected BlockedIp	blocked;

	protected
	IPBlockedImpl(
		IPFilter	_filter,
		BlockedIp	_blocked )
	{
		filter	= _filter;
		blocked	= _blocked;
	}

	@Override
	public String
	getBlockedIP()
	{
		return( blocked.getBlockedIp());
	}

	@Override
	public String
	getBlockedTorrentName()
	{
		return(blocked.getTorrentName());
	}

	@Override
	public long
	getBlockedTime()
	{
		return( blocked.getBlockedTime());
	}

	@Override
	public IPRange
	getBlockingRange()
	{
	 	return( new IPRangeImpl( filter, blocked.getBlockingRange()));
	}
}
