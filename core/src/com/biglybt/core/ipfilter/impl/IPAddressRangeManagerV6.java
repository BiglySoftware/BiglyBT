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

public class 
IPAddressRangeManagerV6
{
	private Set<IpRangeV6Impl>	ranges = new IdentityHashSet<>();
	
	private volatile int	range_count;
	
	protected void
	addRange(
		IpRangeV6Impl		range )
	{
		synchronized( ranges ){
			
			if ( ranges.add( range )){
				
				range_count = ranges.size();
			}
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
		}
	}
	
	protected IpRange
	isInRange(
		Inet6Address	ia )
	{
		if ( range_count == 0 ){
			
			return( null );
		}
		
		byte[] bytes = ia.getAddress();
		
		synchronized( ranges ){
			
			for ( IpRangeV6Impl r: ranges ){
				
				if ( r.isInRange( bytes )){
					
					return( r );
				}
			}
		}
		
		return( null );
	}
}
