/*
 * File    : TOTorrentImpl.java
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


import java.io.*;
import java.net.URL;
import java.util.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.logging.LogRelation;
import com.biglybt.core.torrent.*;
import com.biglybt.core.util.*;

public class
TOTorrentImpl
	extends LogRelation
	implements TOTorrent
{
	protected static final String TK_ANNOUNCE			= "announce";
	protected static final String TK_ANNOUNCE_LIST		= "announce-list";
	protected static final String TK_COMMENT			= "comment";
	protected static final String TK_CREATION_DATE		= "creation date";
	protected static final String TK_CREATED_BY			= "created by";

	protected static final String TK_INFO				= "info";
	protected static final String TK_NAME				= "name";
	protected static final String TK_LENGTH				= "length";
	protected static final String TK_PATH				= "path";
	protected static final String TK_FILES				= "files";
	protected static final String TK_PIECE_LENGTH		= "piece length";
	protected static final String TK_PIECES				= "pieces";

	protected static final String TK_PRIVATE			= "private";

	protected static final String TK_NAME_UTF8			= "name.utf-8";
	protected static final String TK_PATH_UTF8			= "path.utf-8";
	protected static final String TK_COMMENT_UTF8		= "comment.utf-8";

	protected static final String TK_WEBSEED_BT			= "httpseeds";
	protected static final String TK_WEBSEED_GR			= "url-list";

	protected static final String TK_HASH_OVERRIDE		= "hash-override";

	protected static final List	TK_ADDITIONAL_OK_ATTRS =
		Arrays.asList(new String[]{ TK_COMMENT_UTF8, AZUREUS_PROPERTIES, TK_WEBSEED_BT, TK_WEBSEED_GR });

	
	
	private static CopyOnWriteList<TOTorrentListener>		global_listeners = new CopyOnWriteList<>();
	
	public static void
	addGlobalListener(
		TOTorrentListener		listener )
	{
		global_listeners.add( listener );
	}
	
	public static void
	removeGlobalListener(
		TOTorrentListener		listener )
	{
		global_listeners.remove( listener );
	}
	
	private byte[]							torrent_name;
	private byte[]							torrent_name_utf8;

	private byte[]							comment;
	private URL								announce_url;
	private final TOTorrentAnnounceURLGroupImpl	announce_group = new TOTorrentAnnounceURLGroupImpl(this);

	private long		piece_length;
	private byte[][]	pieces;
	private int			number_of_pieces;

	private byte[]		torrent_hash_override;

	private byte[]		torrent_hash;
	private HashWrapper	torrent_hash_wrapper;

	private boolean				simple_torrent;
	private TOTorrentFileImpl[]	files;

	private long				creation_date;
	private byte[]				created_by;

	private Map					additional_properties 		= new LightHashMap(4);
	private final Map					additional_info_properties	= new LightHashMap(4);

	private boolean				created;
	private boolean				serialising;

	private List<TOTorrentListener>	listeners;

	protected final AEMonitor this_mon 	= new AEMonitor( "TOTorrent" );

	private boolean	constructing = true;
	
	/**
	 * Constructor for deserialisation
	 */

	protected
	TOTorrentImpl()
	{
	}

	/**
	 * Constructor for creation
	 */

	protected
	TOTorrentImpl(
		String		_torrent_name,
		URL			_announce_url,
		boolean		_simple_torrent )

		throws TOTorrentException
	{
		created	= true;

		try{

			torrent_name		= _torrent_name.getBytes( Constants.DEFAULT_ENCODING );

			torrent_name_utf8	= torrent_name;

			setAnnounceURL( _announce_url );

			simple_torrent		= _simple_torrent;

		}catch( UnsupportedEncodingException e ){

			throw( new TOTorrentException( 	"Unsupported encoding for '" + _torrent_name + "'",
											TOTorrentException.RT_UNSUPPORTED_ENCODING));
		}
	}

	protected void
	setConstructed()
	{
		constructing = false;
	}
	
	@Override
	public void
	serialiseToBEncodedFile(
		final File		output_file )

		throws TOTorrentException
	{
			// we have to defer marking as created until some kind of persisting occurs as we don't know that the info-hash is "permanent" until#
			// this point (external code can set info-hash internal properties between create + complete )

		if ( created ){

			TorrentUtils.addCreatedTorrent( this );
		}

		byte[]	res = serialiseToByteArray();

        BufferedOutputStream bos = null;

		try{
			File parent = output_file.getParentFile();
			if (parent == null) {
				throw new TOTorrentException( "Path '" + output_file + "' is invalid", TOTorrentException.RT_WRITE_FAILS);
			}

			// We would expect this to be normally true most of the time.
			if (!parent.isDirectory()) {

				// Try to create a directory.
				boolean dir_created = FileUtil.mkdirs(parent);

				// Something strange going on...
				if (!dir_created) {

					// Does it exist already?
					if (parent.exists()) {

						// And it really isn't a directory?
						if (!parent.isDirectory()) {

							// How strange.
							throw new TOTorrentException( "Path '" + output_file + "' is invalid", TOTorrentException.RT_WRITE_FAILS);

						}

						// It is a directory which does exist. But we tested for that earlier. Perhaps it has been created in the
						// meantime.
						else {
							/* do nothing */
						}
					}

					// It doesn't exist, and we couldn't create it.
					else {
						throw new TOTorrentException( "Failed to create directory '" + parent + "'", TOTorrentException.RT_WRITE_FAILS );
					}
				} // end if (!dir_created)

			} // end if (!parent.isDirectory)


			File temp = new File( parent, output_file.getName() + ".saving");

			if ( temp.exists()){

				if ( !temp.delete()){

					throw( new TOTorrentException( "Insufficient permissions to delete '" + temp + "'", TOTorrentException.RT_WRITE_FAILS ));
				}
			}else{

				boolean	ok = false;

				try{
					ok = temp.createNewFile();

				}catch( Throwable e ){
				}

				if ( !ok ){

					throw( new TOTorrentException( "Insufficient permissions to write '" + temp + "'", TOTorrentException.RT_WRITE_FAILS ));

				}
			}

            FileOutputStream fos = new FileOutputStream( temp, false );

            bos = new BufferedOutputStream( fos, 8192 );

            bos.write( res );

            bos.flush();

		  		// thinking about removing this - just do so for CVS for the moment

			if ( !Constants.isCVSVersion()){

				fos.getFD().sync();
			}

            bos.close();

            bos = null;

              //only use newly saved file if it got this far, i.e. it was written successfully

            if ( temp.length() > 1L ) {
            	
            	if ( output_file.exists() && !output_file.delete()){
            		
            		Debug.out( "Failed to delete " + output_file );
            	}
            	
                if ( !temp.renameTo( output_file )){
                	
                	Debug.out( "Failed to rename '" + temp + "' to '" + output_file + "'" );
                }
            }

		}catch( TOTorrentException e ){

			throw( e );

		}catch( Throwable e){

			throw( new TOTorrentException( 	"Failed to serialise torrent: " + Debug.getNestedExceptionMessage(e),
											TOTorrentException.RT_WRITE_FAILS ));

		}finally{

			if ( bos != null ){

				try{
					bos.close();

				}catch( IOException e ){

					Debug.printStackTrace( e );
				}
			}
		}
	}

	protected byte[]
	serialiseToByteArray()

		throws TOTorrentException
	{
		if ( created ){

			TorrentUtils.addCreatedTorrent( this );
		}

		Map	root = serialiseToMap();

		try{
			return( BEncoder.encode( root ));

		}catch( IOException e ){

			throw( 	new TOTorrentException(
							"Failed to serialise torrent: " + Debug.getNestedExceptionMessage(e),
							TOTorrentException.RT_WRITE_FAILS ));

		}
	}

	@Override
	public Map
	serialiseToMap()

		throws TOTorrentException
	{
			// protect against recursion when getting the hash

		if ( created && !serialising ){

			try{
				serialising	= true;	// not thread safe but we can live without the hassle of using TLS or whatever

				TorrentUtils.addCreatedTorrent( this );

			}finally{

				serialising = false;
			}
		}

		Map	root = new HashMap();

			// seen a NPE here, not sure of cause so handling null announce_url in case

		writeStringToMetaData( root, TK_ANNOUNCE, (announce_url==null?TorrentUtils.getDecentralisedEmptyURL():announce_url).toString());

		TOTorrentAnnounceURLSet[] sets = announce_group.getAnnounceURLSets();

		if (sets.length > 0 ){

			List	announce_list = new ArrayList();

			for (int i=0;i<sets.length;i++){

				TOTorrentAnnounceURLSet	set = sets[i];

				URL[]	urls = set.getAnnounceURLs();

				if ( urls.length == 0 ){

					continue;
				}

				List sub_list = new ArrayList();

				announce_list.add( sub_list );

				for (int j=0;j<urls.length;j++){

					sub_list.add( writeStringToMetaData( urls[j].toString()));
				}
			}

			if ( announce_list.size() > 0 ){

				root.put( TK_ANNOUNCE_LIST, announce_list );
			}
		}

		if ( comment != null ){

			root.put( TK_COMMENT, comment );
		}

		if ( creation_date != 0 ){

			root.put( TK_CREATION_DATE, new Long( creation_date ));
		}

		if ( created_by != null ){

			root.put( TK_CREATED_BY, created_by );
		}

		Map info = new HashMap();

		root.put( TK_INFO, info );

		info.put( TK_PIECE_LENGTH, new Long( piece_length ));

		if ( pieces == null ){

			throw( new TOTorrentException( "Pieces is null", TOTorrentException.RT_WRITE_FAILS ));
		}

		byte[]	flat_pieces = new byte[pieces.length*20];

		for (int i=0;i<pieces.length;i++){

			System.arraycopy( pieces[i], 0, flat_pieces, i*20, 20 );
		}

		info.put( TK_PIECES, flat_pieces );

		info.put( TK_NAME, torrent_name );

		if ( torrent_name_utf8 != null ){

			info.put( TK_NAME_UTF8, torrent_name_utf8 );
		}

		if ( torrent_hash_override != null ){

			info.put( TK_HASH_OVERRIDE, torrent_hash_override );
		}

		if ( simple_torrent ){

			TOTorrentFile	file = files[0];

			info.put( TK_LENGTH, new Long( file.getLength()));

		}else{

			List	meta_files = new ArrayList();

			info.put( TK_FILES, meta_files );

			for (int i=0;i<files.length;i++){

				TOTorrentFileImpl	file	= files[i];

				Map	file_map = file.serializeToMap();

				meta_files.add( file_map );

			}
		}

		Iterator info_it = additional_info_properties.keySet().iterator();

		while( info_it.hasNext()){

			String	key = (String)info_it.next();

			info.put( key, additional_info_properties.get( key ));
		}

		Iterator it = additional_properties.keySet().iterator();

		while( it.hasNext()){

			String	key = (String)it.next();

			Object	value = additional_properties.get( key );

			if ( value != null ){

				root.put( key, value );
			}
		}

		return( root );
	}

	@Override
	public void
	serialiseToXMLFile(
	  File		file )

	  throws TOTorrentException
	{
		if ( created ){

			TorrentUtils.addCreatedTorrent( this );
		}

		TOTorrentXMLSerialiser	serialiser = new TOTorrentXMLSerialiser( this );

		serialiser.serialiseToFile( file );
	}

	@Override
	public byte[]
	getName()
	{
		return( torrent_name );
	}

	protected void
	setName(
		byte[]	_name )
	{
		torrent_name	= _name;
	}

	@Override
	public String
	getUTF8Name()
	{
		try {
			return torrent_name_utf8 == null ? null : new String(torrent_name_utf8,
					"utf8");
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}

	protected void
	setNameUTF8(
		byte[]	_name )
	{
		torrent_name_utf8	= _name;
	}

	@Override
	public boolean
	isSimpleTorrent()
	{
		return( simple_torrent );
	}

	@Override
	public byte[]
	getComment()
	{
		return( comment );
	}

	protected void
	setComment(
		byte[]		_comment )

	{
		comment = _comment;
	}

	@Override
	public void
	setComment(
		String	_comment )
	{
		try{

			byte[]	utf8_comment = _comment.getBytes( Constants.DEFAULT_ENCODING );

			setComment( utf8_comment );

			setAdditionalByteArrayProperty( TK_COMMENT_UTF8, utf8_comment );

		}catch( UnsupportedEncodingException e ){

			Debug.printStackTrace( e );

			comment = null;
		}
	}

	@Override
	public URL
	getAnnounceURL()
	{
		return( announce_url );
	}

	@Override
	public boolean
	setAnnounceURL(
		URL		url )
	{
		URL newURL = anonymityTransform( url );
		String s0 = (newURL == null) ? "" : newURL.toString();
		String s1 = (announce_url == null) ? "" : announce_url.toString();
		if (s0.equals(s1))
			return false;

		if ( newURL == null ){

				// anything's better than null...

			newURL = TorrentUtils.getDecentralisedEmptyURL();
		}

		announce_url	= StringInterner.internURL(newURL);

		fireChanged( TOTorrentListener.CT_ANNOUNCE_URLS );

		return true;
	}

	@Override
	public boolean
	isDecentralised()
	{
		return( TorrentUtils.isDecentralised( getAnnounceURL()));
	}

	@Override
	public long
	getCreationDate()
	{
		return( creation_date );
	}

	@Override
	public void
	setCreationDate(
		long		_creation_date )
	{
			// supposed to be in seconds, not millis. Some torrents have millis so try and
			// fix it

		long	now_secs = SystemTime.getCurrentTime()/1000;

		if ( _creation_date > now_secs + 100*365*24*60*60L){

			_creation_date = _creation_date/1000;
		}

		creation_date 	= _creation_date;
	}

	@Override
	public void
	setCreatedBy(
		byte[]		_created_by )
	{
		created_by	= _created_by;
	}

	protected void
	setCreatedBy(
		String		_created_by )
	{
		try{

			setCreatedBy( _created_by.getBytes( Constants.DEFAULT_ENCODING ));

		}catch( UnsupportedEncodingException e ){

			Debug.printStackTrace( e );

			created_by = null;
		}
	}

	@Override
	public byte[]
	getCreatedBy()
	{
		return( created_by );
	}

	@Override
	public boolean
	isCreated()
	{
		return( created );
	}

	@Override
	public byte[]
	getHash()

		throws TOTorrentException
	{
		if ( torrent_hash == null ){

			Map	root = serialiseToMap();

			Map info = (Map)root.get( TK_INFO );

			setHashFromInfo( info );
		}

		return( torrent_hash );
	}

	@Override
	public HashWrapper
	getHashWrapper()

		throws TOTorrentException
	{
		if ( torrent_hash_wrapper == null ){

			getHash();
		}

		return( torrent_hash_wrapper );
	}

	@Override
	public boolean
	hasSameHashAs(
		TOTorrent		other )
	{
		try{
			byte[]	other_hash = other.getHash();

			return( Arrays.equals( getHash(), other_hash ));

		}catch( TOTorrentException e ){

			Debug.printStackTrace( e );

			return( false );
		}
	}

	protected void
	setHashFromInfo(
		Map		info )

		throws TOTorrentException
	{
		try{
			if ( torrent_hash_override == null ){

				SHA1Hasher s = new SHA1Hasher();

				torrent_hash = s.calculateHash(BEncoder.encode(info));
				
			}else{

				torrent_hash = torrent_hash_override;
			}

			torrent_hash_wrapper = new HashWrapper( torrent_hash );

		}catch( Throwable e ){

			throw( new TOTorrentException( 	"Failed to calculate hash: " + Debug.getNestedExceptionMessage(e),
											TOTorrentException.RT_HASH_FAILS ));
		}
	}

	@Override
	public void
	setHashOverride(
		byte[] 	hash )

		throws TOTorrentException
	{
		if ( torrent_hash_override != null ){

			if ( Arrays.equals( hash, torrent_hash_override )){

				return;

			}else{

				throw( new TOTorrentException( 	"Hash override can only be set once",
								TOTorrentException.RT_HASH_FAILS ));
			}
		}

		torrent_hash_override = hash;

		torrent_hash	= null;

		getHash();
	}

	protected byte[]
	getHashOverride()
	{
		return( torrent_hash_override );
	}

	@Override
	public void
	setPrivate(
		boolean	_private_torrent )

		throws TOTorrentException
	{
		additional_info_properties.put( TK_PRIVATE, new Long(_private_torrent?1:0));

			// update torrent hash

		torrent_hash	= null;

		getHash();
	}

	@Override
	public boolean
	getPrivate()
	{
		Object o = additional_info_properties.get( TK_PRIVATE );

		if ( o instanceof Long ){

			return(((Long)o).intValue() != 0 );
		}

		return( false );
	}

	@Override
	public TOTorrentAnnounceURLGroup
	getAnnounceURLGroup()
	{
		return( announce_group );
	}

	protected void
	addTorrentAnnounceURLSet(
		URL[]		urls )
	{
		announce_group.addSet( new TOTorrentAnnounceURLSetImpl( this, urls ));
	}

	@Override
	public long
	getSize()
	{
		long	res = 0;

		for (int i=0;i<files.length;i++){

			res += files[i].getLength();
		}

		return( res );
	}

	@Override
	public long
	getPieceLength()
	{
		return( piece_length );
	}

	protected void
	setPieceLength(
		long	_length )
	{
		piece_length	= _length;
	}

	@Override
	public int
	getNumberOfPieces()
	{
			// to support buggy torrents with extraneous pieces (they seem to exist) we calculate
			// the required number of pieces rather than the using the actual. Note that we
			// can't adjust the pieces array itself as this results in incorrect torrent hashes
			// being derived later after a save + restore

		if ( number_of_pieces == 0 ){

			number_of_pieces = (int)((getSize() + (piece_length-1)) / piece_length );
		}

		return( number_of_pieces );
	}

	@Override
	public byte[][]
	getPieces()
	{
		return( pieces );
	}

	@Override
	public void
	setPieces(
		byte[][]	_pieces )
	{
		pieces = _pieces;
	}

	@Override
	public int
	getFileCount()
	{
		return( files.length );
	}

	@Override
	public TOTorrentFile[]
	getFiles()
	{
		return( files );
	}

	protected void
	setFiles(
		TOTorrentFileImpl[]		_files )
	{
		files	= _files;
	}

	protected boolean
	getSimpleTorrent()
	{
		return( simple_torrent );
	}

	protected void
	setSimpleTorrent(
		boolean	_simple_torrent )
	{
		simple_torrent	= _simple_torrent;
	}

	protected Map
	getAdditionalProperties()
	{
		return( additional_properties );
	}

	@Override
	public void
	setAdditionalStringProperty(
		String		name,
		String		value )
	{
		try{

			setAdditionalByteArrayProperty( name, writeStringToMetaData( value ));

		}catch( TOTorrentException e ){

				// hide encoding exceptions as default encoding must be available

			Debug.printStackTrace( e );
		}
	}

	@Override
	public String
	getAdditionalStringProperty(
		String		name )
	{
		try{

			return( readStringFromMetaData( getAdditionalByteArrayProperty(name)));

		}catch( TOTorrentException e ){

				// hide encoding exceptions as default encoding must be available

			Debug.printStackTrace( e );

			return( null );
		}
	}

	@Override
	public void
	setAdditionalByteArrayProperty(
		String		name,
		byte[]		value )
	{
		additional_properties.put( name, value );
	}

	@Override
	public void
	setAdditionalProperty(
		String		name,
		Object		value )
	{
		if ( value instanceof String ){

			setAdditionalStringProperty(name,(String)value);

		}else{

			additional_properties.put( name, value );
		}
	}

	@Override
	public byte[]
	getAdditionalByteArrayProperty(
		String		name )
	{
		Object	obj = additional_properties.get( name );

		if ( obj instanceof byte[] ){

			return((byte[])obj);
		}

		return( null );
	}

	@Override
	public void
	setAdditionalLongProperty(
		String		name,
		Long		value )
	{
		additional_properties.put( name, value );
	}

	@Override
	public Long
	getAdditionalLongProperty(
		String		name )
	{
		Object	obj = additional_properties.get( name );

		if ( obj instanceof Long ){

			return((Long)obj);
		}

		return( null );
	}

	@Override
	public void
	setAdditionalListProperty(
		String		name,
		List		value )
	{
		additional_properties.put( name, value );
	}

	@Override
	public List
	getAdditionalListProperty(
		String		name )
	{
		Object	obj = additional_properties.get( name );

		if ( obj instanceof List ){

			return((List)obj);
		}

		return( null );
	}

	@Override
	public void
	setAdditionalMapProperty(
		String		name,
		Map 		value )
	{
		additional_properties.put( name, value );
	}

	@Override
	public Map
	getAdditionalMapProperty(
		String		name )
	{
		Object	obj = additional_properties.get( name );

		if ( obj instanceof Map ){

			return((Map)obj);
		}

		return( null );
	}

	@Override
	public Object
	getAdditionalProperty(
		String		name )
	{
		return(additional_properties.get( name ));
	}

	@Override
	public void
	removeAdditionalProperty(
		String name )
	{
		additional_properties.remove( name );
	}

	@Override
	public void
	removeAdditionalProperties()
	{
		Map	new_props = new HashMap();

		Iterator it = additional_properties.keySet().iterator();

		while( it.hasNext()){

			String	key = (String)it.next();

			if ( TK_ADDITIONAL_OK_ATTRS.contains(key)){

				new_props.put( key, additional_properties.get( key ));
			}
		}

		additional_properties = new_props;
	}

	protected void
	addAdditionalProperty(
		String			name,
		Object			value )
	{
		additional_properties.put( name, value );
	}

	protected void
	addAdditionalInfoProperty(
		String			name,
		Object			value )
	{
		additional_info_properties.put( name, value );
	}

	protected Map
	getAdditionalInfoProperties()
	{
		return( additional_info_properties );
	}

	protected String
	readStringFromMetaData(
		Map		meta_data,
		String	name )

		throws TOTorrentException
	{
		Object	obj = meta_data.get(name);

		if ( obj instanceof byte[]){

			return(readStringFromMetaData((byte[])obj));
		}

		return( null );
	}

	protected String
	readStringFromMetaData(
		byte[]		value )

		throws TOTorrentException
	{
		try{
			if ( value == null ){

				return( null );
			}

			return(	new String(value, Constants.DEFAULT_ENCODING ));

		}catch( UnsupportedEncodingException e ){

			throw( new TOTorrentException( 	"Unsupported encoding for '" + value + "'",
											TOTorrentException.RT_UNSUPPORTED_ENCODING));
		}
	}

	protected void
	writeStringToMetaData(
		Map		meta_data,
		String	name,
		String	value )

		throws TOTorrentException
	{
		meta_data.put( name, writeStringToMetaData( value ));
	}

	protected byte[]
	writeStringToMetaData(
		String		value )

		throws TOTorrentException
	{
		try{

			return(	value.getBytes( Constants.DEFAULT_ENCODING ));

		}catch( UnsupportedEncodingException e ){

			throw( new TOTorrentException( 	"Unsupported encoding for '" + value + "'",
											TOTorrentException.RT_UNSUPPORTED_ENCODING));
		}
	}

	protected URL
	anonymityTransform(
		URL		url )
	{
		/*
		 * 	hmm, doing this is harder than it looks as we have issues hosting
		 *  (both starting tracker instances and also short-cut loopback for seeding
		 *  leave as is for the moment
		if ( HostNameToIPResolver.isNonDNSName( url.getHost())){

			// remove the port as it is uninteresting and could leak information about the
			// tracker

			String	url_string = url.toString();

			String	port_string = ":" + (url.getPort()==-1?url.getDefaultPort():url.getPort());

			int	port_pos = url_string.indexOf( ":" + url.getPort());

			if ( port_pos != -1 ){

				try{

					return( new URL( url_string.substring(0,port_pos) + url_string.substring(port_pos+port_string.length())));

				}catch( MalformedURLException e){

					Debug.printStackTrace(e);
				}
			}
		}
		*/

		return( url );
	}

	@Override
	public void
	print()
	{
		try{
			byte[]	hash = getHash();

			System.out.println( "name = " + torrent_name );
			System.out.println( "announce url = " + announce_url );
			System.out.println( "announce group = " + announce_group.getAnnounceURLSets().length );
			System.out.println( "creation date = " + creation_date );
			System.out.println( "creation by = " + created_by );
			System.out.println( "comment = " + comment );
			System.out.println( "hash = " + ByteFormatter.nicePrint( hash ));
			System.out.println( "piece length = " + getPieceLength() );
			System.out.println( "pieces = " + getNumberOfPieces() );

			Iterator info_it = additional_info_properties.keySet().iterator();

			while( info_it.hasNext()){

				String	key = (String)info_it.next();
				Object	value = additional_info_properties.get( key );

				try{

					System.out.println( "info prop '" + key + "' = '" +
										( value instanceof byte[]?new String((byte[])value, Constants.DEFAULT_ENCODING):value.toString()) + "'" );
				}catch( UnsupportedEncodingException e){

					System.out.println( "info prop '" + key + "' = unsupported encoding!!!!");
				}
			}

			Iterator it = additional_properties.keySet().iterator();

			while( it.hasNext()){

				String	key = (String)it.next();
				Object	value = additional_properties.get( key );

				try{

					System.out.println( "prop '" + key + "' = '" +
										( value instanceof byte[]?new String((byte[])value, Constants.DEFAULT_ENCODING):value.toString()) + "'" );
				}catch( UnsupportedEncodingException e){

					System.out.println( "prop '" + key + "' = unsupported encoding!!!!");
				}
			}

			if ( pieces == null ){

				System.out.println( "\tpieces = null" );

			}else{
				for (int i=0;i<pieces.length;i++){

					System.out.println( "\t" + ByteFormatter.nicePrint(pieces[i]));
				}
			}

			for (int i=0;i<files.length;i++){

				byte[][]path_comps = files[i].getPathComponents();

				String	path_str = "";

				for (int j=0;j<path_comps.length;j++){

					try{

						path_str += (j==0?"":File.separator) + new String( path_comps[j], Constants.DEFAULT_ENCODING );

					}catch( UnsupportedEncodingException e ){

						System.out.println( "file - unsupported encoding!!!!");
					}
				}

				System.out.println( "\t" + path_str + " (" + files[i].getLength() + ")" );
			}
		}catch( TOTorrentException e ){

			Debug.printStackTrace( e );
		}
	}

	protected void
	fireChanged(
		int	type )
	{
		if ( constructing ){
			
			return;
		}
		
		List<TOTorrentListener> to_fire = null;

		try{
			this_mon.enter();

			if ( listeners != null ){

				to_fire = new ArrayList<>(listeners);
			}
		}finally{

			this_mon.exit();
		}

		if ( to_fire != null ){

			for ( TOTorrentListener l: to_fire ){

				try{
					l.torrentChanged( this, type );

				}catch( Throwable e ){

					Debug.out(e);
				}
			}
		}
		
		for ( TOTorrentListener l: global_listeners ){
			
			try{
				l.torrentChanged( this, type );

			}catch( Throwable e ){

				Debug.out(e);
			}
		}
	}

 	@Override
  public void
	addListener(
		TOTorrentListener		l )
	{
 		try{
			this_mon.enter();

			if ( listeners == null ){

				listeners = new ArrayList<>();
			}

			listeners.add( l );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	removeListener(
		TOTorrentListener		l )
	{
 		try{
			this_mon.enter();

			if ( listeners != null ){

				listeners.remove( l );

				if ( listeners.size() == 0 ){

					listeners = null;
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public AEMonitor
	getMonitor()
	{
		return( this_mon );
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.logging.LogRelation#getLogRelationText()
	 */
	@Override
	public String getRelationText() {
		return "Torrent: '" + new String(torrent_name) + "'";
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.logging.LogRelation#queryForClass(java.lang.Class)
	 */
	@Override
	public Object[] getQueryableInterfaces() {
		// yuck
		try {
			return new Object[] { CoreFactory.getSingleton()
					.getGlobalManager().getDownloadManager(this) };
		} catch (Exception e) {
		}

		return null;
	}
}