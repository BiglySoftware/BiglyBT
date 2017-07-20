/*
 * Created on 25-Apr-2005
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

package com.biglybt.core.dht.db;

public interface
DHTDBStats
{
	public static final int	VD_VALUE_COUNT		= 0;		// total values
	public static final int	VD_LOCAL_SIZE		= 1;		// locally stored data size
	public static final int	VD_DIRECT_SIZE		= 2;		// directly stored to us by others
	public static final int	VD_INDIRECT_SIZE	= 3;		// indirectly (cache forwarded) stored
	public static final int	VD_DIV_FREQ			= 4;		// diversifications caused by frequency
	public static final int	VD_DIV_SIZE			= 5;		// diversifications caused by size

	public int
	getKeyCount();

	public int
	getLocalKeyCount();

	public int
	getKeyBlockCount();

	public int
	getSize();

	public int
	getValueCount();

		/**
		 * returned values indexed by above VD_ constants for meaning
		 * @return
		 */

	public int[]
	getValueDetails();
}
