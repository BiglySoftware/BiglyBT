/*
 * File    : PEPeerManagerStatsImpl.java
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

package com.biglybt.core.peer.impl;

import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManagerAdapter;
import com.biglybt.core.peer.PEPeerManagerStats;
import com.biglybt.core.peer.impl.control.PEPeerControlImpl;
import com.biglybt.core.util.Average;
import com.biglybt.core.util.GeneralUtils;
import com.biglybt.core.util.GeneralUtils.SmoothAverage;
import com.biglybt.core.util.SystemTime;

public class
PEPeerManagerStatsImpl
	implements PEPeerManagerStats
{
	private final PEPeerManagerAdapter	adapter;

	private long total_data_bytes_received = 0;
	private long total_protocol_bytes_received = 0;

	private long total_data_bytes_sent = 0;
	private long total_protocol_bytes_sent = 0;

	private long total_data_bytes_received_lan = 0;
	private long total_protocol_bytes_received_lan = 0;

	private long total_data_bytes_sent_lan = 0;
	private long total_protocol_bytes_sent_lan = 0;

	private long totalDiscarded;
	private long hash_fail_bytes;

	private int	last_data_received_seconds;
	private int last_data_sent_seconds;

	private final Average data_receive_speed = Average.getInstance(1000, 10);  //average over 10s, update every 1s.
	private final Average protocol_receive_speed = Average.getInstance(1000, 10);

	private final Average data_send_speed  = Average.getInstance(1000, 10);  //average over 10s, update every 1s.
	private final Average protocol_send_speed  = Average.getInstance(1000, 10);

	private final Average overallSpeed = Average.getInstance(5000, 100); //average over 100s, update every 5s

	private long smooth_last_sent;
	private long smooth_last_received;

	private int current_smoothing_window 	= GeneralUtils.getSmoothUpdateWindow();
	private int current_smoothing_interval 	= GeneralUtils.getSmoothUpdateInterval();

	private SmoothAverage smoothed_receive_rate 	= GeneralUtils.getSmoothAverage();
	private SmoothAverage smoothed_send_rate 		= GeneralUtils.getSmoothAverage();

	private long peak_receive_rate;
	private long peak_send_rate;

	private int	total_incoming;
	private int total_outgoing;

	public
	PEPeerManagerStatsImpl(
		PEPeerControlImpl	_manager )
	{
		adapter	= _manager.getAdapter();
	}

	@Override
	public void discarded(PEPeer peer, int length) {
	  this.totalDiscarded += length;

	  adapter.discarded( peer, length );
	}

	@Override
	public void
	hashFailed(
		int		length )
	{
		hash_fail_bytes += length;
	}

	@Override
	public long
	getTotalHashFailBytes()
	{
		return( hash_fail_bytes );
	}

	@Override
	public void dataBytesReceived(PEPeer peer, int length) {
	  total_data_bytes_received += length;
	  if ( peer.isLANLocal()){
		  total_data_bytes_received_lan += length;
	  }
	  data_receive_speed.addValue(length);

	  if ( length > 0 ){
		  last_data_received_seconds = (int)(SystemTime.getCurrentTime()/1000);
	  }

	  adapter.dataBytesReceived( peer, length );
	}

  @Override
  public void protocolBytesReceived(PEPeer peer, int length) {
    total_protocol_bytes_received += length;
	  if ( peer.isLANLocal()){
		  total_protocol_bytes_received_lan += length;
	  }
    protocol_receive_speed.addValue(length);

    adapter.protocolBytesReceived( peer, length );
  }


	@Override
	public void dataBytesSent(PEPeer peer, int length ) {
	  total_data_bytes_sent += length;
	  if ( peer.isLANLocal()){
		  total_data_bytes_sent_lan += length;
	  }
	  data_send_speed.addValue(length);

	  if ( length > 0 ){
		  last_data_sent_seconds = (int)(SystemTime.getCurrentTime()/1000);
	  }

	  adapter.dataBytesSent( peer, length );
	}

  @Override
  public void protocolBytesSent(PEPeer peer, int length) {
    total_protocol_bytes_sent += length;
	  if ( peer.isLANLocal()){
		  total_protocol_bytes_sent_lan += length;
	  }
    protocol_send_speed.addValue(length);

 	adapter.protocolBytesSent( peer, length );
  }


	@Override
	public void haveNewPiece(int pieceLength) {
	  overallSpeed.addValue(pieceLength);
	}

	@Override
	public long getDataReceiveRate() {
	  return( data_receive_speed.getAverage());
	}

	@Override
	public long getProtocolReceiveRate() {
		return protocol_receive_speed.getAverage();
	}


	@Override
	public long getDataSendRate() {
	  return( data_send_speed.getAverage());
	}

	@Override
	public long getProtocolSendRate() {
		return protocol_send_speed.getAverage();
	}

	@Override
	public long getTotalDiscarded() {
	  return( totalDiscarded );
	}

	public void setTotalDiscarded(long total) {
	  this.totalDiscarded = total;
	}

	@Override
	public long getTotalDataBytesSent() {
	  return total_data_bytes_sent;
	}

  @Override
  public long getTotalProtocolBytesSent() {
    return total_protocol_bytes_sent;
  }

	@Override
	public long getTotalDataBytesReceived() {
	  return total_data_bytes_received;
	}

  @Override
  public long getTotalProtocolBytesReceived() {
    return total_protocol_bytes_received;
  }

	@Override
	public long getTotalDataBytesSentNoLan()
	{
		return( Math.max( total_data_bytes_sent - total_data_bytes_sent_lan, 0 ));
	}
	@Override
	public long getTotalProtocolBytesSentNoLan()
	{
		return( Math.max( total_protocol_bytes_sent - total_protocol_bytes_sent_lan, 0 ));
	}
  	@Override
	  public long getTotalDataBytesReceivedNoLan()
	{
  		return( Math.max( total_data_bytes_received - total_data_bytes_received_lan, 0 ));
  	}
  	@Override
	  public long getTotalProtocolBytesReceivedNoLan()
	{
  		return( Math.max( total_protocol_bytes_received - total_protocol_bytes_received_lan, 0 ));
	}


	@Override
	public long
	getTotalAverage()
	{
	  return( overallSpeed.getAverage() + getDataReceiveRate() );
	}

	@Override
	public int
	getTimeSinceLastDataReceivedInSeconds()
	{
		if ( last_data_received_seconds == 0 ){

			return( -1 );
		}

		int	now = (int)(SystemTime.getCurrentTime()/1000);

		if ( now < last_data_received_seconds ){

			last_data_received_seconds	= now;
		}

		return( now - last_data_received_seconds );
	}

	@Override
	public int
	getTimeSinceLastDataSentInSeconds()
	{
		if ( last_data_sent_seconds == 0 ){

			return( -1 );
		}

		int	now = (int)(SystemTime.getCurrentTime()/1000);

		if ( now < last_data_sent_seconds ){

			last_data_sent_seconds	= now;
		}

		return( now - last_data_sent_seconds );
	}

 	@Override
  public void
 	haveNewConnection(
 		boolean incoming )
 	{
 		if ( incoming ){

 			total_incoming++;

 		}else{

 			total_outgoing++;
 		}
 	}

	@Override
	public int
	getTotalIncomingConnections()
	{
		return( total_incoming );
	}

	@Override
	public int
	getTotalOutgoingConnections()
	{
		return( total_outgoing );
	}

	@Override
	public int
	getPermittedBytesToReceive()
	{
		return( adapter.getPermittedBytesToReceive());
	}

	@Override
	public void
	permittedReceiveBytesUsed(
		int bytes )
	{
		adapter.permittedReceiveBytesUsed(bytes);
	}

	@Override
	public int
	getPermittedBytesToSend()
	{
		return( adapter.getPermittedBytesToSend());
	}

	@Override
	public void
	permittedSendBytesUsed(
		int bytes )
	{
		adapter.permittedSendBytesUsed(bytes);
	}

	@Override
	public long
	getSmoothedDataReceiveRate()
	{
		return(smoothed_receive_rate.getAverage());
	}

	@Override
	public long
	getSmoothedDataSendRate()
	{
		return( smoothed_send_rate.getAverage());
	}

	@Override
	public long
	getPeakDataReceiveRate()
	{
		return( peak_receive_rate );
	}

	@Override
	public long
	getPeakDataSendRate()
	{
		return( peak_send_rate );
	}

	public void
	update(
		int	tick_count )
	{
		peak_receive_rate 	= Math.max( peak_receive_rate, data_receive_speed.getAverage());
		peak_send_rate 		= Math.max( peak_send_rate, data_send_speed.getAverage());

		if ( tick_count % current_smoothing_interval == 0 ){

			int	current_window = GeneralUtils.getSmoothUpdateWindow();

			if ( current_smoothing_window != current_window ){

				current_smoothing_window 	= current_window;
				current_smoothing_interval	= GeneralUtils.getSmoothUpdateInterval();
				smoothed_receive_rate 		= GeneralUtils.getSmoothAverage();
				smoothed_send_rate 			= GeneralUtils.getSmoothAverage();
			}

			long	up 		= total_data_bytes_sent;
			long	down 	= total_data_bytes_received;

			smoothed_send_rate.addValue( up - smooth_last_sent );
			smoothed_receive_rate.addValue( down - smooth_last_received );

			smooth_last_sent 		= up;
			smooth_last_received 	= down;
		}
	}
}
