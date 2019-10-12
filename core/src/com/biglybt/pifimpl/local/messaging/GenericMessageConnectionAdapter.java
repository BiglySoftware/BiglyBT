/*
 * Created on 9 Aug 2006
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

package com.biglybt.pifimpl.local.messaging;

import java.nio.ByteBuffer;

import com.biglybt.pif.messaging.MessageException;
import com.biglybt.pif.messaging.generic.GenericMessageEndpoint;
import com.biglybt.pif.messaging.generic.GenericMessageStartpoint;
import com.biglybt.pif.network.Connection;
import com.biglybt.pif.network.RateLimiter;
import com.biglybt.pif.utils.PooledByteBuffer;

public interface
GenericMessageConnectionAdapter
{
	public void
	setOwner(
		GenericMessageConnectionImpl	_owner );

	public GenericMessageEndpoint
	getEndpoint();

	public GenericMessageStartpoint
	getStartpoint();
	
	public int
	getMaximumMessageSize();

	public String
	getType();

	public int
	getTransportType();

	public void
	connect(
		ByteBuffer			initial_data,
		ConnectionListener	listener );

	public void
	accepted();

	public Connection
	getConnection();
	
	public void
	send(
		PooledByteBuffer			message )

		throws MessageException;

	public void
	addInboundRateLimiter(
		RateLimiter		limiter );

	public void
	removeInboundRateLimiter(
		RateLimiter		limiter );

	public void
	addOutboundRateLimiter(
		RateLimiter		limiter );

	public void
	removeOutboundRateLimiter(
		RateLimiter		limiter );

	public void
	close()

		throws MessageException;

	public interface
	ConnectionListener
	{
		public void
		connectSuccess();

		public void
		connectFailure(
			Throwable failure_msg );
		
		public Object
		getConnectionProperty(
			String property_name );
	}
}
