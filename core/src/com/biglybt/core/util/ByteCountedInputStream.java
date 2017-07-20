/*
 * Created on Feb 8, 2007
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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class
ByteCountedInputStream
	extends FilterInputStream
{
	private long	position;
	private long	mark;

	public
	ByteCountedInputStream(
		InputStream		is )
	{
		super( is );
	}

	@Override
	public int
	read()

		throws IOException
	{
		int	read = in.read();

		position += read;

		return( read );
	}

	@Override
	public int
	read(byte b[])

		throws IOException
	{
	   	int	read = read(b, 0, b.length);

	   	position += read;

	   	return( read );
	}

	@Override
	public int
	read(byte b[], int off, int len)

		throws IOException
	{
		int	read = in.read(b, off, len);

		position += read;

		return( read );
	}

	@Override
	public long
	skip(long n)

		throws IOException
	{
		long	skipped = in.skip(n);

		position += skipped;

		return( skipped );
	}

	@Override
	public synchronized void
	mark(int readlimit)
	{
		in.mark(readlimit);

		mark	= position;
	}

	@Override
	public synchronized void
	reset()

		throws IOException
	{
		in.reset();

		position = mark;
	}

	public long
	getPosition()
	{
		return( position );
	}
}
