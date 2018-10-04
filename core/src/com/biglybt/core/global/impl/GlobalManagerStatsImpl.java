/*
 * File    : GlobalManagerStatsImpl.java
 * Created : 21-Oct-2003
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

package com.biglybt.core.global.impl;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author parg
 *
 */

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;
import com.biglybt.core.global.GlobalManagerAdapter;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.global.GlobalManagerStats.CountryDetails;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerManagerListener;
import com.biglybt.core.peer.PEPeerManagerListenerAdapter;
import com.biglybt.core.peer.PEPeerStats;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.peermanager.peerdb.PeerItem;
import com.biglybt.core.util.Average;
import com.biglybt.core.util.GeneralUtils;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SimpleTimer.TimerTickReceiver;
import com.biglybt.core.util.average.AverageFactory;
import com.biglybt.core.util.average.MovingImmediateAverage;


public class
GlobalManagerStatsImpl
	implements GlobalManagerStats, TimerTickReceiver
{
	private final GlobalManagerImpl		manager;

	private long smooth_last_sent;
	private long smooth_last_received;

	private int current_smoothing_window 	= GeneralUtils.getSmoothUpdateWindow();
	private int current_smoothing_interval 	= GeneralUtils.getSmoothUpdateInterval();

	private MovingImmediateAverage smoothed_receive_rate 	= GeneralUtils.getSmoothAverage();
	private MovingImmediateAverage smoothed_send_rate 		= GeneralUtils.getSmoothAverage();


	private long total_data_bytes_received;
    private long total_protocol_bytes_received;

	private long totalDiscarded;

    private long total_data_bytes_sent;
    private long total_protocol_bytes_sent;

    private int	data_send_speed_at_close;

	private final Average data_receive_speed = Average.getInstance(1000, 10);  //average over 10s, update every 1000ms
    private final Average protocol_receive_speed = Average.getInstance(1000, 10);  //average over 10s, update every 1000ms
	private final Average data_receive_speed_no_lan = Average.getInstance(1000, 10);  //average over 10s, update every 1000ms
    private final Average protocol_receive_speed_no_lan = Average.getInstance(1000, 10);  //average over 10s, update every 1000ms

	private final Average data_send_speed = Average.getInstance(1000, 10);  //average over 10s, update every 1000ms
    private final Average protocol_send_speed = Average.getInstance(1000, 10);  //average over 10s, update every 1000ms
	private final Average data_send_speed_no_lan = Average.getInstance(1000, 10);  //average over 10s, update every 1000ms
    private final Average protocol_send_speed_no_lan = Average.getInstance(1000, 10);  //average over 10s, update every 1000ms

    private static final Object	PEER_DATA_KEY = new Object();
    
    private List<PEPeer>	removed_peers = new LinkedList<>();
    
	protected
	GlobalManagerStatsImpl(
		GlobalManagerImpl	_manager )
	{
		manager = _manager;

		load();

		manager.addListener(
			new GlobalManagerAdapter()
			{
				public void
				downloadManagerAdded(
					DownloadManager	dm )
				{
					dm.addPeerListener(
						new DownloadManagerPeerListener(){
							
							@Override
							public void 
							peerRemoved(
								PEPeer peer)
							{
								PEPeerStats stats = peer.getStats();
								
								if ( stats.getTotalDataBytesReceived() + stats.getTotalDataBytesSent() > 0 ){

									synchronized( PEER_DATA_KEY ){
									
										removed_peers.add( peer );
									}
								}
							}
							
							@Override
							public void 
							peerManagerWillBeAdded(
									PEPeerManager manager)
							{
							}
							
							@Override
							public void 
							peerManagerRemoved(
								PEPeerManager manager)
							{
							}
							
							@Override
							public void 
							peerManagerAdded(
								PEPeerManager manager)
							{
							}
							
							@Override
							public void 
							peerAdded(
								PEPeer peer)
							{
							}
						});
				}
			}, true );
		
		SimpleTimer.addTickReceiver( this );
	}

	protected void
	load()
	{
		data_send_speed_at_close	= COConfigurationManager.getIntParameter( "globalmanager.stats.send.speed.at.close", 0 );
	}

	protected void
	save()
	{
		COConfigurationManager.setParameter( "globalmanager.stats.send.speed.at.close", getDataSendRate());
	}

	@Override
	public int
	getDataSendRateAtClose()
	{
		return( data_send_speed_at_close );
	}

  			// update methods

	@Override
	public void discarded(int length) {
		this.totalDiscarded += length;
	}

	@Override
	public void dataBytesReceived(int length, boolean LAN){
		total_data_bytes_received += length;
		if ( !LAN ){
			data_receive_speed_no_lan.addValue(length);
		}
		data_receive_speed.addValue(length);
	}


	@Override
	public void protocolBytesReceived(int length, boolean LAN ){
		total_protocol_bytes_received += length;
		if ( !LAN ){
			protocol_receive_speed_no_lan.addValue(length);
		}
		protocol_receive_speed.addValue(length);
	}

	@Override
	public void dataBytesSent(int length, boolean LAN) {
		total_data_bytes_sent += length;
		if ( !LAN ){
			data_send_speed_no_lan.addValue(length);
		}
		data_send_speed.addValue(length);
	}

	@Override
	public void protocolBytesSent(int length, boolean LAN) {
		total_protocol_bytes_sent += length;
		if ( !LAN ){
			protocol_send_speed_no_lan.addValue(length);
		}
		protocol_send_speed.addValue(length);
	}

	@Override
	public int getDataReceiveRate() {
		return (int)data_receive_speed.getAverage();
	}
	@Override
	public int getDataReceiveRateNoLAN() {
		return (int)data_receive_speed_no_lan.getAverage();
	}
	@Override
	public int getDataReceiveRateNoLAN(int average_period) {
		return (int)(average_period<=0?data_receive_speed_no_lan.getAverage():data_receive_speed_no_lan.getAverage(average_period));
	}
	@Override
	public int getProtocolReceiveRate() {
		return (int)protocol_receive_speed.getAverage();
	}
	@Override
	public int getProtocolReceiveRateNoLAN() {
		return (int)protocol_receive_speed_no_lan.getAverage();
	}
	@Override
	public int getProtocolReceiveRateNoLAN(int average_period) {
		return (int)(average_period<=0?protocol_receive_speed_no_lan.getAverage():protocol_receive_speed_no_lan.getAverage(average_period));
	}

	@Override
	public int getDataAndProtocolReceiveRate(){
		return((int)( protocol_receive_speed.getAverage() + data_receive_speed.getAverage()));
	}

	@Override
	public int getDataSendRate() {
		return (int)data_send_speed.getAverage();
	}
	@Override
	public int getDataSendRateNoLAN() {
		return (int)data_send_speed_no_lan.getAverage();
	}
	@Override
	public int getDataSendRateNoLAN(int average_period) {
		return (int)(average_period<=0?data_send_speed_no_lan.getAverage():data_send_speed_no_lan.getAverage(average_period));
	}

	@Override
	public int getProtocolSendRate() {
		return (int)protocol_send_speed.getAverage();
	}
	@Override
	public int getProtocolSendRateNoLAN() {
		return (int)protocol_send_speed_no_lan.getAverage();
	}
	@Override
	public int getProtocolSendRateNoLAN(int average_period) {
		return (int)(average_period<=0?protocol_send_speed_no_lan.getAverage():protocol_send_speed_no_lan.getAverage(average_period));
	}

	@Override
	public int getDataAndProtocolSendRate(){
		return((int)( protocol_send_speed.getAverage() + data_send_speed.getAverage()));
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


    public long getTotalDiscardedRaw() {
    	return totalDiscarded;
    }

    @Override
    public long getTotalSwarmsPeerRate(boolean downloading, boolean seeding )
    {
    	return( manager.getTotalSwarmsPeerRate(downloading,seeding));
    }


    private static class
    PeerDetails
    {
    	String		cc;
    	long		sent;
    	long		recv;
    	
    	PeerDetails(
    		String		_cc )
    	{
    		cc	= _cc;
    	}
    }
    
    private static class
    CountryDetailsImpl
    	implements CountryDetails
    {
    	String		cc;
    	
    	long		total_sent;
    	long		total_recv;
    	
    	long		last_sent;
    	long		last_recv;
    	
       	com.biglybt.core.util.average.Average		sent_average	= AverageFactory.MovingImmediateAverage( 3 );
       	com.biglybt.core.util.average.Average		recv_average	= AverageFactory.MovingImmediateAverage( 3 );
       	
       	CountryDetailsImpl(
       		String		_cc )
       	{
       		cc	= _cc;
       	}
       	
       	public String
       	getCC()
       	{
       		return( cc );
       	}
       	
		public long
		getTotalSent()
		{
			return( total_sent );
		}
		
		public long
		getLatestSent()
		{
			return( last_sent );
		}
		
		public long
		getAverageSent()
		{
			return((long)sent_average.getAverage());
		}
		
		public long
		getTotalReceived()
		{
			return( total_recv );
		}
		
		public long
		getLatestReceived()
		{
			return( last_recv );
		}
		
		public long
		getAverageReceived()
		{
			return((long)recv_average.getAverage());
		}
		
       	public String
       	toString()
       	{
       		return( "sent: " + total_sent + "/" + last_sent + "/" + (long)sent_average.getAverage() + ", " + 
       				"recv: " + total_recv + "/" + last_recv + "/" + (long)recv_average.getAverage() );
       	}
    }
    
    private Map<String,CountryDetails>		country_details = new ConcurrentHashMap<>();
    private CountryDetailsImpl				country_total	= new CountryDetailsImpl( "" );
    
    {
    	country_details.put( country_total.cc, country_total );
    }
    
	public Iterator<CountryDetails>
	getCountryDetails()
	{
		return( country_details.values().iterator());
	}
	
	@Override
	public void
	tick(
		long		mono_now,
		int			tick_count )
	{
		if ( tick_count % current_smoothing_interval == 0 ){

			int	current_window = GeneralUtils.getSmoothUpdateWindow();

			if ( current_smoothing_window != current_window ){

				current_smoothing_window 	= current_window;
				current_smoothing_interval	= GeneralUtils.getSmoothUpdateInterval();
				smoothed_receive_rate 		= GeneralUtils.getSmoothAverage();
				smoothed_send_rate 			= GeneralUtils.getSmoothAverage();
			}

			long	up 		= total_data_bytes_sent + total_protocol_bytes_sent;
			long	down 	= total_data_bytes_received + total_protocol_bytes_received;

			smoothed_send_rate.update( up - smooth_last_sent );
			smoothed_receive_rate.update( down - smooth_last_received );

			smooth_last_sent 		= up;
			smooth_last_received 	= down;
		}
		
		if ( tick_count % 60 == 0 ){
			
			List<List<PEPeer>>	peer_lists = new LinkedList<>();
			
			synchronized( PEER_DATA_KEY ){
				
				if ( !removed_peers.isEmpty()){
			
					peer_lists.add( removed_peers );
					
					removed_peers = new LinkedList<>();
				}
			}
			
			for ( DownloadManager dm: manager.getDownloadManagers()){
				
				PEPeerManager pm = dm.getPeerManager();
				
				if ( pm != null ){
					
					List<PEPeer> peers = pm.getPeers();
					
					if ( !peers.isEmpty()){
						
						peer_lists.add( peers );
					}
				}
			}
			
				// single threaded here remember
			
			long	total_diff_sent	= 0;
			long	total_diff_recv	= 0;
			
			for ( List<PEPeer> peers: peer_lists ){
				
				for ( PEPeer peer: peers ){
					
					PEPeerStats stats = peer.getStats();
					
					long sent = stats.getTotalDataBytesSent();
					long recv = stats.getTotalDataBytesReceived();

					if ( sent + recv > 0 ){
						
						PeerDetails details = (PeerDetails)peer.getUserData( PEER_DATA_KEY );
						
						if ( details == null ){
							
							String[] dets = PeerUtils.getCountryDetails(peer);
	
							details = new PeerDetails( dets==null||dets.length<1?"??":dets[0] );
							
							peer.setUserData( PEER_DATA_KEY, details );	
						}
																			
						long diff_sent	= sent - details.sent;
						long diff_recv	= recv - details.recv;
						
						if ( diff_sent + diff_recv > 0 ){
							
							String cc = details.cc;

							CountryDetailsImpl cd = (CountryDetailsImpl)country_details.get( cc );
							
							if ( cd == null ){
								
								cd = new CountryDetailsImpl( cc );
								
								country_details.put( cc, cd );
							}
							
							if ( diff_sent > 0 ){
							
								cd.last_sent	= diff_sent;
								cd.total_sent	+= diff_sent;

								cd.sent_average.update( diff_sent );
								
								total_diff_sent += diff_sent;
							}	
							
							if ( diff_recv > 0 ){
								
								cd.last_recv	= diff_recv;
								cd.total_recv	+= diff_recv;

								cd.recv_average.update( diff_recv );
								
								total_diff_recv += diff_recv;
							}
						}
						
						details.sent 	= sent;
						details.recv	= recv;
					}
				}
			}
			
			if ( total_diff_sent > 0 ){
				
				country_total.last_sent		= total_diff_sent;
				country_total.total_sent	+= total_diff_sent;
				
				country_total.sent_average.update( total_diff_sent );
			}	
			
			if ( total_diff_recv > 0 ){
				
				country_total.last_recv		= total_diff_recv;
				country_total.total_recv	+= total_diff_recv;
				
				country_total.recv_average.update( total_diff_recv );
			}
			
			System.out.println( country_details );
		}
	}

	@Override
	public long
	getSmoothedSendRate()
	{
		return((long)(smoothed_send_rate.getAverage()/current_smoothing_interval));
	}

	@Override
	public long
	getSmoothedReceiveRate()
	{
		return((long)(smoothed_receive_rate.getAverage()/current_smoothing_interval));
	}
}