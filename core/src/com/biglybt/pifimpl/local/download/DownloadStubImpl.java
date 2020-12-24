/*
 * Created on Jul 9, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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
 */


package com.biglybt.pifimpl.local.download;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.StringInterner;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.download.DownloadRemovalVetoException;
import com.biglybt.pif.download.DownloadStats;
import com.biglybt.pif.download.DownloadStub.DownloadStubEx;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.util.MapUtils;

public class
DownloadStubImpl
	implements DownloadStubEx
{
	private final DownloadManagerImpl		manager;
	private final String					name;
	private final byte[]					hash;
	private final long						size;
	private final long						date_created;
	private final long						date_completed;
	private String							save_path;
	private final String					main_tracker;
	private final DownloadStubFileImpl[]	files;
	private final String[]					manual_tags;
	private final String					category;
	private final int						share_ratio;

	private final Map<String,Object>		gm_map;

	private DownloadImpl			temp_download;
	private Map<String,Object>		attributes;

	protected
	DownloadStubImpl(
		DownloadManagerImpl		_manager,
		DownloadImpl			_download,
		DownloadManager			_core_dm,
		String[]				_manual_tags,
		String					_category,
		Map<String,Object>		_gm_map )
	{
		manager			= _manager;
		temp_download	= _download;

		date_created = SystemTime.getCurrentTime();

		name	= temp_download.getName();

		Torrent	torrent = temp_download.getTorrent();

		hash		= torrent.getHash();
		size		= torrent.getSize();
		save_path	= temp_download.getSavePath();

		DownloadStubFile[] _files = temp_download.getStubFiles();

		gm_map		= _gm_map;

		files		= new DownloadStubFileImpl[_files.length];

		for ( int i=0;i<files.length;i++){

			files[i] = new DownloadStubFileImpl( this, _files[i] );
		}

		manual_tags = _manual_tags;
		category	= _category;
		
		DownloadStats stats = temp_download.getStats();

		share_ratio = stats.getShareRatio();
		
		URL tracker = torrent.getAnnounceURL();
		
		if ( tracker != null && !TorrentUtils.isDecentralised( tracker )){
			
			main_tracker = tracker.toExternalForm();
			
		}else{
			
			main_tracker = null;
		}
		
		DownloadManagerState dms = _core_dm.getDownloadState();
		
		long comp = dms.getLongAttribute( DownloadManagerState.AT_COMPLETE_LAST_TIME );

		if ( comp == 0 ){

			comp = dms.getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME );	// nothing recorded either way
		}
		
		date_completed = comp;
	}

	protected
	DownloadStubImpl(
		DownloadManagerImpl		_manager,
		Map<String,Object>		_map )
	{
		manager		= _manager;

		date_created	= MapUtils.getMapLong( _map, "dt", 0 );
		hash 			= (byte[])_map.get( "hash" );
		name			= MapUtils.getMapString( _map, "name", null );
		size 			= MapUtils.getMapLong( _map, "s", 0 );
		save_path		= MapUtils.getMapString( _map, "l", null );
		gm_map 			= (Map<String,Object>)_map.get( "gm" );
		
		List<Map<String,Object>>	file_list = (List<Map<String,Object>>)_map.get( "files" );

		if ( file_list == null ){

			files = new DownloadStubFileImpl[0];

		}else{

			files = new DownloadStubFileImpl[file_list.size()];

			for ( int i=0;i<files.length;i++){

				files[i] = new DownloadStubFileImpl( this, (Map)file_list.get(i));
			}
		}

		List<Object>	tag_list = (List<Object>)_map.get( "t" );

		if ( tag_list != null ){

			manual_tags = new String[tag_list.size()];

			for (int i=0;i<manual_tags.length;i++){

				manual_tags[i] = MapUtils.getString( tag_list.get(i));
			}

		}else{

			manual_tags = null;
		}

		category = MapUtils.getMapString( _map, "cat", null );
		
		attributes = (Map<String,Object>)_map.get( "attr" );

		share_ratio	= MapUtils.getMapInt( _map, "sr", -1 );
		
		String tracker 	= MapUtils.getMapString( _map, "tr", null );
		
		if ( tracker == null ){
			
			main_tracker = null;
			
		}else{
			
			main_tracker	= StringInterner.intern( tracker );
		}
		
		date_completed	= MapUtils.getMapLong( _map, "dc", 0 );
	}

	public Map<String,Object>
	exportToMap()
	{
		Map<String,Object>	map = new HashMap<>();

		map.put( "dt", date_created );
		map.put( "hash", hash );
		map.put( "s", size );

		MapUtils.setMapString(map, "name", name );
		MapUtils.setMapString(map, "l", save_path );

		map.put( "gm", gm_map );

		List<Map<String,Object>>	file_list = new ArrayList<>();

		map.put( "files", file_list );

		for ( DownloadStubFileImpl file: files ){

			file_list.add( file.exportToMap());
		}

		if ( manual_tags != null ){

			List<String>	tag_list = new ArrayList<>(manual_tags.length);

			for ( String s: manual_tags ){
				if ( s != null ){
					tag_list.add( s );
				}
			}

			if ( tag_list.size() > 0 ){
				map.put( "t", tag_list );
			}
		}
		
		if ( category != null ){
			
			MapUtils.setMapString(map, "cat", category );
		}

		if ( attributes != null ){

			map.put( "attr", attributes );
		}

		if ( share_ratio >= 0 ){

			map.put( "sr", new Long( share_ratio ));
		}

		if ( main_tracker != null ){
			
			MapUtils.setMapString(map, "tr", main_tracker );
		}
		
		if ( date_completed > 0 ){
			
			map.put( "dc", date_completed );
		}
		
		return( map );
	}

	@Override
	public boolean
	isStub()
	{
		return( true );
	}

	protected void
	setStubbified()
	{
		temp_download = null;
	}

	@Override
	public Download
	destubbify()

		throws DownloadException
	{
		if ( temp_download != null ){

			return( temp_download );
		}

		return( manager.destubbify( this ));
	}

	@Override
	public Torrent
	getTorrent()
	{
		if ( temp_download != null ){

			return( temp_download.getTorrent());
		}

		return( PluginCoreUtils.wrap( manager.getTorrent( this )));
	}

	@Override
	public String
	getName()
	{
		return( name );
	}

	@Override
	public byte[]
	getTorrentHash()
	{
		return( hash );
	}

	@Override
	public long
	getTorrentSize()
	{
		return( size );
	}

	@Override
	public long
	getCreationDate()
	{
		return( date_created );
	}
	
	@Override
	public long 
	getCompletionDate()
	{
		return( date_completed );
	}
	
	@Override
	public String
	getSavePath()
	{
		return( save_path );
	}

		/**
		 * Can ONLY be used before the stub is initially saved
		 * @param sp
		 */
	
	protected void
	setSavePath(
		String	sp )
	{
		save_path = sp;
	}
	
	@Override
	public String 
	getMainTracker()
	{
		return( main_tracker );
	}
	
	@Override
	public DownloadStubFile[]
	getStubFiles()
	{
		return( files );
	}

	@Override
	public String[]
	getManualTags()
	{
		return( manual_tags );
	}

	@Override
	public String 
	getCategory()
	{
		return( category );
	}
	
	@Override
	public int
	getShareRatio()
	{
		return( share_ratio );
	}

	@Override
	public long
	getLongAttribute(
		TorrentAttribute 	attribute )
	{
		if ( attributes == null ){

			return( 0 );
		}

		Long l = (Long)attributes.get( attribute.getName());

		if ( l == null ){

			return( 0 );
		}

		return( l );
	}


	@Override
	public void
	setLongAttribute(
		TorrentAttribute 	attribute,
		long 				value)
	{
		if ( attributes == null ){

			attributes = new HashMap();
		}

		attributes.put( attribute.getName(), value );

		if ( temp_download == null ){

			manager.updated( this );
		}
	}

	public Map
	getGMMap()
	{
		return( gm_map );
	}

	@Override
	public void
	remove()
	{
		manager.remove( this );
	}

	@Override
	public void
	remove(
		boolean delete_torrent,
		boolean delete_data )

		throws DownloadException, DownloadRemovalVetoException
	{
		if ( delete_data ){

			TOTorrent torrent = manager.getTorrent( this );

			if ( torrent != null ){

				File save_location = FileUtil.newFile(getSavePath());

				if ( torrent.isSimpleTorrent()){

					if ( save_location.isFile()){

						FileUtil.deleteWithRecycle( save_location, false );
					}
				}else{

						// stub files are fully resolved (i.e. links followed). Anything outside the
						// save location root doesn't get touched and we want to avoid individual file deletes
						// if we can delete a parent directory instead

					if ( save_location.isDirectory()){

						DownloadStubFile[] files = getStubFiles();

						String	save_path = save_location.getAbsolutePath();

						if ( !save_path.endsWith( File.separator )){

							save_path += File.separator;
						}

						int	found = 0;

						for ( DownloadStubFile file: files ){

							File f = file.getFile();

							String path = f.getAbsolutePath();

							if ( path.startsWith( save_path )){

								if ( f.exists()){

									found++;
								}
							}
						}

						int actual = countFiles( save_location );

						if ( actual == found ){

							FileUtil.deleteWithRecycle( save_location, false );

						}else{

							for ( DownloadStubFile file: files ){

								File f = file.getFile();

								String path = f.getAbsolutePath();

								if ( path.startsWith( save_path )){

									FileUtil.deleteWithRecycle( f, false );
								}
							}

							TorrentUtils.recursiveEmptyDirDelete( save_location, false );
						}
					}
				}
			}
		}

		if ( delete_torrent ){

			byte[]	bytes = (byte[])gm_map.get( "torrent" );

			if ( bytes != null ){

				try{
					String torrent_file = new String( bytes, "UTF-8" );

					File file = FileUtil.newFile( torrent_file );

					TorrentUtils.delete( file, false );

				}catch( Throwable e ){

					Debug.out( e );
				}
			}
		}

		manager.remove( this );
	}

	private int
	countFiles(
		File		dir )
	{
		int	result = 0;

		File[] files = dir.listFiles();

		if ( files != null ){

			for ( File f: files ){

				if ( f.isFile()){

					result++;

				}else{

					result += countFiles( f );
				}
			}
		}

		return( result );
	}

	protected static class
	DownloadStubFileImpl
		implements DownloadStubFile
	{
		private final DownloadStubImpl		stub;
		private final Object				file;
		private final long					length;

		protected
		DownloadStubFileImpl(
			DownloadStubImpl	_stub,
			DownloadStubFile	stub_file )
		{
			stub	= _stub;
			length	= stub_file.getLength();

			File f	= stub_file.getFile();

			String path = f.getAbsolutePath();

			String save_loc = stub.getSavePath();

			int	save_loc_len = save_loc.length();

			if ( 	path.startsWith( save_loc ) &&
					path.length() > save_loc_len &&
					path.charAt( save_loc_len ) == File.separatorChar ){

				file = path.substring( save_loc_len + 1 );

			}else{

				file = f;
			}
		}

		protected
		DownloadStubFileImpl(
			DownloadStubImpl	_stub,
			Map					map )
		{
			stub	= _stub;

			String abs_file = MapUtils.getMapString(map, "file", null );

			if ( abs_file != null ){

				file 	= FileUtil.newFile( abs_file );

			}else{

				file =  MapUtils.getMapString(map, "rel", null );
			}

			length 	= (Long)map.get( "len" );
		}

		protected Map
		exportToMap()
		{
			Map	map = new HashMap();

			if ( file instanceof File ){

				map.put( "file", ((File)file).getAbsolutePath());

			}else{

				map.put( "rel",(String)file );
			}

			map.put( "len", length );

			return( map );
		}

		@Override
		public File
		getFile()
		{
			if ( file instanceof File ){

				return((File)file );

			}else{

				return( FileUtil.newFile( stub.getSavePath(), (String)file ));
			}
		}

		@Override
		public long
		getLength()
		{
			return( length );
		}
	}


	/*
	private static Object	file_name_manager_lock = new Object();

	private static long		nodes 	= 0;
	private static long		chars 	= 0;
	private static long		raw 	= 0;

	private static class
	FileNameManager
	{
		private FileNode	root = new FileNode( null, null );

		public FileNode
		addFile(
			File		f )
		{
			raw += f.getAbsolutePath().length();

			synchronized( file_name_manager_lock ){

				f = f.getAbsoluteFile();

				List<String>	comps = new ArrayList<>( 32 );

				while(true ){

					File parent = f.getParentFile();

					if ( parent == null ){

						comps.add( f.getAbsolutePath());

						break;

					}else{

						comps.add( f.getName());

						f = parent;
					}
				}

				int	comp_num = comps.size();

				if ( comp_num == 1 ){

					return( new FileNode( comps.get(0), null ));

				}else{

					FileNode node = root;

					for ( int i=comp_num-1;i>0;i--){

						String bit = comps.get(i);

						node = node.getChild( bit );
					}

					return( new FileNode( comps.get(0), node ));
				}
			}
		}
	}

	private static final class
	FileNode
	{
		private final String			name;
		private final FileNode			parent;

		private LightHashMap<String,FileNode>	kids;

		private
		FileNode(
			String		_name,
			FileNode	_parent )
		{
			name		= _name;
			parent		= _parent;

			nodes++;
			if ( name != null ){
				chars += name.length();
			}
		}

		public String
		getName()
		{
			return( name );
		}

		public FileNode
		getParent()
		{
			return( parent );
		}

		private FileNode
		getChild(
			String		name )
		{
			FileNode node;

			if ( kids == null ){

				kids = new LightHashMap<String, DownloadStubImpl.FileNode>();

				node = null;

			}else{

				node = kids.get( name );
			}

			if ( node == null ){

				node = new FileNode( name, this );

				kids.put( name, node );
			}

			return( node );
		}

		public File
		getFile()
		{
			if ( parent == null ){

				return( FileUtil.newFile( name ));

			}else{

				List<FileNode> nodes = new ArrayList<>( 32 );

				FileNode current = this;

				synchronized( file_name_manager_lock ){

					while( current.getName() != null ){

						nodes.add(current);

						current = current.getParent();
					}
				}

				StringBuffer path = new StringBuffer( 1024 );

				int	num_nodes = nodes.size();

				for ( int i=num_nodes-1;i>=0;i--){

					if ( path.length() > 0 ){

						path.append( File.separator );
					}

					path.append( nodes.get(i).getName());
				}

				return( FileUtil.newFile( path.toString()));
			}
		}
	}

	private static void
	addFiles(
		FileNameManager		manager,
		File				file )
	{
		if ( file.isFile()){

			FileNode node = manager.addFile( file );

			System.out.println( node.getFile() + ": nodes=" + nodes + ",chars=" + chars + ",raw=" + raw );

		}else{

			File[] files = file.listFiles();

			for ( File f: files ){

				addFiles( manager, f );
			}
		}
	}

	public static void
	main(
		String[]		args )
	{
		try{
			FileNameManager manager = new FileNameManager();

			File root = new File( "C:\\temp" );

			addFiles( manager, root );

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}
	*/
}
