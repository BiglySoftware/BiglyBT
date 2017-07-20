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

/**
 * @author parg
 *
 */

import java.net.URL;

public interface
TrackerAuthenticationListener
{
		/**
		 * authentica a given user/password pair for access to the given resource
		 * @param resource
		 * @param user
		 * @param password
		 * @return true - access OK, false access denied
		 */

	public boolean
	authenticate(
		URL			resource,
		String		user,
		String		password );

		/**
		 * For the UDP tracker protocol it is necessary to return the SHA1 hash of the
		 * password for the user, allowing the core to perform the necessary checks
		 * @param resource
		 * @param user
		 * @return SHA1 password hash or null if either user unknown of user can't access the resource
		 */

	public byte[]
	authenticate(
		URL			resource,
		String		user );
}
