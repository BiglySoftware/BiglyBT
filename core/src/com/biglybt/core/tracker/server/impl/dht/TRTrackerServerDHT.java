/*
 * Created on 13-Feb-2005
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

package com.biglybt.core.tracker.server.impl.dht;

import java.net.InetAddress;

import com.biglybt.core.tracker.server.TRTrackerServerRequestListener;
import com.biglybt.core.tracker.server.impl.TRTrackerServerImpl;

/**
 * @author parg
 *
 */

public class
TRTrackerServerDHT
	extends TRTrackerServerImpl
{
	public
	TRTrackerServerDHT(
		String		_name,
		boolean		_start_up_ready )
	{
		super( _name, _start_up_ready );
	}

	@Override
	public String
	getHost()
	{
		return( "dht");
	}

	@Override
	public int
	getPort()
	{
		return( -1 );
	}

	@Override
	public boolean
	isSSL()
	{
		return( false );
	}

	@Override
	public InetAddress
	getBindIP()
	{
		return( null );
	}

	@Override
	public void
	addRequestListener(
		TRTrackerServerRequestListener	l )
	{
	}

	@Override
	public void
	removeRequestListener(
		TRTrackerServerRequestListener	l )
	{
	}

	@Override
	protected void
	closeSupport()
	{
		destroySupport();
	}
}
