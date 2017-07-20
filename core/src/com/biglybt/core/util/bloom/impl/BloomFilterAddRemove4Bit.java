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
BloomFilterAddRemove4Bit
	extends BloomFilterImpl
{
	private final byte[]		map;

	public
	BloomFilterAddRemove4Bit(
		int		_max_entries )
	{
		super( _max_entries );

		// 4 bits per entry

		map	= new byte[(getMaxEntries()+1)/2];
	}

	public
	BloomFilterAddRemove4Bit(
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
		return( new BloomFilterAddRemove4Bit( getMaxEntries()));
	}

	@Override
	protected int
	trimValue(
		int	value )
	{
		if ( value < 0 ){
			return( 0 );
		}else if ( value > 15 ){
			return( 15 );
		}else{
			return( value );
		}
	}

	@Override
	protected int
	getValue(
		int		index )
	{
		byte	b = map[index/2];

		if ( index % 2 == 0 ){

			return(( b&0x0f ) & 0xff );
		}else{

			return(((b>>4)&0x0f) & 0xff );
		}
	}

	@Override
	protected int
	incValue(
		int		index )
	{
		int	original_value = getValue( index );

		if ( original_value >= 15 ){

			return( 15 );
		}

		setValue( index, (byte)(original_value+1) );

		return( original_value );
	}

	@Override
	protected int
	decValue(
		int		index )
	{
		int	original_value = getValue( index );

		if ( original_value <= 0 ){

			return( 0 );
		}

		setValue( index, (byte)(original_value-1));

		return( original_value );
	}

	private void
	setValue(
		int		index,
		byte	value )
	{
		byte	b = map[index/2];

		if ( index % 2 == 0 ){

			b = (byte)((b&0xf0) | value );

		}else{

			b = (byte)((b&0x0f) | (value<<4)&0xf0 );
		}

		// System.out.println( "setValue[" + index + "]:" + Integer.toHexString( map[index/2]&0xff) + "->" + Integer.toHexString( b&0xff ));

		map[index/2] = b;
	}

	@Override
	public void
	clear()
	{
		Arrays.fill( map, (byte)0);

		super.clear();
	}
}
