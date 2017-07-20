/*
 * Created on Jan 21, 2010
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.core.pairing;

public interface
PairingTest
{
	public static final int	OT_PENDING				= 0;	// waiting to start
	public static final int	OT_SUCCESS				= 1;	// yay
	public static final int	OT_FAILED				= 2;	// server did its stuff, couldn't connect
	public static final int	OT_SERVER_UNAVAILABLE	= 3;	// server not running
	public static final int	OT_SERVER_OVERLOADED	= 4;	// server too busy
	public static final int	OT_SERVER_FAILED		= 5;	// server failed (e.g. db down)
	public static final int	OT_CANCELLED			= 6;	// you cancelled the test

	public int
	getOutcome();

	public String
	getErrorMessage();

	public void
	cancel();
}
