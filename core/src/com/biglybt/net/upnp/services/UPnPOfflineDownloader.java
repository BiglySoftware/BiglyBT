/*
 * Created on 15-Jun-2004
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

package com.biglybt.net.upnp.services;

import com.biglybt.net.upnp.UPnPException;

/**
 * @author parg
 *
 */

public interface
UPnPOfflineDownloader
	extends UPnPSpecificService
{
	public long
	getFreeSpace(
		String		client_id )

		throws UPnPException;

	public void
	activate(
		String		client_id )

		throws UPnPException;

	public String
	addDownload(
		String		client_id,
		String		hash_list,
		String		torrent )

		throws UPnPException;

	public String
	addDownloadChunked(
		String		client_id,
		String		hash_list,
		String		torrent_chunk,
		int			offset,
		int			total_size )

		throws UPnPException;

	public String[]
	updateDownload(
		String		client_id,
		String		hash_list,
		String		required_map )

		throws UPnPException;

	public String[]
	setDownloads(
		String		client_id,
		String		hash_list )

		throws UPnPException;

	public String
	removeDownload(
		String		client_id,
		String		hash )

		throws UPnPException;

	public String[]
	startDownload(
		String		client_id,
		String		hash )

		throws UPnPException;
}
