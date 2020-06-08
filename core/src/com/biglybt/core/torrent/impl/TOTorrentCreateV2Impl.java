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
import com.biglybt.core.util.ByteArrayHashMap;
import com.biglybt.core.util.ByteEncodedKeyHashMap;
import com.biglybt.core.util.Constants;

import java.security.*;


public class 
TOTorrentCreateV2Impl
{
	private final static int block_size = 16*1024;

	private final static int digest_length = 32;	// sha256 length

	private final File		root;
	private final long		piece_size;
	private final Adapter	adapter;
	
	private Map<String,Map>				file_tree 		= new TreeMap<>();
	
	private ByteArrayHashMap<byte[]>	piece_layers 	= new ByteArrayHashMap<>();

	private long	total_file_size;
	private long	total_v1_padding_size;

	private int		file_index	= 0;
	
	private int		files_ignored;	
	
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
		if ( root.isFile()){
			
			processFile( root, "" );
			
		}else{
			
			processDirectory( root, "" );
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
		String						relative_path )
	
		throws TOTorrentException
	{
		if ( adapter.ignore( file.getName())){
			
			files_ignored++;
			
			return;
		}
		
		Map<String,Map>	file_node = new TreeMap<>();
		
		file_tree.put( file.getName(), file_node );

		if ( relative_path.isEmpty()){
			
			relative_path = file.getName();
			
		}else{
			
			relative_path += File.separator + file.getName();
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
		
			total_v1_padding_size += piece_size - excess;
		}
		
		total_file_size += length;
	}
	
	public void
	processDirectory(
		File						dir,
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
		
		if ( relative_path.isEmpty()){
			
			relative_path = dir.getName();
			
		}else{
			
			relative_path += File.separator + dir.getName();
		}

		for ( String name: files ){
			
			if ( name.equals( "." ) || name.equals( ".." )){
				
				continue;
			}
			
			File file = new File( dir, name );
			
			if ( file.isFile()){
				
				processFile( file, relative_path );
				
			}else if ( file.isDirectory()){
				
				Map<String,Map>	sub_tree = new TreeMap<>();
				
				file_tree.put( name, sub_tree );
								
				processDirectory( file, relative_path );
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
				
			File link = adapter.resolveFile( file_index++, file, relative_path );
			
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
				
				FileInputStream fis = new FileInputStream(file);
				
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
	lashUpV1Info(
		TOTorrentImpl	torrent )
	
		throws TOTorrentException
	{
		Map piece_layers = torrent.getAdditionalMapProperty( TOTorrentImpl.TK_V2_PIECE_LAYERS );
		
		if ( piece_layers == null ){
			
			throw( new TOTorrentException( "V2 piece layers missing", TOTorrentException.RT_DECODE_FAILS ));
		}
		
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
		
		for ( TOTorrentFileImpl file: files ){
		
			if ( file.isPadFile()){
				
				continue;	// artifical entry
			}
			
			long length = file.getLength();
			
			if ( length > 0 ){
				
				byte[] pieces_root = (byte[])file.getAdditionalProperties().get( TOTorrentImpl.TK_V2_PIECES_ROOT );

				if ( length <= piece_length ){
								
					pieces[piece_num++] = pieces_root;
				
				}else{
				
					byte[] piece_layer = (byte[])piece_layers.get( new String( pieces_root, Constants.BYTE_ENCODING_CHARSET ));
					
					for ( int i=0;i<piece_layer.length; i+= digest_length ){
						
						byte[] hash = new byte[ digest_length ];
						
						System.arraycopy( piece_layer, i, hash, 0, digest_length );
						
						pieces[piece_num++] = hash;
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
					
						String pad_file = ".pad" + File.separator + (++pad_details[0]) + "_" + pad_size;
							
						pad_details[1] += pad_size;
						
						TOTorrentFileImpl	tf = new TOTorrentFileImpl( torrent, files.size(), torrent_offset[0], pad_size, pad_file );

						tf.setAdditionalProperty( TOTorrentImpl.TK_BEP47_ATTRS, "p".getBytes( Constants.UTF_8 ));
						
						torrent_offset[0] += pad_size;
						
						files.add( tf );
					}
				}
				
				long length = (Long)kid.get( "length" );
				
				byte[][] bpath = path.toArray( new byte[path.size()][] );
				
				TOTorrentFileImpl file = new TOTorrentFileImpl( torrent, files.size(), torrent_offset[0], length, bpath, bpath );
				
				if ( length > 0 ){
					
					byte[] pieces_root = (byte[])kid.get( TOTorrentImpl.TK_V2_PIECES_ROOT );
					
					file.setAdditionalProperty( TOTorrentImpl.TK_V2_PIECES_ROOT, pieces_root );
				}
				
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
