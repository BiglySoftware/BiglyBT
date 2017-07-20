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

public class PNG {

	public static byte[] getPNGBytesForWidth(int width) {
		return getPNGBytesForSize(width,1);
	}

	public static byte[] getPNGBytesForSize(int width,int height) {
		byte[] signature = new PngSignatureChunk().getChunkPayload();
		byte[] ihdr = new IHDRChunk(width,height).getChunkPayload();
		byte[] idat = new IDATChunk(width,height).getChunkPayload();
		byte[] iend = new IENDChunk().getChunkPayload();

		ByteBuffer buffer = ByteBuffer.allocate(signature.length + ihdr.length + idat.length + iend.length);
		buffer.put(signature);
		buffer.put(ihdr);
		buffer.put(idat);
		buffer.put(iend);

		buffer.position(0);
		return buffer.array();
	}
	/*
	public static void main(String args[]) throws Exception {
		File test = new File("/Users/olivier/Desktop/test.png");
		FileOutputStream fos = new FileOutputStream(test);
		fos.write(getPNGBytesForSize(600,400));
		fos.close();
	}
	*/
}
