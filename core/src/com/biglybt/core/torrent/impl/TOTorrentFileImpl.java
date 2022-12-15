/*
 * File    : TOTorrentFileImpl.java
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

package com.biglybt.core.torrent.impl;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.*;

import com.biglybt.core.internat.LocaleTorrentUtil;
import com.biglybt.core.internat.LocaleUtil;
import com.biglybt.core.internat.LocaleUtilDecoder;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.*;

public class
TOTorrentFileImpl
	implements TOTorrentFile
{
	private static final byte FLAG_BEP47_PAD			= 0x01;
	private static final byte FLAG_OTHER_PAD			= 0x02;
	private static final byte FLAG_OTHER_PAD_CHECKED	= 0x04;
	
	private final TOTorrentImpl	torrent;
	
	private final int		index;
	private final long		file_length;
	private final byte[][]	path_components;
	private final byte[][]	path_components_utf8;

	private final int		first_piece_number;
	private final int		last_piece_number;

	private Map				additional_properties_maybe_null;

	private final boolean	is_utf8;

	private byte			flags;

	private TOTorrentFileHashTreeImpl	hash_tree;
	private byte[]	root_hash;
	
	
	protected
	TOTorrentFileImpl(
		TOTorrentImpl	_torrent,
		int				_index,
		long			_torrent_offset,
		long			_len,
		String			_path )

		throws TOTorrentException
	{
		torrent			= _torrent;
		index			= _index;
		file_length		= _len;

		first_piece_number 	= (int)( _torrent_offset / torrent.getPieceLength());
		last_piece_number	= (int)(( _torrent_offset + file_length - 1 ) /  torrent.getPieceLength());

		hash_tree	= null;
		
		is_utf8	= true;

		Vector temp = new Vector();

		int pos = 0;

		while (true) {

			int p1 = _path.indexOf(File.separator, pos);

			if (p1 == -1) {

				temp.add(_path.substring(pos).getBytes(Constants.DEFAULT_ENCODING_CHARSET));
				break;
			}

			temp.add(_path.substring(pos, p1).getBytes(Constants.DEFAULT_ENCODING_CHARSET));

			pos = p1 + 1;
		}

		path_components = new byte[temp.size()][];

		temp.copyInto(path_components);

		path_components_utf8 = new byte[temp.size()][];

		temp.copyInto(path_components_utf8);

		checkComponents();
	}

	protected
	TOTorrentFileImpl(
		TOTorrentImpl	_torrent,
		int				_index,
		long			_torrent_offset,
		long			_len,
		byte[][]		_path_components )

		throws TOTorrentException
	{
		torrent				= _torrent;
		index				= _index;
		file_length			= _len;
		path_components		= _path_components;
		path_components_utf8 = null;

		first_piece_number 	= (int)( _torrent_offset / torrent.getPieceLength());
		last_piece_number	= (int)(( _torrent_offset + file_length - 1 ) /  torrent.getPieceLength());

		hash_tree	= null;

		is_utf8				= false;

		checkComponents();
	}

	protected
	TOTorrentFileImpl(
		TOTorrentImpl	_torrent,
		int				_index,
		long			_torrent_offset,
		long			_len,
		byte[][]		_path_components,
		byte[][]		_path_components_utf8 )

		throws TOTorrentException
	{
		this( _torrent, _index, _torrent_offset, _len, _path_components, _path_components_utf8, null );
	}
		
	protected
	TOTorrentFileImpl(
		TOTorrentImpl	_torrent,
		int				_index,
		long			_torrent_offset,
		long			_len,
		byte[][]		_path_components,
		byte[][]		_path_components_utf8,
		byte[]			_v2_root_hash )

		throws TOTorrentException
	{
		torrent				= _torrent;
		index				= _index;
		file_length			= _len;
		path_components		= _path_components;
		path_components_utf8 = _path_components_utf8;

		first_piece_number 	= (int)( _torrent_offset / torrent.getPieceLength());
		last_piece_number	= (int)(( _torrent_offset + file_length - 1 ) /  torrent.getPieceLength());

		hash_tree	= _v2_root_hash==null?null:new TOTorrentFileHashTreeImpl( this, _v2_root_hash );
		root_hash	= _v2_root_hash;
		
		is_utf8				= false;

		checkComponents();
	}

	protected void
	checkComponents()

		throws TOTorrentException
	{
		byte[][][] to_do = { path_components, path_components_utf8 };

		for (byte[][] pc: to_do ){

			if ( pc == null ){
				continue;
			}

			for (int i=0;i<pc.length;i++){

				byte[] comp = pc[i];
				if (comp.length == 2 && comp[0] == (byte) '.' && comp[1] == (byte) '.')
					throw (new TOTorrentException("Torrent file contains illegal '..' component", TOTorrentException.RT_DECODE_FAILS));

				// intern directories as they're likely to repeat
				if(i < (pc.length - 1))
					pc[i] = StringInterner.internBytes(pc[i]);
			}
		}
	}

	@Override
	public TOTorrentImpl
	getTorrent()
	{
		return( torrent );
	}

	@Override
	public int
	getIndex()
	{
		return( index );
	}

	@Override
	public long
	getLength()
	{
		return( file_length );
	}

	public byte[][]
	getPathComponentsBasic()
	{
		return( path_components );
	}

	@Override
	public byte[][]
	getPathComponents()
	{
		return path_components_utf8 == null ? path_components : path_components_utf8;
	}

	public byte[][]
	getPathComponentsUTF8()
	{
		return( path_components_utf8 );
	}


	protected boolean
	isUTF8()
	{
		return( is_utf8 );
	}

	protected void
	setAdditionalProperty(
		String		name,
		Object		value )
	{
		if ( additional_properties_maybe_null == null ){

			additional_properties_maybe_null = new LightHashMap();
		}

		additional_properties_maybe_null.put( name, value );
		
		if ( name.equals( TOTorrentImpl.TK_BEP47_ATTRS ) && value instanceof byte[] ){
			
			String attr_str = new String((byte[])value, Constants.UTF_8 );
			
			if ( attr_str.contains( "p" )){
				
				flags |= FLAG_BEP47_PAD;
			}
		}			
	}

	@Override
	public TOTorrentFileHashTreeImpl
	getHashTree()
	{
		return( hash_tree );
	}
	
	@Override
	public byte[]
	getRootHash()
	{
		if ( root_hash == null && ( flags & FLAG_BEP47_PAD ) == 0 ){
			
			torrent.fixupRootHashes();
		}
		
		return( root_hash );
	}
	
	protected void
	setHashTree(
		TOTorrentFileHashTreeImpl		_hash_tree )
	{
		hash_tree	= _hash_tree;
		root_hash	= hash_tree.getRootHash();
	}
	
	@Override
	public Map
	getAdditionalProperties()
	{
		return( additional_properties_maybe_null );
	}

	@Override
	public int
	getFirstPieceNumber()
	{
		return( first_piece_number );
	}

	@Override
	public int
	getLastPieceNumber()
	{
		return( last_piece_number );
	}

	@Override
	public int
	getNumberOfPieces()
	{
		return( getLastPieceNumber() - getFirstPieceNumber() + 1 );
	}

	@Override
	public boolean 
	isPadFile()
	{		
		if (( flags & FLAG_BEP47_PAD ) != 0 ){
			
			return( true );
		}
		
		if (( flags & FLAG_OTHER_PAD_CHECKED ) == 0 ){

			byte[][] comps = path_components_utf8 == null ? path_components : path_components_utf8;
			
			byte[] last = comps[ comps.length-1];
	
				// "_____padding_file_0_" etc

			byte	other_pad = 0;
			
			if ( last.length > 20 ){
				
				if ( 	last[0]  == '_' &&
						last[1]  == '_' &&
						last[2]  == '_' &&
						last[3]  == '_' &&
						last[4]  == '_' &&
						last[5]  == 'p' &&
						last[6]  == 'a' &&
						last[7]  == 'd' &&
						last[8]  == 'd' &&
						last[9]  == 'i' &&
						last[10] == 'n' &&
						last[11] == 'g' &&
						last[12] == '_' &&
						last[13] == 'f' &&
						last[14] == 'i' &&
						last[15] == 'l' &&
						last[16] == 'e' &&
						last[17] == '_' ){
						
					int pos = 18;
											
					while( pos < last.length ){
						
						byte c = last[pos];
						
						if ( c < '0' || c > '9' ){
							
							break;
							
						}else{
							
							pos++;
						}
					}
						
					if ( pos > 18 && pos < last.length && last[pos] == '_' ){
						
						other_pad = FLAG_OTHER_PAD;
					}
				}
			}
			
			flags |= ( FLAG_OTHER_PAD_CHECKED | other_pad );
		}
		
		return( ( flags & FLAG_OTHER_PAD ) != 0 );
	}
	
	@Override
	public String getRelativePath() {
		if (torrent == null) {
			return "";
		}

		byte[][] pathComponentsUTF8 = getPathComponentsUTF8();
		if (pathComponentsUTF8 != null) {
			StringBuilder sRelativePathSB = null;

			for (int j = 0; j < pathComponentsUTF8.length; j++) {

				try {
					String comp;
					try {
						comp =  new String(pathComponentsUTF8[j], "utf8");
					} catch (UnsupportedEncodingException e) {
						System.out.println("file - unsupported encoding!!!!");
						comp = "UnsupportedEncoding";
					}

					comp = FileUtil.convertOSSpecificChars(comp, j != pathComponentsUTF8.length-1 );

					if ( j == 0 ){
						if ( pathComponentsUTF8.length == 1 ){
							return( comp );
						}else{
							sRelativePathSB = new StringBuilder( 512 );
						}
					}else{
						sRelativePathSB.append(File.separator);
					}

					sRelativePathSB.append( comp );
				} catch (Exception ex) {
					Debug.out(ex);
				}

			}
			return sRelativePathSB==null?"":sRelativePathSB.toString();
		}

		LocaleUtilDecoder decoder = null;
		try {
			decoder = LocaleTorrentUtil.getTorrentEncodingIfAvailable(torrent);
			if (decoder == null) {
				LocaleUtil localeUtil = LocaleUtil.getSingleton();
				decoder = localeUtil.getSystemDecoder();
			}
		} catch (Exception e) {
			// Do Nothing
		}

		if (decoder != null) {
			StringBuilder sRelativePathSB = null;
			byte[][]components = getPathComponents();
			for (int j = 0; j < components.length; j++) {

				try {
					String comp;
					try {
						comp = decoder.decodeString(components[j]);
					} catch (UnsupportedEncodingException e) {
						System.out.println("file - unsupported encoding!!!!");
						try {
							comp = new String(components[j]);
						} catch (Exception e2) {
							comp = "UnsupportedEncoding";
						}
					}

					comp = FileUtil.convertOSSpecificChars(comp, j != components.length-1 );

					if ( j == 0 ){
						if ( components.length == 1 ){
							return( comp );
						}else{
							sRelativePathSB = new StringBuilder( 512 );
						}
					}else{
						sRelativePathSB.append(File.separator);
					}

					sRelativePathSB.append( comp );
				} catch (Exception ex) {
					Debug.out(ex);
				}

			}
			return sRelativePathSB==null?"":sRelativePathSB.toString();
		}else{
			return( "" );
		}
	}

	/**
	 * @since 4.1.0.5
	 */
	public Map serializeToMap() {
		Map	file_map = new HashMap();

		file_map.put( TOTorrentImpl.TK_LENGTH, new Long( getLength()));

		List<byte[]> path = new ArrayList<>();

		file_map.put( TOTorrentImpl.TK_PATH, path );

		byte[][]	path_comps = getPathComponentsBasic();

		if (path_comps != null) {
			Collections.addAll(path, path_comps);
		}

		if (path_comps != null && isUTF8()){

			List<byte[]> utf8_path = new ArrayList<>();

			file_map.put( TOTorrentImpl.TK_PATH_UTF8, utf8_path );

			Collections.addAll(utf8_path, path_comps);

		} else {

			byte[][]	utf8_path_comps = getPathComponentsUTF8();

			if (utf8_path_comps != null) {
  			List<byte[]> utf8_path = new ArrayList<>();

  			file_map.put( TOTorrentImpl.TK_PATH_UTF8, utf8_path );

  			Collections.addAll(utf8_path, utf8_path_comps);
			}
		}

		Map file_additional_properties = getAdditionalProperties();

		if ( file_additional_properties != null ){
			file_map.putAll(file_additional_properties);
		}

		return file_map;
	}
}
