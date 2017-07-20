/*
 * File    : PRUDPPacketReplyScrape.java
 * Created : 21-Jan-2004
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
PRUDPPacketReplyScrape
	extends PRUDPPacketReply
{
	// protected int		interval;

	protected static final int BYTES_PER_ENTRY = 32;
	protected byte[][]	hashes;
	protected int[]		complete;
	protected int[]		incomplete;
	protected int[]		downloaded;

	public
	PRUDPPacketReplyScrape(
		int			trans_id )
	{
		super( PRUDPPacketTracker.ACT_REPLY_SCRAPE, trans_id );
	}

	protected
	PRUDPPacketReplyScrape(
		DataInputStream		is,
		int					trans_id )

		throws IOException
	{
		super( PRUDPPacketTracker.ACT_REPLY_SCRAPE, trans_id );

		// interval = is.readInt();

		hashes 		= new byte[is.available()/BYTES_PER_ENTRY][];
		complete	= new int[hashes.length];
		incomplete	= new int[hashes.length];
		downloaded	= new int[hashes.length];

		for (int i=0;i<hashes.length;i++){

			hashes[i] = new byte[20];
			is.read(hashes[i]);
			complete[i] 	= is.readInt();
			downloaded[i] 	= is.readInt();
			incomplete[i] 	= is.readInt();
		}
	}

	/*
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
	*/

	public void
	setDetails(
		byte[][]	_hashes,
		int[]		_complete,
		int[]		_downloaded,
		int[]		_incomplete )
	{
		hashes		 	= _hashes;
		complete		= _complete;
		downloaded		= _downloaded;
		incomplete		= _incomplete;
	}

	public byte[][]
	getHashes()
	{
		return( hashes );
	}

	public int[]
	getComplete()
	{
		return( complete );
	}

	public int[]
	getDownloaded()
	{
		return( downloaded );
	}

	public int[]
	getIncomplete()
	{
		return( incomplete );
	}

	@Override
	public void
	serialise(
		DataOutputStream	os )

	throws IOException
	{
		super.serialise(os);

		// os.writeInt( interval );

		if ( hashes != null ){

			for (int i=0;i<hashes.length;i++){

				os.write( hashes[i] );
				os.writeInt( complete[i] );
				os.writeInt( downloaded[i] );
				os.writeInt( incomplete[i] );
			}
		}
	}

	@Override
	public String
	getString()
	{
		return( super.getString().concat("[hashes=").concat(String.valueOf(hashes.length)).concat("]") );
		// return( super.getString() + "[interval=" + interval + ", hashes=" + hashes.length + "]" );
	}
}
