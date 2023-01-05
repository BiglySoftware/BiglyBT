/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.util;

import java.util.*;

public class 
CopyOnWriteMap<K,V> 
{
	private volatile Map<K,V> map;

	private final boolean	is_identify;

	private boolean	visible = false;

	public
	CopyOnWriteMap()
	{
		this( false );
	}
	
	public
	CopyOnWriteMap(
		boolean	identity_hash_map )
	{
		is_identify = identity_hash_map;

		if ( is_identify ){

			map = new IdentityHashMap<>();

		}else{

			map = new HashMap<>();
		}
	}

	public V
	put(
		K		key,
		V		value )
	{
		V	result;

		synchronized( this ){
			
			if ( visible ){

				Map<K,V> new_map;

				if ( is_identify ){

					new_map = new IdentityHashMap<>(map);

				}else{

					new_map = new HashMap<>(map);
				}

				result = new_map.put( key, value );

				map = new_map;

				visible = false;

			}else{

				result = map.put( key, value );
			}
		}

		return( result );
	}
	
	public void
	putAll(
		CopyOnWriteMap<K,V> all )
	{
		putAll( all.map );
	}
	
	public void
	putAll(
		Map<K,V> all )
	{
		synchronized( this ){
			
			if ( visible ){

				Map<K,V> new_map;

				if ( is_identify ){

					new_map = new IdentityHashMap<>(map);

				}else{

					new_map = new HashMap<>(map);
				}

				new_map.putAll( all );

				map = new_map;

				visible = false;

			}else{

				map.putAll( all );
			}
		}
	}
	
	public V
	remove(
		K		key )
	{
		V	result;

		synchronized( this ){
			
			if ( visible ){

				Map<K,V> new_map;

				if ( is_identify ){

					new_map = new IdentityHashMap<>(map);

				}else{

					new_map = new HashMap<>(map);
				}

				result = new_map.remove( key );

				map = new_map;

				visible = false;

			}else{

				result = map.remove( key );
			}
		}

		return( result );
	}
	
	public V
	get(
		K		key )
	{
		return( map.get( key ));
	}
	
	public boolean
	isEmpty()
	{
		return( map.isEmpty());
	}

	public int
	size()
	{
		return( map.size());
	}
	
		/**
		 * DON'T MODIFY THE RESULT
		 * @return
		 */
	
	public Map<K,V>
	getReadOnlyMap()
	{
		synchronized( this ){

			visible = true;

			return( map );
		}
	}
}
