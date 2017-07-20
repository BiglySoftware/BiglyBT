/*
 * Created on 29-Apr-2005
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

package com.biglybt.core.util.bloom;

import java.util.Map;

import com.biglybt.core.util.bloom.impl.*;

public class
BloomFilterFactory
{
		/**
		 * Creates a new bloom filter.
		 * @param max_entries The filter size.
		 * 	a size of 10 * expected entries gives a false-positive of around 0.01%
		 *  17* -> 0.001
		 *  29* -> 0.0001
		 * Each entry takes 1, 4 or 8 bits depending on type
		 * So, if 0.01% is acceptable and expected max entries is 100, use a filter
		 * size of 1000.
		 * @return
		 */

	public static BloomFilter
	createAddRemove4Bit(
		int		filter_size )
	{
		return( new BloomFilterAddRemove4Bit( filter_size ));
	}

	public static BloomFilter
	createAddRemove8Bit(
		int		filter_size )
	{
		return( new BloomFilterAddRemove8Bit( filter_size ));
	}

	public static BloomFilter
	createAddOnly(
		int		filter_size )
	{
		return( new BloomFilterAddOnly( filter_size ));
	}

	public static BloomFilter
	createRotating(
		BloomFilter		basis,
		int				number )
	{
		{
			return( new BloomFilterRotator( basis, number ));
		}
	}

	public static BloomFilter
	deserialiseFromMap(
		Map<String,Object>	map )
	{
		return( BloomFilterImpl.deserialiseFromMap(map));
	}
}
