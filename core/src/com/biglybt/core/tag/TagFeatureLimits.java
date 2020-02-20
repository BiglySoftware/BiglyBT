/*
 * Created on Nov 6, 2015
 * Created by Paul Gardner
 *
 * Copyright 2015 Azureus Software, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.core.tag;

public interface
TagFeatureLimits
{
	public static final int RS_NONE						= 0;
	public static final int RS_ARCHIVE					= 1;
	public static final int RS_REMOVE_FROM_LIBRARY		= 2;
	public static final int RS_DELETE_FROM_COMPUTER		= 3;
	public static final int RS_MOVE_TO_OLD_TAG			= 4;

	public static final int RS_DEFAULT		= RS_NONE;

	public static final int OP_ADDED_TO_VUZE			= 0;
	public static final int OP_ADED_TO_TAG				= 1;

	public static final int OP_DEFAULT					= OP_ADDED_TO_VUZE;

	public int
	getMaximumTaggables();

	public void
	setMaximumTaggables(
		int		max );

	/**
	 * @return {@link #RS_NONE}, {@link #RS_ARCHIVE}, {@link #RS_REMOVE_FROM_LIBRARY}, {@link #RS_DELETE_FROM_COMPUTER}, {@link #RS_MOVE_TO_OLD_TAG}
	 */
	public int
	getRemovalStrategy();

	/**
	 * @param id {@link #RS_NONE}, {@link #RS_ARCHIVE}, {@link #RS_REMOVE_FROM_LIBRARY}, {@link #RS_DELETE_FROM_COMPUTER}, {@link #RS_MOVE_TO_OLD_TAG}
	 */
	public void
	setRemovalStrategy(
		int		id );

	public int
	getOrdering();

	public void
	setOrdering(
		int		id );
}
