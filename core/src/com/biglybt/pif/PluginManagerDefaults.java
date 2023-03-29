/*
 * Created on 13-Jul-2004
 * Created by Paul Gardner
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

package com.biglybt.pif;

/**
 * @author parg
 *
 */

public interface
PluginManagerDefaults
{
			// if default plugins get removed then set their id to -1!

	public static final String	PID_START_STOP_RULES		= "Start/Stop Rules";
	public static final String	PID_REMOVE_RULES			= "Torrent Removal Rules";
	public static final String	PID_SHARE_HOSTER			= "Share Hoster";
	public static final String	PID_DEFAULT_TRACKER_WEB		= "Default Tracker Web";
	//public static final String	PID_UPDATE_LANGUAGE			= "Update Language";
	public static final String	PID_PLUGIN_UPDATE_CHECKER	= "Plugin Update Checker";
	public static final String	PID_CORE_UPDATE_CHECKER		= "Core Update Checker";
	public static final String	PID_CORE_PATCH_CHECKER		= "Core Patch Checker";
	public static final String	PID_PLATFORM_CHECKER		= "Platform Checker";
	public static final String	PID_UPNP					= "UPnP";
	//public static final String	PID_CLIENT_ID				= "Client ID";
	public static final String	PID_DHT						= "DHT";
	public static final String	PID_DHT_TRACKER				= "DHT Tracker";
	public static final String	PID_MAGNET					= "Magnet URI Handler";
	//public static final String	PID_JPC						= "JPC";
	public static final String	PID_EXTERNAL_SEED			= "External Seed";
	public static final String	PID_LOCAL_TRACKER			= "Local Tracker";
	public static final String	PID_TRACKER_PEER_AUTH		= "Tracker Peer Auth";
	public static final String	PID_NET_STATUS				= "Network Status";
	public static final String	PID_BUDDY					= "Buddy";
	public static final String	PID_RSS						= "RSS";
	public static final String	PID_SIMPLE_API				= "Simple API";
	public static final String	PID_NET_TEST				= "Net Test";

	public static final String[] PLUGIN_IDS = {

			PID_START_STOP_RULES,
			PID_REMOVE_RULES,
			PID_SHARE_HOSTER,
			PID_DEFAULT_TRACKER_WEB,
			//PID_UPDATE_LANGUAGE,
			PID_CORE_UPDATE_CHECKER,
			PID_CORE_PATCH_CHECKER,
			PID_PLATFORM_CHECKER,
			PID_UPNP,
			//PID_CLIENT_ID,
			PID_DHT,
			PID_DHT_TRACKER,
			PID_MAGNET,
			//PID_JPC,
			PID_EXTERNAL_SEED,
			PID_LOCAL_TRACKER,
			PID_TRACKER_PEER_AUTH,
			PID_NET_STATUS,
			PID_BUDDY,
			PID_RSS,
			PID_SIMPLE_API,
			PID_NET_TEST
		};

	public String[]
	getDefaultPlugins();

		/**
		 * by default all default plugins are enabled. This methods allows them to be disabled
		 * @param plugin_id
		 */

	public void
	setDefaultPluginEnabled(
		String		plugin_id,
		boolean		enabled );

	public boolean
	isDefaultPluginEnabled(
		String		plugin_id );

		/**
		 * Set the name of the application, default is
		 * {@link com.biglybt.core.util.Constants#BIGLYBT_NAME}, and an identifier
		 * for it, default is "az"
		 * <br/>
		 * default for entry point is the SWT UI
		 * @param name
		 */

	public void
	setApplicationName(
		String		name );

	public String
	getApplicationName();

	public void
	setApplicationIdentifier(
		String		identifier );

	public String
	getApplicationIdentifier();

	public void
	setApplicationEntryPoint(
		String		entry_point );

	public String
	getApplicationEntryPoint();

	public void
	setSingleInstanceHandler(
		int									single_instance_port,
		PluginManagerArgumentHandler		handler );

	public boolean
	setSingleInstanceHandlerAndProcess(
		int									single_instance_port,
		PluginManagerArgumentHandler		handler,
		String[]							args );
}
