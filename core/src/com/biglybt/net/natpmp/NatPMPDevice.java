/*
 * Created on 12 Jun 2006
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

package com.biglybt.net.natpmp;

import java.net.InetAddress;
import java.net.NetworkInterface;

public interface
NatPMPDevice
{
	public boolean
	connect()

		throws Exception;

	public int
	addPortMapping(
		boolean	tcp,
		int		internal_port,
		int		external_port )

		throws Exception;

	public void
	deletePortMapping(
		boolean	tcp,
		int		internal_port,
		int		external_port )

		throws Exception;

	public InetAddress
	getLocalAddress();

	public NetworkInterface
	getNetworkInterface();

	public int
	getEpoch();

	public String
	getExternalIPAddress();
}
