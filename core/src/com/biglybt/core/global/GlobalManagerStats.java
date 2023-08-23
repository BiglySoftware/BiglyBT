/*
 * File    : GlobalManagerStats.java
 * Created : 23-Oct-2003
 * By      : stuff
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

package com.biglybt.core.global;

import java.net.InetAddress;
import java.util.*;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.GeneralUtils;

/**
 * @author parg
 *
 */
public interface
GlobalManagerStats
{
	/**
	 * Data Receive Rate over the last 10s
	 */
	public int getDataReceiveRate();
	public int getDataReceiveRateNoLAN();
	public int getDataReceiveRateNoLAN(int average_period);

	public int getProtocolReceiveRate();
	public int getProtocolReceiveRateNoLAN();
	public int getProtocolReceiveRateNoLAN(int average_period);

	public int getDataAndProtocolReceiveRate();

	/**
	 * Data Send Rate over the last 10s
	 */
	public int getDataSendRate();
	public int getDataSendRateNoLAN();
	public int getDataSendRateNoLAN(int average_period);

	public int getProtocolSendRate();
	public int getProtocolSendRateNoLAN();
	public int getProtocolSendRateNoLAN(int average_period);

	public int getDataAndProtocolSendRate();

	/**
	 * Smoothed Send Rate, including data and protocol, based on
	 * "Stats Smoothing Secs", see @link {@link GeneralUtils} for defaults and limits
	 */
	public long	getSmoothedSendRate();
	/**
	 * Smoothed Receive Rate, including data and protocol, based on
	 * "Stats Smoothing Secs" see @link {@link GeneralUtils} for defaults and limits
	 */
	public long	getSmoothedReceiveRate();

	public int getDataSendRateAtClose();

	public long getTotalDataBytesReceived();

	public long getTotalProtocolBytesReceived();

	public default long
	getTotalDataProtocolBytesReceived()
	{
		return(getTotalDataBytesReceived()+getTotalProtocolBytesReceived());
	}
	
	public long getTotalDataBytesSent();

	public long getTotalProtocolBytesSent();

	public default long
	getTotalDataProtocolBytesSent()
	{
		return(getTotalDataBytesSent()+getTotalProtocolBytesSent());
	}
	
  	public long getTotalSwarmsPeerRate( boolean downloading, boolean seeding );

		// set methods

	public void	dataBytesSent( int bytes, boolean LAN );
	public void protocolBytesSent( int bytes, boolean LAN );

	public void dataBytesReceived( int bytes, boolean LAN );
	public void protocolBytesReceived( int bytes, boolean LAN );

	public void
	discarded(
		int		bytes );
	
	public interface
	CountryDetails
	{
			// values are minute based (last minute, minute average)
		
		public String
		getCC();
		
		public long
		getTotalSent();
		
		public long
		getLatestSent();
		
		public long
		getAverageSent();
		
		public long
		getTotalReceived();
		
		public long
		getLatestReceived();
		
		public long
		getAverageReceived();
	}
	
	public Iterator<CountryDetails>
	getCountryDetails();
	
	public interface
	RemoteCountryStats
	{
		public String 
		getCC();
		
			/**
			 * 
			 * @return bytes per MINUTE
			 */
		
		public long
		getAverageReceivedBytes();
		
		public long
		getAverageSentBytes();
	}
	
	public interface
	RemoteStats
	{
		public InetAddress
		getRemoteAddress();
				
		public RemoteCountryStats[]
		getStats();
	}
	
	public void
	receiveRemoteStats(
		RemoteStats		stats );
	
	public interface
	AggregateStats
	{
		public int
		getSamples();
		
		public int
		getEstimatedPopulation();
		
		public int
		getSequence();
		
		public long
		getLatestReceived();
		
		public long
		getLatestSent();
		
		public Map<String,Map<String,long[]>>
		getStats();
	}
	
	public AggregateStats
	getAggregateRemoteStats();
	
	public AggregateStats
	getAggregateLocalStats();
	
	public AggregateStats
	getAggregateLocalStats(
		DownloadManager		dm );
}
