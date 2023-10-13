/*
 * Created on 1 Nov 2006
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


package com.biglybt.core.networkmanager.admin.impl;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import com.biglybt.core.Core;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.ipchecker.natchecker.NatChecker;
import com.biglybt.core.networkmanager.admin.NetworkAdminException;
import com.biglybt.core.networkmanager.admin.NetworkAdminProgressListener;
import com.biglybt.core.versioncheck.VersionCheckClient;

public class
NetworkAdminTCPTester
	implements NetworkAdminProtocolTester
{
	private final Core core;
	private final NetworkAdminProgressListener	listener;

	protected
	NetworkAdminTCPTester(
		Core _core,
		NetworkAdminProgressListener	_listener )
	{
		core		= _core;
		listener	= _listener;
	}

	@Override
	public InetAddress
	testOutbound(
		InetAddress		bind_ip,
		int				bind_port,
		boolean			ipv6 )

		throws NetworkAdminException
	{
		try{
				// try to use our service first

			return( VersionCheckClient.getSingleton().getExternalIpAddressTCP( bind_ip, bind_port, ipv6 ));

		}catch( Throwable e ){

				// fallback to something else

			try{
				  Socket socket = new Socket();

				  if ( bind_ip != null ){

					  socket.bind( new InetSocketAddress( bind_ip, bind_port ));

				  }else if ( bind_port != 0 ){

					  socket.bind( new InetSocketAddress( bind_port ));
				  }

				  socket.setSoTimeout( 10000 );

				  	// TODO: v6 
				  
					String domain = COConfigurationManager.getStringParameter(
						ConfigKeys.Connection.SCFG_CONNECTION_TEST_DOMAIN);
				  socket.connect( new InetSocketAddress( domain, 80 ), 10000 );

				  socket.close();

				  return( null );

			}catch( Throwable f ){

				throw( new NetworkAdminException( "Outbound test failed", e ));
			}
		}
	}

	@Override
	public InetAddress
	testInbound(
		InetAddress		bind_ip,
		int				local_port,
		boolean			ipv6 )

		throws NetworkAdminException
	{
		NatChecker	checker = new NatChecker( core, bind_ip, local_port, ipv6, false );

		if ( checker.getResult() == NatChecker.NAT_OK ){

			return( checker.getExternalAddress());

		}else{

			throw( new NetworkAdminException( "NAT test failed: " + checker.getAdditionalInfo()));
		}
	}
}
