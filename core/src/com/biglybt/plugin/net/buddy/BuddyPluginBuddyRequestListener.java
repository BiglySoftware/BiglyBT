/*
 * Created on Apr 4, 2008
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.plugin.net.buddy;

import java.util.Map;

public interface
BuddyPluginBuddyRequestListener
{
		/**
		 * Request receieved for a buddy. If the buddy is not authorised then the BuddyPluginBuddy
		 * is transient and should only be used for the duration of this request. Use outside of
		 * this context is undefined. If you want to authorise a transient buddy then you need
		 * to call BuddyPlugin.addBuddy with the required public key
		 *
		 * @param from_buddy
		 * @param subsystem
		 * @param request
		 * @return
		 * @throws BuddyPluginException
		 */

	public Map
	requestReceived(
		BuddyPluginBuddy	from_buddy,
		int					subsystem,
		Map					request )

		throws BuddyPluginException;

	public void
	pendingMessages(
		BuddyPluginBuddy[]	from_buddies );
}
