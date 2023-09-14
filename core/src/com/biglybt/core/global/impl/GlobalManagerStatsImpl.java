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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.biglybt.core.CoreFactory;

/**
 * @author parg
 *
 */

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.dht.DHT;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;
import com.biglybt.core.global.GlobalManagerAdapter;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerListener;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerStats;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.peermanager.piecepicker.util.BitFlags;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AddressUtils;
import com.biglybt.core.util.AsyncDispatcher;
import com.biglybt.core.util.Average;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.GeneralUtils;
import com.biglybt.core.util.GeneralUtils.SmoothAverage;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.SimpleTimer.TimerTickReceiver;
import com.biglybt.core.util.average.AverageFactory;
import com.biglybt.pif.PluginInterface;
import com.biglybt.plugin.dht.DHTPlugin;


public class
GlobalManagerStatsImpl
	implements GlobalManagerStats, TimerTickReceiver
{
	private final GlobalManagerImpl		manager;

	private long smooth_last_sent;
	private long smooth_last_received;

	private int current_smoothing_window 	= GeneralUtils.getSmoothUpdateWindow();
	private int current_smoothing_interval 	= GeneralUtils.getSmoothUpdateInterval();

	private SmoothAverage smoothed_receive_rate 	= GeneralUtils.getSmoothAverage();
	private SmoothAverage smoothed_send_rate 		= GeneralUtils.getSmoothAverage();


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

    private static final Object	PEER_DATA_INIT_KEY 	= new Object();
    private static final Object	PEER_DATA_KEY 		= new Object();
    private static final Object	PEER_DATA_FINAL_KEY = new Object();
   
    private static final Object	DOWNLOAD_DATA_KEY = new Object();

    
    private Map<DownloadManager,List<PEPeer>>	removed_peers = new IdentityHashMap<>();
    
	protected
	GlobalManagerStatsImpl()
	{
		manager = null;
	}
	
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
					dm.setUserData( DOWNLOAD_DATA_KEY, new AggregateStatsDownloadWrapper() );

					dm.addPeerListener(
						new DownloadManagerPeerListener(){
							
							@Override
							public void 
							peerAdded(
								PEPeer peer)
							{
								if ( peer.getPeerState() ==  PEPeer.TRANSFERING ){
									
									saveInitialStats( peer );
									
								}else{
									peer.addListener(
										new PEPeerListener(){
											
											@Override
											public void 
											stateChanged(
												PEPeer peer, 
												int new_state)
											{
												if ( new_state == PEPeer.TRANSFERING ){
													
													saveInitialStats( peer );
													
													peer.removeListener( this );
												}
											}
											
											@Override
											public void sentBadChunk(PEPeer peer, int piece_num, int total_bad_chunks){
											}
											
											@Override
											public void removeAvailability(PEPeer peer, BitFlags peerHavePieces){
											}
											
											@Override
											public void addAvailability(PEPeer peer, BitFlags peerHavePieces){
											}
										});
								}
							}
							
							private void
							saveInitialStats(
								PEPeer		peer )
							{
								PEPeerStats stats = peer.getStats();
								
								long sent = stats.getTotalDataBytesSent();
								long recv = stats.getTotalDataBytesReceived();
								
									// account for the fact that we remember stats across peer reconnects...
								
								if ( sent + recv > 0 ){

									peer.setUserData( PEER_DATA_INIT_KEY, new long[]{ sent, recv });
								}
							}
							
							@Override
							public void 
							peerRemoved(
								PEPeer peer)
							{
								PEPeerStats stats = peer.getStats();
								
								long sent = stats.getTotalDataBytesSent();
								long recv = stats.getTotalDataBytesReceived();
								
								if ( sent + recv > 0 ){

										// gotta snapshot these values now in case this stats object
										// is re-associated :(
									
									peer.setUserData( PEER_DATA_FINAL_KEY, new long[]{ sent, recv });
									
									synchronized( PEER_DATA_KEY ){
									
										List<PEPeer> peers = removed_peers.get( dm );
										
										if ( peers == null ){
											
											peers = new LinkedList<>();
											
											removed_peers.put( dm, peers );
										}
										
										peers.add( peer );
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
    
	
	@Override
	public long
	getSmoothedSendRate()
	{
		return( smoothed_send_rate.getAverage());
	}

	@Override
	public long
	getSmoothedReceiveRate()
	{
		return( smoothed_receive_rate.getAverage());
	}
	
	
	
    private Map<String,CountryDetails>		country_details = new ConcurrentHashMap<>();
    private CountryDetailsImpl				country_total	= new CountryDetailsImpl( "" );
    private AtomicInteger					country_details_seq	= new AtomicInteger();
    private String							country_my_cc	= "";
    
    {
    	country_details.put( country_total.cc, country_total );
    }
    
	public Iterator<CountryDetails>
	getCountryDetails()
	{
		return( country_details.values().iterator());
	}
	
	private AsyncDispatcher stats_dispatcher = new AsyncDispatcher( "GMStats", 1000 );
	
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

			smoothed_send_rate.addValue( up - smooth_last_sent );
			smoothed_receive_rate.addValue( down - smooth_last_received );

			smooth_last_sent 		= up;
			smooth_last_received 	= down;
		}
		
		if ( tick_count % 60 == 0 ){
			
			stats_dispatcher.dispatch(
				new AERunnable(){
					
					@Override
					public void runSupport()
					{
						try{
							if (PeerUtils.hasCountryProvider()) {
								InetAddress ia = NetworkAdmin.getSingleton().getDefaultPublicAddress();

								String[] dets = PeerUtils.getCountryDetails( ia );

								if ( dets != null && dets.length > 0 ){

									country_my_cc = dets[0];
								}
							}
						}catch( Throwable e ){
							
						}
						
						List<DownloadManager>	all_dms = manager.getDownloadManagers();
						
						List<AggregateStatsDownloadWrapper>	dms_list	= new ArrayList<>( all_dms.size() * 2 );
						List<List<PEPeer>>					peer_lists 	= new ArrayList<>( all_dms.size() * 2 );
						
						synchronized( PEER_DATA_KEY ){
							
							if ( !removed_peers.isEmpty()){
						
								for ( Map.Entry<DownloadManager,List<PEPeer>> entry: removed_peers.entrySet()){
																		
									AggregateStatsDownloadWrapper as = (AggregateStatsDownloadWrapper)entry.getKey().getUserData( DOWNLOAD_DATA_KEY );
								
									dms_list.add( as );
																		
									peer_lists.add( entry.getValue());
								}
								
								removed_peers.clear();
							}
						}

						List<AggregateStatsDownloadWrapper>	dms_complete = new ArrayList<>( all_dms.size());
						
						for ( DownloadManager dm: all_dms ){
							
							AggregateStatsDownloadWrapper as = (AggregateStatsDownloadWrapper)dm.getUserData( DOWNLOAD_DATA_KEY );

							if ( as != null ){
							
								dms_complete.add( as );
							}
							
							PEPeerManager pm = dm.getPeerManager();
							
							if ( pm != null ){
								
								List<PEPeer> peers = pm.getPeers();
								
								if ( !peers.isEmpty()){
																			
									dms_list.add( as );
									
									peer_lists.add( peers );
								}
							}
						}
						
							// single threaded here remember
									
						Set<CountryDetailsImpl>	updated = new HashSet<>();
						
						Map<String,long[]>	updates = new HashMap<>();
						
						for ( int i=0; i<dms_list.size();i++){
						
							AggregateStatsDownloadWrapper	dms 	= dms_list.get( i );
							List<PEPeer> 					peers 	= peer_lists.get( i );
						
							for ( PEPeer peer: peers ){
								
								int peer_state = peer.getPeerState();
								
								if ( 	peer_state != PEPeer.TRANSFERING &&
										peer_state != PEPeer.CLOSING && 
										peer_state != PEPeer.DISCONNECTED ){
									
									continue;
								}
								
								if ( peer.isLANLocal()){
									
									if ( !AddressUtils.isExplicitLANRateLimitAddress( peer.getIp())){
										
										continue;
									}
								}
								
								PEPeerStats stats = peer.getStats();
								
								long sent = stats.getTotalDataBytesSent();
								long recv = stats.getTotalDataBytesReceived();
			
								if ( sent + recv > 0 ){
									
									PeerDetails details = (PeerDetails)peer.getUserData( PEER_DATA_KEY );
									
									if ( details == null ){
										
										String[] dets = PeerUtils.getCountryDetails(peer);
				
										details = new PeerDetails( dets==null||dets.length<1?PeerUtils.CC_UNKNOWN:dets[0] );
										
										long[] init_data = (long[])peer.getUserData( PEER_DATA_INIT_KEY );
										
										if ( init_data != null ){
											
											details.sent	= init_data[0];
											details.recv	= init_data[1];
										}
										
										peer.setUserData( PEER_DATA_KEY, details );	
									}
										
									long[] final_data = (long[])peer.getUserData( PEER_DATA_FINAL_KEY );
									
									if ( final_data != null ){
										
										sent	= final_data[0];
										recv	= final_data[1];
									}
									
									long diff_sent	= sent - details.sent;
									long diff_recv	= recv - details.recv;
															
									if ( diff_sent + diff_recv > 0 ){
										
										String cc = details.cc;
			
										{
											// global
										 
											long[] totals = updates.get( cc );
											
											if ( totals == null ){
												
												totals = new long[]{ diff_sent, diff_recv };
												
												updates.put( cc, totals );
												
											}else{
											
												totals[0] += diff_sent;
												totals[1] += diff_recv;
											}
										}
										
										if ( dms != null ){
											
											// download specific
										 
											long[] totals = dms.updates.get( cc );
											
											if ( totals == null ){
												
												totals = new long[]{ diff_sent, diff_recv };
												
												dms.updates.put( cc, totals );
												
											}else{
											
												totals[0] += diff_sent;
												totals[1] += diff_recv;
											}
										}
									}
									
									details.sent 	= sent;
									details.recv	= recv;
								}
							}
						}
						
						for ( AggregateStatsDownloadWrapper dms: dms_complete ){
							
							dms.updateComplete();
						}
						
						long	total_diff_sent	= 0;
						long	total_diff_recv	= 0;
			
						for ( Map.Entry<String,long[]> entry: updates.entrySet()){
							
							String	cc 		= entry.getKey();
							long[]	totals 	= entry.getValue();
								
							long	diff_sent	= totals[0];
							long	diff_recv	= totals[1];
							
							CountryDetailsImpl cd = (CountryDetailsImpl)country_details.get( cc );
							
							if ( cd == null ){
								
								cd = new CountryDetailsImpl( cc );
								
								country_details.put( cc, cd );
							}
							
							updated.add( cd );
							
							if ( diff_sent > 0 ){
							
								cd.last_sent	= diff_sent;
								cd.total_sent	+= diff_sent;
			
								cd.sent_average.update( diff_sent );
								
								total_diff_sent += diff_sent;
								
							}else{
								
								cd.last_sent	= 0;
								
								cd.sent_average.update( diff_sent );
							}
							
							if ( diff_recv > 0 ){
								
								cd.last_recv	= diff_recv;
								cd.total_recv	+= diff_recv;
			
								cd.recv_average.update( diff_recv );
								
								total_diff_recv += diff_recv;
								
							}else{
								
								cd.last_recv	= 0;
								
								cd.recv_average.update( diff_recv );
							}
						}
						
						updated.add( country_total );
						
						if ( total_diff_sent > 0 ){
							
							country_total.last_sent		= total_diff_sent;
							country_total.total_sent	+= total_diff_sent;
							
							country_total.sent_average.update( total_diff_sent );
							
						}else{
							
							country_total.last_sent		= 0;
							
							country_total.sent_average.update( 0 );
						}		
						
						if ( total_diff_recv > 0 ){
							
							country_total.last_recv		= total_diff_recv;
							country_total.total_recv	+= total_diff_recv;
							
							country_total.recv_average.update( total_diff_recv );
							
						}else{
							
							country_total.last_recv		= 0;
							
							country_total.recv_average.update( 0 );
						}
						
						for ( CountryDetails cd: country_details.values()){
							
							if ( !updated.contains( cd )){
								
								CountryDetailsImpl cdi = (CountryDetailsImpl)cd;
								
								cdi.last_recv 	= 0;
								cdi.last_sent	= 0;
								
								cdi.recv_average.update( 0 );
								cdi.sent_average.update( 0 );
							}
						}
						
						country_details_seq.incrementAndGet();
					}
				});	
		}
		
		if ( tick_count % 10 == 0 ){
			
			stats_dispatcher.dispatch(
				new AERunnable(){
					
					@Override
					public void 
					runSupport()
					{							
						for ( Iterator<RemoteStats> it = pending_stats.values().iterator(); it.hasNext();){
							
							RemoteStats stats = it.next();
							
							it.remove();
							
							addRemoteStats( stats );
						}
					}
				});
		}
		
		/* test load
		if ( tick_count % 30 == 0 ){
			
			RemoteStats	stats = 
				new RemoteStats()
				{
					final String[] CCS = { "US", "GB", "FR", "IT", "AU", "DE" };
										
					RemoteCountryStats[]	stats = new RemoteCountryStats[Math.abs(RandomUtils.nextInt(4))+1];
					
					{
						for ( int i=0; i<stats.length;i++){
					
							String cc = CCS[RandomUtils.nextAbsoluteInt() % CCS.length ];

							stats[i] = 
								new RemoteCountryStats()
								{
									public String 
									getCC()
									{
										return( cc );
									}
									
									public long
									getAverageReceivedBytes()
									{
										return( 100*1024 );
									}
								};
						}
					}
					
					public InetAddress
					getRemoteAddress()
					{
						byte[] bytes = new byte[4];
						
						RandomUtils.nextBytes( bytes );
						
						try{
							return( InetAddress.getByAddress( bytes ));
							
						}catch( Throwable e ){
							
							return( null );
						}
					}
					
					public long
					getMonoTime()
					{
						return( SystemTime.getMonotonousTime());
					}
					
					public RemoteCountryStats[]
					getStats()		
					{
						return( stats );
					}
				};
				
			receiveRemoteStats( stats );
		}
		*/
	}

	private ConcurrentHashMap<InetAddress,RemoteStats>	pending_stats = new ConcurrentHashMap<>();
	
	public void
	receiveRemoteStats(
		RemoteStats		stats )
	{
		pending_stats.put( stats.getRemoteAddress(), stats );
	}
	
	
	private static final long STATS_HISTORY_MAX_AGE		= 30*60*1000;
	private static final long STATS_HISTORY_MAX_SAMPLES	= 1000;
	
	private static final long MAX_ALLOWED_BYTES_PER_MIN	= 60*1024*1024*1024L;		// gb/sec limit
	
	private Map<String,Map<String,long[]>>	aggregate_stats = new ConcurrentHashMap<>();
		
	private int sequence;
		
	private long	total_received_overall;
	private long	total_sent_overall;

	private volatile AggregateStatsImpl	as_remote_latest = new AggregateStatsImpl( sequence++ );

	private DHT	dht_biglybt;
		
	private static class
	HistoryEntry
	{
		final long					time	= SystemTime.getMonotonousTime();

		final InetAddress			address;
		final String				cc;
		final RemoteCountryStats[]	stats;
		
		private
		HistoryEntry(
			String		_cc,
			RemoteStats	_stats )
		{
			cc		= _cc;
			address	= _stats.getRemoteAddress();
			stats	= _stats.getStats();
		}
	}
	
	private LinkedList<HistoryEntry>		stats_history 			= new LinkedList<>();
	private Set<InetAddress>				stats_history_addresses	= new HashSet<>();
	
	private void
	addRemoteStats(
		RemoteStats		stats )
	{		
			// add new entry
				
		{
			RemoteCountryStats[] rcs = stats.getStats();

			if ( rcs.length == 0 ){
				
					// nothing of use
				
				return;
			}
			
			InetAddress address = stats.getRemoteAddress();
					
			if ( stats_history_addresses.contains( address )){
				
				return;
			}
			
			String[] o_details = PeerUtils.getCountryDetails( address );
			
			String originator_cc;
			
			if ( o_details == null || o_details.length < 1 ){
			
				originator_cc = PeerUtils.CC_UNKNOWN;
				
			}else{
			
				originator_cc = o_details[0];
			}
			
			Map<String,long[]> map = aggregate_stats.get( originator_cc );
				
			boolean	added = false;
						
			for ( RemoteCountryStats rc: rcs ){
				
				String cc = rc.getCC();
				
				long	recv = rc.getAverageReceivedBytes();
				long	sent = rc.getAverageSentBytes();
									
				if ( recv < 0 || recv > MAX_ALLOWED_BYTES_PER_MIN ){
					recv = 0;
				}
				
				if ( sent < 0 || sent > MAX_ALLOWED_BYTES_PER_MIN ){
					sent = 0;
				}				
				
				if ( sent + recv > 0 ){
										
					if ( cc.isEmpty()){
						
						total_received_overall 	+= recv;
						total_sent_overall		+= sent;
					}
					
					
					if ( map == null ){
						
						map = new ConcurrentHashMap<>();
						
						aggregate_stats.put( originator_cc, map );
					}

					long[]	val = map.get( cc );
					
					if ( val == null ){
						
						map.put( cc, new long[]{ recv, sent });
						
					}else{
						
						val[0]	+= recv;
						val[1]	+= sent;
					}
					
					added = true;
				}
			}
			
			if ( added ){
					
				HistoryEntry entry = new HistoryEntry( originator_cc, stats );
				
				stats_history.addLast( entry );
				
				stats_history_addresses.add( address );
			}
		}
		
			// remove old
		
		boolean	things_are_borked = false;
		
		{
			List<HistoryEntry>	to_remove = new ArrayList<>();

			long	now = SystemTime.getMonotonousTime();
	
			Iterator<HistoryEntry>	it = stats_history.iterator();
			
			while( it.hasNext()){
				
				HistoryEntry entry = it.next();
										
				if ( 	stats_history.size() > STATS_HISTORY_MAX_SAMPLES  ||
						now - entry.time > STATS_HISTORY_MAX_AGE ){
										
					it.remove();
					
					stats_history_addresses.remove( entry.address );
					
					to_remove.add( entry );
					
				}else{
					
					break;
				}
			}
			
			for ( HistoryEntry entry: to_remove ){
								
				String originator_cc = entry.cc;
				
				Map<String,long[]> map = aggregate_stats.get( originator_cc );
		
				if ( map == null ){
					
					things_are_borked = true;
					
					Debug.out( "inconsistent");
					
					break;
				}
				
				for ( RemoteCountryStats rc: entry.stats ){
					
					String cc = rc.getCC();
					
					long	recv = rc.getAverageReceivedBytes();
					long	sent = rc.getAverageSentBytes();
						
					if ( recv < 0 || recv > MAX_ALLOWED_BYTES_PER_MIN ){
						recv = 0;
					}
					
					if ( sent < 0 || sent > MAX_ALLOWED_BYTES_PER_MIN ){
						sent = 0;
					}
					
					if ( recv + sent > 0 ){
						
						long[]	val = map.get( cc );
						
						if ( val == null ){
							
							things_are_borked = true;
							
							Debug.out( "inconsistent");
							
						}else{
							
							long new_recv = val[0] - recv;
							long new_sent = val[1] - sent;
															
							if ( new_recv < 0 || new_sent < 0 ){
							
								things_are_borked = true;
								
								Debug.out( "inconsistent");
								
							}else{

								if ( cc.isEmpty()){
									
									total_received_overall 	-= recv;
									total_sent_overall		-= sent;
								}
								
								if ( new_recv + new_sent == 0 ){
									
									map.remove( cc );
									
								}else{
									
									val[0]	= new_recv;
									val[1]	= new_sent;
								}
							}
						}
					}
				}
				
				if ( map.isEmpty()){
					
					aggregate_stats.remove( originator_cc );
				}
			}
		}
		
		if ( things_are_borked ){
			
			stats_history.clear();
			
			stats_history_addresses.clear();
			
			aggregate_stats.clear();
			
			total_received_overall	= 0;
			total_sent_overall		= 0;
		}
		
		if ( dht_biglybt == null ){
			
			try{
				PluginInterface dht_pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByClass( DHTPlugin.class );
				
				if ( dht_pi != null ){
					
					dht_biglybt = ((DHTPlugin)dht_pi.getPlugin()).getDHT( DHT.NW_BIGLYBT_MAIN );
				}
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
				
		as_remote_latest = 
			new AggregateStatsImpl(
				stats_history.size(),
				dht_biglybt==null?0:(int)dht_biglybt.getControl().getStats().getEstimatedDHTSize(),
				sequence++,
				total_received_overall,
				total_sent_overall,
				aggregate_stats );
	}
	
	private AggregateStatsWrapper	as_remote_wrapper 	= new AggregateStatsWrapper( false );
	private AggregateStatsWrapper	as_local_wrapper 	= new AggregateStatsWrapper( true );
	
	public AggregateStats
	getAggregateRemoteStats()
	{
		return( as_remote_wrapper );
	}
	
	public AggregateStats
	getAggregateLocalStats()
	{
		return( as_local_wrapper );
	}
	
	@Override
	public AggregateStats 
	getAggregateLocalStats(
		DownloadManager dm )
	{
		AggregateStats as =(AggregateStats)dm.getUserData( DOWNLOAD_DATA_KEY );
		
		if ( as == null ){
			
			Debug.out( "AggregateStats are null for " + dm.getDisplayName());
		}
		
		return( as );
	}
	
	private class
	AggregateStatsWrapper
		implements AggregateStats
	{
		final boolean is_local;
		
		private int		last_local_seq = -1;
		
		private Map<String,Map<String,long[]>>	as_local_latest = new HashMap<>();
		
		AggregateStatsWrapper(
			boolean		_is_local )
		{
			is_local	= _is_local;
		}
		
		public int
		getSamples()
		{
			if ( is_local ){
				return( -1 );
			}else{
				return( as_remote_latest.getSamples());

			}
		}
		
		public int
		getEstimatedPopulation()
		{
			if ( is_local ){
				return( -1 );
			}else{
				return( as_remote_latest.getEstimatedPopulation());
			}		
		}
		
		public int
		getSequence()
		{
			if ( is_local ){
				
				int seq = country_details_seq.get();
				
				if ( seq != last_local_seq ){
					
					Iterator<CountryDetails> it = getCountryDetails();
					
					Map<String,Map<String,long[]>> my_sample = new HashMap<>();
					
					Map<String,long[]>	my_stats = new HashMap<>();
					
					my_sample.put( country_my_cc, my_stats );
					
					while( it.hasNext()){
					
						CountryDetails cd = it.next();
						
						my_stats.put( cd.getCC(), new long[]{ cd.getAverageReceived(), cd.getAverageSent(), cd.getTotalReceived(), cd.getTotalSent() } );
					}
					
					as_local_latest	= my_sample;
							
					last_local_seq = seq;
				}
				
				return( seq );
				
			}else{
				
				return( as_remote_latest.getSequence());
			}		
		}
		
		public long
		getLatestReceived()
		{
			if ( is_local ){
				return( -1 );
			}else{
				return( as_remote_latest.getLatestReceived());
			}		
		}
		
		public long
		getLatestSent()
		{
			if ( is_local ){
				return( -1 );
			}else{
				return( as_remote_latest.getLatestSent());

			}		
		}
		
		public Map<String,Map<String,long[]>>
		getStats()
		{
			if ( is_local ){
				return( as_local_latest );
			}else{
				return( as_remote_latest.getStats());

			}		
		}
	}
	
	private class
	AggregateStatsDownloadWrapper
		implements AggregateStats
	{
		private AtomicInteger						dl_country_details_seq		= new AtomicInteger();
		private Map<String,CountryDetailsImpl>		dl_country_details 		= new ConcurrentHashMap<>();

		private int		last_local_seq = -1;
		
		private Map<String,Map<String,long[]>>	as_local_latest = new HashMap<>();
		
			// working storage
		
		private Map<String,long[]>	updates = new HashMap<>();
		
		private boolean	inactive;
		
		AggregateStatsDownloadWrapper()
		{
		}
		
		protected void
		updateComplete()
		{
			if ( updates.isEmpty()){
				
				if ( !inactive ){
					
					boolean has_active = false;
					
					for ( CountryDetailsImpl cd: dl_country_details.values()){
					
						cd.last_recv 	= 0;
						cd.last_sent	= 0;
						
						if ( cd.recv_average.update( 0 ) != 0 ){
							
							has_active = true;
						}
						
						if ( cd.sent_average.update( 0 ) != 0 ){
							
							has_active = true;
						}
					}
					
					if ( !has_active ){
						
						inactive = true;
					}
					
					dl_country_details_seq.incrementAndGet();
				}
				
				return;
			}
			
			inactive = false;
			
			try{
				Set<CountryDetails>	updated = new HashSet<>();
				
				for ( Map.Entry<String,long[]> entry: updates.entrySet()){
					
					String	cc 		= entry.getKey();
					long[]	totals 	= entry.getValue();
						
					long	diff_sent	= totals[0];
					long	diff_recv	= totals[1];
					
					CountryDetailsImpl cd = (CountryDetailsImpl)dl_country_details.get( cc );
					
					if ( cd == null ){
						
						cd = new CountryDetailsImpl( cc );
						
						dl_country_details.put( cc, cd );
					}
					
					updated.add( cd );
					
					if ( diff_sent > 0 ){
					
						cd.last_sent	= diff_sent;
						cd.total_sent	+= diff_sent;
	
						cd.sent_average.update( diff_sent );
						
						//total_diff_sent += diff_sent;
						
					}else{
						
						cd.last_sent	= 0;
						
						cd.sent_average.update( diff_sent );
					}
					
					if ( diff_recv > 0 ){
						
						cd.last_recv	= diff_recv;
						cd.total_recv	+= diff_recv;
	
						cd.recv_average.update( diff_recv );
						
						//total_diff_recv += diff_recv;
						
					}else{
						
						cd.last_recv	= 0;
						
						cd.recv_average.update( diff_recv );
					}
				}
				
				for ( CountryDetails cd: dl_country_details.values()){
					
					if ( !updated.contains( cd )){
						
						CountryDetailsImpl cdi = (CountryDetailsImpl)cd;
						
						cdi.last_recv 	= 0;
						cdi.last_sent	= 0;
						
						cdi.recv_average.update( 0 );
						cdi.sent_average.update( 0 );
					}
				}
				
				dl_country_details_seq.incrementAndGet();
				
			}finally{
				
				updates.clear();
			}
		}
		
		public int
		getSamples()
		{
			return( -1 );
		}
		
		public int
		getEstimatedPopulation()
		{
			return( -1 );		
		}
		
		public int
		getSequence()
		{
			int seq = dl_country_details_seq.get();
			
			if ( seq != last_local_seq ){
				
				Iterator<CountryDetailsImpl> it = dl_country_details.values().iterator();
				
				Map<String,Map<String,long[]>> my_sample = new HashMap<>();
				
				Map<String,long[]>	my_stats = new HashMap<>();
				
				my_sample.put( country_my_cc, my_stats );
				
				while( it.hasNext()){
				
					CountryDetails cd = it.next();
					
					my_stats.put( cd.getCC(), new long[]{ cd.getAverageReceived(), cd.getAverageSent(), cd.getTotalReceived(), cd.getTotalSent() } );
				}
				
				as_local_latest	= my_sample;
						
				last_local_seq = seq;
			}
			
			return( seq );	
		}
		
		public long
		getLatestReceived()
		{
			return( -1 );		
		}
		
		public long
		getLatestSent()
		{
			return( -1 );		
		}
		
		public Map<String,Map<String,long[]>>
		getStats()
		{
			
			return( as_local_latest );	
		}
	}
	
	
	private static class
	AggregateStatsImpl
		implements AggregateStats
	{
		final int	samples;
		final int	population;
		final int	sequence;
		final long	latest_received;
		final long	latest_sent;
		
		final Map<String,Map<String,long[]>>	stats;
		
		AggregateStatsImpl(
			int		_sequence )
		{
			samples			= 0;
			population		= 0;
			sequence		= _sequence;
			latest_received	= 0;
			latest_sent		= 0;
			stats			= new HashMap<>();
		}
		
		AggregateStatsImpl(
			int		_samples,
			int		_population,
			int		_sequence,
			long	_latest_received,
			long	_latest_sent,
			Map<String,Map<String,long[]>>	_stats )
		{
			samples			= _samples;
			population		= _population;
			sequence		= _sequence;
			latest_received	= _latest_received;
			latest_sent		= _latest_sent;
			stats			= _stats;
		}
		
		public int
		getSamples()
		{
			return( samples );
		}
		
		public int
		getEstimatedPopulation()
		{
			return( population );
		}
		
		public int
		getSequence()
		{
			return( sequence );
		}
		
		@Override
		public long 
		getLatestReceived()
		{
			return( latest_received );
		}
	
		@Override
		public long 
		getLatestSent()
		{
			return( latest_sent );
		}
		
		public Map<String,Map<String,long[]>>
		getStats()
		{
			return( stats );
		}
	}
}
