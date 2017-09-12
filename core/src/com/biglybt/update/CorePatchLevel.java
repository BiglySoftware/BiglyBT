/*
 * Created on 26-May-2004
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

package com.biglybt.update;

/**
 * @author parg
 *
 */

public class
CorePatchLevel
{
		// everytime a patch is issued this number must go up by one
		// The resulting patch file must include this file and be named
		// BiglyBT_<mainline_build>_P<patch_level>.pat
		//  e.g. BiglyBT_2.0.8.5_P1.pat
		// dont' reset to 1 on a new mainline, keep going up!

		// Level 1: 2302 - fix for DHT version propagation problem
		// Level 2: 2306 - fix for SF mirror parsing problem for core updates
		// Level 3: 4208 - fix for OSX Snow Leopard torrent opening problem
		// Level 4: 4208 - fix for OSX Snow Leopard torrent opening problem and UI hang on torrent with Tor URL add

	public static final int	PATCH_LEVEL	= 4;

	public static int
	getCurrentPatchLevel()
	{
		return( PATCH_LEVEL );
	}
}
