/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.net.upnp.services;

import java.net.InetAddress;

import com.biglybt.net.upnp.UPnPException;

public interface 
UPnPWANIPv6FirewallControl
	extends UPnPSpecificService
{
	public void
	addPinhole(
		boolean			tcp,
		int				port,
		InetAddress		local_address,
		String			description )

		throws UPnPException;
	
	public void
	updatePinhole(
		boolean		tcp,
		int			port )

		throws UPnPException;
	
	public void
	removePinhole(
		boolean		tcp,
		int			port )

		throws UPnPException;
}
