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
DistributedDatabaseKey
{
	public static final int FL_ANON 	= 0x00000001;
	public static final int FL_BRIDGED 	= 0x00000002;

	public Object
	getKey()

		throws DistributedDatabaseException;

	public String
	getDescription();

		/**
		 * @since 4901
		 * @param flags
		 */

	public void
	setFlags(
		int	flags );

		/**
		 * @since 4901
		 * @return
		 */

	public int
	getFlags();
}
