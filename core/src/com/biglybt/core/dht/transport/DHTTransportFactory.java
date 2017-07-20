/*
 * Created on 12-Jan-2005
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

package com.biglybt.core.dht.transport;

/**
 * @author parg
 *
 */


import com.biglybt.core.dht.DHTLogger;
import com.biglybt.core.dht.transport.loopback.DHTTransportLoopbackImpl;
import com.biglybt.core.dht.transport.udp.DHTTransportUDP;
import com.biglybt.core.dht.transport.udp.impl.DHTTransportUDPImpl;

public class
DHTTransportFactory
{
	public static DHTTransport
	createLoopback(
		int		id_byte_num )
	{
		return( new DHTTransportLoopbackImpl( id_byte_num ));
	}

	public static DHTTransportUDP
	createUDP(
		byte			protocol_version,
		int				network,
		boolean			v6,
		String			ip,
		String			default_ip,
		int				port,
		int				max_fails_for_live,
		int				max_fails_for_unknown,
		long			timeout,
		int				send_delay,
		int				receive_delay,
		boolean			bootstrap_node,
		boolean			reachable,
		DHTLogger		logger )

		throws DHTTransportException
	{
		return( new DHTTransportUDPImpl(
					protocol_version,
					network,
					v6,
					ip,
					default_ip,
					port,
					max_fails_for_live,
					max_fails_for_unknown,
					timeout,
					send_delay,
					receive_delay,
					bootstrap_node,
					reachable,
					logger ));
	}
}
