/*
 * Created on Nov 4, 2008
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


package com.biglybt.core.util;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.diskmanager.cache.CacheFileManager;
import com.biglybt.core.diskmanager.cache.CacheFileManagerFactory;
import com.biglybt.core.diskmanager.cache.CacheFileManagerStats;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;

public class
DirectByteBufferPoolReal
	extends DirectByteBufferPool
{
	private static final boolean disable_gc = System.getProperty( "az.disable.explicit.gc", "0" ).equals( "1" );

	static{

		if ( disable_gc ){

			System.out.println( "Explicit GC disabled" );
		}
	}

	protected static final boolean				DEBUG_TRACK_HANDEDOUT	= AEDiagnostics.TRACE_DBB_POOL_USAGE;
	protected static final boolean				DEBUG_PRINT_MEM			= AEDiagnostics.PRINT_DBB_POOL_USAGE;

	protected static final int					DEBUG_PRINT_TIME		= 120 * 1000;

	protected static final boolean				DEBUG_HANDOUT_SIZES		= false;
	protected static final boolean				DEBUG_FREE_SIZES		= false;





	  // There is no point in allocating buffers smaller than 4K,
	  // as direct ByteBuffers are page-aligned to the underlying
	  // system, which is 4096 byte pages under most OS's.
	  // If we want to save memory, we can distribute smaller-than-4K
	  // buffers by using the slice() method to break up a standard buffer
	  // into smaller chunks, but that's more work.

	private static final int START_POWER = 12;    // 4096
	private static final int END_POWER   = 28;    // 25=32MB, 28=256MB - yes, there is a 500GB torrent out there with 128MB pieces :(

  		// without an extra bucket here we get lots of wastage with the file cache as typically
  		// 16K data reads result in a buffer slightly bigger than 16K due to protocol header
  		// This means we would bump up to 32K pool entries, hence wasting 16K per 16K entry

	private static final int[]	EXTRA_BUCKETS = { DiskManager.BLOCK_SIZE + 128 };


	public static final int MAX_SIZE = BigInteger.valueOf(2).pow(END_POWER).intValue();


	private final Map buffersMap = new LinkedHashMap(END_POWER - START_POWER + 1);

	private final Object poolsLock = new Object();

	private static final int	SLICE_END_SIZE				= 2048;
	private static final int    SLICE_ALLOC_CHUNK_SIZE		= 4096;


	private static final short[]		SLICE_ENTRY_SIZES		= { 8, 16, 32, 64, 128, 256, 512, 1024, SLICE_END_SIZE };
	private static final short[]		SLICE_ALLOC_MAXS		= { 256, 256, 128, 64, 64,  64,  64,  64,   64 };

	private static final short[]		SLICE_ENTRY_ALLOC_SIZES = new short[SLICE_ENTRY_SIZES.length];
	private static final List[]			slice_entries 			= new List[SLICE_ENTRY_SIZES.length];
	private static final boolean[][]	slice_allocs 			= new boolean[SLICE_ENTRY_SIZES.length][];
	private static final boolean[]		slice_alloc_fails		= new boolean[SLICE_ENTRY_SIZES.length];

	static{

		int mult = COConfigurationManager.getIntParameter( "memory.slice.limit.multiplier" );

		if ( mult > 1 ){

			for (int i=0;i<SLICE_ALLOC_MAXS.length;i++){

				SLICE_ALLOC_MAXS[i] *= mult;
			}
		}

		for (int i=0;i<SLICE_ENTRY_SIZES.length;i++){

			SLICE_ENTRY_ALLOC_SIZES[i] = (short)(SLICE_ALLOC_CHUNK_SIZE/SLICE_ENTRY_SIZES[i]);

			slice_allocs[i] = new boolean[SLICE_ALLOC_MAXS[i]];

			slice_entries[i] = new LinkedList();
		}
	}

	private static final long[]			slice_use_count 	= new long[SLICE_ENTRY_SIZES.length];

	private final Map handed_out	= new IdentityHashMap();	// for debugging (ByteBuffer has .equals defined on contents, hence IdentityHashMap)

	private final Map	size_counts	= new TreeMap();

	private static final long COMPACTION_CHECK_PERIOD = 2*60*1000; //2 min
	private static final long MAX_FREE_BYTES = 10*1024*1024; //10 MB
	private static final long MIN_FREE_BYTES = 1*1024*1024; // 1 MB

	private long bytesIn = 0;
	private long bytesOut = 0;


	protected
	DirectByteBufferPoolReal()
	{
	    //create the buffer pool for each buffer size

	  	ArrayList	list = new ArrayList();

	    for (int p=START_POWER; p <= END_POWER; p++) {

	    	list.add( new Integer(BigInteger.valueOf(2).pow(p).intValue()));
	    }

	    for (int i=0;i<EXTRA_BUCKETS.length;i++){

	        list.add( new Integer(EXTRA_BUCKETS[i]));
	    }

	    Integer[]	sizes = new Integer[ list.size() ];
	    list.toArray( sizes );
	    Arrays.sort( sizes);

	    for (int i=0;i<sizes.length;i++){

	    	ArrayList bufferPool = new ArrayList();

	    	buffersMap.put(sizes[i], bufferPool);
	    }

	    //initiate periodic timer to check free memory usage
	    SimpleTimer.addPeriodicEvent(
	    	"DirectBB:compact",
	        COMPACTION_CHECK_PERIOD,
	        new TimerEventPerformer() {
	          @Override
	          public void perform(TimerEvent ev ) {

	            compactBuffers();
	          }
	        }
	     );

	    if( DEBUG_PRINT_MEM ) {
	      Timer printer = new Timer("printer");
	      printer.addPeriodicEvent(
	          DEBUG_PRINT_TIME,
	          new TimerEventPerformer() {
	            @Override
	            public void perform(TimerEvent ev ) {
	              printInUse( false );
	            }
	          }
	      );
	    }
	}


  /**
   * Allocate and return a new direct ByteBuffer.
   */
  private ByteBuffer allocateNewBuffer(final int _size) {
    try {
      return ByteBuffer.allocateDirect(_size);
    }
    catch (OutOfMemoryError e) {
       //Debug.out("Running garbage collector...");

       clearBufferPools();

       runGarbageCollection();

       try {
       		return ByteBuffer.allocateDirect(_size);

       }catch (OutOfMemoryError ex) {

         String msg = "Memory allocation failed: Out of direct memory space.\n"
                    + "To fix: Use the -XX:MaxDirectMemorySize=512m command line option,\n"
                    + "or upgrade your Java JRE to version 1.4.2_05 or 1.5 series or newer.";
       	 Debug.out( msg );

       	 Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, msg));

         printInUse( true );

         throw( ex );
       }
    }
  }


  /**
   * Retrieve a buffer from the buffer pool of size at least
   * <b>length</b>, and no larger than <b>DirectByteBufferPool.MAX_SIZE</b>
   */
  @Override
  protected DirectByteBuffer
  getBufferSupport(
  	byte	_allocator,
  	int 	_length)
  {
    if (_length < 1) {
        Debug.out("requested length [" +_length+ "] < 1");
        return null;
    }

    if (_length > MAX_SIZE) {
        Debug.out("requested length [" +_length+ "] > MAX_SIZE [" +MAX_SIZE+ "]");
        return null;
    }

    return getBufferHelper(_allocator,_length);
  }


  /**
   * Retrieve an appropriate buffer from the free pool, or
   * create a new one if the pool is empty.
   */


  	private DirectByteBuffer
  	getBufferHelper(
		byte	_allocator,
		int 	_length)
	{
		DirectByteBuffer	res;

		if ( _length <= SLICE_END_SIZE ){

			res = getSliceBuffer( _allocator, _length );

		}else{

			ByteBuffer	buff = null;

			Integer reqVal = new Integer(_length);

				//loop through the buffer pools to find a buffer big enough

			Iterator it = buffersMap.keySet().iterator();

			while (it.hasNext()) {

				Integer keyVal = (Integer)it.next();

					//	check if the buffers in this pool are big enough

				if ( reqVal.compareTo(keyVal) <= 0 ){

					ArrayList bufferPool = (ArrayList)buffersMap.get(keyVal);

					while( true ){

						synchronized ( poolsLock ) {

							// make sure we don't remove a buffer when running compaction
							// if there are no free buffers in the pool, create a new one.
							// otherwise use one from the pool

							if ( bufferPool.isEmpty()){

								buff = allocateNewBuffer(keyVal.intValue());

								if ( buff == null ){

									Debug.out( "allocateNewBuffer for " + _length + " returned null" );
								}

								break;

							}else{

								synchronized ( bufferPool ) {

									buff = (ByteBuffer)bufferPool.remove(bufferPool.size() - 1);
								}

								if ( buff == null ){

									Debug.out( "buffer pool for " + _length + " contained null entry" );

								}else{

									break;
								}
							}
						}
					}

					break;
				}
			}

			if ( buff == null ){

				String str = "Unable to find an appropriate buffer pool for " + _length;

			    Debug.out( str );

			    throw( new RuntimeException( str ));
			}

			res = new DirectByteBuffer( _allocator, buff, this );
		}

        	// clear doesn't actually zero the data, it just sets pos to 0 etc.

		ByteBuffer buff = res.getBufferInternal();

        buff.clear();   //scrub the buffer

		buff.limit( _length );

        if ( DEBUG_PRINT_MEM || DEBUG_TRACK_HANDEDOUT ){

        	synchronized( handed_out ){

                bytesOut += buff.capacity();

				if ( DEBUG_HANDOUT_SIZES ){

					int	trim_size;

					if ( _length < 32 ){

						trim_size = 4;
					}else{

						trim_size = 16;
					}

					int	trim = ((_length+trim_size-1)/trim_size)*trim_size;

					Long count = (Long)size_counts.get(new Integer(trim));

					if ( count == null ){

						size_counts.put( new Integer( trim ), new Long(1));

					}else{

						size_counts.put( new Integer( trim), new Long( count.longValue() + 1 ));
					}
				}

        		if ( handed_out.put( buff, res ) != null ){

        			Debug.out( "buffer handed out twice!!!!");

        			throw( new RuntimeException( "Buffer handed out twice" ));
        		}

				//System.out.println( "[" + handed_out.size() + "] -> " + buff + ", bytesIn = " + bytesIn + ", bytesOut = " + bytesOut );
          	}
        }

        // addInUse( dbb.capacity() );

        return( res );
    }


	  /**
	   * Return the given buffer to the appropriate pool.
	   */

	@Override
	protected void
	returnBufferSupport(
		DirectByteBuffer ddb )
	{
		ByteBuffer	buff = ddb.getBufferInternal();

		if ( buff == null ){

			Debug.out( "Returned dbb has null delegate" );

			throw( new RuntimeException( "Returned dbb has null delegate" ));
		}

		int	capacity = buff.capacity();

	  	if ( DEBUG_TRACK_HANDEDOUT ){

	  		synchronized( handed_out ){

	  			bytesIn += capacity;

	  			if ( handed_out.remove( buff ) == null ){

	  				Debug.out( "buffer not handed out" );

	  				throw( new RuntimeException( "Buffer not handed out" ));
	  			}

	       		// System.out.println( "[" + handed_out.size() + "] <- " + buffer + ", bytesIn = " + bytesIn + ", bytesOut = " + bytesOut );
	  		}
	  	}

	    // remInUse( buffer.capacity() );

		if ( capacity <= SLICE_END_SIZE ){

			freeSliceBuffer( ddb );

		}else{
		    Integer buffSize = new Integer(capacity);

		    ArrayList bufferPool = (ArrayList)buffersMap.get(buffSize);

		    if (bufferPool != null) {

				//no need to sync around 'poolsLock', as adding during compaction is ok

		      synchronized ( bufferPool ){

		        bufferPool.add(buff);
		      }
		    }else{

		      Debug.out("Invalid buffer given; could not find proper buffer pool");
		    }
		}
	}


  /**
   * Clears the free buffer pools so that currently
   * unused buffers can be garbage collected.
   */
  private void clearBufferPools() {
    Iterator it = buffersMap.values().iterator();
    while (it.hasNext()) {
        ArrayList bufferPool = (ArrayList)it.next();
        bufferPool.clear();
    }
  }


  /**
   * Force system garbage collection.
   */
  private void runGarbageCollection() {
	if ( !disable_gc ){
	    if( DEBUG_PRINT_MEM ) {
	      System.out.println( "runGarbageCollection()" );
	    }
	    System.runFinalization();
	    System.gc();
	}
  }


  /**
   * Checks memory usage of free buffers in buffer pools,
   * and calls the compaction method if necessary.
   */
  private void compactBuffers() {

	  nonsliecd: synchronized (poolsLock)
		{
			long freeSize = bytesFree();

			if (freeSize < MIN_FREE_BYTES)
				break nonsliecd;

			// apply cleanup pressure based on filling degree
			float remainingFactor;
			if (freeSize > MAX_FREE_BYTES) // downsize to 50% of the limit (not the current capacity!) if we're overlimit
				remainingFactor = 0.5f * MAX_FREE_BYTES / (float) freeSize;
			else // reduce to something between 50% (full: maximum reduction) and 100% (empty: no reduction)
				remainingFactor = 1.0f - 0.5f * freeSize / (float) MAX_FREE_BYTES;

			if (DEBUG_PRINT_MEM)
				System.out.println("Performing cleanup, reducing to " + remainingFactor * 100 + "%");

			ArrayList pools = new ArrayList(buffersMap.values());
			for (int i = pools.size() - 1; i >= 0; i--)
			{
				ArrayList pool = (ArrayList) pools.get(i);
				int limit = (int) (pool.size() * remainingFactor); // floor(), this way we can reach 0 at some point
				for (int j = pool.size() - 1; j >= limit; j--)
					pool.remove(j);
			}

			runGarbageCollection();

			if (DEBUG_PRINT_MEM)
			{
				printInUse(false);
				System.out.println("Cleanup done\n");
			}
		}

		compactSlices();
	}



  private long bytesFree() {
    long bytesUsed = 0;
    synchronized( poolsLock ) {
      //count up total bytes used by free buffers
      Iterator it = buffersMap.keySet().iterator();
      while (it.hasNext()) {
        Integer keyVal = (Integer)it.next();
        ArrayList bufferPool = (ArrayList)buffersMap.get(keyVal);

        bytesUsed += keyVal.intValue() * bufferPool.size();
      }
    }
    return bytesUsed;
  }


  	private void
	printInUse(
		boolean		verbose )
  	{
  		if ( DEBUG_PRINT_MEM ){

	  		synchronized( handed_out ){

	            System.out.print("DIRECT: given=" +bytesOut/1024/1024+ "MB, returned=" +bytesIn/1024/1024+ "MB, ");

	            long in_use = bytesOut - bytesIn;
	            if( in_use < 1024*1024 ) System.out.print( "in use=" +in_use+ "B, " );
	            else System.out.print( "in use=" +in_use/1024/1024+ "MB, " );

	            long free = bytesFree();
	            if( free < 1024*1024 ) System.out.print( "free=" +free+ "B" );
	            else System.out.print( "free=" +free/1024/1024+ "MB" );

	  			System.out.println();

		  		CacheFileManager cm	= null;

				try{
		 			cm = CacheFileManagerFactory.getSingleton();

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}

	  			Iterator	it = handed_out.values().iterator();

	  			Map	cap_map		= new TreeMap();
	  			Map	alloc_map	= new TreeMap();

	  			while( it.hasNext()){

	  				DirectByteBuffer	db = (DirectByteBuffer)it.next();

	  				if ( verbose ){
		  				String	trace = db.getTraceString();

		  				if ( trace != null ){

		  					System.out.println( trace );
		  				}
	  				}

	  				Integer cap 	= new Integer( db.getBufferInternal().capacity());
	  				Byte	alloc 	= new Byte( db.getAllocator());

	  				myInteger	c = (myInteger)cap_map.get(cap);

	  				if ( c == null ){

	  					c	= new myInteger();

	  					cap_map.put( cap, c );
	  				}

	  				c.value++;

					myInteger	a = (myInteger)alloc_map.get(alloc);

	  				if ( a == null ){

	  					a	= new myInteger();

	  					alloc_map.put( alloc, a );
	  				}

	  				a.value++;
	  			}

	  			it = cap_map.keySet().iterator();

	  			while( it.hasNext()){

	  				Integer		key 	= (Integer)it.next();
	  				myInteger	count 	= (myInteger)cap_map.get( key );

	  		        if( key.intValue() < 1024 ){

	  		        	System.out.print("[" +key.intValue()+ " x " +count.value+ "] ");

	  		        }else{

	  		        	System.out.print("[" +key.intValue()/1024+ "K x " +count.value+ "] ");
	  		        }
	  			}

	  			System.out.println();

				it = alloc_map.keySet().iterator();

	  			while( it.hasNext()){

	  				Byte		key 	= (Byte)it.next();
	  				myInteger	count 	= (myInteger)alloc_map.get( key );

	  	        	System.out.print("[" + DirectByteBuffer.AL_DESCS[key.intValue()]+ " x " +count.value+ "] ");
	  			}

	  			if ( cm != null ){

	  				CacheFileManagerStats stats = cm.getStats();

	  				System.out.print( " - Cache: " );


					System.out.print( "sz=" + stats.getSize());
					System.out.print( ",us=" + stats.getUsedSize());
					System.out.print( ",cw=" + stats.getBytesWrittenToCache());
					System.out.print( ",cr=" + stats.getBytesReadFromCache());
					System.out.print( ",fw=" + stats.getBytesWrittenToFile());
					System.out.print( ",fr=" + stats.getBytesReadFromFile());

	  			}

	  			System.out.println();

				if ( DEBUG_HANDOUT_SIZES ){
					it = size_counts.entrySet().iterator();

					String	str = "";

					while( it.hasNext()){

						Map.Entry	entry = (Map.Entry)it.next();

						str += (str.length()==0?"":",") + entry.getKey() + "=" + entry.getValue();
					}

					System.out.println( str );
				}

				String str = "";

				for (int i=0;i<slice_entries.length;i++){

					boolean[]	allocs = slice_allocs[i];
					int	alloc_count = 0;
					for (int j=0;j<allocs.length;j++){
						if( allocs[j]){
							alloc_count++;
						}
					}
					str += (i==0?"":",") + "["+SLICE_ENTRY_SIZES[i]+"]f=" +slice_entries[i].size()+",a=" + (alloc_count*SLICE_ENTRY_ALLOC_SIZES[i]) + ",u=" +slice_use_count[i];
				}

				System.out.println( "slices: " + str );

	  		}

	  		if(DEBUG_FREE_SIZES)
	  		{
	  			System.out.print("free block sizes: ");

	  			synchronized (poolsLock)
				{
					Iterator it = buffersMap.keySet().iterator();
					while (it.hasNext())
					{
						Integer keyVal = (Integer) it.next();
						ArrayList bufferPool = (ArrayList) buffersMap.get(keyVal);

						int blocksize = keyVal.intValue();
						int blockfootprint = keyVal.intValue() * bufferPool.size();
						if(blockfootprint == 0)
							continue;
						String blocksuffix = "";
						if(blocksize > 1024) { blocksize /= 1024; blocksuffix = "k";}
						if(blocksize > 1024) { blocksize /= 1024; blocksuffix = "M";}
						String footsuffix = "";
						if(blockfootprint > 1024) { blockfootprint /= 1024; footsuffix = "k";}
						if(blockfootprint > 1024) { blockfootprint /= 1024; footsuffix = "M";}


						System.out.print("["+ blocksize + blocksuffix + ":" + blockfootprint + footsuffix + "] ");
					}
				}

	  			System.out.println();
	  		}

            long free_mem = Runtime.getRuntime().freeMemory() /1024/1024;
            long max_mem = Runtime.getRuntime().maxMemory() /1024/1024;
            long total_mem = Runtime.getRuntime().totalMemory() /1024/1024;
            System.out.println("HEAP: max=" +max_mem+ "MB, total=" +total_mem+ "MB, free=" +free_mem+ "MB");
            System.out.println();
  		}
  	}


		// Slice buffer management

  	private DirectByteBuffer
	getSliceBuffer(
		byte		_allocator,
		int			_length )
	{
		int	slice_index = getSliceIndex( _length );

		List		my_slice_entries 	= slice_entries[slice_index];

		synchronized( my_slice_entries ){

			boolean[]	my_allocs			= slice_allocs[slice_index];

			sliceBuffer	sb = null;

			if ( my_slice_entries.size() > 0 ){

				sb = (sliceBuffer)my_slice_entries.remove(0);

				slice_use_count[slice_index]++;

			}else{

					// find a free slot

				short	slot = -1;

				for (short i=0;i<my_allocs.length;i++){

					if( !my_allocs[i]){

						slot	= i;

						break;
					}
				}

				if ( slot != -1 ){

					short	slice_entry_size 	= SLICE_ENTRY_SIZES[slice_index];
					short	slice_entry_count	= SLICE_ENTRY_ALLOC_SIZES[slice_index];

					ByteBuffer	chunk = ByteBuffer.allocateDirect(  slice_entry_size*slice_entry_count  );

					my_allocs[slot] = true;

					for (short i=0;i<slice_entry_count;i++){

						chunk.limit((i+1)*slice_entry_size);
						chunk.position(i*slice_entry_size);

						ByteBuffer	slice = chunk.slice();

						sliceBuffer new_buffer = new sliceBuffer( slice, slot, i );

						if ( i == 0 ){

							sb = new_buffer;

							slice_use_count[slice_index]++;

						}else{

							my_slice_entries.add( new_buffer );
						}
					}
				}else{

					if ( !slice_alloc_fails[slice_index] ){

						slice_alloc_fails[slice_index]	= true;

						Debug.out( "Run out of slice space for '" + SLICE_ENTRY_SIZES[slice_index] + ", reverting to normal allocation" );
					}

					ByteBuffer buff = ByteBuffer.allocate( _length );

				    return( new DirectByteBuffer( _allocator, buff, this ));

				}
			}

			sliceDBB dbb = new sliceDBB( this, _allocator, sb );

			return( dbb );
		}
	}

  	private void
	freeSliceBuffer(
		DirectByteBuffer	ddb )
	{
		if ( ddb instanceof sliceDBB ){

			int	slice_index = getSliceIndex( ddb.getBufferInternal().capacity());

			List		my_slice_entries 	= slice_entries[slice_index];

			synchronized( my_slice_entries ){

				my_slice_entries.add( 0, ((sliceDBB)ddb).getSliceBuffer());
			}
		}
	}

  	private void
	compactSlices()
	{
			// we don't maintain the buffers in sorted order as this is too costly. however, we
			// always allocate and free from the start of the free list, so unused buffer space
			// will be at the end of the list. we periodically sort this list into allocate block
			// order so that if an entire block isn't used for one compaction cycle all of
			// its elements will end up together, if you see what I mean :P

			// when we find an entire block is unused then we just drop them from the list to
			// permit them (and the underlying block) to be garbage collected

		for (int i=0;i<slice_entries.length;i++){

			int			entries_per_alloc 	= SLICE_ENTRY_ALLOC_SIZES[i];

			List<sliceBuffer>	l = slice_entries[i];

				// no point in trying gc if not enough entries

			if ( l.size() >= entries_per_alloc ){

				synchronized( l ){

					Collections.sort( l,
						new Comparator<sliceBuffer>()
						{
							@Override
							public int
							compare(
								sliceBuffer	sb1,
								sliceBuffer	sb2 )
							{
								int	res = sb1.getAllocID() - sb2.getAllocID();

								if ( res == 0 ){

									res = sb1.getSliceID() - sb2.getSliceID();
								}

								return( res );
							}
						});

					boolean[]	allocs				= slice_allocs[i];

					Iterator<sliceBuffer>	it = l.iterator();

					int	current_alloc 	= -1;
					int entry_count		= 0;

					boolean	freed_one	= false;

					while( it.hasNext()){

						sliceBuffer	sb = it.next();

						int	aid = sb.getAllocID();

						if ( aid != current_alloc ){

							if ( entry_count == entries_per_alloc ){

								// System.out.println( "CompactSlices[" + SLICE_ENTRY_SIZES[i]+"] freeing " + aid );

								freed_one	= true;

								allocs[aid]	= false;
							}

							current_alloc	= aid;

							entry_count		= 1;

						}else{

							entry_count++;
						}
					}

					if ( entry_count == entries_per_alloc ){

						// System.out.println( "CompactSlices[" + SLICE_ENTRY_SIZES[i]+"] freeing " + current_alloc );

						freed_one	= true;

						allocs[current_alloc]	= false;
					}

					if ( freed_one ){

						it = l.iterator();

						while( it.hasNext()){

							sliceBuffer	sb = it.next();

							if ( !allocs[ sb.getAllocID()]){

								it.remove();
							}
						}
					}
				}
			}
		}
	}

  	private int
	getSliceIndex(
		int	_length )
	{
		for (int i=0;i<SLICE_ENTRY_SIZES.length;i++){

			if ( _length <= SLICE_ENTRY_SIZES[i] ){

				return( i );
			}
		}

		Debug.out( "eh?");

		return( 0 );
	}

  	private static class
	sliceBuffer
	{
		private final ByteBuffer	buffer;
		private final short		alloc_id;
		private final short		slice_id;

		protected
		sliceBuffer(
			ByteBuffer	_buffer,
			short		_alloc_id,
			short		_slice_id )
		{
			buffer		= _buffer;
			alloc_id	= _alloc_id;
			slice_id	= _slice_id;
		}

		protected ByteBuffer
		getBuffer()
		{
			return( buffer );
		}

		protected short
		getAllocID()
		{
			return( alloc_id );
		}

		protected short
		getSliceID()
		{
			return( slice_id );
		}
	}

  	private static class
	sliceDBB
		extends DirectByteBuffer
	{
		private final sliceBuffer	slice_buffer;

		protected
		sliceDBB(
			DirectByteBufferPoolReal	_pool,
			byte						_allocator,
			sliceBuffer					_sb )
		{
			super( _allocator, _sb.getBuffer(), _pool );

			slice_buffer	= _sb;
		}

		protected sliceBuffer
		getSliceBuffer()
		{
			return( slice_buffer );
		}
	}

  	private static class
	myInteger
  	{
  		int	value;
  	}
}
