/*
 * Created on 12 May 2006
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

package com.biglybt.core.peermanager.piecepicker;

public interface
PieceRTAProvider
{
	public long[]
	updateRTAs(
		PiecePicker		picker );

	public long
	getStartTime();

	public long
	getStartPosition();

	public long
	getCurrentPosition();

	public long
	getBlockingPosition();

		/**
		 * Sets an external view of how much buffer is being maintained by an external source. This
		 * reduces piece urgency and therefore reduces discard.
		 */

	public void
	setBufferMillis(
		long	millis,
		long	delay_millis );

		/**
		 * Returns the user-agent associated with this RTA activity, if known
		 * @return
		 */

	public String
	getUserAgent();
}
