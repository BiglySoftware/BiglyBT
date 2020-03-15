/*
 * Created on Nov 6, 2015
 * Created by Paul Gardner
 *
 * Copyright 2015 Azureus Software, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.core.tag;

public interface
TagFeatureNotifications
{
	public int NOTIFY_NONE			= 0x00000000;
	public int NOTIFY_ON_ADD		= 0x00000001;
	public int NOTIFY_ON_REMOVE		= 0x00000002;

	public int
	getPostingNotifications();

	public void
	setPostingNotifications(
		int		flags );
	
	public String
	getNotifyMessageChannel();

	public void
	setNotifyMessageChannel(
		String		chat );
}
