/*
 * Created on 19 Jun 2006
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

package com.biglybt.pif.messaging.generic;

import com.biglybt.pif.messaging.MessageException;
import com.biglybt.pif.network.Connection;
import com.biglybt.pif.network.RateLimiter;
import com.biglybt.pif.utils.PooledByteBuffer;

public interface
GenericMessageConnection
{
	public static final int TT_NONE			= 0;
	public static final int TT_TCP			= 0;
	public static final int TT_UDP			= 0;
	public static final int TT_INDIRECT		= 0;

	public GenericMessageEndpoint
	getEndpoint();

		/**
		 * @return may be null if unknown
		 */
	
	public GenericMessageStartpoint
	getStartpoint();

	public void
	connect(
		GenericMessageConnectionPropertyHandler l )

		throws MessageException;

	public Connection
	getConnection();
	
	public void
	send(
		PooledByteBuffer			message )

		throws MessageException;

	public void
	close()

		throws MessageException;

	public int
	getMaximumMessageSize();

	public String
	getType();

	public int
	getTransportType();

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
	addListener(
		GenericMessageConnectionListener		listener );

	public void
	removeListener(
		GenericMessageConnectionListener		listener );
	
	public interface
	GenericMessageConnectionPropertyHandler
	{
		public Object 
		getConnectionProperty(
			String property_name);
	}
}
