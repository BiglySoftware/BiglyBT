/*
 * File    : FMFileManagerImpl.java
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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.diskmanager.file.impl;

/**
 * @author parg
 *
 */

import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.diskmanager.file.FMFile;
import com.biglybt.core.diskmanager.file.FMFileManager;
import com.biglybt.core.diskmanager.file.FMFileManagerException;
import com.biglybt.core.diskmanager.file.FMFileOwner;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.*;

public class
FMFileManagerImpl
	implements FMFileManager
{
	public static final boolean DEBUG	= false;

	protected static FMFileManagerImpl	singleton;
	protected static final AEMonitor			class_mon	= new AEMonitor( "FMFileManager:class" );


	public static FMFileManager
	getSingleton()
	{
		try{
			class_mon.enter();

			if ( singleton == null ){

				singleton = new FMFileManagerImpl();
			}

			return( singleton );

		}finally{

			class_mon.exit();
		}
	}

	protected final LinkedHashMap<FMFileLimited,FMFileLimited>		map;
	protected final AEMonitor			map_mon	= new AEMonitor( "FMFileManager:Map");

	protected final boolean			limited;
	protected final int				limit_size;

	protected AESemaphore			close_queue_sem;
	protected List<FMFileLimited>	close_queue;
	protected final AEMonitor		close_queue_mon	= new AEMonitor( "FMFileManager:CQ");

	protected List<FMFile>				files;
	protected final AEMonitor			files_mon		= new AEMonitor( "FMFileManager:File");

	protected
	FMFileManagerImpl()
	{
		limit_size = COConfigurationManager.getIntParameter( "File Max Open" );

		limited		= limit_size > 0;

		if ( DEBUG ){

			System.out.println( "FMFileManager::init: limit = " + limit_size );

			files = new ArrayList<>();
		}

		map	= new LinkedHashMap<>( limit_size, (float)0.75, true );	// ACCESS order selected - this means oldest

		if ( limited ){

			close_queue_sem	= new AESemaphore("FMFileManager::closeqsem");

			close_queue		= new LinkedList<>();

			new AEThread2("FMFileManager::closeQueueDispatcher")
				{
					@Override
					public void
					run()
					{
						closeQueueDispatch();
					}
				}.start();
		}
	}

	@Override
	public FMFile
	createFile(
		FMFileOwner				owner,
		StringInterner.FileKey	file,
		int						type,
		boolean					force )

		throws FMFileManagerException
	{
		if ( owner.getTorrentFile().isPadFile()){
			
			return( new FMFilePadding( owner, file, false ));
		}
		
		FMFile	res;

		if ( AEDiagnostics.USE_DUMMY_FILE_DATA ){

			res = new FMFileTestImpl( owner, this, file, type, force );

		}else{

			if ( limited ){

				res = new FMFileLimited( owner, this, file, type, force );

			}else{

				res = new FMFileUnlimited( owner, this, file, type, force );
			}
		}

		if (DEBUG){

			try{
				files_mon.enter();

				files.add( res );

			}finally{

				files_mon.exit();
			}
		}

		return( res );
	}

	protected void
	getSlot(
		FMFileLimited	file )
	{
			// must close the oldest file outside sync block else we'll get possible deadlock

		FMFileLimited	oldest_file = null;

		try{
			map_mon.enter();

			if (DEBUG ){
				System.out.println( "FMFileManager::getSlot: " + file.getName() +", map_size = " + map.size());
			}

			if ( map.size() >= limit_size ){

				Iterator it = map.keySet().iterator();

				oldest_file = (FMFileLimited)it.next();

				it.remove();
			}

			map.put( file, file );

		}finally{

			map_mon.exit();
		}

		if ( oldest_file != null ){

			closeFile( oldest_file );

		}
	}

	protected void
	releaseSlot(
		FMFileLimited	file )
	{
		if ( DEBUG ){
			System.out.println( "FMFileManager::releaseSlot: " + file.getName());
		}

		try{
			map_mon.enter();

			map.remove( file );

		}finally{

			map_mon.exit();
		}
	}

	protected void
	usedSlot(
		FMFileLimited	file )
	{
		if ( DEBUG ){
			System.out.println( "FMFileManager::usedSlot: " + file.getName());
		}

		try{
			map_mon.enter();

				// only update if already present - might not be due to delay in
				// closing files

			if ( map.containsKey( file )){

				map.put( file, file );		// update MRU
			}
		}finally{

			map_mon.exit();
		}
	}

	protected void
	closeFile(
		FMFileLimited	file )
	{
		if ( DEBUG ){
			System.out.println( "FMFileManager::closeFile: " + file.getName());
		}

		try{
			close_queue_mon.enter();

			close_queue.add( file );

		}finally{

			close_queue_mon.exit();
		}

		close_queue_sem.release();
	}

	protected void
	closeQueueDispatch()
	{
		while(true){

			if ( DEBUG ){

				close_queue_sem.reserve(1000);

			}else{

				close_queue_sem.reserve();
			}

			FMFileLimited	file = null;

			try{
				close_queue_mon.enter();

				if ( close_queue.size() > 0 ){

					file = (FMFileLimited)close_queue.remove(0);

					if ( DEBUG ){

						System.out.println( "FMFileManager::closeQ: " + file.getName() + ", rem = " + close_queue.size());
					}
				}
			}finally{

				close_queue_mon.exit();
			}

			if ( file != null ){

				try{
					file.close(false);

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}

			if ( DEBUG ){

				try{
					files_mon.enter();

					int	open = 0;

					for (int i=0;i<files.size();i++){

						FMFileLimited	f = (FMFileLimited)files.get(i);

						if ( f.isOpen()){

							open++;
						}
					}

					System.out.println( "INFO: files = " + files.size() + ", open = " + open );

				}finally{

					files_mon.exit();
				}
			}
		}
	}

	protected void
	generate(
		IndentWriter	writer )
	{
		writer.println( "FMFileManager slots" );

		try{
			writer.indent();

			try{
				map_mon.enter();

				Iterator it = map.keySet().iterator();

				while( it.hasNext()){

					FMFileLimited	file = (FMFileLimited)it.next();

					writer.println( file.getString());
				}

			}finally{

				map_mon.exit();
			}
		}finally{

			writer.exdent();
		}
	}
	
	protected void
	generate(
		IndentWriter	writer,
		TOTorrent		torrent )
	{
		writer.println( "FMFileManager slots" );

		try{
			HashWrapper hw = torrent.getHashWrapper();
			
			try{
				writer.indent();
	
				try{
					map_mon.enter();
	
					Iterator it = map.keySet().iterator();
	
					while( it.hasNext()){
	
						FMFileLimited	file = (FMFileLimited)it.next();
	
						if ( file.getOwner().getTorrentFile().getTorrent().getHashWrapper() == hw ){
						
							writer.println( file.getString());
						}
					}
	
				}finally{
	
					map_mon.exit();
				}
			}finally{
	
				writer.exdent();
			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
	}
	
	protected static void
	generateEvidence(
		IndentWriter	writer )
	{
		getSingleton();

		singleton.generate( writer );
	}
	
	public void
	generateEvidence(
		IndentWriter	writer,
		TOTorrent		torrent )
	{
		getSingleton();

		singleton.generate( writer, torrent );
	}
}
