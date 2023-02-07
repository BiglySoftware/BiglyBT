/*
 * File    : PRHelpers.java
 * Created : 10-Mar-2004
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.tracker.protocol;

/**
 * @author parg
 *
 */

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.HostNameToIPResolver;

public class
PRHelpers
{
	public static int
	addressToInt(
		String		address )

		throws UnknownHostException
	{
		InetAddress i_address = HostNameToIPResolver.syncResolve(address);

		byte[]	bytes = i_address.getAddress();

		if ( Constants.IS_CVS_VERSION && bytes.length > 4 ){
			
			Debug.out( "hmm" );
		}
		
		int	resp = (bytes[0]<<24)&0xff000000 | (bytes[1] << 16)&0x00ff0000 | (bytes[2] << 8)&0x0000ff00 | bytes[3]&0x000000ff;

		// System.out.println( "addressToInt: " + address + " -> " + Integer.toHexString(resp));

		return( resp );
	}

	public static byte[]
	addressToBytes(
		String		address )

		throws UnknownHostException
	{
		InetAddress i_address = HostNameToIPResolver.syncResolve(address);

		return( i_address.getAddress());
	}
	
	public static int
	addressToInt(
		InetAddress		i_address )
	{
		byte[]	bytes = i_address.getAddress();

		if ( Constants.IS_CVS_VERSION && bytes.length > 4 ){
			
			Debug.out( "hmm" );
		}

		int	resp = (bytes[0]<<24)&0xff000000 | (bytes[1] << 16)&0x00ff0000 | (bytes[2] << 8)&0x0000ff00 | bytes[3]&0x000000ff;

		// System.out.println( "addressToInt: " + address + " -> " + Integer.toHexString(resp));

		return( resp );
	}

	public static long
	addressToLong(
		InetAddress		i_address )
	{
		return(((long)addressToInt( i_address ))&0xffffffffL);
	}

	public static String
	intToAddress(
		int		value )
	{
		byte[]	bytes = { (byte)(value>>24), (byte)(value>>16),(byte)(value>>8),(byte)value };

		try{
			String	res = InetAddress.getByAddress(bytes).getHostAddress();

			// System.out.println( "intToAddress: " + Integer.toHexString(value) + " -> " + res );

			return( res );

		}catch( UnknownHostException e ){

				// should never get here as always valid byte array (4 long)

			Debug.printStackTrace(e);

			return( null );
		}
	}

	public static String
	bytesToAddress(
		byte[]		bytes )
	{
		try{
			String	res = InetAddress.getByAddress(bytes).getHostAddress();

			// System.out.println( "intToAddress: " + Integer.toHexString(value) + " -> " + res );

			return( res );

		}catch( UnknownHostException e ){

				// should never get here as always valid byte array (4 long)

			Debug.printStackTrace(e);

			return( null );
		}
	}
	
	public static String
	DNSToIPAddress(
		String		dns_name )

		throws UnknownHostException
	{
		return( HostNameToIPResolver.syncResolve(dns_name).getHostAddress());
	}
}
