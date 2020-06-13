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

package com.biglybt.core.torrent.impl;

import java.security.MessageDigest;
import java.util.*;

import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFileHashTree;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SHA256;

public class 
TOTorrentFileHashTreeImpl
	implements TOTorrentFileHashTree
{
	
	private final static int DIGEST_LENGTH = SHA256.DIGEST_LENGTH;
	
	private final static int BLOCK_SIZE		= 16*1024;

	
		// contains hashes to use when padding keyed byh layer above base layer
		// 0 -> 0-byte hash, 1-> sha256( [0], [0] ) etc
	
	private final static Map<Integer,byte[]> pad_hash_cache = new HashMap<>();
	
	private final static int[]		tree_hash_widths = new int[31];

	static{
		try{
			MessageDigest sha256 = MessageDigest.getInstance( "SHA-256" );
			
			byte[] pad_hash = new byte[DIGEST_LENGTH];
			
			pad_hash_cache.put( 0, pad_hash );
						
			tree_hash_widths[0]	= 1;
			
			int width = 2;
			
			for ( int i=1; i<tree_hash_widths.length; i++){
				
				tree_hash_widths[i] 	= width;
				
				width <<= 1;
						
				sha256.update( pad_hash );
				sha256.update( pad_hash );
				
				pad_hash = sha256.digest();
				
				pad_hash_cache.put( i, pad_hash );
			} 
		}catch( Throwable e ){
			
			Debug.out( e );
		}	
	}
	
	private final TOTorrentFileImpl		file;
			
	private Object		tree_lock = new Object();
		
	private byte[][]	tree;
		
	private final int 	piece_layer_index;
	
	protected
	TOTorrentFileHashTreeImpl(
		TOTorrentFileImpl	_file,
		byte[]				_root_hash )
	{
		file			= _file;
		
		long	file_length = file.getLength();
							
		long highestOneBit = Long.highestOneBit(file_length);
			
		long base_width_bytes;
		
		if ( file_length == highestOneBit ) {
				
			base_width_bytes = file_length;	// already a power of 2
				
		}else{
				
			base_width_bytes =  highestOneBit << 1;	// round up to next power of 2
		}
			
		int block_depth	= 13;

		int tree_depth;
		
		if ( base_width_bytes <= BLOCK_SIZE ){
			
			tree_depth = 1;
			
		}else{
		
			tree_depth = 63 - Long.numberOfLeadingZeros( base_width_bytes );
			
			tree_depth -= block_depth;
		}
		
		int piece_length = (int)file.getTorrent().getPieceLength();
		
		if ( file_length > piece_length ){
					
			int	piece_depth = 31 - Integer.numberOfLeadingZeros( piece_length ) - block_depth;
			
			piece_layer_index = tree_depth - piece_depth;
			
		}else{
			
			piece_layer_index = 0;
		}
				
		tree = new byte[tree_depth][];
		
		tree[0]	= _root_hash;
		
		long tree_bytes = tree[0].length; 
		
		long	layer_block_size = base_width_bytes;
		
		for ( int i=1;i<tree_depth;i++){
			
			layer_block_size	>>>= 1;
		
			// System.out.println( "    " + i + " = " + layer_block_size );
			
				// generally there will be a lot of padding on the right of the tree so don't waste space (entries
				// can be grabbed from pad_hash_cache when needed)
		
			int needed_blocks = (int)( file_length / layer_block_size );
		
			if ( file_length % layer_block_size != 0 ){
				
				needed_blocks++;
			}
		
			int layer_size = needed_blocks*DIGEST_LENGTH;
			
			tree[i] = new byte[ layer_size ];
			
			tree_bytes += layer_size;
		}
		
		System.out.println( "tree for " + file_length + " / " + base_width_bytes + ", blocks=" + (file_length/BLOCK_SIZE ) + " -> " + DisplayFormatters.formatByteCountToKiBEtc( tree_bytes ) + ", piece=" + piece_length + ", piece_layer=" + piece_layer_index );
		
		//printTree();
	}
	
	private void
	printTree()
	{
		System.out.println( "tree for " + file.getRelativePath() + ", length=" + file.getLength() + ", depth=" + tree.length );
		
		for ( int i=0;i<tree.length;i++){
			
			byte[] 	layer = tree[i];
			
			String str = "'";
			
			for ( int j=0;j<layer.length;j+=DIGEST_LENGTH ){
			
				boolean blank = true;
				
				for ( int k=j; k<j+DIGEST_LENGTH; k++ ){
					if ( layer[k] != 0 ){
						blank = false;
						break;
					}
				}
				
				str += blank?" ":"*";
			}
			
			System.out.println( tree_hash_widths[i] + " / " + layer.length/DIGEST_LENGTH + " - " + str + "'");
		}
	}
	
	@Override
	public byte[] 
	getRootHash()
	{
		return( tree[0] );
	}
	
	protected List<byte[]>
	addPieceLayer(
		byte[]			piece_layer )
	
		throws TOTorrentException
	{
		if ( false ){
			
			Debug.outNoStack( "Ignoring piece layer" );
			
			List<byte[]> result = new ArrayList<>( piece_layer.length/DIGEST_LENGTH );
			
			for ( int i=0;i<piece_layer.length;i+= DIGEST_LENGTH ){
				
				result.add( null );
			}
			
			return( result );
		}
		
		synchronized( tree_lock ){
			
			try{
				MessageDigest sha256 = MessageDigest.getInstance( "SHA-256" );
	
				int tree_depth =  tree.length;
	
				int layer_index = piece_layer_index;
																									
				byte[]	tree_layer = tree[layer_index];
								
				System.arraycopy( piece_layer, 0, tree_layer, 0, piece_layer.length );
								
				byte[] computed_root_hash = null;
				
				while( layer_index > 0 ){
				
					byte[] layer_pad_hash = pad_hash_cache.get( tree_depth - layer_index -1 );
	
					int layer_width = tree_hash_widths[layer_index];
					
					byte[] layer_above = tree[layer_index-1];
					
					int	layer_above_offset = 0;
					
					for ( int i=0;i<layer_width; i += 2 ){
						
						int offset1 	= i*DIGEST_LENGTH;
						
						if ( offset1 >= tree_layer.length ){
							
							break;	// past the end of useful hashes
							
						}else{
							
							sha256.update( tree_layer, offset1, DIGEST_LENGTH );
						}
						
						int offset2 	= offset1 + DIGEST_LENGTH;
						
						if ( offset2 >= tree_layer.length ){
							
							sha256.update( layer_pad_hash );
							
						}else{
							
							sha256.update( tree_layer, offset2, DIGEST_LENGTH );
						}	
						
						byte[] hash = sha256.digest();
						
						if ( layer_index > 1 ){
						
							System.arraycopy( hash, 0, layer_above, layer_above_offset, DIGEST_LENGTH );
							
							layer_above_offset += DIGEST_LENGTH;
							
						}else{
						
							computed_root_hash = hash;
						}
					}
																	
					layer_index--;
					
					tree_layer = tree[layer_index];
				}
							
				if ( !Arrays.equals( computed_root_hash, tree[0] )){
					
					for ( int i=piece_layer_index;i>0;i--){
						
						tree[i] = new byte[tree[i].length];
					}
					
					throw( new TOTorrentException( "Piece layer validation against root failed", TOTorrentException.RT_DECODE_FAILS ));
				}
				
				
				//printTree();
				
				List<byte[]> result = new ArrayList<>( piece_layer.length/DIGEST_LENGTH );
	
				for ( int i=0;i<piece_layer.length;i+= DIGEST_LENGTH ){
					
					byte[] x = new byte[DIGEST_LENGTH];
					
					System.arraycopy( piece_layer, i, x, 0, DIGEST_LENGTH );
					
					result.add( x );
				}
	
				return( result );
				
			}catch( TOTorrentException e ){
				
				throw( e );
				
			}catch( Throwable e ){
				
				throw( new TOTorrentException( "Failed to validate piece layer", TOTorrentException.RT_DECODE_FAILS, e ));
			}
		}
	}
	
	@Override
	public HashRequest 
	requestPieceHash(
		int piece_number)
	{
		if ( piece_layer_index == 0 ){
			
			return( null );
		}
		
			// make relative to this file
		
		int index = piece_number -  file.getFirstPieceNumber();
		
		synchronized( tree_lock ){
			
			byte[]	piece_layer = tree[ piece_layer_index ];
			
			int offset = index * DIGEST_LENGTH;
			
			for ( int i=offset;i<offset+DIGEST_LENGTH;i++){
				
				if ( piece_layer[i] != 0 ){
					
					return( null );
				}
			}
			
			int	base_layer = tree.length - piece_layer_index - 1;
			
			index	= index&0xfffffffe;
			
			int	length			= 2;
			int proof_layers	= piece_layer_index-1;
			
			
				// todo: 1 - request more pieces if the peer has them
				// 2: limit proof layers to depth required rather than full set 
			
			System.out.println( "hash request for " + piece_number + " -> " + base_layer + ", " + index + ", " + length );
			
			return( new HashRequestImpl( base_layer, index, length, proof_layers ));
		}
	}
	
	public void 
	receivedHashes(
		byte[]			root_hash, 
		int 			base_layer, 
		int 			index, 
		int 			length,
		int 			proof_layers, 
		byte[][] 		hashes )
	{
		System.out.println( "hash reply for " + ( file.getFirstPieceNumber() + index ) + " -> " + base_layer + ", " + index + ", " + length );
		
		int layer_index = tree.length - base_layer -1;
		
		if ( layer_index != piece_layer_index ){
			
			return;
		}
		
		synchronized( tree_lock ){

					
				// int missing_proofs = 31 - Integer.numberOfLeadingZeros( length ) -1;
			
			byte[][]	copy_bytes 		= new byte[layer_index+1][];
			int[]		copy_offsets	= new int[copy_bytes.length];
			
			
			try{		
				MessageDigest sha256 = MessageDigest.getInstance( "SHA-256" );
	
				List<byte[]> l_hashes = Arrays.asList( hashes ).subList( 0,  length );
				
				int	proof_pos = length;
	
				int	layer_offset = index;
				
				boolean	layer_match = false;
				
				for ( int li=layer_index; li>0; li-- ){
					
					int copy_offset_bytes = layer_offset*DIGEST_LENGTH;
					
					if ( l_hashes.size() == 1 ){
						
						if ( proof_pos == hashes.length ){
						
								// run out of proof layers, if we asked for less then we would have had
								// a layer match
							
							break;
						}
						
						if ( ( layer_offset & 0x00000001 ) == 0 ){
						
							l_hashes.add( hashes[ proof_pos++ ]);
							
						}else{
						
							l_hashes.add( 0,  hashes[ proof_pos++ ] );
							
							copy_offset_bytes -= DIGEST_LENGTH;
						}
					}
					
					byte[] hashes_bytes = new byte[l_hashes.size()*DIGEST_LENGTH];
					
					for ( int i=0;i < l_hashes.size();i++ ){
						
						System.arraycopy( l_hashes.get(i), 0, hashes_bytes, i*DIGEST_LENGTH, DIGEST_LENGTH );
					}
					
					byte[] tree_layer = tree[li];
					
					layer_match = true;
					
					int tree_pos = copy_offset_bytes;
					
					for ( int i=0; i<hashes_bytes.length && tree_pos < tree_layer.length; i++ ){
						
						if ( tree_layer[ tree_pos++ ] != hashes_bytes[i] ){
							
							layer_match = false;
							
							break;
						}
					}
					
					if ( layer_match ){
						
						break;
					}
					
					copy_bytes[li] 		= hashes_bytes;
					copy_offsets[li]	= copy_offset_bytes;  
							
					List<byte[]> next_hashes = new ArrayList<>( l_hashes.size() / 2 );
					
					for ( int i=0; i<hashes_bytes.length; i += 2*DIGEST_LENGTH ){
					
						sha256.update( hashes_bytes, i, 2*DIGEST_LENGTH );
						
						next_hashes.add( sha256.digest());
					}	
					
					l_hashes = next_hashes;
									
					layer_offset >>>= 1;
				}
	
				if ( !layer_match ){
					
					byte[] computed_root_hash = l_hashes.get(0);
					
					if ( !Arrays.equals( computed_root_hash, tree[0] )){
						
						Debug.out( "Computed root hash mismatch" );
						
						return;
					}
				}
				
				for ( int li = layer_index; li > 0; li-- ){
					
					byte[] 	bytes = copy_bytes[li];
					
					if ( bytes == null ){
						
						break;
					}
										
					int copy_offset = copy_offsets[li];
					
						// layer length is limited to actual needed, not fully padded tree width
					
					byte[] tree_layer = tree[li];
							
					int to_copy = Math.min( bytes.length, tree_layer.length - copy_offset );
					
					if ( to_copy > 0 ){
					
						System.arraycopy( bytes, 0, tree_layer, copy_offset, to_copy );
					}
				}
			}catch( Throwable e ){
				
				Debug.out( e );
				
				return;
			}
		}
		
		
		
		for ( int i=0; i<length; i++ ){
			
			byte[]	piece_hash = new byte[DIGEST_LENGTH];
			
			System.arraycopy( hashes[i], 0, piece_hash, 0, DIGEST_LENGTH );
			
			int piece_number = file.getFirstPieceNumber() + index + i;
			
			if ( piece_number <= file.getLastPieceNumber()){
			
				file.getTorrent().setPiece( piece_number, piece_hash );
			}
		}
	}
	
	private class
	HashRequestImpl
		implements HashRequest
	{		
		private final int		base_layer;
		private final int		offset;
		private final int		length;
		private final int		proof_layers;
		
		private 
		HashRequestImpl(
			int		_base_layer,
			int		_offset,
			int		_length,
			int		_proof_layers )
		{
			base_layer		= _base_layer;
			offset			= _offset;
			length			= _length;
			proof_layers	= _proof_layers;
		}
		
		public byte[]
		getRootHash()
		{
			return( tree[0] );
		}
		
		public int
		getBaseLayer()
		{
			return( base_layer );
		}
		
		public int
		getOffset()
		{
			return( offset );
		}
		
		public int
		getLength()
		{
			return( length );
		}
		
		public int
		getProofLayers()
		{
			return( proof_layers );
		}
	}
}
