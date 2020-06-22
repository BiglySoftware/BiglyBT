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

import java.io.*;

public class
DeleteFileOnCloseInputStream
	extends InputStream
{
	private InputStream			in;
	private final File				file;
	private boolean				closed;

	private long				pos;
	private long				mark;

	public
	DeleteFileOnCloseInputStream(
		File		_file )

		throws IOException
	{
		file		= _file;
		in			= new BufferedInputStream( FileUtil.newFileInputStream( file ), 128*1024 );
	}

	@Override
	public void
	close()

		throws IOException
	{
		if ( closed ){

			return;
		}

		closed = true;

		try{
			in.close();

		}finally{

			if ( !file.delete()){

				Debug.out( "Failed to delete file '" + file + "'" );
			}
		}
	}

	@Override
	public int
	read()

		throws IOException
	{
		int	result = in.read();

		pos++;

		return( result );
	}


	@Override
	public int
	read(
		byte 	b[] )

		throws IOException
	{
		int	res = read( b, 0, b.length );

		if ( res > 0 ){

			pos += res;
		}

		return( res );
	}


	@Override
	public int
	read(
		byte	b[],
		int 	off,
		int 	len )

		throws IOException
	{
		int res = in.read( b, off, len );

		if ( res > 0 ){

			pos += res;
		}

		return( res );
	}


	@Override
	public long
	skip(
		long 	n )

		throws IOException
	{
		long res = in.skip( n );

		pos += res;

		return( res );
	}


	@Override
	public int
	available()

		throws IOException
	{
		return( in.available());
	}

	@Override
	public synchronized void
	mark(
		int readlimit )
	{
		mark	= pos;
	}

	@Override
	public synchronized void
	reset()

		throws IOException
	{
		in.close();

		in = FileUtil.newFileInputStream( file );

		in.skip( mark );

		pos = mark;
	}

	@Override
	public boolean
	markSupported()
	{
		return( true );
	}

	/**
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	public File getFile() {
		return file;
	}
}
