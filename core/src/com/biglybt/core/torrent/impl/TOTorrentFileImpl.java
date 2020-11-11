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
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.*;

public class
TOTorrentFileImpl
	implements TOTorrentFile
{
	private final TOTorrentImpl	torrent;
	
	private final int		index;
	private final long		file_length;
	private final byte[][]	path_components;
	private final byte[][]	path_components_utf8;

	private final int		first_piece_number;
	private final int		last_piece_number;

	private Map				additional_properties_maybe_null;

	private final boolean	is_utf8;

	private boolean		attr_pad_file;

	private final TOTorrentFileHashTreeImpl	hash_tree;
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
			
			attr_pad_file = attr_str.contains( "p" );
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
		if ( root_hash == null && !attr_pad_file ){
			
			torrent.fixupRootHashes();
		}
		
		return( root_hash );
	}
	
	protected void
	setRootHash(
		byte[]		h )
	{
		root_hash	= h;
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
		return( attr_pad_file );
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
