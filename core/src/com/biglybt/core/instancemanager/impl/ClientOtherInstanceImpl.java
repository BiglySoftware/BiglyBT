/*
 * Created on 20-Dec-2005
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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.core.util.SystemTime;


public class
ClientOtherInstanceImpl
	extends ClientInstanceImpl
{
	protected static ClientOtherInstanceImpl
	decode(
		InetAddress		internal_address,
		Map				map )
	{
		String	id			= new String((byte[])map.get( "id" ));
		String	int_ip		= new String((byte[])map.get( "iip" ));
		String	ext_ip		= new String((byte[])map.get( "eip" ));
		int		tcp			= ((Long)map.get("tp" )).intValue();
		int		udp			= ((Long)map.get("dp" )).intValue();

		Long	l_udp_other = (Long)map.get("dp2" );

		int		udp_other	= l_udp_other==null?udp:l_udp_other.intValue();

		byte[]	app_id_bytes = (byte[])map.get( "ai" );

		String app_id;

		if ( app_id_bytes == null ){

			app_id = SystemProperties.AZ_APP_ID + "_4.2.0.2";	// we dont know, but this is most likely

		}else{

			app_id = new String( app_id_bytes );
		}

		Map<String,Object>	props = (Map<String,Object>)map.get( "pr" );

		try{
			List<InetAddress>	internal_addresses = new ArrayList<>(2);
			
			internal_addresses.add( internal_address );
			
			if ( !int_ip.equals("0.0.0.0")){

				internal_address = InetAddress.getByName( int_ip );
				
				if ( !internal_addresses.contains( internal_address )){
					
					internal_addresses.add( 0, internal_address );
				}
			}

			InetAddress	external_address = InetAddress.getByName( ext_ip );

				// ignore incompatible address mappings

			if ( internal_address instanceof Inet4Address == external_address instanceof Inet4Address ){

				return( new ClientOtherInstanceImpl(id, app_id, internal_addresses, external_address, tcp, udp, udp_other, props ));
			}

			return( null );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}

		return( null );
	}

	private final String				id;
	private final String				app_id;
	
	private final CopyOnWriteList<InetAddress>		internal_addresses	= new CopyOnWriteList<>();
	
	private InetAddress					external_address;
	private int							tcp_port;
	private int							udp_port;
	private final int					udp_non_data_port;
	private final Map<String,Object>	props;

	private long	alive_time;


	protected 
	ClientOtherInstanceImpl(
		String					_id,
		String					_app_id,
		List<InetAddress>		_internal_addresses,
		InetAddress				_external_address,
		int						_tcp_port,
		int						_udp_port,
		int						_udp_non_data_port,
		Map<String,Object>		_props )
	{
		id					= _id;
		app_id				= _app_id;

		internal_addresses.addAll( _internal_addresses );

		external_address	= _external_address;
		tcp_port			= _tcp_port;
		udp_port			= _udp_port;
		udp_non_data_port	= _udp_non_data_port;

		props				= _props;

		alive_time	= SystemTime.getCurrentTime();
	}

	protected boolean
	update(
		ClientOtherInstanceImpl new_inst )
	{
		alive_time	= SystemTime.getCurrentTime();

		List<InetAddress>	new_addresses = new_inst.getInternalAddresses();

		boolean	same = true;

		int	pos = 0;
		
		for ( InetAddress new_address: new_addresses ){
			
			if ( !internal_addresses.contains( new_address )){
	
				same	= false;
	
				internal_addresses.add( pos++, new_address );
			}
		}
		
		same	 = 	same &&
					external_address.equals( new_inst.external_address ) &&
					tcp_port == new_inst.tcp_port  &&
					udp_port == new_inst.udp_port;


		external_address	= new_inst.external_address;
		tcp_port			= new_inst.tcp_port;
		udp_port			= new_inst.udp_port;

		return( !same );
	}

	@Override
	public String
	getID()
	{
		return( id );
	}

	@Override
	public String
	getApplicationID()
	{
		return( app_id );
	}

	@Override
	public InetAddress
	getInternalAddress()
	{
		return( internal_addresses.get(0));
	}

	@Override
	public List<InetAddress>
	getInternalAddresses()
	{
		return( internal_addresses.getList());
	}

	@Override
	public InetAddress
	getExternalAddress()
	{
		return( external_address );
	}

	@Override
	public int
	getTCPListenPort()
	{
		return( tcp_port );
	}

	@Override
	public int
	getUDPListenPort()
	{
		return( udp_port );
	}

	@Override
	public int
	getUDPNonDataListenPort()
	{
		return( udp_non_data_port );
	}

	@Override
	public Map<String, Object>
	getProperties()
	{
		return( props );
	}

	protected long
	getAliveTime()
	{
		long	now = SystemTime.getCurrentTime();

		if ( now < alive_time ){

			alive_time	= now;
		}

		return( alive_time );
	}
}
