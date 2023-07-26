/*
 * Created on 22-Feb-2006
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
 * This is a hack based on Sun's implementation below to just support byte[] efficiently
 *  * @(#)HashMap.java	1.57 03/01/23
 *
 * Copyright 2003 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package com.biglybt.core.util;

import java.util.ArrayList;
import java.util.List;



public class
ByteArrayHashMap<T>
{
    /**
     * The default initial capacity - MUST be a power of two.
     */
    static final int DEFAULT_INITIAL_CAPACITY = 16;

    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two <= 1<<30.
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;


    static final float DEFAULT_LOAD_FACTOR = 0.75f;


    protected Entry<T>[] table;
    protected int size;
    private int threshold;
    final float loadFactor;


    @SuppressWarnings("unchecked")
	public ByteArrayHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal initial capacity: " +
                                               initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal load factor: " +
                                               loadFactor);

        // Find a power of 2 >= initialCapacity
        int capacity = 1;
        while (capacity < initialCapacity)
            capacity <<= 1;

        this.loadFactor = loadFactor;
        threshold = (int)(capacity * loadFactor);
        table = new Entry[capacity];
    }


    public ByteArrayHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    @SuppressWarnings("unchecked")
    public ByteArrayHashMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
        threshold = (int)(DEFAULT_INITIAL_CAPACITY * DEFAULT_LOAD_FACTOR);
        table = new Entry[DEFAULT_INITIAL_CAPACITY];
    }



    public int size() {
        return size;
    }


    public boolean isEmpty() {
        return size == 0;
    }

    public T get(byte[] key, int offset, int len )
    {
    	byte[]	k = new byte[len];
    	System.arraycopy( key, offset, k, 0, len );
    	return( get( k ));
    }

    public T get(byte[] key) {

        int hash = hash(key);
        int i = indexFor(hash, table.length);
        Entry<T> e = table[i];
        while (true) {
            if (e == null)
                return null;
            if (e.hash == hash && eq(key, e.key))
                return e.value;
            e = e.next;
        }
    }

    public boolean
    containsKey(
    	byte[]	key )
    {
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        Entry<T> e = table[i];
        while (true) {
            if (e == null)
                return( false );
            if (e.hash == hash && eq(key, e.key))
                return( true );
            e = e.next;
        }
    }

    public T put(byte[] key, T value) {
        int hash = hash(key);
        int i = indexFor(hash, table.length);

        for (Entry<T> e = table[i]; e != null; e = e.next) {
            if (e.hash == hash && eq(key, e.key)) {
                T oldValue = e.value;
                e.value = value;

                return oldValue;
            }
        }

        addEntry(hash, key, value, i);
        return null;
    }


    public T remove(byte[] key) {
        Entry<T> e = removeEntryForKey(key);
        return (e == null ? null : e.value);
    }


    public void clear() {

        Entry<T> tab[] = table;
        for (int i = 0; i < tab.length; i++)
            tab[i] = null;
        size = 0;
    }

    public List<byte[]>
    keys()
    {
    	List<byte[]>	res = new ArrayList<>();

        for (int j = 0; j < table.length; j++) {
	         Entry<T> e = table[j];
	         while( e != null ){
               	res.add( e.key );

                 e = e.next;
	        }
	    }

        return( res );
    }

    public List<T>
    values()
    {
    	List<T>	res = new ArrayList<>();

        for (int j = 0; j < table.length; j++) {
	         Entry<T> e = table[j];
	         while( e != null ){
               	res.add( e.value );

                e = e.next;
	        }
	    }

        return( res );
    }

    /**
     * Bit inefficient at the moment
     * @return
     */

    public ByteArrayHashMap<T>
    duplicate()
    {
    	ByteArrayHashMap<T>	res = new ByteArrayHashMap<>(size, loadFactor);

        for (int j = 0; j < table.length; j++) {
	         Entry<T> e = table[j];
	         while( e != null ){
              	res.put( e.key, e.value );

               e = e.next;
	        }
	    }

       return( res );
    }

    	//////////////////////////////////

    @SuppressWarnings("unchecked")
    void resize(int newCapacity) {
        Entry<T>[] oldTable = table;
        int oldCapacity = oldTable.length;
        if (oldCapacity == MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }

        Entry<T>[] newTable = new Entry[newCapacity];
        transfer(newTable);
        table = newTable;
        threshold = (int)(newCapacity * loadFactor);
    }


    void transfer(Entry<T>[] newTable) {
        Entry<T>[] src = table;
        int newCapacity = newTable.length;
        for (int j = 0; j < src.length; j++) {
            Entry<T> e = src[j];
            if (e != null) {
                src[j] = null;
                do {
                    Entry<T> next = e.next;
                    int i = indexFor(e.hash, newCapacity);
                    e.next = newTable[i];
                    newTable[i] = e;
                    e = next;
                } while (e != null);
            }
        }
    }


    Entry<T> removeEntryForKey(byte[] key) {
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        Entry<T> prev = table[i];
        Entry<T> e = prev;

        while (e != null) {
            Entry<T> next = e.next;
            if (e.hash == hash && eq(key, e.key)) {

                size--;
                if (prev == e)
                    table[i] = next;
                else
                    prev.next = next;

                return e;
            }
            prev = e;
            e = next;
        }

        return e;
    }



    protected static class Entry<S>{
    	public final byte[] key;
        public S value;
        public final int hash;
        public Entry<S> next;

        /**
         * Create new entry.
         */
        Entry(int h, byte[] k, S v, Entry<S> n) {
            value = v;
            next = n;
            key = k;
            hash = h;
        }

        public byte[] getKey() {
            return key;
        }

        public S getValue() {
            return value;
        }

    }


    void addEntry(int hash, byte[] key, T value, int bucketIndex) {
        table[bucketIndex] = new Entry<>(hash, key, value, table[bucketIndex]);
        if (size++ >= threshold)
            resize(2 * table.length);
    }


    void createEntry(int hash, byte[] key, T value, int bucketIndex) {
        table[bucketIndex] = new Entry<>(hash, key, value, table[bucketIndex]);
        size++;
    }

    private static int
    hash(byte[] x)
    {
    	int	hash = 0;

    	int	len = x.length;

        for (int i = 0; i < len; i++){

        	hash = 31*hash + x[i];
        }

        return( hash );
    }


    private static boolean eq(byte[] x, byte[] y)
    {
        if ( x == y ){
        	return( true );
        }

        int	len = x.length;

        if ( len != y.length ){
        	return( false );
        }

        for (int i=0;i<len;i++){
        	if ( x[i] != y[i] ){
        		return( false );
        	}
        }

        return( true );
    }


    private static int indexFor(int h, int length)
    {
        return h & (length-1);
    }


    /*
    public static void
    main(
    	String[]	args )
    {
    	ByteArrayHashMap	map = new ByteArrayHashMap();

    	byte[][]	keys = new byte[1024][];

    	Random	random = new Random();

    	for (int i=0;i<keys.length;i++){
    		byte[]	key	= new byte[random.nextInt(32)];
    		random.nextBytes(key);
    		keys[i] = key;

    		if ( i < 512 ){
    			map.put( key, new Object());
    		}
    	}

    	for (int i=0;i<keys.length;i++){
    		System.out.println( i + " - " + map.get( keys[i] ));
    	}
    }
    */
}
