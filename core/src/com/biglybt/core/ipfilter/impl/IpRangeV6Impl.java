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
import java.util.Locale;

import com.biglybt.core.ipfilter.IpRange;
import com.biglybt.core.util.AddressUtils;
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
		str = str.trim().toLowerCase( Locale.US );
		
		char[] chars = str.toCharArray();
		
		for ( char c: chars ){
			
			if ( Character.isDigit( c ) || ( c >= 'a' && c <= 'f' ) || c == ':' || c == '[' || c == ']' ){
				
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
					
					if ( start_mask < 0 ){
						start_mask = 0;
					}else if ( start_mask > 128 ){
						start_mask = 128;
					}
					
						// ensure there aren't extra bits in the prefix
									
					int whole_bytes = (start_mask/8);
					
					int	rem = start_mask%8;
					
					if ( rem > 0 ){
						
						byte	mask = (byte)(  0xff << (8-rem));
						
						byte b = start_prefix[whole_bytes];
						
						byte	masked = (byte)(b&mask);
							
						if ( masked != b ){
						
							start_prefix[whole_bytes]	= masked;
							
							System.out.println( "clearing extra bits in " + str );
						}
						
						whole_bytes++;
					}
					
					for ( int i=whole_bytes; i<start_prefix.length;i++){
						
						if ( start_prefix[i] != 0 ){
							
							System.out.println( "clearing extra byte " + i + " in " + str );
							
							start_prefix[i] = 0;
						}
					}
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
					
					if ( end_mask < 0 ){
						end_mask = 0;
					}else if ( end_mask > 128 ){
						end_mask = 128;
					}
					
						// ensure there aren't extra bits in the prefix
									
					int whole_bytes = (end_mask/8);
					
					int	rem = end_mask%8;
					
					if ( rem > 0 ){
						
						byte	mask = (byte)(  0xff << (8-rem));
						
						byte b = end_prefix[whole_bytes];
						
						byte	masked = (byte)(b&mask);
							
						if ( masked != b ){
						
							end_prefix[whole_bytes]	= masked;
							
							System.out.println( "clearing extra bits in " + str );
						}
						
						whole_bytes++;
					}
					
					for ( int i=whole_bytes; i<end_prefix.length;i++){
						
						if ( end_prefix[i] != 0 ){
							
							System.out.println( "clearing extra byte " + i + " in " + str );
							
							end_prefix[i] = 0;
						}
					}
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
	
	private String 
	getStartIpSlow() 
	{
		if (( flags & FLAG_INVALID_START ) != 0 ){
	
			return( "" );
		}
		
		try{
			return( AddressUtils.getShortForm((Inet6Address)InetAddress.getByAddress( start_prefix )) + (start_mask==128?"":( "/" + start_mask )));
			
		}catch( Throwable e ){
			
			return( "" );
		}
	}
	
	protected byte[]
	getStartPrefix()
	{
		return( start_prefix );
	}
	
	protected int
	getStartMask()
	{
		return( start_mask );
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
	
	private String 
	getEndIpSlow() 
	{
		if (( flags & FLAG_INVALID_END ) != 0 ){
			
			return( "" );
		}
		
		if ( end_prefix == null ){
			
			return( "" );
			
		}else{
			
			try{
				return( AddressUtils.getShortForm((Inet6Address)InetAddress.getByAddress( end_prefix )) + (end_mask==128?"":( "/" + end_mask )));
				
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
			
			int	whole_bytes = start_mask/8;
			
			for ( int i=0; i<whole_bytes; i++ ){
				
				if ( bytes[i] != start_prefix[i] ){
					
					return( false );
				}
			}
			
			int	rem = start_mask%8;
			
			if ( rem > 0 ){
				
				byte	mask = (byte)(  0xff << (8-rem));
								
				return( (byte)( bytes[whole_bytes] & mask ) == start_prefix[ whole_bytes ] );
				
			}else{
		
				return( true );
			}
		}
	}
	
	private int
	compare(
		byte[]		s1,
		byte[]		s2,
		int			len )
	{
		if ( s1 == null && s2 == null ){
			return( 0 );
		}else if ( s1 == null ){
			return( 1 );
		}else if ( s2 == null ){
			return( -1 );
		}else{
			for ( int i=0;i<len;i++){
				
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
			
			int len = (Math.max( start_mask, o.start_mask ) + 7 )/8;
			
			return( compare( s1, s2, len ));
			
		}else{
			
			return( 1 );
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
			
			int len = (Math.max( end_mask, o.end_mask ) + 7 )/8;
			
			return( compare( s1, s2, len ));

		}else{
			
			return( 1 );
		}
	}
	
	public String 
	toString() 
	{
		String start 	= getStartIp();
		String end		= getEndIp();
		
		return( getDescription() + " : " + start + (start.equals( end )||end.isEmpty()?"":(" - " + end )));
	}
	
	public String 
	getStringSlow() 
	{
		String start 	= getStartIpSlow();
		String end		= getEndIpSlow();
		
		return( getDescription() + " : " + start + (start.equals( end )||end.isEmpty()?"":(" - " + end )));
	}
}
