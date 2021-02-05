/*
 * Created on 02-Dec-2005
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

package com.biglybt.core.diskmanager.access;

public interface
DiskAccessRequestListener
{
	public void
	requestQueued(
		DiskAccessRequest	request );

	public void
	requestComplete(
		DiskAccessRequest	request );

	public void
	requestCancelled(
		DiskAccessRequest	request );

	public void
	requestFailed(
		DiskAccessRequest	request,
		Throwable			cause );

	public int
	getPriority();
	
	public Object
	getUserData();

		/**
		 * Called to indicate that an actual request operation occurred. If this request has
		 * been aggregated with others then the byted reported will be for the contiguous
		 * region and subsequent aggregated requests will be reported with 0 bytes
		 * @param bytes
		 */

	public void
	requestExecuted(
		long	bytes );
}
