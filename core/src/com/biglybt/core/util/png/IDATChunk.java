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

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class IDATChunk extends CRCedChunk {
	private static final byte[] type = {(byte) 73, (byte) 68, (byte) 65, (byte) 84 };

	private final int width;
	private final int height;

	public IDATChunk(int width, int height) {
		super(type);
		this.width = width;
		this.height = height;
	}


	@Override
	public byte[] getContentPayload() {
		byte[] payload = new byte[(width+1)*height];
		for(int i = 0; i<height ; i++) {
			int offset = i * (width+1);
			//NO filter on this line
			payload[offset++] = 0;
			for(int j = 0 ; j<width ; j++) {
				payload[offset+j] = (byte)(127);
			}
		}

		Deflater deflater = new Deflater( Deflater.DEFAULT_COMPRESSION );
	    ByteArrayOutputStream outBytes = new ByteArrayOutputStream((width+1)*height);

	    DeflaterOutputStream compBytes = new DeflaterOutputStream( outBytes, deflater );
	    try {
	    	compBytes.write(payload);
	    	compBytes.close();
	    } catch(Exception e) {
	    	e.printStackTrace();
	    }
	    byte[] compPayload = outBytes.toByteArray();

		return compPayload;
	}

}
