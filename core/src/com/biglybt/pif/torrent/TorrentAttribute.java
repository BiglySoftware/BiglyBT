/*
 * Created on 23-Jun-2004
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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
 *
 */

package com.biglybt.pif.torrent;

/**
 * @author parg
 *
 */

public interface
TorrentAttribute
{
	public static final String	TA_CATEGORY						= "Category";
	public static final String	TA_NETWORKS						= "Networks";
	public static final String	TA_PEER_SOURCES					= "PeerSources";
	public static final String	TA_TRACKER_CLIENT_EXTENSIONS	= "TrackerClientExtensions";
	public static final String	TA_SHARE_PROPERTIES				= "ShareProperties";	// private=true/false;dht_backup=true/false
	public static final String  TA_CONTENT_MAP                  = "ContentMap";

	/**
	 * Controls the displayed name of a download.
	 *
	 * @since 2.5.0.0
	 */
	public static final String  TA_DISPLAY_NAME                 = "DisplayName";

	/**
	 * The user-defined comment for a download.
	 *
	 * @since 2.5.0.1
	 */
	public static final String  TA_USER_COMMENT                 = "UserComment";

	/**
	 * Defines a relative file path for this download - this attribute is used
	 * when moving downloads for completion or removal - this path is relative
	 * to the default download directories.
	 *
	 * @since 2.5.0.1
	 */
	public static final String  TA_RELATIVE_SAVE_PATH           = "RelativePath";


	public String
	getName();

		/**
		 * Returns the currently defined values for this attribute
		 * @return
		 */

	public String[]
	getDefinedValues();

		/**
		 * Adds a new value to the attribute set
		 * @param name
		 */

	public void
	addDefinedValue(
		String		name );

		/**
		 * Removes a value from the attribute set
		 * @param name
		 */

	public void
	removeDefinedValue(
		String		name );

	public void
	addTorrentAttributeListener(
		TorrentAttributeListener	l );

	public void
	removeTorrentAttributeListener(
		TorrentAttributeListener	l );


}
