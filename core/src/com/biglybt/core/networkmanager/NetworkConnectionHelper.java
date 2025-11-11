/*
 * Created on Feb 6, 2007
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


package com.biglybt.core.networkmanager;

import java.util.concurrent.atomic.AtomicInteger;

import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;

public abstract class
NetworkConnectionHelper
	implements NetworkConnectionBase, ControllerAllocationManagement
{
	private static final AtomicInteger	next_partition_id = new AtomicInteger();
	
	private final int partition_id = next_partition_id.incrementAndGet();
	
	@Override
	public int 
	getPartitionID()
	{
		return( partition_id );
	}
	
	int	upload_limit;

	private final LimitedRateGroup upload_limiter = new LimitedRateGroup() {
		@Override
		public String
		getName()
		{
			return( "per_con_up: " + getString());
		}
		@Override
		public long getRateLimitBytesPerSecond()
		{
			return upload_limit;
		}
		@Override
		public boolean
		isDisabled()
		{
			return( upload_limit == -1 );
		}
		@Override
		public void
		updateBytesUsed(
				long	used )
		{
		}
	};

	int	download_limit;

	private final LimitedRateGroup download_limiter =
			new LimitedRateGroup()
	{
		@Override
		public String
		getName()
		{
			return( "per_con_down: " + getString());
		}
		@Override
		public long getRateLimitBytesPerSecond() {  return download_limit;  }
		@Override
		public boolean
		isDisabled()
		{
			return( download_limit == -1 );
		}
		@Override
		public void
		updateBytesUsed(
				long	used )
		{
		}
	};

	private volatile LimitedRateGroup[]	upload_limiters 	= { upload_limiter };
	private volatile LimitedRateGroup[]	download_limiters 	= { download_limiter };

	public abstract void 
	close(
		String reason );
		
	@Override
	public int
	getUploadLimit()
	{
		return( upload_limit );
	}

	@Override
	public int
	getDownloadLimit()
	{
		return( download_limit );
	}

	@Override
	public void
	setUploadLimit(
			int		limit )
	{
		upload_limit = limit;
	}

	@Override
	public void
	setDownloadLimit(
			int		limit )
	{
		download_limit = limit;
	}

	@Override
	public void
	addRateLimiter(
			LimitedRateGroup 	limiter,
			boolean 			upload	)
	{
		synchronized( this ){

			if ( upload ){

				for (int i=0;i<upload_limiters.length;i++){

					if ( upload_limiters[i] == limiter ){

						return;
					}
				}

				LimitedRateGroup[] new_upload_limiters = new LimitedRateGroup[upload_limiters.length+1];

				System.arraycopy(upload_limiters, 0, new_upload_limiters, 0, upload_limiters.length );

				new_upload_limiters[ upload_limiters.length ] = limiter;

				upload_limiters = new_upload_limiters;
			}else{

				for (int i=0;i<download_limiters.length;i++){

					if ( download_limiters[i] == limiter ){

						return;
					}
				}
				LimitedRateGroup[] new_download_limiters = new LimitedRateGroup[download_limiters.length+1];

				System.arraycopy(download_limiters, 0, new_download_limiters, 0, download_limiters.length );

				new_download_limiters[ download_limiters.length ] = limiter;

				download_limiters = new_download_limiters;
			}
		}

		NetworkManager.getSingleton().addRateLimiter( this, limiter, upload );
	}

	@Override
	public void
	removeRateLimiter(
		LimitedRateGroup 	limiter,
		boolean 			upload )
	{
		synchronized( this ){

			if ( upload ){

				if ( upload_limiters.length == 0 ){

					return;
				}

				int	pos = 0;

				LimitedRateGroup[] new_upload_limiters = new LimitedRateGroup[upload_limiters.length-1];

				for (int i=0;i<upload_limiters.length;i++){

					if ( upload_limiters[i] != limiter ){

						if ( pos == new_upload_limiters.length ){

							return;
						}

						new_upload_limiters[pos++] = upload_limiters[i];
					}
				}

				upload_limiters = new_upload_limiters;

			}else{

				if ( download_limiters.length == 0 ){

					return;
				}

				int	pos = 0;

				LimitedRateGroup[] new_download_limiters = new LimitedRateGroup[download_limiters.length-1];

				for (int i=0;i<download_limiters.length;i++){

					if ( download_limiters[i] != limiter ){

						if ( pos == new_download_limiters.length ){

							return;
						}

						new_download_limiters[pos++] = download_limiters[i];
					}
				}

				download_limiters = new_download_limiters;
			}
		}

		NetworkManager.getSingleton().removeRateLimiter( this, limiter, upload );
	}

	@Override
	public LimitedRateGroup[]
	getRateLimiters(
		boolean upload )
	{
		if ( upload ){

			return( upload_limiters );

		}else{

			return( download_limiters );
		}
	}
	
	private final AtomicInteger		read_release_count = new AtomicInteger();
	private volatile int 			target_read_partition = UNALLOCATED_PARTITION;
	private final AtomicInteger		active_read_partition = new AtomicInteger(UNALLOCATED_PARTITION);
	private long					last_read_stall;	// doesn't need to be volatile as used in thead-specific context
	
	private final AtomicInteger		write_release_count = new AtomicInteger();
	private volatile int 			target_write_partition = UNALLOCATED_PARTITION;
	private final AtomicInteger		active_write_partition = new AtomicInteger(UNALLOCATED_PARTITION);
	private long					last_write_stall;	// doesn't need to be volatile as used in thead-specific context

	
	
					
	@Override
	public void 
	setTargetReadControllerPartition(
		int partition)
	{
		target_read_partition = partition;
	}
	
	@Override
	public void 
	setReadControllerInactive()
	{
		active_read_partition.set( UNALLOCATED_PARTITION );
	}
	
	@Override
	public void 
	activeReadControllerRelease(
		boolean added)
	{
		//System.out.println( "read release: " + added + " for " + getString());
		
		if ( added ){
			
			read_release_count.incrementAndGet();
			
		}else{
			
			if ( read_release_count.decrementAndGet() == 0 ){
				
				active_read_partition.set( UNALLOCATED_PARTITION );
			}
		}
	}
	
	@Override
	public boolean
	isReadControllerActive(
		int		partition )
	{
		int arp = active_read_partition.get();
		
		if ( arp == partition ){
			
			return( true );
			
		}else{
			
			if ( arp == UNALLOCATED_PARTITION ){
				
				if ( target_read_partition != partition ){
					
					//Debug.out( "Target read partition mismatch " + target_read_partition + "/" + partition );
										
				}else{
				
					return( active_read_partition.compareAndSet( UNALLOCATED_PARTITION, partition ));
				}
				
			}else{
				
				//Debug.out( "Read is no go for " + getString() + ", active=" + active_read_partition + ", target=" + partition );			
			}
			
			long now = SystemTime.getMonotonousTime();
			
			if ( last_read_stall == 0 || ( now - last_read_stall > 60*1000 )){
				
				last_read_stall = now;
				
			}else if ( now - last_read_stall > 30*1000 ){
			
				Debug.out( "Stalled waiting for read controller: active=" + arp + ", target=" + target_read_partition + ", required=" + partition + " - " + getString());
				
				last_read_stall = 0;
				
				close( "Stalled waiting for read controller" );
			}
			
			return( false );
		}
	}
	
	
		// write
	
	@Override
	public void 
	setTargetWriteControllerPartition(
		int partition)
	{
		target_write_partition = partition;
	}
	
	@Override
	public void 
	setWriteControllerInactive()
	{
		active_write_partition.set( UNALLOCATED_PARTITION );
	}
	
	@Override
	public void 
	activeWriteControllerRelease(
		boolean added)
	{
		//System.out.println( "write release: " + added + " for " + getString());
		
		if ( added ){
			
			write_release_count.incrementAndGet();
			
		}else{
			
			if ( write_release_count.decrementAndGet() == 0 ){
				
				active_write_partition.set( UNALLOCATED_PARTITION );
			}
		}
	}
	
	@Override
	public boolean
	isWriteControllerActive(
		int		partition )
	{
		int awp = active_write_partition.get();
		
		if ( awp == partition ){
			
			return( true );
			
		}else{
		
			if ( awp == UNALLOCATED_PARTITION ){
				
				if ( target_write_partition != partition ){
					
					// Debug.out( "Target write partition mismatch " + target_read_partition + "/" + partition );
										
				}else{
				
					return( active_write_partition.compareAndSet( UNALLOCATED_PARTITION, partition ));
				}			
			}else{
				
				// Debug.out( "Write is no go for " + getString() + ", active=" + active_read_partition + ", target=" + partition );			
			}
			
			long now = SystemTime.getMonotonousTime();
						
			if ( last_write_stall == 0 || ( now - last_write_stall > 16*1000 )){
				
				last_write_stall = now;
				
			}else if ( now - last_write_stall > 8*1000 ){
			
				Debug.out( "Stalled waiting for write controller: active=" + awp + ", target=" + target_write_partition + ", required=" + partition + " - " + getString());
				
				last_write_stall = 0;
				
				close( "Stalled waiting for write controller" );
			}
			
			return( false );
		}
	}
}
