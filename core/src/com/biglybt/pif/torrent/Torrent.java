/*
 * File    : Torrent.java
 * Created : 08-Dec-2003
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

import java.io.File;
import java.net.URL;
import java.util.Map;

public interface
Torrent
{
	public String
	getName();

	public URL
	getAnnounceURL();

	public void
	setAnnounceURL(
		URL		url );

		/**
		 * get the announce list for multi-tracker torrents. Will always be present but
		 * may contain 0 sets which means that this is not a multi-tracker torrent
		 * @return
		 */

	public TorrentAnnounceURLList
	getAnnounceURLList();

		/**
		 * v1 torrent hash or truncated v2 hash if v2 only 
		 * @return
		 */
	
	public byte[]
	getHash();

		/**
		 * 
		 * @return v2 torrent hash if hybrid or v2 only torrent
		 */
	
	public default byte[]
	getFullHash(
		int	type )
	{
		return( null );
	}
	
	/**
	 * If size is 0 then this is an "external" torrent and we only know its hash (and name
	 * constructed from hash). e.g. we don't know file details
	 * @return
	 */

	public long
	getSize();

	public String
	getComment();

	public void
	setComment(
		String		comment );

	/**
	 * UNIX epoch format in seconds
	 */
	public long
	getCreationDate();

	public String
	getCreatedBy();

	public long
	getPieceSize();

	public long
	getPieceCount();

	public byte[][]
    getPieces();

	public TorrentFile[]
	getFiles();

	public String
	getEncoding();

	public void
	setEncoding(
		String		encoding)

		throws TorrentEncodingException;

	public void
	setDefaultEncoding()

		throws TorrentEncodingException;

		/**
		 * Access to top-level properties in the torrent
		 * @param name
		 * @return
		 */

	public Object
	getAdditionalProperty(
		String		name );

		/**
		 * Removal all non-standard properties (excluding plugin-properties below)
		 * @return
		 */

	public Torrent
	removeAdditionalProperties();

		/**
		 * Set a property specific to this plugin
		 * @param name
		 * @param value
		 */

	public void
	setPluginStringProperty(
		String		name,
		String		value );

		/**
		 * Get a property specific to this plugin
		 * @param name
		 * @return
		 */

	public String
	getPluginStringProperty(
		String		name );

		/**
		 * Sets a map property in the torrent, retrievable via getMapProperty
		 * @param name	should be unique across plugins (i.e. prefix it with something unique)
		 * @param value	bencodable Map value
		 */

	public void
	setMapProperty(
		String		name,
		Map			value );

		/**
		 * Gets a previously set map value
		 * @see setMapProperty
		 * @param name	should be unique across plugins (i.e. prefix it with something unique)
		 * @return
		 */

	public Map
	getMapProperty(
		String		name );

		/**
		 * A decentralised torrent uses the DHT only as a "tracker"
		 * @return
		 */

	public boolean
	isDecentralised();

		/**
		 * Decentralised backup permits the DHT to be used as a tracker when the
		 * "real" tracker is unavailable
		 * @return
		 */

	public boolean
	isDecentralisedBackupEnabled();

		/**
		 * By default torrents with OK trackers are not tracked in the DHT. This allows a specific
		 * torrent to be marked so that it will be
		 * @param requested
		 */

	public void
	setDecentralisedBackupRequested(
		boolean	requested );

	public boolean
	isDecentralisedBackupRequested();

		/**
		 * A private torrent is either explicitly private via info/private or
		 * has decentralised backup disabled and peer exchange disabled
		 * @return
		 */

	public boolean
	isPrivate();

	public void
	setPrivate(
		boolean	priv );

		/**
		 * @since 2501
		 * @return
		 */

	public boolean
	wasCreatedByUs();

		/**
		 * Gets the magnet URI for the torrent - throws exception if not available
		 * @return
		 */

	public URL
	getMagnetURI()

		throws TorrentException;

	public Map
	writeToMap()

		throws TorrentException;

	public void
	writeToFile(
		File		file )

		throws TorrentException;

	public byte[]
	writeToBEncodedData()

		throws TorrentException;

		/**
		 * Saves the torrent to its persistent location
		 * @throws TorrentException
		 */

	public void
	save()

		throws TorrentException;

		/**
		 * sets the torrent complete - i.e. ready for seeding. Doing this avoids
		 * a recheck on torrent addition
		 * @param data_dir
		 * @throws TorrentException
		 */

	public void
	setComplete(
		File		data_dir )

		throws TorrentException;

	public boolean
	isComplete();

	/**
	 * Returns <tt>true</tt> if the torrent is a single file torrent,
	 * <tt>false</tt> if it is a multi file torrent.
	 *
	 * @since 3.0.4.3
	 */
	public boolean isSimpleTorrent();

	public Torrent
	getClone()

		throws TorrentException;
}
