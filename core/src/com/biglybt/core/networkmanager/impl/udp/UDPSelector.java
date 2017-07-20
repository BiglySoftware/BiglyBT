/*
 * Created on 23 Jun 2006
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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.networkmanager.impl.TransportHelper;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;

public class
UDPSelector
{
	static final int POLL_FREQUENCY	= COConfigurationManager.getIntParameter( "network.udp.poll.time", 100 );

	final List		ready_set	= new LinkedList();
	final AESemaphore	ready_sem	= new AESemaphore( "UDPSelector" );

	volatile boolean destroyed;

	protected
	UDPSelector(
		final UDPConnectionManager		manager )
	{
		new AEThread2( "UDPSelector", true )
		{
			@Override
			public void
			run()
			{
				boolean	quit		= false;
				long	last_poll	= 0;

				while( !quit ){

					if ( destroyed ){

							// one last dispatch cycle

						quit	= true;
					}

					long	now = SystemTime.getCurrentTime();

					if ( now < last_poll || now - last_poll >= POLL_FREQUENCY ){

						manager.poll();

						last_poll	= now;
					}

					if ( ready_sem.reserve(POLL_FREQUENCY/2)){

						Object[]	entry;

						synchronized( ready_set ){

							if ( ready_set.size() == 0 ){

								continue;
							}

							entry = (Object[])ready_set.remove(0);
						}


						TransportHelper	transport 	= (TransportHelper)entry[0];

						TransportHelper.selectListener	listener = (TransportHelper.selectListener)entry[1];

						if ( listener == null ){

							Debug.out( "Null listener" );

						}else{

							Object	attachment = entry[2];

							try{
								if ( entry.length == 3 ){

									listener.selectSuccess( transport, attachment );

								}else{

									listener.selectFailure( transport, attachment, (Throwable)entry[3] );

								}
							}catch( Throwable e ){

								Debug.printStackTrace(e);
							}
						}
					}
				}
			}
		}.start();
	}

	protected void
	destroy()
	{
		synchronized( ready_set ){

			destroyed	= true;
		}
	}

	protected void
	ready(
		TransportHelper						transport,
		TransportHelper.selectListener		listener,
		Object								attachment )

		throws IOException
	{
		boolean	removed = false;

		synchronized( ready_set ){

			if( destroyed ){

				throw( new IOException( "Selector has been destroyed" ));
			}

			Iterator	it = ready_set.iterator();

			while( it.hasNext()){

				Object[]	entry = (Object[])it.next();

				if ( entry[1] == listener ){

					it.remove();

					removed	= true;

					break;
				}
			}

			ready_set.add( new Object[]{ transport, listener, attachment });
		}

		if ( !removed ){

			ready_sem.release();
		}
	}

	protected void
	ready(
		TransportHelper						transport,
		TransportHelper.selectListener		listener,
		Object								attachment,
		Throwable							error )

		throws IOException
	{
		boolean	removed = false;

		synchronized( ready_set ){

			if( destroyed ){

				throw( new IOException( "Selector has been destroyed" ));
			}

			Iterator	it = ready_set.iterator();

			while( it.hasNext()){

				Object[]	entry = (Object[])it.next();

				if ( entry[1] == listener ){

					it.remove();

					removed	= true;

					break;
				}
			}

			ready_set.add( new Object[]{ transport, listener, attachment, error });
		}

		if ( !removed ){

			ready_sem.release();
		}
	}

	protected void
	cancel(
		TransportHelper						transport,
		TransportHelper.selectListener		listener )
	{
		synchronized( ready_set ){

			Iterator	it = ready_set.iterator();

			while( it.hasNext()){

				Object[]	entry = (Object[])it.next();

				if ( entry[0] == transport && entry[1] == listener ){

					it.remove();

					break;
				}
			}
		}
	}
}
