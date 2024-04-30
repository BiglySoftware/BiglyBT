/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.core.tag;

import java.io.File;
import java.util.List;

public interface 
TagGroup
{
	public String
	getName();
	
	public void
	setName(
		String	name );
	
	public boolean
	isExclusive();
	
	public void
	setExclusive(
		boolean		b );
	
	public void
	setRootMoveOnAssignLocation(
		File		loc );
	
	public File
	getRootMoveOnAssignLocation();
	
	public void
	setColor(
		int[]		rgb );

	public int[]
	getColor();
	
	public TagType
	getTagType();
	
	public List<Tag>
	getTags();
	
	public void
	addListener(
		TagGroupListener	l,
		boolean				fire_for_existing );
	
	public void
	removeListener(
		TagGroupListener	l );
}
