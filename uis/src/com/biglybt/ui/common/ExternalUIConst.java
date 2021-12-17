/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.common;

import java.util.HashMap;
import java.util.Hashtable;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.ui.common.util.LegacyHashtable;

/**
 * @author Tobias Minich
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class ExternalUIConst {
	public static Hashtable parameterlegacy = null;
	private static boolean defaultsNotRegistered = true;

	static {
		parameterlegacy = new LegacyHashtable();

			// abbreviations

		parameterlegacy.put("max_up", "Max Upload Speed KBs");
		parameterlegacy.put("max_down", "Max Download Speed KBs");

		parameterlegacy.put("General_sDefaultSave_Directory", "Default save path");
		parameterlegacy.put("Core_sOverrideIP", "Override Ip");
		//parameterlegacy.put("Core_bAllocateNew", "Zero New");
		parameterlegacy.put("Core_iTCPListenPort", "TCP.Listen.Port");
		//parameterlegacy.put("Core_iLowPort", "TCP.Listen.Port");
		//parameterlegacy.put("Core_iHighPort", "High Port");
		parameterlegacy.put("Core_iMaxActiveTorrents", "max active torrents");
		parameterlegacy.put("Core_iMaxDownloads", "max downloads");
		//parameterlegacy.put("Core_iMaxClients", "Max Clients");
		parameterlegacy.put("Core_iMaxUploads", "Max Uploads");
		parameterlegacy.put("Core_iMaxUploadSpeed", "Max Upload Speed KBs");
		parameterlegacy.put("Core_iSaveResumeInterval", "Save Resume Interval");
		parameterlegacy.put("Core_bIncrementalAllocate", "Enable incremental file creation");
		parameterlegacy.put("Core_bCheckPiecesOnCompletion", "Check Pieces on Completion");
		parameterlegacy.put("Core_fSeedingShareStop", "Stop Ratio");
		parameterlegacy.put("StartStopManager_bIgnoreRatioPeers", "Stop Peers Ratio");
		parameterlegacy.put("Core_iSeedingRatioStart", "Start Peers Ratio");
		parameterlegacy.put("Core_bDisconnectSeed", "Disconnect Seed");
		parameterlegacy.put("Core_bSwitchPriority", "Switch Priority");
		parameterlegacy.put("Core_bSlowConnect", "Slow Connect");
		parameterlegacy.put("Core_sPriorityExtensions", "priorityExtensions");
		parameterlegacy.put("Core_bPriorityExtensionsIgnoreCase", "priorityExtensionsIgnoreCase");
		parameterlegacy.put("Core_bIpFilterEnabled", "Ip Filter Enabled");
		parameterlegacy.put("Core_bIpFilterAllow", "Ip Filter Allow");
		parameterlegacy.put("Core_bAllowSameIPPeers", "Allow Same IP Peers");
		parameterlegacy.put("Core_bUseSuperSeeding", "Use Super Seeding");
		parameterlegacy.put("Core_iMaxPeerConnectionsPerTorrent", "Max.Peer.Connections.Per.Torrent");
		parameterlegacy.put("Core_iMaxPeerConnectionsTotal", "Max.Peer.Connections.Total");
		parameterlegacy.put("SWT_bUseCustomTab", "useCustomTab");
		parameterlegacy.put("SWT_iGUIRefresh", "GUI Refresh");
		parameterlegacy.put("SWT_iGraphicsUpdate", "Graphics Update");
		parameterlegacy.put("SWT_iReOrderDelay", "ReOrder Delay");
		parameterlegacy.put("SWT_bSendVersionInfo", "Send Version Info");
		parameterlegacy.put("SWT_bShowDownloadBasket", "Show Download Basket");
		parameterlegacy.put("SWT_bAlwaysRefreshMyTorrents", "config.style.refreshMT");
		parameterlegacy.put("SWT_bOpenDetails", "Open Details");
		parameterlegacy.put("SWT_bProgressBarColorOverride", "Colors.progressBar.override");
		parameterlegacy.put("Plugin_sConfig_Directory", "Plugin.config.directory");
		parameterlegacy.put("Plugin_bConfigEnable", "Plugin.config.enable");
		parameterlegacy.put("Plugin_iConfigIntlist", "Plugin.config.intlist");
	    parameterlegacy.put("Plugin_sConfigLogfile", "Plugin.config.logfile");
	    parameterlegacy.put("Plugin_sConfigNick", "Plugin.config.nick");
	    parameterlegacy.put("Plugin_iConfigPortBlue", "Plugin.config.port.blue");
	    parameterlegacy.put("Plugin_iConfigPortGreen", "Plugin.config.port.green");
	    parameterlegacy.put("Plugin_iConfigPortRed", "Plugin.config.port.red");
	    parameterlegacy.put("Plugin_iConfigPort", "Plugin.config.port");
	    parameterlegacy.put("Plugin_sConfigStringlist", "Plugin.config.stringlist");
		parameterlegacy.put("Logger_bEnable","Logging Enable");
		parameterlegacy.put("Logger_sDir_Directory", "Logging Dir");
		parameterlegacy.put("Logger_iMaxSize", "Logging Max Size");

		parameterlegacy.put("Tracker_Password_Enable","Tracker Password Enable Web");
		parameterlegacy.put("Tracker_UserName","Tracker Username");
		parameterlegacy.put("Tracker_Password","Tracker Password");

	}

	public static void registerDefaults() {
		HashMap def =new HashMap();
		if (defaultsNotRegistered) {
			defaultsNotRegistered = false;
			/** Headless Server settings **/
			// Server Name
			def.put("Server_sName", Constants.APP_NAME + " WebInterface");
			// IP to bind to
			def.put("Server_sBindIP", "");
			// Port the server runs on
			def.put("Server_iPort", new Long(8088));
			// Connection Timeout in seconds.
			def.put("Server_iTimeout", new Long(10));
			// Path to the html templates.
			def.put("Server_sTemplate_Directory", SystemProperties.getUserPath()+"template");
			// Maximal simultaneous connections
			def.put("Server_iMaxHTTPConnections", new Long(5));
			// Auto-refresh torrents every (seconds, 0 = off);
			def.put("Server_iRefresh", new Long(20));
			// Allowed static ips (space separated list)
			def.put("Server_sAllowStatic", "127.0.0.1");
			// Allowed dynamic hosts (space separated list)
			def.put("Server_sAllowDynamic", "");
			// Recheck dynamic hosts every (minutes)
			def.put("Server_iRecheckDynamic", new Long(30));
			// Be not JavaScript-dependant
			def.put("Server_bNoJavaScript", new Long(0));

			// Relevant for the proxy part
			// Fake hostname to access the webinterface when used in proxy mode
			def.put("Server_sAccessHost", "torrent");
			// Enable Cookies
			def.put("Server_bProxyEnableCookies", new Long(1));
			// Block certain URLs
			def.put("Server_bProxyBlockURLs", new Long(0));
			// Filter HTTP Headers (Referer and User Agent)
			def.put("Server_bProxyFilterHTTP", new Long(0));
			// User agent for outgoing connections
			def.put("Server_sProxyUserAgent", "Mozilla/4.0 (compatible; MSIE 4.0; WindowsNT 5.0)");
			// Use a downstream proxy
			def.put("Server_bUseDownstreamProxy", new Long(0));
			// Server Host Name
			def.put("Server_sDownstreamProxyHost", "127.0.0.1");
			// Port of a downstream proxy
			def.put("Server_iDownstreamProxyPort", new Long(0));
			// Grab Torrents in Proxy mode
			def.put("Server_bProxyGrabTorrents", new Long(1));
			// Page to redirect to if torrent download was successful
			def.put("Server_sProxySuccessRedirect", "torrents");

			// Logging relevant Stuff
			//  Log levels:
			//   50000 Fatal
			//   40000 Error
			//   30000 Warn
			//   20000 Info
			//   12000 HTTP (SLevel)
			//   11101 Torrent Received (SLevel)
			//   11100 Torrent Sent (SLevel)
			//   11000 Core info (SLevel)
			//   10001 Thread (SLevel)
			//   10000 Debug
			// Log to file
			def.put("Server_bLogFile", new Long(0));
			// Logfile
			def.put("Server_sLogFile", SystemProperties.getUserPath()+"webinterface.log");
			// Log Level for web interface
			def.put("Server_iLogLevelWebinterface", new Long(20000));
			// Log Level for core
			def.put("Server_iLogLevelCore", new Long(20000));
			// Number of remembered log entries
			def.put("Server_iLogCount", new Long(200));
			COConfigurationManager.registerExternalDefaults(def);
		}
	}

}
