/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */
package com.biglybt.core.util;

import java.util.*;


/**
 * A lighter (on memory) hash set<br>
 *
 * Advantages over HashSet:
 * <ul>
 * <li>Lower memory footprint
 * <li>Everything is stored in a single array, this might improve cache performance (not verified)
 * <li>Read-only operations on iterators should be concurrency-safe but they might return null values unexpectedly under concurrent modification (not verified)
 * </ul>
 *
 * Disadvantages:
 * <ul>
 * <li>removal is implemented with thombstone-keys, this can significantly increase the lookup time if many values are removed. Use compactify() for scrubbing
 * <li>entry set iterators and thus transfers to other maps are slower than comparable implementations
 * <li>the map does not store hashcodes and relies on either the key-objects themselves caching them (such as strings) or a fast computation of hashcodes
 * </ul>
 *
 * @author Aaron Grunthal
 * @create 28.11.2007
 */
public class LightHashSet extends AbstractSet implements Cloneable {
	private static final Object	THOMBSTONE			= new Object();
	private static final Object NULLKEY				= new Object();
	private static final float	DEFAULT_LOAD_FACTOR	= 0.75f;
	private static final int	DEFAULT_CAPACITY	= 8;

	public LightHashSet()
	{
		this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR);
	}

	public LightHashSet(final int initialCapacity)
	{
		this(initialCapacity, DEFAULT_LOAD_FACTOR);
	}

	public LightHashSet(final Collection c)
	{
		this(0);
		if(c instanceof LightHashSet)
		{
			final LightHashSet lightMap = (LightHashSet)c;
			this.size = lightMap.size;
			this.data = (Object[])lightMap.data.clone();
		} else
			addAll(c);
	}

	@Override
	public Object clone() {
		try
		{
			final LightHashMap newMap = (LightHashMap) super.clone();
			newMap.data = (Object[])data.clone();
			return newMap;
		} catch (CloneNotSupportedException e)
		{
			// should not ever happen
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public LightHashSet(int initialCapacity, final float loadFactor)
	{
		if (loadFactor > 1)
			throw new IllegalArgumentException("Load factor must not be > 1");
		this.loadFactor = loadFactor;
		int capacity = 1;
		while (capacity < initialCapacity)
			capacity <<= 1;
		data = new Object[capacity];
	}

	final float	loadFactor;
	int			size;
	Object[]	data;


	@Override
	public Iterator iterator() {
		return new HashIterator();
	}

	private class HashIterator implements Iterator {
		private int	nextIdx		= -1;
		private int	currentIdx	= -1;
		private final Object[] itData = data;

		public HashIterator()
		{
			findNext();
		}

		private void findNext() {
			do
				nextIdx++;
			while (nextIdx < itData.length && (itData[nextIdx] == null || itData[nextIdx] == THOMBSTONE));
		}

		@Override
		public void remove() {
			if (currentIdx == -1)
				throw new IllegalStateException("No entry to delete, use next() first");
			if (itData != data)
				throw new ConcurrentModificationException("removal operation not supported as concurrent structural modification occured");
			LightHashSet.this.removeForIndex(currentIdx);
			currentIdx = -1;
		}

		@Override
		public boolean hasNext() {
			return nextIdx < itData.length;
		}

		@Override
		public Object next() {
			if (!hasNext())
				throw new IllegalStateException("No more entries");
			currentIdx = nextIdx;
			findNext();
			final Object key = itData[currentIdx];
			return key != NULLKEY ? key : null;
		}

	}

	@Override
	public boolean add(final Object key) {
		checkCapacity(1);
		return addInternal(key, false);
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public boolean addAll(final Collection c) {
		checkCapacity(c.size());
		boolean changed = false;
		for (final Iterator it = c.iterator(); it.hasNext();)
		{
			changed |= addInternal(it.next(), true);
		}
		// compactify in case we overestimated the new size due to redundant entries
		//compactify(0.f);
		return changed;
	}

	public int capacity()
	{
		return data.length;
	}

	/**
	 * Fetches an element which does equal() the provided object but is not necessarily the same object
	 * @param Object to retrieve
	 * @return an object fulfilling the equals contract or null if no such object was found in this set
	 */
	public Object get(Object key) {
		if(key == null)
			key = NULLKEY;
		final int idx = nonModifyingFindIndex(key);
		if(keysEqual(data[idx], key))
			return data[idx];
		return null;
	}

	private boolean addInternal(Object key, final boolean bulkAdd) {
		if(key == null)
			key = NULLKEY;
		final int idx = bulkAdd ? nonModifyingFindIndex(key) : findIndex(key);
		if (data[idx] == null || data[idx] == THOMBSTONE)
		{
			data[idx] = key;
			size++;
			return true;
		}
		return false;
	}

	@Override
	public boolean remove(Object key) {
		if(size == 0)
			return false;
		if(key == null)
			key = NULLKEY;
		final int idx = findIndex(key);
		if (keysEqual(key, data[idx]))
		{
			removeForIndex(idx);
			return true;
		}
		return false;
	}

	private void removeForIndex(final int idx)
	{
		data[idx] = THOMBSTONE;
		size--;
	}

	@Override
	public void clear() {
		size = 0;
		int capacity = 1;
		while (capacity < DEFAULT_CAPACITY)
			capacity <<= 1;
		data = new Object[capacity];
	}

	@Override
	public boolean contains(Object key) {
		if(size == 0)
			return false;
		if(key == null)
			key = NULLKEY;
		return keysEqual(key, data[nonModifyingFindIndex(key)]);
	}

	private boolean keysEqual(final Object o1, final Object o2) {
		return o1 == o2 || (o1 != null && o2 != null && o1.hashCode() == o2.hashCode() && o1.equals(o2));
	}

	private int findIndex(final Object keyToFind) {
		final int hash = keyToFind.hashCode();
		/* hash ^= (hash >>> 20) ^ (hash >>> 12);
		 * hash ^= (hash >>> 7) ^ (hash >>> 4);
		 */
		int probe = 1;
		int newIndex = hash & (data.length - 1);
		int thombStoneIndex = -1;
		int thombStoneCount = 0;
		final int thombStoneThreshold = Math.min(data.length-size, 100);
		// search until we find a free entry or an entry matching the key to insert
		while (data[newIndex] != null && !keysEqual(data[newIndex], keyToFind))
		{
			if (data[newIndex] == THOMBSTONE)
			{
				if(thombStoneIndex == -1)
					thombStoneIndex = newIndex;
				thombStoneCount++;
				if(thombStoneCount * 2 > thombStoneThreshold)
				{
					compactify(0.f);
					thombStoneIndex = -1;
					probe = 0;
					thombStoneCount = 0; // not really necessary
				}
			}

			newIndex = (hash + ((probe + probe * probe) >> 1)) & (data.length - 1);
			probe++;
		}
		// if we didn't find an exact match then the first thombstone will do too for insert
		if (thombStoneIndex != -1 && !keysEqual(data[newIndex], keyToFind))
			return thombStoneIndex;
		return newIndex;
	}

	private int nonModifyingFindIndex(final Object keyToFind) {
		final int hash = keyToFind.hashCode();
		/* hash ^= (hash >>> 20) ^ (hash >>> 12);
		 * hash ^= (hash >>> 7) ^ (hash >>> 4);
		 */
		int probe = 1;
		int newIndex = hash & (data.length - 1);
		int thombStoneIndex = -1;
		// search until we find a free entry or an entry matching the key to insert
		while (data[newIndex] != null && !keysEqual(data[newIndex], keyToFind) && probe < data.length)
		{
			if(data[newIndex] == THOMBSTONE && thombStoneIndex == -1)
				thombStoneIndex = newIndex;
			newIndex = (hash + ((probe + probe * probe) >> 1)) & (data.length - 1);
			probe++;
		}
		if (thombStoneIndex != -1 && !keysEqual(data[newIndex], keyToFind))
			return thombStoneIndex;
		return newIndex;
	}


	private void checkCapacity(final int n) {
		final int currentCapacity = data.length;
		if ((size + n) < currentCapacity * loadFactor)
			return;
		int newCapacity = currentCapacity;
		do
			newCapacity <<= 1;
		while (newCapacity * loadFactor < (size + n));
		adjustCapacity(newCapacity);
	}

	/**
	 * will shrink the internal storage size to the least possible amount,
	 * should be used after removing many entries for example
	 *
	 * @param compactingLoadFactor
	 *            load factor for the compacting operation. Use 0f to compact
	 *            with the load factor specified during instantiation. Use
	 *            negative values of the desired load factors to compact only
	 *            when it would reduce the storage size.
	 */
	public void compactify(float compactingLoadFactor) {
		int newCapacity = 1;
		float adjustedLoadFactor = Math.abs(compactingLoadFactor);
		if (adjustedLoadFactor <= 0.f || adjustedLoadFactor >= 1.f)
			adjustedLoadFactor = loadFactor;
		while (newCapacity * adjustedLoadFactor < (size+1))
			newCapacity <<= 1;
		if(newCapacity < data.length || compactingLoadFactor >= 0.f )
			adjustCapacity(newCapacity);
	}

	private void adjustCapacity(final int newSize) {
		final Object[] oldData = data;
		data = new Object[newSize];
		size = 0;
		for (int i = 0; i < oldData.length; i++)
		{
			if (oldData[i] == null || oldData[i] == THOMBSTONE)
				continue;
			addInternal(oldData[i], true);
		}
	}

	static void test() {
		final Random rnd = new Random();
		final byte[] buffer = new byte[5];
		final String[] fillData = new String[(int)((1<<20) * 0.93f)];
		for (int i = 0; i < fillData.length; i++)
		{
			rnd.nextBytes(buffer);
			fillData[i] = new String(buffer);
			fillData[i].hashCode();
		}
		long time;
		final Set s1 = new HashSet();
		final Set s2 = new LightHashSet();
		System.out.println("fill:");
		time = System.currentTimeMillis();
		Collections.addAll(s1, fillData);
		System.out.println(System.currentTimeMillis() - time);
		time = System.currentTimeMillis();
		Collections.addAll(s2, fillData);
		System.out.println(System.currentTimeMillis() - time);
		System.out.println("replace-fill:");
		time = System.currentTimeMillis();
		Collections.addAll(s1, fillData);
		System.out.println(System.currentTimeMillis() - time);
		time = System.currentTimeMillis();
		Collections.addAll(s2, fillData);
		System.out.println(System.currentTimeMillis() - time);
		System.out.println("get:");
		time = System.currentTimeMillis();
		for (int i = 0; i < fillData.length; i++)
			s1.contains(fillData[i]);
		System.out.println(System.currentTimeMillis() - time);
		time = System.currentTimeMillis();
		for (int i = 0; i < fillData.length; i++)
			s2.contains(fillData[i]);
		System.out.println(System.currentTimeMillis() - time);
		System.out.println("compactify light map");
		time = System.currentTimeMillis();
		((LightHashSet) s2).compactify(0.95f);
		System.out.println(System.currentTimeMillis() - time);
		System.out.println("transfer to hashmap");
		time = System.currentTimeMillis();
		new HashSet(s1);
		System.out.println(System.currentTimeMillis() - time);
		time = System.currentTimeMillis();
		new HashSet(s2);
		System.out.println(System.currentTimeMillis() - time);
		System.out.println("transfer to lighthashmap");
		time = System.currentTimeMillis();
		new LightHashSet(s1);
		System.out.println(System.currentTimeMillis() - time);
		time = System.currentTimeMillis();
		new LightHashSet(s2);
		System.out.println(System.currentTimeMillis() - time);
		System.out.println("remove entry by entry");
		time = System.currentTimeMillis();
		for (int i = 0; i < fillData.length; i++)
			s1.remove(fillData[i]);
		System.out.println(System.currentTimeMillis() - time);
		time = System.currentTimeMillis();
		for (int i = 0; i < fillData.length; i++)
			s2.remove(fillData[i]);
		System.out.println(System.currentTimeMillis() - time);
	}

	public static void main(final String[] args) {
		System.out.println("Call with -Xmx300m -Xcomp -server");
		Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

		// some quadratic probing math test:
		/*
		boolean[] testArr = new boolean[1<<13];
		int hash = 0xc8d3 << 1;
		int position = hash & (testArr.length -1);
		int probe = 0;
		do
		{
			position = (hash + probe + probe * probe) & (testArr.length - 1);
			probe++;
			testArr[position] = true;
		} while (probe < (testArr.length>>1));

		for(int i = 0;i<testArr.length;i+=2)
		{
			if(testArr[i] != true)
				System.out.println("even element failed"+i);
			if(testArr[i+1] != false)
				System.out.println("uneven element failed"+(i+1));
		}
		*/


		try
		{
			Thread.sleep(300);
		} catch (final InterruptedException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		test();
		System.out.println("-------------------------------------");
		System.gc();
		try
		{
			Thread.sleep(300);
		} catch (final InterruptedException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		test();

		System.out.println("\n\nPerforming sanity tests");
		final Random rnd = new Random();
		final byte[] buffer = new byte[25];
		final String[] fillData = new String[1048];
		for (int i = 0; i < fillData.length; i++)
		{
			rnd.nextBytes(buffer);
			fillData[i] = new String(buffer);
			fillData[i].hashCode();
		}

		final Set s1 = new HashSet();
		final Set s2 = new LightHashSet();

		for(int i=0;i<fillData.length*10;i++)
		{
			int random = rnd.nextInt(fillData.length);

			s1.add(null);
			s2.add(null);
			if(!s1.equals(s2))
				System.out.println("Error 0");
			s1.add(fillData[random]);
			s2.add(fillData[random]);
			if(!s1.equals(s2))
				System.out.println("Error 1");
		}

		// create thombstones, test removal
		for(int i=0;i<fillData.length/2;i++)
		{
			int random = rnd.nextInt(fillData.length);
			s1.remove(fillData[random]);
			s2.remove(fillData[random]);
			if(!s1.equals(s2))
				System.out.println("Error 2");
		}

		// do some more inserting, this time with thombstones
		for(int i=0;i<fillData.length*10;i++)
		{
			int random = rnd.nextInt(fillData.length);
			s1.add(fillData[random]);
			s1.add(null);
			s2.add(fillData[random]);
			s2.add(null);
			if(!s1.equals(s2))
				System.out.println("Error 3");
		}

		Iterator i1 = s1.iterator();
		Iterator i2 = s2.iterator();
		// now try removal with iterators
		while(i1.hasNext())
		{
			i1.next();
			i1.remove();
			i2.next();
			i2.remove();
		}

		if(!s1.equals(s2))
			System.out.println("Error 4");


		// test churn/thombstones
		s2.clear();
		/*
		for(int i=0;i<fillData.length*10;i++)
		{
			int random = rnd.nextInt(fillData.length);

			m2.put(fillData[random], fillData[i%fillData.length]);
		}
		*/

		for(int i = 0;i<100000;i++)
		{
			rnd.nextBytes(buffer);
			String s = new String(buffer);
			s2.add(s);
			s2.contains(s);
			s2.remove(s);
		}

		System.out.println("checks done");

	}
}
