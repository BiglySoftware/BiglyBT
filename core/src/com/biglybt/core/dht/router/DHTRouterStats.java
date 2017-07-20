/*
 * Created on 18-Jan-2005
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

package com.biglybt.core.dht.router;

/**
 * @author parg
 *
 */

public interface
DHTRouterStats
{
	public static final int	ST_NODES				= 0;
	public static final int	ST_LEAVES				= 1;
	public static final int	ST_CONTACTS				= 2;
	public static final int	ST_REPLACEMENTS			= 3;
	public static final int	ST_CONTACTS_LIVE		= 4;
	public static final int	ST_CONTACTS_UNKNOWN		= 5;
	public static final int	ST_CONTACTS_DEAD		= 6;

		/**
		 * returns
		 * number of nodes
		 * number of leaves
		 * number of contacts
		 * number of replacements
		 * number of live contacts
		 * number of unknown contacts
		 * number of dying contacts
		 */

	public long[]
	getStats();
}
