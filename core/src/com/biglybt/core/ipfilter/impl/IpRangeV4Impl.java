/*
 * File    : IpRange.java
 * Created : 8 oct. 2003 13:02:23
 * By      : Olivier
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.ipfilter.impl;

import java.net.UnknownHostException;

import com.biglybt.core.ipfilter.IpFilterManagerFactory;
import com.biglybt.core.ipfilter.IpRange;
import com.biglybt.core.tracker.protocol.PRHelpers;

/**
 * @author Olivier
 *
 */

public class 
IpRangeV4Impl 
	extends IpRangeImpl
{
	private int ipStart;

	private int ipEnd;

	// Merging stuff
	private final static byte FLAG_MERGED = 0x4;

	private int merged_end;

	private IpRangeV4Impl[] my_merged_entries;

	public IpRangeV4Impl(String _description, String _startIp, String _endIp,
			boolean _sessionOnly) {
		if (_sessionOnly) {
			flags = FLAG_SESSION_ONLY;
		}

		if (_startIp == null || _endIp == null) {

			throw (new RuntimeException(
					"Invalid start/end values - null not supported"));
		}

		try {
			if ( _startIp.contains( ":" )){
				flags |= FLAG_INVALID_START;
			}else{
				ipStart = PRHelpers.addressToInt(_startIp);
			}
		} catch (UnknownHostException e) {
			flags |= FLAG_INVALID_START;
		}
		try {
			if ( _endIp.contains( ":" )){
				flags |= FLAG_INVALID_END;
			}else{
				ipEnd = PRHelpers.addressToInt(_endIp);
			}
		} catch (UnknownHostException e) {
			flags |= FLAG_INVALID_END;
		}

		if (_description.length() > 0) {
			setDescription(_description);
		}

		checkValid();
	}

	public IpRangeV4Impl(String _description, int _startIp, int _endIp,
			boolean _sessionOnly) {
		if (_sessionOnly) {
			flags = FLAG_SESSION_ONLY;
		}

		ipStart = _startIp;
		ipEnd = _endIp;

		if (_description.length() > 0) {
			setDescription(_description);
		}

		checkValid();
	}

	protected final boolean
	isV4()
	{
		return( true );
	}

	@Override
	public boolean isValid() {
		if ((flags & FLAG_INVALID) > 0) {
			return false;
		}

		long start_address = ipStart;
		long end_address = ipEnd;

		if (start_address < 0) {

			start_address += 0x100000000L;
		}
		if (end_address < 0) {

			end_address += 0x100000000L;
		}

		return (end_address >= start_address);
	}

	@Override
	public boolean isInRange(String ipAddress) {
		if (!isValid()) {
			return false;
		}

		try {
			long int_address = PRHelpers.addressToInt(ipAddress);

			if (int_address < 0) {

				int_address += 0x100000000L;
			}

  		long start_address = ipStart;
  		long end_address = ipEnd;

			if (start_address < 0) {

				start_address += 0x100000000L;
			}
			if (end_address < 0) {

				end_address += 0x100000000L;
			}

			return (int_address >= start_address && int_address <= end_address);

		} catch (UnknownHostException e) {

			return (false);
		}
	}

	@Override
	public String getStartIp() {
		return (flags & FLAG_INVALID_START) > 0 ? ""
				: PRHelpers.intToAddress(ipStart);
	}
	
	protected long getStartIpLong() {
		if ((flags & FLAG_INVALID_START) > 0) {
			return -1;
		}

		long val = ipStart;

		if (val < 0) {

			val += 0x100000000L;
		}

		return (val);
	}

	@Override
	public void setStartIp(String str) {
		if (str == null) {
			throw (new RuntimeException("Invalid start value - null not supported"));
		}

		if (str.equals(getStartIp())) {
			return;
		}

		flags &= ~FLAG_INVALID_START;
		try {
			if ( str.contains( ":" )){
				flags |= FLAG_INVALID_START;
			}else{
				ipStart = PRHelpers.addressToInt(str);
			}
		} catch (UnknownHostException e) {
			flags |= FLAG_INVALID_START;
		}

		if ((flags & FLAG_INVALID) == 0) {
			checkValid();
		}
	}

	@Override
	public String getEndIp() {
		return (flags & FLAG_INVALID_END) > 0 ? "" : PRHelpers.intToAddress(ipEnd);
	}
	
	protected long getEndIpLong() {
		if ((flags & FLAG_INVALID_END) > 0) {
			return -1;
		}

		long val = ipEnd;

		if (val < 0) {
			val += 0x100000000L;
		}

		return (val);
	}

	@Override
	public void setEndIp(String str)

	{
		if (str == null) {
			throw (new RuntimeException("Invalid end value - null not supported"));
		}

		if (str.equals(getEndIp())) {
			return;
		}

		flags &= ~FLAG_INVALID_END;
		try {
			if ( str.contains( ":" )){
				flags |= FLAG_INVALID_END;
			}else{
				ipEnd = PRHelpers.addressToInt(str);
			}
		} catch (UnknownHostException e) {
			flags |= FLAG_INVALID_END;
		}

		if ((flags & FLAG_INVALID) == 0) {
			checkValid();
		}
	}

	@Override
	public int 
	compareStartIpTo(
			IpRange other) 
	{
		if ( other instanceof IpRangeV4Impl ){
			
			long l = getStartIpLong() - ((IpRangeV4Impl) other).getStartIpLong();
	
			if (l < 0) {
				return (-1);
			} else if (l > 0) {
				return (1);
			} else {
				return (0);
			}
		}else{
			return( -1 );
		}
	}

	@Override
	public int compareEndIpTo(IpRange other) {
		if ( other instanceof IpRangeV4Impl ){
			long l = getEndIpLong() - ((IpRangeV4Impl) other).getEndIpLong();
	
			if (l < 0) {
				return (-1);
			} else if (l > 0) {
				return (1);
			}
			return (0);
		}else{
			return( -1 );
		}
	}

	protected long getMergedEndLong() {
		return (merged_end < 0 ? (merged_end + 0x100000000L) : merged_end);
	}

	protected IpRangeV4Impl[] getMergedEntries() {
		return (my_merged_entries);
	}

	protected void resetMergeInfo() {
		flags &= ~FLAG_MERGED;

		if ((flags & FLAG_INVALID_END) == 0) {
			merged_end = ipEnd;
		}
	}

	protected boolean getMerged() {
		return (flags & FLAG_MERGED) != 0;
	}

	protected void setMerged() {
		flags |= FLAG_MERGED;
	}

	protected void setMergedEnd(long endIpLong) {
		merged_end = (int) (endIpLong >= 0x100000000L ? endIpLong - 0x100000000L
				: endIpLong);
	}

	protected void addMergedEntry(IpRangeV4Impl e2) {
		if (my_merged_entries == null) {

			my_merged_entries = new IpRangeV4Impl[] { e2 };

		} else {

			IpRangeV4Impl[] x = new IpRangeV4Impl[my_merged_entries.length + 1];

			System.arraycopy(my_merged_entries, 0, x, 0, my_merged_entries.length);

			x[x.length - 1] = e2;

			my_merged_entries = x;
		}
	}
	
	@Override
	public String 
	getStringSlow()
	{
		return( toString());
	}
}
