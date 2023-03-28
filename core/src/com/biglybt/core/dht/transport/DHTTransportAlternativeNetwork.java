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

import java.util.List;

public interface
DHTTransportAlternativeNetwork
{
	public static final int	AT_MLDHT_IPV4		= 1;
	public static final int	AT_MLDHT_IPV6		= 2;
	public static final int	AT_I2P				= 3;
	public static final int	AT_BIGLYBT_IPV4		= 4;
	public static final int	AT_BIGLYBT_IPV6		= 5;
	public static final int	AT_TOR				= 6;	// this is hardcoded in i2p plugin during migration
	
	public static final int[]	AT_ALL_PUB = { AT_MLDHT_IPV4, AT_MLDHT_IPV6, AT_I2P, AT_TOR };
	public static final int[]	AT_ALL_I2P = { AT_BIGLYBT_IPV4, AT_BIGLYBT_IPV6 };

	public int
	getNetworkType();

	public List<DHTTransportAlternativeContact>
	getContacts(
		int		max );
}
