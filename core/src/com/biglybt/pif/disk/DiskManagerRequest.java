/*
 * Created on 29-Mar-2006
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

package com.biglybt.pif.disk;

public interface
DiskManagerRequest
{
	public static final int	REQUEST_READ	= 1;

	public void
	setType(
		int			type );

	public void
	setOffset(
		long		offset );

	public void
	setLength(
		long		length );

		/**
		 * Get the number of bytes available for immediate reading from the request given its current
		 * position. If this information is not known (download not running) then -1 is returned
		 * @return
		 */

	public long
	getAvailableBytes();

		/**
		 * Get the number of bytes remaining to be read for the request
		 * @return
		 */

	public long
	getRemaining();

	public void
	run();

	public void
	cancel();

		/**
		 * Beware that invoking this method signifies that the media is being streamed and therefore may undergo transformations such as MOOV atom relocation in mp4s
		 * @param agent
		 */

	public void
	setUserAgent(
		String		agent );

	public void
	setMaximumReadChunkSize(
		int			size );

	public void
	addListener(
		DiskManagerListener	listener );

	public void
	removeListener(
		DiskManagerListener	listener );
}
