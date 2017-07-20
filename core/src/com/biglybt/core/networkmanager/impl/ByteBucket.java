/*
 * Created on Jul 26, 2004
 * Created by Alon Rohter
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

package com.biglybt.core.networkmanager.impl;


/**
 * Byte-bucket implementation based on the token bucket algorithm.
 * Buckets can be configured with a guaranteed normal rate, along with
 * a burst rate.
 */
public interface
ByteBucket
{
	public int
	getRate();

	public void
	setRate(
		int rate_bytes_per_sec );

	public int
	getAvailableByteCount();

	public void
	setBytesUsed(
		int bytes_used );

	public void
	setFrozen(
		boolean	frozen );
}
