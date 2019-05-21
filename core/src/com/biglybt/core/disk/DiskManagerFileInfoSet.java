/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */
package com.biglybt.core.disk;



/**
 * @author Aaron Grunthal
 * @create 01.05.2008
 */
public interface DiskManagerFileInfoSet {
	/**
	 * Sets a file's storage type to <code>newStorageType</code> only if <code>toChanged</code> is true for the index.
	 *
	 * @param toChange array size must be # of files in set. Only true values will be set to value of <code>setSkipped</code>
	 * @param setSkipped Whether to set file as skipped or unskipped, when toChange[i] is true
	 */
	public boolean[] setStorageTypes(boolean[] toChange, int newStroageType);

	/**
	 * Sets the priorities of all files
	 *
	 * @param newPriorities array size must be # of files in set
	 */
	public void setPriority(int[] newPriorities);

	/**
	 * Sets a file to skipped status to <code>setSkipped</code> if <code>toChanged</code> is true for the index.
	 *
	 * @param toChange array size must be # of files in set. Only true values will be set to value of <code>setSkipped</code>
	 * @param setSkipped Whether to set file as skipped or unskipped, when toChange[i] is true
	 */
	public void setSkipped(boolean[] toChange, boolean setSkipped);
	public DiskManagerFileInfo[] getFiles();
	public int nbFiles();
}
