/*
 * File    : PEPeerManagerStats.java
 * Created : 05-Nov-2003
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

package com.biglybt.core.peer;


public interface
PEPeerManagerStats
{

	public void discarded(PEPeer peer, int length);
	public void hashFailed(int length);

	public void dataBytesReceived(PEPeer peer, int length);
	public void protocolBytesReceived(PEPeer peer, int length);

	public void dataBytesSent(PEPeer peer, int length);
	public void protocolBytesSent(PEPeer peer, int length);

  	public void haveNewPiece(int pieceLength);

  	public void haveNewConnection( boolean incoming );

	public long getDataReceiveRate();
	public long getProtocolReceiveRate();

	public long getDataSendRate();
	public long getProtocolSendRate();

	public long getPeakDataReceiveRate();
	public long getPeakDataSendRate();

	public long getSmoothedDataReceiveRate();
	public long getSmoothedDataSendRate();

	public long getTotalDataBytesSent();
	public long getTotalProtocolBytesSent();

  	public long getTotalDataBytesReceived();
  	public long getTotalProtocolBytesReceived();

	public long getTotalDataBytesSentNoLan();
	public long getTotalProtocolBytesSentNoLan();

  	public long getTotalDataBytesReceivedNoLan();
  	public long getTotalProtocolBytesReceivedNoLan();

	public long getTotalAverage();

	public long getTotalHashFailBytes();
	public long getTotalDiscarded();

	public int getTimeSinceLastDataReceivedInSeconds();
	public int getTimeSinceLastDataSentInSeconds();

	public int getTotalIncomingConnections();
	public int getTotalOutgoingConnections();

	public int getPermittedBytesToReceive();
	public void permittedReceiveBytesUsed( int bytes );

	public int getPermittedBytesToSend();
	public void	permittedSendBytesUsed(	int bytes );
}
