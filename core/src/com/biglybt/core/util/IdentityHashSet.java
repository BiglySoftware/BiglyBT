/*
 * Created on Apr 27, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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


import java.util.AbstractSet;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;

public class
IdentityHashSet<E>
	extends AbstractSet<E>
{
	private final IdentityHashMap<E,Object> 	identity_map;


	public
	IdentityHashSet()
	{
		identity_map = new IdentityHashMap<>();
	}

	public IdentityHashSet(
		Collection<? extends E> 	set )
	{
		identity_map = new IdentityHashMap<>(Math.max((int) (set.size() / .75f) + 1, 16));

		addAll(set);
	}

	@Override
	public int
	size()
	{
		return( identity_map.size());
	}

	@Override
	public boolean
	contains(
		Object entry )
	{
		return( identity_map.containsKey( entry ));
	}


	@Override
	public boolean
	add(
		E entry )
	{
		return( identity_map.put( entry, "" ) == null );
	}

	@Override
	public boolean
	remove(
		Object entry )
	{
		return( identity_map.remove( entry ) != null );
	}

	@Override
	public void
	clear()
	{
		identity_map.clear();
	}

	@Override
	public Iterator<E>
	iterator()
	{
		return identity_map.keySet().iterator();
	}
}
