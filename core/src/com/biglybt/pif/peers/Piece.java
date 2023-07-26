/*
 * Created on 15 May 2006
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

package com.biglybt.pif.peers;

public interface
Piece
{
	public int
	getIndex();

	public int
	getLength();

	public boolean
	isDone();

	public boolean
	isNeeded();

	public boolean
	isDownloading();

		/**
		 * indicates if this piece is free and available to be allocated for download
		 * not done, needed and not downloading
		 * @return
		 */

	public boolean
	isFullyAllocatable();

		/**
		 * number of requests that are available to be made
		 * @return
		 */

	public int
	getAllocatableRequestCount();

		/**
		 * Reserve this piece for a given peer - no other peer will be asked for the piece
		 * @return
		 */

	public Peer
	getReservedFor();

		/**
		 * Set the peer that will be responsible for downloading the piece
		 * @param peer
		 */

	public void
	setReservedFor(
		Peer	peer );
}
