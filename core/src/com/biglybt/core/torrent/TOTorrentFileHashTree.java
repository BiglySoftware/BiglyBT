/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.torrent;

import com.biglybt.core.peermanager.piecepicker.util.BitFlags;

public interface 
TOTorrentFileHashTree
{
	public TOTorrentFile
	getFile();
	
	public byte[]
	getRootHash();
	
	public boolean
	isPieceLayerComplete();
	
	public HashRequest
	requestPieceHash(
		int			piece_number,
		BitFlags	available );
	
	public void 
	receivedHashes(
		byte[]			root_hash, 
		int 			base_layer, 
		int 			index, 
		int 			length,
		int 			proof_layers, 
		byte[][] 		hashes );
	
	public boolean 
	requestHashes(
		PieceTreeProvider	piece_tree_provider,
		HashesReceiver		hashes_receiver,
		byte[]				root_hash, 
		int 				base_layer, 
		int 				index, 
		int 				length,
		int 				proof_layers );
	
	public interface
	HashesReceiver
	{	
		public void
		receiveHashes(
			byte[][]	hashes );
	}
	
	public interface
	PieceTreeReceiver
	{	
		public HashesReceiver
		getHashesReceiver();
		
		public void
		receivePieceTree(
			int			piece_number,
			byte[][]	piece_tree );
	}
	
	public interface
	PieceTreeProvider
	{
		public void
		getPieceTree(
			PieceTreeReceiver		receiver,
			TOTorrentFileHashTree	tree,
			int 					piece_index );
	}
	
	public interface
	HashRequest
	{
		public byte[]
		getRootHash();
		
		public int
		getBaseLayer();
		
		public int
		getOffset();
		
		public int
		getLength();
		
		public int
		getProofLayers();
	}
}
