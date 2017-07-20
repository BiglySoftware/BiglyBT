/*
 * Created on 08-Dec-2004
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

package com.biglybt.core.proxy.socks;

/**
 * @author parg
 *
 */

import com.biglybt.core.proxy.AEProxyException;
import com.biglybt.core.proxy.socks.impl.AESocksProxyImpl;
import com.biglybt.core.proxy.socks.impl.AESocksProxyPlugableConnectionDefault;

public class
AESocksProxyFactory
{
	public static AESocksProxy
	create(
		int			port,
		long		connect_timeout,
		long		read_timeout )

		throws AEProxyException
	{
		return( create( port,
						connect_timeout,
						read_timeout,
						new AESocksProxyPlugableConnectionFactory()
						{
							@Override
							public AESocksProxyPlugableConnection
							create(
								AESocksProxyConnection	connection )
							{
								return( new AESocksProxyPlugableConnectionDefault( connection ));
							}
						}));
	}

	public static AESocksProxy
	create(
		int										port,
		long									connect_timeout,
		long									read_timeout,
		AESocksProxyPlugableConnectionFactory	connection_factory )

			throws AEProxyException
	{
		return( new AESocksProxyImpl( port, connect_timeout, read_timeout, connection_factory ));
	}

	public static void
	main(
		String[]	args )
	{
		try{
			AESocksProxy	proxy = create( 1080, 30*1000, 30*1000 );

			Thread.sleep(24*60*60*10000);

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}
}
