/* Written and copyright 2001-2003 Tobias Minich.
 *
 * ConfigurationDefaults.java
 *
 * Created on 31. Juli 2003, 21:31
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

package com.biglybt.core.config.impl;

import java.io.File;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ConfigKeys.Tag;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.speedmanager.impl.SpeedManagerImpl;
import com.biglybt.core.speedmanager.impl.v2.SMConst;
import com.biglybt.core.speedmanager.impl.v2.SpeedLimitConfidence;
import com.biglybt.core.speedmanager.impl.v2.SpeedLimitMonitor;
import com.biglybt.core.speedmanager.impl.v2.SpeedManagerAlgorithmProviderV2;
import com.biglybt.core.stats.StatsWriterPeriodic;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.tracker.host.TRHost;
import com.biglybt.core.tracker.server.TRTrackerServer;
import com.biglybt.core.util.*;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerFactory;

/**
 * Some (proposed) option naming conventions:
 * - Starts with a general identifier
 *   General_ for, well, general things =)
 *   Server_ for webinterface specific things
 *   GUI_ (eventually) for GUI specific things
 *   Core_ (eventually) for core specific things
 * - Second is some identifing term. It starts with a small letter denoting
 *   the variable type.
 *   b Boolean
 *   i Integer
 *   s String
 * - Directory options should end with _Directory. This activates some
 *   special validity checks in the webinterface option parsing code.
 *   (Namely they are created if they don't exist and the option isn't changed
 *   with a logged error if a normal file of the same name exists)
 *
 * @author Tobias Minich
 */

public class ConfigurationDefaults {

  private static final Long ZERO	= new Long(0);
  private static final Long ONE		= new Long(1);
  private static final Long SIXTY	= new Long(60);

  private static final Long FALSE	= ZERO;
  private static final Long TRUE	= ONE;

  private static ConfigurationDefaults configdefaults;
  private static final AEMonitor				class_mon	= new AEMonitor( "ConfigDef");

  private ConcurrentHashMapWrapper<String,Object> def = null;

  public static final int def_int = 0;
  public static final long def_long = 0;
  public static final float def_float = 0;
  public static final int def_boolean = 0;
  public static final String def_String = "";
  public static final byte[] def_bytes = null;

  public static final String DEFAULT_FILE_CONVERSION_CHARS = "\"='";


  private final Hashtable parameter_verifiers	= new Hashtable();
	public static final String CFG_TORRENTADD_OPENOPTIONS_MANY = "many";
	public static final String CFG_TORRENTADD_OPENOPTIONS_ALWAYS = "always";
	public static final String CFG_TORRENTADD_OPENOPTIONS_NEVER = "never";
	public static final String CFG_TORRENTADD_OPENOPTIONS = "ui.addtorrent.openoptions";
	public static final String CFG_TORRENTADD_OPENOPTIONS_SEP = "ui.addtorrent.openoptions.sep";

  public static ConfigurationDefaults
  getInstance()
  {
  	try{
  		class_mon.enter();

	    if(configdefaults == null){

	    	try{
	    		configdefaults = new ConfigurationDefaults();

	    	}catch( Throwable e ){

	    			// this is here for when we are just using a few of the client classes and
	    			// we can live with no defaults (e.g. swing webui). If we initialise
	    			// the normal config-defaults fully this pulls in all sorts of unwanted
	    			// classes (platform manager for example). Also, don't using Debug/Logging
	    			// to record this fact!

	    		System.out.println( "Falling back to default defaults as environment is restricted" );

	    		configdefaults = new ConfigurationDefaults( new HashMap());
	    	}
	    }

	    return configdefaults;

  	}finally{

  		class_mon.exit();
  	}
  }

  /** Creates a new instance of Defaults */
  protected
  ConfigurationDefaults()
  {
    def = new ConcurrentHashMapWrapper<>(2000, 0.75f, 8);


    /** Core settings **/

    def.put("Override Ip", "");
    def.put("Enable incremental file creation", FALSE);
    def.put("Enable reorder storage mode", FALSE);
    def.put("Reorder storage mode min MB", new Long( 10 ));

    def.put("TCP.Listen.Port", new Long( 6881 ));
    def.put("TCP.Listen.Port.Enable", TRUE );
    def.put("TCP.Listen.Port.Override", "");
    def.put("UDP.Listen.Port", new Long( 6881 ));
    def.put("UDP.Listen.Port.Enable", TRUE );
    def.put("UDP.NonData.Listen.Port", new Long( 6881 ));	// two effective enablers for this, dht + tracker udp client
    def.put("UDP.NonData.Listen.Port.Same", TRUE );			// control over whether non-data and data udp port are the same
    def.put("HTTP.Data.Listen.Port", new Long( Constants.isWindows?80:8080 ));
    def.put("HTTP.Data.Listen.Port.Override", ZERO);
    def.put("HTTP.Data.Listen.Port.Enable", FALSE );
    def.put("Listen.Port.Randomize.Enable", FALSE );
    def.put("Listen.Port.Randomize.Together", TRUE );
    def.put("Listen.Port.Randomize.Range", RandomUtils.LISTEN_PORT_MIN + "-" + RandomUtils.LISTEN_PORT_MAX );
    def.put(ConfigKeys.Connection.BCFG_PEERCONTROL_TCP_PUBLIC_ENABLE, TRUE );
    def.put(ConfigKeys.Connection.BCFG_PEERCONTROL_UDP_PUBLIC_ENABLE, TRUE );
    def.put(ConfigKeys.Connection.BCFG_WEBSEED_ACTIVATION_USES_AVAILABILITY, TRUE );
    def.put(ConfigKeys.Connection.BCFG_WEBSEED_ACTIVATION_MIN_SPEED_KBPS, 5 );
    def.put("IPV6 Enable Support", FALSE );
    def.put(ConfigKeys.Connection.BCFG_IPV_6_CHECK_MULTIPLE_ADDRESS_CHECKS, TRUE );
    def.put("IPV6 Prefer Addresses",FALSE );
    def.put("IPV4 Prefer Stack", FALSE );
    def.put(ConfigKeys.Connection.BCFG_IPV_4_IGNORE_NI_ADDRESSES, FALSE );
    def.put(ConfigKeys.Connection.BCFG_IPV_6_IGNORE_NI_ADDRESSES, FALSE );

    def.put("max active torrents", new Long(4));
    def.put("max downloads", new Long(4));
    def.put("min downloads", ONE);
    def.put("Newly Seeding Torrents Get First Priority", TRUE);

    def.put("Max.Peer.Connections.Per.Torrent", new Long(COConfigurationManager.CONFIG_DEFAULT_MAX_CONNECTIONS_PER_TORRENT));
    def.put("Max.Peer.Connections.Per.Torrent.When.Seeding", new Long(COConfigurationManager.CONFIG_DEFAULT_MAX_CONNECTIONS_PER_TORRENT/2));
    def.put("Max.Peer.Connections.Per.Torrent.When.Seeding.Enable", TRUE );
    def.put("Max.Peer.Connections.Total", new Long(COConfigurationManager.CONFIG_DEFAULT_MAX_CONNECTIONS_GLOBAL));
    def.put("Non-Public Peer Extra Slots Per Torrent", 2 );
    def.put("Non-Public Peer Extra Connections Per Torrent", 4 );

    def.put( "Peer.Fast.Initial.Unchoke.Enabled", FALSE );

    def.put( "File Max Open", new Long(50));
    def.put( "Use Config File Backups", TRUE);

    def.put( "Max Uploads", new Long(4) );
    def.put( "Max Uploads Seeding", new Long(4));
    def.put( "enable.seedingonly.maxuploads", FALSE );
    def.put( "max.uploads.when.busy.inc.min.secs", new Long( 30 ));
    def.put( "Max Download Speed KBs", ZERO );
    def.put( "Down Rate Limits Include Protocol", TRUE );
    def.put( "Use Request Limiting", TRUE );
    def.put( "Use Request Limiting Priorities", TRUE );
    def.put( "Max Upload Speed KBs", ZERO);
    def.put( "Max Upload Speed Seeding KBs", ZERO );
    def.put( "Up Rate Limits Include Protocol", FALSE );
    def.put( "enable.seedingonly.upload.rate", FALSE );
    def.put( "Max Seeds Per Torrent", ZERO);

    //def.put( "Auto Upload Speed Enabled", FALSE );
    def.put( TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY, FALSE ); //"Auto Upload Speed Enabled"
    def.put( "Auto Upload Speed Seeding Enabled", FALSE );
    def.put( "AutoSpeed Available", FALSE );	// informative read-only parameter
    def.put( "AutoSpeed Min Upload KBs", ZERO );
    def.put( "AutoSpeed Max Upload KBs", ZERO );
    def.put( "AutoSpeed Max Increment KBs", ONE);
    def.put( "AutoSpeed Max Decrement KBs", new Long(4));
    def.put( "AutoSpeed Choking Ping Millis", new Long(200) );
    def.put( "AutoSpeed Download Adj Enable", FALSE );
    def.put( "AutoSpeed Download Adj Ratio", "1.0" );
    def.put( "AutoSpeed Latency Factor", new Long(50));
    def.put( "AutoSpeed Forced Min KBs", new Long(4));
    def.put( "Auto Upload Speed Debug Enabled", FALSE );

    def.put( "Auto Adjust Transfer Defaults", TRUE );	// modified by config checker if user has set own values

    def.put( "Bias Upload Enable", TRUE );
    def.put( "Bias Upload Slack KBs", new Long( 5 ));
    def.put( "Bias Upload Handle No Limit", TRUE );

    def.put( "ASN Autocheck Performed Time", ZERO );

    def.put( "LAN Speed Enabled", TRUE );
    def.put( "Max LAN Download Speed KBs", ZERO );
    def.put( "Max LAN Upload Speed KBs", ZERO );

    def.put("On Resume Recheck All", FALSE);
    def.put("Save Resume Interval", new Long(5));
    def.put("Check Pieces on Completion", TRUE);
    def.put("Merge Same Size Files", TRUE );
    def.put("Merge Same Size Files Extended", FALSE );
    def.put("Merge Same Size Files Tolerance", ZERO );
    def.put(ConfigKeys.File.ICFG_MERGE_SAME_SIZE_FILES_MIN_PIECES, 5 );
    def.put(ConfigKeys.File.BCFG_SKIP_COMP_DL_FILE_CHECKS, FALSE );
    def.put(ConfigKeys.File.BCFG_FILE_MOVE_ORIGIN_DELETE_FAIL_IS_WARNING, FALSE );
    def.put("Stop Ratio", new Float(0));
    def.put("Stop Peers Ratio", ZERO);
    def.put("Disconnect Seed", TRUE);
    def.put("Seeding Piece Check Recheck Enable", TRUE );
    def.put("priorityExtensions", "");
    def.put("priorityExtensionsIgnoreCase", FALSE);
    def.put("file.auto.sequential.exts", "");
    def.put("quick.view.exts", ".nfo;.txt;.rar;.gif;.jpg;.png;.bmp" );
    def.put("quick.view.maxkb", new Long( 512 ));

    def.put("Rename Incomplete Files", FALSE );
    def.put("Rename Incomplete Files Extension", ".az!" );

    def.put("Enable Subfolder for DND Files", FALSE );
    def.put("Subfolder for DND Files", ".dnd_az!" );
    def.put("Max File Links Supported", 2048 );

    def.put("Ip Filter Enabled", TRUE);
    def.put("Ip Filter Allow",FALSE);
    def.put("Ip Filter Enable Banning", TRUE);
    def.put("Ip Filter Disable For Updates", FALSE);
    def.put("Ip Filter Ban Block Limit", new Long(4));
    def.put("Ip Filter Ban Discard Ratio", "5.0" );
    def.put("Ip Filter Ban Discard Min KB", new Long(128));
    def.put("Ip Filter Banning Persistent", TRUE);
    def.put(ConfigKeys.IPFilter.BCFG_IP_FILTER_DONT_BAN_LAN, FALSE );
    def.put("Ip Filter Enable Description Cache", TRUE);
    def.put(ConfigKeys.IPFilter.SCFG_IP_FILTER_AUTOLOAD_FILE, "" );
    def.put(ConfigKeys.IPFilter.SCFG_IP_FILTER_V6_AUTOLOAD_FILE, "");
    def.put(ConfigKeys.IPFilter.ICFG_IP_FILTER_AUTOLOAD_LAST, ZERO );
    def.put(ConfigKeys.IPFilter.ICFG_IP_FILTER_AUTOLOAD_DAYS, 7L );
    def.put("Ip Filter Clear On Reload", TRUE );

    def.put("Allow Same IP Peers",FALSE);
    def.put(ConfigKeys.Transfer.ICFG_IPv4_IPv6_CONN_ACTION, ZERO );
    def.put("Use Super Seeding",FALSE);
    def.put("Enable Light Seeding", TRUE);
    def.put("StartStopManager_bAddForDownloadingSR1", TRUE);
    
    def.put("Start On Login", FALSE );
    def.put("Start In Low Resource Mode", FALSE );
    def.put("LRMS UI", TRUE );
    def.put("LRMS UDP Peers", TRUE );
    def.put("LRMS DHT Sleep", TRUE );
    
    def.put("Auto Register App", FALSE );

    def.put("Pause Downloads On Exit", FALSE );
    def.put("Resume Downloads On Start", FALSE );
    def.put("On Downloading Complete Do", "Nothing" );
    def.put("On Seeding Complete Do", "Nothing" );
    def.put("Stop Triggers Auto Reset", TRUE );
    def.put("Prompt To Abort Shutdown", TRUE );
    def.put("Prevent Sleep Downloading", TRUE );
    def.put("Prevent Sleep FP Seeding", FALSE );
    def.put("Prevent Sleep Tag", "" );

    def.put("Auto Restart When Idle", ZERO );

    def.put( "Download History Enabled", TRUE );

    // SWT GUI Settings

    def.put("User Mode", ZERO);

    //default data location options
    def.put( CFG_TORRENTADD_OPENOPTIONS, CFG_TORRENTADD_OPENOPTIONS_ALWAYS);
    def.put( CFG_TORRENTADD_OPENOPTIONS_SEP, TRUE );

	File f = null;

	if ( Constants.isAndroid ){
    try {
      File dirDownloads = PlatformManagerFactory.getPlatformManager().getLocation(PlatformManager.LOC_DOWNLOADS);
      if (dirDownloads != null) {
        f = FileUtil.newFile(dirDownloads, Constants.APP_NAME);
      }
    } catch (Throwable ignore) {
    }
    if (f == null) {
      f = FileUtil.newFile(SystemProperties.getDocPath(), "Downloads");
    }
	}else{
    f = FileUtil.newFile(SystemProperties.getDocPath(),  Constants.APP_NAME + " Downloads");
	}

	def.put("Default save path", f.getAbsolutePath());
	def.put("saveTo_list.max_entries", new Long(15));

    def.put("update.start",TRUE);
    def.put("update.periodic",TRUE);
    def.put("update.opendialog",TRUE);
    def.put("update.autodownload", FALSE);
    def.put("update.anonymous", FALSE);

    def.put( "Config Verify Frequency", new Long( 30*60*1000 ));

    def.put("Send Version Info", TRUE);

    // Logging
    def.put("Logger.Enabled", FALSE);  //logging in general
    def.put("Logging Enable", FALSE);  //file logging
    def.put("Logging Dir", "");
    def.put("Logging Timestamp", "HH:mm:ss.SSS");
    def.put("Logging Max Size", new Long(5));

    //final int[] logTypes = { LogEvent.LT_INFORMATION, LogEvent.LT_WARNING, LogEvent.LT_ERROR };
	  final int[] logTypes = { 0, 1, 3 }; // direct numbers in case LogEvent causes initialization loops
    LogIDs[] configurableLOGIDs = {LogIDs.STDOUT, LogIDs.ALERT, LogIDs.CORE,
        LogIDs.DISK, LogIDs.GUI, LogIDs.NET, LogIDs.NWMAN, LogIDs.PEER,
        LogIDs.PLUGIN, LogIDs.TRACKER, LogIDs.CACHE, LogIDs.PIECES }; // FileLogging.configurableLOGIDs
    for (LogIDs logID : configurableLOGIDs) {
      for (int logType : logTypes) {
        def.put("bLog." + logType + "." + logID.toString(), TRUE);
      }
    }
    def.put("Logger.DebugFiles.Enabled", TRUE);
    def.put("Logger.DebugFiles.SizeKB", 256 );
    def.put("Logger.DebugFiles.Enabled.Force", FALSE );
    def.put("Logging Enable UDP Transport", FALSE);



    	//tracker proxy defaults
    def.put( "Enable.Proxy", FALSE );
    def.put( "Enable.SOCKS", FALSE );
    def.put( "Proxy.Host", "" );
    def.put( "Proxy.Port", "" );
    def.put( "Proxy.Username", "<none>" );	// default is explicit "none", as opposed to "not defined"
    def.put( "Proxy.Password", "" );
    def.put( "Proxy.Check.On.Start", TRUE );
    def.put( "Proxy.SOCKS.ShowIcon", TRUE );
    def.put( "Proxy.SOCKS.ShowIcon.FlagIncoming", TRUE );
    def.put( "Proxy.SOCKS.Tracker.DNS.Disable", TRUE );
    def.put( "Proxy.SOCKS.disable.plugin.proxies", TRUE );

    	// data proxy defaults
    def.put( "Proxy.Data.Enable", FALSE);
    def.put( "Proxy.Data.SOCKS.version", "V4" );
    def.put( "Proxy.Data.SOCKS.inform", TRUE);
    def.put( "Proxy.Data.Same", TRUE);
    
    for ( int i=1;i<=COConfigurationManager.MAX_DATA_SOCKS_PROXIES;i++){
    	if ( i == 1 ){
		    def.put( "Proxy.Data.Host", "" );
		    def.put( "Proxy.Data.Port", "" );
		    def.put( "Proxy.Data.Username", "<none>" );
		    def.put( "Proxy.Data.Password", "" );
    	}else{
    		def.put( "Proxy.Data.Host." + i, "" );
 		    def.put( "Proxy.Data.Port." + i, "" );
 		    def.put( "Proxy.Data.Username." + i, "<none>" );
 		    def.put( "Proxy.Data.Password." + i, "" );
    	}
    }

    def.put( "DNS Alt Servers", "8.8.8.8" );
    def.put( "DNS Alt Servers SOCKS Enable", TRUE );

    //old
    def.put( "Start Num Peers", new Long(-1) );
    def.put( "Max Upload Speed", new Long(-1) );
    def.put( "Max Clients", new Long(-1) );
    def.put( "Server.shared.port", TRUE );
    def.put( "Low Port", new Long(6881) );
    def.put( "Already_Migrated", FALSE );

    //misc
    def.put( "ID", "" );
    def.put( "Play Download Finished", FALSE );
    def.put( "Play Download Finished File", "" );
    def.put( "Watch Torrent Folder", FALSE );
    def.put( "Watch Torrent Folder Interval", ONE );	// deprecated, use secs below, migrated in checker
    def.put( "Watch Torrent Folder Interval Secs", SIXTY );
    def.put( "Start Watched Torrents Stopped", FALSE );	// removed, use mode below, migrated in checker
    def.put( "Watch Torrents Add Mode", ZERO );
    def.put( "Watch Torrent Always Rename", FALSE );
    def.put( "Watch Torrent Folder Path", "" );
    def.put( "Watch Torrent Folder Path Count", ONE );
    def.put( ConfigKeys.Transfer.BCFG_PRIORITIZE_FIRST_PIECE, FALSE );
    def.put( ConfigKeys.Transfer.ICFG_PRIORITIZE_FIRST_MB, 0 );
    def.put( ConfigKeys.Transfer.BCFG_PRIORITIZE_FIRST_PIECE_FORCE, FALSE );
    def.put( "Prioritize Most Completed Files", FALSE );
    def.put( "Piece Picker Request Hint Enabled", TRUE );
    def.put( "Use Lazy Bitfield", FALSE );
    def.put( "Zero New", FALSE );
    def.put( "Zero New Stop", FALSE );
    def.put( "Enable Sparse Files", FALSE );
    def.put( "XFS Allocation", FALSE );
    def.put( "Copy And Delete Data Rather Than Move", FALSE);
    def.put( "Move If On Same Drive", FALSE);
    def.put( "File.save.peers.enable", TRUE );
    def.put( "File.strict.locking", TRUE );
    def.put( "Move Deleted Data To Recycle Bin", TRUE);
    def.put( "Delete Partial Files On Library Removal", FALSE );
    def.put( "Popup Download Finished", FALSE);
    def.put( "Popup File Finished", FALSE);
    def.put( "Popup Download Added", FALSE);
    def.put( "Popup Check Complete", FALSE );
    def.put( "Notify Download Finished", FALSE);
    def.put( "Show Timestamp For Alerts", FALSE);
    def.put( "Request Attention On New Download", TRUE );
    def.put( "Activate Window On External Download", TRUE );
    def.put( "Add Torrent Queue Position", ONE );
    def.put( "Files Auto Tag Enable", FALSE );
    def.put( "Files Auto Tag Count", ONE );		
    def.put( "Files Auto Tag Best Size", FALSE);
    def.put( ConfigKeys.File.BCFG_FILES_AUTO_TAG_ALLOW_MOD, FALSE );
    
    def.put( ConfigKeys.File.BCFG_INSUFFICIENT_SPACE_DOWNLOAD_RESTART, FALSE );
    def.put( ConfigKeys.File.ICFG_INSUFFICIENT_SPACE_DOWNLOAD_RESTART_MINS, 10 );
    def.put( ConfigKeys.File.BCFG_MISSING_FILE_DOWNLOAD_RESTART, FALSE );
    def.put( ConfigKeys.File.ICFG_MISSING_FILE_DOWNLOAD_RESTART_MINS, 30 );

    def.put( "Play Download Error", FALSE );
    def.put( "Play Download Error File", "" );
    def.put( "Play Download Error Announcement", FALSE);
    def.put( "Play Download Error Announcement Text", "Download Error");
    def.put( "Popup Download Error", FALSE);
    
    def.put( "Play Notification Added Announcement", FALSE );
    def.put( "Play Notification Added Announcement Text", "Notification Added" );
    def.put( "Play Notification Added", FALSE );
    def.put( "Play Notification Added File", "" );

    def.put( Tag.BCFG_TRACKER_AUTO_TAG_INTERESTING_TRACKERS, TRUE );
    def.put( Tag.ICFG_TAG_AUTO_FULL_REAPPLY_PERIOD_SECS, 30 );
    
    //default torrent directory option
    def.put( "Save Torrent Files", TRUE );
    def.put( "General_sDefaultTorrent_Directory", SystemProperties.getUserPath()+"torrents");
    def.put( "Delete Original Torrent Files", FALSE );
    def.put( "Delete Saved Torrent Files", FALSE );

    def.put( "Bind IP", "" );
    def.put( "Check Bind IP On Start", TRUE );
    def.put( "Enforce Bind IP", FALSE);
    def.put( "Enforce Bind IP Pause", FALSE);
    def.put( "Show IP Bindings Icon", TRUE );
    def.put( ConfigKeys.Connection.SCFG_NETWORK_ADDITIONAL_SERVICE_BINDS, "" );
    def.put( ConfigKeys.Connection.BCFG_NETWORK_IGNORE_BIND_FOR_LAN, FALSE );
    
    
    def.put( "Stats Period", Long.valueOf(30) );
    def.put( "Stats Export Peer Details", FALSE );
    def.put( "Stats Export File Details", FALSE );
    def.put( "Stats XSL File", "" );
    def.put( "Stats Enable", FALSE );
    def.put( "Stats Period", new Long(StatsWriterPeriodic.DEFAULT_SLEEP_PERIOD) );
    def.put( "Stats Dir", "" );
    def.put( "Stats File", StatsWriterPeriodic.DEFAULT_STATS_FILE_NAME );
    def.put( "long.term.stats.enable", TRUE );
    def.put( "long.term.stats.weekstart", Calendar.SUNDAY );
    def.put( "Stats Smoothing Secs", new Long( 2*60 ));
    def.put( "File.Torrent.AutoSkipExtensions", "" );
    def.put( "File.Torrent.AutoSkipFiles", "" );
    def.put( "File.Torrent.AutoSkipFiles.RegExp", FALSE );
    def.put( "File.Torrent.AutoSkipMinSizeKB", ZERO );
    def.put( "File.Torrent.IgnoreFiles", TOTorrent.DEFAULT_IGNORE_FILES );
    def.put( "File.save.peers.max", new Long( TRTrackerAnnouncer.DEFAULT_PEERS_TO_CACHE ) );
    def.put( "File.Character.Conversions", DEFAULT_FILE_CONVERSION_CHARS );

    	// tracker

    def.put( "Tracker Compact Enable", TRUE );
    def.put( "Tracker Key Enable Client", TRUE );
    def.put( "Tracker Key Enable Server", TRUE );
    def.put( "Tracker Separate Peer IDs", FALSE);
    def.put( "Tracker Client Connect Timeout", new Long(120));
    def.put( "Tracker Client Read Timeout", SIXTY );
    def.put( ConfigKeys.Tracker.ICFG_TRACKER_CLIENT_CLOSEDOWN_TIMEOUT, 20L );
    def.put( ConfigKeys.Tracker.ICFG_TRACKER_CLIENT_CONCURRENT_ANNOUNCE, 32 );
	def.put( "Tracker Client Send OS and Java Version", FALSE);
	def.put( "Tracker Client Show Warnings", TRUE);
	def.put( "Tracker Client Min Announce Interval", ZERO);
	def.put( "Tracker Client Numwant Limit", new Long(100));
	def.put( "Tracker Client No Port Announce", FALSE);
	def.put( ConfigKeys.Tracker.BCFG_TRACKER_CLIENT_SMART_ACTIVATION, TRUE);
	def.put( "Tracker Client Exclude LAN", TRUE);

	def.put( ConfigKeys.Tracker.BCFG_TRACKER_PUBLIC_ENABLE, FALSE );
	def.put( ConfigKeys.Tracker.BCFG_TRACKER_PUBLIC_ENABLE_KNOWN_ONLY, FALSE );
    def.put( "Tracker Log Enable", FALSE );
    def.put( "Tracker Port Enable", FALSE );
    def.put( "Tracker Port", new Long( TRHost.DEFAULT_PORT ) );
    def.put( "Tracker Port Backups", "" );
    def.put( "Tracker Port SSL Enable", FALSE );
    def.put( "Tracker Port SSL", new Long( TRHost.DEFAULT_PORT_SSL ) );
    def.put( "Tracker Port SSL Backups", "" );
    def.put( "Tracker Port Force External", FALSE );
    def.put( "Tracker Host Add Our Announce URLs", TRUE );
    def_put( "Tracker IP", "", new IPVerifier());

    def.put( "Tracker Port UDP Enable", FALSE );
    def.put( "Tracker Port UDP Version", new Long(2) );
    def.put( "Tracker Send Peer IDs", TRUE );
    def.put( "Tracker Max Peers Returned", new Long(100) );
    def.put( "Tracker Scrape Cache", new Long( TRTrackerServer.DEFAULT_SCRAPE_CACHE_PERIOD ) );
    def.put( "Tracker Announce Cache", new Long( TRTrackerServer.DEFAULT_ANNOUNCE_CACHE_PERIOD ) );
    def.put( "Tracker Announce Cache Min Peers", new Long( TRTrackerServer.DEFAULT_ANNOUNCE_CACHE_PEER_THRESHOLD ) );
    def.put( "Tracker Poll Interval Min", new Long( TRTrackerServer.DEFAULT_MIN_RETRY_DELAY) );
    def.put( "Tracker Poll Interval Max", new Long( TRTrackerServer.DEFAULT_MAX_RETRY_DELAY) );
    def.put( "Tracker Poll Seed Interval Mult", new Long(1));
    def.put( "Tracker Scrape Retry Percentage", new Long( TRTrackerServer.DEFAULT_SCRAPE_RETRY_PERCENTAGE ) );
    def.put( "Tracker Password Enable Web", FALSE );
    def.put( "Tracker Password Web HTTPS Only", FALSE);
    def.put( "Tracker Password Enable Torrent", FALSE );
    def.put( "Tracker Username", "" );
    def.put( "Tracker Password", null );
    def.put( "Tracker Poll Inc By", new Long( TRTrackerServer.DEFAULT_INC_BY ) );
    def.put( "Tracker Poll Inc Per", new Long( TRTrackerServer.DEFAULT_INC_PER ) );
    def.put( "Tracker NAT Check Enable", TRUE);
    def.put( "Tracker NAT Check Timeout", new Long(TRTrackerServer.DEFAULT_NAT_CHECK_SECS));
    def.put( "Tracker Max Seeds Retained", ZERO );
    def.put( "Tracker Max Seeds", ZERO );
    def.put( "Tracker Max GET Time", new Long(20));
    def.put( "Tracker Max POST Time Multiplier", ONE);
    def.put( "Tracker Max Threads", new Long( 48 ));
    def.put( "Tracker TCP NonBlocking", FALSE);
    def.put( "Tracker TCP NonBlocking Restrict Request Types", TRUE);
    def.put( "Tracker TCP NonBlocking Conc Max", new Long(2048));
    def.put( "Tracker TCP NonBlocking Immediate Close", FALSE );

    def.put( "Tracker Client Scrape Enable", TRUE);
    def.put( "Tracker Client Scrape Total Disable", FALSE );
    def.put( "Tracker Client Scrape Stopped Enable", TRUE);
    def.put( "Tracker Client Scrape Never Started Disable", TRUE);
    def.put( "Tracker Client Scrape Single Only", FALSE);
    def.put( "Tracker Server Full Scrape Enable", TRUE );
    def.put( "Tracker Server Not Found Redirect", "" );
    def.put( "Tracker Server Support Experimental Extensions", FALSE );

    def.put( "Network Selection Prompt", FALSE );
    def.put( "Network Selection Default.Public", TRUE );
    def.put( "Network Selection Default.I2P", FALSE );
    def.put( "Network Selection Default.Tor", FALSE );
    def.put( "Tracker Network Selection Default.Public", TRUE);
    def.put( "Tracker Network Selection Default.I2P", TRUE);
    def.put( "Tracker Network Selection Default.Tor", TRUE);
    def.put( "Sharing Network Selection Global", TRUE );
    def.put( "Sharing Network Selection Default.Public", TRUE );
    def.put( "Sharing Network Selection Default.I2P", FALSE );
    def.put( "Sharing Network Selection Default.Tor", FALSE );

    def.put( "Peer Source Selection Default.Tracker", TRUE);
    def.put( "Peer Source Selection Default.DHT", TRUE);
    def.put( "Peer Source Selection Default.PeerExchange", TRUE);
    def.put( "Peer Source Selection Default.Plugin", TRUE);
    def.put( "Peer Source Selection Default.Incoming", TRUE);

    def.put( "config.style.useSIUnits", FALSE );
    def.put( "config.style.forceSIValues", Constants.isOSX?FALSE:TRUE );
    def.put( "config.style.useUnitsRateBits", FALSE );
    def.put( "config.style.separateProtDataStats", FALSE );
    def.put( "config.style.dataStatsOnly", FALSE );
    def.put( "config.style.doNotUseGB", FALSE );

    def.put( "Save Torrent Backup", FALSE );

    def.put( "Sharing Protocol", "DHT" );
    def.put( "Sharing Add Hashes", FALSE );
    def.put( "Sharing Rescan Enable", FALSE);
    def.put( "Sharing Rescan Period", SIXTY );
    def.put( "Sharing Torrent Comment", "" );
    def.put( "Sharing Permit DHT", TRUE);
    def.put( "Sharing Torrent Private", FALSE);
    def.put( "Sharing Disable RCM", FALSE);
    def.put( "Sharing Is Persistent", FALSE);

    def.put( "File.Decoder.Prompt", FALSE );
    def.put( "File.Decoder.Default", "" );
    def.put( "File.Decoder.ShowLax", FALSE);
    def.put( "File.Decoder.ShowAll", FALSE);
    def.put( "Password enabled", FALSE );
    def.put( "Password", null );
    def.put( "config.interface.checkassoc", TRUE );
    def.put( "confirmationOnExit", FALSE );
    def.put( "locale", Locale.getDefault().toString() );
    def.put( "locale.set.complete.count", ZERO);
    def.put( "Password Confirm", null );
    def.put( "Auto Update", TRUE );
    def.put( "Alert on close", FALSE );
    def.put( "diskmanager.hashchecking.strategy", ONE );
    def.put( "diskmanager.hashchecking.smallestfirst", TRUE );
    def.put( ConfigKeys.File.BCFG_DISKMANAGER_ONE_OP_PER_FS, FALSE );
    def.put( "diskmanager.hashchecking.maxactive", ONE );
    def.put( "Default Start Torrents Stopped", FALSE);
    def.put( "Default Start Torrents Stopped Auto Pause", FALSE );
    def.put( "Server Enable UDP", TRUE); // this actually means the UDP tracker client
    def.put( "Tracker UDP Probe Enable", TRUE );
    def.put( "Tracker Client Enable TCP", TRUE );
    def.put( "Tracker DNS Records Enable", TRUE );
    def.put( "diskmanager.perf.cache.enable", TRUE);
    def.put( "diskmanager.perf.cache.enable.read", FALSE);
    def.put( "diskmanager.perf.cache.enable.write", TRUE);
    def.put( "diskmanager.perf.cache.size", new Long(4));		// 4 MB
    def.put( "diskmanager.perf.cache.notsmallerthan", new Long(1024));	// 1024 K
    def.put( "diskmanager.perf.read.maxthreads", new Long(32));
    def.put( "diskmanager.perf.read.maxmb", new Long(10));
    def.put( "diskmanager.perf.write.maxthreads", new Long(32));
    def.put( "diskmanager.perf.write.maxmb", new Long(10));
    def.put( "diskmanager.perf.cache.trace", FALSE);
    def.put( "diskmanager.perf.cache.flushpieces", TRUE);
    def.put( "diskmanager.perf.read.aggregate.enable", FALSE );
    def.put( "diskmanager.perf.read.aggregate.request.limit", ZERO );
    def.put( "diskmanager.perf.read.aggregate.byte.limit", ZERO );
    def.put( "diskmanager.perf.write.aggregate.enable", FALSE );
    def.put( "diskmanager.perf.write.aggregate.request.limit", ZERO );
    def.put( "diskmanager.perf.write.aggregate.byte.limit", ZERO );
    def.put( "diskmanager.perf.checking.read.priority", FALSE );
    def.put( "diskmanager.perf.checking.fully.async", FALSE );
    def.put( "diskmanager.perf.queue.torrent.bias", TRUE );

    	// Peer control

    def.put( "peercontrol.udp.fallback.connect.fail", TRUE );
    def.put( "peercontrol.udp.fallback.connect.drop", TRUE );
    def.put( "peercontrol.udp.probe.enable", FALSE );
    def.put( "peercontrol.hide.piece", FALSE );
    def.put( "peercontrol.scheduler.use.priorities", TRUE );
    def.put( "peercontrol.prefer.udp", FALSE );
    def.put( ConfigKeys.Connection.BCFG_PEERCONTROL_PREFER_IPV6_CONNECTIONS, FALSE );

    def.put( "File.truncate.if.too.large", FALSE);
    def.put( "Enable System Tray", TRUE);
    def.put( "System Tray Disabled Override", FALSE);
    def.put( "Show Status In Window Title", FALSE );
    def.put( "config.style.table.defaultSortOrder", 3 );
    def.put( "Ignore.peer.ports", "0" );
    def.put( "Security.JAR.tools.dir", "" );
    def.put( "security.cert.auto.install", TRUE );
    def.put( "security.cert.auto.decline", FALSE );

    boolean	tcp_half_open_limited = Constants.isWindows && !(Constants.isWindowsVistaSP2OrHigher || Constants.isWindows7OrHigher );

    def.put( "network.max.simultaneous.connect.attempts", new Long( tcp_half_open_limited?8:24 ));
    def.put( "network.tcp.max.connections.outstanding", new Long( 2048 ));
    def.put( "network.tcp.connect.outbound.enable", TRUE );
    def.put( "network.tcp.mtu.size", new Long(1500) );
    def.put( "network.udp.mtu.size", new Long(1500) );
    def.put( "network.udp.poll.time", new Long( 100 ));
    def.put( "network.tcp.socket.SO_SNDBUF", ZERO );
    def.put( "network.tcp.socket.SO_RCVBUF", ZERO );
    def.put( "network.tcp.socket.IPDiffServ", "" );
    def.put( "network.tcp.read.select.time", new Long(25));
    def.put( "network.tcp.read.select.min.time", ZERO );
    def.put( "network.tcp.write.select.time", new Long(25));
    def.put( "network.tcp.write.select.min.time", ZERO );
    def.put( "network.tcp.connect.select.time", new Long( 100 ));
    def.put( "network.tcp.connect.select.min.time", ZERO );

    def.put( "network.tracker.tcp.select.time", new Long( 100 ));

    def.put( "network.control.write.idle.time", new Long(50));
    def.put( "network.control.write.aggressive", FALSE );
    def.put( "network.control.read.idle.time", new Long(50));
    def.put( "network.control.read.aggressive", FALSE );
    def.put( "network.control.read.processor.count", new Long(1));
    def.put( "network.control.write.processor.count", new Long(1));
    def.put( "peermanager.schedule.time", new Long(100));
    def.put( "enable_small_osx_fonts", TRUE );
    def.put( "Play Download Finished Announcement", FALSE);
    def.put( "Play Download Finished Announcement Text", "Download Complete");
    def.put( "Play File Finished", FALSE );
    def.put( "Play File Finished File", "" );
    def.put( "Play File Finished Announcement", FALSE);
    def.put( "Play File Finished Announcement Text", "File Complete");

    def.put( "filechannel.rt.buffer.millis", new Long( 60*1000 ));
    def.put( "filechannel.rt.buffer.pieces", new Long( 5 ));

    def.put( "BT Request Max Block Size", new Long(65536));

    def.put( "network.transport.encrypted.require", FALSE );
    def.put( "network.transport.encrypted.min_level", "RC4" );
    def.put( "network.transport.encrypted.fallback.outgoing", FALSE );
    def.put( "network.transport.encrypted.fallback.incoming", FALSE );
    def.put( "network.transport.encrypted.use.crypto.port", FALSE );
    def.put( "network.transport.encrypted.allow.incoming", TRUE );	// should always be TRUE, here for testing only

    def.put( "network.bind.local.port", ZERO );

    def.put( "network.admin.maybe.vpn.enable", TRUE );

    def.put( "crypto.keys.system.managed", FALSE );

    def.put( "peer.nat.traversal.request.conc.max", new Long(3));

    // Memory settings

    def.put( "memory.slice.limit.multiplier", new Long(1));

    // Move on completion settings.
    def.put( "Move Completed When Done", FALSE );
    def.put( "Completed Files Directory", "" );
    def.put( "Move Only When In Default Save Dir", TRUE );
    def.put( "Move Torrent When Done", TRUE );
    def.put( ConfigKeys.File.SCFG_FILE_USE_TEMP_AND_MOVE_PATH, "" );
    def.put( ConfigKeys.File.BCFG_FILE_USE_TEMP_AND_MOVE_ENABLE, FALSE );

    def.put( "Move Completed When Done", FALSE );
    def.put( "Completed Files Directory", "" );

    
    // This gets permanently set by GlobalManagerImpl to false once the client has initialised.
    // This is intended to be used once as part of an upgrade.
    def.put( "Set Completion Flag For Completed Downloads On Start", TRUE );

    // Move on removal settings.
    def.put( "File.move.download.removed.enabled", FALSE );
    def.put( "File.move.download.removed.path", "" );
    def.put( "File.move.download.removed.only_in_default", TRUE );
    def.put( "File.move.download.removed.move_torrent", TRUE );
    def.put( "File.move.download.removed.move_torrent_path", "" );
    def.put( "File.move.download.removed.move_partial", FALSE );

    def.put( "File.delete.include_files_outside_save_dir", FALSE);

    def.put("FilesView.show.full.path", FALSE );

    def.put("MyTorrentsView.menu.show_parent_folder_enabled", FALSE);
    def.put("FileBrowse.usePathFinder", FALSE);

    def.put( "Beta Programme Enabled", FALSE );
    def.put( ConfigKeys.File.BCFG_DEF_DELETETORRENT, TRUE );
    def.put( ConfigKeys.File.BCFG_DEF_DELETEALLSELECTED, FALSE );
    def.put( "tb.confirm.delete.content", Long.valueOf(0) ); // 0=ask; 1=delete content; 2=delete torrent

    def.put( "br.backup.auto.enable", FALSE );
    def.put( "br.backup.auto.everydays", ONE );
    def.put( "br.backup.auto.everyhours", ZERO );
    def.put( "br.backup.auto.retain", 5L );
    def.put( ConfigKeys.BackupRestore.BCFG_BACKUP_PLUGINS, TRUE );
    
    //temp section for SpeedManagerAlgorithmProviderV2

    try{
	    def.put(SpeedManagerAlgorithmProviderV2.SETTING_DOWNLOAD_MAX_LIMIT, new Long(SMConst.START_DOWNLOAD_RATE_MAX) );
	    def.put(SpeedManagerAlgorithmProviderV2.SETTING_UPLOAD_MAX_LIMIT, new Long(SMConst.START_UPLOAD_RATE_MAX) );

	    def.put(SpeedManagerAlgorithmProviderV2.SETTING_DHT_GOOD_SET_POINT, new Long(50) );
	    def.put(SpeedManagerAlgorithmProviderV2.SETTING_DHT_GOOD_TOLERANCE, new Long(100) );
	    def.put(SpeedManagerAlgorithmProviderV2.SETTING_DHT_BAD_SET_POINT, new Long(900) );
	    def.put(SpeedManagerAlgorithmProviderV2.SETTING_DHT_BAD_TOLERANCE, new Long(500) );

	    	//**** NOTE! This default can be overridden in ConfigurationChecker
	    def.put(SpeedManagerImpl.CONFIG_VERSION, 2 );	// 1 == classic, 2 == beta

	    def.put( SpeedLimitMonitor.DOWNLOAD_CONF_LIMIT_SETTING, SpeedLimitConfidence.NONE.getString() );
	    def.put( SpeedLimitMonitor.UPLOAD_CONF_LIMIT_SETTING, SpeedLimitConfidence.NONE.getString() );
	    def.put( SpeedLimitMonitor.UPLOAD_CHOKE_PING_COUNT, new Long(1) );

	    //default V2 algorithm seeding and download mode usage, stored as an Int
	    def.put( SpeedLimitMonitor.USED_UPLOAD_CAPACITY_SEEDING_MODE, new Long(90) );
	    def.put( SpeedLimitMonitor.USED_UPLOAD_CAPACITY_DOWNLOAD_MODE, SIXTY );

	    def.put( SpeedManagerAlgorithmProviderV2.SETTING_WAIT_AFTER_ADJUST, TRUE );
	    def.put( SpeedManagerAlgorithmProviderV2.SETTING_INTERVALS_BETWEEN_ADJUST, new Long(2) );

    }catch( Throwable e ){

    }

    	// subscriptions

    def.put( "subscriptions.max.non.deleted.results", new Long( 512 ));
    def.put( "subscriptions.auto.start.downloads", TRUE );
    def.put( "subscriptions.auto.start.min.mb", ZERO );
    def.put( "subscriptions.auto.start.max.mb", ZERO );
    def.put( "subscriptions.auto.dl.mark.read.days", ZERO );

    def.put( "Show Side Bar", TRUE );
    def.put( "Side Bar Top Level Gap", ONE );
    def.put( "Show Options In Side Bar", FALSE );
    def.put( "Show New In Side Bar", TRUE );
    def.put( "Show Downloading In Side Bar", TRUE );
    def.put( "Side Bar Close Position", ZERO );
    def.put( "Side Bar Indent Expanders", TRUE );
    def.put( "Side Bar Compact View", FALSE );
    def.put( "Side Bar Hide Left Icon", FALSE );
    
    def.put( "Share Ratio Progress Interval", 1000L );	// thousandths

    def.put( "search.showRCMView", FALSE );
    def.put( "search.rss.template.timeout", 20 );
    
    def.put( "Use System Theme", FALSE );
    
    def.put( "installer.mode", "" );		// the type of the last installer used (see installer code for values)
}

  protected
  ConfigurationDefaults(
	Map	_def )
  {
	  def = new ConcurrentHashMapWrapper<>((Map<String, Object>) _def);
  }

  protected void
  def_put(
	String										key,
	String										key_def,
	COConfigurationManager.ParameterVerifier	verifier )
  {
	  def.put( key, key_def );

	  List	l = (List)parameter_verifiers.get( key );

	  if ( l == null ){

		  l = new ArrayList(1);

		  parameter_verifiers.put( key, l );
	  }

	  l.add( verifier );
  }

  private void checkParameterExists(String p) throws ConfigurationParameterNotFoundException {
	  if (!def.containsKey(p)) {
		  ConfigurationParameterNotFoundException cpnfe = new ConfigurationParameterNotFoundException(p);
		  //cpnfe.fillInStackTrace();
		  //Debug.out(cpnfe);
		  throw cpnfe;
	  }
  }

  public String getStringParameter(String p) throws ConfigurationParameterNotFoundException {
    checkParameterExists(p);
    Object o = def.get(p);
    if (o instanceof Number)
      return ((Number)o).toString();
    return (String)o;
  }

  public int getIntParameter(String p) throws ConfigurationParameterNotFoundException {
	    checkParameterExists(p);
	    return ((Number) def.get(p)).intValue();
  }

  public long getLongParameter(String p) throws ConfigurationParameterNotFoundException {
	    checkParameterExists(p);
	    return ((Number) def.get(p)).longValue();
  }

  public float getFloatParameter(String p) throws ConfigurationParameterNotFoundException {
	    checkParameterExists(p);
	    return ((Float) def.get(p)).floatValue();
  }

  public byte[] getByteParameter(String p) throws ConfigurationParameterNotFoundException {
	  checkParameterExists(p);
	  return (byte[])def.get(p);
  }

  public boolean getBooleanParameter(String p) throws ConfigurationParameterNotFoundException {
  	checkParameterExists(p);
  	return ((Long)def.get(p)).equals(TRUE);
  }

  public boolean hasParameter(String p) {
	  return def.containsKey(p);
  }

  /**
   * Returns the default value as an object (String, Long, Float, Boolean)
   *
   * @param key
   * @return default value
   */
  public Object getDefaultValueAsObject(String key) {
  	return def.get(key);
  }

  public Set<String> getAllowedParameters() {
  	return def.keySet();
  }

  public void addParameter(String sKey, String sParameter) {
    def.put(sKey, sParameter);
  }

  public void addParameter(String sKey, int iParameter) {
    def.put(sKey, new Long(iParameter));
  }
  public void addParameter(String sKey, byte[] bParameter) {
    def.put(sKey, bParameter);
  }

  public void addParameter(String sKey, boolean bParameter) {
    Long lParameter = new Long(bParameter ? 1 : 0);
    def.put(sKey, lParameter);
  }

  public void addParameter(String sKey, long lParameter) {
	    def.put(sKey, new Long(lParameter));
  }

  public void addParameter(String sKey, float fParameter) {
	  def.put(sKey, new Float(fParameter));
  }

  public void registerExternalDefaults(Map addmap) {
  	def.putAll(addmap);
  }

  public boolean doesParameterDefaultExist(String p) {
    return def.containsKey(p);
  }

  public Object
  getParameter(
	 String	key )
  {
	return( def.get( key ));
  }

  public List
  getVerifiers(
	 String	key )
  {
	  return((List)parameter_verifiers.get( key ));
  }

  protected void
  runVerifiers()
  {
	  Iterator	it = parameter_verifiers.entrySet().iterator();

	  while( it.hasNext()){

		  Map.Entry	entry =(Map.Entry)it.next();

		  String	key 		= (String)entry.getKey();
		  List		verifiers 	= (List)entry.getValue();

		  for (int i=0;i<verifiers.size();i++){

			  COConfigurationManager.ParameterVerifier	verifier = (COConfigurationManager.ParameterVerifier)verifiers.get(i);

			  Object	val_def = getDefaultValueAsObject( key );
			  Object	val;

			  if ( val_def == null ){

				  continue;
			  }

			  if ( val_def instanceof String ){

				  val = COConfigurationManager.getStringParameter( key );

			  }else{

				  Debug.out( "Unsupported verifier type for parameter '" + key + "' - " + val_def );

				  continue;
			  }

			  if ( val == null ){

				  continue;
			  }

			  if ( !verifier.verify( key, val )){

				  Debug.out( "Parameter '" + key + "', value '" + val +"' failed verification - setting back to default '" + val_def + "'" );

				  COConfigurationManager.removeParameter( key );
			  }
		  }
	  }
  }

  protected static class
  IPVerifier
  	implements COConfigurationManager.ParameterVerifier
  {
	  @Override
	  public boolean
	  verify(
		String	parameter,
		Object	_value )
	  {
		  String	value = (String)_value;

		  	// see http://www.faqs.org/rfcs/rfc3696.html

		  	// we need to include "~" here for I2P generated 'URLs'

		  for (int i=0;i<value.length();i++){

			  char	c = value.charAt(i);

			  	// : for IPv6

			  if ( Character.isLetterOrDigit( c ) || c == '.' || c == '-' || c == ':' || c == '~' ){

			  }else{

				  return( false );
			  }
		  }

		  return( true );
	  }
  }
}
