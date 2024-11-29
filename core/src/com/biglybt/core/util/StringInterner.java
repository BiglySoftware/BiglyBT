/*

 * Created on Jun 8, 2007
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

import java.io.File;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;


public class
StringInterner
{
	/**
	 * Can't be final as set true for a few specific apps
	 */

	@SuppressWarnings("CanBeFinal")
	public static boolean DISABLE_INTERNING = System.getProperty("stringinterner.disable", "0").equals("1");

	private static final int SCHEDULED_CLEANUP_INTERVAL = 60*1000;

	private static final boolean TRACE_CLEANUP = false;
	private static final boolean TRACE_MULTIHITS = false;


	private static final int IMMEDIATE_CLEANUP_TRIGGER = 10000;
	private static final int IMMEDIATE_CLEANUP_GOAL = 8000;
	private static final int SCHEDULED_CLEANUP_TRIGGER = IMMEDIATE_CLEANUP_GOAL;
	private static final int SCHEDULED_CLEANUP_GOAL = 7000;
	private static final int SCHEDULED_AGING_THRESHOLD = 750;

	private static final LightHashSet managedInterningSet = new LightHashSet(800);

	static final ReadWriteLock managedSetLock = new ReentrantReadWriteLock();

	private final static ReferenceQueue managedRefQueue = new ReferenceQueue();

	private static final String[] COMMON_KEYS = {
		"src","port","prot","ip","udpport","azver","httpport","downloaded",
		"Content", "path", "path.utf-8","uploaded","completed","persistent","attributes","encoding",
		"azureus_properties","stats.download.added.time","networks","p1","resume data","dndflags","blocks","resume",
		"primaryfile","resumecomplete","data","peersources", "name", "name.utf-8","valid","torrent filename","parameters",
		"secrets","timesincedl","tracker_cache","filedownloaded","timesinceul","tracker_peers","trackerclientextensions","GlobalRating",
		"comment.utf-8","Count","String","Thumbnail","Plugin.<internal>.DDBaseTTTorrent::sha1","type","Title",
		"displayname", "flags","stats.download.completed.time","Description",
		"hash","ver","id",
		"body","seed","eip","rid","iip","dp2","tp","orig",
		"dp","private","dht_backup_enable","max.uploads","filelinks",
		"sha1","ed2k","dht_backup_requested","ta","size",
		"dateadded","bytesin","announces","status","bytesout","scrapes",
		"passive",
		"pre", "seq", "nick", "msg", "zo",		// frequent msgsync keys
	};

	// private static final ByteArrayHashMap	byte_map = new ByteArrayHashMap( COMMON_KEYS.length );

	static{
		try{
			for (int i=0;i<COMMON_KEYS.length;i++){

				// byte_map.put( COMMON_KEYS[i].getBytes(Constants.BYTE_ENCODING_CHARSET), COMMON_KEYS[i] );
				managedInterningSet.add(new WeakStringEntry(COMMON_KEYS[i], true));
			}
		}catch( Throwable e ){

			e.printStackTrace();
		}

			// initialisation nightmare - we have to create periodic event async to avoid
			// circular class loading issues when azureus.config is borkified

		new AEThread2( "asyncify", true )
		{
			@Override
			public void
			run()
			{
				SimpleTimer.addPeriodicEvent("StringInterner:cleaner", SCHEDULED_CLEANUP_INTERVAL, new TimerEventPerformer() {
					@Override
					public void perform(TimerEvent event) {
						managedSetLock.writeLock().lock();
						try {
							sanitize(true);
						} finally {
							managedSetLock.writeLock().unlock();
						}
					}
				});
			}
		}.start();
	}

	// private final static ReferenceQueue queue = new ReferenceQueue();

	/*
	public static String
	intern(
		byte[]	bytes )
	{
		String res = (String)byte_map.get( bytes );

		// System.out.println( new String( bytes ) + " -> " + res );

		return( res );
	}
	*/
	

	public static String intern(String toIntern) {

		if ( DISABLE_INTERNING ){
			return( toIntern );
		}

		if(toIntern == null)
			return null;

		String internedString;

		WeakStringEntry checkEntry = new WeakStringEntry(toIntern);

		WeakStringEntry internedEntry = null;
		boolean hit = false;

		managedSetLock.readLock().lock();
		try {

			internedEntry = (WeakStringEntry) managedInterningSet.get(checkEntry);

			if (internedEntry != null && (internedString = internedEntry.getString()) != null)
				hit = true;
			else
			{
				managedSetLock.readLock().unlock();
				managedSetLock.writeLock().lock();
				try{
					sanitize(false);

					// get again, weakrefs might have expired and been added by another thread concurrently
					internedEntry = (WeakStringEntry) managedInterningSet.get(checkEntry);

					if (internedEntry != null && (internedString = internedEntry.getString()) != null)
						hit = true;
					else {
						toIntern = new String( toIntern );	// this trims any baggage that might be included in the original string due to char[] sharing for substrings etc
						checkEntry = new WeakStringEntry( toIntern );
						managedInterningSet.add(checkEntry);
						internedString = toIntern;
					}
				}finally{
					managedSetLock.readLock().lock();
					managedSetLock.writeLock().unlock();
				}
			}
		} finally {
			managedSetLock.readLock().unlock();
		}

		if(hit) {
			internedEntry.incHits();
			checkEntry.destroy();
			if(TRACE_MULTIHITS && internedEntry.getHits() % 10 == 0)
				System.out.println("multihit "+internedEntry);
		}


		return internedString;
	}

	/*
	public static char[] intern(char[] toIntern) {

		if ( DISABLE_INTERNING ){
			return( toIntern );
		}

		if(toIntern == null)
			return null;

		char[] internedCharArray;

		WeakCharArrayEntry checkEntry = new WeakCharArrayEntry(toIntern);

		WeakCharArrayEntry internedEntry = null;
		boolean hit = false;

		managedSetLock.readLock().lock();
		try {

			internedEntry = (WeakCharArrayEntry) managedInterningSet.get(checkEntry);

			if (internedEntry != null && (internedCharArray = internedEntry.getCharArray()) != null)
				hit = true;
			else
			{
				managedSetLock.readLock().unlock();
				managedSetLock.writeLock().lock();
				try{
					sanitize(false);

					// get again, weakrefs might have expired and been added by another thread concurrently
					internedEntry = (WeakCharArrayEntry) managedInterningSet.get(checkEntry);

					if (internedEntry != null && (internedCharArray = internedEntry.getCharArray()) != null)
						hit = true;
					else {
						managedInterningSet.add(checkEntry);
						internedCharArray = toIntern;
					}
				}finally{
					managedSetLock.readLock().lock();
					managedSetLock.writeLock().unlock();
				}
			}
		} finally {
			managedSetLock.readLock().unlock();
		}

		if(hit) {
			System.out.println( "hit for " + new String(toIntern ));
			internedEntry.incHits();
			checkEntry.destroy();
			if(TRACE_MULTIHITS && internedEntry.hits % 10 == 0)
				System.out.println("multihit "+internedEntry);
		}


		return internedCharArray;
	}
	*/



	/*
	public static byte[] internBytes(byte[] toIntern) {

		if ( DISABLE_INTERNING ){
			return( toIntern );
		}

		if(toIntern == null)
			return null;

		byte[] internedArray;

		WeakByteArrayEntry checkEntry = new WeakByteArrayEntry(toIntern);

		WeakByteArrayEntry internedEntry = null;
		boolean hit = false;
		managedSetLock.readLock().lock();
		try
		{
			internedEntry = (WeakByteArrayEntry) managedInterningSet.get(checkEntry);
			if (internedEntry != null && (internedArray = internedEntry.getArray()) != null)
				hit = true;
			else
			{
				managedSetLock.readLock().unlock();
				managedSetLock.writeLock().lock();
				try{
					sanitize(false);
					// get again, weakrefs might have expired and been added by another thread concurrently
					internedEntry = (WeakByteArrayEntry) managedInterningSet.get(checkEntry);
					if (internedEntry != null && (internedArray = internedEntry.getArray()) != null)
						hit = true;
					else
					{
						managedInterningSet.add(checkEntry);
						internedArray = toIntern;
					}
				}finally{
					managedSetLock.readLock().lock();
					managedSetLock.writeLock().unlock();
				}
			}
		} finally
		{
			managedSetLock.readLock().unlock();
		}
		if (hit)
		{
			internedEntry.incHits();
			checkEntry.destroy();
			if (TRACE_MULTIHITS && internedEntry.hits % 10 == 0)
				System.out.println("multihit " + internedEntry);
		}

		// should not happen
		if(!Arrays.equals(toIntern, internedArray))
			System.err.println("mismatch");

		return internedArray;
	}
	*/
	
	/**
	 * This is based on File.hashCode() and File.equals(), which can return different values for different representations of the same paths.
	 * Thus internFile should be used with canonized Files exclusively
	 */
	/*
	public static File internFile(File toIntern) {

		if ( DISABLE_INTERNING ){
			return( toIntern );
		}

		if(toIntern == null)
			return null;

		File internedFile;

		WeakFileEntry checkEntry = new WeakFileEntry(toIntern);

		WeakFileEntry internedEntry = null;
		boolean hit = false;
		managedSetLock.readLock().lock();
		try
		{
			internedEntry = (WeakFileEntry) managedInterningSet.get(checkEntry);
			if (internedEntry != null && (internedFile = internedEntry.getFile()) != null)
				hit = true;
			else
			{
				managedSetLock.readLock().unlock();
				managedSetLock.writeLock().lock();
				try{
					sanitize(false);
					// get again, weakrefs might have expired and been added by another thread concurrently
					internedEntry = (WeakFileEntry) managedInterningSet.get(checkEntry);
					if (internedEntry != null && (internedFile = internedEntry.getFile()) != null)
						hit = true;
					else
					{
						managedInterningSet.add(checkEntry);
						internedFile = toIntern;
					}
				}finally{
					managedSetLock.readLock().lock();
					managedSetLock.writeLock().unlock();
				}
			}
		} finally
		{
			managedSetLock.readLock().unlock();
		}

		if (hit)
		{
			internedEntry.incHits();
			checkEntry.destroy();
			if (TRACE_MULTIHITS && internedEntry.hits % 10 == 0)
				System.out.println("multihit " + internedEntry);
		}

		// should not happen
		if(!toIntern.equals(internedFile))
			System.err.println("mismatch");

		return internedFile;
	}
	*/
	
	public static URL internURL(URL toIntern) {

		if ( DISABLE_INTERNING ){
			return( toIntern );
		}

		if(toIntern == null)
			return null;

		URL internedURL;

		WeakURLEntry checkEntry = new WeakURLEntry(toIntern);

		WeakURLEntry internedEntry = null;
		boolean hit = false;
		managedSetLock.readLock().lock();
		try
		{
			internedEntry = (WeakURLEntry) managedInterningSet.get(checkEntry);
			if (internedEntry != null && (internedURL = internedEntry.getURL()) != null)
				hit = true;
			else
			{
				managedSetLock.readLock().unlock();
				managedSetLock.writeLock().lock();
				try{
					sanitize(false);
					// get again, weakrefs might have expired and been added by another thread concurrently
					internedEntry = (WeakURLEntry) managedInterningSet.get(checkEntry);
					if (internedEntry != null && (internedURL = internedEntry.getURL()) != null)
						hit = true;
					else
					{
						managedInterningSet.add(checkEntry);
						internedURL = toIntern;
					}
				}finally{
					managedSetLock.readLock().lock();
					managedSetLock.writeLock().unlock();
				}
			}
		} finally
		{
			managedSetLock.readLock().unlock();
		}

		if (hit)
		{
			internedEntry.incHits();
			checkEntry.destroy();
			if (TRACE_MULTIHITS && internedEntry.getHits() % 10 == 0)
				System.out.println("multihit " + internedEntry);
		}

		// should not happen
		if(!toIntern.toExternalForm().equals(internedURL.toExternalForm()))
			System.err.println("mismatch");

		return internedURL;
	}


	private final static Comparator	savingsComp	=
		new Comparator()
		{
			@Override
			public int compare(Object o1, Object o2) {
				WeakWeightedEntry w1 = (WeakWeightedEntry) o1;
				WeakWeightedEntry w2 = (WeakWeightedEntry) o2;
				return w1.hits * w1.size - w2.hits * w2.size;
			}
		};

	private static void sanitize(boolean scheduled)
	{
		WeakWeightedEntry ref;
		while ((ref = (WeakWeightedEntry) (managedRefQueue.poll())) != null)
		{
			if (!ref.isDestroyed())
			{
				managedInterningSet.remove(ref);
				if (TRACE_CLEANUP && ref.hits > 30)
					System.out.println("queue remove:" + ref);
			} else
			{// should not happen
				System.err.println("double removal " + ref);
			}
		}
		int currentSetSize = managedInterningSet.size();
		aging:
		{
			cleanup:
			{
				// unscheduled cleanup/aging only in case of emergency
				if (currentSetSize < IMMEDIATE_CLEANUP_TRIGGER && !scheduled)
					break aging;
				if (TRACE_CLEANUP)
					System.out.println("Doing cleanup " + currentSetSize);
				List<WeakWeightedEntry> remaining = new ArrayList<>(currentSetSize);
				// remove objects that aren't shared by multiple holders first (interning is useless)
				for (Iterator<WeakWeightedEntry> it = managedInterningSet.iterator(); it.hasNext();)
				{
					if (managedInterningSet.size() < IMMEDIATE_CLEANUP_GOAL && !scheduled)
						break aging;
					WeakWeightedEntry entry = (WeakWeightedEntry) it.next();
					if ( entry.isPermanent() ){
						
					}else if (entry.hits == 0){
						if (TRACE_CLEANUP)
							System.out.println("0-remove: " + entry);
						it.remove();
					}else{
						remaining.add(entry);
					}
				}
				currentSetSize = managedInterningSet.size();
				if (currentSetSize < SCHEDULED_CLEANUP_TRIGGER && scheduled)
					break cleanup;
				if (currentSetSize < IMMEDIATE_CLEANUP_GOAL && !scheduled)
					break aging;
				Collections.sort(remaining, savingsComp);
				// remove those objects that saved the least amount first
				weightedRemove: for (int i = 0; i < remaining.size(); i++)
				{
					currentSetSize = managedInterningSet.size();
					if (currentSetSize < SCHEDULED_CLEANUP_GOAL && scheduled)
						break weightedRemove;
					if (currentSetSize < IMMEDIATE_CLEANUP_GOAL && !scheduled)
						break aging;
					WeakWeightedEntry entry = (WeakWeightedEntry) remaining.get(i);
					if (TRACE_CLEANUP)
						System.out.println("weighted remove: " + entry);
					managedInterningSet.remove(entry);
				}
			}
			currentSetSize = managedInterningSet.size();
			if (currentSetSize < SCHEDULED_AGING_THRESHOLD && scheduled)
				break aging;
			if (currentSetSize < IMMEDIATE_CLEANUP_GOAL && !scheduled)
				break aging;
			for (Iterator it = managedInterningSet.iterator(); it.hasNext();)
				((WeakWeightedEntry) it.next()).decHits();
		}
		if (TRACE_CLEANUP && scheduled)
		{
			List weightTraceSorted = new ArrayList(managedInterningSet);
			Collections.sort(weightTraceSorted, savingsComp);
			System.out.println("Remaining elements after cleanup:");
			for (Iterator it = weightTraceSorted.iterator(); it.hasNext();)
				System.out.println("\t" + it.next());
		}
		if (scheduled)
			managedInterningSet.compactify(-1f);
	}

	private static class WeakEntry extends WeakReference {
		private final int	hash;

		protected WeakEntry(Object o, ReferenceQueue q, int hash)
		{
			super(o, q);
			this.hash = hash;
		}

		public WeakEntry(Object o, ReferenceQueue q)
		{
			super(o, q);
			this.hash = o.hashCode();
		}

		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj instanceof WeakEntry)
			{
				Object myObj = get();
				Object otherObj = ((WeakEntry) obj).get();
				return myObj == null ? false : myObj.equals(otherObj);
			}
			return false;
		}

		public final int hashCode() {
			return hash;
		}
	}

	private static abstract class WeakWeightedEntry extends WeakEntry {
		private final short	size;
		private short		hits;

		public WeakWeightedEntry(Object o, int hash, int size)
		{
			super(o, managedRefQueue,hash);
			this.size = (short) (size & 0x7FFF);
		}
		
		public WeakWeightedEntry(Object o, boolean perm, int hash, int size )
		{
			super(o, managedRefQueue,hash);
			this.size = perm?Short.MIN_VALUE:(short) (size & 0x7FFF);
		}
		
		public void incHits() {
			if (hits < Short.MAX_VALUE )
				hits++;
		}

		public void decHits() {
			if (hits > 0)
				hits--;
		}

		public int
		getHits()
		{
			return( hits );
		}
		
		public String toString() {
			return this.getClass().getName().replaceAll("^.*\\..\\w+$", "") + " h=" + (int)hits  + ";s=" + (size==Short.MIN_VALUE?"p":String.valueOf(size));
		}

		public boolean
		isPermanent()
		{
			return( size == Short.MIN_VALUE );
		}
		
		public void destroy() {
			hits = -1;
		}

		public boolean isDestroyed() {
			return hits == -1;
		}
	}

	/*
	private static class WeakByteArrayEntry extends WeakWeightedEntry {
		public WeakByteArrayEntry(byte[] array)
		{
			// byte-array object
			super(array, HashCodeUtils.hashCode(array), array.length + 8);
		}

		
		// override equals since byte arrays need Arrays.equals
		 
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj instanceof WeakByteArrayEntry)
			{
				byte[] myArray = getArray();
				byte[] otherArray = ((WeakByteArrayEntry) obj).getArray();
				return myArray == null ? false : Arrays.equals(myArray, otherArray);
			}
			return false;
		}

		public byte[] getArray() {
			return (byte[]) get();
		}

		public String toString() {
			return super.toString() + " " + (getArray() == null ? "null" : new String(getArray()));
		}
	}
	*/
	
	/*
	private static class WeakCharArrayEntry extends WeakWeightedEntry {
		public WeakCharArrayEntry(char[] array)
		{
			// byte-array object
			super(array, HashCodeUtils.hashCode(array), array.length + 8);
		}
		
		//override equals since byte arrays need Arrays.equals
		 
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj instanceof WeakCharArrayEntry)
			{
				char[] myArray = getCharArray();
				char[] otherArray = ((WeakCharArrayEntry) obj).getCharArray();
				return myArray == null ? false : Arrays.equals(myArray, otherArray);
			}
			return false;
		}

		public char[] getCharArray() {
			return (char[]) get();
		}

		public String toString() {
			return super.toString() + " " + (getCharArray() == null ? "null" : new String(getCharArray()));
		}
	}
	*/
	
	private static class WeakStringEntry extends WeakWeightedEntry {
		//private final String debug;
		public WeakStringEntry(String entry)
		{
			// string object with 2 fields, char-array object
			super(entry, entry.hashCode(), 16 + 8 + entry.length() * 2);
			//debug = new String( entry.toCharArray());
		}
		public WeakStringEntry(String entry,boolean perm )
		{
			// string object with 2 fields, char-array object
			super(entry, perm, entry.hashCode(), 16 + 8 + entry.length() * 2);
			//debug = new String( entry.toCharArray());
		}
		public String getString() {
			return (String) get();
		}

		public String toString() {
			//return super.toString() + " " + getString() + "/" + debug;
			return super.toString() + " " + getString();
		}
	}

	/*
	private static class WeakFileEntry extends WeakWeightedEntry {
		public WeakFileEntry(File entry)
		{
			// file object with 2 fields, string object with 2 fields, char-array object
			super(entry, entry.hashCode(), 16 + 16 + 8 + entry.getPath().length() * 2);
		}

		public File getFile() {
			return (File) get();
		}

		public String toString() {
			return super.toString() + " " + getFile();
		}
	}
	*/
	
	private static class WeakURLEntry extends WeakWeightedEntry {
		public WeakURLEntry(URL entry)
		{
			// url object with 12 fields, ~4 string objects with 2 fields, 1 shared char-array object
			// use URL.toExternalForm().hashCode since URL.hashCode tries to resolve hostnames :(
			super(entry, entry.toExternalForm().hashCode(), 13 * 8 + 4 * 16 + 8 + entry.toString().length() * 2);
		}

		public URL getURL() {
			return (URL) get();
		}

		/**
		 * override equals since byte arrays need Arrays.equals
		 */
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj instanceof WeakURLEntry)
			{
				URL my = getURL();
				URL other = ((WeakURLEntry) obj).getURL();

				if ( my == other ){
					return( true );
				}
				if ( my == null || other == null ){
					return( false );
				}
				// use string compare as URL.equals tries to resolve hostnames
				return my.toExternalForm().equals(other.toExternalForm());
			}
			return false;
		}

		public String toString() {
			return super.toString() + " " + getURL();
		}
	}
	
	/**
	 * NOTE this should always return the same string, it is not intended to support
	 * a mutable result...
	 */
	
	public interface
	StringSupplier
		extends Supplier<String>, Comparable<StringSupplier>
	{
		public boolean
		isEmpty();
	}

	public static class
	StringSupplierBasic
		implements StringSupplier
	{
		public static final StringSupplier	EMPTY_STRING = new StringSupplierBasic("");
		
		final private String str;
		
		public 
		StringSupplierBasic(
			String		_str )
		{
			str		= _str==null?"":_str;
		}
		
		public boolean
		isEmpty()
		{
			return( str.isEmpty());
		}
		
		@Override
		public int 
		hashCode()
		{
			return( get().hashCode());
		}
		
		@Override
		public boolean 
		equals(
			Object obj)
		{
			if ( obj instanceof StringSupplierBasic ){
				
				return( str.equals(((StringSupplierBasic)obj).str ));
				
			}else if ( obj instanceof StringSupplier ){
				
				return( str.equals(((StringSupplier)obj).get()));
				
			}else{
				
				return( false );
			}
		}
		
		@Override
		public int 
		compareTo(
			StringSupplier o)
		{
			return( get().compareTo( o.get()));
		}
		
		@Override
		public String 
		get()
		{
			return( str );
		}
	}
	
	/**
	 * As above, must always return the same string. Also null/"" is NOT SUPPORTED
	 */
	
	public static class
	StringSupplierGenerated
		implements StringSupplier
	{
		private final Supplier<String>	supplier;
		
		public 
		StringSupplierGenerated(
			Supplier<String>	_supplier )
		{
			supplier = _supplier;
		}
		
		public boolean
		isEmpty()
		{
			return( false );		// assume always something to avoid unnecessary gets...
		}
		
		@Override
		public int 
		hashCode()
		{
			return( get().hashCode());
		}
		
		@Override
		public boolean 
		equals(
			Object obj)
		{
			if ( obj instanceof StringSupplier ){
				
				return( supplier.get().equals(((StringSupplier)obj).get()));
				
			}else{
				
				return( false );
			}
		}
		
		@Override
		public int 
		compareTo(
			StringSupplier o)
		{
			return( get().compareTo( o.get()));
		}
		
		@Override
		public String 
		get()
		{
			if ( Constants.IS_CVS_VERSION ){
				
				String value = supplier.get();
				
				if ( value == null || value.isEmpty()){
					
					Debug.out( "Unsupported" );
				}
				
				return( value );
				
			}else{
			
				return( supplier.get());
			}
		}
	}
	
	
	
	
	
	private final static boolean TRACK_FILE_KEYS = Constants.IS_CVS_VERSION;
	
	private final static ReferenceQueue<FileKey>			file_key_ref_queue;
	private final static Map<WeakReference<FileKey>,String>	file_key_map;
	
	static{
		if ( TRACK_FILE_KEYS ){
			
			file_key_ref_queue	= new ReferenceQueue<>();
			file_key_map		= new ConcurrentHashMap<>();

			new AEThread2( "StringInterner:FileKey" )
			{
				@Override
				public void 
				run()
				{
					while( true ){
						
						try{
							WeakReference<FileKey> next = (WeakReference<FileKey>)file_key_ref_queue.remove( 0 );
							
							file_key_map.remove( next );
														
						}catch( Throwable e ){
							
							Debug.out( e );
						}
					}
				}
			}.start();
		}else{
			
			file_key_ref_queue	= null;
			file_key_map		= null;
		}
	}
	
	static{
		AEDiagnostics.addEvidenceGenerator(
			new AEDiagnosticsEvidenceGenerator()
			{
				@Override
				public void
				generate(
					IndentWriter writer)
				{
					writer.println( "String Interner" );

					try{
						writer.indent();
						
						writer.println( "Managed: " + managedInterningSet.size());
						
						if ( TRACK_FILE_KEYS ){
							
							int	fk_count	= 0;
							long fk_total	= 0;
							long fk_actual	= 0;							
							long fk_dups	= 0;
							
							Iterator<WeakReference<FileKey>> it = file_key_map.keySet().iterator();
							
							Set<String> strings = new IdentityHashSet<>();
							Set<String> strings2 = new HashSet<>();
							
							while( it.hasNext()){
								
								FileKey fk = it.next().get();
								
								if ( fk != null ){
																		
									fk_count++;
									
									String[] comps = fk.comps;
									
									int comp_num = fk.comps.length;
									
									for ( int i=fk.isFile()?1:0; i<comp_num; i++ ){
										
										String str = comps[i];
										
										int len = str.length();
										
										fk_total += len;
										
										if ( !strings.contains( str )){
																					
											strings.add( str );
											
											fk_actual += len;
										
											if ( !strings2.contains( str )){
											
												strings2.add( str );
											
											}else{
												
												fk_dups += len;
											}
										}
									}
								}
							}
							
							writer.println( 
									"File Keys: num=" + fk_count + 
									", total=" + DisplayFormatters.formatByteCountToKiBEtc( fk_total ) +
									", dups=" + DisplayFormatters.formatByteCountToKiBEtc( fk_dups ) +
									", actual=" + DisplayFormatters.formatByteCountToKiBEtc( fk_actual ));
						}
					}finally{
						
						writer.exdent();
					}
				}
			});
	}
	
	public static class
	FileKey
		implements StringSupplier
	{
		public static final FileKey EMPTY_FILE = new FileKey( "" );

		private final int 		hash_code;
		private final String[]	comps;
		
		public
		FileKey(
			File	file )
		{
			this( file, false );
		}
		
		public
		FileKey(
			String	str )
		{
			this((File)null, str, false );
		}
		
		public
		FileKey(
			File	file,
			String	tail )
		{
			this( file, tail, false );
		}
		
		protected
		FileKey(
			File	file,
			boolean	is_dir )
		{
			this( file, null, is_dir );
		}
		
		public
		FileKey(
			FileKey	fk,
			String	tail )
		{
			this( fk, tail, false );
		}
		
		protected
		FileKey(
			FileKey	fk,
			String	tail,
			boolean	is_dir )
		{
			this( fk.getFile(), tail, is_dir );
		}
		
		protected
		FileKey(
			File	original_file,
			String	tail,
			boolean	is_dir )
		{
			if ( TRACK_FILE_KEYS ){
				
				file_key_map.put( new WeakReference<FileKey>( this, file_key_ref_queue ), "" );
			}
			
			List<String> l = null;
		
			int hc = 0;
			
			if ( tail != null && !tail.isEmpty()){
				
				int pos = tail.length();
				
				while( pos >= 0 ){
					
					int next = tail.lastIndexOf( File.separator, pos-1 );
					
					String name;
					
					if ( next < 0 ){
					
						name = tail.substring( 0, pos );
						
					}else{
						
						name = tail.substring( next+1, pos );
					}
					
					hc += name.hashCode();
					
					if ( l == null ){
						
						l = new ArrayList<>( 10 );
						
						l.add( is_dir?intern( name ):name );
						
					}else{
						
						l.add( intern( name ));
					}
					
					pos = next;
				}
			}
			
			File file = original_file;
			
			while( file != null ){
								
				String name = file.getName();
						
				if ( name == null ){
					
					break;
					
				}else if ( name.isEmpty()){
					
					name = file.getPath();
					
					if ( name.endsWith( File.separator )){
						
							// if this is the only component then we retain it verbatim as it might be the volume label (e.g. C:\)
							// otherwise we remove it as it will be re-added when reconstructed
						
						if ( l != null ){
						
							name = name.substring( 0, name.length() -1 );
						}
					}
				}
				
				hc += name.hashCode();
				
				if ( l == null ){
					
					l = new ArrayList<>( 10 );
					
					l.add( is_dir?intern( name ):name );
					
				}else{
					
					l.add( intern( name ));
				}
				
				file = file.getParentFile();
			}
				
			hash_code = hc;
			
			if ( l == null ){
				
				comps = new String[0];
				
			}else{
			
				comps = l.toArray( new String[l.size()]);
			}
			
			/*
			if ( tail == null ){
				
				if ( !original_file.getAbsolutePath().equals( getFile().getAbsolutePath())){
					
					Debug.out( "DERP: " + original_file.getAbsolutePath() + " -> " + toString());
				}
			}else{
				if ( !new File( original_file, tail ).getAbsolutePath().equals( getFile().getAbsolutePath())){
					
					Debug.out( "DERP: " + original_file.getAbsolutePath() + " + " + tail + " ->" +toString());
				}
			}
			*/
		}
		
		public File
		getFile()
		{
			return( FileUtil.newFile( toString()));
		}
		
		public boolean
		isEmpty()
		{
			return( comps.length == 0 );
		}
		
		public String
		toString()
		{
			int pos = comps.length-1;
			
			if ( pos < 0 ){
				
				return( "" );
			}
							
			StringBuilder result = new StringBuilder( comps[pos--] );
			
			while( pos >= 0 ){
				
				result.append(File.separator);
				
				result.append( comps[pos--]);
			}
			
			return( result.toString());
		}
		
		@Override
		public String 
		get()
		{
			return( toString());
		}
		
		@Override
		public int 
		hashCode()
		{
			return( hash_code );
		}
		
		@Override
		public boolean 
		equals(
			Object obj )
		{
			if ( obj instanceof FileKey ){
				
				FileKey other = (FileKey)obj;
				
				String[] ocomps = other.comps;
				
				int num_comps = comps.length;
				
				if ( num_comps != ocomps.length ){
					
					return( false );
				}
				
				for ( int i=0; i<num_comps; i++){
					
					if ( !comps[i].equals( ocomps[i] )){
						
						return( false );
					}
				}
				
				return( true );
				
			}else if ( obj instanceof StringSupplier ){
				
				return( get().equals(((StringSupplier)obj).get()));
				
			}else{
				
				return( false );
			}
		}
		
		@Override
		public int 
		compareTo(
			StringSupplier o )
		{
			return( get().compareTo(o.get()));
		}
		
		public boolean
		isFile()
		{
			return( true );
		}
	}
	
	public static class
	DirKey
		extends FileKey
	{
		public static final DirKey EMPTY_PATH = new DirKey( "" );
		
		public
		DirKey(
			File	dir )
		{
			super( dir, true );
		}
		
		public
		DirKey(
			String	str )
		{
			super((File)null, str, true );
		}
		
		public
		DirKey(
			DirKey	dir,
			String	str )
		{
			super( dir, str, true );
		}
		
		@Override
		public boolean
		isFile()
		{
			return( false );
		}
	}
}
