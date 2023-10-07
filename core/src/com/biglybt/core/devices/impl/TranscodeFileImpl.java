/*
 * Created on Feb 18, 2009
 * Created by Paul Gardner
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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


/**
 *
 */
package com.biglybt.core.devices.impl;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import com.biglybt.core.devices.*;
import com.biglybt.core.download.DiskManagerFileInfoDelegate;
import com.biglybt.core.download.DiskManagerFileInfoFile;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.RegExUtil;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.util.MapUtils;

class
TranscodeFileImpl
	implements TranscodeFile
{
	protected static final String		KEY_FILE			= "file";

	private static final TagManager	tag_manager = TagManagerFactory.getTagManager();

	private static final String			KEY_PROFILE_NAME		= "pn";
	private static final String			KEY_SOURCE_FILE_HASH	= "sf_hash";
	private static final String			KEY_SOURCE_FILE_INDEX	= "sf_index";
	private static final String			KEY_SOURCE_FILE_LINK	= "sf_link";
	private static final String			KEY_NO_XCODE			= "no_xcode";
	private static final String			KEY_FOR_JOB				= "fj";

	private static final String			KEY_DURATION			= "at_dur";
	private static final String			KEY_VIDEO_WIDTH			= "at_vw";
	private static final String			KEY_VIDEO_HEIGHT		= "at_vh";
	private static final String			KEY_XCODE_SIZE			= "at_xs";
	private static final String			KEY_DATE				= "at_dt";
	private static final String			KEY_CATEGORIES			= PT_CATEGORY;
	private static final String			KEY_TAGS				= PT_TAGS;
	private static final String			KEY_COPY_TO_OVERRIDE	= "ct_over";
	private static final String			KEY_COPYING	= "copying";

	private DeviceImpl					device;
	private String						key;
	private Map<String,Map<String,?>>	files_map;

		// don't store any local state here, store it in the map as this is just a wrapper
		// for the underlying map and there can be multiple such wrappers concurrent

	protected
	TranscodeFileImpl(
		DeviceImpl					_device,
		String						_key,
		String						_profile_name,
		Map<String,Map<String,?>>	_files_map,
		File						_file,
		boolean						_for_job )
	{
		device		= _device;
		key			= _key;
		files_map	= _files_map;

		getMap( true );

		setString( KEY_FILE, _file.getAbsolutePath());

		setString( KEY_PROFILE_NAME, _profile_name );

		setLong( KEY_DATE, SystemTime.getCurrentTime());

		setBoolean( KEY_FOR_JOB, _for_job );

		setBoolean( KEY_COPYING, false );
	}

	protected
	TranscodeFileImpl(
		DeviceImpl					_device,
		String						_key,
		Map<String,Map<String,?>>	_map )

		throws IOException
	{
		device			= _device;
		key				= _key;
		files_map		= _map;

		Map<String,?> map = getMap();

		if ( map == null || !map.containsKey( KEY_FILE )){

			throw( new IOException( "File has been deleted" ));
		}
	}


	protected String
	getKey()
	{
		return( key );
	}

	@Override
	public String
	getName()
	{
		TranscodeJob job = getJob();

		String text;

		if ( job == null){

			try{
				DiskManagerFileInfo sourceFile = getSourceFile();

				try {
					Download download = sourceFile.getDownload();

					if ( download == null ){

						text = sourceFile.getFile().getName();

					}else{

						text = download.getName();

						DiskManagerFileInfo[] fileInfo = download.getDiskManagerFileInfo();

						if (fileInfo.length > 1) {

							text += ": " + sourceFile.getFile( true ).getName();
						}
					}
				}catch (DownloadException e ){

					text = sourceFile.getFile().getName();
				}

			}catch( Throwable e ){

				text = "";
			}
		}else{

			text = job.getName();
		}

		return( text );
	}

	@Override
	public Device
	getDevice()
	{
		return( device );
	}

	@Override
	public TranscodeJobImpl
	getJob()
	{
		if ( isComplete()){

			return( null );
		}

		return( device.getManager().getTranscodeManager().getQueue().getJob( this ));
	}

	public File
	getCacheFile()

		throws TranscodeException
	{
		String	file_str = getString( KEY_FILE );

		if ( file_str == null ){

			throw( new TranscodeException( "File has been deleted" ));
		}

		return(new File( file_str ));
	}

	protected void
	setCacheFile(
		File		file )
	{
		setString( KEY_FILE, file.getAbsolutePath());
	}

	protected void
	checkDeleted()

		throws TranscodeException
	{
		if ( isDeleted()){

			throw( new TranscodeException( "File has been deleted" ));
		}
	}

	@Override
	public DiskManagerFileInfo
	getSourceFile()

		throws TranscodeException
	{
		checkDeleted();

			// options are either a download file or a link to an existing non-torrent based file

		String	hash = getString( KEY_SOURCE_FILE_HASH );

		if ( hash != null ){

			try{
				Download download = PluginInitializer.getDefaultInterface().getDownloadManager().getDownload( Base32.decode(hash));

				if ( download != null ){

					int index = (int)getLong( KEY_SOURCE_FILE_INDEX );

					return( download.getDiskManagerFileInfo(index) );
				}

			}catch( Throwable e ){

			}
		}

		String	link = getString( KEY_SOURCE_FILE_LINK );

		if ( link != null ){

			File link_file = new File( link );

				// if we're not transcoding then always return the source even if doesn't exist

			if ( link_file.exists() || getBoolean( KEY_NO_XCODE )){

				return( new DiskManagerFileInfoFile( link_file ));
			}
		}

		// Debug.out( "Source file doesn't exist (hash=" + hash + ",link=" + link +"), returning cache file" );

		return( new DiskManagerFileInfoFile( getCacheFile()));
	}

	protected void
	setSourceFile(
		DiskManagerFileInfo		file )
	{
		try{
			Download download = file.getDownload();

			if ( download != null && download.getTorrent() != null ){

				setString( KEY_SOURCE_FILE_HASH, Base32.encode( download.getTorrent().getHash() ));

				setLong( KEY_SOURCE_FILE_INDEX, file.getIndex());
			}
		}catch( Throwable e ){
		}

		setString( KEY_SOURCE_FILE_LINK, file.getFile().getAbsolutePath());
	}

	@Override
	public DiskManagerFileInfo
	getTargetFile()

		throws TranscodeException
	{
			// options are either the cached file, if it exists, or failing that the
			// source file if transcoding not required

		File	cache_file = getCacheFile();

		if ( cache_file.exists() && cache_file.length() > 0 ){

			return( new DiskManagerFileInfoFile( cache_file ));
		}

		if ( getBoolean( KEY_NO_XCODE )){

			DiskManagerFileInfo res = getSourceFile();

			if ( res instanceof DiskManagerFileInfoFile ){

				return( res );

			}else{

				try{
					return( new DiskManagerFileInfoDelegate( res ));

				}catch( Throwable e ){

					Debug.out( e );

					return( res );
				}
			}
		}

			// Debug.out( "Target file for " + cache_file + " doesn't exist" );

		return( new DiskManagerFileInfoFile( cache_file ));
	}

	protected void
	setTranscodeRequired(
		boolean	required )

		throws TranscodeException
	{
		setBoolean( KEY_NO_XCODE, !required );

		if ( !required ){

				// reset the file name as previous

			device.revertFileName( this );
		}
	}

	@Override
	public boolean
	getTranscodeRequired()
	{
		return( !getBoolean( KEY_NO_XCODE ));
	}

	protected void
	setComplete(
		boolean b )
	{
		setBoolean( PT_COMPLETE, b );
	}

	@Override
	public boolean
	isComplete()
	{
		return( getBoolean( PT_COMPLETE ));
	}

	@Override
	public boolean
	isTemplate()
	{
		return( !getBoolean( KEY_FOR_JOB ));
	}

	protected void
	setCopiedToDevice(
		boolean b )
	{

		setBoolean( PT_COPIED, b );

		setLong( PT_COPY_FAILED, 0 );

		setCopyingToDevice(false);
	}

	protected void
	setCopyToDeviceFailed()
	{
		setLong( PT_COPY_FAILED, getLong( PT_COPY_FAILED ) + 1 );

		setCopyingToDevice(false);
	}

	@Override
	public long
	getCopyToDeviceFails()
	{
		return( getLong( PT_COPY_FAILED ));
	}

	@Override
	public boolean
	isCopiedToDevice()
	{
		return( getBoolean( PT_COPIED ));
	}

	@Override
	public void
	retryCopyToDevice()
	{
		if ( isCopiedToDevice()){

			setCopiedToDevice( false );

		}else{

			setLong( PT_COPY_FAILED, 0 );
		}
	}

	protected void
	setProfileName(
		String s )
	{
		setString( KEY_PROFILE_NAME, s );
	}

	@Override
	public String
	getProfileName()
	{
		String s = getString( KEY_PROFILE_NAME );

		if ( s == null ){

			s = "Unknown";
		}

		return( s );
	}

	protected void
	setCopyToFolderOverride(
		String s )
	{
		setString( KEY_COPY_TO_OVERRIDE, s );
	}

	public String
	getCopyToFolderOverride()
	{
		return( getString( KEY_COPY_TO_OVERRIDE ));
	}

	protected void
	update(
		TranscodeProviderAnalysis		analysis )

		throws TranscodeException
	{
		checkDeleted();

		long	duration		= analysis.getLongProperty( TranscodeProviderAnalysis.PT_DURATION_MILLIS );
		long	video_width		= analysis.getLongProperty( TranscodeProviderAnalysis.PT_VIDEO_WIDTH );
		long	video_height	= analysis.getLongProperty( TranscodeProviderAnalysis.PT_VIDEO_HEIGHT );
		long	xcode_size		= analysis.getLongProperty( TranscodeProviderAnalysis.PT_ESTIMATED_XCODE_SIZE );

		if ( duration > 0 ){

			setLong( KEY_DURATION, duration );
		}

		if ( video_width > 0 && video_height > 0 ){

			setLong( KEY_VIDEO_WIDTH, video_width );

			setLong( KEY_VIDEO_HEIGHT, video_height );
		}

		if ( xcode_size > 0 ){

			setLong( KEY_XCODE_SIZE, xcode_size );
		}
	}

	protected void
	setResolution(
		int		video_width,
		int		video_height )
	{
		if ( video_width > 0 && video_height > 0 ){

			setLong( KEY_VIDEO_WIDTH, video_width );

			setLong( KEY_VIDEO_HEIGHT, video_height );
		}
	}

	@Override
	public long
	getDurationMillis()
	{
		return( getLong( KEY_DURATION ));
	}

	@Override
	public long
	getVideoWidth()
	{
		return( getLong( KEY_VIDEO_WIDTH ));
	}

	@Override
	public long
	getVideoHeight()
	{
		return( getLong( KEY_VIDEO_HEIGHT ));
	}

	@Override
	public long
	getEstimatedTranscodeSize()
	{
		return( getLong( KEY_XCODE_SIZE ));
	}

	@Override
	public String[]
	getCategories()
	{
		String cats = getString( KEY_CATEGORIES );

		if ( cats == null || cats.length() == 0 ){

			return( new String[0] );
		}

		return( RegExUtil.PAT_SPLIT_COMMA.split(cats));
	}

	@Override
	public void
	setCategories(
		String[]		cats )
	{
		String[] existing = getCategories();

		if ( existing.length == 0 && existing.length == cats.length ){

			return;
		}

		String	str = "";

		for ( String cat: cats ){

			cat = cat.replaceAll( ",", "" ).trim();

			if ( cat.length() > 0 ){

				str += (str.length()==0?"":",") + cat;
			}
		}

		setString( KEY_CATEGORIES, str );
	}

	/**
	 * @return if localize, returns localized name<br>
	 *         if !localize, returns tag UIDs (long) as a String
	 */
	@Override
	public String[]
	getTags(
		boolean	localize )
	{
		String tags_str = getString( KEY_TAGS );

		if ( tags_str == null || tags_str.length() == 0 ){

			return( new String[0] );
		}

		String[] tags = RegExUtil.PAT_SPLIT_COMMA.split(tags_str);

		if ( localize ){

			List<String> derp = null;

			int	pos = 0;

			for ( String s: tags ){

				try{
					Tag tag = tag_manager.lookupTagByUID( Long.parseLong( s ));

					if ( tag == null ){

						throw( new Exception()); // handled below
					}

					String tag_name = tag.getTagName( true );

					if ( derp == null ){

						tags[pos++] = tag_name;

					}else{

						derp.add( tag_name );
					}
				}catch( Throwable e ){

					if ( derp == null ){

						derp = new ArrayList<>();

						derp.addAll(Arrays.asList(tags).subList(0, pos));
					}
				}
			}

			if ( derp == null ){

				return( tags );
			}

			return( derp.toArray( new String[ derp.size()]));

		}else{

			return( tags );
		}
	}

	@Override
	public void
	setTags(
		String[]		tags )
	{
		String[] existing = getTags( false );

		if ( existing.length == 0 && existing.length == tags.length ){

			return;
		}

		String	str = "";

		for ( String tag: tags ){

			tag = tag.replaceAll( ",", "" ).trim();

			if ( tag.length() > 0 ){

				str += (str.length()==0?"":",") + tag;
			}
		}

		setString( KEY_TAGS, str );
	}

	@Override
	public long
	getCreationDateMillis()
	{
		return( getLong( KEY_DATE ));
	}

	@Override
	public File
	getCacheFileIfExists()
	{
		try{
			return( getCacheFile());

		}catch( Throwable e ){

			return( null );
		}
	}

	@Override
	public URL
	getStreamURL()
	{
		return( device.getStreamURL( this, null ));
	}

	@Override
	public URL
	getStreamURL(
		String	host )
	{
		return( device.getStreamURL( this, host ));
	}

	public String
	getMimeType()
	{
		return( device.getMimeType( this ));
	}

	@Override
	public void
	delete(
		boolean	delete_contents )

		throws TranscodeException
	{
		device.deleteFile( this, delete_contents, true );
	}

	protected void
	deleteCacheFile()

		throws TranscodeException
	{
		device.deleteFile( this, true, false );
	}

	@Override
	public boolean
	isDeleted()
	{
		return( getMap() == null );
	}

	private Map<String,?>
	getMap()
	{
		return( getMap( false ));
	}

	private Map<String,?>
	getMap(
		boolean	create )
	{
		synchronized( files_map ){

			Map<String,?> map = files_map.get( key );

			if ( map == null && create ){

				map = new HashMap<String, Object>();

				files_map.put( key, map );
			}

			return( map );
		}
	}

	protected boolean
	getBoolean(
		String		key )
	{
		return( getLong(key)==1);
	}

	protected void
	setBoolean(
		String		key,
		boolean		b )
	{
		setLong(key,b?1:0);
	}

	protected long
	getLong(
		String		key )
	{
		try{
			Map<String,?>	map = getMap();

			return(MapUtils.importLong( map, key, 0 ));

		}catch( Throwable e ){

			Debug.out( e );

			return( 0 );
		}
	}

	protected void
	setLong(
		String		key,
		long		value )
	{
		if ( getLong( key ) == value ){

			return;
		}

		synchronized( files_map ){

			try{
				Map<String,?>	map = getMap();

				MapUtils.exportLong( map, key, value);

			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		device.fileDirty( this, TranscodeTargetListener.CT_PROPERTY, key );
	}

	protected String
	getString(
		String		key )
	{
		try{
			Map<String,?>	map = getMap();

			return(MapUtils.getMapString( map, key, null ));

		}catch( Throwable e ){

			Debug.out( e );

			return( "" );
		}
	}

	protected void
	setString(
		String		key,
		String		value )
	{
		String existing = getString( key );

		if ( existing == null && value == null ){

			return;

		}else if ( existing == null || value == null ){

		}else if ( existing.equals( value )){

			return;
		}

		synchronized( files_map ){

			Map<String,?>	map = getMap();

			try{
				MapUtils.setMapString( map, key, value );

			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		device.fileDirty( this, TranscodeTargetListener.CT_PROPERTY, key );
	}

	@Override
	public void
	setTransientProperty(
		Object		key2,
		Object		value )
	{
		device.setTransientProperty( key, key2, value );
	}

	@Override
	public Object
	getTransientProperty(
		Object		key2 )
	{
		return( device.getTransientProperty( key, key2 ));
	}

	public boolean
	equals(
		Object	other )
	{
		if ( other instanceof TranscodeFileImpl ){

			return( key.equals(((TranscodeFileImpl)other).key));
		}

		return( false );
	}

	public int
	hashCode()
	{
		return( key.hashCode());
	}

	protected String
	getString()
	{
		Map<String,?> map = getMap();

		if ( map == null ){

			return( key + ": deleted" );

		}else{

			return( key + ": " + map );
		}
	}

	public void
	setCopyingToDevice(
			boolean b)
	{
		setBoolean(KEY_COPYING, b);
	}

	@Override
	public boolean
	isCopyingToDevice()
	{
		return getBoolean(KEY_COPYING);
	}
}
