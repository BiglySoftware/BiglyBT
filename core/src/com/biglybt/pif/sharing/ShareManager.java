/*
 * File    : ShareManager.java
 * Created : 30-Dec-2003
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.pif.sharing;

/**
 * @author parg
 *
 */

import java.io.File;
import java.util.Map;

public interface
ShareManager
{
	public static final String	PR_PERSONAL		= "personal";		// "true"/"false"
	public static final String	PR_NETWORKS		= "networks";		// String of nets, comma separated
	public static final String	PR_TAGS			= "tags";			// String of long tag IDs, comma separated
	public static final String	PR_USER_DATA	= "user_data";		// something distinct the 'creator' can recognise
	public static final String	PR_PERSISTENT	= "persistent";		// "true"/"false"

	public void
	initialise()

		throws ShareException;

	public boolean
	isInitialising();

	public ShareResource[]
	getShares();

	public int
	getShareCount();
	
	public ShareResource
	lookupShare(
		byte[]		torrent_hash )
	
		throws ShareException;
		/**
		 * returns null if share not defined
		 * @param file_or_dir
		 * @return
		 */

	public ShareResource
	getShare(
		File	file_or_dir );

	public ShareResourceFile
	addFile(
		File	file )

		throws ShareException, ShareResourceDeletionVetoException;

	public ShareResourceFile
	addFile(
		File				file,
		Map<String,String>	properties )

		throws ShareException, ShareResourceDeletionVetoException;

	public ShareResourceDir
	addDir(
		File	dir )

		throws ShareException, ShareResourceDeletionVetoException;

	public ShareResourceDir
	addDir(
		File				dir,
		Map<String,String>	properties )

		throws ShareException, ShareResourceDeletionVetoException;

	public ShareResourceDirContents
	addDirContents(
		File	dir,
		boolean	recursive )

		throws ShareException, ShareResourceDeletionVetoException;

	public ShareResourceDirContents
	addDirContents(
		File				dir,
		boolean				recursive,
		Map<String,String>	properties )

		throws ShareException, ShareResourceDeletionVetoException;

	/**
	 * adding shares can take a long time due to the torrent creation process. The current
	 * activity can be interrupted by calling this function, in which case the original
	 * activity will fail with a ShareException
	 */

	public void
	cancelOperation();

	public void
	addListener(
		ShareManagerListener	listener );

	public void
	removeListener(
		ShareManagerListener	listener );
}
