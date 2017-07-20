/*
 * Created on 27-Feb-2006
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

package com.biglybt.core.download;

public interface
DownloadManagerInitialisationAdapter
{
	public static final int ACT_NONE				= 0x00000000;
	public static final int ACT_ASSIGNS_TAGS		= 0x00000001;
	public static final int ACT_PROCESSES_TAGS		= 0x00000002;

	public void
	initialised(
		DownloadManager		manager,
		boolean				for_seeding );

		/**
		 * Unfortuately order can be important when firing off initialisation adapters, in particular if one listener
		 * assigns tags to a download it needs to do this before any other listeners that might process a download's tags
		 * @return
		 */

	public int
	getActions();
}
