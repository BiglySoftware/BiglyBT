/*
 * Created on 04-Mar-2005
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

/**
 * @author parg
 * Derived from com.bitzi.util.Base32.java
 * (PD) 2001 The Bitzi Corporation
 * Please see http://bitzi.com/publicdomain for more info.
 *
 */

public class
Base32
{
	private static final String base32Chars =
	    "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

	private static final int[] base32Lookup =
	{ 0xFF,0xFF,0x1A,0x1B,0x1C,0x1D,0x1E,0x1F, // '0', '1', '2', '3', '4', '5', '6', '7'
	  0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF, // '8', '9', ':', ';', '<', '=', '>', '?'
	  0xFF,0x00,0x01,0x02,0x03,0x04,0x05,0x06, // '@', 'A', 'B', 'C', 'D', 'E', 'F', 'G'
	  0x07,0x08,0x09,0x0A,0x0B,0x0C,0x0D,0x0E, // 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O'
	  0x0F,0x10,0x11,0x12,0x13,0x14,0x15,0x16, // 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W'
	  0x17,0x18,0x19,0xFF,0xFF,0xFF,0xFF,0xFF, // 'X', 'Y', 'Z', '[', '\', ']', '^', '_'
	  0xFF,0x00,0x01,0x02,0x03,0x04,0x05,0x06, // '`', 'a', 'b', 'c', 'd', 'e', 'f', 'g'
	  0x07,0x08,0x09,0x0A,0x0B,0x0C,0x0D,0x0E, // 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o'
	  0x0F,0x10,0x11,0x12,0x13,0x14,0x15,0x16, // 'p', 'q', 'r', 's', 't', 'u', 'v', 'w'
	  0x17,0x18,0x19,0xFF,0xFF,0xFF,0xFF,0xFF  // 'x', 'y', 'z', '{', '|', '}', '~', 'DEL'
	};

	public static String
	encode(
		final byte[] bytes )
	{
	    int i =0, index = 0, digit = 0;
	    int currByte, nextByte;
	    StringBuilder base32 = new StringBuilder((bytes.length+7)*8/5);

	    while(i < bytes.length)
	    {
	        currByte = (bytes[i]>=0) ? bytes[i] : (bytes[i]+256); // unsign

	        /* Is the current digit going to span a byte boundary? */
	        if (index > 3)
	        {
	            if ((i+1)<bytes.length)
	                nextByte = (bytes[i+1]>=0) ? bytes[i+1] : (bytes[i+1]+256);
	            else
	                nextByte = 0;

	            digit = currByte & (0xFF >> index);
	            index = (index + 5) % 8;
	            digit <<= index;
	            digit |= nextByte >> (8 - index);
	            i++;
	        }
	        else
	        {
	            digit = (currByte >> (8 - (index + 5))) & 0x1F;
	            index = (index + 5) % 8;
	            if (index == 0)
	                i++;
	        }
	        base32.append(base32Chars.charAt(digit));
	    }

	    return base32.toString();
	}

	public static byte[]
	decode(
		final String base32 )
	{
	    int    i, index, lookup, offset, digit;
	    byte[] bytes = new byte[base32.length()*5/8];

	    if ( bytes.length == 0 ){
	    	return( bytes );
	    }

	    for(i = 0, index = 0, offset = 0; i < base32.length(); i++)
	    {
	        lookup = base32.charAt(i) - '0';

	        /* Skip chars outside the lookup table */
	        if ( lookup < 0 || lookup >= base32Lookup.length)
	            continue;

	        digit = base32Lookup[lookup];

	        /* If this digit is not in the table, ignore it */
	        if (digit == 0xFF)
	            continue;

	        if (index <= 3)
	        {
	            index = (index + 5) % 8;
	            if (index == 0)
	            {
	               bytes[offset] |= digit;
	               offset++;
	               if(offset>=bytes.length) break;
	            }
	            else
	               bytes[offset] |= digit << (8 - index);
	        }
	        else
	        {
	            index = (index + 5) % 8;
	            bytes[offset] |= (digit >>> index);
	            offset++;

	            if(offset>=bytes.length) break;
	            bytes[offset] |= digit << (8 - index);
	        }
	    }
	    return bytes;
	}
}