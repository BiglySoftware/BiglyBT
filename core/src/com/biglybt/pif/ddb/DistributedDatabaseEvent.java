/*
 * Created on 18-Feb-2005
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

package com.biglybt.pif.ddb;

/**
 * @author parg
 *
 */

public interface
DistributedDatabaseEvent
{
		// operation level

	public static final int	ET_VALUE_WRITTEN		= 1;
	public static final int	ET_VALUE_READ			= 2;
	public static final int	ET_VALUE_DELETED		= 3;

	public static final int	ET_OPERATION_COMPLETE	= 4;
	public static final int	ET_OPERATION_TIMEOUT	= 5;

	public static final int	ET_KEY_STATS_READ		= 6;

	public static final int	ET_OPERATION_STARTS		= 7;
	
	public static final int	ET_DIVERSIFIED			= 8;

		// ddb level

	public static final int	ET_LOCAL_CONTACT_CHANGED	= 10;

	public int
	getType();

	public DistributedDatabaseKey
	getKey();

	public DistributedDatabaseKeyStats
	getKeyStats();

	public DistributedDatabaseValue
	getValue();

	public DistributedDatabaseContact
	getContact();
}
