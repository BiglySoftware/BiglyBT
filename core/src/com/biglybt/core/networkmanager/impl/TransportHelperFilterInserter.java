/*
 * Created on 26-Jan-2006
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

public class
TransportHelperFilterInserter
	implements TransportHelperFilter
{
	private final TransportHelperFilter	target_filter;

	private ByteBuffer	read_insert;

	public
	TransportHelperFilterInserter(
		TransportHelperFilter		_target_filter,
		ByteBuffer					_read_insert )
	{
		target_filter	= _target_filter;

		read_insert		= _read_insert;
	}

	@Override
	public long
	write(
		ByteBuffer[] 	buffers,
		int 			array_offset,
		int 			length )

		throws IOException
	{
		return( target_filter.write( buffers, array_offset, length ));
	}

	@Override
	public long
	read(
		ByteBuffer[] 	buffers,
		int 			array_offset,
		int 			length )

		throws IOException
	{
		long	total_read	= 0;

		if ( read_insert != null ){

			int	pos_before	= read_insert.position();

			for (int i=array_offset;i<array_offset+length;i++){

				ByteBuffer	buffer = buffers[i];

				int	space = buffer.remaining();

				if ( space > 0 ){

					if ( space < read_insert.remaining()){

						int	old_limit = read_insert.limit();

						read_insert.limit( read_insert.position() + space );

						buffer.put( read_insert );

						read_insert.limit( old_limit );

					}else{

						buffer.put( read_insert );
					}

					if ( !read_insert.hasRemaining()){

						break;
					}
				}
			}

			total_read	= read_insert.position() - pos_before;

			if ( read_insert.hasRemaining()){

				return( total_read );

			}else{

				read_insert	= null;
			}
		}

		total_read += target_filter.read( buffers, array_offset, length );

		return( total_read );
	}

	@Override
	public boolean
	hasBufferedWrite()
	{
		return( target_filter.hasBufferedWrite());
	}

	@Override
	public boolean
	hasBufferedRead()
	{
		return( read_insert != null || target_filter.hasBufferedRead());
	}

	@Override
	public TransportHelper
	getHelper()
	{
		return( target_filter.getHelper());
	}

	@Override
	public void
	setTrace(
			boolean	on )
	{
		target_filter.setTrace( on );
	}

	@Override
	public boolean
	isEncrypted()
	{
		return( target_filter.isEncrypted());
	}

	@Override
	public String
	getName(boolean verbose)
	{
		return( target_filter.getName(verbose));
	}
}
