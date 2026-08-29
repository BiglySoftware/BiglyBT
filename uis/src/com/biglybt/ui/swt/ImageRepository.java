/*
 * Created on 29 juin 2003
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
 *
 */
package com.biglybt.ui.swt;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.AsyncDispatcher;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.HostNameToIPResolver;
import com.biglybt.core.util.RandomUtils;
import com.biglybt.core.util.SHA1Simple;
import com.biglybt.core.util.StringInterner;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.utils.LocationProvider;
import com.biglybt.pifimpl.local.PluginCoreUtils;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.ui.skin.SkinProperties;
import com.biglybt.ui.swt.imageloader.ImageLoader;

/**
 * @author Olivier
 *
 */
public class ImageRepository
{
	private static final String[] noCacheExtList = new String[] {
		".exe"
	};

	private static final boolean forceNoAWT = Constants.isOSX || Constants.isWindows;

	private static final AsyncDispatcher		async_icon_dispatcher = new AsyncDispatcher( "FileIconFetch" );

		// files with a lookup in flight, mapped to whoever wants telling when it
		// lands. a cell that has already painted won't ask for the icon again
		// (its text hasn't changed), so it has to be nudged to repaint - same
		// approach ColumnThumbAndName uses for async thumbnails

	private static final Map<IconFileKey,java.util.List<Consumer<PathIcon>>>	async_icon_pending = new HashMap<>();

		// per-file icons are shared by content: a path maps to a content key and
		// identical icons (the common case - most .exe files carry one of a
		// handful of installer icons) resolve to a single Image. the path->content
		// map is bounded so a library with millions of files can't accumulate
		// millions of path strings

	private static final int	PER_FILE_CACHE_MAX = 1024;

		// per-file icons survive a restart. the in-memory cache already
		// collapses identical icons onto one content key, so the disk layout
		// follows it: one file per *distinct* icon plus a small index mapping
		// files onto them. a library of thousands of installers usually needs
		// only a few dozen icon files.
		//
		// the index is keyed by a hash of the path rather than the path itself,
		// so it stays compact and doesn't write user paths out in the clear.

	private static final int	DISK_INDEX_MAX = 4096;

	private static final File	disk_cache_dir =
		FileUtil.newFile( SystemProperties.getUserPath(), "cache", "fileicons" );

	private static final int	disk_image_format = Constants.isWindows ? SWT.IMAGE_ICO : SWT.IMAGE_PNG;
	private static final String	disk_image_ext    = Constants.isWindows ? ".ico" : ".png";

		// path hash -> "<content key> <modified> <length>"

	private static Map<String,String>	disk_index;

		// names present in the cache directory, so testing for an icon doesn't
		// hit the filesystem; same approach ImageLoader takes for its own cache

	private static Set<String>			disk_files;

	private static volatile boolean		disk_index_dirty;

		// the index is read on the background dispatcher: listing the directory
		// and reading the file is disk work, and doing it inline would block the
		// first paint. until it has been read, lookups simply miss and fall
		// through to the shell, which is what would have happened anyway.

	private static volatile boolean		disk_init_done;
	private static boolean				disk_init_started;

	private static final AsyncDispatcher	disk_dispatcher = new AsyncDispatcher( "FileIconDisk" );

		// returns true once the on-disk index is available; kicks off the read
		// the first time it is asked

	private static boolean
	ensureDiskCache()
	{
		if ( disk_init_done ){

			return( true );
		}

		synchronized( ImageRepository.class ){

			if ( disk_init_started ){

				return( false );
			}

			disk_init_started = true;
		}

		disk_dispatcher.dispatch(()->{

			synchronized( ImageRepository.class ){

				initDiskCache();
			}

			disk_init_done = true;
		});

		return( false );
	}

	static{
			// pre-init otherwise high chance initial icon load from disk will fail
			// and ultimately ends up causing a rewrite of the (already existing)
			// file cache when it finally turns up
		
		ensureDiskCache();
	}
	
		// caller must hold the ImageRepository.class monitor

	private static void
	initDiskCache()
	{
		if ( disk_index != null ){

			return;
		}

		Set<String> names = new HashSet<>();

		File[] files = disk_cache_dir.listFiles();

		if ( files != null ){

			for ( File f: files ){

				names.add( f.getName());
			}
		}

		disk_files = names;

		Map<String,String> loaded =
			new LinkedHashMap<String,String>( 128, 0.75f, true )
			{
				@Override
				protected boolean
				removeEldestEntry(
					Map.Entry<String,String> eldest )
				{
					if ( size() > DISK_INDEX_MAX ){

						disk_index_dirty = true;

						return( true );
					}

					return( false );
				}
			};

		disk_index = loaded;

		if ( !names.isEmpty()){
			
			try{
				Map<String,Object> stored = FileUtil.readResilientFile( disk_cache_dir, "index", false );
	
				Object entries = stored.get( "entries" );
	
				if ( entries instanceof Map ){
	
					for ( Map.Entry<?,?> e: ((Map<?,?>)entries).entrySet()){
	
						Object v = e.getValue();
	
						loaded.put(
							String.valueOf( e.getKey()),
							v instanceof byte[]? new String((byte[])v, Constants.UTF_8 ): String.valueOf( v ));
					}
				}
			}catch( Throwable e ){
	
					// a damaged index just means a cold cache
			}	
	
				// entries age out of the index, but the icons they pointed at would
				// otherwise sit in the directory forever; drop any that nothing
				// refers to any more
	
			Set<String> referenced = new HashSet<>();
	
			for ( String entry: loaded.values()){
	
				int pos = entry.indexOf( ' ' );
	
				if ( pos > 0 ){
	
					referenced.add( getDiskImageName( entry.substring( 0, pos )));
				}
			}
	
			for ( String name: new java.util.ArrayList<>( names )){
	
				if ( name.equals( "index" ) || name.startsWith( "index." )){
	
					continue;
				}
	
				if ( !referenced.contains( name )){
	
					if ( FileUtil.newFile( disk_cache_dir, name ).delete()){
	
						names.remove( name );
					}
				}
			}
		}
	}

		// derived from the key itself rather than from separately passed flags,
		// so the two can't drift apart

	private static String
	getDiskKey(
		File			file,
		IconFileKey		file_key )
	{
		String str = file.getAbsolutePath() + "-" + file_key.modifier;

		return( ByteFormatter.encodeString( new SHA1Simple().calculateHash( str.getBytes( Constants.UTF_8 ))));
	}

	private static String
	getDiskImageName(
		String		content_key )
	{
		return( content_key.substring( content_key.indexOf( ':' ) + 1 ) + disk_image_ext );
	}


		// marks a file whose icon lookup came back empty, so a failure doesn't
		// have every repaint queue the lookup again; the entry ages out of the
		// map normally, which lets a file that has become reachable retry

	private static final int	PFC_NONE	= 0;
	private static final int	PFC_OK		= 1;
	private static final int	PFC_TIMEOUT	= 2;

		// a lookup that couldn't start because another was running. unlike a
		// timeout this says nothing about the file being slow, so it is worth
		// retrying almost immediately rather than after the timeout backoff.

	private static final int	PFC_BUSY	= 3;
	
	
	private static final Map<IconFileKey,PerFileContent>	per_file_content_keys =
			new LinkedHashMap<IconFileKey,PerFileContent>( 128, 0.75f, true )
			{
				@Override
				protected boolean
				removeEldestEntry(
					Map.Entry<IconFileKey,PerFileContent> eldest )
				{
					return( size() > PER_FILE_CACHE_MAX );
				}
			};

	/**public*/
	static void addPath(String path, String id) {
		SkinProperties[] skinProperties = ImageLoader.getInstance().getSkinProperties();
		if (skinProperties != null && skinProperties.length > 0) {
			skinProperties[0].addProperty(id, path);
		}
	}

	/**
	   * Gets an image for a file associated with a given program
	   *
	   */
	
	private static volatile Set<String>	ignore_icon_exts;
	
	static{
		
		COConfigurationManager.addWeakParameterListener(
				(n)->{
					String val = COConfigurationManager.getStringParameter( n );
					
					val = val.replace( ';', ' ' );
					val = val.replace( ',', ' ' );
					
					val = val.toLowerCase( Locale.US );
					
					String[] bits = val.split( " " );
					
					Set<String> exts = new HashSet<>();
					
					for ( String b: bits ){
						
						b = b.trim();
						
						if ( !b.isEmpty()){
														
							if ( !b.startsWith( "." )){
								
								b = "." + b;
							}
							
							exts.add( b );
						}
					}
					
					ignore_icon_exts = exts;
				},
				true,
				"Ignore Icon Exts" );		
	}
	
	public static PathIcon 
	getIconFromExtension(
		File file, 
		String ext, 
		boolean bBig,
		boolean minifolder) 
	{
		return( getIconFromExtension( file, ext, bBig, minifolder, null ));
	}

	public static PathIcon 
	getIconFromExtension(
		File				file, 
		String				ext, 
		boolean				bBig,
		boolean 			minifolder,
		Consumer<PathIcon>	icon_listener ) 
	{
			// when nothing can be resolved we fall back to a stand-in; for a
			// directory that has to be the folder icon, since callers can't tell
			// the transparent one apart from a real answer and will cache it

		String fallback_key = ( minifolder || ext.equals( "-folder" ))? "folder": "transparent";

		Image image = null;

		try {
				// files whose extension is in noCacheExtList (e.g. .exe) can carry
				// a per-file embedded icon, so they must not share a single icon
				// cached under the extension key. use a per-file key and fetch the
				// icon asynchronously to keep file I/O off the UI thread.

			boolean per_file_icon = false;

				// ignore_icon_exts is the user's "don't ask the shell about these"
				// list; per-file lookups must honour it too

			if ( !ignore_icon_exts.contains( ext.toLowerCase( Locale.US ))){

				for ( int i = 0; i < noCacheExtList.length; i++ ){
					if ( noCacheExtList[i].equalsIgnoreCase( ext )){
						per_file_icon = true;
						break;
					}
				}
			}

			String ext_key = "osicon" + ext;

			if ( bBig ) ext_key += "-big";
			if ( minifolder ) ext_key += "-fold";

			if ( per_file_icon ){

				IconFileKey file_key = new IconFileKey( file, bBig, minifolder );

				PerFileContent pfc;
				
				synchronized( per_file_content_keys ){
					
					pfc = per_file_content_keys.get( file_key );
				}
				
				Image ext_key_image = ImageLoader.getInstance().getImage( ext_key );

				Image default_image;
				
				if ( ImageLoader.isRealImage( ext_key_image )){

					default_image = ext_key_image;
					
				}else{
					
					default_image = ImageLoader.getInstance().getImage( fallback_key );
				}
				
				if ( pfc != null ){

					if ( pfc.type == PFC_OK || pfc.canRetry()){

						String content_key = pfc.key;
						
						if ( content_key != null ){
							
							image = ImageLoader.getInstance().getImage( content_key );
	
							if ( ImageLoader.isRealImage( image )){
								
								return( new PathIcon( image, pfc.type == PFC_TIMEOUT ));
							}
						}

						scheduleAsyncIconFetch( file, file_key, bBig, minifolder, default_image, icon_listener );
					}
				}else{

						// nothing in memory, but it may have been resolved in an
						// earlier session

					Image from_disk = getIconFromDisk( file, file_key );
					
					if ( from_disk != null ){

						return( new PathIcon( from_disk ));
					}

					scheduleAsyncIconFetch( file, file_key, bBig, minifolder, default_image, icon_listener );
				}


					// return the shared extension icon until the per-file one arrives

				Image pending_image = null;
				
				if ( ImageLoader.isRealImage( ext_key_image )){
				
					pending_image = ext_key_image;
					
				}else{

					Program program = Program.findProgram( ext );

					if ( program != null ){

						ImageData id = program.getImageData();

						if ( id != null ){

							pending_image = new Image( Display.getDefault(), id );
							
							if ( !bBig ) pending_image = force16height( pending_image );
							
							if ( minifolder ) pending_image = minifolderize( file.getParent(), pending_image, bBig );
							
							ImageLoader.getInstance().addImageNoDipose( ext_key, pending_image );
						}		
					}
				}
				
				if ( pending_image == null ){
					
					pending_image = ImageLoader.getInstance().getImage( fallback_key );
				}

				return( new PathIcon( pending_image, true ));
			}

				// from here on we are setting up the shared, non-file specific, image for the given extension
			
			String key = ext_key;

			image = ImageLoader.getInstance().getImage(key);
			
			if (ImageLoader.isRealImage(image)) {
				
				return( new PathIcon( image ));
			}

			image = null;
		
			boolean responding = Utils.isFileResponding( file );
			
			if ( responding ){

					// a directory that isn't there yet can't be asked about: the shell
					// call sets SHGFI_USEFILEATTRIBUTES for a missing path and the
					// attributes handed to it come from file.isDirectory(), which is
					// false for something that doesn't exist. it then returns the
					// icon for a plain file, and since folder icons are cached under
					// one shared key that answer becomes the folder icon for every
					// row until the client restarts.

				if ( ext.equals( "-folder" ) && !file.exists()){
					
					return( new PathIcon( ImageLoader.getInstance().getImage( "folder" )));
				}

				ImageData imageData = null;

				if (Constants.isWindows) {
						
						// Alcohol causing crashes on various file types. Really can't be bothered
					
					if ( ignore_icon_exts.contains( ext.toLowerCase( Locale.US  ))){
						
						return( new PathIcon( ImageLoader.getInstance().getImage( fallback_key )));
					}
					
					try {
						//Object[] result = Win32UIEnhancer.getFileIcon(new File(path), big);
	
						Class<?> enhancerClass = Class.forName("com.biglybt.ui.swt.win32.Win32UIEnhancer");
						Method method = enhancerClass.getMethod("getFileIcon",
								new Class[] {
									File.class,
									boolean.class
								});
						Object[] result = (Object[])method.invoke(null, new Object[] {
							file,
							bBig
						});
						if (result != null) {
							image = (Image)result[0];
							if ( image != null ){
								if (!bBig)
									image = force16height(image);
								if (minifolder)
									image = minifolderize(file.getParent(), image, bBig);
								ImageLoader.getInstance().addImageNoDipose(key, image);
								return( new PathIcon( image ));
							}
						}
					} catch (Exception e) {
						Debug.printStackTrace(e);
					}
				} else if (Constants.isOSX) {
					try {
						Class<?> enhancerClass = Class.forName("com.biglybt.ui.swt.osx.CocoaUIEnhancer");
						Method method = enhancerClass.getMethod("getFileIcon",
								new Class[] {
									String.class,
									int.class
								});
						image = (Image) method.invoke(null, new Object[] {
							file.getAbsolutePath(),
							(int) (bBig ? 128 : 16)
						});
						if (image != null) {
							if (!bBig)
								image = force16height(image);
							if (minifolder)
								image = minifolderize(file.getParent(), image, bBig);
							ImageLoader.getInstance().addImageNoDipose(key, image);
							return( new PathIcon( image ));
						}
					} catch (Throwable t) {
						Debug.printStackTrace(t);
					}
				}
			
				if (imageData == null) {
					Program program = Program.findProgram(ext);
					if (program != null) {
						imageData = program.getImageData();
					}
				}
	
				if (imageData != null) {
					image = new Image(Display.getDefault(), imageData);
					if (!bBig)
						image = force16height(image);
					if (minifolder)
						image = minifolderize(file.getParent(), image, bBig);
	
					ImageLoader.getInstance().addImageNoDipose(key, image);
				}
			}
		}catch( Throwable e ){
			
			// seen exceptions thrown here, due to images.get failing in Program.hashCode
			// ignore and use default icon
		}

		if (image == null) {
			
			// not responding or failed for some other reason
			// assume it doesn't exist for folder icon purposes
			
			if ( ext.equals( "-folder" )){
				
				return( new PathIcon( ImageLoader.getInstance().getImage( "folder" )));
			}

			return( new PathIcon( ImageLoader.getInstance().getImage( fallback_key )));
		}
		return( new PathIcon( image ));
	}

		// resolve a per-file icon without blocking the interface: the (potentially
		// slow) file existence check runs on a background thread, the icon lookup
		// itself is then handed to the SWT thread as getFileIcon requires

	private static void
	saveDiskIndex()
	{
		synchronized( ImageRepository.class ){

			disk_index_dirty = true;
		}

		disk_dispatcher.dispatch(()->{

			Map<String,String>	copy;

			synchronized( ImageRepository.class ){

				if ( !disk_index_dirty || disk_index == null ){

					return;
				}

				disk_index_dirty = false;

				copy = new LinkedHashMap<>( disk_index );
			}

			try{
				Map<String,Object>	entries = new HashMap<>();

				for ( Map.Entry<String,String> e: copy.entrySet()){

					entries.put( e.getKey(), e.getValue().getBytes( Constants.UTF_8 ));
				}

				Map<String,Object>	stored = new HashMap<>();

				stored.put( "entries", entries );

				disk_cache_dir.mkdirs();

				FileUtil.writeResilientFile( disk_cache_dir, "index", stored, false );

			}catch( Throwable e ){

				Debug.out( e );
			}
		});
	}

		// hands back a cached icon if we have one for this file, and kicks off a
		// background check that the file hasn't changed since it was stored.
		// checking before returning would put the disk access we're avoiding
		// straight back onto the UI thread.

	private static Image
	getIconFromDisk(
		File			file,
		IconFileKey		file_key )
	{
		try{
			if ( !ensureDiskCache()){

				return( null );
			}

			String		disk_key;
			String		entry;

			synchronized( ImageRepository.class ){

				disk_key = getDiskKey( file, file_key );

				entry = disk_index.get( disk_key );
			}

			if ( entry == null ){

				return( null );
			}

			String[] bits = entry.split( " " );

			if ( bits.length != 3 ){

				return( null );
			}

			String content_key = bits[0];

			Image image = ImageLoader.getInstance().getImage( content_key );

			if ( !ImageLoader.isRealImage( image )){

				ImageLoader.getInstance().releaseImage( content_key );

				image = loadDiskImage( content_key );

				if ( image == null ){

					synchronized( ImageRepository.class ){

						disk_index.remove( disk_key );
					}

					saveDiskIndex();

					return( null );
				}

				ImageLoader.getInstance().addImageNoDipose( content_key, image );
			}

			synchronized( per_file_content_keys ){

				per_file_content_keys.put( file_key, new PerFileContent( PFC_OK, content_key ));
			}

			scheduleDiskStaleCheck( file, file_key, disk_key, bits[1], bits[2] );

			return( image );

		}catch( Throwable e ){

			return( null );
		}
	}

		// this does read a file on the SWT thread, which is deliberate: the
		// point of the cache is to hand the icon back synchronously, and it is
		// a few KB from our own directory on the local disk. the shell call it
		// replaces is the one that can block for seconds on a dead share.

	private static Image
	loadDiskImage(
		String		content_key )
	{
		String name = getDiskImageName( content_key );

		synchronized( ImageRepository.class ){

			if ( !disk_files.contains( name )){

				return( null );
			}
		}

		File file = FileUtil.newFile( disk_cache_dir, name );

		InputStream is = null;

		try{
			is = FileUtil.newFileInputStream( file );

			return( new Image( Display.getDefault(), is ));

		}catch( Throwable e ){

				// truncated or corrupt, treat as a miss and let it be written again

			file.delete();

			synchronized( ImageRepository.class ){

				disk_files.remove( name );
			}

			return( null );

		}finally{

			if ( is != null ){

				try{ is.close(); }catch( Throwable f ){}
			}
		}
	}

		// called on the SWT thread, so the only thing done here is pulling the
		// pixels out of the Image - an SWT operation that has to happen on this
		// thread. serialising them, writing the file and stat'ing the source all
		// go to the background dispatcher, since none of it belongs on the UI
		// thread.

	private static void
	storeIconOnDisk(
		File			file,
		IconFileKey		file_key,
		String			content_key,
		Image			icon )
	{
		final String	name = getDiskImageName( content_key );

			// kick the index read off if it hasn't run yet. the dispatcher is
			// sequential, so the work queued below lands after it and nothing
			// is lost while the cache is warming up.

		ensureDiskCache();

		final ImageData data;

		try{
			data = icon.getImageData();

		}catch( Throwable e ){

			return;
		}

		disk_dispatcher.dispatch(()->{

			boolean have_image;

			synchronized( ImageRepository.class ){

				if ( disk_index == null ){

					return;
				}

				have_image = disk_files.contains( name );
			}

			if ( !have_image ){

				try{
					org.eclipse.swt.graphics.ImageLoader saver = new org.eclipse.swt.graphics.ImageLoader();

					saver.data = new ImageData[]{ data };

					ByteArrayOutputStream baos = new ByteArrayOutputStream();

					saver.save( baos, disk_image_format );

					disk_cache_dir.mkdirs();

					FileUtil.writeBytesAsFile( FileUtil.newFile( disk_cache_dir, name ).getAbsolutePath(), baos.toByteArray());

					synchronized( ImageRepository.class ){

						disk_files.add( name );
					}
				}catch( Throwable e ){

						// failing to persist isn't fatal, the memory cache still works

					return;
				}
			}

			try{
				String disk_key = getDiskKey( file, file_key );

				String entry = content_key + " " + file.lastModified() + " " + file.length();

				synchronized( ImageRepository.class ){

					disk_index.put( disk_key, entry );
				}

				saveDiskIndex();

			}catch( Throwable e ){
			}
		});
	}

	private static void
	scheduleDiskStaleCheck(
		File			file,
		IconFileKey		file_key,
		String			disk_key,
		String			modified,
		String			length )
	{
		disk_dispatcher.dispatch(()->{

			try{
				if ( !Utils.fileExistsWithTimeout( file )){

					return;
				}

				if ( 	String.valueOf( file.lastModified()).equals( modified ) &&
						String.valueOf( file.length()).equals( length )){

					return;
				}

					// the file has been replaced since the icon was stored

				synchronized( ImageRepository.class ){

					disk_index.remove( disk_key );
				}

				synchronized( per_file_content_keys ){

					per_file_content_keys.remove( file_key );
				}

				saveDiskIndex();

			}catch( Throwable e ){
			}
		});
	}

	private static void
	scheduleAsyncIconFetch(
		final File					file,
		final IconFileKey			file_key,
		final boolean				bBig,
		final boolean				minifolder,
		final Image					default_icon,
		final Consumer<PathIcon>	listener )
	{
		synchronized( async_icon_pending ){

			java.util.List<Consumer<PathIcon>> waiting = async_icon_pending.get( file_key );

			if ( waiting != null ){

					// a lookup is already in flight for this file; just join it

				if ( listener != null ){

					waiting.add( listener );
				}

				return;
			}

			waiting = new java.util.ArrayList<>();

			if ( listener != null ) waiting.add( listener );

			async_icon_pending.put( file_key, waiting );
		}

		async_icon_dispatcher.dispatch(()->{
			
			PathIcon[] result = { new PathIcon( null, false ) };

			try{

				boolean reachable = Utils.fileExistsWithTimeout( file );

				if ( !reachable ){

					synchronized( per_file_content_keys ){
					
						PerFileContent pfc = per_file_content_keys.get( file_key );
					
						if ( pfc != null && pfc.type == PFC_TIMEOUT ){
							
							pfc.setFailed();
							
						}else{
						
							per_file_content_keys.put( file_key, new PerFileContent( PFC_TIMEOUT, null ));
						}
					}
					
					result[0] = new PathIcon( default_icon, true );
					
					return;
				}

					// getFileIcon has to run on the SWT thread; block this background
					// thread until it has done so, which leaves the dispatcher
					// throttling the lookups one at a time instead of flooding the
					// SWT queue with thousands of them
											
				Utils.execSWTThread(()->{

					Image icon = null;
					boolean	timeout = false;
					boolean	busy	= false;
					
					if ( Constants.isWindows ){
						try{
							Class<?> cls = Class.forName( "com.biglybt.ui.swt.win32.Win32UIEnhancer" );
							Method m = cls.getMethod( "getFileIcon", new Class[]{ File.class, boolean.class });
							Object[] temp = (Object[])m.invoke( null, new Object[]{ file, bBig });
							if ( temp != null ){
								icon = (Image)temp[0];
								timeout = (Boolean)temp[1];
								busy = (Boolean)temp[2];
							}
						}catch( Throwable e ){
						}
					}else if ( Constants.isOSX ){
						try{
							Class<?> cls = Class.forName( "com.biglybt.ui.swt.osx.CocoaUIEnhancer" );
							Method m = cls.getMethod( "getFileIcon", new Class[]{ String.class, int.class });
							icon = (Image)m.invoke( null, new Object[]{ file.getAbsolutePath(), (int)(bBig ? 128 : 16) });
						}catch( Throwable e ){
						}
					}

					if ( icon != null ){
						
						icon = cachePerFileIcon( file, file_key, icon, bBig, minifolder );

						synchronized( result ){
							
							result[0] = new PathIcon( icon );
						}
					}else{
						synchronized( result ){
							
							result[0] = new PathIcon( default_icon, timeout );
						}

							// a timeout, or a lookup that couldn't run because
							// another was in progress, says nothing about the
							// file: record it as retryable. only a lookup that
							// ran and came back empty means there is no icon.

						synchronized( per_file_content_keys ){
						
							int type = busy? PFC_BUSY: timeout? PFC_TIMEOUT: PFC_NONE;
							
							if ( type == PFC_NONE ){
								
								per_file_content_keys.put( file_key, new PerFileContent( PFC_NONE, null ));
								
							}else{
								
								PerFileContent pfc = per_file_content_keys.get( file_key );
								
								if ( pfc != null && pfc.type == type ){
									
									pfc.setFailed();
									
								}else{
									
									per_file_content_keys.put( file_key, new PerFileContent( type, null ));
								}
							}
						}
					}
				}, false );
				
			}catch( Throwable e ){

				Debug.out( e );
				
			}finally{

				java.util.List<Consumer<PathIcon>> waiting;

				synchronized( async_icon_pending ){

					waiting = async_icon_pending.remove( file_key );
				}

				if ( waiting != null ){

					PathIcon pi;
					
					synchronized( result ){
						
						pi = result[0];
					}
					
					for ( Consumer<PathIcon> r: waiting ){

						try{
							r.accept( pi );

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}
			}
		});
	}

		// most files of a given type carry one of a handful of icons, so key the
		// cached Image by icon content: a thousand installers sharing an icon end
		// up sharing a single Image rather than a thousand copies

	private static Image
	cachePerFileIcon(
		File			file,
		IconFileKey		file_key,
		Image			icon,
		boolean			bBig,
		boolean			minifolder )
	{
		boolean	cached = false;

		try{
			ImageData data = icon.getImageData();

				// key on the pixels plus the geometry, so two icons that happen to
				// share a byte pattern at different sizes or depths don't collide

			String content_key =
				"osicon-pfc:" + ByteFormatter.encodeString( new SHA1Simple().calculateHash( data.data )) +
				"-" + data.width + "x" + data.height + "x" + data.depth;

			if ( bBig ) content_key += "-big";
			if ( minifolder ) content_key += "-fold";

			Image existing = ImageLoader.getInstance().getImage( content_key );
			
			boolean already_held = ImageLoader.isRealImage( existing );

			if ( already_held ){

					// an identical icon is already cached, so drop this copy and
					// point the file at the shared one

				synchronized( per_file_content_keys ){
					
					PerFileContent pfc = per_file_content_keys.get( file_key );
				
					if ( pfc == null || pfc.type != PFC_OK ){
				
						pfc = new PerFileContent( PFC_OK, content_key );
					}
				
					per_file_content_keys.put( file_key, pfc );
				}

				storeIconOnDisk( file, file_key, content_key, existing );

				return( existing );
			}

			if ( !bBig ) icon = force16height( icon );
			if ( minifolder ) icon = minifolderize( file.getParent(), icon, bBig );

			ImageLoader.getInstance().addImageNoDipose( content_key, icon );

			cached = true;

			synchronized( per_file_content_keys ){
			
				per_file_content_keys.put( file_key, new PerFileContent( PFC_OK, content_key ));
			}
			
			storeIconOnDisk( file, file_key, content_key, icon );
			
			return( icon );
			
		}catch( Throwable e ){

			Debug.out( e );

			return( icon );
			
		}finally{

			if ( !cached && icon != null && !icon.isDisposed()){

				icon.dispose();
			}
		}
	}

	private static Image minifolderize(String path, Image img, boolean big) {
		Image imgFolder =  ImageLoader.getInstance().getImage(big ? "folder" : "foldersmall");
		Rectangle folderBounds = imgFolder.getBounds();
		Rectangle dstBounds = img.getBounds();
		Image tempImg = Utils.renderTransparency(Display.getCurrent(), img,
				imgFolder, new Point(dstBounds.width - folderBounds.width,
						dstBounds.height - folderBounds.height), 204);
		if (tempImg != null) {
			img.dispose();
			img = tempImg;
		}
		return img;
	}

	private static Image force16height(Image image) {
		if (image == null) {
			return image;
		}

		Rectangle bounds = image.getBounds();
		if (bounds.height != 16) {
			Image newImage = new Image(image.getDevice(), 16, 16);
			GC gc = new GC(newImage);
			try {
				if (!Constants.isUnix) {
					// drawImage doesn't work on GTK when advanced is on
					gc.setAdvanced(true);
				}

				gc.drawImage(image, 0, 0, bounds.width, bounds.height, 0, 0, 16, 16);
			} finally {
				gc.dispose();
			}

			image.dispose();
			image = newImage;
		}

		return image;
	}

	/**
	* <p>Gets an iconic representation of the file or directory at the path</p>
	* <p>For most platforms, the icon is a 16x16 image; weak-referencing caching is used to avoid abundant reallocation.</p>
	* @param path Absolute path to the file or directory
	* @return The image
	*/
	public static PathIcon 
	getPathIcon(
		String		path,
		Boolean		isFile,
		boolean		bBig,
		boolean		minifolder) 
	{
		return( getPathIcon( path, isFile, bBig, minifolder, null ));
	}

		// icon_listener is run once an asynchronously resolved per-file icon has
		// landed in the cache. a table cell that has already painted won't ask
		// again on its own, so it uses this to invalidate itself and repaint.

	public static PathIcon 
	getPathIcon(
		String				path, 
		Boolean				isFile,
		boolean				bBig,
		boolean				minifolder,
		Consumer<PathIcon>	icon_listener ) 
	{
		if (path == null){
			return( new PathIcon( null ));
		}
		
		File file = null;
		boolean bDeleteFile = false;

		boolean noAWT = forceNoAWT || !bBig;

		try {
			file = new File(path);

			// workaround for unsupported platforms
			// notes:
			// Mac OS X - Do not mix AWT with SWT (possible workaround: use IPC/Cocoa)

			String key;
			if ( isFile==null?Utils.isDirectoryWithTimeout( file ):!isFile){
				if (noAWT) {
					if (Constants.isWindows || Constants.isOSX) {
						return getIconFromExtension(file, "-folder", bBig, false);
					}
					return( new PathIcon( ImageLoader.getInstance().getImage("folder")));
				}

				key = file.getPath();
			} else {
				final int idxDot = file.getName().lastIndexOf(".");

				if (idxDot == -1) {
					if (noAWT) {
						return getIconFromExtension(file, "", bBig, false);
					}

					key = "?!blank";
				} else {
					final String ext = file.getName().substring(idxDot);
					key = ext;

					if (noAWT)
						return getIconFromExtension(file, ext, bBig, minifolder, icon_listener);

					// case-insensitive file systems
					for (int i = 0; i < noCacheExtList.length; i++) {
						if (noCacheExtList[i].equalsIgnoreCase(ext)) {
							key = file.getPath();
							break;
						}
					}
				}
			}

			if (bBig)
				key += "-big";
			if (minifolder)
				key += "-fold";

			key = "osicon" + key;

			// this method mostly deals with incoming torrent files, so there's less concern for
			// custom icons (unless user sets a custom icon in a later session)

			// other platforms - try sun.awt
			Image image = ImageLoader.getInstance().getImage(key);
			if (ImageLoader.isRealImage(image)) {
				return( new PathIcon( image ));
			}
			image = null;

			bDeleteFile = !Utils.fileExistsWithTimeout(file);
			if (bDeleteFile) {
				file = File.createTempFile("AZ_", FileUtil.getExtension(path));
			}

			java.awt.Image awtImage = null;

			try {
  			final Class sfClass = Class.forName("sun.awt.shell.ShellFolder");
  			if (sfClass != null && file != null) {
  				Method method = sfClass.getMethod("getShellFolder", new Class[] {
  					File.class
  				});
  				if (method != null) {
  					Object sfInstance = method.invoke(null, new Object[] {
  						file
  					});

  					if (sfInstance != null) {
  						method = sfClass.getMethod("getIcon", new Class[] {
  							Boolean.TYPE
  						});
  						if (method != null) {
  							awtImage = (java.awt.Image) method.invoke(sfInstance,
  									new Object[] {
										  Boolean.valueOf(bBig)
  									});
  						}
  					}
  				}
  			}
			} catch (Throwable e) {
			}

			if (awtImage != null) {
				final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
				ImageIO.write((BufferedImage) awtImage, "png", outStream);
				final ByteArrayInputStream inStream = new ByteArrayInputStream(
						outStream.toByteArray());

				image = new Image(Display.getDefault(), inStream);
				if (!bBig) {
					image = force16height(image);
				}
				if (minifolder)
					image = minifolderize(file.getParent(), image, bBig);


				ImageLoader.getInstance().addImageNoDipose(key, image);

				if (bDeleteFile && file != null && Utils.fileExistsWithTimeout( file )) {
					file.delete();
				}
				return( new PathIcon( image ));
			}
		} catch (Exception e) {
			//Debug.printStackTrace(e);
		}

		if (bDeleteFile && file != null && Utils.fileExistsWithTimeout( file )) {
			file.delete();
		}

		// Possible scenario: Method call before file creation
		String ext = FileUtil.getExtension(path);
		if (ext.length() == 0) {
			return( new PathIcon( ImageLoader.getInstance().getImage("folder")));
		}

		return getIconFromExtension(file, ext, bBig, minifolder, icon_listener);
	}

	private static LocationProvider	flag_provider;
	private static long				flag_provider_last_check;

	private static Image	flag_none		= ImageLoader.getNoImage();
	private static Object	flag_small_key 	= new Object();
	private static Object	flag_big_key 	= new Object();

	private static Map<String,Image>	flag_cache = new HashMap<>();

	private static LocationProvider
	getFlagProvider()
	{
		if ( flag_provider != null ){

			if ( flag_provider.isDestroyed()){

				flag_provider 				= null;
				flag_provider_last_check	= 0;
			}
		}

		if ( flag_provider == null ){

			long	now = SystemTime.getMonotonousTime();

			if ( flag_provider_last_check == 0 || now - flag_provider_last_check > 20*1000 ){

				flag_provider_last_check = now;

				java.util.List<LocationProvider> providers = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface().getUtilities().getLocationProviders();

				for ( LocationProvider provider: providers ){

					if ( 	provider.hasCapabilities(
								LocationProvider.CAP_ISO3166_BY_IP |
								LocationProvider.CAP_FLAG_BY_IP )){

						flag_provider = provider;
					}
				}
			}
		}

		return( flag_provider );
	}

	public static boolean
	hasCountryFlags(
		boolean		small )
	{
		if ( !Utils.isSWTThread()){

			Debug.out( "Needs to be swt thread..." );

			return( false );
		}

		LocationProvider fp = getFlagProvider();

		if ( fp == null ){

			return( false );
		}

		return( true );
	}

	public static Image
	getCountryFlag(
		Peer		peer,
		boolean		small )
	{
		return( getCountryFlag( PluginCoreUtils.unwrap( peer ), small ));
	}

	private static Map<String,Image>	net_images = new HashMap<>();

	public static Image
	getCountryFlag(
		PEPeer		peer,
		boolean		small )
	{
		if ( peer == null ){

			return( null );
		}

		Object	peer_key = small?flag_small_key:flag_big_key;

		Image flag = (Image)peer.getUserData( peer_key );

		if ( flag == null ){

			LocationProvider fp = getFlagProvider();

			if ( fp != null ){

				try{
					String ip = peer.getIp();

					if ( HostNameToIPResolver.isDNSName( ip )){

						InetAddress peer_address = HostNameToIPResolver.syncResolve( ip );

						String cc_key = fp.getISO3166CodeForIP( peer_address ) + (small?".s":".l");

						flag = flag_cache.get( cc_key );

						if ( flag != null ){

							peer.setUserData( peer_key, flag );

						}else{

							InputStream is = fp.getCountryFlagForIP( peer_address, small?0:1 );

							if ( is != null ){

								try{
									Display display = Display.getDefault();

									flag = new Image( display, is);

									//System.out.println( "Created flag image for " + cc_key );

								}finally{

									is.close();
								}
							}else{

								flag = flag_none;
							}

							flag_cache.put( cc_key, flag );

							peer.setUserData( peer_key, flag );
						}
					}else{

						String cat =  AENetworkClassifier.categoriseAddress( ip );

						if ( cat != AENetworkClassifier.AT_PUBLIC ){

							final String key = "net_" + cat + (small?"_s":"_b" );

							Image i = net_images.get( key );

							if ( i == null ){

								Utils.execSWTThread(
									new Runnable()
									{
										@Override
										public void
										run()
										{
											Image i = ImageLoader.getInstance().getImage( key );

											net_images.put( key, i );
										}
									},
									false );

								i = net_images.get( key );
							}

							if ( ImageLoader.isRealImage( i )){

								return( i );
							}
						}
					}

				}catch( Throwable e ){

				}
			}
		}

		if ( flag == flag_none ){

			return( null );
		}

		return( flag );
	}

	public static Image
	getCountryFlag(
		InetAddress		address,
		boolean			small )
	{
		if ( address == null ){

			return( null );
		}

		Image flag = null;

		LocationProvider fp = getFlagProvider();

		if ( fp != null ){

			try{
				String cc_key = fp.getISO3166CodeForIP( address ) + (small?".s":".l");

				flag = flag_cache.get( cc_key );

				if ( flag == null ){

					InputStream is = fp.getCountryFlagForIP( address, small?0:1 );

					if ( is != null ){

						try{
							Display display = Display.getDefault();

							flag = new Image( display, is);

							//System.out.println( "Created flag image for " + cc_key );

						}finally{

							is.close();
						}
					}else{

						flag = flag_none;
					}

					flag_cache.put( cc_key, flag );
				}

			}catch( Throwable e ){

			}
		}

		if ( flag == flag_none ){

			return( null );
		}

		return( flag );
	}

	public static Image
	getCountryFlag(
		String			cc,
		boolean			small )
	{
		if ( cc == null ){

			return( null );
		}

		if ( AENetworkClassifier.internalise( cc ) == cc ){
			
			final String key = "net_" + cc + (small?"_s":"_b" );

			Image i = net_images.get( key );

			if ( i == null ){

				Utils.execSWTThread(
					new Runnable()
					{
						@Override
						public void
						run()
						{
							Image i = ImageLoader.getInstance().getImage( key );

							net_images.put( key, i );
						}
					},
					false );

				i = net_images.get( key );
			}

			if ( ImageLoader.isRealImage( i )){

				return( i );
			}
		}
		
		Image flag = null;

		LocationProvider fp = getFlagProvider();

		if ( fp != null ){

			try{
				String cc_key = cc + (small?".s":".l");

				flag = flag_cache.get( cc_key );

				if ( flag == null ){

					InputStream is = fp.getCountryFlagForISO3166Code( cc, small?0:1 );

					if ( is != null ){

						try{
							Display display = Display.getDefault();

							flag = new Image( display, is);

						}finally{

							is.close();
						}
					}else{

						flag = flag_none;
					}

					flag_cache.put( cc_key, flag );
				}

			}catch( Throwable e ){

			}
		}

		if ( flag == flag_none ){

			return( null );
		}

		return( flag );
	}

	static class
	IconFileKey
	{
		final StringInterner.FileKey		file_key;
		final byte							modifier;
		
		IconFileKey(
			File		file,
			boolean		big,
			boolean		minifolder )
		{
			file_key = new StringInterner.FileKey( file );
			
			short mod = 0;
			
			if ( big ){
				mod += 1;
			}
			if ( minifolder ){
				mod += 2;
			}
			modifier = (byte)mod;
		}
		
		public int
		hashCode()
		{
			return( file_key.hashCode() + modifier );
		}
		
		public boolean
		equals(
			Object	_other )
		{
			IconFileKey other = (IconFileKey)_other;
			
			return( other.file_key.equals( file_key ) && other.modifier == modifier );
		}
	}

	static class
	PerFileContent
	{
		final long		time = SystemTime.getMonotonousTime();

		final int		type;
		final String	key;
		
			// written under the per_file_content_keys monitor but read by
			// canRetry() outside it, so make the read see the current value

		volatile int	fail_count;
		
		PerFileContent(
			int		_type,
			String	_key )
		{
			type	= _type;
			key		= _key;
			
			if ( type == PFC_TIMEOUT || type == PFC_BUSY ){
				
				fail_count = 1;
			}
		}
		
		void
		setFailed()
		{
			if ( fail_count < 20 ){
				
				fail_count++;
			}
		}
		
		boolean
		canRetry()
		{
			long elapsed = SystemTime.getMonotonousTime() - time;

			if ( type == PFC_TIMEOUT ){
				
				long delay = fail_count * ( 30*1000 + RandomUtils.nextInt( 10*1000 ));
				
				return( elapsed > delay );
				
			}else if ( type == PFC_BUSY ){
				
					// the queue is serialised, so the next slot comes round
					// quickly; back off a little if it keeps missing

				long delay = Math.min( fail_count, 10 ) * ( 1000 + RandomUtils.nextInt( 500 ));
				
				return( elapsed > delay );
				
			}else{
				
				return( false );
			}
		}
	}

	public static class
	PathIcon
	{
		final public Image		image;
		final public boolean	temporary;
		
		PathIcon(
			Image		_image )
		{
			this( _image, false );
		}
			
		PathIcon(
			Image		_image,
			boolean		_t )
		{
			image		= _image;
			temporary	= _t;
		}
	}
	
	public static void main(String[] args) {
		Display display = new Display();
		Shell shell = new Shell(display, SWT.SHELL_TRIM);
		shell.setLayout(new FillLayout(SWT.VERTICAL));

		final Label label = new Label(shell, SWT.BORDER);

		final Text text = new Text(shell, SWT.BORDER);
		text.addModifyListener(new ModifyListener() {

			@Override
			public void modifyText(ModifyEvent e) {
				ImageRepository.PathIcon pi = getPathIcon(text.getText(), null, false, false);
				label.setImage(pi.image);
			}
		});

		shell.open();

		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}
}