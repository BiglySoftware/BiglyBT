/*
 * Created on 15-Jun-2004
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

package com.biglybt.net.upnp.services;

import com.biglybt.net.upnp.UPnPException;

/**
 * @author parg
 *
 */

public interface
UPnPWANConnection
	extends UPnPSpecificService
{
	public static final int	CAP_UDP_TCP_SAME_PORT	= 0x0000001;
	public static final int	CAP_ALL					= 0xffffffff;

	public String
	getConnectionType();

		/**
		 * adda new port mapping from external port X to port X on local host
		 * @param tcp
		 * @param port
		 * @param description
		 */

	public void
	addPortMapping(
		boolean		tcp,			// false -> UDP
		int			port,
		String		description )

		throws UPnPException;

	public UPnPWANConnectionPortMapping[]
	getPortMappings()

		throws UPnPException;

	public void
	deletePortMapping(
		boolean		tcp,
		int			port )

		throws UPnPException;

	public String[]
	getStatusInfo()

		throws UPnPException;

	public void
	periodicallyRecheckMappings(
		boolean	on );

	public int
	getCapabilities();

	public String
	getExternalIPAddress()

		throws UPnPException;

	public void
	addListener(
		UPnPWANConnectionListener	listener );

	public void
	removeListener(
		UPnPWANConnectionListener	listener );
}
