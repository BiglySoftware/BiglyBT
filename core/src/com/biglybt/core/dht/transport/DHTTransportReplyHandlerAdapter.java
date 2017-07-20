/*
 * Created on 12-Jan-2005
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

package com.biglybt.core.dht.transport;

import java.util.List;

import com.biglybt.core.dht.transport.util.DHTTransportStatsImpl;
import com.biglybt.core.util.Debug;

/**
 * @author parg
 *
 */

public abstract class
DHTTransportReplyHandlerAdapter
	implements DHTTransportReplyHandler
{
	private int		elapsed;

	@Override
	public void
	pingReply(
		DHTTransportContact contact,
		int					_elapsed )
	{
		elapsed	= _elapsed;

		DHTTransportStats stats = contact.getTransport().getStats();

		if ( stats instanceof DHTTransportStatsImpl ){

			if ( _elapsed >= 0 ){

				((DHTTransportStatsImpl)stats).receivedRTT( _elapsed );
			}
		}

		pingReply( contact );
	}

	public void
	pingReply(
		DHTTransportContact contact )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	public int
	getElapsed()
	{
		return( elapsed );
	}

	@Override
	public void
	statsReply(
		DHTTransportContact 	contact,
		DHTTransportFullStats	stats )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	@Override
	public void
	storeReply(
		DHTTransportContact contact,
		byte[]				diversifications )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	@Override
	public void
	queryStoreReply(
		DHTTransportContact contact,
		List<byte[]>		response )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	@Override
	public void
	findNodeReply(
		DHTTransportContact 	contact,
		DHTTransportContact[]	contacts )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	@Override
	public void
	findValueReply(
		DHTTransportContact 	contact,
		DHTTransportValue[]		values,
		byte					diversification_type,
		boolean					more_to_come )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	@Override
	public void
	findValueReply(
		DHTTransportContact 	contact,
		DHTTransportContact[]	contacts )
	{
		throw( new RuntimeException( "Not implemented" ));
	}

	@Override
	public void
	keyBlockRequest(
		DHTTransportContact		contact,
		byte[]					key,
		byte[]					key_signature )
	{
		Debug.out( "keyblock not handled" );
	}

	@Override
	public void
	keyBlockReply(
		DHTTransportContact 	_contact )
	{
		throw( new RuntimeException( "Not implemented" ));
	}
}
