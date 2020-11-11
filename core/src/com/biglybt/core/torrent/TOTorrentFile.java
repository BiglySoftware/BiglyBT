/*
 * File    : TOTorrentFile.java
 * Created : 5 Oct. 2003
 * By      : Parg
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

package com.biglybt.core.torrent;

import java.util.Map;

public interface
TOTorrentFile
{
	public TOTorrent
	getTorrent();

	public int
	getIndex();

	public long
	getLength();

	public byte[][]
	getPathComponents();

	/**
	 * Build a relative path based on path components, using {@link java.io.File#separator}
	 */
	public String getRelativePath();

	public int
	getFirstPieceNumber();

	public int
	getLastPieceNumber();

	public int
	getNumberOfPieces();

		/**
		 * is BEP_47 pad file
		 * @return
		 */
	
	public boolean
	isPadFile();
	
		/**
		 * V2 torrents only - also null for zero length files
		 * @return
		 */
	
	public TOTorrentFileHashTree
	getHashTree();
	
	public byte[]
	getRootHash();
	
	public Map
	getAdditionalProperties();
}
