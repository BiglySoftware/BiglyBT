/*
 * File    : PluginPEPeerStatsWrapper.java
 * Created : 01-Dec-2003
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

package com.biglybt.pifimpl.local.peers;

/**
 * @author parg
 *
 */

import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerStats;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.peers.PeerStats;

public class
PeerStatsImpl
	implements PeerStats
{
	private final PeerManagerImpl	peer_manager;
	private final PEPeerManager		manager;
	private final PEPeerStats		delegate;
	private final Peer				owner;

	public
	PeerStatsImpl(
		PeerManagerImpl	_peer_manager,
		Peer			_owner,
		PEPeerStats		_delegate )
	{
		peer_manager	= _peer_manager;
		manager			= peer_manager.getDelegate();
		delegate		= _delegate;
		owner			= _owner;
	}

	public PEPeerStats
	getDelegate()
	{
		return( delegate );
	}

	@Override
	public int getDownloadAverage()
	{
		return( (int)delegate.getDataReceiveRate());
	}

	@Override
	public int getReception()
	{
		return( (int)delegate.getSmoothDataReceiveRate());
	}

	@Override
	public int getUploadAverage()
	{
		return( (int)delegate.getDataSendRate());
	}

	@Override
	public int getTotalAverage()
	{
		return( (int)delegate.getEstimatedDownloadRateOfPeer());
	}

	@Override
	public long getTotalDiscarded()
	{
		return( delegate.getTotalBytesDiscarded());
	}

	@Override
	public long getTotalSent()
	{
		return( delegate.getTotalDataBytesSent());
	}

	@Override
	public long getTotalReceived()
	{
		return( delegate.getTotalDataBytesReceived());
	}

	@Override
	public int getStatisticSentAverage()
	{
		return( (int)delegate.getEstimatedUploadRateOfPeer());
	}

	@Override
	public int
	getPermittedBytesToReceive()
	{
		return( delegate.getPermittedBytesToReceive());
	}

	@Override
	public void
	permittedReceiveBytesUsed(
		int bytes )
	{
		delegate.permittedReceiveBytesUsed( bytes );

		received( bytes );
	}

	@Override
	public int
	getPermittedBytesToSend()
	{
		return( delegate.getPermittedBytesToSend());
	}

	@Override
	public void
	permittedSendBytesUsed(
		int bytes )
	{
		delegate.permittedSendBytesUsed( bytes );

		sent( bytes );
	}

	@Override
	public void
	received(
		int		bytes )
	{
		delegate.dataBytesReceived( bytes );

		manager.dataBytesReceived( delegate.getPeer(), bytes );
	}

	@Override
	public void
	sent(
		int		bytes )
	{
		delegate.dataBytesSent( bytes );

		manager.dataBytesSent( delegate.getPeer(), bytes );
	}

	@Override
	public void
	discarded(
		int		bytes )
	{
		delegate.bytesDiscarded( bytes );

		manager.discarded( delegate.getPeer(), bytes );
	}

	@Override
	public long
	getTimeSinceConnectionEstablished()
	{
		return( peer_manager.getTimeSinceConnectionEstablished( owner ));
	}

	@Override
	public int
	getDownloadRateLimit()
	{
		return( delegate.getDownloadRateLimitBytesPerSecond());
	}

	@Override
	public void
	setDownloadRateLimit(
		int bytes )
	{
		delegate.setDownloadRateLimitBytesPerSecond( bytes );
	}

	@Override
	public int
	getUploadRateLimit()
	{
		return( delegate.getUploadRateLimitBytesPerSecond());
	}

	@Override
	public void
	setUploadRateLimit(
		int bytes )
	{
		delegate.setUploadRateLimitBytesPerSecond( bytes );
	}

	@Override
	public long
	getOverallBytesRemaining()
	{
		return( delegate.getPeer().getBytesRemaining());
	}
}
