/*
 * Created on 05-Jul-2004
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

package com.biglybt.core.ipfilter.impl;

import java.net.Inet4Address;

/**
 * @author parg
 *
 */

import java.util.*;

import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.tracker.protocol.PRHelpers;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.IdentityHashSet;
import com.biglybt.core.util.SystemTime;

public class
IPAddressRangeManagerV4
{
	private static final LogIDs LOGID = LogIDs.CORE;

	protected final Set<IpRangeV4Impl> entries = new IdentityHashSet<>();

	protected long		total_span;

	protected boolean	rebuild_required;
	protected long		last_rebuild_time = -1;

	protected IpRangeV4Impl[] mergedRanges = new IpRangeV4Impl[0];

	protected final AEMonitor	this_mon	= new AEMonitor( "IPAddressRangeManager" );

	protected
	IPAddressRangeManagerV4()
	{
	}

	protected void
	addRange(IpRangeV4Impl range)
	{
		try{
			this_mon.enter();

			// checking for existing (either by entries.contains, or by
			// rebuilding and searching ips) is slow.  Skip check, merge will take
			// care of it

			entries.add( range);

			rebuild_required	= true;

		}finally{

			this_mon.exit();
		}
	}

	protected void removeRange(IpRangeV4Impl range) {
		try{
			this_mon.enter();

			entries.remove( range );

			rebuild_required	= true;

		}finally{

			this_mon.exit();
		}
	}

	public IpRangeV4Impl
	isInRange(
		Inet4Address	ip )
	{
			// optimise for pretty normal case where there are no ranges

		if ( entries.size() == 0 ){

			return( null );
		}
		
		try{
			this_mon.enter();

			long address_long = PRHelpers.addressToInt( ip );

			if ( address_long < 0 ){

				address_long += 0x100000000L;
			}

			IpRangeV4Impl res = isInRange( address_long );

			// LGLogger.log( "IPAddressRangeManager: checking '" + ip + "' against " + entries.size() + "/" + merged_entries.length + " -> " + res );

			return( res );

		}finally{

			this_mon.exit();
		}
	}

	private IpRangeV4Impl
	isInRange(
		long	address_long )
	{
		try{
			this_mon.enter();

			checkRebuild();

			if ( mergedRanges.length == 0 ){

				return( null );
			}

				// assisted binary chop

			int	bottom 	= 0;
			int	top		= mergedRanges.length-1;

			int	current	= -1;

			while( top >= 0 && bottom < mergedRanges.length && bottom <= top){

				current = (bottom+top)/2;

				IpRangeV4Impl	e = mergedRanges[current];

				long	this_start 	= e.getStartIpLong();
				long 	this_end	= e.getMergedEndLong();

				if ( address_long == this_start ){

					break;

				}else if ( address_long > this_start ){

					if ( address_long <= this_end ){

						break;
					}

						// lies to the right of this entry

					bottom	= current + 1;

				}else if ( address_long == this_end ){

					break;

				}else{
					// < this_end

					if ( address_long >= this_start ){

						break;
					}

					top = current - 1;
				}
			}

			if ( top >= 0 && bottom < mergedRanges.length && bottom <= top ){

				IpRangeV4Impl	e = mergedRanges[current];

				if ( address_long <= e.getEndIpLong()){

					return( e );
				}

				IpRangeV4Impl[]	merged = e.getMergedEntries();

				if ( merged == null ){

					Debug.out( "IPAddressRangeManager: inconsistent merged details - no entries" );

					return( null );
				}

				for (int i=0;i<merged.length;i++){

					IpRangeV4Impl	me = merged[i];

					if ( me.getStartIpLong() <= address_long && me.getEndIpLong() >= address_long ){

						return( me );
					}
				}

				Debug.out( "IPAddressRangeManager: inconsistent merged details - entry not found" );
			}

			return( null );

		}finally{

			this_mon.exit();
		}
	}

	private void
	checkRebuild()
	{
		try{
			this_mon.enter();

			if ( rebuild_required ){

					// with substantial numbers of filters (e.g. 80,000) rebuilding
					// is a slow process. Therefore prevent frequent rebuilds at the
					// cost of delaying the effect of the change

				long	now = SystemTime.getMonotonousTime();

					// allow one second per 2000 entries

				if ( last_rebuild_time == -1 || (now - last_rebuild_time)/1000 > entries.size()/2000 ){

					last_rebuild_time	= now;

					rebuild_required	= false;

					rebuild();
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	private void
	rebuild()
	{
		if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "IPAddressRangeManager: rebuilding "
					+ entries.size() + " entries starts"));

		IpRangeV4Impl[]	ents = new IpRangeV4Impl[entries.size()];

		entries.toArray(ents);

		for (int i=0;i<ents.length;i++){

			ents[i].resetMergeInfo();
		}

			// sort based on start address

		Arrays.sort(
			ents,
			new Comparator<IpRangeV4Impl>()
			{
				@Override
				public int
				compare(
					IpRangeV4Impl e1,
					IpRangeV4Impl e2 )
				{
					long diff = e1.getStartIpLong() - e2.getStartIpLong();

					if (diff == 0) {

						diff = e2.getEndIpLong() - e1.getEndIpLong();
					}

					return signum(diff);
				}
			});

			// now merge overlapping ranges

		List me = new ArrayList( ents.length );

		for (int i=0;i<ents.length;i++){

			IpRangeV4Impl	entry = ents[i];

			if ( entry.getMerged()){

				continue;
			}

			me.add( entry );

			int	pos = i+1;

			while( pos < ents.length ){

				long	end_pos = entry.getMergedEndLong();

				IpRangeV4Impl	e2 = ents[pos++];

				if (!e2.getMerged()){

					if ( end_pos >= e2.getStartIpLong()){

						e2.setMerged();

						if ( e2.getEndIpLong() > end_pos ){

							entry.setMergedEnd( e2.getEndIpLong() );

							entry.addMergedEntry( e2 );
						}
					}else{

						break;
					}
				}
			}
		}

		/*
		for (int i=0;i<ents.length;i++){

			entry	e = ents[i];

			System.out.println( Long.toHexString(e.getStart()) + " - " + Long.toHexString(e.getEnd()) + ": " + e.getMerged() + "/" + Long.toHexString(e.getMergedEnd()));
		}
		*/

		mergedRanges = new IpRangeV4Impl[me.size()];

		me.toArray( mergedRanges );

		total_span	= 0;

		for (int i=0;i<mergedRanges.length;i++){

			IpRangeV4Impl	e = mergedRanges[i];

				// span is inclusive

			long	span = ( e.getMergedEndLong() - e.getStartIpLong()) + 1;

			total_span	+= span;
		}
			//	System.out.println( "non_merged = " + merged_entries.length );

		if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "IPAddressRangeManager: rebuilding "
					+ entries.size() + " entries ends"));

	}

	/**
	 * @param diff
	 * @return
	 */
	private int signum(long diff) {
		if (diff > 0) {
			return 1;
		}

		if (diff < 0) {
			return -1;
		}

		return 0;
	}

	public static void
	main(
		String[]	args )
	{
		IPAddressRangeManagerV4 manager = new IPAddressRangeManagerV4();

		/*
		Object[] testBlockIPs1 = {
				new String[] { "1", "3.1.1.1", "3.1.1.2" },
				new String[] { "2", "3.1.1.1", "3.1.1.3" },
				new String[] { "3", "1.1.1.1", "2.2.2.2", "2" },
				new String[] { "4", "0.1.1.1", "2.2.2.2", "3" },
				new String[] { "5", "1.1.1.1", "1.2.2.2" },
				new String[] { "6", "7.7.7.7", "7.7.8.7" },
				new String[] { "7", "8.8.8.8", "8.8.8.8" },
				//new String[] {"8","0.0.0.0", "255.255.255.255"},
				new String[] { "9", "5.5.5.5", "6.6.6.9" },
				new String[] { "10", "6.6.6.6", "7.7.0.0" },
				new String[] { "11", "254.6.6.6", "254.7.0.0" } };

		Object[] testBlockIPs2 = {
				new String[] { "1", "0.0.0.1", "60.0.0.0" },
				new String[] { "2", "60.0.0.2", "119.255.255.255" },
				new String[] { "2a", "60.0.0.2", "119.255.255.255" },
				new String[] { "3", "120.0.0.1", "180.0.0.0" },
				new String[] { "4", "180.0.0.0", "255.255.255.255" }
		};

		Object[] testBlockIPs = testBlockIPs2;

		for (int i = 0; i < testBlockIPs.length; i++) {
			String[] ip = (String[]) testBlockIPs[i];

			if (ip == null)
				continue;

			manager.addRange(new IpRangeImpl(ip[0], ip[1], ip[2], true));
		}

		System.out.println( "inRange -> " + manager.isInRange( "254.6.6.8" ));


		String [] testIPs = { "60.0.0.0", "60.0.0.1", "60.0.0.2", "60.0.0.3",
				"119.255.255.254", "119.255.255.255", "120.0.0.0", "120.0.0.1",
				"120.0.0.2", "179.255.255.255", "180.0.0.0", "180.0.0.1"
		};

		for (int i = 0; i < testIPs.length; i++) {
			String string = testIPs[i];
			System.out.println(string + " InRange? " + manager.isInRange(string));

		}



		System.out.println( "Total span = " + manager.getTotalSpan());
		*/

		Random r = new Random();

		for (int i=0;i<1000000;i++){

			int	ip1 	= r.nextInt( 0x0fffffff );

			int	ip2 	= ip1 + r.nextInt( 255 );

			String	start 	= PRHelpers.intToAddress( ip1 );
			String	end		= PRHelpers.intToAddress( ip2 );

			manager.addRange( new IpRangeV4Impl("test_" + i, start, end, true ));
		}

		/*
		for (int i=0;i<100000;i++){

			int	start 	= (int)(Math.random() * 0xfffff000);
			int	end		= start + (int)(Math.random()*5000);

			manager.addRange( start, end, new Object());
		}
		*/

		int	num 	= 0;
		int	hits	= 0;


		while(true){

			if ( num % 1000 == 0 ){

				System.out.println( num + "/" + hits );

			}

			num++;

			int	ip 	= r.nextInt();

			Object	res = manager.isInRange( ip );

			if ( res != null ){

				hits++;
			}
		}
	}

	protected List
	getEntries()
	{
		try{
			this_mon.enter();
		
			return( new ArrayList<>( entries ));
			
		}finally{

			this_mon.exit();
		}
	}

	protected int
	getEntryCount()
	{
		return( entries.size());
	}
	
	protected void 
	clearAllEntries() 
	{
		try{
			this_mon.enter();

			entries.clear();

			rebuild_required	= true;

		}finally{

			this_mon.exit();
		}
	}
}
