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

import com.biglybt.core.ipfilter.IpFilterManagerFactory;
import com.biglybt.core.ipfilter.IpRange;

public abstract class 
IpRangeImpl
	implements IpRange
{
	protected final static byte FLAG_SESSION_ONLY = 0x1;

	protected final static byte FLAG_ADDED_TO_RANGE_LIST = 0x2;

	protected final static byte FLAG_INVALID_START = 0x8;

	protected final static byte FLAG_INVALID_END = 0x10;

	protected final static byte FLAG_INVALID = FLAG_INVALID_START | FLAG_INVALID_END;

	
	protected byte flags;

	private Object descRef = null;

	protected void setAddedToRangeList(boolean b) {
		if (b) {
			flags |= FLAG_ADDED_TO_RANGE_LIST;
		} else {
			flags &= ~FLAG_ADDED_TO_RANGE_LIST;
		}
	}

	public boolean getAddedToRangeList() {
		return (flags & FLAG_ADDED_TO_RANGE_LIST) != 0;
	}
	
	protected abstract boolean
	isV4();

	@Override
	public String getDescription() {
		return new String(IpFilterManagerFactory.getSingleton().getDescription(
				descRef));
	}

	@Override
	public void setDescription(String str) {
		descRef = IpFilterManagerFactory.getSingleton().addDescription(this,
				str.getBytes());
	}

	@Override
	public int compareDescription(IpRange other) {
		return getDescription().compareTo(other.getDescription());
	}

	protected Object getDescRef() {
		return descRef;
	}

	protected void setDescRef(Object descRef) {
		this.descRef = descRef;
	}

	public String toString() {
		return getDescription() + " : " + getStartIp() + " - " + getEndIp();
	}

	@Override
	public boolean isSessionOnly() {
		return (flags & FLAG_SESSION_ONLY) != 0;
	}

	@Override
	public void setSessionOnly(boolean _sessionOnly) {
		if (_sessionOnly) {
			flags |= FLAG_SESSION_ONLY;
		} else {
			flags &= ~FLAG_SESSION_ONLY;
		}
	}

	@Override
	public void checkValid() {
		((IpFilterImpl) IpFilterImpl.getInstance()).setValidOrNot(this, isValid());
	}
}
