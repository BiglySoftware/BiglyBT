/*
 * Created on 29-Dec-2004
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

package com.biglybt.pif.clientid;

import java.util.Properties;

/**
 * @author parg
 *
 */

public interface
ClientIDGenerator
{
	public static final String PR_URL			= "URL";			// in/out, the target URL
	public static final String PR_PROXY			= "Proxy";			// if we're using a Proxy
	public static final String PR_RAW_REQUEST	= "Raw-Request";	// in, Boolean
	public static final String PR_USER_AGENT	= "User-Agent";		// out
	public static final String PR_SNI_HACK		= "SNI-Hack";		// in, Boolean

	public static final String PR_CLIENT_NAME		= "Client-Name";		// out, String
	public static final String PR_MESSAGING_MODE 	= "Messaging-Mode";		// out, Integer

		/**
		 * generate a peer id - must be exactly 20 bytes
		 * @param torrent
		 * @param for_tracker	generator to give to tracker or for peer-peer comms
		 * @return
		 */

	public byte[]
	generatePeerID(
		byte[]		hash,
		boolean		for_tracker )

		throws ClientIDException;

		/**
		 * generate appropriate properties to be supplied to HTTP(S) URL connection
		 * If you want to access the torrents then you need to decode the URL to extract the
		 * hashes and then look them up (for scrape there could be multiple torrents)
		 * Enabled when the generator isn't specified as a filter
		 * @param torrent
		 * @param properties
		 */

	public void
	generateHTTPProperties(
		byte[]		hash,
		Properties	properties )

		throws ClientIDException;

		/**
		 * For more complex situations a filter approach is used. The lines of the request are
		 * passed in for modification and return
		 * Enabled when the generator is specified as a filter
		 * @param lines_in
		 * @return
		 */

	public String[]
	filterHTTP(
		byte[]		hash,
		String[]	lines_in )

		throws ClientIDException;

	public Object
	getProperty(
		byte[]		hash,
		String		property_name );
}
