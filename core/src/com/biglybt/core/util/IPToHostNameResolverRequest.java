/*
 * Created on 12-Nov-2004
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

import java.net.InetAddress;

public class
IPToHostNameResolverRequest
	extends AERunnable
{
	protected final String						ip;
	protected IPToHostNameResolverListener	listener;

	protected
	IPToHostNameResolverRequest(
		String							_ip,
		IPToHostNameResolverListener	_listener )
	{
		ip			= _ip;
		listener	= _listener;
	}

	@Override
	public void 
	runSupport()
	{
		IPToHostNameResolverListener l = listener;
		
		if ( l != null ){

			if ( AENetworkClassifier.categoriseAddress( ip ) == AENetworkClassifier.AT_PUBLIC ){

				try{
					InetAddress addr = InetAddress.getByName( ip );

					l.IPResolutionComplete( addr.getHostName(), true );

				}catch( Throwable e ){

					l.IPResolutionComplete( ip, false );

				}
			}else{

				l.IPResolutionComplete( ip, true );
			}
		}		
	}
	
	public void
	cancel()
	{
		listener	= null;
	}

	protected String
	getIP()
	{
		return( ip );
	}

	protected IPToHostNameResolverListener
	getListener()
	{
		return( listener );
	}
}