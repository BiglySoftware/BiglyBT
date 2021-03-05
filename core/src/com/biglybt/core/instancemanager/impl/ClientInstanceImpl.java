/*
 * Created on 23-Dec-2005
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

package com.biglybt.core.instancemanager.impl;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import com.biglybt.core.instancemanager.ClientInstance;

public abstract class
ClientInstanceImpl
	implements ClientInstance
{
	protected ClientInstanceImpl()
	{
	}

	protected void
	encode(
		Map<String,Object>		map )
	{
		map.put( "id", getID().getBytes());

		map.put( "ai", getApplicationID().getBytes());

		map.put( "iip", getInternalAddress().getHostAddress().getBytes());

		map.put( "eip", getExternalAddress().getHostAddress().getBytes());

		map.put( "tp", new Long( getTCPListenPort()));

        map.put( "dp", new Long( getUDPListenPort()));

        map.put( "dp2", new Long( getUDPNonDataListenPort()));

        Map<String,Object> props = getProperties();

        if ( props != null ){

        	map.put( "pr", props );
        }
	}

	@Override
	public String
	getString()
	{
		String	id = getID();

		if ( id.length() > 8 ){

			id = id.substring(0,8) + "...";
		}

		List<InetAddress> internal_addresses = getInternalAddresses();
		
		String int_str = "";
		
		for ( InetAddress ia: internal_addresses ){
			
			int_str += (int_str.isEmpty()?"":";") + ia.getHostAddress();
		}
		
		return( "id=" + id +
				",ap=" + getApplicationID() +
				",int=" + int_str +
				",ext=" + getExternalAddress().getHostAddress() +
				",tcp=" + getTCPListenPort() +
				",udp=" + getUDPListenPort() +
				",udp2=" + getUDPNonDataListenPort() +
				",props=" + getProperties());
	}
}
