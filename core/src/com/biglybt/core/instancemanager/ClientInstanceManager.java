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

package com.biglybt.core.instancemanager;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.regex.PatternSyntaxException;


public interface
ClientInstanceManager
{
	public static final int	AT_TCP				= 1;
	public static final int	AT_UDP				= 2;
	public static final int	AT_UDP_NON_DATA		= 3;

	public void
	initialize();

	public boolean
	isInitialized();

	public ClientInstance
	getMyInstance();

	public int
	getOtherInstanceCount(
		boolean	block_if_needed );

	public ClientInstance[]
	getOtherInstances();

	public void
	updateNow();

	public ClientInstanceTracked[]
	track(
		byte[]									hash,
		ClientInstanceTracked.TrackTarget		target );

	public InetSocketAddress
	getLANAddress(
		InetSocketAddress	external_address,
		int					address_type );

	public InetSocketAddress
	getExternalAddress(
		InetSocketAddress	lan_address,
		int					address_type );

	public boolean
	isLANAddress(
		InetSocketAddress	address );

	public boolean
	addLANSubnet(
		String				subnet )

		throws PatternSyntaxException;

	public void
	addExplicitLANAddress(
		InetSocketAddress	address );

	public void
	removeExplicitLANAddress(
		InetSocketAddress	address );

	public boolean
	isExplicitLANAddress(
		InetSocketAddress	address );
	
	public boolean
	getIncludeWellKnownLANs();

	public void
	setIncludeWellKnownLANs(
		boolean		include );

	public long
	getClockSkew();

	public boolean
	addInstance(
		InetAddress			explicit_address );

	public void
	addListener(
		ClientInstanceManagerListener l );

	public void
	removeListener(
		ClientInstanceManagerListener l );
}
