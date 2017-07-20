/*
 * Created on 15 Jun 2006
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

package com.biglybt.core.security;

public interface
CryptoManagerPasswordHandler
{
	/**
	 * HANDLER_TYPE_UNKNOWN is not for public use
	 **/

	public static final int HANDLER_TYPE_UNKNOWN	= 0;
	public static final int HANDLER_TYPE_USER		= 1;
	public static final int HANDLER_TYPE_SYSTEM		= 2;
	public static final int HANDLER_TYPE_ALL		= 3;	// just for clearing passwords...

	public static final int ACTION_ENCRYPT				= 1;
	public static final int	ACTION_DECRYPT				= 2;
	public static final int	ACTION_PASSWORD_SET			= 3;

	public int
	getHandlerType();

		/**
		 * Gets a password
		 * @param handler_type	from AESecurityManager.HANDLER_x enum
		 * @param action_type	from above ACTION_x enum
		 * @param reason		reason for the password being sought
		 * @return password details or null if no password available
		 */

	public passwordDetails
	getPassword(
		int			handler_type,
		int			action_type,
		boolean		last_pw_incorrect,
		String		reason );

	public void
	passwordOK(
		int					handler_type,
		passwordDetails		details );

	public interface
	passwordDetails
	{
		public char[]
		getPassword();

			/**
			 * @return	0 -> don't persist, Integer.MAX_VALUE -> persist forever
			 * < 0 -> current session; other -> seconds to persist
			 */

		public int
		getPersistForSeconds();
	}
}
