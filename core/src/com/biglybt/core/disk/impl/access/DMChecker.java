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

import com.biglybt.core.disk.DiskManagerCheckRequest;
import com.biglybt.core.disk.DiskManagerCheckRequestListener;

/**
 * @author parg
 *
 */
public interface
DMChecker
{
	public void
	start();

	public void
	stop();

	public DiskManagerCheckRequest
	createCheckRequest(
		int 	pieceNumber,
		Object	user_data );

	public void
	enqueueCompleteRecheckRequest(
		DiskManagerCheckRequest				request,
		DiskManagerCheckRequestListener 	listener );

	public void
	enqueueCheckRequest(
		DiskManagerCheckRequest			request,
		DiskManagerCheckRequestListener listener );

	public boolean
	hasOutstandingCheckRequestForPiece(
		int		piece_number );

	public int
	getCompleteRecheckStatus();
	
	public boolean
	getRecheckCancelled();
	
	public void
	setCheckingEnabled(
		boolean		enabled );
}
