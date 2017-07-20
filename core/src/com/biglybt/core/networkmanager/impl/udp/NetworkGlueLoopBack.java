/*
 * Created on 22 Jun 2006
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

package com.biglybt.core.networkmanager.impl.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.biglybt.core.util.AEThread;
import com.biglybt.core.util.SystemTime;

public class
NetworkGlueLoopBack
	implements NetworkGlue

{
	private static final int latency			= 0;

	final NetworkGlueListener		listener;

	final List	message_queue	= new ArrayList();

	private final Random	random = new Random();

	protected
	NetworkGlueLoopBack(
		NetworkGlueListener		_listener )
	{
		listener	= _listener;

		new AEThread( "NetworkGlueLoopBack", true )
		{
			@Override
			public void
			runSupport()
			{
				while( true ){

					try{
						Thread.sleep(1);

					}catch( Throwable e ){

					}

					InetSocketAddress	target_address 	= null;
					InetSocketAddress	source_address 	= null;
					byte[]				data			= null;

					long	now = SystemTime.getCurrentTime();

					synchronized( message_queue ){

						if ( message_queue.size() > 0 ){

							Object[]	entry = (Object[])message_queue.get(0);

							if (((Long)entry[0]).longValue() < now ){

								message_queue.remove(0);

								source_address	= (InetSocketAddress)entry[1];
								target_address 	= (InetSocketAddress)entry[2];
								data			= (byte[])entry[3];
							}
						}
					}

					if ( source_address != null ){

						listener.receive( target_address.getPort(), source_address, data, data.length );
					}
				}
			}
		}.start();
	}

	@Override
	public int
	send(
		int					local_port,
		InetSocketAddress	target,
		byte[]				data )

		throws IOException
	{
		Long	expires = new Long( SystemTime.getCurrentTime() + latency );

		InetSocketAddress local_address = new InetSocketAddress( target.getAddress(), local_port );

		synchronized( message_queue ){

			if ( random.nextInt(4) != 9 ){

				message_queue.add( new Object[]{ expires, local_address, target, data });
			}
		}

		return( data.length );
	}

	@Override
	public long[]
	getStats()
	{
		return( new long[]{ 0,0,0,0 });
	}
}
