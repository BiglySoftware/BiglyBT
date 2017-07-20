/*
 * Created on Feb 5, 2008
 * Created by Olivier Chalouhi
 *
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
 */

package com.biglybt.core.util.png;

import java.nio.ByteBuffer;
import java.security.InvalidParameterException;

public abstract class CRCedChunk extends Chunk{


	private byte[] type;


	public CRCedChunk(byte[] type) throws InvalidParameterException {
		if(type.length != 4) {
			throw new InvalidParameterException("type must be of length 4, provided : " + type.length);
		}
		this.type = type;
	}

	@Override
	public byte[] getChunkPayload() {

		byte[] contentPayload = getContentPayload();
		int length = contentPayload.length;
		ByteBuffer buffer = ByteBuffer.allocate(length + 12);
		buffer.putInt(length);
		buffer.put(type);
		buffer.put(contentPayload);

		buffer.position(4);
		buffer.limit(length + 8);

		long crc = crc(buffer);
		buffer.limit(length + 12);
		buffer.putInt((int)crc);

		buffer.position(0);
		return buffer.array();

	}

	public abstract byte[] getContentPayload();


	 /* Table of CRCs of all 8-bit messages. */
	   private static final long[] crc_table = new long[256];

	   /* Flag: has the table been computed? Initially false. */
	   private static boolean crc_table_computed = false;

	   /* Make the table for a fast CRC. */
	   private static synchronized void make_crc_table()
	   {
	     long c;
	     int n, k;

	     for (n = 0; n < 256; n++) {
	       c = (long) n;
	       for (k = 0; k < 8; k++) {
	         if ((c & 1) != 0)
	           c = 0x0edb88320L ^ ((c >> 1) & 0x0FFFFFFFF);
	         else
	           c = c >> 1;

	           c = c & 0x0FFFFFFFF;
	       }
	       crc_table[n] = c;
	     }
	     crc_table_computed = true;
	   }

	   /* Update a running CRC with the bytes buf[0..len-1]--the CRC
	      should be initialized to all 1's, and the transmitted value
	      is the 1's complement of the final running CRC (see the
	      crc() routine below). */

	   private static long update_crc(long crc, ByteBuffer buf)
	   {
	     long c = crc;

	     if (!crc_table_computed) {
	    	 make_crc_table();
	     }
	     while(buf.hasRemaining()) {
	       c = crc_table[(int) ((c ^ buf.get()) & 0xff)] ^ (c >> 8);
	     }
	     return c;
	   }

	   /* Return the CRC of the bytes buf[0..len-1]. */
	   private static long crc(ByteBuffer buf)
	   {
	     return update_crc(0xffffffffL, buf) ^ 0xffffffffL;
	   }

}
