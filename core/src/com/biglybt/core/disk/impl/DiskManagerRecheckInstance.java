/*
 * Created on 19-Dec-2005
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

package com.biglybt.core.disk.impl;

public class
DiskManagerRecheckInstance
{
	private final DiskManagerRecheckScheduler	scheduler;
	private final long							metric;
	private final int							piece_length;
	private final boolean						low_priority;

	protected
	DiskManagerRecheckInstance(
		DiskManagerRecheckScheduler	_scheduler,
		long						_size,
		int							_piece_length,
		boolean						_low_priority )
	{
		scheduler		= _scheduler;
		metric			= (_low_priority?0:0x7000000000000000L) + _size;
		piece_length	= _piece_length;
		low_priority	= _low_priority;
	}

	protected long
	getMetric()
	{
		return( metric );
	}

	public int
	getPieceConcurrency()
	{
		return( scheduler.getPieceConcurrency( this ));
	}
			
	protected int
	getPieceLength()
	{
		return( piece_length );
	}

	protected boolean
	isLowPriority()
	{
		return( low_priority );
	}

	public boolean
	getPermission()
	{
		return( scheduler.getPermission( this ));
	}

	public void
	unregister()
	{
		scheduler.unregister( this );
	}
}
