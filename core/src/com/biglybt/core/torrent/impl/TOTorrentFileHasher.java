/*
 * Created on Oct 5, 2003
 * Created by Paul Gardner
 * Modified Apr 13, 2004 by Alon Rohter
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

package com.biglybt.core.torrent.impl;


import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.util.ED2KHasher;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.SHA1Hasher;

public class
TOTorrentFileHasher
{
	private static byte[]	fake_sha1_hash = new byte[20];
	
	private final boolean	do_other_per_file_hash;
	private final int		piece_length;

	private final List<byte[]>	pieces = new LinkedList<>();

	private final byte[]	buffer;
	private int				buffer_pos;

	private SHA1Hasher					overall_sha1_hash;
	private ED2KHasher					overall_ed2k_hash;

	private byte[]						sha1_digest;
	private byte[]						ed2k_digest;

	private byte[]						per_file_sha1_digest;
	private byte[]						per_file_ed2k_digest;

	private final TOTorrentFileHasherListener	listener;

	private boolean		skip_hashing;
	
	private boolean		cancelled;

	protected
	TOTorrentFileHasher(
		boolean							_do_other_overall_hashes,
		boolean							_do_other_per_file_hash,
		int								_piece_length,
		TOTorrentFileHasherListener		_listener )
	{
		if ( _do_other_overall_hashes ){
		  overall_sha1_hash 	= new SHA1Hasher();

		  overall_ed2k_hash 	= new ED2KHasher();
		}

		do_other_per_file_hash	= _do_other_per_file_hash;
		piece_length			= _piece_length;
		listener				= _listener;

		buffer = new byte[piece_length];
	}
	
	protected void
	setSkipHashing(
		boolean	b )
	{
		skip_hashing = b;
	}
	
	protected long
	add(
		File		_file )

		throws TOTorrentException
	{
		if ( skip_hashing ){
			
			long	file_length = _file.length();
			
			long 	rem = file_length;
			
			while( rem > 0 ){
				
				int len = (int)Math.min( rem, piece_length - buffer_pos );
				
				rem	-= len;
				
				buffer_pos += len;
				
				if ( buffer_pos == piece_length ){
					
					pieces.add( fake_sha1_hash );
					
					buffer_pos = 0;
				}
			}
			
			return( file_length );
			
		}else{
			long		file_length = 0;
	
			InputStream is = null;
	
			SHA1Hasher	sha1_hash		= null;
			ED2KHasher	ed2k_hash		= null;
	
			try{
				if ( do_other_per_file_hash ){
	
					sha1_hash		= new SHA1Hasher();
					ed2k_hash		= new ED2KHasher();
				}
	
				is = new BufferedInputStream(FileUtil.newFileInputStream( _file ), 65536);
	
				while(true){
	
					if ( cancelled ){
	
						throw( new TOTorrentException( 	"TOTorrentCreate: operation cancelled",
														TOTorrentException.RT_CANCELLED ));
					}
	
					int	len = is.read( buffer, buffer_pos, piece_length - buffer_pos );
	
					if ( len > 0 ){
	
						if ( do_other_per_file_hash ){
	
							sha1_hash.update( buffer, buffer_pos, len );
							ed2k_hash.update( buffer, buffer_pos, len );
						}
	
	
						file_length += len;
	
						buffer_pos += len;
	
						if ( buffer_pos == piece_length ){
	
							// hash this piece
	
							byte[] hash = new SHA1Hasher().calculateHash(buffer);
	
							if ( overall_sha1_hash != null ){
	
								overall_sha1_hash.update( buffer );
								overall_ed2k_hash.update( buffer );
							}
	
							pieces.add( hash );
	
							if ( listener != null ){
	
								listener.pieceHashed( pieces.size() );
							}
	
							buffer_pos = 0;
						}
					}else{
	
						break;
					}
				}
	
				if ( do_other_per_file_hash ){
	
					per_file_sha1_digest = sha1_hash.getDigest();
					per_file_ed2k_digest = ed2k_hash.getDigest();
				}
	
			}catch( TOTorrentException e ){
	
				throw( e );
	
			}catch( Throwable e ){
	
				throw( new TOTorrentException( 	"TOTorrentFileHasher: file read fails '" + e.toString() + "'",
												TOTorrentException.RT_READ_FAILS ));
			}finally {
				if (is != null) {
					try {
						is.close();
					}
					catch (Exception e) {
					}
				}
			}

			return( file_length );
		}
	}

	protected void
	addPad(
		int		pad_length)

		throws TOTorrentException
	{
		if ( skip_hashing ){
						
			long 	rem = pad_length;
			
			while( rem > 0 ){
				
				int len = (int)Math.min( rem, piece_length - buffer_pos );
				
				rem	-= len;
				
				buffer_pos += len;
				
				if ( buffer_pos == piece_length ){
					
					pieces.add( fake_sha1_hash );
					
					buffer_pos = 0;
				}
			}
		}else{
			
			InputStream is = null;

			try{
	
				is = new ByteArrayInputStream( new byte[ pad_length ]);
	
				while(true){
	
					if ( cancelled ){
	
						throw( new TOTorrentException( 	"TOTorrentCreate: operation cancelled",
														TOTorrentException.RT_CANCELLED ));
					}
	
					int	len = is.read( buffer, buffer_pos, piece_length - buffer_pos );
	
					if ( len > 0 ){
	
	
						buffer_pos += len;
	
						if ( buffer_pos == piece_length ){
	
							// hash this piece
	
							byte[] hash = new SHA1Hasher().calculateHash(buffer);
	
							if ( overall_sha1_hash != null ){
	
								overall_sha1_hash.update( buffer );
								overall_ed2k_hash.update( buffer );
							}
	
							pieces.add( hash );
	
							if ( listener != null ){
	
								listener.pieceHashed( pieces.size() );
							}
	
							buffer_pos = 0;
						}
					}else{
	
						break;
					}
				}
	
			}catch( TOTorrentException e ){
	
				throw( e );
	
			}catch( Throwable e ){
	
				throw( new TOTorrentException( 	"TOTorrentFileHasher: file read fails '" + e.toString() + "'",
												TOTorrentException.RT_READ_FAILS ));
			}finally {
				if (is != null) {
					try {
						is.close();
					}
					catch (Exception e) {
					}
				}
			}
		}
	}
	
	protected byte[]
	getPerFileSHA1Digest()
	{
		return( per_file_sha1_digest );
	}

	protected byte[]
	getPerFileED2KDigest()
	{
		return( per_file_ed2k_digest );
	}

	protected byte[][]
	getPieces()

		throws TOTorrentException
	{
		try{
			if ( buffer_pos > 0 ){

				byte[] rem = new byte[buffer_pos];

				System.arraycopy( buffer, 0, rem, 0, buffer_pos );

				pieces.add(new SHA1Hasher().calculateHash(rem));

				if ( overall_sha1_hash != null ){

					overall_sha1_hash.update( rem );
					overall_ed2k_hash.update( rem );
				}

				if ( listener != null ){

					listener.pieceHashed( pieces.size() );
				}

				buffer_pos = 0;
			}

			if ( overall_sha1_hash != null && sha1_digest == null ){

				sha1_digest	= overall_sha1_hash.getDigest();
				ed2k_digest	= overall_ed2k_hash.getDigest();
			}

			byte[][] res = new byte[pieces.size()][];

			pieces.toArray( res );

			return( res );

		}catch( Throwable e ){

			throw( new TOTorrentException( 	"TOTorrentFileHasher: file read fails '" + e.toString() + "'",
											TOTorrentException.RT_READ_FAILS ));
		}
	}

	protected byte[]
	getED2KDigest()

		throws TOTorrentException
	{
		if ( ed2k_digest == null ){

			getPieces();
		}

		return( ed2k_digest );
	}

	protected byte[]
	getSHA1Digest()

		throws TOTorrentException
	{
		if ( sha1_digest == null ){

			getPieces();
		}

		return( sha1_digest );
	}

	protected void
	cancel()
	{
		cancelled	= true;
	}
}
