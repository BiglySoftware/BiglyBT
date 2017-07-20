/*
 * Created on Oct 29, 2008
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.core.util.bloom.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.bloom.BloomFilter;

public class
BloomFilterRotator
	implements BloomFilter
{
	private volatile BloomFilter 	current_filter;
	private int						current_filter_index;

	private final BloomFilter[]	filters;

	private long start_time = SystemTime.getMonotonousTime();

	public
	BloomFilterRotator(
		BloomFilter		_target,
		int				_num )
	{
		filters = new BloomFilter[_num];

		filters[0] = _target;

		for (int i=1;i<filters.length;i++){

			filters[i] = _target.getReplica();
		}

		current_filter 			= _target;
		current_filter_index	= 0;
	}

	public
	BloomFilterRotator(
		Map<String,Object>		x )
	{
		List<Map<String,Object>>	list = (List<Map<String,Object>>)x.get( "list" );

		filters = new BloomFilter[ list.size() ];

		for (int i=0;i<filters.length;i++){

			filters[i] = BloomFilterImpl.deserialiseFromMap( list.get(i));
		}

		current_filter_index = ((Long)x.get( "index" )).intValue();

		current_filter = filters[ current_filter_index ];
	}

	@Override
	public Map<String, Object>
	serialiseToMap()
	{
		Map<String, Object>  m = new HashMap<>();

		serialiseToMap( m );

		return( m );
	}

	protected void
	serialiseToMap(
		Map<String,Object>		x )
	{
		synchronized( filters ){

			String	cla = this.getClass().getName();

			if ( cla.startsWith( BloomFilterImpl.MY_PACKAGE )){

				cla = cla.substring( BloomFilterImpl.MY_PACKAGE.length());
			}

			x.put( "_impl", cla );

			List<Map<String,Object>>	list = new ArrayList<>();

			for ( BloomFilter filter: filters ){

				list.add( filter.serialiseToMap());
			}

			x.put( "list", list );
			x.put( "index", new Long( current_filter_index ));
		}
	}

	@Override
	public int
	add(
		byte[]		value )
	{
		synchronized( filters ){

			int	filter_size 	= current_filter.getSize();
			int	filter_entries	= current_filter.getEntryCount();

			int	limit	= filter_size / 8;	// capacity limit

			if ( filter_entries > limit ){

				filter_entries = limit;
			}

			int	update_chunk = limit / filters.length;

			int	num_to_update =  ( filter_entries / update_chunk ) + 1;

			if ( num_to_update > filters.length ){

				num_to_update = filters.length;
			}

			//System.out.println( "rot_bloom: cur=" + current_filter_index + ", upd=" + num_to_update + ",ent=" + filter_entries );

			int	res = 0;

			for (int i=current_filter_index;i<current_filter_index+num_to_update;i++){

				int	r = filters[i%filters.length].add( value );

				if ( i == current_filter_index ){

					res = r;
				}
			}

			if ( current_filter.getEntryCount() > limit ){

				filters[current_filter_index] = current_filter.getReplica();

				current_filter_index = (current_filter_index+1)%filters.length;

				current_filter = filters[ current_filter_index ];
			}

			return( res );
		}
	}

	@Override
	public int
	remove(
		byte[]		value )
	{
		int	res = 0;

		for (int i=0;i<filters.length;i++){

			BloomFilter	filter = filters[i];

			int r = filter.remove( value );

			if ( filter == current_filter ){

				res = r;
			}
		}

		return( res );
	}

	@Override
	public boolean
	contains(
		byte[]		value )
	{
		return( current_filter.contains(value));
	}

	@Override
	public int
	count(
		byte[]		value )
	{
		return( current_filter.count( value ));
	}

	@Override
	public int
	getEntryCount()
	{
		return( current_filter.getEntryCount());
	}

	@Override
	public int
	getSize()
	{
		return( current_filter.getSize());
	}

	@Override
	public BloomFilter
	getReplica()
	{
		return( new BloomFilterRotator( current_filter, filters.length ));
	}

	@Override
	public long
	getStartTimeMono()
	{
		return( start_time );
	}

	@Override
	public void
	clear()
	{
		start_time  = SystemTime.getMonotonousTime();

		for ( BloomFilter filter: filters ){

			filter.clear();
		}
	}

	@Override
	public String
	getString()
	{
		return( "ind=" + current_filter_index + ",filt=" + current_filter.getString());
	}
}
