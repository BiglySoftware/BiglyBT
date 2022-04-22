/*
 * Created on 15-Mar-2006
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
import java.util.*;

public class
CopyOnWriteList<T>
implements Iterable<T>
{
	private static final boolean LOG_STATS = false;

	//private int mutation_count = 0;

	private List<T>	list = Collections.EMPTY_LIST;

	private final boolean	use_linked_list;

	private boolean	visible = false;

	private int initialCapacity;

	private static CopyOnWriteList stats;

	static {
		if (LOG_STATS) {
			stats = new CopyOnWriteList(10);
			AEDiagnostics.addEvidenceGenerator(new AEDiagnosticsEvidenceGenerator() {
				@Override
				public void generate(IndentWriter writer) {
					writer.println("COWList Info");
					writer.indent();
					try {
						long count = 0;
						long size = 0;
						for (Iterator iter = stats.iterator(); iter.hasNext();) {
							WeakReference wf = (WeakReference) iter.next();
							CopyOnWriteList cowList = (CopyOnWriteList) wf.get();
							if (cowList != null) {
								count++;
								size += cowList.size();
							}
						}
						writer.println(count + " lists with " + size + " total entries");
						if ( count > 0 ){
							writer.println((size/count) + " avg size");
						}
					} catch (Throwable t) {
					} finally {
						writer.exdent();
					}
				}
			});
		}
	}

	/**
	 * @param i
	 */
	public CopyOnWriteList(int initialCapacity) {
		this.initialCapacity = initialCapacity;
		use_linked_list = false;
		if (LOG_STATS) {
			stats.add(new WeakReference(this));
		}
	}

	/**
	 *
	 */
	public CopyOnWriteList() {
		// Smaller default initial capacity as most of our lists are small
		// Last check on 7/24/2008: 444 lists with 456 total entries
		this.initialCapacity = 1;
		use_linked_list = false;
		if (LOG_STATS) {
			stats.add(new WeakReference(this));
		}
	}

	public
	CopyOnWriteList(boolean _use_linked_list ) {
		this.initialCapacity = 1;
		use_linked_list = _use_linked_list;
		if (LOG_STATS) {
			stats.add(new WeakReference(this));
		}
	}

	/*
	public int
	getMutationCount()
	{
		synchronized( this ){

			return( mutation_count );
		}
	}
	*/
	
	public void
	add(
		T	obj )
	{
		synchronized( this ){

			// mutation_count++;

			if ( visible ){

				List<T>	new_list = use_linked_list? new LinkedList<>(list): new ArrayList<>(list);

				//mutated();

				new_list.add( obj );

				list	= new_list;

				visible = false;

			}else{
				if (list == Collections.EMPTY_LIST) {
					list = use_linked_list?new LinkedList<T>():new ArrayList<T>(initialCapacity);
				}

				list.add( obj );
			}
		}
	}

		/**
		 *
		 * @param obj
		 * @return true if added, false if not
		 */

	public boolean
	addIfNotPresent(
		T	obj )
	{
		synchronized( this ){

			if ( list.contains( obj )){

				return(false );
			}else{

				add( obj );

				return( true );
			}
		}
	}

	public T
	get(
		T	entry )
	{
		synchronized( this ){

			for ( T e: list ){

				if ( e.equals( entry )){

					return( e );
				}
			}
		}

		return( null );
	}

	public void
	add(
		int	index,
		T	obj )
	{
		if ( Constants.IS_CVS_VERSION && use_linked_list ){
			Debug.out( "hmm" );
		}
		synchronized( this ){

			// mutation_count++;

			if ( visible ){

				List<T>	new_list = use_linked_list? new LinkedList<>(list): new ArrayList<>(list);

				//mutated();

				new_list.add( index, obj );

				list	= new_list;

				visible = false;

			}else{
				if (list == Collections.EMPTY_LIST) {
					list = use_linked_list?new LinkedList<T>():new ArrayList<T>(initialCapacity);
				}

				list.add( index, obj );
			}
		}
	}

	public void
	addAll(
		Collection<T>	c )
	{
		synchronized( this ){

			// mutation_count++;

			if ( visible ){

				List<T>	new_list = use_linked_list? new LinkedList<>(list): new ArrayList<>(list);

				//mutated();

				new_list.addAll( c );

				list	= new_list;

				visible = false;

			}else{
				if (list == Collections.EMPTY_LIST) {
					list = use_linked_list?new LinkedList<T>():new ArrayList<T>(initialCapacity);
				}

				list.addAll( c );
			}
		}
	}

	public void
	setAll(
		Collection<T>	c )
	{
		synchronized( this ){

			// mutation_count++;

			if ( visible ){

				List<T>	new_list = use_linked_list? new LinkedList<>( c ): new ArrayList<>( c );

				//mutated();

				list	= new_list;

				visible = false;

			}else{
				if (list == Collections.EMPTY_LIST) {
					list = use_linked_list?new LinkedList<T>():new ArrayList<T>(initialCapacity);
				}else{
					list.clear();
				}
				
				list.addAll( c );
			}
		}
	}
	
	public T
	get(
		int		index )
	{
		if ( Constants.IS_CVS_VERSION && use_linked_list ){
			Debug.out( "hmm" );
		}

		synchronized( this ){

			return( list.get(index));
		}
	}

	public boolean
	remove(
		T	obj )
	{
		synchronized( this ){

			// mutation_count++;

			if ( visible ){

				List<T>	new_list = use_linked_list? new LinkedList<>(list): new ArrayList<>(list);

				//mutated();

				boolean result = new_list.remove( obj );

				list	= new_list;

				visible = false;

				return( result );

			}else{

				return( list.remove( obj ));
			}
		}
	}

	public void
	clear()
	{
		synchronized( this ){

			// mutation_count++;

			list	= Collections.EMPTY_LIST;

			visible = false;
		}
	}

	public boolean
	contains(
		T	obj )
	{
		synchronized( this ){

			return( list.contains( obj ));
		}
	}

	@Override
	public Iterator<T>
	iterator()
	{
		synchronized( this ){

			visible = true;

			return( new CopyOnWriteListIterator( list.iterator()));
		}
	}

	public List<T>
	getList()
	{
			// TODO: we need to either make this a read-only-list or obey the copy-on-write semantics correctly...

		synchronized( this ){

			visible = true;

			if ( Constants.IS_CVS_VERSION ){

				return( Collections.unmodifiableList( list ));

			}else{

				return( list );
			}
		}
	}

	public int
	size()
	{
		synchronized( this ){

			return( list.size());
		}
	}

	public boolean
	isEmpty()
	{
		synchronized( this ){

			return list.isEmpty();
		}
	}

	public Object[]
	toArray()
	{
		synchronized( this ){

			return( list.toArray());
		}
	}

	public T[]
  	toArray(
  		T[]	 x )
  	{
		synchronized( this ){

			return( list.toArray(x));
		}
  	}

	/*
	private void
	mutated()
	{
		mutation_count++;

		if ( mutation_count%10 == 0 ){

			System.out.println( this + ": mut=" + mutation_count );
		}
	}
	*/

	private class
	CopyOnWriteListIterator
		implements Iterator<T>
	{
		private final Iterator<T>	it;
		private T			last;

		protected
		CopyOnWriteListIterator(
			Iterator<T>		_it )
		{
			it		= _it;
		}

		@Override
		public boolean
		hasNext()
		{
			return( it.hasNext());
		}

		@Override
		public T
		next()
		{
			last	= it.next();

			return( last );
		}

		@Override
		public void
		remove()
		{
				// don't actually remove it from the iterator. can't go backwards with this iterator so this is
				// not a problem

			if ( last == null ){

				throw( new IllegalStateException( "next has not been called!" ));
			}

			CopyOnWriteList.this.remove( last );
		}
	}

	public int getInitialCapacity() {
		return initialCapacity;
	}

	public void setInitialCapacity(int initialCapacity) {
		this.initialCapacity = initialCapacity;
	}
}
