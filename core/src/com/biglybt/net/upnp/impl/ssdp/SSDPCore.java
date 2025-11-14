/*
 * Created on 14-Jun-2004
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

package com.biglybt.net.upnp.impl.ssdp;

import java.net.*;
import java.util.*;

import com.biglybt.core.util.*;
import com.biglybt.net.udp.mc.MCGroup;
import com.biglybt.net.udp.mc.MCGroupAdapter;
import com.biglybt.net.udp.mc.MCGroupFactory;
import com.biglybt.net.upnp.UPnPException;
import com.biglybt.net.upnp.UPnPSSDP;
import com.biglybt.net.upnp.UPnPSSDPAdapter;
import com.biglybt.net.upnp.UPnPSSDPListener;
import com.biglybt.pif.utils.UTTimer;
import com.biglybt.pif.utils.UTTimerEvent;
import com.biglybt.pif.utils.UTTimerEventPerformer;

/**
 * @author parg
 *
 */

public class
SSDPCore
	implements UPnPSSDP, MCGroupAdapter
{
	private static Map			singletons	= new HashMap();
	private static AEMonitor	class_mon 	= new AEMonitor( "SSDPCore:class" );

	public static SSDPCore
	getSingleton(
		UPnPSSDPAdapter 	adapter,
		String				group_address_v4,
		String				group_address_v6,
		int					group_port,
		int					control_port,
		String[]			selected_interfaces )

		throws UPnPException
	{
		try{
			class_mon.enter();

			String	key = group_address_v4 + "/" + group_address_v6 + ":" + group_port + ":" + control_port;

			SSDPCore	singleton = (SSDPCore)singletons.get( key );
			
			if ( singleton == null ){

				SSDPCoreImpl	core_v4 = null;
				SSDPCoreImpl	core_v6 = null;

				if ( group_address_v4 != null ){
					
					core_v4 = new SSDPCoreImpl( adapter, group_address_v4, group_port, control_port, selected_interfaces );
				}
				
				if ( group_address_v6 != null ){
					
					core_v6 = new SSDPCoreImpl( adapter, group_address_v6, group_port, control_port, selected_interfaces );
				}
				
				singleton = new SSDPCore( core_v4, core_v6, adapter );

				singletons.put( key, singleton );
			}

			return( singleton );

		}finally{

			class_mon.exit();
		}
	}
	
	private final UPnPSSDPAdapter	adapter;
	private final SSDPCoreImpl		core_v4;
	private final SSDPCoreImpl		core_v6;

	private final List<SSDPCoreImpl>		cores = new ArrayList<>(2);
	
	private
	SSDPCore(
		SSDPCoreImpl	_core_v4,
		SSDPCoreImpl	_core_v6,
		UPnPSSDPAdapter	_adapter )
	{
		core_v4		= _core_v4;
		core_v6		= _core_v6;
		
		if ( core_v4 != null ){
			cores.add( core_v4 );
		}
		if ( core_v6 != null ){
			cores.add( core_v6 );
		}
		
		adapter = _adapter;
	}
	
	public int
	getControlPort(
		boolean		v4 )
	{
		if ( v4 ){
			if ( core_v4 != null ){
				return( core_v4.getControlPort());
			}
		}else{
			if ( core_v6 != null ){
				return( core_v6.getControlPort());
			}
		}
		
		return( -1 );
	}

	public void
	search(
		String[]		STs )
	{
		for ( SSDPCoreImpl core: cores ){
			core.search(STs);
		}
	}

	public void
	notify(
		String		NT,
		String		NTS,
		String		UUID,
		String		url )
	{
		for ( SSDPCoreImpl core: cores ){
			core.notify(NT, NTS, UUID, url);
		}
	}

	public void
	addListener(
		UPnPSSDPListener	l )
	{
		for ( SSDPCoreImpl core: cores ){
			core.addListener(l);
		}
	}

	public void
	removeListener(
		UPnPSSDPListener	l )
	{
		for ( SSDPCoreImpl core: cores ){
			core.removeListener(l);
		}
	}
	
	public void
	received(
		NetworkInterface	network_interface,
		InetAddress			local_address,
		InetSocketAddress	originator,
		byte[]				data,
		int					length )
	{
		if ( originator.getAddress() instanceof Inet4Address ){
			if ( core_v4 != null ){
				core_v4.received(network_interface, local_address, originator, data, length);
			}
		}else if ( originator.getAddress() instanceof Inet6Address ){
			if ( core_v6 != null ){
				core_v6.received(network_interface, local_address, originator, data, length);
			}
		}
	}

	public void
	interfaceChanged(
		NetworkInterface	network_interface )
	{
		for ( SSDPCoreImpl core: cores ){
			core.interfaceChanged(network_interface);
		}
	}
	
	@Override
	public void
	trace(
		String	str )
	{
		adapter.log( str );
	}

	@Override
	public void
	log(
		Throwable	e )
	{
		adapter.log( e );
	}
	
	@Override
	public void
	log(
		String		msg,
		Throwable	e )
	{
		adapter.log( msg, e );
	}
}