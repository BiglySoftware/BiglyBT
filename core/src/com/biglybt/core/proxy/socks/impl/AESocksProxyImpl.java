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

package com.biglybt.core.proxy.socks.impl;

import java.io.IOException;

import com.biglybt.core.proxy.*;
import com.biglybt.core.proxy.socks.AESocksProxy;
import com.biglybt.core.proxy.socks.AESocksProxyConnection;
import com.biglybt.core.proxy.socks.AESocksProxyPlugableConnection;
import com.biglybt.core.proxy.socks.AESocksProxyPlugableConnectionFactory;

/**
 * @author parg
 *
 */

public class
AESocksProxyImpl
	implements AESocksProxy, AEProxyHandler
{
	private AEProxy										proxy;
	private final AESocksProxyPlugableConnectionFactory		connection_factory;

	private String	proxy_chain_host;
	private int		proxy_chain_port;
	private String	proxy_chain_version;

	public
	AESocksProxyImpl(
		int										_port,
		long									_ct,
		long									_rt,
		AESocksProxyPlugableConnectionFactory	_connection_factory )
		throws AEProxyException
	{
		connection_factory	= _connection_factory;

		proxy = AEProxyFactory.create( _port, _ct, _rt, this );
	}

	@Override
	public int
	getPort()
	{
		return( proxy.getPort());
	}

	@Override
	public AESocksProxyPlugableConnection
	getDefaultPlugableConnection(
		AESocksProxyConnection		basis )
	{
		return( new AESocksProxyPlugableConnectionDefault(basis ));
	}

	@Override
	public AEProxyState
	getInitialState(
		AEProxyConnection	connection )

		throws IOException
	{
		return( new AESocksProxyConnectionImpl( this, connection_factory, connection ).getInitialState());
	}

	@Override
	public void
	setNextSOCKSProxy(
		String		host,
		int			port,
		String		proxy_version )
	{
		proxy_chain_host		= host;
		proxy_chain_port		= port;
		proxy_chain_version		= proxy_version;
	}

	@Override
	public String
	getNextSOCKSProxyHost()
	{
		return( proxy_chain_host );
	}

	@Override
	public int
	getNextSOCKSProxyPort()
	{
		return( proxy_chain_port );
	}

	@Override
	public String
	getNextSOCKSProxyVersion()
	{
		return( proxy_chain_version );
	}

	@Override
	public void
	setAllowExternalConnections(
		boolean	permit )
	{
		proxy.setAllowExternalConnections( permit );
	}

	@Override
	public void
	destroy()
	{
		proxy.destroy();
	}
}
