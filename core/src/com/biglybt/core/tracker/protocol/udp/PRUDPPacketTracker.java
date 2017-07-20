/*
 * Created on 21-Jan-2005
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

package com.biglybt.core.tracker.protocol.udp;

import com.biglybt.core.config.COConfigurationManager;

/**
 * @author parg
 *
 */

public class
PRUDPPacketTracker
{
	public static int VERSION = 2;

	static{
		VERSION = COConfigurationManager.getIntParameter( "Tracker Port UDP Version", 2 );

		// System.out.println( "UDP Version = " + VERSION );
	}

	public static final int DEFAULT_RETRY_COUNT		= 1;		// changed from 4 after advice from XTF

	public static final int	ACT_REQUEST_CONNECT		= 0;
	public static final int	ACT_REQUEST_ANNOUNCE	= 1;
	public static final int	ACT_REQUEST_SCRAPE		= 2;

	public static final int	ACT_REPLY_CONNECT		= 0;
	public static final int	ACT_REPLY_ANNOUNCE		= 1;
	public static final int	ACT_REPLY_SCRAPE		= 2;
	public static final int	ACT_REPLY_ERROR			= 3;

	public static final long	INITIAL_CONNECTION_ID	= 0x41727101980L;

	// see VersionCheckClientUDPCodecs for more actions

	// see DHTUDPPacketHelper for more actions

	// see NetworkAdminNATUDPCodecs for more actions

}
