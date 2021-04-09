/*
 * Created on Dec 8, 2009
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


package com.biglybt.core.tracker;

import java.net.URL;

public interface
TrackerPeerSource
{
	public static final int TP_UNKNOWN		= 0;
	public static final int TP_TRACKER		= 1;
	public static final int TP_HTTP_SEED	= 2;
	public static final int TP_DHT			= 3;
	public static final int TP_LAN			= 4;
	public static final int TP_PEX			= 5;
	public static final int TP_INCOMING		= 6;
	public static final int TP_PLUGIN		= 7;

	public static final int ST_UNKNOWN		= 0;
	public static final int ST_DISABLED		= 1;
	public static final int ST_STOPPED		= 2;
	public static final int ST_QUEUED		= 3;
	public static final int ST_UPDATING		= 4;
	public static final int ST_ONLINE 		= 5;
	public static final int ST_ERROR		= 6;
	public static final int ST_AVAILABLE	= 7;
	public static final int ST_UNAVAILABLE	= 8;
	public static final int ST_INITIALISING	= 9;


	public int
	getType();

	public String
	getName();
	
	public String
	getDetails();

	public int
	getStatus();

	public String
	getStatusString();

	public int
	getSeedCount();

	public int
	getLeecherCount();

		/**
		 * 
		 * @return URL associated with this source, if there is one, null otherwise
		 */
	
	public URL
	getURL();
	
	public int
	getPeers();

		/**
		 * If the tracker source supports 'number downloads completed' this will be >= 0
		 * @return
		 */

	public int
	getCompletedCount();

	public int
	getLastUpdate();

		/**
		 * 
		 * @return Integer.MIN_VAL -> no details available
		 * 
		 */
	public int
	getSecondsToUpdate();

	public int
	getInterval();

	public int
	getMinInterval();

	public boolean
	isUpdating();

	public boolean
	canManuallyUpdate();

	public void
	manualUpdate();

	public long[]
	getReportedStats();
	
	public boolean
	canDelete();

	public void
	delete();
}
