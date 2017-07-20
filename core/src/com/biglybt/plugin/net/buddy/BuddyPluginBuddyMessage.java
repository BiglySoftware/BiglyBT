/*
 * Created on Apr 23, 2008
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

public class
BuddyPluginBuddyMessage
{
	private BuddyPluginBuddyMessageHandler		handler;
	private int									id;
	private int									subsystem;
	private int									timeout;
	private long								create_time;
	private boolean								deleted;

	protected
	BuddyPluginBuddyMessage(
		BuddyPluginBuddyMessageHandler		_handler,
		int									_id,
		int									_subsystem,
		Map									_request,
		int									_timeout,
		long								_create_time )

		throws BuddyPluginException
	{
		handler		= _handler;
		id			= _id;
		subsystem	= _subsystem;
		timeout		= _timeout;
		create_time	= _create_time;

		if ( _request != null ){

			handler.writeRequest( this, _request );
		}
	}

	public BuddyPluginBuddy
	getBuddy()
	{
		return( handler.getBuddy());
	}

	public int
	getID()
	{
		return( id );
	}

	public int
	getSubsystem()
	{
		return( subsystem );
	}

	public Map
	getRequest()

		throws BuddyPluginException
	{
		return( handler.readRequest( this ));
	}

		/**
		 * Only available for pending-success messages, so don't make public
		 * @return
		 * @throws BuddyPluginException
		 */

	protected Map
	getReply()

		throws BuddyPluginException
	{
		return( handler.readReply( this ));
	}

	protected int
	getTimeout()
	{
		return( timeout );
	}

	protected long
	getCreateTime()
	{
		return( create_time );
	}

	public void
	delete()
	{
		deleted = true;

		handler.deleteMessage( this );
	}

	public boolean
	isDeleted()
	{
		return( deleted );
	}
}
