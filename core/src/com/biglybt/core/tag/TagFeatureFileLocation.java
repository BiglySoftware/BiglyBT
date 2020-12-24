/*
 * Created on May 21, 2013
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

import java.io.File;

public interface
TagFeatureFileLocation
{
	public static final long	FL_NONE			= 0x00;
	public static final long	FL_DATA			= 0x01;
	public static final long	FL_TORRENT		= 0x02;
	public static final long	FL_BOTH			= FL_DATA | FL_TORRENT;
	public static final long	FL_DEFAULT		= FL_DATA;

	public boolean
	getPreventDelete();
	
	public void
	setPreventDelete(
		boolean		b );
	
		// initial location

	public boolean
	supportsTagInitialSaveFolder();

	public File
	getTagInitialSaveFolder();

	public void
	setTagInitialSaveFolder(
		File		folder );

	public long
	getTagInitialSaveOptions();

	public void
	setTagInitialSaveOptions(
		long		opions );

		// move

	public boolean
	supportsTagMoveOnComplete();

	public File
	getTagMoveOnCompleteFolder();

	public void
	setTagMoveOnCompleteFolder(
		File		folder );

	public long
	getTagMoveOnCompleteOptions();

	public void
	setTagMoveOnCompleteOptions(
		long		opions );

		// copy

	public boolean
	supportsTagCopyOnComplete();

	public File
	getTagCopyOnCompleteFolder();

	public void
	setTagCopyOnCompleteFolder(
		File		folder );

	public long
	getTagCopyOnCompleteOptions();

	public void
	setTagCopyOnCompleteOptions(
		long		opions );
	
		// move on remove
	
	public boolean
	supportsTagMoveOnRemove();

	public File
	getTagMoveOnRemoveFolder();

	public void
	setTagMoveOnRemoveFolder(
		File		folder );

	public long
	getTagMoveOnRemoveOptions();

	public void
	setTagMoveOnRemoveOptions(
		long		opions );
	
		// move on assign
		
	public boolean
	supportsTagMoveOnAssign();
	
	public File
	getTagMoveOnAssignFolder();
	
	public void
	setTagMoveOnAssignFolder(
		File		folder );
	
	public long
	getTagMoveOnAssignOptions();
	
	public void
	setTagMoveOnAssignOptions(
		long		opions );
		
}
