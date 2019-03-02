/*
 * Created on Mar 28, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.pif.utils;

import java.io.InputStream;
import java.net.InetAddress;
import java.util.Locale;

public abstract class
LocationProvider
{
	public static final long CAP_COUNTY_BY_IP		= 0x00000001;	// meh
	public static final long CAP_COUNTRY_BY_IP		= 0x00000001;
	public static final long CAP_ISO3166_BY_IP		= 0x00000002;
	public static final long CAP_FLAG_BY_IP			= 0x00000004;
	public static final long CAP_FLAG_BY_CC			= 0x00000008;

	public abstract String
	getProviderName();

	public abstract long
	getCapabilities();

	public boolean
	hasCapability(
		long		capability )
	{
		return((getCapabilities()&capability) != 0 );
	}

	public boolean
	hasCapabilities(
		long		capabilities )
	{
		return((getCapabilities()&capabilities) == capabilities );
	}

	public String
	getCountryNameForIP(
		InetAddress		address,
		Locale			locale )
	{
		return( null );
	}

	public String
	getISO3166CodeForIP(
		InetAddress		address )
	{
		return( null );
	}

		/**
		 * Array of [width, height] pairs, smallest to largest
		 * @return
		 */

	public int[][]
	getCountryFlagSizes()
	{
		return( null );
	}

		/**
		 *
		 * @param address
		 * @param size_index - index in getCountryFlagSizes response of desired size
		 * @return
		 */

	public InputStream
	getCountryFlagForIP(
		InetAddress		address,
		int				size_index )
	{
		return( null );
	}

	public InputStream
	getCountryFlagForISO3166Code(
		String			cc,
		int				size_index )
	{
		return( null );
	}
	
	public abstract boolean
	isDestroyed();
}
