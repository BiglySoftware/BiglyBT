/*
 * Created on 27 Jul 2006
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

package com.biglybt.core;

import java.util.concurrent.atomic.AtomicBoolean;

public interface
CoreOperation
{
	public static final int	OP_FILE_MOVE				= 2;
	public static final int	OP_PROGRESS					= 3;
	public static final int	OP_DOWNLOAD_EXPORT			= 4;
	public static final int	OP_DOWNLOAD_ALLOCATION		= 5;
	public static final int	OP_DOWNLOAD_CHECKING		= 6;
	public static final int	OP_DOWNLOAD_COPY			= 7;

	public static final int[] OP_SORT_ORDER = { -1, -1, 3, 0, 4, 1, 2, 5 };
	
	public int
	getOperationType();

	public CoreOperationTask
	getTask();
	
	public boolean
	isRemoved();
	
	public void
	setRemoved();
	
	public static abstract class
	CoreOperationAdapter
		implements CoreOperation
	{
		private AtomicBoolean	removed = new AtomicBoolean();
		
		public boolean
		isRemoved()
		{
			return( removed.get());
		}
		
		public void
		setRemoved()
		{
			removed.set( true );
		}
	}
}
