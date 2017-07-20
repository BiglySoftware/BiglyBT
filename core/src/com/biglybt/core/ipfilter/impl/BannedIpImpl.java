/*
 * Created on 05-Jul-2004
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

package com.biglybt.core.ipfilter.impl;

/**
 * @author parg
 *
 */

import com.biglybt.core.ipfilter.BannedIp;
import com.biglybt.core.util.SystemTime;

public class
BannedIpImpl
	implements BannedIp
{
	protected long			time;
	protected final String		torrent_name;
	protected final String 		ip;

	protected
	BannedIpImpl(
		String	_ip,
		String	_torrent_name,
		boolean	_temporary )
	{
		ip				= _ip;
		torrent_name	= _torrent_name;

		time	= SystemTime.getCurrentTime();

		if ( _temporary ){

			time = -time;
		}
	}

	protected
	BannedIpImpl(
		String	_ip,
		String	_torrent_name,
		long	_time )
	{
		ip				= _ip;
		torrent_name	= _torrent_name;
		time			= _time;
	}

	@Override
	public String
	getIp()
	{
		return( ip );
	}

	public boolean
	isTemporary()
	{
		return( time < 0 );
	}

	@Override
	public long
	getBanningTime()
	{
		return( Math.abs( time ));
	}

	@Override
	public String
	getTorrentName()
	{
		return( torrent_name );
	}
}
