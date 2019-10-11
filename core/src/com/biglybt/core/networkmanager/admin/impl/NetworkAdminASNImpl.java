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

			byte[]	bytes = start.getAddress();

			for ( int i=cidr_mask;i<bytes.length*8;i++){
				
				bytes[i/8] |= 1<<(7-(i%8));
			}
			
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
			int	pos = bgp_prefix.indexOf('/');

			InetAddress	start = InetAddress.getByName( bgp_prefix.substring(0,pos));

			int	cidr_mask = Integer.parseInt( bgp_prefix.substring( pos+1 ));

			byte[] prefix = start.getAddress();
			
			byte[] bytes  = address.getAddress();
			
			for ( int i=0;i< cidr_mask; i++ ){
				
				byte mask = (byte)( 1<<(7-(i%8)));
				
				int b1 = prefix[i/8] & mask;
				int b2 = bytes[i/8] & mask;
				
				if ( b1 != b2 ){
					
					return( false );
				}
			}
			
			return( true );
			
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
