/*
 * Created on 10-Jun-2004
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

package com.biglybt.pif.tracker.web;

import java.net.URL;

/**
 * @author parg
 *
 */

public class
TrackerAuthenticationAdapter
	implements TrackerAuthenticationListener
{
	@Override
	public boolean
	authenticate(
		URL			resource,
		String		user,
		String		password )
	{
		return( false );
	}

		/**
		 * Hack to support header-based auth - not included in listener for compatibility reasons
		 * @param headers
		 * @param resource
		 * @param user
		 * @param password
		 * @return
		 */

	public boolean
	authenticate(
		String		headers,
		URL			resource,
		String		user,
		String		password )
	{
		return( authenticate( resource, user, password ));
	}

	@Override
	public byte[]
	authenticate(
		URL			resource,
		String		user )
	{
		return( null );
	}
}
