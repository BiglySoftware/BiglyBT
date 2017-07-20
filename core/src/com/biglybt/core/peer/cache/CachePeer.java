/*
 * Created on Feb 1, 2007
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


package com.biglybt.core.peer.cache;

import java.net.InetAddress;

public interface
CachePeer
{
	public static final int PT_NONE				= 1;
	public static final int PT_CACHE_LOGIC		= 2;

	public int
	getType();

	public InetAddress
	getAddress();

	public int
	getPort();

	public long
	getCreateTime(
		long	now );

	public long
	getInjectTime(
		long	now );

	public void
	setInjectTime(
		long	time );

	public long
	getSpeedChangeTime(
		long	now );

	public void
	setSpeedChangeTime(
		long	time );

	public boolean
	getAutoReconnect();

	public void
	setAutoReconnect(
		boolean		auto );

	public boolean
	sameAs(
		CachePeer	other );

	public String
	getString();
}
