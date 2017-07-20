/*
 * Created on Nov 6, 2007
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


package com.biglybt.core.util;

import com.biglybt.core.versioncheck.VersionCheckClient;

public class
FeatureAvailability
{
	private static final long	FT_DISABLE_REQUEST_LIMITING			= 0x0000000000000001;
	private static final long	FT_DISABLE_PEER_GENERAL_RECONNECT	= 0x0000000000000002;
	private static final long	FT_DISABLE_PEER_UDP_RECONNECT		= 0x0000000000000004;
	private static final long	FT_AUTO_SPEED_DEFAULT_CLASSIC		= 0x0000000000000008;
	private static final long	FT_DISABLE_RCM						= 0x0000000000000010;
	private static final long	FT_DISABLE_DHT_REP_V2				= 0x0000000000000020;
	private static final long	FT_DISABLE_MAGNET_SL				= 0x0000000000000040;
	private static final long	FT_ENABLE_ALL_FE_CLIENTS			= 0x0000000000000080;

	private static final long	FT_ENABLE_INTERNAL_FEATURES			= 0x0000000000000100;

	private static final long	FT_TRIGGER_SPEED_TEST_V1			= 0x0000000000000200;
	private static final long	FT_DISABLE_MAGNET_MD				= 0x0000000000000800;

	private static final VersionCheckClient vcc = VersionCheckClient.getSingleton();

	public static boolean
	areInternalFeaturesEnabled()
	{
		final boolean result = ( vcc.getFeatureFlags() & FT_ENABLE_INTERNAL_FEATURES ) != 0;

		return( result );
	}

	public static boolean
	isRequestLimitingEnabled()
	{
		final boolean result = ( vcc.getFeatureFlags() & FT_DISABLE_REQUEST_LIMITING ) == 0;

		return( result );
	}

	public static boolean
	isGeneralPeerReconnectEnabled()
	{
		final boolean result = ( vcc.getFeatureFlags() & FT_DISABLE_PEER_GENERAL_RECONNECT ) == 0;

		return( result );
	}

	public static boolean
	isUDPPeerReconnectEnabled()
	{
		final boolean result = ( vcc.getFeatureFlags() & FT_DISABLE_PEER_UDP_RECONNECT ) == 0;

		return( result );
	}

	public static boolean
	isAutoSpeedDefaultClassic()
	{
		final boolean result = ( vcc.getFeatureFlags() & FT_AUTO_SPEED_DEFAULT_CLASSIC ) != 0;

		return( result );
	}

	public static boolean
	isRCMEnabled()
	{
		final boolean result = ( vcc.getFeatureFlags() & FT_DISABLE_RCM ) == 0;

		return( result );
	}

	public static boolean
	isDHTRepV2Enabled()
	{
		final boolean result = ( vcc.getFeatureFlags() & FT_DISABLE_DHT_REP_V2 ) == 0;

		return( result );
	}

	public static boolean
	isMagnetSLEnabled()
	{
		final boolean result = ( vcc.getFeatureFlags() & FT_DISABLE_MAGNET_SL ) == 0;

		return( result );
	}

	public static boolean
	isMagnetMDEnabled()
	{
		final boolean result = ( vcc.getFeatureFlags() & FT_DISABLE_MAGNET_MD ) == 0;

		return( result );
	}

	public static boolean
	allowAllFEClients()
	{
		final boolean result = ( vcc.getFeatureFlags() & FT_ENABLE_ALL_FE_CLIENTS ) != 0;

		return( result );
	}

	public static boolean
	triggerSpeedTestV1()
	{
		final boolean result = ( vcc.getFeatureFlags() & FT_TRIGGER_SPEED_TEST_V1 ) != 0;

		return( result );
	}
}
