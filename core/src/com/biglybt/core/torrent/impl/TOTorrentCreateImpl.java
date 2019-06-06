/*
 * File    : TOTorrentCreateImpl.java
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentProgressListener;
import com.biglybt.core.util.*;

public class
TOTorrentCreateImpl
	extends		TOTorrentImpl
	implements	TOTorrentFileHasherListener
{
	private static final Comparator<File> file_comparator;

	static{
		if ( System.getProperty( "az.create.torrent.alphanumeric.sort", "0" ).equals( "1" )){

			file_comparator =
				new Comparator<File>()
				{
					@Override
					public int
					compare(
						File	f1,
						File	f2 )
					{
						String	s1 = f1.getName();
						String	s2 = f2.getName();

						int	l1 = s1.length();
						int	l2 = s2.length();

						int	c1_pos	= 0;
						int c2_pos	= 0;

						while( c1_pos < l1 && c2_pos < l2 ){

							char	c1 = s1.charAt( c1_pos++ );
							char	c2 = s2.charAt( c2_pos++ );

							if ( Character.isDigit(c1) && Character.isDigit(c2)){

								int	n1_pos = c1_pos-1;
								int n2_pos = c2_pos-1;

								while( c1_pos < l1 ){

									if ( !Character.isDigit( s1.charAt( c1_pos ))){

										break;
									}

									c1_pos++;
								}

								while(c2_pos<l2){

									if ( !Character.isDigit( s2.charAt( c2_pos ))){

										break;
									}

									c2_pos++;
								}

								int	n1_length = c1_pos - n1_pos;
								int n2_length = c2_pos - n2_pos;

								if ( n1_length != n2_length ){

									return( n1_length - n2_length );
								}

								for (int i=0;i<n1_length;i++){

									char	nc1 = s1.charAt( n1_pos++ );
									char	nc2 = s2.charAt( n2_pos++ );

									if ( nc1 != nc2 ){

										return( nc1 - nc2 );
									}
								}
							}else{

								if ( true ){

									 c1 = Character.toLowerCase( c1 );

									 c2 = Character.toLowerCase( c2 );
								}

								if ( c1 != c2 ){

									return( c1 - c2 );
								}
							}
						}

						return( l1 - l2);
					}
				};
		}else{

			file_comparator = null;
		}
	}

	private File							torrent_base;
	private long							piece_length;

	private TOTorrentFileHasher			file_hasher;

	private long	total_file_size		= -1;
	private long	total_file_count	= 0;

	private long							piece_count;
	private boolean							add_other_hashes;

	private final List<TOTorrentProgressListener>							progress_listeners = new ArrayList<>();

	private int	reported_progress;

	private Set<String>	ignore_set = new HashSet<>();

	private Map<String,File>	linkage_map;
	private final Map<String,String>	linked_tf_map = new HashMap<>();

	private boolean	cancelled;

	protected
	TOTorrentCreateImpl(
		Map<String,File>			_linkage_map,
		File						_torrent_base,
		URL							_announce_url,
		boolean						_add_other_hashes,
		long						_piece_length )

		throws TOTorrentException
	{
		super( _torrent_base.getName(), _announce_url, _torrent_base.isFile());

		linkage_map 		= _linkage_map;
		torrent_base		= _torrent_base;
		piece_length		= _piece_length;
		add_other_hashes	= _add_other_hashes;
	}

	protected
	TOTorrentCreateImpl(
		Map<String,File>			_linkage_map,
		File						_torrent_base,
		URL							_announce_url,
		boolean						_add_other_hashes,
		long						_piece_min_size,
		long						_piece_max_size,
		long						_piece_num_lower,
		long						_piece_num_upper )

		throws TOTorrentException
	{
		super( _torrent_base.getName(), _announce_url, _torrent_base.isFile());

		linkage_map 		= _linkage_map;
		torrent_base		= _torrent_base;
		add_other_hashes	= _add_other_hashes;

		long	total_size = calculateTotalFileSize( _torrent_base );

		piece_length = getComputedPieceSize( total_size, _piece_min_size, _piece_max_size, _piece_num_lower, _piece_num_upper );
	}

	protected void
	create()

		throws TOTorrentException
	{
		try{
			int ignored = constructFixed( torrent_base, piece_length );
	
				// linkage map doesn't include ignored files, if it is supplied, so take account of this when
				// checking that linkages have resolved correctly
	
			if ( 	linkage_map.size() > 0 &&
					linkage_map.size() != ( linked_tf_map.size() + ignored )){
	
				throw( new TOTorrentException( "TOTorrentCreate: unresolved linkages: required=" + linkage_map + ", resolved=" + linked_tf_map,
						TOTorrentException.RT_DECODE_FAILS));
			}
	
			if ( linked_tf_map.size() > 0 ){
	
				Map	m = getAdditionalMapProperty( TOTorrent.AZUREUS_PRIVATE_PROPERTIES );
	
				if ( m == null ){
	
					m = new HashMap();
	
					setAdditionalMapProperty( TOTorrent.AZUREUS_PRIVATE_PROPERTIES, m );
				}
	
				if ( linked_tf_map.size() < 100 ){
	
					m.put( TorrentUtils.TORRENT_AZ_PROP_INITIAL_LINKAGE, linked_tf_map );
	
				}else{
	
					ByteArrayOutputStream baos = new ByteArrayOutputStream( 100*1024 );
	
					try{
						GZIPOutputStream gos = new GZIPOutputStream( baos );
	
						gos.write( BEncoder.encode( linked_tf_map ));
	
						gos.close();
	
						m.put( TorrentUtils.TORRENT_AZ_PROP_INITIAL_LINKAGE2, baos.toByteArray() );
	
					}catch( Throwable e ){
	
						throw( new TOTorrentException( "Failed to serialise linkage", TOTorrentException.RT_WRITE_FAILS ));
					}
				}
			}
		}finally{
			
			setConstructed();
		}
	}

	private int
	constructFixed(
		File		_torrent_base,
		long		_piece_length )

		throws TOTorrentException
	{
		setIgnoreList();

		setCreationDate( SystemTime.getCurrentTime() / 1000);

		setCreatedBy( Constants.BIGLYBT_NAME + "/" + Constants.BIGLYBT_VERSION );

		setPieceLength( _piece_length );

		report( "Torrent.create.progress.piecelength", _piece_length );

		piece_count = calculateNumberOfPieces( _torrent_base,_piece_length );

		if ( piece_count == 0 ){

			throw( new TOTorrentException( "TOTorrentCreate: specified files have zero total length",
											TOTorrentException.RT_ZERO_LENGTH ));
		}

		report( "Torrent.create.progress.hashing");

		for (int i=0;i<progress_listeners.size();i++){

			((TOTorrentProgressListener)progress_listeners.get(i)).reportProgress( 0 );
		}

		boolean add_other_per_file_hashes 	= add_other_hashes&&!getSimpleTorrent();

		file_hasher =
			new TOTorrentFileHasher(
					add_other_hashes,
					add_other_per_file_hashes,
					(int)_piece_length,
					progress_listeners.size()==0?null:this );

		int	ignored = 0;

		try{
			if ( cancelled ){

				throw( new TOTorrentException( 	"TOTorrentCreate: operation cancelled",
												TOTorrentException.RT_CANCELLED ));
			}

			if ( getSimpleTorrent()){

				File link = linkage_map.get( _torrent_base.getName());

				if ( link != null ){

					linked_tf_map.put( "0", link.getAbsolutePath());
				}

				long length = file_hasher.add( link==null?_torrent_base:link );

				setFiles( new TOTorrentFileImpl[]{ new TOTorrentFileImpl( this, 0, 0, length, new byte[][]{ getName()})});

				setPieces( file_hasher.getPieces());

			}else{

				List<TOTorrentFileImpl>	encoded = new ArrayList<>();

				ignored = processDir( file_hasher, _torrent_base, encoded, _torrent_base.getName(), "" );

				TOTorrentFileImpl[] files = new TOTorrentFileImpl[ encoded.size()];

				encoded.toArray( files );

				setFiles( files );
			}

			setPieces( file_hasher.getPieces());

			if ( add_other_hashes ){

				byte[]	sha1_digest = file_hasher.getSHA1Digest();
				byte[]	ed2k_digest = file_hasher.getED2KDigest();

				addAdditionalInfoProperty( "sha1", sha1_digest );
				addAdditionalInfoProperty( "ed2k", ed2k_digest );

				//System.out.println( "overall:sha1 = " + ByteFormatter.nicePrint( sha1_digest, true));
				//System.out.println( "overall:ed2k = " + ByteFormatter.nicePrint( ed2k_digest, true));
			}

			return( ignored );

		}finally{

			file_hasher = null;
		}
	}

	private int
	processDir(
		TOTorrentFileHasher			hasher,
		File						dir,
		List<TOTorrentFileImpl>		encoded,
		String						base_name,
		String						root )

		throws TOTorrentException
	{
		File[]	dir_file_list = dir.listFiles();

		if ( dir_file_list == null ){

			throw( new TOTorrentException( "TOTorrentCreate: directory '" + dir.getAbsolutePath() + "' returned error when listing files in it",
					TOTorrentException.RT_FILE_NOT_FOUND ));

		}
			// sort contents so that multiple encodes of a dir always
			// generate same torrent

		List<File> file_list = new ArrayList<>(Arrays.asList(dir_file_list));

		if ( file_comparator == null ){

			Collections.sort(file_list);

		}else{

			Collections.sort( file_list,file_comparator);
		}

		long	offset	= 0;

		int	ignored = 0;

		for (int i=0;i<file_list.size();i++){

			File	file = (File)file_list.get(i);

			String	file_name = file.getName();

			if ( !(file_name.equals( "." ) || file_name.equals( ".." ))){

				if ( file.isDirectory()){

					if ( root.length() > 0 ){

						file_name = root + File.separator + file_name ;
					}

					ignored += processDir( hasher, file, encoded, base_name, file_name );

				}else{

					if ( ignoreFile( file_name )){

						ignored++;

					}else{

						if ( root.length() > 0 ){

							file_name = root + File.separator + file_name;
						}

						File link = linkage_map.get( base_name + File.separator + file_name );

						if ( link != null ){

							linked_tf_map.put( String.valueOf( encoded.size()), link.getAbsolutePath());
						}

						long length = hasher.add( link==null?file:link );

						TOTorrentFileImpl	tf = new TOTorrentFileImpl( this, i, offset, length, file_name );

						offset += length;

						if ( add_other_hashes ){

							byte[]	ed2k_digest	= hasher.getPerFileED2KDigest();
							byte[]	sha1_digest	= hasher.getPerFileSHA1Digest();

							//System.out.println( "file:ed2k = " + ByteFormatter.nicePrint( ed2k_digest, true ));
							//System.out.println( "file:sha1 = " + ByteFormatter.nicePrint( sha1_digest, true ));

							tf.setAdditionalProperty( "sha1", sha1_digest );
							tf.setAdditionalProperty( "ed2k", ed2k_digest );
						}

						encoded.add( tf );
					}
				}
			}
		}

		return( ignored );
	}

	@Override
	public void
	pieceHashed(
		int		piece_number )
	{
		for (int i=0;i<progress_listeners.size();i++){

			int	this_progress = (int)((piece_number*100)/piece_count );

			if ( this_progress != reported_progress ){

				reported_progress = this_progress;

				((TOTorrentProgressListener)progress_listeners.get(i)).reportProgress( reported_progress );
			}
		}
	}

	protected long
	calculateNumberOfPieces(
		File				_file,
		long				_piece_length )

		throws TOTorrentException
	{
		long	res = getPieceCount(calculateTotalFileSize( _file ), _piece_length );

		report( "Torrent.create.progress.piececount", ""+res );

		return( res );
	}

	protected long
	calculateTotalFileSize(
		File				file )

		throws TOTorrentException
	{
		if ( total_file_size == -1 ){

			total_file_size = getTotalFileSize( file );
		}

		return( total_file_size );
	}

	protected long
	getTotalFileSize(
		File				file )

		throws TOTorrentException
	{
		report( "Torrent.create.progress.parsingfiles" );

		long res = getTotalFileSizeSupport( file, "" );

		report( "Torrent.create.progress.totalfilesize", res );

		report( "Torrent.create.progress.totalfilecount", ""+total_file_count );

		return( res );
	}

	protected long
	getTotalFileSizeSupport(
		File				file,
		String				root )

		throws TOTorrentException
	{
		String	name = file.getName();

		if ( name.equals( "." ) || name.equals( ".." )){

			return( 0 );
		}

		if ( !file.exists()){

			throw( new TOTorrentException( "TOTorrentCreate: file '" + file.getName() + "' doesn't exist",
											TOTorrentException.RT_FILE_NOT_FOUND ));
		}

		if ( file.isFile()){

			if ( !ignoreFile( name )){

				total_file_count++;

				if ( root.length() > 0 ){

					name = root + File.separator + name;
				}

				File link = linkage_map.get( name );

				return( link==null?file.length():link.length());

			}else{

				return( 0 );
			}
		}else{

			File[]	dir_files = file.listFiles();

			if ( dir_files == null ){

				throw( new TOTorrentException( "TOTorrentCreate: directory '" + file.getAbsolutePath() + "' returned error when listing files in it",
						TOTorrentException.RT_FILE_NOT_FOUND ));

			}

			long	length = 0;

			if ( root.length() == 0 ){

				root = name;
			}else{

				root = root + File.separator + name;
			}

			for (int i=0;i<dir_files.length;i++){

				length += getTotalFileSizeSupport( dir_files[i], root );
			}

			return( length );
		}
	}

	protected void
	report(
		String	resource_key )
	{
		report( resource_key, null );
	}

	protected void
	report(
		String	resource_key,
		long	bytes )
	{
		if ( progress_listeners.size() > 0 ){

			report( resource_key, DisplayFormatters.formatByteCountToKiBEtc( bytes ));
		}
	}

	protected void
	report(
		String	resource_key,
		String	additional_text )
	{
		if ( progress_listeners.size() > 0 ){

			String	prefix = MessageText.getString(resource_key);

			for (int i=0;i<progress_listeners.size();i++){

				((TOTorrentProgressListener)progress_listeners.get(i)).reportCurrentTask( prefix + (additional_text==null?"":additional_text ));
			}
		}
	}

	public static long
	getComputedPieceSize(
		long 	total_size,
		long	_piece_min_size,
		long	_piece_max_size,
		long	_piece_num_lower,
		long	_piece_num_upper )
	{
		long	piece_length = -1;

		long	current_piece_size = _piece_min_size;

		while( current_piece_size <= _piece_max_size ){

			long	pieces = total_size / current_piece_size;

			if ( pieces <= _piece_num_upper ){

				piece_length = current_piece_size;

				break;
			}

			current_piece_size = current_piece_size << 1;
		}

		// if we haven't set a piece length here then there are too many pieces even
		// at maximum piece size. Go for largest piece size

		if ( piece_length == -1 ){

			// just go for the maximum piece size

			piece_length = 	_piece_max_size;
		}

		return( piece_length );
	}

	public static long
	getPieceCount(
		long		total_size,
		long		piece_size )
	{
		return( (total_size + (piece_size-1))/piece_size );
	}

	protected void
	setIgnoreList()
	{
		try{
			ignore_set = TorrentUtils.getIgnoreSet();

		}catch( NoClassDefFoundError e ){

			return;
		}
	}

	private boolean
	ignoreFile(
		String		file )
	{
		if ( ignore_set.contains(file.toLowerCase())){

			report( "Torrent.create.progress.ignoringfile", " '" + file + "'" );

			return( true );
		}

		return( false );
	}

	protected void
	cancel()
	{
		if ( !cancelled ){

			report( "Torrent.create.progress.cancelled");

			cancelled	= true;

			if ( file_hasher != null ){

				file_hasher.cancel();
			}
		}
	}

	protected void
	addListener(
		TOTorrentProgressListener	listener )
	{
		progress_listeners.add( listener );
	}

	protected void
	removeListener(
		TOTorrentProgressListener	listener )
	{
		progress_listeners.remove( listener );
	}
}
