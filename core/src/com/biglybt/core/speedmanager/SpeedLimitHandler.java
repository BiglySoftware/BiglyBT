/*
 * Created on Feb 3, 2012
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


package com.biglybt.core.speedmanager;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.biglybt.core.Core;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.LimitedRateGroup;
import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.stats.transfer.LongTermStats;
import com.biglybt.core.stats.transfer.LongTermStatsListener;
import com.biglybt.core.stats.transfer.StatsFactory;
import com.biglybt.core.tag.*;
import com.biglybt.core.tag.impl.TagBase;
import com.biglybt.core.tag.impl.TagTypeWithState;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.*;
import com.biglybt.core.util.average.Average;
import com.biglybt.core.util.average.AverageFactory;
import com.biglybt.core.util.average.MovingImmediateAverage;
import com.biglybt.pif.PluginAdapter;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadAttributeListener;
import com.biglybt.pif.download.DownloadManagerListener;
import com.biglybt.pif.download.DownloadPeerListener;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.logging.LoggerChannelListener;
import com.biglybt.pif.network.RateLimiter;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.peers.PeerManager;
import com.biglybt.pif.peers.PeerManagerEvent;
import com.biglybt.pif.peers.PeerManagerListener2;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl;
import com.biglybt.plugin.net.buddy.BuddyPlugin;
import com.biglybt.plugin.net.buddy.BuddyPluginUtils; 

public class
SpeedLimitHandler
	implements LongTermStatsListener
{
	private static SpeedLimitHandler		singleton;

	private static final Object	RL_TO_BE_REMOVED_LOCK = new Object();
	private static final Object	RLD_TO_BE_REMOVED_KEY = new Object();
	private static final Object	RLU_TO_BE_REMOVED_KEY = new Object();

	private static final Object PEER_LT_WAIT_START_KEY	= new Object();
	private static final Object PEER_ASN_WAIT_START_KEY	= new Object();
	
	public static SpeedLimitHandler
	getSingleton(
		Core core )
	{
		synchronized( SpeedLimitHandler.class ){

			if ( singleton == null ){

				try{
					singleton = new SpeedLimitHandler( core );

				}catch( Throwable e ){

					Debug.out( e );
				}
			}

			return( singleton );
		}
	}

	private static final int SCHEDULER_PERIOD			= 30*1000;
	private static final int NETLIMIT_TAG_LOG_PERIOD	= 60*1000;
	private static final int NETLIMIT_TAG_LOG_TICKS		= NETLIMIT_TAG_LOG_PERIOD/SCHEDULER_PERIOD;

	private static final int PRIORITISER_CHECK_PERIOD_BASE	= 5*1000;

	private static final String	NET_IPV4		= "IPv4";
	private static final String	NET_IPV6		= "IPv6";
	private static final String	NET_LAN			= "LAN";
	private static final String	NET_WAN			= "WAN";

	private static final int AS_UNKOWN		= 0;
	private static final int AS_INACTIVE	= 1;
	private static final int AS_ACTIVE		= 2;
	
	final Core core;
	final PluginInterface 	plugin_interface;
	final TorrentAttribute	category_attribute;

	private final LoggerChannel	logger;

	private boolean	is_enabled = true;
	
	private TimerEventPeriodic		schedule_event;
	private List<ScheduleRule>		current_rules	= new ArrayList<>();
	private ScheduleRule			active_rule;

	private boolean					preserve_inactive_limits;
	private boolean					pause_forced_downloads	= true;
	
	private boolean					prioritiser_enabled = true;
	private TimerEventPeriodic		prioritiser_event;
	private List<Prioritiser>		current_prioritisers = new ArrayList<>();

	private Map<String,PeerSet>		current_ip_sets 			= new HashMap<>();
	private final Map<String,RateLimiter>	ip_set_rate_limiters_up 	= new HashMap<>();
	private final Map<String,RateLimiter>	ip_set_rate_limiters_down 	= new HashMap<>();
	private TimerEventPeriodic		ip_set_event;

	private boolean					net_limit_listener_added;

	private Map<Integer,List<NetLimit>>		net_limits_by_type	= new HashMap<>();
	private List<NetLimit>					net_limits_all		= new ArrayList<>();

	private final static String INACTIVE_PROFILE_NAME	= "preserved_limits (auto)";
	
	private final List<String> predefined_profile_names = new ArrayList<>();

	{
		predefined_profile_names.add( "null" );
		predefined_profile_names.add( "pause_all" );
		predefined_profile_names.add( "resume_all" );
	}

	private boolean rule_pause_all_active;
	private boolean net_limit_pause_all_active;

	private final IPSetTagType	ip_set_tag_type = TagManagerFactory.getTagManager().isEnabled()?new IPSetTagType():null;

	private final Object extensions_lock = new Object();

	private final List<String>	auto_peer_set_queue_client 	= new ArrayList<>();
	private final List<String>	auto_peer_set_queue_intf	= new ArrayList<>();
	
	private volatile	BuddyPlugin buddy_plugin;
	
	private
	SpeedLimitHandler(
		Core _core )
	{
		core 	= _core;

		plugin_interface = core.getPluginManager().getDefaultPluginInterface();

		category_attribute	= plugin_interface.getTorrentManager().getAttribute( TorrentAttribute.TA_CATEGORY );

		logger = plugin_interface.getLogger().getTimeStampedChannel( "Speed Limit Handler" );

		if ( Constants.isCVSVersion()){

			logger.setDiagnostic( 1024*1024, true);
		}

		UIManager	ui_manager = plugin_interface.getUIManager();

		final BasicPluginViewModel model =
			ui_manager.createBasicPluginViewModel( "Speed Limit Handler" );

		model.getActivity().setVisible( false );
		model.getProgress().setVisible( false );

		logger.addListener(
				new LoggerChannelListener()
				{
					@Override
					public void
					messageLogged(
						int		type,
						String	message )
					{
						model.getLogArea().appendText( message+"\n");
					}

					@Override
					public void
					messageLogged(
						String		str,
						Throwable	error )
					{
						model.getLogArea().appendText( error.toString()+"\n");
					}
				});

		loadPauseAllActive();

		loadSchedule( true );
		
		plugin_interface.addListener(
			new PluginAdapter()
			{
				@Override
				public void closedownInitiated(){
					
					int active_state = getActiveState();
					
					if ( active_state == AS_INACTIVE && preserve_inactive_limits ){
						
							// save latest inactive limits 
						
						saveProfile( INACTIVE_PROFILE_NAME );				
					}
				}
			});
	}

	private int
	getActiveState()
	{
		return( COConfigurationManager.getIntParameter( "speed.limit.handler.active.state" , AS_UNKOWN ));
	}

	private void
	setActiveState(
		int	state )
	{
		COConfigurationManager.setParameter( "speed.limit.handler.active.state" , state );
	}
	
	public boolean
	hasAnyProfiles() {
		if (!COConfigurationManager.hasParameter("speed.limit.handler.state",
				true)) {
			return false;
		}

		Map map = loadConfig();
		if (map.size() == 0) {
			return false;
		}

		List<Map> list = (List<Map>)map.get( "profiles" );
		if (list == null || list.size() == 0) {
			return false;
		}
		return true;
	}

	private synchronized Map
	loadConfig()
	{
		return( BEncoder.cloneMap( COConfigurationManager.getMapParameter( "speed.limit.handler.state", new HashMap())));
	}

	private synchronized void
	saveConfig(
		Map		map )
	{
		if (map.isEmpty()) {
			COConfigurationManager.removeParameter( "speed.limit.handler.state");
		} else {
			COConfigurationManager.setParameter( "speed.limit.handler.state", map );
		}

		COConfigurationManager.save();
	}

	private void
	loadPauseAllActive()
	{
		setRulePauseAllActive( COConfigurationManager.getBooleanParameter( "speed.limit.handler.schedule.pa_active", false ));

		setNetLimitPauseAllActive( COConfigurationManager.getBooleanParameter( "speed.limit.handler.schedule.nl_pa_active", false ));
	}

	private BuddyPlugin
	getBuddyPlugin()
	{
		if ( buddy_plugin == null ){
			
			buddy_plugin = BuddyPluginUtils.getPlugin();
		}
		
		return( buddy_plugin );
	}
	
	private void
	setRulePauseAllActive(
		boolean	active )
	{
		GlobalManager gm = core.getGlobalManager();

		if ( active ){

			if ( !rule_pause_all_active ){

				logger.logAlertRepeatable(
						LoggerChannel.LT_INFORMATION,
						"Pausing all downloads due to pause_all rule" );
			}

			gm.pauseDownloads( pause_forced_downloads );

			rule_pause_all_active = true;

		}else{

			if ( !net_limit_pause_all_active ){

				if ( COConfigurationManager.getBooleanParameter( "speed.limit.handler.schedule.pa_capable", false )){

					if ( rule_pause_all_active ){

						logger.logAlertRepeatable(
								LoggerChannel.LT_INFORMATION,
								"Resuming all downloads as pause_all rule no longer applies" );
					}

					gm.resumeDownloads( true );
				}
			}

			rule_pause_all_active = false;
		}

		COConfigurationManager.setParameter( "speed.limit.handler.schedule.pa_active", active );
	}

	private void
	setNetLimitPauseAllActive(
		boolean	active )
	{
		GlobalManager gm = core.getGlobalManager();

		if ( active ){

			if ( !net_limit_pause_all_active ){

				logger.logAlertRepeatable(
					LoggerChannel.LT_INFORMATION,
					"Pausing all downloads as network limit exceeded" );
			}

			gm.pauseDownloads( pause_forced_downloads );

			net_limit_pause_all_active = true;

		}else{

			if ( !rule_pause_all_active ){

				if ( COConfigurationManager.getBooleanParameter( "speed.limit.handler.schedule.pa_capable", false )){

					if ( net_limit_pause_all_active ){

						logger.logAlertRepeatable(
							LoggerChannel.LT_INFORMATION,
							"Resuming all downloads as network limit no longer exceeded" );
					}

					gm.resumeDownloads( true );
				}
			}

			net_limit_pause_all_active = false;
		}

		COConfigurationManager.setParameter( "speed.limit.handler.schedule.nl_pa_active", active );
	}

	public List<String>
	clearCurrentLimits()
	{
		if ( net_limit_pause_all_active ){

			setNetLimitPauseAllActive( false );
		}

		return( resetRules());
	}

	private List<String>
	resetRules()
	{
		if ( rule_pause_all_active ){

			setRulePauseAllActive( false );
		}

		LimitDetails details = new LimitDetails();

		details.loadForReset();

		details.apply();

		return( details.getString( true, false ));
	}

	public List<String>
	getCurrent()
	{
		LimitDetails details = new LimitDetails();

		details.loadCurrent();

		List<String> lines = details.getString( true, false );

		lines.add( "" );
		lines.add( "Peer Sets" );
		if ( current_ip_sets.size() == 0 ){
			lines.add( "    None" );
		}else{
			for( Map.Entry<String,PeerSet> entry: current_ip_sets.entrySet()){
				lines.add( "    " + entry.getValue().getDetailString());
			}
		}

		List<Object[]> tag_nls = new ArrayList<>();

		for ( Map.Entry<Integer, List<NetLimit>> entry: net_limits_by_type.entrySet()){

			for ( NetLimit nl: entry.getValue()){

				if ( nl.getTag() != null ){

					tag_nls.add( new Object[]{ entry.getKey(), nl });
				}
			}
		}

		if ( tag_nls.size() > 0 ){

			lines.add( "" );
			lines.add( "Tag/Peer Set Net Limits" );

			for (Object[] entry: tag_nls ){

				int 		type 	= (Integer)entry[0];
				NetLimit	nl 		= (NetLimit)entry[1];

				long[] stats = nl.getLongTermStats().getTotalUsageInPeriod( type, nl.getMultiplier());

				long[] limits = nl.getLimits();

				long total_up = stats[LongTermStats.ST_PROTOCOL_UPLOAD] + stats[LongTermStats.ST_DATA_UPLOAD];
				long total_do = stats[LongTermStats.ST_PROTOCOL_DOWNLOAD] + stats[LongTermStats.ST_DATA_DOWNLOAD];

				String	lim_str = "";

				lim_str += LongTermStats.PT_NAMES[type] + ", mult=" + nl.getMultiplier() + ": ";

				long total_lim 	= limits[0];
				long up_lim		= limits[1];
				long down_lim	= limits[2];

				String sep = "";

				if ( total_lim > 0 ){

					lim_str += "Total limit=" + DisplayFormatters.formatByteCountToKiBEtc( total_lim ) + ", used=" + DisplayFormatters.formatByteCountToKiBEtc( total_up+total_do ) + " - " + (100*(total_up+total_do)/total_lim) + "%";

					sep = ", ";
				}
				if ( up_lim > 0 ){

					lim_str += sep + "Up limit=" + DisplayFormatters.formatByteCountToKiBEtc( up_lim ) + ", used=" + DisplayFormatters.formatByteCountToKiBEtc( total_up ) + " - " + (100*(total_up)/up_lim) + "%";

					sep = ", ";
				}
				if ( down_lim > 0 ){

					lim_str += sep + "Down limit=" + DisplayFormatters.formatByteCountToKiBEtc( down_lim ) + ", used=" + DisplayFormatters.formatByteCountToKiBEtc( total_do ) + " - " + (100*(total_do)/down_lim) + "%";
				}

				lim_str += sep + "enabled=" + nl.isEnabled();

				String tag_name = nl.getTag().getTag().getTagName( true );

				String name = nl.getName();

				name += (name.length()==0?"":" ") + tag_name;

				lines.add( "    " + name + ": " + lim_str);
			}

		}

		if ( current_prioritisers.size() > 0 ){
			lines.add( "" );
			lines.add( "Prioritizers: " + current_prioritisers.size());
		}

		ScheduleRule rule = active_rule;

		lines.add( "" );
		lines.add( "Scheduler" );
		
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTime( new Date());

		lines.add( "    Current Time: " + cal.get( Calendar.HOUR_OF_DAY ) + ":" + cal.get( Calendar.MINUTE ));
		lines.add( "    Rules defined: " + current_rules.size());
		lines.add( "    Active rule: " + (rule==null?"None":rule.getString()));

		lines.add( "" );
		lines.add( "Network Totals" );

		LongTermStats lt_stats = StatsFactory.getLongTermStats();

		if ( lt_stats == null || !lt_stats.isEnabled()){

			lines.add( "    Not Available" );

		}else{

			lines.add( "    Today:\t\t" + getString( lt_stats, LongTermStats.PT_CURRENT_DAY, net_limits_by_type.get( LongTermStats.PT_CURRENT_DAY )));
			lines.add( "    This week:\t" + getString( lt_stats, LongTermStats.PT_CURRENT_WEEK, net_limits_by_type.get( LongTermStats.PT_CURRENT_WEEK )));
			lines.add( "    This month:\t" + getString( lt_stats, LongTermStats.PT_CURRENT_MONTH, net_limits_by_type.get( LongTermStats.PT_CURRENT_MONTH )));
			lines.add( "" );
			lines.add( "    Rate (3 minute average):\t\t" + getString( lt_stats.getCurrentRateBytesPerSecond(), null, true));
		}

		return( lines );
	}

	private String
	getString(
		LongTermStats	lts,
		int				type,
		List<NetLimit>	net_limits )
	{
		if ( net_limits == null ){

			net_limits = new ArrayList<>();
			
		}else{
			
			net_limits = new ArrayList<>( net_limits );
		}
		
		net_limits.add( 0, null );		// for overall

		String result = "";

		int lines = 0;
		
		for ( NetLimit net_limit: net_limits ){

			long[]	stats = getLongTermUsage( lts, type, net_limit );

			long total_up = stats[LongTermStats.ST_PROTOCOL_UPLOAD] + stats[LongTermStats.ST_DATA_UPLOAD] + stats[LongTermStats.ST_DHT_UPLOAD];
			long total_do = stats[LongTermStats.ST_PROTOCOL_DOWNLOAD] + stats[LongTermStats.ST_DATA_DOWNLOAD] + stats[LongTermStats.ST_DHT_DOWNLOAD];

			String	lim_str = "";
			String	profile	= null;

			if ( net_limit != null ){

				if ( net_limit.getTag() != null ){

					continue;
				}

				profile = net_limit.getProfile();

				long[] limits = net_limit.getLimits();

				long total_lim 	= limits[0];
				long up_lim		= limits[1];
				long down_lim	= limits[2];

				if ( total_lim > 0 ){

					lim_str += "Total=" + DisplayFormatters.formatByteCountToKiBEtc( total_lim ) + " " + (100*(total_up+total_do)/total_lim) + "%";
				}
				if ( up_lim > 0 ){

					lim_str += (lim_str.length()==0?"":", ") + "Up=" + DisplayFormatters.formatByteCountToKiBEtc( up_lim ) + " " + (100*(total_up)/up_lim) + "%";
				}
				if ( down_lim > 0 ){

					lim_str += (lim_str.length()==0?"":", ") + "Down=" + DisplayFormatters.formatByteCountToKiBEtc( down_lim ) + " " + (100*(total_do)/down_lim) + "%";
				}

				if ( lim_str.length() > 0 ){

					lim_str = "\t[ Limits: " + lim_str + "]";
				}
			}

			lines++;

			if ( lines > 1 ){

				if ( lines == 2 ){
					result = "\r\n        " + result;
				}
				
				result += "\r\n        ";
			}

			result +=
				(profile==null?"Overall":profile) + " - " +
				"Upload=" + DisplayFormatters.formatByteCountToKiBEtc( total_up ) + ", Download=" + DisplayFormatters.formatByteCountToKiBEtc( total_do ) + lim_str;
		}

		return( result );
	}

	private long[]
	getLongTermUsage(
		LongTermStats	lts,
		int				type,
		NetLimit		net_limit_maybe_null )
	{
		double multiplier=net_limit_maybe_null==null?1:net_limit_maybe_null.getMultiplier();

		if ( net_limit_maybe_null == null || net_limit_maybe_null.getProfile() == null ){

			return( lts.getTotalUsageInPeriod( type, multiplier ));
		}

		final String profile = net_limit_maybe_null.getProfile();

		return(
			lts.getTotalUsageInPeriod(
				type, multiplier,
				new LongTermStats.RecordAccepter()
				{
					@Override
					public boolean
					acceptRecord(
						long timestamp)
					{
						ScheduleRule rule = getActiveRule( new Date( timestamp ));

						return( rule != null && rule.profile_name.equals( profile ));
					}
				}));
	}

	private String
	getString(
		long[]				stats,
		long[]				limits,
		boolean				is_rate )
	{
		long total_up = stats[LongTermStats.ST_PROTOCOL_UPLOAD] + stats[LongTermStats.ST_DATA_UPLOAD] + stats[LongTermStats.ST_DHT_UPLOAD];
		long total_do = stats[LongTermStats.ST_PROTOCOL_DOWNLOAD] + stats[LongTermStats.ST_DATA_DOWNLOAD] + stats[LongTermStats.ST_DHT_DOWNLOAD];

		String	lim_str = "";

		if ( limits != null ){

			long total_lim 	= limits[0];
			long up_lim		= limits[1];
			long down_lim	= limits[2];

			if ( total_lim > 0 ){

				lim_str += "Total=" + DisplayFormatters.formatByteCountToKiBEtc( total_lim ) + " " + (100*(total_up+total_do)/total_lim) + "%";
			}
			if ( up_lim > 0 ){

				lim_str += (lim_str.length()==0?"":", ") + "Up=" + DisplayFormatters.formatByteCountToKiBEtc( up_lim ) + " " + (100*(total_up)/up_lim) + "%";
			}
			if ( down_lim > 0 ){

				lim_str += (lim_str.length()==0?"":", ") + "Down=" + DisplayFormatters.formatByteCountToKiBEtc( down_lim ) + " " + (100*(total_do)/down_lim) + "%";
			}

			if ( lim_str.length() > 0 ){

				lim_str = "\t[ Limits: " + lim_str + "]";
			}
		}

		if ( is_rate ){

			return( "Upload=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( total_up ) + ", Download=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( total_do ));

		}else{

			return( "Upload=" + DisplayFormatters.formatByteCountToKiBEtc( total_up ) + ", Download=" + DisplayFormatters.formatByteCountToKiBEtc( total_do ) + lim_str );
		}
	}

	public List<String>
	getProfileNames()
	{
		Map	map = loadConfig();

		List<String> profiles = new ArrayList<>();

		List<Map> list = (List<Map>)map.get( "profiles" );

		if ( list != null ){

			for ( Map m: list ){

				String	name = importString( m, "n" );

				if ( name != null ){

					profiles.add( name );
				}
			}
		}

		return( profiles );
	}

	public List<String>
	loadProfile(
		String		name )
	{
		Map	map = loadConfig();

		List<Map> list = (List<Map>)map.get( "profiles" );

		if ( list != null ){

			for ( Map m: list ){

				String	p_name = importString( m, "n" );

				if ( p_name != null && name.equals( p_name )){

					Map profile = (Map)m.get( "p" );

					LimitDetails ld = new LimitDetails( profile );

					ld.apply();

					return( ld.getString( false, false ));
				}
			}
		}

		List<String> result = new ArrayList<>();

		result.add( "Profile not found" );

		return( result );
	}

	private boolean
	profileExists(
		String		name )
	{
		Map	map = loadConfig();

		List<Map> list = (List<Map>)map.get( "profiles" );

		if ( list != null ){

			for ( Map m: list ){

				String	p_name = importString( m, "n" );

				if ( p_name != null && name.equals( p_name )){

					return( true );
				}
			}
		}

		return( false );
	}

	public List<String>
	getProfile(
		String		name )
	{
		return( getProfileSupport( name, false ));
	}

	public List<String>
	getProfileSupport(
		String		name,
		boolean		use_hashes )
	{
		Map	map = loadConfig();

		List<Map> list = (List<Map>)map.get( "profiles" );

		if ( list != null ){

			for ( Map m: list ){

				String	p_name = importString( m, "n" );

				if ( p_name != null && name.equals( p_name )){

					Map profile = (Map)m.get( "p" );

					LimitDetails ld = new LimitDetails( profile );

					return( ld.getString( false, use_hashes ));
				}
			}
		}

		List<String> result = new ArrayList<>();

		result.add( "Profile not found" );

		return( result );
	}

	public List<String>
	getProfilesForDownload(
		byte[]		hash )
	{
		List<String> result = new ArrayList<>();

		Map	map = loadConfig();

		List<Map> list = (List<Map>)map.get( "profiles" );

		if ( list != null ){

			String	hash_str = Base32.encode( hash );

			for ( Map m: list ){

				String	p_name = importString( m, "n" );

				if ( p_name != null ){

					Map profile = (Map)m.get( "p" );

					LimitDetails ld = new LimitDetails( profile );

					if ( ld.getLimitsForDownload( hash_str ) != null ){

						result.add( p_name );
					}
				}
			}
		}

		return( result );
	}

	private void
	addRemoveDownloadsToProfile(
		String			name,
		List<byte[]>	hashes,
		boolean			add )
	{
		Map	map = loadConfig();

		List<Map> list = (List<Map>)map.get( "profiles" );

		List<String>	hash_strs = new ArrayList<>();

		for ( byte[] hash: hashes ){

			hash_strs.add( Base32.encode( hash ));
		}

		if ( list != null ){

			for ( Map m: list ){

				String	p_name = importString( m, "n" );

				if ( p_name != null && name.equals( p_name )){

					Map profile = (Map)m.get( "p" );

					LimitDetails ld = new LimitDetails( profile );

					ld.addRemoveDownloads( hash_strs, add );

					m.put( "p", ld.export());

					saveConfig( map );

					return;
				}
			}
		}
	}

	public void
	addDownloadsToProfile(
		String			name,
		List<byte[]>	hashes )
	{
		addRemoveDownloadsToProfile( name, hashes, true );
	}

	public void
	removeDownloadsFromProfile(
		String			name,
		List<byte[]>	hashes )
	{
		addRemoveDownloadsToProfile( name, hashes, false );
	}

	public void
	deleteProfile(
		String		name )
	{
		Map	map = loadConfig();

		List<Map> list = (List<Map>)map.get( "profiles" );

		if ( list != null ){

			for ( Map m: list ){

				String	p_name = importString( m, "n" );

				if ( p_name != null && name.equals( p_name )){

					list.remove( m );

					saveConfig( map );

					return;
				}
			}
		}
	}

	public List<String>
	saveProfile(
		String		name )
	{
		LimitDetails details = new LimitDetails();

		details.loadCurrent();

		Map	map = loadConfig();

		List<Map> list = (List<Map>)map.get( "profiles" );

		if ( list == null ){

			list = new ArrayList<>();

			map.put( "profiles", list );
		}

		for ( Map m: list ){

			String	p_name = importString( m, "n" );

			if ( p_name != null && name.equals( p_name )){

				list.remove( m );

				break;
			}
		}

		Map m = new HashMap();

		list.add( m );

		m.put( "n", name );
		m.put( "p", details.export());

		saveConfig( map );

		ScheduleRule	rule;

		synchronized( this ){

			rule = active_rule;
		}

		if ( rule != null && rule.profile_name.equals( name )){

			details.apply();
		}

		return( details.getString( false, false ));
	}

	private synchronized List<String>
	loadSchedule(
		boolean	start_of_day )
	{
		List<String>	result = new ArrayList<>();

		List list = COConfigurationManager.getListParameter( "speed.limit.handler.schedule.lines", new ArrayList());
		List<String> schedule_lines = BDecoder.decodeStrings( BEncoder.cloneList(list) );

		boolean	enabled = true;

		List<ScheduleRule>	rules 	= new ArrayList<>();
		Map<String,PeerSet>	ip_sets	= new HashMap<>();

		Map<Integer,List<NetLimit>> new_net_limits_by_type	= new HashMap<>();
		List<NetLimit>				new_net_limits_all		= new ArrayList<>();

		List<Prioritiser>	new_prioritisers = new ArrayList<>();

		boolean checked_lts_enabled	= false;
		boolean	lts_enabled			= false;
		boolean preserve_limits		= false;
		boolean pause_forced		= true;
		
		for ( String line: schedule_lines ){

			line = line.trim();

			if ( line.length() == 0 || line.startsWith( "#" )){

				continue;
			}

			String lc_line = line.toLowerCase( Locale.US );

			if ( lc_line.startsWith( "enable" )){

				String[]	bits = lc_line.split( "=" );

				boolean	ok = false;

				if ( bits.length == 2 ){

					String arg = bits[1].trim();

					if ( arg.equals( "yes" )){

						enabled = true;
						ok		= true;

					}else if ( arg.equals( "no" )){

						enabled = false;
						ok		= true;
					}
				}

				if ( !ok ){

					result.add( "'" +line + "' is invalid: use enable=(yes|no)" );
				}
			}else if ( lc_line.startsWith( "preserve_inactive_limits" )){

				String[]	bits = lc_line.split( "=" );

				boolean	ok = false;

				if ( bits.length == 2 ){

					String arg = bits[1].trim();

					if ( arg.equals( "yes" )){

						preserve_limits = true;
						ok		= true;

					}else if ( arg.equals( "no" )){

						preserve_limits = false;
						ok		= true;
					}
				}

				if ( !ok ){

					result.add( "'" +line + "' is invalid: use preserve_inactive_limits=(yes|no)" );
				}
			}else if ( lc_line.startsWith( "pause_force_downloads" )){

				String[]	bits = lc_line.split( "=" );

				boolean	ok = false;

				if ( bits.length == 2 ){

					String arg = bits[1].trim();

					if ( arg.equals( "yes" )){

						pause_forced = true;
						ok		= true;

					}else if ( arg.equals( "no" )){

						pause_forced = false;
						ok		= true;
					}
				}

				if ( !ok ){

					result.add( "'" +line + "' is invalid: use pause_force_downloads=(yes|no)" );
				}

			}else if ( lc_line.startsWith( "ip_set" ) || lc_line.startsWith( "peer_set" ) ){

				try{
						// uppercase here as category names are case sensitive..

					String[] args = line.substring(lc_line.indexOf('_')+4).split( "," );

					boolean	inverse 	= false;
					int		up_lim		= -1;
					int		down_lim	= -1;

					int		peer_up_lim		= 0;
					int		peer_down_lim	= 0;


					Set<String>	categories_or_tags = new HashSet<>();

					Pattern client_pattern	= null;
					Pattern intf_pattern	= null;
					Pattern asn_pattern		= null;
					boolean client_pattern_inverse	= false;
					boolean intf_pattern_inverse	= false;
					boolean asn_pattern_inverse		= false;
					
					String group	= null;
					
					PeerSet set = null;

					for ( String arg: args ){

						String[]	bits = arg.split( "=" );

						if ( bits.length != 2 ){

							throw( new Exception( "Expected <key>=<value> for '" + arg + "'" ));

						}else{

							String lhs		= bits[0].trim();
							String lc_lhs	= lhs.toLowerCase( Locale.US );
							String rhs 		= bits[1].trim();

							String lc_rhs = rhs.toLowerCase( Locale.US );

							if ( lc_lhs.equals( "inverse" )){

								inverse = lc_rhs.equals( "yes" );

							}else if ( lc_lhs.equals( "up" )){

								up_lim = (int)parseRate( lc_rhs );

							}else if ( lc_lhs.equals( "down" )){

								down_lim = (int)parseRate( lc_rhs );

							}else if ( lc_lhs.equals( "peer_up" )){

								peer_up_lim = (int)parseRate( lc_rhs );

							}else if ( lc_lhs.equals( "peer_down" )){

								peer_down_lim = (int)parseRate( lc_rhs );

							}else if ( lc_lhs.equals( "cat" ) || lc_lhs.equals( "tag" )){

								String[] cats = rhs.split( " " );

								for ( String cat: cats ){

									cat = cat.trim();

									if ( cat.length() > 0 ){

										categories_or_tags.add( cat );
									}
								}
							}else if ( lc_lhs.equals( "client" )){
								
								try{
									if ( rhs.startsWith( "!" )){
										rhs = rhs.substring(1);
										client_pattern_inverse = true;
									}
									
									client_pattern = Pattern.compile( rhs, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );
									
								}catch( Throwable e ){
									
									throw( new Exception( "Invalid client pattern - '" + rhs + "'" ));
								}
							}else if ( lc_lhs.equals( "intf" )){
								
								try{
									if ( rhs.startsWith( "!" )){
										rhs = rhs.substring(1);
										intf_pattern_inverse = true;
									}
									
									intf_pattern = Pattern.compile( rhs, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );
									
								}catch( Throwable e ){
									
									throw( new Exception( "Invalid intf pattern - '" + rhs + "'" ));
								}
							}else if ( lc_lhs.equals( "asn" )){
								
								try{
									if ( rhs.startsWith( "!" )){
										rhs = rhs.substring(1);
										asn_pattern_inverse = true;
									}
									
									asn_pattern = Pattern.compile( rhs, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );
									
								}catch( Throwable e ){
									
									throw( new Exception( "Invalid asn pattern - '" + rhs + "'" ));
								}
							}else if ( lc_lhs.equals( "group" )){

								group = rhs;
								
							}else{

								String name = lhs;

								String def = rhs.replace(';', ' ');

								set = ip_sets.get( name );

								if ( set == null ){

									set = new PeerSet( name );

									ip_sets.put( name, set );
								}

								bits = def.split( " " );

								for ( String bit: bits ){

									bit = bit.trim();

									if ( bit.length() > 0 ){

										PeerSet other_set = ip_sets.get( bit );

										if ( other_set != null && other_set != set ){

											set.addSet( other_set );

										}else{

											if ( !set.addCIDRorCCetc( bit )){

												result.add( "CIDR, CC, Network or ip_set reference '" + bit + "' isn't valid" );
											}
										}
									}
								}
							}
						}
					}

					if ( set == null ){

						throw( new Exception());
					}

					int	pattern_count = 0;
					if ( client_pattern != null )pattern_count++;
					if ( intf_pattern != null )pattern_count++;
					if ( asn_pattern != null )pattern_count++;
					
					if ( pattern_count > 1 ){
						
						throw( new Exception( "Only one of client, intf and asn pattern can be set for a peer set" ));
					}
					
					set.setParameters( 
							inverse, up_lim, down_lim, peer_up_lim, peer_down_lim, categories_or_tags, 
							client_pattern, client_pattern_inverse, 
							intf_pattern, intf_pattern_inverse,
							asn_pattern, asn_pattern_inverse, group );

				}catch( Throwable e ){

					result.add( "'" +line + "' is invalid: use ip_set <name>=<cidrs...> [,inverse=[yes|no]] [,up=<limit>] [,down=<limit>] [,peer_up=<limit>] [,peer_down=<limit>] [,cat=<categories>]: " + e.getMessage());
				}
			}else if ( lc_line.startsWith( "net_limit" )){

				if ( !checked_lts_enabled ){

					checked_lts_enabled = true;

					lts_enabled = StatsFactory.getLongTermStats().isEnabled();

					if ( !lts_enabled ){

						result.add( "Long-term stats are currently disabled, limits will NOT be applied" );
					}
				}

				line = line.substring(9).replace( ",", " " );

				String[] args = line.split( " " );

				String	name		= "";
				int		type		= -1;
				double	mult 		= 1;
				String	profile		= null;

				TagType		tag_type 	= null;
				String		tag_name	= null;

				long	total_lim	= 0;
				long	up_lim		= 0;
				long	down_lim	= 0;

				for ( String arg: args ){

					arg = arg.trim();

					if ( arg.length() == 0 ){

						continue;
					}

					if ( type == -1 ){

						int	sep = arg.indexOf( ":" );

						if ( sep != -1 ){

							profile = arg.substring( sep+1 ).trim();

							if ( !profileExists( profile )){

								result.add( "net_limit profile '" + profile + "' not defined" );

								break;
							}

							arg = arg.substring( 0, sep );

						}else{

							sep = arg.indexOf( "$" );

							if ( sep != -1 ){

								tag_name = arg.substring( sep+1 ).trim();

								TagType tag_type_dm = TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_MANUAL );

								TagFeatureRateLimit tag_dm = (TagFeatureRateLimit)tag_type_dm.getTag( tag_name, true );

								if ( tag_dm != null ){

									tag_type = tag_type_dm;

								}else{

									if ( ip_sets.get( tag_name ) != null ){

										tag_type = ip_set_tag_type;
									}
								}

								if ( tag_type == null ){

									result.add( "net_limit tag/peer set '" + tag_name + "' not defined or invalid" );

									break;
								}

								arg = arg.substring( 0, sep );
							}
						}

						int	pos = arg.indexOf( "*" );

						if ( pos != -1 ){

							mult = Double.parseDouble( arg.substring( pos+1 ));

							arg = arg.substring( 0, pos );
						}

						boolean	sliding = false;

						if ( arg.equalsIgnoreCase( "hourly" )){

							type = LongTermStats.PT_CURRENT_HOUR;

						}else if ( arg.equalsIgnoreCase( "shourly" )){

							type = LongTermStats.PT_SLIDING_HOUR;

							sliding	= true;

						}else if ( arg.equalsIgnoreCase( "daily" )){

							type = LongTermStats.PT_CURRENT_DAY;

						}else if ( arg.equalsIgnoreCase( "sdaily" )){

							type = LongTermStats.PT_SLIDING_DAY;

							sliding	= true;

						}else if ( arg.equalsIgnoreCase( "weekly" )){

							type = LongTermStats.PT_CURRENT_WEEK;

						}else if ( arg.equalsIgnoreCase( "sweekly" )){

							type = LongTermStats.PT_SLIDING_WEEK;

							sliding	= true;

						}else if ( arg.equalsIgnoreCase( "monthly" )){

							type = LongTermStats.PT_CURRENT_MONTH;

						}else{

							result.add( "net_limit type of '" + arg + "' not recognised - use hourly, daily, weekly or monthly" );

							break;
						}

						if ( mult != 1 && !sliding ){

							result.add( "'" + line + "': invalid net_limit specification. multiplier only supported for sliding windows." );

						}
					}else{

						String[]	bits = arg.split( "=" );

						if ( bits.length != 2 ){

							result.add( "'" + line + "': invalid net_limit specification" );

						}else{

							String lhs = bits[0];
							String rhs = bits[1];

							if ( lhs.equalsIgnoreCase( "name" )){

								name = rhs;

							}else{
								long lim = parseRate( rhs );

								if ( lhs.equalsIgnoreCase( "total" )){

									total_lim = lim;

								}else if ( lhs.equalsIgnoreCase( "up" )){

									up_lim = lim;

								}else if ( lhs.equalsIgnoreCase( "down" )){

									down_lim = lim;

								}else{

									result.add( "'" + line + "': invalid net_limit specification" );
								}
							}
						}
					}
				}

				if ( type != -1 ){

					List<NetLimit>	limits = new_net_limits_by_type.get( type );

					if ( limits == null ){

						limits = new ArrayList<>();

						new_net_limits_by_type.put( type, limits );
					}

					NetLimit limit = new NetLimit( type, name, mult, profile, tag_type, tag_name, total_lim, up_lim, down_lim );

					limits.add( limit );

					new_net_limits_all.add( limit );
				}
			}else if ( lc_line.startsWith( "priority_down " ) || lc_line.startsWith( "priority_up " )){

				String[] args = line.substring(lc_line.indexOf(' ')+1).split( "," );

				Prioritiser pri = new Prioritiser();

				pri.setIsDown( lc_line.startsWith( "priority_down " ));

				TagType tag_type_dm = TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_MANUAL );

				boolean	pri_ok = true;

				for ( String arg: args ){

					String[]	bits = arg.split( "=" );

					boolean ok = false;

					try{
						if ( bits.length == 2 ){

							String lhs 	= bits[0].trim();
							String rhs	= bits[1].trim();

							if ( Character.isDigit( lhs.charAt(0))){

								int p = Integer.parseInt( lhs );

								TagType	tag_type = null;

								TagFeatureRateLimit tag_dm = (TagFeatureRateLimit)tag_type_dm.getTag( rhs, true );

								if ( tag_dm != null ){

									tag_type = tag_type_dm;

								}else{

									if ( ip_sets.get( rhs ) != null ){

										tag_type = ip_set_tag_type;
									}
								}

								if ( tag_type != null ){

									pri.addTarget( p, tag_type, rhs );

									ok = true;
								}
							}else if ( lhs.equalsIgnoreCase( "freq" )){

								pri.setFrequency( Integer.parseInt( rhs ));

								ok = true;

							}else if ( lhs.equalsIgnoreCase( "rest" )){

								pri.setRestTicks( Integer.parseInt( rhs ));

								ok = true;

							}else if ( lhs.equalsIgnoreCase( "probe" )){

								pri.setProbePeriod( Integer.parseInt( rhs ));

								ok = true;

							}else if ( lhs.equals( "min" )){

								int min = (int)parseRate( rhs );

								if ( min == 0 ){

									min = -1;
								}

								pri.setMinimum( min );

								ok = true;

							}else if ( lhs.equals( "max" )){

								pri.setMaximum((int)parseRate( rhs ));

								ok = true;

							}else if ( lhs.equals( "name" )){

								pri.setName( rhs );

								ok = true;
							}
						}
					}catch( Throwable e ){
					}

					if ( !ok ){

						result.add( "'" + line + "': invalid argument: " + arg );

						pri_ok = false;
					}
				}

				if ( pri.getTargetCount() < 2 ){

					result.add( "'" + line + "': insufficient targets" );

				}else{

					if ( pri_ok ){

						new_prioritisers.add( pri );
					}
				}
			}else{

				String[]	_bits = line.split( " " );

				List<String>	bits = new ArrayList<>();

				for ( String b: _bits ){

					b = b.trim();

					if ( b.length() > 0 ){

						bits.add( b );
					}
				}

				List<String>	errors = new ArrayList<>();

				if ( bits.size() >= 6 ){

					String	freq_str = bits.get(0).toLowerCase( Locale.US );

					byte	freq = 0;

					if ( freq_str.equals( "daily" )){

						freq = ScheduleRule.FR_DAILY;

					}else if ( freq_str.equals( "weekdays" )){

						freq = ScheduleRule.FR_WEEKDAY;

					}else if ( freq_str.equals( "weekends" )){

						freq = ScheduleRule.FR_WEEKEND;

					}else if ( freq_str.equals( "mon" )){

						freq = ScheduleRule.FR_MON;

					}else if ( freq_str.equals( "tue" )){

						freq = ScheduleRule.FR_TUE;

					}else if ( freq_str.equals( "wed" )){

						freq = ScheduleRule.FR_WED;

					}else if ( freq_str.equals( "thu" )){

						freq = ScheduleRule.FR_THU;

					}else if ( freq_str.equals( "fri" )){

						freq = ScheduleRule.FR_FRI;

					}else if ( freq_str.equals( "sat" )){

						freq = ScheduleRule.FR_SAT;

					}else if ( freq_str.equals( "sun" )){

						freq = ScheduleRule.FR_SUN;

					}else{

						errors.add( "frequency '" + freq_str + "' is invalid" );
					}

					String	profile = bits.get(1);

					if ( !profileExists( profile ) && !predefined_profile_names.contains( profile.toLowerCase())){

						errors.add( "profile '" + profile + "' not found" );

						profile = null;
					}

					int from_mins = -1;

					if ( bits.get(2).equalsIgnoreCase( "from" )){

						from_mins = getMins( bits.get(3));
					}

					if ( from_mins == -1 ){

						errors.add( "'from' is invalid" );
					}

					int to_mins = -1;

					if ( bits.get(4).equalsIgnoreCase( "to" )){

						to_mins = getMins( bits.get(5));
					}

					if ( to_mins == -1 ){

						errors.add( "'to' is invalid" );
					}

					List<ScheduleRuleExtensions>	extensions = null;

					for ( int i=6; i<bits.size(); i++ ){

							// optional extensions
							// start_tag:<tag_name> and stop_tag

						String	extension = bits.get(i);

						String[] temp = extension.split( ":" );

						boolean	ok 		= false;
						String	extra 	= "";

						if ( temp.length == 1 ){

							String	ext_cmd 	= temp[0];

							if ( 	ext_cmd.equals( "enable_priority" ) ||
									ext_cmd.equals( "disable_priority" )){

								if ( extensions == null ){

									extensions = new ArrayList<>(bits.size() - 6);
								}

								int	et;

								if ( ext_cmd.equals( "enable_priority" )){

									et = ScheduleRuleExtensions.ET_ENABLE_PRIORITY;

								}else{

									et = ScheduleRuleExtensions.ET_DISABLE_PRIORITY;
								}

								extensions.add( new ScheduleRuleExtensions( et ));

								ok = true;
							}
						}else if ( temp.length == 2 ){

							String	ext_cmd 	= temp[0];
							String	ext_param	= temp[1];

							if ( 	ext_cmd.equals( "start_tag" ) ||
									ext_cmd.equals( "stop_tag" )  ||
									ext_cmd.equals( "pause_tag" ) ||
									ext_cmd.equals( "resume_tag" )){

								TagDownload tag = (TagDownload)TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_MANUAL ).getTag( ext_param, true );

								if ( tag == null ){

									tag = (TagDownload)TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_STATE ).getTag( ext_param, true );

								}
								if ( tag == null ){

									extra = ", tag '" + ext_param + "' not found";

								}else{

									if ( extensions == null ){

										extensions = new ArrayList<>(bits.size() - 6);
									}

									int	et;

									if ( ext_cmd.equals( "start_tag" )){

										et = ScheduleRuleExtensions.ET_START_TAG;

									}else if ( ext_cmd.equals( "stop_tag" )){

										et = ScheduleRuleExtensions.ET_STOP_TAG;

									}else if ( ext_cmd.equals( "pause_tag" )){

										et = ScheduleRuleExtensions.ET_PAUSE_TAG;

									}else{

										et = ScheduleRuleExtensions.ET_RESUME_TAG;
									}

									extensions.add( new ScheduleRuleExtensions( et, tag ));

									ok = true;
								}
							}else if ( 	ext_cmd.equals( "enable_net_limit" ) ||
										ext_cmd.equals( "disable_net_limit" )){

								List<NetLimit>	limits = new ArrayList<>();

								String[] nls = ext_param.split( ";" );

								List<String> missing = new ArrayList<>();

								for ( String nl: nls ){

									nl = nl.trim();

									boolean found = false;

									for ( NetLimit x: net_limits_all ){

										if ( x.getName().equals( nl )){

											limits.add( x );

											found = true;

											break;
										}
									}
									if ( !found ){

										missing.add( nl );
									}
								}

								if ( missing.size() == 0 ){

									int	et;

									if ( ext_cmd.equals( "enable_net_limit" )){

										et = ScheduleRuleExtensions.ET_ENABLE_NET_LIMIT;

									}else{

										et = ScheduleRuleExtensions.ET_DISABLE_NET_LIMIT;
									}

									ok = true;

									if ( extensions == null ){

										extensions = new ArrayList<>(bits.size() - 6);
									}

									extensions.add( new ScheduleRuleExtensions( et, limits ));

								}else{

									extra = ", net_limit(s) '" + missing + "' not found";
								}
							}
						}

						if ( !ok ){

							errors.add( "extension '" + extension + "' is invalid" + extra );
						}
					}

					if ( errors.size() == 0 ){

						rules.add( new ScheduleRule( freq, profile, from_mins, to_mins, extensions ));

					}else{

						String	err_str = "";

						for ( String e: errors ){

							err_str += (err_str.length()==0?"":", ") + e;
						}

						result.add( "'" + line + "' is invalid (" + err_str + ") - use <frequency> <profile> from <hh:mm> to <hh:mm>" );
					}
				}else{

					result.add( "'" + line + "' is invalid: use <frequency> <profile> from <hh:mm> to <hh:mm> [extensions]*" );
				}
			}
		}

			// schedule fully loaded into local variables
			// handle overall changes in pause/resume features, in particular to disable them if
			// the schedule no longer controls them

		boolean	schedule_has_net_limits = false;
		boolean	schedule_has_pausing 	= false;

		if ( enabled ){

			preserve_inactive_limits	= preserve_limits;
			pause_forced_downloads		= pause_forced;
			
			if ( start_of_day && rules.isEmpty()){
				
				setActiveState( AS_INACTIVE );
			}
			
			if ( new_net_limits_all.size() > 0 ){

				schedule_has_net_limits = true;
			}

			for ( ScheduleRule rule: rules ){

				String profile_name = rule.profile_name;

				if ( profile_name.equalsIgnoreCase( "pause_all" ) || profile_name.equalsIgnoreCase( "resume_all" )){

					schedule_has_pausing = true;

					break;
				}
			}
		}

		if ( !schedule_has_pausing ){

			setRulePauseAllActive( false );
		}

		if ( !schedule_has_net_limits ){

			setNetLimitPauseAllActive( false );
		}

			// this marker is used to prevent unwanted 'resumeAll' operations being performed by the
			// scheduler when it is enabled but doesn't have any features that could warrant this. This
			// allows manual pause states to be respected. Of course we should probably differeniate between
			// manually paused downloads and those auto-paused to generally support this better, but
			// that would take a bit of effort to persistently remember this....

		COConfigurationManager.setParameter( "speed.limit.handler.schedule.pa_capable", enabled && ( schedule_has_pausing || schedule_has_net_limits ));

		if ( enabled ){

			current_rules = rules;

				// need to do ip-sets early as other things (netlimits, prioritizers) can refer to ipset tag types
				// and these are created here

			for( PeerSet s: current_ip_sets.values()){

				s.destroy();
			}

			current_ip_sets.clear();

			initialiseIPSets( ip_sets );

			checkIPSets();

				// net limits

			if ( !lts_enabled ){

				new_net_limits_by_type.clear();
				
				new_net_limits_all.clear();
			}

			net_limits_by_type	= new_net_limits_by_type;
			net_limits_all		= new_net_limits_all;

			if ( net_limits_all.size() > 0 ){

				for ( NetLimit n: net_limits_all ){

					n.initialise();
				}

				if ( !net_limit_listener_added ){

					net_limit_listener_added = true;

					StatsFactory.getLongTermStats().addListener( 1024*1024, this );
				}

				updated( StatsFactory.getLongTermStats());

			}else{

				if ( net_limit_listener_added ){

					net_limit_listener_added = false;

					StatsFactory.getLongTermStats().removeListener( this );
				}
			}

				// prioritizers

			current_prioritisers = new_prioritisers;

			if ( new_prioritisers.size() == 0 ){

				if ( prioritiser_event != null ){

					prioritiser_event.cancel();

					prioritiser_event = null;
				}
			}else{

				for ( Prioritiser p: new_prioritisers ){

					p.initialise();
				}

				if ( prioritiser_event == null ){

					prioritiser_event =
						SimpleTimer.addPeriodicEvent(
								"speed handler prioritiser",
								PRIORITISER_CHECK_PERIOD_BASE,
								new TimerEventPerformer()
								{
									@Override
									public void
									perform(
										TimerEvent event)
									{
										checkPrioritisers();
									}
								});
				}
			}

				// setup scheduler if needed

			if ( schedule_event == null && ( rules.size() > 0 || net_limits_all.size() > 0 )){

				schedule_event =
					SimpleTimer.addPeriodicEvent(
						"speed handler scheduler",
						SCHEDULER_PERIOD,
						new TimerEventPerformer()
						{
							private int tick_count;

							@Override
							public void
							perform(
								TimerEvent event)
							{
								tick_count++;

								checkSchedule( false, tick_count );
							}
						});
			}

			if ( active_rule != null || rules.size() > 0 || net_limits_all.size() > 0 ){

				checkSchedule( start_of_day, 0 );
			}

		}else{
			
			current_rules.clear();

			if ( schedule_event != null ){

				schedule_event.cancel();

				schedule_event = null;
			}

			if ( active_rule != null ){

				active_rule	= null;

				setProfileActive( null );
			}

			for( PeerSet s: current_ip_sets.values()){

				s.destroy();
			}

			current_ip_sets.clear();

			checkIPSets();

			if ( net_limit_pause_all_active ){

				setNetLimitPauseAllActive( false );
			}

			net_limits_by_type.clear();
			
			net_limits_all.clear();

			if ( net_limit_listener_added ){

				net_limit_listener_added = false;

				StatsFactory.getLongTermStats().removeListener( this );
			}
			
			setActiveState( AS_INACTIVE );
		}
		
		if ( !preserve_inactive_limits && profileExists( INACTIVE_PROFILE_NAME )){
			
			deleteProfile( INACTIVE_PROFILE_NAME );
		}

		is_enabled = enabled;
		
		return( result );
	}

	private long
	parseRate(
		String	str )
	{
		str = str.toLowerCase( Locale.US );

		int	pos = str.indexOf( "/" );

		if ( pos != -1 ){

			str = str.substring( 0, pos ).trim();
		}

		String	 	num 	= "";
		long		mult	= 1;

		for ( int i=0;i<str.length();i++){

			char c = str.charAt(i);

			if ( Character.isDigit( c ) || c == '.' ){

				num += c;

			}else{

				if ( c == 'k' ){

					mult = 1024;

				}else if ( c == 'm' ){

					mult = 1024*1024;

				}else if ( c == 'g' ){

					mult = 1024*1024*1024L;
				}

				break;
			}
		}

		if ( num.contains( "." )){

			return((long)( Float.parseFloat( num ) * mult ));

		}else{

			return( Integer.parseInt( num ) * mult );
		}
	}
	
	public boolean
	isEnabled()
	{
		return ( is_enabled );
	}

	public void
	addConfigLine(
		String		line,
		boolean		auto_enable )
	{
		List<String> lines = getSchedule();
		
		if ( !is_enabled && auto_enable ){
		
			lines.add( "enable=yes" );
		}
		
		lines.add( line );
		
		setSchedule( lines );
	}
	
	public List<PeerSet>
	getPeerSets()
	{
		synchronized( SpeedLimitHandler.this ){
			
			return( new ArrayList<>(  current_ip_sets.values()));
		}
	}
	private int
	getMins(
		String	str )
	{
		try{
			String[]	bits = str.split( ":" );

			if ( bits.length == 2 ){

				return( Integer.parseInt(bits[0].trim())*60 + Integer.parseInt(bits[1].trim()));
			}
		}catch( Throwable e ){
		}

		return( -1 );
	}

	private DML current_dml;

	private static final Object	ip_set_peer_key = new Object();

	private final FrequencyLimitedDispatcher check_ip_sets_limiter = new FrequencyLimitedDispatcher(
			new AERunnable() {
				@Override
				public void runSupport() {
					checkIPSetsSupport();
				}
			}, 500 );

	{
		check_ip_sets_limiter.setSingleThreaded();
	}

	private final FrequencyLimitedDispatcher auto_peer_set_checker = new FrequencyLimitedDispatcher(
			new AERunnable() {
				
				AsyncDispatcher dispatcher = new AsyncDispatcher();
				
				@Override
				public void runSupport(){
					
					dispatcher.dispatch( AERunnable.create(
						()->{
							List<String>	todo_clients;
							List<String>	todo_intf;
							
							synchronized( auto_peer_set_queue_client ){
							
								todo_clients = new ArrayList<>( auto_peer_set_queue_client );
								
								auto_peer_set_queue_client.clear();
							}
							
							synchronized( auto_peer_set_queue_intf ){
								
								todo_intf = new ArrayList<>( auto_peer_set_queue_intf );
								
								auto_peer_set_queue_intf.clear();
							}

							
							if ( todo_clients.isEmpty() && todo_intf.isEmpty()){
								
								return;
							}
							
							synchronized( SpeedLimitHandler.this ){
		
								Map<String,PeerSet>	added = new HashMap<>();
								
								for ( String name: todo_clients ){
								
									if ( name.isEmpty()){
										
										name = "?";
										
									}else{
										
										char c = name.charAt(0);
										
											// stupid micro lower/upper
										
										if ( c == '\u03bc' ){
											
											name = '\u00B5' + name.substring( 1 );
										}
									}
									
									String set_name = MessageText.getString( "Peers.column.client" ) + "_" + name;
									
									PeerSet set = current_ip_sets.get( set_name );
									
									if ( set == null ){
																														
										set = new PeerSet( set_name );
										
										try{
											Pattern pattern = Pattern.compile( "^" + Pattern.quote(name) + ".*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE  );
											
											set.setParameters( false, -1, -1, 0, 0, new HashSet<String>(), pattern, false, null, false, null, false, null );
											
											set.addCIDRorCCetc( "all" );
												
											set.setGroup( MessageText.getString( "Peers.column.client" ) + "_" + MessageText.getString( "wizard.maketorrent.auto" ));
											
											added.put( set_name, set );
																						
										}catch( Throwable e ){
											
											Debug.out( e );
										}
									}
								}
								
								for ( String name: todo_intf ){
									
									if ( name.isEmpty()){
										
										name = "?";
									}
									
									String set_name = MessageText.getString( "label.interface.short" ) + "_" + name;
									
									PeerSet set = current_ip_sets.get( set_name );
									
									if ( set == null ){
																														
										set = new PeerSet( set_name );
										
										try{
											Pattern pattern = Pattern.compile( "^" + Pattern.quote(name) + ".*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE  );
											
											set.setParameters( false, -1, -1, 0, 0, new HashSet<String>(), null, false, pattern, false, null, false, null );
											
											set.addCIDRorCCetc( "all" );
												
											set.setGroup( MessageText.getString( "label.interface.short" ) + "_" + MessageText.getString( "wizard.maketorrent.auto" ));
											
											added.put( set_name, set );
																						
										}catch( Throwable e ){
											
											Debug.out( e );
										}
									}
								}
								
								if ( !added.isEmpty()){
									
									initialiseIPSets( added );
									
									checkIPSets();
								}
							}
						}));
				}
			}, 2000 );

	
	private void
	checkIPSets()
	{
		check_ip_sets_limiter.dispatch();
	}

	private void
	initialiseIPSets(
		Map<String,PeerSet>		sets )
	{
		String config_max_key = "speed.limit.handler.ipset_n.max";
		
		Set<Integer>			id_allocated	= new HashSet<>();
		Map<PeerSet,Integer>	id_map 			= new HashMap<>();
		int						id_max			= COConfigurationManager.getIntParameter( config_max_key, -1 );

		int	original_id_max = id_max;
		
		for ( int i=0; i<2; i++ ){
			
			for ( PeerSet s: i==0?current_ip_sets.values():sets.values()){
					
				String name = s.getName();
	
				try{
					String config_key = "speed.limit.handler.ipset_n." + Base32.encode( name.getBytes( "UTF-8" ));
	
					int existing = COConfigurationManager.getIntParameter( config_key, -1 );
					
					if ( existing != -1 ){
						
						if ( id_allocated.contains( existing )){
							
							COConfigurationManager.removeParameter( config_key );	// clash
							
						}else{
							
							id_allocated.add( existing );
							
							id_map.put( s, existing );
	
							id_max = Math.max( id_max, existing );
						}
					}
				}catch( Throwable e ){
	
					Debug.out( e );
				}
			}
		}
			
		for ( PeerSet s: sets.values()){

			String name = s.getName();

			try{
				String config_key = "speed.limit.handler.ipset_n." + Base32.encode( name.getBytes( "UTF-8" ));

				Integer tag_id = id_map.get( s );

				if ( tag_id == null ){

					tag_id = ++id_max;

					COConfigurationManager.setParameter( config_key, tag_id );
				}

				s.initialise( tag_id );
						
				current_ip_sets.put( s.getName(), s );
					
			}catch( Throwable e ){

				Debug.out( e );
			}
		}
		
		if ( id_max > original_id_max ){
			
			COConfigurationManager.setParameter( config_max_key, id_max );
			
			COConfigurationManager.setDirty();
		}
	}
	
	private synchronized void
	checkIPSetsSupport()
	{
		final com.biglybt.pif.download.DownloadManager download_manager = plugin_interface.getDownloadManager();

			// first off kill any existing download manager listener so that any peers that
			// may happen to to get added while we're working through this stuff don't sneak in and
			// get allocated to rate limiters incorrectly

		if ( current_dml != null ){

			current_dml.destroy();

			current_dml = null;
		}

		Download[] downloads = download_manager.getDownloads();

		for ( Download dm: downloads ){

			if ( dm.getFlag( Download.FLAG_METADATA_DOWNLOAD )){

				continue;
			}
			
			PeerManager pm = dm.getPeerManager();

			if ( pm != null ){

				Peer[] peers = pm.getPeers();

				for ( Peer peer: peers ){

					RateLimiter[] lims = peer.getRateLimiters( false );

					for ( RateLimiter l: lims ){

						if ( ip_set_rate_limiters_down.containsValue( l )){

							synchronized( RL_TO_BE_REMOVED_LOCK ){

								List<RateLimiter> to_be_removed = (List<RateLimiter>)peer.getUserData( RLD_TO_BE_REMOVED_KEY );

								if ( to_be_removed == null ){

									to_be_removed = new ArrayList<>();

									peer.setUserData( RLD_TO_BE_REMOVED_KEY, to_be_removed );
								}

								to_be_removed.add( l );
							}

							// defer as removing the rate limiter and then re-adding it gives time for
							// quite a lot to happen in between

							// peer.removeRateLimiter( l , false );
						}
					}

					lims = peer.getRateLimiters( true );

					for ( RateLimiter l: lims ){

						if ( ip_set_rate_limiters_up.containsValue( l )){

							synchronized( RL_TO_BE_REMOVED_LOCK ){

								List<RateLimiter> to_be_removed = (List<RateLimiter>)peer.getUserData( RLU_TO_BE_REMOVED_KEY );

								if ( to_be_removed == null ){

									to_be_removed = new ArrayList<>();

									peer.setUserData( RLU_TO_BE_REMOVED_KEY, to_be_removed );
								}

								to_be_removed.add( l );
							}

							// peer.removeRateLimiter( l , true );
						}
					}
				}
			}
		}

		ip_set_rate_limiters_down.clear();
		ip_set_rate_limiters_up.clear();

		Set<String>	has_cats_or_tags = new HashSet<>();

		for ( PeerSet set: current_ip_sets.values()){

			ip_set_rate_limiters_down.put( set.getName(), set.getDownLimiter());

			ip_set_rate_limiters_up.put( set.getName(), set.getUpLimiter());

			Set<String> cot = set.getCategoriesOrTags();
			
			if ( cot != null ){

				has_cats_or_tags.addAll( cot );
			}

			set.removeAllPeers();
		}

		if ( current_ip_sets.size() == 0 ){

			if ( ip_set_event != null ){

				ip_set_event.cancel();

				ip_set_event = null;

			}
		}else{

			if ( ip_set_event == null ){

				ip_set_event =
					SimpleTimer.addPeriodicEvent(
						"speed handler ip set scheduler",
						1000,
						new TimerEventPerformer()
						{
							private int	tick_count;

							@Override
							public void
							perform(
								TimerEvent event)
							{
								tick_count++;

								synchronized( SpeedLimitHandler.this ){

									for ( PeerSet set: current_ip_sets.values()){

										set.updateStats( tick_count );
									}

									/*
									if ( tick_count % 30 == 0 ){

										String str = "";

										for ( IPSet set: current_ip_sets.values()){

											str += (str.length()==0?"":", ") + set.getString();
										}

										logger.log( str );
									}
									*/
								}
							}
						});
			}

			current_dml = new DML( download_manager, has_cats_or_tags );
		}
	}

	private class
	DML
		implements DownloadManagerListener
	{
		private final Object		lock = SpeedLimitHandler.this;

		private final com.biglybt.pif.download.DownloadManager		download_manager;
		private final Set<String>									has_cats_or_tags;

		final List<Runnable>	listener_removers = new ArrayList<>();

		private volatile boolean	destroyed;

		private
		DML(
			com.biglybt.pif.download.DownloadManager		_download_manager,
			Set<String>										_has_cats_or_tags )
		{
			download_manager	= _download_manager;
			has_cats_or_tags	= _has_cats_or_tags;

			download_manager.addListener( this, true );
		}

		private void
		destroy()
		{
			synchronized( lock ){

				destroyed	= true;

				download_manager.removeListener( this );

				for ( Runnable r: listener_removers ){

					try{
						r.run();

					}catch( Throwable e ){

						Debug.out( e );
					}
				}

				listener_removers.clear();
			}
		}

		@Override
		public void
		downloadAdded(
			final Download	download )
		{
			if ( download.getFlag( Download.FLAG_METADATA_DOWNLOAD )){
				
				return;
			}
			
			synchronized( lock ){

				if ( destroyed ){

					return;
				}

				if ( !has_cats_or_tags.isEmpty()){

						// attribute listener
					
					final DownloadAttributeListener attr_listener = new
							DownloadAttributeListener()
							{
								String current_cat = download.getAttribute( category_attribute );

								@Override
								public void
								attributeEventOccurred(
									Download 			download,
									TorrentAttribute 	attribute,
									int 				event_type )
								{
									String old_cat = current_cat;
									
									current_cat = download.getAttribute( category_attribute );
									
									if ( has_cats_or_tags.contains( old_cat ) || has_cats_or_tags.contains( current_cat )){
									
										checkIPSets();
									}
								}
							};


						// tag listener

					final TagType tt = TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_MANUAL );

					final DownloadManager core_download = PluginCoreUtils.unwrap( download );

					final TagListener tag_listener =
						new TagListener() {

							@Override
							public void
							taggableSync(
								Tag tag )
							{
							}

							@Override
							public void
							taggableRemoved(
								Tag 		tag,
								Taggable 	tagged)
							{
								if ( has_cats_or_tags.contains( tag.getTagName( true ))){
								
									checkIPSets();
								}
							}

							@Override
							public void
							taggableAdded(
								Tag 		tag,
								Taggable 	tagged)
							{
								if ( has_cats_or_tags.contains( tag.getTagName( true ))){
								
									checkIPSets();
								}
							}
						};

						download.addAttributeListener( attr_listener, category_attribute, DownloadAttributeListener.WRITTEN );

						tt.addTagListener( core_download, tag_listener );

						listener_removers.add(
							new Runnable(){
								@Override
								public void run()
								{
									download.removeAttributeListener( attr_listener, category_attribute, DownloadAttributeListener.WRITTEN );

									tt.removeTagListener( core_download, tag_listener );
								}});
					}

					// peer listener

				final DownloadPeerListener	peer_listener =
					new DownloadPeerListener()
					{
						private Runnable 	pm_listener_remover;
						private PeerManager	current_pm;
						
						@Override
						public void
						peerManagerAdded(
							final Download			download,
							final PeerManager		peer_manager )
						{

							synchronized( lock ){

								if ( destroyed ){

									return;
								}

								final PeerManagerListener2 listener =
									new PeerManagerListener2()
									{
										@Override
										public void
										eventOccurred(
											PeerManagerEvent	event )
										{
											if ( destroyed ){

												return;
											}

											if ( event.getType() == PeerManagerEvent.ET_PEER_ADDED ){

												peersAdded( download, peer_manager, new Peer[]{ event.getPeer() });

											}else if ( event.getType() == PeerManagerEvent.ET_PEER_REMOVED ){

												peerRemoved( download, peer_manager, event.getPeer());
											}
										}
									};

								peer_manager.addListener( listener );

								if ( pm_listener_remover != null ){
									
									Debug.out( "Old listener still active" );
								}
								
								current_pm = peer_manager;
								
								pm_listener_remover =
									new Runnable(){
										@Override
										public void run()
										{
											peer_manager.removeListener( listener );
										}};

								listener_removers.add( pm_listener_remover );
							}

							Peer[] peers = peer_manager.getPeers();

							peersAdded( download, peer_manager, peers );
						}

						@Override
						public void
						peerManagerRemoved(
							Download		download,
							PeerManager		peer_manager )
						{
							synchronized( lock ){ 

								if ( peer_manager != current_pm ){
									
									Debug.out( "PM mismatch: " + current_pm + "/" + peer_manager );
								}
								
								current_pm = null;
								
								if ( listener_removers.remove( pm_listener_remover  )){

									pm_listener_remover.run();
									
									pm_listener_remover = null;
								}
							}
						}
					};

				download.addPeerListener( peer_listener );

				listener_removers.add(
					new Runnable(){
						@Override
						public void run()
						{
							download.removePeerListener( peer_listener );
						}
					});
			}
		}


		@Override
		public void
		downloadRemoved(
			Download	download )
		{
		}
	}


	private void
	peersAdded(
		Download	download,
		PeerManager	peer_manager,
		Peer[]		peers )
	{
		PeerSet[]		sets;
		long[][][]	set_ranges;
		Set[]		set_ccs;
		Set[]		set_nets;

		boolean	has_ccs 	= false;
		boolean	has_nets 	= false;

		Set<String>	category_or_tags = null;

		TagManager tm = TagManagerFactory.getTagManager();

		synchronized( this ){

			int	len = current_ip_sets.size();

			sets 		= new PeerSet[len];
			set_ranges	= new long[len][][];
			set_ccs		= new Set[len];
			set_nets	= new Set[len];

			int	pos = 0;

			for ( PeerSet set: current_ip_sets.values()){

				sets[pos]		= set;
				set_ranges[pos]	= set.getRanges();
				set_ccs[pos]	= set.getCountryCodes();
				set_nets[pos]	= set.getNetworks();

				if ( set_ccs[pos].size() > 0 ){

					has_ccs = true;
				}

				if ( set_nets[pos].size() > 0 ){

					has_nets = true;
				}

				pos++;

				if ( category_or_tags == null && set.getCategoriesOrTags() != null ){

					category_or_tags = new HashSet<>();

					String cat = download.getAttribute( category_attribute );

					if ( cat != null && cat.length() > 0 ){

						category_or_tags.add( cat );
					}

					List<Tag> tags = tm.getTagsForTaggable( TagType.TT_DOWNLOAD_MANUAL, PluginCoreUtils.unwrap( download ));

					for ( Tag t: tags ){

						category_or_tags.add( t.getTagName( true ));
					}
				}
			}
		}

		if ( sets.length == 0 ){

			return;
		}

		for ( Peer peer: peers ){

			List<RateLimiter>	rlu_tbr;
			List<RateLimiter>	rld_tbr;

			synchronized( RL_TO_BE_REMOVED_LOCK ){

				rlu_tbr = (List<RateLimiter>)peer.getUserData( RLU_TO_BE_REMOVED_KEY );
				rld_tbr = (List<RateLimiter>)peer.getUserData( RLD_TO_BE_REMOVED_KEY );

				if ( rlu_tbr != null ){
					peer.setUserData( RLU_TO_BE_REMOVED_KEY, null );
				}
				if ( rld_tbr != null ){
					peer.setUserData( RLD_TO_BE_REMOVED_KEY, null );
				}
			}

			try{
				long[] entry = (long[])peer.getUserData( ip_set_peer_key );

				long	l_address;

				if ( entry == null ){

					l_address = 0;

					String ip = peer.getIp();

					if ( !ip.contains( ":" )){

						byte[] bytes = HostNameToIPResolver.hostAddressToBytes( ip );

						if ( bytes != null ){

							l_address = ((long)((bytes[0]<<24)&0xff000000 | (bytes[1] << 16)&0x00ff0000 | (bytes[2] << 8)&0x0000ff00 | bytes[3]&0x000000ff))&0xffffffffL;

						}
					}

					entry = new long[]{ l_address };

					peer.setUserData( ip_set_peer_key, entry );

				}else{

					l_address = entry[0];
				}

				String	peer_cc 	= null;
				String 	peer_net	= null;

				if ( has_ccs ){

					String[] details = PeerUtils.getCountryDetails( peer );

					if ( details != null && details.length > 0 ){

						peer_cc = details[0];
						
					}else{
						
						peer_cc = "??";
					}
				}

				if ( has_nets ){

					peer_net = AENetworkClassifier.categoriseAddress( peer.getIp());
				}

				Set<PeerSet>	added_to_sets = new HashSet<>();

				if ( l_address != 0 ){

					for ( int i=0;i<set_ranges.length;i++ ){

						long[][] ranges = set_ranges[i];

						if ( ranges.length == 0 ){

							continue;
						}

						PeerSet set = sets[i];

						boolean is_inverse = set.isInverse();

						Set<String> set_cats_or_tags = set.getCategoriesOrTags();

						if ( set_cats_or_tags == null || new HashSet<>(set_cats_or_tags).removeAll( category_or_tags )){

							boolean	hit = false;

							for ( long[] range: ranges ){

								if ( l_address >= range[0] && l_address <= range[1] ){

									hit	= true;

									if ( !is_inverse ){

										addLimiters( peer_manager, peer, set, rlu_tbr, rld_tbr );

										added_to_sets.add( set );
									}

									break;
								}
							}

							if ( is_inverse && !hit ){

								addLimiters( peer_manager, peer, set, rlu_tbr, rld_tbr );

								added_to_sets.add( set );
							}
						}
					}
				}

				if ( peer_cc != null ){

					for ( int i=0;i<set_ccs.length;i++ ){

						PeerSet set = sets[i];

						if ( added_to_sets.contains( set )){

							continue;
						}

						Set<String>	ccs = set_ccs[i];

						if ( ccs.size() == 0 ){

							continue;
						}

						boolean not_inverse = !set.isInverse();

						Set<String> set_cats_or_tags = set.getCategoriesOrTags();

						if ( set_cats_or_tags == null || new HashSet<>(set_cats_or_tags).removeAll( category_or_tags )){

							boolean	hit = ccs.contains( peer_cc );

							if ( hit == not_inverse ){

								addLimiters( peer_manager, peer, set, rlu_tbr, rld_tbr );

								added_to_sets.add( set );
							}
						}
					}
				}

				if ( peer_net != null ){

					String	pub_peer_net 	= null;
					String	pub_lan			= null;

					if ( peer_net == AENetworkClassifier.AT_PUBLIC ){

						try{
							byte[]	address = InetAddress.getByName( peer.getIp() ).getAddress();

							pub_peer_net = address.length==4?NET_IPV4:NET_IPV6;

						}catch( Throwable e ){

						}

						if ( peer.isLANLocal()){

							pub_lan = NET_LAN;

						}else{

							pub_lan = NET_WAN;
						}
					}

					for ( int i=0;i<set_nets.length;i++ ){

						PeerSet set = sets[i];

						if ( added_to_sets.contains( set )){

							continue;
						}

						Set<String>	nets = set_nets[i];

						if ( nets.size() == 0 ){

							continue;
						}

						boolean not_inverse = !set.isInverse();

						Set<String> set_cats_or_tags = set.getCategoriesOrTags();

						if ( set_cats_or_tags == null || new HashSet<>(set_cats_or_tags).removeAll( category_or_tags )){

							boolean	hit = nets.contains( peer_net );

							if ( !hit ){

								if ( pub_peer_net != null ){

									hit = nets.contains( pub_peer_net );
								}

								if ( !hit && pub_lan != null ){

									hit = nets.contains( pub_lan );
								}
							}

							if ( hit == not_inverse ){

								addLimiters( peer_manager, peer, set, rlu_tbr, rld_tbr );

								added_to_sets.add( set );
							}
						}
					}
				}
			}finally{

				if ( rlu_tbr != null ){

					for ( RateLimiter l: rlu_tbr ){

						peer.removeRateLimiter( l, true );
					}
				}

				if ( rld_tbr != null ){

					for ( RateLimiter l: rld_tbr ){

						peer.removeRateLimiter( l, false );
					}
				}
			}
		}
	}

	private void
	peerRemoved(
		Download	download,
		PeerManager	peer_manager,
		Peer		peer )
	{
		Collection<PeerSet> sets;

		synchronized( this ){

			if ( current_ip_sets.size() == 0 ){

				return;
			}

			sets = current_ip_sets.values();
		}

		for ( PeerSet s: sets ){

			s.removePeer( peer_manager, peer );
		}
	}

	private void
	addLimiters(
		PeerManager			peer_manager,
		Peer				peer,
		PeerSet				set,
		List<RateLimiter>	up_to_be_removed,
		List<RateLimiter>	down_to_be_removed )
	{
		boolean	matched = false;

		{
			RateLimiter l = set.getUpLimiter();

			RateLimiter[] existing = peer.getRateLimiters( true );

			boolean found = false;

			for ( RateLimiter e: existing ){

				if ( e == l ){

					found = true;

					break;
				}
			}

			if ( found ){

				if ( up_to_be_removed != null && up_to_be_removed.remove( l )){

						// supposed to have been removed but is still required

					matched = true;
				}
			}else{

				peer.addRateLimiter( l, true );

				matched = true;
			}
		}

		{
			RateLimiter l = set.getDownLimiter();

			RateLimiter[] existing = peer.getRateLimiters( false );

			boolean found = false;

			for ( RateLimiter e: existing ){

				if ( e == l ){

					found = true;

					break;
				}
			}

			if ( found ){

				if ( down_to_be_removed != null && down_to_be_removed.remove( l )){

					matched = true;
				}
			}else{

				peer.addRateLimiter( l, false );

				matched = true;
			}
		}

		if ( matched ){

			set.addPeer( peer_manager, peer );
		}

		int	peer_up = set.getPeerUpLimit();

		if ( peer_up > 0 ){

			peer.getStats().setUploadRateLimit( peer_up );
		}

		int	peer_down = set.getPeerDownLimit();

		if ( peer_down > 0 ){

			peer.getStats().setDownloadRateLimit( peer_down );
		}
	}

	private void
	checkPrioritisers()
	{
		List<Prioritiser>	prioritisers;

		synchronized( this ){

			prioritisers = new ArrayList<>(current_prioritisers);
		}

		synchronized( extensions_lock ){

			for ( Prioritiser p: prioritisers ){

				p.check();
			}
		}
	}

	private ScheduleRule
	getActiveRule(
		Date		date )
	{
		Calendar cal = new GregorianCalendar();

		cal.setTime( date );

		int	day_of_week = cal.get( Calendar.DAY_OF_WEEK );
		int	hour_of_day	= cal.get( Calendar.HOUR_OF_DAY );
		int min_of_hour	= cal.get( Calendar.MINUTE );

		int	day = -1;

		switch( day_of_week ){
		case Calendar.MONDAY:
			day = ScheduleRule.FR_MON;
			break;
		case Calendar.TUESDAY:
			day = ScheduleRule.FR_TUE;
			break;
		case Calendar.WEDNESDAY:
			day = ScheduleRule.FR_WED;
			break;
		case Calendar.THURSDAY:
			day = ScheduleRule.FR_THU;
			break;
		case Calendar.FRIDAY:
			day = ScheduleRule.FR_FRI;
			break;
		case Calendar.SATURDAY:
			day = ScheduleRule.FR_SAT;
			break;
		case Calendar.SUNDAY:
			day = ScheduleRule.FR_SUN;
			break;
		}

		int	min_of_day = hour_of_day * 60 + min_of_hour;

		ScheduleRule latest_match = null;

		for ( ScheduleRule main_rule: current_rules ){

			List<ScheduleRule>	sub_rules = main_rule.splitByDay();

			for ( ScheduleRule rule: sub_rules ){

				if (( rule.frequency & day ) == 0 ){

					continue;
				}

				if (	rule.from_mins <= min_of_day &&
						rule.to_mins >= min_of_day ){

					latest_match = main_rule;
				}
			}
		}

		return( latest_match );
	}

	private void
	setProfileActive(
		String		profile_name )
	{
		int	active_state = getActiveState();
				
		if ( profile_name == null ){
			
			if ( preserve_inactive_limits && profileExists( INACTIVE_PROFILE_NAME )){
				
				if ( active_state == AS_ACTIVE ){
				
						// active -> inactive
				
					if ( rule_pause_all_active ){
	
						setRulePauseAllActive( false );
					}
				}
				
				loadProfile( INACTIVE_PROFILE_NAME );
								
			}else{
				
				resetRules();
			}
			
			setActiveState( AS_INACTIVE );

		}else{
			
			if ( active_state == AS_INACTIVE && preserve_inactive_limits ){
				
					// inactive -> active 
				
				saveProfile( INACTIVE_PROFILE_NAME );				
			}
			
			loadProfile( profile_name );
			
			setActiveState( AS_ACTIVE );
		}
	}
	
	private void
	checkSchedule(
		boolean	start_of_day,
		int		tick_count )
	{
		GlobalManager gm = core.getGlobalManager();

		ScheduleRule	current_rule;

		synchronized( this ){

			current_rule = active_rule;

			ScheduleRule latest_match = getActiveRule( new Date());

			if ( latest_match == null ){

				active_rule = null;

				if ( start_of_day || current_rule != null ){

					setProfileActive( null );
				}
			}else{

				String	profile_name = latest_match.profile_name;

				boolean is_rule_pause_all = false;

				if ( active_rule == null || !active_rule.sameAs( latest_match )){

					String lc_profile_name = profile_name.toLowerCase();

					if ( predefined_profile_names.contains( lc_profile_name)){

						if ( lc_profile_name.equals( "pause_all" )){

							active_rule = latest_match;

							is_rule_pause_all = true;

							setRulePauseAllActive( true );

						}else if ( lc_profile_name.equals( "resume_all" )){

							active_rule = latest_match;

							setRulePauseAllActive( false );

						}else if ( lc_profile_name.equals( "null" )){

							active_rule = latest_match;

						}else{

							Debug.out( "Unknown pre-def name '" + profile_name + "'" );
						}

					}else if ( profileExists( profile_name )){

						active_rule = latest_match;

						setProfileActive( profile_name );

					}else if ( active_rule != null ){

						active_rule = null;

						setProfileActive( null );
					}
				}else{

					active_rule = latest_match;	// must update because might have reloaded and the old rule may reference old stuff (e.g. old net limit)

					is_rule_pause_all = rule_pause_all_active;	// same rule as before
				}

				if ( rule_pause_all_active ){

					if ( !is_rule_pause_all ){

						setRulePauseAllActive( false );

					}else{

						if ( gm.canPauseDownloads( pause_forced_downloads )){

							gm.pauseDownloads( pause_forced_downloads );
						}
					}
				}
			}
		}

			// default is for everything to be enabled, extension can then disable if needed

		synchronized( extensions_lock ){

			prioritiser_enabled = true;

			for ( NetLimit n: net_limits_all ){

				n.setEnabled( true );
			}

			if ( active_rule != null ){

				active_rule.checkExtensions();
			}
		}
		
			// net_limit_pause_all_active overrides any tag based limits

		if ( !net_limit_pause_all_active && net_limits_all.size() > 0 ){

			checkTagNetLimits( tick_count );
		}

		if ( 	( current_rule != active_rule && net_limits_all.size() > 0 ) ||
				net_limit_pause_all_active ){

				// net_limits can depend on the active rule, recalc

				// also have to periodically recheck stats if everything's paused as the stats won't in general
				// naturally update and trigger an update that way...

			updated( StatsFactory.getLongTermStats());
		}

		if ( net_limit_pause_all_active ){

			if ( gm.canPauseDownloads( pause_forced_downloads )){

				gm.pauseDownloads( pause_forced_downloads );
			}
		}
	}

	public List<String>
	getSchedule()
	{
		List<String>	result = new ArrayList<>();

		result.add( "# Enter rules on separate lines below this section - see " + Wiki.SPEED_LIMIT_SCHEDULER + " for more details" );
		result.add( "# Rules are of the following types:" );
		result.add( "#    enable=(yes|no)   - controls whether the entire schedule is enabled or not (default=yes)" );
		result.add( "#    preserve_inactive_limits=(yes|no) - save existing limits when activating and reinstate on deactivation (default=no)" );
		result.add( "#    pause_force_downloads=(yes|no) - when pausing downloads also pause force-start ones (default=yes)" );
		result.add( "#    <frequency> <profile_name> from <time> to <time> [extension]*" );
		result.add( "#        frequency: daily|weekdays|weekends|<day_of_week>" );
		result.add( "#            day_of_week: mon|tue|wed|thu|fri|sat|sun" );
		result.add( "#        time: hh:mm - 24 hour clock; 00:00=midnight; local time" );
		result.add( "#        extension: (start_tag|stop_tag|pause_tag|resume_tag):<tag_name> (enable_priority|disable_priority)" );
		result.add( "#    peer_set <set_name>=[<CIDR_specs...>|CC list|Network List|<prior_set_name>] [,inverse=[yes|no]] [,up=<limit>] [,down=<limit>] [peer_up=<limit>] [peer_down=<limit>] [,cat=<cat names>] [,tag=<tag names>] [,client=<regular expression>|auto] [,intf=<regular expression>|auto] [,asn=<regular expression>]" );
		result.add( "#    net_limit (hourly|daily|weekly|monthly)[(:<profile>|$<tag>)] [total=<limit>] [up=<limit>] [down=<limit>]");
		result.add( "#    priority_(up|down) <id>=<tag_name> [,<id>=<tag_name>]+ [,freq=<secs>] [,max=<limit>] [,probe=<cycles>]" );
		result.add( "#" );
		result.add( "# For example - assuming there are profiles called 'no_limits' and 'limited_upload' defined:" );
		result.add( "#" );
		result.add( "#     daily no_limits from 00:00 to 23:59" );
		result.add( "#     daily limited_upload from 06:00 to 22:00 stop_tag:bigstuff" );
		result.add( "#     daily pause_all from 08:00 to 17:00" );
		result.add( "#" );
		result.add( "#     net_limit monthly total=250G          // flat montly limit" );
		result.add( "#" );
		result.add( "#     net_limit monthly:no_limits                  // no monthly limit when no_limits active" );
		result.add( "#     net_limit monthly:limited_upload total=100G  // 100G a month limit when limited_upload active" );
		result.add( "#" );
		result.add( "#     peer_set external=211.34.128.0/19 211.35.128.0/17" );
		result.add( "#     peer_set Europe=EU;AD;AL;AT;BA;BE;BG;BY;CH;CS;CZ;DE;DK;EE;ES;FI;FO;FR;FX;GB;GI;GR;HR;HU;IE;IS;IT;LI;LT;LU;LV;MC;MD;MK;MT;NL;NO;PL;PT;RO;SE;SI;SJ;SK;SM;UA;VA" );
		result.add( "#     peer_set Blorp=Europe;US" );
		result.add( "#     peer_set BiglyBTPeers=Public;I2P;Tor,client=BiglyBT.*" );
		result.add( "#     peer_set AutoIntf=all,intf=auto" );
		result.add( "#" );
		result.add( "# When multiple rules apply the one further down the list of rules take precedence" );
		result.add( "# Currently peer_set limits are not schedulable" );
		result.add( "# Comment lines are prefixed with '#'" );
		result.add( "# Pre-defined profiles: " + predefined_profile_names );


		List<String> profiles = getProfileNames();

		if ( profiles.size() == 0 ){

			result.add( "# No user profiles currently defined." );

		}else{

			String	str = "";

			for( String s: profiles ){
				str += (str.length()==0?"":", ") + s;
			}

			result.add( "# Current profiles details:" );
			result.add( "#     defined: " + str );

			ScheduleRule	current_rule;

			synchronized( this ){

				current_rule = active_rule;
			}

			result.add( "#     active: " + (current_rule==null?"none":current_rule.profile_name ));
		}

		result.add( "# ---- Do not edit this line or any text above! ----" );

		List lines_list = COConfigurationManager.getListParameter( "speed.limit.handler.schedule.lines", new ArrayList());

		List<String> schedule_lines = BDecoder.decodeStrings(BEncoder.cloneList(lines_list) );

		if ( schedule_lines.size() == 0 ){

			schedule_lines.add( "" );
			schedule_lines.add( "" );

		}else{

			for ( String l: schedule_lines ){

				result.add( l.trim());
			}
		}

		return( result );
	}

	public List<String>
	setSchedule(
		List<String>		lines )
	{
		int	trim_from = 0;

		for ( int i=0; i<lines.size(); i++ ){

			String	line = lines.get( i );

			if ( line.startsWith( "# ---- Do not edit" )){

				trim_from = i+1;
			}
		}

		if ( trim_from > 0 ){

			lines = lines.subList( trim_from, lines.size());
		}

		COConfigurationManager.setParameter( "speed.limit.handler.schedule.lines", lines );

		COConfigurationManager.save();

		return( loadSchedule( false ));
	}

	private List<LimitedRateGroup>
	trim(
		LimitedRateGroup[]	groups )
	{
		List<LimitedRateGroup> result = new ArrayList<>();

		for ( LimitedRateGroup group: groups ){

			if ( group instanceof UtilitiesImpl.PluginLimitedRateGroup ){

				result.add( group );
			}
		}

		return( result );
	}

	@Override
	public void
	updated(
		LongTermStats stats )
	{
		boolean exceeded = false;

		for ( NetLimit limit: net_limits_all ){

			LongTermStats net_lts = limit.getLongTermStats();

			if ( net_lts != null ){

				continue;
			}

			String profile = limit.getProfile();

			if ( 	profile != null &&
					( active_rule == null || !active_rule.profile_name.equals( profile ))){

				continue;
			}

			long[] usage = getLongTermUsage( stats, limit.getType(), limit );

			long total_up = usage[LongTermStats.ST_PROTOCOL_UPLOAD] + usage[LongTermStats.ST_DATA_UPLOAD] + usage[LongTermStats.ST_DHT_UPLOAD];
			long total_do = usage[LongTermStats.ST_PROTOCOL_DOWNLOAD] + usage[LongTermStats.ST_DATA_DOWNLOAD] + usage[LongTermStats.ST_DHT_DOWNLOAD];

			long[]	limits = limit.getLimits();

			if ( limits[0] > 0 ){

				exceeded = total_up + total_do >= limits[0];
			}

			if ( limits[1] > 0 && !exceeded){

				exceeded = total_up >= limits[1];
			}

			if ( limits[2] > 0 && !exceeded){

				exceeded = total_do >= limits[2];
			}

			if ( exceeded ){

				break;
			}
		}

		if ( net_limit_pause_all_active != exceeded ){

			if ( exceeded ){
				
					// defer the pause until in a good state to do so
				
				if ( canPauseAll()){
			
					setNetLimitPauseAllActive( true );
				}
			}else{
				
				setNetLimitPauseAllActive( false );
			}
		}
	}

	private boolean 
	canPauseAll()
	{
		GlobalManager gm = core.getGlobalManager();
		
		for ( DownloadManager dm: gm.getDownloadManagers()){
			
			int state = dm.getState();

			if ( state == DownloadManager.STATE_STOPPED ){
				
				continue;
			}
			
			if ( dm.getMoveProgress() != null || FileUtil.hasTask( dm )){
				
				return( false );
			}
			

			if ( state == DownloadManager.STATE_CHECKING ){

				return( false );

			}else if ( state == DownloadManager.STATE_SEEDING ){

				DiskManager disk_manager = dm.getDiskManager();

				if ( disk_manager != null ){

					if ( disk_manager.getCompleteRecheckStatus() != -1 ){
						
						return( false );
					}
				}
			}
		}
		
		return( true );
	}
	
	private void
	checkTagNetLimits(
		int	tick_count )
	{
		boolean	do_log = tick_count % NETLIMIT_TAG_LOG_TICKS == 0;

		String log_str = "";

		Map<TagFeatureRunState,List<Object[]>>	rs_ops = new LinkedHashMap<>();
		Map<TagFeatureRateLimit,List<Object[]>>	rl_ops = new LinkedHashMap<>();

		Set<Taggable>	handled_dms = new IdentityHashSet<>();
		
		if ( !pause_forced_downloads ){
			
			for ( DownloadManager dm: core.getGlobalManager().getDownloadManagers()){
				
				if ( dm.isForceStart()){
					
					handled_dms.add( dm );
				}
			}
		}
		
		for ( NetLimit limit: net_limits_all ){

			String name_str = "net_limit";

			String name = limit.getName();

			if ( name.length() > 0 ){

				name_str += " " + name;
			}

			LongTermStats stats = limit.getLongTermStats();

			if ( stats == null ){

				continue;
			}

			TagFeatureRateLimit tag_rl = limit.getTag();

			Tag tag = tag_rl.getTag();

			long[] usage = getLongTermUsage( stats, limit.getType(), limit );

			long total_up = usage[LongTermStats.ST_PROTOCOL_UPLOAD] + usage[LongTermStats.ST_DATA_UPLOAD];
			long total_do = usage[LongTermStats.ST_PROTOCOL_DOWNLOAD] + usage[LongTermStats.ST_DATA_DOWNLOAD];

			boolean	enabled = limit.isEnabled();

			log_str += (log_str.length()==0?"":"; ") + (name.length()==0?"":(name + " " )) +
					tag.getTagName( true ) + ": up=" + DisplayFormatters.formatByteCountToKiBEtc( total_up ) +
					", down=" + DisplayFormatters.formatByteCountToKiBEtc( total_do ) + ", enabled=" + enabled;

			long[]	limits = limit.getLimits();

			boolean exceeded_up 	= false;
			boolean exceeded_down 	= false;

			boolean	handled = false;

			if ( enabled ){

				if ( limits[0] > 0 ){

					exceeded_up = exceeded_down = total_up + total_do >= limits[0];
				}

				if ( limits[1] > 0 && !exceeded_up){

					exceeded_up = total_up >= limits[1];
				}

				if ( limits[2] > 0 && !exceeded_down){

					exceeded_down = total_do >= limits[2];
				}

				if ( tag instanceof TagFeatureRunState ){

					TagFeatureRunState rs = (TagFeatureRunState)tag;

					if ( rs.hasRunStateCapability( TagFeatureRunState.RSC_PAUSE )){

							// if both are exceeded then we pause the downloads
							// otherwise we set the up/down limit to disabled in the code below
						
						boolean pause = exceeded_up && exceeded_down;

						int op = pause?TagFeatureRunState.RSC_PAUSE:TagFeatureRunState.RSC_RESUME;

						List<Object[]> list = rs_ops.get( rs );

						if ( list == null ){

							list = new ArrayList<>();

							rs_ops.put( rs, list );
						}

						list.add(
							new Object[]{
									name_str + " : " + (pause?"pausing":"resuming") + " tag " + tag.getTagName( true ),
									op });

						handled = pause;
					}
				}
			}

			if ( !handled ){

				int target_up 	= exceeded_up?-1:0;
				int target_down = exceeded_down?-1:0;

				List<Object[]> list = rl_ops.get( tag_rl );

				if ( list == null ){

					list = new ArrayList<>();

					rl_ops.put( tag_rl, list );
				}

				list.add(
					new Object[]{
							name_str + ": setting rates to " + format( target_up ) + "/" + format( target_down ) + " on tag " + tag.getTagName( true ),
							target_up, target_down });
			}
		}

		Predicate<Taggable> filter = taggable->!handled_dms.contains( taggable );
		
			// we want to process all pause ops before resume as pause overrides resume
		
		for ( int op_to_do: new int[]{ TagFeatureRunState.RSC_PAUSE, TagFeatureRunState.RSC_RESUME }){
			
			for ( Map.Entry<TagFeatureRunState,List<Object[]>> entry: rs_ops.entrySet()){
	
				TagFeatureRunState 	tag_rs 	= entry.getKey();
				List<Object[]>		details	= entry.getValue();
	
				int	selected_op = TagFeatureRunState.RSC_RESUME;
	
				String all_str = "";
	
				for ( Object[] detail: details ){
	
					String	str = (String)detail[0];
	
					all_str += (all_str.length()==0?"":";") + str;
	
					int		op	= (Integer)detail[1];
	
					if  ( op == TagFeatureRunState.RSC_PAUSE ){
	
						selected_op = TagFeatureRunState.RSC_PAUSE;
					}
				}
	
				if ( op_to_do == selected_op ){
					
					if ( selected_op == TagFeatureRunState.RSC_PAUSE ){
						
						Set<Taggable> dms = tag_rs.getTag().getTagged();
						
						for ( Taggable t: dms ){
							
							if (((DownloadManager)t).isPaused()){
								
								handled_dms.add( t );
							}
						}
					}
					
					boolean[] result = tag_rs.getPerformableOperations( new int[]{ selected_op }, filter );
		
					if ( result[0] ){
		
						logger.log( all_str );
		
						do_log = true;
		
						List<Taggable>	affected = tag_rs.performOperation( selected_op, filter );
						
						if ( selected_op == TagFeatureRunState.RSC_PAUSE ){
							
							handled_dms.addAll( affected );
						}
					}
				}
			}
		}
		
		for ( Map.Entry<TagFeatureRateLimit,List<Object[]>> entry: rl_ops.entrySet()){

			TagFeatureRateLimit 	tag_rl 	= entry.getKey();
			List<Object[]>			details	= entry.getValue();

			String all_str = "";

			int	selected_up 	= 0;
			int	selected_down 	= 0;

			for ( Object[] detail: details ){

				String	str = (String)detail[0];

				all_str += (all_str.length()==0?"":";") + str;

				int	up		= (Integer)detail[1];
				int	down	= (Integer)detail[2];

				if ( up == -1 ){

					selected_up = -1;
				}

				if ( down == -1 ){

					selected_down = -1;
				}
			}

			int up_lim	 = tag_rl.getTagUploadLimit();
			int down_lim = tag_rl.getTagDownloadLimit();

			if ( up_lim != selected_up || down_lim != selected_down ){

				logger.log( all_str );

				do_log = true;

				tag_rl.setTagUploadLimit( selected_up );

				tag_rl.setTagDownloadLimit( selected_down );
			}
		}

		if ( log_str.length() > 0 && do_log ){

			logger.log( "net_limit: current: " + log_str );
		}
	}

	private String
	formatUp(
		int	rate )
	{
		return( "Up=" + format( rate ));
	}

	private String
	formatDown(
		int	rate )
	{
		return( "Down=" + format( rate ));
	}

	private String
	format(
		int		rate )
	{
		if ( rate < 0 ){

			return( "Disabled" );

		}else if ( rate == 0 ){

			return( "Unlimited" );

		}else{

			return( DisplayFormatters.formatByteCountToKiBEtcPerSec( rate ));
		}
	}

	private String
	formatUp(
		List<LimitedRateGroup>	groups )
	{
		return( "Up=" + format( groups ));
	}

	private String
	formatDown(
		List<LimitedRateGroup>	groups )
	{
		return( "Down=" + format( groups ));
	}

	private String
	format(
		List<LimitedRateGroup>	groups )
	{
		String str = "";

		for ( LimitedRateGroup group: groups ){

			str += (str.length()==0?"":", ") + group.getName() + ":" + format( group.getRateLimitBytesPerSecond());
		}

		return( str );
	}

    private void
    exportBoolean(
    	Map<String,Object>	map,
    	String				key,
    	boolean				b )
    {
    	map.put( key, Long.valueOf(b?1:0));
    }

    private boolean
    importBoolean(
    	Map<String,Object>	map,
    	String				key )
    {
    	Long	l = (Long)map.get( key );

    	if ( l != null ){

    		return( l == 1 );
    	}

    	return( false );
    }

    private void
    exportInt(
    	Map<String,Object>	map,
    	String				key,
    	int					i )
    {
    	map.put( key, new Long( i ));
    }

    private int
    importInt(
    	Map<String,Object>	map,
    	String				key )
    {
    	Long	l = (Long)map.get( key );

    	if ( l != null ){

    		return( l.intValue());
    	}

    	return( 0 );
    }

    private void
    exportString(
    	Map<String,Object>	map,
    	String				key,
    	String				s )
    {
    	try{
    		map.put( key, s.getBytes( "UTF-8" ));

    	}catch( Throwable e ){
    	}
    }

    private String
    importString(
    	Map<String,Object>	map,
    	String				key )
    {
       	Object obj= map.get( key );

       	if ( obj instanceof String ){

       		return((String)obj);

       	}else if ( obj instanceof byte[] ){

    		try{
    			return( new String((byte[])obj, "UTF-8" ));

    		}catch( Throwable e ){
	    	}
       	}

    	return( null );
    }

    public void
    dump(
    	IndentWriter		iw )
    {
    	iw.println( "Profiles" );

    	iw.indent();

    	try{
	    	List<String> profiles = getProfileNames();

	    	for (String profile: profiles ){

	    		iw.println( profile );

	    		iw.indent();

	    		try{
	    			List<String> p_lines = getProfileSupport( profile, true );

	    			for ( String line: p_lines ){

	    				iw.println( line );
	    			}
	    		}finally{

	    			iw.exdent();
	    		}
	    	}
    	}finally{

    		iw.exdent();
    	}

    	iw.println( "Schedule" );

    	iw.indent();

    	try{
	    	List lines_list = COConfigurationManager.getListParameter( "speed.limit.handler.schedule.lines", new ArrayList());

			List<String> schedule_lines = BDecoder.decodeStrings(BEncoder.cloneList(lines_list) );

			for ( String line: schedule_lines ){

				iw.println( line );
			}
    	}finally{

    		iw.exdent();
    	}
    }

	private class
	LimitDetails
	{
	    private boolean		auto_up_enabled;
	    private boolean		auto_up_seeding_enabled;
	    private boolean		seeding_limits_enabled;
	    private int			up_limit;
	    private int			up_seeding_limit;
	    private int			down_limit;

	    private boolean		lan_rates_enabled;
	    private int			lan_up_limit;
	    private int			lan_down_limit;

	    private final Map<String,int[]>	download_limits = new HashMap<>();
	    private final Map<String,int[]>	category_limits = new HashMap<>();
	    private final Map<String,int[]>	tag_limits 		= new HashMap<>();

	    private
	    LimitDetails()
	    {
	    }

	    private
	    LimitDetails(
	    	Map<String,Object>		map )
	    {
	    	auto_up_enabled 		= importBoolean( map, "aue" );
	    	auto_up_seeding_enabled	= importBoolean( map, "ause" );
	    	seeding_limits_enabled	= importBoolean( map, "sle" );

	    	up_limit			= importInt( map, "ul" );
	    	up_seeding_limit	= importInt( map, "usl" );
	    	down_limit			= importInt( map, "dl" );

	    	if ( map.containsKey( "lre" )){

	    		lan_rates_enabled 		= importBoolean( map, "lre" );

	    	}else{
	    			// migration from before LAN rates added

	    		lan_rates_enabled = COConfigurationManager.getBooleanParameter( "LAN Speed Enabled" );
	    	}

	    	lan_up_limit		= importInt( map, "lul" );
	    	lan_down_limit		= importInt( map, "ldl" );


	    	List<Map<String,Object>>	d_list = (List<Map<String,Object>>)map.get( "dms" );

	    	if ( d_list != null ){

	    		for ( Map<String,Object> m: d_list ){

	    			String	k = importString( m, "k" );

	    			if ( k != null ){

	    				int	ul = importInt( m, "u" );
	    				int	dl = importInt( m, "d" );

	    				download_limits.put( k, new int[]{ ul, dl });
	    			}
	    		}
	    	}

	    	List<Map<String,Object>>	c_list = (List<Map<String,Object>>)map.get( "cts" );

	    	if ( c_list != null ){

	    		for ( Map<String,Object> m: c_list ){

	    			String	k = importString( m, "k" );

	    			if ( k != null ){

	    				int	ul = importInt( m, "u" );
	    				int	dl = importInt( m, "d" );

	    				category_limits.put( k, new int[]{ ul, dl });
	    			}
	    		}
	    	}

	    	List<Map<String,Object>>	t_list = (List<Map<String,Object>>)map.get( "tgs" );

	    	if ( t_list != null ){

	    		for ( Map<String,Object> m: t_list ){

	    			String	t = importString( m, "k" );

	    			if ( t != null ){

	    				int	ul = importInt( m, "u" );
	    				int	dl = importInt( m, "d" );

	    				tag_limits.put( t, new int[]{ ul, dl });
	    			}
	    		}
	    	}
	    }

	    private Map<String,Object>
	    export()
	    {
	    	Map<String,Object>	map = new HashMap<>();

	    	exportBoolean( map, "aue", auto_up_enabled );
	    	exportBoolean( map, "ause", auto_up_seeding_enabled );
	    	exportBoolean( map, "sle", seeding_limits_enabled );

	    	exportInt( map, "ul", up_limit );
	    	exportInt( map, "usl", up_seeding_limit );
	    	exportInt( map, "dl", down_limit );

	    	exportBoolean( map, "lre", lan_rates_enabled );
	    	exportInt( map, "lul", lan_up_limit );
	    	exportInt( map, "ldl", lan_down_limit );


	    	List<Map<String,Object>>	d_list = new ArrayList<>();

	    	map.put( "dms", d_list );

	    	for ( Map.Entry<String,int[]> entry: download_limits.entrySet()){

	    		Map<String,Object> m = new HashMap<>();

	    		d_list.add( m );

	    		exportString( m, "k", entry.getKey());
	    		exportInt( m, "u", entry.getValue()[0]);
	    		exportInt( m, "d", entry.getValue()[1]);
	    	}

	    	List<Map<String,Object>>	c_list = new ArrayList<>();

	    	map.put( "cts", c_list );

	    	for ( Map.Entry<String,int[]> entry: category_limits.entrySet()){

	    		Map<String,Object> m = new HashMap<>();

	    		c_list.add( m );

	    		exportString( m, "k", entry.getKey());
	    		exportInt( m, "u", entry.getValue()[0]);
	    		exportInt( m, "d", entry.getValue()[1]);
	    	}

	    	List<Map<String,Object>>	t_list = new ArrayList<>();

	    	map.put( "tgs", t_list );

	    	for ( Map.Entry<String,int[]> entry: tag_limits.entrySet()){

	    		Map<String,Object> m = new HashMap<>();

	    		t_list.add( m );

	    		exportString( m, "k", entry.getKey());
	    		exportInt( m, "u", entry.getValue()[0]);
	    		exportInt( m, "d", entry.getValue()[1]);
	    	}

	    	return( map );
	    }

	    private void
	    loadForReset()
	    {
	    		// just maintain the auto upload setting over a reset

		    auto_up_enabled 		= COConfigurationManager.getBooleanParameter( TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY );
	    }

	    private void
	    loadCurrent()
	    {
		    auto_up_enabled 		= COConfigurationManager.getBooleanParameter( TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY );
		    auto_up_seeding_enabled = COConfigurationManager.getBooleanParameter( TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY );
		    seeding_limits_enabled 	= COConfigurationManager.getBooleanParameter( TransferSpeedValidator.UPLOAD_SEEDING_ENABLED_CONFIGKEY );
		    up_limit 				= COConfigurationManager.getIntParameter( TransferSpeedValidator.UPLOAD_CONFIGKEY );
		    up_seeding_limit 		= COConfigurationManager.getIntParameter( TransferSpeedValidator.UPLOAD_SEEDING_CONFIGKEY );
		    down_limit				= COConfigurationManager.getIntParameter( TransferSpeedValidator.DOWNLOAD_CONFIGKEY );

		    lan_rates_enabled 		= COConfigurationManager.getBooleanParameter( "LAN Speed Enabled" );
		    lan_up_limit 			= COConfigurationManager.getIntParameter( "Max LAN Upload Speed KBs" );
		    lan_down_limit 			= COConfigurationManager.getIntParameter( "Max LAN Download Speed KBs" );

		    download_limits.clear();

			GlobalManager gm = core.getGlobalManager();

			List<DownloadManager>	downloads = gm.getDownloadManagers();

			for ( DownloadManager download: downloads ){

				TOTorrent torrent = download.getTorrent();

				byte[]	hash = null;

				if ( torrent!= null ){

					try{
						hash = torrent.getHash();

					}catch( Throwable e ){

					}
				}

				if ( hash != null ){
					int	download_up_limit 	= download.getStats().getUploadRateLimitBytesPerSecond();
					int	download_down_limit = download.getStats().getDownloadRateLimitBytesPerSecond();

			    	if ( download_up_limit > 0 || download_up_limit < 0 || download_down_limit > 0 || download_down_limit < 0 ){

			    		download_limits.put( Base32.encode( hash ), new int[]{ download_up_limit, download_down_limit });
			    	}
				}
			}

			Category[] categories = CategoryManager.getCategories();

			category_limits.clear();

		    for ( Category category: categories ){

		    	int	cat_up_limit	 	= category.getUploadSpeed();
		    	int	cat_down_limit 		= category.getDownloadSpeed();

		    	if ( cat_up_limit > 0 || cat_down_limit > 0 ){

		    		category_limits.put( category.getName(), new int[]{ cat_up_limit, cat_down_limit });
		    	}
		    }

			List<TagType>	tag_types = TagManagerFactory.getTagManager().getTagTypes();

			tag_limits.clear();

		    for ( TagType tag_type: tag_types ){

		    	if ( tag_type.getTagType() == TagType.TT_DOWNLOAD_CATEGORY ){

		    		continue;
		    	}

		    	if ( tag_type.hasTagTypeFeature( TagFeature.TF_RATE_LIMIT )){

		    		List<Tag> tags = tag_type.getTags();

		    		for ( Tag tag: tags ){

			    		TagFeatureRateLimit rl = (TagFeatureRateLimit)tag;

				    	int	tag_up_limit	 	= rl.getTagUploadLimit();
				    	int	tag_down_limit 		= rl.getTagDownloadLimit();

				    	if ( tag_up_limit != 0 || tag_down_limit != 0 ){

				    		tag_limits.put(
				    			tag_type.getTagType() + "." + tag.getTagID(),
				    			new int[]{ tag_up_limit, tag_down_limit });
				    	}
		    		}
		    	}
		    }
	    }

	    private int[]
	    getLimitsForDownload(
	    	String	hash )
	    {
	    	return( download_limits.get( hash ));
	    }

	    private void
	    addRemoveDownloads(
	    	List<String>		hashes,
	    	boolean				add )
	    {
			GlobalManager gm = core.getGlobalManager();

	    	for ( String hash: hashes ){

	    		if ( add ){

	   				DownloadManager download = gm.getDownloadManager( new HashWrapper( Base32.decode( hash )));

	    			if ( download != null ){

						int	download_up_limit 	= download.getStats().getUploadRateLimitBytesPerSecond();
						int	download_down_limit = download.getStats().getDownloadRateLimitBytesPerSecond();

						if ( download_up_limit > 0 || download_up_limit < 0 || download_down_limit > 0 || download_down_limit < 0 ){

				    		download_limits.put(hash, new int[]{ download_up_limit, download_down_limit });
				    	}
	    			}
	    		}else{

	    			download_limits.remove( hash );
	    		}
	    	}
	    }

	    private void
	    apply()
	    {
	    		// don't manage this properly because the speedmanager has a 'memory' of
    			// the last upload limit in force before it became active and we're
    			// not persisting this... rare use case methinks anyway

    		COConfigurationManager.setParameter( TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY, auto_up_enabled );
	    	COConfigurationManager.setParameter( TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY, auto_up_seeding_enabled );

    		if ( !( auto_up_enabled || auto_up_seeding_enabled )){

 		     	COConfigurationManager.setParameter( TransferSpeedValidator.UPLOAD_CONFIGKEY, up_limit );
    		}

		    COConfigurationManager.setParameter( TransferSpeedValidator.UPLOAD_SEEDING_ENABLED_CONFIGKEY, seeding_limits_enabled );
		    COConfigurationManager.setParameter( TransferSpeedValidator.UPLOAD_SEEDING_CONFIGKEY, up_seeding_limit );

		    COConfigurationManager.setParameter( TransferSpeedValidator.DOWNLOAD_CONFIGKEY, down_limit );

		    COConfigurationManager.setParameter( "LAN Speed Enabled", lan_rates_enabled );
		    COConfigurationManager.setParameter( "Max LAN Upload Speed KBs", lan_up_limit );
		    COConfigurationManager.setParameter( "Max LAN Download Speed KBs", lan_down_limit );

			GlobalManager gm = core.getGlobalManager();

			Set<DownloadManager>	all_managers = new HashSet<>(gm.getDownloadManagers());

			for ( Map.Entry<String,int[]> entry: download_limits.entrySet()){

				byte[] hash = Base32.decode( entry.getKey());

				DownloadManager dm = gm.getDownloadManager( new HashWrapper( hash ));

				if ( dm != null ){

					int[]	limits = entry.getValue();

					dm.getStats().setUploadRateLimitBytesPerSecond( limits[0] );
					dm.getStats().setDownloadRateLimitBytesPerSecond( limits[1] );

					all_managers.remove( dm );
				}
			}

			for ( DownloadManager dm: all_managers ){

				dm.getStats().setUploadRateLimitBytesPerSecond( 0 );
				dm.getStats().setDownloadRateLimitBytesPerSecond( 0 );
			}

				//cats

			Set<Category> all_categories = new HashSet<>(Arrays.asList(CategoryManager.getCategories()));

			Map<String, Category> cat_map = new HashMap<>();

			for ( Category c: all_categories ){

				cat_map.put( c.getName(), c );
			}

			for ( Map.Entry<String,int[]> entry: category_limits.entrySet()){

		    	String cat_name = entry.getKey();

		    	Category category = cat_map.get( cat_name );

		    	if ( category != null ){

		    		int[]	limits = entry.getValue();

		    		category.setUploadSpeed( limits[0] );
		    		category.setDownloadSpeed( limits[1] );

		    		all_categories.remove( category );
		    	}
			}

			for ( Category category: all_categories ){

	    		category.setUploadSpeed( 0 );
	    		category.setDownloadSpeed( 0 );
			}

				// tags

			TagManager tm = TagManagerFactory.getTagManager();

			List<TagType> all_tts = tm.getTagTypes();

			Set<Tag>	all_rl_tags = new HashSet<>();

			for ( TagType tt: all_tts ){

				if ( tt.getTagType() == TagType.TT_DOWNLOAD_CATEGORY ){
					continue;
				}

				if ( tt.hasTagTypeFeature( TagFeature.TF_RATE_LIMIT )){

					all_rl_tags.addAll( tt.getTags());
				}
			}

			for ( Map.Entry<String,int[]> entry: tag_limits.entrySet()){

		    	String tag_key = entry.getKey();

		    	String[] bits = tag_key.split( "\\." );

		    	try{
		    		int	tag_type 	= Integer.parseInt( bits[0] );
		    		int tag_id		= Integer.parseInt( bits[1] );

		    		TagType tt = tm.getTagType( tag_type );

		    		if ( tt == null || !tt.hasTagTypeFeature( TagFeature.TF_RATE_LIMIT )){

		    			continue;
		    		}

		    		Tag tag = tt.getTag( tag_id );

		    		if ( tag == null ){

		    			continue;
		    		}

		    		TagFeatureRateLimit rl = (TagFeatureRateLimit)tag;

		    		int[]	limits = entry.getValue();

		    		rl.setTagUploadLimit( limits[0] );
		    		rl.setTagDownloadLimit( limits[1] );

		    		all_rl_tags.remove( tag );

		    	}catch( Throwable e ){

		    	}
			}

			for ( Tag tag: all_rl_tags ){

				try{
					TagFeatureRateLimit rl = (TagFeatureRateLimit)tag;

					rl.setTagUploadLimit( 0 );
		    		rl.setTagDownloadLimit( 0 );

				}catch( Throwable e ){

		    	}
			}
	    }

	    private List<String>
	    getString(
	    	boolean	is_current,
	    	boolean	use_hashes )
	    {
			List<String> result = new ArrayList<>();

			result.add( "Global Limits" );

		    if ( auto_up_enabled ){

			    result.add( "    Auto upload limit enabled" );

		    }else if ( auto_up_seeding_enabled ){

		    	result.add( "    Auto upload seeding limit enabled" );

		    }else{

		    	result.add( "    " + formatUp( up_limit*1024 ));

	    		if ( seeding_limits_enabled ){

	    			result.add( "    Seeding only limit enabled" );

		    		result.add( "    Seeding only: " + format( up_seeding_limit*1024 ));
		    	}
		    }

		    result.add( "    " + formatDown( down_limit*1024 ));

		    if ( lan_rates_enabled ){

		    	result.add( "" );
			    result.add( "    LAN limits enabled" );
		    	result.add( "        " + formatUp( lan_up_limit*1024 ));
		    	result.add( "        " + formatDown( lan_down_limit*1024 ));
		    }

		    result.add( "" );

		    result.add( "Download Limits" );

		    int	total_download_limits 				= 0;
		    int	total_download_limits_up 			= 0;
		    int	total_download_limits_up_disabled 	= 0;
		    int	total_download_limits_down 			= 0;
		    int	total_download_limits_down_disabled	= 0;

			GlobalManager gm = core.getGlobalManager();

			for ( Map.Entry<String,int[]> entry: download_limits.entrySet()){

				String hash_str = entry.getKey();

				byte[] hash = Base32.decode( hash_str );

				DownloadManager dm = gm.getDownloadManager( new HashWrapper( hash ));

				if ( dm != null ){

					int[]	limits = entry.getValue();

		    		total_download_limits++;

		    		int	up 		= limits[0];
		    		int	down 	= limits[1];

		    		if ( up < 0 ){

		    			total_download_limits_up_disabled++;

		    		}else{

		    			total_download_limits_up 	+= up;
		    		}

		    		if ( down < 0 ){

		    			total_download_limits_down_disabled++;

		    		}else{

		    			total_download_limits_down 	+= down;
		    		}

		    		result.add( "    " + (use_hashes?hash_str.substring(0,16):dm.getDisplayName()) + ": " + formatUp( up ) + ", " + formatDown( down ));
		    	}
			}

		    if ( total_download_limits == 0 ){

		    	result.add( "    None" );

		    }else{

		    	result.add( "    ----" );

		    	result.add(
		    		"    Total=" + total_download_limits +
		    		" - Compounded limits: " + formatUp( total_download_limits_up ) +
		    		(total_download_limits_up_disabled==0?"":( " [" + total_download_limits_up_disabled + " disabled]" )) +
		    		", " + formatDown( total_download_limits_down ) +
		    		(total_download_limits_down_disabled==0?"":( " [" + total_download_limits_down_disabled + " disabled]" )));
		    }

			Category[] categories = CategoryManager.getCategories();

			Map<String, Category> cat_map = new HashMap<>();

			for ( Category c: categories ){

				cat_map.put( c.getName(), c );
			}

		    result.add( "" );

			result.add( "Category Limits" );

			int	total_cat_limits 		= 0;
		    int	total_cat_limits_up 	= 0;
		    int	total_cat_limits_down 	= 0;

		    Map<String,int[]> sorted_category_limits = new TreeMap<>(category_limits);

			for ( Map.Entry<String,int[]> entry: sorted_category_limits.entrySet()){

		    	String cat_name = entry.getKey();

		    	Category category = cat_map.get( cat_name );

		    	if ( category != null ){

		    		if ( category.getType() == Category.TYPE_UNCATEGORIZED ){

		    			cat_name = "Uncategorised";
		    		}

					int[]	limits = entry.getValue();

		    		total_cat_limits++;

		    		int	up 		= limits[0];
		    		int	down 	= limits[1];

		    		total_cat_limits_up 	+= up;
		    		total_cat_limits_down 	+= down;

		    		result.add( "    " + cat_name + ": " + formatUp( up ) + ", " + formatDown( down ));
		    	}
		    }

		    if ( total_cat_limits == 0 ){

		    	result.add( "    None" );

		    }else{

		    	result.add( "    ----" );

		    	result.add( "    Total=" + total_cat_limits + " - Compounded limits: " + formatUp( total_cat_limits_up ) + ", " + formatDown( total_cat_limits_down ));

		    }

		    result.add( "" );

			result.add( "Tag Limits" );

			int	total_tag_limits 		= 0;
		    int	total_tag_limits_up 	= 0;
		    int	total_tag_limits_down 	= 0;

		    boolean some_up_disabled 	= false;
		    boolean some_down_disabled	= false;

		    TagManager tm = TagManagerFactory.getTagManager();

		    Map<String,int[]> sorted_tag_limts = new TreeMap<>(tag_limits);

			for ( Map.Entry<String,int[]> entry: sorted_tag_limts.entrySet()){

		    	String tag_key = entry.getKey();

		    	String[] bits = tag_key.split( "\\." );

		    	try{
		    		int	tag_type 	= Integer.parseInt( bits[0] );
		    		int tag_id		= Integer.parseInt( bits[1] );

		    		TagType tt = tm.getTagType( tag_type );

		    		if ( tt == null || !tt.hasTagTypeFeature( TagFeature.TF_RATE_LIMIT )){

		    			continue;
		    		}

		    		Tag tag = tt.getTag( tag_id );

		    		if ( tag == null ){

		    			continue;
		    		}

		    		String tag_name = tt.getTagTypeName( true ) + " - " + tag.getTagName( true );

					int[]	limits = entry.getValue();

		    		total_tag_limits++;

		    		int	up 		= limits[0];
		    		int	down 	= limits[1];

		    		if ( up > 0 ){
		    			total_tag_limits_up 	+= up;
		    		}else if ( up < 0 ){
		    			some_up_disabled = true;
		    		}

		    		if ( down > 0 ){
		    			total_tag_limits_down 	+= down;
		    		}else if ( down < 0 ){
		    			some_down_disabled = true;
		    		}

		    		result.add( "    " + tag_name + ": " + formatUp( up ) + ", " + formatDown( down ));

		    	}catch( Throwable e ){

		    	}
		    }

			String dis_str = "";

			if ( some_up_disabled ){

				dis_str = "up";
			}

			if ( some_down_disabled ){

				dis_str += (dis_str.length()==0?"":"&") + "down";

			}

			if (dis_str.length() > 0 ){

				dis_str = " (some " + dis_str + " disabled)";
			}

		    if ( total_tag_limits == 0 ){

		    	result.add( "    None" + dis_str );

		    }else{

		    	result.add( "    ----" );

		    	result.add( "    Total=" + total_tag_limits + " - Compounded limits: " + formatUp( total_tag_limits_up ) + ", " + formatDown( total_tag_limits_down ) + dis_str );

		    }



		    if ( is_current ){

				Map<LimitedRateGroup,List<Object>> plugin_limiters = new HashMap<>();

				List<DownloadManager> dms = gm.getDownloadManagers();

				for ( DownloadManager dm: dms ){

					for ( boolean upload: new Boolean[]{ true, false }){

						List<LimitedRateGroup> limiters = trim( dm.getRateLimiters( upload ));

						for ( LimitedRateGroup g: limiters ){

							List<Object> entries = plugin_limiters.get( g );

							if ( entries == null ){

								entries = new ArrayList<>();

								plugin_limiters.put( g, entries );

								entries.add( upload );
								entries.add( new int[]{ 0 });
							}

							entries.add( dm );
						}
					}

		    		PEPeerManager pm = dm.getPeerManager();

		    		if ( pm != null ){

		    			List<PEPeer> peers = pm.getPeers();

		    			for ( PEPeer peer: peers ){

		    				for ( boolean upload: new Boolean[]{ true, false }){

		    					List<LimitedRateGroup> limiters = trim( peer.getRateLimiters( upload ));

		    					for ( LimitedRateGroup g: limiters ){

		    						List<Object> entries = plugin_limiters.get( g );

		    						if ( entries == null ){

		    							entries = new ArrayList<>();

		    							plugin_limiters.put( g, entries );

		    							entries.add( upload );

		    							entries.add( new int[]{ 1 });

		    						}else{

		    							((int[])entries.get(1))[0]++;
		    						}
		    					}
		    				}
		    			}
		    		}
	    		}

			    result.add( "" );

				result.add( "Plugin Limits" );

			    if ( plugin_limiters.size() == 0 ){

			    	result.add( "    None" );

			    }else{

			    	List<String>	plugin_lines = new ArrayList<>();

			    	for ( Map.Entry<LimitedRateGroup,List<Object>> entry: plugin_limiters.entrySet()){

			    		LimitedRateGroup group = entry.getKey();

			    		List<Object> list = entry.getValue();

			    		boolean is_upload 	= (Boolean)list.get(0);
			    		int		peers		= ((int[])list.get(1))[0];

			    		String line = "    " + group.getName() + ": " + (is_upload?formatUp( group.getRateLimitBytesPerSecond()):formatDown( group.getRateLimitBytesPerSecond()));

			    		if ( peers > 0 ){

			    			line += ", peers=" + peers;
			    		}

			    		if ( list.size() > 2 ){

			    			line += ", downloads=" + (list.size()-2);
			    		}

			    		plugin_lines.add( line );
			    	}

			    	Collections.sort( plugin_lines );

			    	result.addAll( plugin_lines );
			    }
		    }

			return( result );
	    }
	}

	private static class
	ScheduleRule
	{
		private static final byte	FR_MON		= 0x01;
		private static final byte	FR_TUE		= 0x02;
		private static final byte	FR_WED		= 0x04;
		private static final byte	FR_THU		= 0x08;
		private static final byte	FR_FRI		= 0x10;
		private static final byte	FR_SAT		= 0x20;
		private static final byte	FR_SUN		= 0x40;
		private static final byte	FR_OVERFLOW	= (byte)0x80;
		private static final byte	FR_WEEKDAY	= ( FR_MON | FR_TUE | FR_WED | FR_THU | FR_FRI );
		private static final byte	FR_WEEKEND	= ( FR_SAT | FR_SUN );
		private static final byte	FR_DAILY	= ( FR_WEEKDAY | FR_WEEKEND );

		final String	profile_name;
		final byte	frequency;
		final int		from_mins;
		final int		to_mins;

		private final List<ScheduleRuleExtensions>	extensions;

		private
		ScheduleRule(
			byte							_freq,
			String							_profile,
			int								_from,
			int								_to,
			List<ScheduleRuleExtensions>	_exts )
		{
			frequency 		= _freq;
			profile_name	= _profile;
			from_mins		= _from;
			to_mins			= _to;
			extensions		= _exts;
		}

		private List<ScheduleRule>
		splitByDay()
		{
			List<ScheduleRule>	result = new ArrayList<>();

			if ( to_mins > from_mins ){

				result.add( this );

			}else{

					// handle rules that wrap across days. e.g. 23:00 to 00:00

				byte next_frequency = (byte)(frequency << 1 );

				if ((next_frequency & FR_OVERFLOW ) != 0 ){

					next_frequency &= ~FR_OVERFLOW;

					next_frequency |= FR_MON;
				}

				ScheduleRule	rule1 = new ScheduleRule( frequency, profile_name, from_mins, 23*60+59, extensions );
				ScheduleRule	rule2 = new ScheduleRule( next_frequency, profile_name, 0, to_mins, extensions );

				result.add( rule1 );
				result.add( rule2 );
			}

			return( result );
		}

		private void
		checkExtensions()
		{
			if ( extensions != null ){

				for ( ScheduleRuleExtensions ext: extensions ){

					ext.checkExtension();
				}
			}
		}

		private boolean
		sameAs(
			ScheduleRule	other )
		{
			if ( other == null ){

				return( false );
			}

			if ( extensions != other.extensions ){

				if ( extensions == null || other.extensions == null || extensions.size() != other.extensions.size()){

					return( false );
				}

				for ( ScheduleRuleExtensions ext1: extensions ){

					boolean match = false;

					for ( ScheduleRuleExtensions ext2: other.extensions ){

						if ( ext1.sameAs( ext2 )){

							match = true;

							break;
						}
					}

					if ( !match ){

						return( false );
					}
				}
			}

			return( frequency == other.frequency &&
					profile_name.equals( other.profile_name ) &&
					from_mins == other.from_mins &&
					to_mins == other.to_mins );
		}

		public String
		getString()
		{
			String	freq_str = "";

			if ( frequency == FR_DAILY ){

				freq_str = "daily";

			}else if ( frequency == FR_WEEKDAY ){

				freq_str = "weekdays";

			}else if ( frequency == FR_WEEKEND ){

				freq_str = "weekends";

			}else if ( frequency == FR_MON ){

				freq_str = "mon";

			}else if ( frequency == FR_TUE ){

				freq_str = "tue";

			}else if ( frequency == FR_WED ){

				freq_str = "wed";

			}else if ( frequency == FR_THU ){

				freq_str = "thu";

			}else if ( frequency == FR_FRI ){

				freq_str = "fri";

			}else if ( frequency == FR_SAT ){

				freq_str = "sat";

			}else if ( frequency == FR_SUN ){

				freq_str = "sun";
			}

			String ext_str = "";

			if ( extensions != null ){

				for ( ScheduleRuleExtensions ext: extensions ){

					ext_str += ", " + ext.getString();
				}
			}

			return( "profile=" + profile_name + ", frequency=" + freq_str + ", from=" + getTime( from_mins ) + ", to=" + getTime( to_mins ) + ext_str );
		}

		private String
		getTime(
			int	mins )
		{
			String str = getTimeBit( mins/60 ) + ":" + getTimeBit( mins % 60 );

			return( str );
		}

		private String
		getTimeBit(
			int	num )
		{
			String str = String.valueOf( num );

			if ( str.length() < 2 ){

				str = "0" + str;
			}

			return( str );
		}
	}

	private class
	ScheduleRuleExtensions
	{
		private static final int ET_START_TAG 	= 1;
		private static final int ET_STOP_TAG 	= 2;
		private static final int ET_PAUSE_TAG 	= 3;
		private static final int ET_RESUME_TAG 	= 4;

		private static final int ET_ENABLE_PRIORITY 	= 5;
		private static final int ET_DISABLE_PRIORITY 	= 6;

		private static final int ET_ENABLE_NET_LIMIT 	= 7;
		private static final int ET_DISABLE_NET_LIMIT 	= 8;

		private final int				extension_type;
		private final TagDownload		tag;
		private final List<NetLimit>	net_limits;


		private
		ScheduleRuleExtensions(
			int				_et )
		{
			extension_type		= _et;
			tag					= null;
			net_limits			= null;
		}

		private
		ScheduleRuleExtensions(
			int				_et,
			TagDownload		_tag )
		{
			extension_type		= _et;
			tag					= _tag;
			net_limits			= null;
		}

		private
		ScheduleRuleExtensions(
			int				_et,
			List<NetLimit>	_net_limits )
		{
			extension_type		= _et;
			tag					= null;
			net_limits			= _net_limits;
		}

		private void
		checkExtension()
		{
			if ( net_limits != null ){

				boolean enable = extension_type == ET_ENABLE_NET_LIMIT;

				for ( NetLimit nl: net_limits ){

					nl.setEnabled( enable );
				}
			}else if ( tag == null ){

				if ( extension_type == ET_ENABLE_PRIORITY ){

					prioritiser_enabled	= true;

				}else{

					prioritiser_enabled = false;
				}
			}else{
				Set<DownloadManager> downloads = tag.getTaggedDownloads();

				for ( DownloadManager download: downloads ){

					if ( download.isPaused()){

						if ( extension_type == ET_RESUME_TAG ){

							if ( rule_pause_all_active || net_limit_pause_all_active ){

									// things are going to get messy if we do this

							}else{

								download.resume();
							}
						}

						continue;
					}

					int	state = download.getState();

					if ( extension_type == ET_START_TAG ){

						if ( state == DownloadManager.STATE_STOPPED ){

							download.setStateWaiting();
						}
					}else{

						if ( extension_type == ET_PAUSE_TAG ){

							if ( !download.isPaused()){

								download.pause( true );
							}
							
							download.setStopReason( "Speed Limit Handler: Tag " + tag.getTagName( true ));
							
						}else if ( extension_type == ET_STOP_TAG ){

							if ( 	state != Download.ST_ERROR &&
									state != Download.ST_STOPPED &&
									state != Download.ST_STOPPING ){

								download.stopIt( DownloadManager.STATE_STOPPED, false, false );
								
								download.setStopReason( "Speed Limit Handler: Tag " + tag.getTagName( true ));
							}
						}
					}
				}
			}
		}

		private boolean
		sameAs(
			ScheduleRuleExtensions	other )
		{
			return( extension_type == other.extension_type && tag == other.tag );
		}

		private String
		getString()
		{
			String str;

			if ( extension_type == ET_START_TAG ){

				str = "start_tag";

			}else if ( extension_type == ET_STOP_TAG ){

				str = "stop_tag";

			}else if ( extension_type == ET_RESUME_TAG ){

				str = "resume_tag";

			}else if ( extension_type == ET_PAUSE_TAG ){

				str = "pause_tag";

			}else if ( extension_type == ET_ENABLE_PRIORITY ){

				str = "enable_priority";

			}else if ( extension_type == ET_DISABLE_PRIORITY ){

				str = "disable_priority";

			}else if ( extension_type == ET_ENABLE_NET_LIMIT ){

				str = "enable_net_limit";

			}else if ( extension_type == ET_DISABLE_NET_LIMIT ){

				str = "disable_net_limit";

			}else{

				str = "eh?";
			}

			if ( tag != null ){

				str += ":" + tag.getTagName( true );
			}

			if ( net_limits != null ){

				str += ":netlimits=" + net_limits.size();
			}
			return( str );
		}
	}

	class
	NetLimit
	{
		final private int				type;
		final private String			name;
		final private double			multiplier;
		final private String			profile;
		final private TagType			tag_type;
		final private String			tag_name;
		final private long[]			limits;

		private boolean						enabled = true;

		private TagFeatureRateLimit			tag;
		private LongTermStats 				lt_stats;

		private
		NetLimit(
			int						_type,
			String					_name,
			double					_mult,
			String					_profile,
			TagType					_tag_type,
			String					_tag_name,
			long					_total_lim,
			long					_up_lim,
			long					_down_lim )
		{
			type		= _type;
			name		= _name;
			multiplier	= _mult;
			profile		= _profile;
			tag_type	= _tag_type;
			tag_name	= _tag_name;
			limits		= new long[]{ _total_lim, _up_lim, _down_lim };
		}

		private void
		initialise()
		{
			if ( tag_type != null ){

				tag = (TagFeatureRateLimit)tag_type.getTag( tag_name, true );

				if ( tag == null ){

					Debug.out( "hmm, tag " + tag_name + " not found" );

				}else{

					try{
						lt_stats = StatsFactory.getGenericLongTermStats(
								"tag" + "." + tag.getTag().getTagUID(),
								new NetLimitStatsProvider( tag ));

					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			}
		}

		private int
		getType()
		{
			return( type );
		}
		
		private String
		getName()
		{
			return( name );
		}

		private boolean
		isEnabled()
		{
			return( enabled );
		}

		private void
		setEnabled(
			boolean	_b )
		{
			enabled = _b;
		}

		private double
		getMultiplier()
		{
			return( multiplier );
		}

		private LongTermStats
		getLongTermStats()
		{
			return( lt_stats );
		}


		private String
		getProfile()
		{
			return( profile );
		}

		private TagFeatureRateLimit
		getTag()
		{
			return( tag );
		}

		private long[]
		getLimits()
		{
			return( limits );
		}

		private class
		NetLimitStatsProvider
			implements LongTermStats.GenericStatsSource
		{
			private final TagType			tag_type;
			private final String			tag_name;

			private TagFeatureRateLimit		tag_rl;

			private
			NetLimitStatsProvider(
				TagFeatureRateLimit		_tag_rl )
			{
				tag_rl	= _tag_rl;

				Tag tag = tag_rl.getTag();

				tag_type	= tag.getTagType();
				tag_name	= tag.getTagName( true );
			}

			@Override
			public int
			getEntryCount()
			{
				return( 4 );
			}

			@Override
			public long[]
			getStats(
				String		id )
			{
				if ( tag_type == ip_set_tag_type ){

						// need to re-resolve this

					TagFeatureRateLimit t = (TagFeatureRateLimit)ip_set_tag_type.getTag( tag_name, true );

					if ( t != tag_rl ){

						tag_rl = t;
					}

				}
					// currently protocol/data isn't separated so lump it all in as data

				long[] up 	= tag_rl.getTagUploadTotal();
				long[] down = tag_rl.getTagDownloadTotal();

				long[] result = new long[4];

				if ( up != null ){

					result[LongTermStats.ST_DATA_UPLOAD] = up[0];

				}
				if ( down != null ){

					result[LongTermStats.ST_DATA_DOWNLOAD] = down[0];

				}

				return( result );
			}
		}
	}

	private static class
	IPSetTagType
		extends TagTypeWithState
	{
		private final int[] color_default		 	= { 132, 16, 57 };

		private
		IPSetTagType()
		{
			super( TagType.TT_PEER_IPSET, TagPeer.FEATURES, "tag.type.ipset" );

			addTagType();
		}

		@Override
		public int[]
	    getColorDefault()
		{
			return( color_default );
		}
	}

	public class
	PeerSet
	{
		private final String		name;

		private long[][]			ranges 			= new long[0][];
		private final Set<String>			country_codes 	= new HashSet<>();
		private final Set<String>			networks	 	= new HashSet<>();

		private boolean	inverse;

		private Set<String>	categories_or_tags;

		private boolean	has_explicit_up_lim;
		private boolean	has_explicit_down_lim;

		private long	last_send_total = -1;
		private long	last_recv_total = -1;

		//private Average send_rate		= Average.getInstance(1000, 10);  //average over 10s, update every 1000ms
		//private Average receive_rate	= Average.getInstance(1000, 10);  //average over 10s, update every 1000ms
		final Average send_rate		= AverageFactory.MovingImmediateAverage( 10 );
		final Average receive_rate	= AverageFactory.MovingImmediateAverage( 10 );

		final RateLimiter		up_limiter;
		final RateLimiter		down_limiter;

		private int				peer_up_lim;
		private int				peer_down_lim;

		private Pattern			client_pattern;
		private Pattern			intf_pattern;
		private Pattern			asn_pattern;
		
		private boolean			client_pattern_inverse;
		private boolean			intf_pattern_inverse;
		private boolean			asn_pattern_inverse;
		
		private String			group;
		
		private TagPeerImpl		tag_impl;

		private
		PeerSet(
			String	_name )
		{
			name	= _name;

			up_limiter 		= plugin_interface.getConnectionManager().createRateLimiter( "ps-" + name, 0 );
			down_limiter 	= plugin_interface.getConnectionManager().createRateLimiter( "ps-" + name, 0 );
		}

		private void
		initialise(
			int		tag_id )
		{
			// System.out.println( "init " + name + " -> " + tag_id );
			
			if ( ip_set_tag_type != null ){

				tag_impl	= new TagPeerImpl( this, tag_id );
			}

			if ( !has_explicit_up_lim ){

				up_limiter.setRateLimitBytesPerSecond( COConfigurationManager.getIntParameter( "speed.limit.handler.ipset_n." + tag_id + ".up", 0 ));
			}

			if ( !has_explicit_down_lim ){

				down_limiter.setRateLimitBytesPerSecond( COConfigurationManager.getIntParameter( "speed.limit.handler.ipset_n." + tag_id + ".down", 0 ));
			}
			
			Tag	tag = tag_impl;

			if ( group != null ){
					
				if ( tag != null ){
					
					tag.setGroup( group );
				}
			}
			
			if ( 	( client_pattern != null && client_pattern.pattern().equals( "auto" )) ||
					( intf_pattern != null && intf_pattern.pattern().equals( "auto" ))){
				
				if ( tag != null ){
					
					tag.setVisible( false );
				}
			}
		}

		private void
		setParameters(
			boolean			_inverse,
			int				_up_lim,
			int				_down_lim,
			int				_peer_up_lim,
			int				_peer_down_lim,
			Set<String>		_cats_or_tags,
			Pattern			_client_pattern,
			boolean			_client_pattern_inverse,
			Pattern			_intf_pattern,
			boolean			_intf_pattern_inverse,
			Pattern			_asn_pattern,
			boolean			_asn_pattern_inverse,
			String			_group )
		{
			inverse	= _inverse;

			has_explicit_up_lim = _up_lim >= 0;
			if ( !has_explicit_up_lim ){
				_up_lim = 0;
			}

			has_explicit_down_lim = _down_lim >= 0;
			if ( !has_explicit_down_lim ){
				_down_lim = 0;
			}

			up_limiter.setRateLimitBytesPerSecond( _up_lim );
			down_limiter.setRateLimitBytesPerSecond( _down_lim );

			peer_up_lim 	= _peer_up_lim;
			peer_down_lim	= _peer_down_lim;

			categories_or_tags = _cats_or_tags.size()==0?null:_cats_or_tags;
			
			client_pattern 	= _client_pattern;
			intf_pattern 	= _intf_pattern;
			asn_pattern		= _asn_pattern;
			
			client_pattern_inverse	= _client_pattern_inverse;
			intf_pattern_inverse	= _intf_pattern_inverse;
			asn_pattern_inverse		= _asn_pattern_inverse;
						
			if ( client_pattern != null && client_pattern.pattern().equals( "auto" )){
				
				setGroup( MessageText.getString( "Peers.column.client" ) + "_" + MessageText.getString( "wizard.maketorrent.auto" ));
				
			}else if ( intf_pattern != null && intf_pattern.pattern().equals( "auto" )){
				
				setGroup( MessageText.getString( "label.interface.short" ) + "_" + MessageText.getString( "wizard.maketorrent.auto" ));
				
			}else if ( _group != null ){
				
				setGroup( _group );
			}
		}

		private void
		setGroup(
			String	_group )
		{
			group = _group;
		}
		
		public Pattern
		getClientPattern()
		{
			return( client_pattern );
		}
		
		private int
		getPeerUpLimit()
		{
			return( peer_up_lim );
		}

		private int
		getPeerDownLimit()
		{
			return( peer_down_lim );
		}

		private boolean
		addCIDRorCCetc(
			String		cidr_or_cc_etc )
		{
			if ( Character.isDigit( cidr_or_cc_etc.charAt( 0 ))){

				String cidr = cidr_or_cc_etc;

				int	pos = cidr.indexOf( '/' );

				if ( pos == -1 ){

					return( false );
				}

				String	address = cidr.substring( 0, pos );

					// no ipv6 atm

				if ( address.contains( ":" )){

					return( false );
				}

				try{
					byte[] start_bytes = HostNameToIPResolver.syncResolve( address ).getAddress();

					int	cidr_mask = Integer.parseInt( cidr.substring( pos+1 ));

					int	rev_mask = 0;

					for (int i=0;i<32-cidr_mask;i++){

						rev_mask = ( rev_mask << 1 ) | 1;
					}

					start_bytes[0] &= ~(rev_mask>>24);
					start_bytes[1] &= ~(rev_mask>>16);
					start_bytes[2] &= ~(rev_mask>>8);
					start_bytes[3] &= ~(rev_mask);

					byte[] end_bytes = start_bytes.clone();

					end_bytes[0] |= (rev_mask>>24)&0xff;
					end_bytes[1] |= (rev_mask>>16)&0xff;
					end_bytes[2] |= (rev_mask>>8)&0xff;
					end_bytes[3] |= (rev_mask)&0xff;

					long	l_start = ((long)((start_bytes[0]<<24)&0xff000000 | (start_bytes[1] << 16)&0x00ff0000 | (start_bytes[2] << 8)&0x0000ff00 | start_bytes[3]&0x000000ff))&0xffffffffL;
					long	l_end	= ((long)((end_bytes[0]<<24)&0xff000000 | (end_bytes[1] << 16)&0x00ff0000 | (end_bytes[2] << 8)&0x0000ff00 | end_bytes[3]&0x000000ff))&0xffffffffL;

					//System.out.println( cidr + " -> " + ByteFormatter.encodeString( start_bytes ) + " - " +  ByteFormatter.encodeString( end_bytes ) + ": " + ((l_end-l_start+1)));

					int	len = ranges.length;

					long[][] new_ranges = new long[len+1][];

					System.arraycopy(ranges, 0, new_ranges, 0, len);

					new_ranges[len] = new long[]{ l_start, l_end };

					ranges = new_ranges;

					return( true );

				}catch( Throwable e ){

					return( false );
				}
			}else{

				for ( String net: AENetworkClassifier.AT_NETWORKS ){

					if ( cidr_or_cc_etc.equalsIgnoreCase( net )){

						networks.add( net );

						return( true );
					}
				}

				if ( cidr_or_cc_etc.equalsIgnoreCase( "IPv4" )){

					networks.add( NET_IPV4 );

					return( true );

				}else if ( cidr_or_cc_etc.equalsIgnoreCase( "IPv6" )){

					networks.add( NET_IPV6 );

					return( true );

				}else if ( cidr_or_cc_etc.equalsIgnoreCase( "LAN" )){

					networks.add( NET_LAN );

					return( true );

				}else if ( cidr_or_cc_etc.equalsIgnoreCase( "WAN" )){

					networks.add( NET_WAN );

					return( true );
				}

					// hack these in a country code for the moment

					// special case for matching everything

				if ( cidr_or_cc_etc.equalsIgnoreCase( "all" )){

					networks.addAll( Arrays.asList( AENetworkClassifier.AT_NETWORKS ));

					return( true );
				}

				String cc = cidr_or_cc_etc;

				if ( cc.length() != 2 ){

					return( false );
				}

				country_codes.add( cc.toUpperCase( Locale.US ));

				return( true );
			}
		}

		private void
		addSet(
			PeerSet	other )
		{
			long[][] new_ranges = new long[ ranges.length + other.ranges.length ][];

			System.arraycopy( ranges, 0, new_ranges, 0, ranges.length );
			System.arraycopy( other.ranges, 0, new_ranges, ranges.length, other.ranges.length );

			ranges = new_ranges;

			country_codes.addAll( other.country_codes );

			networks.addAll( other.networks );
		}

		public String
		getName()
		{
			return( name );
		}

		private long[][]
		getRanges()
		{
			return( ranges );
		}

		private Set<String>
		getCountryCodes()
		{
			return( country_codes );
		}

		private Set<String>
		getNetworks()
		{
			return( networks );
		}

		private RateLimiter
		getUpLimiter()
		{
			return( up_limiter );
		}

		private RateLimiter
		getDownLimiter()
		{
			return( down_limiter );
		}

		private Set<String>
		getCategoriesOrTags()
		{
			return( categories_or_tags );
		}

		private void
		updateStats(
			int	tick_count )
		{
			long	send_total 	= up_limiter.getRateLimitTotalByteCount();
			long	recv_total	= down_limiter.getRateLimitTotalByteCount();

			if ( last_send_total != -1 ){

				long send_diff = send_total - last_send_total;
				long recv_diff = recv_total - last_recv_total;

				send_rate.update( send_diff );
				receive_rate.update( recv_diff );
			}

			last_send_total = send_total;
			last_recv_total = recv_total;

			TagPeerImpl tag = tag_impl;

			if ( tag != null ){

				tag.update(tick_count );
			}
		}

		private boolean
		isInverse()
		{
			return( inverse );
		}

		private void
		addPeer(
			PeerManager		peer_manager,
			Peer			peer )
		{
			TagPeerImpl tag = tag_impl;

			if ( tag != null ){

				tag.add( peer_manager, peer );
			}
		}

		private void
		removePeer(
			PeerManager		peer_manager,
			Peer			peer )
		{
			TagPeerImpl tag = tag_impl;

			if ( tag != null ){

				tag.remove( peer_manager, peer );
			}
		}

		private void
		removeAllPeers()
		{
			TagPeerImpl tag = tag_impl;

			if ( tag != null ){

				tag.removeAll();
			}
		}

		private void
		destroy()
		{
			TagPeerImpl tag = tag_impl;
			
			if ( tag != null ){

				tag_impl = null;

				tag.removeAll();
				
				tag.removeTag();
			}
		}

		private String
		getAddressString()
		{
			long	address_count = 0;

			for ( long[] range: ranges ){
				address_count += range[1] - range[0] + 1;
			}

			if ( address_count == 0 ){

				return( "[]");
			}

			return( String.valueOf( address_count ));
		}

		private String
		getDetailString()
		{
			return( name + ": Up=" + format(up_limiter.getRateLimitBytesPerSecond()) + " (" + DisplayFormatters.formatByteCountToKiBEtcPerSec((long)send_rate.getAverage()) + ")" +
					", Down=" + format( down_limiter.getRateLimitBytesPerSecond()) + " (" + DisplayFormatters.formatByteCountToKiBEtcPerSec((long)receive_rate.getAverage()) + ")" +
					", Addresses=" + getAddressString() +
					", CC=" + country_codes +
					", Networks=" + networks +
					", Inverse=" + inverse +
					", Categories/Tags=" + (categories_or_tags==null?"[]":String.valueOf(categories_or_tags)) +
					", Peer_Up=" + format( peer_up_lim ) + ", Peer_Down=" + format( peer_down_lim )) +
					", Client=" + (client_pattern==null?"":(client_pattern_inverse?"!":"")+client_pattern) +
					", Intf=" + (intf_pattern==null?"":(intf_pattern_inverse?"!":"")+intf_pattern) +
					", ASN=" + (asn_pattern==null?"":(asn_pattern_inverse?"!":"")+asn_pattern);

		}

		private class
		TagPeerImpl
			extends TagBase
			implements TagPeer, TagFeatureExecOnAssign
		{
			private final PeerSet		ip_set;
			
			private final Object	UPLOAD_PRIORITY_ADDED_KEY 	= new Object();
			private final Object	BOOSTED_KEY 				= new Object();

			private int 		upload_priority;
			
			private final Set<PEPeer>	added_peers 	= new HashSet<>();
			private final Set<PEPeer>	pending_peers 	= new HashSet<>();

			private
			TagPeerImpl(
				PeerSet	_ip_set,
				int		tag_id )
			{
				super( ip_set_tag_type, tag_id, name );

				ip_set	= _ip_set;
				
				addTag();

					// these tags aget added and removed so we need to remember the config separately so we can reset it
				
				upload_priority = COConfigurationManager.getIntParameter( "speed.limit.handler.ipset_n." + tag_id + ".uppri", 0 );
				
				setTagBoost( COConfigurationManager.getBooleanParameter( "speed.limit.handler.ipset_n." + tag_id + ".boost", false ));
				
				int actions = COConfigurationManager.getIntParameter( "speed.limit.handler.ipset_n." + tag_id + ".eos", -1 );
				
				if ( actions == TagFeatureExecOnAssign.ACTION_DESTROY ){
					
					super.setActionEnabled( actions, true );
				}
				
				int[] colour = COConfigurationManager.getRGBParameter( "speed.limit.handler.ipset_n." + getTagID() + ".color" );
				
				if ( colour != null ){
					
					super.setColor( colour );
				}
			}

			@Override
			public int
			getTaggableTypes()
			{
				return( Taggable.TT_PEER );
			}

			@Override
			public int
			getSupportedActions()
			{
				return( TagFeatureExecOnAssign.ACTION_DESTROY );
			}

			@Override
			public void
			setActionEnabled(
				int			action,
				boolean		enabled )
			{
				super.setActionEnabled( action, enabled );
				
				if ( action == TagFeatureExecOnAssign.ACTION_DESTROY  ){
				
					COConfigurationManager.setParameter( "speed.limit.handler.ipset_n." + getTagID() + ".eos", enabled?TagFeatureExecOnAssign.ACTION_DESTROY:-1);
				}
			}
			
			@Override
			public void 
			setColor(int[] rgb)
			{
				super.setColor(rgb);
				
				COConfigurationManager.setRGBParameter( "speed.limit.handler.ipset_n." + getTagID() + ".color", rgb, null );
			}
			
			private void
			update(
				int		tick_count )
			{
				List<PEPeer> to_remove 	= null;
				List<PEPeer> to_add		= null;
				List<PEPeer> to_delete	= null;
				
				synchronized( this ){

					if ( tick_count % 5 == 0 ){

						Iterator<PEPeer> it = added_peers.iterator();

						while( it.hasNext()){

							PEPeer peer = it.next();

							if ( peer.getPeerState() == PEPeer.DISCONNECTED ){

								it.remove();

								if ( to_remove == null ){

									to_remove = new ArrayList<>();
								}

								to_remove.add( peer );
							}
						}
					}

					Iterator<PEPeer> it = pending_peers.iterator();

					while ( it.hasNext()){

						PEPeer peer = it.next();

						int state =  peer.getPeerState();

						if ( state == PEPeer.TRANSFERING ){

							int can_add = canAdd( peer );
							
							if ( can_add == 0 ){
								
								// defer
								
							}else if ( can_add == 3 ){
								
									// immediate remove
								
								it.remove();
								
								if ( to_delete == null ){
									
									to_delete = new ArrayList<>();
								}
								
								to_delete.add( peer );
								
							}else{
								
								it.remove();

								if ( can_add == 1 ){
									
									added_peers.add( peer );
								
									if ( to_add == null ){
	
										to_add = new ArrayList<>();
									}
	
									to_add.add( peer );
									
								}else{
									
									deferredRemove( peer );
								}
							}
						}else if ( state == PEPeer.DISCONNECTED ){

							it.remove();

								// no need to untag as never added
						}
					}
				}

				if ( to_add != null ){

					for ( PEPeer peer: to_add ){

						addTaggable( peer );
					}
				}

				if ( to_remove != null ){

					for ( PEPeer peer: to_remove ){

						removeTaggable( peer );
					}
				}
				
				if ( to_delete != null ){
					
					for ( PEPeer peer: to_delete ){
						
						PEPeerManager pm = peer.getManager();
						
						if ( pm != null ){
						
							pm.removePeer( peer, "PeerSet removal action", Transport.CR_IP_BLOCKED );
						}
					}
				}
			}

			private boolean
			deferEOS()
			{
					// we might not match this peer set as not yet applied client/intf.asn matching...
				
				return( ip_set.client_pattern != null || ip_set.intf_pattern != null || ip_set.asn_pattern != null );
			}
			
			private void
			deferredRemove(
				PEPeer		peer )
			{	
					// shouldn't have been added but remove anyway
				
				if ( upload_priority > 0 ){
					
					peer.updateAutoUploadPriority( UPLOAD_PRIORITY_ADDED_KEY, false );
				}
				
					// remove any rate-limiters
				
				Peer p = PluginCoreUtils.wrap( peer );
									
				p.removeRateLimiter( ip_set.up_limiter, true );
				p.removeRateLimiter( ip_set.down_limiter, false );
			}
			
			/**
			 * 
			 * @param peer
			 * @return 0=defer, 1=yes, 2=no, 3=remove
			 */
			
			private int
			canAdd(
				PEPeer		peer )
			{
				Pattern client_pattern 	= ip_set.client_pattern;
				Pattern intf_pattern 	= ip_set.intf_pattern;
				Pattern asn_pattern 	= ip_set.asn_pattern;
				
				if ( client_pattern == null && intf_pattern == null && asn_pattern == null ){
					
					return( 1 );
					
				}else if ( client_pattern != null ){
					
					return( canAddClient( peer, client_pattern ));
					
				}else if ( intf_pattern != null ){
					
					return( canAddIntf( peer, intf_pattern ));
					
				}else{
					
					return( canAddASN( peer, asn_pattern ));
				}
			}
			
			private int
			canAddClient(
				PEPeer		peer,
				Pattern		client_pattern )
			{
				boolean auto_client = client_pattern.pattern().equals( "auto" );
				
				boolean	result = false;
				
				String hs_name	= peer.getClientNameFromExtensionHandshake();
				
				if ( hs_name != null && !hs_name.isEmpty()){
					
					if ( auto_client ){
						
						char[] chars = hs_name.toCharArray();
						
						String client = hs_name;
						
							// take at least first 2 chars (handles I2PSnark as well...)
						
						for ( int i=2;i<chars.length;i++){
							
							if ( !Character.isLetter( chars[i] )){
								
								client = new String( chars, 0, i );
								
								break;
							}
						}
						
						synchronized( auto_peer_set_queue_client ){
							
							if ( !auto_peer_set_queue_client.contains( client )){
							
								auto_peer_set_queue_client.add( client );
							
								auto_peer_set_checker.dispatch();
							}
						}
						
						return( 2 );
					}
					
					if ( client_pattern.matcher( hs_name ).find()){
						
						result = true;
					}
					
					if ( client_pattern_inverse ){
						result = !result;
					}
				}else{
						// we want to give a bit more time for the extension handshake to arrive
					
					
					Long start = (Long)peer.getUserData( PEER_LT_WAIT_START_KEY );
					
					long now = SystemTime.getMonotonousTime();
					
					if ( start == null ){
						
						peer.setUserData( PEER_LT_WAIT_START_KEY, now );
						
						return( 0 );
						
					}else if ( now - start < 20*1000 ){
					
						return( 0 );
					}
				}
				
				if ( !result ){
					
					String id_name	= peer.getClientNameFromPeerID();
					
					if ( id_name != null && !id_name.isEmpty()){
						
						if ( auto_client ){
							
							char[] chars = id_name.toCharArray();
							
							String client = id_name;
							
							for ( int i=2;i<chars.length;i++){
								
								if ( !Character.isLetter( chars[i] )){
									
									client = new String( chars, 0, i );
									
									break;
								}
							}
							
							synchronized( auto_peer_set_queue_client ){
								
								if ( !auto_peer_set_queue_client.contains( client )){
								
									auto_peer_set_queue_client.add( client );
								
									auto_peer_set_checker.dispatch();
								}
							}
							
							return( 2 );
						}
						
						if ( client_pattern.matcher( id_name ).find()){
							
							result = true;
						}
						
						if ( client_pattern_inverse ){
							result = !result;
						}
					}
				}
				
				if ( result ){
					
					if ( isActionEnabled( TagFeatureExecOnAssign.ACTION_DESTROY )){

						return( 3 );
					}
				}
				
				return( result?1:2 );
			}
			
			private int
			canAddIntf(
				PEPeer		peer,
				Pattern		intf_pattern )
			{			
				boolean auto_intf = intf_pattern.pattern().equals( "auto" );
				
				boolean	result = false;
				
				NetworkInterface	ni = PeerUtils.getLocalNetworkInterface( peer );
				
				String intf_name;
				
				if ( ni != null ){
					
					intf_name = ni.getName();
					
				}else{
					
					intf_name = "?";
				}

				if ( auto_intf ){
											
					synchronized( auto_peer_set_queue_intf ){
						
						if ( !auto_peer_set_queue_intf.contains( intf_name )){
						
							auto_peer_set_queue_intf.add( intf_name );
						
							auto_peer_set_checker.dispatch();
						}
					}
					
					return( 2 );
				}
				
				if ( intf_pattern.matcher( intf_name ).find()){
					
					result = true;
				}
				
				if ( intf_pattern_inverse ){
					result = !result;
				}
				
				if ( result ){
					
					if ( isActionEnabled( TagFeatureExecOnAssign.ACTION_DESTROY )){

						return( 3 );
					}
				}
				
				return( result?1:2 );
			}
			
			private int
			canAddASN(
				PEPeer		peer,
				Pattern		asn_pattern )
			{
				boolean	result = false;
				
				String[] as_details	= PeerUtils.getASandASN( peer );
				
				if ( as_details != null ){
					
					String as 	= as_details[0];
					String asn 	= as_details[1];
					
					boolean matched = false;
					
					if ( !as.isEmpty()){
						
						if ( asn_pattern.matcher( as ).find()){
							
							result = true;
							
							matched = true;
						}
					}
					
					if ( !matched && !asn.isEmpty()){
						
						if ( asn_pattern.matcher( asn ).find()){
						
							result = true;
						}
					}
					
					if ( asn_pattern_inverse ){
						
						result = !result;
					}
				}else{
						// we want to give a bit more time for asn lookup
									
					Long start = (Long)peer.getUserData( PEER_ASN_WAIT_START_KEY );
					
					long now = SystemTime.getMonotonousTime();
					
					if ( start == null ){
						
						peer.setUserData( PEER_ASN_WAIT_START_KEY, now );
						
						return( 0 );
						
					}else if ( now - start < 20*1000 ){
					
						return( 0 );
					}
				}
				
				if ( result ){
					
					if ( isActionEnabled( TagFeatureExecOnAssign.ACTION_DESTROY )){

						return( 3 );
					}
				}
				
				return( result?1:2 );
			}
				
			private void
			add(
				PeerManager		peer_manager,
				Peer			_peer )
			{
				PEPeer peer = PluginCoreUtils.unwrap( _peer );

				if ( isActionEnabled( TagFeatureExecOnAssign.ACTION_DESTROY ) && !deferEOS()){

					peer_manager.removePeer( _peer, "PeerSet removal action", Transport.CR_IP_BLOCKED );

					return;
				}

				synchronized( this ){

					if ( peer.getPeerState() == PEPeer.TRANSFERING ){

						if ( added_peers.contains( peer )){

							return;
						}

						int can_add = canAdd( peer );
						
						if ( can_add == 0 ){
							
								// defer
							
							pending_peers.add( peer );
							
							return;
							
						}else if ( can_add == 3 ){
							
								// immediate remove
							
							peer_manager.removePeer( _peer, "PeerSet removal action", Transport.CR_IP_BLOCKED );
							
							return;
							
						}else{

							pending_peers.remove( peer );

							if ( can_add == 1 ){
							
								added_peers.add( peer );
								
							}else{
								
								deferredRemove( peer );
								
								return;
							}
						}
					}else{

						pending_peers.add( peer );

						return;
					}
				}

				addTaggable( peer );
			}

			private void
			remove(
				PeerManager		peer_manager,
				Peer			_peer )
			{
				PEPeer peer = PluginCoreUtils.unwrap( _peer );

				synchronized( this ){

					if ( pending_peers.remove( peer )){

						return;
					}

					if ( !added_peers.remove( peer )){

						return;
					}
				}

				removeTaggable( peer );
			}

			private void
			removeAll()
			{
				List<PEPeer> to_remove;

				synchronized( this ){

					pending_peers.clear();

					to_remove = new ArrayList<>(added_peers);

					added_peers.clear();
				}

				for ( PEPeer peer: to_remove ){

					removeTaggable( peer );
				}
			}

			@Override
			public void
			addTaggable(
				Taggable	t )
			{
				if ( upload_priority > 0 ){

					((PEPeer)t).updateAutoUploadPriority( UPLOAD_PRIORITY_ADDED_KEY, true );
				}

				if ( getTagBoost()){
				
					setBoost((PEPeer)t, true );
				}
				
				super.addTaggable( t );
			}

			@Override
			public void
			removeTaggable(
				Taggable	t )
			{
				if ( upload_priority > 0 ){

					((PEPeer)t).updateAutoUploadPriority( UPLOAD_PRIORITY_ADDED_KEY, false );
				}

				if ( getTagBoost()){
					
					setBoost((PEPeer)t, false );
				}
				
				super.removeTaggable( t );
			}

			@Override
			public int
			getTaggedCount()
			{
				synchronized( this ){

					return( added_peers.size());
				}
			}

			@Override
			public List<PEPeer>
			getTaggedPeers()
			{
				synchronized( this ){

					return(new ArrayList<>(added_peers));
				}
			}

			@Override
			public Set<Taggable>
			getTagged()
			{
				synchronized( this ){

					return( new HashSet<Taggable>( added_peers ));
				}
			}

			@Override
			public boolean
			hasTaggable(
				Taggable	t )
			{
				synchronized( this ){

					return( added_peers.contains( t ));
				}
			}

			@Override
			public boolean
			supportsTagRates()
			{
				return( true );
			}

			@Override
			public boolean
			supportsTagUploadLimit()
			{
				return( !has_explicit_up_lim );
			}

			@Override
			public boolean
			supportsTagDownloadLimit()
			{
				return( !has_explicit_down_lim );
			}

			@Override
			public int
			getTagUploadLimit()
			{
				return( up_limiter.getRateLimitBytesPerSecond());
			}

			@Override
			public void
			setTagUploadLimit(
				int		bps )
			{
				if ( supportsTagUploadLimit()){

					up_limiter.setRateLimitBytesPerSecond( bps );

					COConfigurationManager.setParameter( "speed.limit.handler.ipset_n." + getTagID() + ".up", bps );

						// force a resync of rates (there's a rate limit wrapper on PeerImpl that might need a kick)

					List<PEPeer> peers = getTaggedPeers();

					for ( PEPeer peer: peers ){

						for ( LimitedRateGroup l: peer.getRateLimiters( true )){

							l.getRateLimitBytesPerSecond();
						}
					}
				}
			}

			@Override
			public int
			getTagCurrentUploadRate()
			{
				return( (int)send_rate.getAverage());
			}

			@Override
			protected long[]
			getTagSessionUploadTotalCurrent()
			{
				return( new long[]{ last_send_total });
			}

			@Override
			protected long[]
			getTagSessionDownloadTotalCurrent()
			{
				return( new long[]{ last_recv_total });
			}

			@Override
			public int
			getTagDownloadLimit()
			{
				return( down_limiter.getRateLimitBytesPerSecond());
			}

			@Override
			public void
			setTagDownloadLimit(
				int		bps )
			{
				if ( supportsTagDownloadLimit()){

					down_limiter.setRateLimitBytesPerSecond( bps );

					COConfigurationManager.setParameter( "speed.limit.handler.ipset_n." + getTagID() + ".down", bps );

						// force a resync of rates (there's a rate limit wrapper on PeerImpl that might need a kick)

					List<PEPeer> peers = getTaggedPeers();

					for ( PEPeer peer: peers ){

						for ( LimitedRateGroup l: peer.getRateLimiters( false )){

							l.getRateLimitBytesPerSecond();
						}
					}
				}
			}

			@Override
			public int
			getTagCurrentDownloadRate()
			{
				return( (int)receive_rate.getAverage());
			}

			@Override
			public boolean
			getCanBePublicDefault()
			{
				return( false );
			}


			@Override
			public int
			getTagUploadPriority()
			{
				return( upload_priority );
			}

			@Override
			public void
			setTagUploadPriority(
				int		priority )
			{
				if ( priority < 0 ){

					priority = 0;
				}

				if ( priority == upload_priority ){

					return;
				}

				int	old_up = upload_priority;

				upload_priority	= priority;

				COConfigurationManager.setParameter( "speed.limit.handler.ipset_n." + getTagID() + ".uppri", priority );

				if ( old_up == 0 || priority == 0 ){

					List<PEPeer> peers = getTaggedPeers();

					for ( PEPeer peer: peers ){

						peer.updateAutoUploadPriority( UPLOAD_PRIORITY_ADDED_KEY, priority>0 );
					}
				}
			}

			@Override
			public void
			setTagBoost(
				boolean		boost )
			{
				super.setTagBoost( boost );
				
				COConfigurationManager.setParameter( "speed.limit.handler.ipset_n." + getTagID() + ".boost", boost );

				List<PEPeer> peers = getTaggedPeers();

				for ( PEPeer peer: peers ){

					setBoost( peer, boost );
				}
			}

			private void
			setBoost(
				PEPeer		pe_peer,
				boolean		boost )
			{			
				BuddyPlugin bp = getBuddyPlugin();
				
				if ( bp != null ){
				
					boolean is_boosted = pe_peer.getUserData( BOOSTED_KEY ) != null;
					
					if ( boost || is_boosted ){
						
						try{
							Download download = PluginCoreUtils.wrap( core.getGlobalManager().getDownloadManager( new HashWrapper( pe_peer.getManager().getHash())));
							
							Peer peer = PluginCoreUtils.wrap( pe_peer );
							
							if ( download != null && peer != null ){
							
								if ( boost ){
											
									if ( !is_boosted ){
									
										bp.setPartialBuddy( download, peer, true, false );
										
										pe_peer.setUserData( BOOSTED_KEY, "" );
									}
						
								}else{
									
										// must be boosted here
									
									bp.setPartialBuddy( download, peer, false, false );
									
									pe_peer.setUserData( BOOSTED_KEY, null );
								}
							}					
						}catch( Throwable e ){
							
							Debug.out( e );
						}
					}
				}
			}
			
			@Override
			public void
			removeTag()
			{
				if ( upload_priority > 0 ){

					List<PEPeer> peers = getTaggedPeers();

					for ( PEPeer peer: peers ){

						peer.updateAutoUploadPriority( UPLOAD_PRIORITY_ADDED_KEY, false );
						
						setBoost( peer, false );
					}
				}

				super.removeTag();
			}

			@Override
			public String
			getDescription()
			{
				return( getDetailString());
			}
		}
	}

	private class
	Prioritiser
	{
		private static final int		FREQ_DEFAULT	= 5;
		private static final int		MIN_DEFAULT		= 1024;
		private static final int		MAX_DEFAULT		= 100*1024*1024;
		private static final int		PROBE_DEFAULT	= 3;
		private static final int		REST_DEFAULT	= 12;

		private boolean				is_down;
		private int					freq			= FREQ_DEFAULT;
		private int					min				= MIN_DEFAULT;
		private int					max				= MAX_DEFAULT;
		private int					probe_period	= PROBE_DEFAULT;
		private String				name			= "";
		private int					rest_ticks		= REST_DEFAULT;

		private int	tick_count		= 0;

		private int	check_ticks		= 1;
		private int skip_ticks		= 0;

		private final List<Object[]>				temp_states = new ArrayList<>();

		private final List<PrioritiserTagState>	tag_states = new ArrayList<>();

		private int					phase 					= 0;
		private int					phase_0_stable_waits	= 0;
		private int					phase_0_count			= 0;


		private PrioritiserTagState	phase_1_tag			= null;
		private int					phase_1_tag_state	= 0;
		private int					phase_1_tag_rate;
		private boolean				phase_1_limit_hit;
		private int					phase_1_higher_pri_rates;
		private int					phase_1_lower_pri_decrease;

		private int					consec_limits_hit = 0;

		private int					phase_2_max_detected = 0;

		private final Map<PrioritiserTagState,int[]>		phase_2_limits = new HashMap<>();

		private int					phase_4_tag_state	= 0;

		private final Map<PrioritiserTagState,int[]>		phase_4_limits = new HashMap<>();

		private final Set<PrioritiserTagState>	wake_on_active_tags = new HashSet<>();

		private
		Prioritiser()
		{
			setFrequency( FREQ_DEFAULT );
		}

		private void
		setIsDown(
			boolean	_down )
		{
			is_down	= _down;
		}

		private void
		addTarget(
			int				priority,
			TagType			tag_type,
			String			tag_name )
		{
				// todo sort by priority I guess, maybe handle duplicate priorities

			temp_states.add( new Object[]{ tag_type, tag_name });
		}

		private void
		initialise()
		{
				// we have to delay resolution of peer-set tags until they have been setup...

			for ( Object[] entry: temp_states ){

				TagType 	tag_type 	= (TagType)entry[0];
				String		tag_name	= (String)entry[1];

				TagFeatureRateLimit tag = (TagFeatureRateLimit)tag_type.getTag( tag_name, true );

				if ( tag == null ){

					Debug.out( "Hmm, tag '" + tag_name + "' not found for " + tag_type.getTagTypeName(true));

				}else{

					PrioritiserTagState tag_state = new PrioritiserTagState( tag );

					tag_states.add( tag_state );

					setLimit(tag_state, tag_states.size()==1?max:-1, "initial" );
				}
			}
		}

		private int
		getTargetCount()
		{
			return( temp_states.size());
		}

		private void
		setFrequency(
			int		_freq )
		{
			freq	= _freq;

			check_ticks = freq*1000/PRIORITISER_CHECK_PERIOD_BASE;

			if ( check_ticks < 1 ){

				check_ticks = 1;
			}
		}

		private void
		setMinimum(
			int		_min )
		{
			min	= _min;
		}

		private void
		setMaximum(
			int		_max )
		{
			max	= _max;
		}

		private void
		setProbePeriod(
			int		_period )
		{
			probe_period = _period;
		}

		private void
		setRestTicks(
			int	ticks )
		{
			rest_ticks	= ticks;
		}

		private void
		setName(
			String	str )
		{
			name	= str;
		}

		private String
		getName()
		{
			return( name );
		}

		private void
		check()
		{
			if ( !prioritiser_enabled ){

				for ( PrioritiserTagState tag_state: tag_states ){

					tag_state.setLimit( Integer.MAX_VALUE, "disabled" );
				}

				return;
			}

			int	num_tags = tag_states.size();

			if ( skip_ticks > 0 ){

				skip_ticks--;

				int total_wakeup_rate = 0;

				for ( PrioritiserTagState tag_state: tag_states ){

					int	raw_rate = tag_state.updateAverage( true );

					if ( wake_on_active_tags.contains( tag_state )){

						boolean active = tag_state.update();

						if ( active ){

							total_wakeup_rate += raw_rate;
						}
					}
				}

				if ( total_wakeup_rate > 2048 ){

					log( "Waking up early, active tag(s) detected" );

					skip_ticks = 0;

				}else{

					return;
				}
			}

			wake_on_active_tags.clear();

			tick_count++;

			if ( tick_count % check_ticks != 0 ){

				return;
			}

			List<PrioritiserTagState>	active_tags = new ArrayList<>();

			boolean	adjusting = false;

			int	rate_available = phase_2_max_detected==0?max:phase_2_max_detected;

			for ( int i=0;i<num_tags;i++){

				PrioritiserTagState tag_state = tag_states.get(i);

				tag_state.updateAverage( false );

				int	rate = tag_state.getRate();

				boolean active = tag_state.update();

				if ( active ){

					active_tags.add( tag_state );

					if ( tag_state.isAdjusting()){

						adjusting = true;
					}
				}else{

						// not active

					int	inactive_rate;

					if ( i < num_tags/3 ){

						inactive_rate = Math.max( rate_available, 5*1024 );

					}else{

						inactive_rate = 5*1024;
					}

					tag_state.setLimit( inactive_rate, "inactive[no log]" );
				}

				rate_available -= rate;
			}

			final int	num_active = active_tags.size();

			if ( num_active == 0 ){

				return;
			}

			if ( num_active == 1 ){

				active_tags.get(0).setLimit( max, "only one active" );

				return;
			}

			if ( adjusting ){

				return;
			}

			String str = "";

			for ( int i=0;i<num_active;i++){

				PrioritiserTagState tag_state = active_tags.get(i);

				if ( !is_down ){

					tag_state.getTag().setTagUploadPriority( i <= (num_active-1)/3?1:0);
				}

				str += (str.length()==0?"":", ") + tag_state.getString();
			}

			GlobalManagerStats gm_stats = core.getGlobalManager().getStats();

			long glob = is_down?gm_stats.getSmoothedReceiveRate():gm_stats.getSmoothedSendRate();

			log( "* " + str + " [global=" + formatRate(glob,false) + "]");


				// go through the active tags and make adjustments based on whether tags are
				// achieving stable rates

				// if tag X is stable and at max then we can look to raising the limit on the next tag etc.

				// if tag X is running below max then this might be because of two things
				//     1) it can't run faster because there's no demand
				//     2) a lower ranked tag is grabbing bandwidth


				// phase 0
				// Get all tags with limits set and stable rates at that limit

				// phase 1
				// Cycle through tags from high to low
				//		find next non-max one, set to max and reduce rates of others - set limit to achieved rate
				//

			//System.out.println( "phase=" + phase );

			if ( phase == 0 ){

				boolean	all_good = true;

				if ( phase_0_stable_waits < 1 ){

					phase_0_stable_waits++;

					for ( int i=0;i<active_tags.size();i++){

						PrioritiserTagState tag_state = active_tags.get(i);

						int	limit 	= tag_state.getLimit();
						int rate	= tag_state.getRate();

						boolean	stable = tag_state.isStable();

						if ( limit == -1 ){

							limit = 0;	// no upload
						}

						if ( stable && sameRate( limit, rate )){

							// looking good

						}else{

								// if we have a probe result then we can use this to see how far away we are from that value
								// and react accordingly. In particular if we have a 'weak' tag with varying rates the we need to be more
								// lenient as it'll get hammered down

							boolean	weak_tag		= false;
							boolean	weakly_stable 	= false;

							int	probe_rate = tag_state.getProbeRate();

							if ( tag_state.getStrength() < 5 && probe_rate > 0 ){

								weak_tag = true;

								if ( rate >= 80*probe_rate/100 ){

									weakly_stable = true;
								}
							}

							if ( weakly_stable ){

								int target = Math.max( probe_rate*2, rate );

								target = Math.min( max, target );

								target -= 2048;

								if ( target < 1024 ){

									target = 1024;
								}

								tag_state.setLimit( target, "0: weak stable" );

							}else{

								all_good = false;

									// reduce limit

								if ( limit > 0 ){

									if ( stable ){

										int target = rate;

										if ( target < 1024 ){

											target = 1024;
										}

										tag_state.setLimit( target, "0: reducing to current" );

									}else{

										int target = rate - 2048;

										if ( target <= 1024 ){

											target = -1;
										}

										tag_state.setLimit( target, "0: reducing, unstable" );
									}
								}
							}
						}
					}
				}

				//System.out.println( "all good->" + all_good );

				if ( all_good ){

					phase_0_stable_waits = 0;	// ready for next time

					if ( probe_period > 0 && phase_0_count % ( probe_period + 1 ) == 0 ){

							// periodic raise to max and see if overall throughput changed

						phase_2_limits.clear();

						boolean	changed = false;

						for ( PrioritiserTagState tag: active_tags ){

							int	rate = tag.getRate();

							phase_2_limits.put( tag, new int[]{ tag.getLimit(), rate, rate });

							if ( tag.setLimit( max, "1: probing" )){

								changed = true;
							}
						}

						if ( changed ){

							phase = 2;

							skip_ticks = 1;
						}
					}

						// if we didn't start probing them move onto the test phase

					if ( phase == 0 ){

						phase = 1;

						phase_1_tag = active_tags.get(0);

						phase_1_tag_state = 0;

						phase_1_limit_hit = false;
					}
				}
			}else if ( phase == 1 ){

					// note that we only get here when things have finished 'adjusting'

				int	start_index = active_tags.indexOf( phase_1_tag );

				if ( start_index == -1 ){

						// active tag set changed and screwed us up, restart

					phase = 0;

				}else{

					boolean	stay_in_phase_1 = false;

					for ( int i=start_index;i<num_active;i++){

						PrioritiserTagState tag_state = active_tags.get(i);

						phase_1_tag = tag_state;	// in case we've come around due to current already being at max

						int current_rate = tag_state.getRate();

						int	total_rate			= 0;
						int higher_pri_rates 	= 0;

						int high_priority_strength 	= 0;
						int low_priority_strength 	= 0;

						for ( int j=0;j<num_active;j++){

							PrioritiserTagState s = active_tags.get(j);

							int rate = s.getRate();

							total_rate += rate;

							if ( j < i ){

								higher_pri_rates += rate;
							}

							if ( j <= i ){

								high_priority_strength += s.getStrength();

							}else{

								low_priority_strength += s.getStrength();
							}
						}

						if ( phase_2_max_detected > 0 && phase_2_max_detected < total_rate ){

							phase_2_max_detected = total_rate;
						}

						if ( tag_state.getLimit() != max && phase_1_tag_state == 0 ){

							// not at max, let's modify it

							for ( int j=0;j<i;j++){

								PrioritiserTagState s = active_tags.get(j);

								int rate = s.getRate();

								s.setPreTestRate( rate );
							}

							int	limits_hit = tag_state.getLimitsHit();

							int	raise_to;

							if ( limits_hit == 0 ){

								raise_to = max;

							}else{

								int	diff = max - current_rate;

								int bump = diff/(limits_hit+1);

								if ( bump < 2048 ){

									bump = 2048;
								}

								raise_to = Math.min( current_rate + bump, max );
							}

								// if we're adjusting a high priority (let's say in top third of priorities) and it
								// isn't very strong then give it more time to adjust)

							int	change_type = PrioritiserTagState.CT_NORMAL;

							if ( i < num_active / 3 ){

								if ( high_priority_strength <= low_priority_strength/2 ){

									change_type = PrioritiserTagState.CT_MEDIUM;
								}
							}

							tag_state.setLimit( raise_to, change_type, "1: raising to " + (raise_to==max?"max":formatRate( raise_to, true )) + " {" + high_priority_strength + "/" + low_priority_strength + "}");

							int	decrease_by = 2048;

							for ( int j=0;j<consec_limits_hit;j++){

								decrease_by = decrease_by*2;

								if ( decrease_by > Math.min( max/4, 10*1024 )){

									break;
								}
							}

								// move from lowest priority to highest distributing the rate decrease
								// as we go

							int	total_decrease = 0;

							for ( int j=num_active-1;j>i;j--){

								PrioritiserTagState ts = active_tags.get(j);

								int rate = ts.getRate();

								int target;
								int decrease;

								if ( rate >= decrease_by ){

									decrease = decrease_by;

									target = rate - decrease_by;

									decrease_by = 0;

								}else{

									decrease = rate;

									target = 0;

									decrease_by -= rate;
								}

								total_decrease += decrease;

								if ( target <= 1024 ){

									target = -1;
								}

								ts.setLimit( target, "1: decreasing lower priority (dec=" + formatRate(decrease,false) + ")");

								if ( decrease_by <= 0 ){

									break;
								}
							}

							phase_1_tag_state 			= 1;
							phase_1_tag_rate			= current_rate;
							phase_1_higher_pri_rates	= higher_pri_rates;
							phase_1_lower_pri_decrease	= total_decrease;

							stay_in_phase_1 = true;

								// when raising a limit things tend to bounce for a short time (bytes queued in Vuze ready
								// for delivery get flushed to OS buffers and give the appearance of an increase in delivery rate
								// that exceeds the peer's actual capacity - same true for incoming data queued in OS )

							skip_ticks = 1;

							break;

						}else{

								// is at max - might have previously been at max so ignore if so

							if ( phase_1_tag_state == 1 ){

								int	my_diff 	= current_rate - phase_1_tag_rate;
								int	hp_diff 	= higher_pri_rates - phase_1_higher_pri_rates;

								int my_target = current_rate;

								if ( my_target <= 1024 ){

									my_target = -1;

								}else if ( sameRate( my_target, max )){

									my_target = max;
								}

								int	overall_gain = my_diff + hp_diff - phase_1_lower_pri_decrease;

								int hp_drop = -hp_diff;

								boolean limit_hit = hp_drop > 0 && my_diff > 0;

								if ( limit_hit ){

									// higher priority has dropped, need to assess as to whether or not this is significant
									// as they can be volatile if few peers

									// if overall stuff went up by 100k while hp dropped by 2k then even if this has
									// pushed us to saturation it is a pretty good bet that clamping our limit to something a
									// bit lower will bring us back under saturation and push the higher priorities back up

									if ( hp_drop <= 1024 || hp_drop <= ( 3*phase_1_higher_pri_rates/100 )){

										limit_hit = false;	// ignore very small drops

									}else if ( 	hp_drop <= 10*1024  &&
												overall_gain >= 3 * hp_drop ){

											// only assume hp can grab bandwidth back if it has
											// sufficient strength to do so

										if ( high_priority_strength >= low_priority_strength ){

											limit_hit = false;

											my_target = my_target - 3 * hp_drop;

											if ( my_target <= 1024 ){

												my_target = -1;
											}
										}
									}
								}

								if ( limit_hit ){

										// another test - if we have probe results then give weak tags the benefit of the doubt if they're
										// still above this limit

									boolean stick_with_decision = false;

									for ( int j=0;j<i;j++){

										PrioritiserTagState s = active_tags.get(j);

										int	pre_rate	= s.getPreTestRate();
										int rate 		= s.getRate();

										int	diff = rate - pre_rate;

										if ( diff < 0 ){

												// went down

											if ( s.getStrength() < 5 ){

													// if it went down by more than 25% then that ain't good - remember that
													// the probe value can be a bad indicator of capability

												if ( -diff >= pre_rate/4 ){

													stick_with_decision = true;

													break;
												}

												int	probe_rate = s.getProbeRate();

												if ( probe_rate <= 0 || rate < 110*probe_rate/100 ){

														// weak one went down but previous probe rate doesn't
														// let it off teh hook

													stick_with_decision = true;

													break;
												}
											}else{

													// relative strong one went down, take it at face value

												stick_with_decision = true;

												break;
											}

										}else{

											// went up, ignore
										}
									}

									if ( !stick_with_decision ){

										limit_hit = false;

										log( "Ignoring limit indicator as weak tags within probed limits (diffs=" + formatRate( hp_diff, false ) + "/" + formatRate( my_diff, false ));
									}
								}

								if ( limit_hit ){

										// verify the limit hitting is real and not just a fluctuation (if small)

									if ( phase_1_limit_hit || hp_drop > 10*1024 ){

											// higher priorities total rate has dropped as a result on increasing the limit on this tag (and depressing the lower priorities a bit)
											// so we don't want to carry on probing the limits of lower priority tags
											// we want to try and shunt bandwidth from this tag back to the higher priority ones


											// make sure that the new target is definitely a bit lower than it used to be
											// so that some bandwidth from this tag gets hopefully shunted 'left'

											// actually the above logic isn't great as it can cause a tag to get stuck at a low rate

										if ( overall_gain > 4*1024 ){

												// reasonable gain, nudge the rate up from what it was before the experiment
												// by a bit

											my_target = phase_1_tag_rate + (overall_gain/4);

										}else{

												// initial target is what we're currently achieving minus whatever
												// was lost by the higher priority tags

											my_target = current_rate + hp_diff;

												// not a good gain so make sure we're below the original

											my_target = Math.min( my_target, phase_1_tag_rate - 2048 );
										}

										if ( my_target <= 1024 ){

											my_target = -1;
										}

										consec_limits_hit++;

										tag_state.hitLimit( true );

										tag_state.setLimit( my_target, PrioritiserTagState.CT_MAJOR, "1: adjusting after limit hit (diffs=" + formatRate( hp_diff, false ) + "/" + formatRate( my_diff, false ) + ", consec=" + consec_limits_hit + ")" );

											// decrease lower priority limits agressively as any bandwidth they are consuming
											// needs to be pushed our way

										int	low_pri_rates = 0;

										for ( int j=num_active-1;j>i;j--){

											PrioritiserTagState ts = active_tags.get(j);

											low_pri_rates += ts.getRate();
										}

										int	decrease_by = low_pri_rates/4;

										decrease_by = Math.min( decrease_by, 32*1024 );

										int	total_decrease = 0;

										for ( int j=active_tags.size()-1;j>i;j--){

											if ( decrease_by <= 0 ){

												break;
											}

											PrioritiserTagState ts = active_tags.get(j);

											int rate = ts.getRate();

											int target;
											int decrease;

											if ( rate >= decrease_by ){

												decrease = decrease_by;

												target = rate - decrease_by;

												decrease_by = 0;

											}else{

												decrease = rate;

												target = 0;

												decrease_by -= rate;
											}

											total_decrease += decrease;

											if ( target <= 1024 ){

												target = -1;
											}

											ts.setLimit( target, "1: decreasing lower priority (dec=" + formatRate(decrease,false) + ")");
										}

										// don't set stay_in_phase_1 as we want to exit
									}else{

											// hit limit for the first time, verify

										phase_1_limit_hit = true;

										tag_state.hitLimit( true );

										tag_state.setLimit( phase_1_tag_rate, PrioritiserTagState.CT_MAJOR, "1: limit hit (diffs=" + formatRate( hp_diff, false ) + "/" + formatRate( my_diff, false ) + ", verifying" );

										phase_1_tag_state = 0;

										stay_in_phase_1 = true;
									}
								}else{

									phase_1_limit_hit = false;

									tag_state.hitLimit( false );

									tag_state.setLimit( my_target, "1: setting to current (diffs=" + formatRate( hp_diff, false ) + "/" + formatRate( my_diff, false ) + ")" );

									if ( i < num_active - 1 ){

											// time to consider whether or not we should progress to testing
											// the next tag.
											// if we have a previous probe result and the tags up to this point
											// are close to it then don't

										boolean	quit_now = false;

										if ( phase_2_max_detected > 0 ){

											int	hp_rate = 0;

											for ( int j=0;j<=i;j++){

												hp_rate += active_tags.get(j).getRate();
											}

											if ( hp_rate >= 90*phase_2_max_detected/100 ){

												quit_now = true;
											}
										}

										if ( quit_now ){

											log( "Higher priority tags satisfy 90% of last probe result (" + formatRate( phase_2_max_detected, false ) + ")" );

										}else{

											phase_1_tag			= active_tags.get( i+1 );

											phase_1_tag_state = 0;

											stay_in_phase_1 = true;
										}
									}else{

											// got to end of tags without hitting limit

										consec_limits_hit = 0;
									}
								}

								break;

							}else{

									// tag already at max, don't touch it and move on
							}
						}
					}

					if ( !stay_in_phase_1 ){

							// time for a rest phase

						phase = 3;
					}
				}
			}else if ( phase == 2 ){

					// probe result

				long	old_total_rate	= 0;
				long	new_total_rate	= 0;

				long	total_inc	= 0;

				String probe_str = "";

				boolean	tag_rate_went_down = false;

				for ( PrioritiserTagState tag: active_tags ){

					int[] entry = phase_2_limits.get( tag );

					if ( entry != null ){

						int	old_rate	= entry[1];

						old_total_rate += old_rate;

						int new_rate	= tag.getRate();

						tag.setProbeRate( new_rate );

						new_total_rate += new_rate;

						entry[2] = new_rate;

						int	inc = new_rate - old_rate;

						if ( inc > 0 ){

							total_inc += inc;

							probe_str += (probe_str.length()==0?"":", ") + tag.getTagName() + " +" + formatRate( inc, false );

						}else if ( inc < -1024 ){

							tag_rate_went_down = true;
						}
					}
				}

				long	diff = new_total_rate - old_total_rate;

				phase_2_max_detected = (int)new_total_rate;

				log( "Probe result: before=" + formatRate( old_total_rate, false ) + ", after=" + formatRate( new_total_rate, false ) + ", inc=" + formatRate( total_inc, false ) + " [" + probe_str + "]" );

					// I wanted to use the probe results to adjust limits but unfortunately this puts things out
					// of whack when lower priorities grab bandwidth and weak higher priorities take a long
					// time to grab it back. So adjusted this so that only if nothing went down

				int 	major_done 		= 0;
				int 	major_skipped 	= 0;

				for ( Map.Entry<PrioritiserTagState,int[]> entry: phase_2_limits.entrySet()){

					PrioritiserTagState tag = entry.getKey();

					int[]	vals = entry.getValue();

					int	limit 		= vals[0];

					int change_type;

					if ( tag_rate_went_down ){

						change_type = PrioritiserTagState.CT_MAJOR;

					}else{

						change_type = PrioritiserTagState.CT_NORMAL;

						if ( diff > 0 && total_inc > 0 ){

							int	old_rate	= vals[1];
							int	new_rate	= vals[2];

							int	inc = new_rate - old_rate;

							if ( inc > 0 ){

								limit = limit + (int)((inc*diff)/total_inc);

								if ( limit > max ){

									limit = max;
								}
							}else{

									// rate went down for this tag as a result of probing so give it
									// a decent time to recover otherwise it'll get chopped back

								change_type = PrioritiserTagState.CT_MAJOR;
							}
						}else{

								// things didn't work out well

							change_type = PrioritiserTagState.CT_MAJOR;
						}
					}

					boolean did_it = tag.setLimit( limit, change_type, "2: probe result" );

					if ( change_type != PrioritiserTagState.CT_NORMAL ){

						if ( did_it ){

							major_done++;

						}else{

							major_skipped++;
						}
					}
				}

					// handle case where we switch limits back to where they were - in this case we want
					// to ensure that some time is given for adjustment

				if ( major_skipped > 0 && major_done == 0 ){

					skip_ticks = 2;
				}

					// after probing start a new cycle

				phase = 0;

				phase_0_count++;

			}else if ( phase == 3 ){

					// introduced the concept of a rest-period if we have a probe result and the current
					// rate is relatively close to it

				if ( rest_ticks > 0 && phase_2_max_detected > 0 ){

					int	current_rate = 0;

					for ( PrioritiserTagState tag: active_tags ){

						int	rate = tag.getRate();

						current_rate += rate;
					}

					/*
					if ( current_rate >= 75*phase_2_max_detected/100 ){

						log( "Resting..." );

						skip_ticks = rest_ticks;
					}
					*/

						// interpolate based on %age achieved

					int	achieved = current_rate*100/phase_2_max_detected;

					achieved += 10;	// add in 10 % so that 90->100 gets max rest

					skip_ticks = Math.min( rest_ticks, achieved*rest_ticks/100 );

						// might as well bang the highest priority limit up to max while resting

					active_tags.get(0).setLimit( max, "resting" );

					for ( int i=0;i<(num_tags+2)/3;i++){

						PrioritiserTagState tag = tag_states.get(i);

						if ( !active_tags.contains( tag )){

							wake_on_active_tags.add( tag );
						}
					}

					log( "Resting for " + skip_ticks );

						// after resting try a mini-probe to pick up any significant high priority changes

					phase_4_tag_state	= 0;

					phase = 4;

				}else{

					phase = 0;

					phase_0_count++;
				}
			}else if ( phase == 4 ){

				if ( phase_4_tag_state == 0 ){

					phase_4_limits.clear();

					boolean	changed = false;

					int	cutoff = ( num_active + 2 ) / 3;

					for ( int i=0;i<num_active;i++){

						PrioritiserTagState tag = active_tags.get(i);

						int	limit 	= tag.getLimit();
						int	rate 	= tag.getRate();

						phase_4_limits.put( tag, new int[]{ limit, rate, rate, i<cutoff?0:1 });

						if ( i < cutoff ){

							if ( tag.setLimit( max, "4: mini-probing" )){

								changed = true;
							}
						}else{

							if ( !changed ){

									// if we didn't raise any hp limits no point in proceeding to lower the lp
									// ones

								break;
							}

							int lim = (9*rate)/10;

							if ( lim < 1024 ){

								lim = 1024;
							}

							tag.setLimit( lim, "4: mini-probing" );
						}
					}

					if ( changed ){

						phase_4_tag_state = 1;

						skip_ticks = 1;

					}else{

						phase = 0;

						phase_0_count++;
					}
				}else{

						// results are in

					int	total_inc = 0;

					String probe_str = "";

					for ( Map.Entry<PrioritiserTagState,int[]> entry: phase_4_limits.entrySet()){

						PrioritiserTagState tag 	= entry.getKey();
						int[]				details	= entry.getValue();

						if ( active_tags.contains( tag )){

							boolean hp = details[3] == 0;

							if ( hp ){

								int old_rate	= details[1];

								int new_rate 	= tag.getRate();

								details[2] = new_rate;

								int inc = new_rate - old_rate;

								if ( inc > 0 ){

									if ( tag.getProbeRate() < new_rate ){

										tag.setProbeRate( new_rate );
									}

									total_inc += inc;

									probe_str += (probe_str.length()==0?"":", ") + tag.getTagName() + " +" + formatRate( inc, false );

								}
							}
						}
					}

					log( "Mini-probe result: inc=" + formatRate( total_inc, false ) + " [" + probe_str + "]" );

					if ( total_inc > 10*1024 ){

						// leave things as they are

					}else{

						// put the limits back

						for ( Map.Entry<PrioritiserTagState,int[]> entry: phase_4_limits.entrySet()){

							PrioritiserTagState tag 	= entry.getKey();
							int[]				details	= entry.getValue();

							tag.setLimit( details[0], "4: reverting" );
						}

						skip_ticks = 1;
					}

					phase = 0;

					phase_0_count++;
				}
			}
		}

		private String
		formatRate(
			long		rate,
			boolean		is_limit )
		{
			if ( rate == -1 && is_limit ){

				return( "x" );

			}else if ( rate < 0 ){

				return( "-" + DisplayFormatters.formatByteCountToKiBEtcPerSec(-rate));

			}else if ( ( rate == 0 || rate >= MAX_DEFAULT ) && is_limit ){

				return( Constants.INFINITY_STRING );

			}else{

				return( DisplayFormatters.formatByteCountToKiBEtcPerSec(rate));
			}
		}

		private boolean
		setLimit(
			PrioritiserTagState 	tag_state,
			int						rate,
			String					reason )
		{
				// round a bit here to avoid sillyness if rate changes by fraction of a kb/sec

			if ( rate > 1024 ){

				rate = (rate/256)*256;
			}

			TagFeatureRateLimit tag = tag_state.getTag();

			if ( is_down ){

				if ( rate != tag.getTagDownloadLimit()){

					tag.setTagDownloadLimit( rate );

					if ( !reason.contains( "[no log]" )){

						log( tag_state, "->" + formatRate( rate, true ) + " (" + reason + ")");
					}

					return( true );
				}
			}else{

				if ( rate != tag.getTagUploadLimit()){

					tag.setTagUploadLimit( rate );

					if ( !reason.contains( "[no log]" )){

						log( tag_state, "->" + formatRate( rate, true ) + " (" + reason + ")");
					}

					return( true );
				}
			}

			return( false );
		}

		private boolean
		sameRate(
			int	r1,
			int	r2 )
		{
			int	diff =  Math.abs( r1 - r2 );

			if ( diff <= 1024 ){

				return( true );
			}

			int max = Math.max( r1, r2 );

				// within 3%

			return( max*3/100 >= diff );
		}

		private void
		log(
			PrioritiserTagState		tag_state,
			String					str )
		{
			log( tag_state.getTagName() + ": " + str );

		}
		private void
		log(
			String			str )
		{
			if ( name.length() > 0 ){

				logger.log( "priority " + name + ": " + str );

			}else{

				logger.log( "priority: " + str );
			}

		}

		class
		PrioritiserTagState
		{
			private static final int STABLE_PERIODS				= 2;
			private static final int AVERAGE_PERIODS			= 3;

			private static final int ADJUSTMENT_PERIODS			= 2;
			private static final int INITIAL_ADJUSTMENT_PERIODS	= 4;

			private static final int CT_NORMAL	= 0;
			private static final int CT_MEDIUM	= 1;
			private static final int CT_MAJOR	= 2;

			private final TagFeatureRateLimit		tag;

			private final MovingImmediateAverage average = AverageFactory.MovingImmediateAverage( AVERAGE_PERIODS );

			private final int[] 	last_averages = new int[ STABLE_PERIODS ];

			private int		active_ticks	= 0;

			private int		last_average_index;

			private boolean	last_stable;
			private int		last_rate;
			private int		last_limit;

			private int		adjusting_ticks = INITIAL_ADJUSTMENT_PERIODS;

			private int		tag_limits_hit;

			private int		strength;
			private int		probe_rate	= -1;
			private int		pre_test_rate;

			private
			PrioritiserTagState(
				TagFeatureRateLimit		_tag )
			{
				tag	= _tag;
			}

			private String
			getTagName()
			{
				return( tag.getTag().getTagName( true ));
			}

			private int
			getWeight(
				List<PEPeer>		peers )
			{
				int	weight = 0;

				for ( PEPeer peer: peers ){

					if ( peer.getPeerState() == PEPeer.TRANSFERING ){

						weight++;
					}
				}

				return( weight );
			}

			private boolean
			update()
			{
				Tag t = tag.getTag();

				int	weight = 0;

				if ( t instanceof TagDownload ){

					Set<DownloadManager> downloads = ((TagDownload)tag).getTaggedDownloads();

					for ( DownloadManager dm: downloads ){

						PEPeerManager pm = dm.getPeerManager();

						if ( pm != null ){

							if ( is_down && dm.isDownloadComplete( false )){

								continue;
							}

							LimitedRateGroup[] limiters = dm.getRateLimiters( !is_down );

							boolean disabled = false;

							for ( LimitedRateGroup rl: limiters ){

								disabled = rl.isDisabled();

								if ( disabled ){

									break;
								}
							}

							if ( !disabled ){

								List<PEPeer> peers = pm.getPeers();

								weight += getWeight( peers );
							}
						}
					}
				}else{

					List<PEPeer> peers = ((TagPeer)tag).getTaggedPeers();

					weight = getWeight( peers );
				}

				strength = weight;

				if ( weight > 0 ){

					active_ticks++;

					return( active_ticks > 1 );

				}else{

					active_ticks = 0;

					return( false );
				}
			}

			/*
			private void
			updateAverageOld(
				boolean		is_skip_cycle )
			{
				if ( is_skip_cycle ){

					return;
				}

				int	rate;
				int	limit;

				if ( is_down ){

					rate 	= tag.getTagCurrentDownloadRate();
					limit	= tag.getTagDownloadLimit();
				}else{

					rate 	= tag.getTagCurrentUploadRate();
					limit	= tag.getTagUploadLimit();
				}

				if ( limit == -1 ){

					rate = 0;

				}else  if ( rate > limit ){

					rate = limit;
				}

				int average_rate = (int)average.update( rate );

				boolean	 stable = true;

				for ( int la: last_averages ){

					if ( !sameRate( average_rate, la )){

						stable = false;
					}
				}

				last_averages[last_average_index++%last_averages.length] = average_rate;

				last_limit	= limit;
				last_rate 	= average_rate;
				last_stable	= stable;

				//System.out.println( tag.getTagName( true ) + " -> rate=" + average_rate + ", stable=" + stable );

			}
			*/

			private long	last_byte_count 	= -1;
			private long	last_average_time	= 0;

			private int
			updateAverage(
				boolean		is_skip_cycle )
			{
				long	now = SystemTime.getMonotonousTime();

				long[]	current_byte_counts;

				int	rate;
				int	limit;

				if ( is_down ){

					current_byte_counts = tag.getTagDownloadTotal();
					limit				= tag.getTagDownloadLimit();

				}else{

					current_byte_counts	= tag.getTagUploadTotal();
					limit				= tag.getTagUploadLimit();
				}

				long	current_byte_count	= 0;

				for ( long l: current_byte_counts ){

					current_byte_count += l;
				}


				if ( last_byte_count == -1 ){

					rate	= 0;

				}else{

					long	diff_bytes 	= current_byte_count - last_byte_count;
					long	diff_time	= now - last_average_time;

					if ( diff_time <= 0 ){

						rate = 0;

					}else{

						rate = (int)((diff_bytes*1000)/diff_time);
					}
				}

				last_byte_count		= current_byte_count;
				last_average_time	= now;

				if ( !is_skip_cycle ){

					if ( adjusting_ticks > 0 ){

						adjusting_ticks--;
					}

					if ( limit == -1 ){

						rate = 0;

					}else  if ( rate > limit ){

						rate = limit;
					}

					int average_rate = (int)average.update( rate );

					boolean	 stable = true;

					for ( int la: last_averages ){

						if ( !sameRate( average_rate, la )){

							stable = false;
						}
					}

					last_averages[last_average_index++%last_averages.length] = average_rate;

					last_limit	= limit;
					last_rate 	= average_rate;
					last_stable	= stable;

					//System.out.println( tag.getTag().getTagName( true ) + " -> rate=" + average_rate + ", stable=" + stable );
				}

				return( rate );
			}

			private TagFeatureRateLimit
			getTag()
			{
				return( tag );
			}

			private int
			getLimit()
			{
				return( last_limit );
			}

			private int
			getRate()
			{
				return( last_rate );
			}

			private boolean
			isStable()
			{
				return( last_stable );
			}

			private boolean
			isAdjusting()
			{
				return( adjusting_ticks > 0 );
			}

			public int
			getStrength()
			{
				return( strength );
			}

			private int
			getLimitsHit()
			{
				return( tag_limits_hit );
			}

			private void
			hitLimit(
				boolean	b )
			{
				if ( b ){

					tag_limits_hit++;

				}else{

					tag_limits_hit = 0;
				}
			}

			private boolean
			setLimit(
				int		limit,
				String	reason )
			{
				return( setLimit( limit, CT_NORMAL, reason ));
			}

			private boolean
			setLimit(
				int		limit,
				int		change_type,
				String	reason )
			{
				if ( limit == Integer.MAX_VALUE ){

					limit = 0;

				}else if ( limit < min ){

					limit	= min;

				}else if ( limit > max ){

					limit 	= max;
				}

				if ( change_type == CT_MEDIUM ){

					reason += " (medium)";

				}else if ( change_type == CT_MAJOR ){

					reason += " (major)";
				}

				if ( Prioritiser.this.setLimit( this, limit, reason )){

					last_limit		= limit;

					average.reset();

					adjusting_ticks = ADJUSTMENT_PERIODS;

					if ( change_type == CT_MEDIUM  ){

						adjusting_ticks += 1;

					}else if ( change_type == CT_MAJOR ){

						adjusting_ticks = adjusting_ticks * 2;
					}

					return( true );

				}else{

					return( false );
				}
			}

			private void
			setProbeRate(
				int		rate )
			{
				probe_rate	 = rate;
			}

			private int
			getProbeRate()
			{
				return( probe_rate );
			}

			private void
			setPreTestRate(
				int		rate )
			{
				pre_test_rate	 = rate;
			}

			private int
			getPreTestRate()
			{
				return( pre_test_rate );
			}

			private String
			getString()
			{
				String str = getTagName() + "=" + formatRate( getRate(), false) +
				" (" + formatRate( getLimit(), true ) + ") {" + getStrength() + (probe_rate<=0?"":("/"+formatRate(probe_rate,false))) + "}";


				return( str );
			}
		}
	}
}
