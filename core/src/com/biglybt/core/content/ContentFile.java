/*
 * Created on Feb 11, 2009
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


package com.biglybt.core.content;

import com.biglybt.pif.disk.DiskManagerFileInfo;

public interface
ContentFile
{
	public static final String	PT_TITLE			= "title";			// String
	public static final String	PT_CREATOR			= "creator";		// String
	public static final String	PT_DATE				= "date";			// Long, millis
	public static final String	PT_DURATION			= "duration";		// Long, millis
	public static final String	PT_VIDEO_WIDTH		= "video_width";	// Long
	public static final String	PT_VIDEO_HEIGHT		= "video_height";	// Long
	public static final String	PT_CATEGORIES		= "cats";			// String[]
	public static final String	PT_TAGS				= "tags";			// String[]
	public static final String	PT_PERCENT_DONE		= "percent";		// Long, thousandths
	public static final String	PT_ETA				= "eta";			// Long, seconds

	public DiskManagerFileInfo
	getFile();

	public Object
	getProperty(
		String		name );
}
