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

import java.net.UnknownHostException;

public class
IPToHostNameResolver
{
	private static final ThreadPool resolve_pool = new ThreadPool( "IPResolver", 8, true );
	
	public static IPToHostNameResolverRequest
	addResolverRequest(
		String							ip,
		IPToHostNameResolverListener	l )
	{
		IPToHostNameResolverRequest	request = new IPToHostNameResolverRequest( ip, l );

		resolve_pool.run( request );

		return( request );
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
