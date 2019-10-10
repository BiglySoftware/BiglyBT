/*
 * Created on Jul 11, 2007
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

import java.net.Inet4Address;
import java.net.InetAddress;

import com.biglybt.core.networkmanager.admin.NetworkAdminASN;
import com.biglybt.core.networkmanager.admin.NetworkAdminException;
import com.biglybt.core.tracker.protocol.PRHelpers;
import com.biglybt.core.util.Debug;

public class
NetworkAdminASNImpl
	implements NetworkAdminASN
{
	private final  boolean		ipv4;		
	private final String		as;
	private String				asn;
	private final String		bgp_prefix;



	protected
	NetworkAdminASNImpl(
		boolean		_ipv4,
		String		_as,
		String		_asn,
		String		_bgp )
	{
		ipv4		= _ipv4;
		as			= _as;
		asn			= _asn;
		bgp_prefix	= _bgp;
	}
	
	protected boolean
	isIPv4()
	{
		return( ipv4 );
	}

	@Override
	public String
	getAS()
	{
		return( as==null?"":as );
	}

	@Override
	public String
	getASName()
	{
		return( asn==null?"":asn );
	}

	protected void
	setASName(
		String		s )
	{
		asn		= s;
	}

	@Override
	public String
	getBGPPrefix()
	{
		return( bgp_prefix==null?"":bgp_prefix );
	}

	@Override
	public InetAddress
	getBGPStartAddress()
	{
		if ( bgp_prefix == null ){

			return( null );
		}

		try{
			return( getCIDRStartAddress());

		}catch( NetworkAdminException e ){

			Debug.out(e);

			return( null );
		}
	}

	protected InetAddress
	getCIDRStartAddress()

		throws NetworkAdminException
	{
		int	pos = bgp_prefix.indexOf('/');

		try{
			return( InetAddress.getByName( bgp_prefix.substring(0,pos)));

		}catch( Throwable e ){

			throw( new NetworkAdminException( "Parse failure for '" + bgp_prefix + "'", e ));
		}
	}

	protected InetAddress
	getCIDREndAddress()

		throws NetworkAdminException
	{

		int	pos = bgp_prefix.indexOf('/');

		try{
			InetAddress	start = InetAddress.getByName( bgp_prefix.substring(0,pos));

			int	cidr_mask = Integer.parseInt( bgp_prefix.substring( pos+1 ));

			int	rev_mask = 0;

			for (int i=0;i<32-cidr_mask;i++){


				rev_mask = ( rev_mask << 1 ) | 1;
			}

			byte[]	bytes = start.getAddress();

			bytes[0] |= (rev_mask>>24)&0xff;
			bytes[1] |= (rev_mask>>16)&0xff;
			bytes[2] |= (rev_mask>>8)&0xff;
			bytes[3] |= (rev_mask)&0xff;

			return( InetAddress.getByAddress( bytes ));

		}catch( Throwable e ){

			throw( new NetworkAdminException( "Parse failure for '" + bgp_prefix + "'", e ));
		}
	}

	@Override
	public boolean
	matchesCIDR(
		InetAddress	address )
	{
		if ( bgp_prefix == null || bgp_prefix.length() == 0 ){

			return( false );
		}

		boolean	isv4 = address instanceof Inet4Address;
		
		if ( isv4 != ipv4 ){
			
			return( false );
		}
		
		try{
			InetAddress	start	= getCIDRStartAddress();
			InetAddress	end		= getCIDREndAddress();

			long	l_start = PRHelpers.addressToLong( start );
			long	l_end	= PRHelpers.addressToLong( end );

			long	test = PRHelpers.addressToLong( address );

			return( test >= l_start && test <= l_end );

		}catch( Throwable e ){

			Debug.printStackTrace(e);

			return( false );
		}
	}

	@Override
	public InetAddress
	getBGPEndAddress()
	{
		if ( bgp_prefix == null ){

			return( null );
		}

		try{
			return( getCIDREndAddress());

		}catch( NetworkAdminException e ){

			Debug.out(e);

			return( null );
		}
	}

	@Override
	public boolean
	sameAs(
		NetworkAdminASN	other )
	{
		return( getAS().equals( other.getAS()));
	}

	@Override
	public String
	getString()
	{
		return( "as=" + getAS() + ",asn=" + getASName() + ", bgp_prefx=" + getBGPPrefix() + "[" +getBGPStartAddress() + "-" + getBGPEndAddress() + "]" );
	}
}
