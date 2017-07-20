/*
 * Created on 17-Jan-2006
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

package com.biglybt.core.networkmanager.impl;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface
TransportHelperFilter
{
	public long
	write(
		ByteBuffer[] 	buffers,
		int 			array_offset,
		int 			length )

		throws IOException;

	public long
	read(
		ByteBuffer[] 	buffers,
		int 			array_offset,
		int 			length )

		throws IOException;

	public boolean
	hasBufferedWrite();

	public boolean
	hasBufferedRead();

	public TransportHelper
	getHelper();

	public String
	getName(boolean verbose);

	public boolean
	isEncrypted();

	public void
	setTrace(
		boolean	on );
}
