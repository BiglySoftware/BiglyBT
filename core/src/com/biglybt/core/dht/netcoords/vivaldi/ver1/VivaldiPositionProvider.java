/*
 * Created on 24-Apr-2006
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

package com.biglybt.core.dht.netcoords.vivaldi.ver1;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.biglybt.core.dht.netcoords.DHTNetworkPosition;
import com.biglybt.core.dht.netcoords.DHTNetworkPositionProvider;

public class
VivaldiPositionProvider
	implements DHTNetworkPositionProvider
{
	@Override
	public byte
	getPositionType()
	{
		return( DHTNetworkPosition.POSITION_TYPE_VIVALDI_V1 );
	}

	@Override
	public DHTNetworkPosition
	create(
		byte[]		ID,
		boolean		is_local )
	{
		return( VivaldiPositionFactory.createPosition());
	}

	@Override
	public DHTNetworkPosition
	getLocalPosition()
	{
		return( null );
	}

	@Override
	public DHTNetworkPosition
	deserialisePosition(
		DataInputStream		is )

		throws IOException
	{
		float[]	data = new float[4];

		for (int i=0;i<data.length;i++){

			data[i] = is.readFloat();
		}

		VivaldiPosition	pos = VivaldiPositionFactory.createPosition();

		pos.fromFloatArray( data );

		return( pos );
	}

	@Override
	public void
	serialiseStats(
		DataOutputStream	os )

		throws IOException
	{
	}

	@Override
	public void
	startUp(
		DataInputStream		is )
	{
	}

	@Override
	public void
	shutDown(
		DataOutputStream	os )
	{
	}
}
