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
			
			details.put( "pieces root", result.root_hash );
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
			
			int digest_length = sha256.getDigestLength();
									
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
