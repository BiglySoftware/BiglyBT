/*
 * Created on 16-Sep-2005
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

package com.biglybt.net.upnp.impl.services;

import com.biglybt.net.upnp.*;
import com.biglybt.net.upnp.services.UPnPWANCommonInterfaceConfig;

public class
UPnPSSWANCommonInterfaceConfigImpl
	implements UPnPWANCommonInterfaceConfig
{
	private UPnPServiceImpl		service;

	protected
	UPnPSSWANCommonInterfaceConfigImpl(
		UPnPServiceImpl		_service )
	{
		service = _service;
	}

	@Override
	public UPnPService
	getGenericService()
	{
		return( service );
	}

	@Override
	public long[]
	getCommonLinkProperties()

		throws UPnPException
	{
		UPnPAction act = service.getAction( "GetCommonLinkProperties" );

		if ( act == null ){

			service.getDevice().getRootDevice().getUPnP().log( "Action 'GetCommonLinkProperties' not supported, binding not established" );

			throw( new UPnPException( "GetCommonLinkProperties not supported" ));

		}else{

			UPnPActionInvocation inv = act.getInvocation();

			UPnPActionArgument[]	args = inv.invoke();

			long[]	res = new long[2];

			for (int i=0;i<args.length;i++){

				UPnPActionArgument	arg = args[i];

				String	name = arg.getName();

				if ( name.equalsIgnoreCase("NewLayer1UpstreamMaxBitRate")){

					res[1] = Long.parseLong( arg.getValue());

				}else if ( name.equalsIgnoreCase("NewLayer1DownstreamMaxBitRate")){

					res[0] = Long.parseLong( arg.getValue());
				}
			}

			return( res );
		}
	}
	
	@Override
	public long
	getTotalBytesSent()

		throws UPnPException
	{
		UPnPAction act = service.getAction( "getTotalBytesSent" );

		if ( act == null ){

			throw( new UPnPException( "getTotalBytesSent not supported" ));

		}else{

			UPnPActionInvocation inv = act.getInvocation();

			UPnPActionArgument[]	args = inv.invoke();

			for (int i=0;i<args.length;i++){

				UPnPActionArgument	arg = args[i];

				String	name = arg.getName();

				if ( name.equalsIgnoreCase("NewTotalBytesSent")){

					return( Long.parseLong( arg.getValue()));

				}
			}

			throw( new UPnPException( "getTotalBytesSent failed to return result" ));
		}
	}
	
	@Override
	public long
	getTotalBytesReceived()

		throws UPnPException
	{
		UPnPAction act = service.getAction( "getTotalBytesReceived" );

		if ( act == null ){

			throw( new UPnPException( "getTotalBytesReceived not supported" ));

		}else{

			UPnPActionInvocation inv = act.getInvocation();

			UPnPActionArgument[]	args = inv.invoke();

			for (int i=0;i<args.length;i++){

				UPnPActionArgument	arg = args[i];

				String	name = arg.getName();

				if ( name.equalsIgnoreCase("NewTotalBytesReceived")){

					return( Long.parseLong( arg.getValue()));

				}
			}

			throw( new UPnPException( "getTotalBytesReceived failed to return result" ));
		}
	}
}
