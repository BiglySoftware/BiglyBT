/*
 * File    : PRUDPPacketReplyAnnounce.java
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
PRUDPPacketReplyAnnounce
	extends PRUDPPacketReply
{
	protected int		interval;

	protected static final int BYTES_PER_ENTRY = 6;
	protected int[]		addresses;
	protected short[]	ports;

	public
	PRUDPPacketReplyAnnounce(
		int			trans_id )
	{
		super( PRUDPPacketTracker.ACT_REPLY_ANNOUNCE, trans_id );
	}

	protected
	PRUDPPacketReplyAnnounce(
		DataInputStream		is,
		int					trans_id )

		throws IOException
	{
		super( PRUDPPacketTracker.ACT_REPLY_ANNOUNCE, trans_id );

		interval = is.readInt();

		addresses 	= new int[is.available()/BYTES_PER_ENTRY];
		ports		= new short[addresses.length];

		for (int i=0;i<addresses.length;i++){

			addresses[i] 	= is.readInt();
			ports[i]		= is.readShort();
		}
	}

	public void
	setInterval(
		int		value )
	{
		interval	= value;
	}

	public int
	getInterval()
	{
		return( interval );
	}

	public void
	setPeers(
		int[]		_addresses,
		short[]		_ports )
	{
		addresses 	= _addresses;
		ports		= _ports;
	}

	public int[]
	getAddresses()
	{
		return( addresses );
	}

	public short[]
	getPorts()
	{
		return( ports );
	}

	@Override
	public void
	serialise(
		DataOutputStream	os )

		throws IOException
	{
		super.serialise(os);

		os.writeInt( interval );

		if ( addresses != null ){

			for (int i=0;i<addresses.length;i++){

				os.writeInt( addresses[i] );
				os.writeShort( ports[i] );
			}
		}
	}

	@Override
	public String
	getString()
	{
		return( super.getString().concat("[interval=").concat(String.valueOf(interval)).concat(", addresses=").concat(String.valueOf(addresses.length)).concat("]") );
	}
}

