/*
 * Created on 13-May-2005
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

package com.biglybt.core.util.bloom.impl;

import java.util.Arrays;
import java.util.Map;

import com.biglybt.core.util.bloom.BloomFilter;

public class
BloomFilterAddOnly
	extends BloomFilterImpl
{
	private final byte[]		map;

	public
	BloomFilterAddOnly(
		int		_max_entries )
	{
		super( _max_entries );

		map	= new byte[(getMaxEntries()+7)/8];
	}

	public
	BloomFilterAddOnly(
		Map<String,Object>		x )
	{
		super( x );

		map = (byte[])x.get( "map" );
	}

	@Override
	protected void
	serialiseToMap(
		Map<String,Object>		x )
	{
		super.serialiseToMap( x );

		x.put( "map", map.clone());
	}

	@Override
	public BloomFilter
	getReplica()
	{
		return( new BloomFilterAddOnly( getMaxEntries()));
	}

	@Override
	protected int
	trimValue(
		int	value )
	{
		if ( value < 0 ){
			return( 0 );
		}else if ( value > 1 ){
			return( 1);
		}else{
			return( value );
		}
	}

	@Override
	protected int
	getValue(
		int		index )
	{
		byte	b = map[index/8];

		return(((b>>(index%8))&0x01));

	}

		/**
		 * returns the value BEFORE increment
		 */

	@Override
	protected int
	incValue(
		int		index )
	{
		int	original_value = getValue( index );

		if ( original_value >= 1 ){

			return( 1 );
		}

		setValue( index, (byte)(original_value+1) );

		return( original_value );
	}

		/**
		 * returns the value BEFORE decrement
		 */

	@Override
	protected int
	decValue(
		int		index )
	{
		int	original_value = getValue( index );

		if ( original_value <= 0 ){

			return( 0 );
		}

		setValue( index, (byte)(original_value-1) );

		return( original_value );
	}

	private void
	setValue(
		int		index,
		byte	value )
	{

		byte	b = map[index/8];

		if ( value == 0 ){


			// b = (byte)(b&~(0x01<<(index%8)));

			throw( new RuntimeException( "remove not supported" ));

		}else{

			b = (byte)(b|(0x01<<(index%8)));
		}

		// System.out.println( "setValue[" + index + "]:" + Integer.toHexString( map[index/2]&0xff) + "->" + Integer.toHexString( b&0xff ));

		map[index/8] = b;
	}

	@Override
	public void
	clear()
	{
		Arrays.fill( map, (byte)0);

		super.clear();
	}
}
