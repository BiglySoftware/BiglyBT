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
	public static final int AT_IPV4	= 0;
	public static final int AT_IPV6	= 1;
	public static final int AT_I2P	= 2;
	
	private static final int[] BYTES_PER_ENTRY = { 4, 18, 32 };
	
	private final int	address_type;
	
	
	private int		interval;
	private int		leechers;
	private int		seeders;

	private byte[][]		addresses;
	private short[]		ports;

	public
	PRUDPPacketReplyAnnounce2(
		int			trans_id,
		int			_address_type )
	{
		super( PRUDPPacketTracker.ACT_REPLY_ANNOUNCE, trans_id );
		
		address_type = _address_type;
	}

	protected
	PRUDPPacketReplyAnnounce2(
		DataInputStream		is,
		int					trans_id,
		int					_address_type )

		throws IOException
	{
		super( PRUDPPacketTracker.ACT_REPLY_ANNOUNCE, trans_id );

		address_type = _address_type;
		
		interval = is.readInt();
		leechers = is.readInt();
		seeders  = is.readInt();

		int bpe = BYTES_PER_ENTRY[address_type];
		
		int num = is.available()/bpe;
		
		addresses 	= new byte[num][];
		ports		= new short[num];

		if ( address_type == AT_I2P ){
		
			for (int i=0;i<num;i++){

				byte[] a = addresses[i] = new byte[bpe];
				
				is.read( a );
				
				boolean all_z = true;
				
				for ( int j=0; j<bpe; j++ ){
					
					if ( a[j] != (byte)0){
						
						all_z = false;
						
						break;
					}
				}
				
				if ( all_z ){
					
					num = i;
					
					byte[][]	t_addresses	= new byte[num][];
					short[]		t_ports		= new short[num];

					for ( int j=0; j<num; j++ ){
						
						t_addresses[j]	= addresses[j];
						t_ports[j]		= ports[j];
					}
					
					addresses 	= t_addresses;
					ports		= t_ports;
					
					break;
				}
			}
		}else{
			
			for (int i=0;i<num;i++){
				
				addresses[i] = new byte[bpe-2];
				
				is.read( addresses[i] );
				
				ports[i]		= is.readShort();
			}
		}
	}

	public int
	getAddressType()
	{
		return( address_type );
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

