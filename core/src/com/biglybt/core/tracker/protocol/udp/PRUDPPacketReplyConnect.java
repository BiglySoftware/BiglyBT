/*
 * File    : PRUDPPacketReplyConnect.java
 * Created : 20-Jan-2004
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.tracker.protocol.udp;

/**
 * @author parg
 *
 */

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.biglybt.net.udp.uc.PRUDPPacketReply;

public class
PRUDPPacketReplyConnect
	extends PRUDPPacketReply
{
	protected long	connection_id;

	public
	PRUDPPacketReplyConnect(
		int			trans_id,
		long		conn_id )
	{
		super( PRUDPPacketTracker.ACT_REPLY_CONNECT, trans_id );

		connection_id	= conn_id;
	}

	protected
	PRUDPPacketReplyConnect(
		DataInputStream		is,
		int					trans_id )

		throws IOException
	{
		super( PRUDPPacketTracker.ACT_REPLY_CONNECT, trans_id );

		connection_id = is.readLong();
	}

	public long
	getConnectionId()
	{
		return( connection_id );
	}

	@Override
	public void
	serialise(
		DataOutputStream	os )

		throws IOException
	{
		super.serialise(os);

		os.writeLong( connection_id );
	}

	@Override
	public String
	getString()
	{
		return( super.getString().concat(",[con=").concat(String.valueOf(connection_id)).concat("]"));
	}
}
