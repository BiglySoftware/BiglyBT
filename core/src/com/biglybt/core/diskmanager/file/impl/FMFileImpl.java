/*
 * File    : FMFileImpl.java
 * Created : 12-Feb-2004
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
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.diskmanager.file.impl;

/**
 * @author parg
 *
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.diskmanager.file.FMFile;
import com.biglybt.core.diskmanager.file.FMFileManagerException;
import com.biglybt.core.diskmanager.file.FMFileOwner;
import com.biglybt.core.diskmanager.file.impl.FMFileAccess.FileAccessor;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.*;

public abstract class
FMFileImpl
	implements FMFile
{
	protected static final String		READ_ACCESS_MODE	= "r";
	protected static final String		WRITE_ACCESS_MODE	= "rw"; // "rwd"; - removing this to improve performance

	private static final Map<StringInterner.FileKey,List<Object[]>>			file_map 		= new HashMap<>();
	
	private static final AEMonitor		file_map_mon	= new AEMonitor( "FMFile:map");

	// If there is an exception that occurs, which causes us to try and perform
	// a reopen, setting this flag to true will print it to debug.
	private static final boolean OUTPUT_REOPEN_RELATED_ERRORS = true;

	static{
		AEDiagnostics.addEvidenceGenerator(
			new AEDiagnosticsEvidenceGenerator()
			{
				@Override
				public void
				generate(
					IndentWriter		writer )
				{
					generateEvidence( writer );
				}
			});
	}

	static volatile boolean	switch_to_upload_only_enable;
	
	static{
		 COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				ConfigKeys.File.BCFG_UPLOAD_ONLY_ON_WRITE_ERROR_ENABLE,
			},
			new ParameterListener(){
				@Override
				public void parameterChanged(String parameterName) {
										
	    	    	switch_to_upload_only_enable	= COConfigurationManager.getBooleanParameter( ConfigKeys.File.BCFG_UPLOAD_ONLY_ON_WRITE_ERROR_ENABLE );
				}
			});
	}
	
	private final FMFileManagerImpl	manager;
	private final FMFileOwner		owner;
	
	private int					access_mode			= FM_READ;

	private StringInterner.FileKey	linked_file;
	private long					last_modified;
	private StringInterner.FileKey	canonical_path;
	private FileAccessor			fa;

	private FMFileAccessController		file_access;

	private File				created_dirs_leaf;
	private List<File>			created_dirs;

	protected final AEMonitor			this_mon	= new AEMonitor( "FMFile" );

	private volatile long		length_cache = -1;
	
	private boolean				clone;

	protected
	FMFileImpl(
		FMFileOwner				_owner,
		FMFileManagerImpl		_manager,
		StringInterner.FileKey	_file,
		int						_type,
		boolean 				_force )

		throws FMFileManagerException
	{
		owner			= _owner;
		manager			= _manager;

		linked_file		= owner.getFileLink( _file ); //  manager.getFileLink( tf.getTorrent(), tf.getIndex(), _file );

		boolean	file_was_created	= false;
		boolean	file_reserved		= false;
		boolean	ok 					= false;

		File lf = linked_file.getFile();

		try{			
			String linked_path = lf.getPath();
			try {

				String cp = FileUtil.getCanonicalPath( lf, true );
				
				if ( cp.equals(linked_path)){
					
					canonical_path = linked_file;
				}else{
					
					canonical_path = new StringInterner.FileKey( cp );
				}

			}catch( IOException ioe ) {

				String msg = ioe.getMessage();

		        if( msg != null && msg.contains("There are no more files")) {

		          String abs_path = lf.getAbsolutePath();

		          String error = "Caught 'There are no more files' exception during file.getCanonicalPath(). " +
		                         "os=[" +Constants.OSName+ "], file.getPath()=[" + linked_path + "], file.getAbsolutePath()=[" +abs_path+ "]. ";

		          Debug.out( error, ioe );
		        }

		        throw ioe;
			}

			createDirs( lf );

			reserveFile();

			file_reserved	= true;

			file_access = new FMFileAccessController( this, _type, _force );

			ok	= true;

		}catch( Throwable e ){

			if ( file_was_created ){

				lf.delete();
			}

			deleteDirs();

			if ( e instanceof FMFileManagerException ){

				throw((FMFileManagerException)e);
			}

			throw( new FMFileManagerException( FMFileManagerException.OP_OPEN, "initialisation failed", e ));

		}finally{

			if ( file_reserved && !ok ){

				releaseFile();
			}
		}
	}

	protected
	FMFileImpl(
		FMFileImpl		basis )

		throws FMFileManagerException
	{
		owner			= basis.owner;
		manager			= basis.manager;
		linked_file		= basis.linked_file;
		canonical_path	= basis.canonical_path;

		clone			= true;

		try{
			file_access = new FMFileAccessController( this, basis.file_access.getStorageType(), false );

		}catch( Throwable e ){

			if ( e instanceof FMFileManagerException ){

				throw((FMFileManagerException)e);
			}

			throw( new FMFileManagerException( FMFileManagerException.OP_OPEN, "initialisation failed", e ));
		}
	}

	protected FMFileManagerImpl
	getManager()
	{
		return( manager );
	}

	@Override
	public String
	getName()
	{
		return( linked_file.toString());
	}

	@Override
	public boolean
	exists()
	{
		File lf = linked_file.getFile();
		
		return( FileUtil.exists( lf ));
	}

	protected File
	getFile()
	{
		return( linked_file.getFile());
	}
	
	@Override
	public FMFileOwner
	getOwner()
	{
		return( owner );
	}

	@Override
	public boolean
	isClone()
	{
		return( clone );
	}

	@Override
	public void
	setStorageType(
		int		new_type,
		boolean	force )

		throws FMFileManagerException
	{
		try{
			this_mon.enter();

			boolean	was_open = isOpen();

			if ( was_open ){

				closeSupport( false );
			}

			try{
				file_access.setStorageType(  new_type, force );

			}finally{

				if ( was_open ){

					openSupport( "Re-open after storage type change" );
				}
			}

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public int
	getStorageType()
	{
		return( file_access.getStorageType());
	}

	@Override
	public int
	getAccessMode()
	{
		return( access_mode );
	}

	protected void
	setAccessModeSupport(
		int		mode )
	{
		access_mode	= mode;
	}

	protected File
	getLinkedFile()
	{
		return( linked_file.getFile());
	}

	@Override
	public void
	moveFile(
		File						new_linked_file,
		FileUtil.ProgressListener	pl )

		throws FMFileManagerException
	{
		try{
			this_mon.enter();

				// when a file is being moved we want to prevent other operations from being blocked simply if they want
				// to read the file length
			
			length_cache = getLength();
			
			String	new_canonical_path;
				
				// 3701 - switched the passed file to always be the actual destination regardless of any links, these are set up separately as required by callers
				// TOTorrentFile tf = owner.getTorrentFile();
				// File	new_linked_file	= manager.getFileLink( tf.getTorrent(), tf.getIndex(), new StringInterner.FileKey( new_unlinked_file )).getFile();

			try{

		        try {

		          new_canonical_path = new_linked_file.getCanonicalPath();


		        }catch( IOException ioe ) {

		          String msg = ioe.getMessage();

		          if( msg != null && msg.contains("There are no more files")) {
		            String abs_path = new_linked_file.getAbsolutePath();
		            String error = "Caught 'There are no more files' exception during new_file.getCanonicalPath(). " +
		                           "os=[" +Constants.OSName+ "], new_file.getPath()=[" +new_linked_file.getPath()+ "], new_file.getAbsolutePath()=[" +abs_path+ "]. ";
		                           //"new_canonical_path temporarily set to [" +abs_path+ "]";
		            Debug.out( error, ioe );
		          }
		          throw ioe;
		        }

			}catch( Throwable e ){

				throw( new FMFileManagerException( FMFileManagerException.OP_OTHER, "getCanonicalPath fails", e ));
			}

			if ( FileUtil.exists( new_linked_file )){

				throw( new FMFileManagerException( FMFileManagerException.OP_OTHER, "moveFile fails - file '" + new_canonical_path + "' already exists"));
			}

			boolean	was_open	= isOpen();

			close();	// full close, this will release any slots in the limited file case

			createDirs( new_linked_file );

			File lf = linked_file.getFile();
			
			if ( !FileUtil.exists( lf ) || FileUtil.renameFile( lf, new_linked_file, pl )) {

				linked_file		= new StringInterner.FileKey( new_linked_file );
				canonical_path	= new StringInterner.FileKey( new_canonical_path );

				reserveFile();

				if ( was_open ){

					ensureOpen( "moveFile target" );	// ensure open will regain slots in limited file case
				}

			}else{

				try{
					reserveFile();

				}catch( FMFileManagerException e ){

					Debug.printStackTrace( e );
				}

				if ( was_open ){

					try{
						ensureOpen( "moveFile recovery" );

					}catch( FMFileManagerException e){

						Debug.printStackTrace( e );
					}
				}

				throw( new FMFileManagerException( FMFileManagerException.OP_OTHER, "moveFile fails"));
			}
		}finally{

			length_cache = -1;
			
			this_mon.exit();
		}
	}

	@Override
	public void
	renameFile(
		String		new_name )

		throws FMFileManagerException
	{		
		try{
			this_mon.enter();

			File lf = linked_file.getFile();

			length_cache = getLength();
			
			String	new_canonical_path;

			File 	new_linked_file = FileUtil.newFile( lf.getParentFile(), new_name );

			try{

		        try {

		          new_canonical_path = new_linked_file.getCanonicalPath();


		        }catch( IOException ioe ) {

		          String msg = ioe.getMessage();

		          if( msg != null && msg.contains("There are no more files")) {
		            String abs_path = new_linked_file.getAbsolutePath();
		            String error = "Caught 'There are no more files' exception during new_file.getCanonicalPath(). " +
		                           "os=[" +Constants.OSName+ "], new_file.getPath()=[" +new_linked_file.getPath()+ "], new_file.getAbsolutePath()=[" +abs_path+ "]. ";
		                           //"new_canonical_path temporarily set to [" +abs_path+ "]";
		            Debug.out( error, ioe );
		          }
		          throw ioe;
		        }

			}catch( Throwable e ){

				throw( new FMFileManagerException( FMFileManagerException.OP_OTHER, "getCanonicalPath fails", e ));
			}

			if ( FileUtil.exists( new_linked_file )){				
				
				throw( 
					new FMFileManagerException( 
						FMFileManagerException.OP_OTHER, 
						"renameFile fails - new file '" + new_canonical_path + "' already exists (old file='" +
								lf + "', exists=" + FileUtil.exists(lf) +")"));
			}

			boolean	was_open	= isOpen();

			close();	// full close, this will release any slots in the limited file case

			if ( !FileUtil.exists( lf ) || lf.renameTo( new_linked_file )){

				linked_file		= new StringInterner.FileKey( new_linked_file );
				canonical_path	= new StringInterner.FileKey( new_canonical_path );

				reserveFile();

				if ( was_open ){

					ensureOpen( "renameFile target" );	// ensure open will regain slots in limited file case
				}

			}else{

				try{
					reserveFile();

				}catch( FMFileManagerException e ){

					Debug.printStackTrace( e );
				}

				if ( was_open ){

					try{
						ensureOpen( "renameFile recovery" );

					}catch( FMFileManagerException e){

						Debug.printStackTrace( e );
					}
				}

				throw( new FMFileManagerException( FMFileManagerException.OP_OTHER, "renameFile fails"));
			}
		}finally{

			length_cache = -1;
			
			this_mon.exit();
		}
	}

	@Override
	public void
	ensureOpen(
		String	reason )

		throws FMFileManagerException
	{
		try{
			this_mon.enter();

			if ( isOpen()){

				return;
			}

			openSupport( reason );

		}finally{

			this_mon.exit();
		}
	}

	protected long
	getLengthCache()
	{
		return( length_cache );
	}
	
	protected long
	getLengthSupport()

		throws FMFileManagerException
	{
		try{
			return( file_access.getLength( fa ));

		}catch( FMFileManagerException e ){

			if (OUTPUT_REOPEN_RELATED_ERRORS) {Debug.printStackTrace(e);}

			try{
				reopen( e );

				return( file_access.getLength( fa ));

			}catch( Throwable e2 ){

				throw( e );
			}
		}
	}

	protected void
	setLengthSupport(
		long		length )

		throws FMFileManagerException
	{		
		try{
			file_access.setLength( fa, length );

		}catch( FMFileManagerException e ){

			if (OUTPUT_REOPEN_RELATED_ERRORS) {Debug.printStackTrace(e);}

			try{
				reopen( e );

				file_access.setLength( fa, length );

			}catch( Throwable e2 ){

				throw( e );
			}
		}
	}

	private void
	getFileAccessor()
		throws FileNotFoundException
	{
		File lf = linked_file.getFile();
		
		try{
		
			fa = FileUtil.newFileAccessor( lf, access_mode==FM_READ?READ_ACCESS_MODE:WRITE_ACCESS_MODE);
			
		}catch( FileNotFoundException e ){
			
				// relax to read-only if we are trying to handle switching to upload only on write errors
			
			if ( 	switch_to_upload_only_enable && 
					access_mode == FM_WRITE && 
					FileUtil.exists( lf )  && 
					lf.canRead()){
				
				fa = FileUtil.newFileAccessor( lf, READ_ACCESS_MODE );
				
			}else{
				
				throw( e );
			}
		}
	}
	
	protected void
	reopen(
		FMFileManagerException		cause )

		throws Throwable
	{
		if ( !cause.isRecoverable()){

			throw( cause );
		}

		if ( fa != null ){

			try{

				fa.close();

			}catch( Throwable e ){

					// ignore any close failure as can't do much
			}

				// don't clear down raf here as we want to leave things looking as they were
				// if the subsequent open fails
		}

		file_access.aboutToOpen();

		getFileAccessor();

		last_modified = 0;
		
		Debug.outNoStack( "Recovered connection to " + getName() + " after access failure" );
	}

	protected void
	openSupport(
		String	reason )

		throws FMFileManagerException
	{
		if ( fa != null ){

			throw( new FMFileManagerException( FMFileManagerException.OP_OPEN, "file already open" ));
		}

		reserveAccess( reason );

		try{
			file_access.aboutToOpen();

			getFileAccessor();

			last_modified = 0;
			
		}catch( FileNotFoundException e ){

			int st = file_access.getStorageType();

			boolean	ok = false;

				// edge case here when switching one file from dnd -> download and the compact files at edge boundaries not
				// yet allocated - attempt to create the file on demand

			try{
				File lf = linked_file.getFile();
				
				lf.getParentFile().mkdirs();

				lf.createNewFile();

				getFileAccessor();

				last_modified = 0;
				
				ok = true;

			}catch( Throwable f ){
				
				Debug.printStackTrace( f );
			}

			if ( !ok ){

				Debug.printStackTrace( e );

				throw( new FMFileManagerException( access_mode==FM_READ?FMFileManagerException.OP_READ:FMFileManagerException.OP_WRITE, "open fails for '" + linked_file.getFile().getAbsolutePath() + "'", e ));
			}
		}catch( Throwable e ){

			Debug.printStackTrace( e );

			throw( new FMFileManagerException( access_mode==FM_READ?FMFileManagerException.OP_READ:FMFileManagerException.OP_WRITE, "open fails for '" + linked_file.getFile().getAbsolutePath() + "'", e ));
		}
	}

	protected void
	closeSupport(
		boolean		explicit )

		throws FMFileManagerException
	{
		FMFileManagerException	flush_exception = null;

		try{
			flush();

		}catch( FMFileManagerException e ){

			flush_exception = e;
		}

		if ( fa == null ){

				// may have previously been implicitly closed, tidy up if required

			if ( explicit ){

				releaseFile();

				deleteDirs();
			}
		}else{

			try{
				fa.close();

			}catch( Throwable e ){

				throw( new FMFileManagerException( FMFileManagerException.OP_CLOSE, "close fails", e ));

			}finally{

				fa	= null;

				if ( explicit ){

					releaseFile();
				}
			}
		}

		if ( flush_exception != null ){

			throw( flush_exception );
		}
	}

	@Override
	public void
	flush()

		throws FMFileManagerException
	{
		file_access.flush();
	}

	protected boolean
	isPieceCompleteProcessingNeeded(
		int					piece_number )

		throws FMFileManagerException
	{
		return( file_access.isPieceCompleteProcessingNeeded( piece_number ));
	}

	protected void
	setPieceCompleteSupport(
		int					piece_number,
		DirectByteBuffer	piece_data )

		throws FMFileManagerException
	{
		file_access.setPieceComplete( fa, piece_number, piece_data );
	}

	@Override
	public void
	delete()

		throws FMFileManagerException
	{
		close();

		File lf = linked_file.getFile();

		if ( FileUtil.exists( lf )){

			if ( !lf.delete()){

				throw( new FMFileManagerException( FMFileManagerException.OP_OTHER, "Failed to delete '" + linked_file + "'" ));
			}
		}
	}

	protected void
	readSupport(
		DirectByteBuffer	buffer,
		long				position )

		throws FMFileManagerException
	{
		readSupport(new DirectByteBuffer[]{buffer}, position );
	}

	protected void
	readSupport(
		DirectByteBuffer[]	buffers,
		long				position )

		throws FMFileManagerException
	{
		try{

			file_access.read( fa, buffers, position );

		}catch( FMFileManagerException e ){

			if (OUTPUT_REOPEN_RELATED_ERRORS) {Debug.printStackTrace(e);}

			try{
				reopen( e );

				file_access.read( fa, buffers, position );

			}catch( Throwable e2 ){

				throw( e );
			}
		}
	}

	protected void
	writeSupport(
		DirectByteBuffer		buffer,
		long					position )

		throws FMFileManagerException
	{
		writeSupport(new DirectByteBuffer[]{buffer}, position );
	}

	protected void
	writeSupport(
		DirectByteBuffer[]		buffers,
		long					position )

		throws FMFileManagerException
	{
		try{

			file_access.write( fa, buffers, position );

			last_modified = SystemTime.getCurrentTime();
			
		}catch( FMFileManagerException e ){

			if (OUTPUT_REOPEN_RELATED_ERRORS) {Debug.printStackTrace(e);}

			try{
				reopen( e );

				file_access.write( fa, buffers, position );

				last_modified = SystemTime.getCurrentTime();
				
			}catch( Throwable e2 ){

				throw( e );
			}
		}
	}

	@Override
	public boolean
	isOpen()
	{
		return( fa != null );
	}
	
	@Override
	public long
	getLastModified()
	{
		if (last_modified == 0) {
			last_modified = linked_file.getFile().lastModified();
		}
		return( last_modified );
	}
	

		// file reservation is used to manage the possibility of multiple torrents
		// referring to the same file. Initially introduced to stop a common problem
		// whereby different torrents contain the same files - without
		// this code the torrents could interfere resulting in all sorts of problems
		// The original behavior was to completely prevent the sharing of files.
		// However, better behaviour is to allow sharing of a file as long as only
		// read access is required.
		// we store a list of owners against each canonical file with a boolean "write" marker

	private void
	reserveFile()

		throws FMFileManagerException
	{
		if ( clone ){

			return;
		}

		try{
			file_map_mon.enter();

			// System.out.println( "FMFile::reserveFile:" + canonical_path + "("+ owner.getName() + ")" + " - " + Debug.getCompressedStackTrace() );

			List<Object[]>	owners = file_map.get(canonical_path);

			if ( owners == null ){

				owners = new ArrayList<>(2);

				//System.out.println( "    creating new owners entr" );

				file_map.put( canonical_path, owners );
				
			}else{

				TOTorrentFile	my_torrent_file = owner.getTorrentFile();
				TOTorrent		my_torrent		= my_torrent_file==null?null:my_torrent_file.getTorrent();
				
				for ( Object[] entry: owners ){
	
					FMFileOwner	this_owner = (FMFileOwner)entry[0];
	
					if ( my_torrent != null ){
						
						TOTorrentFile	this_tf 		= this_owner.getTorrentFile();
						
						if ( this_tf != null ){
						
							TOTorrent		this_torrent	= this_tf.getTorrent();
							
							if ( this_torrent == my_torrent && this_tf != my_torrent_file ){
								
								throw( new FMFileManagerException(  FMFileManagerException.OP_OTHER, "File '"+canonical_path + "' occurs more than once in download.\nRename one of the files in Files view via the right-click menu." ));
							}
						}
					}
										
					String	entry_name = this_owner.getName();
	
					//System.out.println( "    existing entry: " + entry_name );
	
					if ( owner.getName().equals( entry_name )){
	
							// already present, start off read-access
	
						Debug.out( "reserve file - entry already present" );
	
						entry[1] = Boolean.FALSE;
	
						return;
					}
				}
			}

			owners.add( new Object[]{ owner, Boolean.FALSE, "<reservation>" });

		}finally{

			file_map_mon.exit();
		}
	}

	private void
	reserveAccess(
		String	reason )

		throws FMFileManagerException
	{
		if ( clone ){

			return;
		}

		try{
			file_map_mon.enter();

			//System.out.println( "FMFile::reserveAccess:" + canonical_path + "("+ owner.getName() + ")" + " [" + (access_mode==FM_WRITE?"write":"read") + "]" + " - " + Debug.getCompressedStackTrace());

			List<Object[]>	owners = file_map.get( canonical_path );

			Object[]	my_entry = null;

			if ( owners == null ){

				String str = "File '"+canonical_path+"' has not been reserved (no entries), '" + owner.getName()+"'";
						
				Debug.out( "reserveAccess fail: " + str );

				throw( new FMFileManagerException( FMFileManagerException.OP_OTHER, str ));
			}

			for ( Object[] entry: owners ){

				String	entry_name = ((FMFileOwner)entry[0]).getName();

				//System.out.println( "    existing entry: " + entry_name );

				if ( owner.getName().equals( entry_name )){

					my_entry	= entry;
				}
			}

			if ( my_entry == null ){

				String str = "File '"+canonical_path+"' has not been reserved (not found), '" + owner.getName()+"'";
				
				Debug.out( "reserveAccess fail: " + str );

				throw( new FMFileManagerException( FMFileManagerException.OP_OTHER, str ));
			}

			my_entry[1] = Boolean.valueOf(access_mode == FM_WRITE);
			my_entry[2] = reason;

			int	read_access 		= 0;
			int write_access		= 0;
			int	write_access_lax	= 0;
			int	write_access_hybrid	= 0;

			TOTorrentFile	my_torrent_file = owner.getTorrentFile();
			TOTorrent		my_torrent		= my_torrent_file==null?null:my_torrent_file.getTorrent();
			
			byte[] my_v2_hash = null;
			
			if ( my_torrent != null && my_torrent.getTorrentType() == TOTorrent.TT_V1_V2 ){
				
				try{
					my_v2_hash = my_torrent.getFullHash( TOTorrent.TT_V2 );
					
				}catch( Throwable e ){

					Debug.out( e );
				}
			}
			
			StringBuilder	users_sb = owners.size()==1?null:new StringBuilder( 128 );

			for ( Object[] entry: owners ){

				FMFileOwner	this_owner = (FMFileOwner)entry[0];

				if (((Boolean)entry[1]).booleanValue()){

					write_access++;

					TOTorrentFile this_tf = this_owner.getTorrentFile();

					if ( my_torrent_file != null && this_tf != null ){
						
						if ( my_torrent_file.getLength() == this_tf.getLength()){

							write_access_lax++;
						}
						
						if ( my_v2_hash != null ){
							
							TOTorrent	this_torrent = this_tf.getTorrent();
							
							if ( this_torrent != null && this_torrent.getTorrentType() == TOTorrent.TT_V1_V2 ){
								
								try{
									if ( Arrays.equals( my_v2_hash, this_torrent.getFullHash( TOTorrent.TT_V2 ))){
									
										write_access_hybrid++;
									}
								}catch( Throwable e ){
									
									Debug.out( e );
								}
							}
						}
					}

					if ( users_sb != null ){
						if ( users_sb.length() > 0 ){
							users_sb.append( "," );
						}
						users_sb.append( this_owner.getName());
						users_sb.append( " [write]" );
					}

				}else{

					read_access++;

					if ( users_sb != null ){
						if ( users_sb.length() > 0 ){
							users_sb.append( "," );
						}
						users_sb.append( this_owner.getName());
						users_sb.append( " [read]" );
					}
				}
			}

			if ( 	write_access > 1 ||
					( write_access == 1 && read_access > 0 )){

					// relax locking for shared hybrid swarms				

				if ( write_access_hybrid == write_access ){

					return;
				}
				
					// relax locking if strict is disabled and torrent file is same size

				if ( !COConfigurationManager.getBooleanParameter( "File.strict.locking" )){

					if ( write_access_lax == write_access ){

						return;
					}
				}
				
				String str = "File '"+canonical_path+"' is in use by '" + (users_sb==null?"eh?":users_sb.toString()) +"'";
				
				Debug.out( "reserveAccess fail: " + str );
				
				throw( new FMFileManagerException( FMFileManagerException.OP_OTHER, str ));
			}

		}finally{

			file_map_mon.exit();
		}
	}

	private void
	releaseFile()
	{
		if ( clone ){

			return;
		}

		try{
			file_map_mon.enter();

			// System.out.println( "FMFile::releaseFile:" + canonical_path + "("+ owner.getName() + ")" + " - " + Debug.getCompressedStackTrace());

			List<Object[]>	owners = file_map.get( canonical_path );

			if ( owners != null ){

				for ( Iterator<Object[]> it=owners.iterator();it.hasNext();){

					Object[] entry = it.next();
					
					if ( owner.getName().equals(((FMFileOwner)entry[0]).getName())){

						it.remove();

						break;
					}
				}

				if ( owners.size() == 0 ){

					file_map.remove( canonical_path );
				}
			}
		}finally{

			file_map_mon.exit();
		}
	}

	protected void
	createDirs(
		File		target )

		throws FMFileManagerException
	{
		if ( clone ){

			return;
		}

		deleteDirs();

		File	parent = target.getParentFile();

		if ( !FileUtil.existsWithCache( parent )){

			List<File>	new_dirs = new ArrayList<>();

			File	current = parent;

			while( current != null && !FileUtil.exists( current )){

				new_dirs.add( current );

				current = current.getParentFile();
			}

			created_dirs_leaf	= target;
			created_dirs		= new ArrayList<>();

			if ( FileUtil.mkdirs(parent)){

				created_dirs		= new_dirs;

				/*
				for (int i=created_dirs.size()-1;i>=0;i--){

					System.out.println( "created " + created_dirs.get(i));
				}
				*/
			}else{
					// had some reports of this exception being thrown when starting a torrent
					// double check in case there's some parallel creation being triggered somehow

				try{
					Thread.sleep( 100 + RandomUtils.nextInt( 900 ));

				}catch( Throwable e ){
				}

				FileUtil.mkdirs( parent );

				if ( parent.isDirectory()){

					created_dirs		= new_dirs;

				}else{

					FMFileManagerException error = new FMFileManagerException( FMFileManagerException.OP_OTHER, MessageText.getString( "DownloadManager.error.datamissing" ) + ": " + target.getAbsolutePath());
					
					error.setType(FMFileManagerException.ET_FILE_OR_DIR_MISSING );
					
					throw( error );
				}
        	}
        }
	}

	protected void
	deleteDirs()
	{
		if ( clone ){

			return;
		}

		if ( created_dirs_leaf != null ){

				// delete any dirs we created if the target file doesn't exist

			if ( !FileUtil.exists( created_dirs_leaf )){

				Iterator<File>	it = created_dirs.iterator();

				while( it.hasNext()){

					File	dir = it.next();

					if ( FileUtil.exists( dir ) && dir.isDirectory()){

						File[]	entries = dir.listFiles();

						if ( entries == null || entries.length == 0 ){

							// System.out.println( "deleted " + dir );

							dir.delete();

						}else{

							break;
						}
					}else{

						break;
					}
				}
			}

			created_dirs_leaf	= null;
			created_dirs 		= null;
		}
	}

	protected String
	getString()
	{
		String cp = canonical_path.toString();
		
		File cPath = canonical_path.getFile();
		String sPaths;
		
		FileAccessor current_fa = fa;
		
		String fa_str = current_fa==null?"null":current_fa.getString();
		
		if (cPath.equals(linked_file)){
			
			sPaths = "can/link=" + Debug.secretFileName(cp);
			
		}else{
			
			sPaths = "can=" + Debug.secretFileName(cp) + ",link="
					+ Debug.secretFileName(linked_file.toString());
		}
		
		return sPaths + ",fa=" + fa_str + ",acc=" + access_mode + ",ctrl={"
				+ file_access.getString()+ "}";
	}

	protected static void
	generateEvidence(
		IndentWriter	writer )
	{
		writer.println( file_map.size() + " FMFile Reservations" );

		try{
			writer.indent();

			try{
				file_map_mon.enter();

				Iterator<StringInterner.FileKey>	it = file_map.keySet().iterator();

				while( it.hasNext()){

					StringInterner.FileKey	key = it.next();

					List<Object[]>	owners = file_map.get(key);

					Iterator<Object[]>	it2 = owners.iterator();

					String	str = "";

					while( it2.hasNext()){

						Object[]	entry = it2.next();

						FMFileOwner	owner 	= (FMFileOwner)entry[0];
						Boolean		write	= (Boolean)entry[1];
						String		reason	= (String)entry[2];


						str += (str.length()==0?"":", ") + owner.getName() + "[" + (write.booleanValue()?"write":"read")+ "/" + reason + "]";
					}


					writer.println( Debug.secretFileName(key.toString()) + " -> " + str );
				}
			}finally{

				file_map_mon.exit();
			}

			FMFileManagerImpl.generateEvidence( writer );

		}finally{

			writer.exdent();
		}
	}
}
