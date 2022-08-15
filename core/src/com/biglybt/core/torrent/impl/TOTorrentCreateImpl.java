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
	private static final Comparator<File> file_comparator_v2;

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
		
		file_comparator_v2 =
				new Comparator<File>()
				{
					@Override
					public int
					compare(
						File	f1,
						File	f2 )
					{
						return( f1.getName().compareTo( f2.getName()));
					}
				};
	}
	
	private final int						torrent_type;
	
	private File							torrent_base;
	private long							piece_length;

	private TOTorrentFileHasher			file_hasher;

	private long	total_file_size_no_pad		= -1;
	private long	total_file_count_no_pad		= 0;

	private long	piece_count_no_pad;
	
	private boolean							add_other_hashes;
	
	private boolean							add_pad_files = false;
	private int								pad_file_num;
	private long							pad_file_sizes;
	private final boolean					add_v1;
	private final boolean					add_v2;
	
	private final List<TOTorrentProgressListener>							progress_listeners = new ArrayList<>();

	private int	reported_progress;

	private Set<String>	ignore_set = new HashSet<>();

	private Map<String,File>	linkage_map;
	private final Map<String,String>	linked_tf_map = new HashMap<>();

	private volatile boolean	cancelled;

	protected
	TOTorrentCreateImpl(
		int							_torrent_type,
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
		
		torrent_type = _torrent_type;
		
		add_v1	= torrent_type == TT_V1 || torrent_type == TT_V1_V2;
		add_v2	= torrent_type == TT_V2 || torrent_type == TT_V1_V2;
		
		if ( add_v2 ){
			
			long highestOneBit = Long.highestOneBit( piece_length );
			
				// if not already power of two round up to next
			
			if ( piece_length != highestOneBit ) {
										
				piece_length =  highestOneBit << 1;	// round up to next power of 2
			}
		}
	}

	protected
	TOTorrentCreateImpl(
		int							_torrent_type,
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

		torrent_type = _torrent_type;

		add_v1	= torrent_type == TT_V1 || torrent_type == TT_V1_V2;
		add_v2	= torrent_type == TT_V2 || torrent_type == TT_V1_V2;

		long	total_size = calculateTotalFileSize( _torrent_base );

		piece_length = getComputedPieceSize( total_size, _piece_min_size, _piece_max_size, _piece_num_lower, _piece_num_upper );
		
		if ( add_v2 ){
			
			long highestOneBit = Long.highestOneBit( piece_length );
			
				// if not already power of two round up to next
			
			if ( piece_length != highestOneBit ) {
										
				piece_length =  highestOneBit << 1;	// round up to next power of 2
			}
		}
	}

	protected void
	create(
		boolean		skip_hashing )

		throws TOTorrentException
	{
		if ( !( add_v1 || add_v2 )){
			
			throw( new TOTorrentException( "No torrent versions selected", TOTorrentException.RT_CREATE_FAILED ));
		}
	
		if ( add_v2 ){
			
			add_pad_files = true;
		}
		
		try{
			setIgnoreList();

			setTorrentType( torrent_type );
			
			setCreationDate( SystemTime.getCurrentTime() / 1000);

			setCreatedBy( Constants.BIGLYBT_NAME + "/" + Constants.BIGLYBT_VERSION );

			setPieceLength( piece_length );

			piece_count_no_pad = calculateNumberOfPieces( torrent_base, piece_length );

			if ( piece_count_no_pad == 0 ){

				throw( new TOTorrentException( "Specified files have zero total length",
												TOTorrentException.RT_ZERO_LENGTH ));
			}

			report( "Torrent.create.progress.piecelength", piece_length );

			if ( add_v1 ){
				
				int ignored = createV1( skip_hashing );
		
					// linkage map doesn't include ignored files, if it is supplied, so take account of this when
					// checking that linkages have resolved correctly
		
				if ( 	linkage_map.size() > 0 &&
						linkage_map.size() != ( linked_tf_map.size() + ignored )){
		
					throw( new TOTorrentException( "Unresolved V1 linkages: required=" + linkage_map + ", resolved=" + linked_tf_map,
							TOTorrentException.RT_DECODE_FAILS));
				}
			}
			
			if ( add_v2 ){
				
				linked_tf_map.clear();
				
				int ignored = createV2( skip_hashing );
				
					// linkage map doesn't include ignored files, if it is supplied, so take account of this when
					// checking that linkages have resolved correctly
		
				if ( 	linkage_map.size() > 0 &&
						linkage_map.size() != ( linked_tf_map.size() + ignored )){
		
					throw( new TOTorrentException( "Unresolved V2 linkages: required=" + linkage_map + ", resolved=" + linked_tf_map,
							TOTorrentException.RT_DECODE_FAILS));
				}
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
	createV1(
		boolean		skip_hashing )
	
		throws TOTorrentException
	{
		report( "Torrent.create.progress.hashing", (add_v2?": V1":"" ));

		for (int i=0;i<progress_listeners.size();i++){

			((TOTorrentProgressListener)progress_listeners.get(i)).reportProgress( 0 );
		}

		boolean add_other_per_file_hashes 	= add_other_hashes&&!getSimpleTorrent();

		file_hasher =
			new TOTorrentFileHasher(
					add_other_hashes,
					add_other_per_file_hashes,
					(int)piece_length,
					progress_listeners.size()==0?null:this );

		if ( skip_hashing ){
			
			file_hasher.setSkipHashing( true );
		}
		
		int	ignored = 0;

		try{
			if ( cancelled ){

				throw( new TOTorrentException( 	"Operation cancelled",
												TOTorrentException.RT_CANCELLED ));
			}

			if ( getSimpleTorrent()){

				File link = linkage_map.get( torrent_base.getName());

				if ( link != null ){

					linked_tf_map.put( "0", link.getAbsolutePath());
				}

				long length = file_hasher.add( link==null?torrent_base:link );

				setFiles( new TOTorrentFileImpl[]{ new TOTorrentFileImpl( this, 0, 0, length, new byte[][]{ getName()})});

				setPieces( file_hasher.getPieces());

			}else{

				List<TOTorrentFileImpl>	encoded = new ArrayList<>();

				ignored = processDir( file_hasher, torrent_base, encoded, torrent_base.getName(), "", new long[1] );

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
			}

			return( ignored );

		}finally{

			file_hasher = null;
		}
	}

	private int
	createV2(
		boolean		skip_hashing )	// not implemented yet
	
		throws TOTorrentException
	{
		report( "Torrent.create.progress.hashing", (add_v1?": V2":"" ));

		for (int i=0;i<progress_listeners.size();i++){

			((TOTorrentProgressListener)progress_listeners.get(i)).reportProgress( 0 );
		}

		TOTorrentCreateV2Impl v2_creator = 
			new TOTorrentCreateV2Impl( 
				torrent_base, 
				piece_length,
				new TOTorrentCreateV2Impl.Adapter(){
					
					@Override
					public File 
					resolveFile(
						int			index,
						File 		file, 
						String 		relative_file)
					{
						File link = linkage_map.get( relative_file );
							
						if ( link != null ){

							linked_tf_map.put( String.valueOf( index ), link.getAbsolutePath());
						}

						return( link );
					}
					
					@Override
					public void 
					reportHashedBytes(
						long 	bytes )
					{
						pieceHashed( (int)( bytes/piece_length ));
					}
					
					@Override
					public void 
					report(
						String resource_key)
					{
						TOTorrentCreateImpl.this.report( resource_key );
					}
					
					@Override
					public boolean 
					ignore(
						String name)
					{
						return( ignoreFile( name ));
					}
					
					@Override
					public boolean 
					cancelled()
					{
						return( cancelled );
					}
				});
			
		Map<String,Object> v2_torrent = v2_creator.create();
		
		pieceHashed( (int)piece_count_no_pad );
		
		if ( add_v1 ){
						
			if ( v2_creator.getTotalFileSize() != total_file_size_no_pad ){
				
				throw( new TOTorrentException( "V1 and V2 total file sizes inconsistent",
						TOTorrentException.RT_READ_FAILS ));
			}
			
			if ( v2_creator.getTotalPadding() != pad_file_sizes ){
				
				throw( new TOTorrentException( "V1 and V2 padding file sizes inconsistent",
						TOTorrentException.RT_READ_FAILS ));
			}
			
			// verify files
		}
		
		setAdditionalStringProperty( TK_ENCODING, "UTF-8" );
		
		setAdditionalMapProperty( TK_V2_PIECE_LAYERS, (Map)v2_torrent.get( TK_V2_PIECE_LAYERS  ));
		
		Map v2_info = (Map)v2_torrent.get( TK_INFO );
		
		Map v2_file_tree = (Map)v2_info.get( TK_V2_FILE_TREE );
		
		addAdditionalInfoProperty( TK_V2_FILE_TREE, v2_file_tree );
		
		addAdditionalInfoProperty( TK_V2_META_VERSION, v2_info.get( TK_V2_META_VERSION ));
		
		return( v2_creator.getIgnoredFiles());
	}
	
	private int
	processDir(
		TOTorrentFileHasher			hasher,
		File						dir,
		List<TOTorrentFileImpl>		encoded,
		String						base_name,
		String						root,
		long[]						torrent_offset )

		throws TOTorrentException
	{
		File[]	dir_file_list = dir.listFiles();

		if ( dir_file_list == null ){

			throw( new TOTorrentException( "Directory '" + dir.getAbsolutePath() + "' returned error when listing files in it",
					TOTorrentException.RT_FILE_NOT_FOUND ));

		}
			// sort contents so that multiple encodes of a dir always
			// generate same torrent

		List<File> file_list = new ArrayList<>(Arrays.asList(dir_file_list));

		if ( add_v2 ){

				// v2 requires files to be in 'natural' order, default File comparator isn't
			
			Collections.sort( file_list, file_comparator_v2 );
			
		}else  if ( file_comparator == null ){
			
			Collections.sort(file_list); 

		}else{

			Collections.sort( file_list,file_comparator);
		}

		int	ignored = 0;

		for (int i=0;i<file_list.size();i++){

			File	file = (File)file_list.get(i);

			String	file_name = file.getName();

			if ( !(file_name.equals( "." ) || file_name.equals( ".." ))){

				if ( file.isDirectory()){

					if ( root.length() > 0 ){

						file_name = root + File.separator + file_name ;
					}

					ignored += processDir( hasher, file, encoded, base_name, file_name, torrent_offset );

				}else{

					if ( ignoreFile( file_name )){

						ignored++;

					}else{

						if ( add_pad_files ){
							
							long offset = torrent_offset[0];
							
							long l = offset%piece_length;
							
							if ( l > 0 ){
								
								long pad_size = piece_length - l;
							
									// System.out.println( "V1: " + file_name + ", " + offset + ", " + pad_size );
								
								hasher.addPad((int)pad_size);

								String pad_file = ".pad" + File.separator + (++pad_file_num) + "_" + pad_size;
									
								pad_file_sizes += pad_size;
								
								TOTorrentFileImpl	tf = new TOTorrentFileImpl( this, i, torrent_offset[0], pad_size, pad_file );

								tf.setAdditionalProperty( TOTorrentImpl.TK_BEP47_ATTRS, "p".getBytes( Constants.UTF_8 ));
								
								torrent_offset[0] += pad_size;
								
								encoded.add( tf );
							}
						}
						
						if ( root.length() > 0 ){

							file_name = root + File.separator + file_name;
						}

						File link = linkage_map.get( base_name + File.separator + file_name );

						if ( link != null ){

							linked_tf_map.put( String.valueOf( encoded.size()), link.getAbsolutePath());
						}

						long length = hasher.add( link==null?file:link );

						TOTorrentFileImpl	tf = new TOTorrentFileImpl( this, i, torrent_offset[0], length, file_name );

						torrent_offset[0] += length;

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
		int	this_progress = (int)((piece_number*100)/piece_count_no_pad );

		if ( this_progress > 100 ){
			
			this_progress = 100;
		}
		
		if ( this_progress != reported_progress ){

			reported_progress = this_progress;

			for (int i=0;i<progress_listeners.size();i++){
	
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
		if ( total_file_size_no_pad == -1 ){

			total_file_size_no_pad = getTotalFileSize( file );
		}

		return( total_file_size_no_pad );
	}

	protected long
	getTotalFileSize(
		File				file )

		throws TOTorrentException
	{
		report( "Torrent.create.progress.parsingfiles" );

		long res = getTotalFileSizeSupport( file, "" );

		report( "Torrent.create.progress.totalfilesize", res );

		report( "Torrent.create.progress.totalfilecount", ""+total_file_count_no_pad );

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

			throw( new TOTorrentException( "File '" + file.getName() + "' doesn't exist",
											TOTorrentException.RT_FILE_NOT_FOUND ));
		}

		if ( file.isFile()){

			if ( !ignoreFile( name )){

				total_file_count_no_pad++;

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

				throw( new TOTorrentException( "Directory '" + file.getAbsolutePath() + "' returned error when listing files in it",
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

				((TOTorrentProgressListener)progress_listeners.get(i)).reportCurrentTask( prefix + (additional_text==null?"":( " " + additional_text )));
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

	private void
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
		String		file_name )
	{
		if ( ignore_set.contains(file_name.toLowerCase())){

			report( "Torrent.create.progress.ignoringfile", " '" + file_name + "'" );

			return( true );
		}

		String converted = FileUtil.convertOSSpecificChars(file_name, false );
		
		if ( !converted.equals( file_name )){
			
			report( "Torrent.create.progress.fileconvwarn", " '" + file_name + "' -> '" + converted + "'" );
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
