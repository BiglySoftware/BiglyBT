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
import java.io.RandomAccessFile;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.diskmanager.file.FMFile;
import com.biglybt.core.diskmanager.file.FMFileManagerException;
import com.biglybt.core.diskmanager.file.FMFileOwner;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.*;

public abstract class
FMFileImpl
	implements FMFile
{
	protected static final String		READ_ACCESS_MODE	= "r";
	protected static final String		WRITE_ACCESS_MODE	= "rw"; // "rwd"; - removing this to improve performance

	private static final Map			file_map = new HashMap();
	private static final AEMonitor	file_map_mon	= new AEMonitor( "FMFile:map");

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

	private final FMFileManagerImpl	manager;
	private final FMFileOwner		owner;
	
	private int					access_mode			= FM_READ;

	private File				linked_file;
	private long				last_modified;
	private String				canonical_path;
	private RandomAccessFile	raf;

	private FMFileAccessController		file_access;

	private File				created_dirs_leaf;
	private List				created_dirs;

	protected final AEMonitor			this_mon	= new AEMonitor( "FMFile" );

	private boolean				clone;

	protected
	FMFileImpl(
		FMFileOwner			_owner,
		FMFileManagerImpl	_manager,
		File				_file,
		int					_type )

		throws FMFileManagerException
	{
		owner			= _owner;
		manager			= _manager;

		TOTorrentFile tf = owner.getTorrentFile();

		linked_file		= manager.getFileLink( tf.getTorrent(), tf.getIndex(), _file );

		boolean	file_was_created	= false;
		boolean	file_reserved		= false;
		boolean	ok 					= false;

		try{

			try {

				canonical_path = linked_file.getCanonicalPath();
				if(canonical_path.equals(linked_file.getPath()))
					canonical_path = linked_file.getPath();

			}catch( IOException ioe ) {

				String msg = ioe.getMessage();

		        if( msg != null && msg.contains("There are no more files")) {

		          String abs_path = linked_file.getAbsolutePath();

		          String error = "Caught 'There are no more files' exception during file.getCanonicalPath(). " +
		                         "os=[" +Constants.OSName+ "], file.getPath()=[" +linked_file.getPath()+ "], file.getAbsolutePath()=[" +abs_path+ "]. ";

		          Debug.out( error, ioe );
		        }

		        throw ioe;
			}

			createDirs( linked_file );

			reserveFile();

			file_reserved	= true;

			file_access = new FMFileAccessController( this, _type );

			last_modified = linked_file.lastModified();
			
			ok	= true;

		}catch( Throwable e ){

			if ( file_was_created ){

				linked_file.delete();
			}

			deleteDirs();

			if ( e instanceof FMFileManagerException ){

				throw((FMFileManagerException)e);
			}

			throw( new FMFileManagerException( "initialisation failed", e ));

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
			file_access = new FMFileAccessController( this, basis.file_access.getStorageType());

			last_modified = linked_file.lastModified();
			
		}catch( Throwable e ){

			if ( e instanceof FMFileManagerException ){

				throw((FMFileManagerException)e);
			}

			throw( new FMFileManagerException( "initialisation failed", e ));
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
		return( linked_file.exists());
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
		int		new_type )

		throws FMFileManagerException
	{
		try{
			this_mon.enter();

			boolean	was_open = isOpen();

			if ( was_open ){

				closeSupport( false );
			}

			try{
				file_access.setStorageType(  new_type );

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
		return( linked_file );
	}

	@Override
	public void
	moveFile(
		File						new_unlinked_file,
		FileUtil.ProgressListener	pl )

		throws FMFileManagerException
	{
		try{
			this_mon.enter();

			TOTorrentFile tf = owner.getTorrentFile();

			String	new_canonical_path;

			File	new_linked_file	= manager.getFileLink( tf.getTorrent(), tf.getIndex(), new_unlinked_file );

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

				throw( new FMFileManagerException( "getCanonicalPath fails", e ));
			}

			if ( new_linked_file.exists()){

				throw( new FMFileManagerException( "moveFile fails - file '" + new_canonical_path + "' already exists"));
			}

			boolean	was_open	= isOpen();

			close();	// full close, this will release any slots in the limited file case

			createDirs( new_linked_file );

			if ( !linked_file.exists() || FileUtil.renameFile( linked_file, new_linked_file, pl )) {

				linked_file		= new_linked_file;
				canonical_path	= new_canonical_path;

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

				throw( new FMFileManagerException( "moveFile fails"));
			}
		}finally{

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

			String	new_canonical_path;

			File 	new_linked_file = new File( linked_file.getParentFile(), new_name );

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

				throw( new FMFileManagerException( "getCanonicalPath fails", e ));
			}

			if ( new_linked_file.exists()){

				throw( new FMFileManagerException( "renameFile fails - file '" + new_canonical_path + "' already exists"));
			}

			boolean	was_open	= isOpen();

			close();	// full close, this will release any slots in the limited file case

			if ( !linked_file.exists() || linked_file.renameTo( new_linked_file )){

				linked_file		= new_linked_file;
				canonical_path	= new_canonical_path;

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

				throw( new FMFileManagerException( "renameFile fails"));
			}
		}finally{

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
	getLengthSupport()

		throws FMFileManagerException
	{
		try{
			return( file_access.getLength( raf ));

		}catch( FMFileManagerException e ){

			if (OUTPUT_REOPEN_RELATED_ERRORS) {Debug.printStackTrace(e);}

			try{
				reopen( e );

				return( file_access.getLength( raf ));

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
			file_access.setLength( raf, length );

		}catch( FMFileManagerException e ){

			if (OUTPUT_REOPEN_RELATED_ERRORS) {Debug.printStackTrace(e);}

			try{
				reopen( e );

				file_access.setLength( raf, length );

			}catch( Throwable e2 ){

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

		if ( raf != null ){

			try{

				raf.close();

			}catch( Throwable e ){

					// ignore any close failure as can't do much
			}

				// don't clear down raf here as we want to leave things looking as they were
				// if the subsequent open fails
		}

		file_access.aboutToOpen();

		raf = new RandomAccessFile( linked_file, access_mode==FM_READ?READ_ACCESS_MODE:WRITE_ACCESS_MODE);

		last_modified = linked_file.lastModified();
		
		Debug.outNoStack( "Recovered connection to " + getName() + " after access failure" );
	}

	protected void
	openSupport(
		String	reason )

		throws FMFileManagerException
	{
		if ( raf != null ){

			throw( new FMFileManagerException( "file already open" ));
		}

		reserveAccess( reason );

		try{
			file_access.aboutToOpen();

			raf = new RandomAccessFile( linked_file, access_mode==FM_READ?READ_ACCESS_MODE:WRITE_ACCESS_MODE);

			last_modified = linked_file.lastModified();
			
		}catch( FileNotFoundException e ){

			int st = file_access.getStorageType();

			boolean	ok = false;

				// edge case here when switching one file from dnd -> download and the compact files at edge boundaries not
				// yet allocated - attempt to create the file on demand

			try{
				linked_file.getParentFile().mkdirs();

				linked_file.createNewFile();

				raf = new RandomAccessFile( linked_file, access_mode==FM_READ?READ_ACCESS_MODE:WRITE_ACCESS_MODE);

				last_modified = linked_file.lastModified();
				
				ok = true;

			}catch( Throwable f ){
			}

			if ( !ok ){

				Debug.printStackTrace( e );

				throw( new FMFileManagerException( "open fails", e ));
			}
		}catch( Throwable e ){

			Debug.printStackTrace( e );

			throw( new FMFileManagerException( "open fails", e ));
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

		if ( raf == null ){

				// may have previously been implicitly closed, tidy up if required

			if ( explicit ){

				releaseFile();

				deleteDirs();
			}
		}else{

			try{
				raf.close();

			}catch( Throwable e ){

				throw( new FMFileManagerException("close fails", e ));

			}finally{

				raf	= null;

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
		file_access.setPieceComplete( raf, piece_number, piece_data );
	}

	@Override
	public void
	delete()

		throws FMFileManagerException
	{
		close();

		if ( linked_file.exists()){

			if ( !linked_file.delete()){

				throw( new FMFileManagerException( "Failed to delete '" + linked_file + "'" ));
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

			file_access.read( raf, buffers, position );

		}catch( FMFileManagerException e ){

			if (OUTPUT_REOPEN_RELATED_ERRORS) {Debug.printStackTrace(e);}

			try{
				reopen( e );

				file_access.read( raf, buffers, position );

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

			file_access.write( raf, buffers, position );

			last_modified = SystemTime.getCurrentTime();
			
		}catch( FMFileManagerException e ){

			if (OUTPUT_REOPEN_RELATED_ERRORS) {Debug.printStackTrace(e);}

			try{
				reopen( e );

				file_access.write( raf, buffers, position );

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
		return( raf != null );
	}
	
	@Override
	public long
	getLastModified()
	{
		return( last_modified );
	}
	

		// file reservation is used to manage the possibility of multiple torrents
		// refering to the same file. Initially introduced to stop a common problem
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

			List	owners = (List)file_map.get(canonical_path);

			if ( owners == null ){

				owners = new ArrayList();

				//System.out.println( "    creating new owners entr" );

				file_map.put( canonical_path, owners );
			}

			for (Iterator it=owners.iterator();it.hasNext();){

				Object[]	entry = (Object[])it.next();

				String	entry_name = ((FMFileOwner)entry[0]).getName();

				//System.out.println( "    existing entry: " + entry_name );

				if ( owner.getName().equals( entry_name )){

						// already present, start off read-access

					Debug.out( "reserve file - entry already present" );

					entry[1] = Boolean.FALSE;

					return;
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

			List	owners = (List)file_map.get( canonical_path );

			Object[]	my_entry = null;

			if ( owners == null ){

				Debug.out( "reserveAccess fail" );

				throw( new FMFileManagerException( "File '"+canonical_path+"' has not been reserved (no entries), '" + owner.getName()+"'"));
			}

			for (Iterator it=owners.iterator();it.hasNext();){

				Object[]	entry = (Object[])it.next();

				String	entry_name = ((FMFileOwner)entry[0]).getName();

				//System.out.println( "    existing entry: " + entry_name );

				if ( owner.getName().equals( entry_name )){

					my_entry	= entry;
				}
			}

			if ( my_entry == null ){

				Debug.out( "reserveAccess fail" );

				throw( new FMFileManagerException( "File '"+canonical_path+"' has not been reserved (not found), '" + owner.getName()+"'"));
			}

			my_entry[1] = Boolean.valueOf(access_mode == FM_WRITE);
			my_entry[2] = reason;

			int	read_access 		= 0;
			int write_access		= 0;
			int	write_access_lax	= 0;

			TOTorrentFile	my_torrent_file = owner.getTorrentFile();

			StringBuilder	users_sb = owners.size()==1?null:new StringBuilder( 128 );

			for (Iterator it=owners.iterator();it.hasNext();){

				Object[]	entry = (Object[])it.next();

				FMFileOwner	this_owner = (FMFileOwner)entry[0];

				if (((Boolean)entry[1]).booleanValue()){

					write_access++;

					TOTorrentFile this_tf = this_owner.getTorrentFile();

					if ( my_torrent_file != null && this_tf != null && my_torrent_file.getLength() == this_tf.getLength()){

						write_access_lax++;
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

					// relax locking if strict is disabled and torrent file is same size


				if ( !COConfigurationManager.getBooleanParameter( "File.strict.locking" )){

					if ( write_access_lax == write_access ){

						return;
					}
				}

				Debug.out( "reserveAccess fail" );

				throw( new FMFileManagerException( "File '"+canonical_path+"' is in use by '" + (users_sb==null?"eh?":users_sb.toString()) +"'"));
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

			List	owners = (List)file_map.get( canonical_path );

			if ( owners != null ){

				for (Iterator it=owners.iterator();it.hasNext();){

					Object[]	entry = (Object[])it.next();

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

		if ( !parent.exists()){

			List	new_dirs = new ArrayList();

			File	current = parent;

			while( current != null && !current.exists()){

				new_dirs.add( current );

				current = current.getParentFile();
			}

			created_dirs_leaf	= target;
			created_dirs		= new ArrayList();

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
					Thread.sleep( RandomUtils.nextInt( 1000 ));

				}catch( Throwable e ){
				}

				FileUtil.mkdirs( parent );

				if ( parent.isDirectory()){

					created_dirs		= new_dirs;

				}else{

					throw( new FMFileManagerException( "Failed to create parent directory '" + parent + "'"));
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

			if ( !created_dirs_leaf.exists()){

				Iterator	it = created_dirs.iterator();

				while( it.hasNext()){

					File	dir = (File)it.next();

					if ( dir.exists() && dir.isDirectory()){

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
		File cPath = new File(canonical_path);
		String sPaths;
		if (cPath.equals(linked_file))
			sPaths = "can/link=" + Debug.secretFileName(canonical_path);
		else
			sPaths = "can=" + Debug.secretFileName(canonical_path) + ",link="
					+ Debug.secretFileName(linked_file.toString());
		return sPaths + ",raf=" + raf + ",acc=" + access_mode + ",ctrl = "
				+ file_access.getString();
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

				Iterator	it = file_map.keySet().iterator();

				while( it.hasNext()){

					String	key = (String)it.next();

					List	owners = (List)file_map.get(key);

					Iterator	it2 = owners.iterator();

					String	str = "";

					while( it2.hasNext()){

						Object[]	entry = (Object[])it2.next();

						FMFileOwner	owner 	= (FMFileOwner)entry[0];
						Boolean		write	= (Boolean)entry[1];
						String		reason	= (String)entry[2];


						str += (str.length()==0?"":", ") + owner.getName() + "[" + (write.booleanValue()?"write":"read")+ "/" + reason + "]";
					}


					writer.println( Debug.secretFileName(key) + " -> " + str );
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
