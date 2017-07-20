/*
 * Created on 27-May-2004
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

package com.biglybt.core.util;

/**
 * @author parg
 *
 */

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class
IPToHostNameResolver
{
	static protected AEThread2		resolver_thread;
	static protected final List			request_queue		= new ArrayList();
	static protected final AEMonitor		request_mon			= new AEMonitor( "IPToHostNameResolver" );

	static protected final AESemaphore	request_semaphore	= new AESemaphore("IPToHostNameResolver");

	public static IPToHostNameResolverRequest
	addResolverRequest(
		String							ip,
		IPToHostNameResolverListener	l )
	{
		try{
			request_mon.enter();

			IPToHostNameResolverRequest	request = new IPToHostNameResolverRequest( ip, l );

			request_queue.add( request );

			request_semaphore.release();

			if ( resolver_thread == null ){

				resolver_thread =
					new AEThread2("IPToHostNameResolver", true )
					{
						@Override
						public void
						run()
						{
							while(true){

								try{
									request_semaphore.reserve(30000);

									IPToHostNameResolverRequest	req;

									try{
										request_mon.enter();

										if ( request_queue.isEmpty()){

											resolver_thread = null;

											break;
										}

										req	= (IPToHostNameResolverRequest)request_queue.remove(0);

									}finally{

										request_mon.exit();
									}

									IPToHostNameResolverListener	listener = req.getListener();

										// if listener is null the request has been cancelled

									if ( listener != null ){

										String ip = req.getIP();

										if ( AENetworkClassifier.categoriseAddress( ip ) == AENetworkClassifier.AT_PUBLIC ){

											try{
												InetAddress addr = InetAddress.getByName( ip );

												req.getListener().IPResolutionComplete( addr.getHostName(), true );

											}catch( Throwable e ){

												req.getListener().IPResolutionComplete( ip, false );

											}
										}else{

											req.getListener().IPResolutionComplete( ip, true );
										}
									}
								}catch( Throwable e ){

									Debug.printStackTrace( e );
								}
							}
						}
					};

				resolver_thread.start();
			}

			return( request );

		}finally{

			request_mon.exit();
		}
	}

	public static String
	syncResolve(
		String			ip,
		int				timeout )

		throws Exception
	{
		final AESemaphore	sem = new AESemaphore( "IPToHostNameREsolver:sync" );

		final Object[]	result = {null};

		addResolverRequest(
			ip,
			new IPToHostNameResolverListener()
			{
				@Override
				public void
				IPResolutionComplete(
					String		resolved_ip,
					boolean		succeeded )
				{
					try{
						synchronized( result ){

							if ( succeeded ){

								result[0] = resolved_ip;
							}
						}
					}finally{

						sem.release();
					}
				}
			});

		if ( !sem.reserve( timeout )){

			throw( new Exception( "Timeout" ));
		}

		synchronized( result ){

			if ( result[0] != null ){

				return((String)result[0]);
			}

			throw( new UnknownHostException( ip ));
		}
	}
}
