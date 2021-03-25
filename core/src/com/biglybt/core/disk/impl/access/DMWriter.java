/*
 * Created on 31-Jul-2004
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

package com.biglybt.core.disk.impl.access;

import com.biglybt.core.disk.DiskManagerException;
import com.biglybt.core.disk.DiskManagerWriteRequest;
import com.biglybt.core.disk.DiskManagerWriteRequestListener;
import com.biglybt.core.disk.impl.DiskManagerAllocationScheduler;
import com.biglybt.core.disk.impl.DiskManagerFileInfoImpl;
import com.biglybt.core.util.DirectByteBuffer;

/**
 * @author parg
 *
 */
public interface
DMWriter
{
	public void
	start();

	public void
	stop();

	public boolean
	zeroFile(
		DiskManagerAllocationScheduler.AllocationInstance	alloc_inst,
		DiskManagerFileInfoImpl 							file,
		long												start_from,
		long 												overall_length,
		ProgressListener									listener )
		
		throws DiskManagerException;

	public DiskManagerWriteRequest
	createWriteRequest(
		int 							pieceNumber,
		int 							offset,
		DirectByteBuffer 				data,
		Object 							user_data );

	public void
	writeBlock(
		DiskManagerWriteRequest			request,
		DiskManagerWriteRequestListener	listener );

	public boolean
	hasOutstandingWriteRequestForPiece(
		int		piece_number );
	
		/**
		 * 4 entries, total write-ops, total write-bytes, outstanding write-ops, outstanding write-bytes
		 * @return
		 */
	
	public long[]
	getStats();

	public long
	getLatency();
	
	public interface
	ProgressListener
	{
		public void
		allocated(
			long		bytes );
	}
}
