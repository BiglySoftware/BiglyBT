/*
 * Created on 07-Nov-2004
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

package com.biglybt.core.torrent;

/**
 * @author parg
 *
 */

public interface
TOTorrentCreator
{
		/**
		 * A 'layout descriptor' is a file that explicitly details the construction of the torrent from a collection of files, rather than relying
		 * on a natural file system structure.
		 * The file is bencoded and consists of a Map with a List<Map> called 'file_map'. The sub-maps have two entries: <logical_path>, <target>
		 * <logical_path> is a list of Strings that correspond to a virtual folder structure and the logical file name
		 * <target> is the absolute path of the physical file or dir
		 * @param b
		 */

	public void
	setFileIsLayoutDescriptor(
		boolean		b );

	public TOTorrent
	create()

		throws TOTorrentException;

	public TOTorrent
	create(
		boolean		skip_hashing )

		throws TOTorrentException;

	public long
	getTorrentDataSizeFromFileOrDir()

		throws TOTorrentException;

	public void
	cancel();

	public void
	addListener(
		TOTorrentProgressListener	listener );

	public void
	removeListener(
		TOTorrentProgressListener	listener );
}
