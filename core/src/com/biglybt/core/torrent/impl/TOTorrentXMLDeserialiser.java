/*
 * File    : TOTorrentXMLDeserialiser.java
 * Created : 14-Oct-2003
 * By      : parg
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

/**
 * @author parg
 *
 */

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentAnnounceURLGroup;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.xml.simpleparser.SimpleXMLParserDocumentFactory;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocument;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentAttribute;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentException;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentNode;

public class
TOTorrentXMLDeserialiser
{
	public
	TOTorrentXMLDeserialiser()
	{
	}

	public TOTorrent
	deserialise(
		File		file )

		throws TOTorrentException
	{
		try{

			SimpleXMLParserDocument	doc = SimpleXMLParserDocumentFactory.create( file );

			TOTorrent res = decodeRoot( doc );

			return( res );

		}catch( SimpleXMLParserDocumentException e ){

			throw( new TOTorrentException( "XML Parse Fails: " + e.getMessage(), TOTorrentException.RT_DECODE_FAILS ));
		}
	}

	protected TOTorrent
	decodeRoot(
		SimpleXMLParserDocument		doc )

		throws TOTorrentException
	{
		String root_name = doc.getName();

		if ( root_name.equalsIgnoreCase("TORRENT")){

			TOTorrentImpl	torrent = new TOTorrentImpl();

			SimpleXMLParserDocumentNode[] kids = doc.getChildren();

			URL		announce_url 					= null;

			byte[]	torrent_hash 			= null;
			byte[]	torrent_hash_override 	= null;

			for (int i=0;i<kids.length;i++){

				SimpleXMLParserDocumentNode	kid = kids[i];

				String	name = kid.getName();

				if ( name.equalsIgnoreCase( "ANNOUNCE_URL")){

					try{

						announce_url = new URL(kid.getValue());

					}catch( MalformedURLException e ){

						throw( new TOTorrentException( "ANNOUNCE_URL malformed", TOTorrentException.RT_DECODE_FAILS));
					}

				}else if ( name.equalsIgnoreCase( "ANNOUNCE_LIST")){

					SimpleXMLParserDocumentNode[]	set_nodes = kid.getChildren();

					TOTorrentAnnounceURLGroup group = torrent.getAnnounceURLGroup();

					TOTorrentAnnounceURLSet[]	sets = new TOTorrentAnnounceURLSet[set_nodes.length];

					for (int j=0;j<sets.length;j++){

						SimpleXMLParserDocumentNode[]	url_nodes = set_nodes[j].getChildren();

						URL[] urls = new URL[url_nodes.length];

						for (int k=0;k<urls.length;k++){

							try{

								urls[k] = new URL(url_nodes[k].getValue());

							}catch( MalformedURLException e ){

								throw( new TOTorrentException( "ANNOUNCE_LIST malformed", TOTorrentException.RT_DECODE_FAILS));
							}
						}

						sets[j] = group.createAnnounceURLSet( urls );
					}

					group.setAnnounceURLSets( sets );

				}else if ( name.equalsIgnoreCase( "COMMENT")){

					torrent.setComment( readLocalisableString( kid ));

				}else if ( name.equalsIgnoreCase( "CREATED_BY")){

					torrent.setCreatedBy( readLocalisableString(kid));

				}else if ( name.equalsIgnoreCase( "CREATION_DATE")){

					torrent.setCreationDate( readGenericLong( kid ).longValue());

				}else if ( name.equalsIgnoreCase( "TORRENT_HASH")){

					torrent_hash	= readGenericBytes( kid );

				}else if ( name.equalsIgnoreCase( "TORRENT_HASH_OVERRIDE")){

					torrent_hash_override	= readGenericBytes( kid );

				}else if ( name.equalsIgnoreCase( "INFO" )){

					decodeInfo( kid, torrent );

				}else{

					mapEntry entry = readGenericMapEntry( kid );

					torrent.addAdditionalProperty( entry.name, entry.value );
				}
			}

			if ( announce_url == null ){

				throw( new TOTorrentException( "ANNOUNCE_URL missing", TOTorrentException.RT_DECODE_FAILS));
			}

			torrent.setAnnounceURL( announce_url );

			if ( torrent_hash_override != null ){

				try{
					torrent.setHashOverride( torrent_hash_override );

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}

			if ( torrent_hash != null ){

				if ( !Arrays.equals( torrent.getHash(), torrent_hash )){

					throw( 	new TOTorrentException( "Hash differs - declared TORRENT_HASH and computed hash differ. If this really is the intent (unlikely) then remove the TORRENT_HASH element",
							TOTorrentException.RT_DECODE_FAILS));
				}
			}

			return( torrent );
		}else{

			throw( new TOTorrentException( "Invalid root element", TOTorrentException.RT_DECODE_FAILS));
		}
	}

	protected void
	decodeInfo(
		SimpleXMLParserDocumentNode		doc,
		TOTorrentImpl					torrent )

		throws TOTorrentException
	{
		SimpleXMLParserDocumentNode[] kids = doc.getChildren();

		byte[]	torrent_name 	= null;
		long	torrent_length	= 0;

		SimpleXMLParserDocumentNode[] file_nodes	= null;

		for (int i=0;i<kids.length;i++){

			SimpleXMLParserDocumentNode	kid = kids[i];

			String	name = kid.getName();

			if ( name.equalsIgnoreCase( "PIECE_LENGTH")){

				torrent.setPieceLength( readGenericLong( kid ).longValue());

			}else if ( name.equalsIgnoreCase( "LENGTH")){

				torrent.setSimpleTorrent( true );

				torrent_length =  readGenericLong( kid ).longValue();

			}else if ( name.equalsIgnoreCase( "NAME")){

				torrent.setName( readLocalisableString( kid ));

			}else if ( name.equalsIgnoreCase( "FILES" )){

				torrent.setSimpleTorrent( false );

				file_nodes = kid.getChildren();

			}else if ( name.equalsIgnoreCase( "PIECES" )){

				SimpleXMLParserDocumentNode[]	piece_nodes = kid.getChildren();

				byte[][]	pieces = new byte[piece_nodes.length][];

				for (int j=0;j<pieces.length;j++){

					pieces[j] = readGenericBytes( piece_nodes[j] );
				}

				torrent.setPieces( pieces );

			}else{

				mapEntry entry = readGenericMapEntry( kid );

				torrent.addAdditionalInfoProperty( entry.name, entry.value );
			}
		}

		if ( torrent.isSimpleTorrent()){

			torrent.setFiles(
				new TOTorrentFileImpl[]{
						new TOTorrentFileImpl( 	torrent,
												0,
												0,
												torrent_length,
												new byte[][]{ torrent.getName()})});
		}else{

			if ( file_nodes == null ){

				throw( new TOTorrentException( "FILES element missing", TOTorrentException.RT_DECODE_FAILS));
			}

			TOTorrentFileImpl[]	files = new TOTorrentFileImpl[ file_nodes.length ];

			long	offset = 0;

			for (int j=0;j<files.length;j++){

				SimpleXMLParserDocumentNode	file_node = file_nodes[j];

				SimpleXMLParserDocumentNode[]	file_entries = file_node.getChildren();

				long		file_length	= 0;

				boolean		length_entry_found	= false;

				byte[][]	path_comps	= null;

				Vector	additional_props	= new Vector();

				for ( int k=0;k<file_entries.length;k++){

					SimpleXMLParserDocumentNode	file_entry = file_entries[k];

					String	entry_name = file_entry.getName();

					if ( entry_name.equalsIgnoreCase( "LENGTH" )){

						file_length = readGenericLong(file_entry).longValue();

						length_entry_found = true;

					}else if ( entry_name.equalsIgnoreCase( "PATH" )){

						SimpleXMLParserDocumentNode[]	path_nodes = file_entry.getChildren();

						path_comps = new byte[path_nodes.length][];

						for (int n=0;n<path_nodes.length;n++){

							path_comps[n] = readLocalisableString( path_nodes[n] );
						}
					}else{

						additional_props.addElement( readGenericMapEntry( file_entry ));
					}
				}

				if ( (!length_entry_found) || path_comps == null ){

					throw( new TOTorrentException( "FILE element invalid (file length = " + file_length + ")", TOTorrentException.RT_DECODE_FAILS));
				}

				files[j] = new TOTorrentFileImpl( torrent, j, offset, file_length, path_comps );

				offset += file_length;

				for (int k=0;k<additional_props.size();k++){

					mapEntry	entry = (mapEntry)additional_props.elementAt(k);

					files[j].setAdditionalProperty( entry.name, entry.value );
				}

			}

			torrent.setFiles( files );
		}
	}

	protected mapEntry
	readGenericMapEntry(
		SimpleXMLParserDocumentNode		node )

		throws TOTorrentException
	{
		if ( !node.getName().equalsIgnoreCase("KEY")){

			throw( new TOTorrentException( "Additional property invalid, must be KEY node", TOTorrentException.RT_DECODE_FAILS));
		}

		String 	name = node.getAttribute("name").getValue();

		SimpleXMLParserDocumentNode[]	kids = node.getChildren();

		if ( kids.length != 1 ){

			throw( new TOTorrentException( "Additional property invalid, KEY must have one child", TOTorrentException.RT_DECODE_FAILS));
		}

		String	type = kids[0].getName();

		Object  value = readGenericValue( kids[0] );

		return( new mapEntry( name, value ));
	}

	protected Object
	readGenericValue(
		SimpleXMLParserDocumentNode	node )

		throws TOTorrentException
	{
		String	name = node.getName();

		if ( name.equalsIgnoreCase( "BYTES")){

			return( readGenericBytes( node ));

		}else if ( name.equalsIgnoreCase( "LONG" )){

			return( readGenericLong( node ));

		}else if ( name.equalsIgnoreCase( "LIST" )){

			return( readGenericList( node ));

		}else if ( name.equalsIgnoreCase( "MAP" )){

			return( readGenericMap( node ));

		}else{

			throw( new TOTorrentException( "Additional property invalid, sub-key '" + name + "' not recognised", TOTorrentException.RT_DECODE_FAILS));
		}
	}

	protected byte[]
	readGenericBytes(
		SimpleXMLParserDocumentNode node )

		throws TOTorrentException
	{
		String value = node.getValue();

		byte[]	res = new byte[value.length()/2];

		for (int i=0;i<res.length;i++){

			res[i] = (byte)Integer.parseInt( value.substring(i*2,i*2+2), 16 );

		}

		return( res );
		/*
		try{

			return( URLDecoder.decode( node.getValue(), Constants.BYTE_ENCODING ).getBytes( Constants.BYTE_ENCODING ));

		}catch( UnsupportedEncodingException e ){

			throw( new TOTorrentException( "bytes invalid - unsupported encoding", TOTorrentException.RT_DECODE_FAILS));
		}
		*/
	}

	protected Long
	readGenericLong(
		SimpleXMLParserDocumentNode node )

		throws TOTorrentException
	{
		String	value = node.getValue();

		try{

			return( new Long( value ));

		}catch( Throwable e ){

			throw( new TOTorrentException( "long value invalid for '" + node.getName() + "'", TOTorrentException.RT_DECODE_FAILS));
		}
	}

	protected Map
	readGenericMap(
		SimpleXMLParserDocumentNode node )

		throws TOTorrentException
	{
		Map res = new HashMap();

		SimpleXMLParserDocumentNode[]	kids = node.getChildren();

		for (int i=0;i<kids.length;i++){

			mapEntry	entry = readGenericMapEntry( kids[i] );

			res.put( entry.name, entry.value );
		}

		return( res );
	}

	protected byte[]
	readLocalisableString(
		SimpleXMLParserDocumentNode	kid )

		throws TOTorrentException
	{
		SimpleXMLParserDocumentAttribute attr = kid.getAttribute("encoding");

		if ( attr == null || attr.getValue().equalsIgnoreCase("bytes")){

			return( readGenericBytes( kid ));
		}

		try{

			return( kid.getValue().getBytes( Constants.DEFAULT_ENCODING ));

		}catch( UnsupportedEncodingException e ){

			throw( new TOTorrentException( "bytes invalid - unsupported encoding", TOTorrentException.RT_DECODE_FAILS));
		}
	}

	protected List
	readGenericList(
		SimpleXMLParserDocumentNode node )

		throws TOTorrentException
	{
		List res = new ArrayList();

		SimpleXMLParserDocumentNode[]	kids = node.getChildren();

		for (int i=0;i<kids.length;i++){

			res.add( readGenericValue( kids[i]));
		}

		return( res );
	}

	protected static class
	mapEntry
	{
		final String		name;
		final Object		value;

		mapEntry(
			String	_name,
			Object	_value )
		{
			name	= _name;
			value	= _value;
		}
	}
}
