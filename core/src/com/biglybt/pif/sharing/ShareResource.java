/*
 * File    : ShareResource.java
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

import java.util.Map;

import com.biglybt.pif.torrent.TorrentAttribute;

/**
 * @author parg
 *
 */
public interface
ShareResource
{
	public static final int	ST_FILE			= 1;
	public static final int	ST_DIR			= 2;
	public static final int	ST_DIR_CONTENTS	= 3;

	public int
	getType();

	public String
	getName();

	public void
	delete()

		throws ShareException, ShareResourceDeletionVetoException;

	public void
	delete(
		boolean		force )

		throws ShareException, ShareResourceDeletionVetoException;

	public void
	setAttribute(
		TorrentAttribute		attribute,
		String					value );

		/**
		 * @param attribute
		 * @return	null if no value defined
		 */

	public String
	getAttribute(
		TorrentAttribute		attribute );

		/**
		 * get the defined attributes for this resource
		 * @return
		 */

	public TorrentAttribute[]
	getAttributes();

	public Map<String,String>
	getProperties();

	public void
	setProperties(
		Map<String,String>	props,
		boolean				internal );
	
	public boolean
	isPersistent();

	public boolean
	canBeDeleted()

		throws ShareResourceDeletionVetoException;

	public boolean
	isDeleted();
	
	public ShareResourceDirContents
	getParent();

	public void
	addChangeListener(
		ShareResourceListener	l );

	public void
	removeChangeListener(
		ShareResourceListener	l );

	public void
	addDeletionListener(
		ShareResourceWillBeDeletedListener	l );

	public void
	removeDeletionListener(
		ShareResourceWillBeDeletedListener	l );
}
