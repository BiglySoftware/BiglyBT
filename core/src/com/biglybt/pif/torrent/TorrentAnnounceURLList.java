/*
 * File    : TorrentAnnounceURLList.java
 * Created : 03-Mar-2004
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

package com.biglybt.pif.torrent;

/**
 * @author parg
 *
 */

import java.net.URL;

public interface
TorrentAnnounceURLList
{
	public TorrentAnnounceURLListSet[]
	getSets();

	public void
	setSets(
		TorrentAnnounceURLListSet[]		sets );

		/**
		 * create a new set. This is NOT added to the list, you have to manually add it afterwards
		 * @param urls
		 * @return
		 */

	public TorrentAnnounceURLListSet
	create(
		URL[]		urls );

		/**
		 * Adds a set to the torrent at the end of the list. If the torrent currently has NO announcelist
		 * entries then the existing "announce-url" is also added to the set as the first entry. To avoid
		 * this behaviour manipulate the sets yourself and use setSets
		 * Duplicate set entries are ignored
		 * @param urls
		 */

	public void
	addSet(
		URL[]		urls );

	/**
	 * Adds a set to the torrent at the front of the list. If the torrent currently has NO announcelist
	 * entries then the existing "announce-url" is also added to the set as the first entry. To avoid
	 * this behaviour manipulate the sets yourself and use setSets
	 * Duplicate set entries are ignored
	 * @param urls
	 */

	public void
	insertSetAtFront(
		URL[]		urls );
}
