/*
 * Created on Apr 1, 2008
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

public interface
BuddyPluginListener
{
	public void
	initialised(
		boolean		available );

	public void
	buddyAdded(
		BuddyPluginBuddy	buddy );

	public void
	buddyRemoved(
		BuddyPluginBuddy	buddy );

	public void
	buddyChanged(
		BuddyPluginBuddy	buddy );

	public void
	messageLogged(
		String		str,
		boolean		is_error );

	public void
	enabledStateChanged(
		boolean classic_enabled,
		boolean	beta_enabled );

	public void
	updated();
}
