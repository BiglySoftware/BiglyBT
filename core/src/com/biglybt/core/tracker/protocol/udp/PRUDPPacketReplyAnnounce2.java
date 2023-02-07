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
PRUDPPacketReplyAnnounce2
	extends PRUDPPacketReply
{
	private final boolean	is_ipv6;
	
	protected int		interval;
	protected int		leechers;
	protected int		seeders;

	protected static final int BYTES_PER_ENTRY_IPV4 = 6;
	protected static final int BYTES_PER_ENTRY_IPV6 = 18;
	
	protected byte[][]		addresses;
	protected short[]		ports;

	public
	PRUDPPacketReplyAnnounce2(
		int			trans_id,
		boolean		ipv6 )
	{
		super( PRUDPPacketTracker.ACT_REPLY_ANNOUNCE, trans_id );
		
		is_ipv6 = ipv6;
	}

	protected
	PRUDPPacketReplyAnnounce2(
		DataInputStream		is,
		int					trans_id,
		boolean				ipv6 )

		throws IOException
	{
		super( PRUDPPacketTracker.ACT_REPLY_ANNOUNCE, trans_id );

		is_ipv6 = ipv6;
		
		interval = is.readInt();
		leechers = is.readInt();
		seeders  = is.readInt();

		int bpe = is_ipv6?BYTES_PER_ENTRY_IPV6:BYTES_PER_ENTRY_IPV4;
		
		int num = is.available()/bpe;
		
		addresses 	= new byte[num][];
		ports		= new short[num];

		for (int i=0;i<num;i++){

			addresses[i] = new byte[bpe-2];
			
			is.read( addresses[i] );
			
			ports[i]		= is.readShort();
		}
	}

	public boolean
	isIPV6()
	{
		return( is_ipv6 );
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
	setLeechersSeeders(
		int		_leechers,
		int		_seeders )
	{
		leechers	= _leechers;
		seeders		= _seeders;
	}

	public void
	setPeers(
		byte[][]		_addresses,
		short[]			_ports )
	{
		addresses 	= _addresses;
		ports		= _ports;
	}

	public byte[][]
	getAddresses()
	{
		return( addresses );
	}

	public short[]
	getPorts()
	{
		return( ports );
	}

	public int
	getLeechers()
	{
		return( leechers );
	}

	public int
	getSeeders()
	{
		return( seeders );
	}

	@Override
	public void
	serialise(
		DataOutputStream	os )

		throws IOException
	{
		super.serialise(os);

		os.writeInt( interval );
		os.writeInt( leechers );
		os.writeInt( seeders );

		if ( addresses != null ){

			for (int i=0;i<addresses.length;i++){

				os.write( addresses[i] );
				os.writeShort( ports[i] );
			}
		}
	}

	@Override
	public String
	getString()
	{
		return( super.getString() +
				"[interval="+interval+
				",leechers="+leechers+
				",seeders="+seeders+
				",addresses="+addresses.length+"]");
	}
}

