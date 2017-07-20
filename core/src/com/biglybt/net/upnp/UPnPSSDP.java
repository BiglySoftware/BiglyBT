/*
 * Created on 20-Dec-2005
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

package com.biglybt.net.upnp;

public interface
UPnPSSDP
{
	public static final String				SSDP_GROUP_ADDRESS 	= "239.255.255.250";
	public static final int					SSDP_GROUP_PORT		= 1900;

	public int
	getControlPort();

	public void
	search(
		String[]		STs );

	public void
	notify(
		String		NT,
		String		NTS,
		String		UUID,
		String		url );

	public void
	addListener(
		UPnPSSDPListener	l );

	public void
	removeListener(
		UPnPSSDPListener	l );
}
