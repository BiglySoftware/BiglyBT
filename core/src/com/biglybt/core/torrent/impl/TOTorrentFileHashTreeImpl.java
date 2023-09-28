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

import com.biglybt.core.peermanager.piecepicker.util.BitFlags;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.torrent.TOTorrentFileHashTree;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.RandomUtils;
import com.biglybt.core.util.SHA256;

public class 
TOTorrentFileHashTreeImpl
	implements TOTorrentFileHashTree
{
	private static final boolean TEST_LEAF_REQUESTS		= false;
	
	static{
		if ( TEST_LEAF_REQUESTS ){
			
			Debug.out( "LEAF TESTING ENABLED!!!!");
		}
	}
	
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
		
		//long tree_bytes = tree[0].length; 
		
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
			
			//tree_bytes += layer_size;
			
				// nah, not going to allocate below piece layer as takes a LOT of space
			
			if ( i == piece_layer_index ){
				
				break;
			}
		}
		
		//System.out.println( "tree for " + file_length + " / " + base_width_bytes + ", blocks=" + (file_length/BLOCK_SIZE ) + " -> " + DisplayFormatters.formatByteCountToKiBEtc( tree_bytes ) + ", piece=" + piece_length + ", piece_layer=" + piece_layer_index );
		
		//printTree();
	}
	
	/*
	private void
	printTree()
	{
		System.out.println( "tree for " + file.getRelativePath() + ", length=" + file.getLength() + ", depth=" + tree.length );
		
		for ( int i=0;i<tree.length;i++){
			
			byte[] 	layer = tree[i];
			
			String str = "'";
			
			if ( layer == null ){
				
				str = "<null>";
				
			}else{

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
			}
			
			System.out.println( tree_hash_widths[i] + " / " + layer.length/DIGEST_LENGTH + " - " + str + "'");
		}
	}
	*/
	
	@Override
	public TOTorrentFile 
	getFile()
	{
		return( file );
	}
	
	@Override
	public byte[] 
	getRootHash()
	{
		return( tree[0] );
	}
	
	protected Map<String,Object>
	exportState()
	{
		synchronized( tree_lock ){

			byte[]	piece_layer = tree[piece_layer_index];
			
			boolean is_empty = true;
			
			for ( int i=0;i<piece_layer.length;i++){
				
				if ( piece_layer[i] != 0 ){
			
					is_empty = false;
					
					break;
				}
			}
			
			if ( is_empty ){
				
				return( null );
			}
			
			Map<String,Object>	map = new HashMap<>();
			
			for ( int i=1; i <= piece_layer_index; i++ ){
				
				map.put( String.valueOf( i ), tree[i].clone());
			}
			
			return( map );
		}
	}
	
	protected List<byte[]>
	importState(
		Map<String,Object>	map )
	{
		List<byte[]> result = null;
		
		byte[][] updated_tree = new byte[tree.length][];
		
		synchronized( tree_lock ){
			
			for ( int i=1; i <= piece_layer_index; i++ ){
				
				byte[] layer = (byte[])map.get(String.valueOf( i ));
				
				if ( layer != null && layer.length == tree[i].length ){
					
					updated_tree[i] = layer;
					
					if ( i == piece_layer_index ){
						
						result = new ArrayList<>( layer.length / DIGEST_LENGTH );
						
						for ( int j=0;j<layer.length;j+= DIGEST_LENGTH ){
							
							byte[] x = new byte[DIGEST_LENGTH];
							
							System.arraycopy( layer, j, x, 0, DIGEST_LENGTH );
						
							boolean ok = false;
							
							for ( int k=0; k<DIGEST_LENGTH;k++){
								
								if ( x[k] != 0 ){
									
									ok = true;
									
									break;
								}
							}
								
							result.add( ok?x:null );
						}
					}
				}else{
					
						// invalid
					
					Debug.out( "Invalid hash tree state" );
					
					return( Collections.EMPTY_LIST );
				}
			}
		
			if ( result == null ){
				
				Debug.out( "Invalid hash tree state" );

				return( Collections.EMPTY_LIST );
				
			}else{
				
				for ( int i=1;i<=piece_layer_index;i++){
					
					tree[i] = updated_tree[i];
				}
				
				return( result );
			}
		}
	}
	
	@Override
	public boolean 
	isPieceLayerComplete()
	{
		if ( piece_layer_index == 0 ){
			
			return( true );
		}
		
		synchronized( tree_lock ){
																												
			byte[]	tree_layer = tree[piece_layer_index];
			
			for ( int i=0; i < tree_layer.length; i+= DIGEST_LENGTH ){
				
				boolean	ok = false;
				
				for ( int j=i; j<i+DIGEST_LENGTH; j++){
					
					if ( tree_layer[j] != 0 ){
						
						ok = true;
						
						break;
					}
				}
				
				if ( !ok ){
					
					return( false );
				}
			}
			
			return( true );
		}
	}
	
	protected byte[]
	getPieceLayer()
	{
		if ( piece_layer_index == 0 ){
			
			return( null );
		}
		
		synchronized( tree_lock ){
																												
			byte[]	tree_layer = tree[piece_layer_index];
			
			for ( int i=0; i < tree_layer.length; i+= DIGEST_LENGTH ){
				
				boolean	ok = false;
				
				for ( int j=i; j<i+DIGEST_LENGTH; j++){
					
					if ( tree_layer[j] != 0 ){
						
						ok = true;
						
						break;
					}
				}
				
				if ( !ok ){
					
					return( null );
				}
			}
			
			return( tree_layer.clone());
		}
	}
		
	protected List<byte[]>
	addPieceLayer(
		byte[]			piece_layer )
	
		throws TOTorrentException
	{
		synchronized( tree_lock ){
			
			try{
				MessageDigest sha256 = MessageDigest.getInstance( "SHA-256" );
		
				int layer_index = piece_layer_index;
																									
				byte[]	tree_layer = tree[layer_index];
								
				System.arraycopy( piece_layer, 0, tree_layer, 0, piece_layer.length );
								
				byte[] computed_root_hash = null;
				
				while( layer_index > 0 ){
				
					byte[] layer_pad_hash = pad_hash_cache.get( tree.length - layer_index -1 );
	
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
		int 		piece_number,
		BitFlags	available )
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
			
			int	piece_base_layer = tree.length - piece_layer_index - 1;

			if ( TEST_LEAF_REQUESTS ){
				
				// convert piece request into a leaf request for testing purposes
				
				int	leaf_base_layer = 0;	// leaf
				
				int layer_diff = piece_base_layer - leaf_base_layer;
				
				int leafs_per_piece = 1 << layer_diff;
				
				index <<= layer_diff;
				
				index += RandomUtils.nextInt( leafs_per_piece );
				
				index	= index&0xfffffffe;
				
				int	length			= 2;
				int proof_layers	= tree.length - 2;
				
								
				//System.out.println( "hash leaf request for " + piece_number + " -> " + leaf_base_layer + ", " + index + ", " + length );
				
				return( new HashRequestImpl( leaf_base_layer, index, length, proof_layers ));

			}else{
							
				index	= index&0xfffffffe;
				
				int	length			= 2;
				int proof_layers	= piece_layer_index-1;
				
				
					// todo: 1 - request more pieces if the peer has them
					// 2: limit proof layers to depth required rather than full set 
				
				//System.out.println( "hash piece request for " + piece_number + " -> " + piece_base_layer + ", " + index + ", " + length );
				
				return( new HashRequestImpl( piece_base_layer, index, length, proof_layers ));

			}
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
		try{
			// System.out.println( "hash reply for " + ( file.getFirstPieceNumber() + index ) + " -> " + base_layer + ", " + index + ", " + length );
			
			int layer_index = tree.length - base_layer -1;
			
				// we never request anything other than piece hashes from peers (unless testing leaf hashes)
			
			if ( layer_index != piece_layer_index ){
				
				if ( !TEST_LEAF_REQUESTS ){
				
					return;
				}
			}
			
			synchronized( tree_lock ){
				
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
						
						if ( tree_layer != null ){
							
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
								
						if ( tree_layer != null ){
							
							int to_copy = Math.min( bytes.length, tree_layer.length - copy_offset );
							
							if ( to_copy > 0 ){
							
								System.arraycopy( bytes, 0, tree_layer, copy_offset, to_copy );
								
								if ( li == piece_layer_index && TEST_LEAF_REQUESTS ){
									
										// end up here when testing leaf hashes
									
									int	piece_offset = copy_offset/DIGEST_LENGTH;

									int piece_number = file.getFirstPieceNumber() + piece_offset;
									
									for ( int x=0; x<bytes.length;x+=DIGEST_LENGTH){
																			
										byte[]	piece_hash = new byte[DIGEST_LENGTH];
										
										System.arraycopy( bytes, x, piece_hash, 0, DIGEST_LENGTH );
																				
										if ( piece_number <= file.getLastPieceNumber()){
										
											file.getTorrent().setPiece( piece_number, piece_hash );
										}
										
										piece_number++;
									}
								}
							}
						}
					}
				}catch( Throwable e ){
					
					Debug.out( e );
					
					return;
				}
			}
			
			if ( layer_index == piece_layer_index && !TEST_LEAF_REQUESTS ){
				
					// normal case when not testing leaf hashes
				
				for ( int i=0; i<length; i++ ){
					
					byte[]	piece_hash = new byte[DIGEST_LENGTH];
					
					System.arraycopy( hashes[i], 0, piece_hash, 0, DIGEST_LENGTH );
					
					int piece_number = file.getFirstPieceNumber() + index + i;
					
					if ( piece_number <= file.getLastPieceNumber()){
					
						file.getTorrent().setPiece( piece_number, piece_hash );
					}
				}
			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
	}
	
	public boolean
	requestHashes(
		PieceTreeProvider	piece_tree_provider,
		HashesReceiver		hashes_receiver,
		byte[]				root_hash, 
		int 				base_layer, 
		int 				index, 
		int 				length,
		int 				proof_layers )
	{
		return( requestHashesSupport( piece_tree_provider, hashes_receiver, root_hash, base_layer, index, length, proof_layers, null ));
	}
	
		/*
		 * If we need to load a piece tree we come back through here asynchronously with it loaded
		 */
	
	private boolean
	requestHashesSupport(
		PieceTreeProvider	piece_tree_provider,
		HashesReceiver		hashes_receiver,
		byte[]				root_hash, 
		int 				base_layer, 
		int 				index, 
		int 				length,
		int 				proof_layers,
		byte[][]			loaded_piece_tree )
	{
		try{
			//System.out.println( "hash request for file " + file.getIndex() + ": " + base_layer + ", " + index + ", " + length );
			
			int leaf_layer_index = tree.length - 1;
			
			int layer_index = leaf_layer_index - base_layer;
			
				// support piece layer and leaf layer only
			
			if ( layer_index != piece_layer_index && layer_index != tree.length - 1 ){
				
				return( false );
			}
			
			if ( length < 2 || length > 512 ){
				
				return( false );
			}
			
			if ( length != Integer.highestOneBit( length )){
				
				return( false );	// not a power of 2
			}
			
			if ( index % length != 0 ){
				
				return( false );	// index must be multiple of length
			}
			
			if ( proof_layers >= tree.length ){
				
				return( false );
			}
			
			int[]		layer_offsets	= new int[tree.length];
			byte[][]	piece_tree		= null;
			
			int layer_offset_x = index;
					
			for ( int i=layer_index;i>0;i--){
				
				layer_offsets[i] = layer_offset_x;
				
				layer_offset_x >>>= 1;
			}
			
			int missing_proofs = 31 - Integer.numberOfLeadingZeros( length ) -1;
	
				// we use a local tree when hacking in a piece tree, normal tree at and above piece layer
				// and then piece-only tree below with adjusted layer offsets to index into it correctly
			
			byte[][]	local_tree;
			
			if  ( layer_index == piece_layer_index ){
				
				local_tree	= tree;
				
			}else{
			
					// dynamically construct the hash tree below the relevant piece hash
								
				int pli_offset = index >>> ( leaf_layer_index - piece_layer_index ); 
			
				byte[] piece_layer = tree[piece_layer_index];
				
				int piece_length = (int)file.getTorrent().getPieceLength();

				int hashes_per_piece = piece_length / BLOCK_SIZE;
				
				int pieces_required = ( length + hashes_per_piece - 1 ) / hashes_per_piece;
								
				for ( int piece_num=0; piece_num < pieces_required; piece_num++){
				
					boolean ok = false;
					
					int current_offset = pli_offset;
					
					for ( int i=current_offset;i<current_offset+DIGEST_LENGTH;i++){
						
						if ( piece_layer[i] != 0 ){
							
							ok = true;
							
							break;
						}
					}
					
					if ( !ok ){
						
						return( false );		// we don't have the piece hash
					}
					
					current_offset += DIGEST_LENGTH;
				}
				
				if ( loaded_piece_tree == null ){
										
					PieceTreeReceiver pt_receiver = 
						new PieceTreeReceiver(){
							
							byte[][][] trees = new byte[pieces_required][][];
							
							int		remaining	= pieces_required;
							
							boolean	done;
							
							@Override
							public void 
							receivePieceTree(
								int			piece_offset,
								byte[][] 	piece_tree )
							{
								if ( pieces_required == 1 ){
									
									if ( piece_tree == null ){
										
										hashes_receiver.receiveHashes( null );
										
									}else{
										
										if ( requestHashesSupport(
												piece_tree_provider,
												hashes_receiver,
												root_hash, 
												base_layer, 
												index, 
												length,
												proof_layers,
												piece_tree )){
											
												// all done, receiver informed
										}else{
											
											hashes_receiver.receiveHashes( null );
										}
									}
								}else{
										
									synchronized( trees ){
										
										if ( done ){
											
											return;
										}
										
										if ( piece_tree == null ){
										
											hashes_receiver.receiveHashes( null );
											
											done = true;
											
											return;
										}
										
										int entry = piece_offset - pli_offset;
										
										if ( trees[entry] != null ){
											
											Debug.out( "Got tree twice" );
											
											hashes_receiver.receiveHashes( null );
											
											done = true;
											
											return;
										}
										
										trees[entry] = piece_tree;
										
										remaining--;
										
										if ( remaining > 0 ){
											
											return;
										}
										
										done = true;
									}
									
									byte[][]	tree0 = trees[0];
									
									int layers = tree0.length;
									
									byte[][]	combined = new byte[layers][];
									
									for ( int li = 0; li < tree0.length; li++ ){
										
										combined[li] = new byte[ tree0[li].length * pieces_required ];
									}
									
									for ( int ti=0; ti < pieces_required; ti++ ){
										
										byte[][] t = trees[ti];
										
										for ( int li = 0; li < layers; li++ ){
										
											byte[]	src = t[li];
											
											int		len = src.length;
											
											System.arraycopy( src, 0, combined[li], len*ti, len );
										}
									}
									
									if ( requestHashesSupport(
											piece_tree_provider,
											hashes_receiver,
											root_hash, 
											base_layer, 
											index, 
											length,
											proof_layers,
											combined )){
										
											// all done, receiver informed
									}else{
										
										hashes_receiver.receiveHashes( null );
									}
								}
							}
							
							@Override
							public HashesReceiver 
							getHashesReceiver()
							{
								return( hashes_receiver );
							}
						};
					
					for ( int piece_num=0; piece_num < pieces_required; piece_num++){
					
						piece_tree_provider.getPieceTree( pt_receiver, this, pli_offset + piece_num );
					}
					
					return( true );
					
				}else{
					
					piece_tree = loaded_piece_tree;
				}
			
				local_tree = tree.clone();
				
				int x = piece_layer_index + 1;
				
				int missing_offset = layer_offsets[ piece_layer_index ];
				
				for ( int i=0;i<piece_tree.length;i++){
					
					missing_offset <<= 1;

					local_tree[ x ] = piece_tree[i];
					
					layer_offsets[ x] -= missing_offset;
										
					x++;
				}
			}
			
			
			byte[]	tree_layer = local_tree[layer_index];
						
			byte[][]	hashes = new byte[length+proof_layers-missing_proofs][];
			
			int	hash_pos = 0;
			
			int	hashes_start = layer_offsets[layer_index] * DIGEST_LENGTH;
		
			for ( int i=hashes_start; i<tree_layer.length && hash_pos < length; i += DIGEST_LENGTH ){
				
				byte[] hash = new byte[DIGEST_LENGTH];
				
				System.arraycopy( tree_layer, i, hash, 0, DIGEST_LENGTH );
				
				if ( layer_index <= piece_layer_index ){
					
					boolean ok = false;
					
					for ( int j=0; j<hash.length; j++ ){
						
						if ( hash[j] != 0 ){
							
							ok = true;
							
							break;
						}	
					}
					
					if ( !ok ){
						
						return( false );		// hash not available
					}
				}
				
				hashes[ hash_pos++ ] = hash;
			}
			
			if ( hash_pos < length ){
				
				byte[] layer_pad_hash = pad_hash_cache.get( tree.length - layer_index - 1 );
				
				while( hash_pos < length ){
				
					hashes[ hash_pos++ ] = layer_pad_hash;
				}
			}
			
			while( layer_index > 1 && hash_pos < hashes.length ){
								
				layer_index--;
								
				if ( missing_proofs > 0 ){
					
					missing_proofs--;
					
				}else{
				
					int	layer_offset = layer_offsets[layer_index];

					tree_layer = local_tree[layer_index];
	
					int uncle_offset;
					
					if ( ( layer_offset & 0x00000001 ) == 0 ){
	
						uncle_offset = layer_offset+1;
						
					}else{
						
						uncle_offset = layer_offset-1;
					}
					
					int hash_start = uncle_offset * DIGEST_LENGTH;
					
					if ( hash_start < tree_layer.length ){
						
						byte[] hash = new byte[DIGEST_LENGTH];
						
						System.arraycopy( tree_layer, hash_start, hash, 0, DIGEST_LENGTH );
						
						boolean ok = false;
						
						for ( int j=0; j<hash.length; j++ ){
							
							if ( hash[j] != 0 ){
								
								ok = true;
								
								break;
							}	
						}
						
						if ( !ok ){
							
							return( false );		// hash not available
						}
						
						hashes[ hash_pos++ ] = hash;
						
					}else{
						
						hashes[ hash_pos++ ] = pad_hash_cache.get( tree.length - layer_index - 1 );
					}
				}
			}
			
			if ( hash_pos != hashes.length ){
				
				return( false );		// didn't fill things in
			}
			
			hashes_receiver.receiveHashes( hashes );
			
			return( true );
			
		}catch( Throwable e ){
			
			Debug.out( e );
		}
		
		return( false );
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
