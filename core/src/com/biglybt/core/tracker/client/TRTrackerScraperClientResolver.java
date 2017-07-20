/*
 * Created on 27-Aug-2004
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

package com.biglybt.core.tracker.client;

import java.net.URL;

import com.biglybt.core.util.HashWrapper;

/**
 * @author parg
 *
 */

public interface
TRTrackerScraperClientResolver
{
	public static final Character FL_NONE					= new Character( 'n' );
	public static final Character FL_INCOMPLETE_STOPPED		= new Character( 's' );
	public static final Character FL_INCOMPLETE_QUEUED		= new Character( 'q' );
	public static final Character FL_INCOMPLETE_RUNNING		= new Character( 'r' );
	public static final Character FL_COMPLETE_STOPPED		= new Character( 'S' );
	public static final Character FL_COMPLETE_QUEUED		= new Character( 'Q' );
	public static final Character FL_COMPLETE_RUNNING		= new Character( 'R' );

	public boolean
	isScrapable(
		HashWrapper	torrent_hash );

		/**
		 *
		 * @param hash
		 * @return
		 */

	public int[]
	getCachedScrape(
		HashWrapper	hash );

	public boolean
	isNetworkEnabled(
		HashWrapper	hash,
		URL			url );

	public String[]
	getEnabledNetworks(
		HashWrapper	hash );

		/**
		 * Two kinds of extensions: entry [0] = String (or null) that gets passed with the scrape verbotem after infohash
		 * entry [1] = Character - status of download, aggregated into a single String passed with scrape
		 * status flags are above FL_ values
		 * @param hash
		 * @return
		 */

	public Object[]
	getExtensions(
		HashWrapper	hash );

	public boolean
	redirectTrackerUrl(
		HashWrapper		hash,
		URL				old_url,
		URL				new_url );
}
