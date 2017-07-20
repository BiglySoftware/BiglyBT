/*
 * Created on 19-Jul-2004
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

package com.biglybt.core.ipfilter;

/**
 * @author parg
 *
 */

public interface
IpFilterManager
{
	public IpFilter
	getIPFilter();

	public BadIps
	getBadIps();

	/**
	 * @param range
	 * @return
	 */
	byte[] getDescription(Object info);

	/**
	 * @param range
	 * @param description
	 */
	Object addDescription(IpRange range, byte[] description);

	/**
	 *
	 */
	void cacheAllDescriptions();

	/**
	 *
	 */
	void clearDescriptionCache();

	/**
	 *
	 *
	 * @since 3.0.1.5
	 */
	void deleteAllDescriptions();
}
