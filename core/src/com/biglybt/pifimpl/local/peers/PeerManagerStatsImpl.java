/*
 * Created on 13-Jul-2004
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

package com.biglybt.pifimpl.local.peers;

/**
 * @author parg
 *
 */

import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerManagerStats;
import com.biglybt.pif.peers.PeerManagerStats;

public class
PeerManagerStatsImpl
	implements PeerManagerStats
{
	protected PEPeerManager			manager;
	protected PEPeerManagerStats	stats;

	protected
	PeerManagerStatsImpl(
		PEPeerManager		_manager )
	{
		manager	= _manager;
		stats	= manager.getStats();
	}

	@Override
	public int
	getConnectedSeeds()
	{
		return( manager.getNbSeeds());
	}

	@Override
	public int
	getConnectedLeechers()
	{
		return( manager.getNbPeers());
	}

	@Override
	public long
	getDownloaded()
	{
		return( stats.getTotalDataBytesReceived());
	}

	@Override
	public long
	getUploaded()
	{
		return( stats.getTotalDataBytesSent());
	}

	@Override
	public long
	getDownloadAverage()
	{
		return( stats.getDataReceiveRate());
	}

	@Override
	public long
	getUploadAverage()
	{
		return( stats.getDataSendRate());
	}

	@Override
	public long
	getDiscarded()
	{
		return( stats.getTotalDiscarded());
	}

	@Override
	public long
	getHashFailBytes()
	{
		return( stats.getTotalHashFailBytes());
	}

	@Override
	public int
	getPermittedBytesToReceive()
	{
		return( stats.getPermittedBytesToReceive());
	}

	@Override
	public void
	permittedReceiveBytesUsed(
		int bytes )
	{
		stats.permittedReceiveBytesUsed( bytes );
	}

	@Override
	public int
	getPermittedBytesToSend()
	{
		return( stats.getPermittedBytesToSend());
	}

	@Override
	public void
	permittedSendBytesUsed(
		int bytes )
	{
		stats.permittedSendBytesUsed( bytes );
	}
}
