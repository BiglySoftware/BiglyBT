/*
 * File    : PEPeerStatsImpl.java
 * Created : 15-Oct-2003
 * By      : Olivier
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

/**
 * @author parg
 *
 */

import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerStats;
import com.biglybt.core.util.Average;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pifimpl.local.network.ConnectionImpl;

public class
PEPeerStatsImpl
	implements PEPeerStats
{
	private PEPeer	owner;

    private long total_data_bytes_received = 0;
    private long total_protocol_bytes_received = 0;

    private final Average data_receive_speed = Average.getInstance( 1000, 10 );  //update every 1s, average over 10s
    private final Average protocol_receive_speed = Average.getInstance( 1000, 10 );

    private long total_data_bytes_sent = 0;
    private long total_protocol_bytes_sent = 0;

    private final Average data_send_speed = Average.getInstance( 1000, 5 );   //update every 1s, average over 5s
    private final Average protocol_send_speed = Average.getInstance( 1000, 5 );

    private final Average receive_speed_for_choking = Average.getInstance( 1000, 20 );  //update every 1s, average over 20s
    private final Average estimated_download_speed = Average.getInstance( 5000, 100 );  //update every 5s, average over 100s
    private final Average estimated_upload_speed = Average.getInstance( 3000, 60 );  //update every 3s, average over 60s

    private long total_bytes_discarded = 0;
    private long total_bytes_downloaded = 0;

    private long 	disk_read_bytes	= 0;
    private int		disk_read_count = 0;
    private int		disk_aggregated_read_count = 0;

    private long	last_new_piece_time;

	  public PEPeerStatsImpl( PEPeer _owner ) {
		  owner = _owner;
	  }

	  @Override
	  public PEPeer
	  getPeer()
	  {
		  return( owner );
	  }

	  /* (non-Javadoc)
	 * @see com.biglybt.core.peer.PEPeerStats#setPeer()
	 */
	@Override
	public void setPeer(PEPeer peer) {
		owner = peer;
	}

    @Override
    public void dataBytesSent(int num_bytes ) {
      total_data_bytes_sent += num_bytes;
      data_send_speed.addValue( num_bytes );
    }

    @Override
    public void protocolBytesSent(int num_bytes ) {
      total_protocol_bytes_sent += num_bytes;
      protocol_send_speed.addValue( num_bytes );
    }

    @Override
    public void dataBytesReceived(int num_bytes ) {
      total_data_bytes_received += num_bytes;
      data_receive_speed.addValue( num_bytes );
      receive_speed_for_choking.addValue( num_bytes );
    }

    @Override
    public void protocolBytesReceived(int num_bytes ) {
      total_protocol_bytes_received += num_bytes;
      protocol_receive_speed.addValue( num_bytes );
      //dont count protocol overhead towards a peer's choke/unchoke value, only piece data
    }

    @Override
    public void bytesDiscarded(int num_bytes ) {
      total_bytes_discarded += num_bytes;
    }

    @Override
    public void hasNewPiece(int piece_size ) {
      total_bytes_downloaded += piece_size;

      	// ignore first few seconds here to avoid lazy-bitfield from totally spamming initial
      	// stats

      long connected_at = owner.getConnectionEstablishedMonoTime();
      
      if ( connected_at >= 0 && SystemTime.getMonotonousTime() - connected_at > 5000 ){

    	  estimated_download_speed.addValue( piece_size );

    	  last_new_piece_time = SystemTime.getCurrentTime();
      }
    }

    /**
     * 0 if complete
     * Long.MaxValue if infinite
     */
    @Override
    public long
    getEstimatedSecondsToCompletion()
    {
    	long	remaining = owner.getBytesRemaining();

    	if ( remaining == 0 ){

    		return( 0 );
    	}

    	long	download_rate = estimated_download_speed.getAverage();

    	long	our_send_rate = getDataSendRate();

    		// make sure we at least take into account our own send speed

    	if ( download_rate < our_send_rate ){

    		download_rate = our_send_rate;
    	}

    	if ( download_rate == 0 ){

    		return( Long.MAX_VALUE );
    	}

    	if ( last_new_piece_time > 0 ){

    		long	elapsed_secs = ( SystemTime.getCurrentTime() - last_new_piece_time )/1000;

    		remaining -= elapsed_secs * download_rate;
    	}

    	long secs_remaining = remaining / download_rate;

    	if ( secs_remaining <= 0 ){

    		secs_remaining = 1;
    	}

    	return( secs_remaining );
    }

    @Override
    public void statisticalSentPiece(int piece_size ) {
      estimated_upload_speed.addValue( piece_size );
    }

    @Override
    public long getDataReceiveRate() {  return data_receive_speed.getAverage();  }
    @Override
    public long getProtocolReceiveRate() {  return protocol_receive_speed.getAverage();  }

    @Override
    public long getDataSendRate() {  return data_send_speed.getAverage();  }
    @Override
    public long getProtocolSendRate() {  return protocol_send_speed.getAverage();  }

    @Override
    public long getSmoothDataReceiveRate() {  return receive_speed_for_choking.getAverage();  }

    @Override
    public long getTotalBytesDiscarded() {  return total_bytes_discarded;  }

    @Override
    public long getTotalBytesDownloadedByPeer() {  return total_bytes_downloaded;  }

    @Override
    public long getEstimatedDownloadRateOfPeer() {  return estimated_download_speed.getAverage();  }
    @Override
    public long getEstimatedUploadRateOfPeer() {  return estimated_upload_speed.getAverage();  }

    @Override
    public long getTotalDataBytesReceived() {  return total_data_bytes_received;  }
    @Override
    public long getTotalProtocolBytesReceived() {  return total_protocol_bytes_received;  }

    @Override
    public long getTotalDataBytesSent() {  return total_data_bytes_sent;  }
    @Override
    public long getTotalProtocolBytesSent() {  return total_protocol_bytes_sent;  }

    @Override
    public void
    diskReadComplete(
    	long bytes )
    {
    	disk_read_bytes	+= bytes;
    	disk_read_count++;
    	if ( bytes > 0 ){
    		disk_aggregated_read_count++;
    	}
    }

    @Override
    public int getTotalDiskReadCount(){ return( disk_read_count ); }
    @Override
    public int getAggregatedDiskReadCount(){ return( disk_aggregated_read_count ); }
    @Override
    public long getTotalDiskReadBytes(){ return( disk_read_bytes ); }

    @Override
    public void setUploadRateLimitBytesPerSecond(int bytes ){owner.setUploadRateLimitBytesPerSecond( bytes );}
    @Override
    public void setDownloadRateLimitBytesPerSecond(int bytes ){owner.setDownloadRateLimitBytesPerSecond( bytes );}
    @Override
    public int getUploadRateLimitBytesPerSecond(){return owner.getUploadRateLimitBytesPerSecond();}
    @Override
    public int getDownloadRateLimitBytesPerSecond(){return owner.getDownloadRateLimitBytesPerSecond();}

    @Override
    public int
    getPermittedBytesToSend()
    {
    	return(NetworkManager.getSingleton().getRateHandler(
    		((ConnectionImpl)owner.getPluginConnection()).getCoreConnection(),
    		true ).getCurrentNumBytesAllowed()[0]);
    }

    @Override
    public void
    permittedSendBytesUsed(
    	int num )
    {
    	NetworkManager.getSingleton().getRateHandler(
        	((ConnectionImpl)owner.getPluginConnection()).getCoreConnection(),
        	true ).bytesProcessed( num, 0 );
    }

    @Override
    public int
    getPermittedBytesToReceive()
    {
    	return(NetworkManager.getSingleton().getRateHandler(
        	((ConnectionImpl)owner.getPluginConnection()).getCoreConnection(),
        	false ).getCurrentNumBytesAllowed()[0]);
    }

    @Override
    public void
    permittedReceiveBytesUsed(
    	int num )
    {
       	NetworkManager.getSingleton().getRateHandler(
        	((ConnectionImpl)owner.getPluginConnection()).getCoreConnection(),
        	false ).bytesProcessed( num, 0 );
    }
}
