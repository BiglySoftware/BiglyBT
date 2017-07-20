/*
 * Created on Mar 20, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.core.tag;

public interface
TagFeature
{
	public static final int TF_NONE				= 0x00000000;
	public static final int TF_RATE_LIMIT		= 0x00000001;
	public static final int TF_RSS_FEED			= 0x00000002;
	public static final int TF_RUN_STATE		= 0x00000004;
	public static final int TF_XCODE			= 0x00000008;
	public static final int TF_FILE_LOCATION	= 0x00000010;
	public static final int TF_PROPERTIES		= 0x00000020;
	public static final int TF_EXEC_ON_ASSIGN	= 0x00000040;
	public static final int TF_LIMITS			= 0x00000080;
	public static final int TF_NOTIFICATIONS	= 0x00000100;

	public Tag
	getTag();

}
