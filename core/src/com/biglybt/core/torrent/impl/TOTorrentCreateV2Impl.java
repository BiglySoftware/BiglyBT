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


import java.io.*;
import java.util.*;

import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.util.*;

import java.security.*;


public class 
TOTorrentCreateV2Impl
{
	private final static int block_size = 16*1024;

	private final static int digest_length = SHA256.DIGEST_LENGTH;

	private final File		root;
	private final long		piece_size;
	private final Adapter	adapter;
		
	private ByteArrayHashMap<byte[]>	piece_layers 	= new ByteArrayHashMap<>();

	private long	total_file_size;
	private long	total_v1_padding_size;

	private long	file_bytes_hashed	= 0;
	
	private int		file_index	= 0;
	
	private int		synthetic_pad_file_count;
	
	private int		files_ignored;	
	
	private final boolean flatten_files = false;		// for testing only
	
	public static byte[]
	getV2RootHash(
		File		file )
	
		throws TOTorrentException
	{
		TOTorrentCreateV2Impl temp =
			new TOTorrentCreateV2Impl( 
				file, 
				16*1024, 
				new Adapter(){
			
					@Override
					public File 
					resolveFile(int index, File file, String relative_file){
						return( file );
					}
					
					@Override
					public void 
					reportHashedBytes(long bytes){
					}
					
					@Override
					public void 
					report(String resource_key){
					}
					
					@Override
					public boolean 
					ignore(String name){
						return false;
					}
					
					@Override
					public boolean 
					cancelled(){
						return false;
					}
				});
		
		return( temp.handleFile( file, "" ).root_hash );
	}
	
	protected
	TOTorrentCreateV2Impl(
		File		_root,
		long		_piece_size,
		Adapter		_adapter )
	{
		root		= _root;
		piece_size 	= _piece_size;
		adapter		= _adapter;
	}
	
	protected Map<String,Object>
	create()

		throws TOTorrentException
	{	
		Map<String,Map>				file_tree 		= new TreeMap<>();

		if ( root.isFile()){
			
			processFile( root, file_tree, "" );
			
		}else{
			
			processDirectory( root, file_tree, "" );
		}
		
		if ( total_file_size == 0 ){
			
			throw( new TOTorrentException( "V2: No files processed", TOTorrentException.RT_ZERO_LENGTH ));
		}
		
		// System.out.println( file_tree );
		
		Map<String,Object> info = new HashMap<>();
		
		info.put( TOTorrentImpl.TK_NAME, 		root.getName());
		info.put( TOTorrentImpl.TK_NAME_UTF8, 	root.getName());
		
		info.put( TOTorrentImpl.TK_V2_META_VERSION, 2 );
		
		info.put( TOTorrentImpl.TK_PIECE_LENGTH, piece_size );
		
		info.put( TOTorrentImpl.TK_V2_FILE_TREE, file_tree );
		
		Map<String,Object> torrent = new HashMap<>();
		
		torrent.put( TOTorrentImpl.TK_INFO, info );
		
		ByteEncodedKeyHashMap<String,byte[]>	pl_map = new ByteEncodedKeyHashMap<>();
		
		for ( byte[] key: piece_layers.keys()){
		
			pl_map.put( new String( key, Constants.BYTE_ENCODING_CHARSET ), piece_layers.get( key ));
		}
		
		torrent.put( TOTorrentImpl.TK_V2_PIECE_LAYERS, pl_map );

		return( torrent );
	}

	public long
	getTotalFileSize()
	{
		return( total_file_size );
	}
	
	public long
	getTotalPadding()
	{
		return( total_v1_padding_size );
	}
	
	public int
	getIgnoredFiles()
	{
		return( files_ignored );
	}
	
	public void
	processFile(
		File						file,
		Map<String,Map>				node,
		String						relative_path )
	
		throws TOTorrentException
	{
		if ( adapter.ignore( file.getName())){
			
			files_ignored++;
			
			return;
		}
		
		Map<String,Map>	file_node = new TreeMap<>();
		
		node.put( file.getName(), file_node );

		if ( relative_path.isEmpty()){
			
			relative_path = file.getName();
			
		}else{
			
			relative_path += File.separator + file.getName();
		}
		
			// we need to keep track of where pad files will be inserted during the 'lashup process' as any file linkage information
			// has to take account of this...
		
		if ( total_file_size % piece_size != 0 ){
			
			synthetic_pad_file_count++;
		}
		
		FileDetails result = handleFile( file, relative_path );
					
		Map<String,Object>	details = new HashMap<>();
		
		file_node.put( "",  details );
		
		long length = result.length;
		
		details.put( "length", length );
		
		if ( length > 0 ){
			
			details.put( TOTorrentImpl.TK_V2_PIECES_ROOT, result.root_hash );
		}
		
		if ( length > piece_size ){
			
			piece_layers.put( result.root_hash, result.pieces_layer );
		}
		
			// pad this file up to a piece boundary
		
		long excess = (total_file_size+total_v1_padding_size)%piece_size;
		
		if ( excess > 0 ){
		
			long pad_size = piece_size - excess;
			
			//System.out.println( "V2: " + file.getName() +", " + total_file_size + ", " + pad_size );

			total_v1_padding_size += pad_size;
		}
		
		total_file_size += length;
	}
	
	public void
	processDirectory(
		File						dir,
		Map<String,Map>				node,
		String						relative_path )
	
		throws TOTorrentException
	{
		if ( adapter.cancelled()){

			throw( new TOTorrentException( 	"Operation cancelled",
											TOTorrentException.RT_CANCELLED ));
		}
		
		String[] files = dir.list();
		
		if ( files == null || files.length == 0 ){
			
			return;
		}
				
		Arrays.sort( files );
		
		if (!flatten_files ){
			
			if ( relative_path.isEmpty()){
				
				relative_path = dir.getName();
				
			}else{
				
				relative_path += File.separator + dir.getName();
			}
		}
		
		for ( String name: files ){
			
			if ( name.equals( "." ) || name.equals( ".." )){
				
				continue;
			}
			
			File file = FileUtil.newFile( dir, name );
			
			if ( file.isFile()){
				
				processFile( file, node, relative_path );
				
			}else if ( file.isDirectory()){
				
				Map<String,Map>	sub_tree = new TreeMap<>();
				
				node.put( name, sub_tree );
								
				processDirectory( file, sub_tree, relative_path );
			}
		}

	}
	
	public FileDetails
	handleFile(
		File				file,
		String				relative_path )
	
		throws TOTorrentException
	{
		long file_length	= -1;
		byte[] root_hash 	= null;
		byte[] pieces_layer = null;
		
		try{
			MessageDigest sha256 = MessageDigest.getInstance( "SHA-256" );
												
			byte[]	buffer = new byte[block_size];
				
			File link = adapter.resolveFile( file_index + synthetic_pad_file_count, file, relative_path );
			
			file_index++;
			
			if ( link != null ){
								
				file = link;
			}
			
			file_length = file.length();
						
			if ( file_length > 0 ){
				
				long leaf_width;
				
				long highestOneBit = Long.highestOneBit(file_length);
				
				if ( file_length == highestOneBit ) {
					
					leaf_width = file_length;
					
				}else{
					
					leaf_width =  highestOneBit << 1;
				}
							
				int leaf_count = (int)( leaf_width/block_size);
				
				if ( leaf_count == 0 ){
					
					leaf_count = 1;
				}
				
				FileInputStream fis = FileUtil.newFileInputStream(file);
				
				List<byte[]> leaf_digests = new ArrayList<>( leaf_count );
	
				try{				
					while( true ){

						if ( adapter.cancelled()){

							throw( new TOTorrentException( 	"Operation cancelled",
															TOTorrentException.RT_CANCELLED ));
						}

						int len = fis.read( buffer );
						
						if ( len <= 0 ){
							
							break;
						}
						
						sha256.update( buffer, 0, len );
						
						byte[] digest = sha256.digest();
						
						file_bytes_hashed += len;
						
						adapter.reportHashedBytes( file_bytes_hashed );
						
						leaf_digests.add( digest );
					}
				}finally{
					
					fis.close();
				}
				
				byte[]	zero_buffer = new byte[digest_length];

				while( leaf_digests.size() < leaf_count ){
					
					leaf_digests.add( zero_buffer );
				}
				
				List<byte[]> current_level = leaf_digests;
				
				int current_size = block_size;
				
				while( current_level.size() > 1 ){
					
					if ( adapter.cancelled()){

						throw( new TOTorrentException( 	"Operation cancelled",
														TOTorrentException.RT_CANCELLED ));
					}

					// System.out.println( "level " + current_size + "/" + current_level.size());
					
					List<byte[]> next_level = new ArrayList<byte[]>(current_level.size()/2);
																	
					for ( int i=0;i<current_level.size();i+=2 ){
						
						sha256.update( current_level.get(i));
						sha256.update( current_level.get(i+1));
						
						byte[] hash = sha256.digest();
						
						next_level.add( hash );
					}
					
					if ( current_size == piece_size ){
						
						int useful_pieces = (int)( file_length/piece_size );
						
						if ( file_length%piece_size != 0 ){
							
							useful_pieces++;
						}
								
						pieces_layer = new byte[digest_length*useful_pieces];
						
						int pos = 0;
						
						for ( int i=0;i<useful_pieces;i++){
							
							System.arraycopy( current_level.get(i), 0, pieces_layer, pos, digest_length );
							
							pos += digest_length;
						}
						
						//System.out.println( "pieces_layer=" + ByteFormatter.encodeStringFully( pieces_layer ));
					}
					
					current_level = next_level;
					
					current_size *= 2;
				}
				
				root_hash = current_level.get(0);
				
				//System.out.println( "root=" + ByteFormatter.encodeString( root_hash ));
			}
					
			return( new FileDetails( file_length, root_hash, pieces_layer ));
			
		}catch( Throwable e ){
			
			throw( new TOTorrentException( "V2 file processing failed", TOTorrentException.RT_READ_FAILS, e ));
		}
	}
	
	protected static void
	setV2FileHashes(
		TOTorrentImpl	torrent )
	{
		Map<String,Object> file_tree = (Map<String,Object>)torrent.getAdditionalInfoProperties().get( TOTorrentImpl.TK_V2_FILE_TREE );
		
		if ( file_tree != null ){
			
			try{
				long piece_length = torrent.getPieceLength();
				
				List<TOTorrentFileImpl>	v2_files = new ArrayList<>();
				
				long[] torrent_offset	= { 0 };
				long[] pad_details 		= { 0, 0 };
				
				lashUpV2Files( torrent, v2_files, new LinkedList<byte[]>(), file_tree, piece_length, torrent_offset, pad_details );
				
				TOTorrentFileImpl[]	v1_files = torrent.getFiles();
				
				if ( v1_files.length == v2_files.size()){
				
					for ( int i=0; i<v1_files.length; i++){
					
						TOTorrentFileImpl v1_file = v1_files[i];
						
						if ( !v1_file.isPadFile()){
						
							TOTorrentFileImpl v2_file = v2_files.get(i);
							
							if ( v1_file.getLength() == v2_file.getLength()){
							
								v1_files[i].setRootHash( v2_file.getHashTree().getRootHash());
								
							}else{
								
								Debug.out( "Inconsistent v1/v2 file lengths" );
							}
						}
					}
				}else{
					
					Debug.out( "Inconsistent v1/v2 files" );
				}
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
	}
	
	protected static void
	lashUpV1Info(
		TOTorrentImpl	torrent )
	
		throws TOTorrentException
	{		
		Map<String,Object> file_tree = (Map<String,Object>)torrent.getAdditionalInfoProperties().get( TOTorrentImpl.TK_V2_FILE_TREE );
		
		if ( file_tree == null ){
			
			throw( new TOTorrentException( "V2 piece layers missing", TOTorrentException.RT_DECODE_FAILS ));
		}
		
		long piece_length = torrent.getPieceLength();
		
		List<TOTorrentFileImpl>	files = new ArrayList<>();
		
		long[] torrent_offset	= { 0 };
		long[] pad_details 		= { 0, 0 };
		
		lashUpV2Files( torrent, files, new LinkedList<byte[]>(), file_tree, piece_length, torrent_offset, pad_details );
		
		torrent.setFiles( files.toArray( new TOTorrentFileImpl[ files.size() ]));
		
		long	total_file_sizes = torrent_offset[0];
		
		long	piece_count = total_file_sizes/piece_length;
		
		if ( total_file_sizes%piece_length != 0 ){
			
			piece_count++;
		}
		
		byte[][] pieces = new byte[(int)piece_count][];
		
		int piece_num = 0;
		
		Map piece_layers = torrent.getAdditionalMapProperty( TOTorrentImpl.TK_V2_PIECE_LAYERS );
		
		Map hash_tree_state;
		
		if ( piece_layers == null ){
			
			hash_tree_state = TorrentUtils.getHashTreeState( torrent );
			
		}else{
			
			hash_tree_state = null;
		}
		
		/*
		 * torrent spec says piece layers must be present. however, for magnet downloads this isn't the case
		 * as the piece hashes are grabbed during download. Relax this so that in general we'll grab them
		 * 
		if ( piece_layers == null ){
			
			throw( new TOTorrentException( "V2 piece layers missing", TOTorrentException.RT_DECODE_FAILS ));
		}
		*/
		
		for ( TOTorrentFileImpl file: files ){
		
			if ( file.isPadFile()){
				
				continue;	// artificial entry
			}
			
			long length = file.getLength();
			
			if ( length > 0 ){
				
				TOTorrentFileHashTreeImpl tree = file.getHashTree();
				
				byte[] pieces_root = tree.getRootHash();

				if ( length <= piece_length ){
								
					pieces[piece_num++] = pieces_root;
				
				}else{
				
					int file_pieces = file.getNumberOfPieces();
					
					String root_key = new String( pieces_root, Constants.BYTE_ENCODING_CHARSET );
					
					byte[] piece_layer = piece_layers==null?null:(byte[])piece_layers.get( root_key );
					
					if ( piece_layer == null ){
						
						Map state;
						
						if ( hash_tree_state != null ){
							
							state = (Map)hash_tree_state.get( String.valueOf( file.getIndex()));
							
						}else{
							
							state = null;
						}
						
						if ( state == null ){
							
							for ( int i=0;i<file_pieces;i++ ){
																						
								pieces[piece_num++] = null;		// don't have this piece hash yet
							}
						}else{
							
							List<byte[]> imported_pieces = tree.importState( state );
							
							for ( byte[] hash: imported_pieces ){
								
								pieces[piece_num++] = hash;
							}
						}
					}else{
					
						if ( piece_layer.length % digest_length != 0 ){
							
							throw( new TOTorrentException( "V2 piece layer length invalid", TOTorrentException.RT_DECODE_FAILS ));
						}
						
						int layer_pieces = piece_layer.length / digest_length;
						
						if ( file_pieces != layer_pieces ){
							
							throw( new TOTorrentException( "V2 piece layer hash count invalid", TOTorrentException.RT_DECODE_FAILS ));
						}
	
						try{
							List<byte[]> validated_pieces = tree.addPieceLayer( piece_layer );
							
							for ( byte[] hash: validated_pieces ){
																
								pieces[piece_num++] = hash;
							}
						}catch( Throwable e ){
							
							Debug.out( e );
							
							for ( int i=0;i<file_pieces;i++ ){
								
								pieces[piece_num++] = null;		// don't have this piece hash yet
							}

						}
					}
				}
			}
		}
		
		if ( piece_num != piece_count ){
			
			throw( new TOTorrentException( "V2 piece layers inconsistent", TOTorrentException.RT_DECODE_FAILS ));
		}
		
		torrent.setPieces( pieces );
	}
	
	
	
	private static void
	lashUpV2Files(
		TOTorrentImpl				torrent,
		List<TOTorrentFileImpl>		files,
		LinkedList<byte[]>			path,
		Map<String,Object> 			node,
		long						piece_length,
		long[]						torrent_offset,
		long[]						pad_details )
	
		throws TOTorrentException
	{
		List<String> keys = new ArrayList<>( node.keySet());
		
		Collections.sort( keys );
		
		for ( String name: keys ){
			
			Map<String,Object> kid = (Map<String,Object>)node.get( name );
			
			if ( name.isEmpty()){
				
				if ( !files.isEmpty()){
					
					long offset = torrent_offset[0];
					
					long l = offset%piece_length;
					
					if ( l > 0 ){
						
						long pad_size = piece_length - l;
					
						byte[][] pad_file = new byte[][]{ ".pad".getBytes( Constants.UTF_8 ), ((++pad_details[0]) + "_" + pad_size ).getBytes( Constants.UTF_8 ) };
						
						pad_details[1] += pad_size;
						
						TOTorrentFileImpl	tf = new TOTorrentFileImpl( torrent, files.size(), torrent_offset[0], pad_size, pad_file, pad_file, null );

						tf.setAdditionalProperty( TOTorrentImpl.TK_BEP47_ATTRS, "p".getBytes( Constants.UTF_8 ));
						
						torrent_offset[0] += pad_size;
						
						files.add( tf );
					}
				}
				
				long length = (Long)kid.get( "length" );
				
				byte[][] bpath = path.toArray( new byte[path.size()][] );
				
				byte[] pieces_root = null;
				
				if ( length > 0 ){
					
					pieces_root = (byte[])kid.get( TOTorrentImpl.TK_V2_PIECES_ROOT );
					
					if ( pieces_root == null ){
						
						throw( new TOTorrentException( "Pieces root missing for file " + files.size(), TOTorrentException.RT_DECODE_FAILS ));
					}
				}
				
				TOTorrentFileImpl file = new TOTorrentFileImpl( torrent, files.size(), torrent_offset[0], length, bpath, bpath, pieces_root );
				
				files.add( file );
				
				torrent_offset[0] += length;
				
			}else{
				
				path.add( name.getBytes( Constants.UTF_8 ));
				
				try{
					
					lashUpV2Files( torrent, files, path, kid, piece_length, torrent_offset, pad_details );
					
				}finally{
					
					path.removeLast();
				}
			}
		}
	}
		
	private class
	FileDetails
	{
		final long			length;
		final byte[]		root_hash;
		final byte[]		pieces_layer;
		
		FileDetails(
			long		_length,
			byte[]		_root_hash,
			byte[]		_pieces_layer )
		{
			length			= _length;
			root_hash		= _root_hash;
			pieces_layer	= _pieces_layer;
		}
	}
	
	protected interface
	Adapter
	{
		public boolean
		ignore(
			String		name );
		
		public File
		resolveFile(
			int			index,
			File		file,
			String		relative_file );
		
		public void
		reportHashedBytes(
			long		bytes );
		
		public void
		report(
			String		resource_key );
		
		public boolean
		cancelled();
	}
	
	public static void
	main(
		String[]	args )
	{
		try{

			int piece_size = 524288;
			
			File dir = new File( "D:\\Downloads\\bittorrent-v1-v2-hybrid-test" );

			TOTorrentCreateV2Impl creator = 
				new TOTorrentCreateV2Impl(
					dir,  
					piece_size,
					new Adapter()
					{
						@Override
						public boolean ignore(String name){
							return false;
						}
						
						@Override
						public File resolveFile(int index,File file, String relative_file){
							System.out.println( "resolve: " + relative_file );
							return( null );
						}
						
						@Override
						public void reportHashedBytes(long bytes){							
						}
						
						@Override
						public void report(String resource_key){
							System.out.println( resource_key );
						}
						
						@Override
						public boolean cancelled(){
							return false;
						}
					});
						
			Map<String,Object> torrent = creator.create(); 
	
			System.out.println( "size=" + creator.total_file_size + ", padding=" + creator.total_v1_padding_size );
			
			//byte[] enc = BEncoder.encode( torrent );
			
			//System.out.println( new String( enc ));
			
		}catch( Throwable e ){
			
			e.printStackTrace();
		}
	}
}
