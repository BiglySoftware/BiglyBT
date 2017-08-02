/*
 * Created on Jan 9, 2014
 * Created by Paul Gardner
 *
 * Copyright 2014 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.core.util;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class
ConcurrentHashMapWrapper<S,T>
{
	/**
	 * ConcurrentHashMap doesn't support null keys or values so this is a hack to support them
	 */

	private static final Object NULL = new Object();

	private final S	S_NULL = (S)NULL;
	private final T	T_NULL = (T)NULL;

	private final ConcurrentHashMap<S,T>	map;

	public
	ConcurrentHashMapWrapper(
		int	initialCapacity )
	{
		map = new ConcurrentHashMap<>(initialCapacity);
	}

	public
	ConcurrentHashMapWrapper(
		int		initialCapacity,
		float	loadFactor,
		int 	concurrencyLevel )
	{
		map = new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel);
	}

	public
	ConcurrentHashMapWrapper()
	{
		map = new ConcurrentHashMap<>();
	}

	public
	ConcurrentHashMapWrapper(
		Map<S,T>	init_map )
	{
		map = new ConcurrentHashMap<>(init_map.size());

		putAll( init_map );
	}

	public void
	putAll(
		Map<S,T>	from_map )
	{
		for ( Map.Entry<S,T> entry: from_map.entrySet()){

			S key 	= entry.getKey();
			T value	= entry.getValue();

			if ( key == null ){

				key = S_NULL;
			}

			if ( value == null ){

				value = T_NULL;
			}

			map.put( key, value );
		}
	}

	public T
	put(
		S	key,
		T	value )
	{
		if ( key == null ){

			key = S_NULL;
		}

		if ( value == null ){

			value = T_NULL;
		}

		T result = map.put( key, value );

		if ( result == T_NULL ){

			return( null );

		}else{

			return( result );
		}
	}

	public T
	get(
		S	key )
	{
		if ( key == null ){

			key = S_NULL;
		}

		T result = map.get( key );

		if ( result == T_NULL ){

			return( null );

		}else{

			return( result );
		}
	}

	public T
	remove(
		S	key )
	{
		if ( key == null ){

			key = S_NULL;
		}


		T result = map.remove( key );

		if ( result == T_NULL ){

			return( null );

		}else{

			return( result );
		}
	}

	public boolean
	containsKey(
		S		key )
	{
		if ( key == null ){

			key = S_NULL;
		}

		return( map.containsKey( key ));
	}

		/**
		 * NOT MODIFIABLE
		 * @return
		 */
	public Set<S>
	keySet()
	{
		// Sidestep covariance in J8, which returns KeySetView instead of Set.  Needed for Android
		Set<S>	result = ((Map) map).keySet();

		if ( result.contains( S_NULL )){

			result.remove( S_NULL );

			result.add( null );
		}

		return( Collections.unmodifiableSet( result ));
	}

	/**
	 * Helper for config writing
	 * @return
	 */

	public TreeMap<S,T>
	toTreeMap()
	{
		TreeMap<S,T>	result = new TreeMap<>();

		for ( Map.Entry<S,T> entry: map.entrySet()){

			S key 	= entry.getKey();
			T value	= entry.getValue();

			if ( key == S_NULL ){

				key = null;
			}

			if ( value == T_NULL ){

				value = null;
			}

			result.put( key, value );
		}

		return( result );
	}
}
