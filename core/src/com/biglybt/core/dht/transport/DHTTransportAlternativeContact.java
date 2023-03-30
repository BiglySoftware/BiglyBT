/*
 * Created on May 29, 2014
 * Created by Paul Gardner
 *
 * Copyright 2014 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.core.dht.transport;

import java.util.Map;

import com.biglybt.core.util.SystemTime;

public interface
DHTTransportAlternativeContact
{
	public int
	getNetworkType();

	public int
	getVersion();

		/**
		 * A good-enough ID to spot duplicates - must be equal to Arrays.hashCode( BEncode( getProperties()));
		 * @return
		 */

	public int
	getID();

		/**
		 * @return alive time in seconds since "mono time" start - can be negative
		 */

	public int
	getLastAlive();

		/**
		 * Gets the contact's age since last known to be alive in seconds
		 * Obviously this varies so don't use it for sorting!
		 * @return
		 */

	public default int
	getAge()
	{
		return((int)(SystemTime.getMonotonousTime()/1000) - getLastAlive());
	}

	public Map<String,Object>
	getProperties();
}
