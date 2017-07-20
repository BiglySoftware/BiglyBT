/*
 * Created on 21 Jun 2006
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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;


public interface
TransportHelper
{
	public InetSocketAddress
	getAddress();

	public String
	getName(boolean verbose);

	public boolean
	minimiseOverheads();

	public int
	getConnectTimeout();

	public int
	getReadTimeout();

	public boolean
	delayWrite(
		ByteBuffer	buffer );

	public boolean
	hasDelayedWrite();

	public int
	write(
		ByteBuffer 	buffer,
		boolean		partial_write )

		throws IOException;

    public long
    write(
    	ByteBuffer[] buffers,
    	int array_offset,
    	int length )

    	throws IOException;

    public int
    read(
    	ByteBuffer buffer )

    	throws IOException;

    public long
    read(
    	ByteBuffer[] buffers,
    	int array_offset,
    	int length )

    	throws IOException;

    public void
    pauseReadSelects();

    public void
    pauseWriteSelects();

    public void
    resumeReadSelects();

    public void
    resumeWriteSelects();

    public void
    registerForReadSelects(
    	selectListener	listener,
    	Object			attachment );

    public void
    registerForWriteSelects(
    	selectListener	listener,
    	Object			attachment );

    public void
    cancelReadSelects();

    public void
    cancelWriteSelects();

    public boolean
    isClosed();

    public void
    close(
    	String		reason );

    public void
    failed(
    	Throwable	reason );

    public interface
    selectListener
    {
    	public boolean
    	selectSuccess(
    		TransportHelper	helper,
    		Object 			attachment );

        public void
        selectFailure(
        	TransportHelper	helper,
        	Object 			attachment,
        	Throwable 		msg);
    }

    public void
    setUserData(
    	Object	key,
    	Object	data );

    public Object
    getUserData(
    	Object	key );

	public void
	setTrace(
		boolean	on );

	public void
	setScatteringMode(long forBytes);
}
