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

package com.biglybt.core.ipfilter.impl;

import java.net.Inet6Address;
import java.util.*;

import com.biglybt.core.ipfilter.IpRange;
import com.biglybt.core.util.IdentityHashSet;
import com.biglybt.core.util.SystemTime;

public class 
IPAddressRangeManagerV6
{
	private Set<IpRangeV6Impl>	ranges = new IdentityHashSet<>();
	
	private volatile int	range_count;
	
	private IpRangeV6Impl[]	sorted_ranges;
	
	protected boolean	rebuild_required;
	protected long		last_rebuild_time	= -1;
	
	
	private Comparator<IpRangeV6Impl> range_comparator = 
		new Comparator<IpRangeV6Impl>(){
		@Override
			public int compare(IpRangeV6Impl o1, IpRangeV6Impl o2){
				return( o1.compareStartIpTo( o2 ));
			}
		};
	
	protected void
	addRange(
		IpRangeV6Impl		range )
	{
		synchronized( ranges ){
			
			if ( ranges.add( range )){
				
				range_count = ranges.size();
			}
			
			rebuild_required = true;
		}
	}
	
	protected void
	removeRange(
		IpRangeV6Impl		range )
	{
		synchronized( ranges ){
			
			if ( ranges.remove( range )){
				
				range_count = ranges.size();
			}
						
			rebuild_required = true;
		}
	}
	
	protected List
	getEntries()
	{
		synchronized( ranges ){
		
			return( new ArrayList<>( ranges ));
		}
	}
	
	protected int
	getEntryCount()
	{
		return( range_count );
	}
	
	protected void
	clearAllEntries()
	{
		synchronized( ranges ){
			
			ranges.clear();
						
			rebuild_required	= true;
		}
	}
	
	private void
	rebuild()
	{
		synchronized( ranges ){
			
			if ( ranges.isEmpty()){
				
				sorted_ranges = null;
				
			}else{
					
				sorted_ranges = new IpRangeV6Impl[ranges.size()];
								
				ranges.toArray( sorted_ranges );
				
				Arrays.sort( sorted_ranges, range_comparator );

					// remove prefix overlaps
				
				Set<IpRangeV6Impl>	temp = new HashSet<>( ranges );
				
				for ( int i=0;i<sorted_ranges.length-1;i++){
					
					IpRangeV6Impl r1 = sorted_ranges[i];
					
					for ( int j=i+1;j<sorted_ranges.length;j++){
						
						IpRangeV6Impl r2 = sorted_ranges[j];
						
						if ( r1.isInRange( r2.getStartPrefix())){
								
							// System.out.println( "Dup: " + r2 );
							
							temp.remove( r2 );
							
							i++;
							
						}else{
							
							break;
						}
					}
				}
				
				if ( temp.size() < ranges.size()){
					
					sorted_ranges = new IpRangeV6Impl[temp.size()];
					
					temp.toArray( sorted_ranges );
										
					Arrays.sort( sorted_ranges, range_comparator );
				}
			}
		}
	}
		
	protected IpRange
	isInRange(
		Inet6Address	ia )
	{
		if ( range_count == 0 ){

			return( null );
		}

		IpRangeV6Impl ia_range = new IpRangeV6Impl( "", ia, true );

		synchronized( ranges ){

			if ( rebuild_required ){

				long	now = SystemTime.getMonotonousTime();

				if ( last_rebuild_time == -1 || (now - last_rebuild_time)/1000 > range_count/2000 ){

					last_rebuild_time	= now;

					rebuild_required	= false;

					rebuild();
				}
			}
			
			int res = Arrays.binarySearch( sorted_ranges, ia_range, range_comparator );
			
			if ( res >= 0 ){
				
					// exact match
				
				return( sorted_ranges[res] );
				
			}else{
			
				int prev = (-res) - 2;
				
				if ( prev >= 0 ){
						
					IpRangeV6Impl range = sorted_ranges[ prev ];
					
					if ( range.isInRange( ia.getAddress())){
						
						return( range );
					}
				}
			}
		}

		//System.out.println( "No match for " + ia );
		
		return( null );
	}
}
