/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.ipfilter.impl;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;

import com.biglybt.core.ipfilter.IpRange;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.HostNameToIPResolver;


public class 
IpRangeV6Impl
	extends IpRangeImpl
{
	private byte[]	start_prefix;
	private byte[]	end_prefix;
	
	private int		start_mask;
	private int		end_mask;

	protected
	IpRangeV6Impl(
		String			desc,
		Inet6Address	address,
		boolean			session )
	{
		if ( session ){
			
			flags = FLAG_SESSION_ONLY;
		}

		if ( address == null) {

			throw (new RuntimeException(
					"Invalid start/end values - null not supported"));
		}
	
		start_prefix	= address.getAddress();
		start_mask		= 128;
			
		if ( desc.length() > 0){
			
			setDescription(desc);
		}

		checkValid();
	}
	
	protected
	IpRangeV6Impl(
		String			desc,
		String			start,
		String			end,
		boolean			session )
	{
		if (session) {
			flags = FLAG_SESSION_ONLY;
		}

		if ( start == null || end == null ){

			throw (new RuntimeException(
					"Invalid start/end values - null not supported"));
		}

		setStartSupport( start );
		
		setEndSupport( end );
		
		if ( desc.length() > 0 ){
			
			setDescription(desc);
		}

		checkValid();
	}
	
	@Override
	protected boolean 
	isV4()
	{
		return( false );
	}
	
	@Override
	public boolean 
	isValid() 
	{
		if ((flags & FLAG_INVALID) > 0){
			
			return false;
		}

		if ( start_prefix == null ){
			
			return( false );
		}
		
		if ( end_prefix != null ){
			
			if (!( Arrays.equals( start_prefix, end_prefix ) && start_mask == end_mask )){
				
				return( false );
			}
		}
		
		return( true );
	}

	private Inet6Address
	getLiteralAddress(
		String		str )
	
		throws Exception
	{
		str = str.trim();
		
		char[] chars = str.toCharArray();
		
		for ( char c: chars ){
			
			if ( Character.isDigit( c ) || c == ':' || c == '[' || c == ']' ){
				
			}else{
				
				throw( new Exception( "Not literal: " + str ));
			}
		}
			
		return((Inet6Address)InetAddress.getByName( str ));	
	}
	
	private void
	setStartSupport(
		String		str )
	{
		flags &= ~FLAG_INVALID_START;

		try{
			str = str.trim();
			
			if ( str.isEmpty()){
				
				start_prefix	= null;
				
				flags |= FLAG_INVALID_START;
				
			}else{	
				int	pos = str.indexOf('/');
	
				if ( pos == -1 ){
					
					InetAddress	address = getLiteralAddress( str );
					
					start_prefix = address.getAddress();
					
					start_mask	= 128;
					
				}else{
					
					InetAddress	address = getLiteralAddress( str.substring(0,pos));
		
					start_prefix = address.getAddress();
					
					start_mask = Integer.parseInt( str.substring( pos+1 ));
				}
			}
		}catch( Throwable e ){
			
			flags |= FLAG_INVALID_START;
		}
	}
	
	private void
	setEndSupport(
		String		str )
	{
		flags &= ~FLAG_INVALID_END;

		try{
			str = str.trim();
			
			if ( str.isEmpty()){
				
				end_prefix	= null;
				
			}else{
				int	pos = str.indexOf('/');
	
				if ( pos == -1 ){
					
					InetAddress	address = getLiteralAddress( str );
					
					end_prefix = address.getAddress();
					
					end_mask	= 128;
					
				}else{
					
					InetAddress	address = getLiteralAddress( str.substring(0,pos));
		
					end_prefix = address.getAddress();
					
					end_mask = Integer.parseInt( str.substring( pos+1 ));
				}
			}
			
		}catch( Throwable e ){
			
			flags |= FLAG_INVALID_END;
		}
	}
	
	@Override
	public String 
	getStartIp() 
	{
		if (( flags & FLAG_INVALID_START ) != 0 ){
	
			return( "" );
		}
		
		try{
			return( InetAddress.getByAddress( start_prefix ).getHostAddress() + (start_mask==128?"":( "/" + start_mask )));
			
		}catch( Throwable e ){
			
			return( "" );
		}
	}
	
	@Override
	public void 
	setStartIp(
		String str) 
	{
		if ( str == null ){
			
			throw (new RuntimeException("Invalid start value - null not supported"));
		}

		if ( str.equals( getStartIp())){
			
			return;
		}

		setStartSupport( str );

		if (( flags & FLAG_INVALID) == 0 ){
			
			checkValid();
		}
	}

	@Override
	public String 
	getEndIp() 
	{
		if (( flags & FLAG_INVALID_END ) != 0 ){
			
			return( "" );
		}
		
		if ( end_prefix == null ){
			
			return( "" );
			
		}else{
			
			try{
				return( InetAddress.getByAddress( end_prefix ).getHostAddress() + (end_mask==128?"":( "/" + end_mask )));
				
			}catch( Throwable e ){
				
				return( "" );
			}
		}
	}
	
	@Override
	public void 
	setEndIp(
		String str )
	{
		if ( str == null ){
			
			throw (new RuntimeException("Invalid end value - null not supported"));
		}

		if ( str.equals(getEndIp())){
			
			return;
		}

		if ( str.isEmpty()){
			
			end_prefix = null;
			
		}else{
			
			setEndSupport( str );
		}

		if ((flags & FLAG_INVALID) == 0){
			
			checkValid();
		}
	}

	@Override
	public boolean 
	isInRange(
		String ipAddress) 
	{
		if (!isValid()){
			
			return false;
		}

			// filter out IPv4 addresses
		
		if ( ipAddress.contains( "." )){
		
			byte[] bytes = HostNameToIPResolver.hostAddressToBytes( ipAddress );
			
			if ( bytes != null && bytes.length == 4 ){
				
				return( false );
			}
		}
		
		try{
			InetAddress ia = getLiteralAddress( ipAddress );
			
			return( isInRange( ia.getAddress()));
			
		}catch( Throwable e ){
			
			Debug.out( e );
		}
		
		return( false );
	}

	protected boolean
	isInRange(
		byte[]		bytes )
	{
		if (!isValid()){
			
			return false;
		}
		
		if ( start_mask == 128 ){
			
			return( Arrays.equals( bytes, start_prefix ));
			
		}else{
			
			for ( int i=0;i< start_mask; i++ ){
				
				byte mask = (byte)( 1<<(7-(i%8)));
				
				int b1 = start_prefix[i/8] & mask;
				int b2 = bytes[i/8] & mask;
				
				if ( b1 != b2 ){
					
					return( false );
				}
			}
		}
		
		return( true );
	}
	
	private int
	compare(
		byte[]		s1,
		byte[]		s2 )
	{
		if ( s1 == null && s2 == null ){
			return( 0 );
		}else if ( s1 == null ){
			return( 1 );
		}else if ( s2 == null ){
			return( -1 );
		}else{
			for ( int i=0;i<s1.length;i++){
				
				int	i1 = ((int)s1[i])&0xff;
				int	i2 = ((int)s2[i])&0xff;
				
				int res = i1 - i2;
				
				if ( res != 0 ){
					
					return( res );
				}
			}
		}	
		
		return( 0 );
	}
	
	@Override
	public int 
	compareStartIpTo(
		IpRange other) 
	{
		if ( other instanceof IpRangeV6Impl ){
			
			IpRangeV6Impl o = (IpRangeV6Impl)other;
			
			byte[]	s1 	= start_prefix;
			byte[]	s2	= o.start_prefix;
			
			return( compare( s1, s2 ));
			
		}else{
			
			return( 0 );
		}
	}

	@Override
	public int 
	compareEndIpTo(
		IpRange other) 
	{
		if ( other instanceof IpRangeV6Impl ){
			
			IpRangeV6Impl o = (IpRangeV6Impl)other;
			
			byte[]	s1 	= end_prefix;
			byte[]	s2	= o.end_prefix;
			
			return( compare( s1, s2 ));
		}else{
			
			return( 0 );
		}
	}
	
	public String 
	toString() 
	{
		String start 	= getStartIp();
		String end		= getEndIp();
		
		return( getDescription() + " : " + start + (start.equals( end )?"":(" - " + end )));
	}
}
