/*
 * Created on May 27, 2008
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.plugin.net.buddy.tracker;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerAdapter;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.*;
import com.biglybt.pif.peers.*;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.torrent.TorrentManager;
import com.biglybt.pif.ui.config.BooleanParameter;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterListener;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.plugin.net.buddy.BuddyPluginNetwork;
import com.biglybt.plugin.net.buddy.BuddyPlugin;
import com.biglybt.plugin.net.buddy.BuddyPluginAZ2TrackerListener;
import com.biglybt.plugin.net.buddy.BuddyPluginBuddy;
import com.biglybt.plugin.net.buddy.BuddyPluginListener;
import com.biglybt.plugin.net.buddy.PartialBuddy;

public class
BuddyPluginTracker
	implements BuddyPluginListener, DownloadManagerListener, BuddyPluginAZ2TrackerListener, DownloadPeerListener
{
	private  static final Object	PEER_DOWNLOAD_KEY				= new Object();		// maps to { Download, isPartial }
	private  static final Object	PEER_UPLOAD_PRIORITY_KEY		= new Object();

	private static final Object	PEER_STATS_KEY	= new Object();
	private static final Object PB_PEER_KEY		= new Object();
	
	public static final int BUDDY_NETWORK_IDLE			= 1;
	public static final int BUDDY_NETWORK_OUTBOUND		= 2;
	public static final int BUDDY_NETWORK_INBOUND		= 3;
	public static final int BUDDY_NETWORK_INOUTBOUND	= 4;

	private static final int	TRACK_CHECK_PERIOD		= 15*1000;
	private static final int	TRACK_CHECK_TICKS		= TRACK_CHECK_PERIOD/ BuddyPlugin.TIMER_PERIOD;

	
	
	private static final int	PARTIAL_PEER_CHECK_PERIOD	= 5*1000;
	private static final int	PARTIAL_PEER_CHECK_TICKS	= PARTIAL_PEER_CHECK_PERIOD/BuddyPlugin.TIMER_PERIOD;
	
	private static final int	PEER_CHECK_PERIOD		= 60*1000;
	private static final int	PEER_CHECK_TICKS		= PEER_CHECK_PERIOD/BuddyPlugin.TIMER_PERIOD;

	private static final int	PEER_RECHECK_PERIOD		= 120*1000;
	private static final int	PEER_RECHECK_TICKS		= PEER_RECHECK_PERIOD/BuddyPlugin.TIMER_PERIOD;

	private static final int	PEER_CHECK_INTERVAL		= 1*60*1000;

	private static final int	SHORT_ID_SIZE			= 4;
	private static final int	FULL_ID_SIZE			= 20;

	private static final int	REQUEST_TRACKER_SUMMARY	= 1;
	private static final int	REPLY_TRACKER_SUMMARY	= 2;
	private static final int	REQUEST_TRACKER_STATUS	= 3;
	private static final int	REPLY_TRACKER_STATUS	= 4;
	private static final int	REQUEST_TRACKER_CHANGE	= 5;
	private static final int	REPLY_TRACKER_CHANGE	= 6;
	private static final int	REQUEST_TRACKER_ADD		= 7;
	private static final int	REPLY_TRACKER_ADD		= 8;

	private static final int	RETRY_SEND_MIN			= 5*60*1000;
	private static final int	RETRY_SEND_MAX			= 60*60*1000;

	private static final int	BUDDY_NO		= 0;
	private static final int	BUDDY_MAYBE		= 1;
	private static final int	BUDDY_YES		= 2;

	private final BuddyPlugin		plugin;

	private final TorrentAttribute		ta_networks;

	private boolean			plugin_enabled;
	private boolean			tracker_enabled;
	private boolean			seeding_only;

	private boolean			tracker_so_enabled;

	private boolean			old_plugin_enabled;
	private boolean			old_tracker_enabled;
	private boolean			old_seeding_only;

	private int				network_status = BUDDY_NETWORK_IDLE;

	private Set<BuddyPluginBuddy>				online_buddies 			= new HashSet<>();
	private Map<String,List<BuddyPluginBuddy>>	online_buddy_ips		= new HashMap<>();

	private Map<String,PartialBuddyData>		partial_buddies			= new HashMap<>();
	
	private Set<Download>	tracked_downloads		= new HashSet<>();
	private int				download_set_id;

	private Set<Download>	last_processed_download_set	= new HashSet<>();
	
	private int				last_processed_download_set_id;

	private Map<HashWrapper,List<Download>>	short_id_map	= new HashMap<>();
	private Map<HashWrapper,Download>		full_id_map		= new HashMap<>();

	private Set<Download>				actively_tracking	= new HashSet<>();

	private CopyOnWriteSet<Peer>	buddy_peers	= new CopyOnWriteSet<>(true);

	private CopyOnWriteList<BuddyPluginTrackerListener>	listeners = new CopyOnWriteList<>();

	private TimerEventPeriodic	buddy_stats_timer;

	private Average buddy_receive_speed = Average.getInstance(1000, 10);

	private Average buddy_send_speed 	= Average.getInstance(1000, 10);

	public
	BuddyPluginTracker(
		BuddyPlugin				_plugin,
		BooleanParameter 		tracker_enable,
		BooleanParameter		tracker_so_enable )
	{
		plugin		= _plugin;

		PluginInterface pi = plugin.getPluginInterface();

		TorrentManager  tm = pi.getTorrentManager();

		ta_networks 			= tm.getAttribute( TorrentAttribute.TA_NETWORKS );

		tracker_enabled = tracker_enable.getValue();

		tracker_enable.addListener(
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					Parameter param )
				{
					tracker_enabled = tracker_enable.getValue();

					checkEnabledState();
				}
			});

		tracker_so_enabled = tracker_so_enable.getValue();

		tracker_so_enable.addListener(
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						Parameter param )
					{
						tracker_so_enabled = tracker_so_enable.getValue();
					}
				});

		// Assumed if we already have a plugin reference, that the
		// Core is available
		GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();

		gm.addListener(
			new GlobalManagerAdapter()
			{
				@Override
				public void
				seedingStatusChanged(
					boolean seeding_only_mode,
					boolean potentially_seeding_only )
				{
					seeding_only = potentially_seeding_only;

					checkEnabledState();
				}
			}, false );

		seeding_only = gm.isPotentiallySeedingOnly();

		checkEnabledState();
	}

	public void
	initialise()
	{
		plugin_enabled = plugin.isClassicEnabled();

		checkEnabledState();

		List<BuddyPluginBuddy> buddies = plugin.getBuddies();

		for (int i=0;i<buddies.size();i++){

			buddyAdded(buddies.get(i));
		}

		plugin.addListener( this );

		for ( BuddyPluginNetwork pn: plugin.getPluginNetworks()){
			
			pn.getAZ2Handler().addTrackerListener( this );
		}

		plugin.getPluginInterface().getDownloadManager().addListener( this, true );
	}

	public void
	tick(
		int		tick_count )
	{
		if ( tick_count % TRACK_CHECK_TICKS == 0 ){

			checkTracking();
		}

		if ( ( tick_count-1 ) % TRACK_CHECK_TICKS == 0 ){

			doTracking();
		}

		if ( tick_count % PARTIAL_PEER_CHECK_TICKS == 0 ){

			checkPartialPeers();
		}
		
		if ( tick_count % PEER_CHECK_TICKS == 0 ){

			checkPeers();
		}

		if ( tick_count % PEER_RECHECK_TICKS == 0 ){

			recheckPeers();
		}
	}

	public int
	getNetworkStatus()
	{
		return( network_status );
	}

	public long
	getNetworkReceiveBytesPerSecond()
	{
		return( buddy_receive_speed.getAverage());
	}

	public long
	getNetworkSendBytesPerSecond()
	{
		return( buddy_send_speed.getAverage());
	}

	protected void
	doTracking()
	{
		if ( !( plugin_enabled && tracker_enabled )){

			return;
		}

		Map<BuddyPluginBuddy,List<Download>>	peers_to_check = new HashMap<>();
		
		Set<Download> active_set = new HashSet<>();

		synchronized( online_buddies ){

			Iterator<BuddyPluginBuddy> it = online_buddies.iterator();

			while( it.hasNext()){

				BuddyPluginBuddy	buddy = it.next();

				BuddyTrackingData buddy_data = getBuddyData( buddy );

				Map<Download,Boolean> active = buddy_data.getDownloadsToTrack();

				if ( active.size() > 0 ){

					Iterator<Map.Entry<Download,Boolean>> it2 = active.entrySet().iterator();

					List<Download> check_peers = new ArrayList<>();

					while( it2.hasNext()){

						Map.Entry<Download,Boolean> entry = it2.next();

						Download 	dl 			= entry.getKey();
						boolean		check_peer 	= entry.getValue();

						if ( check_peer ){

							check_peers.add( dl );
						}

						active_set.add( dl );
					}

					if ( check_peers.size() > 0 ){

						peers_to_check.put( buddy, check_peers );
					}
				}
			}
			
			for ( PartialBuddyData pbd: partial_buddies.values()){
									
				active_set.addAll( pbd.downloads );
			}
		}

			// check the addition of peer listeners based on what's active

		synchronized( actively_tracking ){

			Iterator<Download> it = active_set.iterator();

			while( it.hasNext()){

				Download dl = it.next();

				if ( !actively_tracking.contains( dl )){

					actively_tracking.add( dl );

					trackPeers( dl );
				}
			}

			it = actively_tracking.iterator();

			while( it.hasNext()){

				Download dl = (Download)it.next();

				if ( !active_set.contains( dl )){

					it.remove();

					untrackPeers( dl );
				}
			}
		}

			// check peer connections

		Iterator<Map.Entry<BuddyPluginBuddy,List<Download>>> it = peers_to_check.entrySet().iterator();

		boolean lan = plugin.getPeersAreLANLocal();

		while( it.hasNext()){

			Map.Entry<BuddyPluginBuddy,List<Download>>	entry = it.next();

			BuddyPluginBuddy buddy = entry.getKey();

			if ( !buddy.isOnline( false )){

				continue;
			}

			InetSocketAddress ip 			= buddy.getAdjustedIP();

			if ( ip == null ){

				continue;
			}

			String host = AddressUtils.getHostAddress( ip );
			
			String net	 = AENetworkClassifier.categoriseAddress( ip );
			
			int			tcp_port	= buddy.getTCPPort();
			int			udp_port	= buddy.getUDPPort();

			List<Download>	downloads = (List<Download>)entry.getValue();

			for (int i=0;i<downloads.size();i++){

				Download	download = downloads.get(i);

				com.biglybt.core.download.DownloadManager core_dm = PluginCoreUtils.unwrap( download );
				
				if ( !core_dm.getDownloadState().isNetworkEnabled( net )){
					
					continue;
				}
				
				PeerManager pm = download.getPeerManager();

				if ( pm == null ){

					continue;
				}

				Peer[] existing_peers = pm.getPeers( host );

				boolean	connected = false;

				for (int j=0;j<existing_peers.length;j++){

					Peer peer = existing_peers[j];

					if ( 	peer.getTCPListenPort() == tcp_port ||
							peer.getUDPListenPort() == udp_port ){

						if ( lan && !peer.isLANLocal()){

							AddressUtils.addExplicitLANRateLimitAddress( ip );

							peer.resetLANLocalStatus();
						}

						connected = true;

						break;
					}
				}

				if ( connected ){

					log( download.getName() + " - peer " +  host + " already connected" );

					continue;
				}

				log( download.getName() + " - connecting to peer " +  host );

				PEPeerManager c_pm = PluginCoreUtils.unwrap( pm );

				Map	user_data = new LightHashMap();

				user_data.put( PEER_DOWNLOAD_KEY, new Object[]{ download, false });

				user_data.put( Peer.PR_PRIORITY_CONNECTION, Boolean.TRUE);

				try{
					c_pm.addPeer( host, tcp_port, udp_port, true, user_data );
					
				}catch( Throwable e ){
				}
			}
		}
	}

	protected void
	checkTracking()
	{
		if ( !( plugin_enabled && tracker_enabled )){

			return;
		}

		List<BuddyPluginBuddy>	online;

		synchronized( online_buddies ){

			online = new ArrayList<>(online_buddies);
		}

		Set<Download>			downloads;
		int						downloads_id;

		synchronized( tracked_downloads ){

			boolean downloads_changed = last_processed_download_set_id != download_set_id;

			if ( downloads_changed ){

				last_processed_download_set 	= new HashSet<>(tracked_downloads);
				last_processed_download_set_id	= download_set_id;
			}

			downloads 		= last_processed_download_set;
			downloads_id	= last_processed_download_set_id;
		}

		Map	diff_map = new HashMap();

		Set<Download>	downloads_with_remote_incomplete = new HashSet<>();
		
		for (int i=0;i<online.size();i++){

			BuddyPluginBuddy	buddy = (BuddyPluginBuddy)online.get(i);

			BuddyTrackingData buddy_data = getBuddyData( buddy );

			buddy_data.updateLocal( downloads, downloads_id, diff_map, downloads_with_remote_incomplete );
		}
		
		Set<Download>	temp = new HashSet<>( downloads );
		
		if ( plugin.getFPEnabled()){
			
			for ( Download d: downloads_with_remote_incomplete ){
				
				temp.remove( d );
				
				PluginCoreUtils.unwrap( d ).getDownloadState().setTransientFlag( DownloadManagerState.TRANSIENT_FLAG_FRIEND_FP, true );
			}
		}
		
		for ( Download d: temp ){
						
			PluginCoreUtils.unwrap( d ).getDownloadState().setTransientFlag( DownloadManagerState.TRANSIENT_FLAG_FRIEND_FP, false );
		}
	}

	@Override
	public void
	initialised(
		boolean		available )
	{
	}

	@Override
	public void
	buddyAdded(
		BuddyPluginBuddy	buddy )
	{
		buddyChanged( buddy );
	}

	@Override
	public void
	buddyRemoved(
		BuddyPluginBuddy	buddy )
	{
		buddyChanged( buddy );
	}

	@Override
	public void
	buddyChanged(
		BuddyPluginBuddy	buddy )
	{
		if ( buddy.isTransient()){
			
			return;
		}
		
		if ( buddy.isOnline( false )){

			addBuddy( buddy );

		}else{

			removeBuddy( buddy );
		}
	}

	protected BuddyTrackingData
	getBuddyData(
		BuddyPluginBuddy		buddy )
	{
		synchronized( online_buddies ){

			BuddyTrackingData buddy_data = (BuddyTrackingData)buddy.getUserData( BuddyPluginTracker.class );

			if ( buddy_data == null ){

				buddy_data = new BuddyTrackingData( buddy );

				buddy.setUserData( BuddyPluginTracker.class, buddy_data );
			}

			return( buddy_data );
		}
	}

	protected BuddyTrackingData
	addBuddy(
		BuddyPluginBuddy		buddy )
	{
		synchronized( online_buddies ){

			if ( !online_buddies.contains( buddy )){

				online_buddies.add( buddy );
			}

			BuddyTrackingData bd = getBuddyData( buddy );

			bd.updateIPs();

			return( bd );
		}
	}

	protected void
	removeBuddy(
		BuddyPluginBuddy		buddy )
	{
		synchronized( online_buddies ){

			if ( online_buddies.contains( buddy )){

				BuddyTrackingData bd = getBuddyData( buddy );

				online_buddies.remove( buddy );

				bd.destroy();
			}
		}
	}

	protected int
	isBuddy(
		Peer		peer )
	{
		PartialBuddy pb = new PartialBuddy( this, peer );

		String	peer_ip = peer.getIp();

		List<String> ips = AddressUtils.getLANAddresses( peer_ip );

		synchronized( online_buddies ){

			if ( partial_buddies.containsKey( pb )){
				
				return( BUDDY_YES );
			}
			
			int	result = BUDDY_NO;

outer:
			for (int i=0;i<ips.size();i++){

				String ip = ips.get(i);
				
				List<BuddyPluginBuddy> buddies = online_buddy_ips.get( ip  );

				if ( buddies != null ){

					if ( peer.getTCPListenPort() == 0 && peer.getUDPListenPort() == 0 ){

						result = BUDDY_MAYBE;

					}else{

						for (int j=0;j<buddies.size();j++){

							BuddyPluginBuddy	buddy = (BuddyPluginBuddy)buddies.get(j);

							if (	buddy.getTCPPort() == peer.getTCPListenPort() &&
									buddy.getTCPPort() != 0 ){

								result =  BUDDY_YES;

								break outer;
							}

							if (	buddy.getUDPPort() == peer.getUDPListenPort() &&
									buddy.getUDPPort() != 0 ){

								result =  BUDDY_YES;

								break outer;
							}
						}
					}
				}
			}

			return( result );
		}
	}

	public List<PartialBuddy>
	getPartialBuddies()
	{
		synchronized( online_buddies ){
			
			List<PartialBuddy>	result = new ArrayList<>( partial_buddies.size());
			
			for ( PartialBuddyData pbd: partial_buddies.values()){
				
				result.add( pbd.pb );
			}
			
			return( result );
		}
	}
	
	public void
	addPartialBuddy(
		Download	download,
		Peer		peer,
		boolean		manual )
	{
		if ( peer.getState() == Peer.DISCONNECTED && !manual ){
			
			return;
		}
		
		PartialBuddy pb = new PartialBuddy( this, peer );
		
		String key = pb.getKey();
		
		boolean	is_new = false;
		
		synchronized( online_buddies ){
			
			PartialBuddyData pbd = partial_buddies.get( key );
			
			if ( pbd == null ){
				
				pbd = new PartialBuddyData( pb );
				
				partial_buddies.put( key, pbd );
				
				peer.setUserData( PB_PEER_KEY,  key );
				
				is_new = true;
				
			}else{
				
				pb = pbd.pb;
			}
			
			List<Download>	dls = pbd.downloads;
			
			if ( dls.contains( download )){
				
				return;
			}
			
			dls.add( download );
		}
				
		if ( is_new ){
			
			try{
				if ( plugin.getPeersAreLANLocal()){
										
					InetSocketAddress isa = AddressUtils.getSocketAddress( pb.getIP());
					
					AddressUtils.addExplicitLANRateLimitAddress( isa );
					
					if ( !peer.isLANLocal()){
					
						peer.resetLANLocalStatus();
					}
					
					Peer[] peers = download.getPeerManager().getPeers( pb.getIP());
				
					for ( Peer p: peers ){
						
						if ( p != peer && !p.isLANLocal()){
							
							p.resetLANLocalStatus();
						}
					}
				}
			}catch( Throwable e ){
				
				Debug.out( e );
			}
			
			markBuddyPeer( download, peer, true );
			
			plugin.logMessage( null, "Partial buddy added: " + download.getName() + " - " + pb );

			plugin.partialBuddyAdded( pb );
			
		}else{
			
			plugin.partialBuddyChanged( pb );
		}
	}
	
	public boolean
	isFullBuddy(
		Peer		peer )
	{
		Object[] details = (Object[])peer.getUserData( PEER_DOWNLOAD_KEY );

		return( details != null && !(Boolean)details[1]);
	}
	
	
	public boolean
	isPartialBuddy(
		Download	download,
		Peer		peer )
	{
		String key = PartialBuddy.getPartialBuddyKey( peer );
		
		synchronized( online_buddies ){
			
			PartialBuddyData pbd = partial_buddies.get( key );
						
			return( pbd != null && pbd.downloads.contains( download ));
		}		
	}
	
	public String
	getDownloadsSummary(
		PartialBuddy pb )
	{
		PartialBuddyData pbd;
		
		synchronized( online_buddies ){
			
			pbd = partial_buddies.get( pb.getKey());
			
			if ( pbd == null ){
				
				return( "" );
			}
		}
		
		String str = "";
		
		for ( Download dl: pbd.downloads ){
			
			str += (str.isEmpty()?"":", ") + dl.getName();
		}
		
		return( str );
	}
	
	public void
	removePartialBuddy(
		PartialBuddy	pb )
	{
		String key = pb.getKey();
	
		PartialBuddyData pbd;
		
		synchronized( online_buddies ){
			
			pbd = partial_buddies.remove( key );
			
			if ( pbd == null ){
				
				return;
			}
		}
		
		boolean do_lan = plugin.getPeersAreLANLocal();
		
		try{
			if ( do_lan ){
				
				InetSocketAddress isa = AddressUtils.getSocketAddress( pb.getIP());

				AddressUtils.removeExplicitLANRateLimitAddress( isa );
			}
		}catch( Throwable e ){
			
			Debug.out( e );;
		}
		
		List<Peer>	peers = new ArrayList<>();
		
		for ( Download download: pbd.downloads ){

			PeerManager pm = download.getPeerManager();

			if ( pm != null ){

				Peer[] ps = pm.getPeers();

				for ( Peer p: ps ){

					if ( key.equals( new PartialBuddy( this, p ).getKey())){

						peers.add( p );
					}
				}
			}
		}
		
		for ( Peer peer: peers ){
		
			unmarkBuddyPeer( peer );
			
			if ( do_lan && peer.isLANLocal()){
			
				peer.resetLANLocalStatus();
			}
		}
		
		plugin.logMessage( null, "Partial buddy removed: " + pb );
		
		plugin.partialBuddyRemoved( pb );
	}
	
	public void
	removePartialBuddy(
		Download	download,
		Peer		peer,
		boolean		manual )
	{		
		boolean removed = false;
		
		boolean do_lan = plugin.getPeersAreLANLocal();
		
		PartialBuddy pb;
		
		synchronized( online_buddies ){
			
			String key = (String)peer.getUserData( PB_PEER_KEY );
			
			if ( key == null ){
				
				PartialBuddy temp_pb = new PartialBuddy( this, peer );
				
				key = temp_pb.getKey();
			}
			
			PartialBuddyData pbd = partial_buddies.get( key );
				
			if ( pbd == null ){
				
				return;
			}
		
			pbd.downloads.remove( download );
				
			pb = pbd.pb;
			
				// changed so that single dl removal removes partial buddy
			
			if ( do_lan && manual ){
				
				pbd.downloads.clear();
			}
			
			if ( pbd.downloads.isEmpty()){
			
				partial_buddies.remove( key );
				
				removed = true;
			}
		}
		
		if ( removed ){
			
			try{
				if ( do_lan ){
					
					InetSocketAddress isa = AddressUtils.getSocketAddress( pb.getIP());

					AddressUtils.removeExplicitLANRateLimitAddress( isa );
					
					peer.resetLANLocalStatus();
				}
			}catch( Throwable e ){
				
				Debug.out( e );
			}
			
			unmarkBuddyPeer( peer );
	
			plugin.logMessage( null, "Partial buddy removed: " + download.getName() + " - " + pb );
			
			plugin.partialBuddyRemoved( pb );
			
		}else{
			
			plugin.partialBuddyChanged( pb );
		}
	}
	
	@Override
	public void
	messageLogged(
		String		str,
		boolean		error )
	{
	}

	@Override
	public void
	enabledStateChanged(
		boolean 	classic_enabled,
		boolean		beta_enabled )
	{
		plugin_enabled = classic_enabled;

		checkEnabledState();
	}

	@Override
	public void
	updated()
	{
	}

	public boolean
	isEnabled()
	{
		synchronized( this ){

			return( plugin_enabled && tracker_enabled );
		}
	}

	protected void
	checkEnabledState()
	{
		boolean	seeding_change 	= false;
		boolean	enabled_change	= false;

		synchronized( this ){

			boolean	old_enabled = old_plugin_enabled && old_tracker_enabled;

			if ( plugin_enabled != old_plugin_enabled ){

				log( "Plugin enabled state changed to " + plugin_enabled );

				old_plugin_enabled = plugin_enabled;
			}

			if ( tracker_enabled != old_tracker_enabled ){

				log( "Tracker enabled state changed to " + tracker_enabled );

				old_tracker_enabled = tracker_enabled;
			}

			if ( seeding_only != old_seeding_only ){

				log( "Seeding-only state changed to " + seeding_only );

				old_seeding_only = seeding_only;

				seeding_change = true;
			}

			enabled_change = old_enabled != ( plugin_enabled && tracker_enabled );
		}

		if ( seeding_change ){

			updateSeedingMode();
		}

		if ( enabled_change ){

			fireEnabledChanged( isEnabled());
		}
	}

	protected void
	updateSeedingMode()
	{
		updateNetworkStatus();

		List<BuddyPluginBuddy>	online;

		synchronized( online_buddies ){

			online = new ArrayList<>(online_buddies);
		}

		for (int i=0;i<online.size();i++){

			BuddyTrackingData buddy_data = getBuddyData(online.get(i));

			if ( buddy_data.hasDownloadsInCommon()){

				buddy_data.updateStatus();
			}
		}
	}

	@Override
	public void
	downloadAdded(
		final Download	download )
	{
		Torrent t = download.getTorrent();

		if ( t == null ){

			return;
		}

		if ( t.isPrivate()){

			download.addTrackerListener(
				new DownloadTrackerListener()
				{
					@Override
					public void
					scrapeResult(
						DownloadScrapeResult result )
					{
					}

					@Override
					public void
					announceResult(
						DownloadAnnounceResult result)
					{
						if ( okToTrack( download )){

							trackDownload( download );

						}else{

							untrackDownload( download );
						}
					}
				},
				false );
		}

		if ( okToTrack( download )){

			trackDownload( download );
		}

		download.addListener(
			new DownloadListener()
			{
				@Override
				public void
				stateChanged(
					Download		download,
					int				old_state,
					int				new_state )
				{
					if ( okToTrack( download )){

						trackDownload( download );

					}else{

						untrackDownload( download );
					}
				}

				@Override
				public void
				positionChanged(
					Download	download,
					int 		oldPosition,
					int 		newPosition )
				{
				}
			});
	}

	@Override
	public void
	downloadRemoved(
		Download	download )
	{
		untrackDownload( download );
	}

	protected void
	trackDownload(
		Download	download )
	{
		synchronized( tracked_downloads ){

			if ( tracked_downloads.contains( download )){

				return;
			}

			downloadData download_data = new downloadData( download );

			download.setUserData( BuddyPluginTracker.class, download_data );

			HashWrapper	full_id		= download_data.getID();

			HashWrapper short_id 	= new HashWrapper( full_id.getHash(), 0, 4 );

			full_id_map.put( full_id, download );

			List<Download>	dls = short_id_map.get( short_id );

			if ( dls == null ){

				dls = new ArrayList<>();

				short_id_map.put( short_id, dls );
			}

			dls.add( download );

			tracked_downloads.add( download );

			download_set_id++;
		}
	}

	protected void
	untrackDownload(
		Download	download )
	{
		synchronized( tracked_downloads ){

			if ( tracked_downloads.remove( download )){

				PluginCoreUtils.unwrap( download ).getDownloadState().setTransientFlag( DownloadManagerState.TRANSIENT_FLAG_FRIEND_FP, false );

				download_set_id++;

				downloadData download_data = (downloadData)download.getUserData( BuddyPluginTracker.class );

				download.setUserData( BuddyPluginTracker.class, null );

				HashWrapper	full_id		= download_data.getID();

				full_id_map.remove( full_id );

				HashWrapper short_id 	= new HashWrapper( full_id.getHash(), 0, SHORT_ID_SIZE );

				List	dls = (List)short_id_map.get( short_id );

				if ( dls != null ){

					dls.remove( download );

					if ( dls.size() == 0 ){

						short_id_map.remove( short_id );
					}
				}
			}
		}

		synchronized( online_buddies ){

			Iterator<BuddyPluginBuddy> it = online_buddies.iterator();

			while( it.hasNext()){

				BuddyPluginBuddy	buddy = it.next();

				BuddyTrackingData buddy_data = getBuddyData( buddy );

				buddy_data.removeDownload( download );
			}
		}

		synchronized( actively_tracking ){

			actively_tracking.remove( download );
		}
	}

	protected void
	trackPeers(
		final Download		download )
	{
		PeerManager pm = download.getPeerManager();

			// not running

		if ( pm == null ){

			synchronized( actively_tracking ){

				actively_tracking.remove( download );
			}
		}else{

			log( "Tracking peers for " + download.getName());

			download.addPeerListener( this );
		}
	}

	@Override
	public void
	peerManagerAdded(
		Download		download,
		PeerManager		peer_manager )
	{
		trackPeers( download, peer_manager );
	}

	@Override
	public void
	peerManagerRemoved(
		Download		download,
		PeerManager		peer_manager )
	{
		synchronized( actively_tracking ){

			actively_tracking.remove( download );
		}

		download.removePeerListener( this );
	}

	protected void
	trackPeers(
		final Download			download,
		final PeerManager		pm )
	{
		pm.addListener(
			new PeerManagerListener2()
			{
				@Override
				public void
				eventOccurred(
					PeerManagerEvent event )
				{
					if ( event.getType() == PeerManagerEvent.ET_PEER_ADDED ){

						synchronized( actively_tracking ){

							if ( !actively_tracking.contains( download )){

								pm.removeListener( this );

								return;
							}
						}

						trackPeer( download, event.getPeer());
					}
				}
			});

		Peer[] peers = pm.getPeers();

		for (int i=0;i<peers.length;i++){

			trackPeer( download, peers[i] );
		}
	}

	protected void
	trackPeer(
		final Download	download,
		final Peer		peer )
	{
		int type = isBuddy( peer );

		if ( type == BUDDY_YES ){

			markBuddyPeer( download, peer, false );

		}else if ( type == BUDDY_MAYBE ){

				// mark as peer early so that we get optimistic disconnect if needed

			markBuddyPeer( download, peer, false );

			PeerListener2 listener =
				new PeerListener2()
				{
					@Override
					public void
					eventOccurred(
						PeerEvent event )
					{
						if ( event.getType() == PeerEvent.ET_STATE_CHANGED ){

							if (((Integer)event.getData()).intValue() == Peer.TRANSFERING ){

								peer.removeListener( this );

									// withdraw buddy marker if it turns out our earlier optimism
									// was misplaced

								if ( isBuddy( peer ) != BUDDY_YES ){

									unmarkBuddyPeer( peer );
								}
							}
						}
					}
				};

			peer.addListener( listener );

			if ( peer.getState() == Peer.TRANSFERING ){

				peer.removeListener( listener );

					// withdraw buddy marker if it turns out our earlier optimism
					// was misplaced

				if ( isBuddy( peer ) != BUDDY_YES ){

					unmarkBuddyPeer( peer );
				}
			}
		}
	}

	protected void
	untrackPeers(
		Download		download )
	{
		log( "Not tracking peers for " + download.getName());

		download.removePeerListener( this );

		PeerManager pm = download.getPeerManager();

		if ( pm != null ){

			Peer[] peers = pm.getPeers();

			for (int i=0;i<peers.length;i++){

				Peer	peer = peers[i];

				unmarkBuddyPeer( peer );
			}
		}
	}

	protected void
	markBuddyPeer(
		Download		download,
		Peer			peer,
		boolean			is_partial )
	{
		boolean	state_changed 	= false;

		synchronized( buddy_peers ){

			if ( !buddy_peers.contains( peer )){

				log( "Adding buddy peer " + peer.getIp());

				if ( buddy_peers.size() == 0 ){

					if ( buddy_stats_timer == null ){

						buddy_stats_timer =
							SimpleTimer.addPeriodicEvent(
								"BuddyTracker:stats",
								1000,
								new TimerEventPerformer()
								{
									@Override
									public void
									perform(
										TimerEvent event )
									{
										Iterator it = buddy_peers.iterator();

										long	total_sent		= 0;
										long	total_received	= 0;

										while( it.hasNext()){

											Peer	p = (Peer)it.next();

											PeerStats ps = p.getStats();

											long sent		= ps.getTotalSent();
											long received 	= ps.getTotalReceived();

											long[]	last = (long[])p.getUserData( PEER_STATS_KEY );

											if ( last != null ){

												total_sent 		+= sent - last[0];
												total_received	+= received - last[1];
											}

											p.setUserData( PEER_STATS_KEY, new long[]{ sent, received });
										}

										buddy_receive_speed.addValue( total_received );
										buddy_send_speed.addValue( total_sent );
									}
								});
					}

					state_changed 	= true;
				}

				buddy_peers.add( peer );

				peer.setUserData( PEER_DOWNLOAD_KEY, new Object[]{ download, is_partial });

				peer.setPriorityConnection( true );

				try{
					PluginCoreUtils.unwrap( peer ).updateAutoUploadPriority( PEER_UPLOAD_PRIORITY_KEY, true );
					
				}catch( Throwable e ){
					
				}
				
				log( download.getName() + ": adding buddy peer " + peer.getIp());

				peer.addListener(
					new PeerListener2()
					{
						@Override
						public void
						eventOccurred(
							PeerEvent event )
						{
							if ( event.getType() == PeerEvent.ET_STATE_CHANGED ){

								int	state = ((Integer)event.getData()).intValue();

								if ( state == Peer.CLOSING || state == Peer.DISCONNECTED ){

									peer.removeListener( this );

									unmarkBuddyPeer( peer );
								}
							}
						}
					});
			}
		}

		if ( peer.getState() == Peer.CLOSING || peer.getState() == Peer.DISCONNECTED ){

			unmarkBuddyPeer( peer );
		}

		if ( state_changed ){

			updateNetworkStatus();
		}
	}

	protected void
	unmarkBuddyPeer(
		Peer		peer )
	{
		boolean	state_changed = false;

		synchronized( buddy_peers ){

			Object[] details = (Object[])peer.getUserData( PEER_DOWNLOAD_KEY );

			if ( details == null ){

				return;
			}

			if ( buddy_peers.remove( peer )){

				if ( buddy_peers.size() == 0 ){

					state_changed = true;

					if ( buddy_stats_timer != null ){

						buddy_stats_timer.cancel();

						buddy_stats_timer = null;
					}
				}

				log( ((Download)details[0]).getName() + ": removing buddy peer " + peer.getIp());
			}

			peer.setUserData( PEER_DOWNLOAD_KEY, null );

			peer.setPriorityConnection( false );
			
			try{
				PluginCoreUtils.unwrap( peer ).updateAutoUploadPriority(  PEER_UPLOAD_PRIORITY_KEY, false );
				
			}catch( Throwable e ){
				
			}
		}

		if ( state_changed ){

			updateNetworkStatus();
		}
	}
	
	private void
	checkPartialPeers()
	{
		boolean lan = plugin.getPeersAreLANLocal();
		
		if ( lan ){
			
			Set<String>	pb_keys;
			
			synchronized( online_buddies ){
				
				if ( partial_buddies.isEmpty()){
					
					return;
				}
				
				pb_keys = new HashSet<>( partial_buddies.keySet());
			}
			
			// We have to monitor all downloads and automatically make them partial-buddy downloads as lan limits
			// are applied to all peers regardless of whether or not the user has explicitly marked them as partial buddies
			// Breaks the idea of manually marking them obviously but better than having peers being LAN boosted
			// but not partial buddies...
			

			for ( Download d: plugin.getPluginInterface().getDownloadManager().getDownloads()){
				
				int state = d.getState();
				
				if ( state == Download.ST_DOWNLOADING || state == Download.ST_SEEDING ){
					
					PeerManager pm = d.getPeerManager();
					
					if ( pm != null ){
						
						Peer[] peers = pm.getPeers();
						
						for ( Peer peer: peers ){
							
							String key = PartialBuddy.getPartialBuddyKey( peer );
							
							if ( !pb_keys.contains( key )){
								
								continue;
							}
							
							boolean add_it = false;
							
							synchronized( online_buddies ){
								
								PartialBuddyData pbd = partial_buddies.get( key );
								
								if ( pbd != null ){
								
									if ( !pbd.downloads.contains( d )){
										
										add_it = true;
									}
								}
							}
							
							if ( add_it ){
								
								addPartialBuddy( d, peer, false );
							}
						}
					}
				}
			}
		}
	}

	private void
	checkPeers()
	{
		List	to_unmark = new ArrayList();

		synchronized( buddy_peers ){

			Iterator	it = buddy_peers.iterator();

			while( it.hasNext()){

				Peer	peer = (Peer)it.next();

				if ( peer.getState() == Peer.CLOSING || peer.getState() == Peer.DISCONNECTED ){

					to_unmark.add( peer );
				}
			}
		}

		for (int i=0;i<to_unmark.size();i++){

			unmarkBuddyPeer((Peer)to_unmark.get(i));
		}
	}

	protected void
	recheckPeers()
	{
			// go over peers for active torrents to see if we've missed and. can really only
			// happen with multi-homed LAN setups where a new (and utilised) route is found
			// after we start tracking

		synchronized( actively_tracking ){

			Iterator it = actively_tracking.iterator();

			while( it.hasNext()){

				Download download = (Download)it.next();

				PeerManager pm = download.getPeerManager();

				if ( pm != null ){

					Peer[] peers = pm.getPeers();

					for (int i=0;i<peers.length;i++){

						trackPeer( download, peers[i] );
					}
				}
			}
		}
	}

	protected void
	updateNetworkStatus()
	{
		int		new_status;
		boolean	changed = false;

		synchronized( buddy_peers ){

			if ( buddy_peers.size() == 0 ){

				new_status 	= BUDDY_NETWORK_IDLE;

			}else{

				if ( tracker_so_enabled ){

					new_status	= seeding_only?BUDDY_NETWORK_OUTBOUND:BUDDY_NETWORK_INBOUND;

				}else{

					boolean	all_outgoing 	= true;
					boolean	all_incoming	= true;

					for ( Peer peer: buddy_peers ){

						boolean we_are_seed 	= peer.getManager().isSeeding();
						boolean they_are_seed	= peer.isSeed();

						if ( !we_are_seed ){

							all_outgoing = false;

							if ( !all_incoming ){

								break;
							}
						}

						if ( !they_are_seed ){

							all_incoming = false;

							if ( !all_outgoing ){

								break;
							}
						}
					}

					if ( all_incoming ){

						new_status = BUDDY_NETWORK_INBOUND;

					}else if ( all_outgoing ){

						new_status = BUDDY_NETWORK_OUTBOUND;

					}else{

						new_status = BUDDY_NETWORK_INOUTBOUND;
					}
				}
			}

			if ( new_status != network_status ){

				network_status	= new_status;

				changed	= true;
			}
		}

		if ( changed ){

			fireStateChange( new_status );
		}
	}

	public void
	addListener(
		BuddyPluginTrackerListener		l )
	{
		listeners.add( l );
	}

	public void
	removeListener(
		BuddyPluginTrackerListener		l )
	{
		listeners.remove( l );
	}

	protected void
	fireStateChange(
		int		state )
	{
		Iterator	it = listeners.iterator();

		while( it.hasNext()){

			try{
				((BuddyPluginTrackerListener)it.next()).networkStatusChanged( this, state );

			}catch( Throwable e ){

				Debug.out( e );
			}
		}
	}

	protected void
	fireEnabledChanged(
		boolean	enabled )
	{
		Iterator	it = listeners.iterator();

		while( it.hasNext()){

			try{
				((BuddyPluginTrackerListener)it.next()).enabledStateChanged( this, enabled );

			}catch( Throwable e ){

				Debug.out( e );
			}
		}
	}

	protected void
	sendMessage(
		BuddyPluginBuddy		buddy,
		int						type,
		Map<String,Object>		body )
	{
		Map<String,Object>	msg = new HashMap<>();

		msg.put( "type", new Long( type ));
		msg.put( "msg", body );

		buddy.getPluginNetwork().getAZ2Handler().sendAZ2TrackerMessage(
				buddy,
				msg,
				BuddyPluginTracker.this );
	}

	@Override
	public Map<String,Object>
	messageReceived(
		BuddyPluginBuddy		buddy,
		Map<String,Object>		message )
	{
		BuddyTrackingData buddy_data = buddyAlive( buddy );

		int type = ((Long)message.get( "type" )).intValue();

		Map msg = (Map)message.get( "msg" );

		return( buddy_data.receiveTrackerMessage( type, msg ));
	}

	@Override
	public void
	messageFailed(
		BuddyPluginBuddy	buddy,
		Throwable			cause )
	{
		log( "Failed to send message to " + buddy.getName(), cause );

		buddyDead( buddy );
	}

	protected BuddyTrackingData
	buddyAlive(
		BuddyPluginBuddy		buddy )
	{
		BuddyTrackingData buddy_data = addBuddy( buddy );

		buddy_data.setAlive( true );

		return( buddy_data );
	}

	protected void
	buddyDead(
		BuddyPluginBuddy		buddy )
	{
		BuddyTrackingData buddy_data = getBuddyData( buddy );

		if ( buddy_data != null ){

			buddy_data.setAlive( false );
		}
	}

	public BuddyTrackingData
	getTrackingData(
		BuddyPluginBuddy	buddy )
	{
		synchronized( online_buddies ){

			return( (BuddyTrackingData)buddy.getUserData( BuddyPluginTracker.class ));
		}
	}

	public String
	getTrackingStatus(
		BuddyPluginBuddy	buddy )
	{
		BuddyTrackingData data  = getTrackingData( buddy );

		if ( data == null ){

			return( "" );
		}

		return( data.getStatus());
	}

	protected boolean
	okToTrack(
		Download	d )
	{
		Torrent	t = d.getTorrent();

		if ( t == null ){

			return( false );
		}

		String[]	networks = d.getListAttribute( ta_networks );

		boolean	ok = false;

		for ( String net: networks ){

			if ( net == AENetworkClassifier.AT_PUBLIC ){

				ok = true;
			}
		}

		if ( !ok ){

			return( false );
		}

			// only track private torrents if we have successfully received peers from tracker
			// which means we have the torrent legitimately. As this rule is enforced by both
			// ends of the tracking operation it means we will only track between peers that
			// both have a legitimate copy of the torrent.

		if ( t.isPrivate()){

			DownloadAnnounceResult announce = d.getLastAnnounceResult();

			if ( 	announce == null ||
					announce.getResponseType() != DownloadAnnounceResult.RT_SUCCESS ||
					announce.getPeers() == null ||
					announce.getPeers().length < 2 ){

				return( false );
			}
		}

		int state = d.getState();

		return( 	state != Download.ST_ERROR &&
					state != Download.ST_STOPPING &&
					state != Download.ST_STOPPED );
	}

	protected void
	log(
		String		str )
	{
		plugin.log( null, "Tracker: " + str );
	}

	protected void
	log(
		String		str,
		boolean		verbose )
	{
		if ( verbose ){

			if ( Constants.isCVSVersion()){

				log( str );
			}
		}else{

			log( str );
		}
	}

	protected void
	log(
		String		str,
		Throwable 	e )
	{
		plugin.log( null, "Tracker: " + str, e );
	}

	public class
	BuddyTrackingData
	{
		private BuddyPluginBuddy		buddy;

		private Set<Download>	downloads_sent;
		private int				downloads_sent_id;

		private int 			tracking_remote;

		private Map<Download,buddyDownloadData>		downloads_in_common;
		private boolean	buddy_seeding_only;

		private int		consecutive_fails;
		private long	last_fail;

		private String[]	current_ips = {};

		protected
		BuddyTrackingData(
			BuddyPluginBuddy		_buddy )
		{
			buddy	= _buddy;
		}

		protected void
		updateIPs()
		{
			String[] latest_ips = getLatestIPs();
			
			if ( !Arrays.equals( current_ips, latest_ips )){

				for ( String ip: current_ips ){
						
					List<BuddyPluginBuddy>	l = online_buddy_ips.get( ip );

					if ( l != null ){

						l.remove( buddy );

						if ( l.size() == 0 ){

							online_buddy_ips.remove( ip );
						}
					}
				}

				current_ips = latest_ips;
				
				String str = "";
				
				for ( String ip: current_ips ){
						
					str += (str.isEmpty()?"":", ") + ip;
					
					List<BuddyPluginBuddy> l = online_buddy_ips.get( ip );

					if ( l == null ){

						l = new ArrayList<>();

						online_buddy_ips.put( ip, l );
					}

					l.add( buddy );
				}
				
				log( "IPs set to {" + str + "}" );
			}
		}
		
		protected void
		destroy()
		{
			for ( String ip: current_ips ){
					
				List<BuddyPluginBuddy>	l = online_buddy_ips.get( ip );

				if ( l != null ){

					l.remove( buddy );

					if ( l.size() == 0 ){

						online_buddy_ips.remove( ip );
					}
				}
			}
		}
		
		private String[]
		getLatestIPs()
		{
			InetSocketAddress	latest_ip = buddy.getAdjustedIP();
			InetSocketAddress	latest_v6 = buddy.getLatestIP( false );

			String ip1 	= null;
			String ip2	= null;
			
			if ( latest_ip != null ){

				ip1	= AddressUtils.getHostAddress( latest_ip );
				
				if ( latest_v6 != null ){
					
					ip2 = AddressUtils.getHostAddress( latest_v6 );
					
					if ( ip2.equals( ip1 )){
						
						ip2 = null;
					}
				}
			}
			
			if ( ip1 == null ){
				
				return( new String[0] );
								
			}else if ( ip2 == null ){
				
				return( new String[]{ ip1 });
								
			}else{
				
				return( new String[]{ ip1, ip2 });
			}
		}

		protected String[]
		getIPs()
		{
			return( current_ips );
		}

		protected boolean
		hasDownloadsInCommon()
		{
			synchronized( this ){

				return( downloads_in_common != null );
			}
		}

		protected void
		setAlive(
			boolean		alive )
		{
			synchronized( this ){

				if ( alive ){

					consecutive_fails		= 0;
					last_fail				= 0;

				}else{

					consecutive_fails++;

					last_fail	= SystemTime.getMonotonousTime();
				}
			}
		}

		protected void
		updateLocal(
			Set<Download>		downloads,
			int					id,
			Map					diff_map,
			Set<Download>		downloads_with_remote_incomplete )
		{
			if ( consecutive_fails > 0 ){

				long	retry_millis = RETRY_SEND_MIN;

				for (int i=0;i<consecutive_fails-1;i++){

					retry_millis <<= 2;

					if ( retry_millis > RETRY_SEND_MAX ){

						retry_millis = RETRY_SEND_MAX;

						break;
					}
				}

				long	now = SystemTime.getMonotonousTime();

				if ( now - last_fail >= retry_millis ){

					last_fail			= now;	// assume we're going to fail so we avoid
												// falling through here multiple times before
												// actuallt failing again

					downloads_sent 		= null;
					downloads_sent_id	= 0;
				}
			}

				// first check to see if completion state changed for any common downloads

			List<Download>	comp_changed = new ArrayList<>();

			synchronized( this ){

				if ( downloads_in_common != null ){

					Iterator<Map.Entry<Download,buddyDownloadData>>  it = downloads_in_common.entrySet().iterator();

					while( it.hasNext()){

						Map.Entry<Download,buddyDownloadData>	entry = it.next();

						Download d = entry.getKey();

						buddyDownloadData	bdd = entry.getValue();

						if ( !bdd.isRemoteComplete()){
							
							downloads_with_remote_incomplete.add( d );
						}
						
						boolean	local_complete = d.isComplete( false );

						if ( local_complete != bdd.isLocalComplete()){

							bdd.setLocalComplete( local_complete );

							comp_changed.add( d );
						}
					}
				}
			}

			if ( comp_changed.size() > 0 ){

				byte[][] change_details = exportFullIDs( comp_changed );

				if ( change_details[0].length > 0 ){

					Map<String,Object>	msg = new HashMap<>();

					msg.put( "seeding", new Long( seeding_only?1:0 ));

					msg.put( "changed", 		change_details[0] );
					msg.put( "changed_s", 	change_details[1] );

					sendTrackerMessage( REQUEST_TRACKER_CHANGE, msg );
				}
			}

			if ( id == downloads_sent_id ){

				return;
			}

			Long	key = new Long(((long)id) << 32 | (long)downloads_sent_id);

			Object[]	diffs = (Object[])diff_map.get( key );

			boolean	incremental = downloads_sent != null;

			byte[]	added_bytes;
			byte[]	removed_bytes;

			if ( diffs == null ){

				List	added;
				List	removed	= new ArrayList();


				if ( downloads_sent == null ){

					added 	= new ArrayList( downloads );

				}else{

					added	= new ArrayList();

					Iterator<Download>	it1 = downloads.iterator();

					while( it1.hasNext()){

						Download download = (Download)it1.next();

						if ( okToTrack( download )){

							if ( !downloads_sent.contains( download )){

								added.add( download );
							}
						}
					}

					Iterator	it2 = downloads_sent.iterator();

					while( it2.hasNext()){

						Download download = (Download)it2.next();

						if ( !downloads.contains( download )){

							removed.add( download );
						}
					}
				}

				added_bytes 	= exportShortIDs( added );
				removed_bytes	= exportFullIDs( removed )[0];

				diff_map.put( key, new Object[]{ added_bytes, removed_bytes });
			}else{

				added_bytes 	= (byte[])diffs[0];
				removed_bytes 	= (byte[])diffs[1];
			}

			downloads_sent 		= downloads;
			downloads_sent_id	= id;

			if ( added_bytes.length == 0 && removed_bytes.length == 0 ){

				return;
			}

			Map	msg = new HashMap();

			if ( added_bytes.length > 0 ){

				msg.put( "added", 	added_bytes );
			}

			if ( removed_bytes.length > 0 ){

				msg.put( "removed", removed_bytes );
			}

			msg.put( "inc", 	new Long( incremental?1:0 ));
			msg.put( "seeding", new Long( seeding_only?1:0 ));

			sendTrackerMessage( REQUEST_TRACKER_SUMMARY, msg );
		}

		protected Map
		updateRemote(
			Map		msg )
		{
			byte[] added_bytes 		= (byte[])msg.get( "added" );

			List	added 	= importShortIDs( added_bytes );

			Map	reply = new HashMap();

			byte[][] add_details = exportFullIDs( added );

			if( add_details[0].length > 0 ){

				reply.put( "added", 	add_details[0] );
				reply.put( "added_s", 	add_details[1] );
			}

			synchronized( this ){

				if ( downloads_in_common != null ){

					byte[] removed_bytes	= (byte[])msg.get( "removed" );

					Map removed = importFullIDs( removed_bytes, null );

					Iterator it = removed.keySet().iterator();

					while( it.hasNext()){

						Download d = (Download)it.next();

						if ( downloads_in_common.remove( d ) != null ){

							log( "Removed " + d.getName() + " common download", false, true );
						}
					}

					if ( downloads_in_common.size() == 0 ){

						downloads_in_common = null;
					}
				}
			}

			return( reply );
		}

		protected void
		updateCommonDownloads(
			Map			downloads,
			boolean		incremental )
		{
			synchronized( this ){

				if ( downloads_in_common == null ){

					downloads_in_common = new HashMap();

				}else{

						// if not incremental then remove any downloads that no longer
						// are in common

					if ( !incremental ){

						Iterator it = downloads_in_common.keySet().iterator();

						while( it.hasNext()){

							Download download = (Download)it.next();

							if ( !downloads.containsKey( download )){

								log( "Removing " + download.getName() + " from common downloads", false, true );

								it.remove();
							}
						}
					}
				}

				Iterator it = downloads.entrySet().iterator();

				while( it.hasNext()){

					Map.Entry	entry = (Map.Entry)it.next();

					Download d = (Download)entry.getKey();

					buddyDownloadData	bdd = (buddyDownloadData)entry.getValue();

					buddyDownloadData existing = (buddyDownloadData)downloads_in_common.get( d );

					if ( existing == null ){

						log( "Adding " + d.getName() + " to common downloads (bdd=" + bdd.getString() + ")", false, true );

						downloads_in_common.put( d, bdd );

					}else{

						boolean	old_rc = existing.isRemoteComplete();
						boolean	new_rc = bdd.isRemoteComplete();

						if ( old_rc != new_rc ){

							existing.setRemoteComplete( new_rc );

							log( "Changing " + d.getName() + " common downloads (bdd=" + existing.getString() + ")", false, true );
						}
					}
				}

				if ( downloads_in_common.size() == 0 ){

					downloads_in_common = null;
				}
			}
		}

		protected void
		updateStatus()
		{
			Map	msg = new HashMap();

			msg.put( "seeding", new Long( seeding_only?1:0 ));

			sendTrackerMessage( REQUEST_TRACKER_STATUS, msg );
		}

		protected void
		sendTrackerMessage(
			int						type,
			Map<String,Object>		body )
		{
			body.put( "track", tracked_downloads.size());

			sendMessage( buddy, type, body );
		}

		protected Map<String,Object>
		receiveTrackerMessage(
			int					type,
			Map<String,Object>	msg_in )
		{
			int	reply_type	= -1;

			Map<String,Object>	msg_out		= null;

			Long	l_track = (Long)msg_in.get( "track" );

			if ( l_track != null ){

				tracking_remote = l_track.intValue();
			}

			Long	l_seeding = (Long)msg_in.get( "seeding" );

			if ( l_seeding != null ){

				boolean old = buddy_seeding_only;

				buddy_seeding_only = l_seeding.intValue() == 1;

				if ( old != buddy_seeding_only ){

					log( "Seeding only changed to " + buddy_seeding_only );
				}
			}

			if ( type == REQUEST_TRACKER_SUMMARY ){

				reply_type	= REPLY_TRACKER_SUMMARY;

				msg_out = updateRemote( msg_in );

				msg_out.put( "inc", msg_in.get( "inc" ));

			}else if ( type == REQUEST_TRACKER_STATUS ){

				reply_type	= REPLY_TRACKER_STATUS;

			}else if ( type == REQUEST_TRACKER_CHANGE ){

				reply_type	= REPLY_TRACKER_STATUS;

					// bug - message was incorrectly being send with "change" and "change_s" 
				
				if ( msg_in.containsKey( "change" )){
					
					Map downloads = importFullIDs((byte[])msg_in.get( "change" ), (byte[])msg_in.get( "change_s" ));

					updateCommonDownloads( downloads, true );

				}else{
					
					Map downloads = importFullIDs((byte[])msg_in.get( "changed" ), (byte[])msg_in.get( "changed_s" ));

					updateCommonDownloads( downloads, true );
				}
			}else if ( type == REQUEST_TRACKER_ADD ){

				reply_type	= REPLY_TRACKER_ADD;

				Map downloads = importFullIDs((byte[])msg_in.get( "added" ), (byte[])msg_in.get( "added_s" ));

				updateCommonDownloads( downloads, true );

			}else if ( type == REPLY_TRACKER_SUMMARY ){

					// full hashes on reply

				byte[]	possible_matches 		= (byte[])msg_in.get( "added" );
				byte[]	possible_match_states 	= (byte[])msg_in.get( "added_s" );

				boolean	incremental = ((Long)msg_in.get( "inc" )).intValue() == 1;

				if ( possible_matches != null && possible_match_states != null ){

					Map downloads = importFullIDs( possible_matches, possible_match_states );

					if ( downloads.size() > 0 ){

						updateCommonDownloads( downloads, incremental );

						byte[][] common_details = exportFullIDs( new ArrayList( downloads.keySet()));

						if( common_details[0].length > 0 ){

							Map<String,Object>	msg = new HashMap<>();

							msg.put( "seeding", new Long( seeding_only?1:0 ));

							msg.put( "added", 	common_details[0] );
							msg.put( "added_s", common_details[1] );

							sendTrackerMessage( REQUEST_TRACKER_ADD, msg );
						}
					}
				}

			}else if ( 	type == REPLY_TRACKER_CHANGE ||
						type == REPLY_TRACKER_STATUS ||
						type == REPLY_TRACKER_ADD ){

					// nothing interesting in reply for these
			}else{

				log( "Unrecognised type " + type );
			}

			if ( reply_type != -1 ){

				Map	reply = new HashMap();

				reply.put( "type", new Long( reply_type ));

				if ( msg_out == null ){

					msg_out = new HashMap();
				}

				msg_out.put( "seeding", new Long( seeding_only?1:0 ));

				reply.put( "msg", msg_out );

				return( reply );
			}

			return( null );
		}

		protected byte[]
		exportShortIDs(
			List<Download>	downloads )
		{
			byte[]	res = new byte[ SHORT_ID_SIZE * downloads.size() ];

			for (int i=0;i<downloads.size();i++ ){

				Download download = downloads.get(i);

				downloadData download_data = (downloadData)download.getUserData( BuddyPluginTracker.class );

				if ( download_data == null ){
   					
						// might have been removed if we have un-tracked the download. temporarily create a new one so
						// we can communicate removal correctly.
					
					download_data = new downloadData( download );
				}
				
				System.arraycopy(
					download_data.getID().getBytes(),
					0,
					res,
					i * SHORT_ID_SIZE,
					SHORT_ID_SIZE );
			}

			return( res );
		}

		protected List<Download>
		importShortIDs(
			byte[]		ids )
		{
			List<Download>	res = new ArrayList<>();

			if ( ids != null ){

				synchronized( tracked_downloads ){

					for (int i=0;i<ids.length;i+= SHORT_ID_SIZE ){

						List<Download> dls = short_id_map.get( new HashWrapper( ids, i, SHORT_ID_SIZE ));

						if ( dls != null ){

							res.addAll( dls );
						}
					}
				}
			}

			return( res );
		}

		protected byte[][]
   		exportFullIDs(
   			List<Download>	downloads )
   		{
   			byte[]	hashes 	= new byte[ FULL_ID_SIZE * downloads.size() ];
   			byte[] 	states	= new byte[ downloads.size()];

   			for (int i=0;i<downloads.size();i++ ){

   				Download download = downloads.get(i);

   				downloadData download_data = (downloadData)download.getUserData( BuddyPluginTracker.class );

   				if ( download_data == null ){
   					
   						// might have been removed if we have un-tracked the download. temporarily create a new one so
   						// we can communicate removal correctly.
   					
   					download_data = new downloadData( download );
   				}

				System.arraycopy(
					download_data.getID().getBytes(),
					0,
					hashes,
					i * FULL_ID_SIZE,
					FULL_ID_SIZE );

				states[i] = download.isComplete( false )?(byte)0x01:(byte)0x00;
   			}

   			return( new byte[][]{ hashes, states });
   		}

		protected Map<Download,buddyDownloadData>
		importFullIDs(
			byte[]		ids,
			byte[]		states )
		{
			Map<Download,buddyDownloadData>	res = new HashMap<>();

			if ( ids != null ){

				synchronized( tracked_downloads ){

					for (int i=0;i<ids.length;i+= FULL_ID_SIZE ){

						Download dl = full_id_map.get( new HashWrapper( ids, i, FULL_ID_SIZE ));

						if ( dl != null ){

							buddyDownloadData bdd = new buddyDownloadData( dl );

							if ( states != null ){

								bdd.setRemoteComplete(( states[i/FULL_ID_SIZE] & 0x01 ) != 0 );
							}

							res.put( dl, bdd );
						}
					}
				}
			}

			return( res );
		}

		protected Map<Download,Boolean>
		getDownloadsToTrack()
		{
			Map<Download,Boolean>	res = new HashMap<>();


			if ( tracker_so_enabled && seeding_only == buddy_seeding_only ){

				// log( "Not tracking, buddy and me both " + (seeding_only?"seeding":"downloading"), true, false );

				return( res );
			}

			long	now = SystemTime.getMonotonousTime();

			synchronized( this ){

				if ( downloads_in_common == null ){

					// log( "Not tracking, buddy has nothing in common", true, false );

					return( res );
				}

				Iterator<Map.Entry<Download,buddyDownloadData>> it = downloads_in_common.entrySet().iterator();

				while( it.hasNext()){

					Map.Entry<Download,buddyDownloadData>	entry = it.next();

					Download d = entry.getKey();

					buddyDownloadData	bdd = entry.getValue();

					if ( d.isComplete( false ) && bdd.isRemoteComplete()){

							// both complete, nothing to do!

						// log( d.getName() + " - not tracking, both complete", true, true );

					}else{

						long	last_check = bdd.getPeerCheckTime();

						if ( 	last_check == 0 ||
								now - last_check >= PEER_CHECK_INTERVAL ){

							log( d.getName() + " - checking peer", false, true );

							bdd.setPeerCheckTime( now );

							res.put( d, Boolean.TRUE);

						}else{

							res.put( d, Boolean.FALSE);
						}
					}
				}
			}

			return( res );
		}

		protected void
		removeDownload(
			Download		download )
		{
			synchronized( this ){

				if ( downloads_in_common == null ){

					return;
				}

				downloads_in_common.remove( download );
			}
		}

		protected String
		getStatus()
		{
			Map<Download,buddyDownloadData> c = downloads_in_common;

			String str = String.valueOf( tracked_downloads.size());

			str += "/" + tracking_remote + "/" + (c==null?"0":c.size());

			return( str );
		}

		protected void
		log(
			String	str )
		{
			BuddyPluginTracker.this.log( buddy.getName() + ": " + str );
		}

		protected void
		log(
			String	str,
			boolean	verbose,
			boolean	no_buddy )
		{
			BuddyPluginTracker.this.log( (no_buddy?"":( buddy.getName() + ": ")) + str, verbose );
		}
	}

	private static class
	buddyDownloadData
	{
		private boolean	local_is_complete;
		private boolean	remote_is_complete;
		private long	last_peer_check;

		protected
		buddyDownloadData(
			Download		download )
		{
			local_is_complete = download.isComplete( false );
		}

		protected void
		setLocalComplete(
			boolean		b )
		{
			local_is_complete	= b;
		}

		protected boolean
		isLocalComplete()
		{
			return( local_is_complete );
		}

		protected void
		setRemoteComplete(
			boolean		b )
		{
			remote_is_complete	= b;
		}

		protected boolean
		isRemoteComplete()
		{
			return( remote_is_complete );
		}

		protected void
		setPeerCheckTime(
			long	time )
		{
			last_peer_check	= time;
		}

		protected long
		getPeerCheckTime()
		{
			return( last_peer_check );
		}

		protected String
		getString()
		{
			return( "lic=" + local_is_complete + ",ric=" + remote_is_complete + ",lpc=" + last_peer_check );
		}
	}

	private static class
	downloadData
	{
		private static final byte[]	IV = {(byte)0x7A, (byte)0x7A, (byte)0xAD, (byte)0xAB, (byte)0x8E, (byte)0xBF, (byte)0xCD, (byte)0x39, (byte)0x87, (byte)0x0, (byte)0xA4, (byte)0xB8, (byte)0xFE, (byte)0x40, (byte)0xA2, (byte)0xE8 };

		private HashWrapper	id;

		protected
		downloadData(
			Download	download )
		{
			Torrent t = download.getTorrent();

			if ( t != null ){

				byte[]	hash = t.getHash();

				SHA1	sha1 = new SHA1();

				sha1.update( ByteBuffer.wrap( IV ));
				sha1.update( ByteBuffer.wrap( hash ));

				id = new HashWrapper( sha1.digest() );
			}
		}

		protected HashWrapper
		getID()
		{
			return( id );
		}
	}
	
	private static class
	PartialBuddyData
	{
		private final PartialBuddy		pb;
		private final List<Download>	downloads;
		
		private
		PartialBuddyData(
			PartialBuddy		_pb )
		{
			pb			= _pb;
			downloads	= new ArrayList<>();
		}
	}
}
