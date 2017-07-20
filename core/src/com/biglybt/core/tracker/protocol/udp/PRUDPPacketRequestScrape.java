/*
 * File    : PRUDPPacketRequestScrape.java
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.HashWrapper;
import com.biglybt.net.udp.uc.PRUDPPacketRequest;

public class
PRUDPPacketRequestScrape
	extends PRUDPPacketRequest
{
	protected final List		hashes;

	public
	PRUDPPacketRequestScrape(
		long			con_id,
		byte[]			_hash)
	{
		super( PRUDPPacketTracker.ACT_REQUEST_SCRAPE, con_id );

		hashes = new ArrayList();
		hashes.add(_hash);
	}

	public
	PRUDPPacketRequestScrape(
		long			con_id,
		List			hashwrappers)
	{
		super( PRUDPPacketTracker.ACT_REQUEST_SCRAPE, con_id );
		hashes = new ArrayList();
		for(Iterator it = hashwrappers.iterator();it.hasNext();)
			hashes.add(((HashWrapper)it.next()).getBytes());
	}

	protected
	PRUDPPacketRequestScrape(
		DataInputStream		is,
		long				con_id,
		int					trans_id )

		throws IOException
	{
		super( PRUDPPacketTracker.ACT_REQUEST_SCRAPE, con_id, trans_id );
		hashes = new ArrayList();
		byte[] hash;
		while(is.read(hash = new byte[20]) == 20)
			hashes.add(hash);
	}

	public List
	getHashes()
	{
		return hashes;
	}

	@Override
	public void
	serialise(
		DataOutputStream	os )

	throws IOException
	{
		super.serialise(os);

		for(Iterator it = hashes.iterator();it.hasNext();)
			os.write((byte[])it.next());
	}

	@Override
	public String
	getString()
	{
		StringBuilder buf = new StringBuilder();
		buf.append(super.getString());
		buf.append("[");
		for(Iterator it = hashes.iterator();it.hasNext();)
		{
			buf.append(ByteFormatter.nicePrint( (byte[])it.next(), true ));
			if(it.hasNext())
				buf.append(";");
		}
		buf.append("]");
		return buf.toString();
	}
}

