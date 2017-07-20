/*
 * Created on 1 Nov 2006
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


package com.biglybt.core.util;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class
DebugWeakList
{
	final static boolean DEBUG = Constants.isCVSVersion();

	private final String		name;
	final List		list;

	public
	DebugWeakList(
		String	_name )
	{
		name	= _name;
		list	= new ArrayList();
	}

	public
	DebugWeakList(
		String			_name,
		DebugWeakList	l )
	{
		name	= _name;
		list 	= new ArrayList( l.list );
	}

	public void
	add(
		Object		obj )
	{
		if ( DEBUG ){

			list.add( new Object[]{ obj.getClass(), new WeakReference( obj )});

		}else{

			list.add( obj );
		}
	}

	public void
	remove(
		Object		obj )
	{
		if ( DEBUG ){

			Iterator	it = list.iterator();

			while( it.hasNext()){

				Object[]	entry  = (Object[])it.next();

				WeakReference	wr = (WeakReference)entry[1];

				Object	target = wr.get();

				if ( target == null ){

					it.remove();

					logRemoved((Class)entry[0]);

				}else if ( target == obj ){

					it.remove();

					return;
				}
			}
		}else{

			list.remove( obj );
		}
	}

	public boolean
	contains(
		Object	obj )
	{
		if ( DEBUG ){

			Iterator	it = list.iterator();

			while( it.hasNext()){

				Object[]	entry  = (Object[])it.next();

				WeakReference	wr = (WeakReference)entry[1];

				Object	target = wr.get();

				if ( target == null ){

					it.remove();

					logRemoved((Class)entry[0]);

				}else if ( target == obj ){

					return( true );
				}
			}

			return( false );
		}else{

			return( list.contains( obj ));
		}
	}

	protected void
	logRemoved(
		Class	cla )
	{
		Debug.out( "Object '" + cla + "' was not removed correctly from " + name );
	}

	public Iterator
	iterator()
	{
		if ( DEBUG ){

			return( new WeakListIterator());

		}else{

			return( list.iterator());
		}
	}

	public int
	estimatedSize()
	{
		return( list.size());
	}

	protected class
	WeakListIterator
		implements Iterator
	{
		private Iterator	it = list.iterator();

		private Object	pending_result;
		private Object	last_result;

		@Override
		public boolean
		hasNext()
		{
			if ( pending_result != null ){

				return( true );
			}

			while( it.hasNext()){

				Object[]	entry  = (Object[])it.next();

				WeakReference	wr = (WeakReference)entry[1];

				Object	target = wr.get();

				if ( target == null ){

					it.remove();

					logRemoved((Class)entry[0]);

				}else{

					pending_result = target;

					return( true );
				}
			}

			return( false );
		}

		@Override
		public Object
		next()

			throws NoSuchElementException
		{
			if ( pending_result == null ){

				hasNext();
			}

			if ( pending_result == null ){

				throw( new NoSuchElementException());
			}

			last_result = pending_result;

			pending_result = null;

			return( last_result );
		}

		@Override
		public void
		remove()
		{
			Object	lr = last_result;

			if ( lr == null ){

				throw( new NoSuchElementException());
			}

			last_result	= null;

			if ( pending_result == null ){

				it.remove();

			}else{

					// has next has skipped on beyond last result, need to manually fix up...

				Iterator	temp_it = list.iterator();

				while( temp_it.hasNext()){

					Object[]	entry  = (Object[])temp_it.next();

					WeakReference	wr = (WeakReference)entry[1];

					Object	target = wr.get();

					if ( target == lr ){

						it = temp_it;

						it.remove();

						return;
					}
				}

					// not found (Garbage collected), nothing to do
			}
		}
	}
}
