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
public interface 
DiskManagerFileInfoSet 
{
	public void
	load(
		int[]		priorities,
		boolean[]	skipped );
	
	/**
	 * Sets a file's storage type to <code>newStorageType</code> only if <code>toChanged</code> is true for the index.
	 *
	 * @param toChange array size must be # of files in set. Only true values will be set to value of <code>setSkipped</code>
	 * @param setSkipped Whether to set file as skipped or unskipped, when toChange[i] is true
	 */
	
	public default boolean[] setStorageTypes(boolean[] toChange, int newStroageType){ return( setStorageTypes( toChange, newStroageType, false )); }
	
		/**
		 * 
		 * @param toChange
		 * @param newStroageType
		 * @param force may discard file state, use with care...
		 * @return
		 */
	public boolean[] setStorageTypes(boolean[] toChange, int newStroageType, boolean force );

	/**
	 * Sets the priorities of all files
	 *
	 * @param newPriorities array size must be # of files in set. Use Integer.MIN_VALUE to signify "no change"
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
