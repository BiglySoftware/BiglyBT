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


public class
TransportHelperFilterStreamXOR
	extends TransportHelperFilterStream
{
	private final byte[]		mask;
	private int			read_position;
	private int			write_position;

	protected
	TransportHelperFilterStreamXOR(
		TransportHelper			_transport,
		byte[]					_mask )
	{
		super( _transport );

		mask		= _mask;
	}

	@Override
	protected void
	cryptoOut(
		ByteBuffer	source_buffer,
		ByteBuffer	target_buffer )

		throws IOException
	{
		int	rem = source_buffer.remaining();

		for (int i=0;i<rem;i++){

			byte	b = source_buffer.get();

			b = (byte)( b ^ mask[ write_position++ ]);

			target_buffer.put( b );

			if ( write_position == mask.length  ){

				write_position	= 0;
			}
		}
	}

	@Override
	protected void
	cryptoIn(
		ByteBuffer	source_buffer,
		ByteBuffer	target_buffer )

		throws IOException
	{
		int	rem = source_buffer.remaining();

		for (int i=0;i<rem;i++){

			byte	b = source_buffer.get();

			b = (byte)( b ^ mask[ read_position++ ]);

			target_buffer.put( b );

			if ( read_position == mask.length  ){

				read_position	= 0;
			}
		}
	}

	@Override
	public boolean
	isEncrypted()
	{
		return( true );
	}

	@Override
	public String
	getName(boolean verbose)
	{
		String proto_str = getHelper().getName(verbose);

		if ( proto_str.length() > 0 ){

			proto_str = " (" + proto_str + ")";
		}

		return( "XOR-" + mask.length*8 + proto_str );
	}
}
